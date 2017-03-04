package com.github.strangefac.strange;

/** Singleton for analogues of Erlang built-in functions. */
// TODO LATER: A spawn method that can pass args to the actor's init method.
public interface Strange {
  /**
   * Creates an actor object wrapping the given target, and sends the {@link ActorTarget#init(Actor)} message with the new actor object as its argument.
   * 
   * @param target The object on which messages will be invoked serially by threads in the thread collection.
   * @return The new actor object.
   * @throws IllegalArgumentException If target's class does not have a {@link ThreadCollectionType} annotation.
   */
  <A extends Actor, E extends Throwable> Spawned<A, E> spawn(ActorTargetThrows<A, ? extends E> target) throws IllegalArgumentException;
}
