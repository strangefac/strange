package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.impl.SuspendOnly.SUSPEND_ONLY;
import com.github.strangefac.strange.BasicJoin;
import com.github.strangefac.strange.Suspendable;
import com.github.strangefac.strange.Suspension;
import com.github.strangefac.strange.Task;
import com.github.strangefac.strange.util.UncheckedCast;

public abstract class AbstractJoinSuspension extends Suspension {
  private static final long serialVersionUID = 1L;

  public interface JoinTask<J extends BasicJoin<?>> {
    /**
     * @param join The list of subtasks (that are now all done).
     * @see Task#run()
     */
    Object run(J join) throws Throwable;
  }

  public interface VoidJoinTask<J extends BasicJoin<?>> {
    void run(J join) throws Throwable;
  }

  public interface SingletonJoinTask<S extends Suspendable> {
    /** @param done The subtask, that is now done. */
    Object run(S done) throws Throwable;
  }

  public interface VoidSingletonJoinTask<S extends Suspendable> {
    void run(S done) throws Throwable;
  }

  private final JoinTask<?> _task;

  private <J extends BasicJoin<?>, T extends JoinTask<? super J>> Object fire(J join) throws Throwable {
    return UncheckedCast.<JoinTask<?>, T> uncheckedCast(_task).run(join);
  }

  protected <J extends BasicJoin<?>, T extends JoinTask<? super J>> AbstractJoinSuspension(J join, T task) {
    super(join);
    _task = task;
  }

  protected <J extends BasicJoin<?>, T extends VoidJoinTask<? super J>> AbstractJoinSuspension(J join, T task) {
    this(join, (JoinTask<J>) j -> {
      task.run(j);
      return null;
    });
  }

  protected <S extends Suspendable, J extends BasicJoin<S>, T extends SingletonJoinTask<? super S>> AbstractJoinSuspension(J join, T task) {
    this(join, (JoinTask<J>) j -> task.run(j.first()));
  }

  protected <S extends Suspendable, J extends BasicJoin<S>, T extends VoidSingletonJoinTask<? super S>> AbstractJoinSuspension(J join, T task) {
    this(join, (JoinTask<J>) j -> {
      task.run(j.first());
      return null;
    });
  }

  private int _n = 0;

  public Object done(Suspendable done) throws Throwable {
    BasicJoin<?> join = getJoin();
    if (++_n < join.size()) throw SUSPEND_ONLY;
    return fire(join);
  }

  public Object doneImmediately() throws Throwable {
    return fire(getJoin());
  }
}
