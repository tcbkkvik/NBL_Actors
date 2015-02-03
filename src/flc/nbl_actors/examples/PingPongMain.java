/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.examples;

import flc.nbl_actors.core.*;

import java.util.concurrent.atomic.*;

/**
 * Date: 15.08.13
 *
 * @author Tor C Bekkvik
 */
public class PingPongMain extends ActorBase<PingPongMain> {

    static void log(Object o) {
        System.out.println(o);
    }

    static void simpleStatelessPingPong(IGreenThrFactory gf) throws InterruptedException {
        log("\nPing-Pong, stateless:");
        class P {
            void pingPong(final int no) {
                gf.newThread().execute(() -> {
                    log(" ping");
                    gf.newThread().execute(() -> {
                        log("  pong " + no);
                        if (no > 0) pingPong(no - 1);
                    });
                });
            }
        }
        new P().pingPong(5);
        gf.await(0);
    }

    public static void main(String[] args) throws InterruptedException {
        final AtomicInteger count = new AtomicInteger();
        class PP extends ActorBase<PP> {
            IActorRef<PP> other;

            PP(IActorRef<PP> other) {
                this.other = other;
            }

            void ping() {
                log(" ping " + count.get());
                other.send(PP::pong);
            }

            void pong() {
                log("      pong " + count.get());
                if (count.incrementAndGet() < 4)
                    other.send(PP::ping);
            }
        }
        try (IGreenThrFactory gf = new GreenThrFactory_single(2)) {
            log("\nPing-Pong, 2 actors:");
            final IActorRef<PP> actor1 = new PP(null).init(gf);
            final IActorRef<PP> actor2 = new PP(actor1).init(gf);
            actor1.send(a -> {
                a.other = actor2;
                a.ping();
            });
            simpleStatelessPingPong(gf);
            gf.await(0);
        }
    }
}
