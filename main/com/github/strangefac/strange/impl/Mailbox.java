package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.impl.Invocation.PRIVATE_POST_TASK_SIGNATURE_KEY;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import org.slf4j.Logger;
import com.github.strangefac.strange.DeadActorException;
import com.github.strangefac.strange.DwellInfo;
import com.github.strangefac.strange.MailboxElement;
import com.github.strangefac.strange.PrivateActor;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.Wrapper;
import com.github.strangefac.strange.impl.StrangeImpl.TargetClass;
import com.github.strangefac.strange.util.TypedArrayList;
import com.github.strangefac.strange.util.UncheckedCast;

class Mailbox {
  static class Invocations<I extends MailboxElement> extends LinkedList<I> {
    private static final long serialVersionUID = 1L;

    void jumpQueue(I info) {
      for (ListIterator<I> i = listIterator(); i.hasNext();) {
        if (!i.next().jumpQueue()) {
          i.previous(); // Rewind to insert before the first non-jump-queue invocation.
          i.add(info);
          return;
        }
      }
      add(info); // They were all jump-queue invocations, so append.
    }

    private boolean hasImpatient() { // XXX: It would be more efficient to keep track of the number of patient items.
      if (isEmpty()) return false;
      if (!getFirst().patient()) return true;
      // Neither of the common cases fired, so now we iterate starting with index 1 (which needn't exist):
      for (Iterator<I> i = listIterator(1); i.hasNext();) {
        if (!i.next().patient()) {
          return true;
        }
      }
      return false; // All items are patient.
    }
  }

  static final String DEAD_ACTOR_MESSAGE = "Dead actor.";
  private final boolean _afterTaskEnabled;
  private Invocations<InvocationInfo<?, ?>> _invocationsOrNull;
  private InvocationLite _loadedOrNull;

  Mailbox(boolean afterTaskEnabled) {
    _afterTaskEnabled = afterTaskEnabled;
    synchronized (this) {
      _invocationsOrNull = new Invocations<>();
      _loadedOrNull = null;
    }
  }

  synchronized void kill(Logger log) {
    if (null == _invocationsOrNull) return; // Already killed.
    if (null != _loadedOrNull) _loadedOrNull.cancelWithInterrupt(true); // XXX: And set to null?
    for (InvocationInfo<?, ?> i : _invocationsOrNull)
      i.reject(log);
    _invocationsOrNull = null;
  }

  /** @return The wrapper, or null if this is {@link PrivateActor#post(com.github.strangefac.strange.Task, Wrapper)}. */
  synchronized <V, E extends Throwable> SFuture<V, E> add(PrivateActor actor, SignatureInfo signatureInfo, Object... args) throws DeadActorException {
    if (null == _invocationsOrNull) throw new DeadActorException();
    if (null != _loadedOrNull && !signatureInfo.patient()) {
      _loadedOrNull.cancelWithInterrupt(false); // Could already be "done", or could get called multiple times if it sticks around.
    }
    Wrapper<V, E> wrapper;
    SFuture<V, E> futureOrNull;
    if (PRIVATE_POST_TASK_SIGNATURE_KEY.equals(signatureInfo.key())) {
      wrapper = UncheckedCast.uncheckedCast(args[1]);
      futureOrNull = null;
    } else {
      WrapperImpl<V, E> wrapperImpl = new WrapperImpl<>();
      wrapper = wrapperImpl;
      futureOrNull = wrapperImpl;
    }
    InvocationInfo<V, E> info = new InvocationInfo<>(wrapper, actor, signatureInfo, args);
    if (signatureInfo.jumpQueue())
      _invocationsOrNull.jumpQueue(info);
    else
      _invocationsOrNull.add(info);
    return futureOrNull;
  }

  /** @param maxBatchSize Must be at least 1. */
  synchronized InvocationLite load(Logger log, TargetClass<?> targetClass, Object target, int maxBatchSize) throws DeadActorException, IllegalStateException {
    if (null == _invocationsOrNull) throw new DeadActorException();
    if (null != _loadedOrNull) throw new IllegalStateException();
    InvocationInfo<?, ?> info = _invocationsOrNull.removeFirst();
    // We do the bulk of the batching here as it's more efficient this way and doesn't break the mailbox size:
    List<InvocationInfo<?, ?>> batchTail;
    SignatureInfo signatureInfo = info.signatureInfo();
    if (signatureInfo.batch()) {
      batchTail = new TypedArrayList<>(InvocationInfo.class);
      while (!_invocationsOrNull.isEmpty() && signatureInfo.key().equals(_invocationsOrNull.getFirst().signatureInfo().key()) && (1 + batchTail.size()) < maxBatchSize)
        batchTail.add(_invocationsOrNull.removeFirst());
    } else {
      batchTail = Collections.emptyList();
    }
    // Observe we are checking yield after batch, so that a batch is considered as a whole:
    if (signatureInfo.yield() && _invocationsOrNull.hasImpatient())
      _loadedOrNull = info.toYieldInvocation(batchTail, log);
    else
      _loadedOrNull = info.toInvocation(batchTail, log, targetClass, target, _afterTaskEnabled);
    return _loadedOrNull;
  }

  synchronized void unload() throws IllegalStateException {
    if (null == _loadedOrNull) throw new IllegalStateException();
    _loadedOrNull = null;
  }

  synchronized int size() throws IllegalStateException {
    if (null == _invocationsOrNull) throw new IllegalStateException(DEAD_ACTOR_MESSAGE);
    return _invocationsOrNull.size();
  }

  private static class DwellInfoImpl implements DwellInfo {
    private final long _invocationNanoTime;
    private final int _mailboxSize;

    private DwellInfoImpl(long invocationNanoTime, int mailboxSize) {
      _invocationNanoTime = invocationNanoTime;
      _mailboxSize = mailboxSize;
    }

    public long dwellNanos(long systemNanoTime) {
      return systemNanoTime - _invocationNanoTime;
    }

    public int mailboxSize() {
      return _mailboxSize;
    }
  }

  /** Nothing is being executed and the mailbox is empty. */
  private static final DwellInfo TRIVIAL_DWELL_INFO = new DwellInfo() {
    public long dwellNanos(long systemNanoTime) {
      return 0;
    }

    public int mailboxSize() {
      return 0;
    }
  };

  synchronized DwellInfo getDwellInfo() {
    if (null == _invocationsOrNull) return TRIVIAL_DWELL_INFO; // Legit, it's just permanent now.
    int mailboxSize = _invocationsOrNull.size();
    if (null != _loadedOrNull && !_loadedOrNull.slow()) return new DwellInfoImpl(_loadedOrNull.nanoTime(), mailboxSize);
    if (0 != mailboxSize) return new DwellInfoImpl(_invocationsOrNull.getFirst().nanoTime(), mailboxSize);
    return TRIVIAL_DWELL_INFO;
  }
}
