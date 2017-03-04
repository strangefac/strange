package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.impl.Invocation.STACK_TRACE_NOT_LOGGED_HERE_FORMAT;
import static com.github.strangefac.strange.util.Standard.also;
import static com.github.strangefac.strange.util.Standard.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.AllActors;
import com.github.strangefac.strange.BasicJoin;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.Spawned;
import com.github.strangefac.strange.Suspendable;
import com.github.strangefac.strange.Suspension;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.function.VoidCheckedException;
import com.github.strangefac.strange.impl.BasicJoinImpl;
import com.github.strangefac.strange.impl.BasicJoinSuspension;
import com.github.strangefac.strange.impl.DelegatingSuspension;
import com.github.strangefac.strange.impl.JoinSuspension;
import com.github.strangefac.strange.impl.ManualThreadCollection;
import com.github.strangefac.strange.impl.SFutureImpl;
import com.github.strangefac.strange.impl.SimpleJoinSuspension;
import com.github.strangefac.strange.impl.StrangeImpl;
import com.github.strangefac.strange.impl.AbstractJoinSuspension.JoinTask;
import com.github.strangefac.strange.impl.TestSuspend.InitSuspends.InitSuspendsActor;
import com.github.strangefac.strange.impl.TestSuspend.MyTarget.MyActor;
import com.github.strangefac.strange.util.ComponentSource;
import com.github.strangefac.strange.util.EasyMockRule;

public class TestSuspend {
  private static class MyException extends Exception {
    private static final long serialVersionUID = 1L;
    private final int _index;

    private MyException(int index) {
      _index = index;
    }
  }

  @ThreadCollectionType(ManualThreadCollection.class)
  public static class MyTarget implements ActorTarget<MyTarget.MyActor> {
    public interface MyActor extends Actor {
      SFuture<Void, ExecutionException> outer(); // Only ExecutionException as the SuspendException should never make it to the Future.

      SFuture<Void, MyException> inner(int index);

      SFuture<Void, Throwable> throwFromDone(Throwable t);

      SFuture<String, VoidCheckedException> joinNothingTwice();

      SFuture<Void, VoidCheckedException> performance(int n);
    }

    private MyActor _self;

    public void init(MyActor actor) {
      _self = actor;
    }

    public void outer() throws Suspension {
      throw new Suspension(new BasicJoinImpl<SFuture<?, ?>>(_self.inner(0), _self.inner(1))) {
        private static final long serialVersionUID = 1L;

        public Object done(Suspendable done) throws ExecutionException {
          // Returning a value / throwing a non-suspend exception here should suppress execution of this block for the other future:
          ((SFuture<?, ?>) done).assertDoneAndGetUninterruptibly();
          return null;
        }

        public Object doneImmediately() {
          return atLeastOneSubtask();
        }
      };
    }

    public void inner(int index) throws MyException {
      throw new MyException(index);
    }

    public void throwFromDone(Throwable t) throws Suspension {
      throw new Suspension(new BasicJoinImpl<>(also(new SFutureImpl<>(), f -> f.putValue(null)))) {
        private static final long serialVersionUID = 1L;

        public Object done(Suspendable done) throws Throwable {
          throw t;
        }

        public Object doneImmediately() {
          return atLeastOneSubtask();
        }
      };
    }

    public String joinNothingTwice() throws BasicJoinSuspension {
      throw new BasicJoinSuspension(Collections.emptySet(), (JoinTask<BasicJoin<Suspendable>>) join -> {
        throw new SimpleJoinSuspension(Collections.emptySet(), "woo");
      });
    }

    public void performance(int n) throws BasicJoinSuspension {
      if (n > 0) {
        throw new BasicJoinSuspension(Collections.emptySet(), join -> {
          performance(n - 1);
          return null;
        });
      }
    }
  }

  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();
  private final ManualThreadCollection _thread = new ManualThreadCollection();
  private final Logger _log = _mocks.createMock(Logger.class);
  private final StrangeImpl _strange = run(() -> {
    ComponentSource componentSource = also(_mocks.createMock(ComponentSource.class), it -> {
      expect(it.getComponent(ManualThreadCollection.class)).andReturn(_thread);
    });
    ILoggerFactory loggerFactory = also(_mocks.createMock(ILoggerFactory.class), it -> {
      expect(it.getLogger(anyObject())).andReturn(_log).anyTimes();
    });
    AllActors allActors = also(_mocks.createMock(AllActors.class), it -> it.purgeAndAdd(anyObject()));
    return new StrangeImpl(componentSource, loggerFactory, allActors);
  });

  @Test
  public void earlyException() throws InterruptedException {
    _mocks.replay();
    MyActor actor = _strange.spawn(new MyTarget()).actor();
    SFuture<Void, ExecutionException> outerFuture = actor.outer();
    _thread.postExitCommand();
    try {
      _thread.enter();
      fail("You fixed a bug!");
    } catch (IllegalStateException e) {
      return; // To be fixed when I have the time.
    }
    also(catchThrowableOfType(outerFuture::get, ExecutionException.class), e -> {
      Throwable cause = e.getCause();
      assertThat(cause).isInstanceOf(InvocationTargetException.class);
      cause = cause.getCause();
      assertThat(cause).isInstanceOf(ExecutionException.class); // Thrown by done.
      cause = cause.getCause();
      assertThat(cause).isInstanceOf(InvocationTargetException.class);
      cause = cause.getCause();
      assertEquals(0, ((MyException) cause)._index);
    });
  }

  private void throwableFromDone(Throwable t) throws InterruptedException {
    _log.debug(eq(STACK_TRACE_NOT_LOGGED_HERE_FORMAT), anyObject(), anyObject());
    expectLastCall().andAnswer(() -> {
      assertSame(t, getCurrentArguments()[2]);
      return null;
    });
    _mocks.replay();
    MyActor actor = _strange.spawn(new MyTarget()).actor();
    SFuture<Void, Throwable> throwFromDoneFuture = actor.throwFromDone(t);
    _thread.postExitCommand();
    _thread.enter();
    also(catchThrowableOfType(throwFromDoneFuture::get, ExecutionException.class), x -> {
      Throwable cause = x.getCause();
      assertThat(cause).isInstanceOf(InvocationTargetException.class);
      cause = cause.getCause();
      assertSame(t, cause);
    });
  }

  @Test
  public void runtimeExceptionFromDone() throws InterruptedException {
    throwableFromDone(new NullPointerException());
  }

  @Test
  public void uncheckedErrorFromDone() throws InterruptedException {
    throwableFromDone(new ExceptionInInitializerError());
  }

  @Test
  public void suspendFromDone() throws InterruptedException, ExecutionException {
    _mocks.replay();
    Object value = new Object();
    SFuture<Object, VoidCheckedException> valueHolder = new SFutureImpl<>();
    valueHolder.putValue(value);
    MyActor actor = _strange.spawn(new MyTarget()).actor();
    SFuture<Void, Throwable> throwFromDoneFuture = actor.throwFromDone(new DelegatingSuspension(valueHolder));
    _thread.postExitCommand();
    _thread.enter();
    assertSame(value, throwFromDoneFuture.get());
  }

  @Test
  public void joinNothing() throws InterruptedException {
    _mocks.replay();
    MyActor actor = _strange.spawn(new MyTarget()).actor();
    SFuture<String, VoidCheckedException> f = actor.joinNothingTwice();
    _thread.postExitCommand();
    _thread.enter();
    assertEquals("woo", f.sync());
  }

  @Test(timeout = 2000)
  public void performance() throws InterruptedException {
    _mocks.replay();
    MyActor actor = _strange.spawn(new MyTarget()).actor();
    for (int i = 0; i < 100; ++i) {
      SFuture<Void, VoidCheckedException> f = actor.performance(1000);
      _thread.postExitCommand();
      _thread.enter();
      f.sync(); // Probably not necessary.
    }
  }

  @ThreadCollectionType(ManualThreadCollection.class)
  public static class InitSuspends implements ActorTarget<InitSuspends.InitSuspendsActor> {
    public interface InitSuspendsActor extends Actor {
      SFuture<Void, VoidCheckedException> eager();
    }

    private final SFuture<?, ?> _latch;

    private InitSuspends(SFuture<?, ?> latch) {
      _latch = latch;
    }

    private InitSuspendsActor _self;

    public void init(InitSuspendsActor actor) throws JoinSuspension {
      throw new JoinSuspension(_latch, done -> {
        _self = actor;
        return null;
      });
    }

    public void eager() {
      _self.mailboxSize();
    }
  }

  @Test
  public void initCanSuspend() throws InterruptedException {
    _log.debug(eq(STACK_TRACE_NOT_LOGGED_HERE_FORMAT), anyObject(), anyObject());
    _mocks.replay();
    SFutureImpl<Void, VoidCheckedException> latch = new SFutureImpl<>();
    Spawned<InitSuspendsActor, VoidCheckedException> spawned = _strange.spawn(new InitSuspends(latch));
    // Execute init, which will suspend. Without this step the suspension would short-circuit due to suspendable already done:
    _thread.postExitCommand();
    _thread.enter();
    SFuture<Void, VoidCheckedException> eagerFuture = spawned.actor().eager();
    // Execute eager method and resolve the suspension:
    latch.putValue(null);
    SFuture<Void, VoidCheckedException> notSoEagerFuture = spawned.actor().eager();
    _thread.postExitCommand();
    _thread.enter();
    assertSame(spawned.actor(), spawned.sync());
    catchThrowableOfType(eagerFuture::sync, NullPointerException.class);
    assertEquals(null, notSoEagerFuture.sync());
  }
}
