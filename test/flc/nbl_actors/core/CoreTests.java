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

import static org.junit.Assert.assertEquals;

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
        ac.setActiveHandler(a -> assertEquals(a, refCount.get() > 0));
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
}
