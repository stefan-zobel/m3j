// Java port of CM3's m3-sys/m3front/src/builtinTypes/Textt.i3 and Textt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: Textt.m3
// Last Modified On Mon Feb 22 16:08:04 PST 1993 By kalsow
//      Modified On Thu Jul 27 17:26:13 1989 By muller

public final class Textt {
    private Textt() {}

    public static Type.T T;

    public static void initialize() {
        T = Tipe.defineOpaque("TEXT", ObjectRef.T);
    }
}
