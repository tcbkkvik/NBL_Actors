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
 * Immediately available result.
 * Date: 10.11.13
 *
 * @author Tor C Bekkvik
 */
public class ASyncDirect<T> implements IASync<T> {
    private final T value;

    /**
     * Instantiate immutable value
     *
     * @param value Value
     */
    public ASyncDirect(T value) {
        this.value = value;
    }

    /**
     * Set result callback (Called immediately).
     *
     * @param consumer result callback
     */
    @Override
    public void result(Consumer<T> consumer) {
        consumer.accept(value);
    }

}
