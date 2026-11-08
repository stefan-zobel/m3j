// Java port of CM3's m3-sys/m3front/src/builtinOps/Abs.i3 and Abs.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;

// File: Abs.m3
// Last modified on Tue May  3 16:29:56 PDT 1994 by kalsow
//      modified on Wed Nov 28 02:49:27 1990 by muller

public final class Abs {
    private Abs() {}

    private static CallExpr.MethodList Z;

    private static Type.T typeOf(CallExpr.T ce) {
        return Type.base(Expr.typeOf(ce.args[0]));
    }

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T t = typeOf(ce);
        ce.type = t;
        if ((t == Int.T) || (t == LInt.T)
            || (t == Reel.T) || (t == LReel.T) || (t == EReel.T)) {
            // ok
        } else {
            Error.msg("ABS: wrong argument type");
        }
    }

    private static void compile(CallExpr.T ce) {
        Expr.T e = ce.args[0];
        Expr.compile(e);
        CG.abs(Type.cgType(Expr.typeOf(e)));
    }

    private static Expr.T fold(CallExpr.T ce) {
        Expr.T e;
        Expr.T[] x = new Expr.T[1];
        e = Expr.constValue(ce.args[0]);
        if ((e == null)) {
            return null;
        } else if (IntegerExpr.abs(e, x)) {
            return x[0];
        } else if (ReelExpr.abs(e, x)) {
            return x[0];
        } else {
            return null;
        }
    }

    private static void getBounds(CallExpr.T ce, int[] min, int[] max) {
        Expr.getBounds(ce.args[0], min, max);
        if (TInt.LT(min, TInt.Zero)) {
            TInt.assign(min, TInt.Zero);
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 1, true, false, true, null,
                                   Abs::typeOf,
                                   Abs::typeOf,
                                   CallExpr::notAddressable,
                                   Abs::check,
                                   CallExpr::prepArgs,
                                   Abs::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   Abs::fold,
                                   Abs::getBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("ABS", Z, true);
    }
}
