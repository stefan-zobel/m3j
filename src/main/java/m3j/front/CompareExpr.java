// Java port of CM3's m3-sys/m3front/src/exprs/CompareExpr.i3 and CompareExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.util.RangeError;

// File: CompareExpr.m3
// Last modified on Fri Feb 24 16:41:37 PST 1995 by kalsow
//      modified on Thu Nov 29 03:32:44 1990 by muller

public final class CompareExpr {
    private CompareExpr() {}

    private static final long cINT   = 0;
    private static final long cLINT  = 1;
    private static final long cREAL  = 2;
    private static final long cLONG  = 3;
    private static final long cEXTND = 4;
    private static final long cADDR  = 5;
    private static final long cENUM  = 6;
    private static final long cSET   = 7;

    /** Port: indexed by {@code class - cREAL}. */
    private static final Target.CGType[] CGType = {
        Target.CGType.Reel, Target.CGType.LReel, Target.CGType.XReel,  Target.CGType.Addr };

    // Port: Op = [CG.Cmp.GT .. CG.Cmp.LE] is M3CG.CompareOp; New checks
    // the range.

    public static final class OpDesc {
        public final long signA, signB;
        public final String name;

        OpDesc(long signA, long signB, String name) {
            this.signA = signA;
            this.signB = signB;
            this.name = name;
        }
    }

    /** Port: indexed by {@code op.ordinal() - CompareOp.GT.ordinal()}. */
    private static final OpDesc[] Ops = {
        /*GT*/ new OpDesc(  1,  1, "'>'" ),
        /*GE*/ new OpDesc(  1,  0, "'>='" ),
        /*LT*/ new OpDesc( -1, -1, "'<'" ),
        /*LE*/ new OpDesc( -1,  0, "'<='" )
    };

    /** Port: the subscript Ops[op]. */
    private static OpDesc ops(M3CG.CompareOp op) {
        return Ops[op.ordinal() - M3CG.CompareOp.GT.ordinal()];
    }

    public static final class P extends ExprRep.Tabc {
        public M3CG.CompareOp op;
        public boolean bad_set;

        @Override
        public void check(M3.CheckState cs) {
            CompareExpr.check(this, cs);
        }

        @Override
        public void prep() {
            CompareExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            CompareExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepBR(long true_, long false_, int freq) {
            CompareExpr.prepBR(this, true_, false_, freq);
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

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType,
        // need_addr := ExprRep.NotAddressable, prepLV := ExprRep.NotLValue,
        // compileLV := ExprRep.NotLValueBool, getBounds :=
        // ExprRep.NoBounds, isWritable, isDesignator, isZeroes :=
        // ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral, prepLiteral
        // := ExprRep.NoPrepLiteral, genLiteral := ExprRep.NoLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
    }

    public static Expr.T new_(Expr.T a, Expr.T b, M3CG.CompareOp op) {
        P p;
        // Port: the range check of the parameter op: Op.
        if (op.ordinal() < M3CG.CompareOp.GT.ordinal()
            || op.ordinal() > M3CG.CompareOp.LE.ordinal()) {
            throw new RangeError("CompareExpr");
        }
        p = new P();
        ExprRep.init(p);
        p.a       = a;
        p.b       = b;
        p.op      = op;
        p.type    = Bool.T;
        p.repType = Bool.T;
        p.bad_set = false;
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T ta, tb;
        Type.T[] range = new Type.T[1];
        Type.Info info = new Type.Info();
        Expr.typeCheck(p.a, cs);
        Expr.typeCheck(p.b, cs);
        ta = Type.base(Expr.typeOf(p.a));
        tb = Type.base(Expr.typeOf(p.b));
        p.class_ = cINT;
        if ((ta == Int.T) && (tb == Int.T)) {
            p.class_ = cINT;
        } else if ((ta == LInt.T) && (tb == LInt.T)) {
            p.class_ = cLINT;
        } else if ((ta == Reel.T) && (tb == Reel.T)) {
            p.class_ = cREAL;
        } else if ((ta == LReel.T) && (tb == LReel.T)) {
            p.class_ = cLONG;
        } else if ((ta == EReel.T) && (tb == EReel.T)) {
            p.class_ = cEXTND;
        } else if ((Type.isSubtype(ta, Addr.T)) && (Type.isSubtype(tb, Addr.T))) {
            if (Module.isSafe()) {
                Error.msg("unsafe operation");
            }
            p.class_ = cADDR;
        } else if (!Type.isEqual(ta, tb, null)) {
            err(p, ta, tb);
        } else if (EnumType.is(ta)) {
            p.class_ = cENUM;
        } else if (SetType.split(ta, range)) {
            p.class_ = cSET;
            ta = Type.checkInfo(ta, info);
        } else {
            err(p, ta, tb);
        }
        if ((p.class_ == cSET)
            && ((p.op == M3CG.CompareOp.LT) || (p.op == M3CG.CompareOp.GT))
            && (info.size <= M3.target.Integer.size)) {
            p.bad_set = true;
        }
    }

    private static void err(P p, Type.T a, Type.T b) {
        p.type = Expr.badOperands(ops(p.op).name, a, b);
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e == null) {
            return false;
        } else if (e instanceof P b) {
            return (a.class_ == b.class_)
                && (a.op == b.op)
                && Expr.isEqual(a.a, b.a, x)
                && Expr.isEqual(a.b, b.b, x);
        } else {
            return false;
        }
    }

    private static void prep(P p) {
        Expr.prep(p.a);
        Expr.prep(p.b);
    }

    private static void compile(P p, boolean StaticOnly) {
        Target.CGType type;
        CG.Val ta, tb, tmp;
        Type.Info info = new Type.Info();
        if ((p.class_ != cSET)) {
            Expr.compile(p.a);
            Expr.compile(p.b);
            if ((p.class_ == cINT) || (p.class_ == cENUM)) {
                type = M3.target.Integer.cg_type;
            } else if ((p.class_ == cLINT)) {
                type = M3.target.Longint.cg_type;
            } else {
                type = CGType[(int) (p.class_ - cREAL)];
            }
            CG.compare(type, p.op);
        } else if ((p.bad_set)) {
            Expr.compile(p.a);
            ta = CG.pop();
            Expr.compile(p.b);
            tb = CG.pop();
            if ((p.op == M3CG.CompareOp.GT)) {
                tmp = ta;
                ta = tb;
                tb = tmp;
            }
            CG.push(ta);
            CG.push(tb);
            CG.compare(M3.target.Word.cg_type, M3CG.CompareOp.NE);
            CG.push(ta);
            CG.push(tb);
            CG.or(M3.target.Word.cg_type);
            CG.push(tb);
            CG.compare(M3.target.Word.cg_type, M3CG.CompareOp.EQ);
            CG.and(M3.target.Word.cg_type);
            CG.free(ta);
            CG.free(tb);
        } else { // simple set ops
            Expr.compile(p.a);
            Expr.compile(p.b);
            Type.checkInfo(Expr.typeOf(p.a), info);
            CG.set_compare(info.size, p.op);
        }
    }

    private static void prepBR(P p, long true_, long false_, int freq) {
        Target.CGType type;
        CG.Val ta, tb, tmp;
        Type.Info info = new Type.Info();
        Expr.prep(p.a);
        Expr.prep(p.b);
        if ((p.class_ != cSET)) {
            Expr.compile(p.a);
            Expr.compile(p.b);
            if ((p.class_ == cINT) || (p.class_ == cENUM)) {
                type = M3.target.Integer.cg_type;
            } else if ((p.class_ == cLINT)) {
                type = M3.target.Longint.cg_type;
            } else {
                type = CGType[(int) (p.class_ - cREAL)];
            }
            CG.if_then(type, p.op, true_, false_, freq);
        } else if ((p.bad_set)) {
            Expr.compile(p.a);
            ta = CG.pop();
            Expr.compile(p.b);
            tb = CG.pop();
            if ((p.op == M3CG.CompareOp.GT)) {
                tmp = ta;
                ta = tb;
                tb = tmp;
            }
            CG.push(ta);
            CG.push(tb);
            CG.compare(M3.target.Word.cg_type, M3CG.CompareOp.NE);
            CG.push(ta);
            CG.push(tb);
            CG.not(M3.target.Word.cg_type);
            CG.and(M3.target.Word.cg_type);
            CG.load_integer(M3.target.Word.cg_type, TInt.Zero);
            CG.compare(M3.target.Word.cg_type, M3CG.CompareOp.EQ);
            CG.and(M3.target.Word.cg_type);
            if ((true_ != CG.No_label)) {
                CG.if_true(true_, freq);
            } else {
                CG.if_false(false_, freq);
            }
            CG.free(ta);
            CG.free(tb);
        } else { // simple set ops
            Expr.compile(p.a);
            Expr.compile(p.b);
            Type.checkInfo(Expr.typeOf(p.a), info);
            CG.set_compare(info.size, p.op);
            if ((true_ != CG.No_label)) {
                CG.if_true(true_, freq);
            } else {
                CG.if_false(false_, freq);
            }
        }
    }

    private static Expr.T fold(P p) {
        Expr.T e1, e2;
        long[] s = new long[1];
        e1 = Expr.constValue(p.a);
        if ((e1 == null)) {
            return null;
        }
        e2 = Expr.constValue(p.b);
        if ((e2 == null)) {
            return null;
        }
        if (IntegerExpr.compare(e1, e2, s)
            || EnumExpr.compare(e1, e2, s)
            || ReelExpr.compare(e1, e2, s)
            || AddressExpr.compare(e1, e2, s)
            || SetExpr.compare(e1, e2, s)) {
            return Bool.Map[((s[0] == ops(p.op).signA) || (s[0] == ops(p.op).signB)) ? 1 : 0];
        }
        return null;
    }
}
