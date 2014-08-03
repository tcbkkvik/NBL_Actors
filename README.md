Lambda-Actor
============

A lightweight, easy to learn, flexible [Actor](http://en.wikipedia.org/wiki/Actor_model)
concurrency API  based on Java 8 lambdas.

Minimum example;
A plain java object wrapped in an actor reference (`IActorRef`)
with a new thread implicitly given from a  green-thread factory.
To protect against concurrent access, all methods of the object should then
be called through the actor reference:
```java
    static void minimumExample(IGreenThrFactory factory) {
        class PlainObj {
            public void someMethod(double value) {
                System.out.println("received value: " + value);
            }
        }
        //Wrap object in an actor reference:
        IActorRef<PlainObj> ref = new ActorRef<>(factory, new PlainObj());
        //send a message = asynchronous method call (lambda expression):
        ref.send(a -> a.someMethod(34));
    }
```

Example using `ActorBase`;
Access more actor functionality by inheriting from the `ActorBase` base class:
```java
    static void actorBaseExample(IGreenThrFactory factory) {
        class Impl extends ActorBase<Impl> {
            void done(String message) {
                System.out.println(message + ": done!");
            }

            void receive(String message) {
                this.self().send(i -> i.done(message));
            }
        }
        //call 'init' to initiate reference:
        IActorRef<Impl> ref = new Impl().init(factory);
        //send a message = asynchronous method call (lambda expression):
        ref.send(a -> a.receive("do it!"));
    }
```

Example using `ForkJoin`;
Recursively split a string into left/right until small enough (Fork),
and then merge the strings back together (Join):
(Forms a binary, concurrent and non-blocking computation tree)
```java
    static IASync<String> splitMerge(IGreenThrFactory tf, String string) {
        if (string.length() < 6) return new ASyncDirect<>(string);
        ForkJoin<String> fj = new ForkJoin<>("");
        int count = 0;
        for (String str : splitLeftRight(string)) {
            final boolean isFirst = count++ == 0;
            fj.callAsync(tf.newThread()
                    , () -> splitMerge(tf, str)//split string
                    , (val, ret) -> isFirst ? ret + val : val + ret
                    //merge strings again (ForkJoin result updated)
            );
        }
        return fj.resultAsync();
    }

    static void splitMergeDemo(IGreenThrFactory factory, String origString)
            throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        log("Original string:\n " + origString);
        factory.newThread().execute(
                () -> splitMerge(factory, origString)
                        .result(res -> {
                            log("Resulting string:\n " + res);
                            assert origString.equals(res);
                            latch.countDown();
                        })
        );
        latch.await();
    }
```