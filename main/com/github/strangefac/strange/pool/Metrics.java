package com.github.strangefac.strange.pool;

public class Metrics {
  private final int _largestSize, _size, _activeCount;

  Metrics(int largestSize, int size, int activeCount) {
    _largestSize = largestSize;
    _size = size;
    _activeCount = activeCount;
  }

  public int largestSize() {
    return _largestSize;
  }

  public int size() {
    return _size;
  }

  public int activeCount() {
    return _activeCount;
  }
}
