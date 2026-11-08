// Java port of CM3's m3-sys/m3front/src/exprs/SubtractExpr.i3 and SubtractExpr.m3.
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

// File: SubtractExpr.m3
// Last modified on Fri Feb 24 11:45:21 PST 1995 by kalsow
//      modified on Thu Nov 29 23:25:28 1990 by muller

public final class SubtractExpr {
    private SubtractExpr() {}

    public enum Class { cINT, cLINT, cREAL, cLONG, cEXTND, cADDR, cSET, cENUM }

    /** Port: indexed by {@code class.ordinal() - Class.cREAL.ordinal()}. */
    private static final CGType[] FPType = {
        CGType.Reel, CGType.LReel, CGType.XReel };

    public static final class P extends ExprRep.Tab {
        public boolean extended; // => can subtract enum values
        public Class class_;
        public M3CG.Var tmp;

        @Override
        public Type.T typeOf() {
            return SubtractExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return SubtractExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            SubtractExpr.check(this, cs);
        }

        @Override
        public void prep() {
            SubtractExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            SubtractExpr.compile(this, StaticOnly);
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
        public void getBounds(int[] min, int[] max) {
            SubtractExpr.getBounds(this, min, max);
        }

        // Port: the overrides need_addr := ExprRep.NotAddressable,
        // prepLV := ExprRep.NotLValue, compileLV := ExprRep.NotLValueBool,
        // isEqual := ExprRep.EqCheckAB, isWritable, isDesignator, isZeroes
        // := ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral,
        // prepLiteral := ExprRep.NoPrepLiteral, genLiteral :=
        // ExprRep.NoLiteral and note_write := ExprRep.NotWritable restate
        // the inherited methods.
    }

    public static Expr.T new_(Expr.T a, Expr.T b, boolean extended) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.a = a;
        p.b = b;
        p.extended = extended;
        p.tmp = null;
        return p;
    }

    public static Expr.T new_(Expr.T a, Expr.T b) {
        return new_(a, b, false);
    }

    private static Type.T typeOf(P p) {
        Type.T ta;
        ta = Type.base(Expr.typeOf(p.a));
        if ((p.extended) && EnumType.is(ta)) {
            ta = Int.T;
        } else if (Type.isSubtype(ta, Addr.T)
            && Type.isSubtype(Type.base(Expr.typeOf(p.b)), Addr.T)) {
            ta = Int.T;
        }
        return ta;
    }

    private static Type.T repTypeOf(P p) {
        Type.T ta;
        ta = Type.base(Expr.repTypeOf(p.a));
        if ((p.extended) && EnumType.is(ta)) {
            ta = Int.T;
        } else if (Type.isSubtype(ta, Addr.T)
            && Type.isSubtype(Type.base(Expr.repTypeOf(p.b)), Addr.T)) {
            ta = Int.T;
        }
        return ta;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T ta, tb;
        Type.T[] range = new Type.T[1];
        Expr.typeCheck(p.a, cs);
        Expr.typeCheck(p.b, cs);
        ta = Type.base(Expr.typeOf(p.a));
        tb = Type.base(Expr.typeOf(p.b));
        if ((ta == Int.T) && (tb == Int.T)) {
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
                ta = Expr.badOperands("'-'", ta, tb);
            }
        } else if (EnumType.is(ta)) {
            p.class_ = Class.cENUM;
            if ((p.extended) && (tb == Int.T)) {
                ta = Int.T;
            } else {
                ta = Expr.badOperands("'-'", ta, tb);
            }
        } else if (Type.isSubtype(ta, Addr.T)) {
            p.class_ = Class.cADDR;
            if ((tb == Int.T)) {
                ta = Addr.T;
            } else if (Type.isSubtype(tb, Addr.T)) {
                ta = Int.T;
            } else {
                ta = Expr.badOperands("'-'", ta, tb);
            }
            if (Module.isSafe()) {
                Error.msg("unsafe '-'");
            }
        } else {
            ta = Expr.badOperands("'-'", ta, tb);
            p.class_ = Class.cINT;
        }
        p.type = ta;
    }

    private static void prep(P p) {
        Type.Info info = new Type.Info();
        Expr.prep(p.a);
        Expr.prep(p.b);
        if ((p.class_ == Class.cSET)) {
            Type.checkInfo(p.type, info);
            if ((info.size > M3.target.Integer.size)) {
                p.tmp = CG.declare_temp(info.size, M3.target.Integer.align,
                                        CGType.Struct, /* in_memory := */ true);
                CG.load_addr_of(p.tmp, 0, M3.target.Integer.align);
                CG.forceStacked();
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.set_difference(info.size);
            }
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        long size;
        Type.Info info = new Type.Info();
        AssertError.check(!StaticOnly, "SubtractExpr");
        switch (p.class_) {
            case cINT, cENUM -> {
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.subtract(M3.target.Integer.cg_type);
            }
            case cLINT -> {
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.subtract(M3.target.Longint.cg_type);
            }
            case cREAL, cLONG, cEXTND -> {
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.subtract(FPType[p.class_.ordinal() - Class.cREAL.ordinal()]);
            }
            case cSET -> {
                Type.checkInfo(p.type, info);
                size = info.size;
                if ((size > M3.target.Integer.size)) {
                    // Prep did most of the work
                    CG.load_addr_of_temp(p.tmp, 0, M3.target.Integer.align);
                    p.tmp = null;
                } else {
                    Expr.compile(p.a);
                    Expr.compile(p.b);
                    CG.set_difference(size);
                }
            }
            case cADDR -> {
                if ((p.type == Addr.T)) { // Addr - Int
                    Expr.compile(p.a);
                    Expr.compile(p.b);
                    CG.index_bytes(-Target.Byte);
                } else { // Addr - Addr
                    Expr.compile(p.a);
                    CG.loophole(CGType.Addr, M3.target.Word.cg_type);
                    Expr.compile(p.b);
                    CG.loophole(CGType.Addr, M3.target.Word.cg_type);
                    CG.subtract(M3.target.Word.cg_type);
                }
            }
        }
    }

    private static Expr.T fold(P p) {
        Expr.T e1, e2;
        Expr.T[] e3 = new Expr.T[1];
        int[] x1 = TInt.newInt(), x2 = TInt.newInt(), x3 = TInt.newInt();
        Type.T[] t1 = new Type.T[1];
        e1 = Expr.constValue(p.a);
        e2 = Expr.constValue(p.b);
        e3[0] = null;
        if ((e1 == null) || (e2 == null)) {
            // nothing
        } else if (IntegerExpr.subtract(e1, e2, e3)) {
            // nothing
        } else if ((p.extended)
            && EnumExpr.split(e1, x1, t1)
            && IntegerExpr.split(e2, x2, t1)
            && TInt.subtract(x1, x2, x3)
            && !TInt.LT(x3, M3.target.Integer.min)
            && !TInt.LT(M3.target.Integer.max, x3)) {
            e3[0] = IntegerExpr.new_(t1[0], x3);
        } else if (ReelExpr.subtract(e1, e2, e3)) {
            // nothing
        } else if (AddressExpr.subtract(e1, e2, e3)) {
            // nothing
        } else if (SetExpr.difference(e1, e2, e3)) {
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
        if (TInt.subtract(min_a, max_b, smin)
            && !TInt.LT(smin, min) && !TInt.LT(max, smin)
            && TInt.subtract(max_a, min_b, smax)
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
