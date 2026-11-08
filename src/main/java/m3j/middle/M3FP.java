// Java port of CM3's m3-sys/m3middle/src/M3FP.i3 and M3FP.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import m3j.fingerprint.Fingerprint;
import m3j.util.RangeError;

/**
 * Fingerprints as used by the compiler: M3FP.T is Fingerprint.T.
 *
 * <p>Port: Int (BITS 32 FOR [-16_7fffffff - 1 .. 16_7fffffff]) is a Java
 * {@code int}, INTEGER a {@code long}, and CharBuf (ARRAY
 * [0..2*BYTESIZE(T)-1] OF CHAR) a {@code char[16]}.
 */
public final class M3FP {
    private M3FP() {}

    /** BYTESIZE (T). */
    public static final int BYTESIZE_T = 8;

    /** NUMBER (CharBuf). */
    public static final int CharBufLength = 2 * BYTESIZE_T;

    public static final Fingerprint.T Zero = Fingerprint.Zero;

    public static final Fingerprint.T OfEmpty = Fingerprint.fromText("");

    /** NUMBER (NChars) = BYTESIZE (Int). */
    private static final int NCharsLength = 4;

    public static Fingerprint.T fromText(String t) {
        return Fingerprint.fromText(t);
    }

    public static Fingerprint.T fromChars(char[] buf, Fingerprint.T t) {
        return Fingerprint.fromChars(buf, t);
    }

    /** fromChars on SUBARRAY (buf, start, n). */
    public static Fingerprint.T fromChars(char[] buf, int start, int n, Fingerprint.T t) {
        return Fingerprint.fromChars(buf, start, n, t);
    }

    public static Fingerprint.T combine(Fingerprint.T a0, Fingerprint.T b0) {
        // Port: a and b are value parameters; the procedure changes its
        // copies.
        int[] a = a0.bytes();
        int[] b = b0.bytes();
        int tmp;
        tmp = a[0];
        a[0] = b[3];
        b[3] = b[2];
        b[2] = a[7];
        a[7] = a[4];
        a[4] = b[6];
        b[6] = b[4];
        b[4] = a[1];
        a[1] = a[5];
        a[5] = b[0];
        b[0] = b[7];
        b[7] = a[3];
        a[3] = a[6];
        a[6] = tmp;

        // Port: LOOPHOLE (b, MChars) is the bytes of b as characters.
        char[] mchars = new char[BYTESIZE_T];
        for (int i = 0; i < BYTESIZE_T; i++) {
            mchars[i] = (char) b[i];
        }
        return Fingerprint.fromChars(mchars, Fingerprint.T.ofBytes(a));
    }

    public static Fingerprint.T extendByInt(Fingerprint.T a, int i) {
        char[] buf = new char[NCharsLength];
        for (int x = 0; x < buf.length; x++) {
            buf[x] = (char) (i & 0xff);
            // Sic: Word.Shift shifts the sign-extended 64-bit word; for a
            // negative i the result is out of the range of Int, and the
            // assignment is a checked runtime error.
            if (i < 0) {
                throw new RangeError("M3FP");
            }
            i = i >>> 8;
        }
        return Fingerprint.fromChars(buf, a);
    }

    public static long toInt(Fingerprint.T t) {
        // Port: a and b hold the low and high four bytes as unsigned 32-bit
        // values; the original sign-extends the low 32 bits of their Xor.
        int a, b, c;
        a = (t.byteAt(0) | (t.byteAt(1) << 8))
            | ((t.byteAt(2) << 16) | (t.byteAt(3) << 24));
        b = (t.byteAt(4) | (t.byteAt(5) << 8))
            | ((t.byteAt(6) << 16) | (t.byteAt(7) << 24));
        c = a ^ b;
        return c;
    }

    /** Port: the original returns t in a VAR parameter. */
    public static Fingerprint.T fromInt(long a) {
        int[] t = new int[8];
        t[0] = 0;
        t[1] = 0;
        t[2] = 0;
        t[3] = 0;
        t[4] = (int) (a & 0xff);
        t[5] = (int) ((a >>> 8) & 0xff);
        t[6] = (int) ((a >>> 16) & 0xff);
        t[7] = (int) ((a >>> 24) & 0xff);
        return Fingerprint.T.ofBytes(t);
    }

    private static final int[] Map = { 3, 2, 1, 0, 7, 6, 5, 4 };
    private static final char[] Digit = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /** Fills buf[0..15]; the original takes buf as a VAR CharBuf. */
    public static void toChars(Fingerprint.T t, char[] buf) {
        if (buf.length != CharBufLength) {
            throw new IllegalArgumentException("M3FP.CharBuf has 16 characters");
        }
        int j = 0;
        int k;
        for (int i = 0; i < BYTESIZE_T; i++) {
            k = t.byteAt(Map[i]);
            buf[j] = Digit[k / 16];  j++;
            buf[j] = Digit[k % 16];  j++;
        }
    }
}
