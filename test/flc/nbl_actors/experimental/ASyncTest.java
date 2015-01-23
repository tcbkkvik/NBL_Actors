/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import flc.nbl_actors.core.*;
import flc.nbl_actors.experimental.log.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

/**
 * Date: 14.10.13
 *
 * @author Tor C Bekkvik
 */
public class ASyncTest {

    static void log(Object o) {
        System.out.println(o);
    }

    static String indent(int n) {
        final String spc = "                                                             ";
        return spc.substring(0, Math.min(spc.length(), n));
    }

    private static volatile boolean isLogging;

    static class Node {
        static IASync<Integer> func(int depth) {
            if (depth < 1)
                return new ASyncDirect<>(1);
            final IGreenThrFactory f = ThreadContext.get().getFactory();
            ForkJoin<Integer> fj = new ForkJoin<>(0);
            final Supplier<IASync<Integer>> message = () ->
                    func(depth - 1);
            final BiFunction<Integer, Integer, Integer> callback2 = (s, v) -> {
                if (isLogging)
                    log(indent(depth * 4 + 4) + v);
                return s + v;
            };
            fj.callAsync(f.newThread(), message, callback2);
            fj.callAsync(f.newThread(), message, callback2);
            return fj.resultAsync();
        }
    }

    private static void testASync(int depth, int nThr) throws InterruptedException {
        log("testASync nThr:" + nThr);
        final GreenThrFactory_single factory = new GreenThrFactory_single(nThr);
        final Integer value_exp = 2 << (depth - 1);
        factory.newThread().execute(() -> Node.func(depth).result(val -> {
            log(" expect: " + value_exp);
            log(" result: " + val);
            assertEquals(value_exp, val);
            factory.shutdown();
        }));
        factory.reverseOrder(true);
//        factory.start();
        factory.await(0);
        log(" OK testASync nThr:" + nThr);
    }

    @Test
    public void testASync() throws InterruptedException {
        isLogging = true;
        testASync(3, 1);
        isLogging = false;
        testASync(8, 4);
    }

    @Test
    public void testIASync_result() throws InterruptedException {
        log("\n\ntestIASync_result");
        class Impl {
            IASync<Integer> func(int val) {
                return new ASyncDirect<>(val);
            }
        }
        try (IGreenThrFactory threads = new GreenThr_zero()) {
            ActorRef<Impl> ref = new ActorRef<>(threads, new Impl());
            final Integer val = 234;
            threads.newThread().execute(() -> ref
                    .call(a -> a.func(val))
                    .result(v -> {
                        log(" value = " + v);
                        assertEquals(val, v);
                        log(" OK");
                    })
            );
        }
    }


    static void tst(GreenThrFactory_Q factory) throws InterruptedException {
        IGreenThr gt = factory.newThread();
        CountDownLatch latch = new CountDownLatch(1);
        gt.execute(() -> {
            latch.countDown();
            log(" gt.execute");
        });
        latch.await();
        factory.shutdown();
    }

    static void tst2(IGreenThrFactory factory) throws Exception {
        final AtomicInteger noPending = new AtomicInteger(1);
        final AtomicInteger nodes = new AtomicInteger();
        final AtomicInteger noPendMax = new AtomicInteger();
        final CountDownLatch pendingLatch = new CountDownLatch(1);
        class TreeNod extends ActorBase<TreeNod> {
            int traverse(int pos) {
                if (pos < 10000) {
                    noPending.addAndGet(2);
                    self().send(a -> traverse(pos * 10 + 1));
                    self().send(a -> traverse(pos * 10 + 2));
                }
                log(" " + nodes.incrementAndGet() + " node: "
                        + pos + "  (" + (noPending.get() - 1) + ")");
                if (noPending.addAndGet(-1) == 0) {
                    log("traverse done.");
                    pendingLatch.countDown();
                }
                noPendMax.updateAndGet(op -> Math.max(op, noPending.get()));
                return pos;
            }

            IASync<Integer> sum(int a, int b) {
                return new ASyncDirect<>(a + b);
            }
        }
        final int a = 2;
        final int b = 10;
        IActorRef<TreeNod> actor = new TreeNod().init(factory);
        CompletableFuture<Integer> fut = new CompletableFuture<>();
        factory.newThread().execute(
                () -> {
                    actor.call(inst -> inst.sum(a, b))
                            .result(s -> {
                                boolean ok = s == (a + b);
                                log("sum: " + s + "  ok:" + ok);
                            });
                    actor.send(inst -> inst.sum(a, b).result(fut::complete));
                    actor.call(inst -> a + b, s -> {
                        boolean ok = s == (a + b);
                        log("sum2: " + s + "  ok:" + ok);
                    });
                    actor.send(inst -> inst.traverse(1));
                }
        );
        int ab = fut.get(); //blocks
        assert ab == a + b;
        pendingLatch.await();
        log("traverse max: " + noPendMax.get());
    }

    @Test
    public void testGreenThrFactory_N() throws InterruptedException {
        log("\n\ntestGreenThrFactory_N..");
        ExecutorService exec = Executors.newFixedThreadPool(4);
        GreenThrFactory_Exec factory = new GreenThrFactory_Exec(exec);
        final AtomicInteger count = new AtomicInteger(0);
        final int N = 3;
        for (int n = 0; n < N; ++n) {
            factory.newThread().execute(() -> {
                int no = count.incrementAndGet();
                log(" do " + no);
            });
        }
        factory.setEmptyListener(() -> {
            log("done");
            factory.shutdown();
        });
        factory.await(0);
        assertEquals(N, count.get());
    }

    @Test
    public void testFactory() throws Exception {
        try (IGreenThrFactory f = new GreenThrFactory_Q(1)) {
            tst2(f);
        }
        tst(new GreenThrFactory_Q(2));
        tst(new GreenThrFactory_Q(Executors.newFixedThreadPool(2), 2));
    }

    @Test
    public void testMessageRelay_RingBuf() throws InterruptedException {
        final MsgListenerFactoryRingBuf buffer = new MsgListenerFactoryRingBuf(100, null);
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
                MsgSent s = (MsgSent) event;
                assertTrue(s.userInfo == this);
                assertTrue(s.targetThread == toThr);
            }

            void assertReceived(IMsgEvent event) {
                MsgSent s = ((MsgReceived) event).sent;
                assertTrue(s.userInfo == this);
            }
        }

        class TraceCheck implements Consumer<IMsgEvent> {
            @Override
            public void accept(IMsgEvent rec) {
                lastEvent.set(rec);
                System.out.println("Message trace:");
                int depth = 0;
                List<IMsgEvent> trace = buffer.getMessageTrace(rec);
                for (IMsgEvent event : trace) {
                    System.out.println("   * " + event);
                    ++depth;
                }
                MessageRelay.TContext ctx = MessageRelay.getContext();
                MsgSent sent;
                if (rec instanceof MsgSent) {
                    sent = (MsgSent) rec;
                    assertEquals("sent id", rec.id(), ctx.getLastSent().id());
                } else {
                    sent = ((MsgReceived) rec).sent;
                    assertEquals("received id", rec.id(), ctx.getLastReceived().id());
                    List<IMsgEvent> trace2 = ctx.getMessageTrace();
                    assertEquals("getMessageTrace:list", trace, trace2);
                }
                if (sent.userInfo instanceof Info) {
                    Info info = (Info) sent.userInfo;
                    assertEquals("getMessageTrace;depth", depth, info.depth);
                }
            }
        }

        class Action {
            void repeat(final int depth, final IGreenThrFactory gf) {
                IGreenThr thr = gf.newThread();
                //
                final Info info = new Info("A", depth);
                MessageRelay.logInfo(info);
                thr.execute(() -> {
                    info.assertReceived(lastEvent.get());
                    System.out.println("  got A" + depth);
                    if (depth < 4)
                        repeat(depth + 1, gf);
                });
                info.assertSent(lastEvent.get(), thr);
                //
                final Info infoB = new Info("B", depth);
                MessageRelay.logInfo(infoB);
                thr.execute(() -> {
                    infoB.assertReceived(lastEvent.get());
                    System.out.println("  got B" + depth);
                });
                infoB.assertSent(lastEvent.get(), thr);
            }
        }

        class MyActor extends ActorBase<MyActor> {
            void call(int value) {
                System.out.println("  actor received " + value);
            }
        }

        System.out.println("testMessageRelay_RingBuf");
        try (IGreenThrFactory gf = new GreenThrFactory_single(4)) {
            buffer.listenToIncoming(new TraceCheck());
            gf.setMessageRelay(new MessageRelay(buffer));
            new Action().repeat(1, gf);
            final int no = 747;
            MessageRelay.logInfo("" + no);
            new MyActor().init(gf)
                    .send(a -> a.call(no));
//            gf.await(10000L);
            gf.await(0);
        }
        System.out.println("\nDone running.  Buffer dump:");
        buffer.forEach(e -> System.out.println(e.info()));
        System.out.println("done testMessageRelay_RingBuf");
        //todo? junit-test smaller parts
    }
}
