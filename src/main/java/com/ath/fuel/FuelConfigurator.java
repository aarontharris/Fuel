package com.ath.fuel;

import android.app.Application;

import androidx.annotation.NonNull;

import com.ath.fuel.FuelModule.FuelProvider;

public class FuelConfigurator {
    public final @NonNull FuelProvider REFLECTIVE_PROVIDER;
    private final @NonNull FuelModule module;

    public FuelConfigurator(@NonNull FuelModule module) {
        this.module = module;
        REFLECTIVE_PROVIDER = module.REFLECTIVE_PROVIDER;
    }

    public Application getApp() {
        return module.getApplication();
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
    public final void bind(Class<?> from, Class<?> to) {
        module.bind(from, to);
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
    public final void bind(Class<?> from, @NonNull Object to) {
        module.bind(from, to);
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
    public final <BASE, DERIVED extends BASE> void bind(Class<BASE> from, FuelProvider<DERIVED> to) {
        module.bind(from, to);
    }

    public final void bindReflectively(Class from) {
        module.bindReflectively(from);
    }
}