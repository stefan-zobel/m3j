// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

import m3j.front.Formal;
import m3j.front.ProcType;
import m3j.front.Type;
import m3j.front.Value;

/**
 * How parameters are passed. A formal becomes one or more JVM parameters:
 * <ul>
 * <li>a scalar passed by value or READONLY: its value;
 * <li>a scalar passed VAR: a reference object (LongRef, IntRef, ...);
 * <li>a record: the record object (VALUE: a copy made by the caller);
 * <li>a fixed array or a large set: the storage and an offset (VALUE: a
 * copy made by the caller);
 * <li>an open array: the storage, an offset and the open dimensions
 * (VALUE: copied by the callee).
 * </ul>
 */
final class Params {
    private Params() {}

    /** The JVM parameters of one formal. */
    record Layout(List<ClassDesc> descs, JType type) {
        int slots() {
            int n = 0;
            for (ClassDesc d : descs) {
                n += d.equals(ConstantDescs.CD_long) || d.equals(ConstantDescs.CD_double) ? 2 : 1;
            }
            return n;
        }

        /** The location of the formal in the callee, its first parameter in slot. */
        Loc loc(int slot, Formal.T f) {
            switch (type.kind) {
                case ARRAY, BIGSET -> {
                    return new Loc.Arr(new Loc.Local(slot, type), new Loc.Local(slot + 1, JType.INT),
                        new Loc[0], f.type, type);
                }
                case OPENARRAY -> {
                    int depth = descs.size() - 2;
                    Loc[] dims = new Loc[depth];
                    for (int i = 0; i < depth; i++) {
                        dims[i] = new Loc.Local(slot + 2 + i, JType.INT);
                    }
                    return new Loc.Arr(new Loc.Local(slot, type), new Loc.Local(slot + 1, JType.INT),
                        dims, f.type, type);
                }
                case RECORD -> {
                    return new Loc.Local(slot, type);
                }
                default -> {
                    if (f.mode != Formal.Mode.mVALUE) {
                        return new Loc.VarRef(new Loc.Local(slot, JType.ref(Refs.refClass(type))), type);
                    }
                    return new Loc.Local(slot, type);
                }
            }
        }
    }

    static Layout layout(Formal.T f) {
        JType t = JType.of(f.type);
        List<ClassDesc> d = new ArrayList<>();
        switch (t.kind) {
            case ARRAY, BIGSET -> {
                d.add(t.desc);
                d.add(ConstantDescs.CD_int);
            }
            case OPENARRAY -> {
                d.add(t.desc);
                d.add(ConstantDescs.CD_int);
                for (int i = 0; i < Aggregates.openDepth(f.type); i++) {
                    d.add(ConstantDescs.CD_int);
                }
            }
            case RECORD -> d.add(t.desc);
            default -> d.add(f.mode != Formal.Mode.mVALUE ? Refs.refClass(t) : t.desc);
        }
        return new Layout(d, t);
    }

    /**
     * The descriptor of a procedure of an interface that the runtime
     * provides: the classes of Modula-3 types (m3.t.*, m3.p.*) are Object.
     */
    static MethodTypeDesc erased(MethodTypeDesc d) {
        List<ClassDesc> ps = new ArrayList<>();
        for (ClassDesc c : d.parameterList()) {
            ps.add(erase(c));
        }
        return MethodTypeDesc.of(erase(d.returnType()), ps);
    }

    static ClassDesc erase(ClassDesc c) {
        String s = c.descriptorString();
        if (s.startsWith("Lm3/t/") || s.startsWith("Lm3/p/")) {
            return ConstantDescs.CD_Object;
        }
        return c;
    }

    /** The descriptor of a procedure with the given signature. */
    static MethodTypeDesc descriptor(Type.T signature) {
        Type.T sig = Type.base(signature);
        List<ClassDesc> params = new ArrayList<>();
        for (Value.T v = ProcType.formals(sig); v != null; v = v.next) {
            params.addAll(layout((Formal.T) v).descs());
        }
        JType r = JType.of(ProcType.result(sig));
        return MethodTypeDesc.of(r.desc, params);
    }
}
