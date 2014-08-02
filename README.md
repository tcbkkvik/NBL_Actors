Lambda-Actor
============

A lightweight, easy to learn, flexible [Actor](http://en.wikipedia.org/wiki/Actor_model)
concurrency API  based on Java 8 lambdas.

Minimum example;
A plain java object wrapped in an actor reference (IActorRef)
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

Example;
Access more actor functionality by inheriting from the 'ActorBase' base class:
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
