package com.github.strangefac.strange;

/** Like {@link Task} but doesn't force your lambda to return a value. */
public interface VoidTask<E extends Throwable> {
  void run() throws E, Suspension;
}
