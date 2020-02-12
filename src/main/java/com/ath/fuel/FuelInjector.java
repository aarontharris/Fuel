package com.ath.fuel;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Preconditions;

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

@SuppressWarnings({"unchecked", "WeakerAccess", "FinalPrivateMethod", "FinalStaticMethod", "unused", "UnusedAssignment", "SameParameterValue"})
public final class FuelInjector {
    @NonNull static final FuelInjector injector = new FuelInjector();

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

    public static @NonNull FuelInjector get() {
        return injector;
    }

    private Application app;
    private long mainThreadId;
    private FuelModule rootModule;
    private final WeakHashMap<Object, Queue<Lazy>> preprocessQueue = new WeakHashMap<>(); // LazyParent -> Queue<LazyChildren>
    private final WeakHashMap<Context, WeakReference<Context>> contextToWeakContextCache = new WeakHashMap<>();
    private final Map<Object, WeakHashMap<Object, Lazy>> lazyCache = Collections.synchronizedMap(new WeakHashMap<Object, WeakHashMap<Object, Lazy>>());

    private final long startTimeMillis = System.currentTimeMillis();
    private static boolean isDebug = false;

    private FuelInjector() {
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
    private final void initializeModule(Object owner, FuelModule fuelModule) throws FuelUnableToObtainContextException, FuelScopeViolationException {
        //initializeModule(owner, fuelModule, false);
    }


    @VisibleForTesting
    @MainThread
    private final void initializeModule(Object owner, FuelSubmodule fuelModule, boolean insertAtFront) throws FuelUnableToObtainContextException, FuelScopeViolationException {
        if (insertAtFront && isInitialized()) {
            throw new UnsupportedOperationException("insertAtFront not supported yet");
        /*
            TODO: inserAtFront for testing
            //FLog.w("initializeModules called again -- be careful!");
            // We do this to support resetting the the fuel module for testing
            // anything that was registered can now unregister itself before death
            // its not pretty but it works

            // REMOVE callbacks from the old root as we only maintain one set of callbacks per tree and then delegate down

            //if (injector.rootModule != null) { injector.rootModule.prepareForDeath(); }
            FuelModule prev = injector.rootModule;
            injector.rootModule = rootModule;
            injector.rootModule.addSubModule(prev);
         */
        }

        if (!isInitialized()) {

            // TODO: make sure the owner is App
            // TODO: associate owner to module

            // this is our first module

            //noinspection AccessStaticViaInstance
            mainThreadId = Thread.currentThread().getId();
            injector.rootModule = fuelModule;
        } else {
            fuelModule.addSubModule(fuelModule);
        }

        fuelModule.configure();
    }

    @MainThread
    public final void ignite(@NonNull Application app, @NonNull FuelModule rootModule) {
        if (this.rootModule == null) {
            this.rootModule = rootModule;
            this.app = app;
            mainThreadId = Thread.currentThread().getId();
            this.rootModule.configure();
            ignite(app, app);
        }
    }

    @MainThread
    public final void ignite(@NonNull Application app, @NonNull FuelSubmodule module) {
        if (getRootModule().containsModule(module)) {
            return;
        }
        getRootModule().addSubModule(module);
        module.configure();
        ignite(app, app);
    }

    @MainThread
    public final void ignite(@NonNull Service service) {
        // ignite(service, service);  // TODO
    }

    @MainThread
    public final void ignite(@NonNull Activity activity, @NonNull FuelSubmodule module) {
        ignite(activity);
    }

    @MainThread
    public final void ignite(@NonNull Activity activity) {
        ignite(activity, activity);
    }

    @MainThread
    public final void ignite(@NonNull View view, @NonNull FuelSubmodule module) {
        if (view.isInEditMode()) {
            return; // Bail out if in edit mode
        }
        ignite(view);
    }

    @MainThread
    public final void ignite(@NonNull View view) {
        if (view.isInEditMode()) {
            return; // Bail out if in edit mode
        }

        //noinspection ConstantConditions
        ignite(view.getContext(), view);
    }

    /**
     * Associates a context to the given instance so that its injections may find it in the dependency hierarchy.<br>
     * Also dequeues any injections that were queued up due to a context not being known at the time of Lazy.attain.<br>
     * <br>
     * NOTE:<br>
     * You may skip ignite() when a the object has is mapped to a context via {@link FuelModule#provideContext(Object)}<br>
     * One exception to this rule is that injections are always queued up until Fuel has been initialized.<br>
     */
    @MainThread
    public final void ignite(@NonNull Context context, @NonNull Object instance) {
        try {
            if (isDebug()) {
                FLog.leaveBreadCrumb("ignite %s w/ %s", instance, context);
            }

            // skip wrappers
            context = toContext(context);

            boolean didInitNewInst = false;
            Lazy lazyInstance = findLazyByInstance(instance);
            if (lazyInstance == null) {
                lazyInstance = Lazy.newInstanceIgnited(context, instance);
            }

            if (!Lazy.isPreProcessed(lazyInstance)) {
                doPreProcessParent(lazyInstance, context);
            }

            // In the case of a service, we need to plug it into the cache after it calls ignite because we cant construct it
            if (isService(instance.getClass())) {
                CacheKey key = CacheKey.attain(instance.getClass());
                getRootModule().putObjectByContextType(lazyInstance, key, instance);
            }
            // Don't try to instantiate services
            else {
                if (!Lazy.isPostProcessed(lazyInstance)) {
                    getRootModule().initializeNewInstance(lazyInstance);
                    didInitNewInst = true;
                }
            }

            // hacky :(
            if (!didInitNewInst) {
                getRootModule().doOnFueled(lazyInstance, true);
            }
        } catch (Exception e) {
            throw doFailure(null, e);
        }
    }

    /**
     * True will tighten up tolerances for quicker failures and more verbosity
     */
    public final boolean isDebug() {
        return isDebug;
    }

    /**
     * True will tighten up tolerances for quicker failures and more verbosity
     */
    public final void setDebug(boolean debug) {
        isDebug = debug;
    }

    /**
     * @return null if the FuelInjector has not yet been {@link #isInitialized()}
     */
    public final @NonNull Application getApp() {
        return Preconditions.checkNotNull(app);
    }

    @NonNull WeakReference<Context> getContextRef(@NonNull Context context) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (context) { // synchronized on context because we dont want to hold up an activity when a background service is working
            Context realContext = toContext(context);
            WeakReference<Context> out = contextToWeakContextCache.get(realContext);
            if (out == null || out.get() == null) {
                out = new WeakReference<>(realContext);
                contextToWeakContextCache.put(realContext, out);
            }
            return out;
        }
    }

    /**
     * Get the real context when the given context is a wrapper
     *
     * @param context - ambiguous context
     */
    @SuppressWarnings("StatementWithEmptyBody") final @NonNull Context toContext(@NonNull Context context) {
        Context out = context;
        if (context instanceof Activity) {
            // no-op
        } else if (context instanceof Application) {
            // no-op
        } else if (context instanceof ContextWrapper) { // some other foreign context
            out = toContext(((ContextWrapper) context).getBaseContext());
        }
        return context;
    }

    /**
     * @return True after {@link #initializeModule(Object, FuelModule)}
     */
    public boolean isInitialized() {
        return rootModule != null;
    }

    /**
     * Null when not yet {@link #isInitialized()}
     */
    public @NonNull FuelModule getRootModule() throws FuelInjectionException { // FIXME: Submodule
        FuelModule module = rootModule;
        if (module == null) {
            throw new FuelInjectionException("FuelModule not initialized");
        }
        return module;
    }

    public @NonNull FuelModule findModule(@NonNull Lazy<?> lazy) {
        return getRootModule().findModule(lazy);
    }

    final boolean isAppSingleton(Class<?> leafType) {
        Boolean singleton = isAppSingletonCache.get(leafType);
        if (singleton == null) {
            singleton = leafType.isAnnotationPresent(AppSingleton.class);
            isAppSingletonCache.put(leafType, singleton);
        }
        return singleton;
    }

    final boolean isActivitySingleton(Class<?> leafType) {
        Boolean singleton = isActSingletonCache.get(leafType);
        if (singleton == null) {
            singleton = leafType.isAnnotationPresent(ActivitySingleton.class);
            isActSingletonCache.put(leafType, singleton);
        }
        return singleton;
    }

    final boolean isSingleton(Class<?> leafType) {
        Boolean singleton = isSingletonCache.get(leafType);
        if (singleton == null) {
            singleton = (isAppSingleton(leafType) || isActivitySingleton(leafType));
            isSingletonCache.put(leafType, singleton);
        }
        return singleton;
    }

    final boolean isInjectionRequired(Class<?> leafType) {
        Boolean match = isInjectionRequired.get(leafType);
        if (match == null) {
            match = leafType.isAnnotationPresent(RequiresInjection.class);
            isInjectionRequired.put(leafType, match);
        }
        return match;
    }

    boolean isApplication(Class<?> leafType) {
        Boolean match = isAppCache.get(leafType);
        if (match == null) {
            match = Application.class.isAssignableFrom(leafType);
            isAppCache.put(leafType, match);
        }
        return match;
    }

    boolean isActivity(Class<?> leafType) {
        Boolean match = isActCache.get(leafType);
        if (match == null) {
            match = Activity.class.isAssignableFrom(leafType);
            isActCache.put(leafType, match);
        }
        return match;
    }

    boolean isFragment(Class<?> leafType) {
        Boolean match = isFragCache.get(leafType);
        if (match == null) {
            match = (android.app.Fragment.class.isAssignableFrom(leafType) || androidx.fragment.app.Fragment.class.isAssignableFrom(leafType));
            isFragCache.put(leafType, match);
        }
        return match;
    }

    boolean isService(Class<?> leafType) {
        Boolean match = isServCache.get(leafType);
        if (match == null) {
            match = Service.class.isAssignableFrom(leafType);
            isServCache.put(leafType, match);
        }
        return match;
    }

    boolean isContext(Class<?> leafType) {
        Boolean match = isContextCache.get(leafType);
        if (match == null) {
            match = Context.class.isAssignableFrom(leafType);
            isContextCache.put(leafType, match);
        }
        return match;
    }

    /** reverse lookup to find a Lazy for a previously ignited/injected Object */
    final void rememberLazyByInstance(Object instance, Lazy lazy) {
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

    /** reverse lookup to find a Lazy for a previously ignited/injected Object */
    final @Nullable Lazy findLazyByInstance(Object instance) {
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

    private Collection<Lazy> getPreprocessQueue(final Object parent, boolean readonly) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (parent) {
            Queue<Lazy> queue = preprocessQueue.get(parent);
            if (queue != null) {
                return queue;
            } else if (!readonly) {
                queue = new LinkedList<>();
                preprocessQueue.put(parent, queue);
                return queue;
            }
            return Collections.emptyList();
        }
    }

    void enqueueLazy(Object parent, Lazy lazy) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter -- I know what I'm doing
        synchronized (parent) {
            Collection<Lazy> queue = getPreprocessQueue(parent, false);
            queue.add(lazy);
        }
    }

    /**
     * @param parent lazy must have an instance - aka postProcessed
     * @throws FuelUnableToObtainContextException -
     * @throws FuelScopeViolationException        -
     */
    void dequeuePreProcesses(@NonNull Lazy parent) throws FuelUnableToObtainContextException, FuelScopeViolationException {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter -- it's fine...
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

    void doServicePreProcess(Lazy lazy) {
        Context context = lazy.getContext();

        if (lazy.isDebug()) {
            FLog.leaveBreadCrumb("doServicePreProcess for %s, context is %s", lazy, context.getClass().getSimpleName());
        }

        // in the special case of a service, we need to spawn it now so that its ready when we call get
        // FLog.d( "SERVICE: doServicePreProcess Service: %s", lazy.leafType.getSimpleName() );

        if (isService(lazy.getLeafType())) {
            CacheKey key = CacheKey.attain(lazy.getLeafType());
            // FLog.d( "SERVICE: doServicePreProcess get Service: %s", lazy.leafType.getSimpleName() );
            Object service = getRootModule().getServiceInstance(lazy, key, false);
            if (service != null) {
                // FLog.d( "SERVICE: doServicePreProcess got Service: %s = %s", lazy.leafType.getSimpleName(), service );
                lazy.setInstance(service);
            } else {
                // FLog.d( "SERVICE: Starting Service: %s", lazy.leafType.getSimpleName() );
                Preconditions.checkNotNull(getApp()).startService(new Intent(getApp(), lazy.getLeafType()));
            }
        }
    }

    void doPreProcessParent(@NonNull Lazy parent, Context context) {
        if (isDebug()) {
            FLog.leaveBreadCrumb("pre-process parent %s, %s", parent, context);
        }
        doPreProcessCommon(parent, context);
        Scope contextScope = determineScope(parent.getContext().getClass());
        parent.scope = determineScope(parent.getLeafType());
        if (Scope.Object.equals(parent.scope)) { // Object scopes should inherit their parent scope
            parent.scope = contextScope;
        }
        validateScope(parent.scope, contextScope);
    }

    void doPreProcessCommon(@NonNull Lazy lazy, Context context) {
        Context lazyContext = context;
        lazy.setLeafType(findModule(lazy).toLeafType(lazy.type, lazy.getFlavor()));

        // Override with App Context if App Singleton to be safe
        if (isAppSingleton(lazy.getLeafType())) {
            lazyContext = getApp();
        }

        lazy.setContext(lazyContext);
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
    void doPreProcessChild(@NonNull Lazy child, Lazy parent) throws FuelUnableToObtainContextException, FuelScopeViolationException {
        if (isDebug()) {
            FLog.leaveBreadCrumb("pre-process child %s, %s", child, parent);
        }
        Context context = parent.getContext();

        doPreProcessCommon(child, context);
        child.scope = determineScope(child.getLeafType());
        if (Scope.Object.equals(child.scope)) { // Object scopes should inherit their parent scope
            child.scope = parent.scope;
        }
        validateScope(parent.scope, child.scope);
        child.inheritScopeRef(parent);

        if (child.isDebug()) {
            FLog.leaveBreadCrumb("doPreProcessChild for %s, context ended up with %s", child, context.getClass().getSimpleName());
        }

        if (isService(child.getLeafType())) {
            doServicePreProcess(child);
        }
    }

    Scope determineScope(Class leafType) {
        if (leafType != null) {
            // ordered by precedence
            if (isActivitySingleton(leafType)) {
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
     * @throws FuelScopeViolationException -
     */
    void validateScope(Scope a, Scope b) throws FuelScopeViolationException {
        //noinspection PointlessNullCheck -- it makes me feel better seeing it ok?
        if (a == null || b == null || !a.canAccess(b)) {
            throw new FuelScopeViolationException("Fuel Scope Violation: %s cannot access %s", a, b);
        }
    }

    /**
     * after we have an instance
     *
     * @param lazy must have an instance
     */
    void doPostProcess(Lazy lazy) throws FuelUnableToObtainContextException, FuelScopeViolationException {
        if (isDebug()) {
            FLog.leaveBreadCrumb("post-process %s", lazy);
        }
        rememberLazyByInstance(lazy.getInstance(), lazy);
        lazy.postProcessed = true; // before processing queue bcuz this lazy is done and its children should consider it done
        dequeuePreProcesses(lazy);
    }


    final <T> T attain(Context context, Class<T> type) {
        return attain(context, type, CacheKey.DEFAULT_FLAVOR);
    }

    final <T> T attain(Context context, Class<T> type, Integer flavor) {
        try {
            Lazy<T> lazy = Lazy.attain(context, type, flavor);
            return findModule(lazy).attainInstance(CacheKey.attain(lazy), lazy, true);
        } catch (Exception e) {
            FLog.e("Unable to attain instance of %s", type);
            throw new IllegalStateException(e);
        }
    }

    FuelInjectionException doFailure(Lazy lazy, @NonNull Exception exception) {
        return doFailure(lazy, new FuelInjectionException(exception));
    }

    FuelInjectionException doFailure(Lazy lazy, @NonNull FuelInjectionException exception) {
        if (isInitialized()) {
            if (lazy != null) {
                findModule(lazy).onFailure(lazy, exception);
            } else {
                getRootModule().onFailure(null, exception);
            }
        }
        throw exception;
    }

    public final long getPid() {
        //noinspection AccessStaticViaInstance
        return mainThreadId; // its only set once
    }

    public final boolean inMainThread() {
        long id = Thread.currentThread().getId();
        return id == getPid();
    }


    public void debugInjectionGraph() {
        /*
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
         */
    }

}
