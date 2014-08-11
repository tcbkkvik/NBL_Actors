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
    private volatile Consumer<Boolean> listener = a -> {};
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
        this.listener = Objects.requireNonNull(listener);
        this.listener.accept(isActive.get());
    }

    /**
     * Activity listener: from Integer to Boolean
     */
    private static class Count2Active implements Consumer<Integer> {
        final Consumer<Boolean> listener;
        final AtomicBoolean isActive = new AtomicBoolean();
        volatile boolean first = true;

        Count2Active(Consumer<Boolean> listener) {
            this.listener = listener;
        }

        @Override
        public void accept(Integer count) {
            boolean active = count > 0;
            if (first || isActive.compareAndSet(!active, active)) {
                listener.accept(active);
                first = false;
            }
        }
    }

    public static class Counts {
        public final List<IGreenThrFactory> factories = new ArrayList<>();
        private final AtomicInteger activeCount = new AtomicInteger();
        private volatile Consumer<Integer> listener = a -> {};
        private volatile boolean isShutdownScheduled;

        public Counts() {}

        /**
         * @param list list of thread factories to listen to.
         */
        public Counts(List<IGreenThrFactory> list) {
            list.forEach(this::listenTo);
        }

        /**
         * Add thread factory to listen to.
         *
         * @param f thread factory
         */
        public void listenTo(IGreenThrFactory f) {
            factories.add(Objects.requireNonNull(f));
            f.setActiveListener(new Active2Delta());
        }

        /**
         * Set boolean activity listener, triggered only
         * when condition (count &gt; 0) changes.
         * <p>Calls {@link #setNumActiveListener(Consumer)}
         * </p>
         *
         * @param listener boolean listener
         */
        public void setActiveListener(Consumer<Boolean> listener) {
            setNumActiveListener(new Count2Active(listener));
        }

        /**
         * Set listener for #active threads, triggered
         * when #active changes.
         *
         * @param activeCountListener active count listener
         */
        public void setNumActiveListener(Consumer<Integer> activeCountListener) {
            listener = Objects.requireNonNull(activeCountListener);
            listener.accept(activeCount.get());
        }

        private void trigger(int delta) {
            listener.accept(activeCount.addAndGet(delta));
        }

        private class Active2Delta implements Consumer<Boolean> {
            private Boolean isActive;

            @Override
            public synchronized void accept(Boolean active) {
                if (isActive == null) {
                    if (active) trigger(1);
                } else if (active != isActive)
                    trigger(active ? 1 : -1);
                isActive = active;
            }
        }

        /**
         * Calls {@link IGreenThrFactory#shutdown()} when
         * no more activity.
         *
         * @return this
         */
        public Counts onEmptyShutdown() {
            isShutdownScheduled = true;
            setNumActiveListener(count -> {
                if (count == 0)
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
