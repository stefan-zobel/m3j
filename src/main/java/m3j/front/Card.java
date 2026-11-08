// Java port of CM3's m3-sys/m3front/src/builtinTypes/Card.i3 and Card.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;

// File: Card.m3
// Last Modified On Thu Mar 10 09:44:11 PST 1994 By kalsow
//      Modified On Thu Jul 27 17:38:00 1989 By muller

public final class Card {
    private Card() {}

    public static Type.T T;

    public static void initialize() {
        T = SubrangeType.new_(TInt.Zero, M3.target.Integer.max, Int.T, true);
        Tipe.define("CARDINAL", T, true);
    }
}
