package com.github.strangefac.strange;

/**
 * All actor targets must implement this interface.
 * 
 * @param <A> The type of the actor object that will wrap this target.
 */
public interface ActorTargetThrows<A extends Actor, E extends Throwable> {
  /**
   * Invoked before any other methods, so useful for setting up mutable state and/or state that must be inited using the configured thread collection.
   * 
   * @param actor The actor object wrapping this.
   * @throws Suspension Note this will allow other methods to execute before its done block, which may not be what you want.
   */
  void init(A actor) throws Suspension, E;
}
