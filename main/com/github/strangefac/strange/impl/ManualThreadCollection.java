package com.github.strangefac.strange.impl;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import com.github.strangefac.strange.ThreadCollection;

public class ManualThreadCollection implements ThreadCollection {
  private final BlockingQueue<Runnable> _queue = new LinkedBlockingQueue<>();
  private final ThreadLocal<Boolean> _take = new ThreadLocal<>();

  /** @return The number of tasks executed, at least 1. Note this can be misleading as one {@link Drain} can perform multiple method calls. */
  public int enter() throws InterruptedException {
    int n = 0;
    for (_take.set(true); _take.get();) {
      _queue.take().run();
      Thread.interrupted(); // Prevent leaked interrupt from affecting the next take.
      ++n;
    }
    return n;
  }

  public void execute(Runnable command) {
    _queue.add(command);
  }

  /** Posts a command that will cause one of the threads in {@link #enter()} to exit. */
  public void postExitCommand() {
    _queue.add(() -> _take.set(false));
  }
}
