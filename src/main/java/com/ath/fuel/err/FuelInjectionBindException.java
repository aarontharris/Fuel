package com.ath.fuel.err;

@SuppressWarnings("serial")
public class FuelInjectionBindException extends RuntimeException {

    public FuelInjectionBindException( Exception exception ) {
        super( exception );
    }

    public FuelInjectionBindException( String format, Object... objects ) {
        super( String.format( format, objects ) );
    }

    public FuelInjectionBindException( Exception exception, String format, Object... objects ) {
        super( String.format( format, objects ), exception );
    }
}
