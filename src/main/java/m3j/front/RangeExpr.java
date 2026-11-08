// Java port of CM3's m3-sys/m3front/src/exprs/RangeExpr.i3 and RangeExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.TInt;
import m3j.util.AssertError;

// File: RangeExpr.m3
// Last modified on Wed Aug 11 08:51:57 PDT 1993 by kalsow
//      modified on Fri Dec 21 01:22:54 1990 by muller

public final class RangeExpr {
    private RangeExpr() {}

    public static final class P extends ExprRep.Tab {
        @Override
        public Type.T typeOf() {
            return RangeExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return RangeExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            RangeExpr.check(this, cs);
        }

        @Override
        public void prep() {
            RangeExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            RangeExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        @Override
        public void getBounds(int[] min, int[] max) {
            bounder(this, min, max);
        }

        @Override
        public boolean isZeroes(boolean lhs) {
            return RangeExpr.isZeroes(this, lhs);
        }

        @Override
        public void genFPLiteral(M3Buf.T mbuf) {
            RangeExpr.genFPLiteral(this, mbuf);
        }

        // Port: the overrides need_addr := ExprRep.NotAddressable,
        // prepLV := ExprRep.NotLValue, compileLV := ExprRep.NotLValueBool,
        // prepBR, compileBR := ExprRep.NotBoolean, isEqual :=
        // ExprRep.EqCheckAB, isWritable, isDesignator := ExprRep.IsNever,
        // prepLiteral := ExprRep.NoPrepLiteral, genLiteral :=
        // ExprRep.NoLiteral and note_write := ExprRep.NotWritable restate
        // the inherited methods.
    }

    public static Expr.T new_(Expr.T a, Expr.T b) {
        P p = new P();
        ExprRep.init(p);
        p.a    = a;
        p.b    = b;
        return p;
    }

    public static boolean split(Expr.T e, Expr.T[] min, Expr.T[] max) {
        if (e == null) {
            return false;
        } else if (e instanceof P p) {
            min[0] = p.a;
            max[0] = p.b;
            return true;
        } else {
            return false;
        }
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
            // ok
        } else if (EnumType.is(ta) && Type.isEqual(ta, tb, null)) {
            // ok
        } else {
            Error.msg("incompatible types for '..'");
        }
        p.type = ta;
    }

    private static void prep(P p) {
        Expr.prep(p.a);
        Expr.prep(p.b);
    }

    private static void compile(P p, boolean StaticOnly) {
        Error.msg("INTERNAL ERROR: cannot compile a range expression");
        AssertError.check(false, "RangeExpr");
    }

    private static void bounder(P p, int[] min, int[] max) {
        Expr.T e;
        int[] i = TInt.newInt();
        Type.T[] t = new Type.T[1];
        Type.getBounds(p.type, min, max);
        e = Expr.constValue(p.a);
        if ((e == null)) {
            // can't improve lower bound
        } else if (IntegerExpr.split(e, i, t)) {
            TInt.assign(min, i);
        } else if (EnumExpr.split(e, i, t)) {
            TInt.assign(min, i);
        }
        // Sic: the upper bound is taken from p.a again, not from p.b.
        e = Expr.constValue(p.a);
        if ((e == null)) {
            // can't improve upper bound
        } else if (IntegerExpr.split(e, i, t)) {
            TInt.assign(max, i);
        } else if (EnumExpr.split(e, i, t)) {
            TInt.assign(max, i);
        }
    }

    private static Expr.T fold(P p) {
        Expr.T e1, e2;
        e1 = Expr.constValue(p.a);
        if ((e1 == null)) {
            return null;
        }
        e2 = Expr.constValue(p.b);
        if ((e2 == null)) {
            return null;
        }
        if ((e1 == p.a) && (e2 == p.b)) {
            return p;
        } else {
            return new_(e1, e2);
        }
    }

    private static void genFPLiteral(P p, M3Buf.T buf) {
        M3Buf.putText(buf, "RANGE<");
        Expr.genFPLiteral(p.a, buf);
        M3Buf.putChar(buf, ',');
        Expr.genFPLiteral(p.b, buf);
        M3Buf.putChar(buf, '>');
    }

    private static boolean isZeroes(P p, boolean lhs) {
        return Expr.isZeroes(p.a) && Expr.isZeroes(p.b);
    }
}
