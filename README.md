
# Fuel Injection
FAST, simple, easy to use and very easy to get started dependency injection framework.

## Check out the Wiki
* [Fuel Philosphy](https://github.com/aarontharris/Fuel/wiki#philosophy)
* [Fuel Performance](https://github.com/aarontharris/Fuel/wiki#performance-metrics)

# Fuel Submodule
Clone the Fuel submodule under your project folder
```
git submodule add -b release_1.0.0 git@github.com:aarontharris/Fuel.git Fuel
```

# Quick Integration Tutorial
[Youtube Fuel Tutorial](https://www.youtube.com/watch?v=CuWsEsSgPso)

# How it works
I'm going to assume you watched the video above, if not, do that now, I'll wait...

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
Fuel is aware of the scope of all objects based on the context they've been associated with, either directly or inherited (more on that later).
Proper scoping is guaranteed when using @AppSingleton, @ActivitySingleton, @FragmentSingleton, however when dealing with POJOs that do injections of their own, it is on you to make sure the POJO class receives the correct context for its needs.  Runtime failures will occur at injection time to help you identify early on that there was a scope failure.

## App Scope
Singletons, AppSingletons can inject the Application, Singletons, and AppSingletons becaue they are similary scoped -- one per lifecycle of the app.  POJOs are not one per lifecycle of the app, but they can still inject from App Scope because Fuel is always aware of the Application context and will conveniently associate for you -- only in the case of App Scope, this is not done for any other scope since Fuel cannot confidently know which Activity or Fragment is the correct one since there are many.
// FIXME: needs more

## Activity Scope
ActivitySingletons or POJOs that are associated with an Activity context may inject an Activity, ActivitySingletons, or POJOs that require injection and Activity awareness.
// FIXME: needs more

## Fragment Scope
// FIXME: needs more

## POJOs and Scope
There is no POJO scope but still worth mentioning here.  As mentioned in App Scope, POJOs can inject App Scoped injectables, but POJOs are not scoped.
// FIXME: needs more

# Context Association and Scope Awareness
It is important to note that the examples below demonstrate an Activity context, but the same goes for Application.  However with application association, you receive the Application scope and may only inject AppScoped injectables.

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


