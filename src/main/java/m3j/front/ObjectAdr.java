// Java port of CM3's m3-sys/m3front/src/builtinTypes/ObjectAdr.i3 and ObjectAdr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3ID;

// File: ObjectAdr.m3
// Last Modified On Fri Jun 24 09:44:06 PDT 1994 By kalsow
//      Modified On Tue Nov 20 20:35:05 1990 By muller

public final class ObjectAdr {
    private ObjectAdr() {}

    public static Type.T T;

    public static void initialize() {
        Scope.T s;
        s = Scope.pushNew(false, M3ID.NoID);
        Scope.popNew();
        T = null; // the value is used by ObjectType.New !
        T = ObjectType.new_(null, false, null, s, s);
        Tipe.define("_UNTRACED_ROOT", T, false);
    }
}
