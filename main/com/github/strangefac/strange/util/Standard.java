package com.github.strangefac.strange.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import com.github.strangefac.strange.function.IntConsumerThrows;
import com.github.strangefac.strange.function.VoidCallable;

public class Standard {
  public static <T> T also(T obj, Consumer<? super T> consumer) {
    consumer.accept(obj);
    return obj;
  }

  public static <T, U> U let(T obj, Function<? super T, ? extends U> function) {
    return function.apply(obj);
  }

  public static <T> T run(Supplier<? extends T> supplier) {
    return supplier.get();
  }

  public static <E extends Exception> void repeat(int n, IntConsumerThrows<? extends E> consumer) throws E {
    for (int i = 0; i < n; ++i)
      consumer.accept(i);
  }

  public static <E extends Exception> void repeat(int n, VoidCallable<? extends E> callable) throws E {
    for (; 0 < n; --n)
      callable.call();
  }

  private Standard() {
    // No.
  }
}
