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
        final int val_facit = sumArr(numbers);
        try (GreenThrFactory_single threads = new GreenThrFactory_single(4)) {
            threads.reverseOrder(true);
            IActorRef<ParallelSum> ref = new ParallelSum().init(threads);
            log(" Parallel sum, facit: " + val_facit);
            ref.send(s ->
                    s.sum2(numbers).result(v -> {
                        log(" Parallel sum, non-static version: " + v);
//                assert val_facit == v;
                    })
            );
            threads.newThread().execute(() ->
                    ParallelSum.sum(numbers).result(v -> {
                        log(" Parallel sum, static fork/join  : " + v);
//                assert val_facit == v;
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

    public static void main(String[] args) throws InterruptedException {
        asyncConcept("test string");
        tstParallelSum();
        tstFJ();
    }

}
