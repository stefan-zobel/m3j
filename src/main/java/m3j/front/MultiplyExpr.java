// Java port of CM3's m3-sys/m3front/src/exprs/MultiplyExpr.i3 and MultiplyExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: MultiplyExpr.m3
// Last modified on Wed Feb 22 09:07:35 PST 1995 by kalsow
//      modified on Wed Nov 21 21:55:34 1990 by muller

public final class MultiplyExpr {
    private MultiplyExpr() {}

    private static final long cINT   = 0;
    private static final long cLINT  = 1;
    private static final long cREAL  = 2;
    private static final long cLONG  = 3;
    private static final long cEXTND = 4;
    private static final long cSET   = 5;

    /** Port: indexed by {@code class - cREAL}. */
    private static final Target.CGType[] CGType = {
        Target.CGType.Reel, Target.CGType.LReel, Target.CGType.XReel };

    public static final class P extends ExprRep.Tabc {
        public M3CG.Var tmp;

        @Override
        public Type.T typeOf() {
            return MultiplyExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return MultiplyExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            MultiplyExpr.check(this, cs);
        }

        @Override
        public void prep() {
            MultiplyExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            MultiplyExpr.compile(this, StaticOnly);
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
        p.tmp = null;
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
        Type.T[] range = new Type.T[1];
        Expr.typeCheck(p.a, cs);
        Expr.typeCheck(p.b, cs);
        ta = Type.base(Expr.typeOf(p.a));
        tb = Type.base(Expr.typeOf(p.b));
        if ((tb == Int.T) && (ta == Int.T)) {
            p.class_ = cINT;
        } else if ((tb == LInt.T) && (ta == LInt.T)) {
            p.class_ = cLINT;
        } else if ((tb == Reel.T) && (ta == Reel.T)) {
            p.class_ = cREAL;
        } else if ((tb == LReel.T) && (ta == LReel.T)) {
            p.class_ = cLONG;
        } else if ((tb == EReel.T) && (ta == EReel.T)) {
            p.class_ = cEXTND;
        } else if ((ta == ErrType.T) || (tb == ErrType.T)) {
            p.class_ = cINT; // there's already an error
            ta = ErrType.T;
        } else if (SetType.split(ta, range) && Type.isEqual(ta, tb, null)) {
            p.class_ = cSET;
        } else {
            ta = Expr.badOperands("'*'", ta, tb);
            p.class_ = cINT;
        }
        p.type = ta;
    }

    private static void prep(P p) {
        long size;
        Type.Info info = new Type.Info();
        Expr.prep(p.a);
        Expr.prep(p.b);
        if ((p.class_ == cSET)) {
            Type.checkInfo(p.type, info);
            size = info.size;
            if ((size > M3.target.Integer.size)) {
                p.tmp = CG.declare_temp(size, M3.target.Integer.align,
                                        Target.CGType.Struct, /* in_memory := */ true);
                CG.load_addr_of(p.tmp, 0, M3.target.Integer.align);
                CG.forceStacked();
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.set_intersection(size);
            }
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        long size;
        Type.Info info = new Type.Info();
        AssertError.check(!StaticOnly, "MultiplyExpr");
        if ((p.class_ == cINT)) {
            Expr.compile(p.a);
            Expr.compile(p.b);
            CG.multiply(M3.target.Integer.cg_type);
        } else if ((p.class_ == cLINT)) {
            Expr.compile(p.a);
            Expr.compile(p.b);
            CG.multiply(M3.target.Longint.cg_type);
        } else if ((p.class_ != cSET)) {
            Expr.compile(p.a);
            Expr.compile(p.b);
            CG.multiply(CGType[(int) (p.class_ - cREAL)]);
        } else {
            Type.checkInfo(p.type, info);
            size = info.size;
            if ((size > M3.target.Integer.size)) {
                CG.load_addr_of_temp(p.tmp, 0, M3.target.Integer.align);
                p.tmp = null;
            } else {
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.set_intersection(size);
            }
        }
    }

    private static Expr.T fold(P p) {
        Expr.T e1, e2;
        Expr.T[] e3 = new Expr.T[1];
        e1 = Expr.constValue(p.a);
        e2 = Expr.constValue(p.b);
        e3[0] = null;
        if ((e1 == null) || (e2 == null)) {
            // nothing
        } else if (IntegerExpr.multiply(e1, e2, e3)) {
            // nothing
        } else if (ReelExpr.multiply(e1, e2, e3)) {
            // nothing
        } else if (SetExpr.intersection(e1, e2, e3)) {
            // nothing
        }
        return e3[0];
    }
}
