package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.util.Standard.also;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.AfterTask;
import com.github.strangefac.strange.AllActors;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.function.VoidCheckedException;
import com.github.strangefac.strange.impl.JoinSuspension;
import com.github.strangefac.strange.impl.ManualThreadCollection;
import com.github.strangefac.strange.impl.StrangeImpl;
import com.github.strangefac.strange.util.ComponentSource;
import com.github.strangefac.strange.util.EasyMockRule;
import com.github.strangefac.strange.util.TypedArrayList;

public class TestAfterTask {
  public interface MyActor extends Actor {
    SFuture<Integer, VoidCheckedException> hmm();

    SFuture<Integer, VoidCheckedException> that();
  }

  @ThreadCollectionType(ManualThreadCollection.class)
  public class MyTarget implements ActorTarget<MyActor>, AfterTask {
    private final SFuture<?, ?> _dummy;

    public MyTarget(SFuture<?, ?> dummy) {
      _dummy = dummy;
    }

    private final TypedArrayList<SFuture<?, ?>> _bg = new TypedArrayList<>(SFuture.class);
    private MyActor _self;

    public void init(MyActor actor) {
      _self = actor;
    }

    public void afterTask() {
      assertEquals(1, _bg.size()); // Failure causes an unexpected call to the mock log.
      _bg.clear();
    }

    public Integer hmm() throws JoinSuspension {
      assertEquals(0, _bg.size());
      SFuture<Integer, VoidCheckedException> f = _self.that();
      _bg.add(f);
      throw new JoinSuspension(_bg, join -> { // All Suspensions make a defensive copy.
        assertEquals(0, _bg.size());
        _bg.add(_dummy);
        return f.sync();
      });
    }

    public Integer that() {
      assertEquals(0, _bg.size());
      _bg.add(_dummy);
      return 5;
    }
  }

  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();
  private final ManualThreadCollection _thread = new ManualThreadCollection();
  private final ComponentSource _componentSource = also(_mocks.createMock(ComponentSource.class), it -> {
    expect(it.getComponent(ManualThreadCollection.class)).andReturn(_thread);
  });
  private final ILoggerFactory _loggerFactory = also(_mocks.createMock(ILoggerFactory.class), it -> {
    expect(it.getLogger(anyObject())).andReturn(_mocks.createMock(Logger.class)).anyTimes();
  });
  private final SFuture<?, ?> _dummy = _mocks.createMock(SFuture.class);
  private final AllActors _allActors = also(_mocks.createMock(AllActors.class), it -> it.purgeAndAdd(anyObject()));

  @Test
  public void works() throws InterruptedException {
    _mocks.replay();
    StrangeImpl strange = new StrangeImpl(_componentSource, _loggerFactory, _allActors);
    MyTarget target = new MyTarget(_dummy);
    MyActor actor = strange.spawn(target).actor();
    SFuture<Integer, VoidCheckedException> f = actor.hmm();
    _thread.postExitCommand();
    _thread.enter();
    assertEquals(Integer.valueOf(5), f.sync());
    f = actor.post(() -> {
      assertEquals(0, target._bg.size());
      target._bg.add(_dummy);
      return 6;
    });
    _thread.postExitCommand();
    _thread.enter();
    assertEquals(Integer.valueOf(6), f.sync());
  }
}
