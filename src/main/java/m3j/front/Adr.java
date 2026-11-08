// Java port of CM3's m3-sys/m3front/src/builtinOps/Adr.i3 and Adr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.Target;

// File: Adr.m3
// Last Modified On Wed Jun 29 16:59:33 PDT 1994 By kalsow
//      Modified On Sat Dec  8 00:54:28 1990 By muller

public final class Adr {
    private Adr() {}

    private static CallExpr.MethodList Z;

    private static Type.T typeOf(CallExpr.T ce) {
        if (Host.new_adr) {
            return RefType.new_(Expr.typeOf(ce.args[0]), false, null);
        } else {
            return Addr.T;
        }
    }

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Expr.T e = ce.args[0];
        if (ce.type == null) {
            ce.type = typeOf(ce);
        }
        ce.type = Type.check(ce.type);
        if (Module.isSafe()) {
            Error.msg("unsafe operation");
        }
        if (!Expr.isDesignator(e)) {
            Error.msg("Argument of ADR must be a designator (2.7).");
            ce.hasError = true;
        } else {
            Expr.needsAddress(e);
            if (Math.floorMod(Expr.alignment(e), Target.Byte) != 0) {
                Error.msg
                    ("CM3 restriction: non-byte-aligned value cannot be passed to ADR"
                     + " (2.3.2)");
                ce.hasError = true;
            }
        }
    }

    private static void prep(CallExpr.T ce) {
        if (ce.hasError) {
            return;
        }
        Expr.prepLValue(ce.args[0], /* traced := */ false);
    }

    private static void compile(CallExpr.T ce) {
        if (ce.hasError) {
            return;
        }
        Expr.compileLValue(ce.args[0], /* traced := */ false);
        CG.check_byte_aligned();
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 1, true, false, true, null,
                                   Adr::typeOf,
                                   Adr::typeOf,
                                   CallExpr::notAddressable,
                                   Adr::check,
                                   Adr::prep,
                                   Adr::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   CallExpr::noValue,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("ADR", Z, true);
    }
}
