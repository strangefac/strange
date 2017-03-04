package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.util.StrangeUtils.uninterruptibly;
import static com.github.strangefac.strange.util.UncheckedCast.uncheckedCast;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.slf4j.Logger;
import com.github.strangefac.strange.AfterTask;
import com.github.strangefac.strange.BasicJoin;
import com.github.strangefac.strange.PrivateActor;
import com.github.strangefac.strange.Suspendable;
import com.github.strangefac.strange.Suspension;
import com.github.strangefac.strange.Task;
import com.github.strangefac.strange.VoidTask;
import com.github.strangefac.strange.Wrapper;
import com.github.strangefac.strange.impl.SignatureInfo.SignatureKey;
import com.github.strangefac.strange.impl.StrangeImpl.TargetClass;
import com.github.strangefac.strange.util.UncheckedCast;
import gnu.trove.set.hash.THashSet;

class Invocation<V, E extends Throwable> extends FutureTask<V> implements InvocationLite {
  private static final SignatureKey PUBLIC_POST_TASK_SIGNATURE_KEY = new SignatureKey("post", Task.class);
  private static final SignatureKey POST_VOID_TASK_SIGNATURE_KEY = new SignatureKey("post", VoidTask.class);
  static final SignatureKey PRIVATE_POST_TASK_SIGNATURE_KEY = new SignatureKey("post", Task.class, Wrapper.class);
  private static final THashSet<SignatureKey> POST_TASK_SIGNATURE_KEYS = new THashSet<>(Arrays.asList(PUBLIC_POST_TASK_SIGNATURE_KEY, PRIVATE_POST_TASK_SIGNATURE_KEY));
  static final String STACK_TRACE_NOT_LOGGED_HERE_FORMAT = "{} stack trace not logged here: {}";
  private final Wrapper<V, E> _wrapper;
  private final PrivateActor _actor;
  private final long _nanoTime;
  private final int _batchSize;
  private final boolean _yield, _slow;

  /** @param batchSize The number of {@link InvocationInfo} objects covered by this object. */
  Invocation(Logger log, SignatureInfo signatureInfo, TargetClass<?> targetClass, Object target, Object[] args, Wrapper<V, E> wrapper, PrivateActor actor, long nanoTime, int batchSize, boolean afterTaskEnabled) {
    super(new Callable<V>() {
      private <T extends Throwable> T log(T t) {
        try {
          throw SFutureImpl.<E> unwrapCauseOfExecutionException(t);
        } catch (Throwable u) {
          log.debug(STACK_TRACE_NOT_LOGGED_HERE_FORMAT, signatureInfo, u);
        }
        return t;
      }

      public V call() throws Exception {
        // Observe we log the throwable if we think it will never be retrieved from the Future:
        try {
          if (POST_TASK_SIGNATURE_KEYS.contains(signatureInfo.key())) {
            Task<? extends V, ? extends E> task = UncheckedCast.uncheckedCast(args[0]); // Fake method body.
            try {
              return task.run();
            } catch (Throwable t) { // Even Suspension(s), which are unwrapped later.
              throw new InvocationTargetException(t, "Posted task failed."); // Simulate behaviour of real method call.
            }
          } else if (POST_VOID_TASK_SIGNATURE_KEY.equals(signatureInfo.key())) {
            VoidTask<? extends E> task = UncheckedCast.uncheckedCast(args[0]);
            try {
              task.run();
            } catch (Throwable t) {
              throw new InvocationTargetException(t, "Posted task failed.");
            }
            return null;
          } else {
            // We resolve the method so late so that the throwable gets stored in the Future:
            Method method = targetClass.resolve(signatureInfo.key());
            return UncheckedCast.uncheckedCast(method.invoke(target, args));
          }
        } catch (Exception e) {
          if (e instanceof InvocationTargetException && e.getCause() instanceof Suspension) {
            throw e; // Never log, it has special behaviour.
          } else {
            throw log(e);
          }
        } catch (Error e) {
          throw log(e);
        } finally {
          if (afterTaskEnabled) { // Literally means the cast will succeed.
            try {
              ((AfterTask) target).afterTask();
            } catch (Throwable t) {
              log.error("afterTask failed:", t);
            }
          }
        }
      }
    });
    _wrapper = wrapper;
    _actor = actor;
    _nanoTime = nanoTime;
    _batchSize = batchSize;
    _yield = signatureInfo.yield();
    _slow = signatureInfo.slow();
  }

  public boolean slow() {
    return _slow;
  }

  public long nanoTime() {
    return _nanoTime;
  }

  public int batchSize() {
    return _batchSize;
  }

  public void cancelWithInterrupt(boolean notJustIfYield) {
    if (notJustIfYield || _yield) {
      // We don't care about the return value, we just want the invocation to exit:
      cancel(true); // A method annotated with Yield expects that it may get an interrupt.
    }
  }

  // FIXME LATER: Don't let system break when IllegalStateException thrown here, e.g. user made wrapper done or a Suspension offered more than one outcome.
  protected void done() { // The super impl does nothing.
    if (isCancelled()) {
      _wrapper.putCancelled(); // Propagate the cancelled state.
    } else {
      // It wasn't a cancel, so this is the same thread that executed the above Callable, which is important in the no suspendables case.
      try {
        _wrapper.putValue(uninterruptibly(this::get)); // It should be fine to get uninterruptibly as we know this is done.
      } catch (ExecutionException e) {
        if (e.getCause() instanceof InvocationTargetException && e.getCause().getCause() instanceof Suspension) {
          suspension((Suspension) e.getCause().getCause());
        } else {
          _wrapper.putCauseOfExecutionException(e.getCause());
        }
      }
    }
  }

  private void suspension(Suspension suspension) {
    BasicJoin<?> join = suspension.getJoin();
    if (0 == join.size()) {
      // Treat the done block as an extension of the suspending method:
      Object value = null;
      boolean valueFlag = false;
      try {
        value = suspension.doneImmediately();
        valueFlag = true;
      } catch (Suspension s) {
        suspension(s);
      } catch (RuntimeException | Error t) {
        _wrapper.putCauseOfExecutionException(new InvocationTargetException(t));
      } catch (Throwable t) {
        _wrapper.putCauseOfInvocationTargetException(uncheckedCast(t));
      }
      if (valueFlag) _wrapper.putValue(uncheckedCast(value));
    } else {
      // Make each subtask post the done method to this actor:
      for (Suspendable subtask : join)
        subtask.postAfterDone(_actor, new PassSubtaskToDone<>(suspension, subtask), _wrapper);
    }
  }

  // MUST be static to avoid keeping magic reference to the Invocation and thus leaking memory.
  private static class PassSubtaskToDone<V, E extends Throwable> implements Task<V, E> {
    private final Suspension _suspension;
    private final Suspendable _subtask;

    private PassSubtaskToDone(Suspension suspension, Suspendable subtask) {
      _suspension = suspension;
      _subtask = subtask;
    }

    public V run() throws E, Suspension {
      try {
        return uncheckedCast(_suspension.done(_subtask));
      }
      // It will work just fine without the non-casting clause (see unit tests), but it's logically correct this way:
      catch (Error | RuntimeException | Suspension e) {
        throw e; // Let e.g. NPE through without casting to E.
      } catch (Throwable e) {
        // It's a non-suspend checked throwable, which we enforced by javadoc to be compatible with E:
        throw UncheckedCast.<Throwable, E> uncheckedCast(e);
      }
    }
  }
}
