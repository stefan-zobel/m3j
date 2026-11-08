// Java port of CM3's m3-sys/m3front/src/values/Revelation.i3 and
// Revelation.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3ID;
import m3j.util.AssertError;

// File: Revelation.def
// Last modified on Tue Dec 20 15:09:01 PST 1994 by kalsow
//      modified on Fri Feb 23 03:41:40 1990 by muller

// File: Revelation.m3
// Last modified on Tue Dec 20 15:23:30 PST 1994 by kalsow
//      modified on Sat Aug 25 02:55:44 1990 by muller

public final class Revelation {
    private Revelation() {}

    public static final class TypeList {
        public TypeList next;
        public Type.T type;
    }

    public static final class TypeSet {
        public long cnt;
        public Type.T[] types = new Type.T[10];
        public TypeList others;

        public TypeSet() {}
    }

    public static final class T {
        /** the containing interface or module */
        public Value.T home;
        public Scope.T env;
        public M3.QID qid = new M3.QID();
        /** value named by 'qid' in scope 'env' */
        public Value.T obj;
        /** REVEAL qid (&lt;:|=) rhs */
        public Type.T rhs;
        /** == type that corresponds to qid */
        public Type.T lhs;
        /** TRUE =&gt; lhs = rhs, FALSE =&gt; lhs &lt;: rhs */
        public boolean equal;
        public boolean checked;
        public long origin;
    }

    public static final class List {
        public List next;
        public T ident;
        /** as opposed to inherited */
        public boolean local;
        public boolean used;
        /** External.T that caused the import */
        public Value.T home;
    }

    public static final class Node {
        public Node next;
        public Type.T key;
        public List best = null;
        public List contents = null;
        public boolean reducing = false;
    }

    // Port: HashTable = REF ARRAY OF Node is Node[].

    /** Port: Revelation.Set with the fields of its revelation. */
    public static class Set {
        public Value.T home = null;
        public long count = 0;
        public List idents = null;
        public Node[] hash = null;
        public PersistentRevelation.Seq seq = null;
        // The visible revelations are in the union of idents and hash
        // seq is a temporary, placed here to reduce heap allocations.
    }

    public static final class Iterator {
        public List cur;
        public Set set;
        public List next_list;
        public Node next_node;
        public long next_hash;
        public long max_hash;

        Iterator() {}
    }

    private static Set top = newSet(null);

    public static Set newSet(Value.T module) {
        Set s = new Set();
        s.home = module;
        s.seq = new PersistentRevelation.Seq().init();
        return s;
    }

    public static Set push(Set s) {
        Set old = top;
        AssertError.check(s != null, "Revelation");
        top = s;
        return old;
    }

    public static void pop(Set s) {
        top = s;
    }

    public static void parse(Decl.Attributes att) {
        int id, id2;
        long loc;
        Scanner.match(Token.T.tREVEAL);
        while ((Scanner.cur.token == Token.T.tIDENT)) {
            id2 = M3ID.NoID;
            id = Scanner.matchID();
            if ((Scanner.cur.token == Token.T.tDOT)) {
                Scanner.getToken(); // .
                id2 = id;
                id = Scanner.matchID();
            }
            loc = Scanner.offset;
            switch (Scanner.cur.token) {
                case tEQUAL -> {
                    Scanner.getToken(); // =
                    new_(id2, id, Type.parse(), true, loc);
                }
                case tSUBTYPE -> {
                    Scanner.getToken(); // <:
                    new_(id2, id, Type.parse(), false, loc);
                }
                default -> Scanner.fail("missing \'=\' or \'<:\'");
            }
            Scanner.match(Token.T.tSEMI);
        }
    }

    private static void new_(int module, int name, Type.T rhs, boolean eq, long loc) {
        T t;
        AssertError.check(top.home != null, "Revelation");
        t = new T();
        t.home = top.home;
        t.qid.module = module;
        t.qid.item = name;
        t.obj = null;
        t.rhs = rhs;
        t.lhs = null;
        t.equal = eq;
        t.checked = false;
        t.origin = loc;
        t.env = Scope.top();
        addOne(t, true, null);
    }

    public static void inherit(Set s, Value.T import_) {
        Iterator iter = new Iterator();
        initIterator(s, iter);
        while (iterate(iter)) {
            if ((iter.cur.local)) {
                addOne(iter.cur.ident, false, import_);
            }
        }
    }

    private static void addOne(T t, boolean isLocal, Value.T import_) {
        List y = new List();
        y.ident = t;
        y.local = isLocal;
        y.used = false;
        y.home = import_;
        y.next = top.idents;
        top.idents = y;
        top.count++;
    }

    public static void typeCheck(Set s) {
        List l;
        long save, n_buckets;
        Node n;
        if ((s.count <= 0)) {
            return;
        }

        save = Scanner.offset;

        // allocate and initialize the hash table
        AssertError.check(s.hash == null, "Revelation"); // otherwise we've been checked twice!?
        n_buckets = 2 * s.count;
        s.hash = new Node[(int) n_buckets];
        for (long i = 0; i <= n_buckets - 1; i++) {
            s.hash[(int) i] = null;
        }

        // bind the lhs qid's to types & map them into the hash table
        while ((s.idents != null)) {
            l = s.idents;
            s.idents = l.next;
            if (doBind(l.ident)) {
                hashInsert(s, l);
            }
        }

        // type check the lhs and rhs
        for (long i = 0; i <= n_buckets - 1; i++) {
            n = s.hash[(int) i];
            while ((n != null)) {
                l = n.contents;
                while ((l != null)) {
                    doCheck0(l.ident);
                    l = l.next;
                }
                n = n.next;
            }
        }

        // type check the lhs and rhs
        for (long i = 0; i <= n_buckets - 1; i++) {
            n = s.hash[(int) i];
            while ((n != null)) {
                l = n.contents;
                while ((l != null)) {
                    doCheck(l.ident);
                    l = l.next;
                }
                n = n.next;
            }
        }

        // find the strongest revelation for each type
        for (long i = 0; i <= n_buckets - 1; i++) {
            n = s.hash[(int) i];
            while ((n != null)) {
                reduce(s, n.key);
                n = n.next;
            }
        }

        Scanner.offset = save;
    }

    /** Port: returns Success. */
    private static boolean doBind(T t) {
        Value.T obj;
        if ((t.checked)) {
            return true;
        }
        Scanner.offset = t.origin;
        obj = Scope.lookUpQID(t.env, t.qid);
        t.obj = obj;
        if ((obj == null)) {
            Error.QID(t.qid, "undefined");
            return false;
        } else if ((Value.classOf(obj) != Value.Class.Type)) {
            Error.QID(t.qid, "is not a type");
            return false;
        } else {
            t.lhs = Value.toType(obj);
        }
        t.lhs = Type.strip(t.lhs);
        return true;
    }

    private static void hashInsert(Set s, List l) {
        Type.T lhs = l.ident.lhs;
        int hsh = (int) Math.floorMod(OpaqueType.UID(lhs), (long) s.hash.length);
        Node n = s.hash[hsh];
        List x;
        // look for the node that contains l's revelations
        while (true) {
            if ((n == null)) {
                // we didn't find a node for this type
                n = new Node();
                n.next = s.hash[hsh];
                n.key = lhs;
                s.hash[hsh] = n;
                break;
            }
            if (Type.isEqual(n.key, lhs, null)) {
                break;
            }
            n = n.next;
        }

        // check for a duplicate revelation (possible because both "IMPORT X"
        // and "FROM X IMPORT" are allowed in a single unit.  They cause X's
        // revelations to be inherited twice.  sigh.
        x = n.contents;
        while ((x != null)) {
            if ((x.ident == l.ident)) {
                // drop 'l' on the floor
                return;
            }
            x = x.next;
        }

        // add 'l' to the list
        l.next = n.contents;
        n.contents = l;
    }

    private static void doCheck0(T t) {
        Scanner.offset = t.origin;
        Type.check(t.rhs);
        // * t.rhs := Type.Check (t.rhs);  -- we don't want to save the
        //   checked RHS, otherwise if the RHS is a named type we'll miss
        //   the check below which requires a full revelation to have a
        //   branded constructor. *
        t.lhs = Type.check(t.lhs);
    }

    private static void doCheck(T t) {
        Type.T xx;
        String name;
        if ((t.checked)) {
            return;
        }
        Scanner.offset = t.origin;

        if ((!OpaqueType.is(t.lhs))) {
            Error.QID(t.qid, "is not an opaque type");
        } else if (!Type.isSubtype(t.rhs, OpaqueType.super_(t.lhs))) {
            Error.QID(t.qid, "identification is not to a legal subtype");
        }

        if ((t.equal)) {
            xx = Type.strip(t.rhs);
            if ((xx != t.rhs)
                || xx == Reff.T
                   // Reff.T is REFANY, and it is initialized with a brand field.
                || !(RefType.isBranded(xx) || ObjectType.isBranded(xx))) {
                Error.QID(t.qid, "right-hand side must be a branded type expression");
                t.rhs = xx;
            }

            name = Value.globalName(t.obj);
            if (RefType.is(t.rhs)) {
                RefType.noteRefName(t.rhs, name);
            } else {
                ObjectType.noteRefName(t.rhs, name);
            }
        }

        t.checked = true;
    }

    private static void reduce(Set s, Type.T key) {
        long x;
        Node n;
        List best, l;
        if ((key == null)) {
            return;
        }
        key = Type.strip(key);

        x = OpaqueType.UID(key);
        if ((x == 0)) {
            // it's not an opaque type
            reduce(s, ObjectType.super_(key));
            return;
        }
        reduce(s, OpaqueType.super_(key));

        // find the hash table node
        x = Math.floorMod(x, (long) s.hash.length);
        n = s.hash[(int) x];
        while (true) {
            if ((n == null)) {
                return;
            }
            if (Type.isEqual(n.key, key, null)) {
                break;
            }
            n = n.next;
        }

        if ((n.reducing)) {
            // recursive call
            return;
        }
        if ((n.best != null)) {
            // done
            return;
        }
        n.reducing = true;

        // first, reduce the rhs's
        l = n.contents;
        while ((l != null)) {
            reduce(s, l.ident.rhs);
            l = l.next;
        }

        // finally, search for the best candidate
        best = n.contents;
        l = best.next;
        while ((l != null)) {

            best.ident.rhs = Type.check(best.ident.rhs);
            l.ident.rhs = Type.check(l.ident.rhs);
            // We need these checks since Reduce can be called by
            // LookUpAll during an active call to  TypeCheck.

            if (Type.isSubtype(best.ident.rhs, l.ident.rhs)) {
                // best is better than l
                if ((l.ident.equal)) {
                    tooStrong(l, best);
                }
            } else if (Type.isSubtype(l.ident.rhs, best.ident.rhs)) {
                // l is better than best
                if ((best.ident.equal)) {
                    tooStrong(best, l);
                }
                best = l;
            } else { // unrelated revelations!
                Scanner.offset = best.ident.origin;
                Error.QID(best.ident.qid, "non-comparable revelation");
                Scanner.offset = l.ident.origin;
                Error.QID(l.ident.qid, "non-comparable revelation");
            }
            l = l.next;
        }

        n.best = best;
    }

    /** a.ident.equal */
    private static void tooStrong(List xa, List xb) {
        T a = xa.ident;
        T b = xb.ident;
        AssertError.check(a.equal, "Revelation");
        if ((b.equal)) {
            Scanner.offset = a.origin;
            Error.QID(a.qid, "multiple full revelations");
            Scanner.offset = b.origin;
            Error.QID(b.qid, "multiple full revelations");
        } else {
            Scanner.offset = b.origin;
            Error.QID(b.qid, "partial revelation is stronger than full revelation");
        }
    }

    public static Type.T lookUp(Type.T key) {
        long h;
        Type.T x;
        key = Type.strip(key);
        if ((top.hash != null)) {
            h = Math.floorMod(OpaqueType.UID(key), (long) top.hash.length);
            x = searchEQ(top.hash[(int) h], key);
            if ((x != null)) {
                return x;
            }
        }
        return searchListEQ(top.idents, key);
    }

    private static Type.T searchEQ(Node n, Type.T key) {
        List l;
        T t;
        // look for the chain header
        while (true) {
            if ((n == null)) {
                return null;
            }
            if (Type.isEqual(n.key, key, null)) {
                break;
            }
            n = n.next;
        }

        // has it already been reduced?
        l = n.best;
        if ((l != null)) {
            t = l.ident;
            if (!t.equal) {
                return null;
            }
            if ((!t.checked)) {
                checkRHS(t);
            }
            noteUse(l);
            return t.rhs;
        }

        // no, then search the full list for a match
        return searchListEQ(n.contents, key);
    }

    private static Type.T searchListEQ(List l, Type.T key) {
        T t;
        while ((l != null)) {
            t = l.ident;
            AssertError.check(t.lhs != null || t.rhs == null, "Revelation"); // => LHS is bound
            if ((t.equal) && Type.isEqual(t.lhs, key, null)) {
                if ((!t.checked)) {
                    checkRHS(t);
                }
                noteUse(l);
                return t.rhs;
            }
            l = l.next;
        }
        return null; // didn't find a full revelation
    }

    /** Port: x is VAR (OUT). */
    public static void lookUpAll(Type.T key, Revelation.TypeSet x) {
        long h;
        x.cnt = 0;
        x.others = null;
        key = Type.strip(key);
        if ((top.idents != null)) {
            searchListAll(top.idents, key, x);
        }
        if ((top.hash != null)) {
            reduce(top, key);
            h = Math.floorMod(OpaqueType.UID(key), (long) top.hash.length);
            searchAll(top.hash[(int) h], key, x);
        }
    }

    private static void searchAll(Node n, Type.T key, TypeSet x) {
        T t;
        // search the list for a matching node
        while (true) {
            if ((n == null)) {
                return;
            }
            if (Type.isEqual(n.key, key, null)) {
                break;
            }
            n = n.next;
        }

        if ((n.best != null)) {
            noteUse(n.best);
            t = n.best.ident;
            AssertError.check(Type.isEqual(t.lhs, key, null), "Revelation");
            if ((!t.checked)) {
                checkRHS(t);
            }
            addType(x, t.rhs);
        } else {
            // we haven't reduced this node yet => return all possible nodes
            searchListAll(n.contents, key, x);
        }
    }

    private static void searchListAll(List l, Type.T key, TypeSet x) {
        T t;
        while ((l != null)) {
            t = l.ident;
            AssertError.check(t.lhs != null || t.rhs == null, "Revelation"); // => LHS is bound
            if (Type.isEqual(t.lhs, key, null)) {
                noteUse(l);
                if ((!t.checked)) {
                    checkRHS(t);
                }
                addType(x, t.rhs);
            }
            l = l.next;
        }
    }

    private static void addType(TypeSet x, Type.T t) {
        if ((x.cnt < x.types.length)) {
            x.types[(int) x.cnt] = t;
            x.cnt++;
        } else {
            TypeList r = new TypeList();
            r.next = x.others;
            r.type = t;
            x.others = r;
        }
    }

    private static void noteUse(List l) {
        if ((Scanner.in_main)) {
            l.used = true;
            if ((l.home != null)) {
                l.home.used = true;
            }
        }
    }

    /** we're doing a lookup while the revelations are being checked... */
    private static void checkRHS(T t) {
        long save = Scanner.offset;
        t.rhs = Type.check(t.rhs);
        Scanner.offset = save;
    }

    public static void reuse(Set s) {
        Iterator iter = new Iterator();
        initIterator(s, iter);
        while (iterate(iter)) {
            iter.cur.used = false;
        }
    }

    /** Port: full_info and partial_info are VAR. */
    public static void declare(Set s, long[] full_info, long[] partial_info) {
        List l;
        long[] n_full = {0}, n_partial = {0};
        Iterator iter = new Iterator();
        // generate the link info for the revelations defined or used here
        // and count the exported revelations
        initIterator(s, iter);
        while (iterate(iter)) {
            l = iter.cur;
            if ((l.used) || (l.local)) {
                declareRevelation(l.ident, l.local, n_full, n_partial);
            }
        }

        full_info[0] = -1;
        partial_info[0] = -1;
        if ((n_full[0] > 0)) {
            full_info[0] = genList(s, n_full[0], true);
        }
        if ((n_partial[0] > 0)) {
            partial_info[0] = genList(s, n_partial[0], false);
        }
    }

    private static long genList(Set s, long cnt, boolean eq) {
        long base = Module.allocate(cnt * M3.rt.RV_SIZE + M3.target.Integer.size,
                                    M3.target.Integer.align, true, "revelations");
        long offs = base;
        Iterator iter = new Iterator();
        List l;
        PersistentRevelation.Seq seq = s.seq;
        PersistentRevelation.T[] array;

        // Collect into array in hash order, sort, and output.

        seq.sz = 0;
        AssertError.check(seq.st == 0, "Revelation");

        initIterator(s, iter);
        while (iterate(iter)) {
            l = iter.cur;
            if ((l.local) && (l.ident.equal == eq)) {
                PersistentRevelation.T r = new PersistentRevelation.T();
                r.lhs_id = Type.globalUID(l.ident.lhs);
                r.rhs_id = Type.globalUID(l.ident.rhs);
                seq.addhi(r);
            }
        }

        array = seq.elem;

        PersistentRevelation.sort(array, seq.sz);

        for (long i = 0; i <= seq.sz - 1; i++) {
            CG.init_intt(offs + M3.rt.RV_lhs_id, M3.target.Integer.size, array[(int) i].lhs_id, true);
            CG.init_intt(offs + M3.rt.RV_rhs_id, M3.target.Integer.size, array[(int) i].rhs_id, true);
            offs += M3.rt.RV_SIZE;
        }

        return base;
    }

    /** Port: full and partial are VAR. */
    private static void declareRevelation(T t, boolean exported,
                                          long[] full, long[] partial) {
        boolean interface_ = (!exported) || (Module.isInterface());
        long lhs_uid = Type.globalUID(t.lhs);
        long rhs_uid = Type.globalUID(t.rhs);
        Value.declare(t.obj);
        Type.compile(t.rhs);
        Type.compile(t.lhs);
        Host.env.note_revelation(t.home.name, interface_,
                                 lhs_uid, rhs_uid, t.equal, !exported);
        if ((exported)) {
            if ((t.equal)) {
                // Port: the uids are 32-bit values (M3FP.ToInt).
                CG.reveal_opaque((int) lhs_uid, (int) rhs_uid);
                ObjectType.noteOffsets(t.lhs, t.rhs);
                full[0]++;
            } else {
                partial[0]++;
            }
        }
    }

    private static void initIterator(Set s, Iterator i) {
        i.cur = null;
        i.set = s;
        i.next_list = s.idents;
        i.next_node = null;
        i.next_hash = 0;
        i.max_hash = 0;
        if ((s.hash != null)) {
            i.max_hash = s.hash.length;
        }
    }

    private static boolean iterate(Iterator i) {
        if (i.next_list != null) {
            i.cur = i.next_list;
            i.next_list = i.next_list.next;
            return true;
        }
        if ((i.next_node != null)) {
            i.next_list = i.next_node.contents;
            i.next_node = i.next_node.next;
            return iterate(i);
        }
        while ((i.next_hash < i.max_hash) && (i.next_node == null)) {
            i.next_node = i.set.hash[(int) i.next_hash];
            i.next_hash++;
        }
        if ((i.next_node != null)) {
            return iterate(i);
        }
        i.cur = null;
        return false;
    }
}
