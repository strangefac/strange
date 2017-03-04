package com.github.strangefac.strange.impl;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.MailboxElement;
import com.github.strangefac.strange.PrivateActor;
import com.github.strangefac.strange.Wrapper;
import com.github.strangefac.strange.impl.StrangeImpl.TargetClass;
import com.github.strangefac.strange.util.UncheckedCast;

class InvocationInfo<V, E extends Throwable> implements MailboxElement {
  private final long _nanoTime = System.nanoTime(); // On my Linux VM, performance is competitive with currentTimeMillis.
  private final Wrapper<V, E> _wrapper;
  private final PrivateActor _actor;
  private final SignatureInfo _signatureInfo;
  private final Object[] _args; // Note this final field is what ensures safe publication of the args.

  /** For accuracy of {@link Actor#getDwellInfo()} this should be added to the mailbox immediately. */
  InvocationInfo(Wrapper<V, E> wrapper, PrivateActor actor, SignatureInfo signatureInfo, Object... args) {
    _wrapper = wrapper;
    _actor = actor;
    _signatureInfo = signatureInfo;
    _args = args;
  }

  long nanoTime() {
    return _nanoTime;
  }

  SignatureInfo signatureInfo() {
    return _signatureInfo;
  }

  public boolean jumpQueue() {
    return _signatureInfo.jumpQueue();
  }

  public boolean patient() {
    return _signatureInfo.patient();
  }

  Invocation<V, E> toInvocation(Collection<? extends InvocationInfo<?, ?>> batchTail, Logger log, TargetClass<?> targetClass, Object target, boolean afterTaskEnabled) {
    int batchSize;
    Object[] args;
    Wrapper<V, E> wrapper;
    if (_signatureInfo.batch()) {
      batchSize = 1 + batchTail.size();
      args = new Object[_args.length];
      // Create the arrays and fill in their first elements with this info's args:
      for (int argIndex = 0; argIndex < _args.length; ++argIndex) {
        args[argIndex] = Array.newInstance(_signatureInfo.key().parameterType(argIndex), batchSize);
        Array.set(args[argIndex], 0, _args[argIndex]);
      }
      // Now fill in the elements for every remaining info in the batch:
      Iterator<? extends InvocationInfo<?, ?>> tailIterator = batchTail.iterator();
      for (int batchIndex = 1; batchIndex < batchSize; ++batchIndex) {
        InvocationInfo<?, ?> that = tailIterator.next();
        for (int argIndex = 0; argIndex < _args.length; ++argIndex)
          Array.set(args[argIndex], batchIndex, that._args[argIndex]);
      }
      wrapper = batchTail.isEmpty() ? _wrapper : new Wrapper<V, E>() {
        public void putCancelled() {
          _wrapper.putCancelled();
          for (InvocationInfo<?, ?> that : batchTail)
            that._wrapper.putCancelled();
        }

        public void putValue(V value) {
          _wrapper.putValue(value);
          for (InvocationInfo<?, ?> that : batchTail)
            UncheckedCast.<InvocationInfo<?, ?>, InvocationInfo<V, ?>> uncheckedCast(that)._wrapper.putValue(value);
        }

        public void putCauseOfInvocationTargetException(E checkedThrowable) {
          _wrapper.putCauseOfInvocationTargetException(checkedThrowable);
          for (InvocationInfo<?, ?> that : batchTail)
            UncheckedCast.<InvocationInfo<?, ?>, InvocationInfo<?, E>> uncheckedCast(that)._wrapper.putCauseOfInvocationTargetException(checkedThrowable);
        }

        public void putCauseOfExecutionException(Throwable throwable) {
          _wrapper.putCauseOfExecutionException(throwable);
          for (InvocationInfo<?, ?> that : batchTail)
            that._wrapper.putCauseOfExecutionException(throwable);
        }
      };
    } else {
      batchSize = 1;
      args = _args;
      wrapper = _wrapper;
    }
    return new Invocation<>(log, _signatureInfo, targetClass, target, args, wrapper, _actor, _nanoTime, batchSize, afterTaskEnabled);
  }

  static final String YIELDING_FORMAT = "Yielding instead of {}.";

  InvocationLite toYieldInvocation(List<InvocationInfo<?, ?>> batchTail, Logger log) {
    return new InvocationLite() {
      public boolean slow() {
        return false; // This invocation runs for such a short time, it doesn't really matter what we put here.
      }

      public void run() {
        log.info(YIELDING_FORMAT, _signatureInfo);
        _wrapper.putCancelled();
        for (InvocationInfo<?, ?> that : batchTail)
          that._wrapper.putCancelled();
      }

      public long nanoTime() {
        return _nanoTime;
      }

      public int batchSize() {
        return 1 + batchTail.size();
      }

      public void cancelWithInterrupt(boolean notJustIfYield) {
        // Do nothing, we already cancel the wrapper manually (and quickly).
      }
    };
  }

  static final String REJECTING_FORMAT = "Rejecting: {}";

  void reject(Logger log) {
    log.debug(REJECTING_FORMAT, _signatureInfo);
    _wrapper.putCancelled();
  }
}
