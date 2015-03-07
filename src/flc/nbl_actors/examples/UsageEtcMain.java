/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.examples;

import flc.nbl_actors.core.*;

import java.util.*;
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

        final Supplier<Integer> pullSource;

        Throttle(Supplier<Integer> pullSource) {
            this.pullSource = pullSource;
        }

//        Flow control
//        Message-queue overflow can in general be avoided by returning feedback-messages to sending actor. The sender can then slow down by either:
//                1. Blocking until consuming actor is ready.
//                (best to avoid?)
//                2. Alternatively, thread could help receiving actor.
//                (if passive)
//                3. Rejecting received message.
//                (vital messages lost?)
//                4. Message-pulling instead of passive receive.

        public void pullNextMessage() {
            if (consume(pullSource.get())) {
                self().send(s -> s.pullNextMessage());
            }
        }

        private boolean consume(Integer value) {
            log(" got: " + value);
            return value > 0;
        }

        static void demo(IGreenThrFactory factory, int max)
                throws InterruptedException {
            log("Throttle..");
            final AtomicInteger count = new AtomicInteger(max);
            IActorRef<Throttle> ref;
            ref = new Throttle(() -> count.decrementAndGet())
                    .init(factory);
            ref.send(s -> s.pullNextMessage());
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

    @SuppressWarnings({"Convert2MethodRef", "UnusedDeclaration"})
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

    static void asyncConcept(String origString) {
        ASyncValue<String> future = new ASyncValue<>();
        log(" orig: " + origString);
        future.result(s -> { //interface IASync<T>
            log("  got: " + s);
            assert origString.equals(s);
        });
        future.accept(origString);
        future.update(v -> v + ", added to string");
        future.result(s -> log("  got2: " + s));
    }

    @SuppressWarnings("Convert2MethodRef")
    static void nonBlockingFuture(IGreenThrFactory factory) {

        class ValueActor {
            final ASyncValue<Integer> async = new ASyncValue<>();

            IASync<Integer> getAsync() {
                return async;
            }
        }

        class MainActor extends ActorBase<MainActor> {
            int gotValue, gotValue2;

            void someCalls(int correct, IActorRef<ValueActor> ref) {
                ref
                        .call(a -> a.getAsync())
                        .result(ret -> { //In MainActor thread:
                            gotValue = ret;
                            log(" correct value: " + correct);
                            log("returned value: " + ret);
                            assert ret == correct;
                        });
                //equivalent to call, using send:
                ref.send(a -> a.getAsync()
                        .result(ret -> self()
                                .send(b -> {
                                    gotValue2 = ret;
                                    log("returned value2: " + ret);
                                    assert ret == correct;
                                })
                        )
                );
                ref.send(valueActor
                        -> valueActor.async.accept(correct));
            }
        }
        IActorRef<ValueActor> valRef = new ActorRef<>(factory, new ValueActor());
        IActorRef<MainActor> mainRef = new MainActor()
                .init(factory);
        mainRef.send(a -> a.someCalls(31407, valRef));
        // Output:
        // correct value: 31407
        // returned value: 31407
    }

    @SuppressWarnings({"CodeBlock2Expr", "Convert2MethodRef"})
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

    @SuppressWarnings("CodeBlock2Expr")
    static void listenToMultipleFactories(IGreenThrFactory f1)
    {
        //-- Two thread-factories with some work:
        IGreenThrFactory f2 = new GreenThr_zero();
        f1.newThread().execute(() -> {
            System.out.println(" thr 1");
        });
        f2.newThread().execute(() -> {
            System.out.println(" thr 2");
        });

        //-- Listen for all tasks to finish:
        new ActiveCount()
                .listenTo(f1, f2)
                .setActiveListener(b -> {
                    if (!b)
                        System.out.println(" listenToMultipleFactories .. done");
                });
    }

    private static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    static void actorMemoryUse(int numActors, boolean isPureObj) {
        class PureObj {
//            int val;
        }
        class Act extends ActorBase<Act> {
//            int val;
        }
        long mem1, mem2;
        if (isPureObj) {
            PureObj[] objArray = new PureObj[numActors];
            mem1 = usedMemory();
            for (int i = 0; i < objArray.length; i++) {
                objArray[i] = new PureObj();
            }
            mem2 = usedMemory();
        } else {
            IGreenThr thr = Runnable::run;
            IActorRef[] refArray = new IActorRef[numActors];
            mem1 = usedMemory();
            for (int ix = 0; ix < refArray.length; ix++) {
                refArray[ix] = new Act().initThread(thr);
            }
            mem2 = usedMemory();
        }
        long dMem = mem2 - mem1;
        log((isPureObj ? "#objects: " : "#actors: ") + numActors + "   kBytes: " + (dMem / 1024));
        double byte_actor = (double) dMem / (double) numActors;
        log("   bytes / instance:  " + byte_actor);
		System.gc();
    }

    public static void main(String[] args) throws InterruptedException {
//        actorMemoryUse(100000, false);
//        actorMemoryUse(100000, true);
        actorMemoryUse(100000, false);
        actorMemoryUse(100000, true);
        try (IGreenThrFactory f = new GreenThrFactory_single(2, false)) {
            listenToMultipleFactories(f);
            easy_to_learn(f);
            nonBlockingFuture(f);
            new CallChain().demo(f);
            f.await(1000);
            Throttle.demo(f, 7);
            cancelOp(f);
            minimumExample(f);
        }
        asyncConcept("test string");
    }

}
