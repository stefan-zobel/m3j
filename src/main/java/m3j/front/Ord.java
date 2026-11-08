// Java port of CM3's m3-sys/m3front/src/builtinOps/Ord.i3 and Ord.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;

// File: Ord.m3
// Last Modified On Tue May  3 16:32:32 PDT 1994 By kalsow
//      Modified On Sat Dec  8 00:54:19 1990 By muller

public final class Ord {
    private Ord() {}

    private static CallExpr.MethodList Z;

    private static Type.T typeOf(CallExpr.T ce) {
        Type.T t = Expr.typeOf(ce.args[0]);
        if (Type.isSubtype(t, LInt.T)) {
            return LInt.T;
        } else {
            return Int.T;
        }
    }

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T t;
        t = Expr.typeOf(ce.args[0]);
        if (!Type.isOrdinal(t)) {
            Error.msg("ORD: argument must be an ordinal");
        }
        if (Type.isSubtype(t, LInt.T)) {
            ce.type = LInt.T;
        } else {
            ce.type = Int.T;
        }
    }

    private static void compile(CallExpr.T ce) {
        Expr.compile(ce.args[0]);
    }

    private static Expr.T fold(CallExpr.T ce) {
        Expr.T e;
        int[] i = TInt.newInt();
        Type.T[] t = new Type.T[1];
        e = Expr.constValue(ce.args[0]);
        if ((e == null)) {
            return null;
        } else if (EnumExpr.split(e, i, t)) {
            return IntegerExpr.new_(Int.T, i);
        } else if (IntegerExpr.split(e, i, t)) {
            return IntegerExpr.new_(t[0], i);
        } else {
            return null;
        }
    }

    private static void getBounds(CallExpr.T ce, int[] min, int[] max) {
        Expr.getBounds(ce.args[0], min, max);
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 1, true, false, true, null,
                                   Ord::typeOf,
                                   Ord::typeOf,
                                   CallExpr::notAddressable,
                                   Ord::check,
                                   CallExpr::prepArgs,
                                   Ord::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   Ord::fold,
                                   Ord::getBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("ORD", Z, true);
    }
}
