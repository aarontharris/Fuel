package com.ath.fuel;

public enum Scope {
	Application( 1 ), // Lives for the duration of the app and has visibility to Activity or finer scope.
	Activity( 2 ), // Lives for the duration of the Activity and has visibility to Activity and Application scope.
	Fragment( 3 ), // Lives for the duration of the Fragment and has visibility to Fragment, Activity, Application scope.
	Object( 0 ),  // Lives as long as object is alive and has no visibility to any of the above scopes.
	None( 0 ),  // Is not scoped, a new object will be instantiated each time.
	Undef( 0 ) // A scope is not assigned
	;

	private int mScopeValue = 0;

	Scope( int scopeValue ) {
		mScopeValue = scopeValue;
	}

	/**
	 * Do I have access to the given scope?<br>
	 * Ex: true = Activity.canAccess( Application )<br>
	 * Ex: false = Application.canAccess( Activity );<br>
	 */
	public boolean canAccess( Scope scope ) {
		if ( scope != null ) {

			// everyone can access Application
			if ( scope.equals( Application ) ) {
				return true;
			}

			// Evryone can access Object, None, Undef
			if ( scope.mScopeValue == 0 ) {
				return true;
			}

			// Zeros can't access anything
			if ( this.mScopeValue == 0 ) {
				return false;
			}

			// All else follow natural order
			return this.mScopeValue >= scope.mScopeValue;
		}
		return false;
	}
}
