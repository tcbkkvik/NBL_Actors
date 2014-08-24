/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.util.ArrayList;
import java.util.List;
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
    private final ActiveCount ac = new ActiveCount();
    private final List<IGreenThrFactory> threads = new ArrayList<>();

    /**
     * Green thread factory using java.lang.Thread
     * <p> (isDaemon = true)
     * </p>
     *
     * @param totalNThreads Total number of threads started.
     */
    public GreenThrFactory_single(int totalNThreads) {
        this(totalNThreads, true);
    }

    /**
     * Green thread factory using java.lang.Thread
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
            threads.add(thr);
            thr.setActiveListener(ac.newParticipant());
        }
    }

    public void setExceptionHandler(Consumer<Exception> handler) {
        exceptionHandler = Objects.requireNonNull(handler);
    }

    public void reverseOrder(boolean reversed) {
        threads.forEach(t -> ((GreenThr_single)t).reverseOrder(reversed));
    }

    @Override
    public IGreenThr newThread() {
        synchronized(threads) {
            index = (index + 1) % totalNThreads;
            return threads
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
        threads.forEach(IGreenThrFactory::shutdown);
    }

    @Override
    public void shutdownNow() {
        onShutdown();
        threads.forEach(IGreenThrFactory::shutdownNow);
    }

    @Override
    public void setActiveListener(final Consumer<Boolean> listener) {
        ac.setActiveListener(listener);
    }

}