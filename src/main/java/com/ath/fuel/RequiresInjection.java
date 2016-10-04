package com.ath.fuel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Use to describe that a class should only be used via injection.<br>
 * When a class has this annotation, do not construct it by any other means.<br>
 * <br>
 * If you see this annotation on a class, be careful when modifying its constructor.<br>
 * The constructor of injected classes have special considerations, please read the Fuel documentation for more info.<br>
 * https://github.com/aarontharris/Fuel#construction
 */
@Retention( RetentionPolicy.RUNTIME /* Reflectively read by the VM at runtime */ )
public @interface RequiresInjection {
}
