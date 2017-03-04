package com.github.strangefac.strange;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * All actor interfaces must extend this interface.
 * <p>
 * When you invoke a method declared in an interface that extends {@link Actor}, it returns immediately and the {@link Future} or {@link SFuture} may be queried
 * (at any time) for the result of the associated invocation on the target. This is analogous to a private return channel in Scala.
 * <p>
 * If the method is declared in an interface (necessarily a superinterface) that does not extend Actor, then the invocation blocks until the target invocation
 * has completed, and does {@link SFuture#sync()} internally to propagate the result. This non-Future mechanism is intended to simplify migration to strange by
 * allowing legacy interfaces to remain in use. In this case ensure the actor method redeclares at least the checked exceptions thrown by the target method, to
 * avoid {@link UndeclaredThrowableException}.
 */
public interface Actor {
  /** @return The type arg of {@link ActorTarget}, for e.g. inspecting its annotations. */
  Class<? extends Actor> actorInterface();

  /**
   * @return The number of messages awaiting processing, not including the message currently being processed if any.
   * @throws IllegalStateException If {@link #kill()} was called.
   */
  int mailboxSize() throws IllegalStateException;

  /** @return The current dwell time in nanos in this actor, as a thread-safe function from {@link System#nanoTime()}. */
  DwellInfo getDwellInfo();

  /**
   * The given task is queued like a normal actor invocation, the only difference being that it will not have access to the target object.
   * <p>
   * This can be used as a way to execute some code just before or just after a real invocation on the same actor is executed.
   */
  <V, E extends Throwable> SFuture<V, E> post(Task<? extends V, ? extends E> task);

  <E extends Throwable> SFuture<Void, E> post(VoidTask<? extends E> task);

  /**
   * Discards the mailbox along with all pending invocations, thus preventing any new posts, and sends an interrupt to the current task. This is useful for
   * heap/CPU management if the actor is expensive and no longer needed. Obviously, expensive tasks will need to cooperate by regularly checking for
   * interruption. All new post attempts will result in {@link RejectedExecutionException}.
   */
  void kill();
}
