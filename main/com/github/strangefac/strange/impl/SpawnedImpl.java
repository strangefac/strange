package com.github.strangefac.strange.impl;

import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.Spawned;
import com.github.strangefac.strange.Syncable;

public class SpawnedImpl<A extends Actor, E extends Throwable> extends TransformFuture<Void, A, E> implements Spawned<A, E> {
  private final A _actor;

  SpawnedImpl(A actor, Syncable<Void, E> initFuture) {
    super(initFuture, x -> actor);
    _actor = actor;
  }

  public A actor() {
    return _actor;
  }
}
