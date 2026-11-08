// Java port of CM3's m3-sys/m3front/src/types/NamedType.i3 and NamedType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3ID;

// File: NamedType.m3
// Last modified on Tue Jul 19 10:05:49 PDT 1994 by kalsow
//      modified on Fri Dec 21 01:25:25 1990 by muller

public final class NamedType {
    private NamedType() {}

    private static boolean debug = false;

    public static final class P extends Type.T {
        public Scope.T scope = null;
        public M3.QID qid = new M3.QID();
        public Type.T type = null;
        public Value.T obj = null;

        @Override
        public void check() {
            NamedType.check(this);
        }

        @Override
        public boolean no_straddle(long offset, boolean IsEltOrField) {
            return noStraddle(this, offset, IsEltOrField);
        }

        @Override
        public boolean isEqual(Type.T t, Type.Assumption x) {
            return TypeRep.neverEqual(this, t, x);
        }

        // Port: the override isSubtype := TypeRep.NoSubtypes restates the
        // inherited method.

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
            genInit(this, zeroed);
        }

        @Override
        public void mapper(long offset, long size, boolean refs) {
            genMap(this, offset, size, refs);
        }

        @Override
        public void gen_desc() {
            genDesc(this);
        }

        @Override
        public void fprint(M3.FPInfo x) {
            fPrinter(this, x);
        }
    }

    public static Type.T parse() {
        P p = null;
        Type.T t;
        long case_ = 0;
        if ((Scanner.cur.token == Token.T.tIDENT)
            && (Scanner.cur.defn != null)
            && (Value.classOf(Scanner.cur.defn) == Value.Class.Type)) {
            // this identifier is reserved!
            t = Value.toType(Scanner.cur.defn);
            Scanner.getToken(); // IDENT
            case_ = 1;
        } else {
            case_ = 2;
            // this is a non-reserved ID
            p = new P();
            TypeRep.init(p, Type.Class.Named);
            p.scope = Scope.top();
            p.qid.item = Scanner.matchID();
            if ((Scanner.cur.token == Token.T.tDOT)) {
                Scanner.getToken(); // .
                p.qid.module = p.qid.item;
                p.qid.item = Scanner.matchID();
            }
            t = p;
        }

        case_ = case_ * 10;

        if ((Scanner.cur.token == Token.T.tBRANDED)) {
            case_ = case_ + 1;
            t = ObjectType.parse(t, false, Brand.parse());
        } else if ((Scanner.cur.token == Token.T.tOBJECT)) {
            case_ = case_ + 2;
            t = ObjectType.parse(t, false, null);
        }

        if (debug) {
            System.err.print("NamedType.Parse t:");
            System.err.print(refText(t));
            System.err.print(" p:");
            System.err.print(refText(p));
            System.err.print(" ");
            System.err.print(Long.toString(case_));
            System.err.print(" ");
            if (p != null && p.qid.module != 0) {
                System.err.print(M3ID.toText(M3.ids, p.qid.module));
                System.err.print("__");
            }
            if (p != null && p.qid.item != 0) {
                System.err.print(M3ID.toText(M3.ids, p.qid.item));
            }
            System.err.print("\n");
            System.err.flush();
        }

        return t;
    }

    /**
     * Port: the text that RTIO.PutRef writes; Java has no addresses, so the
     * identity hash code stands for the address.
     */
    private static String refText(Object r) {
        return "0x" + (r == null ? "0" : Integer.toHexString(System.identityHashCode(r)));
    }

    public static Type.T new_(Type.T t) {
        P p;
        p = new P();
        TypeRep.init(p, Type.Class.Named);
        p.type = t;
        return p;
    }

    public static Type.T create(int module, int name) {
        P p;
        p = new P();
        TypeRep.init(p, Type.Class.Named);
        p.scope = Scope.top();
        p.qid.module = module;
        p.qid.item = name;
        return p;
    }

    private static P reduce(Type.T t) {
        if ((t == null)) {
            return null;
        }
        if ((t.info.class_ != Type.Class.Named)) {
            return null;
        }
        return (P) t;
    }

    /** Port: VAR name is written. */
    public static boolean split(Type.T t, M3.QID name) {
        P p = reduce(t);
        if ((p == null)) {
            return false;
        }
        resolve(p);
        name.module = p.qid.module;
        name.item = p.qid.item;
        return true;
    }

    /** Port: VAR v is {@code v[0]}. */
    public static boolean splitV(Type.T t, Value.T[] v) {
        // return V for Value
        P p = reduce(t);
        if ((p == null)) {
            return false;
        }
        resolve(p);
        v[0] = p.obj;
        return true;
    }

    private static void resolve(P p) {
        Value.T o;
        Type.T t;
        long save;
        if ((p.type == null)) {
            o = Scope.lookUpQID(p.scope, p.qid);
            p.obj = o;
            if ((o == null)) {
                save = Scanner.offset;
                Scanner.offset = p.origin;
                Error.QID(p.qid, "undefined");
                Scanner.offset = save;
                t = ErrType.T;
            } else if ((Value.classOf(o) == Value.Class.Type)) {
                t = Value.toType(o);
                if (M3.target.Typenames) {
                    p.info.name = M3ID.add(M3.ids,
                        Value.globalName(o, /* dots := */ false /* underscores instead */));
                }
            } else {
                save = Scanner.offset;
                Scanner.offset = p.origin;
                Error.QID(p.qid, "name isn't bound to a type");
                Scanner.offset = save;
                t = ErrType.T;
            }
            p.type = t;
        }
    }

    /** Identity, if t is not a named type. */
    public static Type.T strip(Type.T t) {
        P p = reduce(t);
        if (p == null) {
            return t;
        }
        resolve(p);
        return p.type;
    }

    private static void check(P p) {
        M3.CheckState cs = M3.OuterCheckState.copy();
        long[] nErrs = new long[1], nWarns = new long[1], nErrsB = new long[1];
        int name = M3ID.NoID;
        Type.T type = null;
        resolve(p); // Resolve can fill in name.
        name = p.info.name; // Preserve name around Type.CheckInfo.
        nErrs[0] = 0;
        nErrsB[0] = 0;
        if ((p.obj != null)) {
            Error.count(nErrs, nWarns);
            Value.typeCheck(p.obj, cs);
            Error.count(nErrsB, nWarns);
        }
        if ((nErrs[0] == nErrsB[0])) {
            // no errors yet...
            type = Type.checkInfo(p.type, p.info);
        } else { // some sort of error (probably illegal recursion...)
            Type.checkInfo(ErrType.T, p.info);
        }
        p.info.class_ = Type.Class.Named; // this node is still a Named node
        p.info.name = name;
    }

    private static boolean noStraddle(P p, long offset, boolean IsEltOrField) {
        resolve(p);
        return Type.straddleFreeScalars(p.type, offset, IsEltOrField);
    }

    private static void compiler(P p) {
        resolve(p);
        //** Type.Compile (p.type);  **
        if ((p.type != null)) {
            Scanner.offset = p.type.origin;
            p.type.compile();
        }
    }

    private static long initCoster(P p, boolean zeroed) {
        resolve(p);
        return Type.initCost(p.type, zeroed);
    }

    private static void genInit(P p, boolean zeroed) {
        resolve(p);
        Type.initValue(p.type, zeroed);
    }

    private static void genMap(P p, long offset, long size, boolean refs_only) {
        resolve(p);
        Type.genMap(p.type, offset, size, refs_only);
    }

    private static void genDesc(P p) {
        resolve(p);
        Type.genDesc(p.type);
    }

    private static void fPrinter(P p, M3.FPInfo x) {
        Error.QID(p.qid, "INTERNAL ERROR: fingerprint of named type");
        resolve(p);
        if ((p.type != null)) {
            p.type.fprint(x);
        }
    }

    static {
        // Port: RTParams.IsPresent ("m3front-debug-namedtype") is FALSE.
        debug = false;
    }
}
