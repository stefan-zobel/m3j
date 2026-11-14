// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ByteValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.Field;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.LongValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ShortValue;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

/**
 * The values of a program of Modula-3 under the debugger: read from where
 * the JVM holds them (see m3j.jvm.Aggregates, Frames and Refs) and shown as
 * Modula-3, by the types of the descriptions of the units.
 *
 * <p>A scalar, a TEXT, a record and a reference are one JVM value; an
 * array (fixed or open) and a set of more than 64 elements are storage
 * with an offset, and the open dimensions. A scalar passed by VAR or
 * READONLY, or that lives in a cell, is read through its reference
 * object.
 */
final class Values {

    /** A value of Modula-3 in the program. */
    sealed interface V permits Plain, Store, None, Const {}

    /** A value that the JVM holds as one value; null is NIL. loc is where it is held, or null. */
    record Plain(Value value, Loc loc) implements V {
        Plain(Value value) {
            this(value, null);
        }
    }

    /** An array or a large set: its elements in storage from offset; dims are its open dimensions. */
    record Store(ArrayReference storage, int offset, int[] dims) implements V {}

    /** No value, and why. */
    record None(String why) implements V {}

    /** An ordinal value that the debugger computed (FIRST, LAST, NUMBER). */
    record Const(long value) implements V {}

    /** A part of a value (a field, an element, the referent) or a variable: designator names it in Modula-3. */
    record Part(String name, String type, V value, String designator) {}

    /** Where the JVM holds a value that can be set: a slot, a field, an element of an array. */
    interface Loc {
        /** The JVM type of the place, as a signature (J, I, B, Ljava/lang/String;, ...). */
        String signature();

        Value get();

        void set(Value v) throws InvalidTypeException, ClassNotLoadedException;

        VirtualMachine vm();
    }

    /** Where values are found by name: the slots of a frame, the fields of an object, the static fields of a class. */
    interface Home {
        /** The value of name, or null if there is none (not NIL: see has). */
        Value get(String name);

        boolean has(String name);

        /** The place of name, or null. */
        Loc loc(String name);
    }

    /** The visible slots of a frame. */
    static Home slots(StackFrame f) {
        return new Home() {
            private LocalVariable var(String name) {
                try {
                    return f.visibleVariableByName(name);
                } catch (AbsentInformationException e) {
                    return null;
                }
            }

            @Override
            public Value get(String name) {
                LocalVariable v = var(name);
                return v == null ? null : f.getValue(v);
            }

            @Override
            public boolean has(String name) {
                return var(name) != null;
            }

            @Override
            public Loc loc(String name) {
                LocalVariable v = var(name);
                return v == null ? null : new Loc() {
                    @Override
                    public String signature() {
                        return v.signature();
                    }

                    @Override
                    public Value get() {
                        return f.getValue(v);
                    }

                    @Override
                    public void set(Value x) throws InvalidTypeException, ClassNotLoadedException {
                        f.setValue(v, x);
                    }

                    @Override
                    public VirtualMachine vm() {
                        return f.virtualMachine();
                    }
                };
            }
        };
    }

    /** The fields of an object. */
    static Home fields(ObjectReference o) {
        return new Home() {
            @Override
            public Value get(String name) {
                Field f = o.referenceType().fieldByName(name);
                return f == null ? null : o.getValue(f);
            }

            @Override
            public boolean has(String name) {
                return o.referenceType().fieldByName(name) != null;
            }

            @Override
            public Loc loc(String name) {
                Field f = o.referenceType().fieldByName(name);
                return f == null || f.isStatic() ? null : fieldLoc(f, o);
            }
        };
    }

    /** The static fields of a class. */
    static Home statics(ReferenceType t) {
        return new Home() {
            @Override
            public Value get(String name) {
                Field f = t.fieldByName(name);
                return f == null || !f.isStatic() ? null : t.getValue(f);
            }

            @Override
            public boolean has(String name) {
                Field f = t.fieldByName(name);
                return f != null && f.isStatic();
            }

            @Override
            public Loc loc(String name) {
                Field f = t.fieldByName(name);
                return f == null || !f.isStatic() ? null : fieldLoc(f, null);
            }
        };
    }

    /** The field f of the object o, or the static field f if o is null. */
    private static Loc fieldLoc(Field f, ObjectReference o) {
        return new Loc() {
            @Override
            public String signature() {
                return f.signature();
            }

            @Override
            public Value get() {
                return o == null ? f.declaringType().getValue(f) : o.getValue(f);
            }

            @Override
            public void set(Value x) throws InvalidTypeException, ClassNotLoadedException {
                if (f.isFinal()) {
                    throw new IllegalArgumentException("it is a constant");
                }
                if (o != null) {
                    o.setValue(f, x);
                } else if (f.declaringType() instanceof ClassType c) {
                    c.setValue(f, x);
                } else {
                    throw new IllegalArgumentException("it cannot be set");
                }
            }

            @Override
            public VirtualMachine vm() {
                return f.virtualMachine();
            }
        };
    }

    /** The element i of the array a. */
    private static Loc elementLoc(ArrayReference a, int i) {
        return new Loc() {
            @Override
            public String signature() {
                return ((ArrayType) a.referenceType()).componentSignature();
            }

            @Override
            public Value get() {
                return a.getValue(i);
            }

            @Override
            public void set(Value x) throws InvalidTypeException, ClassNotLoadedException {
                a.setValue(i, x);
            }

            @Override
            public VirtualMachine vm() {
                return a.virtualMachine();
            }
        };
    }

    /** What a type is, for reading and showing its values. */
    private enum Kind { ORDINAL, FLOAT, SET, BIGSET, TEXT, ARRAY, OPEN, RECORD, REF, OTHER }

    private static final List<String> REFS = List.of(
        "m3j.rt.LongRef", "m3j.rt.IntRef", "m3j.rt.FloatRef", "m3j.rt.DoubleRef", "m3j.rt.ObjRef");

    /** At most this many elements in a preview of an array. */
    private static final int PREVIEW = 10;
    /** At most this many characters in a preview, and in a TEXT. */
    private static final int LONG = 100, TEXT = 1000;

    private final TypeTable types;
    /** The name of the procedure of a procedure value of a class, or null. */
    private final Function<ClassType, String> procedures;

    Values(TypeTable types, Function<ClassType, String> procedures) {
        this.types = types;
        this.procedures = procedures;
    }

    // ---- types

    private Kind kind(String t) {
        String r = types.resolve(t);
        switch (r) {
            case "INTEGER", "LONGINT", "CARDINAL", "LONGCARD", "BOOLEAN", "CHAR", "WIDECHAR" -> {
                return Kind.ORDINAL;
            }
            case "REAL", "LONGREAL", "EXTENDED" -> {
                return Kind.FLOAT;
            }
            case "TEXT" -> {
                return Kind.TEXT;
            }
            case "REFANY", "ROOT", "UNTRACED-ROOT", "MUTEX", "NULL" -> {
                return Kind.REF;
            }
            default -> {
            }
        }
        return switch (types.get(r)) {
            case Info.Enum e -> Kind.ORDINAL;
            case Info.Subrange s -> Kind.ORDINAL;
            case Info.SetType s -> number(s.base()) <= 64 ? Kind.SET : Kind.BIGSET;
            case Info.Array a -> Kind.ARRAY;
            case Info.OpenArray a -> Kind.OPEN;
            case Info.Record x -> Kind.RECORD;
            case Info.Ref x -> Kind.REF;
            case Info.ObjectType o -> Kind.REF;
            case Info.Opaque o -> Kind.REF;
            case null, default -> Kind.OTHER;
        };
    }

    /** The number of values of the ordinal type t. */
    private long number(String t) {
        String r = types.resolve(t);
        return switch (r) {
            case "BOOLEAN" -> 2;
            case "CHAR" -> 256;
            case "WIDECHAR" -> 0x10000;
            default -> switch (types.get(r)) {
                case Info.Enum e -> e.elements().size();
                case Info.Subrange s -> s.last() - s.first() + 1;
                case null, default -> Long.MAX_VALUE;
            };
        };
    }

    /** The first value of the ordinal type t. */
    private long first(String t) {
        return types.get(types.resolve(t)) instanceof Info.Subrange s ? s.first() : 0;
    }

    /** The number of elements of storage that a value of type t takes in an array. */
    private long slots(String t) {
        String r = types.resolve(t);
        return switch (types.get(r)) {
            case Info.Array a -> number(a.index()) * slots(a.element());
            case Info.SetType s when kind(r) == Kind.BIGSET -> (number(s.base()) + 63) / 64;
            case null, default -> 1;
        };
    }

    /** The number of open dimensions of type t. */
    private int openDepth(String t) {
        int n = 0;
        for (Info.Type d = types.get(types.resolve(t)); d instanceof Info.OpenArray a;
             d = types.get(types.resolve(a.element()))) {
            n++;
        }
        return n;
    }

    // ---- reading

    /** The value of type t that home holds as name. */
    V read(String t, Home home, String name) {
        if (!home.has(name)) {
            return new None("not available");
        }
        Value v = home.get(name);
        Kind k = kind(t);
        if (k == Kind.ARRAY || k == Kind.OPEN || k == Kind.BIGSET) {
            if (v instanceof ObjectReference o && isRef(o)) {
                return new None("not available");
            }
            if (!(v instanceof ArrayReference a)) {
                return new None("no storage");
            }
            int off = home.has(name + "$off") ? (int) toLong(home.get(name + "$off")) : 0;
            int[] dims = new int[openDepth(t)];
            for (int i = 0; i < dims.length; i++) {
                if (!home.has(name + "$n" + i)) {
                    return new None("not available");
                }
                dims[i] = (int) toLong(home.get(name + "$n" + i));
            }
            return new Store(a, off, dims);
        }
        return plain(t, v, home.loc(name));
    }

    /**
     * The value v of type t held at loc, through the reference object that
     * holds it for a VAR formal or a cell.
     */
    private V plain(String t, Value v, Loc loc) {
        if (v instanceof ObjectReference o && kind(t) != Kind.RECORD && isRef(o)) {
            try {
                Loc l = deref(o);
                return new Plain(l.get(), l);
            } catch (IllegalStateException e) {
                return new None(e.getMessage());
            }
        }
        return new Plain(v, loc);
    }

    /** Whether o is a reference object of the runtime (a VAR formal, a cell). */
    private static boolean isRef(ObjectReference o) {
        if (!(o.referenceType() instanceof ClassType c)) {
            return false;
        }
        for (ClassType s = c.superclass(); s != null; s = s.superclass()) {
            if (REFS.contains(s.name())) {
                return true;
            }
        }
        return false;
    }

    /** The place that the reference object o refers to. */
    private static Loc deref(ObjectReference o) {
        ReferenceType c = o.referenceType();
        String n = c.name();
        if (n.startsWith("m3j.rt.") && n.endsWith("Cell")) {
            Loc l = fields(o).loc("v");
            if (l != null) {
                return l;
            }
            throw new IllegalStateException("not available");
        }
        if (n.startsWith("m3j.rt.") && n.endsWith("Elem")) {
            if (get(o, "a") instanceof ArrayReference a) {
                return elementLoc(a, (int) toLong(get(o, "i")));
            }
            throw new IllegalStateException("not available");
        }
        if (n.startsWith("m3.r.")) {
            // m3.r.OWNER$FIELD (a field of the object o.o) or m3.r.OWNER$$FIELD
            // (a static field), OWNER the class with its dots and dollars as _
            String s = n.substring("m3.r.".length());
            int d = s.indexOf('$');
            if (d > 0 && s.startsWith("$", d + 1)) {
                String owner = s.substring(0, d);
                if (owner.length() > 5 && owner.startsWith("m3_") && owner.charAt(4) == '_') {
                    String cls = "m3." + owner.charAt(3) + "." + owner.substring(5);
                    for (ReferenceType t : o.virtualMachine().classesByName(cls)) {
                        Field f = t.fieldByName(s.substring(d + 2));
                        if (f != null && f.isStatic()) {
                            return fieldLoc(f, null);
                        }
                    }
                }
            } else if (d > 0 && get(o, "o") instanceof ObjectReference owner) {
                Field f = owner.referenceType().fieldByName(s.substring(d + 1));
                if (f != null) {
                    return fieldLoc(f, owner);
                }
            }
        }
        throw new IllegalStateException("not available");
    }

    private static Value get(ObjectReference o, String field) {
        Field f = o.referenceType().fieldByName(field);
        if (f == null) {
            throw new IllegalStateException("not available");
        }
        return o.getValue(f);
    }

    private static long toLong(Value v) {
        return switch (v) {
            case LongValue x -> x.value();
            case IntegerValue x -> x.value();
            case ShortValue x -> x.value();
            case CharValue x -> x.value();
            case ByteValue x -> x.value();
            case BooleanValue x -> x.value() ? 1 : 0;
            case PrimitiveValue x -> x.longValue();
            case null, default -> 0;
        };
    }

    /** The ordinal value v of type t (a byte holds an unsigned value if t has no negative values). */
    private long ordinal(String t, Value v) {
        long x = toLong(v);
        if (v instanceof ByteValue && x < 0 && first(t) >= 0) {
            x &= 0xFF;
        }
        return x;
    }

    // ---- showing

    /** Value v of type t in one line. */
    String show(String t, V v) {
        try {
            return show(t, v, true);
        } catch (RuntimeException e) {
            return "(not available)";
        }
    }

    /** With preview, the fields or elements of an aggregate are shown, else {...}. */
    private String show(String t, V v, boolean preview) {
        if (v instanceof None n) {
            return "(" + n.why() + ")";
        }
        if (v instanceof Const c) {
            return types.ordinal(t, c.value());
        }
        Kind k = kind(t);
        if (v instanceof Store s) {
            if (k == Kind.BIGSET) {
                return bigSet(t, s);
            }
            return preview ? elements(t, s) : "{...}";
        }
        Value x = ((Plain) v).value();
        return switch (k) {
            case ORDINAL -> x instanceof PrimitiveValue ? types.ordinal(t, ordinal(t, x)) : raw(x);
            case FLOAT -> switch (x) {
                case FloatValue f -> Float.toString(f.value());
                case DoubleValue d -> Double.toString(d.value());
                case null, default -> raw(x);
            };
            case SET -> x instanceof PrimitiveValue ? smallSet(t, toLong(x)) : raw(x);
            case TEXT -> x == null ? "NIL" : x instanceof StringReference s ? text(s.value()) : raw(x);
            case RECORD -> x == null ? "NIL" : preview ? fields(record(t, x)) : "{...}";
            case REF, OTHER -> reference(t, x, preview);
            case BIGSET, ARRAY, OPEN -> raw(x);
        };
    }

    private static String raw(Value x) {
        return x == null ? "NIL" : x.toString();
    }

    /** A TEXT as a literal of Modula-3, cut if long. */
    static String text(String s) {
        StringBuilder b = new StringBuilder("\"");
        int n = Math.min(s.length(), TEXT);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            b.append(TypeTable.escape(c, c > 0xFF, '"'));
        }
        b.append('"');
        if (n < s.length()) {
            b.append("...");
        }
        return b.toString();
    }

    private String smallSet(String t, long bits) {
        Info.SetType s = (Info.SetType) types.get(types.resolve(t));
        List<Long> elems = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if ((bits & (1L << i)) != 0) {
                elems.add((long) i);
            }
        }
        return set(s.base(), elems);
    }

    private String bigSet(String t, Store st) {
        Info.SetType s = (Info.SetType) types.get(types.resolve(t));
        long n = number(s.base());
        int words = (int) ((n + 63) / 64);
        List<Value> ws = st.storage().getValues(st.offset(), words);
        List<Long> elems = new ArrayList<>();
        for (int w = 0; w < words; w++) {
            long bits = toLong(ws.get(w));
            for (int i = 0; i < 64; i++) {
                if ((bits & (1L << i)) != 0) {
                    elems.add(w * 64L + i);
                }
            }
        }
        return set(s.base(), elems);
    }

    /** The set of the elements (from 0) of base: {a, b, c..d}, a run of three or more as a range. */
    private String set(String base, List<Long> elems) {
        long first = first(base);
        StringJoiner j = new StringJoiner(", ", "{", "}");
        for (int i = 0; i < elems.size(); ) {
            int k = i;
            while (k + 1 < elems.size() && elems.get(k + 1) == elems.get(k) + 1) {
                k++;
            }
            String a = types.ordinal(base, first + elems.get(i));
            if (k - i >= 2) {
                j.add(a + ".." + types.ordinal(base, first + elems.get(k)));
                i = k + 1;
            } else {
                j.add(a);
                i++;
            }
        }
        return j.toString();
    }

    /** The parts of a record or an object as {name := value, ...}, aggregates in them as {...}. */
    private String fields(List<Part> parts) {
        StringJoiner j = new StringJoiner(", ", "{", "}");
        int len = 0;
        for (Part p : parts) {
            String s = p.name() + " := " + show(p.type(), p.value(), false);
            len += s.length() + 2;
            if (len > LONG) {
                j.add("...");
                break;
            }
            j.add(s);
        }
        return j.toString();
    }

    /** The first elements of an array as {a, b, ...}. */
    private String elements(String t, Store s) {
        int n = count(t, s);
        StringJoiner j = new StringJoiner(", ", "{", "}");
        int len = 0;
        List<Part> parts = elements(t, s, "", 0, Math.min(n, PREVIEW));
        for (Part p : parts) {
            String e = show(p.type(), p.value(), false);
            len += e.length() + 2;
            if (len > LONG) {
                j.add("...");
                return j.toString();
            }
            j.add(e);
        }
        if (n > parts.size()) {
            j.add("...");
        }
        return j.toString();
    }

    /** A reference (also REFANY, an object, a procedure or a value of a type not described) that is not NIL. */
    private String reference(String t, Value x, boolean preview) {
        switch (x) {
            case null -> {
                return "NIL";
            }
            case StringReference s -> {
                return text(s.value());
            }
            case ObjectReference o -> {
                if (o.referenceType() instanceof ClassType c && isProcedure(c)) {
                    String p = procedures.apply(c);
                    return p != null ? "PROCEDURE " + p : "PROCEDURE";
                }
                String uid = dynamicUid(o);
                String name = uid == null ? null : typeName(uid, o);
                if (name == null) {
                    String s = types.name(t);
                    return s != null && !s.equals("REFANY") && !s.equals("ROOT") ? s : o.referenceType().name();
                }
                if (!preview) {
                    return name;
                }
                Info.Type d = types.get(types.resolve(uid));
                return switch (d) {
                    case Info.Ref r -> name + referent(r.target(), o);
                    case Info.ObjectType ob -> name + fields(objectFields(uid, o, ""));
                    case null, default -> name;
                };
            }
            default -> {
                return raw(x);
            }
        }
    }

    /** The referent of the reference o to target, in a preview. */
    private String referent(String target, ObjectReference o) {
        return switch (kind(target)) {
            case RECORD -> fields(record(target, o));
            case OPEN, ARRAY, BIGSET -> {
                V v = refStore(target, o);
                yield v instanceof Store s && kind(target) != Kind.BIGSET ? elements(target, s) : "{" + show(target, v, false) + "}";
            }
            default -> "{" + show(target, plain(target, get(o, "v"), null), false) + "}";
        };
    }

    /** The storage of the referent of o, a reference to an array or a large set. */
    private V refStore(String target, ObjectReference o) {
        if (kind(target) == Kind.OPEN) {
            Home h = fields(o);
            if (!(h.get("a") instanceof ArrayReference a)) {
                return new None("no storage");
            }
            int[] dims = new int[openDepth(target)];
            for (int i = 0; i < dims.length; i++) {
                dims[i] = (int) toLong(h.get("n" + i));
            }
            return new Store(a, 0, dims);
        }
        return get(o, "v") instanceof ArrayReference a ? new Store(a, 0, new int[0]) : new None("no storage");
    }

    /**
     * The methods that the constant pool of class t refers to, as pairs of
     * the name of a class and of a method; empty if the JVM does not give
     * the pool.
     */
    static List<String[]> methodRefs(ReferenceType t) {
        List<String[]> out = new ArrayList<>();
        if (!t.virtualMachine().canGetConstantPool()) {
            return out;
        }
        int n = t.constantPoolCount();
        byte[] b = t.constantPool();
        String[] utf8 = new String[n];
        int[] a = new int[n], c = new int[n];
        byte[] tags = new byte[n];
        int pos = 0;
        for (int i = 1; i < n && pos < b.length; i++) {
            byte tag = b[pos++];
            tags[i] = tag;
            switch (tag) {
                case 1 -> {
                    int len = u2(b, pos);
                    utf8[i] = new String(b, pos + 2, len, java.nio.charset.StandardCharsets.ISO_8859_1);
                    pos += 2 + len;
                }
                case 3, 4 -> pos += 4;
                case 5, 6 -> {
                    pos += 8;
                    i++;
                }
                case 7, 8, 16, 19, 20 -> {
                    a[i] = u2(b, pos);
                    pos += 2;
                }
                case 15 -> pos += 3;
                case 9, 10, 11, 12, 17, 18 -> {
                    a[i] = u2(b, pos);
                    c[i] = u2(b, pos + 2);
                    pos += 4;
                }
                default -> {
                    return out;
                }
            }
        }
        for (int i = 1; i < n; i++) {
            if ((tags[i] == 10 || tags[i] == 11) && a[i] < n && c[i] < n && tags[a[i]] == 7 && tags[c[i]] == 12) {
                String cls = utf8[a[a[i]]];
                String name = utf8[a[c[i]]];
                if (cls != null && name != null) {
                    out.add(new String[] {cls.replace('/', '.'), name});
                }
            }
        }
        return out;
    }

    private static int u2(byte[] b, int pos) {
        return (b[pos] & 0xFF) << 8 | b[pos + 1] & 0xFF;
    }

    private static boolean isProcedure(ClassType c) {
        for (InterfaceType i : c.allInterfaces()) {
            if (i.name().startsWith("m3.p.")) {
                return true;
            }
        }
        return false;
    }

    /** The UID of the type of the object o: the first of its classes that is a type class m3.t.T..., or null. */
    private static String dynamicUid(ObjectReference o) {
        if (!(o.referenceType() instanceof ClassType c)) {
            return null;
        }
        for (ClassType s = c; s != null; s = s.superclass()) {
            String n = s.name();
            if (n.startsWith("m3.t.T") && n.length() == "m3.t.T".length() + 8) {
                return n.substring("m3.t.T".length());
            }
        }
        return null;
    }

    /** The name of the type uid of the object o: its name, else the name its class carries, else its structure. */
    private String typeName(String uid, ObjectReference o) {
        String n = types.name(uid);
        if (n != null) {
            return n;
        }
        for (ClassType s = (ClassType) o.referenceType(); s != null; s = s.superclass()) {
            Field f = s.fieldByName("M3$NAME");
            if (s.name().equals("m3.t.T" + uid) && f != null && f.isStatic()
                && s.getValue(f) instanceof StringReference r) {
                return r.value();
            }
        }
        return types.get(uid) == null ? null : types.show(uid);
    }

    // ---- parts

    /** Whether value v of type t has parts to show. */
    boolean expandable(String t, V v) {
        try {
            return !parts(t, v, "").isEmpty() || count(t, v) > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The number of elements of v if it is an array (also through a reference), else -1. */
    int count(String t, V v) {
        try {
            Kind k = kind(t);
            if (v instanceof Store s && (k == Kind.ARRAY || k == Kind.OPEN)) {
                return count(t, s);
            }
            if (v instanceof Plain(ObjectReference o, _) && k == Kind.REF && !(o instanceof StringReference)) {
                String uid = dynamicUid(o);
                if (uid != null && types.get(types.resolve(uid)) instanceof Info.Ref r
                    && (kind(r.target()) == Kind.OPEN || kind(r.target()) == Kind.ARRAY)
                    && refStore(r.target(), o) instanceof Store s) {
                    return count(r.target(), s);
                }
            }
        } catch (RuntimeException e) {
            // not available
        }
        return -1;
    }

    private int count(String t, Store s) {
        if (kind(t) == Kind.OPEN) {
            return s.dims().length > 0 ? s.dims()[0] : 0;
        }
        Info.Array a = (Info.Array) types.get(types.resolve(t));
        return (int) Math.min(number(a.index()), Integer.MAX_VALUE);
    }

    /** The parts of value v of type t (named designator): fields, or elements from start (count of them, 0: all). */
    List<Part> parts(String t, V v, String designator, int start, int count) {
        try {
            Kind k = kind(t);
            if (v instanceof Store s && (k == Kind.ARRAY || k == Kind.OPEN)) {
                return elements(t, s, designator, start, count);
            }
            if (v instanceof Plain(ObjectReference o, _) && k == Kind.REF && !(o instanceof StringReference)) {
                String uid = dynamicUid(o);
                if (uid != null && types.get(types.resolve(uid)) instanceof Info.Ref r
                    && (kind(r.target()) == Kind.OPEN || kind(r.target()) == Kind.ARRAY)
                    && refStore(r.target(), o) instanceof Store s) {
                    return elements(r.target(), s, designator, start, count);
                }
            }
            return parts(t, v, designator);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** The parts of value v of type t that are not elements of an array. */
    private List<Part> parts(String t, V v, String designator) {
        if (!(v instanceof Plain(ObjectReference o, _)) || o instanceof StringReference) {
            return List.of();
        }
        Kind k = kind(t);
        if (k == Kind.RECORD) {
            return record(t, o, designator);
        }
        if (k != Kind.REF && k != Kind.OTHER) {
            return List.of();
        }
        if (o.referenceType() instanceof ClassType c && isProcedure(c)) {
            return List.of();
        }
        String uid = dynamicUid(o);
        if (uid == null) {
            return List.of();
        }
        return switch (types.get(types.resolve(uid))) {
            case Info.Ref r -> switch (kind(r.target())) {
                case RECORD -> record(r.target(), o, designator);
                case OPEN, ARRAY -> List.of();
                case BIGSET -> List.of(new Part("^", r.target(), refStore(r.target(), o), designator + "^"));
                default -> List.of(new Part("^", r.target(), plain(r.target(), get(o, "v"), fields(o).loc("v")),
                                            designator + "^"));
            };
            case Info.ObjectType ob -> objectFields(uid, o, designator);
            case null, default -> List.of();
        };
    }

    private List<Part> record(String t, Value x) {
        return x instanceof ObjectReference o ? record(t, o, "") : List.of();
    }

    /** The fields of the record of type t that the object o holds. */
    private List<Part> record(String t, ObjectReference o, String designator) {
        if (!(types.get(types.resolve(t)) instanceof Info.Record r)) {
            return List.of();
        }
        return fieldParts(r.fields(), o, designator);
    }

    /** The fields of the object o of type uid, those of its supertypes first. */
    private List<Part> objectFields(String uid, ObjectReference o, String designator) {
        Deque<List<Info.Field>> levels = new ArrayDeque<>();
        String t = uid;
        for (int i = 0; i < 100 && types.get(types.resolve(t)) instanceof Info.ObjectType ob; i++) {
            levels.addFirst(ob.fields());
            t = ob.supertype();
        }
        List<Part> out = new ArrayList<>();
        for (List<Info.Field> fs : levels) {
            out.addAll(fieldParts(fs, o, designator));
        }
        return out;
    }

    private List<Part> fieldParts(List<Info.Field> fs, ObjectReference o, String designator) {
        Home h = fields(o);
        List<Part> out = new ArrayList<>();
        for (Info.Field f : fs) {
            out.add(new Part(f.name(), f.type(), field(f.type(), h, f.name()), designator + "." + f.name()));
        }
        return out;
    }

    /** A field of type t: an array or a large set in it is storage of its own, from 0. */
    private V field(String t, Home h, String name) {
        if (!h.has(name)) {
            return new None("not available");
        }
        Kind k = kind(t);
        Value v = h.get(name);
        if (k == Kind.ARRAY || k == Kind.BIGSET) {
            return v instanceof ArrayReference a ? new Store(a, 0, new int[0]) : new None("no storage");
        }
        return plain(t, v, h.loc(name));
    }

    /** Elements start.. (count of them, 0: all) of the array s of type t. */
    private List<Part> elements(String t, Store s, String designator, int start, int count) {
        int n = count(t, s);
        int from = Math.max(0, Math.min(start, n));
        int to = count <= 0 ? n : Math.min(n, from + count);
        List<Part> out = new ArrayList<>();
        if (from >= to) {
            return out;
        }
        Info.Type d = types.get(types.resolve(t));
        String elem;
        String index;
        long size;
        int[] rest;
        if (d instanceof Info.OpenArray a) {
            elem = a.element();
            index = null;
            rest = java.util.Arrays.copyOfRange(s.dims(), 1, s.dims().length);
            size = slots(innermost(elem));
            for (int x : rest) {
                size *= x;
            }
        } else {
            Info.Array a = (Info.Array) d;
            elem = a.element();
            index = a.index();
            rest = new int[0];
            size = slots(elem);
        }
        Kind ek = kind(elem);
        List<Value> vs = ek == Kind.ARRAY || ek == Kind.OPEN || ek == Kind.BIGSET ? null
            : s.storage().getValues(s.offset() + from, to - from);
        for (int i = from; i < to; i++) {
            String ix = index == null ? Integer.toString(i) : types.ordinal(index, first(index) + i);
            V v = vs == null ? new Store(s.storage(), (int) (s.offset() + i * size), rest)
                : plain(elem, vs.get(i - from), elementLoc(s.storage(), s.offset() + i));
            out.add(new Part("[" + ix + "]", elem, v, designator + "[" + ix + "]"));
        }
        return out;
    }

    /** The element type of t below its open dimensions. */
    private String innermost(String t) {
        String e = t;
        for (int i = 0; i < 100 && types.get(types.resolve(e)) instanceof Info.OpenArray a; i++) {
            e = a.element();
        }
        return e;
    }

    // ---- designators

    /** The field name of p: a record, a reference to one, an object. */
    Part field(Part p, String name) {
        V v = usable(p);
        Kind k = kind(p.type());
        if (k == Kind.RECORD || k == Kind.REF || k == Kind.OTHER) {
            if (v instanceof Plain(Value x, _) && x == null) {
                throw new IllegalArgumentException(p.designator() + " is NIL");
            }
            for (Part f : parts(p.type(), v, p.designator())) {
                if (f.name().equals(name)) {
                    return f;
                }
            }
        }
        throw new IllegalArgumentException(p.designator() + " has no field " + name);
    }

    /** The referent of p, a reference. */
    Part deref(Part p) {
        V v = usable(p);
        if (kind(p.type()) == Kind.REF && v instanceof Plain(Value x, _)) {
            if (x == null) {
                throw new IllegalArgumentException(p.designator() + " is NIL");
            }
            String uid = x instanceof ObjectReference o && !(x instanceof StringReference) ? dynamicUid(o) : null;
            if (uid != null && types.get(types.resolve(uid)) instanceof Info.Ref r) {
                ObjectReference o = (ObjectReference) x;
                String d = p.designator() + "^";
                return switch (kind(r.target())) {
                    case RECORD -> new Part("^", r.target(), new Plain(o), d);
                    case OPEN, ARRAY, BIGSET -> new Part("^", r.target(), refStore(r.target(), o), d);
                    default -> new Part("^", r.target(), plain(r.target(), get(o, "v"), fields(o).loc("v")), d);
                };
            }
        }
        throw new IllegalArgumentException(p.designator() + " is not a reference to dereference");
    }

    /**
     * The array that p is, or that p refers to; fails if it is neither. A
     * reference to an array is dereferenced, as in Modula-3.
     */
    private Part array(Part p) {
        V v = usable(p);
        Kind k = kind(p.type());
        if (v instanceof Store && (k == Kind.ARRAY || k == Kind.OPEN)) {
            return p;
        }
        if (k == Kind.REF && v instanceof Plain(ObjectReference o, _) && !(o instanceof StringReference)) {
            String uid = dynamicUid(o);
            if (uid != null && types.get(types.resolve(uid)) instanceof Info.Ref r
                && (kind(r.target()) == Kind.OPEN || kind(r.target()) == Kind.ARRAY)) {
                return deref(p);
            }
        }
        if (v instanceof Plain(Value x, _) && x == null && k == Kind.REF) {
            throw new IllegalArgumentException(p.designator() + " is NIL");
        }
        throw new IllegalArgumentException(p.designator() + " is not an array");
    }

    /** The type of the index of the array p (or that p refers to): null for an open array. */
    String indexType(Part p) {
        Part a = array(p);
        return types.get(types.resolve(a.type())) instanceof Info.Array x ? x.index() : null;
    }

    /** The element of the array p (or that p refers to) at the index with the ordinal value index. */
    Part element(Part p, long index) {
        Part a = array(p);
        Store s = (Store) a.value();
        String it = indexType(a);
        long lo = it == null ? 0 : first(it);
        int n = count(a.type(), s);
        if (index < lo || index - lo >= n) {
            String shown = it == null ? Long.toString(index) : types.ordinal(it, index);
            String range = n == 0 ? "it is empty"
                : "[" + (it == null ? "0" : types.ordinal(it, lo)) + ".."
                  + (it == null ? Integer.toString(n - 1) : types.ordinal(it, lo + n - 1)) + "]";
            throw new IllegalArgumentException("the index " + shown + " of " + p.designator()
                                               + " is out of range: " + range);
        }
        Part e = elements(a.type(), s, a.designator(), (int) (index - lo), 1).getFirst();
        return new Part(e.name(), e.type(), e.value(), p.designator() + e.name());
    }

    /** The ordinal value of p. */
    long ordinalValue(Part p) {
        V v = usable(p);
        if (v instanceof Const c) {
            return c.value();
        }
        if (kind(p.type()) == Kind.ORDINAL && v instanceof Plain(PrimitiveValue x, _)) {
            return ordinal(p.type(), x);
        }
        throw new IllegalArgumentException(p.designator() + " is not of an ordinal type");
    }

    /** FIRST, LAST or NUMBER (op) of p: an array (or what p refers to), or a variable of an ordinal type. */
    Part attribute(String op, Part p) {
        String d = op + " (" + p.designator() + ")";
        if (kind(p.type()) == Kind.ORDINAL && !(p.value() instanceof None)) {
            long[] b = bounds(p.type());
            return switch (op) {
                case "FIRST" -> new Part(d, p.type(), new Const(b[0]), d);
                case "LAST" -> new Part(d, p.type(), new Const(b[1]), d);
                default -> new Part(d, "CARDINAL", b[1] - b[0] == Long.MAX_VALUE || b[1] < b[0] ? new None("too many")
                                        : new Const(b[1] - b[0] + 1), d);
            };
        }
        Part a = array(p);
        String it = indexType(a);
        int n = count(a.type(), (Store) a.value());
        long lo = it == null ? 0 : first(it);
        String t = it == null ? "INTEGER" : it;
        return switch (op) {
            case "FIRST" -> new Part(d, t, new Const(lo), d);
            case "LAST" -> new Part(d, t, new Const(lo + n - 1), d);
            default -> new Part(d, "CARDINAL", new Const(n), d);
        };
    }

    /** The first and the last value of the ordinal type t. */
    private long[] bounds(String t) {
        String r = types.resolve(t);
        return switch (r) {
            case "INTEGER", "LONGINT" -> new long[] {Long.MIN_VALUE, Long.MAX_VALUE};
            case "CARDINAL", "LONGCARD" -> new long[] {0, Long.MAX_VALUE};
            default -> {
                if (types.get(r) instanceof Info.Subrange s) {
                    yield new long[] {s.first(), s.last()};
                }
                yield new long[] {0, number(r) - 1};
            }
        };
    }

    /** The value of p, if it has one. */
    private static V usable(Part p) {
        if (p.value() instanceof None n) {
            throw new IllegalArgumentException(p.designator() + ": " + n.why());
        }
        return p.value();
    }

    // ---- setting

    /** Sets p to the value that text writes (a literal of its type), and returns p with its new value. */
    Part set(Part p, String text) {
        V v = usable(p);
        Kind k = kind(p.type());
        switch (k) {
            case RECORD, ARRAY, OPEN, SET, BIGSET -> throw new IllegalArgumentException(
                "a value of type " + types.show(p.type()) + " cannot be set as a whole, only its parts");
            default -> {
            }
        }
        if (!(v instanceof Plain pl) || pl.loc() == null) {
            throw new IllegalArgumentException(p.designator() + " cannot be set");
        }
        Loc loc = pl.loc();
        String sig = loc.signature();
        VirtualMachine vm = loc.vm();
        Value x;
        StringReference made = null;
        switch (k) {
            case ORDINAL -> {
                long n = Literal.ordinal(types, p.type(), text);
                long[] b = bounds(p.type());
                if (n < b[0] || n > b[1]) {
                    throw new IllegalArgumentException(
                        "the value is out of the range of " + types.show(p.type()) + ": ["
                        + types.ordinal(p.type(), b[0]) + ".." + types.ordinal(p.type(), b[1]) + "]");
                }
                x = switch (sig) {
                    case "J" -> vm.mirrorOf(n);
                    case "I" -> vm.mirrorOf((int) n);
                    case "S" -> vm.mirrorOf((short) n);
                    case "B" -> vm.mirrorOf((byte) n);
                    case "C" -> vm.mirrorOf((char) n);
                    case "Z" -> vm.mirrorOf(n != 0);
                    default -> throw new IllegalArgumentException(p.designator() + " cannot be set");
                };
            }
            case FLOAT -> {
                double d = Literal.real(text);
                x = switch (sig) {
                    case "D" -> vm.mirrorOf(d);
                    case "F" -> {
                        float f = (float) d;
                        if (Float.isInfinite(f) && !Double.isInfinite(d)) {
                            throw new IllegalArgumentException("the value is out of the range of REAL");
                        }
                        yield vm.mirrorOf(f);
                    }
                    default -> throw new IllegalArgumentException(p.designator() + " cannot be set");
                };
            }
            case TEXT -> {
                String s = Literal.text(text);
                if (s != null) {
                    made = vm.mirrorOf(s);
                    made.disableCollection();
                }
                x = made;
            }
            default -> {
                if (!text.strip().equals("NIL")) {
                    throw new IllegalArgumentException("only NIL can be assigned to " + p.designator()
                                                       + " by the debugger");
                }
                x = null;
            }
        }
        try {
            loc.set(x);
        } catch (InvalidTypeException | ClassNotLoadedException e) {
            throw new IllegalArgumentException(p.designator() + " cannot be set: " + e.getMessage());
        } finally {
            if (made != null) {
                made.enableCollection();
            }
        }
        return new Part(p.name(), p.type(), new Plain(loc.get(), loc), p.designator());
    }
}
