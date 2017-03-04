package com.github.strangefac.strange;

import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import com.github.strangefac.strange.function.VoidCallable;

public interface Delay {
  interface TaskControl {
    /** @return true if the task was cancelled, false if we were too late to prevent it running. */
    boolean cancelOrAllow();

    /** On return the task will be in the done state, so a subsequent call to {@link #cancelOrAllow()} will fail. */
    void waitFor() throws InterruptedException, ExecutionException;
  }

  /**
   * @param seconds Negative values have the same effect as zero.
   * @param runnable Must be thread-safe and should return promptly, typically by just posting one or more fire-and-forget invocations.
   */
  TaskControl after(double seconds, VoidCallable<?> runnable);

  /**
   * Convenience if all you want to do is post to an actor. Also accepts non-void lambdas.
   * 
   * @param log For {@link SFuture#andForget(Logger)}, normally the logger of the caller.
   */
  TaskControl after(double seconds, Actor actor, VoidTask<?> task, Logger log);

  /** Like {@link #after(double, Actor, VoidTask, Logger)} but the task gets a reference to itself. */
  TaskControl after(double seconds, Actor actor, VoidTask8<?> task, Logger log);
}
