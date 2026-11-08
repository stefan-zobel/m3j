// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

import m3j.front.Bool;
import m3j.front.Charr;
import m3j.front.EnumType;
import m3j.front.Addr;
import m3j.front.Int;
import m3j.front.Null;
import m3j.front.ObjectAdr;
import m3j.front.ObjectRef;
import m3j.front.Reff;
import m3j.front.Module;
import m3j.front.OpaqueType;
import m3j.front.Scope;
import m3j.front.Value;
import m3j.front.LInt;
import m3j.front.SetType;
import m3j.front.SubrangeType;
import m3j.front.Textt;
import m3j.front.Type;
import m3j.front.WCharr;
import m3j.middle.TInt;

/**
 * The JVM representation of a Modula-3 type: its kind, the JVM type of a
 * value on the operand stack and in locals, and the descriptor of a field
 * or parameter of the type.
 *
 * <p>Ordinal types whose values fit in {@code int} are {@code int} on the
 * stack; BOOLEAN is {@code boolean}, CHAR and WIDECHAR are {@code char}
 * in descriptors. INTEGER is {@code long}, REAL {@code float}, LONGREAL and
 * EXTENDED {@code double}; references, records and arrays are objects.
 */
final class JType {

    enum Kind {
        /** BOOLEAN */
        BOOL,
        /** CHAR, its subranges */
        CHAR,
        /** WIDECHAR, its subranges */
        WCHAR,
        /** other enumerations and their subranges, INTEGER subranges in int */
        INT,
        /** INTEGER, LONGINT, CARDINAL, large subranges */
        LONG,
        FLOAT,
        DOUBLE,
        /** sets of up to 64 elements: a bit mask */
        SET,
        /** larger sets: long[] */
        BIGSET,
        TEXT,
        /** REFANY, ROOT, NULL, ADDRESS: Object */
        ANYREF,
        /** REF T, object types, opaque types: a class */
        REF,
        PROC,
        RECORD,
        ARRAY,
        OPENARRAY,
        VOID
    }

    final Kind kind;
    final ClassDesc desc;
    /** the bounds of an ordinal type */
    final long min, max;

    private JType(Kind kind, ClassDesc desc, long min, long max) {
        this.kind = kind;
        this.desc = desc;
        this.min = min;
        this.max = max;
    }

    static final ClassDesc CD_String = ConstantDescs.CD_String;
    static final ClassDesc CD_Object = ConstantDescs.CD_Object;

    static final JType VOID = new JType(Kind.VOID, ConstantDescs.CD_void, 0, 0);
    static final JType LONG = new JType(Kind.LONG, ConstantDescs.CD_long, Long.MIN_VALUE, Long.MAX_VALUE);
    static final JType BOOL = new JType(Kind.BOOL, ConstantDescs.CD_boolean, 0, 1);
    static final JType FLOAT = new JType(Kind.FLOAT, ConstantDescs.CD_float, 0, 0);
    static final JType DOUBLE = new JType(Kind.DOUBLE, ConstantDescs.CD_double, 0, 0);
    static final JType TEXT = new JType(Kind.TEXT, CD_String, 0, 0);
    static final JType ANYREF = new JType(Kind.ANYREF, CD_Object, 0, 0);
    /** Procedures: an object of the functional interface of their descriptor. */
    static final JType PROC = new JType(Kind.PROC, CD_Object, 0, 0);
    static final JType INT = new JType(Kind.INT, ConstantDescs.CD_int, Integer.MIN_VALUE, Integer.MAX_VALUE);

    static JType longType(long min, long max) {
        if (min == Long.MIN_VALUE && max == Long.MAX_VALUE) {
            return LONG;
        }
        return new JType(Kind.LONG, ConstantDescs.CD_long, min, max);
    }

    static JType intType(long min, long max) {
        return new JType(Kind.INT, ConstantDescs.CD_int, min, max);
    }

    /** The JVM type of a value on the operand stack. */
    TypeKind typeKind() {
        return switch (kind) {
            case BOOL, CHAR, WCHAR, INT -> TypeKind.INT;
            case LONG, SET -> TypeKind.LONG;
            case FLOAT -> TypeKind.FLOAT;
            case DOUBLE -> TypeKind.DOUBLE;
            case VOID -> TypeKind.VOID;
            default -> TypeKind.REFERENCE;
        };
    }

    boolean isIntLike() {
        return typeKind() == TypeKind.INT;
    }

    boolean isOrdinal() {
        return switch (kind) {
            case BOOL, CHAR, WCHAR, INT, LONG -> true;
            default -> false;
        };
    }

    /** The representation of type t. */
    static JType of(Type.T t) {
        if (t == null) {
            return VOID;
        }
        Type.T u = Type.stripPacked(Type.check(t));
        switch (u.info.class_) {
            case Integer, Longint:
                return LONG;
            case Real:
                return FLOAT;
            case Longreal, Extended:
                return DOUBLE;
            case Enum:
                return ofEnum(u, 0, EnumType.numElts(u) - 1);
            case Subrange: {
                int[] lo = TInt.newInt(), hi = TInt.newInt();
                SubrangeType.split(u, lo, hi);
                long min = toLong(lo), max = toLong(hi);
                Type.T b = Type.base(u);
                if (b.info.class_ == Type.Class.Enum) {
                    return ofEnum(Type.stripPacked(b), min, max);
                }
                if (min >= Integer.MIN_VALUE && max <= Integer.MAX_VALUE && min <= max) {
                    return intType(min, max);
                }
                return new JType(Kind.LONG, ConstantDescs.CD_long, min, max);
            }
            case Set:
                if (SetType.isSmallSet(u)) {
                    return new JType(Kind.SET, ConstantDescs.CD_long, 0, 0);
                }
                return new JType(Kind.BIGSET, ConstantDescs.CD_long.arrayType(), 0, 0);
            case Opaque, Object, Ref:
                if (Type.isEqual(u, Textt.T, null)) {
                    return TEXT;
                }
                return refType(u);
            case Procedure:
                return PROC;
            case Record:
                return new JType(Kind.RECORD, Types.classOf(u), 0, 0);
            case Array: {
                Aggregates.Leaf l = Aggregates.leaf(u);
                return new JType(Kind.ARRAY, l.arrayDesc(), 0, 0);
            }
            case OpenArray: {
                Aggregates.Leaf l = Aggregates.leaf(Aggregates.nonOpenElement(u));
                return new JType(Kind.OPENARRAY, l.arrayDesc(), 0, 0);
            }
            default:
                return ANYREF;
        }
    }

    private static JType ofEnum(Type.T e, long min, long max) {
        if (e == Type.stripPacked(Type.check(Bool.T))) {
            return min == 0 && max == 1 ? BOOL : new JType(Kind.BOOL, ConstantDescs.CD_boolean, min, max);
        }
        if (e == Type.stripPacked(Type.check(Charr.T))) {
            return new JType(Kind.CHAR, ConstantDescs.CD_char, min, max);
        }
        if (e == Type.stripPacked(Type.check(WCharr.T))) {
            return new JType(Kind.WCHAR, ConstantDescs.CD_char, min, max);
        }
        return intType(min, max);
    }

    /**
     * REFANY, ROOT and NULL are Object. An opaque type declared in an
     * interface that the runtime provides is the nested class of the same
     * name of the runtime class (Thread.T is m3.i.Thread$T). Other reference types
     * are Object for now.
     */
    private static JType refType(Type.T u) {
        if (u == strip(m3j.front.Mutex.T)) {
            return new JType(Kind.REF, Types.CD_Mutex, 0, 0);
        }
        if (u == strip(Reff.T) || u == strip(Null.T) || u == strip(ObjectRef.T)
                || u == strip(ObjectAdr.T) || u == strip(Addr.T)) {
            return ANYREF;
        }
        ClassDesc pc = providedClass(u);
        if (pc != null) {
            return new JType(Kind.REF, pc, 0, 0);
        }
        if (u.info.class_ == Type.Class.Ref && Types.target(u) == null) {
            return ANYREF;
        }
        return new JType(Kind.REF, Types.classOf(u), 0, 0);
    }

    /**
     * The runtime class of an opaque type declared in an interface that the
     * runtime provides (Thread.T is m3.i.Thread$T), or null.
     */
    static ClassDesc providedClass(Type.T u) {
        if (u instanceof OpaqueType.P op && op.declared != null) {
            Value.T home = Scope.toUnit(op.declared);
            if (home instanceof Module.T m && m.interface_) {
                Class<?> c = Unit.provided(Code.id(Module.name(m)));
                if (c != null) {
                    String n = c.getName() + "$" + Code.id(op.declared.name);
                    try {
                        Class.forName(n, false, c.getClassLoader());
                        return ClassDesc.of(n);
                    } catch (ClassNotFoundException e) {
                        // not provided
                    }
                }
            }
        }
        return null;
    }

    private static Type.T strip(Type.T t) {
        return t == null ? null : Type.stripPacked(Type.check(t));
    }

    /** t with another descriptor. */
    static JType withDesc(JType t, ClassDesc d) {
        return new JType(t.kind, d, t.min, t.max);
    }

    static JType ref(ClassDesc cd) {
        return new JType(Kind.REF, cd, 0, 0);
    }

    static long toLong(int[] v) {
        long[] r = new long[1];
        if (!TInt.toInt(v, r)) {
            throw new IllegalStateException("integer out of range");
        }
        return r[0];
    }

    /** True if t is INTEGER or LONGINT (or a subrange with their range). */
    static boolean isWholeInteger(Type.T t) {
        JType j = of(t);
        return j.kind == Kind.LONG && j.min == Long.MIN_VALUE && j.max == Long.MAX_VALUE;
    }

    static boolean isBaseInteger(Type.T t) {
        Type.T b = Type.base(t);
        return b == Type.base(Int.T) || b == Type.base(LInt.T);
    }

    @Override
    public String toString() {
        return kind + (isOrdinal() ? "[" + min + ".." + max + "]" : "");
    }
}
