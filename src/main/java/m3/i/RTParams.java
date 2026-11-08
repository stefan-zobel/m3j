// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/**
 * The interface RTParams of m3core: the runtime arguments of the command
 * line ("@M3x" and "@M3x=y"). A program of m3j has none: its runtime takes
 * its options from the JVM, and every argument goes to the program.
 */
public final class RTParams {
    private RTParams() {}

    public static final boolean M3$PROVIDED = true;

    public static long NumParameters = 0;

    public static String Nth(long n) {
        return null;
    }

    public static boolean IsPresent(String n) {
        return false;
    }

    public static String Value(String n) {
        return null;
    }

    public static Object RawValue(String n) {
        return null;
    }

    public static void Init() {
        // nothing to read
    }
}
