package com.github.strangefac.strange.pool;

import java.util.Deque;
import java.util.LinkedList;

class TaskHolder {
  // Package-private for testing only.
  static final Runnable EXIT_TASK = () -> {
    throw new UnsupportedOperationException("Should not be executed.");
  };
  private final Deque<Runnable> _tasks;

  /**
   * @param task The initial task.
   * @throws IllegalArgumentException Thrown by {@link #put(Runnable)}.
   */
  TaskHolder(Runnable task) throws IllegalArgumentException {
    synchronized (this) {
      _tasks = new LinkedList<>();
      put(task); // Take advantage of its arg checks. Will return true.
    }
  }

  /**
   * @return true on success, or false if this currently has a task.
   * @throws IllegalArgumentException If task is null or {@link #EXIT_TASK}.
   */
  synchronized boolean put(Runnable task) throws IllegalArgumentException {
    if (null == task) throw new IllegalArgumentException("The task must not be null.");
    if (EXIT_TASK == task) throw new IllegalArgumentException("Use the exit method.");
    if (!_tasks.isEmpty()) return false;
    addAndNotifyAll(task);
    return true;
  }

  /**
   * Adds the exit task whatever the clear/busy status.
   * 
   * @throws IllegalStateException If the exit task is already enqueued.
   */
  synchronized void exit() throws IllegalStateException {
    if (!_tasks.isEmpty() && EXIT_TASK == _tasks.getLast()) throw new IllegalStateException("The exit task is already added.");
    addAndNotifyAll(EXIT_TASK);
  }

  /** Must be called from a synchronized method. */
  private void addAndNotifyAll(Runnable task) {
    _tasks.add(task);
    notifyAll(); // Wake up all threads in peekOrExit (in practice there is at most one).
  }

  /**
   * @param timeoutMillis The wait timeout, where non-positive means don't bother waiting.
   * @return The non-null task, which may be {@link #EXIT_TASK}.
   */
  synchronized Runnable peekOrExit(long timeoutMillis) {
    if (!_tasks.isEmpty()) return _tasks.getFirst(); // Yay!
    if (timeoutMillis > 0) { // This check is necessary because wait is special when given 0.
      try {
        wait(timeoutMillis); // Waiting for a put.
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // Propagate.
      }
    }
    if (_tasks.isEmpty()) {
      // Almost always a timeout, in which case this thread can retire itself.
      // Could be an interrupt, in which case let's chicken out and exit.
      // Rarely a spurious wakeup, in which case we won't mind the loss of one thread:
      addAndNotifyAll(EXIT_TASK); // In practice there are no other threads to notify.
    }
    return _tasks.getFirst();
  }

  /**
   * Consume the current task so that we can accept another. Unless it's the exit task, which shouldn't be consumed.
   * 
   * @return Whether this holder is now clear.
   * @throws IllegalStateException If empty or it's the exit task.
   */
  synchronized boolean consume() throws IllegalStateException {
    if (_tasks.isEmpty()) throw new IllegalStateException("Nothing to consume.");
    if (EXIT_TASK == _tasks.getFirst()) throw new IllegalStateException("The exit task should not be consumed.");
    _tasks.removeFirst();
    return _tasks.isEmpty();
  }

  /**
   * For metrics only. Note an exiting thread is not clear as it contains the unconsumed exit task. A starting thread isn't clear either as it contains the
   * initial task.
   * 
   * @return true iff this has no tasks.
   */
  synchronized boolean isClear() {
    return _tasks.isEmpty();
  }
}
