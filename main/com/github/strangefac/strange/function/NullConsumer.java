package com.github.strangefac.strange.function;

import java.util.function.Consumer;

public class NullConsumer implements Consumer<Object> {
  public static final NullConsumer NULL_CONSUMER = new NullConsumer();

  private NullConsumer() {
    // Singleton.
  }

  public void accept(Object t) {
    // Do nothing.
  }
}
