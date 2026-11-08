// Java port of CM3's m3-sys/m3front/src/exprs/InExpr.i3 and InExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: InExpr.m3
// Last modified on Fri Jul  8 09:48:45 PDT 1994 by kalsow
//      modified on Thu Nov 29 03:31:28 1990 by muller

public final class InExpr {
    private InExpr() {}

    public static final class P extends ExprRep.Tab {
        public CG.Val tmp;

        @Override
        public void check(M3.CheckState cs) {
            InExpr.check(this, cs);
        }

        @Override
        public void prep() {
            InExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            InExpr.compile(this, StaticOnly);
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
        Type.T[] tc = new Type.T[1];
        Expr.typeCheck(p.a, cs);
        Expr.typeCheck(p.b, cs);
        ta = Type.base(Expr.typeOf(p.a));
        tb = Type.base(Expr.typeOf(p.b));
        if (SetType.split(tb, tc) && Type.isSubtype(ta, Type.base(tc[0]))) {
            // ok
        } else {
            p.type = Expr.badOperands("IN", ta, tb);
        }
    }

    private static void prep(P p) {
        Type.T set;
        Type.T[] range = new Type.T[1];
        boolean b;
        int[] min = TInt.newInt(), max = TInt.newInt(), emin = TInt.newInt(),
              emax = TInt.newInt(), n_elts = TInt.newInt();
        long skip;
        CG.Val index;
        Type.Info info = new Type.Info();
        CGType cg_type;
        set = Type.base(Type.checkInfo(Expr.typeOf(p.b), info));
        b = SetType.split(set, range);
        AssertError.check(b, "InExpr");
        b = Type.getBounds(range[0], min, max);
        AssertError.check(b, "InExpr");
        Expr.getBounds(p.a, emin, emax);
        cg_type = Type.cgType(range[0]);
        Expr.prep(p.a);
        Expr.prep(p.b);
        if (TInt.LT(emin, min) || TInt.LT(max, emax)) {
            // we need to range check a
            if (!TInt.subtract(max, min, n_elts)
                || TInt.LT(M3.target.Integer.max, n_elts)) {
                Error.msg("set too large");
            }
            Expr.compile(p.a);
            if (!TInt.EQ(min, TInt.Zero)) {
                CG.load_integer(cg_type, min);
                CG.subtract(cg_type);
            }
            index = CG.pop();
            Value.load(Bool.False);
            p.tmp = CG.pop_temp();
            CG.push(index);
            CG.loophole(cg_type, M3.target.Word.cg_type);
            CG.load_integer(M3.target.Word.cg_type, n_elts);
            skip = CG.next_label();
            CG.if_compare(M3.target.Word.cg_type, M3CG.CompareOp.GT, skip, CG.Never);
            Expr.compile(p.b);
            CG.push(index);
            CG.set_member(info.size);
            CG.store_temp(p.tmp);
            CG.set_label(skip);
            CG.free(index);
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        Type.T set;
        Type.T[] range = new Type.T[1];
        boolean b;
        int[] min = TInt.newInt(), max = TInt.newInt(), emin = TInt.newInt(),
              emax = TInt.newInt();
        Type.Info info = new Type.Info();
        CGType cg_type;
        AssertError.check(!StaticOnly, "InExpr");
        set = Type.base(Type.checkInfo(Expr.typeOf(p.b), info));
        b = SetType.split(set, range);
        AssertError.check(b, "InExpr");
        b = Type.getBounds(range[0], min, max);
        AssertError.check(b, "InExpr");
        Expr.getBounds(p.a, emin, emax);
        if (TInt.LT(emin, min) || TInt.LT(max, emax)) {
            // we need to range check a
            CG.push(p.tmp);
            CG.free(p.tmp);
            p.tmp = null;
        } else {
            // no range checking is needed
            Expr.compile(p.b);
            Expr.compile(p.a);
            cg_type = Type.cgType(range[0]);
            if (!TInt.EQ(min, TInt.Zero)) {
                CG.load_integer(cg_type, min);
                CG.subtract(cg_type);
            }
            CG.loophole(cg_type, M3.target.Integer.cg_type);
            CG.set_member(info.size);
        }
    }

    private static Expr.T fold(P p) {
        Expr.T e1, e2;
        Expr.T[] e3 = new Expr.T[1];
        e1 = Expr.constValue(p.b);
        e2 = Expr.constValue(p.a);
        e3[0] = null;
        if ((e1 == null) || (e2 == null)) {
            // nothing
        } else if (SetExpr.member(e1, e2, e3)) {
            // nothing
        }
        return e3[0];
    }
}
