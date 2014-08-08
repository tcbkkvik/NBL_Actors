Lambda-Actor
============

A lightweight, easy to learn, flexible, typed-[actor](http://en.wikipedia.org/wiki/Actor_model)
concurrency API, based on lambdas (Java 8).

An actor can be seen as an extension of the Object-Oriented (OO) model:
While the OO-model is good at protecting private fields and methods, its default
multi-threading synchronization is often hard to get right.
In contrast, an actor object has built-in concurrency protection with its message based concurrency.

Taking full advantage of lambdas from beginning, this API is a compact yet flexible implementation
of the actor-model. Redundant concepts and code has continuously been trimmed away
, intended to achieve a high "power-to-weight" ratio (Ie. few concepts, but simple to combine).

## Easy to learn
Basically, an object of a type 'A', wrapped inside an actor reference (`IActorRef<A>`),
becomes an actor. This object should not be referred directly (except from this actor itself)
, but by sending it messages through its actor reference.


Her is a mini-tutorial, covering all essential concepts (as working code):
```java
    static void easy_to_learn(IGreenThrFactory factory) {
        //PS. Must run inside an instance of 'IGreenThr'
        /* 1. Extend your class (A) from ActorBase: */
        class A extends ActorBase<A> {
            int x;
            void increaseX() {++x;}
            int getX() {return x;}
        }

        /* 2. Create a new instance (a) of A in an actor reference.
           The 'init' call binds (a) with a lightweight thread (green-thread)
           to queue and process its received messages: */
        IActorRef<A> refA = new A()
                .init(factory); //init: binds (a) with a new thread.

        /* 3. Send it messages
            3.1) Send = Basic one-way messaging: */
        refA.send(a -> a.increaseX());
        refA.send(A::increaseX); //same effect

        /*  3.2) Call = Messages with callback: */
        refA.call(
                A::getX
                // getX is called from the thread of refA

                , x -> System.out.println(" got x: " + x)
                // callback; called at my own thread
        );
    }
```
..Ready to try yourself?

## Usage patterns

### Actors from plain Java objects
Wrapping an object inside an actor reference (`IActorRef`),
protects it against concurrent access, as long as all calls from
other threads or actors goes through this reference.

Although its best to extend from `ActorBase` to make clear that
the methods should no be called directly from other threads
, it is possible to transform a plain java object into an actor..

Example:
```java
    static void minimumExample(IGreenThrFactory factory) {
        class PlainObj {
            public void someMethod(double value) {
                System.out.println("received value: " + value);
            }
        }
        //Wrap 'PlainObj' in a new actor reference:
        IActorRef<PlainObj> ref = new ActorRef<>(
                factory,  //factory; gives a lightweight thread to the actor
                new PlainObj());
        //send a message = asynchronous method call (lambda expression):
        ref.send(a -> a.someMethod(34));
        //PS. Avoid leaking shared-mutable-access via message passing.
    }
```

### Actor base class
Get access to more actor functionality by extending from class `ActorBase`.
Example - Actor sends to itself using `ActorBase.self`:
```java
    static void actorBaseExample(IGreenThrFactory factory) {
        class Impl extends ActorBase<Impl> {
            void otherMethod(String message) {
                System.out.println(message + ": done!");
            }

            void someMethod(String message) {
                this.self().send(a -> a.otherMethod(message));
            }
        }
        //call 'init' to initiate reference:
        IActorRef<Impl> ref = new Impl().init(factory);
        //send a message = asynchronous method call (lambda expression):
        ref.send(a -> a.someMethod("do it!"));
    }
```

### Protect mutable state
Never access local mutable fields from lambda blocks running in other threads.
Example:
```java
    static class LeakedState extends ActorBase<LeakedState> {
        int value;

        //Avoid leaking state via messages..
        void WRONG_copyFrom(IActorRef<LeakedState> ref) {
            ref.send(a -> {
                value = a.value;
                //Probably in another thread, leading
                //to shared mutable access - DON'T do this!
                //Never access local mutable fields from here!
            });
        }

        void correct_copyFrom(IActorRef<LeakedState> other) {
            other.call(a -> a.value //another thread
                    , result -> value = result //..back to my thread
            );
        }

        static void run(IGreenThrFactory f) {
            final IActorRef<LeakedState> ref = new LeakedState().init(f);
            new LeakedState()
                    .init(f)
                    .send(a -> {
                        a.WRONG_copyFrom(ref);
                        a.correct_copyFrom(ref);
                    });
        }
    }

```

### Become - change runtime behaviour
Change behaviour by calling `ActorBase.become`;
Example:
```java
    static class BecomeDemo extends ActorBase<BecomeDemo> {
        public void gotMessage() {
            System.out.println(" I am the original actor");
            final BecomeDemo original = this;
            become(new BecomeDemo() {
                @Override
                public void gotMessage() {
                    System.out.println(" I am a second implementation");
                    become(original);
                }
            });
        }
    }
```

### Non-blocking 'futures'
Wait for future responses without blocking.
Example - Return an asynchronous value via `IASync`:
```java
    static void nonBlockingFuture(IGreenThr thr) {

        class ValueActor {
            final ASyncValue<Integer> async = new ASyncValue<>();
            IASync<Integer> getAsync() {
                return async;
            }
        }

        class MainActor {
            int gotValue;
            void someCalls(int correct, IActorRef<ValueActor> ref) {
                ref
                        .call(a -> a.getAsync())
                        .result(ret -> { //In MainActor thread:
                            gotValue = ret;
                            log(" correct value: " + correct);
                            log("returned value: " + ret);
                            assert ret == correct;
                        });
                ref.send(valueActor
                        -> valueActor.async.accept(correct));
            }
        }
        IActorRef<ValueActor> valRef = new ActorRef<>(new ValueActor(), thr);
        IActorRef<MainActor> mainRef = new ActorRef<>(new MainActor(), thr);
        mainRef.send(a -> a.someCalls(31407, valRef));
        // Output:
        // correct value: 31407
        // returned value: 31407
    }
```


### Non-blocking Fork/Join
The `ForkJoin` utility class gives you fully generic, non-blocking Fork/Join.
Each call to `ForkJoin.call` or `ForkJoin.callAsync` forks a new concurrent child node (=computation),
which is free to start other types of computation (heterogeneous).
It can also be recursive.

Example;
Recursively split a string to left/right halves until small enough (Fork),
and then merge the strings back together (Join).
The final merged string should be equal to original:
```java
    static IASync<String> splitMerge(IGreenThrFactory tf, String original) {
        if (original.length() < 6) return new ASyncDirect<>(original);
        ForkJoin<String> fj = new ForkJoin<>("");
        int count = 0;
        for (String str : splitLeftRight(original)) {
            final boolean isLeft = count++ == 0;
            fj.callAsync(tf.newThread()
                    , () -> splitMerge(tf, str)
                    , (val, ret) -> isLeft ? ret + val : val + ret
                    //merge strings again (ForkJoin result updated)
            );
        }
        return fj.resultAsync();
    }
```

### Adding functionality
This library is small, with a simple focus on core Actor features,
but it is intended for combination with other useful libraries etc.
for instance with java.util.concurrent.atomic.*.
Example;
Adding Cancel functionality with `AtomicBoolean`:
```java
        final AtomicBoolean isCancel = new AtomicBoolean(false);
        ref.send(a -> {
            if (!isCancel.get())
                a.doWork();
        });
        //..might be called later:
        isCancel.set(true);
```

### Flow control
Message-queue overflow can in general be avoided by
returning feedback-messages to sending actor.
The sender can then slow down by:

    1. Blocking until consuming actor is ready. (best to avoid?)
    2. Instead of blocking, thread could alternatively help receiving actor (if not already active)
    3. Rejecting received message. (vital messages lost?)
    4. Message-pulling instead of passive receive.
    
Example:
```java
        //User defined method inside actor:
        public void pullNextMessage() {
            Integer val = pullSource.get();
            if (val == null) return; //stop
            destination.send(d -> {
                d.consume(val);
                // Feedback "loop" here:
                self().send(s -> s.pullNextMessage());
            });
        }
```

### Dynamic messaging
Messages as lambda expressions (continuations) are powerful,
since they carry algorithmic expressions, executed on receiver's behalf.
This gives you a lot of flexibility..

Example; Passing a message through a chain of actors:
```java
        static void recursive_call_chain(Iterator<IActorRef<A>> actorIt) {
            if (actorIt.hasNext())
                actorIt.next()
                        .send(a -> {
                            a.gotIt();
                            //Got message! now try next actor in chain:
                            recursive_call_chain(actorIt);
                        });
            else
                log("end of call-chain!");
        }
```

### Distributed computing?
Messaging over a network is not implemented, but could become an option by
serializing `IActorRef.send(java.util.function.Consumer<A> msg)`..


## More documentation..
* For more examples & other source code: Check under src/flc/lambdactor/...
* Java docs; Download as ZIP file; check under the doc/ folder.
