package com.ath.fuel.err;

@SuppressWarnings( "serial" )
public class FuelInjectionException extends RuntimeException {

    public FuelInjectionException( Exception exception ) {
        super( exception );
    }

    public FuelInjectionException( String format, Object... objects ) {
        super( String.format( format, objects ) );
    }

    public FuelInjectionException( Exception exception, String format, Object... objects ) {
        super( String.format( format, objects ), exception );
    }
}
