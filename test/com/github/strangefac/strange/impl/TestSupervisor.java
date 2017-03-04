package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.impl.Invocation.STACK_TRACE_NOT_LOGGED_HERE_FORMAT;
import static com.github.strangefac.strange.impl.TestSFutureImpl.keyEq;
import static com.github.strangefac.strange.util.Standard.also;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.same;
import static org.junit.Assert.assertSame;
import java.util.function.Function;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.AllActors;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.Task;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.Wrapper;
import com.github.strangefac.strange.impl.DelegatingSuspension;
import com.github.strangefac.strange.impl.ManualThreadCollection;
import com.github.strangefac.strange.impl.StrangeImpl;
import com.github.strangefac.strange.impl.SignatureInfo.SignatureKey;
import com.github.strangefac.strange.impl.TestSupervisor.SupervisorInner.SupervisorInnerActor;
import com.github.strangefac.strange.util.ComponentSource;
import com.github.strangefac.strange.util.EasyMockRule;

public class TestSupervisor {
  private static class MyException extends Exception {
    private static final long serialVersionUID = 1L;
  }

  @ThreadCollectionType(ManualThreadCollection.class)
  public static class SupervisorOuter implements ActorTarget<SupervisorOuter.SupervisorOuterActor> {
    public interface SupervisorOuterActor extends Actor {
      SFuture<String, MyException> get();
    }

    private final SupervisorInnerActor _inner;

    private SupervisorOuter(SupervisorInnerActor inner) {
      _inner = inner;
    }

    public void init(SupervisorOuterActor actor) {
      // Do nothing.
    }

    public String get() throws DelegatingSuspension {
      throw new DelegatingSuspension(_inner.get());
    }
  }

  @ThreadCollectionType(ManualThreadCollection.class)
  public static class SupervisorInner implements ActorTarget<SupervisorInner.SupervisorInnerActor> {
    public interface SupervisorInnerActor extends Actor {
      SFuture<String, MyException> get();
    }

    private final MyException _e;

    private SupervisorInner(MyException e) {
      _e = e;
    }

    public void init(SupervisorInnerActor actor) {
      // Do nothing.
    }

    public String get() throws MyException {
      throw _e;
    }
  }

  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();
  private final Logger _innerLog = _mocks.createMock(Logger.class), _outerLog = _mocks.createMock(Logger.class);
  private final ILoggerFactory _loggerFactory = also(_mocks.createMock(ILoggerFactory.class), it -> {
    expect(it.getLogger(SupervisorInner.class.getName())).andReturn(_innerLog).anyTimes();
    expect(it.getLogger(SupervisorOuter.class.getName())).andReturn(_outerLog).anyTimes();
  });
  private final MyException _exception = new MyException();

  public void test(SupervisorInner innerTarget, Function<SupervisorInnerActor, SupervisorOuter> outerTarget) throws InterruptedException {
    AllActors allActors = also(_mocks.createMock(AllActors.class), it -> {
      it.purgeAndAdd(anyObject());
      expectLastCall().anyTimes();
    });
    ManualThreadCollection mtc = new ManualThreadCollection();
    ComponentSource componentSource = also(_mocks.createMock(ComponentSource.class), it -> expect(it.getComponent(ManualThreadCollection.class)).andReturn(mtc));
    _mocks.replay();
    StrangeImpl strange = new StrangeImpl(componentSource, _loggerFactory, allActors);
    SFuture<String, MyException> f = strange.spawn(outerTarget.apply(strange.spawn(innerTarget).actor())).actor().get();
    do {
      mtc.postExitCommand();
    } while (mtc.enter() > 1);
    assertSame(_exception, catchThrowable(f::sync));
  }

  @Test
  public void supervisorInnerSupervisorOuter() throws InterruptedException {
    _innerLog.debug(eq(STACK_TRACE_NOT_LOGGED_HERE_FORMAT), keyEq(new SignatureKey("get")), same(_exception));
    _outerLog.debug(eq(STACK_TRACE_NOT_LOGGED_HERE_FORMAT), keyEq(new SignatureKey("post", Task.class, Wrapper.class)), same(_exception));
    test(new SupervisorInner(_exception), SupervisorOuter::new);
  }
}
