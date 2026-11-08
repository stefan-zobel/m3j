// Java port of CM3's m3-libs/m3core/src/convert/Convert.i3 and Convert.m3.
// Original: Copyright (C) 1990, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.convert;

import m3j.util.AssertError;
import m3j.util.RangeError;

/**
 * Binary/ASCII conversions of numbers.
 *
 * <p>This interface provides binary/ASCII conversions for INTEGERs, REALs
 * and LONGREALs.
 *
 * <p>Port: Buffer (ARRAY OF CHAR) is a {@code char[]}; a SUBARRAY is passed
 * as array, start and length. VAR used becomes the {@code end} of the
 * returned {@link CConvert.Parsed}. EXTENDED is LONGREAL on the target
 * (BYTESIZE (EXTENDED) = 8). Only the procedures the compiler needs are
 * ported.
 */
public final class Convert {
    private Convert() {}

    /**
     * Formatting styles for REALs:
     * <pre>
     *   Flo:       xxx.yyy
     *   AltFlo:    xxx.yyy             (trailing zeros are suppressed)
     *   Sci:       xxx.yyy'E'exponent  ('D' for LONGREAL)
     *   AltSci:    xxx.yyy'E'exponent  (trailing zeros are suppressed)
     *   Mix:       == AltFlo unles AltSci is shorter;  if AltFlow is
     *                 selected and there are no zeros after the decimal
     *                 point, the decimal point is suppressed.
     * </pre>
     */
    public enum Style { Flo, AltFlo, Sci, AltSci, Mix }

    /** EXCEPTION Failed. */
    public static final class Failed extends Exception {
        private static final long serialVersionUID = 1L;

        public Failed() {
            super("Convert.Failed");
        }
    }

    private static final char[] Digits = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    private static final long FIRST_INTEGER = Long.MIN_VALUE;

    /** Base = [2..16]. */
    private static int checkBase(int base) {
        if (base < 2 || base > 16) {
            throw new RangeError("Convert");
        }
        return base;
    }

    /*---- Binary to ASCII conversions ----*/

    /*
     * The "From" procedures convert binary values to ASCII character strings.
     * Each procedure returns the number of characters that resulted.  Extra space
     * in the buffers is left unmodified.  Failed is raised if the supplied buffer
     * is too small to hold the result.
     */

    /**
     * Converts value to ASCII in the specified base and stores the result in buf.
     * If prefix=TRUE, include the base prefix in the result.
     */
    public static int fromInt(char[] buf, long value, int base, boolean prefix) throws Failed {
        return fromInt(buf, 0, buf.length, value, base, prefix);
    }

    /** fromInt with base 10 and no prefix. */
    public static int fromInt(char[] buf, long value) throws Failed {
        return fromInt(buf, 0, buf.length, value, 10, false);
    }

    /** fromInt on SUBARRAY (buf, start, n). */
    public static int fromInt(char[] buf, int start, int n, long value, int base, boolean prefix) throws Failed {
        checkBase(base);
        int nDigits = 0;
        boolean minus = false;
        boolean bump = false;
        int i;
        int j;
        char c;
        char[] result = new char[64 + 1];

        if (value == 0) {
            result[0] = '0';
            nDigits = 1;
        } else { // handle a non-zero number
            // get rid of negative numbers
            if (value < 0) {
                if (value == FIRST_INTEGER) {
                    // 2's complement makes FIRST(INTEGER) a special case
                    bump = true;
                    value++;
                }
                minus = true;
                value = -value;
                AssertError.check(value > 0, "Convert");
            }

            // convert the bulk of the digits
            while (value > 0) {
                result[nDigits] = Digits[(int) Math.floorMod(value, (long) base)];
                value = Math.floorDiv(value, (long) base);
                nDigits++;
            }

            // fixup FIRST (INTEGER)
            if (bump) {
                result[nDigits] = '0';
                j = 0;
                for (;;) {
                    c = result[j];
                    if (c <= '9') {
                        i = c - '0';
                    } else {
                        i = c - 'a' + 10;
                    }
                    i++;
                    if (i < base) {
                        result[j] = Digits[i];
                        break;
                    }
                    result[j] = '0';
                    j++;
                }
                nDigits = Math.max(nDigits, j + 1);
            }
        }

        // make sure we've got room for the result
        j = nDigits;
        if (minus) {
            j++;
        }
        if (prefix) {
            if (base > 9) {
                j += 3;
            } else {
                j += 2;
            }
        }
        if (j > n) {
            throw new Failed();
        }

        // build the result buffer
        j = 0;
        if (minus) {
            buf[start] = '-';
            j = 1;
        }
        if (prefix) {
            if (base > 9) {
                buf[start + j] = '1';
                j++;
            }
            buf[start + j] = Digits[base % 10];
            j++;
            buf[start + j] = '_';
            j++;
        }
        for (int k = nDigits - 1; k >= 0; k--) {
            buf[start + j] = result[k];
            j++;
        }

        return j;
    }

    // Port, omitted: FromLongInt (LONGINT is INTEGER on the target).

    /**
     * Treats value as unsigned, converts it to ASCII in the specified base and
     * stores the result in buf.  If prefix=TRUE, include the base prefix in the
     * result.
     */
    public static int fromUnsigned(char[] buf, long value, int base, boolean prefix) throws Failed {
        checkBase(base);
        int nDigits = 0;
        int j;
        char[] result = new char[64 + 1];

        if (value == 0) {
            result[0] = '0';
            nDigits = 1;
        } else {
            // convert the bulk of the digits
            while (value != 0) {
                result[nDigits] = Digits[(int) Long.remainderUnsigned(value, base)];
                value = Long.divideUnsigned(value, base);
                nDigits++;
            }
        }

        // make sure we've got room for the result
        j = nDigits;
        if (prefix) {
            if (base > 9) {
                j += 3;
            } else {
                j += 2;
            }
        }
        if (j > buf.length) {
            throw new Failed();
        }

        // build the result buffer
        j = 0;
        if (prefix) {
            if (base > 9) {
                buf[j] = '1';
                j++;
            }
            buf[j] = Digits[base % 10];
            j++;
            buf[j] = '_';
            j++;
        }
        for (int k = nDigits - 1; k >= 0; k--) {
            buf[j] = result[k];
            j++;
        }

        return j;
    }

    // Port, omitted: FromLongUnsigned.

    /** GetPrecisionMode: returns {mode, precision}. */
    private static int[] getPrecisionMode(Style style, int p) {
        // Port: mode and precision are uninitialized in the original for
        // other styles; there are none.
        int mode = 0;
        int precision = 0;
        if (style == Style.Flo || style == Style.AltFlo) {
            mode = 3;
            precision = p;
        } else if (style == Style.Sci || style == Style.AltSci || style == Style.Mix) {
            mode = 2;
            precision = p + 1;
        }
        return new int[] {mode, precision};
    }

    /**
     * Format: lays out the digits of a dtoa result.
     *
     * <p>Port: the digit string from start to end is {@code dg.digits()};
     * decpt and sign come from {@code dg}; freedtoa is not needed.
     */
    private static int format(char[] buf, int p, Style style, CConvert.Digits dg, char exponentChar)
            throws Failed {
        int len = 0;
        int consumed = 0;
        int digits;
        char[] d;
        int af = 0, bf = 0, cf = 0, df = 0, ef = 0;
        boolean pf = true;
        int as = 0, bs = 0, cs = 0, ds = 0, es = 0;
        boolean ps = true;
        int a = 0, b = 0, c = 0, dd = 0, e = 0;
        long x = 0;
        boolean pp = true;
        int decpt = dg.decpt();
        // Port: sign is the int of the sign bit; only its being # 0 matters.
        boolean sign = dg.sign();

        // The string will consist of: - the first A digits from the conversion
        // - B '0's - '.' if P is TRUE - C '0's - the next D digits from the
        // conversion - E '0's - if X # LAST (INTEGER), " <expchar>
        // <exponent>"

        // Port: Xf, Xs and X are INTEGERs (xfL, xsL, x); LAST (INTEGER)
        // marks "no exponent".
        long xfL = Long.MAX_VALUE;
        long xsL = Long.MAX_VALUE;

        d = dg.digits().toCharArray();
        digits = d.length;

        if (decpt == -32768) { // Special value returned by dtoa
            if (sign) {
                buf[0] = '-';
                len = 1;
            } else {
                len = 0;
            }
            System.arraycopy(d, 0, buf, len, digits);
            return len + digits;
        }

        if (style == Style.Flo || style == Style.AltFlo || style == Style.Mix) {
            if (decpt <= 0) {
                af = 0;
                bf = 1;
                cf = Math.min(-decpt, p);
                df = Math.min(digits, p - cf);
                ef = Math.max(0, p - (cf + df));
            } else if (decpt <= digits) {
                af = decpt;
                bf = 0;
                cf = 0;
                df = Math.min(digits - decpt, p);
                ef = Math.max(0, p - df);
            } else {
                af = digits;
                bf = decpt - digits;
                cf = p;
                df = 0;
                ef = 0;
            }
            if (style == Style.AltFlo || style == Style.Mix) {
                ef = 0;
                while (df > 0 && d[af + df - 1] == '0') {
                    df--;
                }
                if (df == 0) {
                    cf = 0;
                }
            }
        }

        if (style == Style.Sci || style == Style.AltSci || style == Style.Mix) {
            as = Math.min(1, digits);
            bs = 0;
            cs = 0;
            ds = Math.min(digits - 1, p);
            es = Math.max(0, p - digits + 1);
            xsL = decpt - 1;
            if (style == Style.AltSci || style == Style.Mix) {
                es = 0;
                while (ds > 0 && d[as + ds - 1] == '0') {
                    ds--;
                }
                if (ds == 0) {
                    cs = 0;
                }
            }
        }

        if (style == Style.Mix) {
            int floLength = af + bf + 1 + cf + df + ef;
            int sciLength = as + bs + 1 + cs + ds + es;
            if (xsL != Long.MAX_VALUE) {
                // Sic: the length of the exponent is computed from X,
                // which is still 0 here, instead of Xs.
                if (xsL < 0) {
                    sciLength += 3;
                    long xx = -x;
                    while (xx >= 10) {
                        sciLength++;
                        xx = xx / 10;
                    }
                } else {
                    sciLength += 2;
                    long xx = x;
                    while (xx >= 10) {
                        sciLength++;
                        xx = xx / 10;
                    }
                }
            }
            if (floLength > sciLength) {
                style = Style.AltSci;
            } else {
                style = Style.AltFlo;
                if (df == 0 && cf == 0) {
                    pf = false;
                }
            }
        }

        if (style == Style.Flo || style == Style.AltFlo) {
            a = af;
            b = bf;
            c = cf;
            dd = df;
            e = ef;
            x = xfL;
            pp = pf;
        } else {
            a = as;
            b = bs;
            c = cs;
            dd = ds;
            e = es;
            x = xsL;
            pp = ps;
        }

        // Set the sign
        if (sign) {
            buf[len] = '-';
            len++;
        }

        consumed = 0;
        for (int i = 1; i <= a; i++) {
            buf[len] = d[consumed];
            len++;
            consumed++;
        }
        for (int i = 1; i <= b; i++) {
            buf[len] = '0';
            len++;
        }
        if (pp) {
            buf[len] = '.';
            len++;
        }
        for (int i = 1; i <= c; i++) {
            buf[len] = '0';
            len++;
        }
        for (int i = 1; i <= dd; i++) {
            buf[len] = d[consumed];
            len++;
            consumed++;
        }
        for (int i = 1; i <= e; i++) {
            buf[len] = '0';
            len++;
        }
        if (x != Long.MAX_VALUE) {
            buf[len] = exponentChar;
            len++;
            len += fromInt(buf, len, buf.length - len, x, 10, false);
        }

        return len;
    }

    /**
     * Converts value to ASCII in the given style and stores the result in buf.
     * The precision is the number of fractional digits.
     */
    public static int fromFloat(char[] buf, float v, int p, Style style) throws Failed {
        int[] mp = getPrecisionMode(style, p);
        CConvert.Digits dg = CConvert.ftoa(v, mp[0], mp[1]);
        return format(buf, p, style, dg, 'E');
    }

    /**
     * Converts value to ASCII in the given style and stores the result in buf.
     * The precision is the number of fractional digits.
     */
    public static int fromLongFloat(char[] buf, double v, int p, Style style) throws Failed {
        int[] mp = getPrecisionMode(style, p);
        CConvert.Digits dg = CConvert.dtoa(v, mp[0], mp[1]);
        return format(buf, p, style, dg, 'D');
    }

    /**
     * Converts value to ASCII in the given style and stores the result in buf.
     * The precision is the number of fractional digits.
     *
     * <p>Port: BYTESIZE (v) = 8, so dtoa is used; qtoa is omitted.
     */
    public static int fromExtended(char[] buf, double v, int p, Style style) throws Failed {
        int[] mp = getPrecisionMode(style, p);
        CConvert.Digits dg = CConvert.dtoa(v, mp[0], mp[1]);
        return format(buf, p, style, dg, 'X');
    }

    /*---- ASCII to binary conversions ---*/

    // Port, omitted: ToInt, ToLongInt, ToUnsigned, ToLongUnsigned.

    /** A converted REAL and the number of characters used. */
    public record FloatResult(float value, int used) {}

    /** A converted LONGREAL or EXTENDED and the number of characters used. */
    public record DoubleResult(double value, int used) {}

    /**
     * Converts a floating point number. Port: never raises Failed, because
     * strtod always sets its end pointer.
     */
    public static FloatResult toFloat(char[] buf, int start, int n) throws Failed {
        CConvert.Parsed r = toBinary(buf, start, n, 'E', 'E');
        return new FloatResult((float) r.value(), r.end());
    }

    /** Converts a floating point number. */
    public static DoubleResult toLongFloat(char[] buf, int start, int n) throws Failed {
        CConvert.Parsed r = toBinary(buf, start, n, 'D', 'd');
        return new DoubleResult(r.value(), r.end());
    }

    /**
     * Converts a floating point number. Port: BYTESIZE (value) = 8, so
     * ToBinaryExt uses strtod; strtoq is omitted.
     */
    public static DoubleResult toExtended(char[] buf, int start, int n) throws Failed {
        CConvert.Parsed r = toBinary(buf, start, n, 'X', 'x');
        return new DoubleResult(r.value(), r.end());
    }

    /**
     * ToBinary and ToBinaryExt: copy source to tmp, fix the exponent
     * character and null terminate, then convert.
     */
    private static CConvert.Parsed toBinary(char[] source, int start, int nchars, char exp1, char exp2) {
        char[] tmp = new char[nchars + 1];
        char ch;
        for (int i = 0; i <= nchars - 1; i++) {
            ch = source[start + i];
            if (ch == exp1 || ch == exp2) {
                ch = 'e';
            }
            tmp[i] = ch;
        }
        tmp[nchars] = '\000';

        // finally, do the conversion
        return CConvert.strtod(tmp);
    }
}
