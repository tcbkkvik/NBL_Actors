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

    private final RingBuffer<IMsgEvent> ring = new RingBuffer<>();
    private final Consumer<IMsgEvent> logChain;

    /**
     * @param maxRingSize Max #events stored in ring-buffer
     * @param chain       Next in logger chain, or null.
     */
    public MsgListenerFactoryRingBuf(int maxRingSize, Consumer<IMsgEvent> chain) {
        ring.setMaxBufSize(maxRingSize);
        logChain = chain;
    }

    public MessageRelay makeMessageRelay() {
        return new MessageRelay(this);
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
     * Get last (newest) event from ring-buffer.
     *
     * @return last event.
     */
    public IMsgEvent getLastEvent() {
        return ring.lastRecord();
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
            sent = rc.sendEvent;
        } else {
            sent = (MsgSent) last;
        }
        list.add(sent);
        synchronized (ring) {
            Iterator<IMsgEvent> it = ring.buffer.descendingIterator();
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

    static class RingBuffer<T> {
        private final Deque<T> buffer = new ArrayDeque<>();
        private int maxBufSize = 100;

        /**
         * set max buffer size
         *
         * @param maxBufSize max size. size=0 means unlimited
         * @return this
         */
        public synchronized RingBuffer<T> setMaxBufSize(int maxBufSize) {
            this.maxBufSize = maxBufSize;
            return this;
        }

        /**
         * Append to ring buffer
         *
         * @param record item added
         */
        public synchronized void add(T record) {
            buffer.add(record);
            if (maxBufSize > 0)
                while (buffer.size() > maxBufSize) {
                    buffer.poll();
                }
        }

        /**
         * Get most recently added element from buffer
         *
         * @return element
         */
        public synchronized T lastRecord() {
            return buffer.isEmpty() ? null : buffer.getLast();
        }

        /**
         * Dump buffer to consumer
         *
         * @param to consumer
         */
        public synchronized void dump(Consumer<T> to) {
            for (T rec : buffer)
                to.accept(rec);
        }
    }
    //todo unit tests
}
