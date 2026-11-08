// Java port of CM3's m3-sys/m3front/src/builtinOps/Val.i3 and Val.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;

// File: Val.m3
// Last Modified On Tue May  3 16:33:31 PDT 1994 By kalsow
//      Modified On Fri Dec 21 01:18:57 1990 By muller

public final class Val {
    private Val() {}

    private static CallExpr.MethodList Z;

    private static Type.T typeOf(CallExpr.T ce) {
        Type.T[] t = new Type.T[1];
        if (TypeExpr.split(ce.args[1], t)) {
            return Type.stripPacked(t[0]);
        } else {
            return Int.T;
        }
    }

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T[] t = new Type.T[1];
        Type.T u;
        int[] mint = TInt.newInt();
        int[] maxt = TInt.newInt();
        int[] minu = TInt.newInt();
        int[] maxu = TInt.newInt();
        u = Expr.typeOf(ce.args[0]);
        if (Type.isSubtype(u, LInt.T)) {
            t[0] = LInt.T;
        } else if (Type.isSubtype(u, Int.T)) {
            t[0] = Int.T;
        } else {
            Error.msg("VAL: first argument must be an integer");
        }
        if (!TypeExpr.split(ce.args[1], t)) {
            Error.msg("VAL: second argument must be a type");
        } else if (!Type.isOrdinal(t[0])) {
            Error.msg("VAL: second argument must be an ordinal type");
        } else { // looks ok
            Expr.getBounds(ce.args[0], minu, maxu);
            Type.getBounds(t[0], mint, maxt);
            // TODO: Emit warnings when statically detectable RT errors.
            if (TInt.LT(minu, mint)) {
                // we need a lower bound check
                if (TInt.LT(maxt, maxu)) {
                    // we also need an upper bound check
                    ce.args[0] = CheckExpr.new_(ce.args[0], mint, maxt,
                                                M3CG.RuntimeError.ValueOutOfRange);
                    Expr.typeCheck(ce.args[0], cs);
                } else {
                    ce.args[0] = CheckExpr.newLower(ce.args[0], mint,
                                                    M3CG.RuntimeError.ValueOutOfRange);
                    Expr.typeCheck(ce.args[0], cs);
                }
            } else if (TInt.LT(maxt, maxu)) {
                // we need an upper bound check
                ce.args[0] = CheckExpr.newUpper(ce.args[0], maxt,
                                                M3CG.RuntimeError.ValueOutOfRange);
                Expr.typeCheck(ce.args[0], cs);
            }
        }
        ce.type = t[0];
    }

    private static void prep(CallExpr.T ce) {
        Expr.prep(ce.args[0]);
    }

    private static void compile(CallExpr.T ce) {
        Type.T[] t = new Type.T[1];
        Type.T u;
        u = Expr.typeOf(ce.args[0]);
        if (TypeExpr.split(ce.args[1], t)) {
            Type.compile(t[0]);
        }
        Expr.compile(ce.args[0]);
        if (Type.isSubtype(t[0], LInt.T)) {
            // definitely not an enumeration
            if (Type.isSubtype(u, Int.T)) {
                CG.loophole(M3.target.Integer.cg_type, M3.target.Longint.cg_type);
            }
        } else {
            // base type Int.T or enumeration
            if (Type.isSubtype(u, LInt.T)) {
                CG.loophole(M3.target.Longint.cg_type, M3.target.Integer.cg_type);
            }
        }
    }

    private static Expr.T fold(CallExpr.T ce) {
        Type.T[] t = new Type.T[1];
        Expr.T e;
        int[] x = TInt.newInt();
        int[] min = TInt.newInt();
        int[] max = TInt.newInt();
        e = Expr.constValue(ce.args[0]);
        if ((e == null) || (!IntegerExpr.split(e, x, t))
            || (!TypeExpr.split(ce.args[1], t))) {
            return null;
        }
        Type.getBounds(t[0], min, max);
        if (TInt.LT(x, min) || TInt.LT(max, x)) {
            Error.msg("VAL: value out of range");
            return null;
        }
        t[0] = Type.base(t[0]);
        if (EnumType.is(t[0])) {
            return EnumExpr.new_(t[0], x);
        } else {
            return IntegerExpr.new_(t[0], x);
        }
    }

    private static void getBounds(CallExpr.T ce, int[] min, int[] max) {
        Expr.getBounds(ce.args[0], min, max);
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(2, 2, true, false, true, null,
                                   Val::typeOf,
                                   Val::typeOf,
                                   CallExpr::notAddressable,
                                   Val::check,
                                   Val::prep,
                                   Val::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::prepNoBranch,
                                   CallExpr::noBranch,
                                   Val::fold,
                                   Val::getBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("VAL", Z, true);
    }
}
