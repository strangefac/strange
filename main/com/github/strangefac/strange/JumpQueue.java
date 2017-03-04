package com.github.strangefac.strange;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For use on actor interface methods. When a jump-queue invocation is posted, it will go to the front of the queue, but after any jump-queue invocations
 * already in the queue.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JumpQueue {
  // No members.
}
