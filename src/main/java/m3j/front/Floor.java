// Java port of CM3's m3-sys/m3front/src/builtinOps/Floor.i3 and Floor.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;

// File: Floor.m3
// Last Modified On Mon Sep 26 09:15:22 PDT 1994 By kalsow
//      Modified On Tue Mar 20 04:11:50 1990 By muller

public final class Floor {
    private Floor() {}

    private static CallExpr.MethodList Z;

    private static Type.T typeOf(CallExpr.T ce) {
        Type.T[] t = new Type.T[1];
        t[0] = Int.T;
        if ((ce.args.length > 1)) {
            TypeExpr.split(ce.args[1], t);
            t[0] = Type.base(t[0]);
        }
        return t[0];
    }

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Ceiling.doCheck("FLOOR", ce, cs);
    }

    private static void prep(CallExpr.T ce) {
        Expr.prep(ce.args[0]);
    }

    private static void compile(CallExpr.T ce) {
        Expr.T e = ce.args[0];
        Type.T t = typeOf(ce);
        Expr.compile(e);
        CG.cvt_int(Type.cgType(Expr.typeOf(e)), Type.cgType(t), M3CG.ConvertOp.Floor);
    }

    private static Expr.T fold(CallExpr.T ce) {
        Expr.T e;
        Expr.T[] x = new Expr.T[1];
        Type.T t = typeOf(ce);
        e = Expr.constValue(ce.args[0]);
        if ((e == null)) {
            return null;
        } else if (ReelExpr.floor(e, t, x)) {
            return x[0];
        } else { // bogus
            return null;
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 2, true, false, true, null,
                                   Floor::typeOf,
                                   Floor::typeOf,
                                   CallExpr::notAddressable,
                                   Floor::check,
                                   Floor::prep,
                                   Floor::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   Floor::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("FLOOR", Z, true);
    }
}
