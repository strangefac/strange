package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.IncrOrFalse.NULL_DRAIN;
import static com.github.strangefac.strange.impl.Invocation.PRIVATE_POST_TASK_SIGNATURE_KEY;
import static com.github.strangefac.strange.impl.Mailbox.DEAD_ACTOR_MESSAGE;
import static com.github.strangefac.strange.impl.SignatureLookup.SIGNATURE_INFOS;
import static com.github.strangefac.strange.util.Standard.also;
import static com.github.strangefac.strange.util.StrangeUtils.notNull;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTargetThrows;
import com.github.strangefac.strange.AfterTask;
import com.github.strangefac.strange.DeadActorException;
import com.github.strangefac.strange.IncrOrFalse;
import com.github.strangefac.strange.PrivateActor;
import com.github.strangefac.strange.SFuture;
import com.github.strangefac.strange.Syncable;
import com.github.strangefac.strange.ThreadCollection;
import com.github.strangefac.strange.impl.SignatureInfo.SignatureKey;
import com.github.strangefac.strange.impl.StrangeImpl.TargetClass;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

class ActorInvocationHandler<A extends Actor, E extends Throwable> implements InvocationHandler {
  private interface FastInvoke {
    Object invoke(ActorInvocationHandler<?, ?> handler, Object proxy, Object[] args);
  }

  static class BadActorMethodException extends RuntimeException { // Runtime is fine, this is not for catching.
    private static final long serialVersionUID = 1L;

    BadActorMethodException(boolean legacy, Class<?> declaringClass, SignatureInfo signatureInfo) {
      super(String.format("Method is %s elsewhere but %s here: %s#%s", legacy ? "non-legacy" : "legacy", legacy ? "legacy" : "non-legacy", declaringClass.getName(), signatureInfo));
    }
  }

  private static final String LEGACY_SIGNATURES_FORMAT = "{} legacy signatures: {}";
  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  static final SignatureInfo INIT_SIGNATURE_INFO = new SignatureInfo("init", Actor.class);

  private static void allDeclaredMethods(Class<?> someInterface, Consumer<? super Method> consumer) {
    for (Method method : someInterface.getDeclaredMethods()) // Not ordered.
      consumer.accept(method);
    for (Class<?> superinterface : someInterface.getInterfaces()) // Ordered.
      allDeclaredMethods(superinterface, consumer);
  }

  private static final THashMap<SignatureKey, FastInvoke> FAST_INVOKES = also(new THashMap<>(), it -> {
    it.put(new SignatureKey("actorInterface"), (handler, proxy, args) -> handler._targetClass.actorInterface());
    it.put(new SignatureKey("mailboxSize"), (handler, proxy, args) -> handler._mailbox.size());
    it.put(new SignatureKey("getDwellInfo"), (handler, proxy, args) -> handler._mailbox.getDwellInfo());
    it.put(new SignatureKey("toString"), (handler, proxy, args) -> {
      return handler._targetClass.actorInterface().getName() + "Impl@" + Integer.toHexString(proxy.hashCode()); // Simulate the default impl.
    });
    it.put(new SignatureKey("equals", Object.class), (handler, proxy, args) -> proxy == args[0]);
    it.put(new SignatureKey("hashCode"), (handler, proxy, args) -> System.identityHashCode(proxy));
    it.put(new SignatureKey("kill"), (handler, proxy, args) -> {
      handler._mailbox.kill(handler._log);
      return null;
    });
  });
  private final Mailbox _mailbox;
  private final Set<SignatureKey> _legacySignatureKeys;
  private final Logger _log;
  private final TargetClass<A> _targetClass;
  private final ActorTargetThrows<A, ? extends E> _target;
  private final ThreadCollection _threadCollection;

  /**
   * @param target The underlying object.
   * @param threadCollection The collection of threads that are acceptable for invoking methods on the target. Most actors will simply share a thread pool,
   * swing actors must all use the {@link SwingThreadCollection}.
   */
  ActorInvocationHandler(Logger log, TargetClass<A> targetClass, ActorTargetThrows<A, ? extends E> target, ThreadCollection threadCollection) {
    _mailbox = new Mailbox(target instanceof AfterTask);
    _legacySignatureKeys = getLegacySignatureKeys(log, targetClass.actorInterface());
    if (!_legacySignatureKeys.isEmpty()) {
      // Observe non-deterministic order, but good enough for debug logging:
      log.debug(LEGACY_SIGNATURES_FORMAT, targetClass.actorInterface().getName(), _legacySignatureKeys);
    }
    _log = log;
    _targetClass = targetClass;
    _target = target;
    _threadCollection = notNull("threadCollection", threadCollection);
  }

  private static Set<SignatureKey> getLegacySignatureKeys(Logger log, Class<?> actorInterface) throws BadActorMethodException {
    THashMap<SignatureKey, Boolean> signatureKeyToLegacy = new THashMap<>();
    int[] legacyCount = {0};
    allDeclaredMethods(actorInterface, method -> {
      SignatureInfo signatureInfo = SIGNATURE_INFOS.getOrCreate(method);
      if (!FAST_INVOKES.containsKey(signatureInfo.key())) {
        boolean legacy = !Actor.class.isAssignableFrom(method.getDeclaringClass());
        Boolean currentOrNull = signatureKeyToLegacy.get(signatureInfo.key());
        if (null == currentOrNull) {
          signatureKeyToLegacy.put(signatureInfo.key(), legacy);
          if (legacy) {
            ++legacyCount[0];
            Class<?> returnType = method.getReturnType();
            if (Future.class.isAssignableFrom(returnType) || Syncable.class.isAssignableFrom(returnType)) {
              log.warn("Legacy method {} has Future-like return type: {}", signatureInfo, returnType.getName());
            }
          }
        } else if (currentOrNull != legacy) {
          throw new BadActorMethodException(legacy, method.getDeclaringClass(), signatureInfo);
        }
      }
    });
    if (0 == legacyCount[0]) {
      return Collections.emptySet();
    } else {
      THashSet<SignatureKey> legacySignatureKeys = new THashSet<>(legacyCount[0]);
      signatureKeyToLegacy.forEachEntry((signatureKey, legacy) -> {
        if (legacy) legacySignatureKeys.add(signatureKey);
        return true;
      });
      return legacySignatureKeys;
    }
  }

  Set<SignatureKey> legacySignatureKeysForTestingOnly() {
    return _legacySignatureKeys;
  }

  SFuture<Void, E> init(A actor) {
    try {
      return post((PrivateActor) actor, INIT_SIGNATURE_INFO, actor);
    } catch (DeadActorException e) {
      throw new RejectedExecutionException(DEAD_ACTOR_MESSAGE); // The interface method doesn't allow DAE.
    }
  }

  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // XXX: Copy args into pooled array to expedite its gc?
    if (null == args) args = EMPTY_OBJECT_ARRAY;
    SignatureInfo signatureInfo = SIGNATURE_INFOS.getOrCreate(method);
    FastInvoke fastInvoke = FAST_INVOKES.get(signatureInfo.key());
    if (null != fastInvoke) return fastInvoke.invoke(this, proxy, args);
    Supplier<?> propertyAccessorOrNull = signatureInfo.propertyAccessorOrNull(_target);
    if (null != propertyAccessorOrNull) return propertyAccessorOrNull.get(); // TODO LATER: Cache the accessor, or make them up-front.
    SFuture<?, ?> wrapperOrNull;
    try {
      wrapperOrNull = post((PrivateActor) proxy, signatureInfo, args);
    } catch (DeadActorException e) {
      if (PRIVATE_POST_TASK_SIGNATURE_KEY.equals(signatureInfo.key())) {
        throw e;
      } else {
        throw new RejectedExecutionException(DEAD_ACTOR_MESSAGE); // The interface method doesn't allow DAE.
      }
    }
    if (null == wrapperOrNull || !_legacySignatureKeys.contains(signatureInfo.key())) return wrapperOrNull;
    // We recommend in the Actor interface description that the actor method can throw the exceptions that SFuture#sync can throw:
    // TODO LATER: Warn if the actor method is not capable of throwing the exceptions.
    return wrapperOrNull.sync();
  }

  private IncrOrFalse _currentDrain;
  {
    synchronized (this) {
      _currentDrain = NULL_DRAIN;
    }
  }

  private <V, F extends Throwable> SFuture<V, F> post(PrivateActor actor, SignatureInfo signatureInfo, Object... args) throws DeadActorException {
    SFuture<V, F> wrapperOrNull = _mailbox.add(actor, signatureInfo, args);
    synchronized (this) {
      if (!_currentDrain.incrOrFalse()) {
        Drain drain = new Drain(_mailbox, _log, _targetClass, _target);
        _threadCollection.execute(drain);
        _currentDrain = drain;
      }
    }
    return wrapperOrNull;
  }
}
