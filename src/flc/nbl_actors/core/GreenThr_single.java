/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

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
    private volatile Function<Runnable, Runnable> interceptor = r -> r;

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
     *
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
     *
     * @param isDaemon if {@code true}, marks the thread as a daemon thread
     *                 <p> (The Java Virtual Machine exits when the only
     *                 threads running are all daemon threads.)
     *                 </p>
     * @param f        parent factory
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
                while (!isStopping || !queue.isEmpty()) {
                    try {
                        Runnable task = queue.take();
                        threadActive.setActive(true);
                        while (task != null) {
                            tc.beforeRun();
                            task.run();
                            task = queue.poll();
                        }
                    } catch (Exception e) {
                        onException(e);
                    }
                    threadActive.setActive(false);
                }
                threadActive.setActive(false);
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

    @Override
    public void setMessageRelay(IMessageRelay msgRelay) {
        interceptor = msgRelay.newInterceptor(this);
    }

    public void reverseOrder(boolean reversed) {
        isStack = reversed; //true = LIFO
    }

    @Override
    public void execute(Runnable r0) {
        if (isStopping) return;
        Runnable r = interceptor.apply(r0);
        threadActive.setActive(true);
        if (isStack)
            queue.addFirst(r);
        else
            queue.add(r);
    }

    public void shutdownNow() {
        queue.clear();
        shutdown();
        threadActive.setActive(false);
    }

    public void shutdown() {
        isStopping = true;
        thr.interrupt();
    }

    /**
     * Calls {@link Thread#join(long)}
     *
     * @param ms the time to wait in milliseconds
     * @return false if timeout ({@code !thr.isAlive()})
     * @throws InterruptedException if any thread has interrupted the current thread.
     */
    public boolean awaitThread(long ms) throws InterruptedException {
        thr.join(ms);
        return !thr.isAlive();
    }

    @Override
    public void setActiveListener(Consumer<Boolean> listener) {
        threadActive.setListener(listener);
    }

}
