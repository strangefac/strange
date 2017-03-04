package com.github.strangefac.strange;

/**
 * If your target implements this interface, {@link #afterTask()} will be called after every method (including posted tasks) whatever the outcome (including
 * suspension). The use-case is to clear a reusable list of background futures.
 */
public interface AfterTask {
  void afterTask() throws Exception;
}
