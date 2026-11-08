// Java port of CM3's m3-sys/m3front/src/builtinOps/ByteSize.i3 and ByteSize.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: ByteSize.m3
// Last Modified On Tue May  3 16:29:36 PDT 1994 By kalsow
//      Modified On Tue Mar 20 03:05:35 1990 By muller

public final class ByteSize {
    private ByteSize() {}

    private static CallExpr.MethodList Z;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        BitSize.doCheck("BYTESIZE", ce, cs);
    }

    private static void prep(CallExpr.T ce) {
        BitSize.doPrep(ce.args[0]);
    }

    private static void compile(CallExpr.T ce) {
        BitSize.doCompile(ce.args[0], 8);
        // Note: the language defines "8 bit bytes" otherwise, we'd use
        // Target.ByteSize or Target.Char.size as a byte...
    }

    private static Expr.T fold(CallExpr.T ce) {
        return BitSize.doFold(ce.args[0], 8);
        // Note: the language defines "8 bit bytes" otherwise, we'd use
        // Target.ByteSize or Target.Char.size as a byte...
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 1, true, false, false, Card.T,
                                   null, null,
                                   CallExpr::notAddressable,
                                   ByteSize::check,
                                   ByteSize::prep,
                                   ByteSize::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   ByteSize::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("BYTESIZE", Z, true);
    }
}
