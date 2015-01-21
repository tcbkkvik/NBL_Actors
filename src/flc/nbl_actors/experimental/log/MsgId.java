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
 * <p>Date 16.01.2015.</p>
 *
 * @author Tor C Bekkvik
 */
public class MsgId {
    public final int threadNo; //unique real thread number
    public final int messageNo; //unique per thread

    /**
     * @param threadNo thread number
     * @param messageNo    message number
     */
    public MsgId(int threadNo, int messageNo) {
        this.threadNo = threadNo;
        this.messageNo = messageNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MsgId)) return false;

        MsgId msgId = (MsgId) o;

        if (messageNo != msgId.messageNo) return false;
        //noinspection RedundantIfStatement
        if (threadNo != msgId.threadNo) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return messageNo;
    }

    @Override
    public String toString() {
        return threadNo + "." + messageNo;
    }
}
