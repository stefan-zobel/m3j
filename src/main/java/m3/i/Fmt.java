// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.math.BigInteger;
import m3j.rt.Rt;

/** The interface Fmt of libm3. */
public final class Fmt {
    private Fmt() {}

    public static final boolean M3$PROVIDED = true;

    // Style = {Sci, Fix, Auto}; Align = {Left, Right}
    static final int Left = 0, Right = 1;

    public static String Bool(boolean b) {
        return b ? "TRUE" : "FALSE";
    }

    public static String Char(char c) {
        return String.valueOf(c);
    }

    public static String Int(long n, int base) {
        return Long.toString(n, base);
    }

    public static String Unsigned(long n, int base) {
        return Long.toUnsignedString(n, base);
    }

    public static String LongInt(long n, int base) {
        return Long.toString(n, base);
    }

    public static String LongUnsigned(long n, int base) {
        return Long.toUnsignedString(n, base);
    }

    public static String Real(float x, int style, long prec, boolean literal) {
        int bits = Float.floatToRawIntBits(x);
        int sign = bits >>> 31;
        int expField = (bits >>> 23) & 0xFF;
        int frac = bits & 0x7FFFFF;
        if (expField == 0xFF) {
            return special(frac != 0, KindSingle, 2, sign, style, prec, literal);
        }
        if (expField == 0 && frac == 0) {
            return zero(KindSingle, 2, sign, style, prec, literal);
        }
        Decimal d = expField == 0
            ? dragon(-125, frac, 24)
            : dragon(expField - 126, frac | 0x800000, 24);
        return number(KindSingle, 2, sign, d, style, prec, literal);
    }

    public static String LongReal(double x, int style, long prec, boolean literal) {
        return longFloat(x, KindDouble, style, prec, literal);
    }

    public static String Extended(double x, int style, long prec, boolean literal) {
        return longFloat(x, KindExtended, style, prec, literal);
    }

    public static String Pad(String text, long length, char padChar, int align) {
        long len = length - text.length();
        if (len <= 0) {
            return text;
        }
        String pad = String.valueOf(padChar).repeat((int) len);
        return align == Right ? pad.concat(text) : text.concat(pad);
    }

    public static String F(String fmt, String t1, String t2, String t3, String t4, String t5) {
        String[] a = {t1, t2, t3, t4, t5};
        int pos = a.length - 1;
        while (pos >= 0 && a[pos] == null) {
            pos--;
        }
        String[] texts = new String[pos + 1];
        System.arraycopy(a, 0, texts, 0, pos + 1);
        return fn(fmt, texts);
    }

    /** FN (fmt, READONLY texts: ARRAY OF TEXT): the open array as storage, offset and length. */
    public static String FN(String fmt, String[] texts, int off, int n) {
        return fn(fmt, java.util.Arrays.copyOfRange(texts, off, off + n));
    }

    /** One format specifier. */
    private record Spec(int start, int length, int padWidth, int padAlign, char padChar) {}

    static String fn(String fmt, String[] texts) {
        java.util.List<Spec> specs = new java.util.ArrayList<>();
        int cursor = 0, len = fmt.length();
        while (true) {
            while (cursor < len && fmt.charAt(cursor) != '%') {
                cursor++;
            }
            if (cursor >= len) {
                break;
            }
            int start = cursor;
            cursor++;
            if (cursor >= len) {
                continue;
            }
            int align = Right;
            char padChar = ' ';
            int width = 0;
            char ch = fmt.charAt(cursor);
            if (ch == '-') {
                align = Left;
                if (++cursor >= len) {
                    continue;
                }
                ch = fmt.charAt(cursor);
            }
            if (ch == '0') {
                padChar = '0';
                if (++cursor >= len) {
                    continue;
                }
                ch = fmt.charAt(cursor);
            }
            boolean incomplete = false;
            while ('0' <= ch && ch <= '9') {
                width = width * 10 + ch - '0';
                if (++cursor >= len) {
                    incomplete = true;
                    break;
                }
                ch = fmt.charAt(cursor);
            }
            if (incomplete || ch != 's') {
                continue;
            }
            cursor++;
            specs.add(new Spec(start, cursor - start, width, align, padChar));
        }
        if (specs.size() != texts.length) {
            throw Rt.error(RuntimeError.UnhandledException);
        }
        if (specs.isEmpty()) {
            return fmt;
        }
        StringBuilder sb = new StringBuilder();
        int fPos = 0;
        for (int i = 0; i < texts.length; i++) {
            Spec s = specs.get(i);
            sb.append(fmt, fPos, s.start());
            fPos = s.start() + s.length();
            String arg = texts[i];
            int pad = s.padWidth() - arg.length();
            if (s.padAlign() == Right) {
                for (; pad > 0; pad--) {
                    sb.append(s.padChar());
                }
            }
            sb.append(arg);
            if (s.padAlign() == Left) {
                for (; pad > 0; pad--) {
                    sb.append(s.padChar());
                }
            }
        }
        sb.append(fmt, fPos, len);
        return sb.toString();
    }

    // Floating-point values ------------------------------------------------
    //
    // The formatting follows CM3's FmtBuf (libm3) step by step, including its
    // rounding of the decimal digits (NearestElseEven, with "errorSign = 0").
    // The decimal digits are those of CM3's RealFloat/LongFloat.ToDecimal on
    // targets without Grisu: the shortest digits produced by the "Dragon"
    // free-format algorithm of Steele and White, as implemented in m3core's
    // DragonT. Its results differ from Java's Float/Double.toString in rare
    // cases (exact half-way boundaries are never accepted, a tie on the last
    // digit rounds down, and a single digit is never widened to two). Like
    // DragonT, the digits of a number just below a power of ten whose upper
    // half-way boundary is that power of ten (such as the LONGREAL nearest to
    // 1.0d23) start with a 0, which FmtBuf then prints as is.

    static final int Sci = 0, Fix = 1, Auto = 2;

    /** The IEEE kinds {Single, Double, Extended}. */
    private static final int KindSingle = 0, KindDouble = 1, KindExtended = 2;

    private static final char[] EXP_CHAR = {'e', 'd', 'x'};

    /** Upper limit for the precision, beyond which no text can be built. */
    private static final int MAX_PREC = Integer.MAX_VALUE - 4096;

    /** A decimal approximation: digits[0].digits[1]...digits[len-1] * 10^exp. */
    private static final class Decimal {
        final int[] digits;
        final int len;
        final int exp;

        Decimal(int[] digits, int len, int exp) {
            this.digits = digits;
            this.len = len;
            this.exp = exp;
        }
    }

    private static String longFloat(double x, int kind, int style, long prec, boolean literal) {
        long bits = Double.doubleToRawLongBits(x);
        int sign = (int) (bits >>> 63);
        int expField = (int) (bits >>> 52) & 0x7FF;
        long frac = bits & 0xFFFFFFFFFFFFFL;
        if (expField == 0x7FF) {
            return special(frac != 0, kind, 3, sign, style, prec, literal);
        }
        if (expField == 0 && frac == 0) {
            return zero(kind, 3, sign, style, prec, literal);
        }
        Decimal d = expField == 0
            ? dragon(-1021, frac, 53)
            : dragon(expField - 1022, frac | (1L << 52), 53);
        return number(kind, 3, sign, d, style, prec, literal);
    }

    private static int precision(long prec) {
        return prec < 0 || prec > MAX_PREC ? MAX_PREC : (int) prec;
    }

    /**
     * The shortest decimal digits of f * 2^(e-p) by the free-format "Dragon"
     * algorithm (Steele and White, "How to Print Floating-Point Numbers
     * Accurately", PLDI 1990), as in m3core's DragonT.F with cutoff mode
     * "normal".
     */
    private static Decimal dragon(int e, long f, int p) {
        BigInteger bf = BigInteger.valueOf(f);
        BigInteger r = bf.shiftLeft(java.lang.Math.max(e - p, 0));
        BigInteger s = BigInteger.ONE.shiftLeft(java.lang.Math.max(0, p - e));
        BigInteger mm = BigInteger.ONE.shiftLeft(java.lang.Math.max(e - p, 0));
        BigInteger mp = mm;
        if (f == 1L << (p - 1)) {
            // Account for unequal gaps.
            mp = mp.shiftLeft(1);
            r = r.shiftLeft(1);
            s = s.shiftLeft(1);
        }
        int k = 0;
        while (r.compareTo(divideTenUp(s)) < 0) {
            k--;
            r = r.multiply(BigInteger.TEN);
            mm = mm.multiply(BigInteger.TEN);
            mp = mp.multiply(BigInteger.TEN);
        }
        while (r.shiftLeft(1).add(mp).compareTo(s.shiftLeft(1)) >= 0) {
            s = s.multiply(BigInteger.TEN);
            k++;
        }
        int[] digits = new int[24];
        int count = 0;
        int exp = 0;
        int u;
        boolean low, high;
        while (true) {
            k--;
            BigInteger[] qr = r.multiply(BigInteger.TEN).divideAndRemainder(s);
            u = qr[0].intValue();
            r = qr[1];
            mm = mm.multiply(BigInteger.TEN);
            mp = mp.multiply(BigInteger.TEN);
            BigInteger twoR = r.shiftLeft(1);
            BigInteger twoS = s.shiftLeft(1);
            low = twoR.compareTo(mm) < 0;
            high = twoS.compareTo(mp) < 0 || twoR.compareTo(twoS.subtract(mp)) > 0;
            if (count == 0) {
                exp = k;
            }
            if (low || high) {
                break;
            }
            digits[count++] = u;
        }
        if (low && !high) {
            digits[count] = u;
        } else if (high && !low) {
            digits[count] = u + 1;
        } else {
            digits[count] = r.shiftLeft(1).compareTo(s) > 0 ? u + 1 : u;
        }
        count++;
        return new Decimal(digits, count, exp);
    }

    /** The quotient a / 10, rounded up (DragonInt.divideTen). */
    private static BigInteger divideTenUp(BigInteger a) {
        BigInteger[] qr = a.divideAndRemainder(BigInteger.TEN);
        return qr[1].signum() != 0 ? qr[0].add(BigInteger.ONE) : qr[0];
    }

    /** The width of a number in Style.Sci (FmtBuf.SciWidth). */
    private static int sciWidth(int prec, boolean literal, int maxExpDigits) {
        int res = prec + maxExpDigits + 5;
        if (prec == 0) {
            res += literal ? 1 : -1;
        }
        return res;
    }

    /** NaN and infinities (FmtBuf.NaN, FmtBuf.Inf, and FmtBuf.Special). */
    private static String special(boolean nan, int kind, int maxExpDigits, int sign,
                                  int style, long prec, boolean literal) {
        String str;
        boolean truncate = false;
        if (literal) {
            String one = nan ? "0.0" : "1.0";
            str = switch (kind) {
                case KindSingle -> one + "/0.0";
                case KindDouble -> one + "d0/0.0d0";
                default -> one + "x0/0.0x0";
            };
        } else if (nan) {
            str = "NaN";
        } else {
            str = "Infinity";
            truncate = true;
        }
        if (style != Sci) {
            return sign == 1 ? "-" + str : str;
        }
        StringBuilder b = new StringBuilder();
        b.append(sign == 0 ? ' ' : '-');
        int width = sciWidth(precision(prec), literal, maxExpDigits);
        b.append(str.length() >= width && truncate ? str.substring(0, 3) : str);
        for (int n = width - b.length(); n > 0; n--) {
            b.append(' ');
        }
        return b.toString();
    }

    /** Plus and minus zero (FmtBuf.Zero). */
    private static String zero(int kind, int maxExpDigits, int sign,
                               int style, long precision, boolean literal) {
        int prec = precision(precision);
        int fullPrec = prec == 0 && literal ? 1 : prec;
        StringBuilder b = new StringBuilder();
        switch (style) {
            case Sci -> {
                b.append(sign == 0 ? ' ' : '-').append('0');
                if (fullPrec > 0) {
                    b.append('.').repeat('0', fullPrec);
                }
                if (literal && kind != KindSingle) {
                    b.append(EXP_CHAR[kind]).append('0');
                }
                b.repeat(' ', sciWidth(prec, literal, maxExpDigits) - b.length());
            }
            case Fix -> {
                if (sign == 1) {
                    b.append('-');
                }
                b.append('0');
                if (fullPrec > 0) {
                    b.append('.').repeat('0', fullPrec);
                }
                if (literal && kind != KindSingle) {
                    b.append(EXP_CHAR[kind]).append('0');
                }
            }
            default -> {
                if (sign == 1) {
                    b.append('-');
                }
                b.append(literal ? "0.0" : "0");
                if (literal && kind != KindSingle) {
                    b.append(EXP_CHAR[kind]).append('0');
                }
            }
        }
        return b.toString();
    }

    /** Normal and denormal numbers (FmtBuf.Number). */
    private static String number(int kind, int maxExpDigits, int sign, Decimal num,
                                 int style, long precision, boolean literal) {
        int prec = precision(precision);
        StringBuilder b = new StringBuilder();
        int exp = num.exp;
        int[] len = new int[1];
        switch (style) {
            case Sci -> {
                len[0] = prec + 1;
                if (round(num, len)) {
                    exp++;
                }
                b.append(sign == 0 ? ' ' : '-');
                fmtNum(b, num.digits, len[0], prec, 1, literal);
                fmtExp(b, exp, false, kind, maxExpDigits, literal);
            }
            case Fix -> {
                len[0] = prec + 1 + num.exp;
                if (round(num, len)) {
                    exp += 1 + java.lang.Math.abs(java.lang.Math.min(0, len[0]));
                }
                if (sign == 1) {
                    b.append('-');
                }
                fmtNum(b, num.digits, len[0], prec, 1 + exp, literal);
                if (literal && kind != KindSingle) {
                    b.append(EXP_CHAR[kind]).append('0');
                }
            }
            default -> {
                len[0] = prec + 1;
                if (round(num, len)) {
                    exp++;
                }
                if (sign == 1) {
                    b.append('-');
                }
                if (java.lang.Math.abs(exp) >= 6) {
                    fmtNum(b, num.digits, len[0], prec, 1, literal);
                    if (prec > 0) {
                        delTrailingZeros(b, literal);
                    }
                    fmtExp(b, exp, true, kind, maxExpDigits, literal);
                } else {
                    int shift = 1 + exp;
                    int p = java.lang.Math.max(prec, len[0] - shift);
                    fmtNum(b, num.digits, len[0], p, shift, literal);
                    if (p > 0) {
                        delTrailingZeros(b, literal);
                    }
                    if (literal && kind != KindSingle) {
                        b.append(EXP_CHAR[kind]).append('0');
                    }
                }
            }
        }
        return b.toString();
    }

    /**
     * Writes the first len digits, with the decimal point between
     * digits[shift-1] and digits[shift], and exactly prec digits after the
     * decimal point (FmtBuf.Number.FmtNum).
     */
    private static void fmtNum(StringBuilder b, int[] digits, int len, int prec, int shift,
                               boolean literal) {
        int toGo = prec;
        int curr;
        if (shift <= 0) {
            curr = 0;
            b.append('0');
        } else {
            curr = java.lang.Math.min(shift, len);
            for (int i = 0; i < curr; i++) {
                b.append((char) ('0' + digits[i]));
            }
            if (len < shift) {
                b.repeat('0', shift - len);
            }
        }
        if (prec == 0) {
            if (literal) {
                b.append(".0");
            }
            return;
        }
        b.append('.');
        if (shift <= 0) {
            int zeroCnt = java.lang.Math.min(toGo, -shift);
            if (zeroCnt > 0) {
                b.repeat('0', zeroCnt);
                toGo -= zeroCnt;
            }
        }
        if (shift <= 0 || shift < len) {
            int digCnt = java.lang.Math.min(toGo, len - curr);
            for (int i = curr; i < curr + digCnt; i++) {
                b.append((char) ('0' + digits[i]));
            }
            toGo -= digCnt;
        }
        if (toGo > 0) {
            b.repeat('0', toGo);
        }
    }

    /** Deletes trailing zeros after the decimal point (FmtBuf.Number.DelTrailingZeros). */
    private static void delTrailingZeros(StringBuilder b, boolean literal) {
        int pos = b.length();
        do {
            pos--;
        } while (b.charAt(pos) == '0');
        if (b.charAt(pos) == '.') {
            if (literal) {
                pos++;
                b.setCharAt(pos, '0');
            } else {
                pos--;
            }
        }
        b.setLength(pos + 1);
    }

    /** Writes the exponent of a number in scientific notation (FmtBuf.Number.FmtExp). */
    private static void fmtExp(StringBuilder b, int exp, boolean auto, int kind,
                               int maxExpDigits, boolean literal) {
        b.append(literal ? EXP_CHAR[kind] : 'e');
        String digits = Integer.toString(java.lang.Math.abs(exp));
        if (auto) {
            if (exp < 0) {
                b.append('-');
            }
        } else {
            b.append(exp < 0 ? '-' : '+');
            int zLen = maxExpDigits - digits.length();
            if (zLen > 0) {
                b.repeat('0', zLen);
            }
        }
        b.append(digits);
    }

    /**
     * Rounds num to len[0] digits in place with rounding mode NearestElseEven,
     * sets len[0] to the number of valid digits, and returns true iff the
     * digits overflowed (FmtBuf.Round).
     */
    private static boolean round(Decimal num, int[] lenRef) {
        int[] digits = num.digits;
        int len = lenRef[0];
        if (len >= num.len) {
            lenRef[0] = num.len;
            return false;
        }
        boolean inc;
        if (len < 0 || digits[len] < 5) {
            inc = false;
        } else if (digits[len] > 5) {
            inc = true;
        } else {
            int i = len + 1;
            while (i < num.len && digits[i] == 0) {
                i++;
            }
            // The decimal digits are taken as exact ("errorSign = 0"), so a
            // tie is broken to even.
            inc = i < num.len || (len >= 1 && digits[len - 1] % 2 == 1);
        }
        boolean res = false;
        if (inc) {
            if (len <= 0) {
                digits[0] = 1;
                res = true;
            } else {
                int i = len;
                do {
                    i--;
                    digits[i] = (digits[i] + 1) % 10;
                } while (i != 0 && digits[i] == 0);
                if (i == 0 && digits[0] == 0) {
                    digits[0] = 1;
                    res = true;
                }
            }
        }
        lenRef[0] = java.lang.Math.min(java.lang.Math.max(len, res ? 1 : 0), num.len);
        return res;
    }
}
