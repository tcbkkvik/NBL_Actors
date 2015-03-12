/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import flc.nbl_actors.core.*;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Simple ActorBase for single-typed messages
 * <p>Date: 11.03.2015
 * </p>
 *
 * @author Tor C Bekkvik
 */
public abstract class SimpleActorBase<T> implements Consumer<T> {
    private Ref<T> selfRef;

    /**
     * Reference constructor.
     * <p>Attach a green-thread to this actor instance.
     * To avoid shared mutable access, avoid direct method calls on this object from other threads.
     * Instead, access it via returned actor reference.
     * </p>
     *
     * @param thr green thread
     * @return new reference
     */
    public Consumer<T> initThread(IGreenThr thr) {
        setSelfRef(new Ref<>(thr, this));
        return selfRef;
    }

    /**
     * Reference constructor. Equivalent to {@link #initThread(IGreenThr)}
     * with {@code factory.newThread()}
     *
     * @param threads thread factory
     * @return new reference
     */
    public Consumer<T> init(IGreenThrFactory threads) {
        return initThread(threads.newThread());
    }

    /**
     * Self reference - get the public reference to this object.
     *
     * @return my reference
     */
    public Consumer<T> self() {
        return selfRef;
    }

    /**
     * Become - update my {@link #self()} reference to a new implementation.
     * (No explicit initiation via {@link #init(IGreenThrFactory)} or {@link #initThread(IGreenThr)}
     * is needed on the new instance)
     *
     * @param newImpl new implementation.
     * @throws IllegalArgumentException if newImpl already a self-reference != self()
     */
    protected void become(final Consumer<T> newImpl) {
        if (newImpl instanceof SimpleActorBase) {
            //noinspection unchecked
            ((SimpleActorBase) newImpl).setSelfRef(selfRef);
        }
        selfRef.actorImpl = newImpl;
    }

    private void setSelfRef(Ref<T> ref) {
        if (selfRef == null)
            selfRef = ref;
        else if (selfRef != ref)
            throw new IllegalArgumentException("Has another self-reference");
    }

    public static class Ref<T> implements Consumer<T> {
        private final IGreenThr thr;
        protected volatile Consumer<T> actorImpl;

        public Ref(IGreenThr thr, Consumer<T> actorImpl) {
            this.thr = Objects.requireNonNull(thr);
            this.actorImpl = Objects.requireNonNull(actorImpl);
        }

        public void send(T msg) {
            thr.execute(() -> actorImpl.accept(msg));
        }

        @Override
        public void accept(T t) {
            send(t);
        }
    }

    public static void main(String[] args) {
        try (IGreenThrFactory threads = new GreenThr_zero()) {
            //a minimal example:
            new SimpleActorBase<Double>() {
                @Override
                public void accept(Double v) {
                    System.out.println(" val: " + v);
                    self().accept(v - 1);
                    if (v < 1)
                        become(System.out::println);
                }
            }
                    .init(threads)
                    .accept(2.7);
        }
    }
}
