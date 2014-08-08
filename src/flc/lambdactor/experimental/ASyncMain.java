/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.experimental;

import flc.lambdactor.core.*;

import java.util.List;

/**
 * Date: 24.09.13
 *
 * @author Tor C Bekkvik
 */
public class ASyncMain {
    static Integer sumArr(List<Integer> arr) {
        return arr.stream().reduce((u, v) -> u + v).get();
    }

    static void log(Object o) {
        System.out.println(o);
    }

    /**
     * Typical advanced ASync usage: Fork/Join
     */
    static class ParallelSum extends ActorBase<ParallelSum> {
        //stateless variant:
        public static IASync<Integer> sum(List<Integer> arr) {
            if (arr.size() < 6)
                return new ASyncDirect<>(sumArr(arr));
            final ForkJoin<Integer> fj = new ForkJoin<>(0);
            for (List<Integer> sub : Utils.split(arr)) {
                fj.callAsync(newThread()
                        , () -> sum(sub)
                        , (s, v) -> s + v
                );
            }
            return fj.resultAsync();
        }

        //full actor reference encapsulation (needed if stateful <=> mutable object state)
        public IASync<Integer> sum2(List<Integer> arr) {
            if (arr.size() < 6)
                return new ASyncDirect<>(sumArr(arr));
            final ForkJoin<Integer> fj = newForkJoin(0);
            for (List<Integer> sub : Utils.split(arr)) {
                fj.callAsync(new ParallelSum().init()
                        , ai -> ai.sum2(sub)
                        , (s, v) -> s + v
                );
            }
            return fj.resultAsync();
        }
    }

    @SuppressWarnings("CodeBlock2Expr")
    static void tstParallelSum() throws InterruptedException {
        final List<Integer> numbers = Utils.randomIntegers(10);
        final int val_orig = sumArr(numbers);
        try (GreenThrFactory_single threads = new GreenThrFactory_single(4)) {
            threads.reverseOrder(true);
            IActorRef<ParallelSum> ref = new ParallelSum().init(threads);
            log(" Parallel sum, correct: " + val_orig);
            ref.send(s ->
                    s.sum2(numbers).result(v -> {
                        log(" Parallel sum, non-static version: " + v);
//                assert val_orig == v;
                    })
            );
            threads.newThread().execute(() ->
                    ParallelSum.sum(numbers).result(v -> {
                        log(" Parallel sum, static fork/join  : " + v);
//                assert val_orig == v;
                    })
            );
        }
    }

    static void tstFJ() throws InterruptedException { //dummy calls
        IGreenThrFactory factory = new GreenThr_zero();
        class FJ extends ActorBase<FJ> {
            void test(int n) {
                if (n > 0) newForkJoin(n).call(self(), () -> {}, () -> {});
            }
        }
        IGreenThr thr = factory.newThread();
        new FJ().init(factory).send(a -> a.test(3));
        IActorRef<String> ref = new ActorRef<>("string-actor", thr);
        thr.execute(() -> {
            ForkJoin<String> fj = new ForkJoin<>();
            fj.callAsync(thr, () -> new ASyncDirect<>("thr"), s -> {});
            fj.callAsync(ref, a -> new ASyncDirect<>(" 1"), s -> {});
            fj.callAsync(ref, a -> new ASyncDirect<>(" 2"), (s, t) -> s + t);
        });
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

    @SuppressWarnings({"CodeBlock2Expr", "Convert2MethodRef"})
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

    public static void main(String[] args) throws InterruptedException {
        try (IGreenThrFactory f = new GreenThr_zero()) {
            f.newThread()
                    .execute(() -> easy_to_learn(f));
            nonBlockingFuture(f.newThread());
        }
        asyncConcept("test string");
        tstParallelSum();
        tstFJ();
    }

}
