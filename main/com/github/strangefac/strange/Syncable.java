package com.github.strangefac.strange;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import com.github.strangefac.strange.util.UncheckedCast;

public interface Syncable<V, E extends Throwable> extends Suspendable {
  class NormalSyncable<V, E extends Throwable> implements Syncable<V, E> {
    private final V _value;

    public NormalSyncable(V value) {
      _value = value;
    }

    public <W, F extends Throwable> void postAfterDone(PrivateActor actor, Task<? extends W, ? extends F> taskImpl, Wrapper<W, F> wrapper) {
      DONE_SUSPENDABLE.postAfterDone(actor, taskImpl, wrapper);
    }

    public void andForget(Logger log) {
      // Nothing to be done.
    }

    public V sync() {
      return _value;
    }

    public V sync(double timeout) {
      return _value;
    }
  }

  class AbruptSyncable<V, E extends Throwable> implements Syncable<V, E> {
    public static void andForgetImpl(Logger andForgetLog, Throwable t) {
      andForgetLog.error("Fire-and-forget failure:", t); // Logger of the supervisor, I rarely feel like target logger would be more correct.
    }

    private final Throwable _throwable;

    /** @param throwable Must be unchecked or an instance of E. */
    public AbruptSyncable(Throwable throwable) {
      _throwable = throwable;
    }

    public <W, F extends Throwable> void postAfterDone(PrivateActor actor, Task<? extends W, ? extends F> taskImpl, Wrapper<W, F> wrapper) {
      DONE_SUSPENDABLE.postAfterDone(actor, taskImpl, wrapper);
    }

    public void andForget(Logger log) {
      andForgetImpl(log, _throwable);
    }

    public V sync() throws E {
      if (_throwable instanceof RuntimeException) {
        throw (RuntimeException) _throwable;
      } else if (_throwable instanceof Error) {
        throw (Error) _throwable;
      } else {
        throw UncheckedCast.<Throwable, E> uncheckedCast(_throwable);
      }
    }

    public V sync(double timeout) throws E {
      return sync();
    }
  }

  Syncable<?, ?>[] EMPTY_SYNCABLE_ARRAY = new Syncable[0];

  /**
   * Declare that the application won't sync this future, so the error (if any) should be logged automatically when done. Note if this is cancelled the impl may
   * yet create an error, but the {@link Future} interface no longer permits access to it, so there can be no automated logging in that case.
   */
  void andForget(Logger log);

  /**
   * Calls {@link Future#get()} simulating the behaviour of synchronized, i.e. uninterruptibly and unwrapping the original throwable if possible.
   * 
   * @return The value as returned by {@link Future#get()}.
   * @throws CancellationException Thrown by {@link Future#get()}.
   * @throws E The cause of the {@link InvocationTargetException} that caused the {@link ExecutionException} thrown by {@link Future#get()}, unless it's a
   * previously-thrown {@link SyncException}.
   * @throws SyncException Wraps the cause of {@link ExecutionException} if we did not unwrap it above.
   */
  V sync() throws CancellationException, E, SyncException;

  /**
   * Like {@link #sync()} but doesn't block forever. The impl absorbs spurious wakeups, so on return this is either done or the timeout was achieved.
   * 
   * @param timeout The timeout in seconds.
   * @throws TimeoutException If the timeout was achieved and this future was not done.
   */
  V sync(double timeout) throws CancellationException, E, SyncException, TimeoutException;
}
