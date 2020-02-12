package com.ath.fuel.err;

import android.content.Context;

import androidx.annotation.Nullable;

/**
 * Thrown when Fuel was unable to obtain a context the inject lazy from the parent.<br>
 * Typically this is when the parent was not ignited or you tried to Lazy.get() after the context was GC'd<br>
 * <br>
 * You may optionally provide a context to Fuel by calling {@link #setContext(Context)}.<br>
 * If you choose not to, a runtime exception will be thrown upon Lazy.get()<br>
 */
@SuppressWarnings("serial")
public class FuelUnableToObtainContextException extends FuelInjectionException {
    private Context mContext;

    public FuelUnableToObtainContextException(Exception exception) {
        super(exception);
    }

    public FuelUnableToObtainContextException(String format, Object... objects) {
        super(String.format(format, objects));
    }

    public FuelUnableToObtainContextException(Exception exception, String format, Object... objects) {
        super(String.format(format, objects), exception);
    }

    /**
     * Give Fuel the best context for this failure.<br>
     * The context will be immediately consumed and not held.<br>
     */
    public void setContext(@Nullable Context context) {
        mContext = context;
    }

    /**
     * Read once then its gone.
     */
    @Nullable public Context consumeContext() {
        Context out = mContext;
        mContext = null;
        return out;
    }
}
