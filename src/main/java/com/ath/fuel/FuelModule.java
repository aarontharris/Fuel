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
