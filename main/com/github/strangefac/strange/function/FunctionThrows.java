package com.github.strangefac.strange.function;

public interface FunctionThrows<X, Y, E extends Exception> {
  Y apply(X x) throws E;
}
