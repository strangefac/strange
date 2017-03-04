package com.github.strangefac.strange;

public interface Spawned<A extends Actor, E extends Throwable> extends Syncable<A, E> {
  A actor();
}
