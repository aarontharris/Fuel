package com.ath.fuel;

import android.app.Application;
import android.content.Context;

public class FuelBaseObject {

    /**
     * Will use an Application Context
     */
    public FuelBaseObject( Application app ) {
        if ( app == null ) {
            throw new NullPointerException( getClass().getSimpleName() + " got a null Context" );
        }
        FuelInjector.ignite( app, this );
        onFueled();
    }


    /**
     * Will use the given Context, Application or Activity -- Context is not held
     */
    public FuelBaseObject( Context context ) {
        if ( context == null ) {
            throw new NullPointerException( getClass().getSimpleName() + " got a null Context" );
        }

        FuelInjector.ignite( context, this );
        onFueled();
    }

    /**
     * Called after all Lazies are ready
     */
    protected void onFueled() {
    }
}
