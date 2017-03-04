package com.github.strangefac.strange.impl;

import com.github.strangefac.strange.Suspendable;
import com.github.strangefac.strange.Suspension;
import com.github.strangefac.strange.Syncable;

/** Common case of simply delegating to another actor. */
public class DelegatingSuspension extends Suspension {
  private static final long serialVersionUID = 1L;

  public DelegatingSuspension(Syncable<?, ?> future) {
    super(new BasicJoinImpl<Syncable<?, ?>>(future));
  }

  public Object done(Suspendable done) throws Throwable {
    return ((Syncable<?, ?>) done).sync();
  }

  public Object doneImmediately() {
    return atLeastOneSubtask();
  }
}
