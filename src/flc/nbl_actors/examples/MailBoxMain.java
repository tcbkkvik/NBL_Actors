/*
 * Copyright (c) 2015 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.examples;

import flc.nbl_actors.core.*;

import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * MailBox usage ..
 * <p>Date: 07.03.2015
 * </p>
 *
 * @author Tor C Bekkvik
 */
public class MailBoxMain {

    static class Act extends ActorBase<Act> {
        void receive(Deque<Double> queue) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignore) {
            }
            Double val;
            while ((val = queue.poll()) != null)
                System.out.println(val);
        }
    }

    public static void main(String[] args) {

        try (IGreenThrFactory threads = new GreenThr_single(false)) {
            final IActorRef<Act> ref = new Act().init(threads);
            final Deque<Double> queue = new LinkedBlockingDeque<>();

            Consumer<Double> msgBox = MailBox.create(ref, queue::add, a -> a.receive(queue));
            Consumer<Double> msgBoxAddFirst = MailBox.create(ref, queue::addFirst, a -> a.receive(queue));
            BiConsumer<Double, Boolean> priorityMailBox = MailBox.create(
                    ref,
                    (val, hiPri) -> {
                        if (hiPri) queue.addFirst(val);
                        else queue.add(val);
                    },
                    a -> a.receive(queue));
            for (double val = 0; val < 10; val++)
                msgBox.accept(val);
            for (double val = 10; val < 20; val++)
                msgBoxAddFirst.accept(val);
            priorityMailBox.accept(3.2, false);
            priorityMailBox.accept(1.5, true);
        }
    }
}
