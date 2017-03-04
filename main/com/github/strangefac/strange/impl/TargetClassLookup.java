package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.util.UncheckedCast.uncheckedCast;
import java.util.concurrent.ConcurrentHashMap;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTargetThrows;
import com.github.strangefac.strange.impl.StrangeImpl.TargetClass;

class TargetClassLookup {
  static final TargetClassLookup TARGET_CLASSES = new TargetClassLookup();
  private final ConcurrentHashMap<Class<?>, TargetClass<?>> _targetClasses = new ConcurrentHashMap<>();

  private TargetClassLookup() {
    // Singleton.
  }

  <A extends Actor> TargetClass<A> getOrCreate(Class<? extends ActorTargetThrows<A, ?>> targetImpl) {
    return uncheckedCast(_targetClasses.computeIfAbsent(targetImpl, key -> new TargetClass<>(targetImpl)));
  }
}
