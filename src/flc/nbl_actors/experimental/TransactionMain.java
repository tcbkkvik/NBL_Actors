/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import flc.nbl_actors.core.*;

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

    public static void main(String[] args) {
        try (IGreenThrFactory fact = new GreenThrFactory_single(4, false)) {
            Transaction transaction = new Transaction();
            for (int i = 0; i < 5; i++) {
                boolean err = false;//i == 3;
                IActorRef<Act> ref = new Act().init(fact);
                transaction.send(ref, (tr, act) -> {
                    final int tmp = act.value + 1;
                    if (tr.isFailed()) {
                        return;
                    }
                    if (err) throw new IllegalStateException("test err0");
                    tr.ready(() -> {
                        act.value = tmp;
                        System.out.println(" actor committed: " + tr.getId());
                    });
//                    if (err) throw new IllegalStateException("test err");
                });
            }
            transaction.whenDone(isCommit -> {
                System.out.println("Transaction done, committed = " + isCommit);
                Transaction.State s = transaction.getState();
                assert isCommit ? s.isSuccess() : s.isFailed();
            });
        }
    }

}
