// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

import m3j.rt.Rt;

/**
 * The interface RTIO of m3core: unsynchronized, buffered output to the
 * standard error stream for the runtime, flushed explicitly.
 */
public final class RTIO {
    private RTIO() {}

    public static final boolean M3$PROVIDED = true;

    private static final byte[] buf = new byte[4096];
    private static int len;
    private static final FileOutputStream err = new FileOutputStream(FileDescriptor.err);
    /** PutE, PutF and PutG write to the standard output, as printf in cm3. */
    private static final FileOutputStream out = new FileOutputStream(FileDescriptor.out);

    static {
        Rt.atExit(RTIO::Flush);
    }

    public static void PutChar(char c) {
        if (len >= buf.length) {
            Flush();
        }
        buf[len++] = (byte) c;
    }

    public static void PutText(String t) {
        for (int i = 0; i < t.length(); i++) {
            PutChar(Text.GetChar(t, i));
        }
    }

    private static void pad(int n, long width) {
        for (long i = 1; i <= width - n; i++) {
            PutChar(' ');
        }
    }

    private static void put(String s) {
        for (int i = 0; i < s.length(); i++) {
            PutChar(s.charAt(i));
        }
    }

    public static void PutInt(long i, long width) {
        String s = Long.toString(i);
        pad(s.length(), width);
        put(s);
    }

    public static String FmtHex(long i) {
        return "0x" + Long.toHexString(i);
    }

    public static void PutHex(long i, long width) {
        String s = FmtHex(i);
        pad(s.length(), width);
        put(s);
    }

    /** A reference: there are no addresses, so an identity hash stands for it (NIL is 0x0). */
    public static String FmtRef(Object a) {
        return FmtHex(a == null ? 0 : System.identityHashCode(a));
    }

    public static void PutRef(Object a) {
        put(FmtRef(a));
    }

    /** An address: a safe program has no address but NIL (0x0). */
    public static String FmtAddr(Object a) {
        return FmtRef(a);
    }

    public static void PutAddr(Object a, long width) {
        String s = FmtAddr(a);
        pad(s.length(), width);
        put(s);
    }

    /** The memory at an address cannot be read: n > 0 characters are a bad memory reference. */
    public static void PutChars(Object a, long n) {
        if (n > 0) {
            throw Rt.error(RuntimeError.BadMemoryReference);
        }
    }

    /** The string at an address: nothing for NIL, as in cm3. */
    public static void PutString(Object s) {
        if (s != null) {
            throw Rt.error(RuntimeError.BadMemoryReference);
        }
    }

    /** The bytes at an address, in hexadecimal: none for count <= 0. */
    public static void PutBytes(Object a, long count) {
        Flush();
        if (count > 0) {
            throw Rt.error(RuntimeError.BadMemoryReference);
        }
    }

    /** printf ("%e", a) */
    public static void PutE(double a) {
        printf(cE(a, 6));
    }

    /** printf ("%f", a) */
    public static void PutF(double a) {
        printf(cF(a, 6));
    }

    /** printf ("%g", a) */
    public static void PutG(double a) {
        printf(cG(a, 6));
    }

    private static void printf(String s) {
        Flush();
        try {
            out.write(s.getBytes(StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            // ignore, as cm3
        }
    }

    private static boolean negative(double a) {
        return a < 0 || (a == 0 && 1 / a < 0);
    }

    /** inf, -inf or nan as C prints them, else null. */
    private static String special(double a) {
        if (Double.isNaN(a)) {
            return "nan";
        }
        if (Double.isInfinite(a)) {
            return a < 0 ? "-inf" : "inf";
        }
        return null;
    }

    /** C's %.{p}f: the exact value rounded half to even. */
    static String cF(double a, int p) {
        String s = special(a);
        if (s != null) {
            return s;
        }
        String r = new BigDecimal(java.lang.Math.abs(a)).setScale(p, RoundingMode.HALF_EVEN).toPlainString();
        return negative(a) ? "-" + r : r;
    }

    /** The digits of |a| rounded to n significant ones, and the exponent of the first. */
    private record Sci(String digits, int exp) {}

    private static Sci sci(double a, int n) {
        BigDecimal x = new BigDecimal(java.lang.Math.abs(a));
        if (x.signum() == 0) {
            return new Sci("0".repeat(n), 0);
        }
        BigDecimal r = x.round(new MathContext(n, RoundingMode.HALF_EVEN));
        String u = r.unscaledValue().toString();
        int exp = u.length() - 1 - r.scale();
        if (u.length() < n) {
            u = u + "0".repeat(n - u.length());
        } else if (u.length() > n) {
            u = u.substring(0, n);
        }
        return new Sci(u, exp);
    }

    /** C's %.{p}e. */
    static String cE(double a, int p) {
        String s = special(a);
        if (s != null) {
            return s;
        }
        Sci x = sci(a, p + 1);
        StringBuilder b = new StringBuilder();
        if (negative(a)) {
            b.append('-');
        }
        b.append(x.digits().charAt(0));
        if (p > 0) {
            b.append('.').append(x.digits(), 1, p + 1);
        }
        int e = x.exp();
        b.append(e < 0 ? "e-" : "e+");
        int m = java.lang.Math.abs(e);
        if (m < 10) {
            b.append('0');
        }
        b.append(m);
        return b.toString();
    }

    /** C's %.{p}g: %e or %f after the exponent, without trailing zeros. */
    static String cG(double a, int p) {
        String s = special(a);
        if (s != null) {
            return s;
        }
        if (p == 0) {
            p = 1;
        }
        int x = sci(a, p).exp();
        String r;
        if (p > x && x >= -4) {
            r = strip(cF(a, p - 1 - x));
        } else {
            String e = cE(a, p - 1);
            int i = e.indexOf('e');
            r = strip(e.substring(0, i)) + e.substring(i);
        }
        return r;
    }

    /** s without the trailing zeros of its fraction, and without a final point. */
    private static String strip(String s) {
        if (s.indexOf('.') < 0) {
            return s;
        }
        int n = s.length();
        while (s.charAt(n - 1) == '0') {
            n--;
        }
        if (s.charAt(n - 1) == '.') {
            n--;
        }
        return s.substring(0, n);
    }

    public static void PutLong(long i) {
        put(Long.toString(i));
    }

    public static void PutLongHex(long i) {
        put(FmtHex(i));
    }

    public static void Flush() {
        if (len > 0) {
            try {
                err.write(buf, 0, len);
            } catch (IOException e) {
                // ignore, as cm3
            }
            len = 0;
        }
    }
}
