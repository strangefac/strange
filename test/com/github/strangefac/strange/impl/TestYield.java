package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.function.NullRunnable.NULL_RUNNABLE;
import static com.github.strangefac.strange.impl.InvocationInfo.YIELDING_FORMAT;
import static com.github.strangefac.strange.impl.TestSFutureImpl.keyEq;
import static com.github.strangefac.strange.util.Standard.also;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.AllActors;
import com.github.strangefac.strange.Batch;
import com.github.strangefac.strange.Patient;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.Yield;
import com.github.strangefac.strange.impl.ManualThreadCollection;
import com.github.strangefac.strange.impl.StrangeImpl;
import com.github.strangefac.strange.impl.SignatureInfo.SignatureKey;
import com.github.strangefac.strange.util.ComponentSource;
import com.github.strangefac.strange.util.EasyMockRule;

public class TestYield {
  public interface MyActor extends Actor {
    @Yield
    Future<Object> lowPriority(Runnable maybeInterrupt);

    @Batch
    @Yield
    Future<String> lowPriorityBatch(String part, Runnable maybeInterrupt);

    Future<Object> normalPriority();

    @Patient
    Future<Object> patient();
  }

  @ThreadCollectionType(ManualThreadCollection.class)
  public static class MyTarget implements ActorTarget<MyActor> {
    private static void checkInterrupt() throws InterruptedException {
      if (Thread.interrupted()) throw new InterruptedException();
    }

    public void init(MyActor actor) {
      // Do nothing.
    }

    public Object lowPriority(Runnable maybeInterrupt) throws InterruptedException {
      maybeInterrupt.run();
      checkInterrupt();
      return "low";
    }

    public String lowPriorityBatch(String[] parts, Runnable[] maybeInterruptArray) throws InterruptedException {
      for (Runnable maybeInterrupt : maybeInterruptArray) {
        maybeInterrupt.run();
        checkInterrupt();
      }
      StringBuilder sb = new StringBuilder();
      for (String part : parts)
        sb.append(part);
      return sb.toString();
    }

    public Object normalPriority() {
      return "normal";
    }

    public Object patient() {
      return "patient";
    }
  }

  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();
  private final ManualThreadCollection _thread = new ManualThreadCollection();
  private final ComponentSource _componentSource = also(_mocks.createMock(ComponentSource.class), it -> {
    expect(it.getComponent(ManualThreadCollection.class)).andReturn(_thread);
  });
  private final Logger _log = _mocks.createMock(Logger.class);
  private final ILoggerFactory _loggerFactory = also(_mocks.createMock(ILoggerFactory.class), it -> {
    expect(it.getLogger(MyTarget.class.getName())).andReturn(_log).anyTimes();
  });

  private MyActor replayAndSpawn() {
    AllActors allActors = also(_mocks.createMock(AllActors.class), it -> it.purgeAndAdd(anyObject()));
    _mocks.replay();
    return new StrangeImpl(_componentSource, _loggerFactory, allActors).spawn(new MyTarget()).actor();
  }

  @Test
  public void cancelInsteadOfExec() throws Exception {
    _log.info(eq(YIELDING_FORMAT), keyEq(new SignatureKey("lowPriority", Runnable.class)));
    _log.info(eq(YIELDING_FORMAT), keyEq(new SignatureKey("lowPriorityBatch", String.class, Runnable.class)));
    MyActor actor = replayAndSpawn();
    {
      Future<Object> n = actor.normalPriority();
      Future<Object> l = actor.lowPriority(NULL_RUNNABLE);
      _thread.postExitCommand();
      _thread.enter();
      assertEquals("normal", n.get());
      assertEquals("low", l.get());
    }
    {
      Future<Object> l = actor.lowPriority(NULL_RUNNABLE);
      Future<Object> n = actor.normalPriority();
      _thread.postExitCommand();
      _thread.enter();
      catchThrowableOfType(l::get, CancellationException.class);
      assertEquals("normal", n.get());
    }
    {
      Future<Object> n = actor.normalPriority();
      Future<String> l1 = actor.lowPriorityBatch("woo", NULL_RUNNABLE);
      Future<String> l2 = actor.lowPriorityBatch("yay", NULL_RUNNABLE);
      _thread.postExitCommand();
      _thread.enter();
      assertEquals("normal", n.get());
      assertEquals("wooyay", l1.get());
      assertEquals("wooyay", l2.get());
    }
    {
      Future<String> l1 = actor.lowPriorityBatch("woo", NULL_RUNNABLE);
      Future<String> l2 = actor.lowPriorityBatch("yay", NULL_RUNNABLE);
      Future<Object> n = actor.normalPriority();
      _thread.postExitCommand();
      _thread.enter();
      catchThrowableOfType(l1::get, CancellationException.class);
      catchThrowableOfType(l2::get, CancellationException.class);
      assertEquals("normal", n.get());
    }
  }

  @Test
  public void cancelOnNewMessage() throws Exception {
    MyActor actor = replayAndSpawn();
    {
      Future<?>[] n = {null};
      Future<Object> l = actor.lowPriority(() -> {
        // This posting should cause a cancel of this invocation:
        n[0] = actor.normalPriority(); // This line does not respond to the interrupt, but the next one will.
      });
      _thread.postExitCommand();
      _thread.enter();
      catchThrowableOfType(l::get, CancellationException.class);
      assertEquals("normal", n[0].get());
    }
    {
      Future<?>[] n = {null};
      Future<String> l1 = actor.lowPriorityBatch("woo", () -> n[0] = actor.normalPriority()); // Should cancel l1 and l2.
      Future<String> l2 = actor.lowPriorityBatch("yay", NULL_RUNNABLE);
      _thread.postExitCommand();
      _thread.enter(); // Should invoke l1 and l2 as a batch.
      catchThrowableOfType(l1::get, CancellationException.class);
      catchThrowableOfType(l2::get, CancellationException.class);
      assertEquals("normal", n[0].get());
    }
  }

  @Test
  public void patientQueueDoesNotPreventExec() throws Exception {
    _log.info(eq(YIELDING_FORMAT), keyEq(new SignatureKey("lowPriority", Runnable.class)));
    expectLastCall().times(2);
    MyActor actor = replayAndSpawn();
    {
      Future<Object> l = actor.lowPriority(NULL_RUNNABLE);
      _thread.postExitCommand();
      _thread.enter();
      assertEquals("low", l.get());
    }
    {
      Future<Object> l = actor.lowPriority(NULL_RUNNABLE);
      Future<Object> p = actor.patient();
      _thread.postExitCommand();
      _thread.enter();
      assertEquals("low", l.get());
      assertEquals("patient", p.get());
    }
    {
      Future<Object> l = actor.lowPriority(NULL_RUNNABLE);
      Future<Object> p = actor.patient();
      Future<Object> q = actor.patient();
      _thread.postExitCommand();
      _thread.enter();
      assertEquals("low", l.get());
      assertEquals("patient", p.get());
      assertEquals("patient", q.get());
    }
    {
      Future<Object> l = actor.lowPriority(NULL_RUNNABLE);
      Future<Object> p = actor.patient();
      Future<Object> n = actor.normalPriority();
      _thread.postExitCommand();
      _thread.enter();
      catchThrowableOfType(l::get, CancellationException.class);
      assertEquals("patient", p.get());
      assertEquals("normal", n.get());
    }
    {
      Future<Object> l = actor.lowPriority(NULL_RUNNABLE);
      Future<Object> n = actor.normalPriority();
      Future<Object> p = actor.patient();
      _thread.postExitCommand();
      _thread.enter();
      catchThrowableOfType(l::get, CancellationException.class);
      assertEquals("normal", n.get());
      assertEquals("patient", p.get());
    }
  }

  @Test
  public void patientMessageDoesNotTriggerCancel() throws Exception {
    MyActor actor = replayAndSpawn();
    {
      Future<?>[] n = {null};
      Future<Object> l = actor.lowPriority(() -> n[0] = actor.patient());
      _thread.postExitCommand();
      _thread.enter();
      assertEquals("low", l.get());
      assertEquals("patient", n[0].get());
    }
    {
      Future<?>[] n = {null};
      Future<String> l1 = actor.lowPriorityBatch("woo", () -> n[0] = actor.patient());
      Future<String> l2 = actor.lowPriorityBatch("yay", NULL_RUNNABLE);
      _thread.postExitCommand();
      _thread.enter();
      assertEquals("wooyay", l1.get());
      assertEquals("wooyay", l2.get());
      assertEquals("patient", n[0].get());
    }
    {
      Future<?>[] n = {null};
      Future<String> l1 = actor.lowPriorityBatch("woo", NULL_RUNNABLE);
      Future<String> l2 = actor.lowPriorityBatch("yay", () -> n[0] = actor.patient());
      _thread.postExitCommand();
      _thread.enter();
      assertEquals("wooyay", l1.get());
      assertEquals("wooyay", l2.get());
      assertEquals("patient", n[0].get());
    }
    {
      Future<?>[] n = {null, null};
      Future<Object> l = actor.lowPriority(() -> {
        n[0] = actor.patient();
        n[1] = actor.normalPriority(); // Must add self to queue after patient.
      });
      _thread.postExitCommand();
      _thread.enter();
      catchThrowableOfType(l::get, CancellationException.class);
      assertEquals("patient", n[0].get());
      assertEquals("normal", n[1].get());
    }
  }
}
