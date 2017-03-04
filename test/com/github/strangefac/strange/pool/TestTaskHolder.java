package com.github.strangefac.strange.pool;

import static com.github.strangefac.strange.pool.TaskHolder.EXIT_TASK;
import static com.github.strangefac.strange.util.Standard.repeat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import com.github.strangefac.strange.function.VoidCallable;
import com.github.strangefac.strange.pool.TaskHolder;
import com.github.strangefac.strange.util.EasyMockRule;

public class TestTaskHolder {
  private static final long TOO_LONG = 10000, BEFORE_TOO_LONG = 5000;
  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();
  // I'm not sure we depend on it in these tests, but note in EasyMock 2.5 all mocks are thread-safe by default:
  private final Runnable _runnable = _mocks.createMock(Runnable.class);
  private final TaskHolder _taskHolder = new TaskHolder(_runnable);

  @After
  public void tearDown() {
    assertFalse(Thread.interrupted()); // Just in case. Note failure here could mask the real error.
  }

  @Test
  public void initialState() {
    _mocks.replay();
    assertSame(_runnable, _taskHolder.peekOrExit(0)); // No need to wait for the initial task.
  }

  // Quite a small timeout, but on failure is a useful indicator of load that may affect other tests:
  @Test(timeout = 200)
  public void zeroTimeoutDoesNotMeanWaitForever() {
    _mocks.replay();
    assertTrue(_taskHolder.consume());
    _taskHolder.peekOrExit(0);
  }

  @Test
  public void consumeWhenEmpty() {
    _mocks.replay();
    assertTrue(_taskHolder.consume());
    catchThrowableOfType(_taskHolder::consume, IllegalStateException.class);
  }

  /** The holder must have exactly one task (e.g. the initial task) when this is called. */
  private void consumeAndCheckTwoThreadsNotified(VoidCallable<?> notifyAll, Object... results) throws Exception {
    assertTrue(_taskHolder.consume());
    BlockingQueue<Object> q = new LinkedBlockingQueue<>();
    repeat(2, () -> {
      new Thread(() -> {
        try {
          q.add(_taskHolder.peekOrExit(TOO_LONG));
        } catch (Throwable t) {
          q.add(t);
        }
      }).start();
    });
    Thread.sleep(100); // Ensure the threads are waiting.
    assertEquals(0, q.size()); // And are still waiting.
    notifyAll.call();
    for (Object result : results)
      assertEquals(result, q.take());
    Thread.sleep(100); // Give any bad events a chance to happen.
    assertEquals(0, q.size());
  }

  @Test
  public void peekWorks() throws Exception {
    Runnable otherRunnable = _mocks.createMock(Runnable.class);
    _mocks.replay();
    consumeAndCheckTwoThreadsNotified(() -> assertEquals(true, _taskHolder.put(otherRunnable)), otherRunnable, otherRunnable);
  }

  @Test
  public void peekTimeout() throws Exception {
    _mocks.replay();
    consumeAndCheckTwoThreadsNotified(() -> {
      assertSame(EXIT_TASK, _taskHolder.peekOrExit(100)); // Adds and returns the exit task, notifying all threads.
    }, EXIT_TASK, EXIT_TASK);
  }

  @Test(timeout = BEFORE_TOO_LONG)
  public void peekInterruptedWithoutTaskInHolder() {
    _mocks.replay();
    assertTrue(_taskHolder.consume());
    Thread.currentThread().interrupt();
    assertSame(EXIT_TASK, _taskHolder.peekOrExit(TOO_LONG)); // Exit task added due to interrupt.
    assertTrue(Thread.interrupted());
  }

  /** Callers should have a timeout of {@link #BEFORE_TOO_LONG}. */
  private void peekInterruptedWithTask(boolean holderAlsoHasExitTask) {
    _runnable.run();
    _mocks.replay();
    if (holderAlsoHasExitTask) _taskHolder.exit();
    Thread.currentThread().interrupt();
    assertSame(_runnable, _taskHolder.peekOrExit(TOO_LONG)); // No waiting necessary.
    assertTrue(Thread.currentThread().isInterrupted()); // Check it wasn't cleared (it shouldn't even have been explicitly propagated).
    // Simulate behaviour of the consuming thread:
    _runnable.run();
    assertEquals(!holderAlsoHasExitTask, _taskHolder.consume());
    // If holderAlsoHasExitTask is true then the exit task is already there, otherwise it is added due to interrupt:
    assertSame(EXIT_TASK, _taskHolder.peekOrExit(TOO_LONG));
    assertTrue(Thread.interrupted());
  }

  @Test(timeout = BEFORE_TOO_LONG)
  public void peekInterruptedWithTaskInHolder() {
    peekInterruptedWithTask(false);
  }

  @Test(timeout = BEFORE_TOO_LONG)
  public void peekInterruptedWithTaskAndExitInHolder() {
    peekInterruptedWithTask(true);
  }

  @Test
  public void putFailure() {
    Runnable otherRunnable = _mocks.createMock(Runnable.class);
    _mocks.replay();
    assertEquals(false, _taskHolder.put(otherRunnable));
    assertSame(_runnable, _taskHolder.peekOrExit(0)); // Initial task is still there.
  }

  // This timeout ensures the final peekOrExit doesn't hit its timeout:
  @Test(timeout = BEFORE_TOO_LONG)
  public void exitWorks() {
    _runnable.run();
    _mocks.replay();
    _taskHolder.exit(); // We can add it whether the thread is clear or not.
    catchThrowableOfType(_taskHolder::exit, IllegalStateException.class); // But not a second time.
    _taskHolder.peekOrExit(0).run(); // Would blow up if it was EXIT_TASK.
    assertFalse(_taskHolder.consume());
    assertSame(EXIT_TASK, _taskHolder.peekOrExit(TOO_LONG));
  }

  @Test
  public void exitNotify() throws Exception {
    _mocks.replay();
    consumeAndCheckTwoThreadsNotified(_taskHolder::exit, EXIT_TASK, EXIT_TASK);
  }

  private void putExit(boolean clear) {
    _mocks.replay();
    if (clear) assertTrue(_taskHolder.consume());
    catchThrowableOfType(() -> _taskHolder.put(EXIT_TASK), IllegalArgumentException.class);
  }

  @Test
  public void putExitClear() {
    putExit(true);
  }

  @Test
  public void putExitBusy() {
    putExit(false);
  }

  @Test
  public void consumeExit() {
    _mocks.replay();
    assertTrue(_taskHolder.consume());
    _taskHolder.exit();
    catchThrowableOfType(_taskHolder::consume, IllegalStateException.class);
  }
}
