package com.github.strangefac.strange.util;

public class UncheckedCast {
  /** Intended for use with APIs that return raw types. */
  @SuppressWarnings("unchecked")
  public static <S, T extends S> T uncheckedCast(S obj) {
    return (T) obj;
  }

  private UncheckedCast() {
    // No.
  }
}
