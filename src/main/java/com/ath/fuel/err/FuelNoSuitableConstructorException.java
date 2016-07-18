package com.ath.fuel.err;


/**
 * This can happen if the object needed to be instantiated and there was no suitable constructor.<br>
 * That happens when the only available constructors contained an object Fuel could not instantiate.<br>
 */
@SuppressWarnings( "serial" )
public class FuelNoSuitableConstructorException extends FuelInjectionException {

	public FuelNoSuitableConstructorException( Exception exception ) {
		super( exception );
	}

	public FuelNoSuitableConstructorException( String format, Object... objects ) {
		super( String.format( format, objects ) );
	}

	public FuelNoSuitableConstructorException( Exception exception, String format, Object... objects ) {
		super( String.format( format, objects ), exception );
	}
}
