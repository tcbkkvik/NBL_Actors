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

    @SuppressWarnings({"UnusedDeclaration", "Convert2MethodRef"})
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        try (IGreenThrFactory factory = new GreenThrFactory_single(4)) {
            splitMergeDemo(factory,
                    "This is a test-string. Lets see if it comes back the same..");
        }
    }

}
