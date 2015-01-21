/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.util.function.Consumer;

/**
 * Actor message. Message sent to an actor
 * Date 16.01.2015.
 *
 * @author Tor C Bekkvik
 */
public class ActorMessage<A> implements Runnable {
    public final Consumer<A> msg;
    public final ActorRef<A> ref;

    public ActorMessage(Consumer<A> msg, ActorRef<A> ref) {
        this.msg = msg;
        this.ref = ref;
    }

    @Override
    public void run() {
        try {
            msg.accept(ref.getImpl());
        } catch (RuntimeException ex) {
            ref.exceptHd.accept(ex);
        }
    }
}
