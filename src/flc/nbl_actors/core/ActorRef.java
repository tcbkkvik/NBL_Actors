/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.util.Objects;
import java.util.function.*;


/**
 * Actor reference implementation.
 * Date: 18.07.13
 *
 * @author Tor C Bekkvik
 */
public class ActorRef<A> implements IActorRef<A> {
    protected final IGreenThr thr;
    private volatile A impl;
    protected volatile Consumer<RuntimeException>
            exceptHd = RuntimeException::printStackTrace;

    /**
     * @param factory thread factory
     * @param imp     implementation instance
     */
    public ActorRef(IGreenThrFactory factory, A imp) {
        this(imp, factory.newThread());
    }

    /**
     * @param imp implementation instance
     */
    public ActorRef(A imp) {
        this(imp, ThreadContext.get().getFactory().newThread());
    }

    /**
     * @param imp implementation instance
     * @param thr green-thread
     */
    public ActorRef(A imp, IGreenThr thr) {
        this.thr = thr;
        become(imp);
    }

    /**
     * Become; Change implementation. Called from implementation object.
     *
     * @param newImpl a new actor implementation.
     */
    protected void become(A newImpl) {
        impl = newImpl;
    }

    protected A getImpl() {
        return impl;
    }

    @Override
    public void send(final Consumer<A> msg) {
        thr.execute(() -> {
            try {
                msg.accept(getImpl());
            } catch (RuntimeException ex) {
                exceptHd.accept(ex);
            }
        });
    }

    /**
     * Set exception handler
     *
     * @param ex handler
     */
    public void setExceptionHandler(Consumer<RuntimeException> ex) {
        this.exceptHd = Objects.requireNonNull(ex);
    }
}
