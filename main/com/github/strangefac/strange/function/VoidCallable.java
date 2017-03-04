package com.github.strangefac.strange.function;

/** @see java.util.concurrent.Callable */
public interface VoidCallable<E extends Exception> {
  VoidCallable<VoidCheckedException> PASS = () -> {
    // Do nothing.
  };

  void call() throws E;
}
