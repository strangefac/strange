package com.github.strangefac.strange.util;

/** DI container abstraction. */
public interface ComponentSource {
  <T> T getComponent(Class<T> componentType);
}
