// Java port of CM3's m3-sys/m3front/src/builtinOps/BuiltinOps.i3 and BuiltinOps.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: BuiltinOps.m3
// Last Modified On Tue Apr 16 01:18:12 1991 By kalsow
//      Modified On Thu Jul 27 17:30:12 1989 By muller

public final class BuiltinOps {
    private BuiltinOps() {}

    public static void initialize() {
        // NOTE: this list is ordered!
        // builtin procedures
        Abs.initialize();
        Adr.initialize();
        AdrSize.initialize();
        BitSize.initialize();
        ByteSize.initialize();
        Ceiling.initialize();
        Dec.initialize();
        Dispose.initialize();
        First.initialize();
        Floatt.initialize();
        Floor.initialize();
        Inc.initialize();
        IsType.initialize();
        Last.initialize();
        Loophole.initialize();
        Max.initialize();
        Min.initialize();
        Narrow.initialize();
        New.initialize();
        Number.initialize();
        Ord.initialize();
        Round.initialize();
        Subarray.initialize();
        Trunc.initialize();
        Typecode.initialize();
        Val.initialize();
    }
}
