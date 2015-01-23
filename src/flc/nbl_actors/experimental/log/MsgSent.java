/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental.log;

import flc.nbl_actors.core.IActorRef;
import flc.nbl_actors.core.IGreenThr;

import java.util.function.Supplier;

/**
 * Message event: Sent
 * <p>Date 16.01.2015.</p>
 *
 * @author Tor C Bekkvik
 */
public class MsgSent implements IMsgEvent {
    /**
     * Message Id
     */
    public final MsgId id;

    /**
     * Parent message Id. Identifies previous message in chain of events.
     */
    public final MsgId idParent;

    /**
     * source code/line info
     */
    public final StackTraceElement source;

    /**
     * optional user-supplied information
     */
    public final Supplier<String> userInfo;
    /**
     * target green-thread
     */
    public final IGreenThr targetThread;
    /**
     * target actor, or null
     */
    public final IActorRef targetActor;

    public MsgSent(MsgId id, MsgId idParent, Supplier<String> userInfo, StackTraceElement source, IGreenThr to, IActorRef targetActor) {
        this.id = id;
        this.idParent = idParent;
        this.source = source;
        this.userInfo = userInfo;
        this.targetThread = to;
        this.targetActor = targetActor;
    }

    @Override
    public MsgId id() {
        return id;
    }

    private String targetInstanceString() {
        if (targetActor == null) return "";
//        int hash = targetActor.hashCode(); todo? add some type of (unique) instance ID?
        return ":" + targetActor.getActorClass().getSimpleName();
    }

    private String userInfoString() {
        return userInfo == null ? "" : " {" + userInfo.get() + "}";
    }

    protected String infoStr() {
        return "[" + id + "]" + idParent + " at " + source
                + targetInstanceString() + userInfoString();
    }

    @Override
    public String info() {
        return toString();
    }

    @Override
    public String toString() {
        return " sent" + infoStr();
    }
}
