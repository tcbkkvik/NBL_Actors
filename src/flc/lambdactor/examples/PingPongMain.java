/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.examples;

import flc.lambdactor.core.*;

/**
 * Date: 15.08.13
 *
 * @author Tor C Bekkvik
 */
public class PingPongMain extends ActorBase<PingPongMain> {

    /**
     * Ping message.
     * Responds by sending a pong message via self().send(..)
     *
     * @param no #remaining rounds
     */
    private void ping(int no) {
        System.out.println("ping");
        self().send(a -> a.pong(no));
    }

    /**
     * Pong message
     *
     * @param no #remaining rounds
     */
    private void pong(final int no) {
        System.out.println("      pong " + no);
        if (no > 0)
            self().send(a -> a.ping(no - 1));
        else System.out.println("done");
    }

    static void minimumExample(IGreenThrFactory factory) {
        class PlainObj {
            public void someMethod(double value) {
                System.out.println("received value: " + value);
            }
        }
        IActorRef<PlainObj> ref = new ActorRef<>(factory, new PlainObj());
        ref.send(a -> a.someMethod(34));
    }

    static void actorBaseExample(IGreenThrFactory factory) {
        class Impl extends ActorBase<Impl> {
            void otherMethod(String message) {
                System.out.println(message + ": done!");
            }

            void someMethod(String message) {
                this.self().send(a -> a.otherMethod(message));
            }
        }
        IActorRef<Impl> ref = new Impl().init(factory);
        ref.send(a -> a.someMethod("do it!"));
    }

    public static void main(String[] args) throws InterruptedException {
        try (IGreenThrFactory f = new GreenThr_single(false)) {
            minimumExample(f);
            actorBaseExample(f);
            new PingPongMain()
                    .init(f)
                    .send(a -> a.ping(10));
        }
    }
}
