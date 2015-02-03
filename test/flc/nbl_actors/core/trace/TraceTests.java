/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core.trace;

import flc.nbl_actors.core.*;
import org.junit.Test;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * <p>Date: 28.01.2015
 * </p>
 *
 * @author Tor C Bekkvik
 */
public class TraceTests {

    @Test
    public void testMessageRelay_RingBuf() throws InterruptedException {
        final MessageEventBuffer buffer = new MessageEventBuffer(100);
        final ThreadLocal<IMsgEvent> lastEvent = new ThreadLocal<>();

        class Info implements Supplier<String> {
            final String s;
            final int depth;

            public Info(String s, int depth) {
                this.s = s;
                this.depth = depth;
            }

            @Override
            public String get() {
                return s + depth;
            }

            void assertSent(IMsgEvent event, IGreenThr toThr) {
                MsgEventSent s = (MsgEventSent) event;
                assertTrue(s.logInfo == this);
                assertTrue(s.targetThread == toThr);
            }

            void assertReceived(IMsgEvent event) {
                MsgEventSent s = ((MsgEventReceived) event).sent;
                assertTrue(s.logInfo == this);
            }
        }

        class TraceCheck implements Consumer<IMsgEvent> {
            @Override
            public void accept(IMsgEvent rec) {
                lastEvent.set(rec);
                log("Message trace:");
                int depth = 0;
                List<IMsgEvent> trace = buffer.getMessageTrace(rec);
                for (IMsgEvent event : trace) {
                    log("   * " + event);
                    ++depth;
                }
                MessageRelay.TContext ctx = MessageRelay.getContext();
                MsgEventSent sent = null;
                if (rec instanceof MsgEventSent) {
                    sent = (MsgEventSent) rec;
                } else if (rec instanceof MsgEventReceived) {
                    sent = ((MsgEventReceived) rec).sent;
                    assertEquals("received id", rec.id(), ctx.getLastReceived().id());
                    List<IMsgEvent> trace2 = ctx.getMessageTrace();
                    assertEquals("getMessageTrace:list", trace, trace2);
                }
                if (sent != null && sent.logInfo instanceof Info) {
                    Info info = (Info) sent.logInfo;
                    assertEquals("getMessageTrace;depth", depth, info.depth);
                }
            }
        }

        class Action {
            void repeat(final int depth, final IGreenThrFactory gf) {
                IGreenThr thr = gf.newThread();

                final Info info = new Info("A", depth);
                MessageRelay.logInfo(info);
                thr.execute(() -> {
                    info.assertReceived(lastEvent.get());
                    log("  got A" + depth);
                    if (depth < 4)
                        repeat(depth + 1, gf);
                });
                info.assertSent(lastEvent.get(), thr);

                final Info infoB = new Info("B", depth);
                MessageRelay.logInfo(infoB);
                thr.execute(() -> {
                    infoB.assertReceived(lastEvent.get());
                    log("  got B" + depth);
                });
                infoB.assertSent(lastEvent.get(), thr);
            }
        }

        class MyActor extends ActorBase<MyActor> {
            void call(int value) {
                log("  actor received " + value);
            }
        }

        log("\ntestMessageRelay_RingBuf");
        try (IGreenThrFactory gf = new GreenThrFactory_single(4)) {
            buffer.setMaxBufSize(200);
            buffer.setEventAction(new TraceCheck())
                    .listenTo(gf);
            buffer.setReduceLog(false);//default:false
            buffer.setDisableLog(false);//default:false
            gf.newThread().execute(() -> {
                new Action().repeat(2, gf);
                final int no = 747;
                MessageRelay.logInfo("" + no);
                new MyActor().init(gf)
                        .send(a -> a.call(no));
            });
//            gf.await(10000L);
            gf.await(0);
        }
        log(" Done running.  Buffer dump:");
        buffer.forEach(e -> log(e.info()));
        log("done testMessageRelay_RingBuf");
    }

    @Test
    public void testMessageRelay_1thread_messageChain() {
        log("\ntestMessageRelay_1thread_messageChain..");
        MessageRelay.printMessageTrace(System.out);
        class LastMessage implements IMsgListenerFactory, Consumer<IMsgEvent> {
            IMsgEvent event;

            @Override
            public Consumer<IMsgEvent> forkListener() {
                return this;
            }

            @Override
            public void accept(IMsgEvent event) {
                this.event = event;
                log("  " + event.info());
            }
        }
        final LastMessage last = new LastMessage();
        final Function<Runnable, Runnable> intercept
                = new MessageRelay(last).newInterceptor(Runnable::run);

        class MessageChain {
            void call(final int n, final MsgEventReceived parent) {
                final Supplier<String> info = () -> "no:" + n;
                MessageRelay.logInfo(info);
                Runnable msg = intercept.apply(() -> {
                    //message received:
                    MsgEventReceived receivedE = (MsgEventReceived) last.event;
                    assertEquals(receivedE.sent.logInfo, info);
                    if (parent != null)
                        assertEquals(parent.id(), receivedE.sent.idParent);
                    if (n > 0)
                        call(n - 1, receivedE);
                });
                //message sent:
                MsgEventSent sentE = (MsgEventSent) last.event;
                if (parent != null) {
                    assertEquals(sentE.logInfo, info);
                    assertEquals(parent.id(), sentE.idParent);
                }
                msg.run();
            }
        }
        new MessageChain().call(3, null);
        log("done testMessageRelay_1thread_messageChain");
    }

    private static void log(Object o) {
        System.out.println(o);
    }
}
