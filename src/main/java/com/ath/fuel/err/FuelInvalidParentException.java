package com.ath.fuel.err;

/**
 * Thrown when a parent instance is attempting to inject a child but the parent doesn't exist.<br>
 */
@SuppressWarnings( "serial" )
public class FuelInvalidParentException extends FuelInjectionException {
	public FuelInvalidParentException( Exception exception ) {
		super( exception );
	}

	public FuelInvalidParentException( String format, Object... objects ) {
		super( String.format( format, objects ) );
	}

	public FuelInvalidParentException( Exception exception, String format, Object... objects ) {
		super( String.format( format, objects ), exception );
	}
}
