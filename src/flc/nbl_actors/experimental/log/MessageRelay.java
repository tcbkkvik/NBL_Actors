/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental.log;

import flc.nbl_actors.core.IGreenThr;
import flc.nbl_actors.core.IMessageRelay;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Standard message relay implementation.
 * Calls to {@link #intercept(Runnable, flc.nbl_actors.core.IGreenThr)}
 * are logged per thread to listener
 * instances, generated by given {@link IMsgListenerFactory}
 * Date 16.01.2015.
 *
 * @author Tor C Bekkvik
 */
public class MessageRelay implements IMessageRelay {

    private static final ThreadLocal<TContext> threadContext
            = ThreadLocal.withInitial(TContext::new);
    private final IMsgListenerFactory listenerFactory;

    /**
     * @param factory Called once per real thread.
     *                Enables parallel event logging.
     *                The generator must return either
     *                (i) a single synchronized listener instance, or
     *                (ii) multiple listener, where events later normally
     *                gets merged for further processing.
     */
    public MessageRelay(IMsgListenerFactory factory) {
        listenerFactory = factory;
    }

    /**
     * Set user-defined trace information, to be stored in a {@link ThreadLocal} context.
     * It is later retrieved in {@link #intercept(Runnable, flc.nbl_actors.core.IGreenThr)},
     * where it gets logged as part of a "message sent" event.
     *
     * @param info Info string (may be consumed later)
     */
    public static void setTraceInfo(Supplier<String> info) {
        threadContext.get().setUserInfo(info);
    }

    private String stackLine(int lev) {
        StringBuilder sb = new StringBuilder(" ");
        final String coreP = "flc.nbl_actors.core";
        int no = 0;
        Exception ex = new Exception("");
//                ex.printStackTrace();
        StackTraceElement[] trace = ex.getStackTrace();
        for (StackTraceElement se : trace) {
            if (++no >= lev && !se.getClassName().startsWith(coreP)) {
                sb.append(se.toString());
                break;
            }
        }
        return sb.toString();
    }

    private TContext context() {
        TContext c = threadContext.get();
        if (c.listener == null) {
            c.listener = listenerFactory.forkListener();
        }
        return c;
    }

    @Override
    public Runnable intercept(Runnable msg, IGreenThr thread) {
        TContext ctx = context();
//        if (msg instanceof ActorMessage) {
//            ActorMessage actorMsg = (ActorMessage) msg;
//            //todo this is an actor; log extra info?
//        }
        final MsgSent sendEvent = new MsgSent(
                ctx.nextId(), ctx.getParentId(), ctx.getUserInfo(), stackLine(3), thread);
        ctx.setUserInfo(null);
        ctx.listener.accept(sendEvent);
        return () -> {
            TContext ctx2 = context();
            ctx2.setParentId(sendEvent.id);
            ctx2.listener.accept(new MsgReceived(sendEvent));
            msg.run();
        };
    }

    /**
     * Thread Context.
     */
    public static class TContext {
        private static AtomicInteger currThrNo = new AtomicInteger();
        private final int thrNo = currThrNo.incrementAndGet();
        private int msgNo; //starts at 1 (0 is undefined)
        private MsgId parentId;
        private Supplier<String> userInfo;
        private Consumer<IMsgEvent> listener;

        private void setParentId(MsgId parentId) {
            this.parentId = parentId;
        }

        public void setUserInfo(Supplier<String> userInfo) {
            this.userInfo = userInfo;
        }

        public MsgId nextId() {
            return new MsgId(thrNo, ++msgNo);
        }

        public MsgId getParentId() {
            return parentId;
        }

        public Supplier<String> getUserInfo() {
            return userInfo;
        }
    }
    //todo unit tests
}
