// Java port of CM3's m3-sys/m3middle/src/TWord.i3 and TWord.m3.
// Original: Copyright (C) 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import m3j.util.RangeError;

/**
 * Modula-3 target description.
 *
 * <p>This interface provides simulations of unsigned integer operations, at the
 * maximum precision supported by any target.
 *
 * <p>Unless otherwise specified, the arithmetic operations defined
 * below return TRUE if they succeed in producing a new target value,
 * otherwise they return FALSE.
 *
 * <p>Port: Int values are {@code int[9]} arrays as in {@link TInt}.
 */
public final class TWord {
    private TWord() {}

    /** Size = BITSIZE (Int). */
    public static final int Size = TInt.Size;

    public static final int[] Max8  = {0xFF, 0, 0, 0, 0, 0, 0, 0, 0};
    public static final int[] Max16 = {0xFF, 0xFF, 0, 0, 0, 0, 0, 0, 0};
    public static final int[] Max32 = {0xFF, 0xFF, 0xFF, 0xFF, 0, 0, 0, 0, 0};
    public static final int[] Max64 = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0};

    private static final int LAST = TInt.NUMBER - 1;
    private static final int BITSIZE_IByte = 8;
    private static final long Mask = 0xFF;
    private static final long Base = Mask + 1;

    /** An assignment to an IByte: range-checked. */
    private static int ibyte(long x) {
        if (x < 0 || x > 255) {
            throw new RangeError("TWord");
        }
        return (int) x;
    }

    /** A value of the subrange [lo..hi]: range-checked. */
    private static long range(long x, long lo, long hi) {
        if (x < lo || x > hi) {
            throw new RangeError("TWord");
        }
        return x;
    }

    /*------------------------------------------- unsigned integer operations ---*/

    /**
     * Converts the string of characters in 'chars' representing a base 'base'
     * number to an integer value in 'i'.
     */
    public static boolean New(char[] x, int start, int n, int base, int[] r) {
        range(base, 2, 16);
        int[] rr;
        long digit;
        char ch;
        TInt.assign(r, TInt.Zero);
        for (int i = 0; i <= n - 1; i++) {
            ch = x[start + i];
            if ('0' <= ch && ch <= '9') {
                digit = ch - '0';
            } else if ('A' <= ch && ch <= 'F') {
                digit = ch - 'A' + 10;
            } else if ('a' <= ch && ch <= 'f') {
                digit = ch - 'a' + 10;
            } else {
                return false;
            }

            // rr := r * base
            rr = TInt.copy(TInt.Zero);
            for (int ii = 0; ii <= LAST; ii++) {
                long carry = (long) r[ii] * base;
                for (int j = ii; j <= LAST; j++) {
                    if (carry == 0) {
                        break;
                    }
                    carry += rr[j];
                    rr[j] = (int) (carry & Mask);
                    carry = carry >>> BITSIZE_IByte;
                }
                if (carry != 0) {
                    return false;
                }
            }

            // r := rr + digit
            long carry = digit;
            for (int ii = 0; ii <= LAST; ii++) {
                carry += rr[ii];
                r[ii] = (int) (carry & Mask);
                carry = carry >>> BITSIZE_IByte;
            }
            if (carry != 0) {
                return false;
            }
        }

        return true;
    }

    /** Returns 'Word.Plus (a, b)'. */
    public static void add(int[] a, int[] b, int[] r) {
        long carry = 0;
        for (int i = 0; i <= LAST; i++) {
            carry = a[i] + b[i] + carry;
            r[i] = (int) (carry & Mask);
            carry = carry >>> BITSIZE_IByte;
        }
    }

    /** Returns 'Word.Minus (a, b)'. */
    public static void subtract(int[] a, int[] b, int[] r) {
        long borrow = 0;
        for (int i = 0; i <= LAST; i++) {
            borrow = a[i] - b[i] - borrow;
            r[i] = (int) (borrow & Mask);
            borrow = (borrow >>> BITSIZE_IByte) & 1;
        }
    }

    /** Returns 'Word.Times (a, b)'. */
    public static void multiply(int[] a, int[] b, int[] r) {
        // Port: r := TInt.Zero happens before a and b are read, as in the
        // original, so r must not alias a or b.
        long carry;
        TInt.assign(r, TInt.Zero);
        for (int i = 0; i <= LAST; i++) {
            for (int j = 0; j <= LAST; j++) {
                carry = (long) a[i] * b[j];
                for (int k = i + j; k <= LAST; k++) {
                    if (carry == 0) {
                        break;
                    }
                    carry = carry + r[k];
                    r[k] = (int) (carry & Mask);
                    carry = carry >>> BITSIZE_IByte;
                }
            }
        }
    }

    /** Returns 'Word.Divide (a, b)' unless b is zero. */
    public static boolean div(int[] num, int[] den, int[] q) {
        int[] r = TInt.newInt();
        if (TInt.EQ(den, TInt.Zero)) {
            return false;
        }
        if (TInt.EQ(num, TInt.Zero)) {
            TInt.assign(q, TInt.Zero);
            return true;
        }
        divMod(num, den, q, r);
        return true;
    }

    /** Returns 'Word.Mod (a, b)' unless b is zero. */
    public static boolean mod(int[] num, int[] den, int[] r) {
        int[] q = TInt.newInt();
        if (TInt.EQ(den, TInt.Zero)) {
            return false;
        }
        if (TInt.EQ(num, TInt.Zero)) {
            TInt.assign(r, TInt.Zero);
            return true;
        }
        divMod(num, den, q, r);
        return true;
    }

    /** Returns 'q = x DIV y', and 'r = x MOD y', but assumes that 'y # 0'. */
    public static void divMod(int[] x, int[] y, int[] q, int[] r) {
        long carry;
        long borrow;
        long tmp;
        int maxDen = 0;
        int maxNum = 0;
        long scale;
        long quoEst;
        int numHi;
        long x1, x2, x3;
        long[] num = new long[TInt.NUMBER + 2];
        long[] den = new long[TInt.NUMBER + 2];
        // initialize the numerator and denominator,
        // and find the highest non-zero digits
        for (int i = 0; i <= LAST; i++) {
            num[i] = x[i];
            if (num[i] != 0) {
                maxNum = i;
            }
            den[i] = y[i];
            if (den[i] != 0) {
                maxDen = i;
            }
        }

        TInt.assign(q, TInt.Zero);
        TInt.assign(r, TInt.Zero);

        if (maxDen == 0) {
            // single digit denominator case
            carry = 0;
            for (int j = maxNum; j >= 0; j--) {
                tmp = carry * Base + num[j];
                q[j] = ibyte(Math.floorDiv(tmp, den[0]));
                carry = Math.floorMod(tmp, den[0]);
            }
            r[0] = ibyte(carry);
            return;
        }

        // Insure that the first digit of the divisor is at least Base/2.
        // This is required by the quotient digit estimation algorithm.

        scale = Math.floorDiv(Base, den[maxDen] + 1);
        if (scale > 1) { // scale divisor and dividend
            carry = 0;
            for (int i = 0; i < num.length; i++) {
                tmp = (num[i] * scale) + carry;
                num[i] = tmp & Mask;
                carry = tmp >>> BITSIZE_IByte;
                if (num[i] != 0) {
                    maxNum = i;
                }
            }

            carry = 0;
            for (int i = 0; i < den.length; i++) {
                tmp = (den[i] * scale) + carry;
                den[i] = tmp & Mask;
                carry = tmp >>> BITSIZE_IByte;
                if (den[i] != 0) {
                    maxDen = i;
                }
            }
        }

        // Main loop
        for (int i = maxNum - maxDen + 1; i >= 1; i--) {
            // guess the next quotient digit, quo_est, by dividing the first
            // two remaining dividend digits by the high order quotient digit.
            // quo_est is never low and is at most 2 high.

            numHi = i + maxDen; // index of highest remaining dividend digit

            tmp = num[numHi] * Base;
            if (numHi > 0) {
                tmp = tmp + num[numHi - 1];
            }
            if (num[numHi] != den[maxDen]) {
                quoEst = Math.floorDiv(tmp, den[maxDen]);
            } else {
                quoEst = Base - 1;
            }

            // refine quo_est so it's usually correct, and at most one high.
            x3 = 0;
            if (numHi > 1) {
                x3 = num[numHi - 2];
            }
            for (;;) {
                x1 = den[maxDen - 1] * quoEst;
                x2 = (tmp - (quoEst * den[maxDen])) * Base;
                if (x1 <= x2 + x3) {
                    break;
                }
                quoEst--;
            }

            // Try quo_est as the quotient digit, by multiplying the
            // denominator by quo_est and subtracting from the remaining
            // numerator. Keep in mind that quo_est is the i-1st digit.
            // Because we combine the multiply and subtract, borrow
            // can be more than 1.
            borrow = 0;
            for (int j = 0; j <= maxDen; j++) {
                tmp = num[i + j - 1] - (quoEst * den[j]) + borrow;
                num[i + j - 1] = Math.floorMod(tmp, Base);
                borrow = Math.floorDiv(tmp, Base);
            }

            // if quo_est was high by one, we need to correct things.
            if (-borrow > num[numHi]) {
                quoEst--;
                carry = 0;
                for (int j = 0; j <= maxDen; j++) {
                    tmp = num[i + j - 1] + den[j] + carry;
                    num[i + j - 1] = Math.floorMod(tmp, Base);
                    carry = Math.floorDiv(tmp, Base);
                }
                num[i + maxDen] += borrow + carry;
            }

            // store the quotient digit.
            q[i - 1] = ibyte(quoEst);
        }

        // finally, compute the remainder
        multiply(q, y, r);
        subtract(x, r, r);
    }

    /** Returns 'Word.LT (a, b)'. */
    public static boolean LT(int[] a, int[] b) {
        for (int i = LAST; i >= 0; i--) {
            if (a[i] < b[i]) {
                return true;
            } else if (a[i] > b[i]) {
                return false;
            }
        }
        return false;
    }

    /** Returns 'Word.LE (a, b)'. */
    public static boolean LE(int[] a, int[] b) {
        for (int i = LAST; i >= 0; i--) {
            if (a[i] < b[i]) {
                return true;
            } else if (a[i] > b[i]) {
                return false;
            }
        }
        return true;
    }

    /** Returns 'Word.GE (a, b)'. */
    public static boolean GE(int[] a, int[] b) {
        return LE(b, a);
    }

    /** Returns 'Word.GT (a, b)'. */
    public static boolean GT(int[] a, int[] b) {
        return LT(b, a);
    }

    /** Returns 'Word.And (a, b)'. */
    public static void and(int[] a, int[] b, int[] r) {
        for (int i = 0; i <= LAST; i++) {
            r[i] = a[i] & b[i];
        }
    }

    /** Returns 'Word.Or (a, b)'. */
    public static void or(int[] a, int[] b, int[] r) {
        for (int i = 0; i <= LAST; i++) {
            r[i] = a[i] | b[i];
        }
    }

    /** Returns 'Word.Xor (a, b)'. */
    public static void xor(int[] a, int[] b, int[] r) {
        for (int i = 0; i <= LAST; i++) {
            r[i] = a[i] ^ b[i];
        }
    }

    /** Returns 'Word.Not (a)'. */
    public static void not(int[] a, int[] r) {
        for (int i = 0; i <= LAST; i++) {
            r[i] = (int) (~a[i] & Mask);
        }
    }

    /** Returns 'Word.Shift (a, b)'. */
    public static void shift(int[] a, long b, int[] r) {
        // Sic: ABS (FIRST (INTEGER)) is FIRST (INTEGER) (Math.abs gives the
        // same), so that b reaches RightShift and is out of range there.
        if (Math.abs(b) >= Size) {
            TInt.assign(r, TInt.Zero);
        } else if (b > 0) {
            leftShift(a, b, r);
        } else {
            rightShift(a, -b, r);
        }
    }

    /** Returns 'Word.LeftShift (a, b)'. */
    public static void leftShift(int[] a, long b0, int[] r) {
        int b = (int) range(b0, 0, Size - 1);
        int w, i, j, z;
        long x1, x2;
        if (b == 0) { // no shift
            TInt.assign(r, a);
        } else {
            w = b / BITSIZE_IByte;
            i = b % BITSIZE_IByte;
            j = BITSIZE_IByte - i;
            for (int k = LAST; k >= 0; k--) {
                z = k - w;
                x1 = 0;
                x2 = 0;
                if (z >= 0) {
                    x1 = (long) a[z] << i;
                }
                if (z - 1 >= 0) {
                    x2 = (long) a[z - 1] >>> j;
                }
                r[k] = (int) ((x1 | x2) & Mask);
            }
        }
    }

    /** Returns 'Word.RightShift (a, b)'. */
    public static void rightShift(int[] a, long b0, int[] r) {
        int b = (int) range(b0, 0, Size - 1);
        int w, i, j, z;
        long x1, x2;
        if (b == 0) { // no shift
            TInt.assign(r, a);
        } else {
            w = b / BITSIZE_IByte;
            i = b % BITSIZE_IByte;
            j = BITSIZE_IByte - i;
            for (int k = 0; k <= LAST; k++) {
                z = k + w;
                x1 = 0;
                x2 = 0;
                if (z <= LAST) {
                    x1 = (long) a[z] >>> i;
                }
                if (z + 1 <= LAST) {
                    x2 = (long) a[z + 1] << j;
                }
                r[k] = (int) ((x1 | x2) & Mask);
            }

        }
    }

    /** Returns 'Word.Rotate (a, b)'. */
    public static void rotate(int[] a, long b, long n0, int[] r) {
        int n = (int) range(n0, 0, Integer.MAX_VALUE);
        int w, i, j;
        long z, x1, x2;
        int[] tmp = TInt.newInt();
        long size = (long) n * BITSIZE_IByte;
        b = Math.floorMod(b, size);

        if (b == 0) {
            TInt.assign(r, a);

        } else if (b > 0) { // left rotate
            w = (int) (b / BITSIZE_IByte);
            i = (int) (b % BITSIZE_IByte);
            j = BITSIZE_IByte - i;
            for (int k = 0; k <= n - 1; k++) {
                z = k - w;
                x1 = (long) a[Math.floorMod(z, n)] << i;
                x2 = (long) a[Math.floorMod(z - 1, n)] >>> j;
                tmp[k] = (int) ((x1 | x2) & Mask);
            }
            TInt.assign(r, tmp);

        } else { // right rotate
            // Sic: i is computed with DIV instead of MOD. The branch is not
            // reached: b MOD size is not negative.
            w = (int) ((-b) / BITSIZE_IByte);
            i = (int) ((-b) / BITSIZE_IByte);
            j = BITSIZE_IByte - i;
            for (int k = 0; k <= n - 1; k++) {
                z = k + w;
                x1 = (long) a[Math.floorMod(z, n)] >>> i;
                x2 = (long) a[Math.floorMod(z + 1, n)] << j;
                tmp[k] = (int) ((x1 | x2) & Mask);
            }
            TInt.assign(r, tmp);

        }
    }

    /** Returns 'Word.Extract (a, b, c)'. */
    public static boolean extract(int[] x, long i, long n, int[] r) {
        range(i, 0, Long.MAX_VALUE);
        range(n, 0, Long.MAX_VALUE);
        int w, b;
        if (i + n > Size) {
            return false;
        }

        shift(x, -i, r);

        // Sic: n = Size makes w = NUMBER (Int), a subscript out of range.
        w = (int) (n / BITSIZE_IByte);
        b = (int) (n % BITSIZE_IByte);
        r[w] = (int) (r[w] & (Mask >>> (BITSIZE_IByte - b)));
        for (int k = w + 1; k <= LAST; k++) {
            r[k] = 0;
        }

        return true;
    }

    /** Returns 'Word.Insert (a, b, c, d)'. */
    public static boolean insert(int[] x, int[] y, long i, long n, int[] r) {
        range(i, 0, Long.MAX_VALUE);
        range(n, 0, Long.MAX_VALUE);
        int[] yy = TInt.newInt();
        int[] yyy = TInt.newInt();
        int[] yyyy = TInt.newInt();
        if (i + n > Size) {
            return false;
        }

        shift(x, -(i + n), yy);
        shift(yy, n, r);

        shift(y, Size - n, yy);
        shift(yy, -(Size - n), yyy);
        or(r, yyy, r);
        shift(r, i, yyyy);
        TInt.assign(r, yyyy);

        shift(x, Size - i, yy);
        shift(yy, -(Size - i), yyy);
        or(r, yyy, r);

        return true;
    }

    /**
     * Truncates to the low-order 'n' bytes of 'i'.
     * Returns TRUE if 'i' has at most 'n' significant bytes, FALSE otherwise.
     */
    public static boolean truncate(int[] a, long n0, int[] r) {
        int n = (int) range(n0, 0, Integer.MAX_VALUE);
        boolean result = true;
        for (int i = 0; i <= n - 1; i++) {
            r[i] = a[i];
        }
        for (int i = n; i <= LAST; i++) {
            if (a[i] != 0) {
                result = false;
            }
            r[i] = 0;
        }
        return result;
    }

    /** Set rightmost n bits of i to ones, others zero. */
    public static void nBitsOnRight(long n, int[] i) {
        range(n, 0, Size - 1);
        long ln;
        int ss;
        TInt.assign(i, TInt.Zero);
        ln = n;
        ss = 0;
        for (;;) {
            if (ss > LAST) {
                return;
            } else if (ln <= 0) {
                return;
            } else if (ln >= 8) {
                i[ss] = 0xFF;
                ln -= 8;
                ss++;
            } else {
                // Word.Shift (16_FF, ln-8) with ln-8 < 0: a right shift.
                i[ss] = (int) (0xFFL >>> (8 - ln));
                return;
            }
        }
    }
}
