package com.ath.fuel;

@SuppressWarnings("serial")
public class FuelInjectionException extends Exception {

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
