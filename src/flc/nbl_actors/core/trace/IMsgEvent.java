/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core.trace;

/**
 * Message event.
 * <p>Date 16.01.2015.</p>
 *
 * @author Tor C Bekkvik
 */
public interface IMsgEvent {
    /**
     * Unique message identifier
     *
     * @return id
     */
    MsgId id();

    /**
     * Identify parent in message-tree (caused this message to be sent)
     *
     * @return parent Id
     */
    MsgId parentId();

    /**
     * Minimal info; Complete in context with other logged events.
     * Call toString() for more self-contained info.
     *
     * @return info
     */
    String info();
}
