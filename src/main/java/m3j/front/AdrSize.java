// Java port of CM3's m3-sys/m3front/src/builtinOps/AdrSize.i3 and AdrSize.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.Target;

// File: AdrSize.m3
// Last Modified On Tue May  3 16:29:11 PDT 1994 By kalsow
//      Modified On Tue Apr 10 10:57:13 1990 By muller

public final class AdrSize {
    private AdrSize() {}

    private static CallExpr.MethodList Z;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        BitSize.doCheck("ADRSIZE", ce, cs);
    }

    private static void prep(CallExpr.T ce) {
        BitSize.doPrep(ce.args[0]);
    }

    private static void compile(CallExpr.T ce) {
        BitSize.doCompile(ce.args[0], Target.Byte);
    }

    private static Expr.T fold(CallExpr.T ce) {
        return BitSize.doFold(ce.args[0], Target.Byte);
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 1, true, false, false, Card.T,
                                   null, null,
                                   CallExpr::notAddressable,
                                   AdrSize::check,
                                   AdrSize::prep,
                                   AdrSize::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   AdrSize::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("ADRSIZE", Z, true);
    }
}
