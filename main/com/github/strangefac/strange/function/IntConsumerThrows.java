package com.github.strangefac.strange.function;

import java.util.function.IntConsumer;

/** @see IntConsumer */
public interface IntConsumerThrows<E extends Exception> {
  void accept(int value) throws E;
}
