// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The interface LongFloat of m3core (Float(LongReal)), with the results of
 * CM3's IEEE implementation on AMD64_NT. The procedures work on the bits
 * of the values, as the CM3 module does through LongRealRep. ToDecimal is
 * not implemented yet. A provided class can return a record, but it has to
 * create the record's class of the program by reflection, as
 * {@link Fingerprint} does; for ToDecimal that is DecimalApprox.
 */
public final class LongFloat {
    private LongFloat() {}

    public static final boolean M3$PROVIDED = true;

    // The values of IEEEClass.
    static final int SignalingNaN = 0;
    static final int QuietNaN = 1;
    static final int Infinity = 2;
    static final int Normal = 3;
    static final int Denormal = 4;
    static final int Zero = 5;

    /** The source file of the CM3 implementation, for the place of the errors. */
    private static final String FILE = "../src/float/IEEE/LongFloat.m3";

    private static final long SIGN = 0x8000000000000000L;
    private static final long SIGNIFICAND = 0x000fffffffffffffL;

    // ------------------------------------------------- errors of the module

    /** The runtime error code at the given line of a CM3 module. */
    static RuntimeException error(int code, String file, int line) {
        RuntimeError.E e = new RuntimeError.E(code);
        e.at(file, line);
        return e;
    }

    /** The failed &lt;*ASSERT FALSE*&gt; at the given line of a CM3 module. */
    static RuntimeException assertFalse(String file, int line) {
        RuntimeError.E e = new RuntimeError.E(RuntimeError.AssertFailed);
        e.info = "FALSE";
        e.at(file, line);
        return e;
    }

    /** The exponent n of FPU.scalb (an int of C), checked as CM3 passes it. */
    static int scalbExponent(long n, String file) {
        if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) {
            throw error(RuntimeError.ValueOutOfRange, file, 20);
        }
        return (int) n;
    }

    private static int exponent(long b) {
        return (int) (b >>> 52) & 0x7ff;
    }

    // ------------------------------------------------------------ procedures

    public static double Scalb(double x, long n) {
        return ldexp(x, scalbExponent(n, FILE));
    }

    /**
     * The ldexp of the C library of AMD64_NT, which FPU.scalb is: x * 2^n,
     * but a result below the range of the normal numbers is truncated
     * toward zero instead of rounded.
     */
    static double ldexp(double x, int n) {
        long b = Double.doubleToRawLongBits(x);
        int exp = exponent(b);
        long m = b & SIGNIFICAND;
        if (exp == 0x7ff || (exp == 0 && m == 0)) {
            return java.lang.Math.scalb(x, n);
        }
        int e; // x = m * 2^e
        if (exp == 0) {
            e = -1074;
        } else {
            m |= 1L << 52;
            e = exp - 1075;
        }
        long lead = e + 63 - Long.numberOfLeadingZeros(m) + (long) n;
        if (lead >= -1022) {
            return java.lang.Math.scalb(x, n);
        }
        long shift = -1074L - (e + (long) n);
        long q = shift >= 64 ? 0 : shift <= 0 ? m << -shift : m >>> shift;
        double r = java.lang.Math.scalb((double) q, -1074);
        return b < 0 ? -r : r;
    }

    public static double Logb(double x) {
        long b = Double.doubleToRawLongBits(x);
        return switch (Class(x)) {
            case SignalingNaN, QuietNaN -> x;
            case Infinity -> java.lang.Math.abs(x);
            case Zero -> Double.NEGATIVE_INFINITY;
            case Normal -> (double) (exponent(b) - 1023);
            default -> -1022.0;
        };
    }

    public static long ILogb(double x) {
        long b = Double.doubleToRawLongBits(x);
        switch (Class(x)) {
            case SignalingNaN, QuietNaN:
                throw assertFalse(FILE, 51);
            case Infinity:
                return Long.MAX_VALUE;
            case Zero:
                return Long.MIN_VALUE;
            case Normal:
                return exponent(b) - 1023;
            default: {
                long significand0 = (b >>> 32) & 0xfffff;
                long significand1 = b & 0xffffffffL;
                long v, w;
                long n;
                if (significand0 == 0) {
                    v = 0x80000000L;
                    n = -1043;
                    w = significand1;
                } else {
                    v = 0x00080000L;
                    n = -1023;
                    w = significand0;
                }
                while ((v & w) == 0) {
                    v >>>= 1;
                    n--;
                }
                return n;
            }
        }
    }

    public static double NextAfter(double x, double y) {
        if (x == y) {
            return x;
        }
        if (IsNaN(x) || !Finite(x)) {
            return x;
        }
        if (IsNaN(y)) {
            return y;
        }
        if (x == 0.0) {
            return Double.longBitsToDouble((Double.doubleToRawLongBits(y) & SIGN) | 1);
        }
        long b = Double.doubleToRawLongBits(x);
        long sign = b & SIGN;
        int exp = exponent(b);
        long sig = b & SIGNIFICAND;
        if ((x > 0.0 && x > y) || (x < 0.0 && x < y)) {
            if (sig == 0) {
                sig = SIGNIFICAND;
                exp--;
                if (exp == 0) {
                    return (2.0 * x) / 2.0; // generate underflow
                }
            } else {
                sig--;
            }
        } else {
            if (sig == SIGNIFICAND) {
                sig = 0;
                exp++;
                if (exp == 0x7ff) {
                    return x + x; // generate overflow
                }
            } else {
                sig++;
            }
        }
        return Double.longBitsToDouble(sign | ((long) exp << 52) | sig);
    }

    public static double CopySign(double x, double y) {
        long b = Double.doubleToRawLongBits(x) & ~SIGN;
        return Double.longBitsToDouble(b | (Double.doubleToRawLongBits(y) & SIGN));
    }

    public static boolean Finite(double x) {
        return exponent(Double.doubleToRawLongBits(x)) != 0x7ff;
    }

    public static boolean IsNaN(double x) {
        long b = Double.doubleToRawLongBits(x);
        return exponent(b) == 0x7ff && (b & SIGNIFICAND) != 0;
    }

    public static int Sign(double x) {
        return (int) (Double.doubleToRawLongBits(x) >>> 63);
    }

    public static boolean Differs(double x, double y) {
        return x < y || y < x;
    }

    public static boolean Unordered(double x, double y) {
        return !(x <= y || y <= x);
    }

    public static double Sqrt(double x) {
        return sqrt(x);
    }

    /**
     * The sqrt of the C library: the square root, or the default NaN of the
     * processor (a negative quiet NaN) for a negative argument.
     */
    static double sqrt(double x) {
        if (x < 0.0) {
            return Double.longBitsToDouble(0xfff8000000000000L);
        }
        return java.lang.Math.sqrt(x);
    }

    public static int Class(double x) {
        long b = Double.doubleToRawLongBits(x);
        int exp = exponent(b);
        long sig = b & SIGNIFICAND;
        if (exp == 0) {
            return sig == 0 ? Zero : Denormal;
        } else if (exp != 0x7ff) {
            return Normal;
        } else if (sig == 0) {
            return Infinity;
        } else if ((b & 0x0008000000000000L) != 0) {
            return QuietNaN;
        } else {
            return SignalingNaN;
        }
    }

    public static double FromDecimal(int sign, byte[] digits, int off, int n, long exp) {
        return fromDecimal(sign, digits, off, n, exp, FILE, 197);
    }

    /**
     * FromDecimal of RealFloat and LongFloat: the value of the text
     * "sign d0.d1...dn e exp" as the strtod of the C library converts it
     * (correctly rounded). line is the place of the subscript of digits[0].
     */
    static double fromDecimal(int sign, byte[] digits, int off, int n, long exp, String file, int line) {
        if (n == 0) {
            throw error(RuntimeError.SubscriptOutOfRange, file, line);
        }
        StringBuilder sb = new StringBuilder(n);
        int lead = -1;
        for (int i = 0; i < n; i++) {
            int d = digits[off + i];
            sb.append((char) ('0' + d));
            if (lead < 0 && d != 0) {
                lead = i;
            }
        }
        double v;
        if (lead < 0) {
            v = 0.0;
        } else {
            // The value is the integer of the digits times 10^(exp - (n - 1));
            // its leading digit has the weight 10^(exp - lead).
            long top = exp - lead;
            if (top > 400) {
                v = Double.POSITIVE_INFINITY;
            } else if (top < -400) {
                v = 0.0;
            } else {
                long scale = (n - 1) - exp;
                v = new BigDecimal(new BigInteger(sb.toString()), (int) scale).doubleValue();
            }
        }
        return sign != 0 ? -v : v;
    }
}
