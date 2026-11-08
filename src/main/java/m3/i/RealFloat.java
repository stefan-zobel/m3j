// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/**
 * The interface RealFloat of m3core (Float(Real)), with the results of
 * CM3's IEEE implementation on AMD64_NT. The procedures work on the bits
 * of the values, as the CM3 module does through RealRep; Scalb, Sqrt and
 * FromDecimal compute in LONGREAL and round the result to REAL. ToDecimal
 * is not implemented yet, as in {@link LongFloat}.
 */
public final class RealFloat {
    private RealFloat() {}

    public static final boolean M3$PROVIDED = true;

    /** The source file of the CM3 implementation, for the place of the errors. */
    private static final String FILE = "../src/float/IEEE/RealFloat.m3";

    private static final int SIGN = 0x80000000;
    private static final int SIGNIFICAND = 0x007fffff;

    private static int exponent(int b) {
        return (b >>> 23) & 0xff;
    }

    public static float Scalb(float x, long n) {
        return (float) LongFloat.ldexp((double) x, LongFloat.scalbExponent(n, FILE));
    }

    public static float Logb(float x) {
        int b = Float.floatToRawIntBits(x);
        return switch (Class(x)) {
            case LongFloat.SignalingNaN, LongFloat.QuietNaN -> x;
            case LongFloat.Infinity -> java.lang.Math.abs(x);
            case LongFloat.Zero -> Float.NEGATIVE_INFINITY;
            case LongFloat.Normal -> (float) (exponent(b) - 127);
            default -> -126.0f;
        };
    }

    public static long ILogb(float x) {
        int b = Float.floatToRawIntBits(x);
        switch (Class(x)) {
            case LongFloat.SignalingNaN, LongFloat.QuietNaN:
                throw LongFloat.assertFalse(FILE, 47);
            case LongFloat.Infinity:
                return Long.MAX_VALUE;
            case LongFloat.Zero:
                return Long.MIN_VALUE;
            case LongFloat.Normal:
                return exponent(b) - 127;
            default: {
                int v = 0x400000;
                long n = -127;
                int w = b & SIGNIFICAND;
                while ((v & w) == 0) {
                    v >>>= 1;
                    n--;
                }
                return n;
            }
        }
    }

    public static float NextAfter(float x, float y) {
        if (x == y) {
            return x;
        }
        if (IsNaN(x) || !Finite(x)) {
            return x;
        }
        if (IsNaN(y)) {
            return y;
        }
        if (x == 0.0f) {
            return Float.intBitsToFloat((Float.floatToRawIntBits(y) & SIGN) | 1);
        }
        int b = Float.floatToRawIntBits(x);
        int sign = b & SIGN;
        int exp = exponent(b);
        int sig = b & SIGNIFICAND;
        if ((x > 0.0f && x > y) || (x < 0.0f && x < y)) {
            if (sig == 0) {
                sig = SIGNIFICAND;
                exp--;
                if (exp == 0) {
                    return (2.0f * x) / 2.0f; // generate underflow
                }
            } else {
                sig--;
            }
        } else {
            if (sig == SIGNIFICAND) {
                sig = 0;
                exp++;
                if (exp == 0xff) {
                    return x + x; // generate overflow
                }
            } else {
                sig++;
            }
        }
        return Float.intBitsToFloat(sign | (exp << 23) | sig);
    }

    public static float CopySign(float x, float y) {
        int b = Float.floatToRawIntBits(x) & ~SIGN;
        return Float.intBitsToFloat(b | (Float.floatToRawIntBits(y) & SIGN));
    }

    public static boolean Finite(float x) {
        return exponent(Float.floatToRawIntBits(x)) != 0xff;
    }

    public static boolean IsNaN(float x) {
        int b = Float.floatToRawIntBits(x);
        return exponent(b) == 0xff && (b & SIGNIFICAND) != 0;
    }

    public static int Sign(float x) {
        return Float.floatToRawIntBits(x) >>> 31;
    }

    public static boolean Differs(float x, float y) {
        return x < y || y < x;
    }

    public static boolean Unordered(float x, float y) {
        return !(x <= y || y <= x);
    }

    public static float Sqrt(float x) {
        return (float) LongFloat.sqrt((double) x);
    }

    public static int Class(float x) {
        int b = Float.floatToRawIntBits(x);
        int exp = exponent(b);
        int sig = b & SIGNIFICAND;
        if (exp == 0) {
            return sig == 0 ? LongFloat.Zero : LongFloat.Denormal;
        } else if (exp != 0xff) {
            return LongFloat.Normal;
        } else if (sig == 0) {
            return LongFloat.Infinity;
        } else if ((b & 0x00400000) != 0) {
            return LongFloat.QuietNaN;
        } else {
            return LongFloat.SignalingNaN;
        }
    }

    public static float FromDecimal(int sign, byte[] digits, int off, int n, long exp) {
        return (float) LongFloat.fromDecimal(sign, digits, off, n, exp, FILE, 176);
    }
}
