// Java port of CM3's m3-sys/m3middle/src/TInt.i3 and TInt.m3.
// Original: Copyright (C) 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import m3j.util.AssertError;
import m3j.util.RangeError;

/**
 * Modula-3 target description.
 *
 * <p>This interface provides simulations of signed integer operations,
 * at the maximum precision supported by any target.
 *
 * <p>Unless otherwise specified, the arithmetic operations defined
 * below return TRUE if they succeed in producing a new value,
 * otherwise they return FALSE.
 *
 * <p>Port: Int (ARRAY [0..8] OF IByte) is an {@code int[9]} holding values
 * in [0..255], low-order byte first. Like the Modula-3 array it is a
 * mutable value: VAR results are written into the array passed by the
 * caller, also when an operation returns FALSE, and an assignment of Int
 * values is a copy ({@link #copy}, {@link #assign}). The constants must
 * not be passed as results.
 */
public final class TInt {
    private TInt() {}

    /** NUMBER (Int). */
    public static final int NUMBER = 9;

    /** LAST (Int). */
    private static final int LAST = NUMBER - 1;

    /** Size = BITSIZE (Int). */
    public static final int Size = NUMBER * 8;

    public static final int[] Zero   = {0, 0, 0, 0, 0, 0, 0, 0, 0};
    public static final int[] One    = {1, 0, 0, 0, 0, 0, 0, 0, 0};
    public static final int[] Two    = {2, 0, 0, 0, 0, 0, 0, 0, 0};
    public static final int[] MOne   = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

    public static final int[] Min8   = {0x80, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
    public static final int[] Max8   = {0x7f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    public static final int[] Max8U  = {0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    public static final int[] Min16  = {0x00, 0x80, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
    public static final int[] Max16  = {0xFF, 0x7f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    public static final int[] Max16U = {0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    public static final int[] Min32  = {0x00, 0x00, 0x00, 0x80, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
    public static final int[] Max32  = {0xFF, 0xFF, 0xFF, 0x7f, 0x00, 0x00, 0x00, 0x00, 0x00};
    public static final int[] Max32U = {0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00};
    public static final int[] Min64  = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0xFF};
    public static final int[] Max64  = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x7f, 0x00};
    public static final int[] Max64U = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00};

    /** A new Int (Port: Modula-3 variables of type Int are zeroed here). */
    public static int[] newInt() {
        return new int[NUMBER];
    }

    /** A copy of {@code a}: the value of the Modula-3 expression {@code a}. */
    public static int[] copy(int[] a) {
        return a.clone();
    }

    /** {@code r := a}. */
    public static void assign(int[] r, int[] a) {
        System.arraycopy(a, 0, r, 0, NUMBER);
    }

    // CONST (* IMPORTS *) RShift = Word.RightShift; LShift = Word.LeftShift;
    // And = Word.And;

    private static final int BITSIZE_IByte = 8;
    private static final long Mask = 0xFF;
    private static final long SignMask = 1L << (BITSIZE_IByte - 1);
    private static final long Base = Mask + 1;
    private static final char[] Digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    private static final int[] Ten = {10, 0, 0, 0, 0, 0, 0, 0, 0};

    /** An assignment to an IByte: range-checked. */
    private static int ibyte(long x) {
        if (x < 0 || x > 255) {
            throw new RangeError("TInt");
        }
        return (int) x;
    }

    /** Converts a host integer 'x' to a target integer 'i'. */
    public static boolean fromInt(long x, int[] r) {
        for (int i = 0; i <= LAST; i++) {
            r[i] = (int) (x & Mask);
            x = Math.floorDiv(x, Base);
        }
        return x == 0 || x == -1;
    }

    /**
     * Converts a target integer 'i' to a host integer 'x'. Port: VAR x is
     * {@code x[0]}.
     */
    public static boolean toInt(int[] r, long[] x) {
        final int Extras = 64 / BITSIZE_IByte;
        long j = 0;
        int signChunk = Math.min(Extras - 1, LAST);
        // check that any extra bits are the same as the sign bit
        if ((r[signChunk] & SignMask) != 0) {
            j = Mask;
        }
        for (int i = Extras; i <= LAST; i++) {
            if (r[i] != j) {
                return false;
            }
        }

        // ensure the result has the right sign extension
        if (j == 0) {
            x[0] = 0;
        } else {
            x[0] = ~0L;
        }

        // finally, pack the bits
        for (int i = LAST; i >= 0; i--) {
            x[0] = (x[0] << BITSIZE_IByte) | r[i];
        }

        return true;
    }

    /**
     * Converts the string of decimal characters in 'chars' to an integer
     * value in 'i'.
     */
    public static boolean New(char[] x, int start, int n, int[] r) {
        final int ZERO = '0';
        final int ZEROZERO = 10 * ZERO + ZERO;
        int[] tmp = newInt();
        int[] digit;
        assign(r, Zero);
        if (n == 1) {
            r[0] = ibyte(x[start] - ZERO);
        } else if (n == 2) {
            r[0] = ibyte(10 * x[start] + x[start + 1] - ZEROZERO);
        } else {
            digit = copy(Zero);
            for (int i = 0; i <= n - 1; i++) {
                digit[0] = ibyte(x[start + i] - ZERO);
                if (!multiply(r, Ten, tmp)) {
                    return false;
                }
                if (!add(tmp, digit, r)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** New on a whole array. */
    public static boolean New(char[] x, int[] r) {
        return New(x, 0, x.length, r);
    }

    /** Returns 'a + b' unless there's an overflow. */
    public static boolean add(int[] a, int[] b, int[] r) {
        // It is safe for r to alias a or b
        long carry = 0;
        long aSign = a[LAST] & SignMask;
        long bSign = b[LAST] & SignMask;
        long rSign;
        for (int i = 0; i <= LAST; i++) {
            carry = a[i] + b[i] + carry;
            r[i] = (int) (carry & Mask);
            carry = carry >>> BITSIZE_IByte;
        }
        rSign = r[LAST] & SignMask;
        return aSign != bSign || aSign == rSign;
    }

    /** Returns 'a + 1' unless there's an overflow. */
    public static boolean inc(int[] a) {
        int[] b = copy(a);
        return add(b, One, a);
    }

    /** Returns 'a - 1' unless there's an overflow. */
    public static boolean dec(int[] a) {
        int[] b = copy(a);
        return subtract(b, One, a);
    }

    /** Returns 'a - b' unless there's an overflow. */
    public static boolean subtract(int[] a, int[] b, int[] r) {
        // It is safe for r to alias a or b
        long borrow = 0;
        long aSign = a[LAST] & SignMask;
        long bSign = b[LAST] & SignMask;
        long rSign;
        for (int i = 0; i <= LAST; i++) {
            borrow = a[i] - b[i] - borrow;
            r[i] = (int) (borrow & Mask);
            borrow = (borrow >>> BITSIZE_IByte) & 1;
        }
        rSign = r[LAST] & SignMask;
        return aSign == bSign || aSign == rSign;
    }

    /** '-a' or FALSE if overflow. */
    public static boolean negate(int[] a, int[] r) {
        return subtract(Zero, a, r);
    }

    /** ABS(a) or FALSE if overflow. */
    public static boolean abs(int[] a, int[] r) {
        if (GE(a, Zero)) {
            assign(r, a);
            return true;
        }
        return negate(a, r);
    }

    /** Returns 'a * b' unless there's an overflow. */
    public static boolean multiply(int[] a, int[] b, int[] r) {
        int k;
        long carry;
        int[] q = newInt();
        int[] p = new int[2 * NUMBER];
        for (int i = 0; i <= LAST; i++) {
            for (int j = 0; j <= LAST; j++) {
                k = i + j;
                carry = (long) a[i] * b[j];
                while (carry != 0) {
                    carry = carry + p[k];
                    p[k] = (int) (carry & Mask);
                    carry = carry >>> BITSIZE_IByte;
                    k++;
                }
            }
        }

        for (int i = 0; i <= LAST; i++) {
            r[i] = p[i];
        }
        for (int i = 0; i <= LAST; i++) {
            q[i] = p[i + NUMBER];
        }

        // compute the top half
        if ((a[LAST] & SignMask) != 0) {
            subtract(q, b, q);
        }
        if ((b[LAST] & SignMask) != 0) {
            subtract(q, a, q);
        }

        // there is overflow if the top half is not equal to
        // to the sign bit of the low half
        carry = 0;
        if ((r[LAST] & SignMask) != 0) {
            carry = Mask;
        }
        for (int i = 0; i <= LAST; i++) {
            if (q[i] != carry) {
                return false;
            }
        }

        return true;
    }

    /** Returns 'a DIV b' unless there's an overflow. */
    public static boolean div(int[] num, int[] den, int[] q) {
        int[] r = newInt();
        return divMod(num, den, q, r);
    }

    /** Returns 'a MOD b' unless there's an overflow. */
    public static boolean mod(int[] num, int[] den, int[] r) {
        int[] q = newInt();
        return divMod(num, den, q, r);
    }

    private static boolean divMod(int[] a, int[] b, int[] q, int[] r) {
        //  a DIV b  ==  FLOOR (FLOAT (a) / FLOAT (b))
        //  a MOD b  ==  a - b * (a DIV b)
        int[] num = copy(a);
        int[] den = copy(b);
        boolean numNeg;
        boolean denNeg;
        int[] min = newInt();
        if (EQ(b, Zero)) {
            return false;
        }
        if (EQ(a, Zero)) {
            assign(q, Zero);
            assign(r, Zero);
            return true;
        }

        // grab the signs
        numNeg = (a[LAST] & SignMask) != 0;
        denNeg = (b[LAST] & SignMask) != 0;

        // check for the only possible overflow:  FIRST(Int) DIV -1
        if (numNeg && denNeg) {
            TWord.shift(MOne, Size - 1, min);
            if (EQ(a, min) && EQ(b, MOne)) {
                return false;
            }
        }

        // convert the operands to unsigned.
        if (numNeg) {
            subtract(Zero, a, num);
        }
        if (denNeg) {
            subtract(Zero, b, den);
        }

        // compute the unsigned quotient and remainder
        TWord.divMod(num, den, q, r);

        // fix-up the results to match the signs (see note below)
        if (EQ(r, Zero)) {
            if (numNeg != denNeg) {
                subtract(Zero, q, q);
            }
        } else if (numNeg && denNeg) {
            subtract(Zero, r, r);
        } else if (numNeg) {
            subtract(Zero, q, q);
            subtract(q, One, q);
            subtract(den, r, r);
        } else if (denNeg) {
            subtract(Zero, q, q);
            subtract(q, One, q);
            subtract(r, den, r);
        // else everything's already ok
        }

        return true;
    }

    /*
     * DIV and MOD notes:
     *     Modula-3 defines  "a DIV b" to be  "FLOOR(a/b)"
     *                  and  "a MOD b" to be  "a - b * (a DIV b)".
     *
     *     Given a >= 0, b > 0, q = a DIV b, and r = a MOD b
     *     we get the following conversions:
     *
     *         x    y     x DIV y     x MOD y
     *       ----------------------------------
     *         a    b        q           r
     *        -a   -b        q          -r
     *
     *        -a    b       -q           0
     *        -a    b      -q-1         b-r  (r # 0)
     *
     *         a   -b       -q           0
     *         a   -b      -q-1         r-b  (r # 0)
     */

    /** Returns 'a = b'. */
    public static boolean EQ(int[] a, int[] b) {
        return java.util.Arrays.equals(a, b);
    }

    /** Returns 'a &lt; b'. */
    public static boolean LT(int[] a, int[] b) {
        long aSign = a[LAST] & SignMask;
        long bSign = b[LAST] & SignMask;
        if (aSign != bSign) {
            return aSign != 0;
        }

        for (int i = LAST; i >= 0; i--) {
            if (a[i] < b[i]) {
                return true;
            } else if (a[i] > b[i]) {
                return false;
            }
        }

        return false;
    }

    /** Returns 'a &lt;= b'. */
    public static boolean LE(int[] a, int[] b) {
        return EQ(a, b) || LT(a, b);
    }

    /** 'a # b'. */
    public static boolean NE(int[] a, int[] b) {
        return !java.util.Arrays.equals(a, b);
    }

    /** 'a &gt; b'. */
    public static boolean GT(int[] a, int[] b) {
        return LT(b, a);
    }

    /** 'a &gt;= b'. */
    public static boolean GE(int[] a, int[] b) {
        return LE(b, a);
    }

    /** Converts 'i' to a printable string. */
    public static String toText(int[] r) {
        char[] result = new char[Size + 1];
        return new String(result, 0, toChars(r, result));
    }

    /**
     * Converts 'i' to a printable string in 'buf'.  Returns the
     * number of characters in the string.  Returns -1 if 'buf' is too short.
     */
    public static int toChars(int[] r, char[] buf) {
        int nDigits = 0;
        boolean minus = false;
        boolean bump = false;
        int i;
        int j;
        char[] result = new char[Size + 1];
        int[] rr = copy(r);
        int[] quo = newInt();
        int[] rem = newInt();
        int[] min = newInt();
        if (EQ(r, Zero)) {
            result[0] = '0';
            nDigits++;

        } else { // handle a non-zero number

            // get rid of negative numbers
            if ((r[LAST] & SignMask) != 0) {
                TWord.shift(MOne, Size - 1, min);
                if (EQ(r, min)) {
                    // 2's complement makes FIRST(Int) a special case
                    bump = true;
                    add(rr, One, rr);
                }
                minus = true;
                subtract(Zero, rr, rr);
            }

            // convert the bulk of the digits
            while (LT(Zero, rr)) {
                TWord.divMod(rr, Ten, quo, rem);
                result[nDigits] = Digits[rem[0]];
                assign(rr, quo);
                nDigits++;
            }

            // fixup FIRST (Int)
            if (bump) {
                result[nDigits] = '0';
                j = 0;
                for (;;) {
                    i = result[j] - '0';
                    i++;
                    if (i < 10) {
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
        if (j > buf.length) {
            return -1;
        }

        // build the result buffer
        j = 0;
        if (minus) {
            buf[0] = '-';
            j = 1;
        }
        for (int k = nDigits - 1; k >= 0; k--) {
            buf[j] = result[k];
            j++;
        }

        return j;
    }

    /**
     * PRE: r is nonnegative and fits in buf.
     * Converts 'r' to the shortest sequence of bytes which when zero-extended
     * equal 'r', and stores them in little-endian order in buf.  This means
     * the most significant bit in buf is not a sign bit, and could be 1.
     * Returns the number of significant bytes of the result.
     */
    public static int toUnsignedBytes(int[] r, int[] buf) {
        int j = NUMBER - 1;
        AssertError.check(r[j] == 0, "TInt"); // r is nonnegative.

        // skip most-significant zero bytes.
        while (j > 0 && r[j - 1] == 0) {
            j--;
        }

        AssertError.check(j <= buf.length, "TInt"); // Result fits in buf.

        // unpack the bytes
        for (int i = 0; i <= j - 1; i++) {
            buf[i] = r[i];
        }

        return j;
    }

    /**
     * Sign-extends from the low-order 'n' bytes of 'i'.
     * Returns TRUE if 'i' has at most 'n' significant bytes, FALSE otherwise.
     */
    public static boolean extend(int[] a, int n, int[] r) {
        if (n < 0) {
            throw new RangeError("TInt");
        }
        boolean result = true;
        for (int i = 0; i <= n - 1; i++) {
            r[i] = a[i];
        }
        if ((a[n - 1] & SignMask) == 0) {
            for (int i = n; i <= LAST; i++) {
                if (a[i] != 0) {
                    result = false;
                }
                r[i] = 0;
            }
        } else {
            for (int i = n; i <= LAST; i++) {
                if (a[i] != Mask) {
                    result = false;
                }
                r[i] = (int) Mask;
            }
        }
        return result;
    }
}
