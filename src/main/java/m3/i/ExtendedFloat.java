// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/**
 * The interface ExtendedFloat of m3core (Float(Extended)). EXTENDED is
 * LONGREAL on AMD64_NT, and CM3's module calls LongFloat for every
 * procedure, so the errors are those of LongFloat. ToDecimal is not
 * implemented yet, as in {@link LongFloat}.
 */
public final class ExtendedFloat {
    private ExtendedFloat() {}

    public static final boolean M3$PROVIDED = true;

    public static double Scalb(double x, long n) {
        return LongFloat.Scalb(x, n);
    }

    public static double Logb(double x) {
        return LongFloat.Logb(x);
    }

    public static long ILogb(double x) {
        return LongFloat.ILogb(x);
    }

    public static double NextAfter(double x, double y) {
        return LongFloat.NextAfter(x, y);
    }

    public static double CopySign(double x, double y) {
        return LongFloat.CopySign(x, y);
    }

    public static boolean Finite(double x) {
        return LongFloat.Finite(x);
    }

    public static boolean IsNaN(double x) {
        return LongFloat.IsNaN(x);
    }

    public static int Sign(double x) {
        return LongFloat.Sign(x);
    }

    public static boolean Differs(double x, double y) {
        return LongFloat.Differs(x, y);
    }

    public static boolean Unordered(double x, double y) {
        return LongFloat.Unordered(x, y);
    }

    public static double Sqrt(double x) {
        return LongFloat.Sqrt(x);
    }

    public static int Class(double x) {
        return LongFloat.Class(x);
    }

    public static double FromDecimal(int sign, byte[] digits, int off, int n, long exp) {
        return LongFloat.FromDecimal(sign, digits, off, n, exp);
    }
}
