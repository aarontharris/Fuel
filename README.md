
# Fuel Injection
FAST, simple, easy to use and very easy to get started dependency injection framework.

## Check out the Wiki
* [Fuel Philosphy](https://github.com/aarontharris/Fuel/wiki#philosophy)
* [Fuel Performance](https://github.com/aarontharris/Fuel/wiki#performance-metrics)

# Fuel Submodule
Clone the Fuel submodule under your project folder
```
git submodule add -b release_1.0.1 git@github.com:aarontharris/Fuel.git Fuel
```

# Fuek Gradle
```compile 'com.ath.fuel:Fuel:1.0.1'```

# Quick Integration Tutorial
[Youtube Fuel Tutorial](https://www.youtube.com/watch?v=CuWsEsSgPso)

[Youtube Fuel Overview](https://www.youtube.com/watch?v=306GrCktydw)

# Integration Overview
* Inside your Application.onCreate(), be sure to call FuelInjector.initializeModule() with your Custom FuelModule.
* If an object needs to perform injections and itself was not injected, FuelInjector.ignite().
  * Injections are not available until after FuelInjector.ignite().
* All injections are lazy

# How it works
I'm going to assume you watched the video above, if not, do that now, I'll wait...

### Fuel Flow
* You name your injections at the top of your class.
* If the class instance itself was injected, the injections are available immediately
* If the class instance itself was not injected, the injections are queued up waiting for ignite.
* The class gets ignited and injections become available but injection instances are not constructed yet, only Lazy<T>.
* lazy.get() is called and the instance is obtained from a scoped cache or constructed new.

All injections obey type-mappings as you've configured them in your FuelModule -- more on this later.

You can inject anything with Fuel, Singletons, AppSingletons, ActivitySingletons, FragmentSingletons, POJOs, or anything you can teach Fuel to construct, and it's easy.

### Singleton
A singleton in Fuel is exactly as you'd expect it to be, a single instance for the duration of the application lifecycle.  No annotation or special work is necessary.  Your FuelModule is a singleton itself, therefore, any class members in your FuelModule are also Singletons, or any new instances bound to types are Singletons.

For Example:
```
public class SampleFuelModule extends FuelModule {
  private final SomePojo myPojo = new SomePojo();
  
  protected void configre() {
    super.configure(); // inherit super bindings -- useful
    
    bind( SomePojo.class, myPojo ); // Anyone that injects SomePojo will get myPojo forever
    bind( SomeOtherPojo.class, new SomeOtherPojo() ); // Same idea as above but cleaner
    bind( SquarePojo.class, GreenSquarePojo.class ); // you may also map interfaces to other interfaces for chaining
    bind( GreenSquarePojo.class, BigGreenSquarePojoImpl.class ); // and interfaces to implementation class or instances to terminate a chain.
    
    // Note: Because BigGreenSquarePojoImpl is just a POJO not an instance and not an @AppSingleton, it will be instantiated every injection.
  }
}
```

### AppSingleton aka Application Scoped Singleton
An AppSingleton is a Singleton like described above.  There is an annotation @AppSingleton, it serves as documentation to users as well as metada to Fuel to indicate this class should only be instantiated once and shared by all injections for the entire duration of the application.  If your @AppSingleton class does not implement an interface then no mapping is necessary for Fuel, you may just inject and Fuel will do the rest.  However for the sake of encapsulation / abstraction, its best to code to interfaces and if you wish, Fuel will help you.

For Example:
```
@AppSingleton
public class OrangeBox implements Box {
  ...
}

public class SampleFuelModule extends FuelModule {
  protected void configre() {
    super.configure(); // inherit super bindings -- useful
    
    bind( Box.class, OrangeBox.class ); // Any injections requesting Box will get a single instance of OrangeBox
  }
}
```

### ActivitySingleton aka Activity Scoped Singleton
An ActivitySingleton is exactly like an AppSingleton, however, instead of one instance for the entire life of the application, each Activity will receive a unique instance, but only one instance per activity.  All objects aware of the Activity can inject the Activity, ActivitySingletons and AppSingletons.

### FragmentSingleton aka Fragment Scoped Singleton
A FragmentSingleton is exactly like an ActivitySingleton, however, instead of one instance for the entire life of the activity, each Fragment will receive a unique instance, but only one instance per fragment.  All objects aware of the Fragment can inject the Fragment, FragmentSingletons and ActivitySingletons and AppSingletons.

# Scope
Fuel is aware of the scope of all objects based on the context (or Fragment) they've been associated with, either directly or inherited (more on that later).
Proper scoping is guaranteed when using @AppSingleton, @ActivitySingleton, @FragmentSingleton, however when dealing with POJOs that do injections of their own, it is on you to make sure the POJO class receives the correct context for its needs.  Runtime failures will occur at injection time to help you identify early on that there was a scope failure.

## App Scope
Singletons and AppSingletons can inject the Application, Singletons, and AppSingletons becaue they are similary scoped -- one per lifecycle of the app.  POJOs are not one per lifecycle of the app, but they can still inject from App Scope because Fuel is always aware of the Application context and will conveniently associate for you -- only in the case of App Scope -- this is not done for any other scope since Fuel cannot confidently know which Activity or Fragment is the correct one since there are many.

## Activity Scope
ActivitySingletons or POJOs that are associated with an Activity context may inject all App Scoped plus the Activity, ActivitySingletons, or POJOs that require injection and Activity awareness.

## Fragment Scope
FragmentSingletons or  POJOs that are associated with a Fragment may inject all Activity Scoped plus the Fragment, FragmentSingletons and POJOs that require injections and Fragment awareness.  It is important to note that a Fragment is not a context but association (described below) works the same.

## POJOs and Scope
There is no POJO scope but still worth mentioning here.  As mentioned in App Scope, POJOs can inject App Scoped injectables, but POJOs are not scoped.  By default, Injecting a POJO will construct a new instance once for each injection.  However, you may bind a type to a POJO instance essentially telling Fuel to always inject that instance and therefore treating it as a singleton.  You may also use a Provider to choose at injection time, how to obtain an instance (more on this later).

# Context Association and Scope Awareness
It is important to note that the examples below demonstrate an Activity context, but the same goes for Application.

## Self asssociation to a context:
The simplest form of associating a context to an instance.  MyRandomClass knows about the activity and associates itself to the activity.  This example is self-enforcing and self-documenting, however you may not always want to give the class the context.

#### MyRandomClass associates a context to itself
```
public class MyRandomClass {
  private final Lazy<SomeActivitySingleton> mSomeActivitySingleton = Lazy.attain( this, SomeActivitySingleton.class );
  
  public MyRandomClass( Activity activity ) {
    FuelInjector.ignite( activity, this ); // NOTE: associate context to self
  }
}
```

## Direct association to a context;
This is a simple form of association, very direct and easy to understand, however there is a code-design drawback.  SomePojo truly requires an Activity associated with it, or it will fail at runtime.  This pattern does not compile-time communicate to the developer that SomePojo needs to be ignited.  This pattern forces you to remember, document, etc.  Otherwise, there is nothing functionally wrong with it and can be very useful if your team has a strong awareness of injection and you want SomePojo to know nothing about the Activity directly.

#### SomePojo unknowingly receives a context from the parent.
```
public class SomePojo {
  private final Lazy<SomeActivitySingleton> mSomeActivitySingleton = Lazy.attain( this, SomeActivitySingleton.class );
  ...
}

public class MyRandomClass {
  private final Lazy<SomeActivitySingleton> mSomeActivitySingleton = Lazy.attain( this, SomeActivitySingleton.class );
  
  public MyRandomClass( Activity activity ) {
    FuelInjector.ignite( activity, this );
    
    SomePojo pojo = new SomePojo();
    FuelInjector.ignite( this, pojo ); // NOTE: associate self to pojo and pojo inherits context from self
  }
}
```

## Inherited association to a context
Please reference the inline comment below prefixed "NOTE:" -- When doing a Lazy.attain(), the first argument is either a context, or an instance that should be aware of a context so that the injectable can get the context or inherit the context respectively.  A keen eye may notice that at the time that Lazy.attain() has been called, "this" does not yet have a context associated with it since it's FuelInjector.ignite( activity, this ) has not yet been called.  This is okay because injections are queued up until ignite is called and then dequeued.  It works out nicely.

#### mSomeActivitySingleton inherits the context given to MyRandomClass in its constructor, and it is this context that scopes both the MyRandomClass instance and mSomeActivitySingleton.
```
public class SomePojo {
  private final Lazy<SomeActivitySingleton> mSomeActivitySingleton = Lazy.attain( this, SomeActivitySingleton.class );
  ...
}

public class MyRandomClass {
  private final Lazy<SomeActivitySingleton> mSomeActivitySingleton = Lazy.attain( this, SomeActivitySingleton.class ); // NOTE: associate self to injectable
  
  public MyRandomClass( Activity activity ) {
    FuelInjector.ignite( activity, this );
  }
}
```

# Type Mapping and Construction
You may bind a base-type to a leaf-type in the FuelModule to teach Fuel what type to provide when a type is requested.  An example we've seen above; when injecting type "Box" and Box was mapped to OrangeBox.  Box is the base-type and OrangeBox is the leaf-type, the base-type maps to the leaf-type.  You may chain type maps as long as you like, but the terminating type must be something Fuel can construct.

### Type Maps
Mapping a base-type to a leaf-type is performed using the bind(base, leaf) method within FuelModule.configure().

You may bind a class to a instance.  (Highest precedence)

You may bind a class to a provider.  (Next highest precedence)

You may bind a class to a class. (Lowest precedence).
```
public class SampleFuelModule extends FuelModule {
  protected void configure() {
    super.configure();
    
    bind( Base.class, Leaf.class ); // Leaf must be derived from Base.
    
    bind( A.class, B.class );
    bind( B.class, C.class );
    bind( C.class, D.class ); // you main chain types if needed, injecting A will result in D.
  }
}
```

### Construction
Fuel can only construct objects that have one of the following
* A Public Empty Constructor
* A Public Constructor with arguments that Fuel is capable of constructing
  * Each argument must also meet the construction requirements, and so must their constructor arguments if any, and so on recursively.

To avoid confusion, precedence will always be given to empty constructors if available.  If you find yourself with an Class in need of multiple constructors and you want Fuel to choose the correct constructor, consider a Provider.

#### Which Constructor?
```
public class MyClass {
  public MyClass() { // Takes precedence, even if Fuel knows how to obtain SomeThing.
  }
  
  public MyClass( SomeThing arg ) {
    ...
  }
}
```

### Providers
Providers give you the opportunity to evaluate the injection situation.  A Provider is an abstract class with a provide method that gets called once per injection per type.  If the type is an AppSingleton, then the provider is only called once ever.  If type is an ActivitySingleton, the provider is called only once per Activity, Fragment, etc.  For POJOs the provider is called once per POJO.

#### Choose the right constructor with a Provider
This example will return a new instance of MyClass using the correct constructor of MyClass( SomeThing ) by using a Provider to first obtain SomeThing.  Note that as hinted above, even though we call **new** MyClass( someThing ), if MyClass were a singleton, we'd still only have one instance because the provider would not get called again until the singleton goes out of scope.

As you can see in the example below, the power of providers comes from their ability to inject things and evaluate before returning an instance.  This allows you to place all factory wiring inside your FuelModule.

A note to best practices, when injecting inside a provider, its best to use the parent object as the parent for related injections so not to break scope.
```
public class MyClass {
  public MyClass() {
  }
  
  public MyClass( SomeThing arg ) { // Fuel is aware of SomeThing
    ...
  }
}

public class SampleFuelModule extends FuelModule {
  protected void configure() {
    super.configure();
    
    bind( SomeThing.class, new SomeThing() );
    
    bind( MyClass.class, new FuelProvider<MyClass>() {
      @Override public MyClass provide( Lazy lazy, Object parent ) {
        Lazy<SomeThing> someThing = Lazy.attain( parent, SomeThing.class );
        return new MyClass( someThing );
      }
    });
  }
}
```
![](https://github.com/aarontharris/Fuel/blob/master/Fuel%20Flow.png)
