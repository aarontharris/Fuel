package com.ath.fuel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention( RetentionPolicy.RUNTIME /* Reflectively read by the VM at runtime */ )
public @interface FragmentSingleton { /* I didn't want to keep java.x.inject just for the sake of @Singleton :/ */

}
