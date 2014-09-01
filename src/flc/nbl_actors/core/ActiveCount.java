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
 * Active Count; Keep track of #running threads.
 * Date: 16.08.14
 *
 * @author Tor C Bekkvik
 */
public class ActiveCount {
    public class Part implements ListenerSet.IKeep<Boolean> {
        private final AtomicBoolean isActive = new AtomicBoolean();

        public boolean getActive() {
            return isActive.get();
        }

        @Override
        public void accept(Boolean active) {
            if (isActive.getAndSet(active) != active)
                signal(active ? 1 : -1);
        }
    }

    private final AtomicInteger noActive = new AtomicInteger();
    private final ListenerSet<Integer> countHandler = new ListenerSet<>();
    private final ListenerSet<Boolean> activeHandler = new ListenerSet<>();

    public void setActiveListener(Consumer<Boolean> handler) {
        handler.accept(noActive.get() > 0);
        activeHandler.addListener(handler);
    }

    public void setCountHandler(Consumer<Integer> handler) {
        countHandler.addListener(handler);
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

    public ActiveCount listenTo(IGreenThrFactory f) {
        f.setActiveListener(newParticipant());
        return this;
    }

    public ActiveCount listenTo(IGreenThrFactory... fs) {
        for (IGreenThrFactory f : fs)
            f.setActiveListener(newParticipant());
        return this;
    }

    private void signal(int delta) {
        if (delta == 0) return;
        int n0 = noActive.getAndAdd(delta);
        int n1 = n0 + delta;
        if (changedActivity(n0, n1)) {
            activeHandler.accept(n1 > 0);
        }
        countHandler.accept(n1);
    }
}
