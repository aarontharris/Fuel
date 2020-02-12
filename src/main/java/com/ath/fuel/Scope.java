package com.ath.fuel;

/**
 * SEE {@link #canAccess(Scope)} for detail overkill
 */
public enum Scope {
    Application(1), // Lives for the duration of the app and has visibility to Activity or finer scope.
    Activity(2), // Lives for the duration of the Activity and has visibility to Activity and Application scope.
    ViewRoot(3), // Lives for the duration of the View in the view tree annotated with @ViewRoot
    Object(Integer.MAX_VALUE),  // Lives as long as object is alive and has no visibility to any of the above scopes.
    // TODO: if you add a new scope, consider FuelInjector.toCacheScope( scope )
    ;

    private int mScopeValue = 0;

    Scope(int scopeValue) {
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
     * EX: Many-To-One = Many Views to One Activity<br>
     * <br>
     * Whenever in doubt this will help you:<br>
     * Christopher Lambert https://www.youtube.com/watch?v=sqcLjcSloXs<br>
     */
    public boolean canAccess(Scope scope) {
        if (scope != null) {

            // Zeros can't access anything
            if (this.equals(Object)) {
                return false;
            }

            // everyone can access Application
            if (scope.equals(Application)) {
                return true;
            }

            // everyone can access Object
            if (scope.equals(Object)) {
                return true;
            }

            // All else follow natural order
            return this.mScopeValue >= scope.mScopeValue;
        }
        return false;
    }

//    public static Scope getScope(@NonNull Object object) {
//        if (object instanceof FuelConfigurator) {
//            if (FuelInjector.get().isAppSingleton(object.getClass())) {
//                return Scope.Application;
//            } else
//            if (FuelInjector.get().isActivitySingleton(object.getClass())) {
//                return Scope.Activity;
//            } else
//            if (FuelInjector.get().isViewRootSingleton(object.getClass())) {
//                return Scope.ViewRoot;
//            }
//        } else if (object instanceof FuelModule) {
//            return Scope.Application;
//        }
//    }
}
