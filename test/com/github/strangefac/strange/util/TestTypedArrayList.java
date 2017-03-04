package com.github.strangefac.strange.util;

import static com.github.strangefac.strange.util.Standard.also;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import com.github.strangefac.strange.util.TypedArrayList;

public class TestTypedArrayList {
  private static Object[] getArray(ArrayList<?> list) throws Exception {
    return (Object[]) also(ArrayList.class.getDeclaredField("elementData"), f -> f.setAccessible(true)).get(list);
  }

  @Test
  public void noArgConstructor() throws Exception {
    TypedArrayList<String> list = new TypedArrayList<>(Serializable.class);
    assertEquals(0, list.size());
    Object[] array = getArray(list);
    assertEquals(Serializable[].class, array.getClass());
    assertEquals(10, array.length);
  }

  @Test
  public void intConstructor() throws Exception {
    TypedArrayList<String> list = new TypedArrayList<>(Serializable.class, 5);
    assertEquals(0, list.size());
    Object[] array = getArray(list);
    assertEquals(Serializable[].class, array.getClass());
    assertEquals(5, array.length);
  }

  @Test
  public void copyConstructor() throws Exception {
    TypedArrayList<String> list = new TypedArrayList<>(Serializable.class, Arrays.asList("woo", "yay"));
    assertEquals(2, list.size());
    Object[] array = getArray(list);
    assertEquals(Serializable[].class, array.getClass());
    assertArrayEquals(new String[]{"woo", "yay"}, array); // Clearly doesn't compare runtime types.
  }
}
