// Java counterpart of CM3's m3-libs/m3core/src/convert/CConvert.i3, the
// interface to the floating-point conversions in
// m3-libs/m3core/src/Csupport/dtoa.c (m3_ftoa, m3_dtoa, m3_strtod).
// Original: Copyright (C) 1990, Digital Equipment Corporation, and
// Copyright (C) 1998-2000 by Lucent Technologies (David M. Gay's gdtoa).
// Distributed under the licenses in licenses/COPYRIGHT-DEC and
// licenses/COPYRIGHT-LUCENT.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Binary/decimal floating-point conversions with the results of CM3's
 * m3_ftoa, m3_dtoa and m3_strtod.
 *
 * <p>Port: these are not translations of gdtoa's algorithms. The digits
 * are computed with exact {@link BigDecimal} arithmetic, which gives the
 * correctly rounded results that gdtoa computes (modes 2 and 3 round the
 * exact binary value half-even; strtod rounds to nearest, ties to even,
 * with gradual underflow). The syntax accepted by strtod follows
 * strtodg_D2A as configured in CM3 (INFNAN_CHECK and No_Hex_NaN defined).
 */
public final class CConvert {
    private CConvert() {}

    /**
     * The result of dtoa: the digit string ("Infinity" or "NaN" for those
     * values), the position of the decimal point (-32768 for Infinity and
     * NaN), and whether the sign bit is set.
     */
    public record Digits(String digits, int decpt, boolean sign) {}

    /** The result of strtod: the value and the index of the first unused character. */
    public record Parsed(double value, int end) {}

    /** m3_dtoa (d, mode, ndigits, decpt, sign, se). */
    public static Digits dtoa(double d, int mode, int ndigits) {
        long bits = Double.doubleToRawLongBits(d);
        boolean sign = bits < 0;
        if ((bits & 0x7ff0000000000000L) == 0x7ff0000000000000L) {
            // Infinity or NaN
            if ((bits & 0x000fffffffffffffL) != 0) {
                return new Digits("NaN", -32768, sign);
            }
            return new Digits("Infinity", -32768, sign);
        }
        if ((bits & 0x7fffffffffffffffL) == 0) {
            return new Digits("0", 1, sign);
        }
        if (ndigits < 0) {
            ndigits = 0;
        }
        if (ndigits <= 0) {
            mode = 0;
        }
        BigDecimal exact = new BigDecimal(Math.abs(d));
        if (mode == 0) {
            return shortest(new BigDecimal(Double.toString(Math.abs(d))), sign);
        }
        return rounded(exact, mode, ndigits, sign);
    }

    /** m3_ftoa (f, mode, ndigits, decpt, sign, se). */
    public static Digits ftoa(float f, int mode, int ndigits) {
        int bits = Float.floatToRawIntBits(f);
        boolean sign = bits < 0;
        if ((bits & 0x7f800000) == 0x7f800000) {
            if ((bits & 0x007fffff) != 0) {
                return new Digits("NaN", -32768, sign);
            }
            return new Digits("Infinity", -32768, sign);
        }
        if ((bits & 0x7fffffff) == 0) {
            return new Digits("0", 1, sign);
        }
        if (ndigits < 0) {
            ndigits = 0;
        }
        if (ndigits <= 0) {
            mode = 0;
        }
        BigDecimal exact = new BigDecimal((double) Math.abs(f));
        if (mode == 0) {
            return shortest(new BigDecimal(Float.toString(Math.abs(f))), sign);
        }
        return rounded(exact, mode, ndigits, sign);
    }

    /**
     * Mode 0: the shortest digit string that converts back to the same
     * value. Port: computed by Double.toString and Float.toString, which
     * give the shortest digits; the compiler does not use mode 0.
     */
    private static Digits shortest(BigDecimal v, boolean sign) {
        return fromDecimal(v.stripTrailingZeros(), sign);
    }

    /**
     * Mode 2: max(1, ndigits) significant digits; mode 3: ndigits digits
     * after the decimal point. Trailing zeros are suppressed. In mode 3 a
     * value that rounds to zero gives no digits and decpt = -ndigits.
     */
    private static Digits rounded(BigDecimal exact, int mode, int ndigits, boolean sign) {
        BigDecimal r;
        switch (mode) {
            case 2 -> r = exact.round(new MathContext(Math.max(1, ndigits), RoundingMode.HALF_EVEN));
            case 3 -> {
                r = exact.setScale(ndigits, RoundingMode.HALF_EVEN);
                if (r.signum() == 0) {
                    return new Digits("", -ndigits, sign);
                }
            }
            default -> throw new IllegalArgumentException("dtoa mode " + mode);
        }
        return fromDecimal(r.stripTrailingZeros(), sign);
    }

    /** The digits and decimal point position of a positive v without trailing zeros. */
    private static Digits fromDecimal(BigDecimal v, boolean sign) {
        String digits = v.unscaledValue().toString();
        int decpt = digits.length() - v.scale();
        return new Digits(digits, decpt, sign);
    }

    /** gdtoa's quiet NaN, d_QNAN0 and d_QNAN1. */
    private static final long QNAN = 0xfff8000000000000L;

    /**
     * m3_strtod (s, se): converts the longest prefix of {@code s} (up to a
     * NUL character or the end of the array) that has the syntax of a
     * floating-point number.
     */
    @SuppressWarnings("fallthrough")
    public static Parsed strtod(char[] s00) {
        // Port: s00[i] for i >= s00.length reads as the terminating NUL.
        int s = 0;
        int start = 0;
        boolean sign = false;
        int nz0 = 0;
        int nz = 0;
        int nd = 0;
        int nf = 0;
        boolean decpt = false;
        int c;
        loop:
        for (;; s++) {
            switch (ch(s00, s)) {
                case '-':
                    sign = true;
                    // fall through
                case '+':
                    if (ch(s00, ++s) != 0) {
                        break loop;
                    }
                    // fall through
                case 0:
                    // NoNumber
                    return new Parsed(0.0, start);
                case '\t':
                case '\n':
                case 0x0b:
                case '\f':
                case '\r':
                case ' ':
                    continue;
                default:
                    break loop;
            }
        }
        if (ch(s00, s) == '0') {
            if (ch(s00, s + 1) == 'x' || ch(s00, s + 1) == 'X') {
                // Port, omitted: gethex_D2A (hexadecimal floating-point
                // numbers); the compiler never passes them.
                throw new UnsupportedOperationException("hexadecimal floating-point number");
            }
            nz0 = 1;
            while (ch(s00, ++s) == '0') {
                // skip leading zeros
            }
            if (ch(s00, s) == 0) {
                return new Parsed(sign ? -0.0 : 0.0, s);
            }
        }
        // Port: the digits are collected in 'digs' (with the zeros between
        // significant fraction digits) instead of gdtoa's y, z and s0;
        // 'nf' counts the digits after the decimal point that are in 'digs'.
        StringBuilder digs = new StringBuilder();
        for (; (c = ch(s00, s)) >= '0' && c <= '9'; nd++, s++) {
            digs.append((char) c);
        }
        if (c == '.') {
            decpt = true;
            c = ch(s00, ++s);
            boolean haveDig = false;
            if (nd == 0) {
                for (; c == '0'; c = ch(s00, ++s)) {
                    nz++;
                }
                if (c > '0' && c <= '9') {
                    nf += nz;
                    nz = 0;
                    haveDig = true;
                }
            } else {
                haveDig = true;
            }
            if (haveDig) {
                for (; c >= '0' && c <= '9'; c = ch(s00, ++s)) {
                    nz++;
                    if (c != '0') {
                        for (int i = 1; i < nz; i++) {
                            digs.append('0');
                            nd++;
                        }
                        digs.append((char) c);
                        nd++;
                        nf += nz;
                        nz = 0;
                    }
                }
            }
        }
        // dig_done:
        int e = 0;
        int s00e = start;
        if (c == 'e' || c == 'E') {
            if (nd == 0 && nz == 0 && nz0 == 0) {
                return new Parsed(sign ? -0.0 : 0.0, start);
            }
            s00e = s;
            boolean esign = false;
            switch (c = ch(s00, ++s)) {
                case '-':
                    esign = true;
                    // fall through
                case '+':
                    c = ch(s00, ++s);
                    break;
                default:
                    break;
            }
            if (c >= '0' && c <= '9') {
                while (c == '0') {
                    c = ch(s00, ++s);
                }
                if (c > '0' && c <= '9') {
                    long l = c - '0';
                    int s1 = s;
                    while ((c = ch(s00, ++s)) >= '0' && c <= '9') {
                        l = (int) (10 * l + c - '0');
                    }
                    if (s - s1 > 8 || l > 19999) {
                        // Avoid confusion from exponents so large that e
                        // might overflow.
                        e = 19999;
                    } else {
                        e = (int) l;
                    }
                    if (esign) {
                        e = -e;
                    }
                } else {
                    e = 0;
                }
            } else {
                s = s00e;
            }
        }
        if (nd == 0) {
            if (nz == 0 && nz0 == 0) {
                // INFNAN_CHECK: check for NaN and Infinity
                if (!decpt) {
                    switch (c) {
                        case 'i', 'I' -> {
                            if (match(s00, s, "nf")) {
                                s += 3;
                                if (match(s00, s - 1, "inity")) {
                                    s += 5;
                                }
                                return new Parsed(sign ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY, s);
                            }
                        }
                        case 'n', 'N' -> {
                            if (match(s00, s, "an")) {
                                s += 3;
                                long bits = QNAN | (sign ? 0x8000000000000000L : 0L);
                                return new Parsed(Double.longBitsToDouble(bits), s);
                            }
                        }
                        default -> {
                        }
                    }
                }
                return new Parsed(sign ? -0.0 : 0.0, start);
            }
            return new Parsed(sign ? -0.0 : 0.0, s);
        }
        // Port: the correctly rounded value of digs * 10^(e - nf).
        double v = new BigDecimal(new BigInteger(digs.toString()), nf - e).doubleValue();
        return new Parsed(sign ? -v : v, s);
    }

    /** The character at index i of s, or NUL beyond the end. */
    private static int ch(char[] s, int i) {
        return i < s.length ? s[i] : 0;
    }

    /**
     * match_D2A: whether the characters after s[at] are t, ignoring the
     * case of letters in s.
     */
    private static boolean match(char[] s, int at, String t) {
        for (int i = 0; i < t.length(); i++) {
            int c = ch(s, at + 1 + i);
            if (c >= 'A' && c <= 'Z') {
                c += 'a' - 'A';
            }
            if (c != t.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
