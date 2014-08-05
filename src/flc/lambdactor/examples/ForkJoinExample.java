/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.examples;

import flc.lambdactor.core.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Date: 24.07.14
 *
 * @author Tor C Bekkvik
 */
public class ForkJoinExample {

    /**
     * Recursive concurrent binary tree traversal.
     * <p>Returns future number of leaf nodes in this subtree (2^depth).
     * </p>
     *
     * @param tf    thread factory
     * @param depth tree depth
     * @return total number of leaf nodes
     */
    public static IASync<Integer> traverse(IGreenThrFactory tf, int depth) {
        if (depth < 1)
            return new ASyncDirect<>(1); //leaf node (value += 1)
        ForkJoin<Integer> fj = new ForkJoin<>(0);
        for (int i = 0; i < 2; ++i) { //left + right child node
            fj.callAsync(tf.newThread(),
                    () -> traverse(tf, depth - 1),
                    (value, returned) -> value + returned
            );
        }
        return fj.resultAsync(
                r -> log(space(depth * 2) + depth + "   #leafs: " + r)
        );
    }

    static String[] splitLeftRight(String s) {
        int pos = s.length() / 2;
        return new String[]{s.substring(0, pos), s.substring(pos)};
    }


    // Non-blocking Fork/Join example:
    // Recursively split a string to left/right halves until small enough (Fork),
    // and then merge the strings back together (Join).
    // Future result string should be equal to original.
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

    static void log(Object o) {
        System.out.println(o);
    }

    static String space(int len) {
        return "                                  ".substring(0, len);
    }

    @SuppressWarnings({"UnusedDeclaration", "Convert2MethodRef"})
    public static void main(String[] args) throws InterruptedException, ExecutionException {
//        String[] arr = splitIn2("abc123");
//        String[] arr2 = splitIn2("abc12");
//        String[] arr3 = splitIn2("a");
//        String[] arr4 = splitIn2("");
        try (IGreenThrFactory factory = new GreenThrFactory_single(4)) {
            splitMergeDemo(factory,
                    "This is a test-string. Lets see if it comes back the same..");
            final int depth = 3;
            final int correct = 1 << depth;
            log("forkJoin 2^" + depth);
            AtomicInteger ai = new AtomicInteger(0);
            CompletableFuture<Integer> future = new CompletableFuture<>();
            factory.newThread().execute(
                    () -> traverse(factory, depth)
                            .result(sum -> {
                                boolean ok = sum == correct;
                                log("    = " + sum + "   ok:" + ok);
                                future.complete(sum);
                                ai.set(sum);
                            })
            );
            int sum = future.get();
            int sum2 = ai.get();
            assert correct == sum;
            assert correct == sum2;
        }
    }

}
