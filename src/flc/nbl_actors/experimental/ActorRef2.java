/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import flc.nbl_actors.core.*;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;


/**
 * Date: 30.07.14
 *
 * @author Tor C Bekkvik
 */
public class ActorRef2<A> extends ActorRef<A> {
    private final long ID = currID.incrementAndGet();
    private final IActorRef parentRef;
    private volatile IActorRef fromRef;

    private static final ThreadLocal<IActorRef> currRef = new ThreadLocal<>();
    private static final AtomicLong currID = new AtomicLong(0);

    /**
     * @param factory thread factory
     * @param imp     implementation instance
     */
    public ActorRef2(IGreenThrFactory factory, A imp) {
        super(factory, imp);
        parentRef = currRef.get();
    }

    /**
     * @param imp implementation instance
     * @param thr green-thread
     */
    public ActorRef2(A imp, IGreenThr thr) {
        super(imp, thr);
        parentRef = currRef.get();
    }

    /**
     * Get actor ID (number incremented for each new ActorRef instance).
     *
     * @return ID
     */
    public long getID() {
        return ID;
    }

    /**
     * Get parent actor; Who, if an actor, constructed this reference.
     *
     * @return parent ref, or null if not created from an actor.
     */
    public IActorRef parentRef() {
        return parentRef;
    }

    /**
     * Get current 'from' actor; Which actor (if any) sent current message being processed.
     *
     * @return from ref, or null if current message not sent from an actor.
     */
    public IActorRef fromRef() {
        return fromRef;
    }

    @Override
    public void send(final Consumer<A> msg) {
        final IActorRef from = currRef.get();
        thr.execute(() -> {
            fromRef = from;
            currRef.set(ActorRef2.this);
            try {
                msg.accept(getImpl());
            } catch (RuntimeException ex) {
                super.exceptHd.accept(ex);
            }
            currRef.set(null);
        });
    }

}
