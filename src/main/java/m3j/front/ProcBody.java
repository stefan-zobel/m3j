// Java port of CM3's m3-sys/m3front/src/misc/ProcBody.i3 and ProcBody.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: ProcBody.i3
// Last modified on Thu Jul 14 09:10:35 PDT 1994 by kalsow

// File: ProcBody.m3
// Last modified on Tue Dec 20 14:28:10 PST 1994 by kalsow

/** This interface manages the order that procedure bodies are emitted. */
public final class ProcBody {
    private ProcBody() {}

    /** Port: T_ and the full revelation of T are the class T. */
    public static class T {
        /** set by Push */
        public T parent = null;
        /** set by Push */
        public long level = 0;
        /** set by client */
        public String name = null;
        /** set by client */
        public M3CG.Proc cg_proc = null;

        public T sibling = null;
        public T children = null;

        public void gen_decl() {
            throw new AssertError("ProcBody.gen_decl");
        }

        public void gen_body() {
            throw new AssertError("ProcBody.gen_body");
        }
    }

    private static T cur = null;
    private static T head = null;
    private static T done = null;
    private static long depth = -1;

    /**
     * pushes the procedure that will be written as a child of the current
     * procedure.
     */
    public static void push(ProcBody.T t) {
        AssertError.check((t.parent == null) && (t.sibling == null) && (t.children == null),
                          "ProcBody");
        depth++;
        t.level = depth;
        t.parent = cur;
        if ((cur == null)) {
            // depth = 0
            t.sibling = head;
            head = t;
        } else {
            t.sibling = cur.children;
            cur.children = t;
        }
        cur = t;
    }

    /** pops the current procedure */
    public static void pop() {
        cur = cur.parent;
        depth--;
    }

    /** schedules "t" to be written as a top-level procedure */
    public static void schedule(ProcBody.T t) {
        t.sibling = head;
        head = t;
    }

    /**
     * generate all the procedure bodies and build the global proc table.
     *
     * <p>Port: the VAR parameter proc_info is a one-element array.
     */
    public static void emitAll(long[] proc_info) {
        T t;
        long base = 0;
        long n_base;
        long n, total;
        M3CG.Var consts = Module.globalData(/* is_const := */ true);

        proc_info[0] = -1;

        // generate the declarations and bodies
        while ((head != null)) {
            t = head;
            head = null;         // grab the guys that are waiting
            t = sourceOrder(t);  // put'em in souce order
            emitDecl(t);         // generate their declarations
            emitBody(t);         // generate their bodies & build "done" list
        }

        // count the linker registrations
        t = done;
        n = 0;
        while ((t != null)) {
            if ((t.cg_proc != null) && (t.name != null)) {
                n++;
            }
            t = t.sibling;
        }

        if ((n > 0)) {
            // compute the total lengths of the procedure names
            t = done;
            total = 0;
            while ((t != null)) {
                if ((t.cg_proc != null) && (t.name != null)) {
                    total += t.name.length() + 1;
                }
                t = t.sibling;
            }

            // allocate the space we need for names
            total = total * Target.Char.size;
            n_base = Module.allocate(total, M3.target.Address.align, true, "*proc names*");
            CG.comment(n_base, true, "procedure names");

            // allocate the space we need for proc info headers
            n = n * M3.rt.PI_SIZE + M3.target.Address.size;
            base = Module.allocate(n, M3.target.Address.align, true, "*proc info*");
            CG.comment(base, true, "procedure table");
            proc_info[0] = base;

            // generate the procedure names
            t = done;
            total = 0;
            while ((t != null)) {
                if ((t.cg_proc != null) && (t.name != null)) {
                    CG.init_chars(n_base + total, t.name, /* is_const := */ true);
                    total += Target.Char.size * (t.name.length() + 1);
                }
                t = t.sibling;
            }

            // generate the linker registrations
            t = done;
            total = 0;
            n = proc_info[0];
            while ((t != null)) {
                if ((t.cg_proc != null)) {
                    if ((t.name != null)) {
                        CG.init_proc(n + M3.rt.PI_proc, t.cg_proc, /* is_const := */ true);
                        CG.init_var(n + M3.rt.PI_name, consts, n_base + total, /* is_const := */ true);
                        total += Target.Char.size * (t.name.length() + 1);
                    }
                    n += M3.rt.PI_SIZE;
                }
                t = t.sibling;
            }

        }
    }

    private static T sourceOrder(T t) {
        T a, b, c;
        // reverse the list
        a = t;
        b = null;
        while ((a != null)) {
            c = a.sibling;
            a.sibling = b;
            b = a;
            a = c;
        }
        t = b;

        // recursively reorder the children
        while ((t != null)) {
            t.children = sourceOrder(t.children);
            t = t.sibling;
        }

        return b;
    }

    private static void emitDecl(T t) {
        while ((t != null)) {
            t.gen_decl();
            emitDecl(t.children);
            t = t.sibling;
        }
    }

    private static void emitBody(T t) {
        T a;
        while ((t != null)) {
            if ((Host.nested_procs_first)) {
                emitBody(t.children);
                if ((t.name != null)) {
                    CG.comment(-1, false, t.name);
                }
                t.gen_body();
            } else {
                if ((t.name != null)) {
                    CG.comment(-1, false, t.name);
                }
                t.gen_body();
                emitBody(t.children);
            }

            // move to the next sibling, but leave this guy on the "done" list
            a = t.sibling;
            t.sibling = done;
            done = t;
            t = a;
        }
    }

    public static void reset() {
        cur = null;
        head = null;
        done = null;
        depth = -1;
    }
}
