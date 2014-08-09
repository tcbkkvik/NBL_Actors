/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.examples;

import flc.lambdactor.core.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Date: 09.08.14
 *
 * @author Tor C Bekkvik
 */
public class UsageEtcMain {

    static void minimumExample(IGreenThrFactory factory) {
        log("minimumExample..");
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

    static void actorBaseExample(IGreenThrFactory factory) {
        log("actorBaseExample..");
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

    static void log(Object o) {
        System.out.println(o);
    }

    static void cancelOp(IGreenThrFactory factory)
            throws InterruptedException {
        log("addingCancelFeature..");
        class Act extends ActorBase<Act> {
            void doWork() {
                log("  ..doWork called");
            }
        }
//        CountDownLatch latch = new CountDownLatch(2);
        IActorRef<Act> ref = new Act().init(factory);
//        This library is small, with a simple focus on core Actor features,
//                but it is intended for combination with other useful libraries etc.
//        for instance with java.util.concurrent.atomic.*.
//        Example;
//        Making it possible to cancel an operation:
        final AtomicBoolean isCancel = new AtomicBoolean(false);
        ref.send(a -> {
            if (!isCancel.get()) {
                //might be cancelled after send;
                a.doWork();
            }
        });
        Thread.sleep(10);
        //..might be called later.. :
        isCancel.set(true);
        // -----------------------------------------------
        //noinspection CodeBlock2Expr
        ref.send(a -> {
            log(" final send -> isCancel = " + isCancel.get());
//            latch.countDown();
        });
//        latch.await();
    }

    @SuppressWarnings("Convert2MethodRef")
    static class Throttle extends ActorBase<Throttle> {
        static class ReceiveActor {
            void consume(int val) {
                log(" got: " + val);
            }
        }

        final Supplier<Integer> pullSource;
        final IActorRef<ReceiveActor> destination;

        Throttle(Supplier<Integer> pullSource, IActorRef<ReceiveActor> destination) {
            this.pullSource = pullSource;
            this.destination = destination;
        }

        //A general way to avoid message queue overflow,
        //is via reply-messages to sending actor.
        //The sending actor can then slow down by:
        //
        //    - Blocking until consuming actor is ready. (avoid blocking)
        //    - Rejecting received message. (important message lost?)
        //    - Pulling messages instead of passive receive.
        //Example:
        public void pullNextMessage() {
            Integer val = pullSource.get();
            if (val == null) return; //stop
            destination.send(d -> {
                d.consume(val);
                // Feedback "loop" here: (ACK signal)
                self().send(s -> s.pullNextMessage());
            });
        }

        static void demo(IGreenThrFactory factory, int max)
                throws InterruptedException {
            log("Throttle..");
            IActorRef<ReceiveActor> out = new ActorRef<>(factory, new ReceiveActor());
            final AtomicInteger count = new AtomicInteger();
            Supplier<Integer> in = () -> {
                int no = count.incrementAndGet();
                return (Integer) (no < max ? no : null);
            };
            IActorRef<Throttle> ref = new Throttle(in, out).init(factory);
            ref.send(s -> {
                s.pullNextMessage();//send at least 1 "token", staring loop
                s.pullNextMessage();//may send more to "fill pipe"..
                s.pullNextMessage();
            });
            CountDownLatch latch = new CountDownLatch(1);
            factory.setEmptyListener(() -> latch.countDown());
            latch.await();
            log("..done");
        }
    }

    static class CallChain {
        class A extends ActorBase<A> {
            final int id;

            A(int id) {
                this.id = id;
            }

            void gotIt() {
                log(" actor " + id);
            }
        }

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

        void demo(IGreenThrFactory f) {
            log("chained_messages..");
            final List<IActorRef<A>> actors = new ArrayList<>();
            for (A a : new A[]{new A(0), new A(1), new A(2)})
                actors.add(a.init(f));
            recursive_call_chain(actors.iterator());
        }

    }

    @SuppressWarnings("Convert2MethodRef")
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

        @SuppressWarnings("UnusedDeclaration")
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

    public static void main(String[] args) throws InterruptedException {
        try (IGreenThrFactory f = new GreenThr_single(false)) {
            new CallChain().demo(f);
            Throttle.demo(f, 7);
            cancelOp(f);
            minimumExample(f);
            actorBaseExample(f);
        }
    }

}
