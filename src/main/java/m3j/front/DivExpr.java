// Java port of CM3's m3-sys/m3front/src/exprs/DivExpr.i3 and DivExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: DivExpr.m3
// Last modified on Wed Feb 22 09:01:01 PST 1995 by kalsow
//      modified on Thu Nov 29 03:31:09 1990 by muller

public final class DivExpr {
    private DivExpr() {}

    public static final class P extends ExprRep.Tab {
        @Override
        public Type.T typeOf() {
            return DivExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return DivExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            DivExpr.check(this, cs);
        }

        @Override
        public void prep() {
            DivExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            DivExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        // Port: the overrides need_addr := ExprRep.NotAddressable,
        // prepLV := ExprRep.NotLValue, compileLV := ExprRep.NotLValueBool,
        // prepBR, compileBR := ExprRep.NotBoolean, isEqual :=
        // ExprRep.EqCheckAB, getBounds := ExprRep.NoBounds, isWritable,
        // isDesignator, isZeroes := ExprRep.IsNever, genFPLiteral :=
        // ExprRep.NoFPLiteral, prepLiteral := ExprRep.NoPrepLiteral,
        // genLiteral := ExprRep.NoLiteral and note_write :=
        // ExprRep.NotWritable restate the inherited methods.
    }

    public static Expr.T new_(Expr.T a, Expr.T b) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.a = a;
        p.b = b;
        p.type = null;
        return p;
    }

    private static Type.T typeOf(P p) {
        return Type.base(Expr.typeOf(p.a));
    }

    private static Type.T repTypeOf(P p) {
        return Type.base(Expr.repTypeOf(p.a));
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T ta, tb;
        Expr.typeCheck(p.a, cs);
        Expr.typeCheck(p.b, cs);
        ta = Type.base(Expr.typeOf(p.a));
        tb = Type.base(Expr.typeOf(p.b));
        if ((ta == Int.T) && (tb == Int.T)) {
            p.type = Int.T;
        } else if ((ta == LInt.T) && (tb == LInt.T)) {
            p.type = LInt.T;
        } else {
            p.type = Expr.badOperands("DIV", ta, tb);
        }
        p.repType = p.type;
    }

    private static void prep(P p) {
        Expr.prep(p.a);
        Expr.prep(p.b);
    }

    private static void compile(P p, boolean StaticOnly) {
        Expr.T e1, e2;
        Expr.T[] e3 = new Expr.T[1];
        int[] divisor = TInt.newInt();
        long[] log = new long[1];
        Type.T[] t = new Type.T[1];
        CGType cg_type;
        AssertError.check(!StaticOnly, "DivExpr");
        e1 = Expr.constValue(p.a);
        e2 = Expr.constValue(p.b);
        e3[0] = null;
        if ((e1 != null) && (e2 != null) && IntegerExpr.div(e1, e2, e3)) {
            Expr.compile(e3[0]);
        } else if ((e2 != null)
            && IntegerExpr.split(e2, divisor, t)
            && smallPowerOfTwo(divisor, log)) {
            if ((e1 == null)) {
                e1 = p.a;
            }
            if ((log[0] == 0)) {
                // divide by one
                Expr.compile(e1);
            } else {
                Expr.compile(e1);
                cg_type = Type.cgType(p.type);
                CG.extract_mn(cg_type, true, log[0],
                              M3.targetMap.CG_Size[cg_type.ordinal()] - log[0]);
            }
        } else {
            if ((e1 == null)) {
                e1 = p.a;
            }
            if ((e2 == null)) {
                e2 = p.b;
            }
            Expr.compile(e1);
            Expr.compile(e2);
            CG.div(Type.cgType(p.type), Expr.getSign(e1), Expr.getSign(e2));
        }
    }

    private static Expr.T fold(P p) {
        Expr.T e1, e2;
        Expr.T[] e3 = new Expr.T[1];
        e1 = Expr.constValue(p.a);
        e2 = Expr.constValue(p.b);
        e3[0] = null;
        if (IntegerExpr.div(e1, e2, e3)) {
            // nothing
        }
        return e3[0];
    }

    // VAR(*CONST*)
    private static final int[][] powers = new int[64][];
    private static long max_power = 0;

    static {
        for (int i = 0; i < powers.length; i++) {
            powers[i] = TInt.newInt();
        }
    }

    /**
     * If 'x' is a small positive power of 2, sets 'log' to the base 2
     * logorithm of 'x' and returns 'TRUE', otherwise returns 'FALSE'
     */
    public static boolean smallPowerOfTwo(int[] x, long[] log) {
        long lo, hi, mid;
        if (max_power <= 0) {
            TInt.assign(powers[0], TInt.One);
            for (long i = 1; i <= Math.min(M3.target.Longint.size - 2, powers.length - 1); i++) {
                if (TInt.add(powers[(int) (i - 1)], powers[(int) (i - 1)], powers[(int) i])) {
                    max_power = i;
                }
            }
        }
        lo = 0;
        hi = max_power + 1;
        while ((lo < hi)) {
            mid = Math.floorDiv(lo + hi, 2);
            if (TInt.EQ(x, powers[(int) mid])) {
                log[0] = mid;
                return true;
            } else if (TInt.LT(x, powers[(int) mid])) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        log[0] = -1;
        return false;
    }
}
