// Java port of CM3's m3-sys/m3front/src/values/Exceptionz.i3 and Exceptionz.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3CG;
import m3j.middle.M3FP;
import m3j.middle.M3ID;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: Exceptionz.i3
// Last Modified On Tue Dec 20 14:57:54 PST 1994 By kalsow
//      Modified On Fri Feb 23 03:41:38 1990 By muller

// File: Exceptionz.m3
// Last Modified On Tue May 23 15:38:44 PDT 1995 by kalsow
//      Modified On Thu Dec  5 17:20:35 PST 1991 by muller

public final class Exceptionz {
    private Exceptionz() {}

    public static final class T extends Value.T {
        public Type.T tipe;
        public Type.T refTipe;
        public long offset;
        public long coffset;
        public long uid;
        public boolean implicit;
        /** Port: hides M3.ValueNode.next, as in the original. */
        public T next;

        @Override
        public void typeCheck(M3.CheckState cs) {
            check(this, cs);
        }

        @Override
        public void set_globals() {
            setGlobals(this);
        }

        @Override
        public void load() {
            Exceptionz.load(this);
        }

        @Override
        public boolean declare() {
            return declarer(this);
        }

        @Override
        public Type.T typeOf() {
            return ValueRep.typeVoid(this);
        }

        @Override
        public Type.T repTypeOf() {
            return ValueRep.typeVoid(this);
        }

        @Override
        public long add_fp_tag(M3.FPInfo x) {
            return addFPTag(this, x);
        }

        @Override
        public Type.T fp_type() {
            return fpType(this);
        }

        // Port: the overrides const_init := ValueRep.NoInit, need_init :=
        // ValueRep.Never, lang_init, user_init := ValueRep.NoInit, toExpr
        // := ValueRep.NoExpr, toType := ValueRep.NoType and base :=
        // ValueRep.Self restate the inherited methods.
    }

    public static final class Raiser extends ProcBody.T {
        public T self;
        public M3CG.Var arg;
        public M3CG.Var module;
        public M3CG.Var line;

        @Override
        public void gen_decl() {
            emitDecl(this);
        }

        @Override
        public void gen_body() {
            emitBody(this);
        }
    }

    // EXPORTED:
    public static void parseDecl(Decl.Attributes att) {
        // Port: TK = Token.T.
        T t;
        int id;
        Scanner.match(Token.T.tEXCEPTION);
        while ((Scanner.cur.token == Token.T.tIDENT)) {
            id = Scanner.matchID();
            t = new T();
            ValueRep.init(t, id, Value.Class.Exception);
            t.readonly = true;
            t.unused   = att.isUnused;
            t.obsolete = att.isObsolete;
            t.tipe     = null;
            t.refTipe  = null;
            t.offset   = 0;
            t.coffset  = 0;
            t.uid      = 0;
            t.implicit = att.isImplicit;
            if ((Scanner.cur.token == Token.T.tLPAREN)) {
                Scanner.getToken(); // (
                t.tipe = Type.parse();
                Scanner.match(Token.T.tRPAREN);
            }
            Scope.insert(t);
            Scanner.match(Token.T.tSEMI);
        }
    }

    // EXPORTED:
    public static void emitRaise(Value.T v, Expr.T arg) {
        T t = (T) Value.base(v);
        Type.T expr_type;
        CGType arg_type;
        Type.Info info = new Type.Info();
        Procedure.T p = RunTyme.lookUpProc(RunTyme.Hook.RaiseEx);
        CG.Val tmp;
        Value.declare(t);
        AssertError.check(t.offset != 0, "Exceptionz");

        if ((arg == null)) {
            Procedure.startCall(p);
            if (M3.target.DefaultCall.args_left_to_right) {
                loadSelf(t);
                CG.pop_param(CGType.Addr);
                CG.load_nil();
                CG.pop_param(CGType.Addr);
                passLocation();
            } else {
                passLocation();
                CG.load_nil();
                CG.pop_param(CGType.Addr);
                loadSelf(t);
                CG.pop_param(CGType.Addr);
            }
            Procedure.emitCall(p);
        } else if ((t.refTipe == null)) {
            expr_type = Expr.typeOf(arg);
            expr_type = Type.checkInfo(expr_type, info);
            arg_type = info.stk_type;
            Expr.prep(arg);
            AssignStmt.emitRTCheck(t.tipe, arg);
            tmp = CG.pop();
            Procedure.startCall(p);
            if (M3.target.DefaultCall.args_left_to_right) {
                loadSelf(t);
                CG.pop_param(CGType.Addr);
                CG.push(tmp);
                if ((arg_type != CGType.Addr)) {
                    CG.loophole(arg_type, CGType.Addr);
                }
                CG.pop_param(CGType.Addr);
                passLocation();
            } else {
                passLocation();
                CG.push(tmp);
                if ((arg_type != CGType.Addr)) {
                    CG.loophole(arg_type, CGType.Addr);
                }
                CG.pop_param(CGType.Addr);
                loadSelf(t);
                CG.pop_param(CGType.Addr);
            }
            Procedure.emitCall(p);
            CG.free(tmp);
        } else { // large argument => call the raise procedure
            expr_type = Expr.typeOf(arg);
            expr_type = Type.checkInfo(expr_type, info);
            arg_type = info.stk_type;
            Expr.prep(arg);
            AssignStmt.emitRTCheck(t.tipe, arg);
            tmp = CG.pop();
            CG.start_call_indirect(CGType.Void, M3.target.DefaultCall);
            if (M3.target.DefaultCall.args_left_to_right) {
                CG.push(tmp);
                if ((arg_type != CGType.Addr)) {
                    Formal.genCopy(expr_type);
                }
                CG.pop_param(CGType.Addr);
                passLocation();
            } else {
                passLocation();
                CG.push(tmp);
                if ((arg_type != CGType.Addr)) {
                    Formal.genCopy(expr_type);
                }
                CG.pop_param(CGType.Addr);
            }
            loadSelf(t);
            CG.add_offset(M3.rt.ED_SIZE);
            CG.boost_addr_alignment(M3.target.Address.align);
            CG.load_indirect(CGType.Addr, 0, M3.target.Address.size, CG.procAlign());
            CG.gen_Call_indirect(CGType.Void, M3.target.DefaultCall);
            Marker.emitExceptionTest(Procedure.signature(p), /* need_value := */ false);
            CG.free(tmp);
        }
    }

    private static void loadSelf(T t) {
        if ((t.imported)) {
            Module.loadGlobalAddr((Module.T) Scope.toUnit(t), t.offset, /* is_const := */ false);
            CG.load_indirect(CGType.Addr, 0, M3.target.Address.size);
            CG.boost_addr_alignment(M3.target.Address.align);
        } else {
            Module.loadGlobalAddr((Module.T) Scope.toUnit(t), t.coffset, /* is_const := */ true);
        }
    }

    /** push the "module, linenumber" parameters */
    private static void passLocation() {
        M3CG.Var globals = Module.globalData(/* is_const := */ false);
        String[] this_file = new String[1];
        long[] this_line = new long[1];
        Scanner.here(this_file, this_line);
        if (M3.target.DefaultCall.args_left_to_right) {
            CG.load_addr_of(globals, 0, CG.Max_alignment);
            CG.pop_param(CGType.Addr);
            CG.load_intt(this_line[0]);
            CG.pop_param(M3.target.Integer.cg_type);
        } else {
            CG.load_intt(this_line[0]);
            CG.pop_param(M3.target.Integer.cg_type);
            CG.load_addr_of(globals, 0, CG.Max_alignment);
            CG.pop_param(CGType.Addr);
        }
    }

    // EXPORTED:
    public static boolean argByReference(Type.T type) {
        Type.Info info = new Type.Info();
        Type.checkInfo(type, info);
        return (info.size > M3.target.Address.size) || Type.isStructured(type);
    }

    // Externally dispatched-to:
    private static void check(T t, M3.CheckState cs) {
        Type.Info info = new Type.Info();
        if ((t.tipe != null)) {
            t.tipe = Type.checkInfo(t.tipe, info);
            if ((info.size < 0)) {
                Error.ID(t.name, "argument type must have fixed length");
            }
            if (argByReference(t.tipe)) {
                t.refTipe = RefType.new_(Type.stripPacked(t.tipe), true, null);
                // FIXME: Most uses of t.tipe need to be StripPacked.
                t.refTipe = Type.check(t.refTipe);
            }
        }
    }

    // EXPORTED:
    public static Type.T argType(Value.T v) {
        Value.T b = Value.base(v);
        if (b == null) {
            return null;
        } else if (b instanceof T t) {
            return t.tipe;
        } else {
            return null;
        }
    }

    // EXPORTED:
    public static long UID(Value.T v) {
        Value.T b = Value.base(v);
        if (b == null) {
            return 0;
        } else if (b instanceof T t) {
            AssertError.check(t.uid != 0, "Exceptionz");
            return t.uid;
        } else {
            return 0;
        }
    }

    // EXPORTED:
    public static boolean isImplicit(Value.T v) {
        Value.T b = Value.base(v);
        if (b == null) {
            return false;
        } else if (b instanceof T t) {
            return t.implicit;
        } else {
            return false;
        }
    }

    // Externally dispatched-to:
    private static void load(T t) {
        Value.declare(t);
        loadSelf(t);
    }

    // Externally dispatched-to:
    private static void setGlobals(T t) {
        String name;
        long size;
        // Type.SetGlobals (t.tipe);
        // Type.SetGlobals (t.refTipe);
        if ((t.offset != 0) || (t.external)) {
            return;
        }

        name = Value.globalName(t);
        size = M3.rt.ED_SIZE;
        if ((t.refTipe != null)) {
            size += M3.target.Address.size;
        }
        size += (name.length() + 1) * Target.Char.size;
        t.offset = Module.allocate(M3.target.Address.size, M3.target.Address.align,
                                   false, name);
        t.coffset = Module.allocate(size, M3.target.Address.align, true, name);
        t.uid = M3FP.toInt(M3FP.fromText(name));
    }

    // Externally dispatched-to:
    private static boolean declarer(T t) {
        String name;
        M3CG.Var globals, consts;
        int arg_type = 0;
        long size;
        M3CG.Proc proc = null;
        Type.compile(t.tipe);
        Type.compile(t.refTipe);
        if ((t.tipe != null)) {
            // Port: a type UID is a 32-bit CG.TypeUID.
            arg_type = (int) Type.globalUID(t.tipe);
        }

        if (t.external || t.imported) {
            // don't bother to force the import
        } else { // locally declared
            setGlobals(t);
            name = Value.globalName(t);
            globals = Module.globalData(/* is_const := */ false);
            consts  = Module.globalData(/* is_const := */ true);
            size = M3.rt.ED_SIZE;
            if ((t.refTipe != null)) {
                size += M3.target.Address.pack;
                proc = declareRaiseProc(t);
            }
            CG.declare_exception(t.name, arg_type, (t.refTipe != null), consts, t.coffset);
            CG.declare_global_field(t.name, t.offset, M3.target.Address.size, 0, false);
            CG.declare_global_field(t.name, t.coffset, size, 0, true);
            CG.init_var(t.offset, consts, t.coffset, false);
            CG.init_intt(t.coffset + M3.rt.ED_uid, M3.target.Integer.size, t.uid, true);
            CG.init_var(t.coffset + M3.rt.ED_name, consts, t.coffset + size, true);
            CG.init_intt(t.coffset + M3.rt.ED_implicit, M3.target.Integer.size,
                         t.implicit ? 1 : 0, true);
            if ((t.refTipe != null)) {
                CG.init_proc(t.coffset + M3.rt.ED_SIZE, proc, true);
            }
            CG.init_chars(t.coffset + size, name, true);
        }
        return true;
    }

    private static M3CG.Proc declareRaiseProc(T t) {
        Raiser r = new Raiser();
        r.self = t;
        r.name    = Value.globalName(t, /* dots := */ false);
        r.name    = r.name + "_RAISE";
        r.cg_proc = CG.declare_procedure(M3ID.add(M3.ids, r.name), 3, CGType.Void,
                        /* lev := */ 0, /* cc := */ M3.target.DefaultCall, /* exported := */ false,
                        /* parent := */ null);
        // Port: a type UID is a 32-bit CG.TypeUID.
        r.arg     = CG.declare_param(M3ID.NoID, M3.target.Address.size,
                        M3.target.Address.align, CGType.Addr,
                        CG.declare_indirect((int) Type.globalUID(t.tipe)),
                        /* in_memory := */ false, /* up_level := */ false, /* f := */ CG.Always);
        r.module  = CG.declare_param(M3ID.NoID, M3.target.Address.size,
                        M3.target.Address.align, CGType.Addr,
                        (int) Type.globalUID(Addr.T), /* in_memory := */ false,
                        /* up_level := */ false, /* f := */ CG.Always);
        r.line    = CG.declare_param(M3ID.NoID, M3.target.Integer.size,
                        M3.target.Integer.align, M3.target.Integer.cg_type,
                        (int) Type.globalUID(Int.T), /* in_memory := */ false,
                        /* up_level := */ false, /* f := */ CG.Always);
        ProcBody.schedule(r);
        return r.cg_proc;
    }

    // Externally dispatched-to:
    private static void emitDecl(Raiser x) {
    }

    // Externally dispatched-to:
    private static void emitBody(Raiser x) {
        T t = x.self;
        CG.Val ptr;
        long align;
        Procedure.T proc;
        Type.Info info = new Type.Info();
        CG.gen_location(t.origin);
        CG.begin_procedure(x.cg_proc);
        Type.checkInfo(t.tipe, info);
        align = info.alignment;

        // ptr := NEW (REF t.type)
        proc = RunTyme.lookUpProc(RunTyme.Hook.NewTracedRef);
        Procedure.startCall(proc);
        Type.loadInfo(t.refTipe, -1);
        CG.pop_param(CGType.Addr);
        ptr = Procedure.emitValueCall(proc);

        // ptr^ := arg^
        CG.push(ptr);
        CG.boost_addr_alignment(align);
        CG.load_addr(x.arg, 0, align);
        CG.copy(info.size, /* overlap := */ false);

        // RAISE (e, ptr)
        proc = RunTyme.lookUpProc(RunTyme.Hook.RaiseEx);
        Procedure.startCall(proc);
        if (M3.target.DefaultCall.args_left_to_right) {
            loadSelf(t);
            CG.pop_param(CGType.Addr);
            CG.push(ptr);
            CG.pop_param(CGType.Addr);
            CG.load_addr(x.module, 0, M3.target.Address.align);
            CG.pop_param(CGType.Addr);
            CG.load_int(M3.target.Integer.cg_type, x.line);
            CG.pop_param(M3.target.Integer.cg_type);
        } else {
            CG.load_int(M3.target.Integer.cg_type, x.line);
            CG.pop_param(M3.target.Integer.cg_type);
            CG.load_addr(x.module, 0, M3.target.Address.align);
            CG.pop_param(CGType.Addr);
            CG.push(ptr);
            CG.pop_param(CGType.Addr);
            loadSelf(t);
            CG.pop_param(CGType.Addr);
        }
        Procedure.emitCall(proc);

        CG.free(ptr);
        CG.end_procedure(x.cg_proc);
    }

    /** called for RAISES sets, doesn't include the interface record offset */
    // EXPORTED:
    public static long addFPSetTag(Value.T tt, M3.FPInfo x) {
        T t = (T) Value.base(tt);
        M3Buf.putChar(x.buf, '<');
        ValueRep.fpStart(t, x, "EXCEPT ", 0, /* global := */ true);
        M3Buf.putChar(x.buf, '>');
        return (t.tipe != null) ? 1 : 0;
    }

    // Externally dispatched-to:
    private static long addFPTag(T t, M3.FPInfo x) {
        long offset = Math.floorDiv(t.offset, M3.target.Address.align);
        ValueRep.fpStart(t, x, "EXCEPT ", offset, /* global := */ true);
        return (t.tipe != null) ? 1 : 0;
    }

    private static Type.T fpType(T t) {
        return t.tipe;
    }
}
