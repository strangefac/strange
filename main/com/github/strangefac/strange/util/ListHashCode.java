package com.github.strangefac.strange.util;

import java.util.Arrays;
import java.util.List;

/** Hash-code strategy where you treat the fields of your object as list elements. */
public class ListHashCode {
  public static final int EMPTY_LIST_HASH_CODE = 1;
  private static final int PRIME = 31;

  /**
   * Convenience for two applications of {@link #addToListHashCode(int, Object)} starting with the {@link #EMPTY_LIST_HASH_CODE}.
   * 
   * @see List#hashCode()
   * @see Arrays#hashCode(Object[])
   */
  public static int listHashCode(Object obj0, Object obj1) {
    return addToListHashCode(addToListHashCode(EMPTY_LIST_HASH_CODE, obj0), obj1);
  }

  /** @param obj May be null. */
  public static int addToListHashCode(int hashCode, Object obj) {
    return PRIME * hashCode + (null != obj ? obj.hashCode() : 0);
  }

  private ListHashCode() {
    // No.
  }
}
