package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.impl.Mailbox.DEAD_ACTOR_MESSAGE;
import static com.github.strangefac.strange.util.Standard.also;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.function.VoidCheckedException;
import com.github.strangefac.strange.impl.AllActorsImpl;
import com.github.strangefac.strange.impl.DelegatingSuspension;
import com.github.strangefac.strange.impl.ManualThreadCollection;
import com.github.strangefac.strange.impl.StrangeImpl;
import com.github.strangefac.strange.impl.TestKill.Mortal.MortalActor;
import com.github.strangefac.strange.util.ComponentSource;
import com.github.strangefac.strange.util.EasyMockRule;

public class TestKill {
  @ThreadCollectionType(ManualThreadCollection.class)
  public static class Mortal implements ActorTarget<Mortal.MortalActor> {
    public interface MortalActor extends Actor {
      SFuture<String, VoidCheckedException> hello();
    }

    public void init(MortalActor actor) {
      // Do nothing.
    }

    public String hello() {
      return "hello";
    }
  }

  @ThreadCollectionType(ManualThreadCollection.class)
  public static class Leaf implements ActorTarget<Leaf.LeafActor> {
    public interface LeafActor extends Actor {
      SFuture<String, VoidCheckedException> chainMortal();
    }

    private final MortalActor _mortal;

    public Leaf(MortalActor mortal) {
      _mortal = mortal;
    }

    public void init(LeafActor actor) {
      // Do nothing.
    }

    public String chainMortal() throws DelegatingSuspension {
      throw new DelegatingSuspension(_mortal.hello());
    }
  }

  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();
  private final ManualThreadCollection _thread = new ManualThreadCollection();
  private MortalActor _mortal;
  private SFuture<String, VoidCheckedException> _f;

  @Before
  public void setUp() {
    ComponentSource componentSource = also(_mocks.createMock(ComponentSource.class), it -> expect(it.getComponent(ManualThreadCollection.class)).andReturn(_thread));
    _mocks.replay();
    StrangeImpl strange = new StrangeImpl(componentSource, LoggerFactory.getILoggerFactory(), new AllActorsImpl());
    _mortal = strange.spawn(new Mortal()).actor();
    _f = strange.spawn(new Leaf(_mortal)).actor().chainMortal();
  }

  @Test
  public void similar() throws InterruptedException {
    _mortal.kill();
    _thread.postExitCommand();
    _thread.enter();
    assertEquals(DEAD_ACTOR_MESSAGE, catchThrowableOfType(_f::sync, RejectedExecutionException.class).getMessage());
  }

  @Test
  public void similar2() throws InterruptedException {
    _thread.postExitCommand();
    _thread.enter();
    _mortal.kill();
    _thread.postExitCommand();
    _thread.enter();
    catchThrowableOfType(_f::sync, CancellationException.class);
  }
}
