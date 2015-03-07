/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.util.Objects;

/**
 * Thread-local context, used internally by this library.
 * <p>
 * Date: 06.12.13
 * </p>
 *
 * @author Tor C Bekkvik
 */
public class ThreadContext {

    private static final ThreadLocal<ThreadContext> CONTEXT = ThreadLocal.withInitial(ThreadContext::new);

    public static ThreadContext get() {
        return CONTEXT.get();
    }

    private IGreenThrFactory factory;
    private IGreenThr thread;

    private ThreadContext() {
    }

    public static String shortTrace(Exception e) {
        if (e == null) return "";
        String msg = "  :" + e.getClass().getSimpleName() + "(" + e.getMessage() + ")";
        StackTraceElement[] st = e.getStackTrace();
        return (st == null || st.length == 0)
                ? msg
                : msg + " @" + st[0].getFileName()
                + ":" + st[0].getLineNumber();
    }

    public static void logTrace(Exception e, String s) {
        System.out.println(shortTrace(e) + s);
    }

    /**
     * Internal! - Register current thread factory.
     * (Called from factory implementation constructor)
     *
     * @param factory thread factory
     * @return this
     */
    public ThreadContext setFactory(IGreenThrFactory factory) {
        this.factory = Objects.requireNonNull(factory);
        return this;
    }

    /**
     * Internal! - Register current thread.
     * (Called from IGreenThr implementations before message.run().
     * NB should not call from user code)
     *
     * @param thr current thread
     * @return this
     */
    public ThreadContext setThread(IGreenThr thr) {
        thread = thr;
        return this;
    }

    /**
     * Ger current thread factory
     *
     * @return factory
     */
    public IGreenThrFactory getFactory() {
        return factory;
    }

    /**
     * Get current thread.
     *
     * @return current
     */
    public IGreenThr getThread() {
        return thread;
    }

    /**
     * Internal! - called from IGreenThr implementations before message.run()
     */
    public void beforeRun() {
    }

}
