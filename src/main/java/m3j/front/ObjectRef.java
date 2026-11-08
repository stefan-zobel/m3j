// Java port of CM3's m3-sys/m3front/src/builtinTypes/ObjectRef.i3 and ObjectRef.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3ID;

// File: ObjectRef.m3
// Last Modified On Fri Jun 24 09:44:26 PDT 1994 By kalsow
//      Modified On Fri May 11 11:49:51 1990 By muller

public final class ObjectRef {
    private ObjectRef() {}

    public static Type.T T;

    public static void initialize() {
        Scope.T s;
        s = Scope.pushNew(false, M3ID.NoID);
        Scope.popNew();
        T = null; // the value is used by ObjectType.New !
        T = ObjectType.new_(null, true, null, s, s);
        Tipe.define("ROOT", T, true);
    }
}
