package com.github.strangefac.strange.util;

/** RAII for Java. */
public interface Disposable {
  void dispose() throws Exception;
}
