/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.core;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.*;

/**
 * Green-thread backed by a single real java Thread.
 * Date: 18.07.13
 *
 * @author Tor C Bekkvik
 */
public class GreenThr_single implements IGreenThr, IGreenThrFactory {
    private final BlockingDeque<Runnable> queue = new LinkedBlockingDeque<>();
    private final Thread thr;
    private volatile boolean isStopping, isStack;
    private final ThreadActivity threadActive = new ThreadActivity();
    private final IGreenThrFactory myFactory;

    /**
     * Green-thread using java.lang.Thread.
     * <p> (Calls Thread.setDaemon(true) before starting;
     * The Java Virtual Machine exits when the only
     * threads running are all daemon threads)
     * </p>
     */
    public GreenThr_single() {
        this(true, null);
    }

    /**
     * Green-thread using java.lang.Thread
     * @param isDaemon if {@code true}, marks the thread as a daemon thread
     *                 <p> (The Java Virtual Machine exits when the only
     *                 threads running are all daemon threads.)
     *                 </p>
     */
    public GreenThr_single(boolean isDaemon) {
        this(isDaemon, null);
    }

    /**
     * Green-thread using java.lang.Thread
     * @param isDaemon if {@code true}, marks the thread as a daemon thread
     *                 <p> (The Java Virtual Machine exits when the only
     *                 threads running are all daemon threads.)
     *                 </p>
     * @param f parent factory
     */
    public GreenThr_single(boolean isDaemon, IGreenThrFactory f) {
        myFactory = f == null ? this : f;
        ThreadContext.get().setFactory(myFactory);
        thr = new Thread() {
            @Override
            public void run() {
                final ThreadContext tc = ThreadContext.get();
                tc
                        .setThread(GreenThr_single.this)
                        .setFactory(myFactory);
                while (runCheck()) {
                    try {
                        tc.beforeRun();
                        queue.take().run();
                    } catch (Exception e) {
                        onException(e);
                    }
                }
            }
        };
        thr.setDaemon(isDaemon);
        thr.start();
    }

    @Override
    public IGreenThr newThread() {
        return this;
    }

    public void onException(Exception e) {
        ThreadContext.logTrace(e, "/GreenThr_single");
    }

    public void reverseOrder(boolean reversed) {
        isStack = reversed; //true = LIFO
    }

    @Override
    public void execute(Runnable r) {
        if (isStopping) return;
        threadActive.setActive(true);
        if (isStack)
            queue.addFirst(r);
        else
            queue.add(r);
    }

    public void shutdownNow() {
        queue.clear();
        shutdown();
    }

    public void shutdown() {
        isStopping = true;
        thr.interrupt();
    }

    /**
     * Calls {@code Thread.join} , which uses a loop of {@code this.wait} calls
     * conditioned on {@code this.isAlive}.
     */
    public void await(long ms) throws InterruptedException {
        thr.join(ms);
    }

    /**
     * Check running state
     *
     * @return true to keep running
     */
    private boolean runCheck() {
        if (queue.isEmpty()) {
            threadActive.setActive(false);
            return !isStopping;
        }
        return true;
    }

    @Override
    public void setActiveListener(Consumer<Boolean> listener) {
        threadActive.setListener(listener);
    }

}
