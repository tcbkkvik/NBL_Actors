/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental.log;


import flc.nbl_actors.core.IGreenThrFactory;

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
public class MessageEventBuffer
        implements IMsgListenerFactory, Consumer<IMsgEvent>, IMsgEventTracer {

    private final Object lock = new Object();
    private final Deque<IMsgEvent> buffer = new ArrayDeque<>();
    private volatile int maxBufSize;
    private volatile Consumer<? super IMsgEvent> eventAction;
    private volatile MessageRelay relay;

    /**
     * @param maxSize Max #events stored in ring-buffer
     */
    public MessageEventBuffer(int maxSize) {
        maxBufSize = maxSize;
    }

    /**
     * Listen to messages from thread-factory
     * <p>({@code Data flow: IGreenThrFactory ==> MessageRelay ==> this})
     * </p>
     *
     * @param threads thread-factory
     * @param isReduceLog true = reduce log (minimize internal per message stack-traces)
     * @return this
     */
    public MessageEventBuffer listenTo(IGreenThrFactory threads, boolean isReduceLog) {
        threads.setMessageRelay(relay = new MessageRelay(this));
        relay.setReduceLog(isReduceLog);
        return this;
    }

    public MessageEventBuffer listenTo(IGreenThrFactory threads)
    {
        return listenTo(threads, false);
    }

    /**
     * Set event action, to be performed when {@link #accept(IMsgEvent)} is called.
     *
     * @param action event action
     * @return this
     */
    public MessageEventBuffer setEventAction(Consumer<? super IMsgEvent> action) {
        this.eventAction = action;
        return this;
    }

    /**
     * Set max ring buffer size
     *
     * @param maxSize Max #events stored in ring-buffer
     */
    public void setMaxBufSize(int maxSize) {
        maxBufSize = maxSize;
    }

    /**
     * Reduce logging (may help performance).
     * <p>If true: avoids calling Throwable.getStackTrace() for messages
     * with attached MessageRelay.logInfo().
     * Usable after {@link #listenTo(flc.nbl_actors.core.IGreenThrFactory, boolean)}
     * </p>
     *
     * @param isReduce true to reduce (default is false)
     */
    public void setReduceLog(boolean isReduce) {
        if (relay != null)
            relay.setReduceLog(isReduce);
    }

    /**
     * Disable or enable logging
     *
     * @param isDisabled true = disable, false = normal logging
     */
    public void setDisableLog(boolean isDisabled) {
        if (relay != null)
            relay.setDisableLog(isDisabled);
    }


    /**
     * Accept message event, send to attached consumer if any,
     * <p>before adding event to buffer (synchronized).
     * </p>
     *
     * @param event message event
     */
    @Override
    public void accept(IMsgEvent event) {
        synchronized (lock) {
            buffer.add(event);
            if (eventAction != null) {
                eventAction.accept(event);
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

    public IMsgEvent[] toArray() {
        synchronized (lock) {
            return buffer.toArray(new IMsgEvent[buffer.size()]);
        }
    }

    /**
     * Perform given action on buffered elements
     *
     * @param action Action performed for each element
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

    @Override
    public void getMessageTrace(MsgId aId, Consumer<? super IMsgEvent> aConsumer) {
        if (aId == null) return;
        synchronized (lock) {
            Iterator<IMsgEvent> it = buffer.descendingIterator();
            while (it.hasNext()) {
                IMsgEvent elem = it.next();
                if (!elem.id().equals(aId))
                    continue;
                aConsumer.accept(elem);
                MsgEventSent sent = elem instanceof MsgEventSent
                        ? (MsgEventSent) elem
                        : ((MsgEventReceived) elem).sent;
                aId = sent.idParent;
            }
        }
    }

}
