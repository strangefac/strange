package com.github.strangefac.strange.pool;

import static com.github.strangefac.strange.pool.PoolThread.EMPTY_POOL_THREAD_ARRAY;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.strangefac.strange.util.Disposable;
import com.github.strangefac.strange.util.TypedArrayList;
import gnu.trove.set.hash.TLinkedHashSet;

/**
 * Like {@link Executors#newCachedThreadPool()} we don't limit the number of threads, to avoid deadlock when 100 actors try to access the same getter. Unlike
 * the JDK pool, this one attempts to maximise the utilisation of every thread, so that redundant threads time-out promptly. Also there is a facility to
 * throttle thread creation, so that a burst of short-lived tasks does not result in lots and lots of threads.
 * <p>
 * In theory this collection could include the awt event thread, but that would slow the ui.
 */
public class CustomThreadPoolThreadCollection implements Disposable, PoolThreadOwner, ThreadPoolThreadCollection {
  private static final Logger LOG = LoggerFactory.getLogger(CustomThreadPoolThreadCollection.class);
  static final String NOT_ACCEPTING_NEW_MESSAGE = "Not accepting new tasks.";
  private final long _threadTimeoutMillis, _minCreatePeriodNanos;

  /**
   * @param threadTimeout e.g. 10000 to make an existing thread terminate after 10 seconds of being idle.
   * @param minCreatePeriod e.g. 100 to limit thread creation to 10 threads per second. May be 0 for no limit.
   */
  public CustomThreadPoolThreadCollection(long threadTimeout, long minCreatePeriod) {
    _threadTimeoutMillis = threadTimeout;
    _minCreatePeriodNanos = TimeUnit.NANOSECONDS.convert(minCreatePeriod, TimeUnit.MILLISECONDS);
  }

  /** From most recently clear to likely clear to likely occupied. */
  private final TLinkedHashSet<PoolThread> _threads = new TLinkedHashSet<>();
  private final ScheduledExecutorService _resubmit = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Resubmit"));
  private final TypedArrayList<Runnable> _resubmitCommands = new TypedArrayList<>(Runnable.class);
  private boolean _acceptNew;
  private int _threadNumber;
  private int _largestSize;
  private long _minCreateTimeNanos;
  private PoolThread[] _otherThreads;
  {
    synchronized (this) {
      _acceptNew = true;
      _threadNumber = 1;
      _largestSize = 0;
      _minCreateTimeNanos = System.nanoTime();
      _otherThreads = EMPTY_POOL_THREAD_ARRAY;
    }
  }

  /** @throws RejectedExecutionException If not started or is stopping. */
  public synchronized void execute(Runnable command) throws RejectedExecutionException {
    if (!_acceptNew) throw new RejectedExecutionException(NOT_ACCEPTING_NEW_MESSAGE);
    // Try to find a clear thread, starting with the most likely to be clear:
    for (Iterator<PoolThread> i = _threads.iterator(); i.hasNext();) {
      PoolThread thread = i.next();
      if (thread._taskHolder.put(command)) {
        // Move it to the end of the list, as it's now the least likely to be clear:
        i.remove();
        _threads.add(thread);
        return;
      }
    }
    // We need to create a thread, but first we have to observe the min create period:
    long nowNanos = System.nanoTime(), delayNanos = _minCreateTimeNanos - nowNanos;
    if (delayNanos > 0) {
      LOG.trace("[{}] Resubmit after {} nanos.", command, delayNanos);
      _resubmitCommands.add(command);
      _resubmit.schedule(_resubmitTask, delayNanos, TimeUnit.NANOSECONDS); // Like prepending a pause to command.
      return;
    }
    LOG.debug("[{}] Creating thread for pool size: {}", command, _threads.size() + 1);
    PoolThread thread = new PoolThread(getClass().getSimpleName() + '-' + _threadNumber++, command, this, _threadTimeoutMillis);
    thread.start();
    _threads.add(thread); // The only place the set is grown.
    _largestSize = Math.max(_largestSize, _threads.size());
    _minCreateTimeNanos = nowNanos + _minCreatePeriodNanos;
  }

  private final Runnable _resubmitTask = () -> resubmitSafely(true);

  private synchronized void resubmitSafely(boolean scheduled) {
    if (_resubmitCommands.isEmpty()) {
      if (scheduled) LOG.trace("Cool, nothing to resubmit.");
    } else {
      Runnable command = _resubmitCommands.remove(_resubmitCommands.size() - 1);
      try {
        LOG.trace("[{}] Resubmitting {}.", command, scheduled ? "on time" : "early");
        execute(command);
      } catch (Throwable t) {
        LOG.error(String.format("[%s] Failed to resubmit.", command), t); // Should not happen before stop.
      }
    }
  }

  public synchronized void promote(PoolThread thread) {
    // This is clearly inefficient, but there should only be of the order of 100 elements:
    _threads.remove(thread);
    int n = _threads.size();
    _otherThreads = _threads.toArray(_otherThreads); // Note may contain garbage after first n elements.
    _threads.clear(); // Does not affect capacity.
    _threads.add(thread);
    for (int i = 0; i < n; ++i)
      _threads.add(_otherThreads[i]);
    resubmitSafely(false);
  }

  public synchronized void discard(PoolThread thread) {
    _threads.remove(thread);
    LOG.debug("Pool size decremented to: {}", _threads.size());
  }

  public Metrics takeMetrics() {
    int largestSizeSnapshot;
    PoolThread[] threadsSnapshot;
    synchronized (this) {
      largestSizeSnapshot = _largestSize;
      threadsSnapshot = _threads.toArray(EMPTY_POOL_THREAD_ARRAY);
    }
    // Do this outside of synchronized so as not to hog the pool lock:
    int activeCount = 0;
    for (PoolThread thread : threadsSnapshot) {
      if (!thread._taskHolder.isClear()) ++activeCount;
    }
    return new Metrics(largestSizeSnapshot, threadsSnapshot.length, activeCount);
  }

  /**
   * Similar behaviour to {@link ExecutorService#shutdown()} in that existing tasks are allowed to complete.
   * 
   * @throws IllegalStateException If already disposed.
   */
  public void dispose() throws IllegalStateException, InterruptedException {
    // TODO LATER: Not a big deal as all significant work should have stopped already, but this should be a lot smarter i.e. only reject new tasks.
    _resubmit.shutdown(); // Any new or resubmitted execute that requires resubmit will fail.
    while (!_resubmit.isTerminated())
      _resubmit.awaitTermination(1, TimeUnit.SECONDS); // Resubmit involves getting the lock.
    PoolThread[] threads;
    synchronized (this) {
      if (!_acceptNew) throw new IllegalStateException("Already stopped.");
      _acceptNew = false;
      for (PoolThread thread : _threads) {
        try {
          thread._taskHolder.exit();
        } catch (IllegalStateException e) {
          // Ignore: The thread already knows it has to exit.
        }
      }
      threads = _threads.toArray(EMPTY_POOL_THREAD_ARRAY);
    }
    // We can't synchronize around the joins as each thread calls discard before it dies:
    for (PoolThread thread : threads)
      thread.join(); // May take a while if it was busy with something.
  }
}
