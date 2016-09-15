package com.ath.fuel.err;


import com.ath.fuel.Scope;

/**
 * Thrown when you disobeyed {@link com.ath.fuel.Scope#canAccess(Scope)}.<br>
 */
@SuppressWarnings( "serial" )
public class FuelScopeViolationException extends FuelInjectionException {

    public FuelScopeViolationException( Exception exception ) {
        super( exception );
    }

    public FuelScopeViolationException( String format, Object... objects ) {
        super( String.format( format, objects ) );
    }

    public FuelScopeViolationException( Exception exception, String format, Object... objects ) {
        super( String.format( format, objects ), exception );
    }
}
