package com.github.strangefac.strange.util;

import static com.github.strangefac.strange.util.Standard.also;
import org.junit.rules.TestName;

public class SlowTests {
  public static boolean slowTestsEnabled(TestName testName) {
    return also(Boolean.getBoolean("slowTestsEnabled"), it -> {
      System.err.println((it ? "Running" : "Not running") + " a slow test: " + testName.getMethodName());
    });
  }

  private SlowTests() {
    // No.
  }
}
