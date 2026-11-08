// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import m3j.rt.M3Object;
import m3j.rt.Typecodes;

/**
 * The interface M3jPickle of m3j: the bytes of a referent as a CM3 program
 * on AMD64_NT keeps them in memory, for the module Pickle. The layout of
 * every type is the constant M3$PKL of its class, which the compiler
 * writes (m3j.jvm.Layouts describes its form).
 */
public final class M3jPickle {
    private M3jPickle() {}

    public static final boolean M3$PROVIDED = true;

    /** M3jPickle.Image */
    public static final class Image extends M3Object {
        final byte[] bytes;
        final List<Slot> slots = new ArrayList<>();

        Image(int size) {
            bytes = new byte[size];
        }

        /** The scalars, applied by SetBytes. */
        final List<Scalar> scalars = new ArrayList<>();
    }

    /** M3jPickle.RefTable */
    public static final class RefTable extends M3Object {
        final IdentityHashMap<Object, Long> map = new IdentityHashMap<>();
    }

    // ------------------------------------------------------------ elements

    /** The element of a value: the forms of m3j.jvm.Layouts. */
    private sealed interface Elem permits Ord, Ref, Skip, Rec, Arr {}

    /** An ordinal, float or set of size bits; kind is one of i u f d s S. */
    private record Ord(char kind, int size) implements Elem {}

    private record Ref() implements Elem {}

    private record Skip(int size) implements Elem {}

    private record Rec() implements Elem {}

    private record Arr(long n, long stride, Elem elem) implements Elem {}

    /** The number of storage slots of an element in flat storage. */
    private static long slots(Elem e) {
        return switch (e) {
            case Ord o when o.kind == 'S' -> (o.size + 63) / 64;
            case Arr a -> a.n * slots(a.elem);
            default -> 1;
        };
    }

    /** A field of a class: its name, offset in bits and element. */
    private record Item(Field field, long offset, Elem elem) {}

    /** The layout of the referents (or the records) of a class. */
    private record Layout(char kind, long size, long align, int depth, List<Item> items, Elem elem, String problem) {}

    private static final ClassValue<Layout> layouts = new ClassValue<>() {
        @Override
        protected Layout computeValue(Class<?> c) {
            return parse(c);
        }
    };

    private static Layout problem(String why) {
        return new Layout('!', 0, 0, 0, List.of(), null, why);
    }

    private static Layout parse(Class<?> c) {
        String d;
        try {
            Field f = c.getDeclaredField("M3$PKL");
            d = (String) f.get(null);
        } catch (ReflectiveOperationException e) {
            return problem("no layout of the type");
        }
        try {
            String[] w = d.split(" ", -1);
            return switch (w[0]) {
                case "R", "P" -> new Layout(w[0].charAt(0), Long.parseLong(w[1]), 0, 0,
                    items(c, w.length > 2 ? w[2] : ""), null, null);
                case "O" -> new Layout('O', Long.parseLong(w[1]), Long.parseLong(w[2]), 0,
                    items(c, w.length > 3 ? w[3] : ""), null, null);
                case "A" -> {
                    int[] pos = {0};
                    Elem e = elem(w[3], pos);
                    yield new Layout('A', 0, Long.parseLong(w[2]), Integer.parseInt(w[1]), List.of(), e, null);
                }
                default -> problem("a type whose layout is not known");
            };
        } catch (RuntimeException | ReflectiveOperationException e) {
            return problem("a malformed layout (" + e + ")");
        }
    }

    private static List<Item> items(Class<?> c, String s) throws ReflectiveOperationException {
        List<Item> l = new ArrayList<>();
        if (s.isEmpty()) {
            return l;
        }
        for (String it : s.split(";")) {
            int at = it.indexOf('@'), eq = it.indexOf('=');
            String name = it.substring(0, at);
            long off = Long.parseLong(it.substring(at + 1, eq));
            int[] pos = {0};
            Elem e = elem(it.substring(eq + 1), pos);
            l.add(new Item(field(c, name), off, e));
        }
        return l;
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        try {
            return c.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return c.getField(name);
        }
    }

    private static Elem elem(String s, int[] pos) {
        char k = s.charAt(pos[0]++);
        switch (k) {
            case 'r':
                return new Ref();
            case '{':
                return new Rec();
            case 'x':
                return new Skip((int) number(s, pos));
            case '[': {
                long n = number(s, pos);
                pos[0]++; // ,
                long stride = number(s, pos);
                pos[0]++; // ,
                Elem e = elem(s, pos);
                pos[0]++; // ]
                return new Arr(n, stride, e);
            }
            default:
                return new Ord(k, (int) number(s, pos));
        }
    }

    private static long number(String s, int[] pos) {
        int start = pos[0];
        while (pos[0] < s.length() && (Character.isDigit(s.charAt(pos[0])) || s.charAt(pos[0]) == '-')) {
            pos[0]++;
        }
        return Long.parseLong(s.substring(start, pos[0]));
    }

    // ------------------------------------------------------------ locations

    /** A place of a value: a field of an object, or an element of flat storage. */
    private sealed interface Loc permits FieldLoc, ElemLoc {}

    private record FieldLoc(Object obj, Field field) implements Loc {}

    private record ElemLoc(Object storage, int index) implements Loc {}

    /** A slot of a reference: its offset in bytes, and where it is. */
    private record Slot(long offset, boolean traced, Loc loc) {}

    /** A scalar of the data: its place in the bytes and in the object. */
    private record Scalar(long offset, Ord ord, Loc loc) {}

    private static Object get(Loc l) {
        try {
            return switch (l) {
                case FieldLoc f -> f.field.get(f.obj);
                case ElemLoc e -> Array.get(e.storage, e.index);
            };
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean set(Loc l, Object v) {
        try {
            switch (l) {
                case FieldLoc f -> f.field.set(f.obj, v);
                case ElemLoc e -> Array.set(e.storage, e.index, v);
            }
            return true;
        } catch (IllegalArgumentException | IllegalAccessException e) {
            return false;
        }
    }

    /** The bits of an ordinal, float or small set in its JVM storage. */
    private static long bits(Loc l, Ord o) {
        Object v = get(l);
        boolean unsigned = o.kind == 'u';
        return switch (v) {
            case Boolean b -> b ? 1 : 0;
            case Byte b -> unsigned ? b & 0xFF : b;
            case Short s -> unsigned ? s & 0xFFFF : s;
            case Character c -> c;
            case Integer i -> i;
            case Long j -> j;
            case Float f -> Float.floatToRawIntBits(f) & 0xFFFFFFFFL;
            case Double d -> Double.doubleToRawLongBits(d);
            default -> throw new IllegalStateException("not a scalar: " + v);
        };
    }

    /** Stores bits (extended as the kind says) in the JVM storage of l. */
    private static void store(Loc l, Ord o, long b) {
        Class<?> t = switch (l) {
            case FieldLoc f -> f.field.getType();
            case ElemLoc e -> e.storage.getClass().getComponentType();
        };
        Object v;
        if (t == boolean.class) {
            v = b != 0;
        } else if (t == byte.class) {
            v = (byte) b;
        } else if (t == short.class) {
            v = (short) b;
        } else if (t == char.class) {
            v = (char) b;
        } else if (t == int.class) {
            v = (int) b;
        } else if (t == long.class) {
            v = b;
        } else if (t == float.class) {
            v = Float.intBitsToFloat((int) b);
        } else if (t == double.class) {
            v = Double.longBitsToDouble(b);
        } else {
            throw new IllegalStateException("not a scalar: " + t);
        }
        set(l, v);
    }

    // ------------------------------------------------------------ images

    /** Builds the image of a referent; null and a problem if it cannot. */
    private static final class Builder {
        Image img;
        String problem;

        /** Notes the element e at loc and bit offset off. */
        void elem(Elem e, Loc loc, long off) {
            switch (e) {
                case Ord o when o.kind == 'S' -> {
                    long[] w;
                    int start;
                    if (loc instanceof ElemLoc el) {
                        w = (long[]) el.storage;
                        start = el.index;
                    } else {
                        w = (long[]) get(loc);
                        start = 0;
                    }
                    for (int i = 0; i * 64 < o.size; i++) {
                        int n = java.lang.Math.min(64, o.size - i * 64);
                        Ord word = new Ord('u', n);
                        Loc wl = new ElemLoc(w, start + i);
                        img.scalars.add(new Scalar(off + i * 64L, word, wl));
                        put(off + i * 64L, n, w[start + i]);
                    }
                }
                case Ord o -> {
                    img.scalars.add(new Scalar(off, o, loc));
                    put(off, o.size, bits(loc, o));
                }
                case Ref r -> img.slots.add(new Slot(off / 8, true, loc));
                case Skip s -> img.slots.add(new Slot(off / 8, false, loc));
                case Rec r -> {
                    Object rec = get(loc);
                    Layout lay = layouts.get(rec.getClass());
                    if (lay.kind != 'R') {
                        fail(lay, rec.getClass());
                        return;
                    }
                    items(rec, lay.items, off);
                }
                case Arr a -> {
                    Object storage;
                    int base;
                    if (loc instanceof ElemLoc el) {
                        storage = el.storage;
                        base = el.index;
                    } else {
                        storage = get(loc);
                        base = 0;
                    }
                    long k = slots(a.elem);
                    for (long i = 0; i < a.n && problem == null; i++) {
                        elem(a.elem, new ElemLoc(storage, (int) (base + i * k)), off + i * a.stride);
                    }
                }
            }
        }

        void items(Object obj, List<Item> items, long off) {
            for (Item it : items) {
                if (problem != null) {
                    return;
                }
                elem(it.elem, new FieldLoc(obj, it.field), off + it.offset);
            }
        }

        void fail(Layout lay, Class<?> c) {
            problem = lay.problem != null ? lay.problem + ": " + c.getName() : "cannot pickle " + c.getName();
        }

        /** Writes the low n bits of v at bit offset off of the bytes. */
        void put(long off, int n, long v) {
            byte[] b = img.bytes;
            for (int i = 0; i < n; ) {
                long bit = off + i;
                int byteIx = (int) (bit >>> 3), sh = (int) (bit & 7);
                int take = java.lang.Math.min(8 - sh, n - i);
                int mask = ((1 << take) - 1) << sh;
                int val = (int) ((v >>> i) << sh) & mask;
                b[byteIx] = (byte) ((b[byteIx] & ~mask) | val);
                i += take;
            }
        }
    }

    /** Reads n bits at bit offset off of b, extended as kind says. */
    private static long take(byte[] b, long off, int n, char kind) {
        long v = 0;
        for (int i = 0; i < n; ) {
            long bit = off + i;
            int byteIx = (int) (bit >>> 3), sh = (int) (bit & 7);
            int t = java.lang.Math.min(8 - sh, n - i);
            long part = ((b[byteIx] & 0xFF) >>> sh) & ((1 << t) - 1);
            v |= part << i;
            i += t;
        }
        if (kind == 'i' && n < 64 && n > 0) {
            v = (v << (64 - n)) >> (64 - n);
        }
        return v;
    }

    /** The image of r, or a problem. */
    private static Object image(Object r, String[] problem) {
        Class<?> c = r.getClass();
        if (r instanceof M3Object) {
            // an object: the levels of its types, from ROOT down
            List<Class<?>> levels = new ArrayList<>();
            for (Class<?> k = c; k != M3Object.class; k = k.getSuperclass()) {
                if (k == null || k == Object.class) {
                    problem[0] = "cannot pickle " + c.getName();
                    return null;
                }
                levels.addFirst(k);
            }
            List<Layout> lays = new ArrayList<>();
            long end = 64; // the method suite
            List<Long> offsets = new ArrayList<>();
            for (Class<?> k : levels) {
                Layout lay = layouts.get(k);
                if (lay.kind != 'O') {
                    problem[0] = lay.problem != null ? lay.problem + ": " + k.getName() : "cannot pickle " + k.getName();
                    return null;
                }
                long off = roundUp(end, java.lang.Math.max(lay.align, 8));
                offsets.add(off);
                lays.add(lay);
                end = off + lay.size;
            }
            end = roundUp(end, 64);
            Builder bld = new Builder();
            bld.img = new Image((int) ((end - 64) / 8));
            for (int i = 0; i < lays.size() && bld.problem == null; i++) {
                bld.items(r, lays.get(i).items, offsets.get(i) - 64);
            }
            return finish(bld, problem);
        }
        Layout lay = layouts.get(c);
        switch (lay.kind) {
            case 'P': {
                Builder bld = new Builder();
                bld.img = new Image((int) (roundUp(lay.size, 64) / 8));
                bld.items(r, lay.items, 0);
                return finish(bld, problem);
            }
            case 'A': {
                long n = 1;
                try {
                    for (int i = 0; i < lay.depth; i++) {
                        n *= c.getField("n" + i).getInt(r);
                    }
                    Object storage = c.getField("a").get(r);
                    Builder bld = new Builder();
                    long bytes = (n * lay.align + 7) / 8;
                    bld.img = new Image((int) bytes);
                    long k = slots(lay.elem);
                    for (long i = 0; i < n && bld.problem == null; i++) {
                        bld.elem(lay.elem, new ElemLoc(storage, (int) (i * k)), i * lay.align);
                    }
                    return finish(bld, problem);
                } catch (ReflectiveOperationException e) {
                    problem[0] = "cannot pickle " + c.getName();
                    return null;
                }
            }
            default:
                problem[0] = lay.problem != null ? lay.problem + ": " + c.getName() : "cannot pickle " + c.getName();
                return null;
        }
    }

    private static Object finish(Builder bld, String[] problem) {
        if (bld.problem != null) {
            problem[0] = bld.problem;
            return null;
        }
        return bld.img;
    }

    private static long roundUp(long x, long a) {
        return (x + a - 1) / a * a;
    }

    // ------------------------------------------------------------ procedures

    public static Image ImageOf(Object r) {
        if (r == null || r instanceof String) {
            return null;
        }
        return (Image) image(r, new String[1]);
    }

    public static String Problem(Object r) {
        if (r == null) {
            return "NIL";
        }
        if (r instanceof String) {
            return "a TEXT";
        }
        String[] p = new String[1];
        image(r, p);
        return p[0] == null ? "" : p[0];
    }

    public static long Size(Image img) {
        return img.bytes.length;
    }

    public static void GetBytes(Image img, byte[] buf, int off, int n) {
        byte[] b = img.bytes;
        if (n < b.length) {
            throw m3j.rt.Rt.error(RuntimeError.SubscriptOutOfRange);
        }
        System.arraycopy(b, 0, buf, off, b.length);
    }

    public static void SetBytes(Image img, byte[] buf, int off, int n) {
        Image im = img;
        byte[] b = new byte[im.bytes.length];
        System.arraycopy(buf, off, b, 0, java.lang.Math.min(n, b.length));
        for (Scalar s : im.scalars) {
            store(s.loc, s.ord, take(b, s.offset, s.ord.size, s.ord.kind));
        }
    }

    public static long Slots(Image img) {
        return img.slots.size();
    }

    private static Slot slot(Image img, long i) {
        List<Slot> l = img.slots;
        if (i < 0 || i >= l.size()) {
            throw m3j.rt.Rt.error(RuntimeError.SubscriptOutOfRange);
        }
        return l.get((int) i);
    }

    public static long SlotOffset(Image img, long i) {
        return slot(img, i).offset;
    }

    public static boolean SlotIsTraced(Image img, long i) {
        return slot(img, i).traced;
    }

    public static Object GetRef(Image img, long i) {
        Slot s = slot(img, i);
        return s.traced ? get(s.loc) : null;
    }

    public static boolean SetRef(Image img, long i, Object v) {
        return set(slot(img, i).loc, v);
    }

    public static long Shape(Object r, long[] s, int off, int n) {
        if (r == null) {
            return 0;
        }
        Class<?> c = r.getClass();
        Layout lay = layouts.get(c);
        if (lay.kind != 'A') {
            return 0;
        }
        try {
            for (int i = 0; i < lay.depth; i++) {
                if (i >= n) {
                    throw m3j.rt.Rt.error(RuntimeError.SubscriptOutOfRange);
                }
                s[off + i] = c.getField("n" + i).getInt(r);
            }
        } catch (ReflectiveOperationException e) {
            return 0;
        }
        return lay.depth;
    }

    public static Object New(long tc, long[] shape, int off, int n) {
        Class<?> c = Typecodes.classOf(tc);
        if (c == null || c == String.class || java.lang.reflect.Modifier.isAbstract(c.getModifiers())) {
            return null;
        }
        Layout lay = layouts.get(c);
        try {
            if (lay.kind == 'A') {
                if (n < lay.depth) {
                    return null;
                }
                Class<?>[] ps = new Class<?>[lay.depth];
                Object[] args = new Object[lay.depth];
                for (int i = 0; i < lay.depth; i++) {
                    long v = shape[off + i];
                    if (v < 0 || v > Integer.MAX_VALUE) {
                        return null;
                    }
                    ps[i] = int.class;
                    args[i] = (int) v;
                }
                Constructor<?> k = c.getConstructor(ps);
                return k.newInstance(args);
            }
            if (lay.kind != 'P' && lay.kind != 'O' && !(M3Object.class.isAssignableFrom(c))) {
                return null;
            }
            return c.getConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public static RefTable NewTable() {
        return new RefTable();
    }

    public static long Index(RefTable t, Object r, long next) {
        Long i = t.map.putIfAbsent(r, next);
        return i == null ? -1 : i;
    }

    public static void Clear(RefTable t) {
        t.map.clear();
    }
}
