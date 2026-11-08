// Java port of CM3's m3-libs/m3core/src/fingerprint/Poly.i3 and Poly.m3.
// Original: Copyright (C) 1989, 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.fingerprint;

import static m3j.fingerprint.PolyBasis.poly64;
import static m3j.fingerprint.PolyBasis.poly72;
import static m3j.fingerprint.PolyBasis.poly80;
import static m3j.fingerprint.PolyBasis.poly88;
import static m3j.fingerprint.PolyBasis.power;
import static m3j.fingerprint.PolyBasis.power8;
import static m3j.fingerprint.PolyBasis.power16;
import static m3j.fingerprint.PolyBasis.power24;

import m3j.util.RangeError;

/**
 * Polynomials of degree [0..63] with coefficients in the field Z(2).
 *
 * <p>A polynomial {@code T} is an {@code int[2]} (ARRAY [0..1] OF Int32);
 * {@code Bytes} is an {@code int[8]} of values in [0..255]. Procedures
 * never modify their arguments and always return fresh arrays; the
 * constants {@link #ZERO}, {@link #ONE} and {@link #X} must not be
 * modified.
 *
 * <p>The coefficients of a polynominal are in reverse (VAX) order:
 * <pre>
 *    P(x) = x^64 + c[0]*x^63 + c[1]*x^62 + c[2]*x^61 + ... + c[62]*x + c[63]
 * </pre>
 * The leading coefficient is not stored in the 64 bit representation of the
 * basis polynomial. All other polynomials are residues MOD the basis, hence
 * they don't have an x^64 term.
 *
 * <p>Here's the scoop:
 * <pre>
 *     Most Significant -- Least Significant
 *        c[0]         ....   c[63]
 *         b0          ....    b7
 *         i0          ....    i1
 * </pre>
 *
 * <p>Port: Int32 values are Java {@code int}s, and Word operations on them
 * are done in 32-bit arithmetic. Where the original computes on 64-bit
 * words, the comments say why only the low 32 bits matter. Memory given as
 * ADDRESS and length is a {@code byte[]} with offset and length. The JVM
 * port reads words in little-endian order, as the original does on
 * AMD64_NT, so the byte order is not probed.
 */
public final class Poly {
    private Poly() {}

    public static final int[] ZERO = { 0, 0 };
    public static final int[] ONE = { 0, Integer.MIN_VALUE };
    public static final int[] X = { 0, 0x40000000 };

    /** Mask to grab 32 significant bits. */
    private static final long SigBits = 0xffffffffL;

    // Port, omitted: the variables init_done, little_endian and big_endian.

    /** Returns (p+q). */
    public static int[] sum(int[] p, int[] q) {
        int[] r = new int[2];
        r[0] = p[0] ^ q[0];
        r[1] = p[1] ^ q[1];
        return r;
    }

    /** Returns (p * q MOD PolyBasis.P). */
    public static int[] product(int[] p, int[] q) {
        int[] temp = new int[4];
        int[] accum;
        int z;

        // zero the temporaries
        temp[0] = 0;  temp[1] = 0;  temp[2] = 0;  temp[3] = 0;
        accum = temp.clone();

        // form the 128 bit product in accum
        temp[2] = p[0];  temp[3] = p[1];
        for (int j = 1; j >= 0; j--) {
            z = q[j];
            for (int i = 31; i >= 0; i--) {
                if ((z & (1 << i)) != 0) {
                    doubleINC(accum, temp);
                }
                doubleTimesX(temp);
            }
        }

        // collapse and return the accumulated result MOD P
        // Port: ADR (accum) is the bytes of the four Int32s in
        // little-endian order.
        byte[] bytes = new byte[16];
        for (int k = 0; k < 4; k++) {
            putInt32(bytes, 4 * k, accum[k]);
        }
        return computeMod(ZERO, bytes, 0, bytes.length);
    }

    /**
     * This procedure assumes that the 'len' bytes beginning at offset 'off'
     * of 'buf' define a polynomial, A(x), of degree '(8*len)'.
     * The procedure returns '(init*x^(8*len) + A(x)) MOD PolyBasis.P'.
     */
    public static int[] computeMod(int[] t, byte[] buf, int off, int len) {
        int j, k;
        int[] result = t;

        // Port: the original calls FindByteOrder here.

        // word align the source pointer
        // Port: the offset takes the place of the address. The result does
        // not depend on where the words start.
        j = off % 4;
        if ((len >= 4) && (j != 0)) {
            j = 4 - j;
            result = extendBytes(result, buf, off, j);
            off += j;
            len -= j;
        }

        // compute the bulk of the result a word at a time
        if (len >= 4) {
            j = len % 4;
            k = len - j;
            result = extendWords_LE(result, buf, off, k);
            off += k;
            len = j;
        }

        // finish up the last few bytes
        if (len > 0) {
            result = extendBytes(result, buf, off, len);
        }

        return result == t ? t.clone() : result;
    }

    /** Return the sign-extended bottom 32 bits of 'x'. */
    static int fix32(long x) {
        return (int) x;
    }

    private static int[] extendBytes(int[] t, byte[] buf, int cp, int len) {
        int n_bits = 8 * len;
        int x_bits = 32 - n_bits;
        // Port: t0 and t1 are the low 32 bits of t[0] and t[1]. The left
        // shifts are done on 64-bit words in the original, but only the low
        // 32 bits of their results are used.
        int t0 = t[0];
        int t0_x = t0 << x_bits;
        int t0_n = t0 >>> n_bits;
        int t1 = t[1];
        int t1_x = t1 << x_bits;
        int t1_n = t1 >>> n_bits;
        byte[] tmp_b = new byte[4];
        int[] result = new int[2];
        result[0] = fix32(t0_x);
        result[1] = fix32(t0_n ^ t1_x);
        switch (len) {
            case 1 -> {
                tmp_b[0] = (byte) extract(t1_n, 0, 8);
                tmp_b[1] = (byte) extract(t1_n, 8, 8);
                tmp_b[2] = (byte) extract(t1_n, 16, 8);
                tmp_b[3] = buf[cp];
            }
            case 2 -> {
                tmp_b[0] = (byte) extract(t1_n, 0, 8);
                tmp_b[1] = (byte) extract(t1_n, 8, 8);
                tmp_b[2] = buf[cp];
                tmp_b[3] = buf[cp + 1];
            }
            case 3 -> {
                tmp_b[0] = (byte) extract(t1_n, 0, 8);
                tmp_b[1] = buf[cp];
                tmp_b[2] = buf[cp + 1];
                tmp_b[3] = buf[cp + 2];
            }
            default -> throw new AssertionError(len);
        }
        return extendWords_LE(result, tmp_b, 0, tmp_b.length);
    }

    private static int[] extendWords_LE(int[] p, byte[] source, int ip, int len) {
        int p0 = p[0];
        int p1 = p[1];
        while (len > 0) {
            // split the low-order bytes
            int tmp_b0 = extract(p0, 0, 8);
            int tmp_b1 = extract(p0, 8, 8);
            int tmp_b2 = extract(p0, 16, 8);
            int tmp_b3 = extract(p0, 24, 8);

            // compute the new result
            p0 = p1 ^ ((poly88[tmp_b0][0] ^ poly80[tmp_b1][0])
                       ^ (poly72[tmp_b2][0] ^ poly64[tmp_b3][0]));
            p1 = getInt32(source, ip) ^ ((poly88[tmp_b0][1] ^ poly80[tmp_b1][1])
                                         ^ (poly72[tmp_b2][1] ^ poly64[tmp_b3][1]));
            len -= 4;
            ip += 4;
        }
        return new int[] { p0, p1 };
    }

    // Port, omitted: ExtendWords_BE, the variant for big-endian targets.

    /** Returns x^d MOD PolyBasis.P. */
    public static int[] power(int d) {
        if (d < 0) {
            throw new IllegalArgumentException("Poly.Power: d is not a Card32: " + d);
        }
        int i;
        int b0, b1, b2, b3;
        // Port: the bytes of 'd', least significant first, as on a
        // little-endian target.
        b0 = extract(d, 0, 8);
        b1 = extract(d, 8, 8);
        b2 = extract(d, 16, 8);
        b3 = extract(d, 24, 8);

        // find the non-zero bytes of 'd'
        i = 0;
        if (b0 != 0) { i = 1; }
        if (b1 != 0) { i += 2; }
        if (b2 != 0) { i += 4; }
        if (b3 != 0) { i += 8; }

        return switch (i) {
            case 0 -> ONE.clone();
            case 1 -> power[b0].clone();
            case 2 -> power8[b1].clone();
            case 3 -> product(power8[b1], power[b0]);
            case 4 -> power16[b2].clone();
            case 5 -> product(power16[b2], power[b0]);
            case 6 -> product(power16[b2], power8[b1]);
            case 7 -> product(power16[b2], product(power8[b1], power[b0]));
            case 8 -> power24[b3].clone();
            case 9 -> product(power24[b3], power[b0]);
            case 10 -> product(power24[b3], power8[b1]);
            case 11 -> product(power24[b3], product(power8[b1], power[b0]));
            case 12 -> product(power24[b3], power16[b2]);
            case 13 -> product(power24[b3], product(power16[b2], power[b0]));
            case 14 -> product(power24[b3], product(power16[b2], power8[b1]));
            case 15 -> product(product(power24[b3], power16[b2]),
                               product(power8[b1], power[b0]));
            default -> throw new AssertionError(i);
        };
    }

    /** Return (p * X^1). */
    public static int[] timesX(int[] p) {
        int[] result = new int[2];
        result[0] = rightShift1(p[0]);
        if ((p[1] & 1) != 0) {
            result[0] = result[0] | Integer.MIN_VALUE;
        }
        result[1] = rightShift1(p[1]);
        return result;
    }

    /** a := a + b. */
    private static void doubleINC(int[] a, int[] b) {
        a[0] = a[0] ^ b[0];
        a[1] = a[1] ^ b[1];
        a[2] = a[2] ^ b[2];
        a[3] = a[3] ^ b[3];
    }

    /** a := a*x. */
    private static void doubleTimesX(int[] a) {
        a[0] = rightShift1(a[0]);
        if ((a[1] & 1) != 0) { a[0] = a[0] | Integer.MIN_VALUE; }
        a[1] = rightShift1(a[1]);
        if ((a[2] & 1) != 0) { a[1] = a[1] | Integer.MIN_VALUE; }
        a[2] = rightShift1(a[2]);
        if ((a[3] & 1) != 0) { a[2] = a[2] | Integer.MIN_VALUE; }
        a[3] = rightShift1(a[3]);
    }

    /**
     * An Int32 variable := Word.RightShift (x, 1).
     *
     * <p>Sic: on a 64-bit target Word.RightShift shifts the sign-extended
     * 64-bit word, so for x &lt; 0 the result is out of the range of Int32
     * and the assignment is a checked runtime error. Hence Product (and
     * Power for d &gt;= 256) fail on AMD64 whenever a negative Int32 is
     * shifted. The port raises the same error.
     */
    private static int rightShift1(int x) {
        if (x < 0) {
            throw new RangeError("Poly");
        }
        return x >>> 1;
    }

    // Port, omitted: LSL, LSR and ByteSwap, which are commented out in the
    // original, and FindByteOrder.

    /** Generate the bytes in little-endian order. */
    public static int[] toBytes(int[] t) {
        int[] b = new int[8];
        b[0] = extract(t[0], 0, 8);
        b[1] = extract(t[0], 8, 8);
        b[2] = extract(t[0], 16, 8);
        b[3] = extract(t[0], 24, 8);
        b[4] = extract(t[1], 0, 8);
        b[5] = extract(t[1], 8, 8);
        b[6] = extract(t[1], 16, 8);
        b[7] = extract(t[1], 24, 8);
        return b;
    }

    /** Assume the bytes are in little-endian order. */
    public static int[] fromBytes(int[] b) {
        int[] t = new int[2];
        t[0] = fix32((b[0] | (b[1] << 8)) | ((b[2] << 16) | (b[3] << 24)));
        t[1] = fix32((b[4] | (b[5] << 8)) | ((b[6] << 16) | (b[7] << 24)));
        return t;
    }

    // Helpers of the port.

    /** Word.Extract (x, i, n) for the low 32 bits of a word. */
    private static int extract(int x, int i, int n) {
        return (x >>> i) & ((1 << n) - 1);
    }

    /** The Int32 at buf[off..off+3], little-endian. */
    private static int getInt32(byte[] buf, int off) {
        return (buf[off] & 0xff)
            | (buf[off + 1] & 0xff) << 8
            | (buf[off + 2] & 0xff) << 16
            | (buf[off + 3] & 0xff) << 24;
    }

    private static void putInt32(byte[] buf, int off, int x) {
        buf[off] = (byte) x;
        buf[off + 1] = (byte) (x >>> 8);
        buf[off + 2] = (byte) (x >>> 16);
        buf[off + 3] = (byte) (x >>> 24);
    }
}
