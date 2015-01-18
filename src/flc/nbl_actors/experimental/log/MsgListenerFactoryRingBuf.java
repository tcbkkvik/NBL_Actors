/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental.log;


import java.util.*;
import java.util.function.Consumer;

/**
 * Default message event logger, where events are added to a single synchronized ring-buffer.
 * Multiple calls to {@link #forkListener()} returns the same logger instance.
 * <p>Date 16.01.2015.
 * </p>
 *
 * @author Tor C Bekkvik
 */
public class MsgListenerFactoryRingBuf implements IMsgListenerFactory, Consumer<IMsgEvent> {

    private final DequeRingBuffer<IMsgEvent> ring = new DequeRingBuffer<>();
    private Consumer<IMsgEvent> logChain;

    /**
     * @param maxSize  Max #events stored in ring-buffer
     * @param logChain Next in logger chain, or null.
     */
    public MsgListenerFactoryRingBuf(int maxSize, Consumer<IMsgEvent> logChain) {
        ring.setMaxBufSize(maxSize);
        this.logChain = logChain;
    }

    /**
     * Set max ring buffer size
     *
     * @param maxSize max size. size=0 means unlimited
     */
    public void setMaxBufSize(int maxSize) {
        ring.setMaxBufSize(maxSize);
    }

    /**
     * Set next event consumer in log-chain
     *
     * @param logChain event consumer
     */
    public void setLogChain(Consumer<IMsgEvent> logChain) {
        this.logChain = logChain;
    }

    @Override
    public void accept(IMsgEvent event) {
        if (logChain != null)
            logChain.accept(event);
        ring.add(event);
    }

    @Override
    public Consumer<IMsgEvent> forkListener() {
        return this;
    }

    /**
     * Dump all events
     *
     * @param dst destination
     */
    public void dump(Consumer<IMsgEvent> dst) {
        ring.dump(dst);
    }

    /**
     * Retrieves, but does not remove, the last element (most recently added)
     *
     * @return last event, or null if buffer is empty
     */
    public IMsgEvent peekLast() {
        return ring.peekLast();
    }

    /**
     * Get message trace.
     *
     * @param last Last message to be traced from.
     * @return List of messages, found by backward tracing
     */
    public List<IMsgEvent> getMessageTrace(IMsgEvent last) {
        final List<IMsgEvent> list = new LinkedList<>();
        if (last == null) return list;
        MsgSent sent;
        if (last instanceof MsgReceived) {
            MsgReceived rc = (MsgReceived) last;
            sent = rc.sent;
        } else {
            sent = (MsgSent) last;
        }
        list.add(sent);
        synchronized (ring) {
            Iterator<IMsgEvent> it = ring.descendingIterator();
            MsgId prev = sent.idParent;
            while (it.hasNext()) {
                IMsgEvent rec = it.next();
                if (rec instanceof MsgSent) {
                    sent = (MsgSent) rec;
                    if (sent.id().equals(prev)) {
                        prev = sent.idParent;
                        list.add(sent);
                    }
                }
            }
        }
        return list;
    }

    //todo unit tests
}
