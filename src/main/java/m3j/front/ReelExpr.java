// Java port of CM3's m3-sys/m3front/src/exprs/ReelExpr.i3 and ReelExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.TFloat;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: ReelExpr.i3
// Last Modified On Thu Aug 20 11:13:27 PDT 1992 By kalsow

// File: ReelExpr.m3
// Last modified on Fri Feb 24 16:46:45 PST 1995 by kalsow
//      modified on Tue Apr 10 22:38:17 1990 by muller

public final class ReelExpr {
    private ReelExpr() {}

    public static final class P extends Expr.T {
        public Target.Float val = new Target.Float();

        @Override
        public void compile(boolean StaticOnly) {
            ReelExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return ExprRep.self(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public boolean isZeroes(boolean lhs) {
            return ReelExpr.isZeroes(this, lhs);
        }

        @Override
        public void genFPLiteral(M3Buf.T buf) {
            ReelExpr.genFPLiteral(this, buf);
        }

        @Override
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            ReelExpr.genLiteral(this, offset, type, is_const);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType, check :=
        // ExprRep.NoCheck, need_addr := ExprRep.NotAddressable, prep :=
        // ExprRep.NoPrep, prepLV := ExprRep.NotLValue, compileLV :=
        // ExprRep.NotLValueBool, prepBR, compileBR := ExprRep.NotBoolean,
        // getBounds := ExprRep.NoBounds, isWritable, isDesignator :=
        // ExprRep.IsNever, prepLiteral := ExprRep.NoPrepLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
    }

    public static Expr.T new_(Target.Float value) {
        P p = new P();
        ExprRep.init(p);
        p.val.assign(value);
        p.checked = true;
        switch (TFloat.prec(value)) {
            case Short -> p.type = Reel.T;
            case Long -> p.type = LReel.T;
            case Extended -> p.type = EReel.T;
        }
        p.repType = p.type;
        return p;
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e instanceof P b) {
            return TFloat.EQ(a.val, b.val);
        } else {
            return false;
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        AssertError.check(!StaticOnly, "ReelExpr");
        CG.load_float(p.val);
    }

    // returns TRUE iff the operation was successful

    /** Port: VAR sign is {@code sign[0]}. */
    public static boolean compare(Expr.T a, Expr.T b, long[] sign) {
        Target.Float x = new Target.Float(), y = new Target.Float();
        if (!splitPair(a, b, x, y)) {
            return false;
        }
        if (TFloat.LT(x, y)) {
            sign[0] = -1;
        } else if (TFloat.LT(y, x)) {
            sign[0] = +1;
        } else {
            sign[0] = 0;
        }
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean add(Expr.T a, Expr.T b, Expr.T[] c) {
        Target.Float x = new Target.Float(), y = new Target.Float(),
                     res = new Target.Float();
        if (!splitPair(a, b, x, y)) {
            return false;
        }
        if (!TFloat.add(x, y, res)) {
            return false;
        }
        c[0] = new_(res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean subtract(Expr.T a, Expr.T b, Expr.T[] c) {
        Target.Float x = new Target.Float(), y = new Target.Float(),
                     res = new Target.Float();
        if (!splitPair(a, b, x, y)) {
            return false;
        }
        if (!TFloat.subtract(x, y, res)) {
            return false;
        }
        c[0] = new_(res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean multiply(Expr.T a, Expr.T b, Expr.T[] c) {
        Target.Float x = new Target.Float(), y = new Target.Float(),
                     res = new Target.Float();
        if (!splitPair(a, b, x, y)) {
            return false;
        }
        if (!TFloat.multiply(x, y, res)) {
            return false;
        }
        c[0] = new_(res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean divide(Expr.T a, Expr.T b, Expr.T[] c) {
        Target.Float x = new Target.Float(), y = new Target.Float(),
                     res = new Target.Float();
        if (!splitPair(a, b, x, y)) {
            return false;
        }
        if (!TFloat.divide(x, y, res)) {
            return false;
        }
        c[0] = new_(res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean mod(Expr.T a, Expr.T b, Expr.T[] c) {
        Target.Float x = new Target.Float(), y = new Target.Float(),
                     res = new Target.Float();
        if (!splitPair(a, b, x, y)) {
            return false;
        }
        if (!TFloat.mod(x, y, res)) {
            return false;
        }
        c[0] = new_(res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean min(Expr.T a, Expr.T b, Expr.T[] c) {
        Target.Float x = new Target.Float(), y = new Target.Float();
        if (!splitPair(a, b, x, y)) {
            return false;
        }
        if (TFloat.LT(x, y)) {
            c[0] = a;
        } else {
            c[0] = b;
        }
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean max(Expr.T a, Expr.T b, Expr.T[] c) {
        Target.Float x = new Target.Float(), y = new Target.Float();
        if (!splitPair(a, b, x, y)) {
            return false;
        }
        if (TFloat.LT(x, y)) {
            c[0] = b;
        } else {
            c[0] = a;
        }
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean negate(Expr.T a, Expr.T[] c) {
        Target.Float x = new Target.Float(), zero = new Target.Float(),
                     res = new Target.Float();
        if (!split(a, x)) {
            return false;
        }
        switch (TFloat.prec(x)) {
            case Short -> zero.assign(TFloat.ZeroR);
            case Long -> zero.assign(TFloat.ZeroL);
            case Extended -> zero.assign(TFloat.ZeroX);
        }
        if (!TFloat.subtract(zero, x, res)) {
            return false;
        }
        c[0] = new_(res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean abs(Expr.T a, Expr.T[] c) {
        Target.Float x = new Target.Float(), zero = new Target.Float(),
                     res = new Target.Float();
        if (!split(a, x)) {
            return false;
        }
        switch (TFloat.prec(x)) {
            case Short -> zero.assign(TFloat.ZeroR);
            case Long -> zero.assign(TFloat.ZeroL);
            case Extended -> zero.assign(TFloat.ZeroX);
        }
        if (!TFloat.LT(x, zero)) {
            c[0] = a;
            return true;
        }
        if (!TFloat.subtract(zero, x, res)) {
            return false;
        }
        c[0] = new_(res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean floor(Expr.T a, Type.T t, Expr.T[] c) {
        Target.Float x = new Target.Float();
        int[] min = TInt.newInt(), max = TInt.newInt(), res = TInt.newInt();
        if (!split(a, x)) {
            return false;
        }
        if (!TInt.fromInt(TFloat.floor(x), res)) {
            return false;
        }
        if (!Type.getBounds(t, min, max)) {
            return false;
        }
        if (TInt.LT(res, min) || TInt.LT(max, res)) {
            return false;
        }
        c[0] = IntegerExpr.new_(t, res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean ceiling(Expr.T a, Type.T t, Expr.T[] c) {
        Target.Float x = new Target.Float();
        int[] min = TInt.newInt(), max = TInt.newInt(), res = TInt.newInt();
        if (!split(a, x)) {
            return false;
        }
        if (!TInt.fromInt(TFloat.ceiling(x), res)) {
            return false;
        }
        if (!Type.getBounds(t, min, max)) {
            return false;
        }
        if (TInt.LT(res, min) || TInt.LT(max, res)) {
            return false;
        }
        c[0] = IntegerExpr.new_(t, res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean trunc(Expr.T a, Type.T t, Expr.T[] c) {
        Target.Float x = new Target.Float();
        int[] min = TInt.newInt(), max = TInt.newInt(), res = TInt.newInt();
        if (!split(a, x)) {
            return false;
        }
        if (!TInt.fromInt(TFloat.trunc(x), res)) {
            return false;
        }
        if (!Type.getBounds(t, min, max)) {
            return false;
        }
        if (TInt.LT(res, min) || TInt.LT(max, res)) {
            return false;
        }
        c[0] = IntegerExpr.new_(t, res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean round(Expr.T a, Type.T t, Expr.T[] c) {
        Target.Float x = new Target.Float();
        int[] min = TInt.newInt(), max = TInt.newInt(), res = TInt.newInt();
        if (!split(a, x)) {
            return false;
        }
        if (!TInt.fromInt(TFloat.round(x), res)) {
            return false;
        }
        if (!Type.getBounds(t, min, max)) {
            return false;
        }
        if (TInt.LT(res, min) || TInt.LT(max, res)) {
            return false;
        }
        c[0] = IntegerExpr.new_(t, res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean float_(Expr.T a, Type.T t, Expr.T[] c) {
        long[] i = new long[1];
        Target.Float x = new Target.Float(), res = new Target.Float();
        Target.Precision new_pre;
        if ((t == Reel.T)) {
            new_pre = Target.Precision.Short;
        } else if ((t == LReel.T)) {
            new_pre = Target.Precision.Long;
        } else if ((t == EReel.T)) {
            new_pre = Target.Precision.Extended;
        } else {
            return false;
        }

        if (split(a, x)) {
            if (!TFloat.floatF(x, new_pre, res)) {
                return false;
            }
        } else if (IntegerExpr.toInt(a, i)) {
            if (!TFloat.floatI(i[0], new_pre, res)) {
                return false;
            }
        } else {
            return false;
        }
        c[0] = new_(res);
        return true;
    }

    /** Port: x and y are written. */
    private static boolean splitPair(Expr.T a, Expr.T b, Target.Float x,
                                     Target.Float y) {
        Type.T t;
        if (a instanceof P p) {
            x.assign(p.val);
            t = p.type;
        } else {
            return false;
        }
        if (b instanceof P q) {
            y.assign(q.val);
            return t == q.type;
        } else {
            return false;
        }
    }

    /** Port: x is written. */
    private static boolean split(Expr.T e, Target.Float x) {
        if (e instanceof P p) {
            x.assign(p.val);
            return true;
        } else {
            return false;
        }
    }

    private static boolean isZeroes(P p, boolean lhs) {
        return TFloat.EQ(TFloat.ZeroR, p.val)
            || TFloat.EQ(TFloat.ZeroL, p.val)
            || TFloat.EQ(TFloat.ZeroX, p.val);
    }

    private static void genFPLiteral(P p, M3Buf.T buf) {
        if (p.type == Reel.T) {
            M3Buf.putText(buf, "REAL<");
        } else if (p.type == LReel.T) {
            M3Buf.putText(buf, "LREAL<");
        } else if (p.type == EReel.T) {
            M3Buf.putText(buf, "EREAL<");
        } else {
            throw new AssertError("ReelExpr");
        }
        M3Buf.putFloat(buf, p.val);
        M3Buf.putChar(buf, '>');
    }

    private static void genLiteral(P p, long offset, Type.T type,
                                   boolean is_const) {
        CG.init_float(offset, p.val, is_const);
    }
}
