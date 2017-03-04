package com.github.strangefac.strange;

/** Collection of {@link Suspendable}. */
public interface BasicJoin<S extends Suspendable> extends Iterable<S> {
  interface Join<S extends Syncable<?, ?>> extends BasicJoin<S> {
    /** Call this to check all futures up-front so that no exceptions are lost. */
    void syncAll() throws Throwable;
  }

  int size();

  S first();
}
