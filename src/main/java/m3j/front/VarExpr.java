// Java port of CM3's m3-sys/m3front/src/exprs/VarExpr.i3 and VarExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.util.AssertError;

// File: VarExpr.m3
// Last modified on Fri Feb 24 16:48:13 PST 1995 by kalsow
//      modified on Sun Jan 21 10:57:47 1990 by muller

public final class VarExpr {
    private VarExpr() {}

    public static final class P extends Expr.T {
        public Variable.T v;

        @Override
        public void check(M3.CheckState cs) {
            VarExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            VarExpr.compile(this, StaticOnly);
        }

        @Override
        public void compileLV(boolean traced, boolean StaticOnly) {
            VarExpr.compileLV(this, traced, StaticOnly);
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
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public boolean isWritable(boolean lhs) {
            return ExprRep.isAlways(this, lhs);
        }

        @Override
        public boolean isDesignator(boolean lhs) {
            return ExprRep.isAlways(this, lhs);
        }

        @Override
        public void note_write() {
            noteWrites(this);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType, prep :=
        // ExprRep.NoPrep, prepLV := ExprRep.NotLValue, evaluate :=
        // ExprRep.NoValue, getBounds := ExprRep.NoBounds, isZeroes :=
        // ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral, prepLiteral
        // := ExprRep.NoPrepLiteral and genLiteral := ExprRep.NoLiteral
        // restate the inherited methods.
    }

    public static Expr.T new_(Type.T t, int name) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.v = Variable.new_(name, true);
        p.type = Type.base(t);
        Variable.bindType(p.v, p.type, /* indirect := */ false, /* readonly := */ false,
                          /* open_array_ok := */ false, /* needs_init := */ true);
        p.repType = p.type;
        return p;
    }

    public static Variable.T obj(Expr.T e) {
        if (e == null) {
            return null;
        } else if (e instanceof P p) {
            return p.v;
        } else {
            return null;
        }
    }

    private static void check(P p, M3.CheckState cs) {
        Value.typeCheck(p.v, cs);
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e == null) {
            return false;
        } else if (e instanceof P b) {
            return (a.v == b.v);
        } else {
            return false;
        }
    }

    private static void needsAddress(P p) {
        Variable.needsAddress(p.v);
    }

    private static void compile(P p, boolean StaticOnly) {
        AssertError.check(!StaticOnly, "VarExpr");
        Variable.load(p.v);
    }

    private static void compileLV(P p, boolean traced, boolean StaticOnly) {
        AssertError.check(!StaticOnly, "VarExpr");
        Variable.loadLValue(p.v);
    }

    private static void noteWrites(P p) {
        Variable.scheduleTrace(p.v);
    }
}
