package com.github.strangefac.strange;

/**
 * Like {@link VoidTask} but supplies a reference to itself. This is useful if you're implementing by lambda, in which the keyword <code>this</code> refers to
 * the enclosing instance not the task object.
 */
public interface VoidTask8<E extends Throwable> {
  void run(VoidTask8<E> thisTask) throws E, Suspension;
}
