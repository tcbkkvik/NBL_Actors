Non-Blocking Lambda-based Actors
================================

A lightweight but flexible [actor](http://en.wikipedia.org/wiki/Actor_model)
concurrency API, based on java lambdas from start..

An actor can be seen as an extension of the Object-Oriented (OO) model:
While the OO-model is good at protecting private fields and methods, its default
multi-threading synchronization is often hard to get right.
In contrast, even a minimal actor has (or should have) built-in concurrency protection
through message based concurrency.

Taking full advantage of lambdas from beginning, this API is a compact yet flexible implementation
of the actor-model. Redundant concepts and code has continuously been trimmed away
, intended to achieve a high "power-to-weight" ratio (Ie. few concepts, but simple to combine).

In this context, a message sent to an actor **a**, is a lambda expression:
##**&lambda;a&rarr;f(a)**
, where **f** is a void function (closure) on **a**, to be called from receiving actor's thread.
(see examples below)

## Core features
* Intuitive + lambdas: Few concepts but easy to combine, giving readable code.
* Safe concurrent access: Actor state protected behind its actor-reference.
* Non-blocking: Callbacks/lambda-continuations instead of blocking Futures.
* Typed actors: Implies compile-time type checking and no explicit message types.
* Fork/Join: Non-blocking & heterogeneous (allows sub-tasks of different type)
* Debugging: Run single-threaded or use message tracing.
* Thread lifecycle; auto-closing when done: try(gThreads=..){  ...  }
* Small but extensible:
	- Lightweight actors; < 50 bytes per actor.
	- Lightweight threads: Multiple green-threads per real thread
	- No external library dependencies (except JUnit for tests)
	- No fancy processing (no reflection, Proxy/ByteCode generation or annotations)
	- Extensible interfaces (thread, thread-factory, actor-reference..)
	- Combinable with any other libraries.

## Easy to use
Basically, an object of a type 'A', wrapped inside an actor reference (`IActorRef<A>`),
becomes an actor. The original object should not be referred directly (except from
the object itself), but by sending it messages only via its actor-reference.

Here is a **mini-tutorial**, covering all essential concepts (working code):
```java
    static void easy_to_learn(IGreenThrFactory factory)
    {
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
              Send = Basic one-way messaging: */
        refA.send(a -> a.increaseX());
        refA.send(A::increaseX); //same effect

        /* 4. Or, you can use green-threads directly;*/
        factory.newThread().execute(() ->

          /* 5. Call = Messages with callback;
                (Must itself be called from inside a
                 green-thread or actor)  */
                refA.call(
                        A::getX
                        // getX is called from the thread of refA

                        , x -> System.out.println(" got x: " + x)
                        // callback; called at my own thread
                )
        );
    }

    public static void main(String[] args) throws InterruptedException
    {
        IGreenThrFactory gThreads;
        try (gThreads = new GreenThrFactory_single(2, false))
        {
            // Inside a try-with-resources block =>
            // Will trigger automatic thread-shutdown when done..
            easy_to_learn(gThreads);
        }
    }
```

## Usage patterns

### Actors from plain Java objects
Wrapping an object inside an actor reference (`IActorRef`),
protects it against concurrent access, as long as all calls from
other threads or actors goes through this reference.

Although its recommended to extend from `ActorBase`, it is possible
to transform a plain java object into an actor..

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
        //PS. Avoid leaking mutable objects via messages between threads.
    }
```

### Actor base class
Get access to more actor functionality by extending from class `ActorBase`.
Example - Using `ActorBase.self` to generate fibonacci sequence:
```java
    public class Fibonacci extends ActorBase<Fibonacci> {

        public void fib(BigInteger a, BigInteger b
                    , Function<BigInteger, Boolean> out)
        {
            if (out.apply(a)) //output
                self().send(s -> s.fib(b, a.add(b), out));
        }

        public static void run(IGreenThrFactory factory
                    , Function<BigInteger, Boolean> output)
        {
            // 1. call 'init' to initiate reference
            // 2. send a message = asynchronous method call (lambda expression)
            new Fibonacci()
                    .init(factory)
                    .send(s -> s.fib(BigInteger.ONE, BigInteger.ONE, output));
            //Output: 1  1  2  3  5  8  13  21  34 ..
        }
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
Also, consider sending immutable objects.
Tips: guava-libraries might be useful, for example with
'com.google.common.collect.ImmutableList'.

### Become - change runtime behaviour
Behaviour can be changed by calling `ActorBase.become`;
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
    static void nonBlockingFuture(IGreenThrFactory factory) {

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
        IActorRef<ValueActor> valRef = new ActorRef<>(factory, new ValueActor());
        IActorRef<MainActor> mainRef = new ActorRef<>(factory, new MainActor());
        mainRef.send(a -> a.someCalls(31407, valRef));
        // Output:
        // correct value: 31407
        // returned value: 31407
    }
```


### Non-blocking Fork/Join
The `ForkJoin` utility class gives you fully generic, non-blocking Fork/Join.
Each call to `ForkJoin.call` or `ForkJoin.callAsync` forks a new concurrent child node (computation),
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

### Message tracing
Message tracing simplifies debugging of parallel message events.
Using a ring-buffer, send & receive events can be kept for later inspection.

Example, logging to 'MessageEventBuffer':
```java
    try (IGreenThrFactory threads = new GreenThrFactory_single(2)) {

        //Initiate exception and message-tracing:
        final MessageEventBuffer
                messageBuf = new MessageEventBuffer(200)
                .listenTo(threads);
        //Optional user-defined runtime event inspection:
        messageBuf.setEventAction(event -> eventInspect(messageBuf, event));

        //Optional log info added to normal thread message (execute):
        MessageRelay.logInfo("Thread execute");
        threads.newThread().execute(() -> log("Thread got message"));

        //Optional log info added to normal actor message (send):
        MessageRelay.logInfo("Actor send");
        new MyActor().init(threads).send(a -> someTask(1, threads));

        threads.await(60000L);
        log("\nThreads done. Buffer dump:");
        for (IMsgEvent e : messageBuf.toArray())
            log(e.info());
    }
```

Example output, demonstrating mixed Stack + Message trace (backward chain):
```
	..
	at flc.nbl_actors.core.trace.MessageRelay$Interceptor$$Lambda$10.run(Unknown Source)
	at flc.nbl_actors.core.GreenThr_single$1.run(GreenThr_single.java:76)
	Message trace:
	sent![2.1]3.1 at flc.nbl_actors.examples.MessageTrace.someTask(MessageTrace.java:38) {send A2} thread:3  :RuntimeException((NOT a real exception) Demonstrating mixed Stack + Message trace) @MessageTrace.java:27
	sent![3.1]1.2 at flc.nbl_actors.examples.MessageTrace.someTask(MessageTrace.java:38) {send A1} thread:2
```

Example output, dumping event sequence from ring-buffer:
```
    Threads done. Buffer dump:
    sent![1.1]null at flc.nbl_actors.examples.MessageTrace.main(MessageTrace.java:88) {Thread execute} thread:2
    sent![1.2]null at flc.nbl_actors.examples.MessageTrace.main(MessageTrace.java:92):MyActor {Actor send} thread:3
    sent[3.1]1.2 at flc.nbl_actors.examples.MessageTrace.someTask(MessageTrace.java:38) {send A1}
     run[3.1] thread:2
    sent[3.2]1.2 at flc.nbl_actors.examples.MessageTrace.someTask(MessageTrace.java:46) {send B1}
    sent[2.1]3.1 at flc.nbl_actors.examples.MessageTrace.someTask(MessageTrace.java:38) {send A2}
    sent[2.2]3.1 at flc.nbl_actors.examples.MessageTrace.someTask(MessageTrace.java:46) {send B2}
     run[3.2] thread:2
     run[2.1] thread:3
     run[2.1] thread:3  :RuntimeException((NOT a real exception) Demonstrating mixed Stack + Message trace) @MessageTrace.java:27
     run[2.2] thread:3
```

### MailBox utility
Use the MailBox utility for explicit queue-control and selective receive:

```java
	//Basic usage example
	final IActorRef<Act> ref = new Act().init(threads);
	Consumer<Double> mailBox;
	{
		//Explicit queue control:
		final Deque<Double> queue = new LinkedBlockingDeque<>();
		mailBox = MailBox.create(
			ref,  //link from mailBox to your actor reference
			queue::addFirst,  //add message to queue
			act -> act.receive(queue)
			  //Selective receive; actor decides 
			  //in which order to consume from queue
		);
		//queue::addFirst used to get stack/LIFO ordering
	}
	mailBox.accept(100.0);//-> to queue; actor scheduled if needed
	mailBox.accept(200.0);
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
The sender can then slow down by either:

    1. Blocking until consuming actor is ready.
        (best to avoid?)
    2. Alternatively, thread could help receiving actor.
        (if passive)
    3. Rejecting received message.
        (vital messages lost?)
    4. Message-pulling instead of passive receive.
    
Example, message-pulling:
```java
        //User defined method inside actor:
        public void pullNextMessage() {
            if (consume(pullSource.get()))
                self().send(s -> s.pullNextMessage());
        }
```

### Dynamic messaging
Messages as lambda expressions (continuations) are powerful,
since they carry algorithmic expressions, executed on receiver's behalf.
This gives you a lot of flexibility..

Example; Passing a message through a chain of actors:
```java
        static void recursive_call_chain(Iterator<IActorRef<A>> actorIt) {
            if (actorIt.hasNext()) //NB! Assumed thread-safe.
                actorIt.next()
                        .send(a -> {
                            a.gotIt();
                            //Got message! now send to next actor in chain:
                            recursive_call_chain(actorIt);
                        });
            else
                log("end of call-chain!");
        }
```

### Distributed computing?
Messaging over a network is not implemented. (..but could become an option by
serializing `IActorRef.send(java.util.function.Consumer<A> msg)`)


## More documentation..
* For more examples & code: check under src/...
* Java docs; Download and unzip doc/javadoc.zip; See index.html


## Inspiration
* "Simplicity is the ultimate sophistication". Leonardo da Vinci
* "Fools ignore complexity; pragmatists suffer it; experts avoid it; geniuses remove it." Alan Perlis (Turing Award, ALGOL)
* "The competent programmer is fully aware of the strictly limited size of his own skull." Edsger Dijkstra (Turing Award)
* "So much complexity in software comes from trying to make one thing do two things." Ryan Singer
* "The cheapest, fastest, and most reliable components are those that aren't there." Gordon Bell
* Unix Philosophy - http://en.wikipedia.org/wiki/Unix_philosophy
* How to Design a Good API & Why it Matters - http://www.infoq.com/presentations/effective-api-design
