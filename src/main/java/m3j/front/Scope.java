// Java port of CM3's m3-sys/m3front/src/misc/Scope.i3 and Scope.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.convert.Convert;
import m3j.middle.M3Buf;
import m3j.middle.M3ID;
import m3j.util.AssertError;

// File: Scope.i3
// Last modified on Fri Jun 24 16:51:59 PDT 1994 by kalsow
//      modified on Thu Aug  9 20:08:18 1990 by muller

// File: Scope.m3
// Last modified on Tue Feb 28 17:01:39 PST 1995 by kalsow
//      modified on Sat Feb 16 02:55:11 1991 by muller

public final class Scope {
    private Scope() {}

    /** Port: M3.Scope, with the fields of its revelation in Scope.m3. */
    public static class T extends M3.Node {
        public T next;
        public T parent;
        public int sname;
        public long children = 0;
        public Value.T head = null;
        public Value.T tail = null;
        /** => lookups can see parent */
        public boolean open = false;
        /** => is an outer module scope */
        public boolean module = false;
        /** => is a nested block */
        public boolean nested = false;
        public boolean proc_frame = false;
        public Module.T home = null;
        public long n_elts = 0;
        public long hash_size = 0;
        public Value.T[] hash = null;
    }

    public static Scope.T Initial;

    private static final long MinHashed = 30;

    // explicit reset
    private static T top;
    private static T allScopes;

    // string "constants"
    private static int emptyStr;
    private static int Dot;
    private static int DUnderscore;

    // never reset
    private static M3Buf.T stack_buf = null;
    private static final int[] int_cache = new int[16]; // {M3ID.NoID, ..}

    public static void popNew() {
        top = top.parent;
    }

    public static Scope.T pushNew(boolean open, int name, boolean module, boolean nested) {
        T t = new T();
        t.origin = Scanner.offset;
        t.next = allScopes;
        allScopes = t;
        t.parent = top;
        t.sname = name;
        t.open = open;
        t.module = module;
        t.nested = nested;
        t.proc_frame = (open) && (!module) && (!nested);
        t.home = Module.current();
        top = t;
        scopeName(t); // Force every anonymous block to have a number, even
                      // if not used in a prefix of a procedure name.
                      // without this, m3gdb can't figure out what block
                      // a nested procedure is inside of.
        return t;
    }

    public static Scope.T pushNew(boolean open, int name, boolean module) {
        return pushNew(open, name, module, false);
    }

    public static Scope.T pushNew(boolean open, int name) {
        return pushNew(open, name, false, false);
    }

    public static Scope.T new1(Value.T obj) {
        T t = pushNew(/* open := */ true, /* name := */ M3ID.NoID, /* module := */ false, /* nested := */ true);
        insert(obj);
        return t;
    }

    public static Scope.T push(Scope.T t) {
        T old = top;
        AssertError.check(t != null, "Scope");
        top = t;
        return old;
    }

    public static void pop(Scope.T old) {
        AssertError.check(old != null, "Scope");
        top = old;
    }

    /** return the top "open" scope */
    public static Scope.T top() {
        T t;
        t = top;
        while ((t != null) && (!t.open)) {
            t = t.parent;
        }
        return t;
    }

    public static boolean outerMost(Scope.T t) {
        return (t != null) && (t.module);
    }

    public static Value.T lookUpQID(Scope.T t, M3.QID q) {
        Value.T v = null;
        if ((q.module == M3ID.NoID)) {
            v = lookUp(t, q.item, false);
        } else {
            Value.T b = Value.base(lookUp(t, q.module, false));
            if (b == null) {
                return null;
            } else if (b instanceof Module.T m) {
                return lookUp(Module.exportScope(m), q.item, true);
            } else {
                return null;
            }
        }
        return v;
    }

    public static Value.T lookUp(Scope.T t, int name, boolean strict) {
        Value.T o;
        boolean up_level = false;
        long x, y;
        for (;;) {
            if ((t == null)) {
                return null;
            }
            if ((t.n_elts < MinHashed)) {
                o = t.head;
                while ((o != null) && (o.name != name)) {
                    o = o.next;
                }
            } else {
                if ((t.n_elts != t.hash_size)) {
                    rehash(t);
                }
                y = t.hash.length;
                x = Math.floorMod(M3ID.hash(M3.ids, name), y);
                for (;;) {
                    o = t.hash[(int) x];
                    if ((o == null) || (o.name == name)) {
                        break;
                    }
                    x++;
                    if ((x >= y)) {
                        x = 0;
                    }
                }
            }
            if ((o != null)) {
                break;
            }
            if ((strict) || (!t.open)) {
                return null;
            }
            up_level = up_level || t.proc_frame;
            t = t.parent;
        }
        o.up_level = o.up_level || up_level;

        if ((Scanner.in_main)) {
            // this is a top-level use of the symbol!
            o.used = true;
            if (o.obsolete) {
                Error.warnID(2, name, "<*OBSOLETE*> symbol used");
            } else if (o.unused) {
                Error.warnID(2, name, "<*UNUSED*> symbol used");
            }
        }

        return o;
    }

    private static void rehash(T t) {
        long max = 2 * t.n_elts;
        Value.T[] x = new Value.T[(int) max];
        Value.T v = t.head;
        long hash;
        while ((v != null)) {
            hash = Math.floorMod(M3ID.hash(M3.ids, v.name), max);
            while ((x[(int) hash] != null)) {
                hash++;
                if ((hash >= max)) {
                    hash = 0;
                }
            }
            x[(int) hash] = v;
            v = v.next;
        }
        t.hash_size = t.n_elts;
        t.hash = x;
    }

    /** Insert o into Top(). */
    public static void insert(Value.T o) {
        insertScope(o, top);
    }

    /** Insert o into t. */
    public static void insertScope(Value.T o, Scope.T t) {
        // check for a reserved word
        if ((t != Initial) && (lookUp(Initial, o.name, true) != null)) {
            Error.ID(o.name, "Reserved identifier redefined");
        }

        if ((o.next != null)) {
            Error.ID(o.name, "INTERNAL ERROR: value reinserted in scope");
            AssertError.check(false, "Scope");
        }

        // finally, do the insertion
        if ((o.scope == null)) {
            o.scope = t;
        }
        if ((t.tail != null)) {
            t.tail.next = o;
        } else {
            t.head = o;
        }
        t.tail = o;
        t.n_elts++;
    }

    public static Value.T toList(Scope.T t) {
        if ((t == null)) {
            return null;
        } else {
            return t.head;
        }
    }

    /**
     * note: we separate the type checking of procedures heads and bodies
     * in an attempt to keep the error messages sorted in a rational order
     */
    public static void typeCheck(Scope.T t, M3.CheckState cs) {
        Value.T v;
        if ((t == null)) {
            return;
        }
        checkDuplicates(t);
        v = t.head;
        while ((v != null)) {
            Value.typeCheck(v, cs);
            v = v.next;
        }
        v = t.head;
        while ((v != null)) {
            Value.T b = Value.base(v);
            if (b == null) {
                // ignore
            } else if (b instanceof Procedure.T p) {
                Procedure.checkBody(p, cs);
            } else {
                // ignore
            }
            v = v.next;
        }
    }

    public static void enter(Scope.T t) {
        Value.T v;
        Value.Class c;
        if ((t == null)) {
            return;
        }
        if ((t.nested)) {
            CG.begin_block();
        }
        v = t.head;
        while ((v != null)) {
            c = Value.classOf(v);
            if ((c != Value.Class.Procedure)) {
                Value.declare(v);
            }
            v = v.next;
        }
        v = t.head;
        while ((v != null)) {
            c = Value.classOf(v);
            if ((c == Value.Class.Procedure)) {
                Value.declare(v);
            }
            v = v.next;
        }
        v = t.head;
        while ((v != null)) {
            Value.constInit(v);
            v = v.next;
        }
    }

    public static void initValues(Scope.T t) {
        Value.T v;
        if (t == null) {
            return;
        }
        v = t.head;
        while ((v != null)) {
            Value.langInit(v);
            v = v.next;
        }
        Tracer.emitPending();
        v = t.head;
        while ((v != null)) {
            Value.userInit(v);
            v = v.next;
        }
        Tracer.emitPending();
    }

    public static void exit(Scope.T t) {
        if ((t.nested)) {
            CG.end_block();
        }
    }

    public static void warnUnused(Scope.T t) {
        long save, level;
        Value.Class vc;
        Value.T v;
        if ((t == null)) {
            return;
        }
        save = Scanner.offset;
        v = t.head;
        while ((v != null)) {
            if ((!v.used) && (!v.exportable)) {
                if (!(v.unused || v.obsolete)) {
                    level = 2;
                    vc = Value.classOf(v);
                    if ((vc == Value.Class.Formal)
                        || ((vc == Value.Class.Var) && Variable.isFormal((Variable.T) Value.base(v)))) {
                        level = 1;
                    }
                    Scanner.offset = v.origin;
                    Error.warnID(level, v.name, "not used");
                }
            }
            v = v.next;
        }
        Scanner.offset = save;
    }

    private static void checkDuplicates(T t) {
        Value.T v;
        M3ID.advanceMarks(M3.ids);

        // check each name
        v = t.head;
        while ((v != null)) {
            if (M3ID.setMark(M3.ids, v.name)) {
                reportDuplicate(t, v);
            }
            v = v.next;
        }
    }

    private static void reportDuplicate(T t, Value.T v1) {
        long save = Scanner.offset;
        Value.T v2;
        // verify the duplicate
        v2 = t.head;
        while ((v2 != null) && ((v2.name != v1.name) || (v2 == v1))) {
            v2 = v2.next;
        }
        if ((v2 == null)) {
            return;
        }

        // report the error
        Scanner.offset = v1.origin;
        if ((Value.base(v1) == Value.base(v2))) {
            // same item duplicated => kill 2nd one
            Error.ID(v1.name, "duplicate import");
        } else { // different items with the same name!
            Error.ID(v1.name, "symbol redefined");
        }
        Scanner.offset = save;
    }

    public static int moduleName(Value.T v) {
        v = Value.base(v);
        return scopeName(v.scope);
    }

    public static final class IDStack {
        public long top;
        public int[] stk = new int[100];

        public IDStack() {}
    }

    public static void putStack(M3Buf.T mbuf, Scope.IDStack s) {
        for (long i = 0; i <= s.top - 1; i++) {
            M3ID.put(M3.ids, mbuf, s.stk[(int) i]);
        }
    }

    public static String stackToText(Scope.IDStack s) {
        if ((stack_buf == null)) {
            stack_buf = M3Buf.New();
        }
        for (long i = 0; i <= s.top - 1; i++) {
            M3ID.put(M3.ids, stack_buf, s.stk[(int) i]);
        }
        return M3Buf.toText(stack_buf);
    }

    /** Port: the VAR (OUT) parameter p is the IDStack object itself. */
    public static void nameToPrefix(Value.T v, Scope.IDStack p, boolean considerExternal, boolean dots, boolean with_module) {
        T t;
        long count = 0;
        int dot, scope_name;
        long save;
        v = Value.base(v);
        if ((dots)) {
            dot = Dot;
        } else {
            dot = DUnderscore;
        }

        if (considerExternal && v.external) {
            // simple external name:  foo
            p.stk[(int) p.top] = v.extName;
            p.top++;

        } else if (v.exported || v.imported || (v.scope != null && v.scope.module)) {
            // global names:   foo   module.foo
            scope_name = scopeName(v.scope);
            if ((scope_name == emptyStr) || (!with_module)) {
                p.stk[(int) p.top] = v.name;
                p.top += 1;
            } else {
                p.stk[(int) p.top] = scope_name;
                p.stk[(int) p.top + 1] = dot;
                p.stk[(int) p.top + 2] = v.name;
                p.top += 3;
            }

        } else if (with_module
                   || Value.classOf(v) == Value.Class.Procedure
                   || Value.classOf(v) == Value.Class.Expr) {
            // procedure or constant => fully qualified name:  module.p1.p2.p

            // count how may strings we'll produce
            save = p.top;
            count = p.top;
            t = v.scope;
            for (;;) {
                if (t == null) {
                    break;
                }
                if ((with_module) || (!t.module)) {
                    // Sic: the name of v.scope, not of t, is counted.
                    scope_name = scopeName(v.scope);
                    if (scope_name != emptyStr) {
                        count += 2;
                    }
                }
                if ((!t.open)) {
                    break;
                }
                if ((t.module)) {
                    break;
                }
                t = t.parent;
            }
            count++;

            p.top = count;
            count--;
            p.stk[(int) count] = v.name;
            t = v.scope;
            for (;;) {
                if (t == null) {
                    break;
                }
                if ((with_module) || (!t.module)) {
                    scope_name = scopeName(t);
                    if (scope_name != emptyStr) {
                        count -= 2;
                        p.stk[(int) count + 1] = dot;
                        p.stk[(int) count] = scope_name;
                    }
                }
                if ((!t.open)) {
                    break;
                }
                if ((t.module)) {
                    break;
                }
                t = t.parent;
            }
            AssertError.check(count == save, "Scope");

        } else {
            // variable => simple name:  foo
            p.stk[(int) p.top] = v.name;
            p.top++;
        }
    }

    public static void nameToPrefix(Value.T v, Scope.IDStack p, boolean considerExternal, boolean dots) {
        nameToPrefix(v, p, considerExternal, dots, true);
    }

    public static void nameToPrefix(Value.T v, Scope.IDStack p, boolean considerExternal) {
        nameToPrefix(v, p, considerExternal, false, true);
    }

    public static void nameToPrefix(Value.T v, Scope.IDStack p) {
        nameToPrefix(v, p, true, false, true);
    }

    private static int scopeName(T t) {
        if (t == null) {
            return emptyStr;
        }
        if (t.sname == M3ID.NoID) {
            if ((t.parent == null)) {
                t.sname = emptyStr;
            } else {
                t.parent.children++;
                t.sname = internalName(t.parent.children);
            }
        }
        return t.sname;
    }

    private static int internalName(long n) {
        if ((n < 0) || (int_cache.length - 1 < n)) {
            return newInt(n);
        }
        if (int_cache[(int) n] == M3ID.NoID) {
            int_cache[(int) n] = newInt(n);
        }
        return int_cache[(int) n];
    }

    /** Port: Convert.Failed is FATAL; it becomes an AssertError. */
    private static int newInt(long i) {
        char[] buf = new char[64];
        int len;
        try {
            len = Convert.fromInt(buf, i);
        } catch (Convert.Failed e) {
            throw new AssertError("Scope");
        }
        return M3ID.fromStr(M3.ids, buf, len);
    }

    public static Value.T toUnit(Value.T v) {
        T t, t0;
        v = Value.base(v);

        // look for the containing module scope
        t = v.scope;
        t0 = t;
        while ((t != null) && (t.home == null)) {
            t = t.parent;
        }

        if ((t == null)) {
            return null;
        }

        // mark any intervening scopes with the same "home"
        while ((t0 != t)) {
            t0.home = t.home;
            t0 = t0.parent;
        }

        return t.home;
    }

    public static void initialize() {
        emptyStr = M3ID.add(M3.ids, "");
        Dot = M3ID.add(M3.ids, ".");
        DUnderscore = M3ID.add(M3.ids, "__");

        Initial = pushNew(false, M3ID.add(M3.ids, "M3_BUILTIN") /*emptyStr*/);
    }

    public static void reset() {
        allScopes = null;
        top = Initial;
    }
}
