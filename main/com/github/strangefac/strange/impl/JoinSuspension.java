package com.github.strangefac.strange.impl;

import java.util.Collection;
import com.github.strangefac.strange.Syncable;
import com.github.strangefac.strange.BasicJoin.Join;
import com.github.strangefac.strange.impl.BasicJoinImpl.JoinImpl;

public class JoinSuspension extends AbstractJoinSuspension {
  private static final long serialVersionUID = 1L;

  /** Less-verbose way of hinting which constructor you want to a compiler that can't work it out. */
  public static class NotVoid extends JoinSuspension {
    private static final long serialVersionUID = 1L;

    public <S extends Syncable<?, ?>> NotVoid(Collection<? extends S> futures, JoinTask<? super Join<S>> task) {
      super(futures, task);
    }

    public <S extends Syncable<?, ?>> NotVoid(S future, SingletonJoinTask<? super S> task) {
      super(future, task);
    }
  }

  /** @see NotVoid */
  public static class Void extends JoinSuspension {
    private static final long serialVersionUID = 1L;

    public <S extends Syncable<?, ?>> Void(Collection<? extends S> futures, VoidJoinTask<? super Join<S>> task) {
      super(futures, task);
    }

    public <S extends Syncable<?, ?>> Void(S future, VoidSingletonJoinTask<? super S> task) {
      super(future, task);
    }
  }

  /** @param task Typically performs some calculation (executed in the context of the controlling actor) based on the done states of all the futures. */
  public <S extends Syncable<?, ?>> JoinSuspension(Collection<? extends S> futures, JoinTask<? super Join<S>> task) {
    super(new JoinImpl<>(futures), task);
  }

  public <S extends Syncable<?, ?>> JoinSuspension(Collection<? extends S> futures, VoidJoinTask<? super Join<S>> task) {
    super(new JoinImpl<>(futures), task);
  }

  public <S extends Syncable<?, ?>> JoinSuspension(S future, SingletonJoinTask<? super S> task) {
    super(new JoinImpl<>(future), task);
  }

  public <S extends Syncable<?, ?>> JoinSuspension(S future, VoidSingletonJoinTask<? super S> task) {
    super(new JoinImpl<>(future), task);
  }
}
