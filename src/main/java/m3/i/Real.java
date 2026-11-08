// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/** The interface Real of m3core: Equal, Hash and Compare as cm3 has them. */
public final class Real {
    private Real() {}

    public static final boolean M3$PROVIDED = true;

    public static boolean Equal(float a, float b) {
        return a == b;
    }

    /** The exclusive or of the 32-bit words of the value (each sign extended), as cm3. */
    public static long Hash(float a) {
        return Float.floatToRawIntBits(a);
    }

    public static int Compare(float a, float b) {
        return a < b ? -1 : a > b ? 1 : 0;
    }
}
