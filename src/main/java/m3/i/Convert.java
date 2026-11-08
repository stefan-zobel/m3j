// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import m3j.rt.LongRef;
import m3j.rt.M3Exception;

/**
 * The interface Convert of m3core, with the results of CM3 on AMD64_NT.
 * The procedures follow CM3's Convert module; the conversions of its C
 * part (m3_ftoa, m3_dtoa and m3_strtod of gdtoa in dtoa.c) are computed
 * with exact arithmetic, with the choices of gdtoa where they differ from
 * correct rounding. EXTENDED is LONGREAL on the target; LONGINT is
 * INTEGER. The runtime errors and the exception Failed carry the places
 * in CM3's module, as cm3 reports them.
 */
public final class Convert {
    private Convert() {}

    public static final boolean M3$PROVIDED = true;

    // The values of Style.
    static final int Flo = 0;
    static final int AltFlo = 1;
    static final int Sci = 2;
    static final int AltSci = 3;
    static final int Mix = 4;

    /** The source file of the CM3 implementation, for the place of the errors. */
    private static final String FILE = "../src/convert/Convert.m3";

    /** EXCEPTION Failed */
    public static final class Failed extends M3Exception {
        private static final long serialVersionUID = 1L;

        @Override
        public String m3Name() {
            return "Convert.Failed";
        }
    }

    private static final char[] DIGITS = "0123456789abcdef".toCharArray();

    private static RuntimeException error(int code, int line) {
        return LongFloat.error(code, FILE, line);
    }

    private static Failed failed(int line) {
        Failed f = new Failed();
        f.at(FILE, line);
        return f;
    }

    /** buf[i] := c, with the subscript check at the given line. */
    private static void put(byte[] buf, int off, int n, long i, char c, int line) {
        if (i < 0 || i >= n) {
            throw error(RuntimeError.SubscriptOutOfRange, line);
        }
        buf[off + (int) i] = (byte) c;
    }

    // ------------------------------------------- binary to ASCII, integers

    public static long FromInt(byte[] buf, int off, int n, long value, int base, boolean prefix) {
        return fromSigned(buf, off, n, value, base, prefix, 86);
    }

    public static long FromLongInt(byte[] buf, int off, int n, long value, int base, boolean prefix) {
        return fromSigned(buf, off, n, value, base, prefix, 163);
    }

    public static long FromUnsigned(byte[] buf, int off, int n, long value, int base, boolean prefix) {
        return fromUnsigned(buf, off, n, value, base, prefix, 206);
    }

    public static long FromLongUnsigned(byte[] buf, int off, int n, long value, int base,
                                        boolean prefix) {
        return fromUnsigned(buf, off, n, value, base, prefix, 249);
    }

    /** FromInt and FromLongInt; line is the place of RAISE Failed. */
    private static long fromSigned(byte[] buf, int off, int n, long value, int base, boolean prefix,
                                   int line) {
        int nDigits = 0;
        boolean minus = false;
        boolean bump = false;
        char[] result = new char[65];
        if (value == 0) {
            result[0] = '0';
            nDigits = 1;
        } else {
            // get rid of negative numbers
            if (value < 0) {
                if (value == Long.MIN_VALUE) {
                    // 2's complement makes FIRST (INTEGER) a special case
                    bump = true;
                    value++;
                }
                minus = true;
                value = -value;
            }
            // convert the bulk of the digits
            while (value > 0) {
                result[nDigits] = DIGITS[(int) (value % base)];
                value = value / base;
                nDigits++;
            }
            // fixup FIRST (INTEGER)
            if (bump) {
                result[nDigits] = '0';
                int j = 0;
                for (;;) {
                    char c = result[j];
                    int i = c <= '9' ? c - '0' : c - 'a' + 10;
                    i++;
                    if (i < base) {
                        result[j] = DIGITS[i];
                        break;
                    }
                    result[j] = '0';
                    j++;
                }
                nDigits = java.lang.Math.max(nDigits, j + 1);
            }
        }
        return store(buf, off, n, minus, prefix, base, result, nDigits, line);
    }

    /** FromUnsigned and FromLongUnsigned; line is the place of RAISE Failed. */
    private static long fromUnsigned(byte[] buf, int off, int n, long value, int base,
                                     boolean prefix, int line) {
        int nDigits = 0;
        char[] result = new char[65];
        if (value == 0) {
            result[0] = '0';
            nDigits = 1;
        } else {
            while (value != 0) {
                result[nDigits] = DIGITS[(int) Long.remainderUnsigned(value, base)];
                value = Long.divideUnsigned(value, base);
                nDigits++;
            }
        }
        return store(buf, off, n, false, prefix, base, result, nDigits, line);
    }

    /** Builds the result buffer of the From procedures of integers. */
    private static long store(byte[] buf, int off, int n, boolean minus, boolean prefix, int base,
                              char[] result, int nDigits, int line) {
        // make sure we've got room for the result
        int j = nDigits;
        if (minus) {
            j++;
        }
        if (prefix) {
            j += base > 9 ? 3 : 2;
        }
        if (j > n) {
            throw failed(line);
        }
        // build the result buffer
        j = 0;
        if (minus) {
            buf[off] = '-';
            j = 1;
        }
        if (prefix) {
            if (base > 9) {
                buf[off + j++] = '1';
            }
            buf[off + j++] = (byte) DIGITS[base % 10];
            buf[off + j++] = '_';
        }
        for (int k = nDigits - 1; k >= 0; k--) {
            buf[off + j++] = (byte) result[k];
        }
        return j;
    }

    // --------------------------------------------- binary to ASCII, reals

    public static long FromFloat(byte[] buf, int off, int n, float v, long p, int style) {
        int[] mp = precisionMode(style, p, 441);
        return format(buf, off, n, p, style, ftoa(v, mp[0], mp[1]), 'E');
    }

    public static long FromLongFloat(byte[] buf, int off, int n, double v, long p, int style) {
        int[] mp = precisionMode(style, p, 455);
        return format(buf, off, n, p, style, dtoa(v, mp[0], mp[1]), 'D');
    }

    public static long FromExtended(byte[] buf, int off, int n, double v, long p, int style) {
        int[] mp = precisionMode(style, p, 467);
        return format(buf, off, n, p, style, dtoa(v, mp[0], mp[1]), 'X');
    }

    /**
     * GetPrecisionMode: {mode, precision} of dtoa. p is passed as an int
     * of C, with the range check at the given line of the call.
     */
    private static int[] precisionMode(int style, long p, int line) {
        if (p < Integer.MIN_VALUE || p > Integer.MAX_VALUE) {
            throw error(RuntimeError.ValueOutOfRange, line);
        }
        int q = (int) p;
        if (style == Flo || style == AltFlo) {
            return new int[] {3, q};
        }
        if (q == Integer.MAX_VALUE) {
            throw error(RuntimeError.ValueOutOfRange, 273);
        }
        return new int[] {2, q + 1};
    }

    /**
     * The result of m3_ftoa and m3_dtoa: the digits ("Infinity" or "NaN"
     * for those values), the position of the decimal point (-32768 for
     * Infinity and NaN), and whether the sign bit is set.
     */
    private record Digits(String digits, int decpt, boolean sign) {}

    /** Format: lays out the digits of a conversion in buf. */
    private static long format(byte[] buf, int off, int n, long p, int style, Digits dg,
                               char exponentChar) {
        long len = 0;
        String d = dg.digits();
        int digits = d.length();
        int decpt = dg.decpt();
        long af = 0, bf = 0, cf = 0, df = 0, ef = 0;
        boolean pf = true;
        long as = 0, bs = 0, cs = 0, ds = 0, es = 0;
        boolean ps = true;
        long a, b, c, dd, e, x;
        boolean pp;
        // The string will consist of: the first A digits of the conversion,
        // B '0's, '.' if P is TRUE, C '0's, the next D digits of the
        // conversion, E '0's, and if X # LAST (INTEGER), the exponent
        // character and the exponent.
        long xf = Long.MAX_VALUE;
        long xs = Long.MAX_VALUE;

        if (decpt == -32768) {
            // Infinity or NaN
            if (dg.sign()) {
                put(buf, off, n, 0, '-', 312);
                len = 1;
            }
            if (len + digits > n) {
                throw error(RuntimeError.ValueOutOfRange, 313);
            }
            for (int i = 0; i < digits; i++) {
                buf[off + (int) len + i] = (byte) d.charAt(i);
            }
            return len + digits;
        }

        if (style == Flo || style == AltFlo || style == Mix) {
            if (decpt <= 0) {
                af = 0;
                bf = 1;
                cf = java.lang.Math.min(-decpt, p);
                df = java.lang.Math.min(digits, p - cf);
                ef = java.lang.Math.max(0, p - (cf + df));
            } else if (decpt <= digits) {
                af = decpt;
                bf = 0;
                cf = 0;
                df = java.lang.Math.min(digits - decpt, p);
                ef = java.lang.Math.max(0, p - df);
            } else {
                af = digits;
                bf = decpt - digits;
                cf = p;
                df = 0;
                ef = 0;
            }
            if (style == AltFlo || style == Mix) {
                ef = 0;
                while (df > 0 && d.charAt((int) (af + df - 1)) == '0') {
                    df--;
                }
                if (df == 0) {
                    cf = 0;
                }
            }
        }

        if (style == Sci || style == AltSci || style == Mix) {
            as = java.lang.Math.min(1, digits);
            bs = 0;
            cs = 0;
            ds = java.lang.Math.min(digits - 1, p);
            es = java.lang.Math.max(0, p - digits + 1);
            xs = decpt - 1;
            if (style == AltSci || style == Mix) {
                es = 0;
                while (ds > 0 && d.charAt((int) (as + ds - 1)) == '0') {
                    ds--;
                }
                if (ds == 0) {
                    cs = 0;
                }
            }
        }

        if (style == Mix) {
            long floLength = af + bf + 1 + cf + df + ef;
            long sciLength = as + bs + 1 + cs + ds + es;
            if (xs != Long.MAX_VALUE) {
                // As in CM3, the length of the exponent is taken from X,
                // which is still 0 here, instead of from Xs.
                sciLength += xs < 0 ? 3 : 2;
            }
            if (floLength > sciLength) {
                style = AltSci;
            } else {
                style = AltFlo;
                if (df == 0 && cf == 0) {
                    pf = false;
                }
            }
        }

        if (style == Flo || style == AltFlo) {
            a = af;
            b = bf;
            c = cf;
            dd = df;
            e = ef;
            x = xf;
            pp = pf;
        } else {
            a = as;
            b = bs;
            c = cs;
            dd = ds;
            e = es;
            x = xs;
            pp = ps;
        }

        // Set the sign
        if (dg.sign()) {
            put(buf, off, n, len++, '-', 406);
        }
        int consumed = 0;
        for (long i = 1; i <= a; i++) {
            put(buf, off, n, len++, d.charAt(consumed++), 410);
        }
        for (long i = 1; i <= b; i++) {
            put(buf, off, n, len++, '0', 414);
        }
        if (pp) {
            put(buf, off, n, len++, '.', 415);
        }
        for (long i = 1; i <= c; i++) {
            put(buf, off, n, len++, '0', 416);
        }
        for (long i = 1; i <= dd; i++) {
            put(buf, off, n, len++, d.charAt(consumed++), 418);
        }
        for (long i = 1; i <= e; i++) {
            put(buf, off, n, len++, '0', 422);
        }
        if (x != Long.MAX_VALUE) {
            put(buf, off, n, len++, exponentChar, 424);
            len += fromSigned(buf, off + (int) len, n - (int) len, x, 10, false, 86);
        }
        return len;
    }

    // ------------------------------------------------- m3_ftoa and m3_dtoa

    /** m3_ftoa (f, mode, ndigits, ...). */
    private static Digits ftoa(float f, int mode, int ndigits) {
        int bits = Float.floatToRawIntBits(f);
        boolean sign = bits < 0;
        if ((bits & 0x7f800000) == 0x7f800000) {
            return new Digits((bits & 0x7fffff) != 0 ? "NaN" : "Infinity", -32768, sign);
        }
        if ((bits & 0x7fffffff) == 0) {
            return new Digits("0", 1, sign);
        }
        long mant = bits & 0x7fffff;
        int ex = (bits >>> 23) & 0xff;
        if (ex != 0) {
            mant |= 0x800000;
        } else {
            ex = 1;
        }
        return gdtoa(mant, ex - (0x7f + 23), 1 - 127 - 24 + 1, mode, ndigits, sign);
    }

    /** m3_dtoa (d, mode, ndigits, ...). */
    private static Digits dtoa(double v, int mode, int ndigits) {
        long bits = Double.doubleToRawLongBits(v);
        boolean sign = bits < 0;
        if ((bits & 0x7ff0000000000000L) == 0x7ff0000000000000L) {
            return new Digits((bits & 0x000fffffffffffffL) != 0 ? "NaN" : "Infinity", -32768, sign);
        }
        if ((bits & 0x7fffffffffffffffL) == 0) {
            return new Digits("0", 1, sign);
        }
        long mant = bits & 0x000fffffffffffffL;
        int ex = (int) (bits >>> 52) & 0x7ff;
        if (ex != 0) {
            mant |= 1L << 52;
        } else {
            ex = 1;
        }
        return gdtoa(mant, ex - (0x3ff + 52), 1 - 1023 - 53 + 1, mode, ndigits, sign);
    }

    /**
     * gdtoa_D2A for the positive value mant * 2^be of a format whose
     * smallest exponent is emin. Mode 0 (taken when ndigits &lt;= 0) gives
     * the shortest digits that read back as the value; mode 2 ndigits
     * significant digits; mode 3 ndigits digits after the decimal point,
     * none when the value rounds to 0. Modes 2 and 3 round the exact value
     * half-even; trailing zeros are dropped.
     */
    private static Digits gdtoa(long mant, int be, int emin, int mode, int ndigits, boolean sign) {
        if (ndigits < 0) {
            ndigits = 0;
        }
        if (ndigits <= 0) {
            mode = 0;
        }
        BigDecimal v = pow2(be).multiply(BigDecimal.valueOf(mant));
        BigDecimal r;
        if (mode == 0) {
            r = shortest(v, mant, be, emin);
        } else if (mode == 2) {
            r = v.round(new MathContext(ndigits, RoundingMode.HALF_EVEN));
        } else {
            r = v.setScale(ndigits, RoundingMode.HALF_EVEN);
            if (r.signum() == 0) {
                return new Digits("", -ndigits, sign);
            }
        }
        r = r.stripTrailingZeros();
        String digits = r.unscaledValue().toString();
        return new Digits(digits, digits.length() - r.scale(), sign);
    }

    /** 2^e, exactly. */
    private static BigDecimal pow2(int e) {
        if (e >= 0) {
            return new BigDecimal(BigInteger.ONE.shiftLeft(e));
        }
        return new BigDecimal(BigInteger.valueOf(5).pow(-e), -e);
    }

    private static final BigDecimal TEN_15 = BigDecimal.TEN.pow(15);

    /**
     * Mode 0 of gdtoa_D2A. An integer below 10^15 gives all its digits
     * (gdtoa converts those with floating-point arithmetic). Otherwise the
     * result is the first number of n = 1, 2, ... significant digits that
     * lies within half a unit of the last place of the value (a boundary
     * included if the significand is even); of two such numbers, the
     * nearer. gdtoa takes the smaller distance below a power of two only
     * if its exponent field is above 2.
     */
    private static BigDecimal shortest(BigDecimal v, long mant, int be, int emin) {
        int tz = Long.numberOfTrailingZeros(mant);
        if (be + tz >= 0 && v.compareTo(TEN_15) < 0) {
            return v;
        }
        BigDecimal hi = pow2(be - 1);
        boolean specCase = Long.bitCount(mant) == 1 && be > emin + 1;
        BigDecimal lo = specCase ? pow2(be - 2) : hi;
        boolean even = (mant & 1) == 0;
        for (int n = 1;; n++) {
            BigDecimal fl = v.round(new MathContext(n, RoundingMode.FLOOR));
            BigDecimal ce = v.round(new MathContext(n, RoundingMode.CEILING));
            int jl = v.subtract(fl).compareTo(lo);
            int jh = ce.subtract(v).compareTo(hi);
            boolean flOk = jl < 0 || (jl == 0 && even);
            boolean ceOk = jh < 0 || (jh == 0 && even);
            if (jh == 0 && even) {
                // The upper end of the interval is hit: gdtoa keeps the
                // lower number if it is within the interval, unless its
                // last digit is 9.
                boolean nine = fl.unscaledValue().mod(BigInteger.TEN).intValue() == 9;
                return flOk && !nine ? fl : ce;
            }
            if (flOk && ceOk) {
                return v.round(new MathContext(n, RoundingMode.HALF_EVEN));
            }
            if (flOk) {
                return fl;
            }
            if (ceOk) {
                return ce;
            }
        }
    }

    // ------------------------------------------- ASCII to binary, integers

    public static long ToInt(byte[] buf, int off, int n, LongRef used, int base) {
        return toSigned(buf, off, n, used, base);
    }

    public static long ToLongInt(byte[] buf, int off, int n, LongRef used, int base) {
        return toSigned(buf, off, n, used, base);
    }

    public static long ToUnsigned(byte[] buf, int off, int n, LongRef used, int base) {
        return toUnsigned(buf, off, n, used, base);
    }

    public static long ToLongUnsigned(byte[] buf, int off, int n, LongRef used, int base) {
        return toUnsigned(buf, off, n, used, base);
    }

    private static long toSigned(byte[] buf, int off, int n, LongRef used, int base) {
        long value;
        int skipped = 0;
        if (n == 0) {
            used.set(0);
            return 0;
        }
        if (buf[off] == '-') {
            skipped = 1;
            value = internalToInt(buf, off + 1, n - 1, used, base, 1L << 63);
            value = 1 + ~value;
        } else {
            if (buf[off] == '+') {
                skipped = 1;
            }
            value = internalToInt(buf, off + skipped, n - skipped, used, base, Long.MAX_VALUE);
        }
        if (used.get() != 0) {
            used.set(used.get() + skipped);
        }
        return value;
    }

    private static long toUnsigned(byte[] buf, int off, int n, LongRef used, int base) {
        int skipped = 0;
        if (n == 0) {
            used.set(0);
            return 0;
        }
        if (buf[off] == '+') {
            skipped = 1;
        }
        long value = internalToInt(buf, off + skipped, n - skipped, used, base, -1L);
        if (used.get() != 0) {
            used.set(used.get() + skipped);
        }
        return value;
    }

    /** InternalToInt: limit is the largest value, unsigned. */
    private static long internalToInt(byte[] buf, int off, int z, LongRef used, int defaultBase,
                                      long limit) {
        long value = 0;
        int n = 0;
        int ibase;
        boolean based;
        int i, j;
        int c;
        if (z == 0) {
            used.set(0);
            return 0;
        }
        c = buf[off] & 0xFF;

        // peel off any leading zeros
        while (c == '0') {
            n++;
            if (n >= z) {
                used.set(n);
                return 0;
            }
            c = buf[off + n] & 0xFF;
        }

        // check for an explicit base
        if (c == '1' && n + 3 < z && buf[off + n + 2] == '_'
                && '0' <= buf[off + n + 1] && buf[off + n + 1] <= '6') {
            // an explicit base between 10 and 16
            based = true;
            ibase = 10 + buf[off + n + 1] - '0';
            n += 3;
            c = buf[off + n] & 0xFF;
        } else if ('2' <= c && c <= '9' && n + 2 < z && buf[off + n + 1] == '_') {
            // an explicit base between 2 and 9
            based = true;
            ibase = c - '0';
            n += 2;
            c = buf[off + n] & 0xFF;
        } else {
            // no explicit base
            based = false;
            ibase = defaultBase;
        }

        // scan the digits
        j = n; // remember the first digit
        for (;;) {
            if ('0' <= c && c <= '9') {
                i = c - '0';
            } else if ('A' <= c && c <= 'F') {
                i = c - 'A' + 10;
            } else if ('a' <= c && c <= 'f') {
                i = c - 'a' + 10;
            } else {
                break;
            }
            if (i >= ibase) {
                break;
            }
            if (Long.compareUnsigned(Long.divideUnsigned(limit, ibase), value) < 0) {
                break;
            }
            long val2 = value * ibase; // no overflow
            if (Long.compareUnsigned(limit - i, value) < 0) {
                break;
            }
            value = val2 + i; // no overflow
            n++;
            if (n >= z) {
                break;
            }
            c = buf[off + n] & 0xFF;
        }

        if (j == n && based) { // no digits were consumed
            // back up and "rescan" the explicit base
            if (ibase < 10) { // single digit base was specified
                n--; // return the "_"
                if (ibase < defaultBase) { // digit is legal
                    value = ibase;
                } else { // an illegal digit was specified
                    n--; // return the digit
                }
            } else { // 2-digit base was specified
                // first digit was '1' and is always legal
                n--; // return the "_"
                if (ibase - 10 < defaultBase) { // the second digit was legal
                    value = defaultBase + ibase - 10;
                } else { // the second digit was illegal
                    n--; // return the second digit
                    value = 1;
                }
            }
        }

        used.set(n);
        return value;
    }

    // ---------------------------------------------- ASCII to binary, reals

    public static float ToFloat(byte[] buf, int off, int n, LongRef used) {
        return (float) toBinary(buf, off, n, 'E', 'E', used);
    }

    public static double ToLongFloat(byte[] buf, int off, int n, LongRef used) {
        return toBinary(buf, off, n, 'D', 'd', used);
    }

    public static double ToExtended(byte[] buf, int off, int n, LongRef used) {
        return toBinary(buf, off, n, 'X', 'x', used);
    }

    /**
     * ToBinary and ToBinaryExt: the characters with the exponent
     * characters exp1 and exp2 changed to 'e', converted by m3_strtod.
     * The end pointer of strtod is never NIL, so Failed is never raised.
     */
    private static double toBinary(byte[] source, int off, int nchars, char exp1, char exp2,
                                   LongRef used) {
        byte[] tmp = new byte[nchars + 1];
        for (int i = 0; i < nchars; i++) {
            byte ch = source[off + i];
            if (ch == exp1 || ch == exp2) {
                ch = 'e';
            }
            tmp[i] = ch;
        }
        tmp[nchars] = 0;
        int[] end = new int[1];
        double value = strtod(tmp, end);
        used.set(end[0]);
        return value;
    }

    // ------------------------------------------------------------ m3_strtod

    private static final int EMIN = 1 - 1023 - 53 + 1;
    private static final int EMAX = 2046 - 1023 - 53 + 1;
    private static final int NBITS = 53;

    /** gdtoa's quiet NaN (d_QNAN0, d_QNAN1): its sign bit is set. */
    private static final long QNAN = 0xfff8000000000000L;

    /** The character at index i of the NUL-terminated s. */
    private static int ch(byte[] s, int i) {
        return i < s.length ? s[i] & 0xFF : 0;
    }

    /**
     * m3_strtod (strtodg_D2A with round-to-nearest, INFNAN_CHECK and
     * No_Hex_NaN): converts the longest prefix of the NUL-terminated s
     * that has the syntax of a number; end[0] is the index after it, or 0.
     */
    @SuppressWarnings("fallthrough")
    static double strtod(byte[] s00, int[] end) {
        int s;
        boolean sign = false;
        int nz0 = 0;
        int nz = 0;
        int nd = 0;
        int nf = 0;
        boolean decpt = false;
        int c;
        loop:
        for (s = 0;; s++) {
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
                    end[0] = 0;
                    return 0.0;
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
                return gethex(s00, s, sign, end);
            }
            nz0 = 1;
            while (ch(s00, ++s) == '0') {
                // skip leading zeros
            }
            if (ch(s00, s) == 0) {
                end[0] = s;
                return sign ? -0.0 : 0.0;
            }
        }
        // The digits (with the zeros between the significant digits of the
        // fraction) and the number nf of them after the decimal point.
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
        if (c == 'e' || c == 'E') {
            if (nd == 0 && nz == 0 && nz0 == 0) {
                end[0] = 0;
                return sign ? -0.0 : 0.0;
            }
            int s00e = s;
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
                    int l = c - '0';
                    int s1 = s;
                    while ((c = ch(s00, ++s)) >= '0' && c <= '9') {
                        l = 10 * l + c - '0';
                    }
                    if (s - s1 > 8 || l > 19999) {
                        // Avoid confusion from exponents so large that e
                        // might overflow.
                        e = 19999;
                    } else {
                        e = l;
                    }
                    if (esign) {
                        e = -e;
                    }
                }
            } else {
                s = s00e;
            }
        }
        if (nd == 0) {
            if (nz == 0 && nz0 == 0) {
                // Check for NaN and Infinity
                if (!decpt) {
                    if (c == 'i' || c == 'I') {
                        if (match(s00, s, "nf")) {
                            s += 3;
                            if (match(s00, s - 1, "inity")) {
                                s += 5;
                            }
                            end[0] = s;
                            return sign ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                        }
                    } else if (c == 'n' || c == 'N') {
                        if (match(s00, s, "an")) {
                            end[0] = s + 3;
                            return Double.longBitsToDouble(QNAN);
                        }
                    }
                }
                end[0] = 0;
                return sign ? -0.0 : 0.0;
            }
            end[0] = s;
            return sign ? -0.0 : 0.0;
        }
        end[0] = s;
        // The value of the digits times 10^(e - nf), correctly rounded.
        double v = new BigDecimal(new BigInteger(digs.toString()), nf - e).doubleValue();
        return sign ? -v : v;
    }

    /**
     * match_D2A: whether the characters after s[at] are t, ignoring the
     * case of the letters of s.
     */
    private static boolean match(byte[] s, int at, String t) {
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

    /** The hexdig table of gdtoa: 0x10 + the value of a hexadecimal digit, else 0. */
    private static int hexdig(int c) {
        if (c >= '0' && c <= '9') {
            return 0x10 + c - '0';
        } else if (c >= 'a' && c <= 'f') {
            return 0x1a + c - 'a';
        } else if (c >= 'A' && c <= 'F') {
            return 0x1a + c - 'A';
        }
        return 0;
    }

    /** any_on_D2A: whether one of the lowest k bits of b is set. */
    private static boolean anyOn(BigInteger b, int k) {
        return k > 0 && b.signum() != 0 && b.getLowestSetBit() < k;
    }

    /**
     * gethex_D2A for the hexadecimal number at s0 ("0x..."), rounded to
     * nearest as gdtoa does it (which ignores the bit below the rounding
     * bit when it looks for a tie).
     */
    private static double gethex(byte[] str, int start, boolean sign, int[] end) {
        int havedig = 0;
        int s0 = start + 2;
        while (ch(str, s0 + havedig) == '0') {
            havedig++;
        }
        s0 += havedig;
        int s = s0;
        int decpt = -1;
        boolean zret = false;
        int e = 0;
        pcheck: {
            if (hexdig(ch(str, s)) == 0) {
                zret = true;
                if (ch(str, s) != '.') {
                    break pcheck;
                }
                decpt = ++s;
                if (hexdig(ch(str, s)) == 0) {
                    break pcheck;
                }
                while (ch(str, s) == '0') {
                    s++;
                }
                if (hexdig(ch(str, s)) != 0) {
                    zret = false;
                }
                havedig = 1;
                s0 = s;
            }
            while (hexdig(ch(str, s)) != 0) {
                s++;
            }
            if (ch(str, s) == '.' && decpt < 0) {
                decpt = ++s;
                while (hexdig(ch(str, s)) != 0) {
                    s++;
                }
            }
            if (decpt >= 0) {
                e = -((s - decpt) << 2);
            }
        }
        // pcheck:
        int s1 = s;
        if (ch(str, s) == 'p' || ch(str, s) == 'P') {
            boolean esign = false;
            int c = ch(str, ++s);
            if (c == '-') {
                esign = true;
                s++;
            } else if (c == '+') {
                s++;
            }
            int n = hexdig(ch(str, s));
            if (n == 0 || n > 0x19) {
                s = s1;
            } else {
                int e1 = n - 0x10;
                while ((n = hexdig(ch(str, ++s))) != 0 && n <= 0x19) {
                    e1 = 10 * e1 + n - 0x10;
                }
                if (esign) {
                    e1 = -e1;
                }
                e += e1;
            }
        }
        end[0] = s;
        if (zret) {
            if (havedig == 0) {
                end[0] = s0 - 1;
            }
            return sign ? -0.0 : 0.0;
        }
        BigInteger b = BigInteger.ZERO;
        for (int i = s0; i < s1; i++) {
            int c = ch(str, i);
            if (c != '.') {
                b = b.shiftLeft(4).or(BigInteger.valueOf(hexdig(c) & 0x0f));
            }
        }
        int n = b.bitLength();
        int nbits = NBITS;
        int lostbits = 0;
        if (n > nbits) {
            n -= nbits;
            if (anyOn(b, n)) {
                lostbits = 1;
                int k = n - 1;
                if (b.testBit(k)) {
                    lostbits = 2;
                    if (k > 1 && anyOn(b, k - 1)) {
                        lostbits = 3;
                    }
                }
            }
            b = b.shiftRight(n);
            e += n;
        } else if (n < nbits) {
            n = nbits - n;
            b = b.shiftLeft(n);
            e -= n;
        }
        if (e > EMAX) {
            return sign ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        boolean denormal = false;
        if (e < EMIN) {
            denormal = true;
            n = EMIN - e;
            if (n >= nbits) {
                if (n == nbits && (n < 2 || anyOn(b, n - 1))) {
                    // one bit
                    double v = Double.MIN_VALUE;
                    return sign ? -v : v;
                }
                return sign ? -0.0 : 0.0;
            }
            int k = n - 1;
            if (lostbits != 0) {
                lostbits = 1;
            } else if (k > 0) {
                lostbits = anyOn(b, k) ? 1 : 0;
            }
            if (b.testBit(k)) {
                lostbits |= 2;
            }
            nbits -= n;
            b = b.shiftRight(n);
            e = EMIN;
        }
        if (lostbits != 0) {
            boolean up = (lostbits & 2) != 0 && ((lostbits & 1) != 0 || b.testBit(0));
            if (up) {
                b = b.add(BigInteger.ONE);
                if (!denormal && b.bitLength() > nbits) {
                    b = b.shiftRight(1);
                    if (++e > EMAX) {
                        return sign ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                    }
                }
            }
        }
        double v = java.lang.Math.scalb(b.doubleValue(), e);
        return sign ? -v : v;
    }
}
