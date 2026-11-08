// Java port of CM3's m3-sys/m3front/src/builtinTypes/Bool.i3 and Bool.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3ID;
import m3j.middle.TInt;

// File: Bool.m3
// Last Modified On Fri Jun 24 09:42:58 PDT 1994 By kalsow
//      Modified On Mon Nov 27 15:21:12 1989 By muller

public final class Bool {
    private Bool() {}

    public static Type.T T;

    public static Value.T False;

    public static Value.T True;

    /** Port: indexed by 0 (FALSE) and 1 (TRUE). */
    public static Expr.T[] Map = new Expr.T[2];

    public static void initialize() {
        Scope.T elts;
        M3.CheckState cs = M3.OuterCheckState.copy();
        Expr.T true_, false_;
        elts = Scope.pushNew(false, M3ID.add(M3.ids, "BOOLEAN"));
        T = EnumType.new_(2, elts);

        False = EnumElt.new_(M3ID.add(M3.ids, "FALSE"), TInt.Zero, T);
        Scope.insert(False);

        True = EnumElt.new_(M3ID.add(M3.ids, "TRUE"), TInt.One, T);
        Scope.insert(True);

        Scope.popNew();
        Scope.typeCheck(elts, cs);
        Tipe.define("BOOLEAN", T, true);

        false_ = EnumExpr.new_(T, TInt.Zero);
        true_ = EnumExpr.new_(T, TInt.One);

        Constant.declare("FALSE", false_, true);
        Constant.declare("TRUE", true_, true);

        Map[0] = false_;
        Map[1] = true_;
    }
}
