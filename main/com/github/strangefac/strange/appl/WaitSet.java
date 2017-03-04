package com.github.strangefac.strange.appl;

import com.github.strangefac.strange.function.VoidCheckedException;
import com.github.strangefac.strange.impl.SFutureImpl;

public class WaitSet extends SFutureImpl<Void, VoidCheckedException> {
  /** @return A fresh instance, which should typically replace this wait set. */
  public WaitSet execute() {
    putValue(null);
    return new WaitSet();
  }
}
