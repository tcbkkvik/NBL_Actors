/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * MailBox utility; Create explicit message queues for increased control.
 * <pre>Example:
 * {@code
 *   class Act extends ActorBase<Act> {
 *      void receive(Deque<Double> queue) {..}
 *   };
 *   final IActorRef<Act> ref = new Act().init(threads);
 *   final Deque<Double> queue = new LinkedBlockingDeque<>();
 *   BiConsumer<Double, Boolean> priorityMailBox = MailBox.create(
 *      ref,
 *      (val, hiPri) -> {
 *          if (hiPri) queue.addFirst(val);
 *          else queue.add(val);
 *      },
 *      a -> a.receive(queue));
 *   priorityMailBox.accept(3.2, false);
 *   priorityMailBox.accept(1.5, true);
 * }
 * </pre>
 * <p>Date: 07.03.2015
 * </p>
 *
 * @author Tor C Bekkvik
 */
public class MailBox {

    /**
     * Create Mailbox
     *
     * @param ref     actor reference
     * @param queue   message queue
     * @param handler queue consumer
     * @param <A>     actor type
     * @param <T>     message type
     * @return mail box
     */
    public static <A, T> Consumer<T> create(
            final IActorRef<A> ref, final Consumer<T> queue, final Consumer<A> handler) {
        final AtomicBoolean isScheduled = new AtomicBoolean();
        return t -> {
            queue.accept(t);
            if (isScheduled.compareAndSet(false, true))
                ref.send(actor -> {
                    isScheduled.set(false);
                    handler.accept(actor);
                });
        };
    }

    /**
     * Create Priority Mailbox
     *
     * @param ref     actor reference
     * @param queue   message queue
     * @param handler queue consumer
     * @param <A>     actor type
     * @param <T>     message type
     * @param <U>     priority type (normally int)
     * @return mail box
     */
    public static <A, T, U> BiConsumer<T, U> create(
            final IActorRef<A> ref, final BiConsumer<T, U> queue, final Consumer<A> handler) {
        final AtomicBoolean isScheduled = new AtomicBoolean();
        return (t, u) -> {
            queue.accept(t, u);
            if (isScheduled.compareAndSet(false, true))
                ref.send(actor -> {
                    isScheduled.set(false);
                    handler.accept(actor);
                });
        };
    }

}
