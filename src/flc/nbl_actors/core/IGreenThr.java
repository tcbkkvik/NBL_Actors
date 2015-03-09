/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;


import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Green Thread.
 * <p>Schedule Runnable messages.
 * Also used to initiate actor references ({@link ActorBase#initThread(IGreenThr)}).
 * </p>
 * Date: 18.07.13
 *
 * @author Tor C Bekkvik
 */
public interface IGreenThr {

    /**
     * Schedule a runnable message.
     * All messages are executed sequentially; at most one Runnable are processed at any time.
     * <p>
     * (Implementations should call {@code ThreadContext.get().beforeRun()} before running.)
     * </p>
     *
     * @param msg Runnable message
     * @throws NullPointerException if command is null
     */
    void execute(Runnable msg);

    /**
     * Call: Send message with callback function to handle result.
     *
     * @param msg      message
     * @param callback result handler
     * @param <T>      return type
     */
    default <T> void call(final Supplier<T> msg, final Consumer<T> callback) {
        IGreenThr caller = ThreadContext.get().getThread();
        if (caller == null)
            throw new IllegalStateException("Method can only be called from another 'IGreenThr' thread");
        execute(() -> {
            final T value = msg.get();
            caller.execute(
                    () -> callback.accept(value)
            );
        });
    }

    /**
     * Call: Send message, returning asynchronous result
     *
     * @param msg message
     * @param <T> return type
     * @return asynchronous result
     */
    default <T> IASync<T> call(final Supplier<IASync<T>> msg) {
        IGreenThr caller = ThreadContext.get().getThread();
        if (caller == null)
            throw new IllegalStateException("Method can only be called from another 'IGreenThr' thread");
        final ASyncValue<T> c = new ASyncValue<>();
        execute(() -> msg.get().result(
                r -> caller.execute(
                        () -> c.accept(r)
                )
        ));
        return c;
    }

}
