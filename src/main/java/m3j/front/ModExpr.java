// Java port of CM3's m3-sys/m3front/src/exprs/ModExpr.i3 and ModExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: ModExpr.m3
// Last modified on Thu Dec 15 14:01:22 PST 1994 by kalsow
//      modified on Thu Nov 29 03:31:49 1990 by muller

public final class ModExpr {
    private ModExpr() {}

    public enum Class { cINT, cLINT, cREAL, cLONG, cEXTND, cERR }

    /**
     * Port: indexed by {@code class.ordinal() - Class.cREAL.ordinal()}; the
     * index of cERR is out of range, as in the original.
     */
    private static final Target.CGType[] CGType = {
        Target.CGType.Reel, Target.CGType.LReel, Target.CGType.XReel };

    public static final class P extends ExprRep.Tab {
        public Class class_;
        public M3CG.Var tmp1;
        public M3CG.Var tmp2;

        @Override
        public Type.T typeOf() {
            return ModExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return ModExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            ModExpr.check(this, cs);
        }

        @Override
        public void prep() {
            ModExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            ModExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        @Override
        public void getBounds(int[] min, int[] max) {
            ModExpr.getBounds(this, min, max);
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
        p.a       = a;
        p.b       = b;
        p.type    = Int.T; // May be changed by Check.
        p.repType = Int.T; // May be changed by Check.
        p.tmp1    = null;
        p.tmp2    = null;
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
        } else {
            p.class_ = Class.cERR;
            ta = Int.T;
            ta = Expr.badOperands("MOD", ta, tb);
        }
        p.type = ta;
        p.repType = ta;
    }

    private static void prep(P p) {
        Target.CGType cg_type;
        long sz, align;
        Type.Info info = new Type.Info();
        Expr.prep(p.a);
        Expr.prep(p.b);
        if ((p.class_ != Class.cINT) && (p.class_ != Class.cLINT)) {
            // floating point: x MOD y == x - y * FLOOR (x / y)
            Type.checkInfo(p.a.type, info);
            sz = info.size;
            align = info.alignment;
            cg_type = CGType[p.class_.ordinal() - Class.cREAL.ordinal()];
            Expr.compile(p.a);
            p.tmp1 = CG.declare_temp(sz, align, cg_type, /* in_memory := */ false);
            CG.store(p.tmp1, 0, sz, align, cg_type);
            Expr.compile(p.b);
            p.tmp2 = CG.declare_temp(sz, align, cg_type, /* in_memory := */ false);
            CG.store(p.tmp2, 0, sz, align, cg_type);
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        Target.CGType cg_type;
        Type.Info info = new Type.Info();
        long sz, align;
        Expr.T e1, e2;
        Expr.T[] e3 = new Expr.T[1];
        int[] divisor = TInt.newInt(), mask = TInt.newInt();
        long[] log = new long[1];
        Type.T[] t = new Type.T[1];
        AssertError.check(!StaticOnly, "ModExpr");
        if ((p.class_ == Class.cINT) || (p.class_ == Class.cLINT)) {
            cg_type = Type.cgType(p.type);
            e1 = Expr.constValue(p.a);
            e2 = Expr.constValue(p.b);
            e3[0] = null;
            if ((e1 != null) && (e2 != null) && IntegerExpr.mod(e1, e2, e3)) {
                Expr.compile(e3[0]);
            } else if ((e2 != null)
                && IntegerExpr.split(e2, divisor, t)
                && DivExpr.smallPowerOfTwo(divisor, log)) {
                if ((e1 == null)) {
                    e1 = p.a;
                }
                if ((log[0] == 0)) {
                    // mod 1 => zero
                    Expr.compile(e1);
                    CG.discard(cg_type);
                    CG.load_integer(cg_type, TInt.Zero);
                } else {
                    TInt.subtract(divisor, TInt.One, mask);
                    Expr.compile(e1);
                    CG.load_integer(cg_type, mask);
                    CG.and(cg_type);
                }
            } else {
                if ((e1 == null)) {
                    e1 = p.a;
                }
                if ((e2 == null)) {
                    e2 = p.b;
                }
                Expr.compile(e1);
                Expr.compile(e2);
                CG.mod(cg_type, Expr.getSign(e1), Expr.getSign(e2));
            }
        } else {
            // floating point: x MOD y == x - y * FLOOR (x / y)
            Type.checkInfo(p.a.type, info);
            sz = info.size;
            align = info.alignment;
            cg_type = CGType[p.class_.ordinal() - Class.cREAL.ordinal()];
            CG.load(p.tmp1, 0, sz, align, info.addr_align, cg_type);
            CG.load(p.tmp2, 0, sz, align, info.addr_align, cg_type);
            CG.divide(cg_type);
            CG.cvt_int(cg_type, M3.target.Integer.cg_type, M3CG.ConvertOp.Floor);
            CG.cvt_float(M3.target.Integer.cg_type, cg_type);
            CG.load(p.tmp2, 0, sz, align, info.addr_align, cg_type);
            CG.multiply(cg_type);
            CG.load(p.tmp1, 0, sz, align, info.addr_align, cg_type);
            CG.swap();
            CG.subtract(cg_type);
            CG.free_temp(p.tmp1);
            CG.free_temp(p.tmp2);
            p.tmp1 = null;
            p.tmp2 = null;
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
        } else if (IntegerExpr.mod(e1, e2, e3)) {
            // nothing
        } else if (ReelExpr.mod(e1, e2, e3)) {
            // nothing
        }
        return e3[0];
    }

    private static void getBounds(P p, int[] min, int[] max) {
        int[] tmin = TInt.newInt(), tmax = TInt.newInt(), min_b = TInt.newInt(),
              max_b = TInt.newInt();
        if ((p.class_ == Class.cINT) || (p.class_ == Class.cLINT)) {
            Type.getBounds(p.type, tmin, tmax);
            Expr.getBounds(p.b, min_b, max_b);
            if (TInt.LT(min_b, TInt.Zero) || TInt.LT(max_b, TInt.Zero)) {
                ExprRep.noBounds(p, min, max);
            } else {
                TInt.assign(min, TInt.Zero);
                if (!TInt.subtract(max_b, TInt.One, max)
                    || TInt.LT(max, tmin) || TInt.LT(tmax, max)) {
                    TInt.assign(max, tmax);
                }
            }
        } else {
            ExprRep.noBounds(p, min, max);
        }
    }
}
