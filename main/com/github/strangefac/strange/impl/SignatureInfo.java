package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.util.ListHashCode.listHashCode;
import static com.github.strangefac.strange.util.Standard.let;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
import com.github.strangefac.strange.Batch;
import com.github.strangefac.strange.ExtProperty;
import com.github.strangefac.strange.InnerProperty;
import com.github.strangefac.strange.JumpQueue;
import com.github.strangefac.strange.Patient;
import com.github.strangefac.strange.Slow;
import com.github.strangefac.strange.Yield;
import com.github.strangefac.strange.impl.StrangeImpl.TargetClass;

class SignatureInfo {
  static class SignatureKey {
    private final String _name;
    private final Class<?>[] _parameterTypes;
    private final int _hashCode;

    SignatureKey(String name, Class<?>... parameterTypes) {
      _hashCode = listHashCode(name, Arrays.hashCode(parameterTypes));
      _name = name;
      _parameterTypes = parameterTypes;
    }

    Class<?> parameterType(int i) {
      return _parameterTypes[i];
    }

    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (null != obj && SignatureKey.class == obj.getClass()) {
        SignatureKey that = (SignatureKey) obj;
        return _name.equals(that._name) && Arrays.equals(_parameterTypes, that._parameterTypes);
      }
      return false;
    }

    public int hashCode() {
      return _hashCode;
    }

    public String toString() {
      return let(new StringBuilder(_name).append('('), sb -> {
        String separator = ", ";
        for (Class<?> type : _parameterTypes)
          sb.append(type.getSimpleName()).append(separator);
        if ('(' != sb.charAt(sb.length() - 1)) sb.setLength(sb.length() - separator.length());
        return sb.append(')').toString();
      });
    }
  }

  private final boolean _batch, _yield, _slow, _jumpQueue; // These are metadata, so do not need to participate in equals/hashCode.
  private final ExtProperty _extPropertyOrNull; // Metadata.
  private final InnerProperty _innerPropertyOrNull; // Metadata.
  private final boolean _patient; // Metadata.
  private final SignatureKey _key;

  /** @throws IllegalArgumentException If both extPropertyOrNull and innerPropertyOrNull are non-null. */
  SignatureInfo(boolean batch, boolean yield, boolean slow, boolean jumpQueue, ExtProperty extPropertyOrNull, InnerProperty innerPropertyOrNull, boolean patient, String name, Class<?>... parameterTypes) throws IllegalArgumentException {
    if (null != extPropertyOrNull && null != innerPropertyOrNull) throw new IllegalArgumentException("ExtProperty and InnerProperty are mutually exclusive.");
    _batch = batch;
    _yield = yield;
    _slow = slow;
    _jumpQueue = jumpQueue;
    _extPropertyOrNull = extPropertyOrNull;
    _innerPropertyOrNull = innerPropertyOrNull;
    _patient = patient;
    _key = new SignatureKey(name, parameterTypes);
  }

  /** Passes in false/null for all annotations. */
  SignatureInfo(String name, Class<?>... parameterTypes) {
    this(false, false, false, false, null, null, false, name, parameterTypes);
  }

  /** For performance should only be called by {@link SignatureLookup}. */
  SignatureInfo(Method method) {
    this(method.isAnnotationPresent(Batch.class), method.isAnnotationPresent(Yield.class), method.isAnnotationPresent(Slow.class), method.isAnnotationPresent(JumpQueue.class), method.getAnnotation(ExtProperty.class), method.getAnnotation(InnerProperty.class), method.isAnnotationPresent(Patient.class), method.getName(), method.getParameterTypes());
  }

  boolean batch() {
    return _batch;
  }

  boolean yield() {
    return _yield;
  }

  boolean slow() {
    return _slow;
  }

  boolean jumpQueue() {
    return _jumpQueue;
  }

  boolean postable() {
    return null == _extPropertyOrNull && null == _innerPropertyOrNull;
  }

  Supplier<?> propertyAccessorOrNull(Object target) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
    if (null != _extPropertyOrNull) {
      Function<Object, ?> function = _extPropertyOrNull.value().newInstance();
      return () -> function.apply(target);
    } else if (null != _innerPropertyOrNull) {
      Class<? extends Supplier<?>> accessorType = _innerPropertyOrNull.value();
      return accessorType.getDeclaredConstructor(accessorType.getEnclosingClass()).newInstance(target);
    } else {
      return null;
    }
  }

  boolean patient() {
    return _patient;
  }

  SignatureKey key() {
    return _key;
  }

  /**
   * Expensive, should only be called from {@link TargetClass} via {@link TargetClassLookup}.
   * 
   * @see Class#getMethod(String, Class...)
   */
  Method resolve(Class<?> clazz) throws SecurityException, NoSuchMethodException {
    Class<?>[] parameterTypes;
    if (_batch) {
      parameterTypes = Arrays.stream(_key._parameterTypes).map(pt -> Array.newInstance(pt, 0).getClass()).toArray(Class[]::new);
    } else {
      parameterTypes = _key._parameterTypes;
    }
    return clazz.getMethod(_key._name, parameterTypes);
  }

  private static final String NOT_KEY_MESSAGE = "SignatureInfo is not a key.";

  public boolean equals(Object obj) {
    throw new UnsupportedOperationException(NOT_KEY_MESSAGE);
  }

  public int hashCode() {
    throw new UnsupportedOperationException(NOT_KEY_MESSAGE);
  }

  public String toString() {
    return let(new StringBuilder(), sb -> {
      if (_batch) sb.append('@').append(Batch.class.getSimpleName()).append(' ');
      if (_yield) sb.append('@').append(Yield.class.getSimpleName()).append(' ');
      return sb.append(_key).toString();
    });
  }
}
