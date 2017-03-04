package com.github.strangefac.strange.pool;

import static org.junit.Assert.assertEquals;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.Test;
import com.github.strangefac.strange.pool.PoolThread;
import com.github.strangefac.strange.pool.PoolThreadOwner;

public class TestPoolThread {
  @Test(timeout = 5000)
  public void interruptedExceptionCausesExitWithInterruptedStatusSet() throws InterruptedException {
    BlockingQueue<Object> q = new LinkedBlockingQueue<>();
    PoolThreadOwner owner = new PoolThreadOwner() {
      public void promote(PoolThread thread) {
        q.add("promote");
      }

      public void discard(PoolThread thread) {
        q.add(thread.isInterrupted()); // Need to capture the status while it's still alive.
      }
    };
    PoolThread thread = new PoolThread("woo", () -> {
      q.add("run");
      Thread.currentThread().interrupt();
    }, owner, 1000);
    thread.start();
    assertEquals("run", q.take());
    assertEquals("promote", q.take());
    assertEquals(true, q.take());
    Thread.sleep(100); // Give any bad events a chance to happen.
    assertEquals(true, q.isEmpty());
  }
}
