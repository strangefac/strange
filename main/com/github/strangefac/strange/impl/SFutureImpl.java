package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.Syncable.AbruptSyncable.andForgetImpl;
import static com.github.strangefac.strange.function.NullConsumer.NULL_CONSUMER;
import static com.github.strangefac.strange.impl.Mailbox.DEAD_ACTOR_MESSAGE;
import static com.github.strangefac.strange.util.StrangeUtils.uninterruptibly;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.strangefac.strange.DeadActorException;
import com.github.strangefac.strange.PrivateActor;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.SyncException;
import com.github.strangefac.strange.Task;
import com.github.strangefac.strange.Wrapper;
import com.github.strangefac.strange.util.TypedArrayList;
import com.github.strangefac.strange.util.UncheckedCast;

/** The user-visible Future. TODO LATER: Reimplement based on {@link CompletableFuture}. */
public class SFutureImpl<V, E extends Throwable> implements SFuture<V, E> {
  private static final Logger LOG = LoggerFactory.getLogger(SFutureImpl.class);

  private enum State {
    NOT_DONE {
      <V> V assertDoneAndGetUninterruptibly(SFutureImpl<? extends V, ?> synchronizedWrapper) throws IllegalStateException {
        throw new IllegalStateException("Not yet done!");
      }

      <V> V get(SFutureImpl<? extends V, ?> synchronizedWrapper) throws CancellationException, InterruptedException, ExecutionException {
        synchronizedWrapper._notifyEnabled = true;
        synchronizedWrapper.wait();
        return synchronizedWrapper._state.get(synchronizedWrapper); // In the spurious wakeup case this simply recurses.
      }

      <V> V get(SFutureImpl<? extends V, ?> synchronizedWrapper, long timeout, TimeUnit unit) throws CancellationException, InterruptedException, ExecutionException, TimeoutException {
        return getUpToNanos(synchronizedWrapper, unit.toNanos(timeout));
      }

      private <V> V getUpToNanos(SFutureImpl<? extends V, ?> synchronizedWrapper, long remainingNanos) throws CancellationException, InterruptedException, ExecutionException, TimeoutException {
        long lapNanos = System.nanoTime();
        while (true) {
          synchronizedWrapper._notifyEnabled = true;
          TimeUnit.NANOSECONDS.timedWait(synchronizedWrapper, remainingNanos);
          if (this != synchronizedWrapper._state) return synchronizedWrapper._state.get(synchronizedWrapper);
          // Still not done, so check for spurious wakeup:
          long tookNanos = System.nanoTime() - lapNanos;
          if (tookNanos >= remainingNanos) throw new TimeoutException(); // Don't disable notify, other threads may be waiting.
          LOG.debug("Absorbing spurious wakeup."); // I don't recall ever seeing this, so debug is fine.
          remainingNanos -= tookNanos;
          lapNanos += tookNanos;
        }
      }
    },
    COMPLETED_NORMALLY {
      <V> V assertDoneAndGetUninterruptibly(SFutureImpl<? extends V, ?> synchronizedWrapper) {
        return synchronizedWrapper._value;
      }
    },
    COMPLETED_ABRUPTLY {
      <V> V assertDoneAndGetUninterruptibly(SFutureImpl<? extends V, ?> synchronizedWrapper) throws ExecutionException {
        throw new ExecutionException(synchronizedWrapper._throwable);
      }
    },
    CANCELLED {
      <V> V assertDoneAndGetUninterruptibly(SFutureImpl<? extends V, ?> synchronizedWrapper) throws CancellationException {
        throw new CancellationException();
      }
    };
    abstract <V> V assertDoneAndGetUninterruptibly(SFutureImpl<? extends V, ?> synchronizedWrapper) throws IllegalStateException, CancellationException, ExecutionException;

    /**
     * Must be overridden by {@link #NOT_DONE}.
     * 
     * @throws InterruptedException Thrown by {@link #NOT_DONE}.
     */
    <V> V get(SFutureImpl<? extends V, ?> synchronizedWrapper) throws CancellationException, InterruptedException, ExecutionException {
      return assertDoneAndGetUninterruptibly(synchronizedWrapper);
    }

    /**
     * Must be overridden by {@link #NOT_DONE}.
     * 
     * @param timeout Used by {@link #NOT_DONE}.
     * @param unit Used by {@link #NOT_DONE}.
     * @throws InterruptedException Thrown by {@link #NOT_DONE}.
     * @throws TimeoutException Thrown by {@link #NOT_DONE}.
     */
    <V> V get(SFutureImpl<? extends V, ?> synchronizedWrapper, long timeout, TimeUnit unit) throws CancellationException, InterruptedException, ExecutionException, TimeoutException {
      return assertDoneAndGetUninterruptibly(synchronizedWrapper);
    }
  }

  static final String NOT_ITE_MESSAGE = "Non-InvocationTargetException cause of ExecutionException:";

  private static void assertNotDone(State state) throws IllegalStateException {
    if (State.NOT_DONE != state) throw new IllegalStateException("Already done.");
  }

  private State _state;
  private List<Runnable> _donePostsOrNull;
  /** Must be set to true before going into wait. Exists for performance only, never set back to false. */
  private boolean _notifyEnabled;
  {
    synchronized (this) {
      _state = State.NOT_DONE;
      _donePostsOrNull = Collections.emptyList();
      _notifyEnabled = false;
    }
  }
  private V _value;
  private Throwable _throwable;

  public synchronized void putCancelled() throws IllegalStateException {
    assertNotDone(_state);
    _state = State.CANCELLED;
    done();
  }

  public synchronized void putValue(V value) throws IllegalStateException {
    assertNotDone(_state);
    _value = value;
    _state = State.COMPLETED_NORMALLY;
    done();
  }

  public void putCauseOfInvocationTargetException(E checkedThrowable) throws IllegalStateException {
    putThrowable(new InvocationTargetException(checkedThrowable));
  }

  public void putCauseOfExecutionException(Throwable throwable) throws IllegalStateException {
    putThrowable(throwable);
  }

  private synchronized void putThrowable(Throwable throwable) throws IllegalStateException {
    assertNotDone(_state);
    _throwable = throwable;
    _state = State.COMPLETED_ABRUPTLY;
    done();
  }

  public <W, F extends Throwable> void postAfterDone(PrivateActor actor, Task<? extends W, ? extends F> taskImpl, Wrapper<W, F> wrapper) {
    runAfterDone(() -> DONE_SUSPENDABLE.postAfterDone(actor, taskImpl, wrapper));
  }

  private synchronized void runAfterDone(Runnable post) {
    if (null != _donePostsOrNull) {
      if (_donePostsOrNull.isEmpty()) { // It's unmodifiable.
        _donePostsOrNull = new TypedArrayList<>(Runnable.class, 1); // Only big enough for the first post, too aggressive?
      }
      _donePostsOrNull.add(post);
    } else {
      post.run();
    }
  }

  // Must be called from a synchronized method/block.
  private void done() {
    if (_notifyEnabled) notifyAll(); // External waiters won't be notified, which is fine as they should use the API instead of wishful thinking.
    for (Runnable donePost : _donePostsOrNull)
      donePost.run(); // It's just an actor post so won't hog the lock.
    _donePostsOrNull = null; // Any further posts will be executed immediately.
  }

  public boolean cancel(boolean mayInterruptIfRunning) {
    throw new UnsupportedOperationException("There is no unique associated task, use a higher-level mechanism.");
  }

  public synchronized boolean isCancelled() {
    return State.CANCELLED == _state;
  }

  public synchronized boolean isDone() {
    return State.NOT_DONE != _state;
  }

  public synchronized V get() throws CancellationException, InterruptedException, ExecutionException {
    return _state.get(this);
  }

  public synchronized V get(long timeout, TimeUnit unit) throws CancellationException, InterruptedException, ExecutionException, TimeoutException {
    return _state.get(this, timeout, unit);
  }

  public void andForget(Logger log) {
    runAfterDone(() -> {
      if (!isCancelled()) {
        try {
          sync();
        } catch (Throwable t) {
          andForgetImpl(log, t);
        }
      }
    });
  }

  public synchronized V assertDoneAndGetUninterruptibly() throws IllegalStateException, CancellationException, ExecutionException {
    return _state.assertDoneAndGetUninterruptibly(this);
  }

  public V sync() throws CancellationException, E, SyncException {
    try {
      return uninterruptibly(this::get);
    } catch (ExecutionException executionException) {
      throw new StackTraceEnhancer().<E> unwrap(executionException);
    }
  }

  public V sync(double timeout) throws CancellationException, E, SyncException, TimeoutException {
    try {
      return uninterruptibly(() -> get(Math.round(timeout * 1e9), TimeUnit.NANOSECONDS));
    } catch (ExecutionException executionException) {
      throw new StackTraceEnhancer().<E> unwrap(executionException);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) { // Any other checked exception, which can only be TimeoutException.
      throw (TimeoutException) e;
    }
  }

  // Must be created in the sync method itself so that the correct stack is captured:
  private static class StackTraceEnhancer extends Throwable implements Consumer<Throwable> {
    private static final long serialVersionUID = 1L;

    private <E extends Throwable> E unwrap(ExecutionException executionException) throws SyncException {
      return SFutureImpl.unwrapCauseOfExecutionException(this, executionException.getCause());
    }

    public void accept(Throwable t) {
      StackTraceElement[] those = t.getStackTrace(), these = getStackTrace();
      int common = 0;
      for (int n = Math.min(those.length, these.length); common < n; ++common) {
        if (!those[those.length - 1 - common].equals(these[these.length - 1 - common])) break;
      }
      StackTraceElement[] v = new StackTraceElement[those.length - common + (0 != common ? 1 : 0) + these.length];
      System.arraycopy(those, 0, v, 0, those.length - common);
      if (0 != common) {
        // Unfortunately StackTraceElement is final, or we could override its toString:
        v[those.length - common] = new StackTraceElement(String.valueOf(common), "more", null, -1);
      }
      System.arraycopy(these, 0, v, those.length - common + (0 != common ? 1 : 0), these.length);
      t.setStackTrace(v);
    }
  }

  static <E extends Throwable> E unwrapCauseOfExecutionException(Throwable t) throws SyncException {
    return SFutureImpl.unwrapCauseOfExecutionException(NULL_CONSUMER, t);
  }

  private static <E extends Throwable> E unwrapCauseOfExecutionException(Consumer<? super Throwable> enhancer, Throwable t) throws SyncException {
    if (t instanceof InvocationTargetException) {
      Throwable e = t.getCause();
      enhancer.accept(e);
      if (e instanceof Error) {
        throw (Error) e;
      } else if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      } else {
        return UncheckedCast.uncheckedCast(e); // Should be safe provided E satisfies the javadoc.
      }
    } else if (t instanceof DeadActorException) { // Suspension was abended by actor death.
      throw new RejectedExecutionException(DEAD_ACTOR_MESSAGE);
    } else {
      throw new SyncException(NOT_ITE_MESSAGE, t); // No point enhancing this, it already has the current context.
    }
  }
}
