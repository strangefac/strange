package com.github.strangefac.strange.util;

import java.util.concurrent.TimeUnit;

public class Nanoseconds {
  public static Nanoseconds now() {
    return new Nanoseconds(System.nanoTime());
  }

  private final long _nanos;

  public Nanoseconds(long nanos) {
    _nanos = nanos;
  }

  public void timedWait(Object obj) throws InterruptedException {
    TimeUnit.NANOSECONDS.timedWait(obj, _nanos);
  }

  public boolean gt() {
    return _nanos > 0;
  }

  public boolean ge() {
    return _nanos >= 0;
  }

  public Nanoseconds add(Nanoseconds that) {
    return new Nanoseconds(_nanos + that._nanos);
  }

  public Nanoseconds sub(Nanoseconds that) {
    return new Nanoseconds(_nanos - that._nanos);
  }
}
