// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.util.Arrays;
import java.util.Objects;

import m3j.rt.M3Exception;
import m3j.rt.Rt;

/** The interface Java of the package java: what bindings of Java classes share. */
public final class Java {
    private Java() {}

    public static final boolean M3$PROVIDED = true;

    /** EXCEPTION Exception (JavaThrowable.T): a Java exception, with the throwable as its argument. */
    public static final class Exception extends M3Exception {
        private static final long serialVersionUID = 1L;

        @Override
        public String m3Name() {
            return "Java.Exception";
        }
    }

    /**
     * The Modula-3 exception for a throwable that a call of a Java method
     * ended with: a Modula-3 exception that went through Java stays what it
     * is, a JVM exception that stands for a checked runtime error is that
     * runtime error, and any other throwable is Exception.
     */
    public static M3Exception caught(Throwable t) {
        if (t instanceof M3Exception
                || t instanceof NullPointerException
                || t instanceof ArithmeticException
                || t instanceof IndexOutOfBoundsException
                || t instanceof ClassCastException
                || t instanceof StackOverflowError
                || t instanceof OutOfMemoryError) {
            return Rt.toM3(t);
        }
        Exception e = new Exception();
        e.arg = t;
        Rt.locate(e);
        return e;
    }

    public static String ToText(Object x) {
        return String.valueOf(x);
    }

    public static boolean Equals(Object a, Object b) {
        return Objects.equals(a, b);
    }

    public static long HashCode(Object x) {
        return Objects.hashCode(x);
    }

    public static String ClassName(Object x) {
        return x.getClass().getName();
    }

    public static boolean IsInstance(Object x, String name) {
        return x != null && isA(x.getClass(), name);
    }

    /** By name: the class may not be visible from the loader of this class. */
    private static boolean isA(Class<?> c, String name) {
        if (c == null) {
            return false;
        }
        if (c.getName().equals(name) || isA(c.getSuperclass(), name)) {
            return true;
        }
        for (Class<?> i : c.getInterfaces()) {
            if (isA(i, name)) {
                return true;
            }
        }
        return false;
    }

    public static Object FromInt(int i) {
        return Integer.valueOf(i);
    }

    public static Object FromLong(long i) {
        return Long.valueOf(i);
    }

    public static long ToInt(Object x) {
        return ((Number) x).longValue();
    }

    public static Object NewLongs(long[] a, int off, int n) {
        return Arrays.copyOfRange(a, off, off + n);
    }

    public static Object NewBytes(byte[] a, int off, int n) {
        return Arrays.copyOfRange(a, off, off + n);
    }

    public static Object NewDoubles(double[] a, int off, int n) {
        return Arrays.copyOfRange(a, off, off + n);
    }

    public static Object NewStrings(String[] a, int off, int n) {
        return Arrays.copyOfRange(a, off, off + n);
    }

    public static long Length(Object array) {
        if (!array.getClass().isArray()) {
            throw new ClassCastException(array.getClass().getName() + " is not an array");
        }
        return java.lang.reflect.Array.getLength(array);
    }

    public static long GetLongs(Object array, long[] a, int off, int n) {
        return get((long[]) array, ((long[]) array).length, a, off, n);
    }

    public static long GetBytes(Object array, byte[] a, int off, int n) {
        return get((byte[]) array, ((byte[]) array).length, a, off, n);
    }

    public static long GetDoubles(Object array, double[] a, int off, int n) {
        return get((double[]) array, ((double[]) array).length, a, off, n);
    }

    public static long GetStrings(Object array, String[] a, int off, int n) {
        return get((String[]) array, ((String[]) array).length, a, off, n);
    }

    private static long get(Object from, int length, Object to, int off, int n) {
        int m = java.lang.Math.min(length, n);
        System.arraycopy(from, 0, to, off, m);
        return m;
    }
}
