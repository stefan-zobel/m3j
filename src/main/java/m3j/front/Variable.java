// Java port of CM3's m3-sys/m3front/src/values/Variable.i3 and Variable.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;
import m3j.util.RangeError;

// File: Variable.i3
// Last Modified On Tue Dec 20 15:08:50 PST 1994 By kalsow
//      Modified On Fri Apr 27 03:11:00 1990 By muller

// File: Variable.m3
// Last Modified On Tue Jun 20 09:58:08 PDT 1995 By kalsow
//      Modified On Thu Jun 15 12:45:02 PDT 1995 By ericv
//      Modified On Thu Dec  5 17:21:40 PST 1991 By muller

/** Including formal parameters. */
public final class Variable {
    private Variable() {}

    // Port: RTParams.IsPresent ("m3front-debug-variable") is always FALSE.
    private static boolean debug = false;

    /** x Target.Char.size */
    private static final long Big_Local = 8192;
    /** x Target.Integer.size */
    private static final long Big_Param = 8;
    /** x Target.Integer.size */
    private static final long Max_zero_global = 64;

    public static class T extends Value.T {
        /** only written once, not lowered, to retain NamedType */
        public Type.T type = null;
        public Type.T repType = null;
        public Expr.T initExpr = null;
        public String qualName = null;
        public T sibling = null;
        /**
         * This Variable.T represents a formal parameter, but that's a
         * Formal.T, a different and hidden proper subtype of Value.T.
         * Field formal is actually the Formal.T object.
         * A great example of TMIH (Too Much Information Hiding).
         */
        public Value.T formal = null;
        public T alias = null;
        public Tracer.T trace = null;
        public BoundPair bounds = null;
        /** Used if it's a local, formal, or external. */
        public M3CG.Var cg_var = null;
        /** Used if it's a global. */
        public M3CG.Var bss_var = null;
        /**
         * Link field for list of Variable.Ts that have a
         * non-NIL bss_var or cg_var.
         */
        public T nextTWACGVar;
        public long initValOffset = 0;
        public long offset = 0;
        public long size = 0;
        /** Port: AlignVal = [0..255], range-checked by alignVal. */
        public long align = 0;
        public long cg_align = 0;
        // 4 bits suffices, 8 bits provides endian neutral output of C++ backend
        public CGType mem_type = CGType.values()[0];
        public CGType stk_type = CGType.values()[0];
        public boolean indirect = false;
        /**
         * Not in CM3: for a WITH name bound to a packed field or element
         * that need not start at a byte, its offset in bits from the
         * address the name holds (indirect); else 0.
         */
        public long bitBias = 0;
        /** Not in CM3: the alignment of the address of bitBias. */
        public long biasAlign = Target.Byte;
        public boolean open_ok = false;
        public boolean need_addr = false;
        /** Type not explicitly coded. */
        public boolean no_type = false;
        /** Declared in outermost scope. */
        public boolean global = false;
        public boolean initDone = false;
        /** Initial value is all binary zeros. */
        public boolean initZero = false;
        /** Initialization is postponed. */
        public boolean initPending = false;
        /**
         * Needs RT initialization to a value
         * from the static constant area.
         */
        public boolean initStatic = false;
        /** Has allocated space in the global variable area. */
        public boolean allocated = false;
        /** Static initial value has allocated space in the global constant area. */
        public boolean initAllocated = false;

        @Override
        public void typeCheck(M3.CheckState cs) {
            check(this, cs);
        }

        @Override
        public void set_globals() {
            allocGlobalVarSpace(this);
        }

        @Override
        public void load() {
            Variable.load(this);
        }

        @Override
        public boolean declare() {
            return Variable.declare(this);
        }

        @Override
        public void const_init() {
            constInit(this);
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
        public void user_init() {
            userInit(this);
        }

        @Override
        public Type.T typeOf() {
            return Variable.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return Variable.repTypeOf(this);
        }

        @Override
        public long add_fp_tag(M3.FPInfo x) {
            return addFPTag(this, x);
        }

        @Override
        public Type.T fp_type() {
            return Variable.typeOf(this);
        }

        // Port: the overrides toExpr := ValueRep.NoExpr, toType :=
        // ValueRep.NoType and base := ValueRep.Self restate the inherited
        // methods.
    }

    /** Port: the assignment to AlignVal = [0..255], with its range check. */
    private static long alignVal(long x) {
        if (x < 0 || x > 255) {
            throw new RangeError("Variable");
        }
        return x;
    }

    public static final class BoundPair {
        public int[] min = TInt.newInt();
        public int[] max = TInt.newInt();
    }

    /** Linked list of Variable.Ts that have a non-NIL bss_var or cg_var. */
    private static T TsWCGVars = null;

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

    /** Toss as garbage, any CG.Var nodes that we've created */
    // EXPORTED
    public static void reset() {
        T t, u;
        t = TsWCGVars;
        while ((t != null)) {
            u = t;
            t = t.nextTWACGVar;
            u.cg_var      = null;
            u.bss_var     = null;
            u.nextTWACGVar = null;
        }
        TsWCGVars = null;
    }

    // EXPORTED
    public static void parseDecl(Decl.Attributes att) {
        // Port: TK = Token.T.
        T t;
        Type.T type;
        Expr.T expr;
        long j, n;
        Tracer.T trace;
        int alias;
        Scanner.match(Token.T.tVAR);
        while ((Scanner.cur.token == Token.T.tIDENT)) {
            n = Ident.parseList();
            type = null;
            expr = null;
            if ((Scanner.cur.token == Token.T.tCOLON)) {
                Scanner.getToken(); // :
                type = Type.parse();
            }
            if ((Scanner.cur.token == Token.T.tEQUAL)) {
                Error.msg("Variable initialization must begin with ':='.");
                Scanner.cur.token = Token.T.tASSIGN;
            }
            if ((Scanner.cur.token == Token.T.tASSIGN)) {
                Scanner.getToken(); // :=
                expr = Expr.parse();
            }
            trace = parseTrace();
            if ((expr == null) && (type == null)) {
                Error.msg("Variable declaration must include a type or initial value.");
            }
            if (att.isExternal && att.alias != M3ID.NoID && n > 1) {
                Error.warnID(2, att.alias,
                             "EXTERNAL alias applies only to the first variable.");
            }
            alias = att.alias;
            j = Ident.top - n;
            for (long i = 0; i <= n - 1; i++) {
                t = new_(Ident.stack[(int) (j + i)], false);
                t.origin   = Ident.offset[(int) (j + i)];
                t.external = att.isExternal;
                t.javaBound = att.isExternal && (att.isJava || Module.isJava());
                t.unused   = att.isUnused;
                t.obsolete = att.isObsolete;
                t.type     = type;
                t.repType  = null;
                t.initExpr = expr;
                t.no_type  = (type == null);
                if ((att.isExternal)) {
                    if ((alias != M3ID.NoID)) {
                        t.extName = alias;
                        alias = M3ID.NoID;
                    } else {
                        t.extName = t.name;
                    }
                }
                Scope.insert(t);
                bindTrace(t, trace);
            }
            Ident.top -= n;
            Scanner.match(Token.T.tSEMI);
        }
    }

    /** doesn't insert the object into any scope.... */
    // EXPORTED
    public static Variable.T new_(int name, boolean used) {
        T t = new T();
        ValueRep.init(t, name, Value.Class.Var);
        t.used        = used;
        t.readonly    = false;
        t.extName     = M3ID.NoID;
        t.mem_type    = CGType.Void;
        t.stk_type    = CGType.Void;
        return t;
    }

    // EXPORTED
    public static Variable.T newFormal(Value.T formal, int name) {
        T t = new_(name, false);
        Formal.Info f_info = new Formal.Info();
        Formal.split(formal, f_info);
        t.formal   = formal;
        t.type     = f_info.type;
        t.origin   = formal.origin;
        t.indirect = (f_info.mode != Formal.Mode.mVALUE);
        t.readonly = (f_info.mode == Formal.Mode.mREADONLY);
        t.unused   = f_info.unused;
        t.initDone = true;
        // REVIEW^ can this be right?
        t.imported = false; // in spite of Module.depth
        if ((!t.indirect) && (OpenArrayType.is(t.type))) {
            t.indirect = true;
        }

        if (debug) {
            rtioPutText("NewFormal type:");
            rtioPutRef(t.type);
            rtioPutText(" name:");
            if (name != 0) {
                rtioPutText(M3ID.toText(M3.ids, name));
            }
            rtioPutText("\n");
            rtioFlush();
        }

        t.trace = null; // the caller must call BindTrace after the variable
                        // is inserted into a scope
        return t;
    }

    /**
     * Port: type, global, indirect and lhs (traced in the module) are VAR
     * parameters, one-element arrays.
     */
    // EXPORTED
    public static void split(Variable.T t, Type.T[] type, boolean[] global, boolean[] indirect, boolean[] lhs) {
        AssertError.check(t.checked, "Variable");
        type[0]     = t.type;
        global[0]   = t.global;
        indirect[0] = t.indirect;
        lhs[0]      = t.traced;
    }

    /** This gets called at parse time, so can't do any Check. */
    // EXPORTED
    public static void bindType(Variable.T t, Type.T type, boolean indirect, boolean readonly, boolean open_array_ok, boolean needs_init) {
        AssertError.check(t.type == null, "Variable");
        t.type     = type;
        t.repType  = null;
        t.readonly = readonly;
        t.indirect = indirect;
        t.open_ok  = open_array_ok;
        if (!needs_init) {
            t.initDone = true;
        }
    }

    // EXPORTED
    public static void needsAddress(Variable.T t) {
        if ((t == null)) {
            return;
        }
        t.need_addr = true;
    }

    // EXPORTED
    public static boolean isFormal(Variable.T t) {
        return (t != null) && (t.formal != null);
    }

    // EXPORTED
    public static boolean hasClosure(Variable.T t) {
        return (t != null) && (t.formal != null) && Formal.hasClosure(t.formal);
    }

    // Externally dispatched-to
    private static Type.T typeOf(T t) {
        if ((t.type == null)) {
            if (t.initExpr != null) {
                t.type = Expr.typeOf(t.initExpr);
            } else if (t.formal != null) {
                t.type = Value.typeOf(t.formal);
            }
            if ((t.type == null)) {
                Error.ID(t.name, "Variable has no type.");
                t.type = ErrType.T;
            }
        }
        return t.type;
    }

    // Externally dispatched-to
    private static Type.T repTypeOf(T t) {
        Type.T semType;
        if (t.repType == null) {
            semType = typeOf(t);
            t.repType = Type.stripPacked(semType);
        }
        return t.repType;
    }

    // Externally dispatched-to
    private static void check(T t, M3.CheckState cs) {
        Expr.T dfault;
        int[] min = TInt.newInt(), max = TInt.newInt();
        Type.Info info = new Type.Info();
        Type.T[] refType = new Type.T[1];
        Type.T type = null;
        type = Type.checkInfo(typeOf(t), info);
        t.repType  = Type.check(Type.stripPacked(type));
        t.size     = info.size;
        t.align    = alignVal(info.alignment);
        t.mem_type = info.mem_type;
        t.stk_type = info.stk_type;
        if ((info.class_ == Type.Class.OpenArray)
            && (t.formal == null) && (!t.open_ok)) {
            Error.ID(t.name, "Variable cannot have an open array type.");
        }
        if ((info.isEmpty)) {
            Error.ID(t.name, "Variable cannot have empty type.");
            // CHECK: Is this always only secondary to some other error.
        }
        if (type == Null.T) {
            Error.warnID(1, t.name, "Variable cannot have type NULL.");
        }

        t.global = Scope.outerMost(t.scope);
        t.checked = true; // Allow recursions through initExpr.

        if ((!t.indirect) && (!t.global)) {
            if ((t.formal != null) && (info.size > Big_Param * M3.target.Integer.size)) {
                Error.warnID(1, t.name, "Large parameter passed by value ("
                             + Long.toString(Math.floorDiv(info.size, Target.Char.size)) + " bytes).");
            } else if ((info.size > Big_Local * Target.Char.size)) {
                Error.warnID(1, t.name, "Large local variable ("
                             + Long.toString(Math.floorDiv(info.size, Target.Char.size)) + " bytes).");
            }
        } else if ((t.formal != null) && (info.class_ == Type.Class.OpenArray)
                   && Formal.openArrayByVALUE(t.formal, /*VAR*/ refType)) {
            Error.warnID(1, t.name, "Open array passed by value.");
        }

        if (Type.isStructured(type)) {
            t.need_addr = true; // every load requires an address
        }

        Value.typeCheck(t.formal, cs);
        if ((t.external)) {
            if ((t.initExpr != null)) {
                Error.msg("<*EXTERNAL*> variables cannot be initialized.");
                Expr.typeCheck(t.initExpr, cs);
                AssignStmt.check(type, t.initExpr, cs);
            }
        } else if ((t.initExpr != null)) {
            Expr.typeCheck(t.initExpr, cs);
            AssignStmt.check(type, t.initExpr, cs);
            // TODO: What if initExpr contains RT errors?
            dfault = Expr.constValue(t.initExpr);
            if ((dfault == null)) {
                if (Module.isInterface()) {
                    Error.ID(t.name, "Initial value in an interface must be constant.");
                }
                if ((t.global) && (info.size > Max_zero_global * M3.target.Integer.size)) {
                    AssertError.check(!t.indirect, "Variable");
                    t.indirect = true;
                }
            } else { // initialize the variable to an explicit constant
                if (!t.indirect) {
                    t.initZero = Expr.isZeroes(dfault);
                    if ((t.global)) {
                        if ((t.initZero)) {
                            t.initDone = true;
                            if ((info.size > Max_zero_global * M3.target.Integer.size)) {
                                AssertError.check(!t.indirect, "Variable");
                                t.indirect = true;
                            }
                        }
                    } else if ((!t.initZero) && Type.isStructured(type)) {
                        t.initStatic = true;
                    }
                    t.initExpr = dfault;
                }
            }
        } else if ((t.global)) {
            // no explict initialization is given, but the var is global
            if (Type.initCost(type, true) <= 0) {
                if ((info.size > Max_zero_global * M3.target.Integer.size)) {
                    AssertError.check(!t.indirect, "Variable");
                    t.indirect = true;
                }
                t.initDone = true;
            } else if (Type.getBounds(type, min, max)) {
                // synthesize an initialization expression
                if (Type.isSubtype(type, LInt.T)) {
                    t.initExpr = IntegerExpr.new_(LInt.T, min);
                } else {
                    t.initExpr = IntegerExpr.new_(Int.T, min);
                }
            }
        }

        t.qualName = Value.globalName(t);
        checkTrace(t.trace, cs);
    }

    // EXPORTED
    // Externally dispatched-to
    public static void load(Variable.T t) {
        Type.Info type_info = new Type.Info();
        t.used = true;
        Value.declare(t);
        if ((t.initPending)) {
            forceInit(t);
        }
        if (isPackedAlias(t)) {
            loadBiasedAddr(t);
            Type.loadScalar(t.repType);
            return;
        }
        if (Type.isStructured(t.type)) {
            // The runtime representation is an address
            if ((t.bss_var != null)) {
                CG.load_addr_of(t.bss_var, 0, t.cg_align);
            } else if ((t.cg_var == null)) { // => global
                Module.loadGlobalAddr((Module.T) Scope.toUnit(t), t.offset, /* is_const := */ false);
                CG.boost_addr_alignment(t.cg_align);
            } else if ((t.indirect)) {
                CG.load_addr(t.cg_var, t.offset, t.cg_align);
                // ^Misleading name.  Actually load value and label as an address.
            } else {
                CG.load_addr_of(t.cg_var, t.offset, CG.GCD(t.cg_align, t.offset));
            }
        } else { // simple scalar
            Type.checkInfo(t.type, type_info);
            if ((t.bss_var != null)) {
                CG.load(t.bss_var, 0, t.size, t.cg_align, type_info.alignment, t.stk_type);
            } else if ((t.cg_var == null)) { // => global
                Module.loadGlobalAddr((Module.T) Scope.toUnit(t), t.offset, /* is_const := */ false);
                if ((t.indirect)) {
                    CG.load_indirect(CGType.Addr, 0, M3.target.Address.size);
                }
                CG.boost_addr_alignment(type_info.alignment);
                CG.load_indirect(t.stk_type, 0, t.size, type_info.addr_align);
            } else if ((t.indirect)) {
                CG.load_addr(t.cg_var, t.offset, type_info.alignment);
                // ^Misleading name.  Actually load value and label as an address.
                // CG.Load_indirect (t.stk_type, 0, t.size, type_info.addr_align);
                CG.load_indirect(type_info.stk_type, 0, type_info.size, type_info.addr_align);
            } else {
                CG.load(t.cg_var, t.offset, t.size, CG.GCD(t.cg_align, t.offset),
                        type_info.addr_align, t.stk_type);
            }
        }
    }

    // EXPORTED
    public static void loadLValue(Variable.T t) {
        Type.Info type_info = new Type.Info();
        t.used = true;
        Value.declare(t);
        if ((t.initPending)) {
            forceInit(t);
        }
        Type.checkInfo(t.type, type_info);
        if (isPackedAlias(t)) {
            loadBiasedAddr(t);
            return;
        }
        if ((t.bss_var != null)) {
            CG.load_addr_of(t.bss_var, 0, type_info.alignment);
        } else if ((t.cg_var == null)) { // => global variable
            Module.loadGlobalAddr((Module.T) Scope.toUnit(t), t.offset, /* is_const := */ false);
            if ((t.indirect)) {
                CG.load_indirect(CGType.Addr, 0, M3.target.Address.size);
            }
        } else if ((t.indirect)) {
            CG.load_addr(t.cg_var, t.offset, type_info.alignment);
            // ^Misleading name.  Actually load value and label as an address.
        } else {
            CG.load_addr_of(t.cg_var, t.offset, CG.GCD(type_info.alignment, t.offset));
        }
        CG.boost_addr_alignment(t.cg_align);
    }

    /**
     * Not in CM3: t is a WITH name bound to a packed field or element that
     * need not start at a byte; its representation is the packed type of
     * the field or element, repType.  Called after the check of t's scope, which sets
     * repType to the type without packing.
     */
    public static void bindPacked(Variable.T t, Type.T repType) {
        AssertError.check(t.indirect, "Variable");
        t.repType = Type.check(repType);
    }

    /** Not in CM3: t was bound by bindPacked. */
    public static boolean isPackedAlias(Variable.T t) {
        return t.indirect && t.repType != null && PackedType.is(t.repType);
    }

    /**
     * Not in CM3: push the address of the field a WITH name bound by
     * bindPacked is bound to: the address it holds plus its bit bias.
     */
    private static void loadBiasedAddr(Variable.T t) {
        AssertError.check(t.indirect, "Variable");
        if ((t.cg_var == null)) { // => global variable
            Module.loadGlobalAddr((Module.T) Scope.toUnit(t), t.offset, /* is_const := */ false);
            CG.load_indirect(CGType.Addr, 0, M3.target.Address.size);
            CG.boost_addr_alignment(t.biasAlign);
        } else {
            CG.load_addr(t.cg_var, t.offset, t.biasAlign);
            // ^Misleading name.  Actually load value and label as an address.
        }
        CG.add_offset(t.bitBias);
    }

    // EXPORTED
    public static void setLValue(Variable.T t) {
        M3CG.Var v;
        long align;
        t.used = true;
        Value.declare(t);
        if ((t.initPending)) {
            t.initPending = false;
        }
        v = t.cg_var;
        align = t.cg_align;
        if ((v == null)) {
            v = Module.globalData(/* is_const := */ false);
            align = CG.Max_alignment;
        }
        AssertError.check(t.indirect, "Variable");
        if (!isPackedAlias(t)) { // Not as in CM3: else see loadBiasedAddr.
            CG.boost_addr_alignment(t.cg_align);
        }
        CG.store_addr(v, t.offset);
    }

    /**
     * return the back-end address of the non-imported variable 't'.
     *
     * <p>Port: unit and offset are VAR parameters, one-element arrays.
     */
    // EXPORTED
    public static void localCGName(Variable.T t, M3CG.Var[] unit, long[] offset) {
        t.used = true;
        Value.declare(t);
        if ((t.initPending)) {
            forceInit(t);
        }
        AssertError.check(!t.imported, "Variable");
        if ((t.cg_var == null)) {
            unit[0] = Module.globalData(false);
            offset[0] = t.offset;
        } else {
            unit[0] = t.cg_var;
            offset[0] = 0;
        }
    }

    // EXPORTED
    public static void setBounds(Variable.T t, int[] min, int[] max) {
        if ((t.bounds == null)) {
            t.bounds = new BoundPair();
        }
        TInt.assign(t.bounds.min, min);
        TInt.assign(t.bounds.max, max);
    }

    // EXPORTED
    public static void getBounds(Variable.T t, int[] min, int[] max) {
        BoundPair xx = t.bounds;
        Type.getBounds(t.type, min, max);
        if ((xx == null)) {
            return;
        }
        if (TInt.LT(min, xx.min)) {
            TInt.assign(min, xx.min);
        }
        if (TInt.LT(xx.max, max)) {
            TInt.assign(max, xx.max);
        }
    }

    /** Allocate space for a non-external global. */
    // Externally dispatched-to
    private static void allocGlobalVarSpace(T t) {
        long size, align;
        Expr.T constInitExpr;
        Type.T initRepType = null;
        int varID;
        // Type.SetGlobals (t.type);
        // IF (t.initExpr # NIL) THEN Type.SetGlobals (Expr.TypeOf (t.initExpr)) END;
        if (t.allocated /* Already done.*/ || !t.global || t.external) {
            return;
        }
        Type.check(t.type);

        if (t.initExpr != null) {
            constInitExpr = Expr.constValue(t.initExpr);
            ArrayExpr.noteTargetType(constInitExpr, t.type);
            initRepType = Expr.repTypeOf(t.initExpr);
        }

        if ((t.indirect)) {
            size  = M3.target.Address.size;
            align = M3.target.Address.align;
            varID = M3ID.add(M3.ids, t.qualName + "_INDIRECT_");
        } else if (OpenArrayType.is(initRepType)) {
            size  = M3.target.Address.size
                    + OpenArrayType.openDepth(initRepType) * M3.target.Integer.size;
            align = Math.max(M3.target.Address.align, M3.target.Integer.align);
            varID = M3ID.add(M3.ids, t.qualName + "_DOPE_");
        } else {
            size  = t.size;
            align = t.align;
            varID = M3ID.add(M3.ids, t.qualName);
        }

        // declare the actual variable
        t.offset = Module.allocate(size, align, false, /* tag := */ null, /* id := */ varID);
        t.allocated = true;
    }

    // Externally dispatched-to
    private static boolean declare(T t) {
        long size      = t.size;
        long align     = t.align;
        // Port: a type UID is a 32-bit CG.TypeUID.
        long typeUID   = Type.globalUID(t.type);
        CGType mtype   = Type.cgType(t.type, /* in_memory := */ true);
        boolean is_struct = Type.isStructured(t.type);
        String externName = null;
        int externM3ID = M3ID.NoID;
        int[] typename = {M3ID.NoID};
        String indirect_text = " ";
        Type.compile(t.type);

        t.cg_var  = null;
        t.bss_var = null;

        if ((is_struct)) {
            mtype = CGType.Struct;
        }

        if ((t.indirect)) {
            Type.typename(t.type, typename);
            typeUID = CG.declare_indirect((int) typeUID, typename[0]);
            typename[0] = M3ID.NoID;
            size = M3.target.Address.size;
            align = M3.target.Address.align;
            mtype = CGType.Addr;
        }

        // declare the actual variable
        if ((t.external)) {
            externName = Value.globalName(t, /* dots := */ false, /* with_module := */ false);
            externM3ID = M3ID.add(M3.ids, externName);
            t.nextTWACGVar = TsWCGVars;
            TsWCGVars = t;
            Type.typename(t.type, typename);

            if (debug) {
                rtioPutText("Variable.Declare t:");
                rtioPutRef(t);
                rtioPutText(" t.type:");
                rtioPutRef(t.type);
                rtioPutText(" typename:");
                rtioPutInt(typename[0]);
                rtioPutText("\n");
                rtioFlush();
            }

            if (M3.target.BackendMode != Target.M3BackendMode_t.C) {
                typeUID = 0 /*no mangling*/;
            }
            t.cg_var = CG.import_global(externM3ID, size, align, mtype, (int) typeUID, typename[0]);
            t.cg_align = alignVal(align);

        } else if ((t.imported)) {
            AssertError.check(t.allocated, "Variable");

        } else if ((t.global)) {
            AssertError.check(t.allocated, "Variable");
            CG.declare_global_field(t.name, t.offset, size, (int) typeUID, false);
            if ((t.initZero)) {
                t.initDone = true;
            }
            t.cg_align = alignVal(align);
            if ((t.indirect)) {
                t.cg_align = t.align;
                t.nextTWACGVar = TsWCGVars;
                TsWCGVars = t;
                t.bss_var = CG.declare_global(t.name, t.size, t.cg_align,
                                      CGType.Struct, (int) Type.globalUID(t.type),
                                      /* exported := */ false, /* init := */ false);
                CG.init_var(t.offset, t.bss_var, 0, false);
            }

        } else if ((t.formal == null)) {
            // simple local variable
            if ((size < 0)) {
                // it's an open array local introduced by a WITH statement
                align = Math.max(M3.target.Address.align, M3.target.Integer.align);
                size  = M3.target.Address.pack
                          + OpenArrayType.openDepth(t.type) * M3.target.Integer.pack;
            }
            // align := FindAlignment (align, size);
            t.cg_align = alignVal(align);
            t.nextTWACGVar = TsWCGVars;
            TsWCGVars = t;
            t.cg_var = CG.declare_local(t.name, size, align, mtype, (int) typeUID,
                                        t.need_addr, t.up_level, CG.Maybe);
        } else {
            // parameter
            if ((t.indirect)) {
                // formal passed by reference => param is an address
                indirect_text = " indirect ";
                // typename is earlier in this function, for the target of the indirect
            } else {
                // simple parameter
                // align := FindAlignment (align, size);
                Type.typename(typeOf(t), typename);
            }

            if (debug) {
                rtioPutText("Variable.Declare" + indirect_text + "param type:");
                rtioPutRef(t.type);
                rtioPutText(" name:");
                if (t.name != 0) {
                    rtioPutText(M3ID.toText(M3.ids, t.name));
                }
                rtioPutText(" typename:");
                rtioPutInt(typename[0]);
                rtioPutText("\n");
                rtioFlush();
            }

            t.cg_align = t.align;
            t.nextTWACGVar = TsWCGVars;
            TsWCGVars = t;
            t.cg_var = CG.declare_param(t.name, size, align, mtype, (int) typeUID, t.need_addr, t.up_level, CG.Maybe, typename[0]);
        }

        return true;
    }

    // -- this doesn't work with the current gcc-based backend.  It
    //    chokes on  VAR v: BITS 32 FOR CHAR := 'X' -- 10/9/96 WKK
    // PROCEDURE FindAlignment (align: AlignVal;  size: INTEGER): AlignVal =
    //   (* Fix the alignment of small local variables and parameters
    //      with BITS FOR types *)
    //   BEGIN
    //     IF    size < 0                  THEN (*don't mess with open array alignments*)
    //     ELSIF size >= Target.Int_D.size THEN align := MAX (align, Target.Int_D.align);
    //     ELSIF size <= Target.Int_A.size THEN align := MAX (align, Target.Int_A.align);
    //     ELSIF size <= Target.Int_B.size THEN align := MAX (align, Target.Int_B.align);
    //     ELSIF size <= Target.Int_C.size THEN align := MAX (align, Target.Int_C.align);
    //     ELSE                                 align := MAX (align, Target.Int_D.align);
    //     END;
    //     RETURN align;
    //   END FindAlignment;

    // Externally dispatched-to
    private static void constInit(T t) {
        long initSize;
        long initAlign;
        Type.T initRepType = null;
        long initDepth;
        int typeUID;
        Expr.T constInitExpr;
        String initName;
        int initM3ID;
        Type.Info initInfo = new Type.Info();
        if (t.external || t.imported) {
            return;
        }
        if (!t.initStatic && !t.global) {
            return;
        }

        if (t.initStatic && !t.initAllocated) {
            // Allocate space in the global constant area for the initial value.
            // Port: a type UID is a 32-bit CG.TypeUID.
            typeUID = (int) Type.globalUID(t.type);
            constInitExpr = Expr.constValue(t.initExpr);
            AssertError.check(constInitExpr != null, "Variable");
            if ((t.indirect)) {
                typeUID  = CG.declare_indirect(typeUID);
                initSize  = M3.target.Address.size;
                initAlign = alignVal(M3.target.Address.align);
                initName = t.qualName + "_INIT_INDIRECT_";
            } else {
                initRepType = Expr.repTypeOf(constInitExpr);
                Type.checkInfo(initRepType, initInfo);
                initDepth = OpenArrayType.openDepth(initRepType);

                if (initDepth > 0) { // initial value is an open array
                    // Allocate space for the dope only.
                    // See ArrayExpr.GenLiteral, where element space will
                    // be allocated.
                    initSize = M3.target.Address.pack + initDepth * M3.target.Integer.pack;
                    initAlign = alignVal(Math.max(M3.target.Address.align, M3.target.Integer.align));
                    initName = t.qualName + "_INIT_DOPE_";
                } else {
                    initSize  = initInfo.size;
                    initAlign = alignVal(initInfo.alignment);
                    initName = t.qualName + "_INIT_";
                }
            }
            initM3ID = M3ID.add(M3.ids, initName);
            // TODO: Eliminate duplicate copies of same value, including reused,
            //       named constant.
            t.initValOffset
                = Module.allocate(initSize, initAlign, true, "init value for ", initM3ID);
            t.initAllocated = true;
            CG.declare_global_field(t.name, t.initValOffset, initSize, typeUID, true);
            CG.comment(t.initValOffset, true, "init value for ", initName);
            // Sic: initRepType is NIL here if t.indirect.
            Expr.prepLiteral(constInitExpr, initRepType, true);
            Expr.genLiteral(constInitExpr, t.initValOffset, initRepType, true);
        }

        if ((t.global)) {
            // Try to statically initialize directly in the global variable area.
            AssertError.check(t.allocated, "Variable");
            constInitExpr = null;
            if ((t.initExpr != null) && (!t.initDone) && (!t.initStatic)) {
                constInitExpr = Expr.constValue(t.initExpr);
            }
            if ((constInitExpr != null)) {
                if (!Expr.checkUseFailure(t.initExpr)) {
                    // NOTE: Modula3 defines this as a checked runtime error, but in a
                    //  global variable, as in this case, execution of the assignment is
                    //  inevitable, thus it can't fail to fail at runtime.  Also,
                    //  portions of the runtime system are executed before their module's
                    //  initialization (the only place the compiler could put a runtime
                    //  abort) and depend instead on variables being statically initialized.
                    //  So we make this a compile time error.
                    Error.msg("Variable's initial value contains runtime assignability "
                              + "failure(s).");
                }
                Expr.prepLiteral(constInitExpr, t.type, false);
                Expr.genLiteral(constInitExpr, t.offset, t.type, false);
                t.initDone = true;
            }
        }
    }

    // Externally dispatched-to
    private static boolean needInit(T t) {
        Type.T[] refType = new Type.T[1];
        if ((t.imported) || (t.external) || (t.initDone)) {
            return false;
        } else if ((t.formal != null)) {
            return (t.indirect)
                   && Formal.openArrayByVALUE(t.formal, /*VAR*/ refType);
        } else if ((t.indirect) && (!t.global)) {
            return false;
        } else if ((t.global) && (t.initExpr != null) && (!t.initStatic)
                   && (Expr.constValue(t.initExpr) != null)) {
            return false;
        } else if ((t.initExpr != null)) {
            return true;
        } else {
            return Type.initCost(t.type, false) > 0;
        }
    }

    // Externally dispatched-to
    private static void langInit(T t) {
        Type.T[] refType = new Type.T[1];
        if ((t.imported) || (t.external)) {
            t.initDone = true;
        } else if ((t.formal != null)) {
            if (t.indirect
                && Formal.openArrayByVALUE(t.formal, /*VAR*/ refType)) {
                // a by-value open array!
                CG.gen_location(t.origin);
                load(t);
                copyOpenArray(t.type, refType[0]);
                // ^Change the formal parameter to refer to the new storage.
                CG.store_addr(t.cg_var);
            }
            // formal parameters don't need any further initialization
            Tracer.schedule(t.trace);
            t.initDone = true;
        } else if ((t.indirect) && (!t.global)) {
            // is a WITH variable bound to a designator
            Tracer.schedule(t.trace);
            t.initDone = true;
        }

        if ((t.initDone)) {
            return;
        }

        // initialize the value
        if ((t.initExpr != null) && (!t.up_level) && (!t.imported)) {
            // variable has a user specified initExpr value and isn't referenced
            // by any nested procedures => try to avoid the language defined
            // init and wait until we get to the user defined initialization.
            t.initPending = true;
        } else {
            if (Type.initCost(t.type, false) > 0) {
                CG.gen_location(t.origin);
                loadLValue(t);
                Type.initValue(t.type, false);
            }
            if ((t.trace != null) && (!t.imported)) {
                if ((t.initExpr == null) || (t.initDone)) {
                    // there's no explicit user init => might as well trace it now
                    CG.gen_location(t.origin);
                    Tracer.schedule(t.trace);
                }
            }
        }
    }

    private static void forceInit(T t) {
        t.initPending = false;
        CG.gen_location(t.origin);
        loadLValue(t);
        Type.initValue(t.type, false);
    }

    /**
     * PRE: Pointer to array dope is on TOS.
     * Generate code to heap-allocate the copy.
     * POST: TOS replaced by pointer to dope of copy.
     */
    // EXPORTED
    public static void copyOpenArray(Type.T arrayType, Type.T refType) {
        CG.Val oldDopePtr, newDopePtr;
        long depth = OpenArrayType.openDepth(arrayType);
        long align = Math.max(OpenArrayType.eltAlign(arrayType), Target.Word8.align);
        long pack  = OpenArrayType.eltPack(arrayType);
        M3CG.Var sizes = CG.declare_temp(M3.target.Address.pack + M3.target.Integer.pack,
                                         M3.target.Address.align, CGType.Struct,
                                         /* in_memory := */ true);
        Procedure.T proc;
        oldDopePtr = CG.pop();
        // This is confusing.  Build a new 1-D dope vector that treats the shape
        // portion of the to-be-copied dope vector as an open array.
        CG.push(oldDopePtr);
        CG.add_offset(M3.rt.OA_sizes);
        CG.store_addr(sizes, M3.rt.OA_elt_ptr);
        CG.load_intt(depth);
        CG.store_int(M3.target.Integer.cg_type, sizes, M3.rt.OA_size_0);

        // allocate the storage
        proc = RunTyme.lookUpProc(RunTyme.Hook.NewTracedArray);
        Procedure.startCall(proc);
        if (M3.target.DefaultCall.args_left_to_right) {
            Type.loadInfo(refType, -1);
            CG.pop_param(CGType.Addr);
            CG.load_addr_of(sizes, 0, M3.target.Address.align);
            CG.pop_param(CGType.Addr);
        } else {
            CG.load_addr_of(sizes, 0, M3.target.Address.align);
            CG.pop_param(CGType.Addr);
            Type.loadInfo(refType, -1);
            CG.pop_param(CGType.Addr);
        }
        newDopePtr = Procedure.emitValueCall(proc);

        // load the destination and source elements' addresses
        CG.push(newDopePtr);
        CG.boost_addr_alignment(M3.target.Address.align);
        CG.open_elt_ptr(align); // Addr of the new elements.
        CG.forceStacked();
        CG.push(oldDopePtr);
        CG.open_elt_ptr(align); // Addr of the old elements.
        CG.forceStacked();

        // compute the number of elements
        for (long i = 0; i <= depth - 1; i++) {
            CG.push(oldDopePtr);
            CG.open_size(i);
            if ((i != 0)) {
                CG.multiply(M3.target.Word.cg_type);
            }
        }

        // copy the elements into the new storage
        CG.copy_n(pack, /* overlap := */ false);

        // Push new dope pointer for the caller.
        CG.push(newDopePtr);
        CG.boost_addr_alignment(M3.target.Address.align);

        // free our temps
        CG.free_temp(sizes);
        CG.free(oldDopePtr);
        CG.free(newDopePtr); // It's now safely on the stack, so this is OK.
    }

    // Externally dispatched-to
    private static void userInit(T t) {
        Expr.T constInitExpr;
        Type.T initRepType;
        long openEltAlign;
        if ((t.initExpr != null) && (!t.initDone) && (!t.imported)) {
            CG.gen_location(t.origin);
            if ((t.initZero)) {
                t.initPending = false;
                loadLValue(t);
                Type.zero(t.type);
            } else if (t.initAllocated) {
                t.initPending = false;
                if (Expr.checkUseFailure(t.initExpr)) {
                    loadLValue(t);
                    Module.loadGlobalAddr((Module.T) Scope.toUnit(t), t.initValOffset,
                                          /* is_const := */ true);
                    constInitExpr = Expr.constValue(t.initExpr);
                    AssertError.check(constInitExpr != null, "Variable");
                    initRepType = Expr.repTypeOf(constInitExpr);
                    if (OpenArrayType.is(initRepType)) {
                        openEltAlign
                            = Math.max(OpenArrayType.eltAlign(initRepType), Target.Word8.align);
                        CG.open_elt_ptr(openEltAlign);
                    }
                    CG.copy(t.size, /* overlap := */ false);
                } else {
                    // Expr.CheckUseFailure will have generated an unconditional RT error.
                }
            } else {
                t.initPending = false;
                ArrayExpr.noteUseTargetVar(t.initExpr);
                AssignStmt.prepForEmit(t.type, t.initExpr, /* initializing := */ true);
                loadLValue(t);
                AssignStmt.doEmit(t.type, t.initExpr, t.cg_align, /* initializing := */ true);
            }
            t.initDone = true;
            Tracer.schedule(t.trace);
        }
    }

    /** generate the garbage collector's map-proc for the variables of s */
    // EXPORTED
    public static long genGlobalMap(Scope.T s) {
        boolean started = false;
        Type.Info info = new Type.Info();
        Value.T v = Scope.toList(s);
        while ((v != null)) {
            Value.T b = Value.base(v);
            if (b == null) {
                // do nothing
            } else if (b instanceof T t) {
                if ((!t.imported)
                    && (!t.external)) {
                    Type.checkInfo(t.type, info);
                    if ((info.isTraced)) {
                        if ((!started)) {
                            TipeMap.start();
                            started = true;
                        }
                        t.used = true;
                        Value.declare(t);
                        if ((t.indirect)) {
                            TipeMap.add(t.offset, TipeMap.Op.PushPtr, 0);
                            Type.genMap(t.type, 0, -1, /* refs_only := */ true);
                            TipeMap.add(t.size, TipeMap.Op.Return, 0);
                            TipeMap.setCursor(t.offset + M3.target.Address.size);
                        } else {
                            Type.genMap(t.type, t.offset, -1, /* refs_only := */ true);
                        }
                    }
                }
            } else {
                // do nothing
            }
            v = v.next;
        }
        if ((started)) {
            return TipeMap.finish("global type map");
        } else {
            return -1;
        }
    }

    // EXPORTED
    public static boolean needGlobalInit(Variable.T t) {
        return (!t.initDone) && (!t.external);
    }

    // EXPORTED
    public static void initGlobal(Variable.T t) {
        if ((!t.initDone) && (!t.external)) {
            loadLValue(t);
            Type.initValue(t.type, true);
        }
    }

    // Externally dispatched-to
    private static long addFPTag(T t, M3.FPInfo x) {
        ValueRep.fpStart(t, x, "VAR ", t.offset, /* global := */ true);
        return 1;
    }

    //--------------------------------------------------------- trace support ---

    public static final class TraceNode extends Tracer.T {
        public Expr.T handler = null;
        public Expr.T call = null;

        @Override
        public void apply() {
            doTrace(this);
        }
    }

    // EXPORTED
    public static Tracer.T parseTrace() {
        // Port: TK = Token.T.
        Expr.T e;
        if ((Scanner.cur.token != Token.T.tTRACE)) {
            return null;
        }
        Scanner.match(Token.T.tTRACE);
        e = Expr.parse();
        Scanner.match(Token.T.tENDPRAGMA);
        if ((e == null)) {
            return null;
        }
        TraceNode r = new TraceNode();
        r.handler = e;
        return r;
    }

    // EXPORTED
    public static void bindTrace(Variable.T t, Tracer.T xx) {
        TraceNode x = (TraceNode) xx;
        Scope.IDStack p = new Scope.IDStack();
        M3String.T z;
        Expr.T[] args;
        if ((xx == null)) {
            return;
        }

        if ((x.call != null)) {
            TraceNode y = new TraceNode();
            y.handler = x.handler;
            x = y;
        }

        // get the variable's full name
        p.top = 0;
        Scope.nameToPrefix(t, p, true, /* dots := */ true, /* with_module := */ true);
        z = M3String.add(Scope.stackToText(p));

        // build the trace procedure call
        args = new Expr.T[2];
        args[0] = TextExpr.new8(z);
        args[1] = NamedExpr.fromValue(t);
        x.call  = CallExpr.new_(x.handler, args);

        AssertError.check(t.trace == null, "Variable");
        t.trace = x;
    }

    private static void doTrace(TraceNode x) {
        Expr.prep(x.call);
        Expr.compile(x.call);
    }

    // EXPORTED
    public static void checkTrace(Tracer.T tt, M3.CheckState cs) {
        TraceNode x = (TraceNode) tt;
        if ((x != null)) {
            Expr.typeCheck(x.handler, cs);
            Expr.typeCheck(x.call, cs);
        }
    }

    // EXPORTED
    public static void scheduleTrace(Variable.T t) {
        Tracer.schedule(t.trace);
    }
}
