/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.examples;

import flc.nbl_actors.core.*;

import java.math.BigInteger;
import java.util.function.Function;

/**
 * Date: 11.08.14
 *
 * @author Tor C Bekkvik
 */
public class Fibonacci extends ActorBase<Fibonacci> {

    public void fib(BigInteger a, BigInteger b, Function<BigInteger, Boolean> out) {
        if (out.apply(a)) //output
            self().send(s -> s.fib(b, a.add(b), out));
    }

    public static void run(IGreenThrFactory factory, Function<BigInteger, Boolean> output) {
        // 1. call 'init' to initiate reference
        // 2. send a message = asynchronous method call (lambda expression)
        new Fibonacci()
                .init(factory)
                .send(s -> s.fib(BigInteger.ONE, BigInteger.ONE, output));
        //Output: 1  1  2  3  5  8  13  21  34 ..
    }

    public static void main(String[] args) {
        final int count = 15;
        System.out.println("Fibonacci numbers; first " + count);
        run(new GreenThr_zero(), new Function<BigInteger, Boolean>() {
            int no;

            @Override
            public Boolean apply(BigInteger number) {
                System.out.print("  " + number);
                return ++no < count;
            }
        });
        System.out.println();
    }
}
