/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;

import java.util.function.Function;

/**
 * Message relay. Listen to Runnable messages sent and received.
 * <p>Date 16.01.2015.</p>
 *
 * @author Tor C Bekkvik
 */
public interface IMessageRelay {

    /**
     * Make new interceptor.
     * <p>The interceptor should return original Runnable message,
     * optionally wrapped in a containing Runnable for listening purposes.
     * (eg. message tracing and debugging)
     * </p>
     *
     * @param ownerThread owner thread to be using the returned interceptor
     * @return interceptor interceptor function
     */
    Function<Runnable, Runnable> newInterceptor(IGreenThr ownerThread);
}
