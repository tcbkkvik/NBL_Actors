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
import java.util.logging.Logger;

/**
 * Transaction with actor participants. Performs two-phase-commit
 * updates on included participants (Atomic update).
 * Date: 17.08.14
 *
 * @author Tor C Bekkvik
 */
public class Transaction {

    static final Logger LOG = Logger.getLogger(Transaction.class.getSimpleName());

    public enum State {
        initial, ready, failed, committed;

        public boolean isCommit() {
            return this == committed;
        }

        public boolean isFailed() {
            return this == failed;
        }

        public boolean isDone() {
            return isCommit() || isFailed();
        }

        public State changeTo(State s) {
            /* valid state transitions:
            initial -> failed!
            initial -> ready -> [committed or failed]!
             */
            if (s == this) return s;
            switch (this) {
                case initial:
                    if (s == failed || s == ready) return s;
                    break;
                case ready:
                    if (s.isDone()) return s;
            }
            String reason = String.format("Illegal state change; %s -> %s", this, s);
            LOG.warning(reason);
            throw new IllegalStateException(reason);
        }
    }

    /**
     * Transaction Participant interface
     */
    public interface IPart {//transaction participant

        /**
         * Signal that this participant failed
         *
         * @param e Reason
         */
        void fail(Exception e);

        /**
         * Query controller if transaction failed,
         * giving the participant to abort processing.
         *
         * @return true if transaction failed
         */
        boolean isFailed();

        /**
         * Participant succeeded locally, and is ready to commit.
         *
         * @param onDone callback; called with 'true' if participant shall commit.
         */
        void ready(Consumer<Boolean> onDone);//true -> commit

        default void readyCommit(Runnable onCommit) {
            ready(c -> {
                if (c) onCommit.run();
            });
        }
    }

    /**
     * Transaction Controller interface
     */
    public interface ITCon {
        /**
         * Add participant to transaction
         *
         * @return new participant reference
         */
        IPart addParticipant();

        /**
         * Get transaction state
         *
         * @return state
         */
        State getState();

        /**
         * All participants added (no more {@link #addParticipant()} calls)
         *
         * @param onDone callback: true if all participants succeeded (can commit)
         */
        void ready(Consumer<Boolean> onDone);//true -> commit

        /**
         * Signals that transaction is allowed to commit
         * (if internal conditions are satisfied)
         *
         * @param isCommit true: shall commit
         */
        void commit(boolean isCommit);

        /**
         * Utility method; Equivalent to ready(c -&gt; commit(c));
         */
        default void readyCommit() {
            ready(this::commit);
        }
    }

    //---------------------------------------------------

    /**
     * Transaction Participant
     */
    static class Part implements IPart {
        private final TCon tc;
        private State state = State.initial;
        private Consumer<Boolean> onDone;

        Part(TCon tc) {
            this.tc = tc;
        }

        @Override
        public void fail(Exception e) {
            switch (state) {
                case initial:
                case ready:
                    state = state.changeTo(State.failed);
                    tc.partResult(false);
            }
        }

        @Override
        public boolean isFailed() {
            return state.isFailed();
        }

        @Override
        public void ready(Consumer<Boolean> onDone) {
            this.onDone = Objects.requireNonNull(onDone);
            switch (state) {
                case initial:
                    state = state.changeTo(State.ready);
                    tc.partResult(true);
                    break;
//                case failed:
//                case committed:
//                    onDone.accept(state.isCommit());
            }
        }

        private void commit(boolean isCommit) {
            switch (state) {
                case initial:
                case ready:
                    state = state.changeTo(isCommit
                            ? State.committed
                            : State.failed);
                    if (onDone != null)
                        onDone.accept(isCommit);
            }
        }
    }

    /**
     * Transaction Controller
     */
    static class TCon implements ITCon {
        private State state = State.initial;
        private int noPending;
        private Consumer<Boolean> onDone; //todo my thread=?
        private List<Part> participants = new ArrayList<>();

        private void readyTrigger() {
            if (noPending == 0 && state == State.ready) {
//                state = state.changeTo(State.committed);
                onDone.accept(true);
            }
        }

        private synchronized void partResult(boolean ok) {
            switch (state) {
                case initial:
                case ready:
                    if (!ok) {
                        commit(false);
                        if (onDone != null)
                            onDone.accept(false);
                        return;
                    }
            }
            --noPending;
            readyTrigger();
        }

        @Override
        public synchronized IPart addParticipant() {
            Part p = new Part(this);
            ++noPending;
            participants.add(p);
            return p;
        }

        @Override
        public synchronized State getState() {
            return state;
        }

        @Override
        public synchronized void ready(Consumer<Boolean> onDone) {
            this.onDone = Objects.requireNonNull(onDone);
            switch (state) {
                case initial:
                    state = state.changeTo(State.ready);
                    readyTrigger();
                    break;
//                case failed:
//                case committed:
//                    onDone.accept(state.isCommit());
            }
        }

        @Override
        public synchronized void commit(boolean isCommit) {
            switch (state) {
                case initial:
                case ready:
                    state = state.changeTo(isCommit
                            ? State.committed
                            : State.failed);
                    for (Part p : participants)
                        p.commit(isCommit);
            }
        }
    }

    //---------------------------------------------------

    static class ActorPart<A> implements IPart {
        private final IPart part;
        private final IActorRef<A> ref;
        private boolean readyPrepared;
        private Consumer<Boolean> readyAction;

        ActorPart(IPart part, IActorRef<A> actorRef) {
            this.part = part;
            ref = actorRef;
        }

        @Override
        public void fail(Exception e) {
            part.fail(e);
        }

        @Override
        public boolean isFailed() {
            return part.isFailed();
        }

        @Override
        public void ready(Consumer<Boolean> readyAction) {
            this.readyAction = Objects.requireNonNull(readyAction);
            readyCheck();
        }

        private void readyPrepare() {
            readyPrepared = true;
            readyCheck();
        }

        private void readyCheck() {
            if (readyPrepared && readyAction != null)
                part.ready(isCommit
                        -> ref.send(a -> readyAction.accept(isCommit))
                );
        }
    }

    /**
     * Transaction Controller, extended for Actors.
     */
    public static class ActorTCon extends TCon {
        /**
         * Include participant
         *
         * @param actorRef   actor participant
         * @param userAction update action at actor
         * @param <A>        actor type
         */
        public <A> void addParticipant(IActorRef<A> actorRef, BiConsumer<IPart, A> userAction) {
            Objects.requireNonNull(userAction);
            final ActorPart<A> part = new ActorPart<>(addParticipant(), actorRef);
            actorRef.send(act -> {
                try {
                    if (!getState().isFailed()) {
                        userAction.accept(part, act);
                        part.readyPrepare();
                    }
                } catch (RuntimeException ex) {
                    part.fail(ex);
                }
            });
        }
    }
}
