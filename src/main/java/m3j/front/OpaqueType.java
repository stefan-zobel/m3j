// Java port of CM3's m3-sys/m3front/src/types/OpaqueType.i3 and OpaqueType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3ID;
import m3j.middle.Target.CGType;

// File: OpaqueType.i3
// Last Modified On Tue Jun 28 10:18:16 PDT 1994 By kalsow

// File: OpaqueType.m3
// Last modified on Tue May 23 15:25:09 PDT 1995 by kalsow
//      modified on Sun Feb 24 05:41:53 1991 by muller

/** Opaque types: TYPE T &lt;: U. */
public final class OpaqueType {
    private OpaqueType() {}

    public static final class P extends Type.T {
        public Value.T declared;
        /** RHS of &lt;: */
        public Type.T super_;
        public int id;
        public boolean isTraced;

        @Override
        public void check() {
            OpaqueType.check(this);
        }

        @Override
        public boolean no_straddle(long offset, boolean IsEltOrField) {
            return TypeRep.addrNoStraddle(this, offset, IsEltOrField);
        }

        @Override
        public boolean isEqual(Type.T t, Type.Assumption x) {
            return equalChk(this, t, x);
        }

        @Override
        public boolean isSubtype(Type.T t) {
            return subtyper(this, t);
        }

        @Override
        public void compile() {
            compiler(this);
        }

        @Override
        public long initCost(boolean zeroed) {
            return initCoster(this, zeroed);
        }

        @Override
        public void initValue(boolean zeroed) {
            TypeRep.initToZeros(this, zeroed);
        }

        @Override
        public void mapper(long offset, long size, boolean refs) {
            TypeRep.genRefMap(this, offset, size, refs);
        }

        @Override
        public void gen_desc() {
            TypeRep.genRefDesc(this);
        }

        @Override
        public void fprint(M3.FPInfo x) {
            fPrinter(this, x);
        }
    }

    public static Type.T new_(Type.T super_, Value.T decl) {
        P p;
        p = new P();
        TypeRep.init(p, Type.Class.Opaque);
        p.super_ = super_;
        p.declared = decl;
        p.id = M3ID.NoID;
        return p;
    }

    private static P reduce(Type.T t) {
        t = Type.strip(t); // StripPacked?
        if ((t == null) || (t.info.class_ != Type.Class.Opaque)) {
            return null;
        }
        return (P) t;
    }

    public static boolean is(Type.T t) {
        // RHS of <:
        return (reduce(t) != null);
    }

    /** RHS of &lt;: */
    public static Type.T super_(Type.T t) {
        P p = reduce(t);
        if ((p != null)) {
            return p.super_;
        } else {
            return t;
        }
    }

    /** if t is an opaque type, return it's uid, otherwise return zero */
    public static long UID(Type.T t) {
        P p = reduce(t);
        if ((p == null)) {
            return M3ID.NoID;
        }
        if ((p.id == M3ID.NoID)) {
            p.id = M3ID.add(M3.ids, Value.globalName(p.declared));
        }
        return p.id;
    }

    private static void check(P p) {
        Type.Info info = new Type.Info();
        p.super_ = Type.checkInfo(p.super_, info);
        p.isTraced = info.isTraced;

        if ((info.class_ != Type.Class.Opaque)
            && (info.class_ != Type.Class.Ref)
            && (info.class_ != Type.Class.Object)) {
            if ((info.class_ != Type.Class.Error)) {
                Error.msg("opaque super type must be a reference type");
            }
            p.super_ = Reff.T;
        }

        p.info.size = M3.target.Address.size;
        p.info.min_size = M3.target.Address.size;
        p.info.alignment = M3.target.Address.align;
        p.info.addr_align = M3.target.Address.align;
        // ^The revelations are not generally available in this compilation.
        //  One of them could require MaxAlign, which would exceed Address.align
        //  on a 32-bit target, but we can't assume that.
        p.info.mem_type = CGType.Addr;
        p.info.stk_type = CGType.Addr;
        p.info.class_ = Type.Class.Opaque;
        p.info.isTraced = info.isTraced;
        p.info.isEmpty = false;
        p.info.isSolid = true;
        p.info.hash = -p.id; // all opaque types are unique
    }

    private static void compiler(P p) {
        long self = Type.globalUID(p);
        long super_ = Type.globalUID(p.super_);
        int typeId;
        // Port: CG.TypeUID is a 32-bit int; the uids come from M3FP.ToInt.
        CG.declare_opaque((int) self, (int) super_);
        typeId = (int) UID(p);
        Host.env.note_opaque(self, super_, typeId);
    }

    private static boolean equalChk(P a, Type.T b, Type.Assumption x) {
        return false;
    }

    /** called if the normal subtype methods didn't prove a &lt;: b. */
    public static boolean isSubtype(Type.T a, Type.T b) {
        // called if the normal subtype methods didn't prove a <: b.
        P p = reduce(b);
        Type.T t;
        if ((p == null)) {
            return false;
        }
        t = Revelation.lookUp(p);
        if ((t == null)) {
            return false;
        }
        t = Type.check(t);
        return Type.isSubtype(a, t);
    }

    private static long Depth = 0;
    private static long StopDepth = Long.MAX_VALUE;
    private static long bkpt = 0;
    //
    // VAR is: BOOLEAN;
    //

    private static boolean subtyper(P a, Type.T b) {
        Type.T t;
        Revelation.TypeSet x = new Revelation.TypeSet();

        Depth++;
        if (Depth > StopDepth) {
            bkpt = 9;
        }

        // try a's declared super type
        if (Type.isSubtype(a.super_, b)) {
            Depth--;
            return true;
        }

        // **********************************************
        // (* try for a full revelation *)
        // t := Revelation.LookUp (a);
        // IF (t # NIL) THEN
        //   t := Type.Check (t);
        //   is := Type.IsSubtype (t, b);
        //   DEC (Depth);
        //   RETURN is;
        // END;
        // ************************************************

        // finally, try all the visible revelations
        Revelation.lookUpAll(a, x);
        for (long i = 0; i <= x.cnt - 1; i++) {
            t = Type.check(x.types[(int) i]);
            if (Type.isSubtype(t, b)) {
                Depth--;
                return true;
            }
        }
        while ((x.others != null)) {
            t = Type.check(x.others.type);
            if (Type.isSubtype(t, b)) {
                Depth--;
                return true;
            }
            x.others = x.others.next;
        }

        Depth--;
        return false;
    }

    private static long initCoster(P p, boolean zeroed) {
        if ((p.isTraced) && (!zeroed)) {
            return 1;
        } else {
            return 0;
        }
    }

    private static void fPrinter(P p, M3.FPInfo x) {
        Scope.IDStack s = new Scope.IDStack();
        if (Type.isEqual(p, Textt.T, null)) {
            x.tag = "$text";
            x.n_nodes = 0;
        } else if (Type.isEqual(p, Mutex.T, null)) {
            x.tag = "$mutex";
            x.n_nodes = 0;
        } else {
            M3Buf.putText(x.buf, "OPAQUE ");
            s.top = 0;
            Scope.nameToPrefix(p.declared, s, false, true);
            Scope.putStack(x.buf, s);
            x.n_nodes = 1;
            x.nodes[0] = p.super_;
        }
    }

    static {
        Depth = 0;
        StopDepth = 20;
    }
}
