/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental.log;

/**
 * Message event: Received
 * <p>Date 16.01.2015.</p>
 *
 * @author Tor C Bekkvik
 */
public class MsgEventReceived implements IMsgEvent {
    public final MsgEventSent sent;
    public final int toThrNo;

    public MsgEventReceived(MsgEventSent sent, int toRealThreadNo) {
        this.sent = sent;
        this.toThrNo = toRealThreadNo;
    }

    @Override
    public MsgId id() {
        return sent.id;
    }

    @Override
    public String info() {
        return " run[" + sent.id + "] thread:" + toThrNo;
    }

    @Override
    public String toString() {
        return "sent!" + sent.infoStr() + " thread:" + toThrNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MsgEventReceived)) return false;

        MsgEventReceived that = (MsgEventReceived) o;

        if (toThrNo != that.toThrNo) return false;
        if (!sent.equals(that.sent)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return toThrNo;
    }
}
