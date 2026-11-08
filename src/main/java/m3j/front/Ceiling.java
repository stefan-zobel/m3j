// Java port of CM3's m3-sys/m3front/src/builtinOps/Ceiling.i3 and Ceiling.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;

// File: Ceiling.m3
// Last Modified On Fri Jun 24 09:37:32 PDT 1994 By kalsow
//      Modified On Sat Dec  8 00:54:26 1990 By muller

public final class Ceiling {
    private Ceiling() {}

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
        doCheck("CEILING", ce, cs);
    }

    public static void doCheck(String name, CallExpr.T ce, M3.CheckState cs) {
        Type.T[] t = new Type.T[1];
        t[0] = Type.base(Expr.typeOf(ce.args[0]));
        if ((t[0] != Reel.T) && (t[0] != LReel.T) && (t[0] != EReel.T)) {
            Error.txt(name, "argument not REAL, LONGREAL or EXTENDED");
        }
        t[0] = Int.T;
        if ((ce.args.length > 1)) {
            if (!TypeExpr.split(ce.args[1], t)) {
                Error.txt(name, "second argument must be a type");
            }
            t[0] = Type.base(t[0]);
        }
        if ((t[0] != Int.T) && (t[0] != LInt.T)) {
            Error.txt(name, "second argument must be an integer type");
        }

        ce.type = t[0];
    }

    private static void prep(CallExpr.T ce) {
        Expr.prep(ce.args[0]);
    }

    private static void compile(CallExpr.T ce) {
        Expr.T e = ce.args[0];
        Type.T t = typeOf(ce);
        Expr.compile(e);
        CG.cvt_int(Type.cgType(Expr.typeOf(e)), Type.cgType(t), M3CG.ConvertOp.Ceiling);
    }

    private static Expr.T fold(CallExpr.T ce) {
        Expr.T e;
        Expr.T[] x = new Expr.T[1];
        Type.T t = typeOf(ce);
        e = Expr.constValue(ce.args[0]);
        if ((e == null)) {
            return null;
        } else if (ReelExpr.ceiling(e, t, x)) {
            return x[0];
        } else { // bogus
            return null;
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 2, true, false, true, null,
                                   Ceiling::typeOf,
                                   Ceiling::typeOf,
                                   CallExpr::notAddressable,
                                   Ceiling::check,
                                   Ceiling::prep,
                                   Ceiling::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   Ceiling::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("CEILING", Z, true);
    }
}
