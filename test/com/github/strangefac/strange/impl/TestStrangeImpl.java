package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.util.Standard.also;
import static com.github.strangefac.strange.util.Standard.run;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.AllActors;
import com.github.strangefac.strange.Spawned;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.impl.StrangeImpl;
import com.github.strangefac.strange.pool.CustomThreadPoolThreadCollection;
import com.github.strangefac.strange.pool.ThreadPoolThreadCollection;
import com.github.strangefac.strange.util.ComponentSource;
import com.github.strangefac.strange.util.EasyMockRule;

public class TestStrangeImpl {
  private interface MyActor extends Actor {
  }

  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();
  private final Logger _log = _mocks.createMock(Logger.class);
  private final CustomThreadPoolThreadCollection _pool = new CustomThreadPoolThreadCollection(10000, 0);
  private final StrangeImpl _strange = run(() -> {
    ComponentSource componentSource = also(_mocks.createMock(ComponentSource.class), it -> expect(it.getComponent(ThreadPoolThreadCollection.class)).andReturn(_pool));
    AllActors allActors = also(_mocks.createMock(AllActors.class), it -> {
      it.purgeAndAdd(anyObject());
      expectLastCall().anyTimes();
    });
    return new StrangeImpl(componentSource, name -> _log, allActors);
  });

  @After
  public void tearDown() throws InterruptedException {
    _pool.dispose();
  }

  private interface ActorTarget2<A extends Actor> extends ActorTarget<A> {
  }

  @SuppressWarnings("unused")
  private interface MyActorTarget<T> extends ActorTarget<MyActor> {
  }

  private interface MyActorTarget2 extends MyActorTarget<Object> {
  }

  @Test
  public void actorInterfaceDiscovery() {
    // Plain old target impl:
    @ThreadCollectionType(ThreadPoolThreadCollection.class)
    class TargetImpl implements ActorTarget<MyActor> {
      public void init(MyActor actor) {
        // Do nothing.
      }
    }
    // The interface is on the superclass:
    class TargetImpl2 extends TargetImpl {
      // Nothing else.
    }
    // The interface is a parameterized subinterface of ActorTarget:
    @ThreadCollectionType(ThreadPoolThreadCollection.class)
    class Target2Impl implements ActorTarget2<MyActor> {
      public void init(MyActor actor) {
        // Do nothing.
      }
    }
    // On the superclass:
    class Target2Impl2 extends Target2Impl {
      // Nothing else.
    }
    // The interface is a subinterface of ActorTarget and not parameterized:
    @ThreadCollectionType(ThreadPoolThreadCollection.class)
    class MyTargetImpl implements MyActorTarget<Object> {
      public void init(MyActor actor) {
        // Do nothing.
      }
    }
    // On the superclass:
    class MyTargetImpl2 extends MyTargetImpl {
      // Nothing else.
    }
    // The interface is a subinterface of a non-parameterized subinterface of ActorTarget:
    @ThreadCollectionType(ThreadPoolThreadCollection.class)
    class MyTarget2Impl implements MyActorTarget2 {
      public void init(MyActor actor) {
        // Do nothing.
      }
    }
    // On the superclass:
    class MyTarget2Impl2 extends MyTarget2Impl {
      // Nothing else.
    }
    _mocks.replay();
    assertTrue(actor(_strange.spawn(new TargetImpl())) instanceof MyActor);
    assertTrue(actor(_strange.spawn(new TargetImpl2())) instanceof MyActor);
    assertTrue(actor(_strange.spawn(new Target2Impl())) instanceof MyActor);
    assertTrue(actor(_strange.spawn(new Target2Impl2())) instanceof MyActor);
    assertTrue(actor(_strange.spawn(new MyTargetImpl())) instanceof MyActor);
    assertTrue(actor(_strange.spawn(new MyTargetImpl2())) instanceof MyActor);
    assertTrue(actor(_strange.spawn(new MyTarget2Impl())) instanceof MyActor);
    assertTrue(actor(_strange.spawn(new MyTarget2Impl2())) instanceof MyActor);
  }

  private static Actor actor(Spawned<?, ?> spawned) {
    return spawned.actor();
  }
}
