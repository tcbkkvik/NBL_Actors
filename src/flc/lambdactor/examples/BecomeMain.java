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
 * Date: 20.08.13
 *
 * @author Tor C Bekkvik
 */
public class BecomeMain {
    /**
     * An actor demonstrating {@link #become(Object)}
     */
    public static class Actor extends ActorBase<Actor> {
        /**
         * Self modifying method; calls {@link #become(Object)}.
         */
        public void tell() {
            System.out.println(" become(A)  => IActorRef -> object A ");
            final Actor original = this;
            become(new Actor() {
                @Override
                public void tell() {
                    System.out.println("  become(B) => IActorRef -> object B ");
                    become(original);
                }
            });
        }
    }

    public static void main(String[] args) {
        try (IGreenThrFactory f = new GreenThr_zero()) {
            IActorRef<Actor> ref = new Actor().init(f);
            ref.send(Actor::tell);
            ref.send(Actor::tell);
            ref.send(Actor::tell);
        }
    }

}
