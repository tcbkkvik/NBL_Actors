/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import org.junit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/**
 * Date: 16.08.14
 *
 * @author Tor C Bekkvik
 */
public class CoreTests {
    @Test
    public void testActiveCount() {
        //--- init ..
        final int partCount = 5;
        final ActiveCount ac = new ActiveCount();
        final List<ActiveCount.Part> parts = new ArrayList<>();
        for (int i = 0; i < partCount; i++)
            parts.add(ac.newParticipant());
        AtomicInteger refCount = new AtomicInteger();
        ac.setCountHandler(count -> assertEquals(refCount.get(), (int) count));
        ac.setActiveListener(a -> assertEquals(a, refCount.get() > 0));
        //---
        //increase #active
        for (ActiveCount.Part p : parts) {
            refCount.incrementAndGet();
            p.accept(true);
            assertEquals(refCount.get(), ac.getCount());
        }
        //decrease #active
        for (ActiveCount.Part p : parts) {
            refCount.decrementAndGet();
            p.accept(false);
            assertEquals(refCount.get(), ac.getCount());
        }
    }

    @Test
    public void testThreadActivity() {
        System.out.println("testThreadActivity..");
        final ThreadActivity ta = new ThreadActivity();
        class Listen implements Consumer<Boolean> {
            int no, no2;
            boolean active, active2;

            @Override
            public void accept(Boolean isActive) {
                ++no;
                assertTrue(isActive != active);
                active = isActive;
                ta.setListener(a -> {
                    ++no2;
                    active2 = a;
                    //to do assert what??
                    //java.util.ConcurrentModificationException ?
                });
            }
        }

        class ListenN extends Listen implements ListenerSet.IKeep<Boolean> {
        }
        Listen li = new Listen(); //non-durable listener (2 events: init + next ta.setActive)
        ListenN liN = new ListenN();//durable listener (N events: multiShot)
        boolean isActive = true;
        //-----------------------------------
        ta.setActive(isActive);
        ta.setListener(li);
        ta.setListener(liN);
        for (int i = 1; i < 5; i++) {
            System.out.println(i);
            if (i <= 2) {
                assertEquals(li.active, isActive);
                assertEquals(li.no, i);
            }
            assertEquals(liN.active, isActive);
            assertEquals(liN.no, i);
            isActive = !isActive;
            ta.setActive(isActive);
            ta.setActive(isActive);
        }
        assertEquals(li.no, 2);
        System.out.println("ok");
    }

    @Test
    public void testListenerSet() {
        final ListenerSet<Integer> ls = new ListenerSet<>();
        class Listen implements Consumer<Integer> {
            int ev, no, ev2, no2;

            @Override
            public void accept(Integer event) {
                if (event < 0) {
                    ev2 = event;
                    ++no2;
                } else {
                    ev = event;
                    ++no;
                }
                log(event);
            }

            void log(int event) {
                System.out.println(" e: " + event);
                if (no > 1) return;
                ls.addListener(e -> System.out.println(" listener2: " + event));
                ls.accept(-ev);
            }
        }

        class ListenK extends Listen implements ListenerSet.IKeep<Integer> {
            @Override
            void log(int event) {
                System.out.println(" eK: " + event);
            }
        }

        Listen lis = new Listen();
        ListenK lisK = new ListenK();
        ls.addListener(lis);
        ls.addListener(lisK);

        final int N = 5;
        for (int i = 1; i < N; i++) {
            ls.accept(i);
            assertEquals(1, lis.no);
            assertEquals(i, lisK.ev);
            assertEquals(i, lisK.no);
        }
    }
}
