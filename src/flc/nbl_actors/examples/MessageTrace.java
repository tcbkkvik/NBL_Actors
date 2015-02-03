/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.examples;

import flc.nbl_actors.core.*;
import flc.nbl_actors.core.trace.*;

/**
 * Message tracing examples
 * <p>Date: 22.01.2015
 * </p>
 *
 * @author Tor C Bekkvik
 */
public class MessageTrace {

    static void log(Object o) {
        System.out.println(o);
    }

    static void throws2() {
        throw new RuntimeException("(NOT a real exception) Demonstrating mixed Stack + Message trace");
    }

    static void methodThrowsException() {
        throws2();
    }

    static void someTask(final int depth, final IGreenThrFactory gf) {
        IGreenThr thr = gf.newThread();
        //Optionally add logInfo to event (thr.execute):
        MessageRelay.logInfo("send A" + depth);
        thr.execute(() -> {
            log("  got A" + depth);
            if (depth < 3)
                someTask(depth + 1, gf);
            else
                methodThrowsException(); //caught by MessageEventBuffer
        });
        MessageRelay.logInfo("send B" + depth);
        thr.execute(() -> log("  got B" + depth));
    }

    static class MyActor extends ActorBase<MyActor> {
    }

    private static void eventInspect(MessageEventBuffer messageBuf, IMsgEvent event) {
        if (event instanceof MsgEventSent) {
                  /*
                  Demonstrates user-defined runtime event inspection.
                  NB. Calling .getMessageTrace(..) from here
                  gets noisy, and is a bad idea for production code:
                    (i) lots of redundant logging here
                    (ii) records are buffered anyway, and can be
                         inspected later.
                    */
            log("Message trace:");
            messageBuf.getMessageTrace(event.id(), ev -> log("   * " + ev));
        }
    }

    public static void main(String[] args) throws InterruptedException {
        try (final IGreenThrFactory threads = new GreenThrFactory_single(2)) {

            //Initiate exception and message-tracing:
            final MessageEventBuffer messageBuf = new MessageEventBuffer(200) {
                //Optional override: Customized runtime exception handling
                @Override
                public void onError(MsgId msgId, RuntimeException error) {
                    try {
                        super.onError(msgId, error);
                    } catch (Exception e) {
                        threads.shutdown();
                    }
                }
            }.listenTo(threads);

            //Optional user-defined runtime event inspection:
            messageBuf.setEventAction(event -> eventInspect(messageBuf, event));

            //Optional log info added to normal thread message (execute):
            MessageRelay.logInfo("Thread execute");
            threads.newThread().execute(() -> someTask(1, threads));

            //Optional log info added to normal actor message (send):
            MessageRelay.logInfo("Actor send");
            new MyActor().init(threads).send(a -> log("'Actor got message'"));

            threads.await(60000L);
            log("\nThreads done. Buffer dump:");
            for (IMsgEvent e : messageBuf.toArray())
                log(e.info());
            log("\nMain done");
        }
    }

}
