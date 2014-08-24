/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/**
 * Track single-thread activity.
 * Date: 08.01.14
 *
 * @author Tor C Bekkvik
 */
public class ThreadActivity {
    private final ListenerSet<Boolean> listener = new ListenerSet<>();
    private final AtomicBoolean isActive = new AtomicBoolean();

    /**
     * Set if active
     *
     * @param active true if active
     * @return true if changed
     */
    public boolean setActive(boolean active) {
        if (isActive.getAndSet(active) != active) {
            listener.accept(active);
            return true;
        }
        return false;
    }

    /**
     * Set active listener. (false when no threads have remaining work/messages).
     *
     * @param listener active listener
     */
    public void setListener(Consumer<Boolean> listener) {
        synchronized (this.listener) {
            listener.accept(isActive.get());
            this.listener.addListener(listener);
        }
    }
}
