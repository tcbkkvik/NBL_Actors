/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.experimental;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Message scheduler base class for green-threads
 */
public abstract class GreenThrBase {
    private final AtomicBoolean isScheduled = new AtomicBoolean(false);
    private final Executor executor;
    private final Runnable runner;

    /**
     * Initiate Runnable to process messages (called from threadContext threads)
     *
     * @param threadContext Thread pool
     */
    public GreenThrBase(Executor threadContext) {
        executor = Objects.requireNonNull(threadContext);
        runner = () -> {
            try {
                processMessages();
            } catch (RuntimeException e) {
                onException(e);
            } finally {
                isScheduled.set(false);
            }
            if (isMoreMessages()) {
                try {
                    scheduleThread();
                } catch (RuntimeException e) {
                    onException(e);
                }
            }
        };
    }

    /**
     * Schedule thread from ThreadContext.
     * Needed for processMessages() to be called
     */
    public void scheduleThread() {
        if (isScheduled.compareAndSet(false, true)) {
            executor.execute(runner);
        }
    }

    /**
     * Process messages (all or some, depending on priority etc.)
     * Remember to call scheduleThread() to trigger processing.
     */
    protected abstract void processMessages();

    /**
     * Is more messages to consume later (true = need to reschedule)
     *
     * @return true if messages remain.
     */
    protected abstract boolean isMoreMessages();

    public void onException(RuntimeException e) {
        e.printStackTrace();
    }

}
