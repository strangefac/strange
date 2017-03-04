package com.github.strangefac.strange.util;

import static com.github.strangefac.strange.util.Standard.also;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Same as {@link ArrayList} except the underlying array has the passed-in component type instead of {@link Object}, which may be handy when profiling. This
 * works because ArrayList preserves the array type when resizing.
 */
public class TypedArrayList<E> extends ArrayList<E> {
  private static final long serialVersionUID = 1L;
  private static final Field ELEMENT_DATA_FIELD = field("elementData"), SIZE_FIELD = field("size");

  public TypedArrayList(Class<? super E> componentType) {
    this(componentType, 10);
  }

  public TypedArrayList(Class<? super E> componentType, int initialCapacity) {
    super(0); // Create an empty array to discard.
    set(ELEMENT_DATA_FIELD, Array.newInstance(componentType, initialCapacity));
  }

  public TypedArrayList(Class<? super E> componentType, Collection<? extends E> c) {
    super(0); // Create an empty array to discard.
    Object[] array = (Object[]) Array.newInstance(componentType, c.size());
    array = c.toArray(array); // Assign to array in case c grew since we got its size.
    set(ELEMENT_DATA_FIELD, array);
    set(SIZE_FIELD, array.length);
  }

  private static Field field(String fieldName) {
    try {
      return also(ArrayList.class.getDeclaredField(fieldName), f -> f.setAccessible(true));
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private void set(Field field, Object value) {
    try {
      field.set(this, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
