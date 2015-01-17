/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental.log;

/**
 * Message event Id
 * Date 16.01.2015.
 *
 * @author Tor C Bekkvik
 */
public class MsgId {
    public final int threadNo; //unique real thread number
    public final int msgNo; //unique per real thread

    /**
     * @param threadNo thread number
     * @param msgNo    message number
     */
    public MsgId(int threadNo, int msgNo) {
        this.threadNo = threadNo;
        this.msgNo = msgNo;
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MsgId)) return false;

        MsgId msgId = (MsgId) o;

        if (msgNo != msgId.msgNo) return false;
        if (threadNo != msgId.threadNo) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return msgNo;
    }

    @Override
    public String toString() {
        return threadNo + ":" + msgNo;
    }
}
