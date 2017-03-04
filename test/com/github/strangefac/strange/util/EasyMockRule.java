package com.github.strangefac.strange.util;

import static com.github.strangefac.strange.util.Standard.also;
import java.util.ArrayList;
import org.easymock.EasyMock;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class EasyMockRule implements TestRule {
  private final ArrayList<Object> _mocks = new ArrayList<>();

  public <T> T createMock(Class<T> toMock) {
    return also(EasyMock.createMock(toMock), _mocks::add);
  }

  public void replay() {
    EasyMock.replay(_mocks.toArray());
  }

  public Statement apply(Statement base, Description description) {
    return new Statement() {
      public void evaluate() throws Throwable {
        base.evaluate();
        EasyMock.verify(_mocks.toArray());
      }
    };
  }
}
