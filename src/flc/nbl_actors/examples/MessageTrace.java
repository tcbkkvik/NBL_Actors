/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.examples;

import flc.nbl_actors.core.*;
import flc.nbl_actors.experimental.log.*;

/**
 * Example on using MessageRelay to generate a message trace.
 * <p>Date: 22.01.2015
 * </p>
 *
 * @author Tor C Bekkvik
 */
public class MessageTrace {

    static void log(Object o) {
        System.out.println(o);
    }

    static void someTask(final int depth, final IGreenThrFactory gf) {
        IGreenThr thr = gf.newThread();
        //Optionally add logInfo to event (thr.execute):
        MessageRelay.logInfo("send A" + depth);
        thr.execute(() -> {
            log("  got A" + depth);
            if (depth < 4) someTask(depth + 1, gf);
        });
        MessageRelay.logInfo("send B" + depth);
        thr.execute(() -> log("  got B" + depth));
    }

    static class MyActor extends ActorBase<MyActor> {

    }

    public static void main(String[] args) throws InterruptedException {
        final MsgListenerFactoryRingBuf ringBuffer = new MsgListenerFactoryRingBuf(1000, null);
        //- Attach my own special listener:
        ringBuffer.listenToIncoming(rec -> {
            if (rec instanceof MsgSent) {
                  /*
                  Demonstrates user-defined runtime event inspection.
                  NB. Calling ringBuffer.getMessageTrace from here
                  gets noisy, and is a bad idea for production code:
                    (i) lots of redundant logging here
                    (ii) records are buffered anyway, and can be
                         inspected later using buffer.forEach().
                    */
                log("Message trace:");
                ringBuffer.getMessageTrace(rec.id(), event -> log("   * " + event));
            }
        });

        try (IGreenThrFactory thrFactory = new GreenThrFactory_single(2)) {
            //SPECIAL -- tracing messages:
            thrFactory.setMessageRelay(new MessageRelay(ringBuffer));

            thrFactory.newThread().execute(() -> someTask(1, thrFactory));
            IActorRef<MyActor> ref = new MyActor().init(thrFactory);
            ref.send(a -> log("  MyActor received message"));
            thrFactory.await(60000L);
        }
        log("\nDone running. Buffer dump:");
        ringBuffer.forEach(e -> System.out.println(e.info()));
    }

}
