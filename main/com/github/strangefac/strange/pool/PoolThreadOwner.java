package com.github.strangefac.strange.pool;

interface PoolThreadOwner {
  /** The given thread just became clear (and wants to maximise its utilisation). */
  void promote(PoolThread thread);

  /**
   * Removes the given thread from the pool.
   * 
   * @param thread Must be occupied by the exit task.
   */
  void discard(PoolThread thread);
}
