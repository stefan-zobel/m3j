// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import m3j.rt.M3Exception;

/** The interface Tick of m3core: a clock of nanoseconds (System.nanoTime). */
public final class Tick {
    private Tick() {}

    public static final boolean M3$PROVIDED = true;

    /** EXCEPTION Overflow */
    public static final class Overflow extends M3Exception {
        private static final long serialVersionUID = 1L;

        @Override
        public String m3Name() {
            return "Tick.Overflow";
        }
    }

    public static long Now() {
        return System.nanoTime();
    }

    /** t is an unsigned number of nanoseconds (Word.T). */
    public static double ToSeconds(long t) {
        double n = t >= 0 ? t : ((t >>> 1) * 2.0 + (t & 1));
        return n * 1.0e-9;
    }

    public static long FromSeconds(double s) {
        double n = s * 1.0e9;
        if (!(n >= 0) || n >= 0x1p64) {
            throw new Overflow();
        }
        return n >= 0x1p63 ? (long) (n - 0x1p63) ^ Long.MIN_VALUE : (long) n;
    }
}
