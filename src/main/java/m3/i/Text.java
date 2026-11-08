// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import m3j.rt.Rt;

/**
 * The interface Text of m3core. A TEXT is a java.lang.String; its
 * characters are CHAR (0..255) or WIDECHAR (0..65535) values.
 */
public final class Text {
    private Text() {}

    public static final boolean M3$PROVIDED = true;

    private static void nil(String t) {
        if (t == null) {
            throw Rt.error(RuntimeError.BadMemoryReference);
        }
    }

    public static long Length(String t) {
        nil(t);
        return t.length();
    }

    public static boolean Empty(String t) {
        nil(t);
        return t.isEmpty();
    }

    public static boolean Equal(String t, String u) {
        nil(t);
        nil(u);
        return t.equals(u);
    }

    public static int Compare(String t1, String t2) {
        nil(t1);
        nil(t2);
        int c = t1.compareTo(t2);
        return c < 0 ? -1 : c > 0 ? 1 : 0;
    }

    public static String Cat(String t, String u) {
        nil(t);
        nil(u);
        return t.concat(u);
    }

    public static String Sub(String t, long start, long length) {
        nil(t);
        long n = t.length();
        if (start >= n || length <= 0) {
            return "";
        }
        long end = java.lang.Math.min(n, start + java.lang.Math.min(length, n));
        return t.substring((int) start, (int) end);
    }

    /**
     * As cm3's Text.Hash: starting with the length, each character is
     * xor-ed into the value rotated left by 13 bits. A program sees the
     * value, and the order in which a table of texts enumerates its keys
     * depends on it.
     */
    public static long Hash(String t) {
        nil(t);
        int n = t.length();
        long h = n;
        for (int i = 0; i < n; i++) {
            h = Long.rotateLeft(h, 13) ^ t.charAt(i);
        }
        return h;
    }

    public static boolean HasWideChars(String t) {
        nil(t);
        for (int i = 0, n = t.length(); i < n; i++) {
            if (t.charAt(i) > 0xFF) {
                return true;
            }
        }
        return false;
    }

    public static char GetChar(String t, long i) {
        nil(t);
        if (i >= t.length()) {
            throw Rt.error(RuntimeError.SubscriptOutOfRange);
        }
        char c = t.charAt((int) i);
        if (c > 0xFF) {
            throw Rt.error(RuntimeError.ValueOutOfRange);
        }
        return c;
    }

    public static char GetWideChar(String t, long i) {
        nil(t);
        if (i >= t.length()) {
            throw Rt.error(RuntimeError.SubscriptOutOfRange);
        }
        return t.charAt((int) i);
    }

    public static String FromChar(char ch) {
        return String.valueOf(ch);
    }

    public static String FromWideChar(char ch) {
        return String.valueOf(ch);
    }

    public static long FindChar(String t, char c, long start) {
        nil(t);
        if (start < 0) {
            start = 0;
        }
        if (start >= t.length()) {
            return -1;
        }
        return t.indexOf(c, (int) start);
    }

    public static long FindWideChar(String t, char c, long start) {
        return FindChar(t, c, start);
    }

    public static long FindCharR(String t, char c, long start) {
        nil(t);
        if (start < 0) {
            return -1;
        }
        int from = (int) java.lang.Math.min(start, t.length() - 1L);
        return t.lastIndexOf(c, from);
    }

    public static long FindWideCharR(String t, char c, long start) {
        return FindCharR(t, c, start);
    }

    public static String FromChars(byte[] a, int off, int n) {
        return new String(a, off, n, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    public static String FromWideChars(char[] a, int off, int n) {
        return new String(a, off, n);
    }

    /** Copies the characters of t from start into a; as many as fit. */
    public static void SetChars(byte[] a, int off, int n, String t, long start) {
        nil(t);
        for (int i = 0; i < n && start + i < t.length(); i++) {
            char c = t.charAt((int) (start + i));
            if (c > 0xFF) {
                throw Rt.error(RuntimeError.ValueOutOfRange);
            }
            a[off + i] = (byte) c;
        }
    }

    public static void SetWideChars(char[] a, int off, int n, String t, long start) {
        nil(t);
        for (int i = 0; i < n && start + i < t.length(); i++) {
            a[off + i] = t.charAt((int) (start + i));
        }
    }

    private static final ClassValue<MethodHandle> visitString = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> c) {
            return Rt.procHandle(c, MethodType.methodType(void.class, byte[].class,
                                                          int.class, int.class));
        }
    };

    private static final ClassValue<MethodHandle> visitWideString = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> c) {
            return Rt.procHandle(c, MethodType.methodType(void.class, char[].class,
                                                          int.class, int.class));
        }
    };

    /**
     * Calls visitString with the characters of t if they are all CHAR
     * values, else visitWideString. A text is one flat string here, so
     * there is one call (cm3 calls once for each piece of a concatenation
     * or substring). As in cm3, an empty text gives a call of visitString
     * with no characters, and NIL no call.
     */
    public static void ForAllDo(String t, Object visitString, Object visitWideString) {
        if (t == null) {
            return;
        }
        int n = t.length();
        try {
            if (!HasWideChars(t)) {
                byte[] a = new byte[n];
                for (int i = 0; i < n; i++) {
                    a[i] = (byte) t.charAt(i);
                }
                proc(visitString);
                Text.visitString.get(visitString.getClass()).invoke(visitString, a, 0, n);
            } else {
                proc(visitWideString);
                Text.visitWideString.get(visitWideString.getClass())
                    .invoke(visitWideString, t.toCharArray(), 0, n);
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private static void proc(Object p) {
        if (p == null) {
            throw Rt.error(RuntimeError.BadMemoryReference);
        }
    }
}
