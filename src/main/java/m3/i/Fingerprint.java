// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import m3j.rt.Rt;

/**
 * The interface Fingerprint of m3core: 64-bit checksums of texts. The
 * computation is that of m3j's port of cm3's Fingerprint
 * ({@link m3j.fingerprint.Fingerprint}); this class converts between it and
 * the record Fingerprint.T of a program.
 *
 * <p>The variable OfEmpty is not provided: a variable of a record type is a
 * field of the record's class, which only the program has.
 */
public final class Fingerprint {
    private Fingerprint() {}

    public static final boolean M3$PROVIDED = true;

    /** The class of Fingerprint.T, named after the UID of the type. */
    private static final String RECORD = "m3.t.T26466202";

    /** The class of Fingerprint.T, its field byte and its constructor, made on first use. */
    private record Shape(Class<?> c, Field bytes, Constructor<?> make) {}

    private static volatile Shape shape;

    public static Object FromText(String t) {
        if (t == null) {
            throw Rt.error(RuntimeError.BadMemoryReference);
        }
        return toRecord(m3j.fingerprint.Fingerprint.fromText(t));
    }

    public static Object Combine(Object fp1, Object fp2) {
        return toRecord(m3j.fingerprint.Fingerprint.combine(fromRecord(fp1), fromRecord(fp2)));
    }

    public static Object FromChars(byte[] buff, int off, int n, Object fp) {
        char[] c = new char[n];
        for (int i = 0; i < n; i++) {
            c[i] = (char) (buff[off + i] & 0xFF);
        }
        return toRecord(m3j.fingerprint.Fingerprint.fromChars(c, 0, n, fromRecord(fp)));
    }

    public static boolean Equal(Object fp1, Object fp2) {
        return fromRecord(fp1).equals(fromRecord(fp2));
    }

    public static long Hash(Object fp) {
        return m3j.fingerprint.Fingerprint.hash(fromRecord(fp));
    }

    private static Shape shape(Class<?> c) {
        Shape s = shape;
        if (s == null || (c != null && s.c != c)) {
            try {
                if (c == null) {
                    ClassLoader l = Rt.program != null ? Rt.program.getClassLoader()
                        : Fingerprint.class.getClassLoader();
                    c = Class.forName(RECORD, true, l);
                }
                s = new Shape(c, c.getField("byte"), c.getConstructor());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("no class of Fingerprint.T", e);
            }
            shape = s;
        }
        return s;
    }

    private static Object toRecord(m3j.fingerprint.Fingerprint.T v) {
        Shape s = shape(null);
        try {
            Object r = s.make.newInstance();
            byte[] b = (byte[]) s.bytes.get(r);
            for (int i = 0; i < 8; i++) {
                b[i] = (byte) v.byteAt(i);
            }
            return r;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no class of Fingerprint.T", e);
        }
    }

    private static m3j.fingerprint.Fingerprint.T fromRecord(Object fp) {
        Shape s = shape(fp.getClass());
        try {
            byte[] b = (byte[]) s.bytes.get(fp);
            long bits = 0;
            for (int i = 7; i >= 0; i--) {
                bits = (bits << 8) | (b[i] & 0xFF);
            }
            return new m3j.fingerprint.Fingerprint.T(bits);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("not a Fingerprint.T", e);
        }
    }
}
