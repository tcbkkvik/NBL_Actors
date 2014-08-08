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
 * <p>Provides safe asynchronous access to actor implementation methods.
 * </p>
 * <pre> Minimum example:
 * {@code
 *     public static void minimumExample(IGreenThrFactory factory)
 *     {
 *         class PlainObj{
 *             public void someMethod(double value) {
 *                 System.out.println("received value: " + value);
 *             }
 *         }
 *         IActorRef<PlainObj> ref = new ActorRef<>(factory, new PlainObj());
 *         ref.send(a -> a.someMethod(34));
 *     }
 * }
 * </pre>
 * Date: 02.08.13
 *
 * @param <A> The type of user object (actor implementation) held
 *           and protected by this actor reference.
 *
 * @author Tor C Bekkvik
 */
public interface IActorRef<A> {

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
     * Call: Send a message, returning asynchronous result
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

}
