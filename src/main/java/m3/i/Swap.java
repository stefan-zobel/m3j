// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/** The interface Swap of m3core: byte order. The JVM's is little endian on AMD64. */
public final class Swap {
    private Swap() {}

    public static final boolean M3$PROVIDED = true;

    /** Endian = {Big, Little} */
    static final int LITTLE = 1;

    public static int endian = LITTLE;

    public static int GetEndian() {
        return LITTLE;
    }

    public static int Swap4(int i) {
        return Integer.reverseBytes(i);
    }

    public static int Swap2(int i) {
        return Short.reverseBytes((short) i);
    }

    public static int Swap2U(int i) {
        return Character.reverseBytes((char) i);
    }

    /**
     * Swap8 (READONLY i: Int64On32): Int64On32 =
     * Int64On32{a := Swap4(i.b); b := Swap4(i.a)}; the record is an object
     * of the program's class of the type, made and read by reflection.
     */
    public static Object Swap8(Object i) {
        try {
            Class<?> c = i.getClass();
            java.lang.reflect.Field a = c.getField("a");
            java.lang.reflect.Field b = c.getField("b");
            Object r = c.getConstructor().newInstance();
            long ia = ((Number) a.get(i)).longValue();
            long ib = ((Number) b.get(i)).longValue();
            set(a, r, Swap4((int) ib));
            set(b, r, Swap4((int) ia));
            return r;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("not a Swap.Int64On32", e);
        }
    }

    private static void set(java.lang.reflect.Field f, Object r, int v) throws IllegalAccessException {
        if (f.getType() == int.class) {
            f.setInt(r, v);
        } else {
            f.setLong(r, v);
        }
    }

    public static long SwapInt(long i) {
        return Long.reverseBytes(i);
    }
}
