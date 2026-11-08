// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/** The interface Extended of m3core: Equal, Hash and Compare as cm3 has them. */
public final class Extended {
    private Extended() {}

    public static final boolean M3$PROVIDED = true;

    public static boolean Equal(double a, double b) {
        return a == b;
    }

    /** The exclusive or of the 32-bit words of the value (each sign extended), as cm3. */
    public static long Hash(double a) {
        long b = Double.doubleToRawLongBits(a);
        return (long) (int) b ^ (long) (int) (b >>> 32);
    }

    public static int Compare(double a, double b) {
        return a < b ? -1 : a > b ? 1 : 0;
    }
}
