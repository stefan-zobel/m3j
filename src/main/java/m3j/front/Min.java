// Java port of CM3's m3-sys/m3front/src/builtinOps/Min.i3 and Min.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;

// File: Min.m3
// Last Modified On Mon Sep 26 09:15:35 PDT 1994 By kalsow
//      Modified On Tue Mar 20 23:03:27 1990 By muller

public final class Min {
    private Min() {}

    private static CallExpr.MethodList Z;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Max.doCheck("MIN", ce);
    }

    private static void compile(CallExpr.T ce) {
        Expr.compile(ce.args[0]);
        Expr.compile(ce.args[1]);
        CG.min(Type.cgType(Type.base(Expr.typeOf(ce.args[0]))));
    }

    private static Expr.T fold(CallExpr.T ce) {
        Expr.T e1;
        Expr.T e2;
        Expr.T[] e3 = new Expr.T[1];
        int[] i1 = TInt.newInt();
        int[] i2 = TInt.newInt();
        Type.T[] t = new Type.T[1];
        e1 = Expr.constValue(ce.args[0]);
        if ((e1 == null)) {
            return null;
        }
        e2 = Expr.constValue(ce.args[1]);
        if ((e2 == null)) {
            return null;
        }
        if (IntegerExpr.split(e1, i1, t) && IntegerExpr.split(e2, i2, t)) {
            if (TInt.LT(i1, i2)) {
                return e1;
            } else {
                return e2;
            }
        } else if (EnumExpr.split(e1, i1, t) && EnumExpr.split(e2, i2, t)) {
            if (TInt.LT(i1, i2)) {
                return e1;
            } else {
                return e2;
            }
        } else if (ReelExpr.min(e1, e2, e3)) {
            return e3[0];
        } else {
            return null;
        }
    }

    private static void getBounds(CallExpr.T ce, int[] min, int[] max) {
        int[] min_a = TInt.newInt();
        int[] max_a = TInt.newInt();
        int[] min_b = TInt.newInt();
        int[] max_b = TInt.newInt();
        Expr.getBounds(ce.args[0], min_a, max_a);
        Expr.getBounds(ce.args[1], min_b, max_b);
        if (TInt.LT(min_a, min_b)) {
            TInt.assign(min, min_a);
        } else {
            TInt.assign(min, min_b);
        }
        if (TInt.LT(max_a, max_b)) {
            TInt.assign(max, max_a);
        } else {
            TInt.assign(max, max_b);
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(2, 2, true, false, true, null,
                                   Max::typeOf,
                                   Max::typeOf,
                                   CallExpr::notAddressable,
                                   Min::check,
                                   CallExpr::prepArgs,
                                   Min::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::prepNoBranch,
                                   CallExpr::noBranch,
                                   Min::fold,
                                   Min::getBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("MIN", Z, true);
    }
}
