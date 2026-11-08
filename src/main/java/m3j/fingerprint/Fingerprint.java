// Java port of CM3's m3-libs/m3core/src/fingerprint/Fingerprint.i3 and
// Fingerprint.m3.
// Original: Copyright (C) 1992, 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.fingerprint;

/**
 * A {@code Fingerprint.T} is a 64-bit checksum. This class provides
 * procedures that can be used to fingerprint text strings or more general
 * data structures, such as graphs.
 *
 * <p>The interface is based on the original idea of M. O. Rabin, as refined
 * by Andrei Broder. The probabilistic guarantee, example applications and
 * pitfalls are described at the end of the original Fingerprint.i3.
 *
 * <p>Internally a fingerprint is a polynomial of degree 64 over Z[2]
 * (see {@link Poly}).
 */
public final class Fingerprint {
    private Fingerprint() {}

    public static final String Brand = "Fingerprint";

    /**
     * RECORD byte: ARRAY [0..7] OF BITS 8 FOR [0..255] END.
     *
     * <p>Port: the eight bytes are packed into a {@code long}, byte[i] in
     * bits 8*i .. 8*i+7. A {@code T} is an immutable value; {@code equals}
     * is the Modula-3 "=" on the record.
     */
    public record T(long bits) {
        /** byte[i], in [0..255]. */
        public int byteAt(int i) {
            if (i < 0 || i > 7) {
                throw new IndexOutOfBoundsException(i);
            }
            return (int) (bits >>> (8 * i)) & 0xff;
        }

        /** A copy of byte[0..7]. */
        public int[] bytes() {
            int[] b = new int[8];
            for (int i = 0; i < 8; i++) {
                b[i] = byteAt(i);
            }
            return b;
        }

        /** The fingerprint with the given byte[0..7], each in [0..255]. */
        public static T ofBytes(int[] b) {
            if (b.length != 8) {
                throw new IllegalArgumentException("Fingerprint.T needs 8 bytes");
            }
            long bits = 0;
            for (int i = 7; i >= 0; i--) {
                if (b[i] < 0 || b[i] > 255) {
                    throw new IllegalArgumentException("not a byte: " + b[i]);
                }
                bits = (bits << 8) | b[i];
            }
            return new T(bits);
        }
    }

    public static final T Zero = new T(0);

    /** The fingerprint of the empty text. */
    public static final T OfEmpty = T.ofBytes(Poly.toBytes(Poly.ONE));

    /** Returns the fingerprint of t. */
    public static T fromText(String t) {
        int[] poly = Poly.ONE;
        int len = t.length();
        int offset = 0;
        byte[] buf = new byte[256];
        while (offset < len) {
            setChars(buf, t, offset);
            poly = Poly.computeMod(poly, buf, 0, Math.min(len - offset, buf.length));
            offset += buf.length;
        }
        return T.ofBytes(Poly.toBytes(poly));
    }

    /**
     * Return the fingerprint of "t &amp; Text.FromChars(buff)", where "t" is
     * the text whose fingerprint is "fp".
     */
    public static T fromChars(char[] buff, T fp) {
        return fromChars(buff, 0, buff.length, fp);
    }

    /**
     * fromChars on SUBARRAY (buff, start, n).
     *
     * <p>Port: Modula-3 passes a subarray as an open array; the port passes
     * the array, the start and the number of elements.
     */
    public static T fromChars(char[] buff, int start, int n, T fp) {
        if (n <= 0) {
            return fp;
        }
        byte[] bytes = new byte[n];
        for (int i = 0; i < n; i++) {
            bytes[i] = charByte(buff[start + i]);
        }
        int[] init = Poly.fromBytes(fp.bytes());
        int[] poly = Poly.computeMod(init, bytes, 0, n);
        return T.ofBytes(Poly.toBytes(poly));
    }

    private static final int A = 0xff208489;
    private static final int B = 0xf4872e10;
    private static final int C = 0x402d619b;
    private static final int D = 0x0bf359a7;

    private static final int[] Perm = {
        255, 254, 252, 251, 250, 248, 240, 245, 246, 238, 237, 244, 7, 189,
        214, 236, 235, 20, 33, 8, 227, 14, 233, 178, 172, 60, 229, 133, 152,
        19, 210, 203, 221, 208, 76, 18, 13, 199, 113, 62, 40, 190, 213, 194,
        43, 181, 21, 15, 201, 162, 90, 186, 71, 117, 107, 70, 191, 5, 173, 44,
        39, 12, 174, 183, 99, 11, 176, 163, 161, 72, 86, 105, 2, 83, 42, 52,
        179, 135, 103, 110, 151, 58, 108, 96, 166, 25, 115, 66, 142, 10, 141,
        48, 104, 34, 159, 120, 22, 140, 64, 82, 78, 68, 207, 125, 123, 150,
        144, 138, 128, 139, 136, 114, 119, 53, 148, 185, 41, 124, 216, 143,
        49, 92, 98, 51, 112, 73, 50, 63, 16, 46, 158, 126, 206, 122, 94, 132,
        88, 184, 28, 84, 127, 156, 167, 223, 118, 89, 116, 17, 111, 121, 109,
        77, 146, 61, 224, 101, 81, 218, 97, 188, 243, 155, 57, 102, 54, 129,
        93, 192, 153, 106, 36, 145, 79, 31, 137, 26, 67, 85, 175, 80, 168, 65,
        91, 1, 147, 149, 6, 29, 37, 69, 182, 165, 4, 74, 55, 47, 171, 169, 75,
        134, 193, 195, 198, 131, 38, 180, 56, 196, 23, 154, 177, 200, 205, 27,
        209, 95, 204, 160, 3, 30, 157, 32, 9, 212, 211, 45, 202, 170, 0, 219,
        187, 87, 35, 100, 217, 232, 164, 228, 220, 197, 231, 215, 226, 130,
        225, 234, 241, 239, 59, 230, 247, 24, 249, 242, 222, 253
    };

    /** Return the fingerprint of the ordered pair "(fp1, fp2)". */
    public static T combine(T fp1, T fp2) {
        int[] poly1;
        int[] poly2 = new int[2];
        // Port: buf is ARRAY [0..1] OF T; its bytes are those of fp1
        // followed by those of fp2.
        byte[] buf = new byte[16];
        for (int i = 0; i < 8; i++) {
            buf[i] = (byte) fp1.byteAt(i);
            buf[8 + i] = (byte) fp2.byteAt(i);
        }
        poly1 = Poly.computeMod(Poly.ONE, buf, 0, buf.length);

        // Port: Word.Plus and Word.Times work on 64-bit words in the
        // original; Fix32 keeps the low 32 bits, which 32-bit arithmetic
        // computes alike.
        poly2[0] = fix32(poly1[0] * A + poly1[1] * B);
        poly2[1] = fix32(poly1[0] * C + poly1[1] * D);
        int[] res = Poly.toBytes(poly2);

        for (int i = 0; i < res.length; i++) {
            res[i] = Perm[res[i]];
        }
        return T.ofBytes(res);
    }

    /** Return the sign-extended bottom 32 bits of 'x'. */
    private static int fix32(int x) {
        return x;
    }

    /** Return "fp1 = fp2". */
    public static boolean equal(T fp1, T fp2) {
        return fp1.equals(fp2);
    }

    /** Return a hash code for "fp". */
    public static long hash(T fp) {
        int[] x = Poly.fromBytes(fp.bytes());
        // Port: the Xor of the sign-extended Int32s, as a 64-bit INTEGER.
        return (long) (x[0] ^ x[1]);
    }

    // Helpers of the port.

    /**
     * Text.SetChars (buf, t, start): for each i from 0 to
     * MIN(LAST(buf), Length(t)-start-1), set buf[i] to GetChar(t, i+start).
     */
    private static void setChars(byte[] buf, String t, int start) {
        int n = Math.min(buf.length, t.length() - start);
        for (int i = 0; i < n; i++) {
            buf[i] = charByte(t.charAt(start + i));
        }
    }

    /**
     * The value of a CHAR. Text.GetChar makes it a checked runtime error
     * if the character value is not in CHAR.
     */
    private static byte charByte(char c) {
        if (c > 255) {
            throw new IllegalArgumentException(
                "character value is not in CHAR: U+" + Integer.toHexString(c));
        }
        return (byte) c;
    }
}
