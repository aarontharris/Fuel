package com.ath.fuel;

/**
 * SEE {@link #canAccess(Scope)} for detail overkill
 */
public enum Scope {
    Application( 1 ), // Lives for the duration of the app and has visibility to Activity or finer scope.
    Activity( 2 ), // Lives for the duration of the Activity and has visibility to Activity and Application scope.
    Fragment( 3 ), // Lives for the duration of the Fragment and has visibility to Fragment, Activity, Application scope.
    Object( 0 ),  // Lives as long as object is alive and has no visibility to any of the above scopes.
    // TODO: if you add a new scope, consider FuelInjector.toCacheScope( scope )
    ;

    private int mScopeValue = 0;

    Scope( int scopeValue ) {
        mScopeValue = scopeValue;
    }

    /**
     * Do I have access to the given scope?<br>
     * Ex: true = Activity.canAccess( Application )<br>
     * Ex: false = Application.canAccess( Activity );<br>
     * <br>
     * Easier way to think of this is a One-To-Many or One-To-One or Many-To-One relationship.<br>
     * One-To-Many - One cannot access Many because you don't know which one.<br>
     * One-To-One - Works because "There can be only one"<br>
     * Many-To-One - Works because "There can be only one"<br>
     * <br>
     * EX: One-To-Many = One Application to Many Activities<br>
     * EX: One-To-One = One Resources to One Application<br>
     * EX: Many-To-One = Many Fragments to One Activity<br>
     * <br>
     * Whenever in doubt this will help you:<br>
     * Christopher Lambert https://www.youtube.com/watch?v=sqcLjcSloXs<br>
     */
    public boolean canAccess( Scope scope ) {
        if ( scope != null ) {

            // everyone can access Application
            if ( scope.equals( Application ) ) {
                return true;
            }

            // Evryone can access Object
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
