// Java port of CM3's m3-sys/m3front/src/builtinTypes/Charr.i3 and Charr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3ID;

public final class Charr {
    private Charr() {}

    public static Type.T T;

    public static void initialize() {
        Scope.T elts;
        M3.CheckState cs = M3.OuterCheckState.copy();
        elts = Scope.pushNew(false, M3ID.add(M3.ids, "CHAR"));
        T = EnumType.new_(256, elts);
        Scope.popNew();
        Scope.typeCheck(elts, cs);
        Tipe.define("CHAR", T, true);
    }
}
