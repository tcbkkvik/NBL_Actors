Lambda-Actor
============

A lightweight, easy to learn, flexible [Actor](http://en.wikipedia.org/wiki/Actor_model)
concurrency API  based on Java 8 lambdas.

## Actors from plain Java objects
Wrapping an object inside an actor reference (`IActorRef`),
protects it against concurrent access, as long as all calls from
other threads or actors goes through this reference.
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
                factory,
                new PlainObj());
        //send a message = asynchronous method call (lambda expression):
        ref.send(a -> a.someMethod(34));
        //PS. Avoid leaking shared-mutable-access via message passing.
    }
```

## Actor base class
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

## Become - runtime behaviour change
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

## Non-blocking 'futures'
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


## Non-blocking Fork/Join
Example;
Recursively split a string into left/right until small enough (Fork),
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