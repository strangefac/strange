package com.github.strangefac.strange.impl;

import org.slf4j.Logger;
import com.github.strangefac.strange.DeadActorException;
import com.github.strangefac.strange.IncrOrFalse;
import com.github.strangefac.strange.impl.StrangeImpl.TargetClass;

class Drain implements IncrOrFalse, Runnable {
  private final Mailbox _mailbox;
  private final Logger _log;
  private final TargetClass<?> _targetClass;
  private final Object _target;
  private int _remaining;

  /** Creates a runnable that will process just one invocation from the given queue, unless configured to process more using {@link #incrOrFalse()}. */
  Drain(Mailbox mailbox, Logger log, TargetClass<?> targetClass, Object target) {
    _mailbox = mailbox;
    _log = log;
    _targetClass = targetClass;
    _target = target;
    synchronized (this) {
      _remaining = 1;
    }
  }

  public synchronized boolean incrOrFalse() {
    if (0 == _remaining) return false;
    ++_remaining;
    return true;
  }

  public void run() { // Must return normally.
    int maxBatchSize;
    synchronized (this) {
      maxBatchSize = _remaining; // Typically 1, may be greater in theory.
    }
    try {
      while (true) {
        // Observe we assume there is something in the mailbox, and don't remove more than the authorised amount:
        InvocationLite invocation = _mailbox.load(_log, _targetClass, _target, maxBatchSize);
        invocation.run();
        _mailbox.unload();
        synchronized (this) {
          _remaining -= invocation.batchSize();
          if (0 == _remaining) break;
          maxBatchSize = _remaining;
        }
      }
    } catch (DeadActorException e) {
      _log.debug("Abort drain due to actor kill."); // The mailbox has already cancelled the remaining tasks.
    }
  }
}
