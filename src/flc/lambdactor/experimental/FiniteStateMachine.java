/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.experimental;

import java.util.function.Consumer;

/**
 * Date: 04.08.14
 *
 * @author Tor C Bekkvik
 */
public class FiniteStateMachine {

    static class FSM_2 {
        int value;
        private final Consumer<Integer> opAdd;
        private final Consumer<Integer> opSub;

        public final Consumer<Integer> event_mode;
        public Consumer<Integer> event_calc;

        public FSM_2() {
            opAdd = s -> {
                value += s;
            };
            opSub = s -> {
                value -= s;
            };
            event_mode = i -> {
                event_calc = i == 0 ? opAdd : opSub;
            };
            event_calc = opAdd;
        }
    }

    static class FSM_1 {
        class State {
            public void e1() {
                value++;
                become(b);
            }

            public int e2(int x) {
                become(c);
                return value;
            }

            public int e3() {
                return value;
            }
        }

        //        ActorRef<State> self;
        private final State a, b, c;
        private State currentState;
        int value;

        public FSM_1(int val) {
            become(a = new State());
            b = new State() {
                @Override
                public void e1() {
                    value *= 2;
                    become(c);
                }
            };
            c = new State() {
                @Override
                public int e3() {
                    become(a);
                    return value;
                }
            };
            value = val;
        }


        void become(State s) {
            currentState = s;
        }

        public State getCurrentState() {
            return currentState;
        }

    }
}
