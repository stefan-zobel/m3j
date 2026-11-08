// Java port of CM3's m3-sys/m3front/src/builtinTypes/Null.i3 and Null.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;

// File: Null.m3
// Last Modified On Fri Jun 24 09:43:33 PDT 1994 By kalsow
//      Modified On Fri Feb  9 06:18:44 1990 By muller

public final class Null {
    private Null() {}

    public static Type.T T;

    public static Expr.T Nil;

    public static void initialize() {
        T = RefType.new_(null, false, Brand.new_("$null$"));
        Nil = AddressExpr.new_(TInt.Zero);
        Tipe.define("NULL", T, true);
        Constant.declare("NIL", Nil, true);
    }
}
