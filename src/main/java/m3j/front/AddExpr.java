// Java port of CM3's m3-sys/m3front/src/exprs/AddExpr.i3 and AddExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: AddExpr.m3
// Last modified on Fri Feb 24 11:49:03 PST 1995 by kalsow
//      modified on Thu Nov 29 03:30:20 1990 by muller

public final class AddExpr {
    private AddExpr() {}

    public enum Class { cINT, cLINT, cREAL, cLONG, cEXTND, cADDR, cSET }

    /** Port: indexed by {@code class.ordinal() - Class.cREAL.ordinal()}. */
    private static final CGType[] FPType = {
        CGType.Reel, CGType.LReel, CGType.XReel };

    public static final class P extends ExprRep.Tab {
        public Class class_;
        public M3CG.Var tmp;

        @Override
        public Type.T typeOf() {
            return AddExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return AddExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            AddExpr.check(this, cs);
        }

        @Override
        public void prep() {
            AddExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            AddExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        @Override
        public void getBounds(int[] min, int[] max) {
            AddExpr.getBounds(this, min, max);
        }

        // Port: the overrides need_addr := ExprRep.NotAddressable,
        // prepLV := ExprRep.NotLValue, compileLV := ExprRep.NotLValueBool,
        // prepBR, compileBR := ExprRep.NotBoolean, isEqual :=
        // ExprRep.EqCheckAB, isWritable, isDesignator, isZeroes :=
        // ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral, prepLiteral
        // := ExprRep.NoPrepLiteral, genLiteral := ExprRep.NoLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
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
        Type.T ta;
        ta = Expr.typeOf(p.a);
        ta = Type.check(ta);
        if (Type.isSubtype(ta, Addr.T)) {
            ta = Addr.T;
        }
        return Type.base(ta);
    }

    private static Type.T repTypeOf(P p) {
        Type.T ta;
        ta = Expr.repTypeOf(p.a);
        ta = Type.check(ta);
        if (Type.isSubtype(ta, Addr.T)) {
            ta = Addr.T;
        }
        return Type.base(ta);
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T ta, tb;
        Type.T[] range = new Type.T[1];
        Expr.typeCheck(p.a, cs);
        Expr.typeCheck(p.b, cs);
        ta = Type.base(Expr.typeOf(p.a));
        tb = Type.base(Expr.typeOf(p.b));
        if (ta == Int.T && tb == Int.T) {
            p.class_ = Class.cINT;
        } else if ((ta == LInt.T) && (tb == LInt.T)) {
            p.class_ = Class.cLINT;
        } else if ((ta == Reel.T) && (tb == Reel.T)) {
            p.class_ = Class.cREAL;
        } else if ((ta == LReel.T) && (tb == LReel.T)) {
            p.class_ = Class.cLONG;
        } else if ((ta == EReel.T) && (tb == EReel.T)) {
            p.class_ = Class.cEXTND;
        } else if ((ta == ErrType.T) || (tb == ErrType.T)) {
            p.class_ = Class.cINT; // there's already an error
            ta = ErrType.T;
        } else if (SetType.split(ta, range)) {
            p.class_ = Class.cSET;
            if (!Type.isEqual(ta, tb, null)) {
                ta = Expr.badOperands("'+'", ta, tb);
            }
        } else if (Type.isSubtype(ta, Addr.T) && (tb == Int.T)) {
            p.class_ = Class.cADDR;
            ta = Addr.T;
            if (Module.isSafe()) {
                Error.msg("unsafe '+'");
            }
        } else {
            ta = Expr.badOperands("'+'", ta, tb);
        }
        p.type = ta;
        p.repType = ta;
    }

    private static void prep(P p) {
        Type.Info info = new Type.Info();
        Expr.prep(p.a);
        Expr.prep(p.b);
        if ((p.class_ == Class.cSET)) {
            Type.checkInfo(p.type, info);
            if ((info.size > M3.target.Word.size)) {
                p.tmp = CG.declare_temp(info.size, M3.target.Word.align,
                                        CGType.Struct, /* in_memory := */ true);
                CG.load_addr_of(p.tmp, 0, M3.target.Word.align);
                CG.forceStacked();
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.set_union(info.size);
            }
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        Type.Info info = new Type.Info();
        AssertError.check(!StaticOnly, "AddExpr");
        switch (p.class_) {
            case cSET -> {
                Type.checkInfo(p.type, info);
                if ((info.size > M3.target.Word.size)) {
                    CG.load_addr_of_temp(p.tmp, 0, M3.target.Word.align);
                    p.tmp = null;
                } else {
                    Expr.compile(p.a);
                    Expr.compile(p.b);
                    CG.set_union(info.size);
                }
            }
            case cADDR -> {
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.index_bytes(Target.Byte);
            }
            case cINT -> {
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.add(M3.target.Integer.cg_type);
            }
            case cLINT -> {
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.add(M3.target.Longint.cg_type);
            }
            case cREAL, cLONG, cEXTND -> {
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.add(FPType[p.class_.ordinal() - Class.cREAL.ordinal()]);
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
        } else if (IntegerExpr.add(e1, e2, e3)) {
            // nothing
        } else if (ReelExpr.add(e1, e2, e3)) {
            // nothing
        } else if (AddressExpr.add(e1, e2, e3)) {
            // nothing
        } else if (SetExpr.union(e1, e2, e3)) {
            // nothing
        }
        return e3[0];
    }

    private static void getBounds(P p, int[] min, int[] max) {
        int[] min_a = TInt.newInt(), max_a = TInt.newInt(), min_b = TInt.newInt(),
              max_b = TInt.newInt(), smin = TInt.newInt(), smax = TInt.newInt();
        Type.getBounds(p.type, min, max);
        Expr.getBounds(p.a, min_a, max_a);
        Expr.getBounds(p.b, min_b, max_b);
        if (TInt.add(min_a, min_b, smin)
            && !TInt.LT(smin, min) && !TInt.LT(max, smin)
            && TInt.add(max_a, max_b, smax)
            && !TInt.LT(smax, min) && !TInt.LT(max, smax)) {
            if (TInt.LT(min, smin)) {
                TInt.assign(min, smin);
            }
            if (TInt.LT(smax, max)) {
                TInt.assign(max, smax);
            }
        }
    }
}
