package com.github.strangefac.strange.impl;

import static com.github.strangefac.strange.impl.ActorInvocationHandler.INIT_SIGNATURE_INFO;
import static com.github.strangefac.strange.impl.SignatureLookup.SIGNATURE_INFOS;
import static com.github.strangefac.strange.impl.TargetClassLookup.TARGET_CLASSES;
import static com.github.strangefac.strange.util.UncheckedCast.uncheckedCast;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.slf4j.ILoggerFactory;
import com.github.strangefac.strange.Actor;
import com.github.strangefac.strange.ActorTarget;
import com.github.strangefac.strange.ActorTargetThrows;
import com.github.strangefac.strange.AllActors;
import com.github.strangefac.strange.PrivateActor;
import com.github.strangefac.strange.Spawned;
import com.github.strangefac.strange.Strange;
import com.github.strangefac.strange.ThreadCollection;
import com.github.strangefac.strange.ThreadCollectionType;
import com.github.strangefac.strange.impl.SignatureInfo.SignatureKey;
import com.github.strangefac.strange.util.ComponentSource;
import com.github.strangefac.strange.util.TypedArrayList;
import com.github.strangefac.strange.util.UncheckedCast;
import gnu.trove.map.hash.THashMap;

public class StrangeImpl implements Strange {
  // Runtime because I can't imagine why you'd ever want to catch it.
  private static class NotAnActorException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private NotAnActorException(String message) {
      super(message);
    }
  }

  private static class BadActorException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private BadActorException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static List<ParameterizedType> getPathToActorTargetThrows(Class<?> clazz) {
    List<Type> genericTypes = new TypedArrayList<>(Type.class);
    Type superclass = clazz.getGenericSuperclass();
    if (null != superclass) genericTypes.add(superclass);
    genericTypes.addAll(Arrays.asList(clazz.getGenericInterfaces()));
    for (Type t : genericTypes) {
      boolean parameterized = t instanceof ParameterizedType;
      Class<?> c = (Class<?>) (parameterized ? ((ParameterizedType) t).getRawType() : t);
      if (ActorTargetThrows.class.isAssignableFrom(c)) {
        List<ParameterizedType> path = getPathToActorTargetThrows(c);
        if (parameterized) path.add((ParameterizedType) t);
        return path;
      }
    }
    return new TypedArrayList<>(ParameterizedType.class);
  }

  /**
   * @param targetImpl Any actor target impl.
   * @return The type argument to {@link ActorTargetThrows} (or {@link ActorTarget}).
   */
  public static <A extends Actor> Class<A> getActorInterface(Class<? extends ActorTargetThrows<A, ?>> targetImpl) throws NotAnActorException {
    ToIntFunction<? super List<TypeVariable<?>>> getIndex = x -> 0;
    for (ParameterizedType pt : getPathToActorTargetThrows(targetImpl)) {
      Type arg = pt.getActualTypeArguments()[getIndex.applyAsInt(Arrays.asList(((GenericDeclaration) pt.getRawType()).getTypeParameters()))];
      if (Class.class == arg.getClass()) return uncheckedCast(arg);
      if (arg instanceof ParameterizedType) return uncheckedCast(((ParameterizedType) arg).getRawType());
      getIndex = typeParameters -> typeParameters.indexOf(arg);
    }
    throw new NotAnActorException(targetImpl.toString());
  }

  static class TargetClass<A extends Actor> {
    private final Class<A> _actorInterface;
    private final THashMap<SignatureKey, Method> _methods;

    /** Don't call directly, use the {@link TargetClassLookup}. */
    TargetClass(Class<? extends ActorTargetThrows<A, ?>> targetImpl) throws NotAnActorException, BadActorException {
      _actorInterface = getActorInterface(targetImpl);
      _methods = new THashMap<SignatureKey, Method>() {
        private void add(SignatureInfo signatureInfo) throws BadActorException {
          try {
            put(signatureInfo.key(), signatureInfo.resolve(targetImpl));
          } catch (NoSuchMethodException e) {
            throw new BadActorException("Failed to resolve an actor method against the target class:", e);
          }
        }

        {
          add(INIT_SIGNATURE_INFO);
          getActorMethodsImpl(_actorInterface, method -> {
            SignatureInfo signatureInfo = SIGNATURE_INFOS.getOrCreate(method);
            if (signatureInfo.postable()) add(signatureInfo);
          });
        }
      };
    }

    Class<A> actorInterface() {
      return _actorInterface;
    }

    Method resolve(SignatureKey signatureKey) {
      return _methods.get(signatureKey);
    }
  }

  public static <A extends Actor> void getActorMethods(Class<? extends ActorTargetThrows<A, ?>> targetImpl, Consumer<? super Method> consumer) {
    getActorMethodsImpl(getActorInterface(targetImpl), consumer);
  }

  private static <A extends Actor> void getActorMethodsImpl(Class<A> actorInterface, Consumer<? super Method> consumer) {
    for (Method method : actorInterface.getMethods()) {
      if (Actor.class != method.getDeclaringClass()) consumer.accept(method);
    }
  }

  private final ComponentSource _componentSource;
  private final ILoggerFactory _loggerFactory;
  private final AllActors _allActors;

  public StrangeImpl(ComponentSource componentSource, ILoggerFactory loggerFactory, AllActors allActors) {
    _componentSource = componentSource;
    _loggerFactory = loggerFactory;
    _allActors = allActors;
  }

  ComponentSource componentSource() {
    return _componentSource;
  }

  /**
   * Low-level actor instance creator.
   * 
   * @see Proxy#newProxyInstance(ClassLoader, Class[], InvocationHandler)
   */
  public static class ActorFactory<A extends Actor> {
    private final Class<? extends A> _impl;

    public ActorFactory(Class<A> actorInterface) {
      // Assume PrivateActor is resolvable via the actorInterface classLoader:
      _impl = UncheckedCast.<Class<?>, Class<? extends A>> uncheckedCast(Proxy.getProxyClass(actorInterface.getClassLoader(), actorInterface, PrivateActor.class));
    }

    public Class<? extends A> impl() {
      return _impl;
    }

    public A create(InvocationHandler invocationHandler) {
      try {
        return _impl.getConstructor(InvocationHandler.class).newInstance(invocationHandler);
      } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
  }

  <A extends Actor, E extends Throwable> Spawned<A, E> spawnImpl(TargetClass<A> targetClass, ActorTargetThrows<A, ? extends E> target, ThreadCollection threadCollection, Function<? super InvocationHandler, ? extends A> invocationHandlerToActor) {
    ActorInvocationHandler<A, E> invocationHandler = new ActorInvocationHandler<>(_loggerFactory.getLogger(target.getClass().getName()), targetClass, target, threadCollection);
    A actor = invocationHandlerToActor.apply(invocationHandler);
    _allActors.purgeAndAdd(actor); // Do this before posting init so that all invocations can see their actor in the array.
    return new SpawnedImpl<>(actor, invocationHandler.init(actor));
  }

  static Class<? extends ThreadCollection> getThreadCollectionTypeOrFail(AnnotatedElement targetImpl) throws IllegalArgumentException {
    ThreadCollectionType a = targetImpl.getAnnotation(ThreadCollectionType.class);
    if (null == a) throw new IllegalArgumentException(String.format("%s does not have a ThreadCollectionType annotation.", targetImpl));
    return a.value();
  }

  private final ConcurrentHashMap<Class<? extends ThreadCollection>, ThreadCollection> _threadCollections = new ConcurrentHashMap<>();

  private ThreadCollection getThreadCollection(Class<? extends ThreadCollection> type) {
    return _threadCollections.computeIfAbsent(type, _componentSource::getComponent); // No race provided getComponent is idempotent.
  }

  public <A extends Actor, E extends Throwable> Spawned<A, E> spawn(ActorTargetThrows<A, ? extends E> target) throws IllegalArgumentException {
    @SuppressWarnings("rawtypes")
    Class<? extends ActorTargetThrows<A, ? extends E>> targetImpl = UncheckedCast.<Class<? extends ActorTargetThrows>, Class<? extends ActorTargetThrows<A, ? extends E>>> uncheckedCast(target.getClass());
    Class<? extends ThreadCollection> threadCollectionType = getThreadCollectionTypeOrFail(targetImpl);
    TargetClass<A> targetClass = TARGET_CLASSES.getOrCreate(targetImpl);
    return spawnImpl(targetClass, target, getThreadCollection(threadCollectionType), invocationHandler -> new ActorFactory<>(targetClass._actorInterface).create(invocationHandler));
  }
}
