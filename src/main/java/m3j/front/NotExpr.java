// Java port of CM3's m3-sys/m3front/src/exprs/NotExpr.i3 and NotExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.util.AssertError;

// File: NotExpr.m3
// Last modified on Fri Jul  8 09:42:35 PDT 1994 by kalsow
//      modified on Thu Nov 29 03:28:44 1990 by muller

public final class NotExpr {
    private NotExpr() {}

    public static final class P extends ExprRep.Ta {
        @Override
        public void check(M3.CheckState cs) {
            NotExpr.check(this, cs);
        }

        @Override
        public void prep() {
            NotExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            NotExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepBR(long true_, long false_, int freq) {
            NotExpr.prepBR(this, true_, false_, freq);
        }

        @Override
        public void compileBR(long true_, long false_, int freq) {
            ExprRep.noBranch(this, true_, false_, freq);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        @Override
        public long exprAlign() {
            return ExprRep.exprBoolAlign(this);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType,
        // need_addr := ExprRep.NotAddressable, prepLV := ExprRep.NotLValue,
        // compileLV := ExprRep.NotLValueBool, isEqual := ExprRep.EqCheckA,
        // getBounds := ExprRep.NoBounds, isWritable, isDesignator, isZeroes
        // := ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral,
        // prepLiteral := ExprRep.NoPrepLiteral, genLiteral :=
        // ExprRep.NoLiteral and note_write := ExprRep.NotWritable restate
        // the inherited methods.
    }

    public static Expr.T new_(Expr.T a) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.a       = a;
        p.type    = Bool.T;
        p.repType = Bool.T;
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T ta;
        Expr.typeCheck(p.a, cs);
        ta = Type.base(Expr.typeOf(p.a));
        if ((ta != Bool.T)) {
            p.type = Expr.badOperands("NOT", ta);
        }
    }

    private static void prep(P p) {
        Expr.prep(p.a);
    }

    private static void compile(P p, boolean StaticOnly) {
        AssertError.check(!StaticOnly, "NotExpr");
        Expr.compile(p.a);
        Value.load(Bool.False);
        CG.compare(M3.target.Integer.cg_type, M3CG.CompareOp.EQ);
    }

    private static void prepBR(P p, long true_, long false_, int freq) {
        Expr.prepBranch(p.a, false_, true_, CG.Always - freq);
        Expr.compileBranch(p.a, false_, true_, CG.Always - freq);
    }

    private static Expr.T fold(P p) {
        Expr.T e1, e3;
        int[] ii = TInt.newInt();
        long[] i = new long[1];
        Type.T[] t = new Type.T[1];
        e1 = Expr.constValue(p.a);
        e3 = null;
        if ((e1 != null)
            && (EnumExpr.split(e1, ii, t))
            && (Type.base(t[0]) == Bool.T)
            && TInt.toInt(ii, i)) {
            e3 = Bool.Map[(0 == i[0]) ? 1 : 0];
        }
        return e3;
    }
}
