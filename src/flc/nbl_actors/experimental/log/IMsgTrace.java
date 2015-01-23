/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental.log;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * <p>Date: 23.01.2015
 * </p>
 *
 * @author Tor C Bekkvik
 */
public interface IMsgTrace {

    void forEach(Consumer<? super IMsgEvent> action);

    /**
     * Get message trace.
     *
     * @param last Last message to be traced from.
     * @return List of messages, found by backward tracing
     */
    default List<IMsgEvent> getMessageTrace(IMsgEvent last) {
        return getMessageTrace(last == null ? null : last.id());
    }

    default List<IMsgEvent> getMessageTrace(MsgId aId) {
        List<IMsgEvent> list = new LinkedList<>();
        getMessageTrace(aId, list::add);
        return list;
    }

    void getMessageTrace(MsgId aId, Consumer<? super IMsgEvent> aConsumer);
}
