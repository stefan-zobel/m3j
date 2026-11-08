// Java port of CM3's m3-sys/m3front/src/misc/ESet.i3 and ESet.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.util.AssertError;

// File: ESet.i3
// Last modified on Fri Jun 24 09:20:43 PDT 1994 by kalsow

// File: ESet.m3
// Last modified on Wed Feb  1 14:03:26 PST 1995 by kalsow

public final class ESet {
    private ESet() {}

    /** Port: M3.ExSet, with the fields of its revelation in ESet.m3. */
    public static class T extends M3.Node {
        public Elt elts = null;
        public Scope.T env = null;
        public boolean any = false;
        public boolean resolved = false;
        public boolean used_any = false;
        public long hash = 0;
        public long age = 0;
        public long offset = 0;
    }

    /** Port: M3.ExSetList, revealed in ESet.m3. */
    public static final class TList {
        public T set = null;
        public TList next = null;
    }

    public static final class Elt {
        public long origin;
        public M3.QID name = new M3.QID();
        public Value.T except;
        public Elt next;
        public boolean used;
    }

    private static T newT(boolean any, boolean resolved) {
        T t = new T();
        t.any = any;
        t.resolved = resolved;
        return t;
    }

    private static final T AnySet = newT(true, true);
    private static final T NoneSet = newT(false, true);
    private static final T DefaultSet = NoneSet;

    // Port: HashTable = REF ARRAY OF T is T[].

    private static long thisAge = 1;
    private static T[] hashTbl = null;
    private static long n_hashed = 0;

    public static void reset() {
        thisAge++;
        hashTbl = null;
    }

    public static ESet.T parseRaises() {
        T t;
        Elt elt;
        long here = Scanner.offset;
        Scanner.match(Token.T.tRAISES);
        if (Scanner.cur.token == Token.T.tANY) {
            Scanner.getToken(); // ANY
            t = AnySet;
        } else {
            elt = null;
            Scanner.match(Token.T.tLBRACE);
            while ((Scanner.cur.token == Token.T.tIDENT)) {
                Elt n = new Elt();
                n.next = elt;
                elt = n;
                elt.origin = Scanner.offset;
                elt.name.module = M3ID.NoID;
                elt.name.item = Scanner.matchID();
                elt.except = null;
                if ((Scanner.cur.token == Token.T.tDOT)) {
                    Scanner.getToken(); // .
                    elt.name.module = elt.name.item;
                    elt.name.item = Scanner.matchID();
                }
                if ((Scanner.cur.token != Token.T.tCOMMA)) {
                    break;
                }
                Scanner.getToken(); // ,
            }
            Scanner.match(Token.T.tRBRACE);
            if ((elt == null)) {
                t = NoneSet;
            } else {
                t = new T();
                t.origin = here;
                t.env = Scope.top();
                t.elts = elt;
            }
        }
        return t;
    }

    public static ESet.T parseFails(ESet.T existing) {
        T t = existing;
        Elt elt;
        if ((t == null)) {
            t = new T();
            t.origin = Scanner.offset;
        }
        Scanner.match(Token.T.tFATAL);
        for (;;) {
            if ((Scanner.cur.token == Token.T.tANY)) {
                Scanner.getToken(); // ANY
                t.any = true;
            } else if ((Scanner.cur.token == Token.T.tIDENT)) {
                if ((t.env == null)) {
                    t.env = Scope.top();
                }
                elt = new Elt();
                elt.next = t.elts;
                t.elts = elt;
                elt.origin = Scanner.offset;
                elt.name.module = M3ID.NoID;
                elt.name.item = Scanner.matchID();
                elt.except = null;
                if ((Scanner.cur.token == Token.T.tDOT)) {
                    Scanner.getToken(); // .
                    elt.name.module = elt.name.item;
                    elt.name.item = Scanner.matchID();
                }
            } else {
                break;
            }
            if ((Scanner.cur.token != Token.T.tCOMMA)) {
                break;
            }
            Scanner.getToken(); // ,
        }
        Scanner.match(Token.T.tENDPRAGMA);
        return t;
    }

    public static long hash(ESet.T t) {
        long hash = 691;
        Elt e;
        Value.T o;
        int oname;
        if ((t != null)) {
            if ((t.hash == 0)) {
                AssertError.check(t.resolved, "ESet");
                e = t.elts;
                while ((e != null)) {
                    o = e.except;
                    if ((o != null)) {
                        oname = Value.cName(o);
                        hash = hash * 89 + M3ID.hash(M3.ids, oname);
                    }
                    e = e.next;
                }
                t.hash = hash;
            }
            hash = t.hash;
        }
        return hash;
    }

    private static T lookUp(T t) {
        long hx = hash(t);
        long i;
        T x;
        AssertError.check(t.resolved, "ESet");
        if ((hashTbl == null)) {
            hashTbl = new T[100];
        }
        i = Math.floorMod(hx, (long) hashTbl.length);
        for (;;) {
            x = hashTbl[(int) i];
            if ((x == null)) { // a new entry!
                hashTbl[(int) i] = t;
                n_hashed++;
                if ((2 * n_hashed > hashTbl.length)) {
                    expandHashTable();
                }
                return t;
            }
            if ((x == t) || isEqual(x, t)) {
                return x;
            }
            i++;
            if ((i > hashTbl.length - 1)) {
                i = 0;
            }
        }
    }

    private static void expandHashTable() {
        T[] old = hashTbl;
        T t;
        hashTbl = new T[2 * old.length];
        n_hashed = 0;
        for (int i = 0; i <= old.length - 1; i++) {
            t = old[i];
            if ((t != null)) {
                lookUp(t);
            }
        }
    }

    public static void declare(ESet.T t) {
        Elt e;
        long n;
        if ((t == null) || (t.any) || (t.elts == null)) {
            return;
        }
        t = lookUp(t);
        if ((t.age >= thisAge)) {
            return;
        }

        // declare each of the exceptions and size the list
        e = t.elts;
        n = 1;
        while ((e != null)) {
            if ((e.except != null)) {
                Value.declare(e.except);
                n++;
            }
            e = e.next;
        }

        // allocate the space
        t.offset = Module.allocate(n * M3.target.Integer.pack, M3.target.Integer.align,
                                   true, "*exception list*");

        // initialize the list
        e = t.elts;
        n = t.offset;
        while ((e != null)) {
            if ((e.except != null)) {
                CG.init_intt(n, M3.target.Integer.size, Exceptionz.UID(e.except), true);
                n += M3.target.Integer.pack;
            }
            e = e.next;
        }

        t.age = thisAge;
    }

    /** Port: the VAR parameters base and offset are one-element arrays. */
    public static void getAddress(ESet.T t, M3CG.Var[] base, long[] offset) {
        if ((t == null)) {
            base[0] = null;
            offset[0] = 0;
        } else if ((t.any)) {
            Error.msg("INTERNAL ERROR: need address of RAISES ANY list");
            base[0] = null;
            offset[0] = 0;
        } else if ((t.elts == null)) {
            base[0] = null;
            offset[0] = 0;
        } else {
            t = lookUp(t);
            declare(t);
            base[0] = Module.globalData(/* is_const := */ true);
            offset[0] = t.offset;
        }
    }

    public static boolean raisesAny(ESet.T t) {
        return (t != null) && (t.any);
    }

    public static boolean raisesNone(ESet.T t) {
        return (t == null) || ((!t.any) && (t.elts == null));
    }

    public static ESet.T newAny() {
        T t = new T();
        t.any = true;
        t.origin = Scanner.offset;
        return t;
    }

    public static ESet.T newEmpty(Scope.T env) {
        T t = new T();
        t.env = env;
        t.origin = Scanner.offset;
        return t;
    }

    public static void add(ESet.T t, M3.QID name, Value.T ex) {
        Elt e;
        ex = Value.base(ex);

        // check for a duplicate
        e = t.elts;
        while ((e != null)) {
            if ((e.except == ex) && (ex != null)) {
                Error.QID(name, "repeated exception in handler list");
                return;
            }
            e = e.next;
        }

        e = new Elt();
        e.origin = Scanner.offset;
        e.next = t.elts;
        e.name = name.copy();
        e.except = ex;
        t.elts = e;
    }

    /** TRUE iff 'a' equals 'b' */
    public static boolean isEqual(ESet.T a, ESet.T b) {
        if ((a == null)) {
            a = DefaultSet;
        }
        if ((b == null)) {
            b = DefaultSet;
        }
        if ((a.any != b.any)) {
            return false;
        }
        resolve(a);
        resolve(b);
        return eltSubset(a.elts, b.elts) && eltSubset(b.elts, a.elts);
    }

    /** TRUE iff a is a subset of b */
    public static boolean isSubset(ESet.T a, ESet.T b) {
        if ((a == null)) {
            a = DefaultSet;
        }
        if ((b == null)) {
            b = DefaultSet;
        }
        if ((b.any)) {
            return true;
        }
        if ((a.any)) {
            return false;
        }
        resolve(a);
        resolve(b);
        return eltSubset(a.elts, b.elts);
    }

    // type checking

    public static void typeCheck(ESet.T t) {
        if ((t != null)) {
            resolve(t);
        }
    }

    public static void push(M3.CheckState cs, ESet.T ok_to_raise, ESet.T no_error, boolean stop) {
        TList x;
        if ((stop)) {
            // this is a nested procedure => truncate the "ok_to_raise" list
            x = new TList();
            x.set = null;
            x.next = cs.ok_to_raise;
            cs.ok_to_raise = x;
        }

        if ((ok_to_raise != null)) {
            resetUsed(ok_to_raise);
            x = new TList();
            x.set = ok_to_raise;
            x.next = cs.ok_to_raise;
            cs.ok_to_raise = x;
        }
        if ((no_error != null)) {
            resetUsed(no_error);
            x = new TList();
            x.set = no_error;
            x.next = cs.no_error;
            cs.no_error = x;
        }
    }

    private static void resetUsed(T t) {
        Elt e = t.elts;
        t.used_any = false;
        while ((e != null)) {
            e.used = false;
            e = e.next;
        }
    }

    public static void pop(M3.CheckState cs, ESet.T ok_to_raise, ESet.T no_error, boolean stop) {
        TList u;
        if ((ok_to_raise != null)) {
            AssertError.check(ok_to_raise == cs.ok_to_raise.set, "ESet");
            checkUnused(ok_to_raise);
            cs.ok_to_raise = cs.ok_to_raise.next;
        }
        if ((no_error != null)) {
            AssertError.check(no_error == cs.no_error.set, "ESet");
            checkUnused(no_error);
            cs.no_error = cs.no_error.next;
        }
        if ((stop)) {
            u = cs.ok_to_raise;
            cs.ok_to_raise = u.next;
            AssertError.check(u.set == null, "ESet");
        }
    }

    private static void checkUnused(T t) {
        long save = Scanner.offset;
        Elt e = t.elts;
        while ((e != null)) {
            if ((e.except != null) && (!e.used)
                && (!Exceptionz.isImplicit(e.except))) {
                Scanner.offset = e.origin;
                Error.warn(1, "exception is never raised: "
                    + Value.globalName(e.except));
            }
            e = e.next;
        }
        if ((t.any) && (!t.used_any)) {
            if ((t.origin == 0)) {
                Scanner.offset = save;
            } else {
                Scanner.offset = t.origin;
            }
            Error.warn(1, "exception is never raised: <ANY>");
        }
        Scanner.offset = save;
    }

    private static void markEverythingUsed(TList u, boolean handler) {
        T t;
        Elt e;
        while ((u != null)) {
            t = u.set;
            if ((t == null)) {
                break;
            }
            if ((t.any)) {
                t.used_any = true;
                if ((handler)) {
                    break;
                }
            }
            e = t.elts;
            while ((e != null)) {
                e.used = true;
                e = e.next;
            }
            u = u.next;
        }
    }

    public static void noteExceptions(M3.CheckState cs, ESet.T t) {
        TList u;
        Elt e;
        long n_bad;
        String name;
        String bad = null;
        if ((t == null)) {
            return;
        }

        if ((t.any)) {
            markEverythingUsed(cs.ok_to_raise, true);
            markEverythingUsed(cs.no_error, false);
            u = cs.ok_to_raise;
            while ((u != null) && (u.set != null)) {
                if ((u.set.any)) {
                    u.set.used_any = true;
                    return;
                }
                u = u.next;
            }
            cs.raises_others = true;
            u = cs.no_error;
            while ((u != null)) {
                if ((u.set.any)) {
                    u.set.used_any = true;
                    return;
                }
                u = u.next;
            }
            Error.warn(1, "potentially unhandled exception: <ANY>");
            return;
        }

        e = t.elts;
        n_bad = 0;
        while ((e != null)) {
            name = checkRaise(cs, e.except);
            if ((name != null)) {
                if ((n_bad == 0)) {
                    bad = name;
                } else {
                    bad = bad + ", " + name;
                }
                n_bad++;
            }
            e = e.next;
        }

        if ((n_bad > 0)) {
            if ((n_bad == 1)) {
                Error.warn(1, "potentially unhandled exception: " + bad);
            } else {
                Error.warn(1, "potentially unhandled exceptions: " + bad);
            }
        }

    }

    public static void noteException(M3.CheckState cs, Value.T v) {
        String name = checkRaise(cs, v);
        if ((name != null)) {
            Error.warn(1, "potentially unhandled exception: " + name);
        }
    }

    /**
     * Returns NIL if it's ok to raise the exception 'v', otherwise
     * returns the fully qualified name of the exception.
     */
    private static String checkRaise(M3.CheckState cs, Value.T v) {
        if ((v == null)) { // there's already been an error
            return null;
        }
        v = Value.base(v);
        if (checkTList(cs.ok_to_raise, v)) {
            return null;
        }
        cs.raises_others = true;
        if (checkTList(cs.no_error, v)) {
            return null;
        }
        if (Exceptionz.isImplicit(v)) {
            return null;
        }
        return Value.globalName(v);
    }

    /** Return TRUE if exception 'v' occurs in the list of sets 'u'. */
    private static boolean checkTList(TList u, Value.T v) {
        Elt e;
        T t;
        while ((u != null) && (u.set != null)) {
            t = u.set;
            e = t.elts;
            while ((e != null)) {
                if (exceptionEQ(e.except, v)) {
                    e.used = true;
                    return true;
                }
                e = e.next;
            }
            if ((t.any)) {
                t.used_any = true;
                return true;
            }
            u = u.next;
        }
        return false;
    }

    //------------------------------------------------------------- debugging ---

    public static long emitTypes(ESet.T t) {
        Elt e;
        long n;
        if ((t == null)) {
            return 0;
        }
        t = lookUp(t);
        if ((t.any)) {
            return -1;
        }

        e = t.elts;
        n = 0;
        while ((e != null)) {
            if ((e.except != null)) {
                Value.declare(e.except);
                n++;
            }
            e = e.next;
        }

        return n;
    }

    public static void emitNames(ESet.T t) {
        Elt e;
        if ((t == null)) {
            return;
        }
        t = lookUp(t);
        if ((t.any)) {
            return;
        }
        e = t.elts;
        while ((e != null)) {
            if ((e.except != null)) {
                CG.declare_raises(M3ID.add(M3.ids, Value.globalName(e.except)));
            }
            e = e.next;
        }
    }

    public static final class EList {
        public Value.T e;
        public EList next;
    }

    public static ESet.EList listE(ESet.T t) {
        Elt e;
        EList x, ret;
        if ((t == null)) {
            return null;
        }
        t = lookUp(t);
        if ((t.any)) {
            return null;
        }

        e = t.elts;
        ret = null;
        while ((e != null)) {
            if ((e.except != null)) {
                x = new EList();
                x.e = e.except;
                x.next = ret;
                ret = x;
            }
            e = e.next;
        }
        return ret;
    }

    //---------------------------------------------------------- fingerprints ---

    /** Port: the VAR parameter x is the FPInfo object itself. */
    public static long addFPTag(ESet.T t, M3.FPInfo x) {
        Elt e;
        long n;
        if ((t == null)) {
            return 0;
        }
        t = lookUp(t);
        if ((t.any)) {
            M3Buf.putText(x.buf, "RAISES-ANY");
            return 0;
        } else if ((t.elts == null)) {
            return 0;
        } else {
            AssertError.check(t.resolved, "ESet");
            M3Buf.putText(x.buf, "RAISES{");
            e = t.elts;
            n = 0;
            while ((e != null)) {
                n += Exceptionz.addFPSetTag(e.except, x);
                e = e.next;
            }
            M3Buf.putChar(x.buf, '}');
            return n;
        }
    }

    /** Port: the VAR parameter x is the FPInfo object itself. */
    public static long addFPEdges(ESet.T t, M3.FPInfo x, long n) {
        Elt e;
        if ((t == null)) {
            t = DefaultSet;
        }
        t = lookUp(t);
        if ((!t.any)) {
            e = t.elts;
            while ((e != null)) {
                n = Value.addFPEdges(e.except, x, n);
                e = e.next;
            }
        }
        return n;
    }

    //-------------------------------------------------------------- internal ---

    /** look up each of the named exceptions */
    private static void resolve(T t) {
        Elt e;
        Value.T o;
        long save;
        if ((t.resolved)) {
            return;
        }
        save = Scanner.offset;
        Scanner.offset = t.origin;
        e = t.elts;
        while ((e != null)) {
            if ((e.except == null)) {
                o = Scope.lookUpQID(t.env, e.name);
            } else {
                o = e.except;
            }
            if ((o == null)) {
                Error.QID(e.name, "undefined");
            } else if ((Value.classOf(o) != Value.Class.Exception)) {
                Error.QID(e.name, "not an exception");
            } else {
                e.except = Value.base(o);
            }
            e = e.next;
        }
        Scanner.offset = save;
        t.elts = sortElts(t.elts);
        t.resolved = true;
    }

    //************ assume the lists are sorted **************************
    private static boolean eltSubset(Elt a, Elt b) {
        while ((a != null)) {
            for (;;) {
                if ((b == null)) {
                    return false;
                }
                if (exceptionEQ(b.except, a.except)) {
                    break;
                }
                b = b.next;
            }
            // this element of a is in b
            a = a.next;
        }
        return true;
    }

    //**** assumes that the lists are not sorted *************************
    // PROCEDURE EltSubset (a, b: Elt): BOOLEAN =
    //   VAR ar, br: Elt;
    //   BEGIN
    //     ar := a;
    //     WHILE (ar # NIL) DO
    //       br := b;
    //       LOOP
    //         IF (br = NIL) THEN RETURN FALSE END;
    //         IF ExceptionEQ (br.except, ar.except) THEN EXIT END;
    //         br := br.next;
    //       END;
    //       (* this element of a is in b *)
    //       ar := ar.next;
    //     END;
    //     RETURN TRUE;
    //   END EltSubset;
    //********************************************************************

    private static Elt sortElts(Elt e) {
        int i;
        Elt x;
        Elt[] tmp = new Elt[10];
        if ((e == null)) {
            return null;
        }
        if ((e.next == null)) {
            return e;
        }

        // unpack the list
        i = 0;
        x = e;
        while ((x != null)) {
            if ((i <= tmp.length - 1)) {
                tmp[i] = x;
            }
            x = x.next;
            i++;
        }

        if ((i <= tmp.length)) {
            return doSort(tmp, i);
        } else {
            Elt[] ref = new Elt[i];
            i = 0;
            x = e;
            while ((x != null)) {
                ref[i] = x;
                x = x.next;
                i++;
            }
            return doSort(ref, i);
        }
    }

    private static Elt doSort(Elt[] e, int n) {
        Elt x;
        int j;
        // insertion sort the list
        for (int i = 1; i <= n - 1; i++) {
            x = e[i];
            j = i - 1;
            while ((j >= 0) && eltLT(x, e[j])) {
                e[j + 1] = e[j];
                j--;
            }
            e[j + 1] = x;
        }

        // build the new linked list
        for (int i = 0; i <= n - 2; i++) {
            e[i].next = e[i + 1];
        }
        e[n - 1].next = null;

        return e[0];
    }

    private static boolean eltLT(Elt a, Elt b) {
        Scope.IDStack aName = new Scope.IDStack(), bName = new Scope.IDStack();
        if ((a == b)) {
            return false;
        }
        if ((a == null)) {
            return true;
        }
        if ((b == null)) {
            return false;
        }
        if ((a.except == b.except)) {
            return false;
        }
        if ((a.except == null)) {
            return true;
        }
        if ((b.except == null)) {
            return false;
        }
        aName.top = 0;
        bName.top = 0;
        Scope.nameToPrefix(a.except, aName);
        Scope.nameToPrefix(b.except, bName);
        for (long i = 0; i <= Math.min(aName.top, bName.top) - 1; i++) {
            int ax = aName.stk[(int) i], bx = bName.stk[(int) i];
            if ((ax != bx)) {
                return M3ID.isLT(M3.ids, ax, bx);
            }
        }
        return (aName.top < bName.top);
    }

    private static boolean exceptionEQ(Value.T a, Value.T b) {
        Scope.IDStack aName = new Scope.IDStack(), bName = new Scope.IDStack();
        if ((a == b)) {
            return true;
        }
        if ((a == null) || (b == null)) {
            return false;
        }

        aName.top = 0;
        bName.top = 0;
        Scope.nameToPrefix(a, aName);
        Scope.nameToPrefix(b, bName);
        if ((aName.top != bName.top)) {
            return false;
        }
        for (long i = 0; i <= aName.top - 1; i++) {
            if ((aName.stk[(int) i] != bName.stk[(int) i])) {
                return false;
            }
        }

        return true;
    }
}
