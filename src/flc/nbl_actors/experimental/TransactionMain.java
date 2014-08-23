/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import flc.nbl_actors.core.*;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Transaction example
 * Date: 17.08.14
 *
 * @author Tor C Bekkvik
 */
public class TransactionMain {

    static class Act extends ActorBase<Act> {
        int value;
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    static void tstTCon(boolean isErr) {
        System.out.println("tstTCon  err:" + isErr);
        Transaction.TCon tc = new Transaction.TCon();
        for (int i = 3; i >= 0; i--) {
            //participant:
            Transaction.Part part = (Transaction.Part) tc.newPart();
            final int ii = i;
            if (isErr && i == 0)
                part.fail(null);
//            boolean err = part.isFailed();
            part.ready(c -> {
                System.out.println("  " + ii + " isCommit: " + c);
                assert part.isFailed() == !c;
            });
        }
        tc.ready(c -> {
            tc.commit(c);
            System.out.println(" Committed: " + c);
            assert tc.getState().isCommit() == c;
        });
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static void main(String[] args) throws InterruptedException {
        tstTCon(true);
        tstTCon(false);
//        CountDownLatch latch = new CountDownLatch(1);
//        boolean latchDone = latch.await(1, TimeUnit.MILLISECONDS);
        Level myLevel = Level.FINE;
        Logger log = Transaction.LOG;
        log.getParent().getHandlers()[0].setLevel(myLevel);
        log.setLevel(myLevel);
        try (IGreenThrFactory fact = new GreenThrFactory_single(4, false)) {
            final Transaction.ActorTCon transaction = new Transaction.ActorTCon();
            for (int i = 0; i < 1; i++) {
                final int ix = i;
                boolean err = false;//i == 3;
                IActorRef<Act> ref = new Act().init(fact);
                transaction.send(ref, (tr, act) -> {
                    final int tmp = act.value + 1;
                    if (tr.isFailed()) {
                        return;
                    }
                    if (err) throw new IllegalStateException("test err0");
                    tr.readyCommit(() -> {
                        act.value = tmp;
                        System.out.println(" actor committed: " + ix);
                    });
//                    if (err) throw new IllegalStateException("test err");
                });
            }
            transaction.ready(isCommit -> {
                transaction.commit(isCommit);
                System.out.println("Transaction done, committed = " + isCommit);
                Transaction.State s = transaction.getState();
                assert isCommit ? s.isCommit() : s.isFailed();
                assert transaction.getState().isDone();
            });
        }
    }

}
