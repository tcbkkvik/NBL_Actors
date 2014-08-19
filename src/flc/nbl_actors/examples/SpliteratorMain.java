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
import java.util.concurrent.*;

/**
 * Example; Parallel {@link Spliterator} processing using {@link IGreenThr}.
 * Date: 18.08.14
 *
 * @author Tor C Bekkvik
 */
public class SpliteratorMain {
    static String str3(float val) {
        return String.format(Locale.UK, " %.3f", val);
    }

    static List<Float> randomValues(int length) {
        float sum = 0;
        StringBuilder sb = new StringBuilder();
        List<Float> list = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            float rnd = random.nextFloat();
            list.add(rnd);
            sum += rnd;
            sb.append(str3(rnd));
        }
        sb.insert(0, " sum: " + sum + "   ");
        System.out.println(sb);
        return list;
    }

    public interface SpConsumer<T> {
        int splitSize();

        void consume(Spliterator<T> data);
    }

    static <T> void parallel(IGreenThrFactory factory, Spliterator<T> data, SpConsumer<T> consumer) {
        Spliterator<T> data2 = data.estimateSize() > consumer.splitSize() ? data.trySplit() : null;
        if (data2 != null) {
            factory.newThread().execute(() -> parallel(factory, data, consumer));
            parallel(factory, data2, consumer);
        } else
            consumer.consume(data);
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        class Sum implements SpConsumer<Float> {
            float value;

            float getValue() {
                return value;
            }

            @Override
            public int splitSize() {
                return 5;
            }

            @Override
            public void consume(Spliterator<Float> data) {
                float[] val = {0};
                data.forEachRemaining(v -> val[0] += v);
                synchronized (this) {
                    value += val[0];
                }
            }
        }
        try (IGreenThrFactory factory = new GreenThrFactory_single(4, false)) {
            final Sum sum = new Sum();
            parallel(factory, randomValues(20).spliterator(), sum);
            CompletableFuture<Float> result = new CompletableFuture<>();
            factory.setEmptyListener(() -> {
                result.complete(sum.getValue());
            });
            System.out.println(" sum: " + result.get());
        }
    }
}
