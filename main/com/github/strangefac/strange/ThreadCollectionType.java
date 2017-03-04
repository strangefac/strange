package com.github.strangefac.strange;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// It's actually only for classes, but TYPE will have to do:
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
// A subclass will almost never want to override the thread collection:
@Inherited
public @interface ThreadCollectionType {
  /**
   * @return The key for the desired {@link ThreadCollection} in the DI container. The thread collection must be running before the actor is instantiated, so
   * that the target's init method can be invoked.
   */
  Class<? extends ThreadCollection> value();
}
