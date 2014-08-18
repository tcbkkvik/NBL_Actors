/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.examples;

import flc.nbl_actors.core.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

/**
 * Date: 24.07.14
 *
 * @author Tor C Bekkvik
 */
public class ForkJoinExample {

    static String[] strSplit(String str, int no) {
        String[] arr = new String[no];
        int pos = 0;
        for (int ix = 0; ix < no; ix++) {
            int step = (int) Math.ceil((str.length() - pos) / (no - ix));
            arr[ix] = str.substring(pos, pos + step);
            pos += step;
        }
        return arr;
    }

    static String strJoin(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (String s : arr)
            sb.append(s);
        return sb.toString();
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    static IASync<String> splitMerge2(IGreenThrFactory tf, String original) {
        if (original.length() < 6)
            return new ASyncDirect<>(original);
        final String[] arr1 = strSplit(original, 5); //handles # > 2
        final String[] arr2 = new String[arr1.length];
        final ForkJoin<String> fj = new ForkJoin<>();
        for (int i = 0; i < arr1.length; i++) {
            final int ix = i;
            fj.callAsync(tf.newThread(),
                    () -> splitMerge2(tf, arr1[ix]),
                    s -> arr2[ix] = s
            );
        }
        return fj.resultAsync(s -> fj.setValue(strJoin(arr2)));
    }

    // Non-blocking Fork/Join example:
    // Recursively split a string to left/right halves until small enough (Fork),
    // and then merge the strings back together (Join).
    // Future result string should be equal to original.
    static IASync<String> splitMerge(IGreenThrFactory tf, String original) {
        if (original.length() < 6) return new ASyncDirect<>(original);
        ForkJoin<String> fj = new ForkJoin<>("");
        int count = 0;
        for (String str : strSplit(original, 2)) { //only handles binary split
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
        IGreenThr thr = factory.newThread();
        log("\nAlgorithm 1 ..");
        thr.execute(
                () -> splitMerge(factory, origString)
                        .result(res -> {
                            log("Resulting string:\n " + res);
                            assert origString.equals(res);
                            latch.countDown();
                        })
        );
        latch.await();
        log("\nAlgorithm 2 ..");
        thr.execute(
                () -> splitMerge2(factory, origString)
                        .result(res -> {
                            log("Resulting string:\n " + res);
                            assert origString.equals(res);
                            latch.countDown();
                        })
        );
    }

    static void log(Object o) {
        System.out.println(o);
    }

    @SuppressWarnings({"UnusedDeclaration", "Convert2MethodRef"})
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        try (IGreenThrFactory factory = new GreenThrFactory_single(4, false)) {
            splitMergeDemo(factory,
                    "This is a test-string. Lets see if it comes back the same..");
        }
    }

}
