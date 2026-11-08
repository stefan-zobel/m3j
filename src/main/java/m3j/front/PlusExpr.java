// Java port of CM3's m3-sys/m3front/src/exprs/PlusExpr.i3 and PlusExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: PlusExpr.m3
// Last modified on Fri Feb 24 16:45:09 PST 1995 by kalsow
//      modified on Thu Nov 29 03:29:06 1990 by muller

// Unary "+" operator.

public final class PlusExpr {
    private PlusExpr() {}

    public static final class P extends ExprRep.Ta {
        @Override
        public Type.T typeOf() {
            return PlusExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return PlusExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            PlusExpr.check(this, cs);
        }

        @Override
        public void prep() {
            PlusExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            PlusExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        // Port: the overrides need_addr := ExprRep.NotAddressable,
        // prepLV := ExprRep.NotLValue, compileLV := ExprRep.NotLValueBool,
        // prepBR, compileBR := ExprRep.NotBoolean, getBounds :=
        // ExprRep.NoBounds, isWritable, isDesignator, isZeroes :=
        // ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral, prepLiteral
        // := ExprRep.NoPrepLiteral, genLiteral := ExprRep.NoLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
    }

    public static Expr.T new_(Expr.T a) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.a = a;
        return p;
    }

    private static Type.T typeOf(P p) {
        return Type.base(Expr.typeOf(p.a));
    }

    private static Type.T repTypeOf(P p) {
        return Type.base(Expr.repTypeOf(p.a));
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T t;
        Expr.typeCheck(p.a, cs);
        p.type = Expr.typeOf(p.a);
        t = Type.base(p.type);
        if ((t != Int.T) && (t != Reel.T) && (t != LReel.T) && (t != EReel.T)) {
            p.type = Expr.badOperands("unary '+'", t);
        }
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e == null) {
            return false;
        } else if (e instanceof P b) {
            return Expr.isEqual(a.a, b.a, x);
        } else {
            return Expr.isEqual(a.a, e, x);
        }
    }

    private static void prep(P p) {
        Expr.prep(p.a);
    }

    private static void compile(P p, boolean StaticOnly) {
        Expr.compile(p.a, StaticOnly);
    }

    private static Expr.T fold(P p) {
        return Expr.constValue(p.a);
    }
}
