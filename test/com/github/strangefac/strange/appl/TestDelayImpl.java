package com.github.strangefac.strange.appl;

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import com.github.strangefac.strange.appl.DelayImpl;
import com.github.strangefac.strange.appl.DelayImpl.TaskControlImpl;
import com.github.strangefac.strange.function.VoidCallable;

public class TestDelayImpl {
  private final DelayImpl _delay = new DelayImpl();

  @After
  public void tearDown() throws InterruptedException {
    _delay.dispose();
  }

  @Test
  public void cancelOrAllowSuccessBeforeExecution() throws InterruptedException {
    CountDownLatch running = new CountDownLatch(1);
    TaskControlImpl control = _delay.after(.3, running::countDown, VoidCallable.PASS);
    ScheduledFuture<Void> task = control.task();
    assertFalse(task.isDone());
    assertFalse(task.isCancelled());
    assertTrue(control.cancelOrAllow());
    assertTrue(task.isDone());
    assertTrue(task.isCancelled());
    catchThrowableOfType(task::get, CancellationException.class);
    // Finally, make sure it doesn't run anyway:
    assertFalse(running.await(600, TimeUnit.MILLISECONDS));
  }

  @Test
  public void cancelOrAllowSuccessAfterExecutionStartedWithAwaitBeforeCancel() throws InterruptedException {
    cancelOrAllowSuccessAfterExecutionStarted(true);
  }

  @Test
  public void cancelOrAllowSuccessAfterExecutionStartedWithCancelBeforeAwait() throws InterruptedException {
    cancelOrAllowSuccessAfterExecutionStarted(false);
  }

  private void cancelOrAllowSuccessAfterExecutionStarted(boolean awaitBeforeCancel) throws InterruptedException {
    CountDownLatch inTask = new CountDownLatch(1);
    CountDownLatch tryTake = new CountDownLatch(1);
    CountDownLatch running = new CountDownLatch(1);
    TaskControlImpl control = _delay.after(0, running::countDown, () -> {
      inTask.countDown();
      tryTake.await();
    });
    inTask.await();
    ScheduledFuture<Void> task = control.task();
    assertFalse(task.isDone());
    assertFalse(task.isCancelled());
    assertTrue(control.cancelOrAllow(() -> {
      // Simply getting the token changes nothing:
      assertFalse(task.isDone());
      assertFalse(task.isCancelled());
      if (awaitBeforeCancel) {
        tryTake.countDown();
        // Ensure the task thread is in await:
        Thread.sleep(100);
        assertFalse(task.isDone());
        assertFalse(task.isCancelled());
      }
    }));
    // Once cancel has been called the task is both done and cancelled:
    assertTrue(task.isDone());
    assertTrue(task.isCancelled());
    if (!awaitBeforeCancel) tryTake.countDown(); // Should go straight through awaitCancelled.
    catchThrowableOfType(task::get, CancellationException.class);
    assertFalse(running.await(100, TimeUnit.MILLISECONDS)); // Make sure it didn't run.
  }

  @Test
  public void cancelOrAllowFailure() throws InterruptedException, ExecutionException {
    CountDownLatch running = new CountDownLatch(1);
    TaskControlImpl control = _delay.after(0, () -> {
      running.countDown();
      Thread.sleep(100);
    }, VoidCallable.PASS);
    running.await();
    ScheduledFuture<Void> task = control.task();
    assertFalse(task.isDone());
    assertFalse(task.isCancelled());
    assertFalse(control.cancelOrAllow());
    assertFalse(task.isDone());
    assertFalse(task.isCancelled());
    assertEquals(null, task.get()); // Wait for it to complete.
    assertTrue(task.isDone());
    assertFalse(task.isCancelled());
  }
}
