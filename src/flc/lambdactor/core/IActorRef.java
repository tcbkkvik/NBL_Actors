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
 * <p>Provides safe asynchronous, typed access to a
 * user defined actor-object (A) contained within this reference.
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
 * @param <A> Type of the enclosed actor-object (implementation).
 * @author Tor C Bekkvik
 */
public interface IActorRef<A> {

    /**
     * Send a one-way message to this actor;
     * The message may be processed immediately, or scheduled to be
     * processed later. When processed, {@link Consumer#accept} is called
     * on the message, with the contained actor-object as argument.
     * (Ie. any methods on the actor-object can be called from
     * within the message lambda-block. Being typed implies
     * compile-time type checking, and no need for explicit message types)
     * <p>
     * For thread-safety, avoid leaking shared-multiple-access, as the
     * message may be processed in another thread; Do not access mutable,
     * non-synchronized fields on any other object than the actor-object
     * argument. (But thread-safe utilities, like those in
     * {@code java.util.concurrent.atomic}, can be quite useful)
     * </p>
     *
     * @param msg Message
     */
    void send(Consumer<A> msg);

    /**
     * Call: Send two-way message, with a callback function to handle the result
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
     * Call: Send a two-way message, returning an asynchronous result
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
