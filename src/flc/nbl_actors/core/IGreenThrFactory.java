/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Green thread factory
 * <p>Generate green threads needed by actors.
 * </p>
 * <pre>
 * Example usage pattern:
 *
 * {@code
 *    java.util.concurrent.CompletableFuture<Integer>
 *        future = new java.util.concurrent.CompletableFuture<>();
 *    try (IGreenThrFactory f = new A_factory_implementation(..))
 *    {
 *        //Actor usage:
 *        new A(..)     // A: your actor implementation
 *            .init(f)  // IActorRef<A>
 *            .send(a -> {
 *                //call method(s) on instance 'a'
 *            });
 *        //Thread usage:
 *        f.newThread().execute(() -> {
 *            //do some work..  value = ...
 *            future.complete(value);
 *        });
 *    }
 *    int final_result = future.get(); //wait
 * }
 * </pre>
 * Date: 27.07.13
 *
 * @author Tor C Bekkvik
 * @see ActorBase
 */
public interface IGreenThrFactory extends Closeable {

    /**
     * Create a new green thread.
     *
     * @return thread
     */
    IGreenThr newThread();

    /**
     * Set empty listener to be called once, when all threads of this factory becomes inactive.
     * Default implementation is based on {@link #setActiveListener(Consumer)}.
     *
     * @param listener called when done (empty message queues)
     */
    default void setEmptyListener(Runnable listener) {
        setActiveListener(active -> {
            if (!active)
                listener.run();
        });
    }

    /**
     * Set listener to be called once, when threads changes active/non-active state.
     * <pre>
     *  Active = true: at least one thread have remaining work</p>
     *  Passive = false: all threads have processed all messages</p>
     * </pre>
     * The listener should be removed after first event (one-shot),
     * unless it implements {@link flc.nbl_actors.core.ListenerSet.IMultiShot}.
     * </p>
     *
     * @param listener called when active state changes
     */
    void setActiveListener(Consumer<Boolean> listener);

    /**
     * <p>Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     * </p>
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #await(long)}
     * to do that.
     * </p>
     * <p>(Behaviour based on {@link java.util.concurrent.ExecutorService#shutdown()})
     * </p>
     */
    void shutdown();

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks.
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #await(long)} to
     * do that.
     * </p>
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  For example, typical
     * implementations will cancel via {@link Thread#interrupt}, so any
     * task that fails to respond to interrupts may never terminate.
     * </p>
     * <p>(Behaviour based on {@link java.util.concurrent.ExecutorService#shutdownNow()})
     * </p>
     */
    void shutdownNow();

    /**
     * Waits at most {@code millis} milliseconds for thread message-queue
     * to become empty. A timeout of {@code 0} means to wait forever.
     * Default implementation uses {@link java.util.concurrent.CountDownLatch#await()}
     * Use with care:
     * Don't call from inside threads of this factory
     * (created with {@link #newThread()}), or the method can deadlock.
     * </p>
     *
     * @param millis the time to wait in milliseconds
     * @throws InterruptedException if the current thread is interrupted while waiting,
     *                              or time-out.
     */
    default void await(long millis) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        setEmptyListener(latch::countDown);
        if (millis != 0) {
            if (!latch.await(millis, TimeUnit.MILLISECONDS))
                throw new InterruptedException("Timed out");
        } else {
            latch.await();
        }
    }

    /**
     * Prepare for shutdown of associated threads.
     * Default action is to stop all associated threads when there is no more work.
     * If already closed then invoking this method has no effect.
     */
    default void close() {
        setEmptyListener(this::shutdown);
    }

}
