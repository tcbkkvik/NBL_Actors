/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core.trace;

import flc.nbl_actors.core.*;

/**
 * Message event: Received messaged caused an exception
 * <p>Date: 02.02.2015
 * </p>
 *
 * @author Tor C Bekkvik
 */
public class MsgEventError implements IMsgEvent {
    public final MsgEventReceived received;
    public final Exception exception;
    public final String shortTrace;

    public MsgEventError(MsgEventReceived received, Exception exception) {
        this.received = received;
        this.exception = exception;
        shortTrace = ThreadContext.shortTrace(exception);
    }

    @Override
    public MsgId id() {
        return received.id();
    }

    @Override
    public MsgId parentId() {
        return received.parentId();
    }

    @Override
    public String info() {
        return received.info() + shortTrace;
    }

    @Override
    public String toString() {
        return received.toString() + shortTrace;
    }
}
