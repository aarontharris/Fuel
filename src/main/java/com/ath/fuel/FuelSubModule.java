package com.ath.fuel;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ath.fuel.FuelModule.FuelProvider;
import com.ath.fuel.err.FuelInjectionException;

abstract class FuelSubModule { // TODO

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
        return lazy.get();
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
                + lazy.leafType
                + "'. This is because no mapping has been provided. " +
                "For more info see FuelModule.onInstanceUnattainable()");

        //noinspection unchecked
        //return (T) REFLECTIVE_PROVIDER.provide(lazy, null);
        return null;
    }

    protected void onFailure(Lazy lazy, FuelInjectionException exception) {
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

    protected void logD(String message) {
    }

    protected void logW(String message) {
    }

    protected void logE(String message) {
    }

    protected void logE(String message, Exception e) {
    }

    /**
     * Override to plug fuel into your own analytics system
     */
    protected void leaveBreadCrumb(String fmt, Object... args) {
    }

    protected void configure() {
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
    protected void bind(Class<?> from, Object to) {
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
    }

    protected void bindReflectively(Class from) {
    }
}
