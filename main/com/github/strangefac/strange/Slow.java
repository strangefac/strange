package com.github.strangefac.strange;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The annotated actor interface method will contribute to the dwell time only while on the queue, not when actually running. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Slow {
  // No members.
}
