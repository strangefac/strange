package com.github.strangefac.strange.impl;

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;
import com.github.strangefac.strange.impl.ManualThreadCollection;

public class TestManualThreadCollection {
  @Test
  public void leakedInterruptIsCleared() throws InterruptedException {
    boolean[] interrupted = {false};
    ManualThreadCollection mtc = new ManualThreadCollection();
    mtc.execute(() -> {
      Thread.currentThread().interrupt();
      interrupted[0] = true;
    });
    mtc.postExitCommand();
    mtc.enter(); // Should not throw InterruptedException.
    assertTrue(interrupted[0]);
  }

  @Test
  public void interruptedStateNotPropagatedToFirstTask() {
    ManualThreadCollection mtc = new ManualThreadCollection();
    mtc.execute(() -> fail("Should not have been attempted."));
    Thread.currentThread().interrupt();
    catchThrowableOfType(mtc::enter, InterruptedException.class);
  }
}
