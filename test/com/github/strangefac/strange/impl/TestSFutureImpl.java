package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.function.NullRunnable.NULL_RUNNABLE;
import static com.github.strangefac.strange.impl.Invocation.STACK_TRACE_NOT_LOGGED_HERE_FORMAT;
import static com.github.strangefac.strange.impl.SFutureImpl.NOT_ITE_MESSAGE;
import static com.github.strangefac.strange.pool.TestCustomThreadPoolThreadCollection.acceptAny;
import static com.github.strangefac.strange.util.SlowTests.slowTestsEnabled;
import static com.github.strangefac.strange.util.Standard.also;
import static com.github.strangefac.strange.util.Standard.repeat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.reportMatcher;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.easymock.IArgumentMatcher;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.PrivateActor;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.SyncException;
import com.github.strangefac.strange.function.VoidCheckedException;
import com.github.strangefac.strange.impl.Invocation;
import com.github.strangefac.strange.impl.InvocationInfo;
import com.github.strangefac.strange.impl.SFutureImpl;
import com.github.strangefac.strange.impl.SignatureInfo;
import com.github.strangefac.strange.impl.WrapperImpl;
import com.github.strangefac.strange.impl.SignatureInfo.SignatureKey;
import com.github.strangefac.strange.impl.StrangeImpl.TargetClass;
import com.github.strangefac.strange.util.EasyMockRule;

public class TestSFutureImpl {
  public interface MyActor extends Actor {
    SFuture<Integer, VoidCheckedException> getInt(boolean interrupt, int i);

    SFuture<Void, VoidCheckedException> kaboom(boolean interrupt, RuntimeException e);

    SFuture<Void, InterruptedException> slow(Runnable running);
  }

  public static class Target implements ActorTarget<MyActor> {
    public void init(MyActor actor) {
      // Do nothing.
    }

    public int getInt(boolean interrupt, int i) {
      if (interrupt) Thread.currentThread().interrupt();
      return i;
    }

    public void kaboom(boolean interrupt, RuntimeException e) {
      if (interrupt) Thread.currentThread().interrupt();
      throw e;
    }

    public void slow(Runnable running) throws InterruptedException {
      running.run();
      Thread.sleep(10000);
    }
  }

  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();
  private final Logger _log = _mocks.createMock(Logger.class);
  private final IllegalStateException _exception = new IllegalStateException("go away");
  private final PrivateActor _actor = _mocks.createMock(PrivateActor.class);
  private Invocation<Integer, Throwable> _invocation;
  private WrapperImpl<Integer, Throwable> _wrapper;

  @After
  public void tearDown() {
    assertFalse(Thread.interrupted()); // Just in case it's true, prevent it from affecting subsequent tests, and blow up.
  }

  static SignatureInfo keyEq(SignatureKey signatureKey) {
    reportMatcher(new IArgumentMatcher() {
      public boolean matches(Object argument) {
        return signatureKey.equals(((SignatureInfo) argument).key());
      }

      public void appendTo(StringBuffer buffer) {
        buffer.append("keyEq(").append(signatureKey).append(')');
      }
    });
    return null;
  }

  private void expectException(SignatureInfo signatureInfo) {
    _log.debug(eq(STACK_TRACE_NOT_LOGGED_HERE_FORMAT), keyEq(signatureInfo.key()), anyObject());
    expectLastCall().andAnswer(() -> {
      assertSame(_exception, getCurrentArguments()[2]);
      return null;
    });
  }

  private void assertITE(Object x) {
    assertSame(_exception, ((InvocationTargetException) x).getTargetException()); // Assert via CCE.
  }

  private void createInvocation(SignatureInfo signatureInfo, Object... args) {
    InvocationInfo<Integer, Throwable> info = new InvocationInfo<>(_wrapper = new WrapperImpl<>(), _actor, signatureInfo, args);
    _invocation = info.toInvocation(null, _log, new TargetClass<>(Target.class), new Target(), false);
    assertEquals(false, _wrapper.isDone());
    assertEquals(false, _wrapper.isCancelled());
    catchThrowableOfType(() -> _wrapper.get(0, TimeUnit.SECONDS), TimeoutException.class);
  }

  private Number wrapperGet() throws InterruptedException, ExecutionException, TimeoutException {
    assertEquals(true, _wrapper.isDone());
    assertEquals(false, _wrapper.isCancelled());
    return _wrapper.get(0, TimeUnit.SECONDS);
  }

  private void assertWrapperCancelled() {
    assertEquals(true, _wrapper.isDone());
    assertEquals(true, _wrapper.isCancelled());
    catchThrowableOfType(() -> _wrapper.get(0, TimeUnit.SECONDS), CancellationException.class);
  }

  private void assertWrapperException() {
    assertITE(catchThrowableOfType(this::wrapperGet, ExecutionException.class).getCause());
  }

  @Test
  public void returnsNormally() throws InterruptedException, ExecutionException, TimeoutException {
    _mocks.replay();
    createInvocation(new SignatureInfo("getInt", Boolean.TYPE, Integer.TYPE), false, 5);
    _invocation.run();
    assertEquals(5, wrapperGet());
  }

  @Test
  public void throwsException() {
    SignatureInfo sig = new SignatureInfo("kaboom", Boolean.TYPE, RuntimeException.class);
    expectException(sig);
    _mocks.replay();
    createInvocation(sig, false, _exception);
    _invocation.run();
    assertWrapperException();
  }

  private Thread _thread;

  @Test
  public void cancelledByDifferentThreadWhileRunning() throws InterruptedException {
    SignatureInfo sig = new SignatureInfo("slow", Runnable.class);
    expectException(sig);
    _mocks.replay();
    createInvocation(sig, (Runnable) () -> _thread.start());
    _thread = new Thread(new Runnable() {
      private final Invocation<?, ?> _i = _invocation;

      public void run() {
        _i.cancel(true); // Interrupt the long wait, even in the unlikely case of not waiting yet.
      }
    });
    _invocation.run(); // Starts the thread then waits.
    _thread.join(); // Wait until cancel has actually returned, to ensure the wrapper is actually done.
    assertWrapperCancelled();
  }

  @Test
  public void cancelledBySameThreadWhileRunning() {
    SignatureInfo sig = new SignatureInfo("slow", Runnable.class);
    expectException(sig);
    _mocks.replay();
    createInvocation(sig, (Runnable) () -> _invocation.cancel(true)); // Interrupt the long wait.
    _invocation.run();
    assertWrapperCancelled();
  }

  @Test
  public void cancelledBeforeRunning() {
    _mocks.replay();
    createInvocation(new SignatureInfo("slow", Runnable.class), NULL_RUNNABLE);
    _invocation.cancel(true); // Interrupt the long wait.
    _invocation.run();
    assertWrapperCancelled();
  }

  @Test
  public void cancelledAfterReturningNormally() throws InterruptedException, ExecutionException, TimeoutException {
    _mocks.replay();
    createInvocation(new SignatureInfo("getInt", Boolean.TYPE, Integer.TYPE), false, 6);
    _invocation.run();
    _invocation.cancel(false);
    assertEquals(6, wrapperGet());
  }

  @Test
  public void cancelledAfterThrowingException() {
    SignatureInfo sig = new SignatureInfo("kaboom", Boolean.TYPE, RuntimeException.class);
    expectException(sig);
    _mocks.replay();
    createInvocation(sig, false, _exception);
    _invocation.run();
    _invocation.cancel(false);
    assertWrapperException();
  }

  @Test
  public void interruptedBeforeReturningNormally() throws InterruptedException, ExecutionException, TimeoutException {
    _mocks.replay();
    createInvocation(new SignatureInfo("getInt", Boolean.TYPE, Integer.TYPE), true, 7);
    _invocation.run();
    assertEquals(true, Thread.interrupted()); // Clear it.
    assertEquals(7, wrapperGet());
  }

  @Test
  public void interruptedBeforeThrowingException() {
    SignatureInfo sig = new SignatureInfo("kaboom", Boolean.TYPE, RuntimeException.class);
    expectException(sig);
    _mocks.replay();
    createInvocation(sig, true, _exception);
    _invocation.run();
    assertEquals(true, Thread.interrupted()); // Clear it.
    assertWrapperException();
  }

  @Rule
  public final TestName _testName = new TestName();

  /** Would fail if we extended {@link FutureTask}. */
  @Test
  public void isCompletable() throws Exception {
    _mocks.replay();
    if (!slowTestsEnabled(_testName)) return;
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      repeat(1000000, () -> {
        SFutureImpl<String, VoidCheckedException> w = new SFutureImpl<>();
        executor.execute(() -> w.putValue("result"));
        assertEquals("result", w.get()); // Test failure is when this value is null.
      });
    } finally {
      executor.shutdown(); // Don't bother waiting, if it's not idle already it will be very soon.
    }
  }

  @Test
  public void syncWorks() throws Exception {
    _mocks.replay();
    {
      // Returning a value should work as for get:
      Object value = new Object();
      SFutureImpl<Object, VoidCheckedException> f = new SFutureImpl<>();
      f.putValue(value);
      assertSame(value, f.sync());
    }
    {
      // RuntimeExceptions should be unwrapped:
      NullPointerException throwable = new NullPointerException();
      SFutureImpl<Void, VoidCheckedException> f = new SFutureImpl<>();
      f.putCauseOfExecutionException(new InvocationTargetException(throwable)); // Simulate FutureTask behaviour.
      assertSame(throwable, catchThrowable(f::sync));
    }
    {
      // Errors should be unwrapped:
      ExceptionInInitializerError throwable = new ExceptionInInitializerError();
      SFutureImpl<Void, VoidCheckedException> f = new SFutureImpl<>();
      f.putCauseOfExecutionException(new InvocationTargetException(throwable));
      assertSame(throwable, catchThrowable(f::sync));
    }
    {
      // Checked throwables as allowed by the type arg should be unwrapped:
      EOFException throwable = new EOFException();
      SFutureImpl<Void, IOException> f = new SFutureImpl<>();
      f.putCauseOfInvocationTargetException(throwable);
      assertSame(throwable, catchThrowable(f::sync));
    }
    {
      // CancellationException is passed straight through:
      SFutureImpl<Void, VoidCheckedException> f = new SFutureImpl<>();
      f.putCancelled();
      catchThrowableOfType(f::sync, CancellationException.class);
    }
    {
      // SyncException caused by unusual cause of ExecutionException:
      OutOfMemoryError unusual = new OutOfMemoryError();
      SFutureImpl<Void, VoidCheckedException> f = new SFutureImpl<Void, VoidCheckedException>() {
        @SuppressWarnings("sync-override")
        public Void get() throws ExecutionException {
          throw new ExecutionException(unusual);
        }
      };
      also(catchThrowableOfType(f::sync, SyncException.class), e -> {
        assertEquals(NOT_ITE_MESSAGE, e.getMessage());
        assertSame(unusual, e.getCause());
      });
    }
  }

  private static double nowSeconds() {
    return System.nanoTime() / 1e9;
  }

  @Test
  public void timeoutWorks() throws Exception {
    _mocks.replay();
    acceptAny(10, TestSFutureImpl::timeoutWorksImpl);
  }

  private static void timeoutWorksImpl() throws InterruptedException, ExecutionException, TimeoutException {
    SFutureImpl<Void, VoidCheckedException> f = new SFutureImpl<>();
    {
      double start = nowSeconds();
      catchThrowableOfType(() -> f.get(1, TimeUnit.SECONDS), TimeoutException.class);
      assertEquals(start + 1, nowSeconds(), .1);
    }
    {
      double start = nowSeconds();
      catchThrowableOfType(() -> f.sync(1), TimeoutException.class);
      assertEquals(start + 1, nowSeconds(), .1);
    }
    new Thread(() -> {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      f.putValue(null);
    }).start();
    {
      double start = nowSeconds();
      catchThrowableOfType(() -> f.get(0, TimeUnit.SECONDS), TimeoutException.class);
      assertEquals(start, nowSeconds(), .1);
    }
    {
      double start = nowSeconds();
      assertEquals(null, f.get(1, TimeUnit.SECONDS));
      assertEquals(start + .5, nowSeconds(), .1);
    }
    {
      double start = nowSeconds();
      assertEquals(null, f.get(0, TimeUnit.SECONDS));
      assertEquals(start, nowSeconds(), .1);
    }
  }

  @Test
  public void cancelDoesNotifyAll() throws InterruptedException {
    _mocks.replay();
    SFutureImpl<Void, VoidCheckedException> f = new SFutureImpl<>();
    Thread t = new Thread(() -> {
      try {
        Thread.sleep(100);
        f.putCancelled();
      } catch (InterruptedException e) {
        f.putCauseOfExecutionException(e);
      }
    });
    t.start();
    catchThrowableOfType(f::sync, CancellationException.class);
    t.join();
  }
}
