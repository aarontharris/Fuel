package com.ath.fuel;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;

import com.ath.fuel.err.FuelInjectionException;
import com.ath.fuel.err.FuelUnableToObtainContextException;

import java.lang.ref.WeakReference;


public class Lazy<T> {

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
     * @param parent the object responsible for the lazy
     * @param clazz  - the type you wish to attain
     * @return Lazy of the given clazz.
     */
    public static final <TYPE> Lazy<TYPE> attain( Object parent, Class<TYPE> clazz ) {
        return newInstance( parent, clazz, null );
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
     * @param parent the object responsible for the lazy
     * @param clazz  - the type you wish to attain
     * @return Lazy of the given clazz.
     */
    public static final <TYPE> Lazy<TYPE> attain( Object parent, Class<TYPE> clazz, Integer flavor ) {
        return newInstance( parent, clazz, flavor );
    }

    private static final <TYPE> Lazy<TYPE> newInstance( View parent, Class<TYPE> clazz, Integer flavor ) {
        Lazy<TYPE> lazy = new Lazy<TYPE>( clazz, flavor );
        lazy.isInEditMode = parent.isInEditMode();
        if ( !lazy.isInEditMode ) {
            preInitializeNewLazy( lazy, parent );
        }
        return lazy;
    }

    private static final <TYPE> Lazy<TYPE> newInstance( Object parent, Class<TYPE> clazz, Integer flavor ) {
        Lazy<TYPE> lazy = new Lazy<TYPE>( clazz, flavor );
        preInitializeNewLazy( lazy, parent );
        return lazy;
    }

    static final Lazy newEmptyParent( Object parent ) {
        Lazy lazy = new Lazy( parent.getClass() );
        lazy.useWeakInstance = true;
        lazy.setInstance( parent );

        if ( FuelInjector.isFragment( parent.getClass() ) ) {
            lazy.setScopeObjectRef( new WeakReference( parent ) );
        }

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

    // when ActivitySingleton in constructed
    // we come here for it's injectable AppSingleton before ActivitySingleton has its lazy remembered
    // So AppSingleton can't find its parent and ends up creating a new one
    // so AppSingleton is queued up under a temp lazy that never gets processed
    private static final <TYPE> void preInitializeNewLazy( Lazy<TYPE> lazy, Object parent ) {
        Context context = null;
        Lazy lazyParent = null;

        lazy.parentRef = new WeakReference<Object>( parent );

        try {
            if ( FuelInjector.isDebug() ) {
                FLog.leaveBreadCrumb( "initialize lazy %s, %s", lazy, parent );
            }

            if ( FuelInjector.isInitialized() ) {
                // Hopefully this parent has been ignited already and we'll have a Lazy to show for it
                lazyParent = FuelInjector.injector.findLazyByInstance( parent );
                if ( Lazy.isPreProcessed( lazyParent ) ) {
                    context = (Context) lazyParent.contextRef.get(); // not sure why this cast is necessary? AndroidStudio fail?

                    // Do pre-preocess because we know the parent-lazy and do not need to enqueue
                    FuelInjector.doPreProcessChild( lazy, lazyParent );
                }
            }
        } catch ( FuelInjectionException e ) {
            // Failed to work out the parent, lets just enqueue it
            lazy.contextRef = null; // reset
        }

        // Context context = FuelInjector.getFuelModule().provideContext( parent ); // FIXME

        if ( context == null ) {
            // queue up this lazy until the parent is ignited
            FuelInjector.enqueueLazy( parent, lazy );
        }
    }

    Scope scope;
    boolean preProcessed = false;
    boolean postProcessed = false;

    private WeakReference<Object> scopeObjectRef; // We do object bcuz we dont know if its v4.frag or just frag :/
    private WeakReference<Object> parentRef;

    Class<T> type; // the type requested, but not necessarily the type to be instantiated
    Class<?> leafType; // the type to be instantiated, not necessarily the type requested but some derivitive.
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

    Lazy( Class<T> type ) {
        this.type = type;
        this.typeIsContext = FuelInjector.isContext( type );
        this.useWeakInstance = this.useWeakInstance || this.typeIsContext; // don't override useWeakInstance if already true
        this.flavor = CacheKey.DEFAULT_FLAVOR;
    }

    Lazy( Class<T> type, Integer flavor ) {
        this.type = type;
        this.typeIsContext = FuelInjector.isContext( type );
        this.useWeakInstance = this.useWeakInstance || this.typeIsContext; // don't override useWeakInstance if already true
        this.flavor = flavor;
    }

    void setScopeObjectRef( WeakReference<Object> scopeObjectRef ) {
        this.scopeObjectRef = scopeObjectRef;
    }

    WeakReference<Object> getScopeObjectRef() {
        return scopeObjectRef;
    }

    public Lazy setDebug() {
        this.debug = true;
        return this;
    }

    void setLeafType( Class<?> leafType ) {
        this.leafType = leafType;
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

    static boolean isPostProcessed( Lazy lazy ) {
        if ( lazy != null ) {
            return lazy.postProcessed;
        }
        return false;
    }

    static boolean isPreProcessed( Lazy lazy ) {
        if ( lazy != null ) {
            return lazy.preProcessed;
        }
        return false;
    }

    boolean hasContext() {
        return contextRef != null;
    }

    public final Context getContext() throws FuelUnableToObtainContextException {
        Context context = null;
        if ( hasContext() ) {
            context = contextRef.get();
        }
        if ( context == null ) {
            if ( !isPostProcessed( this ) ) {
                throw FuelInjector.doFailure( this, new FuelUnableToObtainContextException( "Never Ignited " + this ) );
            }

            if ( FuelInjector.isAppSingleton( leafType ) ) {
                context = FuelInjector.getApp();
                setContext( context, true );
            } else if ( FuelInjector.isActivitySingleton( leafType ) ) {
                context = FuelInjector.getActivity();
                setContext( context, true );
            }

            // Cannot obtain a context, obviously there was some misuse of Fuel that creeped through
            // This is going to be a critical fail, so lets notify the FuelModule of critical fail
            if ( context == null ) {
                FuelUnableToObtainContextException err = new FuelUnableToObtainContextException( "Unable to obtain context for " + this );
                FuelInjector.doFailure( this, err );
                context = err.consumeContext();
                if ( context == null ) {
                    throw err;
                }
            }
        }
        return context;
    }

    /**
     * The BaseType - the type that was requested, see {@link #getLeafType()}
     */
    public final Class<T> getType() {
        return type;
    }

    /**
     * The LeafType - the type the BaseType mapped to via the {@link FuelModule}. See {@link #getType()}<br>
     * Null until after the lazy has been post-processed (parent is context aware via parent-injection or ignite).
     */
    public final Class<?> getLeafType() {
        return leafType;
    }

    /**
     * @throws NullPointerException when {@link #getLeafType()} is unavailable
     */
    public boolean isAppSingleton() {
        return FuelInjector.isAppSingleton( leafType );
    }

    /**
     * @throws NullPointerException when {@link #getLeafType()} is unavailable
     */
    public boolean isActivitySingleton() {
        return FuelInjector.isActivitySingleton( leafType );
    }

    /**
     * @throws NullPointerException when {@link #getLeafType()} is unavailable
     */
    public boolean isFragmentSingleton() {
        return FuelInjector.isFragmentSingleton( leafType );
    }

    /**
     * @throws NullPointerException when {@link #getLeafType()} is unavailable
     */
    public boolean isSingleton() {
        return FuelInjector.isSingleton( leafType );
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
     * Get the instance associated with this type.<br>
     * May return null and will never throw an exception, however the FuelModule.OnLazyGetFailed will be called.
     */
    @NonNull public T get() throws FuelInjectionException {
        if ( FuelInjector.isDebug() ) {
            FLog.leaveBreadCrumb( "Lazy.get() %s", this );
        }
        T out = getChecked();
        return out;
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
            throw FuelInjector.doFailure( this, e );
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
        //		String ref = getScopeObjectRef() == null ? "null" : String.valueOf( getScopeObjectRef().hashCode() );
        //		String refer = getScopeObjectRef() == null ? "null" : String.valueOf( getScopeObjectRef().get().hashCode() );
        //		return String.format( "%s, %s, %s", this.hashCode(), ref, refer );
        //	}
        //
        //	public String xtoString() {
        try {
            String instanceStr = "null";
            if ( getInstance() != null ) {
                instanceStr = String.format( "%s[%x]", getInstance().getClass().getSimpleName(), getInstance().hashCode() );
            }

            //			Lazy ptr = null;
            //			int i = 0;
            //			do {
            //				ptr = parentNode == null ? null : parentNode.get();
            //				FLog.d("[%s] %s", ptr);
            //			} while ( ptr != null );

            String contextStr = "null";
            if ( contextRef != null ) {
                Context context = contextRef.get();
                if ( context != null ) {
                    contextStr = context.getClass().getSimpleName();
                }
            }


            return String.format( "Lazy[type='%s', leafType='%s', flavor='%s', instance='%s', context='%s'",
                    ( type == null ? null : type.getSimpleName() ),
                    ( leafType == null ? null : leafType.getSimpleName() ),
                    flavor,
                    instanceStr,
                    contextStr
            );
        } catch ( Exception e ) {
            FLog.e( e );
        }
        return super.toString();
    }

}
