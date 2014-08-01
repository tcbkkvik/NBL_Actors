/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.core;

import java.util.Objects;
import java.util.function.*;

/**
 * Fork/Join utility;
 * <pre>
 * Example:
 * {@code
 * Recursive concurrent binary tree traversal.
 *    Returns future number of leaf nodes (2^depth).
 *
 *  static IASync<Integer> traverse(IGreenThrFactory tf, int depth)
 *  {
 *     if (depth < 1)
 *         return new ASyncDirect<>(1); //1 leaf node
 *     ForkJoin<Integer> fj = new ForkJoin<>(0);
 *     for (int i = 0; i < 2; ++i) { //left + right child
 *         fj.callAsync(tf.newThread(),
 *                 () -> traverse(tf, depth - 1),
 *                 (value, returned) -> value + returned
 *         );
 *     }
 *     return fj.resultAsync();
 *  }
 * }
 * </pre>
 * Date: 20.07.14
 *
 * @param <R> Result type
 * @author Tor C Bekkvik
 */
public class ForkJoin<R> {

    private final IGreenThr callerThr;
    private int pendingCalls; //==0 if value ready, >0 otherwise
    private R value;
    private Consumer<R> consumer;

    /**
     * Initiate fork/join with caller thread and a start value.
     *
     * @param thr   caller thread (handles individual call-replies)
     * @param value initial value
     */
    public ForkJoin(IGreenThr thr, R value) {
        if (thr == null)
            throw new NullPointerException("Missing caller thread");
        callerThr = thr;
        this.value = value;
    }

    /**
     * Initiate fork/join with a start value, using current thread.
     *
     * @param value initial value
     */
    public ForkJoin(R value) {
        this(ThreadContext.get().getThread(), value);
    }

    /**
     * Initiate fork/join, using current thread.
     */
    public ForkJoin() {
        this(ThreadContext.get().getThread(), null);
    }

    //------------------------------------- IGreenThr calls:

    /**
     * Call thread with async response
     *
     * @param toThread target thread
     * @param call     action at target thread
     * @param reply    reply action at this thread
     * @param <T>      result type
     */
    public <T> void callAsync(IGreenThr toThread, Supplier<IASync<T>> call, Consumer<T> reply) {
        ++pendingCalls;
        toThread.execute(() -> call.get()
                .result(v -> join(
                        () -> reply.accept(v))
                ));
    }

    /**
     * Call thread with async response and update local value
     *
     * @param toThread target thread
     * @param call     action at target thread
     * @param reply    reply action at this thread
     * @param <T>      result type
     */
    public <T> void callAsync(IGreenThr toThread, Supplier<IASync<T>> call, BiFunction<R, T, R> reply) {
        callAsync(toThread, call, v -> value = reply.apply(value, v));
    }

    /**
     * Call thread
     *
     * @param toThread target thread
     * @param call     action at target thread
     * @param reply    reply action at this thread
     * @param <T>      result type
     */
    public <T> void call(IGreenThr toThread, Supplier<T> call, Consumer<T> reply) {
        ++pendingCalls;
        toThread.execute(() -> {
            final T v = call.get();
            join(() -> reply.accept(v));
        });
    }

    /**
     * Call thread and update local value
     *
     * @param toThread target thread
     * @param call     action at target thread
     * @param reply    reply action at this thread
     * @param <T>      result type
     */
    public <T> void call(IGreenThr toThread, Supplier<T> call, BiFunction<R, T, R> reply) {
        call(toThread, call, v -> value = reply.apply(value, v));
    }

    /**
     * Call thread with Runnable's
     *
     * @param toThread target thread
     * @param call     action at target thread
     * @param reply    reply action at this thread
     */
    public void call(IGreenThr toThread, Runnable call, Runnable reply) {
        ++pendingCalls;
        toThread.execute(() -> {
            call.run();
            join(reply);
        });
    }

    //------------------------------------- IActorRef calls:

    /**
     * Call actor with async response
     *
     * @param toRef target actor
     * @param call  action at target
     * @param reply reply action at this thread
     * @param <A>   actor type
     * @param <T>   result type
     */
    public <A, T> void callAsync(IActorRef<A> toRef, Function<A, IASync<T>> call, Consumer<T> reply) {
        ++pendingCalls;
        toRef.send(a -> call.apply(a)
                .result(v -> join(
                        () -> reply.accept(v))
                ));
    }

    /**
     * Call actor with async response and update local value
     *
     * @param toRef target actor
     * @param call  action at target
     * @param reply reply action at this thread
     * @param <A>   actor type
     * @param <T>   result type
     */
    public <A, T> void callAsync(IActorRef<A> toRef, Function<A, IASync<T>> call, BiFunction<R, T, R> reply) {
        callAsync(toRef, call, v -> value = reply.apply(value, v));
    }

    /**
     * Call actor
     *
     * @param toRef target actor
     * @param call  action at target
     * @param reply reply action at this thread
     * @param <A>   actor type
     * @param <T>   result type
     */
    public <A, T> void call(IActorRef<A> toRef, Function<A, T> call, Consumer<T> reply) {
        ++pendingCalls;
        toRef.send(a -> {
            final T v = call.apply(a);
            join(() -> reply.accept(v));
        });
    }

    /**
     * Call actor and update local value
     *
     * @param toRef target actor
     * @param call  action at target
     * @param reply reply action at this thread
     * @param <A>   actor type
     * @param <T>   result type
     */
    public <A, T> void call(IActorRef<A> toRef, Function<A, T> call, BiFunction<R, T, R> reply) {
        call(toRef, call, v -> value = reply.apply(value, v));
    }

    /**
     * Call actor with Runnable's
     *
     * @param toRef target actor
     * @param call  action at target
     * @param reply reply action at this thread
     * @param <A>   actor type
     */
    public <A> void call(IActorRef<A> toRef, Runnable call, Runnable reply) {
        ++pendingCalls;
        toRef.send(a -> {
            call.run();
            join(reply);
        });
    }

    //-------------------------------------

    /**
     * Set current value
     *
     * @param value value
     */
    public void setValue(R value) {
        this.value = value;
    }

    /**
     * Get current value
     *
     * @return value
     */
    public R getValue() {
        return value;
    }

    /**
     * Get future result
     *
     * @return future result
     */
    public IASync<R> resultAsync() {
        final ASyncValue<R> av = new ASyncValue<>();
        result(av::accept);
        return av;
    }

    /**
     * Get future result
     *
     * @param cons Consumer
     *             (Allowed to call {@link #setValue(Object)} which may alter result)
     * @return future result
     */
    public IASync<R> resultAsync(Consumer<R> cons) {
        Objects.requireNonNull(cons);
        final ASyncValue<R> av = new ASyncValue<>();
        result(r -> { //r==value
            cons.accept(r); //may change 'value' via setValue(..)
            av.accept(value); //'value' finally returned
        });
        return av;
    }

    /**
     * Consume result when ready
     *
     * @param cons consumer
     */
    public void result(Consumer<R> cons) {
        consumer = cons;
        trigger();
    }

    /**
     * Join - register call response and decrease #pendingCalls.
     * If #pendingCalls == 0, trigger value consumer.
     *
     * @param reply action on receive
     */
    private void join(Runnable reply) {
        callerThr.execute(() -> {
            reply.run();
            --pendingCalls;
            trigger();
        });
    }

    private void trigger() {
        if (pendingCalls == 0 && consumer != null)
            consumer.accept(value);
    }

}
