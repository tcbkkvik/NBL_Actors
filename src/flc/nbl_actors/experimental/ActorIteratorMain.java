/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import flc.nbl_actors.core.*;
import flc.nbl_actors.examples.Fibonacci;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.concurrent.SynchronousQueue;

import static java.lang.System.out;

/**
 * Iterate over actor output via blocking queue.
 * Date: 16.08.14
 *
 * @author Tor C Bekkvik
 */
public class ActorIteratorMain
        implements Iterator<BigInteger> {

    private static class Queue<E> {
        final SynchronousQueue<E> queue = new SynchronousQueue<>();

        public boolean put(E val) {
            try {
                queue.put(val);
            } catch (InterruptedException e) {
                return false;
            }
            return true;
        }

        public E take() {
            try {
                return queue.take();
            } catch (InterruptedException e) {
                return null;
            }
        }
    }

    private final Queue<BigInteger> queue = new Queue<>();

    public ActorIteratorMain() {
        new Fibonacci()
                .initThread(new GreenThr_single())
                .send(s -> s.fib(BigInteger.ONE, BigInteger.ONE, queue::put));
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public BigInteger next() {
        return queue.take();
    }

    public static void main(String[] args) {
        out.print("Fibonacci: ");
        Iterator<BigInteger> src = new ActorIteratorMain();
        for (int i = 0; i < 10; i++)
            out.print(" " + src.next());
        out.println();
    }
}
