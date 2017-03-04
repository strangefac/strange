package com.github.strangefac.strange;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;

/**
 * For use on actor interface methods. The accessor type should be an inner (not just nested) class and restrict itself to returning a final immutable field.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InnerProperty {
  /** @return Property accessor type. */
  Class<? extends Supplier<?>> value();
}
