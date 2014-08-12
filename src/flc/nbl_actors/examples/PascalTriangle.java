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
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Concurrent Pascal's Triangle calculation.
 * Date: 11.08.14
 *
 * @author Tor C Bekkvik
 */
public class PascalTriangle extends ActorBase<PascalTriangle> {
    private IActorRef<PascalTriangle> next;
    private BigInteger curr = BigInteger.ONE;

    public void calc(BigInteger leftParent, Consumer<BigInteger> row) {
        row.accept(curr);
        final BigInteger prev = curr;
        curr = curr.add(leftParent);
        if (next != null)
            next.send(a -> a.calc(prev, row));
        else {
            next = new PascalTriangle().init();
            row.accept(BigInteger.ZERO);//done
        }
    }

    static class Row implements Consumer<BigInteger> {
        final int row;
        final ArrayList<BigInteger> lst = new ArrayList<>();

        Row(int row) {
            this.row = row;
        }

        @Override
        public void accept(BigInteger val) {
            if (val.equals(BigInteger.ZERO)) {
                System.out.print(String.format(" Row %2d: ", row));
                lst.forEach(v ->
                        System.out.print(String.format(" %2d", v))
                );
                System.out.println();
            } else lst.add(val);
        }
    }

    public static void main(String[] args) {
//        try (IGreenThrFactory factory = new GreenThr_zero()) {
        try (IGreenThrFactory factory = new GreenThrFactory_single(4, false)) {
            //NB! factory must give FIFO-queue threads (which is the default)
            IActorRef<PascalTriangle> ref = new PascalTriangle()
                    .init(factory);
            int noRows = 10;
            System.out.println("Pascal triangle, #rows: " + noRows);
            for (int row = 0; row < noRows; row++) {
                final Row out = new Row(row);
                ref.send(a -> a.calc(BigInteger.ZERO, out));
            }
        }
        /*Output example:
        Pascal triangle, #rows: 10
         Row  0:   1
         Row  1:   1  1
         Row  2:   1  2  1
         Row  3:   1  3  3  1
         Row  4:   1  4  6  4  1
         Row  5:   1  5 10 10  5  1
         Row  6:   1  6 15 20 15  6  1
         Row  7:   1  7 21 35 35 21  7  1
         Row  8:   1  8 28 56 70 56 28  8  1
         Row  9:   1  9 36 84 126 126 84 36  9  1
         */
    }
}
