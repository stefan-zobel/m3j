// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/**
 * The interface RTCollector of m3core. The collector of the Java platform
 * runs on its own, and a program cannot see whether it moves objects, so
 * disabling and enabling it or its motion have no effect. Collect asks the
 * Java platform for a garbage collection and runs the finalization that
 * puts the weak references that died on the list of the module WeakRef.
 */
public final class RTCollector {
    private RTCollector() {}

    public static final boolean M3$PROVIDED = true;

    public static void Disable() {}

    public static void Enable() {}

    public static void DisableMotion() {}

    public static void EnableMotion() {}

    public static void Collect() {
        m3j.rt.Externals.M3jWeak__Collect();
    }
}
