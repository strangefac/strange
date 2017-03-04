package com.github.strangefac.strange.function;

public class NullRunnable implements Runnable {
  public static final NullRunnable NULL_RUNNABLE = new NullRunnable();

  private NullRunnable() {
    // Singleton.
  }

  public void run() {
    // Do nothing.
  }
}
