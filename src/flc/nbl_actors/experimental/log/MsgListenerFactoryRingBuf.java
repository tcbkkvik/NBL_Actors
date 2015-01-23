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
            buffer.add(event);
            if (listener != null) {
                listener.accept(event);
            }
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

    /**
     * Get message trace.
     *
     * @param last Last message to be traced from. (not null)
     * @return List of messages, found by backward tracing
     */
    public List<IMsgEvent> getMessageTrace(IMsgEvent last) {
        return getMessageTrace(last == null ? null : last.id());
    }

    public List<IMsgEvent> getMessageTrace(MsgId aId) {
        List<IMsgEvent> list = new LinkedList<>();
        getMessageTrace(aId, list::add);
        return list;
    }

    public void getMessageTrace(MsgId aId, Consumer<? super IMsgEvent> aConsumer) {
        if (aId == null) return;
        synchronized (lock) {
            Iterator<IMsgEvent> it = buffer.descendingIterator();
            while (it.hasNext()) {
                IMsgEvent elem = it.next();
                if (!elem.id().equals(aId))
                    continue;
                aConsumer.accept(elem);
                MsgSent sent = elem instanceof MsgSent
                        ? (MsgSent) elem
                        : ((MsgReceived) elem).sent;
                aId = sent.idParent;
            }
        }
    }

}
