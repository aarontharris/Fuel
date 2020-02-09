package com.ath.fuel;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.fragment.app.Fragment;

import com.ath.fuel.err.FuelInjectionException;
import com.ath.fuel.err.FuelInvalidParentException;
import com.ath.fuel.err.FuelScopeViolationException;
import com.ath.fuel.err.FuelUnableToObtainContextException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class FuelInjector {
    static FuelInjector injector = new FuelInjector();
    private static Application app;
    private static Activity activity; // Not a weakref -- this is okay because it gets assigned to the next activity each time a new activity is
    // created, so the previous
    // one is never held unless the app goes to the background in which case, if the activity is onDestroy'd there will have to be a
    // new one when the app is resumed so still calls onActivityCreate so okay! -- We chose not to use a weakref because in the rare
    // case of low memory, it could get GC'd and then you end up referring to a null activity I think its not bad to hold onto in this
    // case

    private static boolean isDebug = false;
    private static final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private static final SparseArrayCompat<Class> leafTypeCache = new SparseArrayCompat<>();
    private static final Map<Class, Boolean> isAppSingletonCache = new HashMap<>();
    private static final Map<Class, Boolean> isSingletonCache = new HashMap<>();
    private static final Map<Class, Boolean> isActSingletonCache = new HashMap<>();
    private static final Map<Class, Boolean> isFragSingletonCache = new HashMap<>();
    private static final Map<Class, Boolean> isAppCache = new HashMap<>();
    private static final Map<Class, Boolean> isActCache = new HashMap<>();
    private static final Map<Class, Boolean> isFragCache = new HashMap<>();
    private static final Map<Class, Boolean> isServCache = new HashMap<>();
    private static final Map<Class, Boolean> isContextCache = new HashMap<>();
    private static final Map<Class, Boolean> isInjectionRequired = new HashMap<>();

    /**
     * True will tighten up tolerances for quicker failures and more verbosity
     */
    public static final boolean isDebug() {
        return isDebug;
    }

    /**
     * True will tighten up tolerances for quicker failures and more verbosity
     */
    public static final void setDebug(boolean debug) {
        isDebug = debug;
    }

    /**
     * @return null if the FuelInjector has not yet been {@link #isInitialized()}
     */
    public static final @Nullable Application getApp() {
        return app;
    }

    /**
     * not guaranteed to exist
     */
    static final @Nullable Activity getActivity() {
        return activity;
    }

    static WeakReference<Context> getContextRef(Context context) {
        synchronized (context) { // synchronized on context because we dont want to hold up an activity when a background service is working
            context = toContext(context);
            WeakReference<Context> out = injector.contextToWeakContextCache.get(context);
            if (out == null) {
                out = new WeakReference<Context>(context);
                injector.contextToWeakContextCache.put(context, out);
            }
            return out;
        }
    }

    /**
     * Get the real context when the given context is a wrapper
     *
     * @param context - ambiguous context
     */
    static final Context toContext(Context context) {
        if (context != null) {
            if (context instanceof Activity) {
                // no-op
            } else if (context instanceof Application) {
                // no-op
                //} else if ( context instanceof Service ) {
                //    context = context.getApplicationContext();
            } else if (context instanceof ContextWrapper) { // some other foreign context
                Context out = toContext(((ContextWrapper) context).getBaseContext());
                if (out != null) {
                    context = out;
                }
            }
        }
        return context;
    }

    /**
     * @return True after {@link #initializeModule(FuelModule)}
     */
    public static boolean isInitialized() {
        if (app == null || injector.fuelModule == null) {
            return false;
        }
        return true;
    }

    /**
     * @see #isInitialized()
     */
    public static boolean isUninitialized() {
        return !isInitialized();
    }

    static final <T> Class<? extends T> toLeafType(Class<T> type, Integer flavor) {
        if (flavor == null) {
            Class leafType = leafTypeCache.get(type.hashCode());
            if (leafType == null) {
                leafType = FuelModule.getType(injector.fuelModule, type, null);
                leafTypeCache.put(type.hashCode(), leafType);
            }
            return leafType;
        }
        return FuelModule.getType(injector.fuelModule, type, flavor);
    }

    static final boolean isAppSingleton(Class<?> leafType) {
        Boolean singleton = isAppSingletonCache.get(leafType);
        if (singleton == null) {
            singleton = leafType.isAnnotationPresent(AppSingleton.class);
            isAppSingletonCache.put(leafType, singleton);
        }
        return singleton;
    }

    static final boolean isActivitySingleton(Class<?> leafType) {
        Boolean singleton = isActSingletonCache.get(leafType);
        if (singleton == null) {
            singleton = leafType.isAnnotationPresent(ActivitySingleton.class);
            isActSingletonCache.put(leafType, singleton);
        }
        return singleton;
    }

    static final boolean isFragmentSingleton(Class<?> leafType) {
        Boolean singleton = isFragSingletonCache.get(leafType);
        if (singleton == null) {
            singleton = leafType.isAnnotationPresent(FragmentSingleton.class);
            isFragSingletonCache.put(leafType, singleton);
        }
        return singleton;
    }

    static final boolean isSingleton(Class<?> leafType) {
        Boolean singleton = isSingletonCache.get(leafType);
        if (singleton == null) {
            singleton = (isAppSingleton(leafType) || isActivitySingleton(leafType) || isFragmentSingleton(leafType));
            isSingletonCache.put(leafType, singleton);
        }
        return singleton;
    }

    static final boolean isInjectionRequired(Class<?> leafType) {
        Boolean match = isInjectionRequired.get(leafType);
        if (match == null) {
            match = leafType.isAnnotationPresent(RequiresInjection.class);
            isInjectionRequired.put(leafType, match);
        }
        return match;
    }

    static boolean isApplication(Class<?> leafType) {
        Boolean match = isAppCache.get(leafType);
        if (match == null) {
            match = Application.class.isAssignableFrom(leafType);
            isAppCache.put(leafType, match);
        }
        return match;
    }

    static boolean isActivity(Class<?> leafType) {
        Boolean match = isActCache.get(leafType);
        if (match == null) {
            match = Activity.class.isAssignableFrom(leafType);
            isActCache.put(leafType, match);
        }
        return match;
    }

    static boolean isFragment(Class<?> leafType) {
        Boolean match = isFragCache.get(leafType);
        if (match == null) {
            match = (android.app.Fragment.class.isAssignableFrom(leafType) || androidx.fragment.app.Fragment.class.isAssignableFrom(leafType));
            isFragCache.put(leafType, match);
        }
        return match;
    }

    static boolean isService(Class<?> leafType) {
        Boolean match = isServCache.get(leafType);
        if (match == null) {
            match = Service.class.isAssignableFrom(leafType);
            isServCache.put(leafType, match);
        }
        return match;
    }

    static boolean isContext(Class<?> leafType) {
        Boolean match = isContextCache.get(leafType);
        if (match == null) {
            match = Context.class.isAssignableFrom(leafType);
            isContextCache.put(leafType, match);
        }
        return match;
    }

    final Lazy findLazyByInstance(Object instance) {
        // work-around for WeakHashMap Iterators being fast-fail
        List<Object> scopedObjects = new ArrayList<>(injector.lazyCache.keySet());

        //for ( Object scopeObject : injector.lazyCache.keySet() ) {
        for (Object scopeObject : scopedObjects) {
            if (scopeObject != null) {
                WeakHashMap<Object, Lazy> parentToLazies = injector.lazyCache.get(scopeObject);
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


    final void rememberLazyByInstance(Object instance, Lazy lazy) {
        Object scopeObject = lazy.toObjectScope();
        if (scopeObject == null) {
            scopeObject = lazy.getContext();
        }
        WeakHashMap<Object, Lazy> parentToLazies = injector.lazyCache.get(scopeObject);
        if (parentToLazies == null) {
            parentToLazies = new WeakHashMap<>();
            injector.lazyCache.put(scopeObject, parentToLazies);
        }
        parentToLazies.put(instance, lazy);
    }

    static final WeakHashMap<Object, Fragment> mMasq = new WeakHashMap<>();

    /**
     * Call before impersonator has been ignited
     *
     * @param impersonator
     * @param impersonated
     */
    public static final void masqueradeAs(Object impersonator, Fragment impersonated) {
        mMasq.put(impersonator, impersonated);
    }

    /**
     * Associates a context to the given instance so that its injections may find it in the dependency hierarchy.<br>
     * Also dequeues any injections that were queued up due to a context not being known at the time of Lazy.attain.<br>
     * <br>
     * NOTE:<br>
     * You may skip {@link #ignite(Context, Object)} when a the object has is mapped to a context via {@link FuelModule#provideContext(Object)}<br>
     * One exception to this rule is that injections are always queued up until Fuel has been initialized.<br>
     */
    public static final void ignite(Context context, Object instance) {
        try {
            if (instance == null) {
                return;
            }

            // Bail out if in edit mode
            if (instance instanceof View) {
                if (((View) instance).isInEditMode()) {
                    return;
                }
            }

            if (isDebug()) {
                FLog.leaveBreadCrumb("ignite %s w/ %s", instance, context);
            }

            // Services use App as their scope
            if (instance instanceof Service) {
                return;
            }

            // skip wrappers
            context = toContext(context);

            // whatever this is we're igniting, it wasn't injected so lets artificially create a lazy for it
            // so that its children can find their parent
            //rememberContext( instance, context );

            Object masq = mMasq.get(instance);
            if (masq == null) {
                boolean didInitNewInst = false;
                Lazy lazyInstance = injector.findLazyByInstance(instance);
                if (lazyInstance == null) {
                    lazyInstance = Lazy.newEmptyParent(instance);
                }

                if (!Lazy.isPreProcessed(lazyInstance)) {
                    doPreProcessParent(lazyInstance, context);
                }

                // In the case of a service, we need to plug it into the cache after it calls ignite because we cant construct it
                if (isService(instance.getClass())) {
                    CacheKey key = CacheKey.attain(instance.getClass());
                    injector.putObjectByContextType(lazyInstance, key, instance);
                }
                // Don't try to instantiate services
                else {
                    if (!Lazy.isPostProcessed(lazyInstance)) {
                        getFuelModule().initializeNewInstance(lazyInstance);
                        didInitNewInst = true;
                    }
                }

                // hacky :(
                if (!didInitNewInst) {
                    getFuelModule().doOnFueled(lazyInstance, true);
                }
            } else {
                // more hacky -- make better later // FIXME: @aaronharris 6/7/17
                Lazy lazyMasq = injector.findLazyByInstance(masq);

                Collection<Lazy> queue = getPreprocessQueue(instance, true);
                if (queue.size() > 0) {
                    for (Lazy lazy : queue) {
                        lazy.setParent(lazyMasq.getInstance());
                        doPreProcessChild(lazy, lazyMasq);
                    }
                    queue.clear();
                }
            }
        } catch (Exception e) {
            throw FuelInjector.doFailure(null, e);
        }
    }

    private static Collection<Lazy> getPreprocessQueue(final Object parent, boolean readonly) {
        synchronized (parent) {
            Queue<Lazy> queue = injector.preprocessQueue.get(parent);
            if (queue != null) {
                return queue;
            } else if (!readonly) {
                queue = new LinkedList<Lazy>();
                injector.preprocessQueue.put(parent, queue);
                return queue;
            }
            return Collections.emptyList();
        }
    }

    /**
     * @param parent must have a lazy
     * @param lazy
     */
    static void enqueueLazy(Object parent, Lazy lazy) {
        synchronized (parent) {
            Collection<Lazy> queue = getPreprocessQueue(parent, false);
            queue.add(lazy);
        }
    }

    /**
     * @param parent lazy must have an instance - aka postProcessed
     * @throws FuelUnableToObtainContextException
     * @throws FuelScopeViolationException
     */
    static void dequeuePreProcesses(final Lazy parent) throws FuelUnableToObtainContextException, FuelScopeViolationException {
        synchronized (parent) {
            if (parent.getInstance() == null) {
                throw new FuelInvalidParentException("ParentLazy has no instance but attempting to dequeue children. Parent=%s", parent);
            }
            Collection<Lazy> queue = getPreprocessQueue(parent.getInstance(), true);
            if (queue.size() > 0) {
                for (Lazy lazy : queue) {
                    doPreProcessChild(lazy, parent);
                }
                queue.clear();
            }
        }
    }

    static void doServicePreProcess(Lazy lazy) {
        Context context = lazy.getContext();

        if (lazy.isDebug()) {
            FLog.leaveBreadCrumb("doServicePreProcess for %s, context is %s", lazy, context == null ? "null" : context.getClass().getSimpleName());
        }

        // in the special case of a service, we need to spawn it now so that its ready when we call get
        // FLog.d( "SERVICE: doServicePreProcess Service: %s", lazy.leafType.getSimpleName() );

        if (isService(lazy.leafType)) {
            CacheKey key = CacheKey.attain(lazy.leafType);
            // FLog.d( "SERVICE: doServicePreProcess get Service: %s", lazy.leafType.getSimpleName() );
            Object service = getServiceInstance(lazy, key, false);
            if (service != null) {
                // FLog.d( "SERVICE: doServicePreProcess got Service: %s = %s", lazy.leafType.getSimpleName(), service );
                lazy.setInstance(service);
            } else {
                // FLog.d( "SERVICE: Starting Service: %s", lazy.leafType.getSimpleName() );
                FuelInjector.getApp().startService(new Intent(FuelInjector.getApp(), lazy.leafType));
            }
        }
    }

    static void doPreProcessParent(Lazy parent, Context context) {
        if (FuelInjector.isDebug()) {
            FLog.leaveBreadCrumb("pre-process parent %s, %s", parent, context);
        }
        doPreProcessCommon(parent, context);
        Scope contextScope = determineScope(parent.getContext().getClass());
        parent.scope = determineScope(parent.leafType);
        if (Scope.Object.equals(parent.scope)) { // Object scopes should inherit their parent scope
            parent.scope = contextScope;
        }
        validateScope(parent.scope, contextScope);
    }

    static void doPreProcessCommon(Lazy lazy, Context context) {
        Context lazyContext = context;
        lazy.setLeafType(FuelInjector.toLeafType(lazy.type, lazy.getFlavor()));

        // Override with App Context if App Singleton to be safe
        if (isAppSingleton(lazy.leafType)) {
            lazyContext = getApp();
        }

        lazy.setContext(lazyContext, false);
        lazy.preProcessed = true;
    }

    /**
     * Process child prior to child-lazy's knowledge of it's instance
     * We initialize the child-lazy now that we know the context.
     * We call this PreProcess because its before the parent has called child-lazy.get()
     * though don't be confused, this is AFTER the lazy has been de-queued.
     *
     * @param child  - assumes not yet preProcessed
     * @param parent - must be postProcessed and have instance/context
     */
    static void doPreProcessChild(Lazy child, Lazy parent) throws FuelUnableToObtainContextException, FuelScopeViolationException {
        if (FuelInjector.isDebug()) {
            FLog.leaveBreadCrumb("pre-process child %s, %s", child, parent);
        }
        Context context = parent.getContext();

        doPreProcessCommon(child, context);
        child.scope = determineScope(child.leafType);
        if (Scope.Object.equals(child.scope)) { // Object scopes should inherit their parent scope
            child.scope = parent.scope;
        }
        validateScope(parent.scope, child.scope);
        child.inheritScopeRef(parent);

        if (child.isDebug()) {
            FLog.leaveBreadCrumb("doPreProcessChild for %s, context ended up with %s", child, context == null ? "null" : context.getClass().getSimpleName());
        }

        if (isService(child.leafType)) {
            doServicePreProcess(child);
        }
    }

    static Scope determineScope(Class leafType) {
        if (leafType != null) {
            // ordered by precedence
            if (isFragmentSingleton(leafType)) {
                return Scope.Fragment;
            } else if (isFragment(leafType)) {
                return Scope.Fragment;
            } else if (isActivitySingleton(leafType)) {
                return Scope.Activity;
            } else if (isActivity(leafType)) {
                return Scope.Activity;
            } else if (isAppSingleton(leafType)) {
                return Scope.Application;
            } else if (isApplication(leafType)) {
                return Scope.Application;
            } else if (isContext(leafType)) {
                return Scope.Application;
            }
        }
        return Scope.Object;
    }

    /**
     * Can a access b ?
     *
     * @param a
     * @param b
     * @throws FuelScopeViolationException
     */
    static void validateScope(Scope a, Scope b) throws FuelScopeViolationException {
        if (a == null || b == null || !a.canAccess(b)) {
            throw new FuelScopeViolationException("Fuel Scope Violation: %s cannot access %s", a, b);
        }
    }

    /**
     * after we have an instance
     *
     * @param lazy must have an instance
     */
    static void doPostProcess(Lazy lazy) throws FuelUnableToObtainContextException, FuelScopeViolationException {
        if (FuelInjector.isDebug()) {
            FLog.leaveBreadCrumb("post-process %s", lazy);
        }
        injector.rememberLazyByInstance(lazy.getInstance(), lazy);
        lazy.postProcessed = true; // before processing queue bcuz this lazy is done and its children should consider it done
        dequeuePreProcesses(lazy);
    }

    /**
     * Associate the given {@link FuelModule} to the {@link FuelInjector}<br>
     * The {@link FuelInjector} will behave based on the {@link FuelModule} overrides allowing
     * for detailed customization or alternate bindings between Test, Debug and Release flavored modules.<br>
     * <br>
     * Required to be called before any injections are expected to work.<br>
     * Best to call from App.onCreate()
     */
    @MainThread
    public static final void initializeModule(FuelModule fuelModule) throws FuelUnableToObtainContextException, FuelScopeViolationException {
        if (FuelInjector.app != null) {
            FLog.w("initializeModules called again -- be careful!");

            // We do this to support resetting the the fuel module for testing
            // anything that was registered can now unregister itself before death
            // its not pretty but it works
            if (injector.fuelModule != null) {
                injector.fuelModule.prepareForDeath();
            }
        }

        injector.mainThreadId = Thread.currentThread().getId();
        FuelInjector.app = fuelModule.getApplication();

        fuelModule.setActivityCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                try {
                    FuelInjector.activity = activity;
                    ignite(activity, activity);
                } catch (Exception e) {
                    FLog.e(e);
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
                FuelInjector.activity = activity;
            }

            @Override
            public void onActivityResumed(Activity activity) {
                FuelInjector.activity = activity;
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });

        injector.fuelModule = fuelModule;
        fuelModule.configure();

        ignite(app, app);
    }

    static final <T> T attain(Context context, Class<T> type) {
        return attain(context, type, CacheKey.DEFAULT_FLAVOR);
    }

    static final <T> T attain(Context context, Class<T> type, Integer flavor) {
        try {
            Lazy<T> lazy = Lazy.attain(context, type, flavor);
            return attainInstance(CacheKey.attain(lazy), lazy, true);
        } catch (Exception e) {
            FLog.e("Unable to attain instance of %s", type);
            throw new IllegalStateException(e);
        }
    }

    /**
     * Does not attain, only returns an item that is already in the cache or null.
     */
    @Nullable private static final <T> T findInstance(Context context, Class<T> type) {
        return getInstance(context, CacheKey.attain(type), null, false); // FIXME lazy cant be null
    }

    /**
     * Does not attain, only returns an item that is already in the cache or null.
     */
    @Nullable private static final <T> T findInstance(Context context, Class<T> type, Integer flavor) {
        return getInstance(context, CacheKey.attain(type, flavor), null, false); // FIXME lazy cant be null
    }

    static final <T> T getInstance(Context context, CacheKey key, Lazy lazy, boolean debug) {
        // if ( Application.class.isAssignableFrom( key.getLeafType() ) ) {
        // return (T) getApp();
        // } else if ( Activity.class.isAssignableFrom( key.getLeafType() ) ) {
        //
        // // FIXME: FUEL this breaks when injecting an Activity from a Module using the App context :(
        // if ( context instanceof Activity ) {
        // return (T) context;
        // } else {
        // // throw new IllegalStateException( "You're trying to create a context singleton with an app context -- not cool" );
        // FLog.w( "You are injecting an Activity from an Application Context, this will result in the 'Active' Activity." );
        // return (T) getActivity();
        // }
        // } else if ( Activity.class.isAssignableFrom( key.getLeafType() ) || Application.class.isAssignableFrom( key.getLeafType() ) ) {
        // return (T) context;
        // }

        // a service wants a context
        // -- context = service or app
        // -- leaf = Context, App or Activity

        // an activity wants a service
        // -- context = activity or app
        // -- leaf = service

        if (isApplication(key.getLeafType())) {
            if (debug) {
                FLog.leaveBreadCrumb("getInstance for App got %s", getApp() == null ? "null" : getApp().getClass().getSimpleName());
            }
            return (T) getApp();
        } else if (isFragment(key.getLeafType())) {
            //WeakReference fragRef = lazy.getScopeObjectRef();
            //return (T) ( fragRef == null ? null : fragRef.get() );
            return (T) lazy.toObjectScope();
        } else if (isActivity(key.getLeafType()) && context instanceof Activity) {
            if (debug) {
                FLog.leaveBreadCrumb("getInstance for Activity got %s", context == null ? "null" : context.getClass().getSimpleName());
            }
            return (T) context;
        } else if (isService(key.getLeafType())) {
            final T serviceInstance = getServiceInstance(lazy, key, true);
            if (debug) {
                FLog.leaveBreadCrumb("getInstance for Service got %s", serviceInstance == null ? "null" : serviceInstance.getClass().getSimpleName());
            }
            return serviceInstance;
        } else if (isContext(key.getLeafType())) {
            if (debug) {
                FLog.leaveBreadCrumb("getInstance for Context got %s", context == null ? "null" : context.getClass().getSimpleName());
            }
            return (T) context;
        }

        final T objectByContextType = (T) injector.getObjectByContextType(lazy, key);
        if (debug) {
            FLog.leaveBreadCrumb("getInstance getObjectByContextType got %s", objectByContextType == null ? "null" : objectByContextType.getClass().getSimpleName());
        }

        return objectByContextType;
    }

    // FIXME: need an attainService and getService
    // Currently ignite is talking directly to the cache and is messy :(
    private static final <T> T getServiceInstance(Lazy lazy, CacheKey key, boolean willingToWait) {
        boolean inMainThread = inMainThread();
        long maxTimeMillis = 1000;
        long sleepTimeMillis = 20;
        long startTime = System.currentTimeMillis();
        int loopCount = 0;
        long duration = 0;
        do {
            try {
                ++loopCount;

                T object = (T) injector.getObjectByContextType(lazy, key);
                if (object != null) {
                    // FLog.d( "SERVICE: getInstance[%s][%s] of %s got %s", loopCount, duration, key.getLeafType().getSimpleName(), object );
                    return object;
                }

                if (!inMainThread && willingToWait) {
                    duration = System.currentTimeMillis() - startTime;
                    if (duration <= (maxTimeMillis - sleepTimeMillis)) {
                        try {
                            Thread.sleep(sleepTimeMillis);
                        } catch (InterruptedException e) {
                            FLog.e(e);
                        }
                    } else {
                        // FLog.d( "SERVICE: getInstance[%s][%s] of %s got %s", loopCount, duration, key.getLeafType().getSimpleName(), null );
                        return null;
                    }
                } else {
                    // FIXME: FUEL most likely a lazy attain but i'm not comfortable with this yet...
                    // lets test calling lazyService.get() in the main thread -- i hope it will just do everything synchronously and end up with a
                    // service
                    // but if not, consider allowing the polling to run for some small amount of time on the main thread?
                    // FLog.d( "SERVICE: getInstance (Main Thread) of %s got %s", key.getLeafType().getSimpleName(), null );
                    return null;
                }
            } catch (Exception e) {
                FLog.e(e);
                return null;
            }
        } while (true);
    }

    // safe because we hash by type
    @SuppressWarnings("unchecked")
    static final <T> T newInstance(CacheKey key, Lazy lazy, boolean allowAnonymousNewInstance) throws FuelInjectionException {
        try {
            T object = null;
            if (isSingleton(lazy.leafType)) {
                if (lazy.isDebug()) {
                    FLog.leaveBreadCrumb("newInstance for singleton %s", lazy);
                }
                synchronized (lazy.leafType) {
                    object = (T) injector.getObjectByContextType(lazy, key);
                    if (lazy.isDebug()) {
                        FLog.leaveBreadCrumb("newInstance getObjectByContextType returned %s for %s",
                                object == null ? "null" : object.getClass().getSimpleName(),
                                lazy);
                    }
                    if (object == null) { // safety check in case another thread did the work while we were waiting
                        object = (T) injector.fuelModule.obtainInstance(lazy, allowAnonymousNewInstance);
                        if (lazy.isDebug()) {
                            FLog.leaveBreadCrumb("newInstance obtainInstance returned %s for %s",
                                    object == null ? "null" : object.getClass().getSimpleName(),
                                    lazy);
                        }
                        if (object != null) {
                            injector.putObjectByContextType(lazy, key, object);
                        }
                    }
                }
            } else {
                if (lazy.isDebug()) {
                    FLog.leaveBreadCrumb("newInstance for non-singleton leaf: %s, type: %s",
                            lazy.leafType == null ? "null" : lazy.leafType.getSimpleName(),
                            lazy);
                }
                object = (T) injector.fuelModule.obtainInstance(lazy, allowAnonymousNewInstance);
            }
            if (lazy.isDebug()) {
                FLog.leaveBreadCrumb("newInstance returning %s for leaf type of lazy: %s",
                        object == null ? "null" : object.getClass().getSimpleName(), lazy);
            }
            return object;
        } catch (FuelInjectionException e) {
            throw e;
        } catch (Exception e) {
            if (lazy.isDebug()) {
                FLog.leaveBreadCrumb("newInstance Exception %s", e.getMessage());
            }
            throw new FuelInjectionException(e);
        }
    }

    static final <T> T attainInstance(CacheKey key, Lazy<T> lazy, boolean allowAnonymousNewInstance) throws FuelInjectionException {
        try {
            if (lazy.isDebug()) {
                FLog.leaveBreadCrumb("attainInstance for key: %s and lazy: %s", key, lazy);
            }

            T obj = getInstance(lazy.getContext(), key, lazy, lazy.isDebug()); // go into getInstance here. grrrr.
            if (lazy.isDebug()) {
                FLog.leaveBreadCrumb("attainInstance getInstance returned %s", obj == null ? "null" : obj.getClass().getSimpleName());
            }

            if (obj == null) {
                obj = newInstance(key, lazy, allowAnonymousNewInstance);
            }
            if (lazy.isDebug()) {
                FLog.leaveBreadCrumb("attainInstance ended up with %s", obj == null ? "null" : obj.getClass().getSimpleName());
            }
            return obj;
        } catch (FuelInjectionException e) {
            throw FuelInjector.doFailure(lazy, e);
        } catch (Exception e) {
            if (lazy.isDebug()) {
                FLog.leaveBreadCrumb("attainInstance Exception: %s", e.getMessage());
            }
            throw FuelInjector.doFailure(lazy, e);
        }
    }

    static FuelInjectionException doFailure(Lazy lazy, Exception exception) {
        return doFailure(lazy, new FuelInjectionException(exception));
    }

    static FuelInjectionException doFailure(Lazy lazy, FuelInjectionException exception) {
        if (isInitialized()) {
            getFuelModule().onFailure(lazy, exception);
            return exception;
        }
        throw exception;
    }

    /**
     * The Process Id Fuel was {@link #initializeModule(FuelModule)} with<br>
     * It is required that Fuel be initialized from the main thread.
     *
     * @return id of the main thread.
     */
    public static final long getPid() {
        return injector.mainThreadId;
    }

    /**
     * @return true when the calling thread is the same threadId that called {@link #initializeModule(FuelModule)}
     */
    public static final boolean inMainThread() {
        long id = Thread.currentThread().getId();
        if (id != FuelInjector.getPid()) {
            return false;
        }
        return true;
    }

    private static long mainThreadId;
    private FuelModule fuelModule;
    //	private final WeakHashMap<Context, Map<CacheKey, Object>> cache = new WeakHashMap<>(); // context to injectable

    // Scope -> ScopeObject -> CacheKey -> instance
    // - Scope is Application, Activity, Fragment, etc
    // - ScopeObject would be the context, or the fragment, paired to the scope
    // - CacheKey describes the instance we're looking for
    // - instance the hidden treasure
    private final Map<Scope, WeakHashMap<Object, Map<CacheKey, Object>>> scopeCache = new HashMap<>();
    private final WeakHashMap<Object, Queue<Lazy>> preprocessQueue = new WeakHashMap<>(); // LazyParent -> Queue<LazyChildren>
    private final Map<Object, WeakHashMap<Object, Lazy>> lazyCache = Collections.synchronizedMap(new WeakHashMap<Object, WeakHashMap<Object, Lazy>>());

    // map (not WeakReference of injectable)
    private final WeakHashMap<Context, WeakReference<Context>> contextToWeakContextCache = new WeakHashMap<Context, WeakReference<Context>>();
    static final long startTimeMillis = System.currentTimeMillis();

    private FuelInjector() {
    }

    /**
     * Null when not yet initialized
     */
    static FuelModule getFuelModule() {
        if (injector != null) {
            return injector.fuelModule;
        }
        return null;
    }

    /**
     * @param primeTheCacheEntry if true, an empty entry will be added for this context if not already present, false leaves it alone and returns
     *                           empty
     * @return an "immutable" map when primeTheCachEntry = false :/ meh
     */
    private Map<CacheKey, Object> getCacheByContextNotThreadSafe(Lazy lazy, boolean primeTheCacheEntry) {
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

    // FIXME: only call if isSingleton !

    private Object getObjectByContextType(Lazy lazy, CacheKey key) {
        Lock lock = cacheLock.readLock();
        try {
            lock.lock();
            Map<CacheKey, Object> contextCache = getCacheByContextNotThreadSafe(lazy, false);
            Object obj = contextCache.get(key);
            return obj;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param value instance not lazy, cannot be null
     */
    private void putObjectByContextType(Lazy lazy, CacheKey key, Object value) {
        Lock lock = cacheLock.writeLock();
        try {
            lock.lock();
            Map<CacheKey, Object> contextCache = getCacheByContextNotThreadSafe(lazy, true);
            contextCache.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    public static void debugInjectionGraph() {
        try {
            FLog.dSimple("####");
            FLog.dSimple("####");
            FLog.dSimple("#### Scope->ScopeObject->Key->Instance Cache");
            Map<Scope, WeakHashMap<Object, Map<CacheKey, Object>>> scopeCache = injector.scopeCache;
            for (Scope scope : scopeCache.keySet()) {
                FLog.dSimple("Scope = %s", scope);
                WeakHashMap<Object, Map<CacheKey, Object>> scopeToKeyToObject = scopeCache.get(scope);
                for (Object scopeObject : scopeToKeyToObject.keySet()) {
                    FLog.dSimple(" - ScopeObject = %s", scopeObject);
                    Map<CacheKey, Object> keyToObject = scopeToKeyToObject.get(scopeObject);
                    for (CacheKey key : keyToObject.keySet()) {
                        FLog.dSimple(" - - Key = %s", key);
                        Object obj = keyToObject.get(key);
                        FLog.dSimple(" - - - Instance = %s", obj);
                    }
                }
            }

            FLog.dSimple("####");
            FLog.dSimple("#### ScopeObject->Parent->LazyInstance Lookup");
            Map<Object, WeakHashMap<Object, Lazy>> lazyCache = injector.lazyCache;
            for (Object scopeObject : lazyCache.keySet()) {
                FLog.dSimple("ScopeObject = %s", scopeObject);
                WeakHashMap<Object, Lazy> parentToLazy = lazyCache.get(scopeObject);
                for (Object parent : parentToLazy.keySet()) {
                    FLog.dSimple(" - Parent = %s", parent);
                    Lazy lazy = parentToLazy.get(parent);
                    FLog.dSimple(" - - LazyInstance = %s", lazy);
                }
            }

            FLog.dSimple("####");
            FLog.dSimple("#### QueueOwner->Lazies Pending Pre-process");
            WeakHashMap<Object, Queue<Lazy>> preprocessQueue = injector.preprocessQueue;
            for (Object parent : preprocessQueue.keySet()) {
                FLog.dSimple("Owner = %s", parent);
                Queue<Lazy> lazies = preprocessQueue.get(parent);
                for (Lazy lazy : lazies) {
                    FLog.dSimple(" - Lazy = %s", lazy);
                }
            }
        } catch (Exception e) {
            FLog.e(e, "Failure while logging injection graph");
        }
    }

}
