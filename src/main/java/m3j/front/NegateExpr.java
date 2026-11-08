// Java port of CM3's m3-sys/m3front/src/exprs/NegateExpr.i3 and NegateExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.util.AssertError;

// File: NegateExpr.m3
// Last modified on Fri Jul  8 09:48:43 PDT 1994 by kalsow
//      modified on Thu Nov 29 03:29:51 1990 by muller

public final class NegateExpr {
    private NegateExpr() {}

    public static final class P extends ExprRep.Ta {
        public Expr.T folded;

        @Override
        public Type.T typeOf() {
            return NegateExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return NegateExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            NegateExpr.check(this, cs);
        }

        @Override
        public void prep() {
            NegateExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            NegateExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        // Port: the overrides need_addr := ExprRep.NotAddressable,
        // prepLV := ExprRep.NotLValue, compileLV := ExprRep.NotLValueBool,
        // prepBR, compileBR := ExprRep.NotBoolean, isEqual :=
        // ExprRep.EqCheckA, getBounds := ExprRep.NoBounds, isWritable,
        // isDesignator, isZeroes := ExprRep.IsNever, genFPLiteral :=
        // ExprRep.NoFPLiteral, prepLiteral := ExprRep.NoPrepLiteral,
        // genLiteral := ExprRep.NoLiteral and note_write :=
        // ExprRep.NotWritable restate the inherited methods.
    }

    public static Expr.T new_(Expr.T a) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.a = a;
        p.folded = null;
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
        t = Type.base(Expr.typeOf(p.a));
        if ((t == Int.T) || (t == LInt.T)
            || (t == Reel.T) || (t == LReel.T) || (t == EReel.T)) {
            // ok
        // ELSIF SetType.split (t, range) THEN (* OK *)
        } else {
            t = Expr.badOperands("unary '-'", t);
        }
        p.type = t;
    }

    private static void prep(P p) {
        Expr.T e = fold(p);
        if ((e == null)) {
            Expr.prep(p.a);
        } else {
            Expr.prep(e);
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        Expr.T e = p.folded;
        AssertError.check(!StaticOnly, "NegateExpr");
        if ((e == null)) {
            Expr.compile(p.a);
            CG.negate(Type.cgType(p.type));
        } else {
            Expr.compile(e); // e is folded p, not p.a, thus already negated.
        }
    }

    private static Expr.T fold(P p) {
        Expr.T e1;
        Expr.T[] e3 = new Expr.T[1];
        if ((p.folded != null)) {
            return p.folded;
        }
        e1 = Expr.constValue(p.a);
        e3[0] = null;
        if ((e1 == null)) {
            // nothing
        } else if (IntegerExpr.negate(e1, e3)) {
            // nothing
        } else if (ReelExpr.negate(e1, e3)) {
            // nothing
        }
        p.folded = e3[0];
        return e3[0];
    }
}
