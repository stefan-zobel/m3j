// Java port of CM3's m3-sys/m3front/src/values/Procedure.i3 and Procedure.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: Procedure.i3
// Last Modified On Tue Dec 20 14:55:57 PST 1994 By kalsow
//      Modified On Tue Oct  9 23:46:24 1990 By muller

// File: Procedure.m3
// Last Modified On Tue Jun 20 09:58:41 PDT 1995 by kalsow
//      Modified On Tue Jun 13 20:00:51 PDT 1995 by ericv
//      Modified On Thu Dec  5 17:21:10 PST 1991 by muller

public final class Procedure {
    private Procedure() {}

    // Port: RTParams.IsPresent ("m3front-debug-procedure") is always FALSE.
    private static boolean debug = false;

    public static class T extends Value.T {
        public T intf_peer;
        public T impl_peer;
        public T next_defn;
        public Type.T signature;
        public Scope.T syms;
        public Stmt.T block;
        public Body body;
        public Variable.T result;
        /** A global, predefined, pseudo-procedure, e.g. NEW. */
        public boolean builtin;
        /**
         * Either 'builtin' or a procedure in an interface known to the
         * compiler, e.g. Word.Plus.
         */
        public boolean predefined;
        public boolean needs_raises;
        public boolean direct;
        public ESet.T fails;
        /** Can be assigned to a variable and passed as a parameter. */
        public boolean assignable;
        public M3CG.Proc cg_proc;
        public T next_cg_proc;
        public long end_origin;
        public Jmpbufs.Proc jmpbufs;

        @Override
        public void typeCheck(M3.CheckState cs) {
            check(this, cs);
        }

        @Override
        public void load() {
            Procedure.load(this);
        }

        @Override
        public boolean declare() {
            return declarer(this);
        }

        @Override
        public boolean need_init() {
            return needInit(this);
        }

        @Override
        public void lang_init() {
            langInit(this);
        }

        @Override
        public Expr.T toExpr() {
            return Procedure.toExpr(this);
        }

        @Override
        public Type.T typeOf() {
            return Procedure.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return Procedure.typeOf(this);
        }

        @Override
        public long add_fp_tag(M3.FPInfo x) {
            return addFPTag(this, x);
        }

        @Override
        public Type.T fp_type() {
            return fpType(this);
        }

        // Port: the overrides set_globals := ValueRep.NoInit, const_init :=
        // ValueRep.NoInit, user_init := ValueRep.NoInit, toType :=
        // ValueRep.NoType and base := ValueRep.Self restate the inherited
        // methods.
    }

    public static final class Body extends ProcBody.T {
        public T self;

        @Override
        public void gen_decl() {
            emitDecl(this);
        }

        @Override
        public void gen_body() {
            emitBody(this);
        }
    }

    // VAR (*CONST*)
    private static int resultName = M3ID.NoID;
    private static int returnName = M3ID.NoID;

    /** interface procedures that are implemented in the current compilation unit */
    private static T defined_procs = null;

    /** procedures that have M3CG attachments */
    private static T cg_procs = null;

    // Port: RTIO output goes to System.err; a reference prints as the hex
    // value of its identity hash code instead of its address.

    private static void rtioPutText(String t) {
        System.err.print(t);
    }

    private static void rtioPutInt(long i) {
        System.err.print(i);
    }

    private static void rtioPutRef(Object r) {
        System.err.print("0x" + Integer.toHexString(r == null ? 0 : System.identityHashCode(r)));
    }

    private static void rtioFlush() {
        System.err.flush();
    }

    public static void reset() {
        T t, u;
        // disconnect the interface->implementation mappings
        t = defined_procs;
        while ((t != null)) {
            u = t;
            t = t.next_defn;
            u.impl_peer = null;
            u.next_defn = null;
        }
        defined_procs = null;

        // release any M3CG objects that were created.
        t = cg_procs;
        while ((t != null)) {
            u = t;
            t = t.next_cg_proc;
            u.cg_proc      = null;
            u.next_cg_proc = null;
            u.body         = null;
        }
        cg_procs = null;
    }

    public static void parseDecl(Decl.Attributes att, boolean headerOnly) {
        // Port: TK = Token.T.
        T t;
        int id, final_id;
        Target.CallingConvention cc = att.callingConv;
        Scanner.match(Token.T.tPROCEDURE);
        id = Scanner.matchID();
        t = create(id);
        t.name = id;
        t.unused    = att.isUnused;
        t.obsolete  = att.isObsolete;
        if ((att.isExternal)) {
            t.external = true;
            t.javaBound = att.isJava || Module.isJava();
            if ((att.alias == M3ID.NoID)) {
                t.extName = t.name;
            } else {
                t.extName = att.alias;
            }
        }
        if ((cc == null)) {
            cc = M3.target.DefaultCall;
        }
        t.signature = ProcType.parseSignature(id, cc);

        if (debug) {
            rtioPutText("ParseDecl id:");
            rtioPutText(M3ID.toText(M3.ids, id));
            rtioPutText(" t:");
            rtioPutRef(t);
            rtioPutText(" signature:");
            rtioPutRef(t.signature);
            rtioPutText("\n");
            rtioFlush();
        }

        Scope.insert(t);
        if ((Scanner.cur.token == Token.T.tEQUAL)) {
            t.body = new Body();
            t.body.self = t;
            ProcBody.push(t.body);
            Scanner.getToken(); // =
            if ((headerOnly)) {
                Error.ID(id, "procedure declaration cannot include a body");
            }
            if ((att.isExternal)) {
                Error.warnID(2, id, "external procedure cannot include a body");
                t.external = false;
                t.javaBound = false;
                t.extName  = M3ID.NoID;
            }
            t.syms  = Scope.pushNew(true, id);
            t.block = BlockStmt.parse(false);
            t.fails = BlockStmt.extractFails(t.block);
            t.end_origin = Scanner.offset;
            final_id = Scanner.matchID();
            if ((final_id != id)) {
                Error.ID(id, "Final procedure name must match Initial name (2.4.4).");
            }
            Scope.popNew();
            ProcBody.pop();
        } else if ((headerOnly) || (att.isExternal)) {
            // ok
        } else if ((Scanner.cur.token == Token.T.tSEMI)) {
            t.body = new Body();
            t.body.self = t;
            ProcBody.push(t.body);
            // It's a syntax error, but try recovering from it by assuming
            // it's Modula-2 syntax
            Error.ID(id, "expecting '=' before procedure body");
            Scanner.getToken(); // ;
            t.syms  = Scope.pushNew(true, id);
            t.block = BlockStmt.parse(false);
            t.fails = BlockStmt.extractFails(t.block);
            t.end_origin = Scanner.offset;
            final_id = Scanner.matchID();
            if ((final_id != id)) {
                Error.ID(id, "Initial name doesn't match final name");
            }
            Scope.popNew();
            ProcBody.pop();
        } else {
            Error.ID(id, "procedure declaration must include a body");
        }
        Scanner.match(Token.T.tSEMI);
    }

    public static void parseDecl(Decl.Attributes att) {
        parseDecl(att, false);
    }

    public static boolean isEqual(Value.T a, Value.T b) {
        T ta, tb;
        a = Value.base(a);
        b = Value.base(b);
        if ((a == b)) {
            return true;
        }
        if (a == null) {
            return false;
        } else if (a instanceof T t) {
            ta = t;
        } else {
            return false;
        }
        if (b == null) {
            return false;
        } else if (b instanceof T t) {
            tb = t;
        } else {
            return false;
        }
        return (ta.intf_peer == tb) || (tb.intf_peer == ta)
            || (ta.impl_peer == tb) || (tb.impl_peer == ta);
    }

    private static T create(int name) {
        T t;
        t = new T();
        ValueRep.init(t, name, Value.Class.Procedure);
        t.readonly     = true;
        t.intf_peer    = null;
        t.impl_peer    = null;
        t.next_defn    = null;
        t.signature    = null;
        t.syms         = null;
        t.body         = null;
        t.block        = null;
        t.external     = false;
        t.builtin      = false;
        t.assignable   = true;
        t.predefined   = false;
        t.result       = null;
        t.extName      = M3ID.NoID;
        t.needs_raises = true;
        t.fails        = null;
        t.cg_proc      = null;
        t.next_cg_proc = null;
        t.end_origin   = t.origin;
        return t;
    }

    public static void definePredefined(String name, CallExpr.MethodList methods, boolean reserved, Type.T signature, boolean assignable) {
        T t;
        int s;
        Type.T sig;
        if ((signature == null)) {
            sig = ProcType.new_(null);
        } else {
            sig = signature;
        }
        ProcType.setMethods(sig, methods);
        s = M3ID.add(M3.ids, name);
        t = create(s);
        t.signature  = sig;
        t.builtin    = (signature == null);
        t.assignable = assignable;
        t.predefined = true;

        if (debug) {
            rtioPutText("DefinePredefined name:");
            rtioPutText(name);
            rtioPutText(" t:");
            rtioPutRef(t);
            rtioPutText(" signature:");
            rtioPutRef(t.signature);
            rtioPutText("\n");
            rtioFlush();
        }

        Scope.insert(t);
        if ((reserved)) {
            Scanner.noteReserved(s, t);
        }
    }

    public static void definePredefined(String name, CallExpr.MethodList methods, boolean reserved, Type.T signature) {
        definePredefined(name, methods, reserved, signature, false);
    }

    public static void definePredefined(String name, CallExpr.MethodList methods, boolean reserved) {
        definePredefined(name, methods, reserved, null, false);
    }

    public static void noteExport(Value.T implv, Value.T intfv) {
        T impl = (T) Value.base(implv);
        T intf = (T) Value.base(intfv);
        if ((impl.intf_peer != null) || (intf.impl_peer != null)) {
            redefined(impl, null /*intf*/);
        } else {
            if (!Type.isAssignable(intf.signature, impl.signature)) {
                redefined(impl, null /*intf*/);
            }
            intf.next_defn = defined_procs;
            defined_procs = intf;
            intf.impl_peer = impl;
            impl.intf_peer = intf;
            impl.scope     = intf.scope; // retain the exported module name
            impl.used      = true;
        }
    }

    private static Type.T typeOf(T p) {
        return p.signature;
    }

    private static void check(T p, M3.CheckState cs) {
        Type.check(p.signature);
        // NOTE: don't save the signature returned by Type.Check cause if
        // you do, the formals will be reused by procedures with the
        // same signature.

        if (Module.isInterface()) {
            Formal.nameDefaultConstructors(p.signature, p.name, cs);
        }

        Value.typeCheck(p.intf_peer, cs);

        // defer the rest to CheckBody
    }

    public static void checkBody(Procedure.T p, M3.CheckState cs) {
        Variable.T v;
        Value.T formal;
        long n_formals;
        Type.T result;
        Scope.T zz;
        ESet.T raises;
        M3.CheckState save;
        Formal.Info f_info = new Formal.Info();
        Target.CallingConvention cconv;
        if ((p.body == null)) {
            return;
        }

        Coverage.noteProcedure(p);
        zz = Scope.push(p.syms);
        p.body.name = Value.globalName(p, true, /* with_module := */ false);
        result = ProcType.result(p.signature);
        cconv = ProcType.callConv(p.signature);

        // create a variable for the return result
        if (cconv.results_on_left) {
            checkResult(p, result);
        }

        // create local variables for each of the formals
        formal = ProcType.formals(p.signature);
        n_formals = 0;
        while ((formal != null)) {
            v = Variable.newFormal(formal, formal.name);
            Scope.insert(v);
            Formal.split(formal, f_info);
            Variable.bindTrace(v, f_info.trace);
            formal.scope = v.scope;
            // identify the full names of the formal & its local variable
            formal = formal.next;
            n_formals++;
        }

        // create a variable for the return result
        if (!cconv.results_on_left) {
            checkResult(p, result);
        }

        raises = ProcType.raises(p.signature);
        save = cs.copy();
        cs.raises_others = false;
        ESet.typeCheck(p.fails);
        ESet.push(cs, raises, p.fails, /* stop := */ true);

        p.checked = true;
        Type.recursionDepth++;
        Scope.typeCheck(p.syms, cs);
        Marker.pushProcedure(result, p.result, cconv);
        p.jmpbufs = Jmpbufs.checkProcPush(cs.jmpbufs, p.name);
        Stmt.typeCheck(p.block, cs);
        Jmpbufs.checkProcPop(cs.jmpbufs, p.jmpbufs);
        Marker.pop();
        Scope.warnUnused(p.syms);
        Type.recursionDepth--;

        p.needs_raises = cs.raises_others;
        cs.raises_others = save.raises_others;
        ESet.pop(cs, raises, p.fails, /* stop := */ true);

        Scope.pop(zz);
    }

    private static void checkResult(T p, Type.T result) {
        Variable.T v;
        Value.T formal;
        if ((result == null)) {
            return;
        }
        if ((resultName == M3ID.NoID)) {
            resultName = M3ID.add(M3.ids, "_result");
        }
        if ((returnName == M3ID.NoID)) {
            returnName = M3ID.add(M3.ids, "_return");
        }

        if (ProcType.largeResult(result)) {
            Formal.Info info = new Formal.Info();
            info.name = returnName;
            info.mode = Formal.Mode.mVAR;
            info.offset = -1;
            info.type = result;
            info.dfault = null;
            info.unused = false;
            info.trace = null;
            formal = Formal.new_(info);
            formal.imported = false;
            formal.origin = p.origin;
            formal.used = true;
            Scope.insert(formal);
            v = Variable.newFormal(formal, resultName);
        } else {
            v = Variable.new_(resultName, true);
            Variable.bindType(v, result, /* indirect := */ false,
                              /* readonly := */ false, /* open_array_ok := */ false,
                              /* needs_init := */ false);
        }
        v.imported = false;
        v.origin = p.origin;
        v.used = true;
        Scope.insert(v);
        p.result = v;
    }

    private static void load(T t) {
        if ((t.builtin)) {
            Error.ID(t.name, "builtin operation is not a procedure");
        }
        if ((t.impl_peer != null)) {
            t = t.impl_peer;
        }
        t.used = true;
        Value.declare(t);
        if (t.assignable) {
            CG.load_procedure(t.cg_proc);
        } else {
            Error.ID(t.name, "this predefined procedure cannot be passed or assigned");
            CG.load_nil();
        }
    }

    /** generate code to load the static link needed to call 't' */
    public static void loadStaticLink(Procedure.T t) {
        if ((t.builtin)) {
            Error.ID(t.name, "builtin operation is not a procedure");
        }
        if ((t.impl_peer != null)) {
            t = t.impl_peer;
        }
        t.used = true;
        Value.declare(t);
        if (isNested(t)) {
            CG.load_static_link(t.cg_proc);
        } else {
            CG.load_nil();
        }
    }

    private static void importProc(T p, String name, long n_formals,
                                   CGType cg_result, int return_typeid,
                                   int return_typename,
                                   Target.CallingConvention cc) {
        Scope.T zz;
        boolean[] new_ = new boolean[1];
        int id = M3ID.add(M3.ids, name);

        if (debug) {
            rtioPutText("ImportProc:");
            rtioPutRef(p);
            rtioPutText(" name:");
            rtioPutText(name);
            rtioPutText(":");
            rtioPutInt(id);
            rtioPutText(" return_typename:");
            rtioPutInt(return_typename);
            rtioPutText("\n");
            rtioFlush();
        }

        AssertError.check(p.cg_proc == null, "Procedure");
        p.next_cg_proc = cg_procs;
        cg_procs = p;
        p.cg_proc = CG.import_procedure(id, n_formals,
                                        cg_result, cc, new_, return_typeid, return_typename);
        if ((new_[0])) {
            // declare the formals
            if ((p.syms != null)) {
                zz = Scope.push(p.syms);
                Scope.enter(p.syms);
                Scope.pop(zz);
            } else {
                declareFormals(p, cc);
            }
        }
    }

    private static void declareFormals(T p, Target.CallingConvention cc) {
        Value.T v;
        Value.T formals = ProcType.formals(p.signature);
        Type.T result  = ProcType.result(p.signature);
        // declare types for each of the formals
        v = formals;
        while ((v != null)) {
            Formal.emitDeclaration(v, true, true);
            v = v.next;
        }

        // declare parameters for each of the formals
        if (cc.results_on_left) {
            declareResult(result);
        }
        v = formals;
        while ((v != null)) {
            Formal.emitDeclaration(v, false, true);
            v = v.next;
        }
        if (!cc.results_on_left) {
            declareResult(result);
        }
    }

    private static void declareResult(Type.T result) {
        long result_id;
        if ((result != null) && ProcType.largeResult(result)) {
            if ((returnName == M3ID.NoID)) {
                returnName = M3ID.add(M3.ids, "_return");
            }
            // Port: a type UID is a 32-bit CG.TypeUID.
            result_id = CG.declare_indirect((int) Type.globalUID(result));
            CG.declare_param(returnName, M3.target.Address.size,
                    M3.target.Address.align, CGType.Addr, (int) result_id,
                    /* in_memory := */ false, /* up_level := */ false, /* f := */ CG.Maybe);
        }
    }

    private static boolean declarer(T p) {
        Scope.T zz;
        M3CG.Proc parent = null;
        CGType cg_result;
        Type.T result = null;
        String name = Value.globalName(p, /* dots := */ false);
        long n_formals;
        Target.CallingConvention cconv;
        long result_typeid = 0;

        if (p.predefined && p.body == null && !p.assignable) {
            // Don't bother importing procedures that can't be assigned
            // or passed, or declaring their signatures, but do generate
            // a version stamp dependency
            return true;
        }

        if ((p.intf_peer != null)) {
            Type.compile(p.intf_peer.signature);
        }

        Type.compile(p.signature);
        result = ProcType.result(p.signature);
        Type.globalUID(p.signature);
        // try to compile the imported type first...

        cg_result = ProcType.cgResult(p.signature);
        if (cg_result != CGType.Void) {
            result_typeid = Type.globalUID(result);
        }
        n_formals = ProcType.nFormals(p.signature);
        cconv     = ProcType.callConv(p.signature);


        if (debug) {
            rtioPutText("Procedure.Declarer p:");
            rtioPutRef(p);
            rtioPutText(" name:");
            rtioPutText(name);
            rtioPutText(" p.signature:");
            rtioPutRef(p.signature);
            rtioPutText(" result:");
            rtioPutRef(result);
            rtioPutText("\n");
            rtioFlush();
        }

        if ((p.body == null)) {
            // it's not a local procedure
            if (p.impl_peer != null) {
                // it's an interface procedure that's implemented in this module
                Value.declare(p.impl_peer);
                return false;
            } else {
                // it's an imported procedure
                // Port: a type UID is a 32-bit CG.TypeUID.
                importProc(p, name, n_formals, cg_result,
                           (int) result_typeid,
                           ProcType.resultTypename(p.signature),
                           cconv);
                return true;
            }
        }

        if ((p.body.parent != null)) {
            parent = p.body.parent.cg_proc;
        }

        p.next_cg_proc = cg_procs;
        cg_procs = p;
        p.cg_proc = CG.declare_procedure(M3ID.add(M3.ids, name),
                        n_formals, cg_result, p.body.level, cconv,
                        /* exported := */ (p.exported || p.imported),
                        /* parent := */ parent,
                        /* return_typeid := */ (int) result_typeid,
                        /* return_typename := */ ProcType.resultTypename(p.signature));
        p.body.cg_proc = p.cg_proc;
        Scanner.offset = p.origin;
        if ((p.syms != null)) {
            zz = Scope.push(p.syms);
            Scope.enter(p.syms);
            Scope.pop(zz);
        }
        return true;
    }

    private static boolean needInit(T t) {
        return (t.body != null) && (t.body.level > 0);
    }

    private static void langInit(T t) {
        Body x = t.body;
        if ((x == null) || (x.level <= 0)) {
            // outer-level proc => don't do anything
        } else if ((Host.inline_nested_procs)) {
            genBody(t);
        } else {
            CG.note_procedure_origin(t.cg_proc);
        }
    }

    private static Expr.T toExpr(T t) {
        return ProcExpr.new_(t);
    }

    public static boolean isNested(Procedure.T t) {
        return (t != null) && (t.body != null) && (t.body.level != 0);
    }

    public static long staticLevel(Procedure.T t) {
        if ((t == null) || (t.body == null)) {
            return 0;
        } else {
            return t.body.level;
        }
    }

    /** return a back-end reference to the procedure 't'. */
    public static M3CG.Proc cgName(Procedure.T t) {
        if ((t.impl_peer != null)) {
            t = t.impl_peer;
        }
        t.used = true;
        Value.declare(t);
        return t.cg_proc;
    }

    private static void emitDecl(Body x) {
        Value.declare(x.self);
    }

    private static void emitBody(Body x) {
        T p = x.self;
        if ((x.level <= 0) || (!Host.inline_nested_procs)) {
            genBody(p);
        }
    }

    private static void genBody(T p) {
        Type.T tresult;
        EnumSet<Stmt.Outcome> oc;
        Scope.T zz;
        boolean fallThru;
        long[] l = new long[1];
        M3CG.Var[] frame = new M3CG.Var[1];
        Target.CallingConvention cconv;
        if ((Host.inline_nested_procs)
            && (p.body != null) && (p.body.level > 0)) {
            // make sure outer-level variable initializations are traced
            // in the outer procedure, before we enter the nested one.
            Tracer.emitPending();
        }

        Scanner.offset = p.origin;
        zz = Scope.push(p.syms);
        tresult = ProcType.result(p.signature);
        cconv   = ProcType.callConv(p.signature);

        CG.gen_location(p.origin);
        CG.begin_procedure(p.cg_proc);
        Scope.enter(p.syms);

        Marker.pushProcedure(tresult, p.result, cconv);
        Marker.allocReturnTemp();
        startRaises(p, l, frame);
        Scope.initValues(p.syms);
        Scanner.offset = BlockStmt.bodyOffset(p.block);
        Coverage.countProcedure(p);
        Jmpbufs.compileProcAllocateJmpbufs(p.jmpbufs);
        oc = Stmt.compile(p.block);
        fallThru = (oc.contains(Stmt.Outcome.FallThrough));
        endRaises(p, l[0], frame[0], fallThru);
        if ((fallThru)) {
            CG.gen_location(p.end_origin);
            if ((tresult == null)) {
                Marker.emitReturn(null, /* fromFinally := */ false);
            } else {
                Error.warnID(1, p.name, "function may not return a value");
                if (Host.doReturnChk) {
                    CG.abort(M3CG.RuntimeError.NoReturnValue);
                    oc = EnumSet.copyOf(oc);
                    oc.remove(Stmt.Outcome.FallThrough);
                }
            }
        }
        Marker.pop();
        Scope.exit(p.syms);

        CG.end_procedure(p.cg_proc);

        Scope.pop(zz);
    }

    /** Port: l and frame are VAR (OUT) parameters, one-element arrays. */
    private static void startRaises(T t, long[] l, M3CG.Var[] frame) {
        ESet.T raises;
        M3CG.Var[] eset = new M3CG.Var[1];
        long[] eoffset = new long[1];
        long frame_size;
        if ((!Host.doRaisesChk) || (!t.needs_raises)) {
            return;
        }
        raises = ProcType.raises(t.signature);
        if (ESet.raisesAny(raises)) {
            return;
        }

        if (M3.target.Has_stack_walker) {
            frame_size = 0;
        } else if (ESet.raisesNone(raises)) {
            frame_size = M3.rt.EF_SIZE;
        } else {
            frame_size = M3.rt.EF3_SIZE;
        }
        frame[0] = null;
        if (frame_size != 0) {
            frame[0] = CG.declare_local(M3ID.NoID, frame_size, M3.target.Address.align,
                                        CGType.Struct, 0, /* in_memory := */ true,
                                        /* up_level := */ false, /* f := */ CG.Never);
        }

        l[0] = CG.next_label(2);
        CG.set_label(l[0], /* barrier := */ true);
        Marker.pushRaises(l[0], l[0] + 1, raises, frame[0]);

        if (M3.target.Has_stack_walker) {
            Marker.saveFrame();
        } else if (ESet.raisesNone(raises)) {
            Marker.pushFrame(frame[0], M3RT.HandlerClass.RaisesNone);
        } else {
            ESet.getAddress(raises, eset, eoffset);
            CG.load_addr_of(eset[0], eoffset[0], M3.target.Address.align);
            CG.store_addr(frame[0], M3.rt.EF3_raises);
            Marker.pushFrame(frame[0], M3RT.HandlerClass.Raises);
        }
    }

    private static void endRaises(T t, long l, M3CG.Var frame, boolean fallThru) {
        ESet.T raises;
        if ((!Host.doRaisesChk) || (!t.needs_raises)) {
            return;
        }
        raises = ProcType.raises(t.signature);
        if (ESet.raisesAny(raises)) {
            return;
        }
        CG.set_label(l + 1, /* barrier := */ true);
        Marker.pop();
        if (M3.target.Has_stack_walker) {
            // nothing to do on exit
        } else if (fallThru) {
            Marker.popFrame(frame);
        }
    }

    /** generate code the start a procedure call */
    public static void startCall(Procedure.T t) {
        CGType result = ProcType.cgResult(t.signature);
        if ((t.impl_peer != null)) {
            t = t.impl_peer;
        }
        t.used = true;
        Value.declare(t);
        CG.start_call_direct(t.cg_proc, staticLevel(t), result);
    }

    /**
     * generate code to finish a procedure call and return the procedure's result
     * in a temporary.
     */
    public static CG.Val emitValueCall(Procedure.T t) {
        CGType result = ProcType.cgResult(t.signature);
        long[] handler = new long[1], handler_body = new long[1];
        M3CG.Var[] info = new M3CG.Var[1];
        if ((t.impl_peer != null)) {
            t = t.impl_peer;
        }

        if (Marker.nextHandler(handler, handler_body, info)) {
            CG.invoke_direct(t.cg_proc, result, handler[0]);
            Marker.invoked();
        } else {
            CG.call_direct(t.cg_proc, result);
        }
        return Marker.emitExceptionTest(t.signature, /* need_value := */ true);
    }

    /**
     * generate code to finish a procedure call and leave any return result
     * on the stack.
     */
    public static void emitCall(Procedure.T t) {
        CGType result = ProcType.cgResult(t.signature);
        long[] handler = new long[1], handler_body = new long[1];
        M3CG.Var[] info = new M3CG.Var[1];
        if ((t.impl_peer != null)) {
            t = t.impl_peer;
        }

        if (Marker.nextHandler(handler, handler_body, info)) {
            CG.invoke_direct(t.cg_proc, result, handler[0]);
            Marker.invoked();
        } else {
            CG.call_direct(t.cg_proc, result);
        }
        Marker.emitExceptionTest(t.signature, /* need_value := */ false);
    }

    private static void redefined(T t, Value.T other) {
        long save;
        save = Scanner.offset;
        if ((other == null)) {
            Scanner.offset = t.origin;
        } else {
            Scanner.offset = Math.min(t.origin, other.origin);
        }
        Error.ID(t.name, "procedure redefined");
        Scanner.offset = save;
    }

    public static Type.T signature(Procedure.T t) {
        if ((t == null)) {
            return null;
        }
        return t.signature;
    }

    public static boolean hasBody(Procedure.T t) {
        return (t != null) && (t.body != null);
    }

    private static long addFPTag(T t, M3.FPInfo x) {
        ValueRep.fpStart(t, x, "PROCEDURE ", 0, /* global := */ true);
        return 1;
    }

    private static Type.T fpType(T t) {
        if ((t.intf_peer == null)) {
            return t.signature;
        } else {
            return t.intf_peer.signature;
        }
    }
}
