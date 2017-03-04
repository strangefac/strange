package com.github.strangefac.strange.impl;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.function.Consumer;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.AllActors;

public class AllActorsImpl implements AllActors {
  private final LinkedList<WeakReference<Actor>> _actors = new LinkedList<>();

  public synchronized void purgeAndAdd(Actor actor) {
    _actors.removeIf(ref -> null == ref.get());
    _actors.add(new WeakReference<>(actor));
  }

  public synchronized void get(Consumer<? super Actor> consumer) {
    for (WeakReference<Actor> r : _actors) {
      Actor actorOrNull = r.get(); // Note this creates a strong reference (if not null).
      if (null != actorOrNull) consumer.accept(actorOrNull);
    }
  }
}
