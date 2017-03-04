package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.util.Standard.also;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.Const;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.Spawned;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.function.VoidCheckedException;
import com.github.strangefac.strange.impl.AllActorsImpl;
import com.github.strangefac.strange.impl.ManualThreadCollection;
import com.github.strangefac.strange.impl.StrangeImpl;
import com.github.strangefac.strange.util.ComponentSource;

@Ignore("TDD for unimplemented feature.")
public class TestMultipleReaders {
  public interface MyActor extends Actor {
    SFuture<Integer, InterruptedException> writer();

    @Const
    SFuture<Integer, InterruptedException> reader();
  }

  @ThreadCollectionType(ManualThreadCollection.class)
  public static class My implements ActorTarget<MyActor> {
    private int _state;

    public void init(MyActor actor) {
      _state = 4;
    }

    public Integer writer() throws InterruptedException {
      Thread.sleep(100);
      return ++_state;
    }

    public Integer reader() throws InterruptedException {
      Thread.sleep(100);
      return _state;
    }
  }

  private static class Mark {
    private final long _mark = System.currentTimeMillis();

    private void assertNear(long expected, long delta) {
      assertEquals(expected, System.currentTimeMillis() - _mark, delta);
    }
  }

  @Test
  public void works() throws InterruptedException {
    ManualThreadCollection mtc = new ManualThreadCollection();
    ComponentSource componentSource = also(createMock(ComponentSource.class), it -> {
      expect(it.getComponent(ManualThreadCollection.class)).andReturn(mtc);
      replay(it);
    });
    StrangeImpl strange = new StrangeImpl(componentSource, LoggerFactory.getILoggerFactory(), new AllActorsImpl());
    Spawned<MyActor, VoidCheckedException> spawned = strange.spawn(new My());
    mtc.postExitCommand();
    mtc.enter();
    MyActor myActor = spawned.sync();
    {
      SFuture<Integer, InterruptedException> w1 = myActor.writer(), w2 = myActor.writer();
      Mark mark = new Mark();
      mtc.postExitCommand();
      mtc.enter();
      mark.assertNear(200, 10);
      assertEquals(Integer.valueOf(5), w1.sync());
      assertEquals(Integer.valueOf(6), w2.sync());
    }
    {
      SFuture<Integer, InterruptedException> r1 = myActor.reader(), r2 = myActor.reader();
      Mark mark = new Mark();
      new Thread(() -> {
        mtc.postExitCommand();
        try {
          mtc.enter();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }).start();
      mtc.postExitCommand();
      mtc.enter();
      mark.assertNear(100, 10);
      assertEquals(Integer.valueOf(6), r1.sync());
      assertEquals(Integer.valueOf(6), r2.sync());
    }
    {
      SFuture<Integer, InterruptedException> w1 = myActor.writer(), w2 = myActor.writer();
      Mark mark = new Mark();
      mtc.postExitCommand();
      mtc.enter();
      mark.assertNear(200, 10);
      assertEquals(Integer.valueOf(7), w1.sync());
      assertEquals(Integer.valueOf(8), w2.sync());
    }
  }
}
