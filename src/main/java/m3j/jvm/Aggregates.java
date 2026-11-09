// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;

import m3j.front.ArrayType;
import m3j.front.OpenArrayType;
import m3j.front.Type;

/**
 * Storage of structured values. A fixed array is flat, row-major storage
 * of its leaf elements (ARRAY [0..9] OF ARRAY [0..9] OF INTEGER is a
 * long[100]); a designator of an array is the storage and an offset. An
 * open array is the storage, an offset and its open dimensions. A large
 * set is an array of words, stored like an array of that many words.
 * Records are objects; arrays of records are arrays of those objects, all
 * created with the array.
 */
final class Aggregates {
    private Aggregates() {}

    /** How the leaf elements of an array are stored. */
    enum Store {
        Z(TypeKind.BOOLEAN), B(TypeKind.BYTE), UB(TypeKind.BYTE), S(TypeKind.SHORT), C(TypeKind.CHAR),
        I(TypeKind.INT), J(TypeKind.LONG), F(TypeKind.FLOAT), D(TypeKind.DOUBLE), A(TypeKind.REFERENCE);

        final TypeKind kind;

        Store(TypeKind kind) {
            this.kind = kind;
        }
    }

    /** The leaf elements of a type: their storage, class, and number per value. */
    record Leaf(Store store, ClassDesc elem, JType type, Type.T leafType, long slots) {
        ClassDesc arrayDesc() {
            return elem.arrayType();
        }
    }

    /** True if values of the type are owned objects (records, arrays, large sets). */
    static boolean isOwned(JType t) {
        return t.kind == JType.Kind.RECORD || t.kind == JType.Kind.ARRAY || t.kind == JType.Kind.BIGSET;
    }

    /** The number of words of a large set. */
    static long setWords(Type.T t) {
        long size = Type.check(t).info.size;
        return (size + 63) / 64;
    }

    static Type.T strip(Type.T t) {
        return Type.stripPacked(Type.check(t));
    }

    /** The leaf storage of type t (an array type, or any other type as one leaf). */
    static Leaf leaf(Type.T t) {
        Type.T u = strip(t);
        switch (u.info.class_) {
            case Array -> {
                Type.T[] idx = new Type.T[1], elt = new Type.T[1];
                ArrayType.split(u, idx, elt);
                Leaf e = leaf(elt[0]);
                long n = JType.toLong(Type.number(idx[0]));
                return new Leaf(e.store, e.elem, e.type, e.leafType, n * e.slots);
            }
            case OpenArray -> {
                Leaf e = leaf(((OpenArrayType.P) u).EltType);
                return new Leaf(e.store, e.elem, e.type, e.leafType, e.slots);
            }
            default -> {
                JType j = JType.of(u);
                if (j.kind == JType.Kind.BIGSET) {
                    return new Leaf(Store.J, ConstantDescs.CD_long, JType.LONG, u, setWords(u));
                }
                Store s = store(j);
                ClassDesc elem = switch (s) {
                    case Z -> ConstantDescs.CD_boolean;
                    case B, UB -> ConstantDescs.CD_byte;
                    case S -> ConstantDescs.CD_short;
                    case C -> ConstantDescs.CD_char;
                    case I -> ConstantDescs.CD_int;
                    case J -> ConstantDescs.CD_long;
                    case F -> ConstantDescs.CD_float;
                    case D -> ConstantDescs.CD_double;
                    case A -> j.desc;
                };
                return new Leaf(s, elem, j, u, 1);
            }
        }
    }

    /** The storage of a scalar of representation j as an array element. */
    static Store store(JType j) {
        return switch (j.kind) {
            case BOOL -> Store.Z;
            case CHAR -> Store.UB;
            case WCHAR -> Store.C;
            case INT -> {
                if (j.min >= -128 && j.max <= 127) {
                    yield Store.B;
                } else if (j.min >= 0 && j.max <= 255) {
                    yield Store.UB;
                } else if (j.min >= -32768 && j.max <= 32767) {
                    yield Store.S;
                } else if (j.min >= 0 && j.max <= 65535) {
                    yield Store.C;
                }
                yield Store.I;
            }
            case LONG, SET -> Store.J;
            case FLOAT -> Store.F;
            case DOUBLE -> Store.D;
            default -> Store.A;
        };
    }

    /** Loads an element: stack (array, index) -> value. */
    static void arrayLoad(CodeBuilder cb, Store s) {
        cb.arrayLoad(s.kind);
        if (s == Store.UB) {
            cb.loadConstant(0xFF);
            cb.iand();
        }
    }

    /** Stores an element: stack (array, index, value). */
    static void arrayStore(CodeBuilder cb, Store s) {
        cb.arrayStore(s.kind);
    }

    /** The open dimensions of an open array type, and its non-open element. */
    static int openDepth(Type.T t) {
        Type.T u = strip(t);
        if (u.info.class_ == Type.Class.OpenArray) {
            return 1 + openDepth(((OpenArrayType.P) u).EltType);
        }
        return 0;
    }

    static Type.T nonOpenElement(Type.T t) {
        Type.T u = strip(t);
        while (u.info.class_ == Type.Class.OpenArray) {
            u = strip(((OpenArrayType.P) u).EltType);
        }
        return u;
    }

    // ------------------------------------------------------------ new values

    /** Pushes a new value of an owned type (a record, a fixed array, a large set). */
    static void newValue(CodeBuilder cb, Type.T t) {
        JType j = JType.of(t);
        switch (j.kind) {
            case RECORD -> {
                ClassDesc cd = Types.classOf(t);
                cb.new_(cd);
                cb.dup();
                cb.invokespecial(cd, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
            }
            case BIGSET -> {
                cb.loadConstant((int) setWords(t));
                cb.newarray(TypeKind.LONG);
            }
            case ARRAY -> {
                Leaf l = leaf(t);
                cb.loadConstant((int) l.slots());
                newStorage(cb, l);
            }
            default -> Code.pushDefault(cb, j);
        }
    }

    /** Pushes new storage for the leaves of l; stack: (count) -> (array). */
    static void newStorage(CodeBuilder cb, Leaf l) {
        if (l.store() == Store.A) {
            cb.anewarray(l.elem());
        } else {
            cb.newarray(l.store().kind);
        }
        if (l.type().kind == JType.Kind.RECORD || Types.needsDefault(l.type())) {
            // fill: records get their objects, subranges their first value
            fill(cb, l);
        }
    }

    /** Fills the array on the stack (kept on the stack) with initial elements. */
    static void fill(CodeBuilder cb, Leaf l) {
        Label top = cb.newLabel(), test = cb.newLabel();
        int arr = cb.allocateLocal(TypeKind.REFERENCE);
        int i = cb.allocateLocal(TypeKind.INT);
        cb.dup();
        cb.astore(arr);
        cb.iconst_0();
        cb.istore(i);
        cb.goto_(test);
        cb.labelBinding(top);
        cb.aload(arr);
        cb.iload(i);
        if (l.type().kind == JType.Kind.RECORD) {
            newValue(cb, l.leafType());
        } else {
            Code.pushDefault(cb, l.type());
        }
        arrayStore(cb, l.store());
        cb.iinc(i, 1);
        cb.labelBinding(test);
        cb.iload(i);
        cb.aload(arr);
        cb.arraylength();
        cb.if_icmplt(top);
    }

    // ------------------------------------------------------------ copies

    /** Copies a whole owned value: stack (src, dst) -> (). */
    static void copyInto(CodeBuilder cb, Type.T t) {
        JType j = JType.of(t);
        switch (j.kind) {
            case RECORD -> {
                ClassDesc cd = Types.classOf(t);
                cb.swap();
                cb.invokevirtual(cd, "copyFrom", MethodTypeDesc.of(ConstantDescs.CD_void, cd));
            }
            case ARRAY, BIGSET -> {
                Leaf l = j.kind == JType.Kind.BIGSET
                    ? new Leaf(Store.J, ConstantDescs.CD_long, JType.LONG, t, setWords(t)) : leaf(t);
                int dst = cb.allocateLocal(TypeKind.REFERENCE);
                cb.astore(dst);
                cb.iconst_0();
                cb.aload(dst);
                cb.iconst_0();
                cb.loadConstant((int) l.slots());
                copyRange(cb, l);
            }
            default -> {
                cb.pop();
                cb.pop();
            }
        }
    }

    /**
     * Copies leaves: stack (src, srcOffset, dst, dstOffset, count) -> ().
     * Records are copied element by element.
     */
    static void copyRange(CodeBuilder cb, Leaf l) {
        if (l.type().kind == JType.Kind.RECORD) {
            ClassDesc rc = l.elem();
            cb.invokestatic(Code.RT, "copyRecords", MethodTypeDesc.of(ConstantDescs.CD_void,
                ConstantDescs.CD_Object.arrayType(), ConstantDescs.CD_int,
                ConstantDescs.CD_Object.arrayType(), ConstantDescs.CD_int, ConstantDescs.CD_int));
            return;
        }
        cb.invokestatic(ClassDesc.of("java.lang.System"), "arraycopy", MethodTypeDesc.of(ConstantDescs.CD_void,
            ConstantDescs.CD_Object, ConstantDescs.CD_int, ConstantDescs.CD_Object, ConstantDescs.CD_int,
            ConstantDescs.CD_int));
    }

    // ------------------------------------------------------------ equality

    /**
     * Compares two values of type t on the stack; jumps to no if they differ.
     * Numbers of REAL types compare as numbers if numeric (0.0 = -0.0, a NaN
     * equals nothing), else as their bits.
     */
    static void equalsJump(CodeBuilder cb, Type.T t, JType j, Label no, boolean numeric) {
        switch (j.typeKind()) {
            case INT -> cb.if_icmpne(no);
            case LONG -> {
                cb.lcmp();
                cb.ifne(no);
            }
            case FLOAT -> {
                if (numeric) {
                    cb.fcmpl();
                } else {
                    cb.invokestatic(ClassDesc.of("java.lang.Float"), "compare", MethodTypeDesc.of(
                        ConstantDescs.CD_int, ConstantDescs.CD_float, ConstantDescs.CD_float));
                }
                cb.ifne(no);
            }
            case DOUBLE -> {
                if (numeric) {
                    cb.dcmpl();
                } else {
                    cb.invokestatic(ClassDesc.of("java.lang.Double"), "compare", MethodTypeDesc.of(
                        ConstantDescs.CD_int, ConstantDescs.CD_double, ConstantDescs.CD_double));
                }
                cb.ifne(no);
            }
            default -> {
                switch (j.kind) {
                    case RECORD -> {
                        ClassDesc cd = Types.classOf(t);
                        cb.invokevirtual(cd, "eq", MethodTypeDesc.of(ConstantDescs.CD_boolean, cd));
                        cb.ifeq(no);
                    }
                    case ARRAY, BIGSET -> {
                        Leaf l = j.kind == JType.Kind.BIGSET
                            ? new Leaf(Store.J, ConstantDescs.CD_long, JType.LONG, t, setWords(t)) : leaf(t);
                        int b = cb.allocateLocal(TypeKind.REFERENCE);
                        cb.astore(b);
                        cb.iconst_0();
                        cb.aload(b);
                        cb.iconst_0();
                        cb.loadConstant((int) l.slots());
                        equalRange(cb, l, false);
                        cb.ifeq(no);
                    }
                    default -> cb.if_acmpne(no);
                }
            }
        }
    }

    /**
     * Compares leaves: stack (a, aOffset, b, bOffset, count) -> (boolean).
     * Leaves of REAL types compare as numbers if numeric, else as their bits.
     */
    static void equalRange(CodeBuilder cb, Leaf l, boolean numeric) {
        ClassDesc arr = l.store() == Store.A ? ConstantDescs.CD_Object.arrayType() : l.arrayDesc();
        TypeKind k = l.type().typeKind();
        boolean real = k == TypeKind.FLOAT || k == TypeKind.DOUBLE;
        cb.invokestatic(Code.RT, l.type().kind == JType.Kind.RECORD ? "equalRecords"
                : numeric && real ? "equalNumbers" : "equal",
            MethodTypeDesc.of(ConstantDescs.CD_boolean, arr, ConstantDescs.CD_int, arr,
                ConstantDescs.CD_int, ConstantDescs.CD_int));
    }

    // ------------------------------------------------------ REF ARRAY OF T

    /**
     * The class of REF ARRAY OF ... T: the storage in the field a, the open
     * dimensions in n0, n1, ...; the constructor takes the dimensions.
     */
    static void openArrayRefClass(ClassDesc cd, Type.T open) {
        int depth = openDepth(open);
        Leaf l = leaf(nonOpenElement(open));
        List<ClassDesc> params = new ArrayList<>();
        for (int i = 0; i < depth; i++) {
            params.add(ConstantDescs.CD_int);
        }
        ClassDesc storage = l.store() == Store.A ? l.elem().arrayType() : l.arrayDesc();
        byte[] b = Types.classFile().build(cd, clb -> {
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SUPER);
            clb.withField("a", storage, ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            for (int i = 0; i < depth; i++) {
                clb.withField("n" + i, ConstantDescs.CD_int, ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            }
            clb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void, params), ClassFile.ACC_PUBLIC, cb -> {
                cb.aload(0);
                cb.invokespecial(ConstantDescs.CD_Object, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
                for (int i = 0; i < depth; i++) {
                    cb.aload(0);
                    cb.iload(1 + i);
                    cb.putfield(cd, "n" + i, ConstantDescs.CD_int);
                }
                cb.aload(0);
                // the number of leaves: n0 * n1 * ... * slots, checked
                cb.loadConstant(l.slots());
                for (int i = 0; i < depth; i++) {
                    cb.iload(1 + i);
                    cb.i2l();
                    cb.invokestatic(Code.RT, "dimension", MethodTypeDesc.of(ConstantDescs.CD_long,
                        ConstantDescs.CD_long, ConstantDescs.CD_long));
                }
                cb.invokestatic(Code.RT, "size", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_long));
                newStorage(cb, l);
                cb.putfield(cd, "a", storage);
                cb.return_();
            });
            if (depth == 1) {
                // the array that a Java method returned
                clb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void, storage), ClassFile.ACC_PUBLIC, cb -> {
                    cb.aload(0);
                    cb.invokespecial(ConstantDescs.CD_Object, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
                    cb.aload(0);
                    cb.aload(1);
                    cb.arraylength();
                    cb.loadConstant((int) l.slots());
                    cb.idiv();
                    cb.putfield(cd, "n0", ConstantDescs.CD_int);
                    cb.aload(0);
                    cb.aload(1);
                    cb.putfield(cd, "a", storage);
                    cb.return_();
                });
            }
        });
        Types.put(cd, ConstantDescs.CD_Object, b);
    }
}
