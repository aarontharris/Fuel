package com.ath.fuel;

import android.app.Service;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Preconditions;

import com.ath.fuel.err.FuelInjectionException;
import com.ath.fuel.err.FuelUnableToObtainContextException;

import java.lang.ref.WeakReference;


@SuppressWarnings({"unchecked", "BooleanMethodIsAlwaysInverted", "WeakerAccess", "FinalPrivateMethod", "FinalStaticMethod", "unused", "UnusedAssignment"})
public final class Lazy<T> {

    public static @NonNull <TYPE> Lazy<TYPE> attain(Object parent, Class<TYPE> clazz) {
        return newInstance(parent, clazz, null);
    }

    public static @NonNull <TYPE> Lazy<TYPE> attain(Service parent, Class<TYPE> clazz) {
        return newInstance(FuelInjector.getApp(), clazz, null);
    }

    public static @NonNull <TYPE> Lazy<TYPE> attain(Object parent, Class<TYPE> clazz, Integer flavor) {
        return newInstance(parent, clazz, flavor);
    }

    private static @NonNull <TYPE> Lazy<TYPE> newInstance(View parent, Class<TYPE> clazz, Integer flavor) {
        Lazy<TYPE> lazy = new Lazy<>(clazz, flavor);
        lazy.isInEditMode = parent.isInEditMode();
        if (!lazy.isInEditMode) {
            preInitializeNewLazy(lazy, parent);
        }
        return lazy;
    }

    private static @NonNull <TYPE> Lazy<TYPE> newInstance(Object parent, Class<TYPE> clazz, Integer flavor) {
        Lazy<TYPE> lazy = new Lazy<>(clazz, flavor);
        preInitializeNewLazy(lazy, parent);
        return lazy;
    }

    /**
     * expected use case here is that the parent was just ignited
     */
    static @NonNull Lazy newEmptyParent(Object parent) {
        Lazy lazy = new Lazy(parent.getClass());
        lazy.useWeakInstance = true; // weak here because its expected that this parent was ignited
        lazy.setInstance(parent);

        if (FuelInjector.isFragment(parent.getClass())) {
            lazy.scopeObjectRef = new WeakReference(parent);
        }

        return lazy;
    }

    // FIXME: FUEL if called from background thread without a context, throw an error -- for now i'm just calling red to see them all
    static final void doThreadCheck(Lazy<?> lazy) {
        if (!FuelInjector.inMainThread()) {
            StackTraceElement elem = FLog.findStackElem(Lazy.class);
            FLog.e(new IllegalStateException(
                    "Attain Lazy " + lazy.getType() + " from a bg. " + FLog.getSimpleName(elem) + "@" + elem.getLineNumber()));
        }
    }

    // when ActivitySingleton in constructed
    // we come here for it's injectable AppSingleton before ActivitySingleton has its lazy remembered
    // So AppSingleton can't find its parent and ends up creating a new one
    // so AppSingleton is queued up under a temp lazy that never gets processed
    private static final <TYPE> void preInitializeNewLazy(Lazy<TYPE> lazy, Object parent) {
        Context context = null;
        Lazy lazyParent = null;

        lazy.parentRef = new WeakReference<>(parent);

        try {
            if (FuelInjector.isDebug()) {
                FLog.leaveBreadCrumb("initialize lazy %s, %s", lazy, parent);
            }

            if (FuelInjector.isInitialized()) {
                Preconditions.checkNotNull(FuelInjector.getFuelModule()); // never null when initialized
                // Hopefully this parent has been ignited already and we'll have a Lazy to show for it
                lazyParent = FuelInjector.getFuelModule().findLazyByInstance(parent);
                if (Lazy.isPreProcessed(lazyParent)) {
                    context = (Context) lazyParent.contextRef.get(); // not sure why this cast is necessary? AndroidStudio fail?

                    // Do pre-preocess because we know the parent-lazy and do not need to enqueue
                    FuelInjector.doPreProcessChild(lazy, lazyParent);
                }
            }
        } catch (FuelInjectionException e) {
            // Failed to work out the parent, lets just enqueue it
            lazy.contextRef = null; // reset
        }

        // Context context = FuelInjector.getFuelModule().provideContext( parent ); // TODO: this may be more trouble than its worth

        if (context == null) {
            // queue up this lazy until the parent is ignited
            FuelInjector.enqueueLazy(parent, lazy);
        }
    }

    boolean onFueledCalled = false;
    Scope scope;
    boolean preProcessed = false;
    boolean postProcessed = false;

    private WeakReference<Object> scopeObjectRef; // We do object bcuz we dont know if its v4.frag or just frag :/
    private WeakReference<Object> parentRef;

    Class<T> type; // the type requested, but not necessarily the type to be instantiated
    Class<?> leafType; // the type to be instantiated, not necessarily the type requested but some derivitive.
    private boolean typeIsContext = false;
    private boolean useWeakInstance = false;
    private T instance = null;
    private WeakReference<T> instanceRef; // for the cases we identify that we don't want to keep a strong ref to the instance
    private WeakReference<Context> contextRef;
    private final Integer flavor;
    private boolean isInEditMode;
    private boolean debug;

    private Lazy(Class<T> type) {
        this.type = type;
        this.typeIsContext = FuelInjector.isContext(type);
        this.useWeakInstance = this.useWeakInstance || this.typeIsContext; // don't override useWeakInstance if already true
        this.flavor = CacheKey.DEFAULT_FLAVOR;
    }

    private Lazy(Class<T> type, Integer flavor) {
        this.type = type;
        this.typeIsContext = FuelInjector.isContext(type);
        this.useWeakInstance = this.useWeakInstance || this.typeIsContext; // don't override useWeakInstance if already true
        this.flavor = flavor;
    }

    void inheritScopeRef(Lazy parent) {
        this.scopeObjectRef = parent.scopeObjectRef;
    }

    /**
     * Some scopes are consolidated into a shared cache use this to get the cacheScope based on the literal scope
     *
     * @return null indicates this lazy is not cacheable
     */
    @Nullable Scope toCacheScope() {
        switch (scope) {
            case Application:
            case Activity:
                return scope;
        }
        return null;
    }

    /**
     * @return null indicates this lazy is not cacheable
     */
    @Nullable Object toObjectScope() {
        Object scopeObject = null;
        Scope scope = toCacheScope();
        if (scope != null) {
            switch (scope) {
                case Application:
                case Activity:
                    scopeObject = getContext();
                    break;
            }
        }
        return scopeObject;
    }

    boolean isCacheable() {
        return toCacheScope() != null && toObjectScope() != null;
    }

    public Lazy setDebug() {
        this.debug = true;
        return this;
    }

    void setLeafType(Class<?> leafType) {
        this.leafType = leafType;
    }


    void setContext(Context context) {
        if (context != null) {
            this.contextRef = FuelInjector.getContextRef(FuelInjector.toContext(context));
        }
    }

    static boolean isPostProcessed(Lazy lazy) {
        if (lazy != null) {
            return lazy.postProcessed;
        }
        return false;
    }

    static boolean isPreProcessed(Lazy lazy) {
        if (lazy != null) {
            return lazy.preProcessed;
        }
        return false;
    }

    boolean hasContext() {
        return contextRef != null;
    }

    public final Context getContext() throws FuelUnableToObtainContextException {
        Context context = null;
        if (hasContext()) {
            context = contextRef.get();
        }
        if (context == null) {
            if (!isPostProcessed(this)) {
                throw FuelInjector.doFailure(this, new FuelUnableToObtainContextException("Never Ignited " + this));
            }

            if (FuelInjector.isAppSingleton(leafType)) {
                context = FuelInjector.getApp();
                setContext(context);
            }

            // Cannot obtain a context, obviously there was some misuse of Fuel that creeped through
            // This is going to be a critical fail, so lets notify the FuelModule of critical fail
            if (context == null) {
                FuelUnableToObtainContextException err = new FuelUnableToObtainContextException("Unable to obtain context for " + this);
                //noinspection ThrowableNotThrown
                FuelInjector.doFailure(this, err);
                context = err.consumeContext();
                if (context == null) {
                    throw err;
                }
            }
        }
        return context;
    }

    /**
     * The BaseType - the type that was requested, see {@link #getLeafType()}
     */
    public final Class<T> getType() {
        return type;
    }

    /**
     * The LeafType - the type the BaseType mapped to via the {@link FuelModule}. See {@link #getType()}<br>
     * Null until after the lazy has been post-processed (parent is context aware via parent-injection or ignite).
     */
    public final Class<?> getLeafType() {
        return leafType;
    }

    /**
     * @throws NullPointerException when {@link #getLeafType()} is unavailable
     */
    public boolean isAppSingleton() {
        return FuelInjector.isAppSingleton(leafType);
    }

    /**
     * @throws NullPointerException when {@link #getLeafType()} is unavailable
     */
    public boolean isActivitySingleton() {
        return FuelInjector.isActivitySingleton(leafType);
    }

    /**
     * @throws NullPointerException when {@link #getLeafType()} is unavailable
     */
    public boolean isSingleton() {
        return FuelInjector.isSingleton(leafType);
    }

    public final Integer getFlavor() {
        return flavor;
    }

    protected void setInstance(T instance) {
        if (useWeakInstance) {
            this.instanceRef = new WeakReference<>(instance);
        } else {
            this.instance = instance;
        }
    }

    protected T getInstance() {

        // We determined that it was unsuitable to keep a strong ref to the instance so lets refer to the instanceRef instead of instance
        if (useWeakInstance) {
            T weakInst = null;
            if (instanceRef != null) {
                weakInst = instanceRef.get();
            }
            return weakInst;
        }

        return this.instance;
    }

    /**
     * Get the instance associated with this type.<br>
     * May return null and will never throw an exception, however the FuelModule.OnLazyGetFailed will be called.
     */
    public @NonNull T get() throws FuelInjectionException {
        if (FuelInjector.isDebug()) {
            FLog.leaveBreadCrumb("Lazy.get() %s", this);
        }
        return getChecked();
    }


    /**
     * Get the instance associated with this type.<br>
     * Never Null
     */
    protected final T getChecked() throws FuelInjectionException {
        try {
            if (getInstance() == null) {
                // convenience for views in edit mode
                if (isInEditMode) {
                    setInstance(type.newInstance());
                    return getInstance();
                }

                if (getContext() == null) {
                    StackTraceElement elem = FLog.findStackElem(Lazy.class);
                    throw new IllegalStateException(
                            "Context was found to be null when you called Lazy.get() for " + this.getType() + " from "
                                    + FLog.getSimpleName(elem) + "@" + elem.getLineNumber() + " -- Why did this happen? " +
                                    "You're trying to inject/attain a Lazy from a class that was not FuelInjector.ignite(context)" +
                                    " Or you called lazy.get() before the ignite occurred.");
                }

                T instance = FuelInjector.attainInstance(CacheKey.attain(this), this, true);
                if (instance == null) {
                    throw new FuelInjectionException("Unable to obtain instance: %s", this);
                } else {
                    setInstance(instance);
                }
            }
        } catch (FuelInjectionException e) {
            throw e;
        } catch (Exception e) {
            if (debug) {
                FLog.leaveBreadCrumb("getChecked Exception %s", e.getMessage());
            }
            throw FuelInjector.doFailure(this, e);
        }
        return getInstance();
    }

    Object getParent() {
        return parentRef == null ? null : parentRef.get();
    }

    void setParent(Object parent) {
        parentRef = new WeakReference<>(parent);
    }

    /**
     * @return true when FuelInjector is in Debug Mode and this Lazy was obtained via Lazy.attainDebug()
     */
    boolean isDebug() {
        return debug && FuelInjector.isDebug();
    }

    @Override
    public @NonNull String toString() {
        try {
            String instanceStr = "null";
            if (getInstance() != null) {
                instanceStr = String.format("%s[%x]", getInstance().getClass().getSimpleName(), getInstance().hashCode());
            }

            String contextStr = "null";
            if (contextRef != null) {
                Context context = contextRef.get();
                if (context != null) {
                    contextStr = context.getClass().getSimpleName();
                }
            }

            return String.format("Lazy[type='%s', leafType='%s', flavor='%s', instance='%s', context='%s'",
                    (type == null ? null : type.getSimpleName()),
                    (leafType == null ? null : leafType.getSimpleName()),
                    flavor,
                    instanceStr,
                    contextStr
            );
        } catch (Exception e) {
            FLog.e(e);
        }
        return super.toString();
    }

}
