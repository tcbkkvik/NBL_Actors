/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core.trace;

import flc.nbl_actors.core.IActorRef;
import flc.nbl_actors.core.IGreenThr;

import java.util.function.Supplier;

/**
 * Message event: Sent
 * <p>Date 16.01.2015.</p>
 *
 * @author Tor C Bekkvik
 */
public class MsgEventSent implements IMsgEvent {
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
     * optional user-supplied log information
     */
    public final Supplier<String> logInfo;
    /**
     * target green-thread
     */
    public final IGreenThr targetThread;
    /**
     * target actor, or null
     */
    public final IActorRef targetActor;

    public MsgEventSent(MsgId id, MsgId idParent, Supplier<String> logInfo, StackTraceElement source, IGreenThr to, IActorRef targetActor) {
        this.id = id;
        this.idParent = idParent;
        this.source = source;
        this.logInfo = logInfo;
        this.targetThread = to;
        this.targetActor = targetActor;
    }

    @Override
    public MsgId id() {
        return id;
    }

    @Override
    public MsgId parentId() {
        return idParent;
    }

    private String targetInstanceString() {
        if (targetActor == null) return "";
//        int hash = targetActor.hashCode(); to do?? add some type of (unique) instance ID?
        return ":" + targetActor.getActorClass().getSimpleName();
    }

    private String userInfoString() {
        return logInfo == null ? "" : " {" + logInfo.get() + "}";
    }

    protected String infoStr() {
        return "[" + id + "]" + idParent + (source == null ? " " : " at " + source)
                + targetInstanceString() + userInfoString();
    }

    @Override
    public String info() {
        return toString();
    }

    @Override
    public String toString() {
        return "sent" + infoStr();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MsgEventSent)) return false;

        MsgEventSent that = (MsgEventSent) o;

        if (!id.equals(that.id)) return false;
        if (idParent != null ? !idParent.equals(that.idParent) : that.idParent != null) return false;
        if (source != null ? !source.equals(that.source) : that.source != null) return false;
        if (targetActor != null ? !targetActor.equals(that.targetActor) : that.targetActor != null) return false;
        if (!targetThread.equals(that.targetThread)) return false;
        if (logInfo != null ? !logInfo.equals(that.logInfo) : that.logInfo != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
