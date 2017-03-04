package com.github.strangefac.strange;

/**
 * @param <E> A common superclass of all non-suspend checked throwables that run can throw, or any RuntimeException/Error if there aren't any.
 * @see SFuture
 */
public interface Task<V, E extends Throwable> {
  V run() throws E, Suspension;
}
