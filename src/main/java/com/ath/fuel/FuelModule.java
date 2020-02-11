package com.ath.fuel;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ath.fuel.err.FuelInjectionBindException;
import com.ath.fuel.err.FuelInjectionException;
import com.ath.fuel.err.FuelUnableToObtainInstanceException;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class FuelModule {

    /**
     * Provider is cached with the context associated with the type it returns<br>
     * Providers are cached against activity unless the type is an AppSingleton<br>
     * Provider.provide's value is not cached so Provider.provide() may be called many times do your own caching if needed.
     *
     * @param <T>
     * @author aharris
     */
    public interface FuelProvider<T> {

        /**
         * Override this method when choosing an injectable depends on runtime state.<br>
         * Here, you may inject objects and inspect state and decide which instance is best.<br>
         * This method is called once for each Lazy.attain, but not for Lazy.get()'s as the lazy's internal instance is cached.<br>
         *
         * @param lazy   the actual lazy returned to the parent requesting injection - contains useful metadata
         * @param parent the object that requested injection and will receive the lazy.
         * @return
         */
        T provide(Lazy lazy, Object parent);
    }

    // Scope -> ScopeObject -> CacheKey -> instance
    // - Scope is Application, Activity, Fragment, etc
    // - ScopeObject would be the context, or the fragment, paired to the scope
    // - CacheKey describes the instance we're looking for
    // - instance the hidden treasure
    private final Map<Scope, WeakHashMap<Object, Map<CacheKey, Object>>> scopeCache = new HashMap<>();
    private final Map<Object, WeakHashMap<Object, Lazy>> lazyCache = Collections.synchronizedMap(new WeakHashMap<Object, WeakHashMap<Object, Lazy>>());

    private final HashMap<Class<?>, Class<?>> classToClassMap = new HashMap<>();
    private final HashMap<Class<?>, Object> classToObjectMap = new HashMap<>();
    private final HashMap<Class<?>, FuelProvider> classToProviderMap = new HashMap<>();
    private Application app;

    private final @NonNull List<FuelModule> submodules = new CopyOnWriteArrayList<>(); // FIXME: Submodule - WeakRef
    private @Nullable FuelModule parent = null; // FIXME: Submodule - WeakRef


    /* package private */
    Application.ActivityLifecycleCallbacks localLifecycleCallbacks;

    public FuelModule(Application app) {
        this.app = app;

        localLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
                FuelModule.this.onActivityCreated(activity, savedInstanceState);
            }

            @Override public void onActivityStarted(@NonNull Activity activity) {
                FuelModule.this.onActivityStarted(activity);
            }

            @Override public void onActivityResumed(@NonNull Activity activity) {
                FuelModule.this.onActivityResumed(activity);
            }

            @Override public void onActivityPaused(@NonNull Activity activity) {
                FuelModule.this.onActivityPaused(activity);
            }

            @Override public void onActivityStopped(@NonNull Activity activity) {
                FuelModule.this.onActivityStopped(activity);
            }

            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
                FuelModule.this.onActivitySaveInstanceState(activity, outState);
            }

            @Override public void onActivityDestroyed(@NonNull Activity activity) {
                FuelModule.this.onActivityDestroyed(activity);
            }
        };
        app.registerActivityLifecycleCallbacks(localLifecycleCallbacks);
    }

    /*

    I'm thinking we support nested modules.

    When I request an injectable, I start from the nearest module relative to me in the tree.
    I check for a match, and bubble my way up the tree until I find it, or I reach the limit of my scope.

    The tree should be deterministic by the View Tree? This could create problems for tests.
    Solution: We only use the view tree to provide the initial module, from there the modules should be aware.

    So:
    A view searches up the view tree and finds a parent fragment has a module associated with it.
    We then check that module for a matching injectable.
    If that module can't find a match, it looks up it's known ancestors until a match is found or scope ends.


    --

    Thoughts

    What if adding modules can be done at a package level and the inheritance follows the packages
     - multiple modules allows for multiple instances of a singleton
       - Lets say
             A            (App)
         B       C        (Act)
       D   E   F   G      (Frag) // NOTE: To reg a Frag, you must reg it's view.
      H I J K L M N O     (View)

        if B and C both define a provider for the same class, then either they both get their own instance or one gets something constructed in an unexpected way
        - This only happens if B & C define an AppSingleton
          So AppSingletons must be defined in AppModule?
          But we want packages to be able to define their own?

          So when a module defines an injectable for a scope above its own, that mapping is placed in the nearest module matching that scope.
          - Enforced by LINT and RUNTIME
          - What about runtime changing of modules?
            - G defines an AppSingleton, then dies, then X swaps in at runtime and defines a different one..
              - FIXED: G cannot define an AppSingleton. Instead it can define an AppModule that defines an AppSingleton and register that AppModule with the root module

            - In order for a sub package to define an AppSingleton, it must define an AppModule and add it to the existing AppModule
              - This way that app module cannot come and go.
            - In order for a view level package to define a FragSingleton
              - It must define a FragModule and append the FragModule to
            - maybe lower level packages CAN ONLY define App and Activity Singletons
            - maybe the only scope lower level packages can define mappings for above their scope is App and Activity?
              - By that I mean, a ViewModule can define App/Activity/View singletons, but not Fragment.
              - Why? Because unless you're defining the Fragment, how do you know there is even a fragment to scope by?
              - We ditch FragScope and just have App/Activity/View scopes ?
                - Frag would just be treated as a view
                - This solves knowing if a Frag is present or not but it doesnt solve nested View Modules overwriting each other.
                - See: Multiple Defs Continued

     Benefit ?
     It lets packaged up things define their own things rather than stuffing it up under the main module.
     I can do that by just having the configure&bind methods,
     Do I really need a whole module?

     The benefit of a module would be that it can own instances scoped below it making it easier to destroy them when going out of scope

     How would we find these instances?
     Look up the tree and find the nearest module that has what you need?

     How do we maintain the tree?
     When we ignite ?

     A, C and G must ignite themselves
     -- is it fine to say only things that ignite themselves can define their own module?
     YES: because if something can inject them, then they belong to that parent's module.
     OK: So we connect the dots during ignite.

     A as App has App
     C as Act has Act and App
     G as Frag has Frag and Act and App
     O as View has Act and App but no Frag
     What about Views defining a module? Should work the same as a Frag

     How does O get the frag?  The view tree?
     When a Frag ignites, it must provide it's root view.
     Then when a view looks up the view tree, it finds a view associated with a Frag and then the frag can get to the module

     Done

     What about Don't Keep Activities losing this View->Frag association ?
     When the activity is destroyed, so are all of its singletons.
     When the activity is rebuilt, so are all of its singletons.
     Their state is lost but its the job of the activity, fragment, and views to restoreInstanceState and re-establish listeners / subscribers

     Done


     # Multiple Defs Continued
     Is the pojo a singleton?
     No:  Then there is no scope, its just a matter of how to instantiate it aka find a provider in a module.
          For non singletons we start from the top level module and work our way down until we find a provider.
          Since modules are always registered with a parent, it should be easy to build the tree at runtime.

     YES: by pojo we mean something we can construct (unlike Activities, etc)
          Lets say we only want this to live at the Fragment level...
          So it should be defined in a fragment level module?
            Not necessarily but its possible.
            So when it IS only defined at a Fragment level module, can it be defined in multiple parallel fragment level modules?
              YES: This basically gives you polymorphism.  What you git differs based on what/where you are.
                   Modules MUST not overlap -- this is fine because a fragment/view can only define one via ignite
            When its not defined at a Fragment level, it means that its mapping is common (not polymorphic)
              Any fragment scope can inject it and they all get the same provider but different instances when different fragments.

            What if we define a fragment mapping at the root, but then we want to override it at a lower tree level ?
                Going top down will take the root first....

             A            (App)
         B       C        (Act)
       D   E   F   G      (View) // fragments
      H I J K L M N O     (View)

                Top Down:
                    - if A defines a FragSingleton and G defines the same FragSingleton -
                      broken: G would get something defined by A unexpected

                      We go straight to the top for AppSingletons since we always know the app module
                      but the app module may have child app modules.
                      - When its inserted there is a failure and you must acknowledge the duplicate mapping
                        by removing yours or entering an explicit "duplicateMapping --take theirs or take ours" etc
                        It would be highly unlikely this will happen outside of our own codebase.
                        If it did then we'd just rename ours?

                Bottom Up:
                    - if the class->provider map is defined twice in the stack then
                      Different parts of teh tree could get different definitions
                      This is ok if the scope is different, but what if its not?
                      What if M and O both define a provider for a FragSingleton -- seems ok
                      What if N and O both define a provider for a FragSingleton -- seems as one of them would get unwanted results broken N & O compete
                      What if FragScoped Singletons must be defined by a FragScoped Module -- solved
                        FragFuelModule can only be ignited by a fragment?
                        FragSingletons can only be defined by Frag (or greater) scoped modules

                      What if A defines a FragSingleton and C defines the same one?
                      - Then B's fragments get the default from A
                        And  C's fragments get the override from C

                      What if G defines a ViewSingleton (which evaporates with its module) and O defines the same ?
                      - G & N get the same singleton, but O gets something different
                        - As a rule: the same map cannot be defined twice in the same tree
                          If G defines V and F defines V its fine, they're both the root of their View Scopes
                          If F defines V and O defines V its fine, they're in different view trees from view scope down
                          If G defines V and O defines V its a fail because N and O will get something different.

                      Is this a special case for views?
                      No not really, if you think about it, its the same pattern as App and Activity
                      Apps A1 and A2 never talk so its not an issue plus you can't define above A
                      Acts B and C (let A be a ActivitySingleton Mapping)
                        - B and C can only map AppSingletons in an AppModule like any other level trying to map above its scope
                        - if B defines A and C defines A its fine, they'll never cross communicate
                        - if B defines V and C defines V its fine, they'll never cross communicate
                        - if C defines V and G defines V its a fail - it broke our rule: two maps in the same tree

                      RULE: Cannot have two maps with the same "from" in the same tree
                      - How do we define the tree

             A            (App)  AppS
         B       C        (Act)  ActS
       D   E   F   G      (View) VewS
      H I J K L M N O     (View) VewS

                        Rules:
                        - If A defines AppS, then it cannot be defined again between A & HIJKLMNO
                        - If A defines ActS, then it cannot be defined again between A & A   // begin & begin^
                        - IF A defines VewS, then it cannot be defined again between A & BC  // begin & begin^
                        - If B defines AppS, then it cannot be defined again between A & HIJKLMNO
                        - If B defines ActS, then it cannot be defined again between B & HIJK
                        - IF B defines VewS, then it cannot be defined again
                        - If D defines AppS, then it cannot be defined again between A & HIJKLMNO
                        - If D defines ActS, then it cannot be defined again
                        - If D defines VewS, then it cannot be defined again
                        - If H defines AppS, then it cannot be defined again between A & HIJKLMNO
                        - If H defines ActS, then it cannot be defined again
                        - If H defines VewS, then it cannot be defined again D-HI, E-JK

                        when we define a map, that mapping is placed at the root-most module scoped teh same as the map->scope
                        if a map of the same leaftype exists, its a fail.

                        So mappings are bubbled up
                        we then find them bottom up // except for App & Activity singletons which we can just shortcut straight to them.
                        submodules must be registered at compile time or statically ? // TODO

                        - What about sub libraries that define a map that conflicts and we can't change their code to fix it?
                        - Perhaps we provide a means to acknowledge and mute them
                          - Take theirs, take ours ? optional ?

                        What about when O adds a class -> instance mapping where class is AppScoped, instance becomes a singleton
                        What about when O adds a class -> instance mapping where class is ActScoped, instance becomes an ActSingleton
                        What about when O adds a class -> instance mapping where class is VewScoped, instance becomes a VewSingleton
                        - which view? All views? the root view, whats the root view? // TODO broken

                        ViewScoped implies every view gets its own -- is that what we really want?
                        I think what we really want is to acknowledge a viewtree root and scope everythign there.
                        How?
                        Annotate a CustomView/Fragment as a ViewRoot and every view under that root shares a scope?
                        There can only be one ViewRoot vertically? but many horizontally?
                        Above the ViewRoot is considered ActivityScope
                        ViewRoot cant be applied to a Fragment or A CustomView


                 What about multiple defs in modules we add to the root module?  Take the first/last or throw an error?
                 - because we now bubble up, multiple defs of the same from->... cannot coexist

     BEWARE: when submodules add mapping, that mapping must stay in that submodule -- DO NOT try to cache it at the tippy top
     Why ? because mapping class -> instance should keep that instance alive only at the module scope
     by promoting the mapping to the root level, we're also promoting that instance to App Singleton.
     this is why we only promo mappings to the root of the appropriate scope


     */



    FuelModule(@NonNull FuelModule parent) { // FIXME: Submodule
        parent.addSubModule(this);
    }

    void prepareForDeath() {
        if (localLifecycleCallbacks != null) {
            app.unregisterActivityLifecycleCallbacks(localLifecycleCallbacks);
        }
    }

    protected void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    protected void onActivityStarted(Activity activity) {
    }

    protected void onActivityResumed(Activity activity) {
    }

    protected void onActivityPaused(Activity activity) {
    }

    protected void onActivityStopped(Activity activity) {
    }

    protected void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    protected void onActivityDestroyed(Activity activity) {
    }

    /**
     * Called whenever a new instance is obtained by Fuel.<br>
     * For Singletons it should only be once per scope.<br>
     * Only called for Singletons.
     */
    protected @NonNull <T> T onInstanceCreated(@NonNull Lazy<T> lazy) {
        return lazy.getInstance();
    }

    void addSubModule(@NonNull FuelModule module) {
        this.submodules.add(module); // FIXME: Submodule - WeakRef
        module.parent = this; // FIXME: Submodule - WeakRef
    }

    void remSubmodule(@NonNull FuelModule module) {
        this.submodules.remove(module);
        module.parent = null;
        // FIXME: Submodule - destroy all children
    }

    /**
     * Called when an instance does not have a mapping.<br>
     * If you don't do something, it's going to fail.<br>
     * <br>
     * How did we get here? You are trying to obtain an instance that Fuel<br>
     * does not know how to construct. Typically this is solved with bindings in the FuelModule.<br>
     * <br>
     * For an easy way out you have some options:<br>
     * <pre>
     * bindReflectively( MyObject.class ); // in your FuelModule <br>
     * </pre>
     * <br>
     * Or override this method in your FuelModule:<br>
     * <pre>
     * Object onInstanceUnattainable( Lazy lazy ) {
     *     return REFLECTIVE_PROVIDER.provide( lazy.getLeafType() );
     * }
     * </pre>
     * Or override and throw an error to fully unsupport reflective attain.<br>
     * Note individual use via bindReflectively() will still be enabled.
     */
    protected @Nullable <T> T onInstanceUnattainable(@NonNull Lazy<T> lazy) {
        FLog.w("Fuel is forced to use reflection to obtain: '"
                + lazy.getLeafType()
                + "'. This is because no mapping has been provided. " +
                "For more info see FuelModule.onInstanceUnattainable()");

        //noinspection unchecked
        return (T) REFLECTIVE_PROVIDER.provide(lazy, null);
    }

    void doOnFueled(Lazy lazy, boolean ignite) {
        try {
            // pre-conditions and exemptions
            if (lazy == null || lazy.getInstance() == null) {
                return;
            } else if (lazy.onFueledCalled) {
                return;
            } else if (!(lazy.getInstance() instanceof OnFueled)) {
                return;
            } else if (ignite) { // white-list ignites, ignoring singleton or reqInj
                // continue
            } else if (FuelInjector.isSingleton(lazy.getLeafType())) { // TODO: could totally cache lazy.isSingleton ... later.
                // continue
            } else if (!FuelInjector.isInjectionRequired(lazy.getLeafType())) {
                // continue
            }

            lazy.onFueledCalled = true;
            ((OnFueled) lazy.getInstance()).onFueled();
        } catch (Exception e) {
            FLog.e(e);
        }
    }

    /**
     * Called when a critical failure occurs and Fuel is unable to recover.<br>
     * Please see derived types of {@link FuelInjectionException} for details on conditions that may cause this method to be called.
     *
     * @param lazy      the culprit
     * @param exception what went wrong
     */
    protected void onFailure(Lazy lazy, FuelInjectionException exception) {
        throw exception;
    }

    /**
     * Teach the fuel system how to obtain a context for a type.<br>
     * If the given object is an instance of something you can provide a context for, then doing so eliminates the need to call ignite for that type
     * and all its derived types.<br>
     * One exception to this rule is that injections are always queued up until Fuel has been initialized.<br>
     * <br>
     * Be aware, if you return an Application context for something that is a ActivitySingleton, you'll get trouble.<br>
     *
     * @param object
     * @return null if unable to map to a context
     */
    @CallSuper
    protected Context provideContext(Object object) {
        if (object != null) {
            if (object instanceof Application) {
                return FuelInjector.getApp(); // may be null if not initialized
            } else if (object instanceof Service) {
                return FuelInjector.getApp(); // may be null if not initialized
            } else if (object instanceof Activity) {
                return (Activity) object;
            } else if (object instanceof View) {
                return ((View) object).getContext();
            } else if (object instanceof ArrayAdapter) {
                return FuelInjector.toContext(((ArrayAdapter) object).getContext());
            } else if (object instanceof AdapterView) {
                return FuelInjector.toContext(((AdapterView) object).getContext());
            } else if (FuelInjector.isAppSingleton(object.getClass())) {
                return FuelInjector.getApp(); // may be null if not initialized
            }
        }
        return null;
    }

    /**
     * Teach the fuel system how to obtain a context for a type.<br>
     * If the given object is an instance of something you can provide a context for, then doing so eliminates the need to call ignite for that type
     * and all its derived types.<br>
     * One exception to this rule is that injections are always queued up until Fuel has been initialized.<br>
     * <br>
     * Be aware, if you return an Application context for something that is a ActivitySingleton, you'll get trouble.<br>
     *
     * @param type
     * @return null if unable to map to a context
     */
    @CallSuper
    protected final Context provideContext(Class<?> type) {
        if (type != null) {
            if (FuelInjector.isApplication(type)) {
                return FuelInjector.getApp(); // may be null if not initialized
            } else if (FuelInjector.isService(type)) {
                return FuelInjector.getApp(); // may be null if not initialized
            } else if (FuelInjector.isAppSingleton(type)) {
                return FuelInjector.getApp(); // may be null if not initialized
            }
        }
        return null;
    }

    protected void logD(String message) {
        android.util.Log.d(FLog.TAG, message);
    }

    protected void logW(String message) {
        android.util.Log.w(FLog.TAG, message);
    }

    protected void logE(String message) {
        android.util.Log.e(FLog.TAG, message);
    }

    protected void logE(String message, Exception e) {
        if (message == null) {
            message = "Message: " + e.getMessage();
        }
        android.util.Log.e(FLog.TAG, message, e);
    }

    /**
     * Override to plug fuel into your own analytics system
     */
    protected void leaveBreadCrumb(String fmt, Object... args) {
        android.util.Log.d(FLog.TAG, String.format(fmt, args));
    }

    /**
     * <pre>
     * Override to set up your own injection configurations here.
     * Call super.configure() for default configurations.
     * Default Configurations (Overridable here)
     *  {@link LayoutInflater}
     *  {@link ConnectivityManager}
     *  {@link AlarmManager}
     *  {@link LocationManager}
     *  {@link NotificationManager}
     * Automatically handed by FuelInjector (not Overridable)
     *   {@link Context} -- Will attempt to inject Activity but will fall back on Application.
     *   {@link Activity} -- Will only inject Activity, unlike Context
     *   {@link Application} -- Will only inject Application, unlike Context
     * </pre>
     */
    protected void configure() {
        // Services
        bind(LayoutInflater.class, getApplication().getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        bind(ConnectivityManager.class, getApplication().getSystemService(Context.CONNECTIVITY_SERVICE));
        bind(AlarmManager.class, getApplication().getSystemService(Context.ALARM_SERVICE));
        bind(LocationManager.class, getApplication().getSystemService(Context.LOCATION_SERVICE));
        bind(NotificationManager.class, getApplication().getSystemService(Context.NOTIFICATION_SERVICE));
    }

    public void printBindings() {
        // FLog.d( "C2O Mapping: " );
        for (Class c : classToObjectMap.keySet()) {
            FLog.d(" -- C2O Mapping: %s -> %s", c, classToObjectMap.get(c));
        }

        // FLog.d( "C2P Mapping: " );
        for (Class c : classToProviderMap.keySet()) {
            FLog.d(" -- C2P Mapping: %s -> %s", c, classToProviderMap.get(c) == null ? "Null" : "Provider");
        }

        // FLog.d( "C2C Mapping: " );
        for (Class c : classToClassMap.keySet()) {
            FLog.d(" -- C2C Mapping: %s -> %s", c, classToClassMap.get(c));
        }
    }

    /**
     * Add a mapping rule for Class to Class.<br>
     * When you {@link Lazy#attain(Object, Class, Integer)}, the Class can be a base-interface and
     * Fuel will search the Mapping Rules to decide the best instance.<br>
     * <br>
     * EX:<br>
     * <pre>
     * define interface Box;
     * define interface BlackBox extends Box;
     * define class LittleBlackBox implements BlackBox;
     * bind Box -> BlackBox;
     * bind BlackBox -> LittleBlackBox;
     * // Now we have Box -> BlackBox -> LittleBlackBox
     * // and when we request Box, Fuel will provide LittleBlackBox
     * Box mBox = attain Box; // mBox is an instance of LittleBlackBox.
     * </pre>
     *
     * @param from
     * @param to
     */
    protected void bind(Class<?> from, Class<?> to) {
        if (FuelInjector.isDebug()) {
            if (from == null) {
                throw new FuelInjectionBindException("bind failure baseType cannot be null");
            }
            if (to == null) {
                throw new FuelInjectionBindException("bind failure cannot bind %s to a null instance", from);
            }
            if (!from.isAssignableFrom(to)) {
                throw new FuelInjectionBindException("bind failure %s is not derived from %s", to, from);
            }
        }
        classToClassMap.put(from, to);
    }

    /**
     * Add a mapping rule for Class to Object.<br>
     * Forever bind this class to this instance -- causes strong reference to instance forever turning the given object into a pseudo-singleton.<br>
     * <br>
     * Note that this follows the same logic as {@link #bind(Class, Class)} except that binding to an instance always terminates the Mapping Rule Chain.
     *
     * @param from
     * @param to
     * @see #bind(Class, Class)
     */
    protected void bind(Class<?> from, @NonNull Object to) {
        if (FuelInjector.isDebug()) {
            if (from == null) {
                throw new FuelInjectionBindException("bind failure baseType cannot be null");
            }
            if (to == null) {
                throw new FuelInjectionBindException("bind failure cannot bind %s to a null instance", from);
            }
            if (!from.isAssignableFrom(to.getClass())) {
                throw new FuelInjectionBindException("bind failure %s is not derived from %s", to.getClass(), from);
            }
        }
        classToObjectMap.put(from, to);
        classToObjectMap.put(to.getClass(), to);
    }

    /**
     * Add a mapping rule for Class to {@link FuelProvider}.<br>
     * Forever bind this class to this instance -- causes strong reference to instance forever turning the given object into a pseudo-singleton.<br>
     * <br>
     * Note that this follows the same logic as {@link #bind(Class, Class)} except that binding to a {@link FuelModule} always terminates the Mapping Rule Chain.
     *
     * @param from
     * @param to
     * @see #bind(Class, Class)
     */
    protected <BASE, DERIVED extends BASE> void bind(Class<BASE> from, FuelProvider<DERIVED> to) {
        if (FuelInjector.isDebug()) {
            if (from == null) {
                throw new FuelInjectionBindException("bind failure baseType cannot be null");
            }
        }
        classToProviderMap.put(from, to);
    }

    protected void bindReflectively(Class from) {
        classToProviderMap.put(from, REFLECTIVE_PROVIDER);
    }

    Application getApplication() {
        return app;
    }

    // FIXME: consider other mappings above
    // FIXME: cleanup exceptions

    public final FuelProvider REFLECTIVE_PROVIDER = new FuelProvider() {
        @Override public Object provide(Lazy lazy, Object parent) {
            return newInstance(FuelModule.this, lazy);
        }
    };

    /**
     * Get an instance based on the FuelModule configure map and the given type
     */
    Object obtainInstance(Lazy lazy, boolean allowAnonymousNewInstance) throws FuelInjectionException {// FIXME: Submodule
        try {
            Class<?> leafType = lazy.getLeafType();

            // First try direct object map
            Object obj = classToObjectMap.get(leafType);
            if (obj != null) {
                if (lazy.isDebug()) {
                    FLog.leaveBreadCrumb("obtainInstance got %s", obj.getClass().getSimpleName());
                }
                return obj;
            }

            // Second try provider map
            FuelProvider<?> provider = classToProviderMap.get(leafType);
            if (provider != null) {
                //noinspection unchecked
                lazy.setInstance(provider.provide(lazy, lazy.getParent()));
                if (lazy.isDebug()) {
                    FLog.leaveBreadCrumb("obtainInstance provider provided instance for lazy - %s", lazy);
                }
                return initializeNewInstance(lazy);
            }

            // Third try class to class map
            Class<?> toType = classToClassMap.get(leafType);
            if (toType != null) {
                //noinspection unchecked
                lazy.setInstance(onInstanceUnattainable(lazy));
                if (lazy.isDebug()) {
                    FLog.leaveBreadCrumb("obtainInstance classToClassMap found instance for lazy - %s", lazy);
                }
                return initializeNewInstance(lazy);
            }

            if (FuelInjector.isSingleton(leafType)) {
                //noinspection unchecked
                lazy.setInstance(onInstanceUnattainable(lazy));
                if (lazy.isDebug()) {
                    FLog.leaveBreadCrumb("obtainInstance other/ActivitySingleton/AppSingleton new instance returned instance for lazy - %s", lazy);
                }
                return initializeNewInstance(lazy);
            }

            // Last (no mapping) and not special, try to instantiate the literal type they requested
            if (allowAnonymousNewInstance) {
                //noinspection unchecked
                lazy.setInstance(onInstanceUnattainable(lazy));
                if (lazy.isDebug()) {
                    FLog.leaveBreadCrumb("obtainInstance allowAnonymousNewInstance new instance for lazy %s", lazy);
                }
                return initializeNewInstance(lazy);
            }
        } catch (Exception e) {
            if (lazy.isDebug()) {
                FLog.leaveBreadCrumb("obtainInstance Exception %s", e.getMessage());
            }
            FuelInjector.doFailure(lazy, new FuelUnableToObtainInstanceException(e));
        }
        if (lazy.isDebug()) {
            FLog.leaveBreadCrumb("obtainInstance fell through to return null");
        }
        return null; // Null eventually gets caught by Lazy.get() and throws... meh...
    }

    /**
     * Obtain an instance via Reflection.<br>
     * <br>
     * NOTE:<br>
     * This is only used when Type-Mapping was not provided in your FuelModule.<br>
     * A warning is logged and the instance is provided.<br>
     * <br>
     *
     * @see #onInstanceUnattainable(Lazy) for details on turning this off, or embracing it<br>
     * <br>
     * How does it work?<br>
     * Fuel will try to obtain the simplist constructor available.<br>
     * If you inject something that has a no-arg constructor, that will always take precedence.<br>
     * If this is not what you want, use a {@link FuelProvider}.<br>
     * <br>
     * If the only constructors take parameters, and those parameters can be obtained by Fuel<br>
     * via mapping or reflection, then an object will be provided.<br>
     * If any of the requested parameters in the constructor cannot be provided by Fuel, an error will be thrown.<br>
     * <br>
     * For best practice, use Mapping & Providers to construct objects.<br>
     */
    private static Object newInstance(@NonNull FuelModule module, @NonNull Lazy lazy) throws FuelInjectionException {
        try {

            if (lazy.isDebug()) {
                FLog.leaveBreadCrumb("newInstance for %s", lazy);
            }

            // FLog.d( "New Instance %s @ %s", leafType, context );
            try {
                return lazy.getLeafType().newInstance();
            } catch (Exception e) {
                if (lazy.isDebug()) {
                    FLog.leaveBreadCrumb("newInstance no empty constructor for %s", lazy);
                }
                // FLog.d( "No Empty Constructor for '%s'", leafType );
            }

            final Class<?> leafType = lazy.getLeafType();
            final Context context = lazy.getContext();

            Constructor[] constructors = leafType.getConstructors();
            if (constructors.length == 0) {
                if (lazy.isDebug()) {
                    FLog.leaveBreadCrumb("newInstance no constructors for %s", lazy);
                }
                throw new Exception("No constructors available for " + leafType + " maybe you need to provide FuelMapping?");
            }
            Constructor ctor = constructors[0];
            // FLog.d( " -- found constructor with args" );

            Class[] parameterTypes = ctor.getParameterTypes();

            int len = parameterTypes.length;
            Object[] args = new Object[len];
            for (int i = 0; i < len; i++) {
                Class type = parameterTypes[i];
                // FLog.d( " -- -- resolving %s", type );

                Class toType = getType(module, type, CacheKey.DEFAULT_FLAVOR); // FIXME: FUEL args should support flavors

                CacheKey key = CacheKey.attain(toType);
                Object o = FuelInjector.getInstance(context, key, lazy, lazy.isDebug());
                if (o == null) {
                    // here we say false because we dont want to allow non mapped or non singletons to be instantiated for constructor args.
                    // If a constructor takes an Integer as an argument, do you think its expecting a new Integer() ? probably not
                    // but if you want to make exceptions for stuff you can call it out in the FuelModule with markAsInjectable().
                    o = FuelInjector.newInstance(key, Lazy.attain(context, toType), false);
                }

                if (o != null) {
                    args[i] = o;
                } else {
                    if (lazy.isDebug()) {
                        FLog.leaveBreadCrumb("newInstance unable to instantiate for %s", lazy);
                    }
                    throw new FuelInjectionException(
                            "Unable to instantiate %s -- cannot satisfy argument %s, maybe they're not singletons or not mapped or marked as " +
                                    "Injectable?",
                            leafType, type);
                }
            }
            // FLog.d( " -- found and instantiating... %s", ctor );
            Object inst = ctor.newInstance(args);
            // FLog.d( " -- found and instantiating... success %s", ctor );
            if (inst != null) {
                // FLog.d( " -- found and instantiating... success and not null %s", ctor );
                // FLog.w( "WARNING: Injecting with ambiguous constructor is dangerous: %s", leafType );
                // FLog.d( " -- found and instantiating... success and not null returning it %s", ctor );
                if (lazy.isDebug()) {
                    FLog.leaveBreadCrumb("newInstance got instance for %s", lazy);
                }
                return inst;
            }

        } catch (FuelInjectionException e) {
            if (lazy.isDebug()) {
                FLog.leaveBreadCrumb("newInstance FIException %s", e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            if (lazy.isDebug()) {
                FLog.leaveBreadCrumb("newInstance Exception %s", e.getMessage());
            }
            throw new FuelInjectionException(e);
        }
        if (lazy.isDebug()) {
            FLog.leaveBreadCrumb("newInstance Unable to instantiate %s", lazy);
        }
        throw new FuelInjectionException("Unable to instantiate %s", lazy);
    }

    /**
     * @param lazy must have an instance
     * @return
     * @throws Exception
     */
    Object initializeNewInstance(Lazy lazy) throws Exception {
        if (lazy.isDebug()) {
            FLog.leaveBreadCrumb("initializeNewInstance for %s", lazy);
        }
        FuelInjector.doPostProcess(lazy);

        doOnFueled(lazy, false);
        if (FuelInjector.isSingleton(lazy.getLeafType())) { // TODO: could totally cache lazy.isSingleton ... later.
            Object obj = onInstanceCreated(lazy);
            if (lazy.getInstance() != obj) {
                lazy.setInstance(obj);
            }
        }
        return lazy.getInstance();
    }

    /**
     * Find the leaf-most type in the mapping rules for the given baseType<br>
     * bind: A -> B<br>
     * bind: B -> C<br>
     * bind: C -> D<br>
     * <br>
     * D = getType( A )<br>
     *
     * @param baseType
     * @param flavor   - considered for providers
     * @param <T>
     * @return
     */
    // Must stay logically paired with obtainInstance -- not super cool but ... for now.
    static <T> Class<? extends T> getType(FuelModule module, Class<T> baseType, Integer flavor) { // FIXME: Submodule
        Object obj = module.classToObjectMap.get(baseType);
        if (obj != null) {
            return (Class<? extends T>) obj.getClass();
        }

        FuelProvider<?> provider = module.classToProviderMap.get(baseType);
        if (provider != null) {
            return baseType;
        }

        Class<?> toType = module.classToClassMap.get(baseType);
        if (toType != null) {
            return (Class<? extends T>) getType(module, toType, flavor); // Recursive check
        }

        return baseType;
    }

    /**
     * @param primeTheCacheEntry if true, an empty entry will be added for this context if not already present, false leaves it alone and returns
     *                           empty
     * @return an "immutable" map when primeTheCachEntry = false :/ meh
     */
    Map<CacheKey, Object> getCacheByContextNotThreadSafe(Lazy lazy, boolean primeTheCacheEntry) { // FIXME: Submodule
        Map<CacheKey, Object> contextCache = null;
        Scope cacheScope = lazy.toCacheScope();
        Object scopeObject = lazy.toObjectScope();

        // if either are null then this lazy is not cacheable
        if (cacheScope != null && scopeObject != null) {
            WeakHashMap<Object, Map<CacheKey, Object>> cache = scopeCache.get(cacheScope);
            if (cache == null) {
                cache = new WeakHashMap<>();
                scopeCache.put(cacheScope, cache);
            }

            contextCache = cache.get(scopeObject);
            if (contextCache == null) {
                if (primeTheCacheEntry) {
                    contextCache = new HashMap<>();
                    cache.put(scopeObject, contextCache);
                }
            }
        }

        // Shouldn't be able to prime the cache if not cacheable -- but potential fail
        if (contextCache == null) {
            contextCache = Collections.emptyMap();
        }
        return contextCache;
    }

    final void rememberLazyByInstance(Object instance, Lazy lazy) {// FIXME: Submodule
        Object scopeObject = lazy.toObjectScope();
        if (scopeObject == null) {
            scopeObject = lazy.getContext();
        }
        WeakHashMap<Object, Lazy> parentToLazies = lazyCache.get(scopeObject);
        if (parentToLazies == null) {
            parentToLazies = new WeakHashMap<>();
            lazyCache.put(scopeObject, parentToLazies);
        }
        parentToLazies.put(instance, lazy);
    }

    final Lazy findLazyByInstance(Object instance) {// FIXME: Submodule
        // work-around for WeakHashMap Iterators being fast-fail
        List<Object> scopedObjects = new ArrayList<>(lazyCache.keySet());

        //for ( Object scopeObject : injector.lazyCache.keySet() ) {
        for (Object scopeObject : scopedObjects) {
            if (scopeObject != null) {
                WeakHashMap<Object, Lazy> parentToLazies = lazyCache.get(scopeObject);
                if (parentToLazies != null) {
                    Lazy lazy = parentToLazies.get(instance);
                    if (lazy != null) {
                        return lazy;
                    }
                }
            }
        }
        return null;
    }
}
