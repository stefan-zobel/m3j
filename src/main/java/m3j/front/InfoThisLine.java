// Java port of CM3's m3-sys/m3front/src/builtinInfo/InfoThisLine.i3 and InfoThisLine.m3.
// Original: Copyright 1996-2000, Critical Mass, Inc.  All rights reserved.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;
import m3j.util.AssertError;

public final class InfoThisLine {
    private InfoThisLine() {}

    private static CallExpr.MethodList Z;
    private static Value.T formals;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        // Port: ce.args is the VAR parameter actuals of Formal.CheckArgs.
        Expr.T[][] actuals = {ce.args};
        Formal.checkArgs(cs, actuals, formals, ce.proc);
        ce.args = actuals[0];
        ce.type = Card.T;
    }

    private static void compile(CallExpr.T ce) {
        String[] file = new String[1];
        long[] line = new long[1];
        Scanner.here(file, line);
        line[0] = Math.max(0, line[0]);
        CG.load_intt(line[0]);
    }

    private static Expr.T fold(CallExpr.T ce) {
        String[] file = new String[1];
        long[] line = new long[1];
        boolean b;
        int[] val = TInt.newInt();
        Scanner.here(file, line);
        line[0] = Math.max(0, line[0]);
        b = TInt.fromInt(line[0], val);
        AssertError.check(b, "InfoThisLine");
        return IntegerExpr.new_(Int.T, val);
    }

    public static void initialize() {
        Type.T t0 = ProcType.new_(Card.T);
        Z = CallExpr.newMethodList(0, 0, true, true, true, Card.T,
                                   null, null,
                                   CallExpr::notAddressable,
                                   InfoThisLine::check,
                                   CallExpr::prepArgs,
                                   InfoThisLine::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   InfoThisLine::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("ThisLine", Z, false, t0);
        formals = ProcType.formals(t0);
    }
}
