/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/**
 * Track thread activity.
 * <p>Example - Listen to multiple factories:
 * </p>
 * <pre>
 * {@code
 *  ThreadActivity.listenTo(factory1, factory2)
 *          .onEmptyShutdown()
 *          .await(0);
 * }
 * </pre>
 * Date: 08.01.14
 *
 * @author Tor C Bekkvik
 */
public class ThreadActivity {
    private final ListenerSet<Boolean> listener = new ListenerSet<>();
    private final AtomicBoolean isActive = new AtomicBoolean();

    /**
     * Set if active
     *
     * @param active true if active
     * @return true if changed
     */
    public boolean setActive(boolean active) {
        if (isActive.getAndSet(active) != active) {
            listener.accept(active);
            return true;
        }
        return false;
    }

    /**
     * Set active listener. (false when no threads have remaining work/messages).
     *
     * @param listener active listener
     */
    public void setListener(Consumer<Boolean> listener) {
        this.listener.addListener(Objects.requireNonNull(listener));
        listener.accept(isActive.get());
    }

    public static class Counts {
        final ActiveCount ac = new ActiveCount();
        public final List<IGreenThrFactory> factories = new ArrayList<>();

        public Counts() {
        }

        public Counts(List<IGreenThrFactory> fs) {
            fs.forEach(f -> {
                f.setActiveListener(ac.newParticipant());
                factories.add(f);
            });
        }

        public void listenTo(IGreenThrFactory fact) {
            factories.add(fact);
            fact.setActiveListener(ac.newParticipant());
        }

        public void setActiveListener(Consumer<Boolean> al) {
            ac.setActiveHandler(al);
        }

        private boolean isShutdownScheduled;

        /**
         * Calls {@link IGreenThrFactory#shutdown()} when
         * no more activity.
         *
         * @return this
         */
        public Counts onEmptyShutdown() {
            isShutdownScheduled = true;
            setActiveListener(active -> {
                if (!active)
                    factories.forEach(IGreenThrFactory::shutdown);
            });
            return this;
        }

        /**
         * Waits at most {@code millis} milliseconds for threads to
         * terminate. A timeout of {@code 0} means to wait forever.*
         * <p>Calls {@link IGreenThrFactory#await(long)} for each factory.
         * </p>
         *
         * @param millis maximum total time to wait in milliseconds
         * @return this
         *
         * @throws InterruptedException if any thread has interrupted the current thread.
         *                              or {@link #onEmptyShutdown()} was not called
         */
        public Counts await(long millis) throws InterruptedException {
            if (!isShutdownScheduled)
                throw new InterruptedException("onEmptyShutdown() was not called");
            await0(millis);
            return this;
        }

        public Counts await0(long millis) throws InterruptedException {
            final long t1 = System.currentTimeMillis() + millis;
            for (IGreenThrFactory f : factories) {
                f.await(millis);
                if (millis > 0 && (millis = t1 - System.currentTimeMillis()) < 1)
                    break;
            }
            return this;
        }
    }

    /**
     * Listen to activity in thread factories.
     * <p>Calls {@link IGreenThrFactory#setActiveListener} and keeps a list of given factories.
     * </p>
     *
     * @param factories thread factories
     * @return factories wrapper
     */
    public static Counts listenTo(List<IGreenThrFactory> factories) {
        return new Counts(factories);
    }

    /**
     * Listen to activity in thread factories.
     * <p>Calls {@link #listenTo(java.util.List)}
     * </p>
     *
     * @param factories factories
     * @return factories wrapper
     */
    public static Counts listenTo(IGreenThrFactory... factories) {
        return new Counts(Arrays.asList(factories));
    }
}
