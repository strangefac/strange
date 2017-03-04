package com.github.strangefac.strange;

public abstract class Suspension extends Throwable {
  private static final long serialVersionUID = 1L;
  private final BasicJoin<?> _join;

  protected Suspension(BasicJoin<?> join) {
    _join = join;
  }

  public BasicJoin<?> getJoin() {
    return _join;
  }

  /**
   * Executed as a task of the same actor that threw this, once per done subtask. May throw {@link com.github.strangefac.strange.impl.SuspendOnly#SUSPEND_ONLY} to suspend
   * again. Note throwing a new {@link Suspension} does not suppress subsequent calls to this method (if there are still subtasks to be done).
   * <p>
   * If there are no subtasks then there won't actually be a suspension, and {@link #doneImmediately()} is called instead of this.
   * 
   * @return Must be compatible with the Future of the suspended invocation.
   * @throws Throwable If the suspended invocation targets an SFuture, all checked throwables (except Suspensions) must be compatible with its throwable type
   * arg.
   */
  public abstract Object done(Suspendable done) throws Throwable;

  /**
   * Called instead of {@link #done(Suspendable)} (and without suspending) if there are no subtasks. If there is at least one subtask the impl may simply
   * delegate to {@link #atLeastOneSubtask()} (which throws {@link UnsupportedOperationException}).
   */
  public abstract Object doneImmediately() throws Throwable;

  /**
   * May be called from {@link #doneImmediately()} to assert there is at least one subtask.
   */
  protected static Void atLeastOneSubtask() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("Assertion (that there is at least one subtask) failed.");
  }

  @SuppressWarnings("sync-override")
  public Throwable fillInStackTrace() {
    return this; // We don't need the stack trace, and preparing it is very slow.
  }
}
