// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The bootstrap of constant arrays: the compiler writes the elements of a
 * constant array of numbers, characters or booleans into strings (each
 * char a byte plus 1, little endian, as many bytes per element as its
 * storage has), and a dynamic constant makes the array once.
 */
public final class Consts {
    private Consts() {}

    public static Object array(MethodHandles.Lookup lookup, String name, Class<?> type, String... data) {
        int len = 0;
        for (String s : data) {
            len += s.length();
        }
        ByteBuffer b = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);
        for (String s : data) {
            for (int i = 0; i < s.length(); i++) {
                b.put((byte) (s.charAt(i) - 1));
            }
        }
        b.flip();
        Class<?> c = type.getComponentType();
        if (c == boolean.class) {
            boolean[] r = new boolean[len];
            for (int i = 0; i < len; i++) {
                r[i] = b.get(i) != 0;
            }
            return r;
        } else if (c == byte.class) {
            byte[] r = new byte[len];
            b.get(r);
            return r;
        } else if (c == short.class) {
            short[] r = new short[len / 2];
            b.asShortBuffer().get(r);
            return r;
        } else if (c == char.class) {
            char[] r = new char[len / 2];
            b.asCharBuffer().get(r);
            return r;
        } else if (c == int.class) {
            int[] r = new int[len / 4];
            b.asIntBuffer().get(r);
            return r;
        } else if (c == float.class) {
            float[] r = new float[len / 4];
            b.asFloatBuffer().get(r);
            return r;
        } else if (c == long.class) {
            long[] r = new long[len / 8];
            b.asLongBuffer().get(r);
            return r;
        } else if (c == double.class) {
            double[] r = new double[len / 8];
            b.asDoubleBuffer().get(r);
            return r;
        }
        throw new IllegalArgumentException(type.getName());
    }

    /**
     * Repeats the block a[src .. src+stride-1] count times after itself (the
     * elements "..": of a constructor); records are copied into their objects.
     */
    public static void repeat(Object a, int src, int stride, int count, boolean records) {
        if (records) {
            Object[] o = (Object[]) a;
            for (int k = 1; k <= count; k++) {
                Rt.copyRecords(o, src, o, src + k * stride, stride);
            }
            return;
        }
        int done = 1;
        while (done <= count) {
            int m = Math.min(done, count + 1 - done);
            System.arraycopy(a, src, a, src + done * stride, m * stride);
            done += m;
        }
    }
}
