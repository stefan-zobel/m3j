// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Typecodes: NIL has 0, TEXT 1, and every other reference type a number
 * given on first use.
 */
public final class Typecodes {
    private Typecodes() {}

    private static final AtomicInteger next = new AtomicInteger(2);

    /** the classes by typecode */
    private static final java.util.Map<Integer, Class<?>> classes = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * the typecodes by class: ClassValue may compute a value more than once
     * when threads ask for it at the same time, a typecode is given once
     */
    private static final java.util.Map<Class<?>, Integer> given = new java.util.concurrent.ConcurrentHashMap<>();

    private static final ClassValue<Integer> codes = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> c) {
            if (c == String.class) {
                return 1;
            }
            return given.computeIfAbsent(c, k -> {
                int tc = next.getAndIncrement();
                classes.put(tc, k);
                return tc;
            });
        }
    };

    /** The largest typecode given so far. */
    public static long max() {
        return next.get() - 1;
    }

    /** The class of the type with typecode tc (given before), or null. */
    public static Class<?> classOf(long tc) {
        return tc == 1 ? String.class : classes.get((int) tc);
    }

    /** The typecode of the type whose class is c. */
    public static long of(Class<?> c) {
        return codes.get(c);
    }

    /** TYPECODE (r): 0 for NIL. */
    public static long ofValue(Object r) {
        return r == null ? 0 : codes.get(r.getClass());
    }
}
