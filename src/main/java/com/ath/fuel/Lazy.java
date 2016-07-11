package com.ath.fuel;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.view.View;

import java.lang.ref.WeakReference;


public class Lazy<T> {
    // FIXME: FUEL add an L2 cache that is keyed on Context and CacheKey since we now know the context!

    public static final <TYPE> Lazy<TYPE> attain( View parent, Class<TYPE> clazz ) {
        Lazy<TYPE> lazy = new Lazy<TYPE>( clazz );
        lazy.isInEditMode = parent.isInEditMode();
        if ( !lazy.isInEditMode ) {
            initializeNewlyAttainedLazy( lazy, parent );
        }
        return lazy;
    }

    /**
     * WARN: As of Android 5.0 (ART) An attained lazy accessed from a super-type's constructor may not be available during that period of execution.
     * <pre>
     * EXAMPLE:
     * 	public class Base {
     * 		public Base() {
     * 			doStuff();
     *        }
     * 		public void doStuff() {
     * 			visitSites();
     * 			eatFood();
     * 			smellRoses();
     *        }
     *  }
     *  public class Derived extends Base {
     * 		private Lazy&lt;Camera&gt; cam = Lazy.attain( this, Camera.class );
     * 		public Derived() {
     * 			super(); // cam is not yet injected during super-type's constructor.
     * 			// cam is now injected and available
     *        }
     * 		&at;Override
     * 		public void doStuff() {
     * 			super.doStuff();
     * 			cam.get().takePicture(); // FAIL: cam has not yet been injected
     *        }
     *  }
     * </pre>
     *
     * @param parent the object responsible for the lazy.
     * @param clazz  - the type you wish to attain
     * @return Lazy of the given clazz.
     */
    public static final <TYPE> Lazy<TYPE> attainDebug( Object parent, Class<TYPE> clazz ) {
        Lazy<TYPE> out = attain( parent, clazz, true );
        FLog.d( "FUEL: attain: %s from %s", clazz, parent );
        return out;
    }

    public static final <TYPE> Lazy<TYPE> attain( Object parent, Class<TYPE> clazz ) {
        return attain( parent, clazz, false );
    }

    private static final <TYPE> Lazy<TYPE> attain( Object parent, Class<TYPE> clazz, boolean debug ) {
        Lazy<TYPE> lazy = new Lazy<TYPE>( clazz, debug );
        initializeNewlyAttainedLazy( lazy, parent );
        return lazy;
    }

    /**
     * WARN: As of Android 5.0 (ART) An attained lazy accessed from a super-type's constructor may not be available during that period of execution.
     * <p/>
     * <pre>
     * EXAMPLE:
     * 	public class Base {
     * 		public Base() {
     * 			doStuff();
     *        }
     * 		public void doStuff() {
     * 			visitSites();
     * 			eatFood();
     * 			smellRoses();
     *        }
     *  }
     *  public class Derived extends Base {
     * 		private Lazy&lt;Camera&gt; cam = Lazy.attain( this, Camera.class );
     * 		public Derived() {
     * 			super(); // cam is not yet injected during super-type's constructor.
     * 			// cam is now injected and available
     *        }
     * 		&at;Override
     * 		public void doStuff() {
     * 			super.doStuff();
     * 			cam.get().takePicture(); // FAIL: cam has not yet been injected
     *        }
     *  }
     * </pre>
     *
     * @return Lazy of the given clazz.
     */
    public static final <TYPE> Lazy<TYPE> attain( Context context, Class<TYPE> type ) {
        Lazy<TYPE> lazy = new Lazy<TYPE>( type );
        initializeNewlyAttainedLazy( lazy, context );
        return lazy;
    }

    /**
     * WARN: As of Android 5.0 (ART) An attained lazy accessed from a super-type's constructor may not be available during that period of execution.
     * <pre>
     * EXAMPLE:
     * 	public class Base {
     * 		public Base() {
     * 			doStuff();
     *        }
     * 		public void doStuff() {
     * 			visitSites();
     * 			eatFood();
     * 			smellRoses();
     *        }
     *  }
     *  public class Derived extends Base {
     * 		private Lazy&lt;Camera&gt; cam = Lazy.attain( this, Camera.class );
     * 		public Derived() {
     * 			super(); // cam is not yet injected during super-type's constructor.
     * 			// cam is now injected and available
     *        }
     * 		&at;Override
     * 		public void doStuff() {
     * 			super.doStuff();
     * 			cam.get().takePicture(); // FAIL: cam has not yet been injected
     *        }
     *  }
     * </pre>
     *
     * @param parent the object responsible for the lazy.
     * @param clazz  - the type you wish to attain
     * @return Lazy of the given clazz.
     */
    public static final <TYPE> Lazy<TYPE> attain( Object parent, Class<TYPE> clazz, Integer flavor ) {
        Lazy<TYPE> lazy = new Lazy<TYPE>( clazz, flavor );
        initializeNewlyAttainedLazy( lazy, parent );
        return lazy;
    }

    /**
     * WARN: As of Android 5.0 (ART) An attained lazy accessed from a super-type's constructor may not be available during that period of execution.
     * <pre>
     * EXAMPLE:
     * 	public class Base {
     * 		public Base() {
     * 			doStuff();
     *        }
     * 		public void doStuff() {
     * 			visitSites();
     * 			eatFood();
     * 			smellRoses();
     *        }
     *  }
     *  public class Derived extends Base {
     * 		private Lazy&lt;Camera&gt; cam = Lazy.attain( this, Camera.class );
     * 		public Derived() {
     * 			super(); // cam is not yet injected during super-type's constructor.
     * 			// cam is now injected and available
     *        }
     * 		&at;Override
     * 		public void doStuff() {
     * 			super.doStuff();
     * 			cam.get().takePicture(); // FAIL: cam has not yet been injected
     *        }
     *  }
     * </pre>
     *
     * @param context the App, Service, or Activity the lazy is tied to.
     * @param clazz   - the type you wish to attain
     * @param flavor  - Tell the injector which flavor of the instance you would like
     * @return Lazy of the given clazz.
     */
    public static final <TYPE> Lazy<TYPE> attain( Context context, Class<TYPE> clazz, Integer flavor ) {
        Lazy<TYPE> lazy = new Lazy<TYPE>( clazz, flavor );
        initializeNewlyAttainedLazy( lazy, context );
        return lazy;
    }

    static final Lazy newLazy( Context context, Object instance ) {
        Lazy lazy = new Lazy( instance.getClass() );
        lazy.setLeafType( instance.getClass() );
        lazy.setContext( context, false );
        lazy.setInstance( instance );
        return lazy;
    }

    // FIXME: FUEL if called from background thread without a context, throw an error -- for now i'm just calling red to see them all
    static final void doThreadCheck( Lazy<?> lazy ) {
        if ( !FuelInjector.inMainThread() ) {
            StackTraceElement elem = FLog.findStackElem( Lazy.class );
            FLog.e( new IllegalStateException(
                    "Attain Lazy " + lazy.getType() + " from a bg. " + FLog.getSimpleName( elem ) + "@" + elem.getLineNumber() ) );
        }
    }

    private static final <TYPE> void initializeNewlyAttainedLazy( Lazy<TYPE> lazy, Object parent ) {
        lazy.parentRef = new WeakReference<Object>( parent );
        if ( FuelInjector.getFuelModule() != null ) { // obtain the leafType now if the module is available (almost always)
            lazy.setLeafType( FuelInjector.toLeafType( lazy.type, lazy.flavor ) );
        }

        lazy.typeName = lazy.type.getSimpleName();

        if ( lazy.isDebug() ) {
            FLog.leaveBreadCrumb( "initializeNewlyAttainedLazy called on %s with parent: %s", lazy,
                    parent == null ? "null" : parent.getClass().getSimpleName() );
        }

        Context context = null;

        // One-off in case we're asking for an Application
        if ( context == null ) {
            if ( Application.class.isAssignableFrom( lazy.type ) ) {
                context = FuelInjector.getApp();
                if ( lazy.isDebug() ) {
                    FLog.leaveBreadCrumb( "initializeNewlyAttainedLazy: %s is application, set context to app: %s", lazy, context );
                }
            }
        }

        // Maybe we can determine if it's an app singleton from the leafType or type
        if ( context == null ) {
            Class<?> bestType = lazy.leafType == null ? lazy.type : lazy.leafType;
            if ( FuelInjector.getFuelModule() != null ) {
                context = FuelInjector.getFuelModule().provideContext( bestType );
                if ( context != null && lazy.isDebug() ) {
                    FLog.leaveBreadCrumb( "initializeNewlyAttainedLazy: %s got context %s from provideContextSafe(bestType)", lazy, context
                            .getClass().getSimpleName() );
                }
            }
        }

        // Can this parent provide its own context?
        if ( context == null ) {
            if ( FuelInjector.getFuelModule() != null ) {
                context = FuelInjector.getFuelModule().provideContext( parent );
                if ( context != null && lazy.isDebug() ) {
                    FLog.leaveBreadCrumb( "initializeNewlyAttainedLazy: %s got context %s from provideContextSafe", lazy, context
                            .getClass().getSimpleName() );
                }
            }
        }

        // Can we find the context somewhere up the dependency hierarchy ?
        if ( context == null ) {
            context = FuelInjector.findContext( parent ); // will find a lazy if the parent was injected
            if ( context != null && lazy.isDebug() ) {
                FLog.leaveBreadCrumb( "initializeNewlyAttainedLazy: %s got context %s from parent findLazy", lazy,
                        context.getClass().getSimpleName() );
            }
        }

        // How'd we do?
        if ( context == null || FuelInjector.isUninitialized() ) { // we guessed but nothing was avail, queue up
            FuelInjector.enqueueLazy( parent, lazy ); // FIXME: FUEL -- queued up stuff should go get GC'd when the activity goes away?
            if ( lazy.isDebug() ) {
                FLog.leaveBreadCrumb( "initializeNewlyAttainedLazy: enqueued to associate context later: %s", lazy );
            }
            // If context is found to be null:
            // if debug build just crash and yell, but if prod use it and yell
            if ( context == null ) {
                Activity guessedActivity = FuelInjector.getActivity();
                if ( guessedActivity != null ) {
                    lazy.setContext( guessedActivity, true );
                    if ( lazy.isDebug() ) {
                        FLog.leaveBreadCrumb( "initializeNewlyAttainedLazy: guessed activity: %s for %s", guessedActivity.getClass().getSimpleName(),
                                lazy );
                    }
                }
            }
        } else {
            if ( lazy.isDebug() ) {
                FLog.leaveBreadCrumb( "initializeNewlyAttainedLazy: had context: %s for %s", context.getClass().getSimpleName(), lazy );
            }

            FuelInjector.doPreProcess( lazy, context );
        }
    }

    Class<T> type;
    Class<?> leafType; // used only to indicate if the type was found to have mapping
    boolean isActivitySingleton = false;
    boolean typeIsContext = false;
    boolean useWeakInstance = false;
    T instance = null;
    private WeakReference<T> instanceRef; // for the cases we identify that we don't want to keep a strong ref to the instance
    private WeakReference<Context> contextRef;
    int contextHashCode = 0;
    boolean guessedContext = false;
    Exception guessedStackTrace = null;
    private final Integer flavor;
    private boolean isInEditMode;
    private boolean debug;
    public String typeName; // meh. it's a one off. make it public.
    private WeakReference<Object> parentRef;

    /**
     * @param parent
     * @param type   -- not enforced but MUST match the Parameterized T type. They don't match because types with their own parameterized types won't
     *               match up :(
     */
    public Lazy( Object parent, Class<T> type ) {
        this( type );
        initializeNewlyAttainedLazy( this, parent );
    }

    /**
     * @param parent
     * @param type   -- not enforced but MUST match the Parameterized T type. They don't match because types with their own parameterized types won't
     *               match up :(
     */
    public Lazy( Object parent, Class<T> type, boolean useWeakInstance ) {
        this( type );
        initializeNewlyAttainedLazy( this, parent );
    }

    Lazy( Class<T> type ) {
        this.type = type;
        this.typeIsContext = FuelInjector.isContext( type );
        this.useWeakInstance = this.useWeakInstance || this.typeIsContext; // don't override useWeakInstance if already true
        this.flavor = CacheKey.DEFAULT_FLAVOR;
    }

    Lazy( Class<T> clazz, boolean debug ) {
        this( clazz );
        this.debug = debug;
    }

    Lazy( Class<T> type, Integer flavor ) {
        this.type = type;
        this.typeIsContext = FuelInjector.isContext( type );
        this.useWeakInstance = this.useWeakInstance || this.typeIsContext; // don't override useWeakInstance if already true
        this.flavor = flavor;
    }

    void setLeafType( Class<?> leafType ) {
        this.leafType = leafType;
        isActivitySingleton = FuelInjector.isActivitySingleton( leafType );
    }


    void setContext( Context context, boolean guessedContext ) {
        if ( context != null ) {
            this.contextRef = FuelInjector.getContextRef( FuelInjector.toContext( context ) );
            this.contextHashCode = context.hashCode();
            this.guessedContext = guessedContext;
            if ( FuelInjector.isDebug() ) {
                StackTraceElement elem = FLog.findStackElem( Lazy.class );
                this.guessedStackTrace = new IllegalStateException(
                        String.format( "Guessed Context for %s %s @ %s", this, FLog.getSimpleName( elem ), elem.getLineNumber() ) );
            }
        }
    }

    // kinda hairy but it's trying to be intelligent
    // considering we can potentially lose a weak reference, this code tries to re-attain the context if lost
    // but only if it's safe. it is considered safe only when the found context's hashcode matches this lazy's original context's hashcode.
    public final Context getContext() {
        Context context = null;
        if ( contextRef != null ) {
            context = contextRef.get();
        }
        if ( context == null ) {
            // first try an activity context for a match
            Context tmp = FuelInjector.getActivity();
            if ( tmp != null && contextHashCode == tmp.hashCode() ) {
                context = tmp;
                setContext( context, false );
            }
            // secondly if unsuccessfully with the activity context matchup and this is not a ActivitySingleton, try app
            if ( context == null && leafType != null && !isActivitySingleton ) { // we consider leafType because isActivitySingleton isnt known until
                // leafType is known
                tmp = FuelInjector.getApp();
                if ( tmp != null && contextHashCode == tmp.hashCode() ) {
                    context = tmp;
                    setContext( context, false );
                }
            }
        }
        return context;
    }

    public final Class<T> getType() {
        return type;
    }

    public final Integer getFlavor() {
        return flavor;
    }

    protected void setInstance( T instance ) {
        if ( useWeakInstance ) {
            this.instanceRef = new WeakReference<T>( instance );
        } else {
            this.instance = instance;
        }
    }

    protected T getInstance() {

        // Asking for some kind of context -- lets see if we can find what they want from the context we already know
        if ( typeIsContext ) {
            // Try Application First -- this is what you get if you ask for Context, Application, CSApplicationBase, Sportacular
            if ( FuelInjector.getApp() != null && type.isAssignableFrom( FuelInjector.getApp().getClass() ) ) {
                return (T) FuelInjector.getApp();
            }

            // FIXME: support finding an activity quickly
        }

        // We determined that it was unsuitable to keep a strong ref to the instance so lets refer to the instanceRef instead of instance
        if ( useWeakInstance ) {
            T weakInst = null;
            if ( instanceRef != null ) {
                weakInst = instanceRef.get();
            }
            return weakInst;
        }

        return this.instance;
    }

    /**
     * Not a noob feature -- use this only to late-ignite a lazy for the given context<br>
     * Useful when you need a one-off {@link Lazy#attain(Context, Class, Integer)} and the context is provided late or in an odd manner
     */
    public T get( Context context ) {
        if ( getContext() == null && parentRef != null ) {
            Object parent = parentRef.get();
            if ( parent != null ) {
                if ( context != null ) {
                    FuelInjector.ignite( context, parent );
                }
            }
        }
        return get();
    }

    /**
     * Get the instance associated with this type.<br>
     * May return null and will never throw an exception, however the FuelModule.OnLazyGetFailed will be called.
     */
    public T get() {
        try {
            T out = getChecked();
            // if a context singleton is being injected from a non-activity context
            if ( ( !( getContext() instanceof Activity ) ) && FuelInjector.isActivitySingleton( leafType ) ) {
                if ( FuelInjector.isDebug() ) {
                    // As Arnie says: BLOW "STUFF" UP
                    String contextName = getContext().getClass().getSimpleName();
                    String singletonName = leafType.getSimpleName();
                    throw new Error(
                            "FAIL: you tried to inject a ActivitySingleton(" + singletonName + ") form within a non-Activity context(" + contextName
                                    + ")" );
                }
            }
            return out;
        } catch ( FuelInjectionException e ) {
            FuelInjector.handleLazyGetFailed( e );
        }
        return null;
    }


    /**
     * Get the instance associated with this type.<br>
     * Never Null
     */
    protected final T getChecked() throws FuelInjectionException {
        try {
            if ( getInstance() == null ) {
                // convenience for views in edit mode
                if ( isInEditMode ) {
                    setInstance( type.newInstance() );
                    return instance;
                }

                // attempt to get the context and ignite parent
                // because if we're here then the parent never got ignited
                // if we're lucky the parent IS a context, if so lets use it.
                if ( getContext() == null && parentRef != null ) {
                    Object parent = parentRef.get();
                    if ( parent != null ) {
                        Context context = FuelInjector.getFuelModule().provideContext( parent );
                        if ( context != null ) {
                            FuelInjector.ignite( context, parent );
                        }
                    }
                }

                // If we guessed during attain and still
                if ( this.guessedContext ) {
                    if ( FuelInjector.isDebug() ) {
                        throw this.guessedStackTrace; // should not be null in debug mode when guessedContext == true
                    } else {
                        FLog.e( new FuelInjectionException( "Guessed Context for " + this ) );
                    }
                }

                if ( getContext() == null ) {
                    StackTraceElement elem = FLog.findStackElem( Lazy.class );
                    throw new IllegalStateException( "Context was found to be null when you called Lazy.get() for " + this.getType() + " from "
                            + FLog.getSimpleName( elem ) + "@" + elem.getLineNumber() );
                }

                setInstance( FuelInjector.attainInstance( CacheKey.attain( this ), this, true ) );
                if ( getInstance() == null ) {
                    throw new FuelInjectionException( "Unable to obtain instance: %s", this );
                }
            }
        } catch ( FuelInjectionException e ) {
            throw e;
        } catch ( Exception e ) {
            if ( debug ) {
                FLog.leaveBreadCrumb( "getChecked Exception %s", e.getMessage() );
            }
            throw new FuelInjectionException( e );
        }
        return getInstance();
    }

    Object getParent() {
        return parentRef == null ? null : parentRef.get();
    }

    /**
     * @return true when FuelInjector is in Debug Mode and this Lazy was obtained via Lazy.attainDebug()
     */
    boolean isDebug() {
        return debug && FuelInjector.isDebug();
    }

    @Override
    public String toString() {
        try {
            return "Lazy [type=" + ( type == null ? null : type.getSimpleName() ) +
                    ", leafType=" + ( leafType == null ? null : leafType.getSimpleName() ) +
                    ", instance=" + ( getInstance() == null ? null : getInstance().getClass().getSimpleName() ) +
                    ", flavor=" + flavor + "]";
        } catch ( Exception e ) {
            FLog.e( e );
        }
        return super.toString();
    }

}
