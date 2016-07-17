package com.ath.fuel;

import android.support.annotation.MainThread;

/**
 * Implement this interface to receive a callback immediately after fuel injections have completed.
 */
public interface OnFueled {

	/**
	 * Called immediately after injections have completed
	 */
	@MainThread void onFueled();

}
