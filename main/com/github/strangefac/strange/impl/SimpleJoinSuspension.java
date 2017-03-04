package com.github.strangefac.strange.impl;

import java.util.Collection;
import java.util.Collections;
import com.github.strangefac.strange.Syncable;

/**
 * The result task simply syncs all futures (ignoring their return values but propagating the first (w.r.t. the given order) exception if any) and returns the
 * given result object.
 */
public class SimpleJoinSuspension extends JoinSuspension {
  private static final long serialVersionUID = 1L;

  public SimpleJoinSuspension(Collection<? extends Syncable<?, ?>> futures, Object result) {
    super(futures, join -> {
      // By doing all syncs at the end, if some future never terminates then neither does this:
      join.syncAll();
      return result;
    });
  }

  public SimpleJoinSuspension(Syncable<?, ?> future, Object result) {
    this(Collections.singleton(future), result);
  }
}
