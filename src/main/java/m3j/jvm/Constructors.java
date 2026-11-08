// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import m3j.front.ArrayExpr;
import m3j.front.ConsExpr;
import m3j.front.Expr;
import m3j.front.Field;
import m3j.front.RangeExpr;
import m3j.front.RecordExpr;
import m3j.front.SetExpr;
import m3j.front.SetType;
import m3j.front.Type;

/** Record, array and set constructors, and the operations on sets. */
final class Constructors {
    private Constructors() {}

    static final MethodTypeDesc J_JJ = MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_long,
        ConstantDescs.CD_long);
    static final ClassDesc LONGS = ConstantDescs.CD_long.arrayType();

    // ---------------------------------------------------------------- records

    /** R {f := e, ...}: a new record object. */
    static JType record(Code c, RecordExpr.P r) {
        CodeBuilder cb = c.cb;
        Type.T t = Expr.typeOf(r);
        JType jt = JType.of(t);
        ClassDesc rc = jt.desc;
        Aggregates.newValue(cb, t);
        Loc obj = c.newLocal(jt);
        obj.store(cb);
        for (RecordExpr.Info i : r.map) {
            if (i.expr == null || !(i.field instanceof Field.T f)) {
                continue;
            }
            Loc l = c.fieldLoc(obj, rc, f);
            if (l instanceof Loc.Arr a) {
                c.assignArr(a, i.expr);
            } else {
                c.assignTo(l, i.expr);
            }
        }
        obj.load(cb);
        return jt;
    }

    // ----------------------------------------------------------------- arrays

    /** A {e, ...}: new storage, filled. */
    static Loc.Arr array(Code c, ArrayExpr.T a) {
        Loc.Arr ka = constArray(c, a, false);
        if (ka != null) {
            return ka;
        }
        CodeBuilder cb = c.cb;
        Type.T t = Expr.typeOf(a);
        Type.T u = Aggregates.strip(t);
        JType jt = JType.of(t);
        boolean open = u.info.class_ == Type.Class.OpenArray;
        int n = open ? a.args.length : (int) JType.toLong(Type.number(Code.arrayIndex(u)));
        Type.T elt = Code.arrayElement(u);
        Aggregates.Leaf el = Aggregates.leaf(Aggregates.nonOpenElement(elt));
        int stride = (int) el.slots();
        // an element that is itself an open array constructor gives the inner dimensions
        int depth = Aggregates.openDepth(u);
        Loc[] dims = new Loc[depth];
        if (open) {
            dims[0] = new Loc.ConstInt(n);
            Expr.T first = a.args.length > 0 ? a.args[0] : null;
            for (int i = 1; i < depth; i++) {
                int k = innerLength(first);
                dims[i] = new Loc.ConstInt(k);
                first = innerFirst(first);
            }
            for (int i = 1; i < depth; i++) {
                stride *= ((Loc.ConstInt) dims[i]).value();
            }
        }
        cb.loadConstant(n * stride);
        Aggregates.newStorage(cb, el);
        Loc st = c.newLocal(JType.withDesc(jt, el.arrayDesc()));
        st.store(cb);
        Loc.Arr result = new Loc.Arr(st, new Loc.ConstInt(0), dims, t, jt);
        Loc.Arr whole = new Loc.Arr(st, new Loc.ConstInt(0), open ? java.util.Arrays.copyOfRange(dims, 1, dims.length)
            : new Loc[0], elt, JType.of(elt));
        JType et = JType.of(elt);
        // the elements of "..": a loop that copies the last one, if there are many
        int explicit = n - a.args.length > 4 ? a.args.length : n;
        for (int i = 0; i < explicit; i++) {
            Expr.T e = i < a.args.length ? a.args[i] : a.args[a.args.length - 1];
            Loc.ConstInt off = new Loc.ConstInt(i * stride);
            if (et.kind == JType.Kind.ARRAY || et.kind == JType.Kind.OPENARRAY || et.kind == JType.Kind.BIGSET) {
                Loc.Arr dst = new Loc.Arr(st, off, whole.dims(), elt, et);
                c.assignArr(dst, e);
            } else if (et.kind == JType.Kind.RECORD) {
                Loc l = new Loc.Elem(st, off, el.store(), et);
                c.assignTo(l, e);
            } else {
                Loc l = new Loc.Elem(st, off, el.store(), et);
                c.assignTo(l, e);
            }
        }
        if (explicit < n) {
            st.load(cb);
            cb.loadConstant((explicit - 1) * stride);
            cb.loadConstant(stride);
            cb.loadConstant(n - explicit);
            cb.loadConstant(el.type().kind == JType.Kind.RECORD ? 1 : 0);
            cb.invokestatic(CONSTS, "repeat", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object,
                ConstantDescs.CD_int, ConstantDescs.CD_int, ConstantDescs.CD_int, ConstantDescs.CD_boolean));
        }
        return result;
    }

    static final ClassDesc CONSTS = ClassDesc.of("m3j.rt.Consts");

    /** The most leaves of a constant array that is made from strings. */
    private static final int MAX_CONST_LEAVES = 1 << 22;

    /** The leaf values of a constant array. */
    private static final class Values {
        long[] v = new long[16];
        int n;

        boolean add(long x) {
            if (n == MAX_CONST_LEAVES) {
                return false;
            }
            if (n == v.length) {
                v = java.util.Arrays.copyOf(v, 2 * n);
            }
            v[n++] = x;
            return true;
        }
    }

    /**
     * A constant array of numbers, characters, booleans or small sets: a
     * dynamic constant made once from strings; shared (the constant itself,
     * which no one writes) or a copy. Null if e is no such array.
     */
    static Loc.Arr constArray(Code c, Expr.T e, boolean shared) {
        if (Code.staticError(e) != null) {
            return null;
        }
        Type.T t = Expr.typeOf(e);
        Type.T u = Aggregates.strip(t);
        boolean open = u.info.class_ == Type.Class.OpenArray;
        if (!open && u.info.class_ != Type.Class.Array || Aggregates.openDepth(u) > 1) {
            return null;
        }
        JType jt = JType.of(t);
        Aggregates.Leaf el = Aggregates.leaf(Aggregates.nonOpenElement(Code.arrayElement(u)));
        JType.Kind k = el.type().kind;
        if (el.store() == Aggregates.Store.A || k == JType.Kind.BIGSET || k == JType.Kind.RECORD) {
            return null;
        }
        Values vals = new Values();
        if (!collect(e, t, el.store(), vals)) {
            return null;
        }
        CodeBuilder cb = c.cb;
        int width = switch (el.store()) {
            case Z, B, UB -> 1;
            case S, C -> 2;
            case I, F -> 4;
            default -> 8;
        };
        java.util.List<java.lang.constant.ConstantDesc> chunks = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.n; i++) {
            long x = vals.v[i];
            for (int b = 0; b < width; b++) {
                sb.append((char) (((x >>> (8 * b)) & 0xFF) + 1));
            }
            if (sb.length() >= 16000) {
                chunks.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() > 0) {
            chunks.add(sb.toString());
        }
        ClassDesc ad = el.arrayDesc();
        var bsm = ConstantDescs.ofConstantBootstrap(CONSTS, "array", ConstantDescs.CD_Object,
            ConstantDescs.CD_String.arrayType());
        cb.ldc(java.lang.constant.DynamicConstantDesc.ofNamed(bsm, "$const", ad,
            chunks.toArray(new java.lang.constant.ConstantDesc[0])));
        if (!shared) {
            cb.invokevirtual(ad, "clone", MethodTypeDesc.of(ConstantDescs.CD_Object));
            cb.checkcast(ad);
        }
        Loc st = c.newLocal(JType.withDesc(jt, ad));
        st.store(cb);
        Loc[] dims = open ? new Loc[] {new Loc.ConstInt((int) (vals.n / el.slots()))} : new Loc[0];
        return new Loc.Arr(st, new Loc.ConstInt(0), dims, t, jt);
    }

    /** Adds the leaf values of the constant e of type t to out; false if it is not constant. */
    private static boolean collect(Expr.T e, Type.T t, Aggregates.Store store, Values out) {
        Type.T u = Aggregates.strip(t);
        boolean open = u.info.class_ == Type.Class.OpenArray;
        if (open || u.info.class_ == Type.Class.Array) {
            Expr.T x = e instanceof ConsExpr.P ce ? ce.base : e;
            if (!(x instanceof ArrayExpr.T)) {
                x = Expr.constValue(e);
                if (x instanceof ConsExpr.P ce) {
                    x = ce.base;
                }
            }
            if (!(x instanceof ArrayExpr.T a) || Code.staticError(x) != null) {
                return false;
            }
            long n = open ? a.args.length : JType.toLong(Type.number(Code.arrayIndex(u)));
            if (a.args.length == 0) {
                return n == 0;
            }
            Type.T elt = Code.arrayElement(u);
            for (long i = 0; i < n; i++) {
                if (!collect(a.args[(int) Math.min(i, a.args.length - 1)], elt, store, out)) {
                    return false;
                }
            }
            return true;
        }
        Expr.T cv = Expr.constValue(e);
        if (cv == null) {
            return false;
        }
        int[] v = m3j.middle.TInt.newInt();
        Type.T[] tt = new Type.T[1];
        if (m3j.front.IntegerExpr.split(cv, v, tt) || m3j.front.EnumExpr.split(cv, v, tt)) {
            return out.add(JType.toLong(v));
        }
        if (cv instanceof m3j.front.ReelExpr.P r) {
            return switch (store) {
                case F -> out.add(Float.floatToRawIntBits((float) r.val.fraction));
                case D -> out.add(Double.doubleToRawLongBits(r.val.fraction));
                default -> false;
            };
        }
        if (cv instanceof SetExpr.P se && constSmallSet(se) instanceof Long s) {
            return out.add(s);
        }
        return false;
    }

    /** The length of an element of an open array constructor: from a constructor or a fixed type. */
    private static int innerLength(Expr.T e) {
        if (e == null) {
            return 0;
        }
        Expr.T x = e instanceof ConsExpr.P ce ? ce.base : e;
        if (x instanceof ArrayExpr.T a && Aggregates.strip(Expr.typeOf(a)).info.class_ == Type.Class.OpenArray) {
            return a.args.length;
        }
        Type.T t = Aggregates.strip(Expr.typeOf(e));
        if (t.info.class_ == Type.Class.Array) {
            return (int) JType.toLong(Type.number(Code.arrayIndex(t)));
        }
        return 0;
    }

    private static Expr.T innerFirst(Expr.T e) {
        if (e == null) {
            return null;
        }
        Expr.T x = e instanceof ConsExpr.P ce ? ce.base : e;
        if (x instanceof ArrayExpr.T a && a.args.length > 0) {
            return a.args[0];
        }
        return null;
    }

    // ------------------------------------------------------------------- sets

    static Type.T domain(Type.T setType) {
        Type.T[] d = new Type.T[1];
        SetType.split(Aggregates.strip(setType), d);
        return d[0];
    }

    /** The bits of a constant small set constructor, or null. */
    static Long constSmallSet(SetExpr.P s) {
        long lo = Code.firstOf(domain(Expr.typeOf(s)));
        long bits = 0;
        if (s.mapped && s.others == null) {
            // a set folded by the front end: its elements are in the tree
            for (SetExpr.Node n = s.tree; n != null; n = n.next) {
                for (long v = n.min; v <= n.max; v++) {
                    bits |= 1L << (v - lo);
                }
            }
            return bits;
        }
        for (Expr.T e : s.args) {
            Expr.T[] min = new Expr.T[1], max = new Expr.T[1];
            if (RangeExpr.split(e, min, max)) {
                Long a = Code.constLong(min[0]), b = Code.constLong(max[0]);
                if (a == null || b == null) {
                    return null;
                }
                for (long v = a; v <= b; v++) {
                    bits |= 1L << (v - lo);
                }
            } else {
                Long v = Code.constLong(e);
                if (v == null) {
                    return null;
                }
                bits |= 1L << (v - lo);
            }
        }
        return bits;
    }

    /** {e, a..b, ...} of a small set type: a long. */
    static JType smallSet(Code c, SetExpr.P s) {
        CodeBuilder cb = c.cb;
        Type.T t = Expr.typeOf(s);
        Type.T dom = domain(t);
        JType dt = JType.of(dom);
        long lo = Code.firstOf(dom);
        Long k = constSmallSet(s);
        if (s.mapped && s.others == null && k != null) {
            cb.loadConstant(k.longValue());
            return JType.of(t);
        }
        cb.lconst_0();
        for (Expr.T e : s.args) {
            Expr.T[] min = new Expr.T[1], max = new Expr.T[1];
            if (RangeExpr.split(e, min, max)) {
                c.exprAs(min[0], dt, true);
                c.widen(dt.typeKind(), java.lang.classfile.TypeKind.LONG);
                c.exprAs(max[0], dt, true);
                c.widen(dt.typeKind(), java.lang.classfile.TypeKind.LONG);
                cb.loadConstant(lo);
                cb.invokestatic(Code.RT, "setRange", MethodTypeDesc.of(ConstantDescs.CD_long,
                    ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_long));
            } else {
                cb.lconst_1();
                c.exprAs(e, dt, true);
                c.widen(dt.typeKind(), java.lang.classfile.TypeKind.LONG);
                cb.loadConstant(lo);
                cb.lsub();
                cb.l2i();
                cb.lshl();
            }
            cb.lor();
        }
        return JType.of(t);
    }

    /** {e, a..b, ...} of a large set type. */
    static Loc.Arr bigSet(Code c, SetExpr.P s) {
        Loc.Arr r = c.freshArr(Expr.typeOf(s));
        bigSetInto(c, s, r, false);
        return r;
    }

    private static final MethodTypeDesc MT_RANGE = MethodTypeDesc.of(ConstantDescs.CD_void, LONGS,
        ConstantDescs.CD_int, ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_long);

    /**
     * True if the elements of constructor s can be evaluated while the
     * destination is being changed: they read no large set and call nothing.
     */
    static boolean independent(SetExpr.P s) {
        if (s.mapped && s.others == null) {
            return true;
        }
        boolean[] ok = {true};
        for (Expr.T a : s.args) {
            Walk.expr(a, x -> {
                if (x instanceof m3j.front.CallExpr.T
                    || JType.of(Expr.typeOf(x)).kind == JType.Kind.BIGSET) {
                    ok[0] = false;
                }
            });
        }
        return ok[0];
    }

    /** Adds the elements of constructor s to the large set r, or removes them from it. */
    static void bigSetInto(Code c, SetExpr.P s, Loc.Arr r, boolean remove) {
        CodeBuilder cb = c.cb;
        Type.T t = Expr.typeOf(s);
        Type.T dom = domain(t);
        JType dt = JType.of(dom);
        long lo = Code.firstOf(dom);
        String m = remove ? "clearRangeBig" : "setRangeBig";
        if (s.mapped && s.others == null) {
            for (SetExpr.Node n = s.tree; n != null; n = n.next) {
                r.storage().load(cb);
                r.offset().load(cb);
                cb.loadConstant(n.min);
                cb.loadConstant(n.max);
                cb.loadConstant(lo);
                cb.invokestatic(Code.RT, m, MT_RANGE);
            }
            return;
        }
        for (Expr.T e : s.args) {
            Expr.T[] min = new Expr.T[1], max = new Expr.T[1];
            r.storage().load(cb);
            r.offset().load(cb);
            if (RangeExpr.split(e, min, max)) {
                c.exprAs(min[0], dt, true);
                c.widen(dt.typeKind(), java.lang.classfile.TypeKind.LONG);
                c.exprAs(max[0], dt, true);
                c.widen(dt.typeKind(), java.lang.classfile.TypeKind.LONG);
            } else {
                c.exprAs(e, dt, true);
                c.widen(dt.typeKind(), java.lang.classfile.TypeKind.LONG);
                cb.dup2();
            }
            cb.loadConstant(lo);
            cb.invokestatic(Code.RT, m, MT_RANGE);
        }
    }

    /** The set constructor e (written T {...}, a ConsExpr around it), or null. */
    private static SetExpr.P setConstructor(Expr.T e) {
        Expr.T x = e instanceof ConsExpr.P ce ? ce.base : e;
        return x instanceof SetExpr.P s ? s : null;
    }

    /**
     * r := e for a large set: a constructor is built in r, and A + {..} and
     * A - {..} change a copy of A in r; false if e is none of these.
     */
    static boolean bigSetAssign(Code c, Expr.T e, Loc.Arr r) {
        CodeBuilder cb = c.cb;
        if (Code.staticError(e) != null) {
            return false;
        }
        if (setConstructor(e) instanceof SetExpr.P s && independent(s)) {
            r.storage().load(cb);
            r.offset().load(cb);
            cb.loadConstant((int) Aggregates.setWords(Expr.typeOf(e)));
            cb.invokestatic(Code.RT, "setClear", MethodTypeDesc.of(ConstantDescs.CD_void, LONGS,
                ConstantDescs.CD_int, ConstantDescs.CD_int));
            bigSetInto(c, s, r, false);
            return true;
        }
        Expr.T a;
        SetExpr.P b;
        boolean remove;
        if (e instanceof m3j.front.AddExpr.P x && setConstructor(x.b) instanceof SetExpr.P y
                && independent(y)) {
            a = x.a;
            b = y;
            remove = false;
        } else if (e instanceof m3j.front.AddExpr.P x && setConstructor(x.a) instanceof SetExpr.P y
                && independent(y)) {
            a = x.b;
            b = y;
            remove = false;
        } else if (e instanceof m3j.front.SubtractExpr.P x && setConstructor(x.b) instanceof SetExpr.P y
                && independent(y)) {
            a = x.a;
            b = y;
            remove = true;
        } else {
            return false;
        }
        if (Code.staticError(b) != null) {
            return false;
        }
        Loc.Arr x = c.arr(a);
        x.storage().load(cb);
        x.offset().load(cb);
        r.storage().load(cb);
        r.offset().load(cb);
        cb.loadConstant((int) Aggregates.setWords(Expr.typeOf(e)));
        cb.invokestatic(Code.RT, "setCopy", MethodTypeDesc.of(ConstantDescs.CD_void, LONGS,
            ConstantDescs.CD_int, LONGS, ConstantDescs.CD_int, ConstantDescs.CD_int));
        bigSetInto(c, b, r, remove);
        return true;
    }

    /** A + B, A - B, A * B, A / B on large sets: into a new set. */
    static Loc.Arr bigSetOp(Code c, Expr.T e) {
        Loc.Arr r = c.freshArr(Expr.typeOf(e));
        bigSetOpInto(c, e, r);
        return r;
    }

    /** True if e is A + B, A - B, A * B or A / B. */
    static boolean isSetOp(Expr.T e) {
        return e instanceof m3j.front.AddExpr.P || e instanceof m3j.front.SubtractExpr.P
            || e instanceof m3j.front.MultiplyExpr.P || e instanceof m3j.front.DivideExpr.P;
    }

    /** A + B, ... on large sets, computed into r (which may be A or B: word by word). */
    static void bigSetOpInto(Code c, Expr.T e, Loc.Arr r) {
        CodeBuilder cb = c.cb;
        Type.T t = Expr.typeOf(e);
        String op;
        Expr.T a, b;
        switch (e) {
            case m3j.front.AddExpr.P x -> { op = "setUnion"; a = x.a; b = x.b; }
            case m3j.front.SubtractExpr.P x -> { op = "setDiff"; a = x.a; b = x.b; }
            case m3j.front.MultiplyExpr.P x -> { op = "setInter"; a = x.a; b = x.b; }
            case m3j.front.DivideExpr.P x -> { op = "setSymDiff"; a = x.a; b = x.b; }
            default -> throw new IllegalStateException();
        }
        Loc.Arr x = c.arr(a), y = c.arr(b);
        x.storage().load(cb);
        x.offset().load(cb);
        y.storage().load(cb);
        y.offset().load(cb);
        r.storage().load(cb);
        r.offset().load(cb);
        cb.loadConstant((int) Aggregates.setWords(t));
        cb.invokestatic(Code.RT, op, MethodTypeDesc.of(ConstantDescs.CD_void, LONGS, ConstantDescs.CD_int,
            LONGS, ConstantDescs.CD_int, LONGS, ConstantDescs.CD_int, ConstantDescs.CD_int));
    }

    /** Pushes (as an int 0 or 1) whether element e is in set s. */
    static void in(Code c, Expr.T e, Expr.T s) {
        CodeBuilder cb = c.cb;
        Type.T st = Expr.typeOf(s);
        Type.T dom = domain(st);
        long lo = Code.firstOf(dom);
        long n = JType.toLong(Type.number(dom));
        JType jt = JType.of(st);
        if (jt.kind == JType.Kind.SET) {
            c.expr(s);
            c.exprAs(e, JType.LONG, false);
            cb.loadConstant(lo);
            cb.loadConstant(n);
            cb.invokestatic(Code.RT, "in", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_long,
                ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_long));
        } else {
            Loc.Arr a = c.arr(s);
            a.storage().load(cb);
            a.offset().load(cb);
            c.exprAs(e, JType.LONG, false);
            cb.loadConstant(lo);
            cb.loadConstant(n);
            cb.invokestatic(Code.RT, "inBig", MethodTypeDesc.of(ConstantDescs.CD_boolean, LONGS,
                ConstantDescs.CD_int, ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_long));
        }
    }

    /**
     * Compares the two small sets on the stack and jumps if op holds (when)
     * or does not hold (not when): set inclusion is a partial order.
     */
    static void setCompare(Code c, Code.CmpOp op, Label target, boolean when) {
        CodeBuilder cb = c.cb;
        switch (op) {
            case EQ -> c.compareJump(true, when ? Code.CmpOp.EQ : Code.CmpOp.NE, target);
            case NE -> c.compareJump(true, when ? Code.CmpOp.NE : Code.CmpOp.EQ, target);
            default -> {
                String m = switch (op) {
                    case LE -> "setLE";
                    case LT -> "setLT";
                    case GE -> "setGE";
                    default -> "setGT";
                };
                cb.invokestatic(Code.RT, m, MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_long,
                    ConstantDescs.CD_long));
                if (when) {
                    cb.ifne(target);
                } else {
                    cb.ifeq(target);
                }
            }
        }
    }

    /** Compares two large sets (<=, <, >=, >) and jumps if op holds. */
    static void bigSetCompare(Code c, Loc.Arr x, Loc.Arr y, Code.CmpOp op, Label target, boolean when) {
        CodeBuilder cb = c.cb;
        boolean swap = op == Code.CmpOp.GE || op == Code.CmpOp.GT;
        Loc.Arr a = swap ? y : x, b = swap ? x : y;
        a.storage().load(cb);
        a.offset().load(cb);
        b.storage().load(cb);
        b.offset().load(cb);
        cb.loadConstant((int) Aggregates.setWords(x.arrayType()));
        cb.invokestatic(Code.RT, op == Code.CmpOp.LE || op == Code.CmpOp.GE ? "bigSetLE" : "bigSetLT",
            MethodTypeDesc.of(ConstantDescs.CD_boolean, LONGS, ConstantDescs.CD_int, LONGS, ConstantDescs.CD_int,
                ConstantDescs.CD_int));
        if (when) {
            cb.ifne(target);
        } else {
            cb.ifeq(target);
        }
    }
}
