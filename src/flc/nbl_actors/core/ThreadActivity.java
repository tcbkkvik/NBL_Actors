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
        listener.accept(isActive.get());
    }

    static class Counts {
        class Source implements Consumer<Boolean> {
            final AtomicBoolean active = new AtomicBoolean();

            @Override
            public void accept(Boolean act) {
                active.set(act);
                signal(act);
            }
        }

        public final List<Source> sources = new ArrayList<>();
        private final AtomicBoolean isActive = new AtomicBoolean();
        private final AtomicBoolean flagged = new AtomicBoolean();
        private volatile Consumer<Boolean> listener = a -> {
        };

        final List<IGreenThrFactory> factories = new ArrayList<>();

        public Counts() {
        }

        public Counts(List<IGreenThrFactory> fs) {
            fs.forEach(f -> {
                sources.add(new Source());
                factories.add(f);
            });
        }

        private void signal(boolean sig) {
            if (sig) {
                setActive(true);
                flagged.set(true);
            } else {
//                if (flagged.compareAndSet(true, false)) {
//                    setActive(isOneActive());
//                    flagged.set(true);
//                }
                synchronized (flagged) {
                    flagged.set(false);
                    setActive(isOneActive());
                }
            }
        }

        private boolean isOneActive() {
            for (Source s : sources) {
                if (s.active.get() || flagged.get())
                    return true;
            }
            return flagged.get();
        }

        private void setActive(boolean active) {
            if (isActive.getAndSet(active) != active)
                listener.accept(active);
        }

        public void listenTo(IGreenThrFactory fact) {
            Source s = new Source();
            sources.add(s);
            factories.add(fact);
            fact.setActiveListener(s);
        }

        public void setActiveListener(Consumer<Boolean> al) {
            listener = al;
            al.accept(isActive.get());
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
