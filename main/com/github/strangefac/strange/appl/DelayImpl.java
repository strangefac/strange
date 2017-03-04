package com.github.strangefac.strange.appl;

import static com.github.strangefac.strange.util.Standard.let;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.strangefac.strange.function.VoidCallable;
import com.github.strangefac.strange.util.Disposable;

public class DelayImpl extends AbstractDelay implements Disposable {
  private static final Logger LOG = LoggerFactory.getLogger(DelayImpl.class);

  private static class PrivateTaskControl {
    private final AtomicBoolean _token = new AtomicBoolean(true);
    private final CountDownLatch _cancelled = new CountDownLatch(1);

    private boolean tryTake() {
      return _token.compareAndSet(true, false);
    }

    private void setCancelled() {
      _cancelled.countDown();
    }

    private void awaitCancelled() throws InterruptedException {
      _cancelled.await();
    }
  }

  public static class TaskControlImpl implements TaskControl {
    private final PrivateTaskControl _control;
    private final ScheduledFuture<Void> _task; // Do not expose without hardening this code against user cancel.

    private TaskControlImpl(PrivateTaskControl control, ScheduledFuture<Void> task) {
      _control = control;
      _task = task;
    }

    public void waitFor() throws InterruptedException, ExecutionException {
      _task.get(); // We assume done state means control was taken, which should be safe as long as we don't allow user cancel.
    }

    public boolean cancelOrAllow() {
      return cancelOrAllow(VoidCallable.PASS);
    }

    // For testing.
    <E extends Exception> boolean cancelOrAllow(VoidCallable<? extends E> beforeCancel) throws E {
      if (_control.tryTake()) {
        beforeCancel.call();
        _task.cancel(false); // Set cancelled rather than done state, our impl doesn't need interrupt, should always return true.
        _control.setCancelled(); // Allow the alternate task body to complete.
        return true;
      } else {
        return false;
      }
    }

    // For testing.
    ScheduledFuture<Void> task() {
      return _task;
    }
  }

  private final ScheduledExecutorService _executor = let(getClass().getSimpleName(), threadName -> Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, threadName)));

  public TaskControl after(double seconds, VoidCallable<?> runnable) {
    return after(seconds, runnable, VoidCallable.PASS);
  }

  // For testing.
  <E extends Exception> TaskControlImpl after(double seconds, VoidCallable<?> runnable, VoidCallable<? extends E> beforeTryTake) {
    PrivateTaskControl control = new PrivateTaskControl();
    return new TaskControlImpl(control, _executor.schedule(() -> {
      beforeTryTake.call();
      if (control.tryTake()) {
        try {
          runnable.call();
        } catch (Throwable t) {
          LOG.error("Delayed runnable failed:", t);
        }
      } else {
        control.awaitCancelled(); // Prevent this going into the done state before cancel is invoked.
      }
      return null;
    }, Math.round(seconds * 1e9), TimeUnit.NANOSECONDS));
  }

  public void dispose() throws InterruptedException {
    // We use shutdownNow instead of shutdown so that existing (and possibly large) delays are cancelled.
    // A task-in-progress will probably complete, as there are no interruptible waits when queueing actor invocations.
    LOG.debug("{} task(s) never started.", _executor.shutdownNow().size());
    while (!_executor.awaitTermination(1, TimeUnit.SECONDS)) { // MUST be outside synchronized.
      // Do nothing.
    }
  }
}
