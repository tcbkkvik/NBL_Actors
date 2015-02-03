/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core.trace;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Get message traces
 * <p>Date: 23.01.2015
 * </p>
 *
 * @author Tor C Bekkvik
 */
public interface IMsgEventTracer {

    /**
     * Get message trace, starting from given message
     *
     * @param lastEvent message event to trace from.
     * @return trace List
     */
    default List<IMsgEvent> getMessageTrace(IMsgEvent lastEvent) {
        return getMessageTrace(lastEvent == null ? null : lastEvent.id());
    }

    /**
     * Get message trace, starting from given message Id.
     *
     * @param lastId message event Id to trace from.
     * @return trace List
     */
    default List<IMsgEvent> getMessageTrace(MsgId lastId) {
        List<IMsgEvent> list = new LinkedList<>();
        getMessageTrace(lastId, list::add);
        return list;
    }

    /**
     * Get message trace, starting from given message Id.
     *
     * @param msgId    message event Id to trace from.
     * @param consumer event consumer
     */
    void getMessageTrace(MsgId msgId, Consumer<? super IMsgEvent> consumer);

    /**
     * Handle error
     *
     * @param msgId Id of runnable message throwing exception
     * @param error Runtime Exception (from Runnable.run())
     */
    void onError(MsgId msgId, RuntimeException error);
}
