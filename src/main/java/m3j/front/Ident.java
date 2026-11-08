// Java port of CM3's m3-sys/m3front/src/values/Ident.i3 and Ident.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: Ident.i3
// Last Modified On Fri Jun 24 09:36:42 PDT 1994 By kalsow

// File: Ident.m3
// Last Modified On Fri Jun 24 15:19:34 PDT 1994 By kalsow

public final class Ident {
    private Ident() {}

    // Port: StringList = REF ARRAY OF M3ID.T is int[].

    // Port: IntegerList = REF ARRAY OF INTEGER is long[].

    public static int[] stack = null;

    public static long[] offset = null;

    /** points to the first empty stack element */
    public static long top = 0;

    /** returns the number of ids in the list */
    public static long parseList() {
        long start;
        if ((stack == null)) {
            init();
        }
        start = top;
        while (true) {
            if ((stack.length - 1 < top)) {
                expandLists();
            }
            offset[(int) top] = Scanner.cur.offset;
            stack[(int) top] = Scanner.matchID();
            top++;
            if ((Scanner.cur.token != Token.T.tCOMMA)) {
                break;
            }
            Scanner.getToken(); // ,
        }
        return (top - start);
    }

    public static void reset() {
        top = 0;
    }

    private static void init() {
        stack = new int[100];
        offset = new long[100];
    }

    private static void expandLists() {
        int[] newStack = new int[2 * stack.length];
        long[] newOffset = new long[2 * offset.length];
        System.arraycopy(stack, 0, newStack, 0, stack.length);
        System.arraycopy(offset, 0, newOffset, 0, offset.length);
        stack = newStack;
        offset = newOffset;
    }
}
