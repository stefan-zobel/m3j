// Java port of CM3's m3-sys/m3front/src/builtinTypes/WCharr.i3 and WCharr.m3.
// Original: Copyright 1996-2000, Critical Mass, Inc.  All rights reserved.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3ID;
import m3j.middle.Target;

public final class WCharr {
    private WCharr() {}

    public static Type.T T;

    public static void initialize() {
        Scope.T elts;
        M3.CheckState cs = M3.OuterCheckState.copy();
        long NUMBER_WIDECHAR;
        elts = Scope.pushNew(false, M3ID.add(M3.ids, "WIDECHAR"));
        NUMBER_WIDECHAR = Target.wideCharNumber(M3.target);
        T = EnumType.new_(NUMBER_WIDECHAR, elts);
        // Widechar Tipe.
        // This looks OK for new TipeDesc.Op.Widechar.  It's just like Charr.
        Scope.popNew();
        Scope.typeCheck(elts, cs);
        Tipe.define("WIDECHAR", T, true);
    }
}
