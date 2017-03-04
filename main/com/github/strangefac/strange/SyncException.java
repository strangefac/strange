package com.github.strangefac.strange;

public class SyncException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public SyncException(String message, Throwable cause) {
    super(message, cause);
  }
}
