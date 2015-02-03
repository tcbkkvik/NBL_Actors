/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
//Run with java 8

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
//Ok with junit-4.10.jar


/**
 * Date: 02.10.13
 *
 * @author Tor C Bekkvik
 */
@SuppressWarnings("UnusedDeclaration,UnnecessaryLocalVariable")
public class ActorTests {

    public interface IFactories {
        void loop(int thrCount, ThrFactories.IConsumer<IGreenThrFactory> consumer)
                throws InterruptedException;
    }

    protected static IFactories factories;

    @BeforeClass
    public static void beforeClass() {
        factories = (thrCount, consumer) -> {
            new ThrFactories(consumer)
                    .runWith(new GreenThrFactory_single(thrCount))
                    .runWith(new GreenThr_single(false))
                    .runWith(new GreenThr_zero())
            ;
        };
    }

    @Test
    public void testContext_getFactory() throws InterruptedException {
        factories.loop(4, factory -> {
            log("testContext_getFactory");
            IGreenThrFactory f = ThreadContext.get().getFactory();
            if (factory != f)
                fail("factory != ThreadContext.get().getFactory(): "
                        + factory.getClass().getSimpleName());
        });
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception ignore) {

        }
    }

    static void log(Object o) {
        System.out.println(o);
    }

    static void log2(Object o) {
        System.out.print(o);
    }


    static void tstGreenThrFactory(IGreenThrFactory factory) throws InterruptedException {
        String text = "tstGreenThrFactory(" + className(factory) + ")";
        log(text + "..");
        IGreenThr thread = factory.newThread();

        final int NUM_JOBS = 10;
        AtomicInteger sequence = new AtomicInteger(0);

        class Job implements Runnable {
            int id;
            int seq;

            Job(int no) {
                id = no;
            }

            @Override
            public void run() {
                sleep(id);
                seq = sequence.incrementAndGet();
                sleep(10);
                int seq_after = sequence.get();
                assertEquals(seq, seq_after);
                if (seq_after >= NUM_JOBS) {
                    factory.shutdown();
                    log("   shutdown ..");
                }
            }
        }
        for (int i = 0; i < NUM_JOBS; i++) {
            thread.execute(new Job(i));
        }
        factory.await(0L);
        assertEquals(sequence.get(), NUM_JOBS);
        log("   OK: " + factory.getClass().getSimpleName());
    }

    @Test
    public void testGreenThrFactory() throws InterruptedException {
        log("\n\ntestGreenThrFactory..");
        factories.loop(4, ActorTests::tstGreenThrFactory);
        log(" done testGreenThrFactory");
    }

    public static void tstGreenThr_order(IGreenThrFactory factory) throws InterruptedException {
        String text = "  tstGreenThr_order(" + className(factory) + ")";
        log(text + "..");

        final int NUM_JOBS = 8;
        final LinkedList<Integer> list0 = new LinkedList<>();
        final ArrayBlockingQueue<Integer> list = new ArrayBlockingQueue<>(10);
        final CountDownLatch latch = new CountDownLatch(NUM_JOBS);
        IGreenThr thr = factory.newThread();
        thr.execute(() -> {
            for (int ix = 0; ix < NUM_JOBS; ix++) {
                if (ix >= NUM_JOBS / 2) {
//                    todo? factory.reverseOrder(true);
                    list0.addFirst(ix);
                } else list0.add(ix);
                final int index = ix;
                thr.execute(() -> {
                    list.add(index);
                    latch.countDown();
                });
            }
        });
        latch.await();
        factory.shutdown();
        factory.await(0L);
        list.stream().forEach(index -> log2(" " + index));
        Integer[] arr0 = list0.toArray(new Integer[list0.size()]);
        Integer[] arr1 = list.toArray(new Integer[list.size()]);
        if (!Arrays.equals(arr0, arr1)) {
            int xx = 0;
        }
        assertArrayEquals(arr0, arr1);
        log("");
    }

    //    @Test
    public void testGreenThr_order() throws InterruptedException {
        log("\n\ntestGreenThr_order..");
        factories.loop(1, ActorTests::tstGreenThr_order);
        log(" done testGreenThr_order");
    }

    static class ActImpl {
        AtomicInteger callCount = new AtomicInteger();

        public void action() {
            int val = callCount.incrementAndGet();
            sleep(10);
            assertEquals(val, callCount.get());
        }
    }

    static String className(Object obj) {
        return obj.getClass().getSimpleName();
    }

    public static void testActorRef(IGreenThrFactory threads)
            throws InterruptedException {
        String text = "testActorRef(" + className(threads) + ")";
        log(text + "..");
        final ActImpl ai = new ActImpl();
        final IActorRef<ActImpl> ref = new ActorRef<>(threads, ai);
        final int N = 10;
        for (int n = 0; n < N; ++n) {
            threads.newThread().execute(
                    () -> ref.send(ActImpl::action)
                    //concurrent send,
                    //sequential receive
            );
        }
        Thread.sleep(10);
        threads.setEmptyListener(() -> {
            assertEquals(N, ai.callCount.get());
            log(" ..empty ok");
        });
        System.out.println(" wait..");
        if (!threads.await(3000)) {
            log(" testActorRef await timeOut !");
            fail();
        }
        assertEquals(N, ai.callCount.get());
        log(" OK: testActorRef");
    }

    @Test
    public void testActorRef() throws InterruptedException {
        final int N = 1;
        for (int n = 0; n < N; ++n) {
            log(" " + n);
            factories.loop(4, ActorTests::testActorRef);
        }
    }


    /**
     * Tests IGreenThrFactory.setEmptyListener via threads.close()
     *
     * @param threads Thread Factory
     * @throws InterruptedException if the current thread is interrupted while waiting.
     */
    static void tst_FactoryEmptyListeners(IGreenThrFactory threads) throws InterruptedException {
        String text = "tst_FactoryEmptyListeners(" + className(threads) + ")";
        log(text + "..");
        final int N = 4;
        final AtomicInteger counter = new AtomicInteger(34);
        //-- test:  decrement counter and send message around actor-ring.
        //--  when done, counter should be == 0
        CountDownLatch latch = new CountDownLatch(1);
        class RingNode extends ActorBase<RingNode> {
            IActorRef<RingNode> next;

            RingNode(IActorRef<RingNode> next) {
                this.next = next;
            }

            void passOn() {
//                log(" passOn " + counter.get());
                sleep(10);
                if (counter.decrementAndGet() > 0) {
                    next.send(RingNode::passOn);
                } else {
                    latch.countDown();
                }
            }
        }
        RingNode nod = new RingNode(null);
        IActorRef<RingNode> ref = nod.init(threads);
        for (int i = 0; i < N; i++) {
            ref = new RingNode(ref).init(threads);
        }
        nod.next = ref;
        ref.send(RingNode::passOn);
        AtomicBoolean done = new AtomicBoolean(false);
        threads.setEmptyListener(() -> {
            done.set(true); //NB if called too early => NO latch.countDown()
            threads.shutdown();
        });
//        latch.await();
//        threads.await(20000);
        threads.await(0);
        if (latch.getCount() > 0)
            fail("threads.empty => shutdown before messages done! (latch.getCount() > 0)");
        assertTrue(done.get());
        assertEquals(counter.get(), 0);
        log("OK " + text);
    }

    @Test
    public void testFactory_EmptyListeners() throws InterruptedException {
        factories.loop(4, ActorTests::tst_FactoryEmptyListeners);
    }

    @Test
    public void testThreadContext_threadsSet() throws InterruptedException {
        factories.loop(4, fact -> {
            log("testThreadContext_threadsSet");
            final IGreenThr thr = fact.newThread();
            thr.execute(() -> {
                IGreenThr t = ThreadContext.get().getThread();
                if (thr != t)
                    fail("ThreadContext.get().getThread() != my own thread");
                fact.shutdown();
            });
            fact.await(0);
        });
    }

}
