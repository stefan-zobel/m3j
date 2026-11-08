// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import m3j.rt.Typecodes;

/**
 * The interface RTTypeSRC of m3core: the names of types. A reference or
 * object type that a declaration names has the name, as cm3 gives it
 * ("Module.T"), in the field M3$NAME of its class; another type has none.
 * The procedures on cm3's type cells are not provided.
 */
public final class RTTypeSRC {
    private RTTypeSRC() {}

    public static final boolean M3$PROVIDED = true;

    /** The name of the type of ref. */
    public static String TypeName(Object ref) {
        return ref == null ? nameOf(null) : nameOf(ref.getClass());
    }

    /** The name of the type with typecode tc. */
    public static String TypecodeName(int tc) {
        return nameOf(Typecodes.classOf(tc));
    }

    private static String nameOf(Class<?> c) {
        if (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("M3$NAME");
                return (String) f.get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // a type without a name
            }
        }
        return "<anon type>";
    }
}
