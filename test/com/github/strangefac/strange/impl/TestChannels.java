package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.impl.Invocation.STACK_TRACE_NOT_LOGGED_HERE_FORMAT;
import static com.github.strangefac.strange.impl.SuspendOnly.SUSPEND_ONLY;
import static com.github.strangefac.strange.impl.TestSFutureImpl.keyEq;
import static com.github.strangefac.strange.util.Standard.also;
import static com.github.strangefac.strange.util.Standard.run;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import java.io.EOFException;
import java.lang.reflect.InvocationTargetException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.Suspendable;
import com.github.strangefac.strange.Suspension;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.function.FunctionThrows;
import com.github.strangefac.strange.impl.AllActorsImpl;
import com.github.strangefac.strange.impl.BasicJoinImpl;
import com.github.strangefac.strange.impl.StrangeImpl;
import com.github.strangefac.strange.impl.SignatureInfo.SignatureKey;
import com.github.strangefac.strange.impl.TestChannels.Deep1.Deep1Actor;
import com.github.strangefac.strange.impl.TestChannels.Deep2.Deep2Actor;
import com.github.strangefac.strange.impl.TestChannels.Shallow.ShallowActor;
import com.github.strangefac.strange.pool.CustomThreadPoolThreadCollection;
import com.github.strangefac.strange.pool.ThreadPoolThreadCollection;
import com.github.strangefac.strange.util.ComponentSource;
import com.github.strangefac.strange.util.EasyMockRule;

public class TestChannels {
  @ThreadCollectionType(ThreadPoolThreadCollection.class)
  public static class Deep1 implements ActorTarget<Deep1.Deep1Actor> {
    public interface Deep1Actor extends Actor {
      SFuture<Integer, Exception> getInt();
    }

    private final Deque<Callable<Integer>> _tasks = new LinkedList<>();

    public void init(Deep1Actor actor) {
      _tasks.add(() -> 101);
      _tasks.add(() -> {
        throw new EOFException("102");
      });
      _tasks.add(() -> 103);
      _tasks.add(() -> {
        throw new EOFException("104");
      });
    }

    public int getInt() throws Exception {
      return _tasks.removeFirst().call();
    }
  }

  @ThreadCollectionType(ThreadPoolThreadCollection.class)
  public static class Deep2 implements ActorTarget<Deep2.Deep2Actor> {
    public interface Deep2Actor extends Actor {
      SFuture<Float, Exception> getFloat();
    }

    private final Deque<Callable<Float>> _tasks = new LinkedList<>();

    public void init(Deep2Actor actor) {
      _tasks.add(() -> 201f);
      _tasks.add(() -> 202f);
      _tasks.add(() -> {
        throw new IllegalStateException("203");
      });
      _tasks.add(() -> {
        throw new Error("204");
      });
    }

    public float getFloat() throws Exception {
      return _tasks.removeFirst().call();
    }
  }

  @ThreadCollectionType(ThreadPoolThreadCollection.class)
  public static class Shallow implements ActorTarget<Shallow.ShallowActor> {
    public interface ShallowActor extends Actor {
      Future<String> getText();
    }

    private final Deep1Actor _deep1;
    private final Deep2Actor _deep2;

    public Shallow(Deep1Actor deep1, Deep2Actor deep2) {
      _deep1 = deep1;
      _deep2 = deep2;
    }

    public void init(ShallowActor actor) {
      // Do nothing.
    }

    public String getText() throws Suspension {
      return getTextImpl(_deep1, _deep2);
    }

    private static String getTextImpl(Deep1Actor deep1, Deep2Actor deep2) throws Suspension {
      SFuture<Integer, Exception> f1 = deep1.getInt();
      SFuture<Float, Exception> f2 = deep2.getFloat();
      throw new Suspension(new BasicJoinImpl<>(f1, f2)) {
        private static final long serialVersionUID = 1L;
        private int _done = 0;

        public Object done(Suspendable done) throws ExecutionException, Suspension {
          if (2 != ++_done) throw SUSPEND_ONLY;
          return f1.assertDoneAndGetUninterruptibly() + ", " + f2.assertDoneAndGetUninterruptibly();
        }

        public Object doneImmediately() {
          return atLeastOneSubtask();
        }
      };
    }
  }

  public static class SynchronousShallow {
    private final ShallowActor _shallow;
    private final Deep1Actor _deep1;
    private final Deep2Actor _deep2;

    public SynchronousShallow(ShallowActor shallow, Deep1Actor deep1, Deep2Actor deep2) {
      _shallow = shallow;
      _deep1 = deep1;
      _deep2 = deep2;
    }

    public String getTextDirectly() throws InterruptedException, ExecutionException {
      return _shallow.getText().get(); // Interruptible wait is exactly what we want here.
    }

    public String getTextViaPost() throws InterruptedException, ExecutionException {
      return _shallow.post(() -> Shallow.getTextImpl(_deep1, _deep2)).get();
    }
  }

  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();
  private final CustomThreadPoolThreadCollection _pool = new CustomThreadPoolThreadCollection(10000, 0);
  private final StrangeImpl _strange = run(() -> {
    ComponentSource componentSource = also(_mocks.createMock(ComponentSource.class), it -> expect(it.getComponent(ThreadPoolThreadCollection.class)).andReturn(_pool));
    Logger deep1Log = also(_mocks.createMock(Logger.class), it -> {
      it.debug(eq(STACK_TRACE_NOT_LOGGED_HERE_FORMAT), keyEq(new SignatureKey("getInt")), anyObject());
      expectLastCall().anyTimes();
    });
    Logger deep2Log = also(_mocks.createMock(Logger.class), it -> {
      it.debug(eq(STACK_TRACE_NOT_LOGGED_HERE_FORMAT), keyEq(new SignatureKey("getFloat")), anyObject());
      expectLastCall().anyTimes();
    });
    Logger shallowLog = also(_mocks.createMock(Logger.class), it -> {
      it.debug(eq(STACK_TRACE_NOT_LOGGED_HERE_FORMAT), anyObject(), anyObject());
      expectLastCall().anyTimes();
    });
    ILoggerFactory loggerFactory = also(_mocks.createMock(ILoggerFactory.class), it -> {
      expect(it.getLogger(Deep1.class.getName())).andReturn(deep1Log);
      expect(it.getLogger(Deep2.class.getName())).andReturn(deep2Log);
      expect(it.getLogger(Shallow.class.getName())).andReturn(shallowLog);
    });
    return new StrangeImpl(componentSource, loggerFactory, new AllActorsImpl());
  });

  @After
  public void tearDown() throws InterruptedException {
    _pool.dispose();
  }

  private void channels(FunctionThrows<SynchronousShallow, String, Exception> getText) throws Exception {
    _mocks.replay();
    SynchronousShallow synchronous = run(() -> {
      Deep1Actor deep1 = _strange.spawn(new Deep1()).sync();
      Deep2Actor deep2 = _strange.spawn(new Deep2()).sync();
      return new SynchronousShallow(_strange.spawn(new Shallow(deep1, deep2)).sync(), deep1, deep2);
    });
    assertEquals("101, 201.0", getText.apply(synchronous));
    try {
      getText.apply(synchronous);
    } catch (ExecutionException e) {
      Throwable t = e.getCause();
      assertSame(InvocationTargetException.class, t.getClass());
      t = t.getCause();
      assertSame(ExecutionException.class, t.getClass());
      t = t.getCause();
      assertSame(InvocationTargetException.class, t.getClass());
      t = t.getCause();
      assertSame(EOFException.class, t.getClass());
      assertEquals("102", t.getMessage());
    }
    try {
      getText.apply(synchronous);
    } catch (ExecutionException e) {
      Throwable t = e.getCause();
      assertSame(InvocationTargetException.class, t.getClass());
      t = t.getCause();
      assertSame(ExecutionException.class, t.getClass());
      t = t.getCause();
      assertSame(InvocationTargetException.class, t.getClass());
      t = t.getCause();
      assertSame(IllegalStateException.class, t.getClass());
      assertEquals("203", t.getMessage());
    }
    try {
      getText.apply(synchronous);
    } catch (ExecutionException e) {
      Throwable t = e.getCause();
      assertSame(InvocationTargetException.class, t.getClass());
      t = t.getCause();
      assertSame(ExecutionException.class, t.getClass());
      t = t.getCause();
      assertSame(InvocationTargetException.class, t.getClass());
      t = t.getCause();
      assertSame(EOFException.class, t.getClass());
      assertEquals("104", t.getMessage());
    }
  }

  @Test
  public void directly() throws Exception {
    channels(SynchronousShallow::getTextDirectly);
  }

  @Test
  public void viaPost() throws Exception {
    channels(SynchronousShallow::getTextViaPost);
  }
}
