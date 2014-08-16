/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Active Count utility; Keep continuous track of how many (concurrent) participants
 * are active.
 * Date: 16.08.14
 *
 * @author Tor C Bekkvik
 */
@SuppressWarnings("UnusedDeclaration")
public class ActiveCount {
    public class Part implements Consumer<Boolean> {
        private final AtomicBoolean isActive = new AtomicBoolean();

        @Override
        public void accept(Boolean active) {
            if (isActive.getAndSet(active) != active)
                signal(active ? 1 : -1);
        }
    }

    private final AtomicInteger noActive = new AtomicInteger();
    private volatile Consumer<Boolean> activeHandler = a -> {
    };
    private volatile Consumer<Integer> countHandler = n -> {
    };

    public void setActiveHandler(Consumer<Boolean> handler) {
        activeHandler = handler;
        handler.accept(noActive.get() > 0);
    }

    public void setCountHandler(Consumer<Integer> handler) {
        countHandler = handler;
        handler.accept(noActive.get());
    }

    private static boolean changedActivity(int old, int now) {
        return (old > 0) != (now > 0);
    }

    public Part newParticipant() {
        return new Part();
    }

    public int getCount() {
        return noActive.get();
    }

    private void signal(int delta) {
        int n0 = noActive.getAndAdd(delta);
        int n1 = n0 + delta;
        if (changedActivity(n0, n1)) {
            synchronized (noActive) {
                activeHandler.accept(n1 > 0);
            }
        }
        countHandler.accept(n1);
    }
}
