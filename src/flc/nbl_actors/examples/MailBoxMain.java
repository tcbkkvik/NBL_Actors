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
            int no = 0;
            while ((val = queue.poll()) != null) {
                System.out.println(val);
                ++no;
            }
            if (no > 0)
                System.out.println("--");
        }
    }

    public static void main(String[] args) {

        try (IGreenThrFactory threads = new GreenThr_single(false)) {
            final IActorRef<Act> ref = new Act().init(threads);
            //Basic usage example
            Consumer<Double> mailBox;
            {
                final Deque<Double> queue = new LinkedBlockingDeque<>();
                mailBox = MailBox.create(ref, queue::addFirst, act -> act.receive(queue));
                //queue::addFirst used to get stack/LIFO ordering
            }
            mailBox.accept(100.0);//first added
            mailBox.accept(200.0);
            //=> actor receive order if first added still in queue:  2.2,  1.1

            // More advanced usage:
            // Combine multiple mailboxes & queues,
            // Useful for priority handling etc.
            Consumer<Double> mailBox1, mailBox2;
            BiConsumer<Double, Boolean> mailBoxPri;
            {
                final Deque<Double> q1 = new LinkedBlockingDeque<>();
                final Deque<Double> q2 = new LinkedBlockingDeque<>();
                Consumer<Act> handler = act -> {
                    act.receive(q1);
                    act.receive(q2);
                };
                mailBox1 = MailBox.create(ref, q1::add, handler);
                mailBox2 = MailBox.create(ref, q2::add, handler);
                mailBoxPri = MailBox.create(ref, (v, pri) -> (pri ? q1 : q2).add(v), handler);
            }
            double val = 0;
            for (int i = 0; i < 10; i++)
                mailBox2.accept(val++);
            for (int i = 0; i < 10; i++)
                mailBox1.accept(val++);
            mailBoxPri.accept(3.2, false);
            mailBoxPri.accept(1.5, true);
        }
    }
}
