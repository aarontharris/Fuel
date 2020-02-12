package com.ath.fuel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This can be a Fragment or a View that you deem to be the defining screen area that reflects a scope.
 * ViewRoots cannot be nested but can live parallel to each other.
 * When a view a ViewRoot dies, so do all the ViewRootSingletons within the associated ViewRoot Module.
 *
 * <pre>
 * Caveats:
 *
 * ## Don't cross the streams -- it would be bad.
 *
 * Don't detatch a view that lived under one ViewRoot and reattach it under another.
 * Consider this imaginary View Tree:
 *
 *                A
 *          B           C
 *       D     E     F     G
 *      H I   J K   L M   N O
 *
 * A is the Activity
 * B is a View Root
 * C is a View Root
 *
 * Just like an ActivitySingleton, two different activities would get two different instances.
 * B and C are different scopes, they will get a different instance when injecting the same type.
 *
 * Imagine:
 *   View-D injected a ViewRootSingleton X
 *   View-G injected a ViewRootSingleton X
 *   D.X != G.X
 *
 *   So if we detached D from under B and moved it somewhere under C
 *   Suddenly D.X would be incompatible with the rest of C's children.
 *
 *
 * Best practice is to destroy B.D and create a new C.D.
 *
 * </pre>
 *
 */
@Retention( RetentionPolicy.RUNTIME /* Reflectively read by the VM at runtime */ )
public @interface ViewRoot {

}
