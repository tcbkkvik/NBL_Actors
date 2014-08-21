/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import flc.nbl_actors.core.IGreenThr;
import flc.nbl_actors.core.IGreenThrFactory;
import flc.nbl_actors.core.ThreadActivity;
import flc.nbl_actors.core.ThreadContext;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Thread factory based on java.util.concurrent.ExecutorService.
 * Date: 07.12.13
 *
 * @author Tor C Bekkvik
 */
public class GreenThrFactory_Exec implements IGreenThrFactory {

    private final ExecutorService service;
    private final ExBuf exBuf;

    public GreenThrFactory_Exec(ExecutorService service) {
        ThreadContext.get().setFactory(this);
        this.service = service;
        exBuf = new ExBuf(service);
        _start();
    }

    @Override
    public IGreenThr newThread() {
        return new GreenThr_Exec(exBuf);
    }

    private void _start() {
        exBuf.isStarted = true;
        exBuf.pump();
    }

//    @Override
    public void reverseOrder(boolean reversed) {
        exBuf.isReverse = reversed;
    }

    @Override
    public void setActiveListener(Consumer<Boolean> listener) {
        exBuf.threadsActive.setListener(listener);
    }

    @Override
    public void shutdown() {
        service.shutdown();
    }

    @Override
    public void shutdownNow() {
        service.shutdownNow();
        exBuf.isShutdownNow = true;
        exBuf.queue.clear();
    }

    /**
     * Join implemented by calling underlying {@code ExecutorService.awaitTermination}.
     * Blocks until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current thread is
     * interrupted, whichever happens first.
     * A timeout of {@code 0} means wait a long time (default: 24 hours)
     *
     * @param millis the time to wait in milliseconds
     * @return false if timeout
     * @throws InterruptedException if interrupted while waiting
     */
    @Override
    public boolean await(long millis) throws InterruptedException {
        if (millis <= 0) millis = 3600L * 24 * 1000;
        return service.awaitTermination(millis, TimeUnit.MILLISECONDS);
    }

    static class ExBuf implements Executor {
        final BlockingDeque<Runnable> queue = new LinkedBlockingDeque<>();
        final ThreadActivity threadsActive = new ThreadActivity();
        final AtomicInteger jobCount = new AtomicInteger();
        final AtomicBoolean isPumping = new AtomicBoolean();
        final ExecutorService service;
        volatile boolean isStarted, isReverse, isShutdownNow;

        ExBuf(ExecutorService exe) {
            this.service = exe;
        }

        @Override
        public void execute(Runnable msg) {
            if (service.isShutdown())
                return;
            jobCount.incrementAndGet();
            threadsActive.setActive(true);
            if (isReverse)
                queue.addFirst(msg);
            else
                queue.add(msg);
            if (isStarted) pump();
        }

        private void pump() {
            if (!isPumping.compareAndSet(false, true))
                return;
            final Runnable r = queue.poll();
            if (r != null) {
                service.execute(() -> {
                    isPumping.set(false);
                    pump();
                    r.run();
                    if (jobCount.decrementAndGet() == 0)
                        threadsActive.setActive(false);
                });
            } else {
                isPumping.set(false);
            }
        }
    }

    static class GreenThr_Exec extends GreenThrBase implements IGreenThr {
        final BlockingDeque<Runnable> messages = new LinkedBlockingDeque<>();
        final IGreenThrFactory factory;
        final ExBuf exBuf;

        GreenThr_Exec(ExBuf exBuf) {
            super(exBuf);
            this.factory = ThreadContext.get().getFactory();
            this.exBuf = exBuf;
        }

        @Override
        protected boolean isMoreMessages() {
            return !messages.isEmpty();
        }

        @Override
        protected void processMessages() {
            Runnable msg;
            int sz = messages.size();
            ThreadContext tc = ThreadContext.get();
            tc.setFactory(factory).setThread(this);
            while (sz-- > 0 && (msg = messages.poll()) != null) {
                try {
                    tc.beforeRun();
                    msg.run();
                    if (exBuf.isShutdownNow)
                        messages.clear();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void execute(Runnable msg) {
            if (exBuf.service.isShutdown()) {
                return;
            }
            if (exBuf.isReverse)
                messages.addFirst(msg);
            else
                messages.add(msg);
            scheduleThread();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(4);

        CountDownLatch latch = new CountDownLatch(1);
        service.execute(latch::countDown);
        latch.await();

        IGreenThrFactory threads = new GreenThrFactory_Exec(service);
        AtomicBoolean active = new AtomicBoolean();
        threads.setActiveListener(a -> System.out.println(" active: " + a));
        threads.newThread().execute(() -> {
            threads.setActiveListener(a -> {
                System.out.println(" active 2: " + a);
                active.set(a);
            });
            threads.shutdown();
        });
        threads.await(0);
        assert !active.get();
        System.out.println("done, ok: " + !active.get());
    }
}
