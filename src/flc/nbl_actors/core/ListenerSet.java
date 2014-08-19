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
     * Multi-shot listener; Signals that this listener should not be
     * removed/deactivated after first event. (One-shot listeners are the
     * default)
     * <p>
     * Implementations of  {@link IGreenThrFactory#setActiveListener(java.util.function.Consumer)}
     * should remove the listener after first event, if it is not instance of  {@link flc.nbl_actors.core.ListenerSet.IMultiShot}.
     * </p>
     */
    public interface IMultiShot<T> extends Consumer<T> {
    }

    static class Listener<E> implements Consumer<E> {
        final SoftReference<Consumer<E>> ref;
        final boolean isMultiShot;

        Listener(Consumer<E> listener, boolean multiShot) {
            ref = new SoftReference<>(listener);
            isMultiShot = multiShot;
        }

        @Override
        public void accept(E event) {
            Consumer<E> c = ref.get();
            if (c != null) c.accept(event);
        }
    }

    private final List<Listener<T>> list = new LinkedList<>();

    public synchronized void addListener(Consumer<T> c) {
        list.add(new Listener<>(c, (c instanceof IMultiShot)));
    }

    @Override
    public synchronized void accept(T t) {
        Iterator<Listener<T>> it = list.iterator();
        while (it.hasNext()) {
            Listener<T> e = it.next();
            if (!e.isMultiShot)
                it.remove();
            e.accept(t); //PS. user code may call addListener again
        }
    }
}
