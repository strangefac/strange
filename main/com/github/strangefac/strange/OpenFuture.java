package com.github.strangefac.strange;

import java.lang.reflect.InvocationTargetException;

public interface OpenFuture<V, E extends Throwable> {
  void putCancelled() throws IllegalStateException;

  void putValue(V value) throws IllegalStateException;

  void putCauseOfInvocationTargetException(E checkedThrowable) throws IllegalStateException;

  /** @param throwable Should not be an {@link InvocationTargetException}, unless it has an unchecked cause. TODO LATER: Enforce this. */
  void putCauseOfExecutionException(Throwable throwable) throws IllegalStateException;
}
