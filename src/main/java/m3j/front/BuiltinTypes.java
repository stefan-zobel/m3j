// Java port of CM3's m3-sys/m3front/src/builtinTypes/BuiltinTypes.i3 and BuiltinTypes.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: BuiltinTypes.m3
// Last Modified On Mon Mar  1 17:24:04 PST 1993 By kalsow
//      Modified On Fri Aug  3 01:38:59 1990 By muller

public final class BuiltinTypes {
    private BuiltinTypes() {}

    public static void initialize() {
        // builtin types
        // NOTE: this list is ordered!
        ErrType.initialize();
        Int.initialize();
        LInt.initialize();
        Card.initialize();
        LCard.initialize();
        Bool.initialize();
        Reel.initialize();
        LReel.initialize();
        EReel.initialize();
        Charr.initialize();
        Null.initialize();
        Addr.initialize();
        Reff.initialize();
        ObjectRef.initialize();
        ObjectAdr.initialize();
        Textt.initialize();
        Mutex.initialize();
        WCharr.initialize();
    }
}
