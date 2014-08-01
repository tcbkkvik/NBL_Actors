/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.core;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Green-thread factory
 * <p>Spawns green threads (IGreenThr) from pre-initialized array of GreenThr_single instances,
 * in a round-robin fashion.
 * </p>
 * Date: 07.08.13
 *
 * @author Tor C Bekkvik
 * @see GreenThr_single
 */
public class GreenThrFactory_single implements IGreenThrFactory {
    private final int totalNThreads;
    private int index;
    private volatile Consumer<Exception> exceptionHandler = e -> {};
    private final ThreadActivity.Counts activeCount = new ThreadActivity.Counts();

    /**
     * Greenthread factory using java.lang.Thread
     * <p> (isDaemon = true)
     * </p>
     *
     * @param totalNThreads Total number of threads started.
     */
    public GreenThrFactory_single(int totalNThreads) {
        this(totalNThreads, true);
    }

    /**
     * Greenthread factory using java.lang.Thread
     * @param totalNThreads Total number of threads started.
     * @param isDaemon if {@code true}, marks the thread as a daemon thread
     *                 <p> (The Java Virtual Machine exits when the only
     *                 threads running are all daemon threads.)
     *                 </p>
     */
    public GreenThrFactory_single(int totalNThreads, boolean isDaemon) {
        setExceptionHandler(e -> ThreadContext.logTrace(e, "/GreenThrFactory_single"));
        ThreadContext.get().setFactory(this);
        this.totalNThreads = totalNThreads;
        for (int i = 0; i < totalNThreads; i++) {
            IGreenThrFactory thr = new GreenThr_single(isDaemon, this) {
                @Override
                public void onException(Exception e) {
                    exceptionHandler.accept(e);
                }
            };
            activeCount.listenTo(thr);
        }
    }

    public void setExceptionHandler(Consumer<Exception> handler) {
        exceptionHandler = Objects.requireNonNull(handler);
    }

    public void reverseOrder(boolean reversed) {
        activeCount.factories.forEach(t -> ((GreenThr_single)t).reverseOrder(reversed));
    }

    @Override
    public IGreenThr newThread() {
        synchronized(activeCount) {
            index = (index + 1) % totalNThreads;
            return activeCount.factories
                    .get(index)
                    .newThread();
        }
    }

    private void onShutdown() {
        final Consumer<Exception> old = exceptionHandler;
        exceptionHandler = e -> {
            if (!(e instanceof InterruptedException))
                old.accept(e);
        };
    }

    public void shutdown() {
        onShutdown();
        activeCount.factories.forEach(IGreenThrFactory::shutdown);
    }

    @Override
    public void shutdownNow() {
        onShutdown();
        activeCount.factories.forEach(IGreenThrFactory::shutdownNow);
    }

    /**
     * Calls {@code Thread.join} on all underlying threads.
     * A timeout of {@code 0} means to wait forever.
     */
    public void await(long ms) throws InterruptedException {
        activeCount.await0(ms);
    }

    @Override
    public void setActiveListener(final Consumer<Boolean> listener) {
        activeCount.setActiveListener(listener);
    }

}