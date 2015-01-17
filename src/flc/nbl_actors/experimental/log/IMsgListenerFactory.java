/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental.log;

import java.util.function.Consumer;

/**
 * Message listener factory
 * Date 16.01.2015.
 *
 * @author Tor C Bekkvik
 */
public interface IMsgListenerFactory {
    /**
     * Get next listener. Intended to be called once
     * from each event-producing thread.
     *
     * @return Event consumer
     */
    Consumer<IMsgEvent> forkListener();
}
