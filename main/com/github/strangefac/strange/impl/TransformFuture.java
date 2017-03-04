package com.github.strangefac.strange.impl;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.slf4j.Logger;
import com.github.strangefac.strange.PrivateActor;
import com.github.strangefac.strange.SyncException;
import com.github.strangefac.strange.Syncable;
import com.github.strangefac.strange.Task;
import com.github.strangefac.strange.Wrapper;

class TransformFuture<U, V, E extends Throwable> implements Syncable<V, E> {
  private final Syncable<? extends U, E> _future;
  private final Function<? super U, ? extends V> _transform;

  /** @param transform Note will be executed by the synchronizing thread, so should be thread-safe. */
  TransformFuture(Syncable<? extends U, E> future, Function<? super U, ? extends V> transform) {
    _future = future;
    _transform = transform;
  }

  public void andForget(Logger log) {
    _future.andForget(log);
  }

  public <W, F extends Throwable> void postAfterDone(PrivateActor actor, Task<? extends W, ? extends F> taskImpl, Wrapper<W, F> wrapper) {
    _future.postAfterDone(actor, taskImpl, wrapper);
  }

  public V sync() throws CancellationException, E, SyncException {
    return _transform.apply(_future.sync());
  }

  public V sync(double timeout) throws CancellationException, E, SyncException, TimeoutException {
    return _transform.apply(_future.sync(timeout));
  }
}
