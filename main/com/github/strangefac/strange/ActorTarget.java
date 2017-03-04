package com.github.strangefac.strange;

import com.github.strangefac.strange.function.VoidCheckedException;

/** Convenience when {@link #init(Actor)} doesn't throw any checked exceptions. */
public interface ActorTarget<A extends Actor> extends ActorTargetThrows<A, VoidCheckedException> {
  // Nothing else.
}
