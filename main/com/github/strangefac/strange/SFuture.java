package com.github.strangefac.strange;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @param <E> A common superclass of all checked throwables that can be put into this future, or anything you like (e.g.
 * {@link com.github.strangefac.strange.function.VoidCheckedException}) if there are no such checked throwables. Don't neglect to consider what's thrown by
 * {@link Suspension#done(Suspendable)}.
 */
public interface SFuture<V, E extends Throwable> extends OpenFuture<V, E>, Future<V>, Syncable<V, E> {
  /**
   * For when you want to avoid {@link InterruptedException} because you know this Future is done.
   * 
   * @throws IllegalStateException If this is not yet done.
   */
  V assertDoneAndGetUninterruptibly() throws IllegalStateException, ExecutionException;
}
