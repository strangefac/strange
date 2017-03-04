package com.github.strangefac.strange;

public interface DwellInfo {
  long dwellNanos(long systemNanoTime);

  int mailboxSize();
}
