package com.github.strangefac.strange;

import java.util.function.Consumer;
import com.github.strangefac.strange.impl.StrangeImpl;

public interface AllActors {
  /** Called by {@link StrangeImpl}, there is no need to call this manually. */
  void purgeAndAdd(Actor actor);

  /**
   * Returns all actors spawned by the application, that have not yet been garbage collected. This can be useful for gathering statistics.
   * 
   * @param consumer Must execute quickly or spawns may be delayed.
   */
  void get(Consumer<? super Actor> consumer);
}
