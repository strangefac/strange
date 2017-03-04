package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.Suspendable.EMPTY_SUSPENDABLE_ARRAY;
import static com.github.strangefac.strange.Syncable.EMPTY_SYNCABLE_ARRAY;
import java.util.Collection;
import java.util.Iterator;
import com.github.strangefac.strange.BasicJoin;
import com.github.strangefac.strange.Suspendable;
import com.github.strangefac.strange.Syncable;
import com.github.strangefac.strange.util.UncheckedCast;

public class BasicJoinImpl<S extends Suspendable> implements BasicJoin<S> {
  public static class JoinImpl<S extends Syncable<?, ?>> extends BasicJoinImpl<S> implements Join<S> {
    protected Object[] emptySubtaskArray() {
      return EMPTY_SYNCABLE_ARRAY;
    }

    @SafeVarargs
    public JoinImpl(S... subtasks) {
      super(subtasks);
    }

    public JoinImpl(Collection<? extends S> subtasks) {
      super(subtasks);
    }

    public void syncAll() throws Throwable {
      Throwable primaryOrNull = null;
      for (Syncable<?, ?> future : this) {
        try {
          future.sync();
        } catch (Throwable t) {
          if (null == primaryOrNull) {
            primaryOrNull = t;
          } else {
            primaryOrNull.addSuppressed(t); // The first failure suppresses all subsequent ones.
          }
        }
      }
      if (null != primaryOrNull) throw primaryOrNull;
    }
  }

  protected Object[] emptySubtaskArray() {
    return EMPTY_SUSPENDABLE_ARRAY;
  }

  private final S[] _subtasks;

  /** Note this constructor does not make a defensive copy, so if you pass in an array (not varargs) you should not subsequently edit it. */
  @SafeVarargs
  public BasicJoinImpl(S... subtasks) {
    _subtasks = subtasks;
  }

  /** Makes a defensive copy. */
  public BasicJoinImpl(Collection<? extends S> subtasks) {
    _subtasks = UncheckedCast.<Object[], S[]> uncheckedCast(subtasks.toArray(emptySubtaskArray())); // Observe just one method call on subtasks.
  }

  public int size() {
    return _subtasks.length;
  }

  public S first() {
    return _subtasks[0];
  }

  public Iterator<S> iterator() {
    return new Iterator<S>() {
      private int _i = 0;

      public boolean hasNext() {
        return _i < _subtasks.length;
      }

      public S next() {
        return _subtasks[_i++];
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
