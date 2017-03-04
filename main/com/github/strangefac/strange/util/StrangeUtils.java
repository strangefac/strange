package com.github.strangefac.strange.util;

import java.util.concurrent.Callable;
import com.github.strangefac.strange.function.FunctionThrows;

public class StrangeUtils {
  /** @see Callable */
  public interface Interruptible<V, E extends Exception> {
    V call() throws InterruptedException, E;
  }

  /** @see FunctionThrows */
  public interface InterruptibleWait<V, E extends Exception> {
    /**
     * @param now The {@link System#nanoTime()} just before this method call.
     * @param strictlyPositiveTimeoutOrNull Either a strictly positive timeout in nanoseconds, or null if waiting is not necessary, in which case the now arg
     * can save you a call to nanoTime if you need it.
     */
    V apply(Nanoseconds now, Nanoseconds strictlyPositiveTimeoutOrNull) throws InterruptedException, E;
  }

  public static <V, E extends Exception> V uninterruptibly(Interruptible<? extends V, ? extends E> interruptible) throws E {
    boolean interruptedException = false;
    try {
      while (true) {
        try {
          return interruptible.call();
        } catch (InterruptedException e) {
          interruptedException = true;
        }
      }
    } finally {
      if (interruptedException) Thread.currentThread().interrupt();
    }
  }

  /** @param target The target {@link System#nanoTime()}. */
  public static <V, E extends Exception> V uninterruptibly(Nanoseconds target, InterruptibleWait<? extends V, ? extends E> interruptibleWait) throws E {
    return uninterruptibly(() -> {
      Nanoseconds now = Nanoseconds.now(), timeout = target.sub(now);
      return interruptibleWait.apply(now, timeout.gt() ? timeout : null);
    });
  }

  public static <T> T notNull(String objName, T obj) throws IllegalArgumentException {
    if (null == obj) throw new IllegalArgumentException(String.format("%s must not be null.", objName));
    return obj;
  }

  private StrangeUtils() {
    // No.
  }
}
