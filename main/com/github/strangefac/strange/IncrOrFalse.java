package com.github.strangefac.strange;

public interface IncrOrFalse {
  IncrOrFalse NULL_DRAIN = () -> false;

  /**
   * Attempts to configure this drain to process one additional item from the mailbox. Obviously the item must already be in the mailbox.
   * 
   * @return true if this will process an additional item, false if this has permanently given up.
   */
  boolean incrOrFalse();
}
