// Java port of CM3's m3-sys/m3front/src/types/ProcType.i3 and ProcType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

import m3j.middle.M3Buf;
import m3j.middle.M3ID;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: ProcType.i3
// Last Modified On Tue Dec 20 14:39:09 PST 1994 By kalsow
//      Modified On Wed Aug 29 02:55:06 1990 By muller

// File: ProcType.m3
// Last modified on Tue May 23 15:25:57 PDT 1995 by kalsow
//      modified on Thu Dec  5 17:23:39 PST 1991 by muller

/** Procedure types and signatures. */
public final class ProcType {
    private ProcType() {}

    // Port: RTParams.IsPresent ("m3front-debug-proctype") in the module
    // body is FALSE.
    private static boolean debug = false;

    public static final class P extends Type.T {
        public CallExpr.MethodList methods;
        public Scope.T formals;
        public long nFormals;
        public Type.T result;
        public ESet.T raises;
        public Target.CallingConvention callConv;

        @Override
        public void check() {
            ProcType.check(this);
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

    /** Port: RTIO.PutRef prints the address of r; the identity hash code stands for it. */
    private static String refText(Object r) {
        return "0x" + Integer.toHexString(System.identityHashCode(r));
    }

    public static Type.T parse() {
        Target.CallingConvention callConv = M3.target.DefaultCall;
        if ((Scanner.cur.token == Token.T.tCALLCONV)) {
            callConv = Target.findConvention(M3.target, M3ID.toText(M3.ids, Scanner.cur.id));
            Scanner.getToken(); // tCALLCONV
            Scanner.match(Token.T.tENDPRAGMA);
        }
        Scanner.match(Token.T.tPROCEDURE);
        return parseSignature(M3ID.NoID, callConv);
    }

    private static final EnumSet<Token.T> FormalStart = EnumSet.of(Token.T.tVALUE, Token.T.tVAR,
            Token.T.tREADONLY, Token.T.tIDENT, Token.T.tUNUSED);

    public static Type.T parseSignature(int name, Target.CallingConvention cc) {
        P p = null;

        p = create(Scope.pushNew(false, name));
        p.callConv = cc;

        if (debug) {
            debug(":", name, p);
        }

        Scanner.match(Token.T.tLPAREN);
        while ((FormalStart.contains(Scanner.cur.token))) {
            parseFormal(p);
            if ((Scanner.cur.token != Token.T.tSEMI)) {
                break;
            }
            Scanner.getToken(); // ;
        }
        Scanner.match(Token.T.tRPAREN);
        if ((Scanner.cur.token == Token.T.tCOLON)) {
            Scanner.getToken(); // :
            p.result = Type.parse();
        }
        if ((Scanner.cur.token == Token.T.tRAISES)) {
            p.raises = ESet.parseRaises();
        }
        Scope.popNew();

        if (debug) {
            debug("^", name, p);
        }

        return p;
    }

    /** Port: the procedure Debug local to ParseSignature; name and p are its up-level variables. */
    private static void debug(String mark, int name, P p) {
        System.err.print("ParseSignature" + mark + " name:");
        if (name != 0) {
            System.err.print(M3ID.toText(M3.ids, name));
            System.err.print(":");
            System.err.print(name);
        }
        System.err.print(" p:");
        System.err.print(refText(p));
        System.err.print("\n");
        System.err.flush();
    }

    private static void parseFormal(P p) {
        long j, n;
        Value.T obj;
        Formal.Info formal = new Formal.Info();
        formal.mode = Formal.Mode.mVALUE;
        formal.type = null;
        formal.dfault = null;
        formal.trace = null;
        formal.unused = (Scanner.cur.token == Token.T.tUNUSED);
        if ((formal.unused)) {
            Scanner.getToken(); // UNUSED
            Scanner.match(Token.T.tENDPRAGMA);
        }
        if ((Scanner.cur.token == Token.T.tVALUE)) {
            formal.mode = Formal.Mode.mVALUE;
            Scanner.getToken(); // VALUE
        } else if ((Scanner.cur.token == Token.T.tVAR)) {
            formal.mode = Formal.Mode.mVAR;
            Scanner.getToken(); // VAR
        } else if ((Scanner.cur.token == Token.T.tREADONLY)) {
            formal.mode = Formal.Mode.mREADONLY;
            Scanner.getToken(); // READONLY
        }
        n = Ident.parseList();
        if ((Scanner.cur.token == Token.T.tCOLON)) {
            Scanner.getToken(); // :
            formal.type = Type.parse();
            if (debug) {
                System.err.print("formal.type.info.name formal.type:");
                System.err.print(refText(formal.type));
                System.err.print(" ");
                System.err.print(formal.type.info.name);
                System.err.print("\n");
                System.err.flush();
            }
        }
        if ((Scanner.cur.token == Token.T.tEQUAL)) {
            Error.msg("default value must begin with ':='");
            Scanner.cur.token = Token.T.tASSIGN;
        }
        if ((Scanner.cur.token == Token.T.tASSIGN)) {
            Scanner.getToken(); // :=
            formal.dfault = Expr.parse();
        }
        formal.trace = Variable.parseTrace();
        if ((formal.type == null) && (formal.dfault == null)) {
            Error.ID(Ident.stack[(int) (Ident.top - 1)],
                     "formals must have a type or default value");
        }
        j = Ident.top - n;
        for (long i = 0; i <= n - 1; i++) {
            formal.name = Ident.stack[(int) (j + i)];
            formal.offset = p.nFormals;
            obj = Formal.new_(formal);
            obj.origin = Ident.offset[(int) (j + i)];
            Scope.insert(obj);
            p.nFormals++;
        }
        Ident.top -= n;
    }

    private static int unnamed = M3ID.NoID;

    public static Type.T methodSigAsProcSig(Type.T sig, Type.T objType) {
        P pProc;
        Value.T f, v;
        Formal.Info formal = new Formal.Info();
        P pMethod = reduce(sig);
        if ((pMethod == null)) {
            return ErrType.T;
        }

        pProc = create(Scope.pushNew(false, M3ID.NoID));
        pProc.nFormals = pMethod.nFormals + 1;
        pProc.result = pMethod.result;
        pProc.raises = pMethod.raises;
        pProc.callConv = pMethod.callConv;

        // insert the "self" formal
        if ((unnamed == M3ID.NoID)) {
            unnamed = M3ID.add(M3.ids, "_self_");
        }
        formal.name = unnamed;
        formal.offset = 0;
        formal.mode = Formal.Mode.mVALUE;
        formal.type = objType;

        if (debug) {
            System.err.print("MethodSigAsProcSig:");
            System.err.print(refText(objType));
            System.err.print("\n");
            System.err.flush();
        }

        formal.dfault = null;
        formal.trace = null;
        formal.unused = false;
        Scope.insert(Formal.new_(formal));

        // copy the remaining formals
        v = Scope.toList(pMethod.formals);
        while ((v != null)) {
            Formal.split(v, formal);
            formal.offset++;
            f = Formal.new_(formal);
            f.origin = v.origin;
            Scope.insert(f);
            v = v.next;
        }

        Scope.popNew();
        return pProc;
    }

    private static P create(Scope.T s) {
        P p = new P();
        TypeRep.init(p, Type.Class.Procedure);
        p.methods = UserProc.Methods;
        p.formals = s;
        p.nFormals = 0;
        p.result = null;
        p.raises = null;
        p.callConv = M3.target.DefaultCall;
        return p;
    }

    private static void check(P p) {
        long hash;
        Value.T v;
        Formal.Info formal = new Formal.Info();
        M3.CheckState cs = M3.OuterCheckState.copy();
        Type.T result = null;
        // look up each of the named exceptions
        ESet.typeCheck(p.raises);

        // add the exceptions to the hash value
        hash = ESet.hash(p.raises);

        // finish my hash value
        v = Scope.toList(p.formals);
        while ((v != null)) {
            Formal.split(v, formal);
            hash = hash * 23 + M3ID.hash(M3.ids, formal.name);
            hash = hash * 37 + formal.mode.ordinal();
            v = v.next;
        }

        hash = hash + p.callConv.m3cg_id;

        p.info.size = M3.target.Address.size;
        p.info.min_size = M3.target.Address.size;
        p.info.alignment = M3.target.Address.align;
        p.info.addr_align = M3.target.Address.align;
        p.info.mem_type = CGType.Addr;
        p.info.stk_type = CGType.Addr;
        p.info.class_ = Type.Class.Procedure;
        p.info.isTraced = false;
        p.info.isEmpty = false;
        p.info.isSolid = true;
        p.info.hash = hash;

        Type.recursionDepth++; //------------------------------------
        p.checked = true;
        Scope.typeCheck(p.formals, cs);
        if ((p.result != null)) {
            result = Type.check(p.result);
            if (OpenArrayType.is(result)) {
                Error.msg("procedures may not return open arrays");
            }
        }
        Type.recursionDepth--; //------------------------------------
    }

    private static void compiler(P p) {
        long n_formals, n_raises;
        Value.T v;
        int result_id;
        if ((p.result == null)) {
            result_id = 0;
        } else {
            Type.compile(p.result);
            result_id = (int) Type.globalUID(p.result);
            if (largeResult(p.result)) {
                result_id = 0;
            }
        }

        v = Scope.toList(p.formals);
        n_formals = 0;
        while ((v != null)) {
            Formal.emitDeclaration(v, true, false);
            n_formals++;
            v = v.next;
        }
        n_raises = ESet.emitTypes(p.raises);
        // Port: CG.TypeUID is a 32-bit int; the uids come from M3FP.ToInt.
        CG.declare_proctype((int) Type.globalUID(p), n_formals, result_id, n_raises,
                            p.callConv); // (the original notes result_typename as still to do)
        v = Scope.toList(p.formals);
        while ((v != null)) {
            Formal.emitDeclaration(v, false, false);
            v = v.next;
        }
        ESet.emitNames(p.raises);
    }

    public static boolean isCompatible(Type.T procSig, Type.T objectType, Type.T methodSig) {
        P p = reduce(procSig);
        P q = reduce(methodSig);
        if ((p == null) || (q == null)) {
            return false;
        }
        if ((p.nFormals != q.nFormals + 1)) {
            return false;
        }
        if ((p.callConv.m3cg_id != q.callConv.m3cg_id)) {
            return false;
        }
        if ((p.result == null) && (q.result == null)) {
            // ok
        } else if (!Type.isEqual(p.result, q.result, null)) {
            return false;
        }
        if (!firstArgOK(p.formals, objectType)) {
            return false;
        }
        if (!formalsMatch(p.formals, q.formals, false, false, null)) {
            return false;
        }
        return ESet.isSubset(p.raises, q.raises);
    }

    private static boolean firstArgOK(Scope.T s, Type.T t) {
        Value.T v = Scope.toList(s);
        Formal.Info formal = new Formal.Info();
        while ((v != null)) {
            Formal.split(v, formal);
            if ((formal.offset == 0)) {
                return (formal.mode == Formal.Mode.mVALUE)
                    && Type.isSubtype(t, formal.type);
            }
            v = v.next;
        }
        return false;
    }

    private static boolean equalChk(P a, Type.T t, Type.Assumption x) {
        P b = (P) t;
        if ((a.methods != b.methods)) {
            return false;
        }
        if ((a.nFormals != b.nFormals)) {
            return false;
        }
        if ((a.callConv.m3cg_id != b.callConv.m3cg_id)) {
            return false;
        }
        if ((a.result == null) && (b.result == null)) {
            // ok
        } else if (!Type.isEqual(a.result, b.result, x)) {
            return false;
        }
        if (!formalsMatch(a.formals, b.formals, true, true, x)) {
            return false;
        }
        return ESet.isEqual(a.raises, b.raises);
    }

    private static boolean subtyper(P a, Type.T t) {
        P b;
        if ((t.info.class_ != Type.Class.Procedure)) {
            return false;
        }
        b = (P) t;
        if ((a.nFormals != b.nFormals)) {
            return false;
        }
        if ((a.callConv.m3cg_id != b.callConv.m3cg_id)) {
            return false;
        }
        if ((a.result == null) && (b.result == null)) {
            // ok
        } else if (!Type.isEqual(a.result, b.result, null)) {
            return false;
        }
        if (!formalsMatch(a.formals, b.formals, false, true, null)) {
            return false;
        }
        return ESet.isSubset(a.raises, b.raises);
    }

    private static boolean formalsMatch(Scope.T a, Scope.T b, boolean strict, boolean useFirst,
                                        Type.Assumption x) {
        Value.T va, vb;
        Formal.Info ia = new Formal.Info();
        Formal.Info ib = new Formal.Info();
        Expr.T e1, e2;
        va = Scope.toList(a);
        vb = Scope.toList(b);
        if ((!useFirst)) {
            if ((va == null)) {
                return false;
            }
            va = va.next;
        }

        while ((va != null) && (vb != null)) {
            Formal.split(va, ia);
            Formal.split(vb, ib);
            if ((ia.mode != ib.mode)) {
                return false;
            }
            if (!Type.isEqual(ia.type, ib.type, x)) {
                return false;
            }
            if ((strict)) {
                if ((ia.name != ib.name)) {
                    return false;
                }
                e1 = Expr.constValue(ia.dfault);
                e2 = Expr.constValue(ib.dfault);
                if (!Expr.isEqual(e1, e2, x)) {
                    return false;
                }
            }
            va = va.next;
            vb = vb.next;
        }

        return (va == null) && (vb == null);
    }

    private static P reduce(Type.T t) {
        t = Type.strip(t); // StripPacked?
        if ((t == null) || (t.info.class_ != Type.Class.Procedure)) {
            return null;
        }
        return (P) t;
    }

    public static boolean is(Type.T t) {
        return (reduce(t) != null);
    }

    public static Type.T result(Type.T t) {
        P p = reduce(t);
        if ((p != null)) {
            return p.result;
        } else {
            return ErrType.T;
        }
    }

    public static int resultTypename(Type.T t) {
        P p = reduce(t);
        int[] id = { M3ID.NoID };
        Type.compile(t);
        // If LargeResult AND standard_structs turned into void, return no type here.
        if (p != null && cgResult(p) != CGType.Void) {
            Type.typename(p.result, id);
        }

        if (debug) {
            System.err.print("ProcType.ResultTypename t:");
            System.err.print(refText(t));
            System.err.print(" p:");
            System.err.print(refText(p));
            System.err.print(" id:");
            System.err.print(id[0]);
            if (p != null) {
                System.err.print(" p.result:");
                System.err.print(refText(p.result));
                System.err.print(" CGResult (p):");
                System.err.print(cgResult(p).ordinal());
                System.err.print(" void:");
                System.err.print(CGType.Void.ordinal());
            }
            System.err.print("\n");
            System.err.flush();
        }

        return id[0];
    }

    public static CGType cgResult(Type.T t) {
        P p = reduce(t);
        if ((p == null) || (p.result == null)) {
            return CGType.Void;
        } else if (!largeResult(p.result)) {
            return Type.cgType(p.result, /* in_memory := */ true);
            //** 2/27/96 WKK:  in_memory = TRUE => so that Win32 code generator
            //     can convert register return values to their full 32-bit width! **
        } else if (p.callConv.standard_structs) {
            return CGType.Void;
        } else {
            return CGType.Struct;
        }
    }

    public static boolean largeResult(Type.T t) {
        return Type.isStructured(t);
    }

    public static long nFormals(Type.T t) {
        P p = reduce(t);
        Value.T v;
        long n;
        if ((p == null)) {
            return 0;
        }
        v = Scope.toList(p.formals);
        n = 0;
        while ((v != null)) {
            v = v.next;
            n++;
        }
        if ((p.result != null) && largeResult(p.result)) {
            n++; // large results are passed as an extra VAR parameter
        }
        return n;
    }

    public static Value.T formals(Type.T t) {
        P p = reduce(t);
        if ((p != null)) {
            return Scope.toList(p.formals);
        } else {
            return null;
        }
    }

    public static ESet.T raises(Type.T t) {
        P p = reduce(t);
        if ((p != null)) {
            return p.raises;
        } else {
            return null;
        }
    }

    public static CallExpr.MethodList methods(Type.T t) {
        P p = reduce(t);
        if ((p != null)) {
            return p.methods;
        } else {
            return null;
        }
    }

    public static Target.CallingConvention callConv(Type.T t) {
        P p = reduce(t);
        if ((p != null)) {
            return p.callConv;
        } else {
            return null;
        }
    }

    public static Type.T new_(Type.T result, Value.T f0, Value.T f1, Value.T f2, Value.T f3, Value.T f4) {
        P p;
        p = create(Scope.pushNew(false, M3ID.NoID));
        if ((f0 != null)) {
            Scope.insert(f0);
            p.nFormals++;
        }
        if ((f1 != null)) {
            Scope.insert(f1);
            p.nFormals++;
        }
        if ((f2 != null)) {
            Scope.insert(f2);
            p.nFormals++;
        }
        if ((f3 != null)) {
            Scope.insert(f3);
            p.nFormals++;
        }
        if ((f4 != null)) {
            Scope.insert(f4);
            p.nFormals++;
        }
        p.result = result;
        Scope.popNew();
        return p;
    }

    public static Type.T new_(Type.T result, Value.T f0, Value.T f1, Value.T f2, Value.T f3) {
        return new_(result, f0, f1, f2, f3, null);
    }

    public static Type.T new_(Type.T result, Value.T f0, Value.T f1, Value.T f2) {
        return new_(result, f0, f1, f2, null, null);
    }

    public static Type.T new_(Type.T result, Value.T f0, Value.T f1) {
        return new_(result, f0, f1, null, null, null);
    }

    public static Type.T new_(Type.T result, Value.T f0) {
        return new_(result, f0, null, null, null, null);
    }

    public static Type.T new_(Type.T result) {
        return new_(result, null, null, null, null, null);
    }

    public static void setMethods(Type.T t, CallExpr.MethodList m) {
        ((P) t).methods = m;
    }

    private static long initCoster(P p, boolean zeroed) {
        if ((zeroed)) {
            return 0;
        } else {
            return 1;
        }
    }

    private static void genMap(P p, long offset, long size, boolean refs_only) {
        AssertError.check(size == M3.target.Address.size, "ProcType");
        if (!refs_only) {
            TipeMap.add(offset, TipeMap.Op.Proc, 0);
        }
    }

    private static void genDesc(P p) {
        TipeDesc.addO(TipeDesc.Op.Proc, p);
    }

    private static void fPrinter(P p, M3.FPInfo x) {
        Value.T v;
        long n;
        M3Buf.putText(x.buf, "PROC(");

        if ((p.callConv.m3cg_id != 0)) {
            M3Buf.putText(x.buf, "<*CC=");
            M3Buf.putInt(x.buf, p.callConv.m3cg_id);
            M3Buf.putText(x.buf, "*>");
        }

        // count the nodes
        v = Scope.toList(p.formals);
        n = 0;
        while ((v != null)) {
            n += Value.addFPTag(v, x);
            v = v.next;
        }
        M3Buf.putChar(x.buf, ')');
        if ((p.result != null)) {
            M3Buf.putText(x.buf, " => ?");
            n++;
        }
        n += ESet.addFPTag(p.raises, x);
        x.n_nodes = n;

        if ((n <= x.nodes.length)) {
            v = Scope.toList(p.formals);
            n = 0;
            while ((v != null)) {
                n = Value.addFPEdges(v, x, n);
                v = v.next;
            }
            if ((p.result != null)) {
                x.nodes[(int) n] = p.result;
                n++;
            }
            n = ESet.addFPEdges(p.raises, x, n);
        } else {
            x.others = new Type.T[(int) n];
            v = Scope.toList(p.formals);
            n = 0;
            while ((v != null)) {
                n = Value.addFPEdges(v, x, n);
                v = v.next;
            }
            if ((p.result != null)) {
                x.others[(int) n] = p.result;
                n++;
            }
            n = ESet.addFPEdges(p.raises, x, n);
        }
    }
}
