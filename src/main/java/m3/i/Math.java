// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import m3j.rt.DoubleRef;
import m3j.rt.IntRef;

/**
 * The interface Math of libm3: the functions of the C math library. The
 * Bessel functions, erf, erfc, gamma and cabs are not provided. expm1,
 * log1p, asinh, acosh and atanh are not in the C library of Windows: they
 * are computed with the formulas of CM3's module Math for WIN32, so that
 * their results are the same.
 */
public final class Math {
    private Math() {}

    public static final boolean M3$PROVIDED = true;

    /** The sign of the gamma function, set by gamma (not provided). */
    public static int signgam;

    public static double exp(double x) {
        return java.lang.Math.exp(x);
    }

    public static double expm1(double x) {
        if (java.lang.Math.abs(x) < 0.04) {
            // the Taylor expansion of exp(x)-1
            return x * (1.0 + x * R2 * (1.0 + x * R3 * (1.0 + x * R4 * (1.0 + x * R5
                * (1.0 + x * R6 * (1.0 + x * R7 * (1.0 + x * R8)))))));
        }
        return exp(x) - 1.0;
    }

    private static final double R2 = 1.0 / 2.0, R3 = 1.0 / 3.0, R4 = 1.0 / 4.0, R5 = 1.0 / 5.0,
        R6 = 1.0 / 6.0, R7 = 1.0 / 7.0, R8 = 1.0 / 8.0, R9 = 1.0 / 9.0, R11 = 1.0 / 11.0,
        R13 = 1.0 / 13.0, R15 = 1.0 / 15.0;

    /** The Taylor expansion of ln((1+x)/(1-x))/2, which is atanh(x). */
    private static double atanhSmall(double x) {
        double x2 = x * x;
        return x * (1.0 + x2 * (R3 + x2 * (R5 + x2 * (R7 + x2 * (R9 + x2 * (R11 + x2 * (R13 + x2 * R15)))))));
    }

    public static double log(double x) {
        return java.lang.Math.log(x);
    }

    public static double log10(double x) {
        return java.lang.Math.log10(x);
    }

    public static double log1p(double x) {
        if (java.lang.Math.abs(x) < 0.04) {
            return 2.0 * atanhSmall(x / (x + 2.0));
        }
        return log(1.0 + x);
    }

    public static double pow(double x, double y) {
        return java.lang.Math.pow(x, y);
    }

    public static double sqrt(double x) {
        return java.lang.Math.sqrt(x);
    }

    public static double cos(double x) {
        return java.lang.Math.cos(x);
    }

    public static double sin(double x) {
        return java.lang.Math.sin(x);
    }

    public static double tan(double x) {
        return java.lang.Math.tan(x);
    }

    public static double acos(double x) {
        return java.lang.Math.acos(x);
    }

    public static double asin(double x) {
        return java.lang.Math.asin(x);
    }

    public static double atan(double x) {
        return java.lang.Math.atan(x);
    }

    public static double atan2(double y, double x) {
        return java.lang.Math.atan2(y, x);
    }

    public static double sinh(double x) {
        return java.lang.Math.sinh(x);
    }

    public static double cosh(double x) {
        return java.lang.Math.cosh(x);
    }

    public static double tanh(double x) {
        return java.lang.Math.tanh(x);
    }

    public static double asinh(double x) {
        return log(sqrt(x * x + 1.0) + x);
    }

    public static double acosh(double x) {
        return log(sqrt(x * x - 1.0) + x);
    }

    public static double atanh(double x) {
        if (java.lang.Math.abs(x) < 0.1) {
            return atanhSmall(x);
        }
        return (log(1.0 + x) - log(1.0 - x)) * R2;
    }

    public static double ceil(double x) {
        return java.lang.Math.ceil(x);
    }

    public static double floor(double x) {
        return java.lang.Math.floor(x);
    }

    public static double rint(double x) {
        return java.lang.Math.rint(x);
    }

    public static double fabs(double x) {
        return java.lang.Math.abs(x);
    }

    public static double hypot(double x, double y) {
        return java.lang.Math.hypot(x, y);
    }

    /** x = f * 2^exp with 0.5 <= |f| < 1 (f = x and exp = 0 for 0, infinities and NaN). */
    public static double frexp(double x, IntRef exp) {
        if (x == 0.0 || Double.isInfinite(x) || Double.isNaN(x)) {
            exp.set(0);
            return x;
        }
        int e = java.lang.Math.getExponent(x);
        if (e == Double.MIN_EXPONENT - 1) {
            // subnormal: scale into the normal range first
            x = x * 0x1p54;
            e = java.lang.Math.getExponent(x) - 54;
            exp.set(e + 1);
            return java.lang.Math.scalb(x, -(e + 54 + 1));
        }
        exp.set(e + 1);
        return java.lang.Math.scalb(x, -(e + 1));
    }

    public static double ldexp(double x, int exp) {
        return java.lang.Math.scalb(x, exp);
    }

    /** The fractional part of x; its integral part goes to i (both with the sign of x). */
    public static double modf(double x, DoubleRef i) {
        if (Double.isInfinite(x)) {
            i.set(x);
            return java.lang.Math.copySign(0.0, x);
        }
        double n = x < 0 ? java.lang.Math.ceil(x) : java.lang.Math.floor(x);
        i.set(n);
        return java.lang.Math.copySign(x - n, x);
    }

    public static double fmod(double x, double y) {
        return x % y;
    }

    public static double drem(double x, double y) {
        return java.lang.Math.IEEEremainder(x, y);
    }

    public static double remainder(double x, double y) {
        return java.lang.Math.IEEEremainder(x, y);
    }
}
