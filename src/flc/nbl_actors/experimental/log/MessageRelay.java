/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental.log;

import flc.nbl_actors.core.*;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Standard message relay implementation.
 * Routes message events to listeners generated by given IMsgListenerFactory
 * <p>Date 16.01.2015
 * </p>
 *
 * @author Tor C Bekkvik
 */
public class MessageRelay implements IMessageRelay {

    private static final ThreadLocal<TContext> threadContext
            = ThreadLocal.withInitial(TContext::new);
    private final IMsgListenerFactory listenerFactory;
    private volatile boolean isReduceLog;
    private volatile boolean isDisableLog;

    /**
     * @param factory Called once per thread.
     *                Enables parallel event logging.
     *                The factory should return either
     *                (i) a single synchronized listener instance, or
     *                (ii) a separate listener per call (which may merge events).
     *                Returned listeners should preferably implement IMsgTrace
     *                to enable message-trace queries.
     */
    public MessageRelay(IMsgListenerFactory factory) {
        listenerFactory = factory;
    }

    /**
     * Reduce logging to help performance.
     * <p>If true: avoids calling Throwable.getStackTrace() for messages
     * where the user has supplied info via MessageRelay.logInfo().
     * </p>
     *
     * @param isReduce true to reduce (default false)
     */
    public void setReduceLog(boolean isReduce) {
        isReduceLog = isReduce;
    }

    /**
     * Disable logging (Can be re-enabled again)
     *
     * @param isDisable true = disable, false = normal logging
     */
    public void setDisableLog(boolean isDisable) {
        isDisableLog = isDisable;
    }

    /**
     * Set user-defined log info in a ThreadLocal variable (java.lang.ThreadLocal), to be
     * attached to next message-send log event.
     * Useful for adding labels and state information to message-trace.
     *
     * @param info extra log information
     */
    public static void logInfo(Supplier<String> info) {
        threadContext.get().setLogInfo(info);
    }

    public static void logInfo(String info) {
        logInfo(() -> info);
    }

    /**
     * Get Thread-Local context (state)
     *
     * @return context
     */
    public static TContext getContext() {
        return threadContext.get();
    }

    private static StackTraceElement stackElement(int lev) {
        final String coreP = "flc.nbl_actors.core";
        int no = 0;
        Throwable ex = new Throwable();
//                ex.printStackTrace();
        StackTraceElement[] trace = ex.getStackTrace();
        for (StackTraceElement se : trace) {
            if (++no >= lev && !se.getClassName().startsWith(coreP)) {
                return se;
            }
        }
        return null;
    }

    private class Interceptor implements Function<Runnable, Runnable> {
        final IGreenThr thread;
        final Consumer<IMsgEvent> listener;

        public Interceptor(IGreenThr thread, Consumer<IMsgEvent> listener) {
            this.thread = thread;
            this.listener = listener;
        }

        @Override
        public Runnable apply(Runnable msg) {
            if (isDisableLog)
                return msg;
            TContext ctx = threadContext.get();
            IActorRef targetActor = null;
            if (msg instanceof ActorMessage) {
                ActorMessage am = (ActorMessage) msg;
                targetActor = am.ref;
            }
            Supplier<String> info = ctx.getLogInfo();
            StackTraceElement stackE = (isReduceLog && info != null)
                    ? null
                    : stackElement(5);
            final MsgEventSent sendEvent = new MsgEventSent(
                    ctx.nextId(), ctx.getParentId(), info, stackE, thread, targetActor);
            ctx.sent(sendEvent);
            return () -> {
                TContext ctx2 = threadContext.get();
                ctx2.received(new MsgEventReceived(sendEvent, ctx2.thrNo), listener);
                msg.run();
                ctx2.reset();
            };
        }
    }

    @Override
    public Function<Runnable, Runnable> newInterceptor(IGreenThr ownerThread) {
        return new Interceptor(ownerThread, listenerFactory.forkListener());
    }

    public static List<IMsgEvent> getMessageTrace() {
        return threadContext.get().getMessageTrace();
    }

    public static void printMessageTrace(PrintStream s) {
        threadContext.get().printMessageTrace(s);
    }

    public static void printMessageTrace() {
        threadContext.get().printMessageTrace();
    }

    /**
     * Thread Context.
     */
    public static class TContext {
        private static AtomicInteger currThrNo = new AtomicInteger();
        private final int thrNo = currThrNo.incrementAndGet();
        private int msgNo; //starts at 1 (0 is undefined)
        private Supplier<String> logInfo;
        private Consumer<IMsgEvent> listener;
        private MsgEventReceived lastReceived;

        private void received(MsgEventReceived rcv, Consumer<IMsgEvent> li) {
            listener = li;
            lastReceived = rcv;
            li.accept(rcv);
        }

        private void reset() {
            lastReceived = null;
            listener = null;
        }

        private void sent(MsgEventSent rec) {
            logInfo = null;
            if (listener != null)
                listener.accept(rec);
        }

        public void setLogInfo(Supplier<String> info) {
            logInfo = info;
        }

        public MsgId nextId() {
            return new MsgId(thrNo, ++msgNo);
        }

        public MsgId getParentId() {
            return lastReceived == null ? null : lastReceived.id();
        }

        public Supplier<String> getLogInfo() {
            return logInfo;
        }

        public MsgEventReceived getLastReceived() {
            return lastReceived;
        }

        /**
         * Attempt to produce a message trace-back for this thread context.
         * <p>(returns empty list if IMsgListenerFactory does not implement IMsgTrace)
         * </p>
         *
         * @return message trace
         */
        public List<IMsgEvent> getMessageTrace() {
            List<IMsgEvent> list = new LinkedList<>();
            if (lastReceived != null && listener instanceof IMsgEventTracer) {
                ((IMsgEventTracer) listener)
                        .getMessageTrace(lastReceived.id(), list::add);
            }
            return list;
        }

        public void printMessageTrace(PrintStream s) {
            for (IMsgEvent event : getMessageTrace())
                s.println("\t" + event);
        }

        public void printMessageTrace() {
            printMessageTrace(System.err);
        }

    }
}
