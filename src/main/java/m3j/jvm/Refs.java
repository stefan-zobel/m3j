// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;

/**
 * VAR parameters of scalar types. Such a parameter is a reference object:
 * a subclass of LongRef, IntRef, FloatRef, DoubleRef or ObjRef of the
 * runtime. A variable passed by reference lives in a cell; an element of
 * an array is passed as an element reference; a field (of a record, an
 * object, a frame, or a static field) as a reference class generated for
 * that field.
 */
final class Refs {
    private Refs() {}

    static String prefix(JType t) {
        return switch (t.typeKind()) {
            case INT -> "Int";
            case LONG -> "Long";
            case FLOAT -> "Float";
            case DOUBLE -> "Double";
            default -> "Obj";
        };
    }

    static ClassDesc refClass(JType t) {
        return ClassDesc.of("m3j.rt." + prefix(t) + "Ref");
    }

    static ClassDesc cellClass(JType t) {
        return ClassDesc.of("m3j.rt." + prefix(t) + "Cell");
    }

    /** The JVM type of get and set. */
    static ClassDesc valueDesc(JType t) {
        return switch (t.typeKind()) {
            case INT -> ConstantDescs.CD_int;
            case LONG -> ConstantDescs.CD_long;
            case FLOAT -> ConstantDescs.CD_float;
            case DOUBLE -> ConstantDescs.CD_double;
            default -> ConstantDescs.CD_Object;
        };
    }

    /** After get: casts an Object to the type of the value. */
    static void narrow(CodeBuilder cb, JType t) {
        if (t.typeKind() == TypeKind.REFERENCE && !t.desc.equals(ConstantDescs.CD_Object)) {
            cb.checkcast(t.desc);
        }
    }

    /** Before set: nothing to do (an int, a long, a reference). */
    static void widen(CodeBuilder cb, JType t) {
    }

    /** The element reference class for a storage kind. */
    static ClassDesc elemClass(Aggregates.Store s) {
        return ClassDesc.of("m3j.rt." + switch (s) {
            case Z -> "BoolElem";
            case B -> "ByteElem";
            case UB -> "UByteElem";
            case S -> "ShortElem";
            case C -> "CharElem";
            case I -> "IntElem";
            case J -> "LongElem";
            case F -> "FloatElem";
            case D -> "DoubleElem";
            case A -> "ObjElem";
        });
    }

    static ClassDesc elemArrayDesc(Aggregates.Store s) {
        return switch (s) {
            case Z -> ConstantDescs.CD_boolean.arrayType();
            case B, UB -> ConstantDescs.CD_byte.arrayType();
            case S -> ConstantDescs.CD_short.arrayType();
            case C -> ConstantDescs.CD_char.arrayType();
            case I -> ConstantDescs.CD_int.arrayType();
            case J -> ConstantDescs.CD_long.arrayType();
            case F -> ConstantDescs.CD_float.arrayType();
            case D -> ConstantDescs.CD_double.arrayType();
            case A -> ConstantDescs.CD_Object.arrayType();
        };
    }

    /**
     * The reference class of a field of an object of class owner: its
     * constructor takes the object.
     */
    static ClassDesc fieldRef(ClassDesc owner, String field, JType t) {
        String o = owner.descriptorString();
        o = o.substring(1, o.length() - 1).replace('/', '_').replace('$', '_');
        ClassDesc cd = ClassDesc.of("m3.r." + o + "$" + field);
        String key = cd.descriptorString();
        if (!Types.out.containsKey(key)) {
            ClassDesc sup = refClass(t);
            byte[] b = ClassFile.of().build(cd, clb -> {
                clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SUPER);
                clb.withSuperclass(sup);
                clb.withField("o", owner, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
                clb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void, owner), ClassFile.ACC_PUBLIC, cb -> {
                    cb.aload(0);
                    cb.invokespecial(sup, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
                    cb.aload(0);
                    cb.aload(1);
                    cb.putfield(cd, "o", owner);
                    cb.return_();
                });
                clb.withMethodBody("get", MethodTypeDesc.of(valueDesc(t)), ClassFile.ACC_PUBLIC, cb -> {
                    cb.aload(0);
                    cb.getfield(cd, "o", owner);
                    cb.getfield(owner, field, t.desc);
                    cb.return_(TypeKind.from(valueDesc(t)));
                });
                clb.withMethodBody("set", MethodTypeDesc.of(ConstantDescs.CD_void, valueDesc(t)), ClassFile.ACC_PUBLIC, cb -> {
                    cb.aload(0);
                    cb.getfield(cd, "o", owner);
                    cb.loadLocal(TypeKind.from(valueDesc(t)), 1);
                    if (t.typeKind() == TypeKind.REFERENCE && !t.desc.equals(ConstantDescs.CD_Object)) {
                        cb.checkcast(t.desc);
                    }
                    cb.putfield(owner, field, t.desc);
                    cb.return_();
                });
            });
            Types.supers.put(cd, sup);
            Types.out.put(key, b);
        }
        return cd;
    }

    /** The reference class of a static field; its constructor takes nothing. */
    static ClassDesc staticRef(ClassDesc owner, String field, JType t) {
        String o = owner.descriptorString();
        o = o.substring(1, o.length() - 1).replace('/', '_').replace('$', '_');
        ClassDesc cd = ClassDesc.of("m3.r." + o + "$$" + field);
        String key = cd.descriptorString();
        if (!Types.out.containsKey(key)) {
            ClassDesc sup = refClass(t);
            byte[] b = ClassFile.of().build(cd, clb -> {
                clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SUPER);
                clb.withSuperclass(sup);
                clb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_PUBLIC, cb -> {
                    cb.aload(0);
                    cb.invokespecial(sup, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
                    cb.return_();
                });
                clb.withMethodBody("get", MethodTypeDesc.of(valueDesc(t)), ClassFile.ACC_PUBLIC, cb -> {
                    cb.getstatic(owner, field, t.desc);
                    cb.return_(TypeKind.from(valueDesc(t)));
                });
                clb.withMethodBody("set", MethodTypeDesc.of(ConstantDescs.CD_void, valueDesc(t)), ClassFile.ACC_PUBLIC, cb -> {
                    cb.loadLocal(TypeKind.from(valueDesc(t)), 1);
                    if (t.typeKind() == TypeKind.REFERENCE && !t.desc.equals(ConstantDescs.CD_Object)) {
                        cb.checkcast(t.desc);
                    }
                    cb.putstatic(owner, field, t.desc);
                    cb.return_();
                });
            });
            Types.supers.put(cd, sup);
            Types.out.put(key, b);
        }
        return cd;
    }

    /** Pushes a reference object for the scalar location l. */
    static void makeRef(Code c, Loc l) {
        CodeBuilder cb = c.cb;
        JType t = l.type();
        switch (l) {
            case Loc.VarRef v -> v.ref().load(cb);
            case Loc.Cell cell -> cell.holder().load(cb);
            case Loc.Field f -> {
                ClassDesc cd = fieldRef(f.owner(), f.name(), t);
                cb.new_(cd);
                cb.dup();
                f.obj().load(cb);
                cb.invokespecial(cd, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, f.owner()));
            }
            case Loc.Static s -> {
                ClassDesc cd = staticRef(s.owner(), s.name(), t);
                cb.new_(cd);
                cb.dup();
                cb.invokespecial(cd, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
            }
            case Loc.Elem e -> {
                ClassDesc cd = elemClass(e.store());
                cb.new_(cd);
                cb.dup();
                e.array().load(cb);
                e.index().load(cb);
                cb.invokespecial(cd, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void,
                    elemArrayDesc(e.store()), ConstantDescs.CD_int));
            }
            case Loc.Local loc -> {
                // a local that is not in a cell: a temporary cell (only for
                // values that cannot be aliased)
                ClassDesc cd = cellClass(t);
                cb.new_(cd);
                cb.dup();
                loc.load(cb);
                cb.invokespecial(cd, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, valueDesc(t)));
            }
            case Loc.FinalStatic f -> {
                f.reject();
                cb.aconst_null();
            }
            case Loc.FinalField f -> {
                f.reject();
                cb.aconst_null();
            }
            default -> c.unsupported("VAR actual " + l.getClass().getSimpleName());
        }
    }
}
