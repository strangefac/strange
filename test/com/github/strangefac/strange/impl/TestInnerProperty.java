package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.util.Standard.also;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.InnerProperty;
import com.github.strangefac.strange.Spawned;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.function.VoidCheckedException;
import com.github.strangefac.strange.impl.AllActorsImpl;
import com.github.strangefac.strange.impl.ManualThreadCollection;
import com.github.strangefac.strange.impl.SFutureImpl;
import com.github.strangefac.strange.impl.StrangeImpl;
import com.github.strangefac.strange.impl.TestInnerProperty.My.Text;
import com.github.strangefac.strange.util.ComponentSource;
import com.github.strangefac.strange.util.EasyMockRule;

public class TestInnerProperty {
  public interface MyActor extends Actor {
    @InnerProperty(Text.class)
    String text();
  }

  @ThreadCollectionType(ManualThreadCollection.class)
  public static class My implements ActorTarget<MyActor> {
    public class Text implements Supplier<String> {
      public String get() {
        return _text;
      }
    }

    private final String _text;

    My(String text) {
      _text = text;
    }

    public void init(MyActor actor) {
      // Do nothing.
    }
  }

  private static class My2 extends My {
    private My2(String text) {
      super(text);
    }
  }

  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();

  private void worksImpl(My myTarget, String expected) throws InterruptedException {
    ManualThreadCollection mtc = new ManualThreadCollection();
    ComponentSource componentSource = also(_mocks.createMock(ComponentSource.class), it -> expect(it.getComponent(ManualThreadCollection.class)).andReturn(mtc));
    _mocks.replay();
    StrangeImpl strange = new StrangeImpl(componentSource, LoggerFactory.getILoggerFactory(), new AllActorsImpl());
    SFutureImpl<String, VoidCheckedException> f = new SFutureImpl<>();
    Spawned<MyActor, VoidCheckedException> spawned = strange.spawn(myTarget);
    mtc.postExitCommand();
    mtc.enter();
    MyActor my = spawned.sync();
    new Thread(() -> {
      try {
        f.putValue(my.text());
      } catch (Throwable t) {
        f.putCauseOfExecutionException(new InvocationTargetException(t));
      }
    }).start();
    assertEquals(expected, f.sync());
  }

  @Test
  public void works() throws InterruptedException {
    worksImpl(new My("hello"), "hello");
  }

  @Test
  public void works2() throws InterruptedException {
    worksImpl(new My2("hello2"), "hello2");
  }
}
