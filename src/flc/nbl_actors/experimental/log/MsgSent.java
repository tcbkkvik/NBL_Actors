/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental.log;

import flc.nbl_actors.core.IGreenThr;

import java.util.function.Supplier;

/**
 * Message event: Sent
 * Date 16.01.2015.
 *
 * @author Tor C Bekkvik
 */
public class MsgSent implements IMsgEvent {
    public final MsgId id, idParent; //parent: enables message trace-back
    public final String source; //source code/line info (from stack trace)
    public final Supplier<String> userInfo; //optional user-state information
    public final IGreenThr toThr; //target green-thread

    public MsgSent(MsgId id, MsgId idParent, Supplier<String> userInfo, String source, IGreenThr to) {
        this.id = id;
        this.idParent = idParent;
        this.source = source;
        this.userInfo = userInfo;
        this.toThr = to;
    }

    @Override
    public MsgId id() {
        return id;
    }

    public String usrInfo() {
        return userInfo == null ? "" : " : " + userInfo.get();
    }

    @Override
    public String toString() {
        return "+sent " + id + " < " + idParent + " @" + source + usrInfo();
    }
}
