package com.ath.fuel;

import android.support.annotation.MainThread;

/**
 * Implement this interface to receive a callback immediately after fuel injections have completed.
 */
public interface OnFueled {

	/**
	 * Called immediately after injections have completed
	 * Should not get called twice, but potentially could get called twice
	 * if you accidentally inject and ignite the same object, or ignite twice.
	 */
	@MainThread void onFueled();

}
