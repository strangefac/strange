package com.github.strangefac.strange.impl;

import java.awt.EventQueue;
import com.github.strangefac.strange.ThreadCollection;

/** The thread collection for swing actors, which necessarily consists of the event thread only. */
public class SwingThreadCollection implements ThreadCollection {
  public static final SwingThreadCollection SWING_THREAD_COLLECTION = new SwingThreadCollection();

  private SwingThreadCollection() {
    // Singleton.
  }

  public void execute(Runnable command) {
    EventQueue.invokeLater(command);
  }
}
