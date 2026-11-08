// Java port of CM3's m3-sys/m3front/src/exprs/OrExpr.i3 and OrExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;
import m3j.util.AssertError;

// File: OrExpr.m3
// Last modified on Fri Jul  8 09:48:43 PDT 1994 by kalsow
//      modified on Thu Nov 29 03:32:03 1990 by muller

public final class OrExpr {
    private OrExpr() {}

    public static final class P extends ExprRep.Tab {
        public CG.Val tmp;

        @Override
        public void check(M3.CheckState cs) {
            OrExpr.check(this, cs);
        }

        @Override
        public void prep() {
            OrExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            OrExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepBR(long true_, long false_, int freq) {
            OrExpr.prepBR(this, true_, false_, freq);
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
        // compileLV := ExprRep.NotLValueBool, isEqual := ExprRep.EqCheckAB,
        // getBounds := ExprRep.NoBounds, isWritable, isDesignator, isZeroes
        // := ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral,
        // prepLiteral := ExprRep.NoPrepLiteral, genLiteral :=
        // ExprRep.NoLiteral and note_write := ExprRep.NotWritable restate
        // the inherited methods.
    }

    public static Expr.T new_(Expr.T a, Expr.T b) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.a       = a;
        p.b       = b;
        p.type    = Bool.T;
        p.repType = Bool.T;
        p.tmp     = null;
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T ta, tb;
        Expr.typeCheck(p.a, cs);
        Expr.typeCheck(p.b, cs);
        ta = Type.base(Expr.typeOf(p.a));
        tb = Type.base(Expr.typeOf(p.b));
        if ((ta != Bool.T) || (tb != Bool.T)) {
            p.type = Expr.badOperands("OR", ta, tb);
        }
    }

    private static void prep(P p) {
        long true_ = CG.next_label();
        Value.load(Bool.True);
        p.tmp = CG.pop_temp();
        prepBR(p, true_, CG.No_label, CG.Maybe);
        Value.load(Bool.False);
        CG.store_temp(p.tmp);
        CG.set_label(true_);
    }

    private static void compile(P p, boolean StaticOnly) {
        AssertError.check(!StaticOnly, "OrExpr");
        CG.push(p.tmp);
        CG.free(p.tmp);
        p.tmp = null;
    }

    private static void prepBR(P p, long true_, long false_, int freq) {
        long skip;
        if ((false_ == CG.No_label)) {
            Expr.prepBranch(p.a, true_, false_, freq);
            Expr.compileBranch(p.a, true_, false_, freq);
            Expr.prepBranch(p.b, true_, false_, freq);
            Expr.compileBranch(p.b, true_, false_, freq);
        } else { // true = NoLabel => fall through
            skip = CG.next_label();
            Expr.prepBranch(p.a, skip, CG.No_label, CG.Always - freq);
            Expr.compileBranch(p.a, skip, CG.No_label, CG.Always - freq);
            Expr.prepBranch(p.b, CG.No_label, false_, freq);
            Expr.compileBranch(p.b, CG.No_label, false_, freq);
            CG.set_label(skip);
        }
    }

    private static Expr.T fold(P p) {
        Expr.T e1, e2;
        int[] i1 = TInt.newInt(), i2 = TInt.newInt();
        Type.T[] t1 = new Type.T[1], t2 = new Type.T[1];
        long[] z1 = new long[1], z2 = new long[1];
        e1 = Expr.constValue(p.a);
        e2 = Expr.constValue(p.b);
        if ((e1 == null) || (e2 == null)
            || (!EnumExpr.split(e1, i1, t1))
            || (!EnumExpr.split(e2, i2, t2))
            || (Type.base(t1[0]) != Bool.T)
            || (Type.base(t2[0]) != Bool.T)
            || (!TInt.toInt(i1, z1))
            || (!TInt.toInt(i2, z2))) {
            return null;
        } else {
            return Bool.Map[((1 == z1[0]) || (1 == z2[0])) ? 1 : 0];
        }
    }
}
