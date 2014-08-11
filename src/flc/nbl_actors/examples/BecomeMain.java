/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.examples;

import flc.nbl_actors.core.*;

/**
 * Date: 20.08.13
 *
 * @author Tor C Bekkvik
 */
public class BecomeMain {
    /**
     * An actor demonstrating {@link #become(Object)}
     */
    static class BecomeDemo extends ActorBase<BecomeDemo> {
        public void gotMessage() {
            System.out.println(" I am the original actor");
            final BecomeDemo original = this;
            become(new BecomeDemo() {
                @Override
                public void gotMessage() {
                    System.out.println(" I am a second implementation");
                    become(original);
                }
            });
        }
    }

    public static void main(String[] args) {
        IActorRef<BecomeDemo> ref = new BecomeDemo().initThread(Runnable::run);
        ref.send(BecomeDemo::gotMessage);
        ref.send(BecomeDemo::gotMessage);
        ref.send(BecomeDemo::gotMessage);
    }

}
