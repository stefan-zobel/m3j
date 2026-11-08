// Java port of CM3's m3-sys/m3front/src/builtinOps/Floatt.i3 and Floatt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: Floatt.m3
// Last Modified On Tue May  3 16:30:33 PDT 1994 By kalsow
//      Modified On Sat Dec  8 00:54:23 1990 By muller

public final class Floatt {
    private Floatt() {}

    private static CallExpr.MethodList Z;

    private static Type.T typeOf(CallExpr.T ce) {
        Type.T[] u = new Type.T[1];
        u[0] = Reel.T;
        if ((ce.args.length > 1)) {
            TypeExpr.split(ce.args[1], u);
            u[0] = Type.base(u[0]);
        }
        return u[0];
    }

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T t;
        Type.T[] u = new Type.T[1];
        Expr.T e;
        e = ce.args[0];

        u[0] = Reel.T;
        if ((ce.args.length > 1)) {
            if (!TypeExpr.split(ce.args[1], u)) {
                Error.msg("FLOAT: second argument must be a floating point type");
            }
            u[0] = Type.base(u[0]);
        }

        t = Type.base(Expr.typeOf(ce.args[0]));
        if ((t != Int.T) && (t != LInt.T)
            && (t != Reel.T) && (t != LReel.T) && (t != EReel.T)) {
            Error.msg("FLOAT: wrong first argument type");
        }

        if ((u[0] != Reel.T) && (u[0] != LReel.T) && (u[0] != EReel.T)) {
            Error.msg("FLOAT: wrong second argument type");
        }

        ce.type = u[0];
    }

    private static void prep(CallExpr.T ce) {
        Expr.prep(ce.args[0]);
    }

    private static void compile(CallExpr.T ce) {
        Expr.T e = ce.args[0];
        Type.T t = Expr.typeOf(e);
        Type.T u = typeOf(ce);
        Expr.compile(e);
        CG.cvt_float(Type.cgType(t), Type.cgType(u));
    }

    private static Expr.T fold(CallExpr.T ce) {
        Expr.T e;
        Expr.T[] x = new Expr.T[1];
        Type.T[] t = new Type.T[1];
        e = Expr.constValue(ce.args[0]);
        if ((e == null)) {
            return null;
        }

        t[0] = Reel.T;
        if ((ce.args.length > 1)) {
            if (!TypeExpr.split(ce.args[1], t)) {
                return null;
            }
        }

        if (ReelExpr.float_(e, t[0], x)) {
            return x[0];
        } else {
            return null;
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 2, true, false, true, null,
                                   Floatt::typeOf,
                                   Floatt::typeOf,
                                   CallExpr::notAddressable,
                                   Floatt::check,
                                   Floatt::prep,
                                   Floatt::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   Floatt::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("FLOAT", Z, true);
    }
}
