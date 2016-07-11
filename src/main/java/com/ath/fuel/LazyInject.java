package com.ath.fuel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME /* Reflectively read by the VM at runtime */)
public @interface LazyInject {

}
