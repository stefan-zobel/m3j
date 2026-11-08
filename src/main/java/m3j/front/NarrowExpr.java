// Java port of CM3's m3-sys/m3front/src/exprs/NarrowExpr.i3 and NarrowExpr.m3.
// Original: Copyright 1996-2000, Critical Mass, Inc.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.util.AssertError;

public final class NarrowExpr {
    private NarrowExpr() {}

    public static final class P extends Expr.T {
        public Expr.T expr;
        public Type.T tipe;
        public CG.Val tmp;

        @Override
        public void check(M3.CheckState cs) {
            NarrowExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            NarrowExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            NarrowExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepBR(long true_, long false_, int freq) {
            ExprRep.prepNoBranch(this, true_, false_, freq);
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
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public long exprAlign() {
            return narrowExprAlign(this);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType,
        // prepLV := ExprRep.NotLValue, compileLV := ExprRep.NotLValueBool,
        // getBounds := ExprRep.NoBounds, isWritable, isDesignator, isZeroes
        // := ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral,
        // prepLiteral := ExprRep.NoPrepLiteral, genLiteral :=
        // ExprRep.NoLiteral and note_write := ExprRep.NotWritable restate
        // the inherited methods.
    }

    public static Expr.T new_(Expr.T a, Type.T t) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.origin  = a.origin;
        p.expr    = a;
        p.tipe    = t;
        p.type    = t;
        p.repType = Type.stripPacked(t);
        p.tmp     = null;
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.Info info = new Type.Info();
        Expr.typeCheck(p.expr, cs);
        p.tipe = Type.checkInfo(p.tipe, info);
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e == null) {
            return false;
        } else if (e instanceof P b) {
            return Type.isEqual(a.tipe, b.tipe, x)
                && Expr.isEqual(a.expr, b.expr, x);
        } else {
            return false;
        }
    }

    private static void needsAddress(P p) {
        Expr.needsAddress(p.expr);
    }

    /** Just inherit from operand expression. */
    private static long narrowExprAlign(P p) {
        return Expr.alignment(p.expr);
    }

    private static void prep(P p) {
        Expr.prep(p.expr);
        Expr.compile(p.expr);
        if (Host.doNarrowChk) {
            Narrow.emit(p.tipe, Expr.typeOf(p.expr));
        }
        p.tmp = CG.pop();
    }

    /** all the work was done by "Prep" */
    private static void compile(P p, boolean StaticOnly) {
        AssertError.check(!StaticOnly, "NarrowExpr");
        CG.push(p.tmp);
        CG.boost_addr_alignment(M3.target.Address.align);
        CG.free(p.tmp);
        p.tmp = null;
    }

    private static Expr.T fold(P p) {
        Expr.T e;
        e = Expr.constValue(p.expr);
        if ((e == null)) {
            return null;
        }
        p.expr = e;
        if (!Host.doNarrowChk || Type.isSubtype(Expr.typeOf(e), p.tipe)) {
            return e;
        }
        return p;
    }
}
