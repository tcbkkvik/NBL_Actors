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

    static void recursiveMethod(int z) {
        if (z > 1)
            recursiveMethod(z - 1);
        else
            throw new IllegalStateException("Demonstrating mixed Stack + Message trace");
    }

    static void someTask(final int depth, final IGreenThrFactory gf) {
        IGreenThr thr = gf.newThread();
        //Optionally add logInfo to event (thr.execute):
        MessageRelay.logInfo("send A" + depth);
        thr.execute(() -> {
            log("  got A" + depth);
            if (depth < 4) {
                someTask(depth + 1, gf);
            } else {
                try {
                    recursiveMethod(3);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.err.println("\tMessage trace:");
                    MessageRelay.printMessageTrace();
                }
            }
        });
        MessageRelay.logInfo("send B" + depth);
        thr.execute(() -> log("  got B" + depth));
    }

    static class MyActor extends ActorBase<MyActor> {

    }

    public static void main(String[] args) throws InterruptedException {
        try (IGreenThrFactory thrFactory = new GreenThrFactory_single(2)) {
            final MessageEventBuffer messageBuf = new MessageEventBuffer(1000)
                    .listenTo(thrFactory);
            messageBuf.setEventAction(rec -> {
                        if (rec instanceof MsgEventSent) {
                  /*
                  Demonstrates user-defined runtime event inspection.
                  NB. Calling .getMessageTrace(..) from here
                  gets noisy, and is a bad idea for production code:
                    (i) lots of redundant logging here
                    (ii) records are buffered anyway, and can be
                         inspected later using .forEach(..)
                    */
                            log("Message trace:");
                            messageBuf.getMessageTrace(rec.id(), event -> log("   * " + event));
                        }
                    }
            );
            thrFactory.newThread().execute(() -> someTask(1, thrFactory));
            IActorRef<MyActor> ref = new MyActor().init(thrFactory);
            ref.send(a -> log("  MyActor received message"));
            thrFactory.await(60000L);
            log("\nDone running. Buffer dump:");
            messageBuf.forEach(e -> System.out.println(e.info()));
        }
    }

}
