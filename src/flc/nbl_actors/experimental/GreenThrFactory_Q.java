/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;



import flc.nbl_actors.core.*;

import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * <p>Green Thread factory;
 * </p>
 * Date: 03.02.14
 *
 * @author Tor C Bekkvik
 */
public class GreenThrFactory_Q implements IGreenThrFactory {
    private final IThreads threads;
    private final GreenT[] greens;
    private int greenIx;
    private volatile boolean isShutdown, isShutdownNow;

    private GreenThrFactory_Q(IThreads threads, int greenCount) {
        this.threads = threads;
        threads.init(this);
        greens = new GreenT[greenCount];
        for (int i = 0; i < greens.length; i++) {
            greens[i] = new GreenT(this);
        }
        ThreadContext ctx = ThreadContext.get();
        ctx.setFactory(this);
    }

    public GreenThrFactory_Q(int numThr) {
        this(new ExThreads(numThr), numThr * 4);
    }

    public GreenThrFactory_Q(ExecutorService service, int greenCount) {
        this(new ExService(service), greenCount);
    }

    @Override
    public IGreenThr newThread() {
        synchronized (greens) {
            greenIx = (greenIx + 1) % greens.length;
            return greens[greenIx];
        }
    }

    @Override
    public void setActiveListener(Consumer<Boolean> listener) {
        threads.setActiveListener(listener);
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        threads.shutdown();
    }

    @Override
    public void shutdownNow() {
        isShutdown = isShutdownNow = true;
        threads.shutdownNow();
    }

    @Override
    public void await(long millis) throws InterruptedException {
        threads.await(millis);
    }

    private interface IThreads extends IGreenThrFactory, Executor {
        @Override
        default IGreenThr newThread() {return null;}
        void init(IGreenThrFactory parent);
    }

    static class ExThreads implements IThreads {
        private final ThreadActivity active = new ThreadActivity();
        private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        private final Thread[] realThreads; //queue consumers
        private volatile boolean isShutdown;
        private volatile IGreenThrFactory myFactory;

        public ExThreads(int numThr) {
            realThreads = new Thread[numThr];
            final AtomicInteger busyCount = new AtomicInteger();
            class R implements Runnable {
                boolean isBusy;

                void busy(boolean b) {
                    if (b == isBusy) return;
                    isBusy = b;
                    if (b) {
                        busyCount.incrementAndGet();
                    } else {
                        if (busyCount.decrementAndGet() == 0)
                            active.setActive(false);
                    }
                }

                @Override
                public void run() {
                    ThreadContext.get().setFactory(myFactory);
                    Runnable r;
                    while (!isShutdown) {
                        try {
                            r = queue.take();
                            busy(true);
                            r.run();
                        } catch (InterruptedException ignore) {}
                        while ((r = queue.poll()) != null) {
                            busy(true);
                            r.run();
                        }
                        busy(false);
                    }
                }
            }
            for (int i = 0; i < numThr; ++i) {
                realThreads[i] = new Thread(new R());
            }
        }

        @Override
        public void init(IGreenThrFactory parent) {
            myFactory = parent;
            for (Thread t : realThreads)
                t.start();
        }

        @Override
        public void execute(Runnable command) {
            if (!isShutdown) {
                queue.add(command);
                active.setActive(true);
            }
        }

        @Override
        public void setActiveListener(Consumer<Boolean> listener) {
            active.setListener(listener);
        }

        @Override
        public void shutdown() {
            isShutdown = true;
            for (Thread thr : realThreads)
                thr.interrupt();
        }

        @Override
        public void shutdownNow() {
            queue.clear();
            shutdown();
        }

        @Override
        public void await(long millis) throws InterruptedException {
            final long t1 = System.currentTimeMillis() + millis;
            for (Thread thr : realThreads) {
                thr.join(millis);
                if (millis > 0 && (millis = t1 - System.currentTimeMillis()) < 1)
                    break;
            }
        }
    }

    static class ExService implements IThreads {
        private final ThreadActivity active = new ThreadActivity();
        private final AtomicInteger count = new AtomicInteger();
        private final ExecutorService service;
        private volatile IGreenThrFactory myFactory;

        public ExService(ExecutorService ex) {
            this.service = ex;
        }

        @Override
        public void init(IGreenThrFactory parent) {
            myFactory = parent;
        }

        @Override
        public void execute(Runnable msg) {
            count.incrementAndGet();
            active.setActive(true);//ok??
            service.execute(() -> {
                ThreadContext.get().setFactory(myFactory);
                msg.run();
                if (count.decrementAndGet() == 0)
                    active.setActive(false);
            });
        }

        @Override
        public void setActiveListener(Consumer<Boolean> listener) {
            active.setListener(listener);
        }

        @Override
        public void shutdown() {
            service.shutdown();
        }

        @Override
        public void shutdownNow() {
            service.shutdownNow();
        }

        @Override
        public void await(long millis) throws InterruptedException {
            service.awaitTermination(millis, TimeUnit.MILLISECONDS);
        }
    }

    public static class GreenT extends GreenThrBase implements IGreenThr {
        private final Deque<Runnable> msgQueue = new ConcurrentLinkedDeque<>();
        private final GreenThrFactory_Q pool;

        public GreenT(GreenThrFactory_Q pool) {
            super(pool.threads);
            this.pool = pool;
        }

        private void enqueue(Runnable job, boolean stack) {
            if (stack)
                msgQueue.addFirst(job);
            else
                msgQueue.add(job);
        }

        @Override
        public void execute(Runnable msg) {
            if (pool.isShutdown)
                return;
            int noParentCalls = 1;
            boolean isStackOrder = noParentCalls > 1; //todo isStackOrder=?
            enqueue(msg::run, isStackOrder);
            scheduleThread();
        }

        @Override
        protected boolean isMoreMessages() {
            return !msgQueue.isEmpty();
        }

        @Override
        protected void processMessages() {
            ThreadContext ctx = ThreadContext.get();
            ctx.setThread(this);
            Runnable r;
            int limit = Math.max(100, msgQueue.size());
            //fairness / avoid livelock (r.run -> execute -> r.run .. )ok??
            while (--limit >= 0 && (r = msgQueue.poll()) != null) {
                ctx.beforeRun();
                r.run();
                if (pool.isShutdownNow)
                    msgQueue.clear();
            }
        }
    }

}
