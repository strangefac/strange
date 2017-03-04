package com.github.strangefac.strange.impl;

import java.util.Collection;
import com.github.strangefac.strange.BasicJoin;
import com.github.strangefac.strange.Suspendable;

public class BasicJoinSuspension extends AbstractJoinSuspension {
  private static final long serialVersionUID = 1L;

  public static class NotVoid extends BasicJoinSuspension {
    private static final long serialVersionUID = 1L;

    public <S extends Suspendable> NotVoid(Collection<? extends S> futures, JoinTask<? super BasicJoin<S>> task) {
      super(futures, task);
    }

    public <S extends Suspendable> NotVoid(S future, SingletonJoinTask<? super S> task) {
      super(future, task);
    }
  }

  public static class Void extends BasicJoinSuspension {
    private static final long serialVersionUID = 1L;

    public <S extends Suspendable> Void(Collection<? extends S> futures, VoidJoinTask<? super BasicJoin<S>> task) {
      super(futures, task);
    }

    public <S extends Suspendable> Void(S future, VoidSingletonJoinTask<? super S> task) {
      super(future, task);
    }
  }

  public <S extends Suspendable> BasicJoinSuspension(Collection<? extends S> futures, JoinTask<? super BasicJoin<S>> task) {
    super(new BasicJoinImpl<>(futures), task);
  }

  public <S extends Suspendable> BasicJoinSuspension(Collection<? extends S> futures, VoidJoinTask<? super BasicJoin<S>> task) {
    super(new BasicJoinImpl<>(futures), task);
  }

  public <S extends Suspendable> BasicJoinSuspension(S future, SingletonJoinTask<? super S> task) {
    super(new BasicJoinImpl<>(future), task);
  }

  public <S extends Suspendable> BasicJoinSuspension(S future, VoidSingletonJoinTask<? super S> task) {
    super(new BasicJoinImpl<>(future), task);
  }
}
