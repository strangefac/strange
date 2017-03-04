package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.util.Standard.also;
import static com.github.strangefac.strange.util.Standard.run;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.AllActors;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.function.VoidCheckedException;
import com.github.strangefac.strange.impl.ManualThreadCollection;
import com.github.strangefac.strange.impl.StrangeImpl;
import com.github.strangefac.strange.impl.WrapperImpl;
import com.github.strangefac.strange.util.ComponentSource;
import com.github.strangefac.strange.util.EasyMockRule;

/** These tests are somewhat obsolete. */
public class TestAsyncOrSync {
  private interface SubtypeOfFuture<V> extends Future<V> {
    // No additional members.
  }

  private interface MyActor extends Actor {
    /** This return type happily accepts a Future. */
    Object sync();

    Future<Number> async();

    SFuture<Number, VoidCheckedException> async2();

    /** Our Future impl is incompatible with this return type. */
    SubtypeOfFuture<String> sync2();

    /** Our Future impl was compatible with this return type, but now isn't. */
    FutureTask<?> sync3();
  }

  @ThreadCollectionType(ManualThreadCollection.class)
  private class MyImpl implements ActorTarget<MyActor> {
    public void init(MyActor actor) {
      // Do nothing.
    }

    @SuppressWarnings("unused")
    public boolean sync() {
      return true;
    }

    @SuppressWarnings("unused")
    public int async() {
      return 1;
    }

    @SuppressWarnings("unused")
    public int async2() {
      return 2;
    }

    @SuppressWarnings("unused")
    public SubtypeOfFuture<?> sync2() {
      return _myFuture;
    }

    @SuppressWarnings("unused")
    public Object sync3() {
      return _task;
    }
  }

  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();
  private final ManualThreadCollection _pool = new ManualThreadCollection();
  private final SubtypeOfFuture<?> _myFuture = _mocks.createMock(SubtypeOfFuture.class);
  private final FutureTask<Void> _task = new FutureTask<>(() -> {
    throw new UnsupportedOperationException();
  });
  private final StrangeImpl _strange = run(() -> {
    ComponentSource componentSource = also(_mocks.createMock(ComponentSource.class), it -> expect(it.getComponent(ManualThreadCollection.class)).andReturn(_pool));
    Logger log = _mocks.createMock(Logger.class);
    AllActors allActors = also(_mocks.createMock(AllActors.class), it -> it.purgeAndAdd(anyObject()));
    return new StrangeImpl(componentSource, name -> log, allActors);
  });

  @Test
  public void asyncOrSync() throws Throwable {
    _mocks.replay();
    MyActor actor = _strange.spawn(new MyImpl()).actor();
    {
      SFuture<?, ?> f = (SFuture<?, ?>) actor.sync();
      _pool.postExitCommand();
      _pool.enter();
      assertEquals(true, f.sync());
    }
    {
      Future<Number> f = actor.async();
      _pool.postExitCommand();
      _pool.enter();
      assertEquals(1, f.get().doubleValue(), 0);
    }
    {
      SFuture<Number, VoidCheckedException> f = actor.async2();
      _pool.postExitCommand();
      _pool.enter();
      assertEquals(2, f.get().doubleValue(), 0);
    }
    also(catchThrowableOfType(actor::sync2, ClassCastException.class), e -> {
      assertEquals(String.format("%s cannot be cast to %s", WrapperImpl.class.getName(), SubtypeOfFuture.class.getName()), e.getMessage());
    });
    also(catchThrowableOfType(actor::sync3, ClassCastException.class), e -> {
      assertEquals(String.format("%s cannot be cast to %s", WrapperImpl.class.getName(), FutureTask.class.getName()), e.getMessage());
    });
  }
}
