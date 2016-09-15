package com.ath.fuel.err;


/**
 * Thrown when fuel was unable to provide the requested instance<br>
 */
@SuppressWarnings( "serial" )
public class FuelUnableToObtainInstanceException extends FuelInjectionException {

    public FuelUnableToObtainInstanceException( Exception exception ) {
        super( exception );
    }

    public FuelUnableToObtainInstanceException( String format, Object... objects ) {
        super( String.format( format, objects ) );
    }

    public FuelUnableToObtainInstanceException( Exception exception, String format, Object... objects ) {
        super( String.format( format, objects ), exception );
    }
}
