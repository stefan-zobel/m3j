// Java port of CM3's m3-sys/m3front/src/builtinTypes/Addr.i3 and Addr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: Addr.m3
// Last Modified On Fri Jun 24 09:42:33 PDT 1994 By kalsow
//      Modified On Fri Feb  9 06:19:03 1990 By muller

public final class Addr {
    private Addr() {}

    public static Type.T T;

    public static void initialize() {
        T = RefType.new_(null, false, Brand.new_("$address$"));
        Tipe.define("ADDRESS", T, true);
    }
}
