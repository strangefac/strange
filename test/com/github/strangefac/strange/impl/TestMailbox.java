package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.impl.InvocationInfo.REJECTING_FORMAT;
import static com.github.strangefac.strange.impl.Mailbox.DEAD_ACTOR_MESSAGE;
import static com.github.strangefac.strange.impl.TestSFutureImpl.keyEq;
import static com.github.strangefac.strange.util.Standard.also;
import static com.github.strangefac.strange.util.Standard.repeat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.Batch;
import com.github.strangefac.strange.DeadActorException;
import com.github.strangefac.strange.DwellInfo;
import com.github.strangefac.strange.MailboxElement;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.function.VoidCheckedException;
import com.github.strangefac.strange.impl.Mailbox;
import com.github.strangefac.strange.impl.SignatureInfo;
import com.github.strangefac.strange.impl.Mailbox.Invocations;
import com.github.strangefac.strange.impl.StrangeImpl.TargetClass;
import com.github.strangefac.strange.util.EasyMockRule;

public class TestMailbox implements ActorTarget<TestMailbox.TestMailboxActor> {
  public interface TestMailboxActor extends Actor {
    @Batch
    SFuture<String, VoidCheckedException> batched(String part);

    SFuture<Void, InterruptedException> countDownAndSleep(CountDownLatch taskRunning);
  }

  @Rule
  public final EasyMockRule _mocks = new EasyMockRule();
  private final Logger _log = _mocks.createMock(Logger.class);

  private static List<MailboxElement> jumpQueue(Collection<? extends MailboxElement> array, MailboxElement element) {
    return also(new Invocations<>(), it -> {
      it.addAll(array);
      it.jumpQueue(element);
    });
  }

  @Test
  public void jumpQueueWorks() {
    MailboxElement i1 = also(_mocks.createMock(MailboxElement.class), it -> expect(it.jumpQueue()).andReturn(false).anyTimes());
    MailboxElement i2 = _mocks.createMock(MailboxElement.class);
    MailboxElement jq1 = also(_mocks.createMock(MailboxElement.class), it -> expect(it.jumpQueue()).andReturn(true).anyTimes());
    MailboxElement jq2 = _mocks.createMock(MailboxElement.class);
    _mocks.replay();
    // Start empty:
    assertEquals(Arrays.asList(jq1), jumpQueue(Collections.emptySet(), jq1));
    assertEquals(Arrays.asList(jq1, jq2), jumpQueue(Arrays.asList(jq1), jq2));
    // Start with ordinary item:
    assertEquals(Arrays.asList(jq1, i1), jumpQueue(Arrays.asList(i1), jq1));
    assertEquals(Arrays.asList(jq1, jq2, i1), jumpQueue(Arrays.asList(jq1, i1), jq2));
    // Start with 2 ordinary items:
    assertEquals(Arrays.asList(jq1, i1, i2), jumpQueue(Arrays.asList(i1, i2), jq1));
    assertEquals(Arrays.asList(jq1, jq2, i1, i2), jumpQueue(Arrays.asList(jq1, i1, i2), jq2));
    // Not possible in practice:
    assertEquals(Arrays.asList(jq2, i1, jq1), jumpQueue(Arrays.asList(i1, jq1), jq2));
  }

  @Test
  public void maxBatchSizeWorks() throws DeadActorException {
    _mocks.replay();
    SignatureInfo s = new SignatureInfo(true, false, false, false, null, null, false, "batched");
    Mailbox q = new Mailbox(false);
    repeat(4, () -> q.add(null, s));
    assertEquals(1, q.load(_log, null, null, 1).batchSize());
    assertEquals(3, q.size());
    q.unload();
    assertEquals(2, q.load(_log, null, null, 2).batchSize());
    assertEquals(1, q.size());
    q.unload();
    assertEquals(1, q.load(_log, null, null, 2).batchSize());
    assertEquals(0, q.size());
  }

  public String batched(String[] parts) {
    return also(new StringBuilder(), sb -> {
      for (String part : parts)
        sb.append(part);
    }).toString();
  }

  @Test
  public void curtailedBatchesDoNotShareWrapper() throws Throwable {
    _mocks.replay();
    SignatureInfo s = new SignatureInfo(TestMailboxActor.class.getMethod("batched", String.class));
    Mailbox q = new Mailbox(false);
    SFuture<Object, Throwable> w1 = q.add(null, s, "a");
    SFuture<Object, Throwable> w2 = q.add(null, s, "b");
    SFuture<Object, Throwable> w3 = q.add(null, s, "c");
    SFuture<Object, Throwable> w4 = q.add(null, s, "d");
    q.load(_log, new TargetClass<>(TestMailbox.class), this, 2).run();
    q.unload();
    q.load(_log, new TargetClass<>(TestMailbox.class), this, 2).run();
    assertEquals("ab", w1.sync());
    assertEquals("ab", w2.sync());
    assertEquals("cd", w3.sync());
    assertEquals("cd", w4.sync());
  }

  @Test
  public void stateAfterKill() {
    _mocks.replay();
    Mailbox m = new Mailbox(false);
    m.kill(null);
    catchThrowableOfType(() -> m.add(null, null), DeadActorException.class);
    catchThrowableOfType(() -> m.load(_log, null, null, 1), DeadActorException.class);
    assertEquals(DEAD_ACTOR_MESSAGE, catchThrowableOfType(m::size, IllegalStateException.class).getMessage());
    catchThrowableOfType(m::unload, IllegalStateException.class);
    DwellInfo dwellInfo = m.getDwellInfo();
    // Observe asymmetry with size() method, trivial stats are suitable for monitoring:
    assertEquals(0, dwellInfo.mailboxSize());
    assertEquals(0, dwellInfo.dwellNanos(System.nanoTime()));
  }

  public void countDownAndSleep(CountDownLatch taskRunning) throws InterruptedException {
    taskRunning.countDown();
    while (true)
      Thread.sleep(1000);
  }

  @Test
  public void killInterruptsCurrentTask() throws Throwable {
    _mocks.replay();
    Mailbox m = new Mailbox(false);
    CountDownLatch taskRunning = new CountDownLatch(1);
    SFuture<Object, Throwable> f = m.add(null, new SignatureInfo(TestMailboxActor.class.getMethod("countDownAndSleep", CountDownLatch.class)), taskRunning);
    Thread thread = new Thread(() -> {
      try {
        m.load(_log, new TargetClass<>(TestMailbox.class), TestMailbox.this, 1).run(); // It's a FutureTask so run should complete normally.
      } catch (DeadActorException e) {
        throw new RuntimeException(e);
      }
    });
    thread.start();
    taskRunning.await();
    m.kill(null);
    thread.join();
    catchThrowableOfType(f::sync, CancellationException.class);
  }

  public void shouldNotRun() {
    fail("Should not run.");
  }

  @Test
  public void killCancelsPendingTasks() throws DeadActorException {
    SignatureInfo s = new SignatureInfo(false, false, false, false, null, null, false, "shouldNotRun");
    Logger log = _mocks.createMock(Logger.class);
    log.debug(eq(REJECTING_FORMAT), keyEq(s.key()));
    log.debug(eq(REJECTING_FORMAT), keyEq(s.key()));
    _mocks.replay();
    Mailbox m = new Mailbox(false);
    SFuture<Object, Throwable> f = m.add(null, s);
    SFuture<Object, Throwable> g = m.add(null, s);
    m.kill(log);
    assertTrue(f.isCancelled());
    assertTrue(g.isCancelled());
  }

  public void init(TestMailboxActor actor) {
    // Do nothing.
  }
}
