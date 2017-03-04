package com.github.strangefac.strange;

import java.util.concurrent.Executor;

/**
 * One or more threads, not necessarily disjoint with other collections. This is intended to be the acceptable collection of threads for calling methods on a
 * particular actor target. Impls may assume that all {@link Runnable#run()} methods will return normally.
 */
public interface ThreadCollection {
  /**
   * @param command Must return normally.
   * @see Executor#execute(Runnable)
   */
  void execute(Runnable command);
}
