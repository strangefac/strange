package com.github.strangefac.strange.function;

/** Type arg for e.g. SFuture meaning no checked exceptions are thrown. */
public class VoidCheckedException extends RuntimeException { // That's right, it's a RuntimeException.
  private static final long serialVersionUID = 1L;

  private VoidCheckedException() {
    // No.
  }
}
