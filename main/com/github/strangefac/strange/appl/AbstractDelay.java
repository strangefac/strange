package com.github.strangefac.strange.appl;

import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.Delay;
import com.github.strangefac.strange.Suspension;
import com.github.strangefac.strange.VoidTask;
import com.github.strangefac.strange.VoidTask8;

abstract class AbstractDelay implements Delay {
  private static <E extends Throwable> void runTask8(VoidTask8<E> task) throws E, Suspension {
    task.run(task);
  }

  public TaskControl after(double seconds, Actor actor, VoidTask<?> task, Logger log) {
    return after(seconds, () -> actor.post(task).andForget(log));
  }

  public TaskControl after(double seconds, Actor actor, VoidTask8<?> task, Logger log) {
    return after(seconds, () -> actor.post(() -> {
      runTask8(task);
      return null;
    }).andForget(log));
  }
}
