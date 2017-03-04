package com.github.strangefac.strange.pool;

import static com.github.strangefac.strange.pool.TaskHolder.EXIT_TASK;

class PoolThread extends Thread {
  static final PoolThread[] EMPTY_POOL_THREAD_ARRAY = new PoolThread[0];
  /** The thread-safe task queue. */
  final TaskHolder _taskHolder;
  private final PoolThreadOwner _owner;
  private final long _timeoutMillis;

  PoolThread(String label, Runnable task, PoolThreadOwner owner, long timeoutMillis) {
    super(label);
    _taskHolder = new TaskHolder(task);
    _owner = owner;
    _timeoutMillis = timeoutMillis;
  }

  public void run() {
    while (true) {
      Runnable task = _taskHolder.peekOrExit(_timeoutMillis);
      if (EXIT_TASK == task) break; // Leave the exit task in the holder.
      task.run(); // ThreadCollection says we can assume this will return normally.
      if (_taskHolder.consume()) { // Accept another task, or move the exit task into position.
        _owner.promote(this); // Try to keep this thread busy, so that redundant threads time-out.
      }
    }
    _owner.discard(this);
  }
}
