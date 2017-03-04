package com.github.strangefac.strange;

public interface Suspendable {
  Suspendable[] EMPTY_SUSPENDABLE_ARRAY = new Suspendable[0];
  Suspendable NEVER_DONE_SUSPENDABLE = new Suspendable() {
    public <W, F extends Throwable> void postAfterDone(PrivateActor actor, Task<? extends W, ? extends F> taskImpl, Wrapper<W, F> wrapper) {
      // Do nothing.
    }
  };
  Suspendable DONE_SUSPENDABLE = new Suspendable() {
    public <W, F extends Throwable> void postAfterDone(PrivateActor actor, Task<? extends W, ? extends F> taskImpl, Wrapper<W, F> wrapper) {
      try {
        actor.post(taskImpl, wrapper);
      } catch (DeadActorException e) {
        wrapper.putCauseOfExecutionException(e);
      }
    }
  };

  /** When this is done (which may be now) post the given task to the given actor targeting the given wrapper. */
  <W, F extends Throwable> void postAfterDone(PrivateActor actor, Task<? extends W, ? extends F> taskImpl, Wrapper<W, F> wrapper);
}
