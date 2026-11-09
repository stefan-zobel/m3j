// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import m3j.rt.M3Object;
import m3j.rt.Rt;
import m3j.rt.Typecodes;

/**
 * The interface RTType of m3core: typecodes. A typecode is given to a type
 * when it is first used (TYPECODE, NARROW, ...). Get, which returns cm3's
 * type cell, is not provided. Supertype of ROOT is NoSuchType, as the
 * interface says (cm3 returns the typecode of an internal type there).
 */
public final class RTType {
    private RTType() {}

    public static final boolean M3$PROVIDED = true;

    /** RTType.NoSuchType = LAST (Typecode) */
    static final int NO_SUCH_TYPE = 0xFFFFF;

    public static int MaxTypecode() {
        return (int) Typecodes.max();
    }

    public static boolean IsSubtype(int a, int b) {
        if (a == 0) {
            // NULL is a subtype of every traced reference type
            check(b);
            return true;
        }
        Class<?> ca = check(a), cb = b == 0 ? null : check(b);
        return cb != null && cb.isAssignableFrom(ca);
    }

    public static int Supertype(int tc) {
        Class<?> c = tc == 0 ? null : check(tc);
        if (c == null || c == M3Object.class) {
            return NO_SUCH_TYPE;
        }
        // an object type below a Java class stops at that class
        if (!M3Object.class.isAssignableFrom(c) && !hasFingerprint(c.getSuperclass())) {
            return NO_SUCH_TYPE;
        }
        return (int) Typecodes.of(c.getSuperclass());
    }

    private static boolean hasFingerprint(Class<?> c) {
        if (c == null) {
            return false;
        }
        try {
            c.getDeclaredField("M3$FP");
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    public static boolean IsTraced(int tc) {
        if (tc != 0) {
            check(tc);
        }
        return true;
    }

    /** The number of open dimensions of the referent of tc (fields n0, n1, ... of its class). */
    public static long GetNDimensions(int tc) {
        Class<?> c = tc == 0 ? null : check(tc);
        int n = 0;
        if (c != null) {
            while (true) {
                try {
                    c.getField("n" + n);
                    n++;
                } catch (NoSuchFieldException e) {
                    break;
                }
            }
        }
        return n;
    }

    /** The class of typecode tc; a checked runtime error if tc is not proper. */
    private static Class<?> check(int tc) {
        Class<?> c = Typecodes.classOf(tc);
        if (c == null && tc != 0) {
            throw Rt.error(RuntimeError.ValueOutOfRange);
        }
        return c;
    }
}
