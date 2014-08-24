/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.function.Consumer;

/**
 * Protected listener references
 * Date: 19.08.14
 *
 * @author Tor C Bekkvik
 */
public class ListenerSet<T> implements Consumer<T> {

    /**
     * Signals that listener should be retained after initial event(s).
     * <p>
     * Used by {@link IGreenThrFactory#setActiveListener(java.util.function.Consumer)}
     * </p>
     */
    public interface IKeep<T> extends Consumer<T> {
    }

    //Listener Soft Reference:
    private static class SR<E> {
        final SoftReference<Consumer<E>> ref;
        final boolean isMultiShot;

        SR(Consumer<E> listener, boolean multiShot) {
            ref = new SoftReference<>(listener);
            isMultiShot = multiShot;
        }
    }

    //Separate add/active list: avoid ConcurrentModificationException
    private final List<SR<T>> listenQueue = new ArrayList<>(); //only append
    private final List<SR<T>> listeners = new LinkedList<>(); //any element may be removed
    private final Deque<T> eventQueue = new LinkedList<>();
    private boolean isActive;

    public synchronized void addListener(Consumer<T> c) {
        (isActive ? listenQueue : listeners)
                .add(new SR<>(c, (c instanceof IKeep)));
    }

    @Override
    public synchronized void accept(T event) {
        eventQueue.add(event);
        if (isActive)
            return;
        isActive = true;
        try {
            //NB A listener could trigger a new event or addListener(),
            //leading to a recursive calls, in which
            //new listeners are queued via listenQueue and events via eventQueue,
            //to avoid disturbing current event processing.
            while (!eventQueue.isEmpty()) {
                listeners.addAll(listenQueue);
                listenQueue.clear();
                toListeners(eventQueue.removeFirst(), listeners);
                //current event processing
            }
        } finally {
            isActive = false;
        }
    }

    /**
     * Event to listeners
     *
     * @param event event
     * @param list  listeners (elements can be removed)
     * @param <T>   event type
     */
    private static <T> void toListeners(T event, Iterable<SR<T>> list) {
        Iterator<SR<T>> it = list.iterator();
        while (it.hasNext()) {
            SR<T> e = it.next();
            Consumer<T> c = e.ref.get();
            if (!e.isMultiShot || c == null) {
                it.remove();
            }
            if (c == null) continue;
            try {
                c.accept(event);
            } catch (Exception ex) {
                onError(ex);
            }
        }
    }

    private static void onError(Exception ex) {
        ex.printStackTrace();
    }

}
