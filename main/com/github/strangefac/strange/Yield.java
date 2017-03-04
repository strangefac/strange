package com.github.strangefac.strange;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Future;

/**
 * For use on actor interface methods. If a non-{@link Patient} message is on the queue at execution time (that isn't in the same {@link Batch}), not executed,
 * and an info is logged. If a non-{@link Patient} message is posted during execution, one interrupt is sent (via {@link Future#cancel(boolean)}) and it's up to
 * the impl how to react (e.g. abort by {@link InterruptedException}).
 * <p>
 * Take care if applying this to a method with side-effects (including modification of actor state), as the cancel mechanism is quite crude w.r.t. whether none,
 * some, or all of the side effects are actually executed.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Yield {
  // No members.
}
