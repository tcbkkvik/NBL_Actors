/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import flc.nbl_actors.core.IGreenThr;

import java.util.function.Consumer;

/**
 * A simple Actor for single-typed or untyped messages.
 * <p> Date: 04.08.14
 * </p>
 *
 * @author Tor C Bekkvik
 */
public class SimpleActorRef<T> {
    private final IGreenThr thr;
    private final Consumer<T> actorImpl;

    public SimpleActorRef(IGreenThr thr, Consumer<T> actorImpl) {
        this.thr = thr;
        this.actorImpl = actorImpl;
    }

    public void send(T msg) {
        thr.execute(() -> actorImpl.accept(msg));
    }

    private static void log(Object o) {
        System.out.println(o);
    }

    public static void main(String[] args) {
        IGreenThr thread = Runnable::run;
        SimpleActorRef<Object> untypedRef = new SimpleActorRef<>(thread, new Consumer<Object>() {
            Object prev;

            @Override
            public void accept(Object obj) {
                log(" prevObj: " + prev);
                log("   newObj: " + obj);
                prev = obj;
            }
        });
        SimpleActorRef<Double> sumRef = new SimpleActorRef<>(thread, new Consumer<Double>() {
            double value;

            @Override
            public void accept(Double v) {
                value += v;
                log(String.format(" + %f = %f", v, value));
            }
        });
        untypedRef.send("test");
        untypedRef.send(123);
        sumRef.send(5.);
        sumRef.send(15.);
    }
}
