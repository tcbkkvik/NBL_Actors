/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.core;


/**
 * Actor base class.
 * <p>Provides self() actor reference, initiated by calling init().
 * Override init() to access the thread factory instance, or
 * to add constructor code, but remember to call super.init() and return self().
 * </p>
 * <pre>{@code
 * Minimum example:
 *     static void actorBaseExample(IGreenThrFactory factory)
 *     {
 *         class Impl extends ActorBase<Impl> {
 *             void otherMethod(String message) {
 *                 System.out.println(message + ": done!");
 *             }
 *             void someMethod(String message) {
 *                 this.self().send(a -> a.otherMethod(message));
 *             }
 *         }
 *         IActorRef<Impl> ref = new Impl().init(factory);
 *         ref.send(a -> a.someMethod("message"));
 *     }
 * }
 * </pre>
 * Date: 18.07.13
 *
 * @author Tor C Bekkvik
 */
public class ActorBase<T> {

    private ActorRef<T> self_reference;

    /**
     * Actor constructor.
     * <p>Attach a green-thread to this actor instance.
     * To avoid shared mutable access, avoid direct method calls on this object from other threads.
     * Instead, access it via returned actor reference.
     * </p>
     *
     * @param thr green thread
     * @return new actor reference
     */
    public IActorRef<T> initThread(IGreenThr thr) {
        return self_reference == null
                ? self_reference = initActorRef(thr)
                : self_reference;
    }

    /**
     * Called internally from {@link #initThread} to initialize 'self' actor reference.
     * (Override to use a subclass of {@link ActorRef}.)
     *
     * @param thr thread
     * @return Actor reference (or a subclass of it)
     */
    protected ActorRef<T> initActorRef(IGreenThr thr) {
        //noinspection unchecked
        return new ActorRef<>((T) this, thr);
    }

    /**
     * Actor constructor. Equivalent to {@link #initThread(IGreenThr)}
     * with {@code factory.newThread()}
     *
     * @param factory thread factory
     * @return new actor reference
     */
    public IActorRef<T> init(IGreenThrFactory factory) {
        return initThread(factory.newThread());
    }

    /**
     * Actor constructor. Equivalent to {@link #init(IGreenThrFactory)}
     * with {@code ThreadContext.get().getFactory()}
     *
     * @return new actor reference
     */
    public IActorRef<T> init() {
        return init(ThreadContext.get().getFactory());
    }

    /**
     * Self reference - get the actor reference to this object.
     *
     * @return my actor reference
     */
    public IActorRef<T> self() {
        return self_reference;
    }

    /**
     * Instantiate new ForkJoin, based on {@link #self()}.
     *
     * @param initValue initial value
     * @param <R>       value type
     * @return initiated ForkJoin
     */
    public <R> ForkJoin<R> newForkJoin(R initValue) {
        return new ForkJoin<>(self(), initValue);
    }

    /**
     * Get a new Green thread.
     * Equivalent to {@code ThreadContext.get().getFactory().newThread()}
     *
     * @return A new thread (from last set factory in {@link ThreadContext})
     */
    public static IGreenThr newThread() {
        return ThreadContext.get().getFactory().newThread();
    }

    /**
     * Become - update my {@link #self()} reference to a new actor implementation.
     * (No explicit initiation via {@link #init()} or {@link #initThread(IGreenThr)}
     * is needed on the new instance)
     *
     * @param newImpl new actor implementation.
     * @throws IllegalStateException if new implementation already has another self-reference != self()
     */
    protected void become(T newImpl) {
        if (newImpl instanceof ActorBase) {
            ActorBase b = (ActorBase) newImpl;
            if (b.self_reference == null)
                b.self_reference = self_reference;
            else if (b.self_reference != self_reference)
                throw new IllegalStateException("New implementation has a different self-reference than self()");
        }
        self_reference.become(newImpl);
    }

}
