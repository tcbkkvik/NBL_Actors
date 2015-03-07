/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.core;


import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/**
 * Asynchronous return value. Result may not be immediately available.
 * Date: 17.11.13
 *
 * @author Tor C Bekkvik
 */
public class ASyncValue<T> implements IASync<T>, Consumer<T> {
    private final AtomicBoolean isSet = new AtomicBoolean();
    private volatile Consumer<T> cons;
    private volatile T value;

    /**
     * With initial value; null is allowed (isSet==false)
     *
     * @param value initial value
     */
    public ASyncValue(T value) {
        this.value = value;
    }

    /**
     * Initiates with value == null
     */
    public ASyncValue() {
    }

    /**
     * Update value by applying unary operator
     *
     * @param operator unary operator
     */
    public void update(UnaryOperator<T> operator) {
        synchronized (isSet) {
            value = operator.apply(value);
        }
    }

    /**
     * Set result value.
     *
     * @param data value
     */
    @Override
    public void accept(T data) {
        value = data;
        accept();
    }

    /**
     * Signal ready value.
     */
    public void accept() {
        isSet.set(true);
        if (cons != null)
            cons.accept(value);
    }

    @Override
    public void result(final Consumer<T> consumer) {
        if (isSet.get())
            consumer.accept(value);
        else
            cons = cons == null ? consumer : v -> {
                cons.accept(v);
                consumer.accept(v);
            };
    }

}
