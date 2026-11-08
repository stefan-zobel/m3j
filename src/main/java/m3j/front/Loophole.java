// Java port of CM3's m3-sys/m3front/src/builtinOps/Loophole.i3 and Loophole.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: Loophole.m3
// Last Modified On Tue May  3 16:36:31 PDT 1994 By kalsow
//      Modified On Sat Dec  8 00:57:40 1990 By muller

public final class Loophole {
    private Loophole() {}

    private static CallExpr.MethodList Z;

    private static Type.T typeOf(CallExpr.T ce) {
        Type.T[] t = {Int.T};
        if (TypeExpr.split(ce.args[1], t)) {
            // ok
        }
        return t[0];
    }

    private static Type.T repTypeOf(CallExpr.T ce) {
        Type.T[] t = {Int.T};
        if (TypeExpr.split(ce.args[1], t)) {
            // ok
        }
        return Type.stripPacked(t[0]);
    }

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T[] t = new Type.T[1];
        if (!TypeExpr.split(ce.args[1], t)) {
            Error.msg("LOOPHOLE: second argument must be a type");
            t[0] = Int.T;
        }
        ce.args[0] = LoopholeExpr.new_(ce.args[0], t[0]);
        if (Module.isSafe()) {
            Error.msg("LOOPHOLE: unsafe operation");
        }
        Expr.typeCheck(ce.args[0], cs);
        ce.type = t[0];
    }

    private static void needsAddress(CallExpr.T ce) {
        Expr.needsAddress(ce.args[0]);
    }

    private static long loopholeExprAlign(CallExpr.T ce) {
        return Expr.alignment(ce.args[0]);
    }

    private static void prep(CallExpr.T ce) {
        Expr.prep(ce.args[0]);
    }

    private static void compile(CallExpr.T ce) {
        Expr.compile(ce.args[0]);
    }

    private static void prepLV(CallExpr.T ce, boolean traced) {
        Expr.prepLValue(ce.args[0], traced);
    }

    private static void compileLV(CallExpr.T ce, boolean traced) {
        Expr.compileLValue(ce.args[0], traced);
    }

    private static boolean isWritable(CallExpr.T ce, boolean lhs) {
        return Expr.isWritable(ce.args[0], lhs);
    }

    private static boolean isDesignator(CallExpr.T ce, boolean lhs) {
        return Expr.isDesignator(ce.args[0]);
    }

    private static void noteWrites(CallExpr.T ce) {
        Expr.noteWrite(ce.args[0]);
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(2, 2, true, false, true, null,
                                   Loophole::typeOf,
                                   Loophole::repTypeOf,
                                   Loophole::needsAddress,
                                   Loophole::check,
                                   Loophole::prep,
                                   Loophole::compile,
                                   Loophole::prepLV,
                                   Loophole::compileLV,
                                   CallExpr::prepNoBranch,
                                   CallExpr::noBranch,
                                   CallExpr::noValue, // fold
                                   CallExpr::noBounds,
                                   Loophole::isWritable,
                                   Loophole::isDesignator,
                                   Loophole::noteWrites,
                                   Loophole::loopholeExprAlign);
        Procedure.definePredefined("LOOPHOLE", Z, true);
    }
}
