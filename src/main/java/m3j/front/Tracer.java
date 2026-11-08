// Java port of CM3's m3-sys/m3front/src/misc/Tracer.i3 and Tracer.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.util.AssertError;

// File: Tracer.i3
// Last Modified On Tue Jun 28 09:56:36 PDT 1994 by kalsow
//      Modified On Tue Jan 19 10:54:35 PST 1993 by mjordan

// File: Tracer.m3
// Last Modified On Tue Jun 28 09:56:03 PDT 1994 by kalsow

public final class Tracer {
    private Tracer() {}

    public static class T {
        public T next;

        public void apply() {
            throw new AssertError("Tracer.apply");
        }
    }

    // IMPORT Error;

    private static boolean emitting = false;
    private static T pending = null;
    private static T stack = null;

    /** schedule 't' to be called during the next 'EmitPending' */
    public static void schedule(Tracer.T t) {
        if ((t == null)) {
            return;
        }

        if ((emitting)) {
            //** silently ignore the new event  ***
            // Error.Msg ("nested <*TRACE*> pragmas are not allowed");
            // **
            return;
        }

        if ((t.next == null)) {
            t.next = pending;
            pending = t;
        }
    }

    /** delimits the region of code that's to be traced by 't'. */
    public static void push(Tracer.T t) {
        if ((t == null)) {
            return;
        }

        if ((emitting)) {
            //** silently ignore the new event  ***
            // Error.Msg ("nested <*TRACE*> pragmas are not allowed");
            // **
            return;
        }

        if ((t.next == null)) {
            t.next = stack;
            stack = t;
        }
    }

    /** delimits the region of code that's to be traced by 't'. */
    public static void pop(Tracer.T tt) {
        T t = stack;
        if ((tt == null)) {
            return;
        }
        if ((emitting)) {
            return;
        }
        if ((t == tt)) {
            stack = t.next;
            t.next = null;
        }
    }

    /** generate all pending trace calls */
    public static void emitPending() {
        T t;
        if ((emitting)) {
            return;
        }
        emitting = true;

        // generate the one-shot traces
        while ((pending != null)) {
            t = pending;
            pending = t.next;
            t.next = null;
            t.apply();
        }

        // generate the persistent traces
        t = stack;
        while ((t != null)) {
            t.apply();
            t = t.next;
        }

        emitting = false;
    }

    public static void reset() {
        emitting = false;
        pending = null;
        stack = null;
    }
}
