/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.core;

import java.util.Deque;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Green-thread using no threads; All scheduled messages are processed by current
 * thread.
 * Date: 18.07.13
 *
 * @author Tor C Bekkvik
 */
public class GreenThr_zero implements IGreenThr, IGreenThrFactory {

    private final Deque<Runnable> queue = new ConcurrentLinkedDeque<>();//new LinkedBlockingDeque<>();
    //    private final Deque<Runnable> queue = new LinkedList<>();
    private boolean isStop; //true if not accepting new messages in execute()
    private boolean isStack; //LIFO
    private final ThreadActivity threadActive = new ThreadActivity();

    /**
     * Init with default order = FIFO (first-in-first-out)
     */
    public GreenThr_zero() {
        ThreadContext.get().setFactory(this);
    }

    public void reverseOrder(boolean reversed) {
        isStack = reversed;
    }

    @Override
    public IGreenThr newThread() {
        return this;
    }

    private void pump() {
        if (queue.isEmpty() || !threadActive.setActive(true))
            return;
        ThreadContext tc = ThreadContext.get();
        IGreenThr orig_thr = tc.getThread();
        tc.setThread(this);
        try {
            Runnable r;
            while ((r = queue.poll()) != null) {
                try {
                    tc.beforeRun();
                    r.run();
                } catch (RuntimeException e) {
                    onException(e);
                }
            }
        } finally {
            threadActive.setActive(false);
            tc.setThread(orig_thr);
        }
    }

    public void onException(RuntimeException e) {
        ThreadContext.logTrace(e, "/GreenThr_zero");
    }

    @Override
    public void execute(Runnable r) {
        if (isStop) return;
        if (isStack)
            queue.addFirst(r);
        else
            queue.add(r); //equ. to addLast
        pump();
    }

    @Override
    public void shutdown() {
        isStop = true;
        pump();
    }

    @Override
    public void shutdownNow() {
        isStop = true;
        queue.clear();
    }

    @Override
    public void await(long millis) {
        pump();
    }

    public void close() {
        pump();
        setEmptyListener(this::shutdown);
    }

    @Override
    public void setActiveListener(Consumer<Boolean> listener) {
        threadActive.setListener(listener);
    }
}
