// Java port of CM3's m3-sys/m3front/src/builtinInfo/InfoThisFile.i3 and InfoThisFile.m3.
// Original: Copyright 1996-2000, Critical Mass, Inc.  All rights reserved.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.Target;

public final class InfoThisFile {
    private InfoThisFile() {}

    private static CallExpr.MethodList Z;
    private static Value.T formals;
    private static Expr.T value = null;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        // Port: ce.args is the VAR parameter actuals of Formal.CheckArgs.
        Expr.T[][] actuals = {ce.args};
        Formal.checkArgs(cs, actuals, formals, ce.proc);
        ce.args = actuals[0];
        ce.type = Textt.T;
    }

    private static void compile(CallExpr.T ce) {
        getValue();
        Expr.prep(value);
        Expr.compile(value);
    }

    private static Expr.T fold(CallExpr.T ce) {
        getValue();
        return value;
    }

    public static void initialize() {
        Type.T t0 = ProcType.new_(Textt.T);
        Z = CallExpr.newMethodList(0, 0, true, true, true, Textt.T,
                                   null, null,
                                   CallExpr::notAddressable,
                                   InfoThisFile::check,
                                   CallExpr::prepArgs,
                                   InfoThisFile::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   InfoThisFile::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("ThisFile", Z, false, t0);
        formals = ProcType.formals(t0);
    }

    public static void reset() {
        value = null;
    }

    private static void getValue() {
        String[] file = new String[1];
        long[] line = new long[1];
        if ((value == null)) {
            Scanner.localHere(file, line);
            file[0] = Target.cleanupSourcePath(M3.target, file[0]);
            value = TextExpr.new8(M3String.add(file[0]));
        }
    }
}
