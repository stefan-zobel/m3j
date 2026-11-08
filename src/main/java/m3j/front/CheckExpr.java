// Java port of CM3's m3-sys/m3front/src/exprs/CheckExpr.i3 and CheckExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: CheckExpr.m3
// Last Modified On Fri Feb 24 16:41:16 PST 1995 By kalsow
//      Modified On Fri Feb 15 04:03:38 1991 By muller

/** Range checks for ordinal types. */
public final class CheckExpr {
    private CheckExpr() {}

    public enum Class { cLOWER, cUPPER, cBOTH }

    // CLEAN ME UP: Use ExprRep.Ta as parent type, then replace uses of
    //              field 'expr' by field 'a' from Ta.
    //              Could then use general-purpose ExprRep.ExprAlignArg0
    //              instead of CheckExpr-specific CheckExprAlign.
    //              There are several *Expr.P types that could use the
    //              same.
    public static final class P extends Expr.T {
        public Expr.T expr;
        public final int[] min = TInt.newInt();
        public final int[] max = TInt.newInt();
        public Class class_;
        public M3CG.RuntimeError err;

        @Override
        public Type.T typeOf() {
            return CheckExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return CheckExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            CheckExpr.check(this, cs);
        }

        @Override
        public void prep() {
            CheckExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            CheckExpr.compile(this, StaticOnly);
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
        public void getBounds(int[] min, int[] max) {
            bounder(this, min, max);
        }

        @Override
        public long exprAlign() {
            return checkExprAlign(this);
        }

        // Port: the overrides need_addr := ExprRep.NotAddressable,
        // prepLV := ExprRep.NotLValue, compileLV := ExprRep.NotLValueBool,
        // isWritable, isDesignator, isZeroes := ExprRep.IsNever,
        // genFPLiteral := ExprRep.NoFPLiteral, prepLiteral :=
        // ExprRep.NoPrepLiteral, genLiteral := ExprRep.NoLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
    }

    // Check pass:

    /**
     * New, NewLower, and NewUpper insert a CheckExpr.T node that, when
     * compiled, will emit conditional RT range checks on its operand
     * expression. They do NOT emit warnings on statically detectable
     * failures. Callers must do that. Nor do they simplify generated code
     * to unconditional aborts on statically detectable failures.
     */
    public static Expr.T new_(Expr.T a, int[] min, int[] max, M3CG.RuntimeError err) {
        return create(a, min, max, Class.cBOTH, err);
    }

    /** See {@link #new_}. */
    public static Expr.T newLower(Expr.T a, int[] min, M3CG.RuntimeError err) {
        return create(a, min, TInt.Zero, Class.cLOWER, err);
    }

    /** See {@link #new_}. */
    public static Expr.T newUpper(Expr.T a, int[] max, M3CG.RuntimeError err) {
        return create(a, TInt.Zero, max, Class.cUPPER, err);
    }

    private static Expr.T create(Expr.T a, int[] min, int[] max, Class c,
                                 M3CG.RuntimeError err) {
        P p;
        if ((!Host.doRangeChk)) {
            return a;
        }
        p = new P();
        ExprRep.init(p);
        p.expr   = a;
        TInt.assign(p.min, min);
        TInt.assign(p.max, max);
        p.class_ = c;
        p.err    = err;
        p.origin = a.origin;
        return p;
    }

    private static Type.T typeOf(P p) {
        return Expr.typeOf(p.expr);
    }

    private static Type.T repTypeOf(P p) {
        return Expr.repTypeOf(p.expr);
    }

    private static void check(P p, M3.CheckState cs) {
        Expr.typeCheck(p.expr, cs);
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e == null) {
            return false;
        } else if (e instanceof P b) {
            return (a.class_ == b.class_)
                && TInt.EQ(a.min, b.min)
                && TInt.EQ(a.max, b.max)
                && Expr.isEqual(a.expr, b.expr, x);
        } else {
            return false;
        }
    }

    private static long checkExprAlign(P p) {
        return Expr.alignment(p.expr);
    }

    private static void prep(P p) {
        Expr.prep(p.expr);
    }

    private static void compile(P p, boolean StaticOnly) {
        Type.T t = Expr.typeOf(p.expr);
        CGType cg_type;
        AssertError.check(!StaticOnly, "CheckExpr");
        if (Type.isSubtype(t, LInt.T)) {
            cg_type = M3.target.Longint.cg_type;
        } else {
            cg_type = M3.target.Integer.cg_type;
        }
        Expr.compile(p.expr, StaticOnly);
        switch (p.class_) {
            case cLOWER -> CG.check_lo(cg_type, p.min, p.err);
            case cUPPER -> CG.check_hi(cg_type, p.max, p.err);
            case cBOTH  -> CG.check_range(cg_type, p.min, p.max, p.err);
        }
    }

    // Compile pass:

    /**
     * Compiles 'e' and ensures that it's contained in [min..max].
     * Does not construct any expression node.
     * Does emit warnings on statically detectable RT failures.
     * Does not simplify generated code to unconditional aborts
     * on statically detectable failures.
     */
    public static void emitChecks(Expr.T e, int[] min, int[] max, M3CG.RuntimeError err) {
        // Compiles 'e' and ensures that it's contained in [min..max].
        // Does not construct an expression node.
        int[] minE = TInt.newInt(), maxE = TInt.newInt();
        Expr.T x;
        Type.T t = Expr.typeOf(e);
        CGType cg_type;
        if (Type.isSubtype(t, LInt.T)) {
            cg_type = M3.target.Longint.cg_type;
        } else {
            cg_type = M3.target.Integer.cg_type;
        }
        x = Expr.constValue(e);
        if ((x != null)) {
            e = x;
        }
        Expr.compile(e);
        if (Host.doRangeChk) {
            Expr.getBounds(e, minE, maxE);
            if (TInt.LT(minE, min) && TInt.LT(max, maxE)) {
                CG.check_range(cg_type, min, max, err);
            } else if (TInt.LT(minE, min)) {
                if (TInt.LT(maxE, min)) {
                    Error.warn(2, "Value out of range--low.");
                }
                CG.check_lo(cg_type, min, err);
            } else if (TInt.LT(max, maxE)) {
                if (TInt.LT(max, minE)) {
                    Error.warn(2, "Value out of range--high.");
                }
                CG.check_hi(cg_type, max, err);
            }
        }
    }

    private static Expr.T fold(P p) {
        Expr.T e;
        int[] i = TInt.newInt();
        Type.T[] t = new Type.T[1];
        e = Expr.constValue(p.expr);
        if ((e == null)) {
            return null;
        }
        if ((!IntegerExpr.split(e, i, t))
            && (!EnumExpr.split(e, i, t))) {
            return null;
        }
        switch (p.class_) {
            case cLOWER -> {
                if (TInt.LT(i, p.min)) {
                    return null;
                }
            }
            case cUPPER -> {
                if (TInt.LT(p.max, i)) {
                    return null;
                }
            }
            case cBOTH -> {
                if (TInt.LT(i, p.min)
                    || TInt.LT(p.max, i)) {
                    return null;
                }
            }
        }
        return e;
    }

    private static void bounder(P p, int[] min, int[] max) {
        Expr.getBounds(p.expr, min, max);
        switch (p.class_) {
            case cLOWER -> {
                if (TInt.LT(min, p.min)) {
                    TInt.assign(min, p.min);
                }
            }
            case cUPPER -> {
                if (TInt.LT(p.max, max)) {
                    TInt.assign(max, p.max);
                }
            }
            case cBOTH -> {
                if (TInt.LT(min, p.min)) {
                    TInt.assign(min, p.min);
                }
                if (TInt.LT(p.max, max)) {
                    TInt.assign(max, p.max);
                }
            }
        }
    }
}
