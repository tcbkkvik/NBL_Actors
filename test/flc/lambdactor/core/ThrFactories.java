/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.core;


/**
 * Date: 20.07.14
 *
 * @author Tor C Bekkvik
 */
public class ThrFactories {
    public interface IConsumer<E> {
        void accept(E e) throws InterruptedException;
    }

    private final IConsumer<IGreenThrFactory> cons;
    private int no;

    public ThrFactories(IConsumer<IGreenThrFactory> consumer) {
        cons = consumer;
    }

    public ThrFactories runWith(IGreenThrFactory factory) throws InterruptedException {
        if (factory instanceof GreenThrFactory_single)
            ((GreenThrFactory_single) factory)
                    .setExceptionHandler(Exception::printStackTrace);

        if (cons != null) {
            ActorTests.log("\nTesting (" + (++no) + ") " + ActorTests.className(factory) + " ..");
            cons.accept(factory);
            factory.close();
            factory.await(300);
        }
        return this;
    }
}
