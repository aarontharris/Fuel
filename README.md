
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
An ActivitySingleton is exactly like an AppSingleton, however, instead of one instance for the entire life of the application, each Activity will receive a unique instance, but only one instance per activity.  All objects aware of the Activity can inject ActivitySingletons and AppSingletons.

### FragmentSingleton aka Fragment Scoped Singleton
A FragmentSingleton is exactly like an ActivitySingleton, however, instead of one instance for the entire life of the activity, each Fragment will receive a unique instance, but only one instance per fragment.  All objects aware of the Fragment can inject FragmentSingletons and ActivitySingletons and AppSingletons.
