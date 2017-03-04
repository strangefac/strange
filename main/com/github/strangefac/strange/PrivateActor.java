package com.github.strangefac.strange;

/** The secret-ish interface implemented by all actors in addition to {@link Actor}. */
public interface PrivateActor {
  /**
   * Variant of {@link Actor#post(Task)} used to implement resume after suspend. Doesn't return a future but otherwise behaves like a normal actor interface
   * method, in particular by returning immediately.
   * 
   * @param wrapper The target future, which won't be a real future if this is a non-trivial batch.
   */
  <V, E extends Throwable> void post(Task<? extends V, ? extends E> task, Wrapper<V, E> wrapper) throws DeadActorException;
}
