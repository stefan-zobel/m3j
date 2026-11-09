// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

import m3j.front.CallExpr;
import m3j.front.Expr;
import m3j.front.KeywordExpr;
import m3j.front.ObjectType;
import m3j.front.Module;
import m3j.front.NamedExpr;
import m3j.front.Procedure;
import m3j.front.QualifyExpr;
import m3j.front.Scope;
import m3j.front.Type;
import m3j.front.TypeExpr;
import m3j.front.Value;

/** Calls of the built-in procedures and of the procedures of Word and Long. */
final class Builtins {
    private Builtins() {}

    static final ClassDesc CD_Long = ClassDesc.of("java.lang.Long");
    static final ClassDesc CD_Math = ClassDesc.of("java.lang.Math");
    static final MethodTypeDesc J_JJ = MethodTypeDesc.of(ConstantDescs.CD_long,
        ConstantDescs.CD_long, ConstantDescs.CD_long);
    static final MethodTypeDesc I_JJ = MethodTypeDesc.of(ConstantDescs.CD_int,
        ConstantDescs.CD_long, ConstantDescs.CD_long);

    /** The name of the built-in procedure called by ce, such as "ABS" or "Word.Plus". */
    static String name(CallExpr.T ce) {
        Value.T v = null;
        int[] nm = new int[1];
        Value.T[] obj = new Value.T[1];
        if (NamedExpr.split(ce.proc, nm, obj)) {
            v = obj[0];
        } else if (QualifyExpr.split(ce.proc, obj)) {
            v = obj[0];
        }
        if (v == null) {
            return null;
        }
        v = Value.base(v);
        if (v instanceof m3j.front.Constant.T) {
            // a constant whose value is the built-in (CONST And = Word.And)
            Value.T[] p = new Value.T[1];
            if (m3j.front.ProcExpr.split(Expr.constValue(ce.proc), p) && p[0] != null) {
                v = Value.base(p[0]);
            }
        }
        String n = Code.id(v.name);
        Value.T home = Scope.toUnit(v);
        if (home instanceof Module.T m && v instanceof Procedure.T p && !p.builtin) {
            return Code.id(Module.name(m)) + "." + n;
        }
        return n;
    }

    static JType call(Code c, CallExpr.T ce, JType t) {
        String n = name(ce);
        if (n == null) {
            return c.unsupportedValue("call", t);
        }
        CodeBuilder cb = c.cb;
        Expr.T[] a = ce.args;
        if (n.startsWith("Word.") || n.startsWith("Long.")) {
            return word(c, n.substring(5), a, t);
        }
        switch (n) {
            case "ORD" -> {
                c.exprAs(a[0], JType.LONG, false);
                return JType.LONG;
            }
            case "VAL" -> {
                JType target = JType.of(typeArg(a[1]));
                c.exprAs(a[0], target, true);
                return target;
            }
            case "INC", "DEC" -> {
                incDec(c, a, n.equals("INC"));
                return JType.VOID;
            }
            case "ABS" -> {
                JType s = c.expr(a[0]);
                switch (s.typeKind()) {
                    case INT -> {
                        cb.i2l();
                        cb.invokestatic(CD_Math, "abs", MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_long));
                        return JType.LONG;
                    }
                    case LONG -> cb.invokestatic(CD_Math, "abs", MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_long));
                    // as cm3: ABS (x) = IF x < 0 THEN -x ELSE x END (not Math.abs: -0.0 stays -0.0)
                    case FLOAT -> cb.invokestatic(Code.RT, "abs", MethodTypeDesc.of(ConstantDescs.CD_float, ConstantDescs.CD_float));
                    case DOUBLE -> cb.invokestatic(Code.RT, "abs", MethodTypeDesc.of(ConstantDescs.CD_double, ConstantDescs.CD_double));
                    default -> {
                        return c.unsupportedValue("ABS", t);
                    }
                }
                return s;
            }
            case "MIN", "MAX" -> {
                JType ta = JType.of(Expr.typeOf(a[0]));
                if (ta.isOrdinal()) {
                    boolean isLong = t.typeKind() == TypeKind.LONG
                        || ta.typeKind() == TypeKind.LONG
                        || JType.of(Expr.typeOf(a[1])).typeKind() == TypeKind.LONG;
                    TypeKind k = isLong ? TypeKind.LONG : TypeKind.INT;
                    c.exprAs(a[0], isLong ? JType.LONG : JType.intType(Integer.MIN_VALUE, Integer.MAX_VALUE), false);
                    c.exprAs(a[1], isLong ? JType.LONG : JType.intType(Integer.MIN_VALUE, Integer.MAX_VALUE), false);
                    ClassDesc d = isLong ? ConstantDescs.CD_long : ConstantDescs.CD_int;
                    cb.invokestatic(CD_Math, n.equals("MIN") ? "min" : "max", MethodTypeDesc.of(d, d, d));
                    JType r = isLong ? JType.LONG : JType.intType(Integer.MIN_VALUE, Integer.MAX_VALUE);
                    if (k == TypeKind.INT && t.typeKind() == TypeKind.INT) {
                        return t;
                    }
                    return r;
                }
                if (Code.isFloat(ta)) {
                    c.expr(a[0]);
                    c.expr(a[1]);
                    ClassDesc d = ta.desc;
                    // as cm3: MIN (a, b) = IF a < b THEN a ELSE b END
                    cb.invokestatic(Code.RT, n.equals("MIN") ? "min" : "max", MethodTypeDesc.of(d, d, d));
                    return ta;
                }
                return c.unsupportedValue(n + " of " + ta.kind, t);
            }
            case "FLOAT" -> {
                JType target = a.length > 1 ? JType.of(typeArg(a[1])) : JType.FLOAT;
                JType s = c.expr(a[0]);
                TypeKind sk = s.typeKind(), tk = target.typeKind();
                if (sk == TypeKind.INT) {
                    cb.i2l();
                    sk = TypeKind.LONG;
                }
                if (sk == TypeKind.LONG) {
                    if (tk == TypeKind.FLOAT) cb.l2f(); else cb.l2d();
                } else if (sk == TypeKind.FLOAT && tk == TypeKind.DOUBLE) {
                    cb.f2d();
                } else if (sk == TypeKind.DOUBLE && tk == TypeKind.FLOAT) {
                    cb.d2f();
                }
                return target;
            }
            case "TRUNC", "ROUND", "FLOOR", "CEILING" -> {
                JType s = c.expr(a[0]);
                if (s.typeKind() == TypeKind.FLOAT) {
                    cb.f2d();
                }
                JType target = a.length > 1 ? JType.of(typeArg(a[1])) : JType.LONG;
                cb.invokestatic(Code.RT, n.toLowerCase(), MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_double));
                return target.typeKind() == TypeKind.LONG ? JType.LONG : JType.LONG;
            }
            case "FIRST", "LAST", "NUMBER" -> {
                Type.T[] tt = new Type.T[1];
                if (TypeExpr.split(a[0], tt)) {
                    return c.unsupportedValue(n + " of a type", t);
                }
                Loc.Arr x = c.arr(a[0]);
                Type.T u = Aggregates.strip(x.arrayType());
                if (u.info.class_ == Type.Class.OpenArray) {
                    if (n.equals("FIRST")) {
                        cb.lconst_0();
                    } else {
                        x.dims()[0].load(cb);
                        cb.i2l();
                        if (n.equals("LAST")) {
                            cb.lconst_1();
                            cb.lsub();
                        }
                    }
                    return JType.LONG;
                }
                return c.unsupportedValue(n + " of a non-constant", t);
            }
            case "NEW" -> {
                return newCall(c, ce);
            }
            case "TYPECODE" -> {
                ClassDesc tc = ClassDesc.of("m3j.rt.Typecodes");
                Type.T[] tt = new Type.T[1];
                if (TypeExpr.split(a[0], tt)) {
                    Type.T u = Aggregates.strip(tt[0]);
                    if (Type.isEqual(u, m3j.front.Null.T, null)) {
                        cb.lconst_0();
                    } else {
                        JType jt = JType.of(u);
                        ClassDesc cd = jt.kind == JType.Kind.TEXT ? ConstantDescs.CD_String
                            : u == Aggregates.strip(m3j.front.ObjectRef.T) ? Types.CD_M3Object : Types.classOf(u);
                        cb.loadConstant(cd);
                        cb.invokestatic(tc, "of", MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_Class));
                    }
                } else {
                    c.expr(a[0]);
                    cb.invokestatic(tc, "ofValue", MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_Object));
                }
                return JType.LONG;
            }
            case "BITSIZE", "BYTESIZE", "ADRSIZE" -> {
                Type.T[] tt = new Type.T[1];
                Type.T u = TypeExpr.split(a[0], tt) ? tt[0] : Expr.typeOf(a[0]);
                long bits = Type.check(u).info.size;
                long unit = n.equals("BITSIZE") ? 1 : 8;
                if (bits >= 0) {
                    cb.loadConstant(Math.floorDiv(bits + unit - 1, unit));
                    return JType.LONG;
                }
                // an open array: the number of its elements times their packed size, as cm3
                Loc.Arr x = c.arr(a[0]);
                for (int i = 0; i < Aggregates.openDepth(u); i++) {
                    c.dim(x, i).load(cb);
                    cb.i2l();
                    if (i > 0) {
                        cb.lmul();
                    }
                }
                long sz = m3j.front.OpenArrayType.eltPack(u);
                if (sz % unit == 0) {
                    cb.loadConstant(sz / unit);
                    cb.lmul();
                } else {
                    cb.loadConstant(sz);
                    cb.lmul();
                    cb.loadConstant(unit - 1);
                    cb.ladd();
                    cb.loadConstant(unit);
                    cb.ldiv();
                }
                return JType.LONG;
            }
            case "ISTYPE" -> {
                Label yes = cb.newLabel(), end = cb.newLabel(), nonnull = cb.newLabel();
                c.expr(a[0]);
                cb.dup();
                cb.ifnonnull(nonnull);
                cb.pop();
                cb.goto_(yes);
                cb.labelBinding(nonnull);
                Exceptions.typeTest(c, typeArg(a[1]), yes);
                cb.iconst_0();
                cb.goto_(end);
                cb.labelBinding(yes);
                cb.iconst_1();
                cb.labelBinding(end);
                return JType.BOOL;
            }
            case "NARROW" -> {
                Type.T target = typeArg(a[1]);
                JType jt = JType.of(target);
                c.expr(a[0]);
                if (m3j.front.Host.doNarrowChk) {
                    Label ok = cb.newLabel();
                    cb.dup();
                    cb.ifnull(ok);
                    cb.dup();
                    Exceptions.typeTest(c, target, ok);
                    c.runtimeError(5);
                    cb.labelBinding(ok);
                }
                c.checkcastIfNeeded(jt.desc);
                return jt;
            }
            default -> {
                return c.unsupportedValue("built-in " + n, t);
            }
        }
    }

    /** The type given as an argument of a built-in. */
    static Type.T typeArg(Expr.T e) {
        Type.T[] t = new Type.T[1];
        if (TypeExpr.split(e, t)) {
            return t[0];
        }
        return Expr.typeOf(e);
    }

    static void incDec(Code c, Expr.T[] a, boolean inc) {
        CodeBuilder cb = c.cb;
        Loc l = c.designator(a[0]);
        if (l == null) {
            c.unsupported((inc ? "INC" : "DEC") + " of " + a[0].getClass().getName());
            return;
        }
        JType t = l.type();
        l.storePre(cb);
        l.load(cb);
        c.widen(t.typeKind(), TypeKind.LONG);
        if (a.length > 1 && a[1] != null) {
            c.exprAs(a[1], JType.LONG, false);
        } else {
            cb.lconst_1();
        }
        if (inc) {
            cb.ladd();
        } else {
            cb.lsub();
        }
        c.convert(null, JType.LONG, t, true);
        l.storePost(cb);
    }

    /** NEW (T, ...) */
    static JType newCall(Code c, CallExpr.T ce) {
        CodeBuilder cb = c.cb;
        Expr.T[] a = ce.args;
        Type.T rt = typeArg(a[0]);
        Type.T u = Aggregates.strip(rt);
        ClassDesc cd = Types.classOf(rt);
        if (Java.classOf(u) != null) {
            c.unit.error("NEW of a Java type: call a constructor of the binding", ce.origin);
        }
        // an opaque type: what the revelation visible here says it is
        while (u.info.class_ == Type.Class.Opaque && m3j.front.Revelation.lookUp(u) instanceof Type.T rev
               && Aggregates.strip(rev) != u) {
            u = Aggregates.strip(rev);
        }
        if (u.info.class_ == Type.Class.Object) {
            Java.checkComplete(c.unit, u, ce.origin);
        }
        if (u.info.class_ == Type.Class.Ref) {
            Type.T target = Types.target(u);
            Type.T tu = target == null ? null : Aggregates.strip(target);
            cb.new_(cd);
            cb.dup();
            if (tu != null && tu.info.class_ == Type.Class.OpenArray) {
                int depth = Aggregates.openDepth(tu);
                List<ClassDesc> ps = new ArrayList<>();
                for (int i = 0; i < depth; i++) {
                    c.exprAs(a[1 + i], JType.LONG, false);
                    cb.invokestatic(Code.RT, "count", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_long));
                    ps.add(ConstantDescs.CD_int);
                }
                cb.invokespecial(cd, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, ps));
                if (Code.hasDefaults(target)) {
                    // the defaults of the records in the new array
                    Loc obj = c.newLocal(JType.ref(cd));
                    obj.store(cb);
                    JType tt = JType.of(target);
                    Loc[] dims = new Loc[depth];
                    for (int i = 0; i < depth; i++) {
                        dims[i] = new Loc.Field(obj, cd, "n" + i, JType.INT);
                    }
                    c.applyArrayDefaults(new Loc.Arr(new Loc.Field(obj, cd, "a", tt), new Loc.ConstInt(0), dims,
                        target, tt));
                    obj.load(cb);
                }
            } else {
                cb.invokespecial(cd, "<init>", Code.MT_V);
                if (tu != null && tu.info.class_ == Type.Class.Array && Code.hasDefaults(target)) {
                    // the defaults of the records in the new array
                    Loc obj = c.newLocal(JType.ref(cd));
                    obj.store(cb);
                    JType tt = JType.of(target);
                    c.applyArrayDefaults(new Loc.Arr(new Loc.Field(obj, cd, "v", tt), new Loc.ConstInt(0),
                        new Loc[0], target, tt));
                    obj.load(cb);
                }
                if (tu != null && tu.info.class_ == Type.Class.Record && Code.hasDefaults(target)) {
                    Loc obj = c.newLocal(JType.ref(cd));
                    obj.store(cb);
                    c.applyDefaults(obj, target);
                    obj.load(cb);
                }
                if (tu != null && tu.info.class_ == Type.Class.Record && a.length > 1) {
                    // NEW (REF R, f := e, ...)
                    Loc obj = c.newLocal(JType.ref(cd));
                    obj.store(cb);
                    ClassDesc rc = Types.classOf(tu);
                    for (int i = 1; i < a.length; i++) {
                        int[] key = new int[1];
                        Expr.T[] val = new Expr.T[1];
                        if (!KeywordExpr.split(a[i], key, val)) {
                            continue;
                        }
                        for (m3j.front.Field.T f : Types.fields(tu)) {
                            if (f.name == key[0]) {
                                Loc l = c.fieldLoc(obj, rc, f);
                                if (l instanceof Loc.Arr arr) {
                                    c.assignArr(arr, val[0]);
                                } else {
                                    c.assignTo(l, val[0]);
                                }
                            }
                        }
                    }
                    obj.load(cb);
                } else if (tu != null && a.length > 1 && tu.info.class_ != Type.Class.OpenArray) {
                    c.unsupported("NEW with values of " + tu.info.class_);
                }
            }
            return JType.ref(cd);
        }
        // an object: then the field values
        cb.new_(cd);
        cb.dup();
        cb.invokespecial(cd, "<init>", Code.MT_V);
        Loc obj = c.newLocal(JType.ref(cd));
        obj.store(cb);
        for (int i = 1; i < a.length; i++) {
            int[] key = new int[1];
            Expr.T[] val = new Expr.T[1];
            if (!KeywordExpr.split(a[i], key, val)) {
                continue;
            }
            Value.T[] v = new Value.T[1];
            Type.T[] vis = new Type.T[1];
            if (ObjectType.lookUp(rt, key[0], v, vis) && v[0] instanceof m3j.front.Field.T f) {
                Loc l = c.objectFieldLoc(obj, vis[0], f);
                if (l instanceof Loc.Arr arr) {
                    c.assignArr(arr, val[0]);
                } else {
                    c.assignTo(l, val[0]);
                }
            }
        }
        obj.load(cb);
        return JType.ref(cd);
    }

    /** A built-in whose value is an array: SUBARRAY; null for others. */
    static Loc.Arr arrayCall(Code c, CallExpr.T ce) {
        if (!"SUBARRAY".equals(name(ce))) {
            return null;
        }
        CodeBuilder cb = c.cb;
        Expr.T[] a = ce.args;
        Loc.Arr x = c.arr(a[0]);
        Type.T rt = Expr.typeOf(ce);
        Type.T elt = Code.arrayElement(x.arrayType());
        Loc n0 = c.dim(x, 0);
        c.exprAs(a[1], JType.LONG, false);
        c.exprAs(a[2], JType.LONG, false);
        n0.load(cb);
        cb.invokestatic(Code.RT, "subarray", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_long,
            ConstantDescs.CD_long, ConstantDescs.CD_int));
        Aggregates.Leaf el = Aggregates.leaf(Aggregates.nonOpenElement(elt));
        Loc[] rest = new Loc[Math.max(0, Aggregates.openDepth(rt) - 1)];
        for (int i = 0; i < rest.length; i++) {
            rest[i] = c.dim(x, i + 1);
        }
        cb.loadConstant((int) el.slots());
        for (Loc d : rest) {
            d.load(cb);
            cb.imul();
        }
        cb.imul();
        if (!(x.offset() instanceof Loc.ConstInt ci && ci.value() == 0)) {
            x.offset().load(cb);
            cb.iadd();
        }
        Loc off = c.newLocal(JType.INT);
        off.store(cb);
        c.exprAs(a[2], JType.LONG, false);
        cb.l2i();
        Loc len = c.newLocal(JType.INT);
        len.store(cb);
        Loc[] dims = new Loc[1 + rest.length];
        dims[0] = len;
        System.arraycopy(rest, 0, dims, 1, rest.length);
        return new Loc.Arr(x.storage(), off, dims, rt, JType.of(rt));
    }

    static JType word(Code c, String op, Expr.T[] a, JType t) {
        CodeBuilder cb = c.cb;
        switch (op) {
            case "Plus", "Minus", "Times", "And", "Or", "Xor" -> {
                c.exprAs(a[0], JType.LONG, false);
                c.exprAs(a[1], JType.LONG, false);
                switch (op) {
                    case "Plus" -> cb.ladd();
                    case "Minus" -> cb.lsub();
                    case "Times" -> cb.lmul();
                    case "And" -> cb.land();
                    case "Or" -> cb.lor();
                    default -> cb.lxor();
                }
                return JType.LONG;
            }
            case "Divide", "Mod" -> {
                c.exprAs(a[0], JType.LONG, false);
                c.exprAs(a[1], JType.LONG, false);
                cb.invokestatic(Code.RT, op.equals("Divide") ? "udiv" : "umod", J_JJ);
                return JType.LONG;
            }
            case "LT", "LE", "GT", "GE" -> {
                c.exprAs(a[0], JType.LONG, false);
                c.exprAs(a[1], JType.LONG, false);
                cb.invokestatic(CD_Long, "compareUnsigned", I_JJ);
                var yes = cb.newLabel();
                var end = cb.newLabel();
                switch (op) {
                    case "LT" -> cb.iflt(yes);
                    case "LE" -> cb.ifle(yes);
                    case "GT" -> cb.ifgt(yes);
                    default -> cb.ifge(yes);
                }
                cb.iconst_0();
                cb.goto_(end);
                cb.labelBinding(yes);
                cb.iconst_1();
                cb.labelBinding(end);
                return JType.BOOL;
            }
            case "Not" -> {
                c.exprAs(a[0], JType.LONG, false);
                cb.loadConstant(-1L);
                cb.lxor();
                return JType.LONG;
            }
            case "Shift", "Rotate" -> {
                c.exprAs(a[0], JType.LONG, false);
                c.exprAs(a[1], JType.LONG, false);
                cb.invokestatic(Code.RT, op.equals("Shift") ? "shift" : "rotate", J_JJ);
                return JType.LONG;
            }
            case "LeftShift", "RightShift", "LeftRotate", "RightRotate" -> {
                c.exprAs(a[0], JType.LONG, false);
                c.exprAs(a[1], JType.intType(0, 63), true);
                switch (op) {
                    case "LeftShift" -> cb.lshl();
                    case "RightShift" -> cb.lushr();
                    case "LeftRotate" -> cb.invokestatic(CD_Long, "rotateLeft",
                        MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_int));
                    default -> cb.invokestatic(CD_Long, "rotateRight",
                        MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_int));
                }
                return JType.LONG;
            }
            case "Extract" -> {
                c.exprAs(a[0], JType.LONG, false);
                c.exprAs(a[1], JType.LONG, false);
                c.exprAs(a[2], JType.LONG, false);
                cb.invokestatic(Code.RT, "extract", MethodTypeDesc.of(ConstantDescs.CD_long,
                    ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_long));
                return JType.LONG;
            }
            case "Insert" -> {
                c.exprAs(a[0], JType.LONG, false);
                c.exprAs(a[1], JType.LONG, false);
                c.exprAs(a[2], JType.LONG, false);
                c.exprAs(a[3], JType.LONG, false);
                cb.invokestatic(Code.RT, "insert", MethodTypeDesc.of(ConstantDescs.CD_long,
                    ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_long));
                return JType.LONG;
            }
            default -> {
                return c.unsupportedValue("Word." + op, t);
            }
        }
    }
}
