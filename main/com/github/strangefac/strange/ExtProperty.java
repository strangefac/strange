package com.github.strangefac.strange;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

/** For use on actor interface methods. The accessor type should restrict itself to returning a final immutable field. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtProperty {
  /** @return Property accessor type. */
  Class<? extends Function<Object, ?>> value();
}
