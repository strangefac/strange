package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.util.Standard.also;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.ThreadCollection;
import com.github.strangefac.strange.function.VoidCheckedException;
import com.github.strangefac.strange.impl.ActorInvocationHandler;
import com.github.strangefac.strange.impl.ManualThreadCollection;
import com.github.strangefac.strange.impl.SignatureInfo;
import com.github.strangefac.strange.impl.ActorInvocationHandler.BadActorMethodException;
import com.github.strangefac.strange.impl.SignatureInfo.SignatureKey;
import com.github.strangefac.strange.impl.StrangeImpl.TargetClass;
import gnu.trove.set.hash.THashSet;

public class TestActorInvocationHandler {
  public interface Legacy {
    Object legacy();

    Object legacy2();

    Object legacy3();
  }

  public interface BadA extends Actor {
    SFuture<Void, VoidCheckedException> a();

    SFuture<Void, VoidCheckedException> legacy2();
  }

  public interface FixedA extends Actor {
    SFuture<Void, VoidCheckedException> a();
  }

  public interface BadB extends BadA, Legacy {
    SFuture<Void, VoidCheckedException> b();

    SFuture<Void, VoidCheckedException> legacy3();
  }

  public interface NotQuiteFixedB extends FixedA, Legacy { // Same as BadB but extends FixedA instead of BadA.
    SFuture<Void, VoidCheckedException> b();

    SFuture<Void, VoidCheckedException> legacy3();
  }

  public interface FixedB extends FixedA, Legacy { // Same as NotQuiteFixedB but without legacy3 override.
    SFuture<Void, VoidCheckedException> b();
  }

  public interface LegacyBadA extends Legacy, BadA {
    // Nothing else.
  }

  public static class BadBTarget implements ActorTarget<BadB> {
    public void init(BadB actor) {
    }

    public void legacy() {
    }

    public void legacy2() {
    }

    public void legacy3() {
    }

    public void a() {
    }

    public void b() {
    }
  }

  public static class NotQuiteFixedBTarget implements ActorTarget<NotQuiteFixedB> {
    public void init(NotQuiteFixedB actor) {
    }

    public void legacy() {
    }

    public void legacy2() {
    }

    public void legacy3() {
    }

    public void a() {
    }

    public void b() {
    }
  }

  public static class LegacyBadATarget implements ActorTarget<LegacyBadA> {
    public void init(LegacyBadA actor) {
    }

    public void legacy() {
    }

    public void legacy2() {
    }

    public void legacy3() {
    }

    public void a() {
    }
  }

  public static class FixedBTarget implements ActorTarget<FixedB> {
    public void init(FixedB actor) {
    }

    public void legacy() {
    }

    public void legacy2() {
    }

    public void legacy3() {
    }

    public void a() {
    }

    public void b() {
    }
  }

  public static class PlainTarget implements ActorTarget<Actor> {
    public void init(Actor actor) {
    }
  }

  private final ThreadCollection _threadCollection = new ManualThreadCollection();

  @Test
  public void badMethods() {
    also(catchThrowableOfType(() -> new ActorInvocationHandler<BadB, VoidCheckedException>(null, new TargetClass<>(BadBTarget.class), null, _threadCollection), BadActorMethodException.class), e -> {
      // Reflection doesn't claim any iteration order, so it could be either:
      assertTrue(also(new THashSet<>(), it -> {
        it.add(new BadActorMethodException(true, Legacy.class, new SignatureInfo("legacy2")).getMessage());
        it.add(new BadActorMethodException(true, Legacy.class, new SignatureInfo("legacy3")).getMessage());
      }).contains(e.getMessage()));
    });
    also(catchThrowableOfType(() -> new ActorInvocationHandler<NotQuiteFixedB, VoidCheckedException>(null, new TargetClass<>(NotQuiteFixedBTarget.class), null, _threadCollection), BadActorMethodException.class), e -> {
      assertEquals(new BadActorMethodException(true, Legacy.class, new SignatureInfo("legacy3")).getMessage(), e.getMessage());
    });
    also(catchThrowableOfType(() -> new ActorInvocationHandler<LegacyBadA, VoidCheckedException>(null, new TargetClass<>(LegacyBadATarget.class), null, _threadCollection), BadActorMethodException.class), e -> {
      assertEquals(new BadActorMethodException(false, BadA.class, new SignatureInfo("legacy2")).getMessage(), e.getMessage());
    });
  }

  @Test
  public void legacyMethodsWorks() {
    Logger log = LoggerFactory.getLogger(TestActorInvocationHandler.class);
    THashSet<SignatureKey> legacySignatureKeys = new THashSet<>(new ActorInvocationHandler<FixedB, VoidCheckedException>(log, new TargetClass<>(FixedBTarget.class), null, _threadCollection).legacySignatureKeysForTestingOnly());
    assertTrue(legacySignatureKeys.remove(new SignatureKey("legacy")));
    assertTrue(legacySignatureKeys.remove(new SignatureKey("legacy2")));
    assertTrue(legacySignatureKeys.remove(new SignatureKey("legacy3")));
    assertTrue(legacySignatureKeys.isEmpty());
  }

  @Test
  public void legacyMethodsEfficiency() {
    assertSame(Collections.emptySet(), new ActorInvocationHandler<Actor, VoidCheckedException>(null, new TargetClass<>(PlainTarget.class), null, _threadCollection).legacySignatureKeysForTestingOnly());
  }
}
