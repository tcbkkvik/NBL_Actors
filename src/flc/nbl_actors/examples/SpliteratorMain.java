/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.examples;

import flc.nbl_actors.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Spliterator;
import java.util.concurrent.ExecutionException;

/**
 * Example of {@link Spliterator} usage.
 * Also, shows how the simpler {@link ASyncValue} can be
 * used instead of {@link ForkJoin}, if the fork/join is only binary,
 * Date: 18.08.14
 *
 * @author Tor C Bekkvik
 */
public class SpliteratorMain {
    /**
     * Parallel sum
     *
     * @param factory thread factory
     * @param stream  values
     * @return resulting sum of values
     */
    static IASync<Float> splitSum(IGreenThrFactory factory, Spliterator<Float> stream) {
        final ASyncValue<Float> ret = new ASyncValue<>(0f);
        final Spliterator<Float> splitStream =
                stream.estimateSize() > 10 ? stream.trySplit() : null;
        if (splitStream != null) {
            factory.newThread()
                    .call(() -> splitSum(factory, splitStream)) //in parallel
                    .result(partSum -> {
                        ret.update(sum -> sum + partSum); //join sum
                        ret.accept();
                    });
        } else ret.accept();
        stream.forEachRemaining(val -> ret.update(sum -> sum + val));
        return ret;//complete after ret.accept() called
    }

    static List<Float> randomValues(int length) {
        List<Float> list = new ArrayList<>();
        float sum = 0;
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            float rnd = random.nextFloat();
            sum += rnd;
            list.add(rnd);
        }
        System.out.println("tstSplitSum      sum = " + sum);
        return list;
    }

    @SuppressWarnings({"UnusedDeclaration", "Convert2MethodRef"})
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        try (IGreenThrFactory factory = new GreenThrFactory_single(4, false)) {
            factory.newThread().execute(() ->
                    splitSum(factory, randomValues(37).spliterator())
                            .result(s -> System.out.println("tstSplitSum splitSum = " + s))
            );
        }
    }
}
