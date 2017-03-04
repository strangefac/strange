package com.github.strangefac.strange.util;

import static com.github.strangefac.strange.util.ListHashCode.EMPTY_LIST_HASH_CODE;
import static com.github.strangefac.strange.util.ListHashCode.addToListHashCode;
import static com.github.strangefac.strange.util.ListHashCode.listHashCode;
import static org.junit.Assert.assertEquals;
import java.util.Objects;
import org.junit.Test;

public class TestListHashCode {
  @Test
  public void works() {
    assertEquals(Objects.hash(), EMPTY_LIST_HASH_CODE);
    assertEquals(Objects.hash(123), addToListHashCode(EMPTY_LIST_HASH_CODE, 123));
    assertEquals(Objects.hash(123, 456), listHashCode(123, 456));
    assertEquals(Objects.hash(123, 456, 789), addToListHashCode(listHashCode(123, 456), 789));
  }
}
