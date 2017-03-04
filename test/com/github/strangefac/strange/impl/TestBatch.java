package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.util.Standard.also;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import java.io.EOFException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.AllActors;
import com.github.strangefac.strange.Batch;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.Syncable;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.Syncable.AbruptSyncable;
import com.github.strangefac.strange.Syncable.NormalSyncable;
import com.github.strangefac.strange.function.VoidCheckedException;
import com.github.strangefac.strange.impl.ManualThreadCollection;
import com.github.strangefac.strange.impl.StrangeImpl;
import com.github.strangefac.strange.impl.TestBatch.MyTarget.MyActor;
import com.github.strangefac.strange.util.ComponentSource;
import com.github.strangefac.strange.util.EasyMockRule;
import gnu.trove.map.hash.THashMap;

public class TestBatch {
  @ThreadCollectionType(ManualThreadCollection.class)
  public static class MyTarget implements ActorTarget<MyTarget.MyActor> {
    public interface MyActor extends Actor {
      @Batch
      Future<String> hmm(String arg, int i);

      @Batch
      SFuture<Map<Object, Syncable<Double, IOException>>, VoidCheckedException> multipleOutcomes(Object callId, IOException fail, Double ifNotFail);
    }

    public void init(MyActor actor) {
      // Do nothing.
    }

    public String hmm(String[] args, int[] is) {
      return also(new StringBuilder(), sb -> {
        for (int x = 0; x < args.length; ++x)
          sb.append('(').append(args[x]).append(' ').append(is[x]).append(')');
      }).toString();
    }

    public Map<Object, Syncable<Double, IOException>> multipleOutcomes(Object[] callIds, IOException[] fails, Double[] ifNotFails) {
      return also(new THashMap<>(), outcomes -> {
        for (int i = 0; i < callIds.length; ++i)
          outcomes.put(callIds[i], null != fails[i] ? new AbruptSyncable<>(fails[i]) : new NormalSyncable<>(ifNotFails[i]));
      });
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
  private final AllActors _allActors = also(_mocks.createMock(AllActors.class), it -> it.purgeAndAdd(anyObject()));
  {
    _mocks.replay();
  }

  @Test
  public void works() throws InterruptedException, ExecutionException {
    StrangeImpl strange = new StrangeImpl(_componentSource, _loggerFactory, _allActors);
    MyActor actor = strange.spawn(new MyTarget()).actor();
    {
      Future<String> f1 = actor.hmm("woo", 6);
      _thread.postExitCommand();
      _thread.enter();
      assertEquals("(woo 6)", f1.get());
    }
    {
      Future<String> f1 = actor.hmm("woo", 6);
      Future<String> f2 = actor.hmm("yay", 5);
      _thread.postExitCommand();
      _thread.enter();
      assertEquals("(woo 6)(yay 5)", f1.get());
      assertEquals("(woo 6)(yay 5)", f2.get());
    }
    {
      Future<String> f1 = actor.hmm("woo", 6);
      Future<String> f2 = actor.hmm("yay", 5);
      Future<String> f3 = actor.hmm("houpla", 4);
      _thread.postExitCommand();
      _thread.enter();
      assertEquals("(woo 6)(yay 5)(houpla 4)", f1.get());
      assertEquals("(woo 6)(yay 5)(houpla 4)", f2.get());
      assertEquals("(woo 6)(yay 5)(houpla 4)", f3.get());
    }
  }

  @Test
  public void multipleOutcomes() throws InterruptedException, IOException {
    StrangeImpl strange = new StrangeImpl(_componentSource, _loggerFactory, _allActors);
    MyActor actor = strange.spawn(new MyTarget()).actor();
    Object id1 = new Object(), id2 = new Object();
    EOFException eof = new EOFException();
    SFuture<Map<Object, Syncable<Double, IOException>>, VoidCheckedException> f1 = actor.multipleOutcomes(id1, eof, null);
    SFuture<Map<Object, Syncable<Double, IOException>>, VoidCheckedException> f2 = actor.multipleOutcomes(id2, null, 5.5);
    _thread.postExitCommand();
    _thread.enter();
    Map<Object, Syncable<Double, IOException>> outcomes = f1.sync();
    assertSame(outcomes, f2.sync());
    Syncable<Double, IOException> o1 = outcomes.get(id1), o2 = outcomes.get(id2);
    assertSame(eof, catchThrowable(o1::sync));
    assertEquals(5.5, o2.sync(), 0);
  }
}
