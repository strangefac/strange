package com.github.strangefac.strange;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For use on actor interface methods. Suppose an actor method is annotated, then each parameter type of the corresponding method on the actor target must be
 * the array of the actor method parameter type in the same position. Adjacent invocations of the actor method may then be batched, if they would otherwise wait
 * in the mailbox.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Batch {
  // No members.
}
