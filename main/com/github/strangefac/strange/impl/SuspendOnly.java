package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.Suspendable.NEVER_DONE_SUSPENDABLE;
import com.github.strangefac.strange.Suspendable;
import com.github.strangefac.strange.Suspension;

/** Has one subtask that never becomes done, so effectively just suspends until some other subtask (if there is one) becomes done. */
public class SuspendOnly extends Suspension {
  private static final long serialVersionUID = 1L;
  public static final SuspendOnly SUSPEND_ONLY = new SuspendOnly();

  private SuspendOnly() {
    super(new BasicJoinImpl<>(NEVER_DONE_SUSPENDABLE));
  }

  public Object done(Suspendable done) {
    throw new UnsupportedOperationException("Should never be called.");
  }

  public Object doneImmediately() {
    return atLeastOneSubtask();
  }
}
