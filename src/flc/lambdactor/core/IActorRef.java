/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.core;


import java.util.function.*;

/**
 * Actor reference.
 * <p>Provide safe asynchronous access to actor implementation methods.
 * </p>
 * <pre> Example usage:
 * {@code
 * class Pong extends ActorBase<Pong> {
 *        void pong() {
 *            System.out.println("  pong");
 *        }
 *    }
 *    class Ping extends ActorBase<Ping> {
 *        int count;
 *        void ping(IActorRef<Pong> pongRef) {
 *            if (++count > 5) return;
 *            System.out.println("  ping " + count);
 *            pongRef.send(a -> {
 *                a.pong();
 *                self().execute(() -> ping(pongRef));
 *            });
 *        }
 *    }
 *    void startPingPong(IGreenThrFactory threads) {
 *        IActorRef<Pong> pongRef = new Pong().init(threads);
 *        IActorRef<Ping> pingRef = new Ping().init(threads);
 *        pingRef.send(a -> a.ping(pongRef));
 *    }
 * }
 * </pre>
 * Date: 02.08.13
 *
 * @param <A> Actor implementation type (class)
 * @author Tor C Bekkvik
 */
public interface IActorRef<A> extends IGreenThr {

    /**
     * Send message to this actor
     *
     * @param msg Message
     */
    void send(Consumer<A> msg);

    /**
     * Call: Send message with callback function to handle result.
     *
     * @param msg      message
     * @param callback result handler
     * @param <T>      return types
     */
    default <T> void call(final Function<A, T> msg, final Consumer<T> callback) {
        IGreenThr caller = ThreadContext.get().getThread();
        if (caller == null)
            throw new IllegalStateException("Method IGreenThr.call can only be called from another 'IGreenThr' thread");
        send(a -> {
            final T value = msg.apply(a);
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
    default <T> IASync<T> call(final Function<A, IASync<T>> msg) {
        IGreenThr caller = ThreadContext.get().getThread();
        if (caller == null)
            throw new IllegalStateException("Method IGreenThr.call can only be called from another 'IGreenThr' thread");
        final ASyncValue<T> c = new ASyncValue<>();
        send(a -> msg.apply(a).result(
                r -> caller.execute(
                        () -> c.accept(r)
                )
        ));
        return c;
    }

    /**
     * Send runnable message. Execute runnable on this actor's thread.
     *
     * @param msg message
     */
    @Override
    default void execute(final Runnable msg) {
        send(ai -> msg.run());
    }

}
