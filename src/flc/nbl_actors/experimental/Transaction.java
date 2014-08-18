/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.nbl_actors.experimental;

import flc.nbl_actors.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transaction with actor participants. Goal: atomic execution (state updated by NONE or ALL actors).
 * Date: 17.08.14
 *
 * @author Tor C Bekkvik
 */
public class Transaction {

    static final Logger LOG = Logger.getLogger(Transaction.class.getSimpleName());

    public enum State {
        active, failed, partReady, success;

        public boolean isSuccess() {
            return this == success;
        }

        public boolean isFailed() {
            return this == failed;
        }

        public State changeTo(State s) {
            /* valid state transitions:
            active -> failed!
            active -> success!
            active -> partReady -> [success or failed]!
             */
            switch (this) {
                case active:
                    return s;
                case partReady:
                    if (s != active) return s;
                    break;
                case failed:
                case success:
                    if (this == s) return s;
            }
            String reason = String.format("Illegal state change; %s -> %s", this, s);
            LOG.warning(reason);
            throw new IllegalStateException(reason);
        }
    }

    public interface Ref {
        int getId();

        void fail(Exception e);

        boolean isFailed();

        void ready(Runnable onCommit);
    }

    private class TransRef implements Ref {
        final int id;
        final IGreenThr actorThread;
        private Runnable onCommit;
        private State partState = State.active;

        Exception ex;

        private TransRef(int id, IGreenThr thread) {
            this.id = id;
            actorThread = thread;
        }

        @Override
        public int getId() {
            return id;
        }

        //--- fail:
        @Override
        public void fail(Exception e) {
            String s = ThreadContext.shortTrace(e)
                    + String.format("  Transaction participant #%d failed", id);
            LOG.fine(s);
            ex = e;
            partState = partState.changeTo(State.failed);
            controller.fail();
        }

        @Override
        public boolean isFailed() {
            return controller.state.isFailed();
        }

        //--- ready:
        @Override
        public void ready(Runnable r) {
            if (onCommit == null) {
                onCommit = r;
                readySend();
            }
        }

        private void ready2() {
            partState = partState.changeTo(State.partReady);
            readySend();
        }

        private void readySend() { //ready to commit
            if (onCommit != null && partState == State.partReady) {
                controller.readyCommit();
            }
        }

        private void commit() {//effect of controller.readyCommit()
            if (onCommit == null) {
                throw new NullPointerException("no commit task");
            }
            partState = partState.changeTo(State.success);
            actorThread.execute(() -> {
                try {
                    onCommit.run();
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Exception during commit!", e);
                }
            });
        }
    }

    private final Transaction controller = this;
    private final List<TransRef> participants = new ArrayList<>();
    private int noPending;
    private Consumer<Boolean> doneHandler;
    private volatile State state = State.active;

    public synchronized <A> void send(IActorRef<A> ref, BiConsumer<Ref, A> action) {
        if (state.isFailed())
            return;
        final TransRef trans = new TransRef(participants.size(),
                msg -> ref.send(a -> msg.run()));
        participants.add(trans);
        //participant initialized; now send..
        ++noPending;
        ref.send(act -> {
            try {
                if (!state.isFailed()) {
                    action.accept(trans, act);
                    trans.ready2();
                }
            } catch (RuntimeException ex) {
                trans.fail(ex);
            }
        });
    }

    //--- failure handling
    public synchronized void fail() {
        if (state == State.active) {
            state = State.failed;
            if (doneHandler != null) {
                doneHandler.accept(false);
                participants.clear();
            }
        }
    }

    //--- commit handling
    private synchronized void readyCommit() {
        --noPending;
        trigger();
    }

    public synchronized void whenDone(Consumer<Boolean> handler) {
        doneHandler = Objects.requireNonNull(handler);
        if (state.isFailed()) {
            doneHandler.accept(false);
        } else
            trigger();
    }

    private synchronized void trigger() {
        if (state == State.active) {
            if (noPending == 0 && doneHandler != null) {
                state = State.success;
                for (TransRef p : participants) p.commit();
                doneHandler.accept(true);
                participants.clear();
            }
        }
    }

    public State getState() {
        return state;
    }
}
