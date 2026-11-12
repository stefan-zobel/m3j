// Java port of CM3's m3-sys/m3middle/src/TFloat.i3 and TFloat.m3.
// Original: Copyright (C) 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import m3j.convert.Convert;
import m3j.middle.Target.Float;
import m3j.middle.Target.Precision;
import m3j.util.AssertError;

/**
 * Modula-3 target description.
 *
 * <p>This interface provides simulations of the target machine's
 * floating-point operations.
 *
 * <p>Unless otherwise specified, the arithmetic operations defined
 * below return TRUE if they succeed in producing a new target value,
 * otherwise they return FALSE.
 *
 * <p>Port: VAR f: Float is a {@link Target.Float} that the procedure
 * changes. REAL is a Java {@code float}, LONGREAL and EXTENDED are
 * {@code double}s. TRUNC, ROUND, FLOOR and CEILING behave as in the
 * reference compiler, which is built with the C back end: the C library's
 * trunc, round, floor and ceil, then a conversion to a 64-bit integer that
 * gives FIRST (INTEGER) for NaN and values out of range (x86-64).
 * Where a result is computed into a local before {@code f} is changed,
 * the original order of the assignments gives the same values, also when
 * {@code f} is the same record as {@code a} or {@code b}.
 */
public final class TFloat {
    private TFloat() {}

    public static final Float ZeroR = new Float(Precision.Short,    0, 0.0);
    public static final Float ZeroL = new Float(Precision.Long,     0, 0.0);
    public static final Float ZeroX = new Float(Precision.Extended, 0, 0.0);

    /** The NaN of an invalid operation on x86-64: the sign bit set. */
    private static final double X86_NAN = Double.longBitsToDouble(0xfff8000000000000L);

    /**
     * Returns x, the result of an operation on a and b, with a NaN that
     * the operation made from two numbers replaced by the NaN of x86-64.
     * The hardware decides the bits of such a NaN (x86-64 sets the sign
     * bit, AArch64 does not), and a folded constant must not depend on
     * the host of the compiler. A NaN operand passes through on both.
     */
    private static double invalid(double x, double a, double b) {
        if (Double.isNaN(x) && !Double.isNaN(a) && !Double.isNaN(b)) {
            return X86_NAN;
        }
        return x;
    }

    /**
     * Converts 'chars' to a floating point value with the specified
     * precision.  Note: regardless of the precision, the exponent must
     * be started by the character 'e'.
     */
    public static boolean New(char[] chars, int start, int n, Precision pre, Float f) {
        int used;
        f.pre = pre;
        f.exponent = 0;
        try {
            if (pre == Precision.Short) {
                Convert.FloatResult r = Convert.toFloat(chars, start, n);
                f.fraction = r.value();
                used = r.used();
            } else if (pre == Precision.Long) {
                Convert.DoubleResult r = Convert.toLongFloat(chars, start, n);
                f.fraction = r.value();
                used = r.used();
            } else {
                Convert.DoubleResult r = Convert.toExtended(chars, start, n);
                f.fraction = r.value();
                used = r.used();
            }
        } catch (Convert.Failed e) {
            return false;
        }
        return used == n && normalize(f);
    }

    /** New on a whole array. */
    public static boolean New(char[] chars, Precision pre, Float f) {
        return New(chars, 0, chars.length, pre, f);
    }

    /** Returns the precision of 'f'. */
    public static Precision prec(Float f) {
        return f.pre;
    }

    /** Returns 'a + b' unless there's an overflow. */
    public static boolean add(Float a, Float b, Float f) {
        if (a.pre != b.pre) {
            return false;
        }
        double x = invalid(a.fraction + b.fraction, a.fraction, b.fraction);
        f.pre = a.pre;
        f.exponent = 0;
        f.fraction = x;
        return normalize(f);
    }

    /** Returns 'a - b' unless there's an overflow. */
    public static boolean subtract(Float a, Float b, Float f) {
        if (a.pre != b.pre) {
            return false;
        }
        double x = invalid(a.fraction - b.fraction, a.fraction, b.fraction);
        f.pre = a.pre;
        f.exponent = 0;
        f.fraction = x;
        return normalize(f);
    }

    /** Returns 'a * b' unless there's an overflow. */
    public static boolean multiply(Float a, Float b, Float f) {
        if (a.pre != b.pre) {
            return false;
        }
        double x = invalid(a.fraction * b.fraction, a.fraction, b.fraction);
        f.pre = a.pre;
        f.exponent = 0;
        f.fraction = x;
        return normalize(f);
    }

    /** Returns 'a / b' unless there's an overflow. */
    public static boolean divide(Float a, Float b, Float f) {
        if (a.pre != b.pre) {
            return false;
        }
        if (b.fraction == 0.0) {
            return false;
        }
        double x = invalid(a.fraction / b.fraction, a.fraction, b.fraction);
        f.pre = a.pre;
        f.exponent = 0;
        f.fraction = x;
        return normalize(f);
    }

    /** Returns 'a MOD b' unless there's an overflow. */
    public static boolean mod(Float a, Float b, Float f) {
        double x;
        double y;
        if (a.pre != b.pre) {
            return false;
        }
        x = a.fraction;
        y = b.fraction;
        if (y == 0.0) {
            return false;
        }
        f.pre = a.pre;
        f.exponent = 0;
        f.fraction = invalid(x - y * (double) floorToInt(x / y), x, y);
        return normalize(f);
    }

    /** Returns 'a = b'. */
    public static boolean EQ(Float a, Float b) {
        if (a.pre != b.pre) {
            return false;
        }
        if (a.exponent != b.exponent) {
            return false;
        }
        if (a.fraction != b.fraction) {
            return false;
        }
        return true;
    }

    /** Returns 'a &lt; b' unless there's an overflow. */
    public static boolean LT(Float a, Float b) {
        if (a.pre != b.pre) {
            return false;
        }
        return a.fraction < b.fraction;
    }

    /** Returns 'a &lt;= b' unless there's an overflow. */
    public static boolean LE(Float a, Float b) {
        if (a.pre != b.pre) {
            return false;
        }
        return a.fraction <= b.fraction;
    }

    /** Returns 'FLOAT (a, p)' unless there's an overflow. */
    public static boolean floatF(Float a, Precision p, Float f) {
        long e = a.exponent;
        double x = a.fraction;
        f.pre = p;
        f.exponent = e;
        f.fraction = x;
        return normalize(f);
    }

    /** Returns 'FLOAT (i, p)' unless there's an overflow. */
    public static boolean floatI(long i, Precision p, Float f) {
        f.pre = p;
        f.exponent = 0;
        f.fraction = (double) i;
        return normalize(f);
    }

    /** Returns 'TRUNC(a)'. */
    public static long trunc(Float a) {
        return switch (a.pre) {
            case Short -> toInt64(truncD(toReal(a)));
            case Long -> toInt64(truncD(toLongreal(a)));
            case Extended -> toInt64(truncD(toExtended(a)));
        };
    }

    /** Returns 'ROUND(a)'. */
    public static long round(Float a) {
        return switch (a.pre) {
            case Short -> toInt64(roundD(toReal(a)));
            case Long -> toInt64(roundD(toLongreal(a)));
            case Extended -> toInt64(roundD(toExtended(a)));
        };
    }

    /** Returns 'FLOOR(a)' unless there's an overflow. */
    public static long floor(Float a) {
        return switch (a.pre) {
            case Short -> toInt64(Math.floor(toReal(a)));
            case Long -> toInt64(Math.floor(toLongreal(a)));
            case Extended -> toInt64(Math.floor(toExtended(a)));
        };
    }

    /** Returns 'CEILING(a)'. */
    public static long ceiling(Float a) {
        return switch (a.pre) {
            case Short -> toInt64(Math.ceil(toReal(a)));
            case Long -> toInt64(Math.ceil(toLongreal(a)));
            case Extended -> toInt64(Math.ceil(toExtended(a)));
        };
    }

    /** FLOOR on an EXTENDED, as in Mod. */
    private static long floorToInt(double x) {
        return toInt64(Math.floor(x));
    }

    /** C's trunc. */
    private static double truncD(double x) {
        return x < 0 ? Math.ceil(x) : Math.floor(x);
    }

    /** C's round: halfway cases away from zero. */
    private static double roundD(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            return x;
        }
        double ax = Math.abs(x);
        double t = Math.floor(ax);
        if (ax - t >= 0.5) {
            t += 1.0;
        }
        return Math.copySign(t, x);
    }

    /** (INT64) x on x86-64: FIRST (INTEGER) for NaN and values out of range. */
    private static long toInt64(double x) {
        if (Double.isNaN(x) || x >= 0x1p63 || x < -0x1p63) {
            return Long.MIN_VALUE;
        }
        return (long) x;
    }

    /**
     * Converts 'f' to a printable string in 'buf'.  Returns the
     * number of characters in the string.  Returns -1 if 'buf' is too short.
     */
    public static int toChars(Float f, char[] buf) {
        char[] zz = new char[46];
        int len;
        AssertError.check(f.exponent == 0, "TFloat");
        try {
            if (f.pre == Precision.Short) {
                len = Convert.fromFloat(zz, (float) f.fraction, 13, Convert.Style.Sci);
            } else if (f.pre == Precision.Long) {
                len = Convert.fromLongFloat(zz, f.fraction, 17, Convert.Style.Sci);
            } else {
                len = Convert.fromExtended(zz, f.fraction, 36, Convert.Style.Sci);
            }
        } catch (Convert.Failed e) {
            // <*FATAL Convert.Failed *>
            throw new IllegalStateException(e);
        }
        if (len > buf.length) {
            return -1;
        }
        System.arraycopy(zz, 0, buf, 0, len);
        return len;
    }

    /**
     * Converts 'f' to an array of bytes in 'buf'.  Returns the
     * number of bytes in 'buf'.  Returns -1 if 'buf' is too short.
     * Port: Byte = [0..255] is an {@code int}; the bytes are those of the
     * little-endian representation.
     */
    public static int toBytes(TargetMap.State map, Float f, int[] buf) {
        long bits;
        int len = (int) (map.Float_types[f.pre.ordinal()].size / 8);
        if (buf.length < len) {
            return -1;
        }
        if (f.pre == Precision.Short) {
            bits = java.lang.Float.floatToRawIntBits(toReal(f)) & 0xffffffffL;
        } else if (f.pre == Precision.Long) {
            bits = Double.doubleToRawLongBits(toLongreal(f));
        } else {
            bits = Double.doubleToRawLongBits(toExtended(f));
        }
        // Port: SUBARRAY (ptr^, 0, len) reads the first len bytes of x1, x2
        // or x3; for len > BYTESIZE of the value it reads beyond, which the
        // compiler's tables never ask for.
        for (int i = 0; i < len; i++) {
            buf[i] = (int) ((bits >>> (8 * i)) & 0xff);
        }
        return len;
    }

    /**
     * Converts the array of bytes in 'buf' to a float 'f' with
     * precision 'p'.
     */
    public static void fromBytes(TargetMap.State map, int[] buf, Precision p, Float f) {
        int len = (int) (map.Float_types[p.ordinal()].size / 8);
        AssertError.check(len <= buf.length, "TFloat");

        f.pre = p;
        f.exponent = 0;

        long bits = 0;
        for (int i = 0; i < len; i++) {
            bits |= (long) (buf[i] & 0xff) << (8 * i);
        }
        switch (p) {
            case Short -> f.fraction = java.lang.Float.intBitsToFloat((int) bits);
            case Long -> f.fraction = Double.longBitsToDouble(bits);
            case Extended -> f.fraction = Double.longBitsToDouble(bits);
        }
    }

    /*-------------------------------------------------------------- internal ---*/

    private static boolean normalize(Float f) {
        return f.exponent == 0;
    }

    private static float toReal(Float f) {
        return (float) f.fraction;
    }

    private static double toLongreal(Float f) {
        return f.fraction;
    }

    private static double toExtended(Float f) {
        return f.fraction;
    }
}
