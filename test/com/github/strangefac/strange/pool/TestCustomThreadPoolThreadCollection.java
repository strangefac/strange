package com.github.strangefac.strange.pool;

import static com.github.strangefac.strange.pool.CustomThreadPoolThreadCollection.NOT_ACCEPTING_NEW_MESSAGE;
import static com.github.strangefac.strange.util.SlowTests.slowTestsEnabled;
import static com.github.strangefac.strange.util.Standard.also;
import static com.github.strangefac.strange.util.Standard.repeat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import com.github.strangefac.strange.function.VoidCallable;
import com.github.strangefac.strange.pool.CustomThreadPoolThreadCollection;
import com.github.strangefac.strange.pool.Metrics;
import com.github.strangefac.strange.pool.PoolThread;

public class TestCustomThreadPoolThreadCollection {
  private static final long DELAY = 100;
  private static final int BLOAT = 10;

  /** Effectively treats negative results as false negatives, provided they are terminated by a positive result. */
  public static <E extends Exception> void acceptAny(int maxAttempts, VoidCallable<? extends E> attempt) throws E, InterruptedException {
    for (int a = 1;; ++a) {
      try {
        attempt.call();
        System.err.println(String.format("Took %s attempt(s).", a));
        break;
      } catch (AssertionError e) {
        if (a == maxAttempts) {
          System.err.println(String.format("All %s attempts failed.", a));
          throw e;
        }
      }
      int sleepTime = 100 * a * a; // int is plenty for what we're doing here.
      System.err.println(String.format("Sleeping for %.3f seconds.", sleepTime / 1000f));
      Thread.sleep(sleepTime);
    }
  }

  private CustomThreadPoolThreadCollection _pool;

  @After
  public void tearDown() throws InterruptedException {
    if (null != _pool) {
      try {
        _pool.dispose();
      } catch (IllegalStateException e) {
        // Some of the tests stopped it themselves, so ignore.
      }
    }
  }

  @Test
  public void states() throws InterruptedException {
    _pool = new CustomThreadPoolThreadCollection(10000, 0);
    CountDownLatch latch = new CountDownLatch(1);
    _pool.execute(latch::countDown);
    if (!latch.await(DELAY, TimeUnit.MILLISECONDS)) fail("Task did not execute.");
    Thread.sleep(DELAY); // Give the thread a chance to become inactive.
    Metrics metrics = _pool.takeMetrics();
    assertEquals(1, metrics.largestSize());
    assertEquals(1, metrics.size());
    assertEquals(0, metrics.activeCount());
    _pool.dispose();
    // The dispose method waited until all threads died, so no waiting necessary here:
    metrics = _pool.takeMetrics();
    assertEquals(1, metrics.largestSize());
    assertEquals(0, metrics.size());
    assertEquals(0, metrics.activeCount());
    also(catchThrowableOfType(() -> _pool.execute(() -> fail("Should not be executed.")), RejectedExecutionException.class), e -> {
      assertEquals(NOT_ACCEPTING_NEW_MESSAGE, e.getMessage());
    });
  }

  /**
   * Ensures that {@link CustomThreadPoolThreadCollection#promote(PoolThread)} actually helps redundant threads to time-out.
   * <p>
   * May fail if the box is under heavy load, which is annoying but not a big deal as nothing critical is being tested here.
   */
  private void utilisationImpl(int overlap) throws InterruptedException {
    if (overlap > BLOAT) throw new IllegalArgumentException("This test is for at most as many overlapping tasks as the bloated pool size.");
    Runnable task = () -> {
      try {
        Thread.sleep(DELAY / 2); // Necessary so that tasks started at the same time actually overlap.
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    };
    int iterations = 5;
    _pool = new CustomThreadPoolThreadCollection(DELAY * iterations, 0); // Turn off create throttling.
    try {
      IntConsumer burst = n -> repeat(n, () -> _pool.execute(task));
      // Create BLOAT threads:
      burst.accept(BLOAT);
      Thread.sleep(DELAY); // Make all threads clear.
      // Now the threads will time-out in DELAY * iterations - DELAY/2 milliseconds, as they all started to wait DELAY/2 milliseconds ago.
      for (int i = 0; i < iterations; ++i) {
        assertEquals(BLOAT, _pool.takeMetrics().size());
        burst.accept(overlap); // If we keep doing this, we expect BLOAT - overlap threads to give up and die.
        Thread.sleep(DELAY);
      }
      // It has been DELAY * iterations milliseconds, so redundant threads should have timed out:
      assertEquals(overlap, _pool.takeMetrics().size());
    } finally {
      _pool.dispose();
    }
  }

  private void utilisation(int overlap) throws InterruptedException {
    acceptAny(20, () -> utilisationImpl(overlap));
  }

  @Test
  public void utilisation0() throws InterruptedException {
    utilisation(0);
  }

  @Test
  public void utilisation1() throws InterruptedException {
    utilisation(1);
  }

  @Test
  public void utilisation5() throws InterruptedException {
    utilisation(5);
  }

  @Test
  public void utilisation10() throws InterruptedException {
    utilisation(10);
  }

  @Rule
  public final TestName _testName = new TestName();

  /** A burst of short tasks shouldn't create hundreds of threads, so we impose a min period on thread creation. */
  @Test
  public void createThrottling() throws InterruptedException {
    if (slowTestsEnabled(_testName)) acceptAny(20, this::createThrottlingImpl);
  }

  private void createThrottlingImpl() throws InterruptedException {
    _pool = new CustomThreadPoolThreadCollection(10000, 1300);
    try {
      ArrayList<LinkedBlockingQueue<Long>> futures = new ArrayList<>();
      repeat(7, () -> futures.add(new LinkedBlockingQueue<>()));
      for (LinkedBlockingQueue<Long> f : futures) {
        _pool.execute(() -> {
          f.add(System.nanoTime());
          try {
            Thread.sleep(500); // Make unavailable until the next round.
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
      }
      long ref = futures.get(0).take(); // Created a thread.
      ArrayList<Double> delays = new ArrayList<>();
      for (LinkedBlockingQueue<Long> f : futures.subList(1, futures.size()))
        delays.add((f.take() - ref) / 1e9);
      Collections.sort(delays); // Resubmitted tasks are likely to have been reordered.
      assertEquals(.5, delays.remove(0), 0.02); // Stolen.
      assertEquals(1, delays.remove(0), 0.02); // Stolen.
      assertEquals(1.3, delays.remove(0), 0.02); // Resubmitted, created a thread.
      assertEquals(1.5, delays.remove(0), 0.02); // Stolen by first thread.
      assertEquals(1.8, delays.remove(0), 0.02); // Stolen by second thread.
      assertEquals(2, delays.remove(0), 0.02); // Stolen by first thread.
      assertEquals(0, delays.size());
      assertEquals(2, _pool.takeMetrics().size());
    } finally {
      _pool.dispose();
    }
  }

  @Test
  public void disposeCanHandleThreadsThatAlreadyHaveTheExitInstruction() throws InterruptedException {
    _pool = new CustomThreadPoolThreadCollection(10000, 0);
    _pool.execute(() -> {
      ((PoolThread) Thread.currentThread())._taskHolder.exit(); // This thread will exit.
      try {
        Thread.sleep(DELAY); // Allow stop to execute.
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    Thread.sleep(DELAY / 2); // Allow the runnable to give the thread the exit instruction.
    _pool.dispose(); // Must not blow up.
  }
}
