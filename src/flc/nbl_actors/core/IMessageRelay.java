/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

/**
 * Message relay. Listen to {@link java.lang.Runnable}
 * messages sent and received.
 * Date 16.01.2015.
 *
 * @author Tor C Bekkvik
 */
public interface IMessageRelay {

    /**
     * Intercept runnable message.
     * To be called when message is sent. The returned (possibly instrumented)
     * message should be added to the thread-queue instead of the original message.
     * The message has been received when {@link Runnable#run()} is called.
     *
     * @param msg    original message
     * @param thread green-thread to execute the message
     * @return original message, optionally wrapped in another
     * Runnable for listening purposes. (eg. message tracing & debugging)
     */
    Runnable intercept(Runnable msg, IGreenThr thread);
}
