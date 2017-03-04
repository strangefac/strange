package com.github.strangefac.strange.impl;

interface InvocationLite extends Runnable {
  boolean slow();

  long nanoTime();

  int batchSize();

  /** May be called multiple times or when already done. */
  void cancelWithInterrupt(boolean notJustIfYield);
}
