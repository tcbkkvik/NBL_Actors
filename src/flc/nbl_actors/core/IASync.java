/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;


import java.util.function.*;

/**
 * Asynchronous call response. Represents a future value, consumed without blocking.
 * (Used in {@link IGreenThr} and {@link IActorRef} call(*) methods)
 * <pre>
 * Example:
 * {@code
 *     IASync<Float> future = longCalculation();
 *     future.result(v ->
 *             System.out.println("value: " + v)
 *     );
 * }
 * </pre>
 * Date: 10.11.13
 *
 * @param <T> result type
 * @author Tor C Bekkvik
 */
public interface IASync<T> {
    /**
     * Set future result handler
     *
     * @param consumer called from caller or producer thread.
     */
    void result(Consumer<T> consumer);
}
