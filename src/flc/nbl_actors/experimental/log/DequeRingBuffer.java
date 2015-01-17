/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental.log;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Ring Buffer based on java.util.ArrayDeque.
 * When full, oldest elements are removed.
 * <p>Date: 17.01.2015
 * </p>
 *
 * @author Tor C Bekkvik
 */
public class DequeRingBuffer<T> {
    private final Deque<T> buffer = new ArrayDeque<>();
    private int maxBufSize = 100;

    /**
     * set max buffer size
     *
     * @param maxBufSize max size. size=0 means unlimited
     * @return this
     */
    public synchronized DequeRingBuffer<T> setMaxBufSize(int maxBufSize) {
        this.maxBufSize = maxBufSize;
        return this;
    }

    /**
     * Append to ring buffer
     *
     * @param e element added
     */
    public synchronized void add(T e) {
        buffer.add(e);
        if (maxBufSize > 0)
            while (buffer.size() > maxBufSize) {
                buffer.poll();
            }
    }

    /**
     * Retrieves and removes the head of the queue (= oldest = first element).
     *
     * @return first element, or {@code null} if empty
     */
    public synchronized T poll() {
        return buffer.poll();
    }

    /**
     * Remove and get most recently added element (= last).
     *
     * @return last element, or null if empty buffer
     */
    public synchronized T pollLast() {
        return buffer.pollLast();
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

    public Iterator<T> descendingIterator() {
        return buffer.descendingIterator();
    }

    public Iterator<T> iterator() {
        return buffer.iterator();
    }
}
