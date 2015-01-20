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

    private final Object lock = new Object();
    private final Deque<IMsgEvent> buffer = new ArrayDeque<>();
    private volatile int maxBufSize;
    private volatile Consumer<IMsgEvent> listener;

    /**
     * @param maxSize  Max #events stored in ring-buffer
     * @param listener Next in logger chain, or null.
     */
    public MsgListenerFactoryRingBuf(int maxSize, Consumer<IMsgEvent> listener) {
        maxBufSize = maxSize;
        this.listener = listener;
    }

    /**
     * Set max ring buffer size
     *
     * @param maxSize max size. size=0 means unlimited
     */
    public void setMaxBufSize(int maxSize) {
        maxBufSize = maxSize;
    }

    /**
     * Listens to incoming message events,
     * received in {@link #accept(IMsgEvent)}.
     *
     * @param listener consumer
     */
    public void listenToIncoming(Consumer<IMsgEvent> listener) {
        this.listener = listener;
    }

    /**
     * Accept message event, send to attached listener if any,
     * <p>before adding event to buffer (synchronized).
     * </p>
     *
     * @param event message event
     */
    @Override
    public void accept(IMsgEvent event) {
        synchronized (lock) {
            if (listener != null)
                listener.accept(event);
            buffer.add(event);
            if (maxBufSize > 0)
                while (buffer.size() > maxBufSize) {
                    buffer.poll();
                }
        }
    }

    @Override
    public Consumer<IMsgEvent> forkListener() {
        return this;
    }

    /**
     * Perform given action on buffered elements
     *
     * @param action The action to be performed for each element
     * @throws NullPointerException if the specified action is null
     */
    public void forEach(Consumer<? super IMsgEvent> action) {
        synchronized (lock) {
            buffer.forEach(action);
        }
    }

    /**
     * Retrieves, but does not remove, the last element (most recently added)
     *
     * @return last event, or null if buffer is empty
     */
    public IMsgEvent peekLast() {
        synchronized (lock) {
            return buffer.peekLast();
        }
    }

    private static MsgSent msgSent(IMsgEvent msg) {
        if (msg instanceof MsgSent)
            return (MsgSent) msg;
        if (msg instanceof MsgReceived)
            return ((MsgReceived) msg).sent;
        return null;
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
        synchronized (lock) {
            Iterator<IMsgEvent> it = buffer.descendingIterator();
            MsgId prev = sent.idParent;
            while (it.hasNext()) {
                sent = msgSent(it.next());
                if (sent != null && sent.id().equals(prev)) {
                    prev = sent.idParent;
                    list.add(sent);
                }
            }
        }
        return list;
    }

}
