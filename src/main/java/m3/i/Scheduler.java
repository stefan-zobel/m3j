// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/**
 * The interface Scheduler of m3core. The threads of the Java platform are
 * not switched by the program, so disabling and enabling switching have no
 * effect, as in cm3 with the threads of the system.
 */
public final class Scheduler {
    private Scheduler() {}

    public static final boolean M3$PROVIDED = true;

    public static void Yield() {
        java.lang.Thread.yield();
    }

    public static void DisableSwitching() {}

    public static void EnableSwitching() {}
}
