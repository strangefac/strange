## Description

[Erlang Processes](http://www.erlang.org/doc/getting_started/conc_prog.html) for Java, inspired by [Scala Actors](http://www.scala-lang.org/node/242) (but maybe more similar to [Akka Actors](http://doc.akka.io/docs/akka/snapshot/java/typed-actors.html)).

### Goals

* Plain old Java implementation (no bytecode manipulation)
  * Bytecode manipulation may come later as an optional extra for performance only
* There should be one obvious way to do things (i.e. not Akka)
* Easy to migrate legacy code
  * And integrate with non-strange code
* Elegance

## Usage

### Example

```java
// ThreadCollectionType specifies which threads are allowed to call methods on Example, the unbounded ThreadPoolThreadCollection is suitable for most actors:
@ThreadCollectionType(ThreadPoolThreadCollection.class)
public class Example implements ActorTarget<Example.ExampleActor> {

  public interface ExampleActor extends Actor { // Doesn't have to be nested, but it's convenient to be able to see the methods.

    // Signature must be consistent with greetMe on Example, here VoidCheckedException means it throws no checked exceptions:
    SFuture<String, VoidCheckedException> greetMe();

  }

  private final String _label;

  private final ExampleActor _delegateOrNull;

  public Example(String label, ExampleActor delegateOrNull) {
    _label = label;
    _delegateOrNull = delegateOrNull;
  }

  public void init(ExampleActor actor) {
    // A more complicated example can save actor to a field (conventionally called _self) so it can call back into itself via the mailbox.
  }

  // Signature must be consistent with greetMe on ExampleActor, note Suspension and its subclasses are always allowed:
  public String greetMe() throws DelegatingSuspension {
    System.err.println("Enter: " + _label);
    try {
      if (null != _delegateOrNull) {
        // Release this actor immediately, and magically propagate delegate's result (when it has one) to our future:
        throw new DelegatingSuspension(_delegateOrNull.greetMe());
      }
      else {
        return "Hello, world!";
      }
    }
    finally {
      System.err.println("Exit: " + _label);
    }
  }

  public static void main(String[] args) throws InterruptedException {
    // Try setting minCreatePeriod to 0 to see inner running concurrently with outer:
    CustomThreadPoolThreadCollection pool = new CustomThreadPoolThreadCollection(10000, 100);
    StrangeImpl strange = new StrangeImpl(new DumbestContainer(pool), LoggerFactory.getILoggerFactory(), new AllActorsImpl());
    ExampleActor inner = strange.spawn(new Example("inner", null)).sync();
    ExampleActor outer = strange.spawn(new Example("outer", inner)).sync();
    System.out.println(outer.greetMe().sync()); // Can't suspend in non-actor code, have to use sync.
    pool.dispose();
  }

  private static class DumbestContainer implements ComponentSource { // Replace with your favourite dependency injection container.

    private final Object[] _objs;

    private DumbestContainer(Object... objs) {
      _objs = objs;
    }

    @SuppressWarnings("unchecked")
    public <T> T getComponent(Class<T> componentType) {
      for (Object obj : _objs) {
        if (componentType.isInstance(obj)) {
          return (T) obj;
        }
      }
      throw new NoSuchElementException(componentType.toString());
    }

  }

}
```

### Memory efficiency

* Thanks to safe publication of args when posting an actor call, you can build a data structure in one actor and pass it to another (without keeping any references) without additional synchronization
  * Similarly for return values
* Large temporary collections can be grown once and kept as fields for re-use

### Immutable state

* It's tempting to provide a facility to directly access the immutable state of an actor
* But that would be redundant, i.e. the requesting code can get the immutable state in the same way the actor got it
  * Unless it's a property as opposed to a dependency, e.g. one actor per financial instrument?

### Suspend target

* Currently only the same actor can resume after a suspend, and I'm not convinced this needs extending to any actor
