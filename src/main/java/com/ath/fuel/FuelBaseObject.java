package com.ath.fuel;

import android.content.Context;

public class FuelBaseObject {

    /**
     * Will use an Application Context
     */
    public FuelBaseObject() {
        FuelInjector.ignite( FuelInjector.getGenericContext(), this );
        onFueled();
    }


    /**
     * Will use the given Context, Application or Activity -- Context is not held
     */
    public FuelBaseObject( Context context ) {
        if ( context == null ) {
            throw new NullPointerException( getClass().getSimpleName() + " got a null Context, did you forget to call super( context ) in the constructor?" );
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
