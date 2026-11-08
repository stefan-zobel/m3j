// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.util.HashMap;
import java.util.Map;

import m3j.rt.Rt;
import m3j.rt.Typecodes;

/**
 * The interface RTTypeFP of m3core: the fingerprints of types. The compiler
 * gives every class of a type a constant M3$FP with the fingerprint of the
 * type, and the main class a list of all these classes (M3$TYPES); the
 * types that the runtime implements have their fingerprints here.
 */
public final class RTTypeFP {
    private RTTypeFP() {}

    public static final boolean M3$PROVIDED = true;

    /** The class of Fingerprint.T, named after the UID of the type. */
    private static final String FINGERPRINT = "m3.t.T26466202";

    /** The fingerprints of the types that the runtime implements (as cm3 has them). */
    private static final long NULL = 0x6e756c6c00008024L;
    private static final long TEXT = 0x811da972e35a860cL;
    private static final long ROOT = 0x41ad8665c81909f8L;
    private static final long MUTEX = 0x3df76df52a9d990fL;

    /** The classes by fingerprint, made on first use. */
    private static Map<Long, Class<?>> byFingerprint;

    public static Object ToFingerprint(int tc) {
        return toRecord(fingerprint(tc));
    }

    public static int FromFingerprint(Object fp) {
        long v = fromRecord(fp);
        if (v == NULL) {
            return 0;
        }
        Class<?> c = classes().get(v);
        if (c == null) {
            return 0xFFFFF;   // RTType.NoSuchType
        }
        return (int) Typecodes.of(c);
    }

    private static long fingerprint(int tc) {
        if (tc == 0) {
            return NULL;
        }
        Class<?> c = Typecodes.classOf(tc);
        if (c == null) {
            throw Rt.error(RuntimeError.ValueOutOfRange);
        }
        if (c == String.class) {
            return TEXT;
        }
        if (c == m3j.rt.M3Object.class) {
            return ROOT;
        }
        if (c == m3j.rt.Mutex.class) {
            return MUTEX;
        }
        try {
            return c.getField("M3$FP").getLong(null);
        } catch (ReflectiveOperationException e) {
            throw Rt.error(RuntimeError.ValueOutOfRange);
        }
    }

    private static synchronized Map<Long, Class<?>> classes() {
        if (byFingerprint == null) {
            Map<Long, Class<?>> m = new HashMap<>();
            m.put(TEXT, String.class);
            m.put(ROOT, m3j.rt.M3Object.class);
            m.put(MUTEX, m3j.rt.Mutex.class);
            Class<?> main = Rt.program;
            if (main != null) {
                // the list is M3$TYPES, continued in M3$TYPES1, M3$TYPES2, ...
                for (int i = 0; ; i++) {
                    String list;
                    try {
                        list = (String) main.getField(i == 0 ? "M3$TYPES" : "M3$TYPES" + i).get(null);
                    } catch (ReflectiveOperationException e) {
                        break; // no (more) list: only the types of the runtime
                    }
                    for (String n : list.split(" ")) {
                        if (n.isEmpty()) {
                            continue;
                        }
                        try {
                            Class<?> c = Class.forName(n.replace('/', '.'), false, main.getClassLoader());
                            m.putIfAbsent(c.getField("M3$FP").getLong(null), c);
                        } catch (ReflectiveOperationException e) {
                            // a class without a fingerprint
                        }
                    }
                }
            }
            byFingerprint = m;
        }
        return byFingerprint;
    }

    private static Object toRecord(long v) {
        try {
            ClassLoader l = Rt.program != null ? Rt.program.getClassLoader() : RTTypeFP.class.getClassLoader();
            Class<?> c = Class.forName(FINGERPRINT, true, l);
            Object r = c.getConstructor().newInstance();
            byte[] b = (byte[]) c.getField("byte").get(r);
            for (int i = 0; i < 8; i++) {
                b[i] = (byte) (v >>> (56 - 8 * i));
            }
            return r;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no class of Fingerprint.T", e);
        }
    }

    private static long fromRecord(Object fp) {
        try {
            byte[] b = (byte[]) fp.getClass().getField("byte").get(fp);
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (b[i] & 0xFF);
            }
            return v;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("not a Fingerprint.T", e);
        }
    }
}
