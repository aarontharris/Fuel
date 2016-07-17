package com.ath.fuel;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import com.ath.fuel.err.FuelInjectionException;
import com.ath.fuel.err.FuelUnableToObtainInstanceException;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public abstract class FuelModule {

	/**
	 * Provider is cached with the context associated with the type it returns<br>
	 * Providers are cached against activity unless the type is an AppSingleton<br>
	 * Provider.provide's value is not cached so Provider.provide() may be called many times do your own caching if needed.
	 *
	 * @param <T>
	 * @author aharris
	 */
	public abstract static class FuelProvider<T> {

		/**
		 * Override this method when choosing an injectable depends on runtime state.<br>
		 * Here, you may inject objects and inspect state and decide which instance is best.<br>
		 * This method is called once for each Lazy.attain, but not for Lazy.get()'s as the lazy's internal instance is cached.<br>
		 *
		 * @param lazy
		 * @param parent that attained the lazy.
		 * @return
		 */
		public abstract T provide( Lazy lazy, Object parent );

		/**
		 * Guaranteed to be called at least once prior to provide()<br>
		 * May be called many times per provide() so it should be quick.
		 */
		public abstract Class<T> getType( Class<?> baseType, Integer flavor );
	}


	/**
	 * Like FuelProvider<T> except the getType method auto-returns the <T> type thus ignoring any kind of type mapping and making this simple
	 */
	public abstract static class FuelProviderSimple<T> extends FuelProvider<T> {
		@Override
		public abstract T provide( Lazy lazy, Object parent );

		@Override
		public Class<T> getType( Class<?> arg0, Integer arg1 ) {
			return (Class<T>) arg0;
		}
	}

	public abstract static class LazyProvider<T> {
		public LazyProvider( Object parent ) {
		}

		public abstract T get( Context context );
	}

	private final HashMap<Class<?>, Class<?>> classToClassMap = new HashMap<Class<?>, Class<?>>();
	private final HashMap<Class<?>, Object> classToObjectMap = new HashMap<Class<?>, Object>();
	private final HashMap<Class<?>, FuelProvider> classToProviderMap = new HashMap<Class<?>, FuelModule.FuelProvider>();
	private final HashSet<Class<?>> otherInjectables = new HashSet<Class<?>>();
	private Application app;

	/* package private */
	Application.ActivityLifecycleCallbacks localLifecycleCallbacks;
	Application.ActivityLifecycleCallbacks externalLifecycleCallbacks;

	public FuelModule( Application app ) {
		this.app = app;

		localLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
			@Override
			public void onActivityCreated( Activity activity, Bundle savedInstanceState ) {
				if ( externalLifecycleCallbacks != null ) {
					externalLifecycleCallbacks.onActivityCreated( activity, savedInstanceState );
				}
				FuelModule.this.onActivityCreated( activity, savedInstanceState );
			}

			@Override
			public void onActivityStarted( Activity activity ) {
				if ( externalLifecycleCallbacks != null ) {
					externalLifecycleCallbacks.onActivityStarted( activity );
				}
				FuelModule.this.onActivityStarted( activity );
			}

			@Override
			public void onActivityResumed( Activity activity ) {
				if ( externalLifecycleCallbacks != null ) {
					externalLifecycleCallbacks.onActivityResumed( activity );
				}
				FuelModule.this.onActivityResumed( activity );
			}

			@Override
			public void onActivityPaused( Activity activity ) {
				FuelModule.this.onActivityPaused( activity );
				if ( externalLifecycleCallbacks != null ) {
					externalLifecycleCallbacks.onActivityPaused( activity );
				}
			}

			@Override
			public void onActivityStopped( Activity activity ) {
				FuelModule.this.onActivityStopped( activity );
				if ( externalLifecycleCallbacks != null ) {
					externalLifecycleCallbacks.onActivityStopped( activity );
				}
			}

			@Override
			public void onActivitySaveInstanceState( Activity activity, Bundle outState ) {
				FuelModule.this.onActivitySaveInstanceState( activity, outState );
				if ( externalLifecycleCallbacks != null ) {
					externalLifecycleCallbacks.onActivitySaveInstanceState( activity, outState );
				}
			}

			@Override
			public void onActivityDestroyed( Activity activity ) {
				FuelModule.this.onActivityDestroyed( activity );
				if ( externalLifecycleCallbacks != null ) {
					externalLifecycleCallbacks.onActivityDestroyed( activity );
				}
			}
		};
		app.registerActivityLifecycleCallbacks( localLifecycleCallbacks );
	}

	void prepareForDeath() {
		if ( localLifecycleCallbacks != null ) {
			app.unregisterActivityLifecycleCallbacks( localLifecycleCallbacks );
		}
		if ( externalLifecycleCallbacks != null ) {
			app.unregisterActivityLifecycleCallbacks( externalLifecycleCallbacks );
		}
	}

	protected void onActivityCreated( Activity activity, Bundle savedInstanceState ) {
	}

	protected void onActivityStarted( Activity activity ) {
	}

	protected void onActivityResumed( Activity activity ) {
	}

	protected void onActivityPaused( Activity activity ) {
	}

	protected void onActivityStopped( Activity activity ) {
	}

	protected void onActivitySaveInstanceState( Activity activity, Bundle outState ) {
	}

	protected void onActivityDestroyed( Activity activity ) {
	}

	/**
	 * Called whenever a new instance is obtained by Fuel.<br>
	 * For Singletons it should only be once per scope.<br>
	 * Only called for Singletons.
	 */
	@CallSuper
	protected void onObtainNewSingleton( Object instance ) {
		try {
			if ( instance instanceof OnFueled ) {
				( (OnFueled) instance ).onFueled();
			}
		} catch ( Exception e ) {
			FLog.e( e );
		}
	}


	/**
	 * Called when a critical failure occurs and Fuel is unable to recover.<br>
	 * Please see derived types of {@link FuelInjectionException} for details on conditions that may cause this method to be called.
	 *
	 * @param lazy the culprit
	 * @param exception what went wrong
	 */
	protected void onFailure( Lazy lazy, FuelInjectionException exception ) {
		throw exception;
	}

	/**
	 * Teach the fuel system how to obtain a context for a type.<br>
	 * If the given object is an instance of something you can provide a context for, then doing so eliminates the need to call ignite for that type
	 * and all its derived types.<br>
	 * One exception to this rule is that injections are always queued up until Fuel has been initialized.<br>
	 * <br>
	 * Be aware, if you return an Application context for something that is a ActivitySingleton, you'll get trouble.<br>
	 *
	 * @param object
	 * @return null if unable to map to a context
	 */
	@CallSuper
	protected Context provideContext( Object object ) {
		if ( object != null ) {
			if ( object instanceof Application ) {
				return FuelInjector.getApp(); // may be null if not initialized
			} else if ( object instanceof Service ) {
				return FuelInjector.getApp(); // may be null if not initialized
			} else if ( object instanceof Activity ) {
				return (Activity) object;
			} else if ( object instanceof View ) {
				return ( (View) object ).getContext();
			} else if ( object instanceof ArrayAdapter ) {
				return FuelInjector.toContext( ( (ArrayAdapter) object ).getContext() );
			} else if ( object instanceof AdapterView ) {
				return FuelInjector.toContext( ( (AdapterView) object ).getContext() );
			} else if ( FuelInjector.isAppSingleton( object.getClass() ) ) {
				return FuelInjector.getApp(); // may be null if not initialized
			}
		}
		return null;
	}

	/**
	 * Teach the fuel system how to obtain a context for a type.<br>
	 * If the given object is an instance of something you can provide a context for, then doing so eliminates the need to call ignite for that type
	 * and all its derived types.<br>
	 * One exception to this rule is that injections are always queued up until Fuel has been initialized.<br>
	 * <br>
	 * Be aware, if you return an Application context for something that is a ActivitySingleton, you'll get trouble.<br>
	 *
	 * @param type
	 * @return null if unable to map to a context
	 */
	@CallSuper
	protected final Context provideContext( Class<?> type ) {
		if ( type != null ) {
			if ( Application.class.isAssignableFrom( type ) ) {
				return FuelInjector.getApp(); // may be null if not initialized
			} else if ( Service.class.isAssignableFrom( type ) ) {
				return FuelInjector.getApp(); // may be null if not initialized
			} else if ( FuelInjector.isAppSingleton( type ) ) {
				return FuelInjector.getApp(); // may be null if not initialized
			}
		}
		return null;
	}

	protected void logD( String message ) {
		android.util.Log.d( FLog.TAG, message );
	}

	protected void logW( String message ) {
		android.util.Log.w( FLog.TAG, message );
	}

	protected void logE( String message ) {
		android.util.Log.e( FLog.TAG, message );
	}

	protected void logE( String message, Exception e ) {
		if ( message == null ) {
			message = "Message: " + e.getMessage();
		}
		android.util.Log.e( FLog.TAG, message, e );
	}

	/**
	 * Override to plug fuel into your own analytics system
	 */
	protected void leaveBreadCrumb( String fmt, Object... args ) {
		android.util.Log.d( FLog.TAG, String.format( fmt, args ) );
	}

	/**
	 * <pre>
	 * Override to set up your own injection configurations here.
	 * Call super.configure() for default configurations.
	 * Default Configurations (Overridable here)
	 *  {@link LayoutInflater}
	 *  {@link ConnectivityManager}
	 *  {@link AlarmManager}
	 *  {@link LocationManager}
	 *  {@link NotificationManager}
	 * Automatically handed by FuelInjector (not Overridable)
	 *   {@link Context} -- Will attempt to inject Activity but will fall back on Application.
	 *   {@link Activity} -- Will only inject Activity, unlike Context
	 *   {@link Application} -- Will only inject Application, unlike Context
	 * </pre>
	 */
	protected void configure() {
		// Services
		bind( LayoutInflater.class, getApplication().getSystemService( Context.LAYOUT_INFLATER_SERVICE ) );
		bind( ConnectivityManager.class, getApplication().getSystemService( Context.CONNECTIVITY_SERVICE ) );
		bind( AlarmManager.class, getApplication().getSystemService( Context.ALARM_SERVICE ) );
		bind( LocationManager.class, getApplication().getSystemService( Context.LOCATION_SERVICE ) );
		bind( NotificationManager.class, getApplication().getSystemService( Context.NOTIFICATION_SERVICE ) );
	}

	public void printBindings() {
		// FLog.d( "C2O Mapping: " );
		for ( Class c : classToObjectMap.keySet() ) {
			FLog.d( " -- C2O Mapping: %s -> %s", c, classToObjectMap.get( c ) );
		}

		// FLog.d( "C2P Mapping: " );
		for ( Class c : classToProviderMap.keySet() ) {
			FLog.d( " -- C2P Mapping: %s -> %s", c, classToProviderMap.get( c ) == null ? "Null" : "Provider" );
		}

		// FLog.d( "C2C Mapping: " );
		for ( Class c : classToClassMap.keySet() ) {
			FLog.d( " -- C2C Mapping: %s -> %s", c, classToClassMap.get( c ) );
		}
	}

	/**
	 * Add a mapping rule for Class to Class.<br>
	 * When you {@link Lazy#attain(Context, Class, Integer)}, the Class can be a base-interface and
	 * Fuel will search the Mapping Rules to decide the best instance.<br>
	 * <br>
	 * EX:<br>
	 * <pre>
	 * define interface Box;
	 * define interface BlackBox extends Box;
	 * define class LittleBlackBox implements BlackBox;
	 * bind Box -> BlackBox;
	 * bind BlackBox -> LittleBlackBox;
	 * // Now we have Box -> BlackBox -> LittleBlackBox
	 * // and when we request Box, Fuel will provide LittleBlackBox
	 * Box mBox = attain Box; // mBox is an instance of LittleBlackBox.
	 * </pre>
	 *
	 * @param from
	 * @param to
	 */
	protected void bind( Class<?> from, Class<?> to ) {
		classToClassMap.put( from, to );
	}

	/**
	 * Add a mapping rule for Class to Object.<br>
	 * Forever bind this class to this instance -- causes strong reference to instance forever turning the given object into a pseudo-singleton.<br>
	 * <br>
	 * Note that this follows the same logic as {@link #bind(Class, Class)} except that binding to an instance always terminates the Mapping Rule Chain.
	 *
	 * @param from
	 * @param to
	 * @see #bind(Class, Class)
	 */
	protected void bind( Class<?> from, Object to ) {
		classToObjectMap.put( from, to );
		classToObjectMap.put( to.getClass(), to );
	}

	/**
	 * Add a mapping rule for Class to {@link FuelProvider}.<br>
	 * Forever bind this class to this instance -- causes strong reference to instance forever turning the given object into a pseudo-singleton.<br>
	 * <br>
	 * Note that this follows the same logic as {@link #bind(Class, Class)} except that binding to a {@link FuelModule} always terminates the Mapping Rule Chain.
	 *
	 * @param from
	 * @param to
	 * @see #bind(Class, Class)
	 */
	protected void bind( Class<?> from, FuelProvider to ) {
		classToProviderMap.put( from, to );
	}

	/**
	 * Tell Fuel that this class is injectable.
	 * In other words, once instantiated, it will be placed in the singleton pool and future injections will return the same instance.
	 * Essentially it turns it into a singleton even if it is not declared @AppSingleton<br>
	 * <br>
	 * Useful when the code is not yours but you want only one instance.
	 */
	protected void markAsInjectable( Class<?> clazz ) {
		otherInjectables.add( clazz );
	}

	Application getApplication() {
		return app;
	}

	/**
	 * Unregisters old callbacks if any
	 *
	 * @param lifecycleCallbacks
	 */
	void setActivityCallbacks( Application.ActivityLifecycleCallbacks lifecycleCallbacks ) {
		if ( this.externalLifecycleCallbacks != null ) {
			app.unregisterActivityLifecycleCallbacks( this.externalLifecycleCallbacks );
		}

		this.externalLifecycleCallbacks = lifecycleCallbacks;
	}

	// FIXME: consider other mappings above
	// FIXME: cleanup exceptions

	/**
	 * Get an instance based on the FuelModule configure map and the given type
	 */
	Object obtainInstance( Lazy lazy, boolean allowAnonymousNewInstance ) throws FuelInjectionException {
		try {
			Class<?> leafType = lazy.leafType;

			// First try direct object map
			Object obj = classToObjectMap.get( leafType );
			if ( obj != null ) {
				if ( lazy.isDebug() ) {
					FLog.leaveBreadCrumb( "obtainInstance got %s", obj.getClass().getSimpleName() );
				}
				return obj;
			}

			// Second try provider map
			FuelProvider<?> provider = classToProviderMap.get( leafType );
			if ( provider != null ) {
				// FIXME: FUEL - maybe we don't need this kneejerk reaction to the contract -- I think FuelModule.getType( baseType ) is always
				// called and it calls
				// provider.getType()
				provider.getType( lazy.type,
						lazy.getFlavor() ); // Obey contract that provider.getType() will always be called prior to provider.provide()

				lazy.instance = provider.provide( lazy, lazy.getParent() );

				if ( lazy.isDebug() ) {
					FLog.leaveBreadCrumb( "obtainInstance provider provided instance for lazy - %s", lazy );
				}

				return initializeNewInstance( lazy );
			}

			// Third try class to class map
			Class<?> toType = classToClassMap.get( leafType );
			if ( toType != null ) {
				lazy.instance = newInstance( lazy );
				if ( lazy.isDebug() ) {
					FLog.leaveBreadCrumb( "obtainInstance classToClassMap found instance for lazy - %s", lazy );
				}
				return initializeNewInstance( lazy );
			}

			if ( FuelInjector.isSingleton( leafType ) ) {
				lazy.instance = newInstance( lazy );
				if ( lazy.isDebug() ) {
					FLog.leaveBreadCrumb( "obtainInstance other/ActivitySingleton/AppSingleton new instance returned instance for lazy - %s", lazy );
				}
				return initializeNewInstance( lazy );
			}

			// Last (no mapping) and not special, try to instantiate the literal type they requested
			if ( allowAnonymousNewInstance ) {
				lazy.instance = newInstance( lazy );
				if ( lazy.isDebug() ) {
					FLog.leaveBreadCrumb( "obtainInstance allowAnonymousNewInstance new instance for lazy %s", lazy );
				}
				return initializeNewInstance( lazy );
			}
		} catch ( Exception e ) {
			if ( lazy.isDebug() ) {
				FLog.leaveBreadCrumb( "obtainInstance Exception %s", e.getMessage() );
			}
			FuelInjector.doFailure( lazy, new FuelUnableToObtainInstanceException( e ) );
		}
		if ( lazy.isDebug() ) {
			FLog.leaveBreadCrumb( "obtainInstance fell through to return null" );
		}
		return null; // FIXME: probably shouldn't ever return null? probably throw?
	}

	Object newInstance( Lazy lazy ) throws FuelInjectionException {
		try {

			if ( lazy.isDebug() ) {
				FLog.leaveBreadCrumb( "newInstance for %s", lazy );
			}

			// FLog.d( "New Instance %s @ %s", leafType, context );
			try {
				return lazy.leafType.newInstance();
			} catch ( Exception e ) {
				if ( lazy.isDebug() ) {
					FLog.leaveBreadCrumb( "newInstance no empty constructor for %s", lazy );
				}
				// FLog.d( "No Empty Constructor for '%s'", leafType );
			}

			final Class<?> leafType = lazy.leafType;
			final Context context = lazy.getContext();

			Constructor[] constructors = leafType.getConstructors();
			if ( constructors.length == 0 ) {
				if ( lazy.isDebug() ) {
					FLog.leaveBreadCrumb( "newInstance no constructors for %s", lazy );
				}
				throw new Exception( "No constructors available for " + leafType + " maybe you need to provide FuelMapping?" );
			}
			Constructor ctor = constructors[0];
			// FLog.d( " -- found constructor with args" );

			Class[] parameterTypes = ctor.getParameterTypes();

			int len = parameterTypes.length;
			Object[] args = new Object[len];
			for ( int i = 0; i < len; i++ ) {
				Class type = parameterTypes[i];
				// FLog.d( " -- -- resolving %s", type );

				Class toType = getType( type, CacheKey.DEFAULT_FLAVOR ); // FIXME: FUEL args should support flavors

				CacheKey key = CacheKey.attain( toType );
				Object o = FuelInjector.getInstance( context, key, lazy.isDebug() );
				if ( o == null ) {
					// here we say false because we dont want to allow non mapped or non singletons to be instantiated for constructor args.
					// If a constructor takes an Integer as an argument, do you think its expecting a new Integer() ? probably not
					// but if you want to make exceptions for stuff you can call it out in the FuelModule with markAsInjectable().
					o = FuelInjector.newInstance( key, Lazy.attain( context, toType ), false );
				}

				if ( o != null ) {
					args[i] = o;
				} else {
					if ( lazy.isDebug() ) {
						FLog.leaveBreadCrumb( "newInstance unable to instantiate for %s", lazy );
					}
					throw new FuelInjectionException(
							"Unable to instantiate %s -- cannot satisfy argument %s, maybe they're not singletons or not mapped or marked as " +
									"Injectable?",
							leafType, type );
				}
			}
			// FLog.d( " -- found and instantiating... %s", ctor );
			Object inst = ctor.newInstance( args );
			// FLog.d( " -- found and instantiating... success %s", ctor );
			if ( inst != null ) {
				// FLog.d( " -- found and instantiating... success and not null %s", ctor );
				// FLog.w( "WARNING: Injecting with ambiguous constructor is dangerous: %s", leafType );
				// FLog.d( " -- found and instantiating... success and not null returning it %s", ctor );
				if ( lazy.isDebug() ) {
					FLog.leaveBreadCrumb( "newInstance got instance for %s", lazy );
				}
				return inst;
			}

		} catch ( FuelInjectionException e ) {
			if ( lazy.isDebug() ) {
				FLog.leaveBreadCrumb( "newInstance FIException %s", e.getMessage() );
			}
			throw e;
		} catch ( Exception e ) {
			if ( lazy.isDebug() ) {
				FLog.leaveBreadCrumb( "newInstance Exception %s", e.getMessage() );
			}
			throw new FuelInjectionException( e );
		}
		if ( lazy.isDebug() ) {
			FLog.leaveBreadCrumb( "newInstance Unable to instantiate %s", lazy );
		}
		throw new FuelInjectionException( "Unable to instantiate %s", lazy );
	}

	/**
	 * @param lazy must have an instance
	 * @return
	 * @throws Exception
	 */
	Object initializeNewInstance( Lazy lazy ) throws Exception {
		if ( lazy.isDebug() ) {
			FLog.leaveBreadCrumb( "initializeNewInstance for %s", lazy );
		}
		FuelInjector.doPostProcess( lazy );

		if ( FuelInjector.isSingleton( lazy.leafType ) ) { // TODO: could totally cache lazy.isSingleton ... later.
			onObtainNewSingleton( lazy.instance );
		}
		return lazy.instance;
	}

	// TODO: cache chains ? maybe cache already excludes non @FuelMethods to improve doAnnotLazyInject ?
	// private HashMap<Class<?>, List<Class<?>>> classChains = new HashMap<Class<?>, List<Class<?>>>();

	private List<Class<?>> getClassChain( Class<?> clazz ) {
		List<Class<?>> chain = new ArrayList<Class<?>>();
		chain.add( clazz );
		Class<?> tmp = clazz;
		while ( ( tmp = tmp.getSuperclass() ) != null ) {
			chain.add( tmp );
		}
		Collections.reverse( chain );
		return chain;
	}


	<T> Class<? extends T> getType( Class<T> baseType, Integer flavor ) { // Must stay logically paired with obtainInstance -- not super cool but ... for now.
		Object obj = classToObjectMap.get( baseType );
		if ( obj != null ) {
			return (Class<? extends T>) obj.getClass();
		}

		FuelProvider<?> provider = classToProviderMap.get( baseType );
		if ( provider != null ) {
			return (Class<? extends T>) provider.getType( baseType, flavor );
		}

		Class<?> toType = classToClassMap.get( baseType );
		if ( toType != null ) {
			return (Class<? extends T>) getType( toType, flavor ); // Recursive check
		}
		return baseType;
	}

}
