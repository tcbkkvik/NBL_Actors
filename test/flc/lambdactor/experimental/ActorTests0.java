/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.experimental;

import flc.lambdactor.core.*;
import org.junit.BeforeClass;

import java.util.concurrent.Executors;

/**
 * Date: 29.07.14
 *
 * @author Tor C Bekkvik
 */
public class ActorTests0 extends ActorTests {
    @BeforeClass
    public static void beforeClass() {
        factories = (thrCount, consumer) -> {
            new ThrFactories(consumer)
                    .runWith(new GreenThrFactory_Q(thrCount))
//                    .add(new GreenThrFactory_Heavy())
                    .runWith(new GreenThrFactory_Exec(Executors.newFixedThreadPool(thrCount)))
            ;
        };
    }

}
