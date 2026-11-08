// Java port of CM3's m3-sys/m3front/src/builtinTypes/Reff.i3 and Reff.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: Reff.m3
// Last Modified On Fri Jun 24 09:44:39 PDT 1994 By kalsow
//      Modified On Thu Nov  2 21:29:23 1989 By muller

public final class Reff {
    private Reff() {}

    public static Type.T T;

    public static void initialize() {
        T = RefType.new_(null, true, Brand.new_("$refany$"));
        Tipe.define("REFANY", T, true);
    }
}
