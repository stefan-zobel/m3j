// Java port of CM3's m3-sys/m3front/src/exprs/IntegerExpr.i3 and IntegerExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.TInt;
import m3j.util.AssertError;

// File: IntegerExpr.i3
// Last Modified On Tue Aug 25 14:21:07 PDT 1992 By kalsow

// File: IntegerExpr.m3
// Last modified on Fri Feb 24 16:43:29 PST 1995 by kalsow
//      modified on Tue Apr 10 22:35:24 1990 by muller

/** INTEGER and LONGINT *Constants* only. */
public final class IntegerExpr {
    private IntegerExpr() {}

    // INTEGER and LONGINT *Literals* only.

    public static final class P extends Expr.T {
        public int[] value = TInt.newInt();

        @Override
        public void compile(boolean StaticOnly) {
            IntegerExpr.compile(this, StaticOnly);
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
        public void getBounds(int[] min, int[] max) {
            bounder(this, min, max);
        }

        @Override
        public boolean isZeroes(boolean lhs) {
            return IntegerExpr.isZeroes(this, lhs);
        }

        @Override
        public void genFPLiteral(M3Buf.T buf) {
            IntegerExpr.genFPLiteral(this, buf);
        }

        @Override
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            IntegerExpr.genLiteral(this, offset, type, is_const);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType, check :=
        // ExprRep.NoCheck, need_addr := ExprRep.NotAddressable, prep :=
        // ExprRep.NoPrep, prepLV := ExprRep.NotLValue, compileLV :=
        // ExprRep.NotLValueBool, prepBR, compileBR := ExprRep.NotBoolean,
        // isWritable, isDesignator := ExprRep.IsNever, prepLiteral :=
        // ExprRep.NoPrepLiteral and note_write := ExprRep.NotWritable restate
        // the inherited methods.
    }

    /** Port: the first index of the second dimension of cache. */
    private static final int CacheFirst = -7;

    /** Port: the last index of the second dimension of cache. */
    private static final int CacheLast = 64;

    /**
     * Port: ARRAY BOOLEAN, [-7 .. 64] OF P; the first index is
     * {@code t ? 1 : 0}, the second {@code n - CacheFirst}.
     */
    private static final P[][] cache =
        new P[2][CacheLast - CacheFirst + 1];

    /** PRE: Type.IsSubtype (type, Int.T) OR Type.IsSubtype (type, LInt.T) */
    public static Expr.T new_(Type.T type, int[] value) {
        P p;
        long[] n = new long[1];
        boolean t = Type.isSubtype(type, LInt.T);
        int[] min = TInt.newInt(), max = TInt.newInt();
        if (TInt.toInt(value, n)
            && (CacheFirst <= n[0]) && (n[0] <= CacheLast)) {
            p = cache[t ? 1 : 0][(int) (n[0] - CacheFirst)];
            if ((p != null)) {
                return p;
            }
        }
        if (!Type.getBounds(type, min, max)
            || TInt.LT(value, min)
            || TInt.LT(max, value)) {
            throw new AssertError("IntegerExpr");
        }
        p = new P();
        ExprRep.init(p);
        TInt.assign(p.value, value);
        p.type = type;
        p.repType = type;
        p.checked = true;
        if (TInt.toInt(value, n)
            && (CacheFirst <= n[0]) && (n[0] <= CacheLast)) {
            cache[t ? 1 : 0][(int) (n[0] - CacheFirst)] = p;
        }
        return p;
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e instanceof P b) {
            return TInt.EQ(a.value, b.value);
        } else {
            return false;
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        AssertError.check(!StaticOnly, "IntegerExpr");
        CG.load_integer(Type.cgType(p.type), p.value);
    }

    private static void bounder(P p, int[] min, int[] max) {
        TInt.assign(min, p.value);
        TInt.assign(max, p.value);
    }

    /** Port: VAR sign is {@code sign[0]}. */
    public static boolean compare(Expr.T a, Expr.T b, long[] sign) {
        int[] x = TInt.newInt(), y = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (!splitPair(a, b, x, y, t)) {
            return false;
        }
        if (TInt.LT(x, y)) {
            sign[0] = -1;
        } else if (TInt.LT(y, x)) {
            sign[0] = +1;
        } else {
            sign[0] = 0;
        }
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean add(Expr.T a, Expr.T b, Expr.T[] c) {
        int[] x = TInt.newInt(), y = TInt.newInt(), min = TInt.newInt(),
              max = TInt.newInt(), res = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (!splitPair(a, b, x, y, t)) {
            return false;
        }
        if (!TInt.add(x, y, res)) {
            return false;
        }
        if (!Type.getBounds(t[0], min, max)) {
            return false;
        }
        if (TInt.LT(res, min) || TInt.LT(max, res)) {
            return false;
        }
        c[0] = new_(t[0], res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean subtract(Expr.T a, Expr.T b, Expr.T[] c) {
        int[] x = TInt.newInt(), y = TInt.newInt(), min = TInt.newInt(),
              max = TInt.newInt(), res = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (!splitPair(a, b, x, y, t)) {
            return false;
        }
        if (!TInt.subtract(x, y, res)) {
            return false;
        }
        if (!Type.getBounds(t[0], min, max)) {
            return false;
        }
        if (TInt.LT(res, min) || TInt.LT(max, res)) {
            return false;
        }
        c[0] = new_(t[0], res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean multiply(Expr.T a, Expr.T b, Expr.T[] c) {
        int[] x = TInt.newInt(), y = TInt.newInt(), min = TInt.newInt(),
              max = TInt.newInt(), res = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (!splitPair(a, b, x, y, t)) {
            return false;
        }
        if (!TInt.multiply(x, y, res)) {
            return false;
        }
        if (!Type.getBounds(t[0], min, max)) {
            return false;
        }
        if (TInt.LT(res, min) || TInt.LT(max, res)) {
            return false;
        }
        c[0] = new_(t[0], res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean div(Expr.T a, Expr.T b, Expr.T[] c) {
        int[] x = TInt.newInt(), y = TInt.newInt(), min = TInt.newInt(),
              max = TInt.newInt(), res = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (!splitPair(a, b, x, y, t)) {
            return false;
        }
        if (TInt.EQ(y, TInt.Zero)) {
            Error.msg("attempt to DIV by 0");
            return false;
        }
        if (!TInt.div(x, y, res)) {
            return false;
        }
        if (!Type.getBounds(t[0], min, max)) {
            return false;
        }
        if (TInt.LT(res, min) || TInt.LT(max, res)) {
            return false;
        }
        c[0] = new_(t[0], res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean mod(Expr.T a, Expr.T b, Expr.T[] c) {
        int[] x = TInt.newInt(), y = TInt.newInt(), min = TInt.newInt(),
              max = TInt.newInt(), res = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (!splitPair(a, b, x, y, t)) {
            return false;
        }
        if (TInt.EQ(y, TInt.Zero)) {
            Error.msg("attempt to MOD by 0");
            return false;
        }
        if (!TInt.mod(x, y, res)) {
            return false;
        }
        if (!Type.getBounds(t[0], min, max)) {
            return false;
        }
        if (TInt.LT(res, min) || TInt.LT(max, res)) {
            return false;
        }
        c[0] = new_(t[0], res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean negate(Expr.T a, Expr.T[] c) {
        int[] x = TInt.newInt(), min = TInt.newInt(), max = TInt.newInt(),
              res = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (!split(a, x, t)) {
            return false;
        }
        if (!TInt.subtract(TInt.Zero, x, res)) {
            return false;
        }
        if (!Type.getBounds(t[0], min, max)) {
            return false;
        }
        if (TInt.LT(res, min) || TInt.LT(max, res)) {
            return false;
        }
        c[0] = new_(t[0], res);
        return true;
    }

    /** Port: VAR c is {@code c[0]}. */
    public static boolean abs(Expr.T a, Expr.T[] c) {
        int[] x = TInt.newInt(), min = TInt.newInt(), max = TInt.newInt(),
              res = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (!split(a, x, t)) {
            return false;
        }
        if (TInt.LT(x, TInt.Zero)) {
            if (!TInt.subtract(TInt.Zero, x, res)) {
                return false;
            }
            if (!Type.getBounds(t[0], min, max)) {
                return false;
            }
            if (TInt.LT(res, min) || TInt.LT(max, res)) {
                return false;
            }
            c[0] = new_(t[0], res);
        } else {
            c[0] = a;
        }
        return true;
    }

    /** Port: VAR i is {@code i[0]}. */
    public static boolean toInt(Expr.T a, long[] i) {
        int[] x = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (!split(a, x, t)) {
            return false;
        }
        if (!TInt.toInt(x, i)) {
            return false;
        }
        return true;
    }

    /** Port: VAR t is {@code t[0]}; x and y are written. */
    private static boolean splitPair(Expr.T a, Expr.T b, int[] x, int[] y,
                                     Type.T[] t) {
        if (a instanceof P p) {
            TInt.assign(x, p.value);
            t[0] = p.type;
        } else {
            return false;
        }
        if (b instanceof P q) {
            TInt.assign(y, q.value);
            return t[0] == q.type;
        } else {
            return false;
        }
    }

    /** Port: v is written; VAR t is {@code t[0]}. */
    public static boolean split(Expr.T e, int[] v, Type.T[] t) {
        if (e instanceof P p) {
            TInt.assign(v, p.value);
            t[0] = p.type;
            return true;
        } else {
            return false;
        }
    }

    private static boolean isZeroes(P p, boolean lhs) {
        return TInt.EQ(p.value, TInt.Zero);
    }

    private static void genFPLiteral(P p, M3Buf.T buf) {
        if (p.type == Int.T) {
            M3Buf.putText(buf, "INT<");
        } else if (p.type == LInt.T) {
            M3Buf.putText(buf, "LINT<");
        } else {
            throw new AssertError("IntegerExpr");
        }
        M3Buf.putIntt(buf, p.value);
        M3Buf.putChar(buf, '>');
    }

    private static void genLiteral(P p, long offset, Type.T type,
                                   boolean is_const) {
        Type.Info info = new Type.Info();
        Type.checkInfo(type, info);
        CG.init_int(offset, info.size, p.value, is_const);
    }
}
