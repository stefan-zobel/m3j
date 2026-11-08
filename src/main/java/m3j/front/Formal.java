// Java port of CM3's m3-sys/m3front/src/values/Formal.i3 and Formal.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: Formal.i3
// Last Modified On Mon Dec  5 15:19:54 PST 1994 By kalsow
//      Modified On Wed Feb  7 01:02:52 1990 By muller

// File: Formal.m3
// Last modified on Tue May 23 15:42:16 PDT 1995 by kalsow
//      modified on Fri Nov  9 20:39:07 1990 by muller

public final class Formal {
    private Formal() {}

    public enum Mode { mVALUE, mVAR, mREADONLY }

    public static final class Info {
        public int name = M3ID.NoID;
        public Mode mode = Mode.mVALUE;
        /**
         * NOTE WELL, lest thou be deceived.  'offset' is not an offset,
         * it is the zero-origin sequence *number* of the formal.
         */
        public long offset = 0;
        public Type.T type = null;
        public Expr.T dfault = null;
        public boolean unused = false;
        public Tracer.T trace = null;

        public Info() {}

        public Info copy() {
            Info r = new Info();
            r.name = name;
            r.mode = mode;
            r.offset = offset;
            r.type = type;
            r.dfault = dfault;
            r.unused = unused;
            r.trace = trace;
            return r;
        }
    }

    // Port: set below from RTParams.IsPresent ("m3front-debug-formal"),
    // which is FALSE here.
    private static boolean debug = false;

    public static final class T extends Value.T {
        public long offset = 0;
        /** only written once, not lowered, to retain NamedType */
        public Type.T type = null;
        public Type.T repType = null;
        public Expr.T dfault = null;
        /** Needed to copy an open array. */
        public Type.T refType = null;
        public CG.Val tempCGVal = null;
        public int cg_type = 0;
        public Mode mode = Mode.mVALUE;
        public Type.Class kind = Type.Class.Error;
        public Tracer.T trace = null;
        public boolean openArray = false;
        public boolean hasError = false;

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
            Formal.load(this);
        }

        @Override
        public void lang_init() {
            compile(this);
        }

        @Override
        public Type.T typeOf() {
            return Formal.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return Formal.repTypeOf(this);
        }

        @Override
        public long add_fp_tag(M3.FPInfo x) {
            return addFPTag(this, x);
        }

        @Override
        public Type.T fp_type() {
            return Formal.typeOf(this);
        }

        // Port: the overrides declare := ValueRep.Never, const_init :=
        // ValueRep.NoInit, need_init := ValueRep.Never, user_init :=
        // ValueRep.NoInit, toExpr := ValueRep.NoExpr, toType :=
        // ValueRep.NoType and base := ValueRep.Self restate the inherited
        // methods.
    }

    public static final class ArgSlot {
        public T formal;
        public Expr.T actual;
        public int name;
        public boolean matched;
        public boolean errored;

        ArgSlot() {}
    }

    /** Port: RTIO.PutRef; Java has no addresses, the identity hash stands in. */
    private static void putRef(Object r) {
        System.err.print("0x" + Integer.toHexString(System.identityHashCode(r)));
    }

    //EXPORTED
    public static Value.T newBuiltin(String name, long offset, Type.T type) {
        T t = new T();
        ValueRep.init(t, M3ID.add(M3.ids, name), Value.Class.Formal);
        t.readonly = false;
        t.offset = offset;
        t.mode = Mode.mVALUE;
        t.type = type;
        t.unused = false;
        t.kind = Type.Class.Error;
        return t;
    }

    //EXPORTED
    public static Value.T new_(Formal.Info info) {
        T t = new T();
        ValueRep.init(t, info.name, Value.Class.Formal);
        t.readonly = (info.mode == Mode.mREADONLY);
        t.offset = info.offset;
        t.mode = info.mode;
        t.type = info.type;
        t.dfault = info.dfault;
        t.unused = info.unused;
        t.kind = Type.Class.Error;
        t.trace = info.trace;

        if (debug) {
            System.err.print("Formal.New:");
            putRef(t);
            System.err.print(" type:");
            putRef(t.type);
            System.err.print(" name:");
            if (t.name != 0) {
                System.err.print(M3ID.toText(M3.ids, t.name));
            }
            System.err.print("\n");
            System.err.flush();
        }

        return t;
    }

    /**
     * An anonymous array constructor as a parameter default, in an interface,
     * can be referred-to from another unit.  So give it a name.
     */
    //EXPORTED
    public static void nameDefaultConstructors(Type.T sig, int id, M3.CheckState cs) {
        // Port: TYPECASE; NIL matches T.
        Value.T x = ProcType.formals(sig);
        if (x == null || x instanceof T) {
            T formal = (T) x; // Head of linked list
            while (formal != null) {
                Expr.T[] dfault = {formal.dfault};
                Expr.nameAnonConstr(dfault, id, formal.name, cs);
                formal.dfault = dfault[0];
                formal = (T) formal.next;
            }
        } else {
        }
    }

    /** Port: info is VAR. */
    //EXPORTED
    public static void split(Value.T formal, Formal.Info info) {
        T t = (T) formal;
        Type.T type1 = t.type;

        info.name = t.name;
        info.offset = t.offset;
        info.mode = t.mode;
        info.type = typeOf(t);
        info.dfault = t.dfault;
        info.unused = t.unused;
        info.trace = t.trace;

        if (debug) {
            System.err.print("Formal.Split:");
            putRef(t);
            System.err.print(" name:");
            if (t.name != 0) {
                System.err.print(M3ID.toText(M3.ids, t.name));
            }
            System.err.print(" type1:");
            putRef(type1);
            System.err.print(" type2:");
            putRef(info.type);
            System.err.print(" typename:");
            if (info.name != 0) {
                System.err.print(M3ID.toText(M3.ids, info.name));
            }
            System.err.print("\n");
            System.err.flush();
        }

    }

    /** Only for a formal of a procedure type or an imported procedure. */
    //EXPORTED
    public static void emitDeclaration(Value.T formal, boolean types_only, boolean param) {
        T t = (T) formal;
        CGType mtype;
        long size;
        long align;
        Type.Info info = new Type.Info();
        int[] typename = {M3ID.NoID};
        boolean indirect = false;
        long trace = 0;
        if ((types_only)) {
            trace = trace * 10 + 1;
            compile(t);
            // Port: the uid is a 32-bit value (M3FP.ToInt).
            t.cg_type = (int) Type.globalUID(typeOf(t));
        }

        indirect = t.mode != Mode.mVALUE || t.openArray;

        if (types_only || !indirect) {
            Type.typename(typeOf(t), typename);
        }

        if ((types_only)) {
            if (indirect) { // lo-level pass by reference.
                CG.declare_typename(t.cg_type, typename[0]);
                t.cg_type = CG.declare_indirect(t.cg_type, typename[0]);
            }
        } else if ((param)) {
            trace = trace * 10 + 2;
            if (indirect) { // lo-level pass by reference.
                trace = trace * 10 + 3;
                size = M3.target.Address.size;
                align = M3.target.Address.align;
                mtype = CGType.Addr;
                // typename handled above in declare_indirect;
                // specifying it here would miss the indirection
            } else { // lo-level pass by value.
                trace = trace * 10 + 4;
                Type.checkInfo(typeOf(t), info);
                size = info.size;
                align = info.alignment;
                mtype = info.mem_type;
            }

            CG.declare_param(t.name, size, align, mtype,
                             t.cg_type, /* in_memory := */ false, /* up_level := */ false,
                             /* f := */ CG.Maybe, /* typename := */ typename[0]);
        } else { // This is part of debug info for a signature.

            if (debug) {
                System.err.print("Formal.EmitDeclaration Declare_formal name:");
                System.err.print(M3ID.toText(M3.ids, t.name));
                System.err.print("\n");
                System.err.flush();
            }

            CG.declare_formal(t.name, t.cg_type, typename[0]);
        }
    }

    //EXPORTED
    public static boolean hasClosure(Value.T formal) {
        if (formal == null) {
            return false;
        } else if (formal instanceof T t) {
            return (t.mode != Mode.mVAR)
                && ((t.kind == Type.Class.Procedure)
                    || ProcType.is(Type.base(typeOf(t))));
        } else {
            return false;
        }
    }

    /**
     * If 'formal' is a "VALUE ARRAY OF X" formal, sets 'refType' to "REF ARRAY OF X"
     * and returns TRUE, otherwise returns FALSE.
     *
     * <p>Port: refType is VAR.
     */
    //EXPORTED
    public static boolean openArrayByVALUE(Value.T formal, Type.T[] refType) {
        if (formal == null) {
        } else if (formal instanceof T t) {
            if (t.mode == Mode.mVALUE && t.openArray) {
                refType[0] = t.refType;
                AssertError.check(refType[0] != null, "Formal");
                return true;
            }
        } else {
        } // TYPECASE
        return false;
    }

    // Externally dispatched-to:
    private static Type.T typeOf(T t) {
        if ((t.type == null)) {
            t.type = Expr.typeOf(t.dfault);
        }
        return t.type;
    }

    // Externally dispatched-to:
    private static Type.T repTypeOf(T t) {
        if (t.repType == null) {
            t.repType = Expr.repTypeOf(t.dfault);
        }
        return t.repType;
    }

    // Externally dispatched-to:
    /** Only checks on the formal itself. */
    private static void check(T t, M3.CheckState cs) {
        Type.Info info = new Type.Info();
        Type.T type = null;
        type = Type.checkInfo(typeOf(t), info);
        t.repType = Type.stripPacked(type);
        Type.check(t.repType);
        t.kind = info.class_;
        if ((info.class_ == Type.Class.Packed)) { // Ignore BITS in setting class.
            Type.checkInfo(Type.base(type), info);
            t.kind = info.class_;
        }

        if ((t.dfault != null)) {
            Expr.typeCheck(t.dfault, cs);
            if ((t.mode == Mode.mVAR)) {
                Error.ID(t.name, "VAR parameters cannot have defaults (2.2.8).");
                t.kind = Type.Class.Error;
            }
            if (!Type.isAssignable(t.type, Expr.typeOf(t.dfault))) {
                Error.ID(t.name, "default must be assignable to formal (2.2.8).");
                // FIXME ^ 2.2.8 says default must be a *member* of t.type, which is stronger
                //   than assignable.
                t.kind = Type.Class.Error;
            }
            if ((Expr.constValue(t.dfault) == null)) {
                Error.ID(t.name, "parameter default must be constant (2.2.8).");
                t.kind = Type.Class.Error;
            }
            // NOTE: we don't save the constant-folded version of the default,
            //   otherwise we'd loose references to large named constants.
        }

        t.openArray = OpenArrayType.is(Type.base(t.type));
        if (t.openArray && t.mode == Mode.mVALUE
            // ^Open array VALUE formal is lo-level passed by reference.
            //   Prolog will make a copy.
            || t.mode == Mode.mREADONLY // lo-level passed by reference.
            // ^Also, some actuals (non-designator, multi-use, actual and formal
            //   both open array) passed READONLY will do call-site copying.
        ) { // We need a reference type to the formal type to do open array
            //   copying.
            t.refType = RefType.new_(type, /* traced := */ true, /* brand := */ null);
            Type.check(t.refType);
        }

    }

    // Externally dispatched-to:
    private static void load(T t) {
        boolean useOK;
        if ((t.dfault == null)) {
            Error.ID(t.name, "Unsupplied formal has no default value (2.2.8)");
        }
        useOK = Expr.checkUseFailure(t.dfault);
        if (useOK) {
            Expr.prep(t.dfault);
            Expr.compile(t.dfault);
        }
    }

    // Externally dispatched-to:
    private static void compile(T t) {
        Type.T dfaultTyp;
        Type.compile(typeOf(t));
        Type.compile(repTypeOf(t));
        Type.compile(t.refType);
        dfaultTyp = Expr.typeOf(t.dfault);
        Type.compile(dfaultTyp);
        if (ArrayExpr.isAnon(t.dfault) && Module.isInterface()) {
            // Get the static value into this interface, while we are in it,
            //   so it can be referred-to from other units.
            Expr.prep(t.dfault);
            Expr.compile(t.dfault, /* StaticOnly := */ true);
        }
    }

    // Externally dispatched-to:
    private static void setGlobals(T t) {
        // Type.SetGlobals (t.type);
        // Type.SetGlobals (t.refType);
        // IF (t.dfault # NIL) THEN Type.SetGlobals (Expr.TypeOf (t.dfault)) END;
    }

    private static final String[] Tags = { "VALUE ", "VAR ", "READONLY " };

    // Externally dispatched-to:
    private static long addFPTag(T t, M3.FPInfo x) {
        ValueRep.fpStart(t, x, Tags[t.mode.ordinal()], 0, /* global := */ false);
        if ((t.dfault != null)) {
            M3Buf.putText(x.buf, " := ");
            Expr.genFPLiteral(t.dfault, x.buf);
        }
        return 1;
    }

    //--------------------------------------------------- actual typechecking ---

    /**
     * Whereas method check handles only a formal, the following handle
     * actual/formal matchups for single parameter or list of parameters,
     * thus can be called only when compiling a call.
     *
     * <p>Do checks on actual/formal relationship, for one call, all parameters.
     *
     * <p>Port: actuals is VAR; the procedure may replace the list, so it is
     * passed in a one-element array.
     */
    //EXPORTED
    public static boolean checkArgs(M3.CheckState cs, Expr.T[][] actuals, Value.T formals, Expr.T proc) {
        ArgSlot[] slots = newArgSlots(20);
        Value.T v;
        long n;
        v = formals;
        n = 0;
        while ((v != null)) {
            n++;
            v = v.next;
        }
        if ((n <= slots.length)) {
            return doCheckArgs(cs, actuals, formals, n, slots, proc);
        } else {
            return doCheckArgs(cs, actuals, formals, n,
                               newArgSlots(n), proc);
        }
    }

    /** Port: an array of n new records. */
    private static ArgSlot[] newArgSlots(long n) {
        ArgSlot[] a = new ArgSlot[(int) n];
        for (int i = 0; i < a.length; i++) {
            a[i] = new ArgSlot();
        }
        return a;
    }

    /** Formals are linked.  Actuals are in a heap-allocated open array. */
    private static boolean doCheckArgs(M3.CheckState cs,
                                       Expr.T[][] actuals,
                                       Value.T formals,
                                       long nFormals,
                                       ArgSlot[] slots,
                                       Expr.T proc) {
        long j;
        Expr.T actualExpr;
        Expr.T[] value = new Expr.T[1];
        Type.T[] index = new Type.T[1], elt = new Type.T[1];
        Type.T t;
        Type.T actSemType;
        int[] name = new int[1];
        T formal;
        boolean posOK, ok;
        ok = true;

        if ((nFormals < actuals[0].length)) {
            Error.msg("too many actual parameters (2.3.2): " + procName(proc));
            ok = false;
        }

        // Initialize the local/formal argument array.
        formal = (T) formals;
        while ((formal != null)) {
            {
                ArgSlot z = slots[(int) formal.offset];
                z.formal = formal;
                z.actual = formal.dfault;
                z.matched = false;
                z.errored = false;
                z.name = formal.name;
            }
            formal = (T) formal.next;
        }

        // bind the parameters
        posOK = true;
        for (long i = 0, last = Math.min(actuals[0].length - 1, nFormals - 1); i <= last; i++) {
            actualExpr = actuals[0][(int) i];
            if (KeywordExpr.split(actualExpr, name, value)) {
                posOK = false;
                actualExpr = value[0];
                j = 0;
                while (true) {
                    if ((j >= nFormals)) {
                        Error.ID(name[0], "No such formal parameter (2.3.2): ");
                        ok = false;
                        j = i;
                        break;
                    }
                    if ((slots[(int) j].name == name[0])) {
                        break;
                    }
                    j++;
                }
            } else {
                if ((!posOK)) {
                    Error.msg
                        ("positional parameters must precede keyword parameters (2.3.2): "
                                   + procName(proc));
                    ok = false;
                }
                j = i;
            }
            {
                ArgSlot z = slots[(int) j];
                if ((z.matched)) {
                    err(z, "This parameter was previously specified (2.3.2): ");
                    ok = false;
                }
                z.matched = true;
                z.actual = actualExpr;
            }
        }

        // check for any unspecified parameters
        for (long i = 0; i <= nFormals - 1; i++) {
            if ((slots[(int) i].actual != null)) {
                slots[(int) i].matched = true;
            }
            if (!slots[(int) i].matched) {
                err(slots[(int) i], "parameter not specified (2.3.2): ");
                ok = false;
            }
        }

        // typecheck each binding
        for (long i = 0; i <= nFormals - 1; i++) {
            actualExpr = slots[(int) i].actual;
            formal = slots[(int) i].formal;
            if ((actualExpr != null) && (formal != null)) {
                // we've got both a formal and an actual
                Expr.typeCheck(actualExpr, cs);

                // try to fold scalar constant values
                // NOTE: if we fold named structured constants, we lose the
                //   names and hence generate code to build the value...
                if (!Type.isStructured(formal.type)) {
                    value[0] = Expr.constValue(actualExpr);
                    if (value[0] != null) {
                        actualExpr = value[0];
                        slots[(int) i].actual = actualExpr;
                    }
                }

                actSemType = Expr.semTypeOf(actualExpr);
                t = formal.type;
                switch (formal.mode) {
                    case mVALUE -> {
                        if (!Type.isAssignable(t, actSemType)) {
                            err(slots[(int) i], "actual not assignable to VALUE formal (2.3.2) ");
                            ok = false;
                        }
                    }
                    case mVAR -> {
                        if (!Expr.isDesignator(actualExpr)) {
                            err(slots[(int) i], "VAR actual must be a designator (2.3.2) ");
                            ok = false;
                        }
                        if (!Expr.isWritable(actualExpr, /* traced := */ true)) {
                            err(slots[(int) i], "VAR actual must be writable (2.3.2): ");
                            ok = false;
                        }
                        if (Type.isEqual(t, actSemType, null)
                              || (ArrayType.split(t, index, elt)
                                  && ArrayType.split(actSemType, index, elt)
                                  && Type.isAssignable(t, actSemType))) {
                            if (PackedType.is(actSemType)) {
                                err(slots[(int) i],
                                    "CM3 restriction: VAR actual cannot be packed. (2.3.2): ");
                                ok = false;
                            }
                        } else {
                            err(slots[(int) i], "Actual's type must equal VAR formal's (2.3.2): ");
                            ok = false;
                        }
                        if (ok) {
                            Expr.needsAddress(actualExpr);
                        }
                    }
                    case mREADONLY -> {
                        if (!Type.isAssignable(t, actSemType)) {
                            err(slots[(int) i],
                                "Actual must be assignable to READONLY formal (2.3.2): ");
                            ok = false;
                        } else if (Expr.isDesignator(actualExpr)
                                   && Type.isEqual(t, actSemType, null)
                                   || (ArrayType.split(t, index, elt))
                                   // ^Excludes BITS FOR
                        ) { // Pass by reference.
                            Expr.needsAddress(actualExpr);
                        } else { // Type.IsAssignable (t, actSemType), pass by value.
                            // we'll make a copy when it's generated
                        }
                    }
                } // case

                // check to see if it's a reference and needs an implicit NARROW,
                //   which may generate a nested procedure call...
                if ((ok) && Host.doNarrowChk
                        && ((formal.kind == Type.Class.Ref)
                          || (formal.kind == Type.Class.Object)
                          || (formal.kind == Type.Class.Opaque))) {
                    if (!Type.isSubtype(actSemType, t)) {
                        // This reference value needs an implicit NARROW
                        actualExpr = NarrowExpr.new_(actualExpr, t);
                        slots[(int) i].actual = actualExpr;
                        Expr.typeCheck(actualExpr, cs);
                    }
                }

            } // if got actual & formal
        } // FOR

        if ((!ok)) {
            return false;
        }

        // no more possible errors => build the new argument list
        if ((actuals[0].length != nFormals)) {
            actuals[0] = new Expr.T[(int) nFormals];
        }
        for (long i = 0; i <= nFormals - 1; i++) {
            actuals[0][(int) i] = slots[(int) i].actual;
        }
        return true;
    }

    /** Port: slot is VAR. */
    private static void err(ArgSlot slot, String msg) {
        if ((!slot.errored)) {
            Error.ID(slot.name, msg);
            slot.errored = true;
        }
    }

    private static String procName(Expr.T proc) {
        Value.T[] v = new Value.T[1];
        if ((proc != null) && UserProc.isProcedureLiteral(proc, v)) {
            return ": " + Value.globalName(v[0]);
        } else {
            return "";
        }
    }

    //----------------------------------------------------------- caller code ---

    //EXPORTED
    public static void prepArg(Value.T formal, Expr.T actExpr) {
        T formVal = (T) formal;
        Type.T actRepType;
        // Sic: actSemType is read in the mVAR case before it is assigned; a
        // traced local reference starts as NIL.
        Type.T actSemType = null;
        long actStaticSize; // Excluding dope.
        boolean typeOKForByRef;
        formVal.hasError = false;
        formVal.tempCGVal = null;
        // ^We could be reusing formVal, after a previous call.
        ArrayExpr.noteTargetType(actExpr, formVal.type);

        switch (formVal.mode) {
            case mVALUE -> {
                Expr.prep(actExpr);

            }
            case mVAR -> {
                // Pass by reference.
                if (!Expr.isDesignator(actExpr)
                    || !Expr.isWritable(actExpr, /* traced := */ true)
                    || PackedType.is(actSemType)) { // Just error recovery.
                    formVal.hasError = true;
                } else if (requiresFetch(formVal, actExpr)) {
                    Error.ID(formVal.name,
                        "CM3 restriction: non-byte-aligned value cannot be passed VAR"
                        + " (2.3.2)");
                    formVal.hasError = true;
                } else {
                    Expr.prepLValue(actExpr, /* traced := */ true);
                }

            }
            case mREADONLY -> {
                actSemType = Expr.semTypeOf(actExpr);
                typeOKForByRef // Already know it's assignable.
                    = Type.isEqual(formVal.type, actSemType, null)
                      || (ArrayType.is /* Strips packed. */ (formVal.type)
                          && ArrayType.is(actSemType)
                          && !PackedType.is(actSemType)
                         );
                if (typeOKForByRef) {
                    if (PackedType.is(actSemType)) {
                        Error.info
                            ("CM3 exception: Packed-typed actual to READONLY formal "
                             + "is passed by value.");
                        typeOKForByRef = false;
                    }
                }
                if (typeOKForByRef && Expr.isDesignator(actExpr)) { // Pass by reference.
                    if (requiresFetch(formVal, actExpr)) {
                        Error.ID(formVal.name,
                            "CM3 restriction: non-byte-aligned value cannot be passed READONLY"
                            + " by reference (2.3.2)");
                        formVal.hasError = true;
                    } else {
                        Expr.prepLValue(actExpr, /* traced := */ false);
                    }
                } else if (typeOKForByRef && Expr.isAnonConstructor(actExpr)) {
                    // Pass by reference but do not treat as LValue.
                    if (requiresFetch(formVal, actExpr)) {
                        Error.ID(formVal.name,
                            "CM3 restriction: non-byte-aligned value cannot be passed READONLY"
                            + " by reference (2.3.2)");
                        formVal.hasError = true;
                    } else {
                        Expr.prep(actExpr); // Do not treat as LValue.
                    }
                } else { // Pass by value. Lo-level: make a copy here at the call site and
                         //   pass it by reference.
                    Expr.prep(actExpr);
                    actStaticSize = Expr.staticSize(actExpr);
                    if (requiresFetch(formVal, actExpr)
                        || SetType.isSmallSet(formVal.repType)
                        || !OpenArrayType.is(formVal.repType)
                        || actStaticSize > 0) { // Static size.
                    } else { // Must make a dynamic-sized copy in a heap-allocated temp.
                        actRepType = Expr.repTypeOf(actExpr);
                        // ^May be a BITS-FOR, type, but only if actExpr is a field or element.
                        Expr.compile(actExpr);
                        redepthArray(formVal.repType, actRepType, 0);
                        Variable.copyOpenArray(actRepType, formVal.refType);
                        formVal.tempCGVal = CG.pop();
                    }
                } // IF
            }
        } // CASE
    }

    /** generate code to pass 'actual' as a 'formal'. */
    //EXPORTED
    public static void emitArg(Expr.T proc, Value.T formalVal, Expr.T actExpr) {
        T form = (T) formalVal;
        boolean useOK;
        useOK = Expr.checkUseFailure(actExpr);
        if (!useOK) {
            return;
        }
        switch (form.kind) {
            case Error ->
                throw new AssertError("Formal");
            case Named, Packed ->
                throw new AssertError("Formal");
            case Integer, Enum, Subrange,
                 Longint ->
                genOrdinal(form, actExpr);
            case Real, Longreal, Extended ->
                genFloat(form, actExpr);
            case Ref, Object, Opaque ->
                genReference(form, actExpr);
            case Procedure ->
                genProcedure(form, actExpr, proc);
            case Set -> {
                if (SetType.isSmallSet(form.repType)) {
                    genSmallSet(form, actExpr);
                } else {
                    genStruct
                        (form, actExpr, /* formIsArray := */ false, /* formIsOpen := */ false);
                }
            }
            case Record ->
                genStruct
                    (form, actExpr, /* formIsArray := */ false, /* formIsOpen := */ false);
            case Array ->
                genStruct
                    (form, actExpr, /* formIsArray := */ true, /* formIsOpen := */ false);
            case OpenArray ->
                genStruct
                    (form, actExpr, /* formIsArray := */ true, /* formIsOpen := */ true);
        }
    }

    /**
     * PRE: A scalar of type 'type' is on top of CG stack. It is not bitpacked.
     * POST: TOS replaced by the address of a temp of type 'type' containing a copy.
     */
    private static void genScalarCopy(Type.T type) {
        M3CG.Var tempVar;
        Type.Info typeInfo = new Type.Info();
        Type.checkInfo(Type.stripPacked(type), typeInfo);
        tempVar = CG.declare_temp
            (typeInfo.size, typeInfo.alignment, typeInfo.mem_type, /* in_memory := */ true);
        CG.store(tempVar, 0, typeInfo.size, typeInfo.alignment, typeInfo.mem_type);
        CG.load_addr_of(tempVar, 0, typeInfo.alignment);
    }

    private static void genOrdinal(T formVal, Expr.T actExpr) {
        int[] min = TInt.newInt(), max = TInt.newInt();
        Type.T actSemType;
        // * VAR constant := Expr.ConstValue (actExpr); *
        // **
        //   -- we can't fold constant actExprs since they may have been precompiled
        //      and have allocated temporaries that still need to be freed ....
        // IF (constant # NIL) THEN actExpr := constant END;
        // **
        if (formVal.hasError) { // Error recovery.
            CG.load_nil();
            CG.pop_param(CGType.Addr);
        } else {
            switch (formVal.mode) {
                case mVALUE -> {
                    Type.getBounds(formVal.type, min, max); // Of formal.
                    CheckExpr.emitChecks(actExpr, min, max, M3CG.RuntimeError.ValueOutOfRange);
                    CG.pop_param(Type.cgType(formVal.type, /* in_memory := */ true));
                }
                case mVAR -> {
                    Expr.compileAddress(actExpr, /* traced := */ true);
                    CG.pop_param(CGType.Addr);
                    Expr.noteWrite(actExpr);
                }
                case mREADONLY -> {
                    actSemType = Expr.typeOf(actExpr);
                    if (!Type.isEqual(formVal.type, actSemType, null)) {
                        // Pass by value. Could need bounds check.
                        Type.getBounds(formVal.type, min, max); // Of formal.
                        CheckExpr.emitChecks
                            (actExpr, min, max, M3CG.RuntimeError.ValueOutOfRange);
                        genScalarCopy(formVal.type);
                        CG.pop_param(CGType.Addr);
                    } else if (!Expr.isDesignator(actExpr) || PackedType.is(actSemType)) {
                        // Same type, no bounds check needed, pass by value.
                        Expr.compile(actExpr);
                        genScalarCopy(formVal.type);
                        CG.pop_param(CGType.Addr);
                    } else { // Pass by ref.
                        Expr.compileAddress(actExpr, /* traced := */ false);
                        CG.pop_param(CGType.Addr);
                    }
                }
            } // CASE
        }
    }

    private static void genFloat(T formVal, Expr.T actExpr) {
        Type.T actSemType;
        if (formVal.hasError) {
            CG.load_nil();
            CG.pop_param(CGType.Addr);
        } else {
            AssertError.check(Math.floorMod(Expr.alignment(actExpr), (long) Target.Byte) == 0, "Formal");
            switch (formVal.mode) {
                case mVALUE -> {
                    Expr.compile(actExpr);
                    CG.pop_param(Type.cgType(formVal.type, /* in_memory := */ true));
                }
                case mVAR -> {
                    Expr.compileAddress(actExpr, /* traced := */ true);
                    CG.pop_param(CGType.Addr);
                    Expr.noteWrite(actExpr);
                }
                case mREADONLY -> {
                    actSemType = Expr.typeOf(actExpr);
                    if (Expr.isDesignator(actExpr)
                        && Type.isEqual(formVal.type, actSemType, null)
                        && !PackedType.is(actSemType)) {
                        Expr.compileAddress(actExpr, /* traced := */ false);
                        CG.pop_param(CGType.Addr);
                    } else {
                        Expr.compile(actExpr);
                        genScalarCopy(formVal.type);
                        CG.pop_param(CGType.Addr);
                    }
                }
            }
        } // CASE
    }

    private static void genReference(T formVal, Expr.T actExpr) {
        Type.T actSemType;
        if (formVal.hasError) {
            CG.load_nil();
            CG.pop_param(CGType.Addr);
        } else {
            AssertError.check(Math.floorMod(Expr.alignment(actExpr), (long) Target.Byte) == 0, "Formal");
            switch (formVal.mode) {
                case mVALUE -> {
                    Expr.compile(actExpr);
                    CG.pop_param(Type.cgType(formVal.type, /* in_memory := */ true));
                }
                case mVAR -> {
                    Expr.compileAddress(actExpr, /* traced := */ true);
                    CG.pop_param(CGType.Addr);
                    Expr.noteWrite(actExpr);
                }
                case mREADONLY -> {
                    actSemType = Expr.typeOf(actExpr);
                    if (Expr.isDesignator(actExpr)
                        && Type.isEqual(formVal.type, actSemType, null)
                        && !PackedType.is(actSemType)) { // Pass by reference, lo-level too.
                        Expr.compileAddress(actExpr, /* traced := */ false);
                        CG.pop_param(CGType.Addr);
                    } else { // Pass by value.  Lo-level: copy and pass the copy by ref.
                        Expr.compile(actExpr);
                        // A reference actual could be a NARROW, which, by language
                        //   definition, produces a non-designator, but Expr.Compile
                        //   does not generate a copy, so we do it here.  Are there
                        //   other non-designator reference cases that would not be
                        //   aliasable, and thus not need a copy?
                        genScalarCopy(formVal.type);
                        CG.pop_param(CGType.Addr);
                    }
                }
            } // CASE
        }
    }

    private static void genProcedure(T formVal, Expr.T actExpr, Expr.T proc) {
        Type.T actSemType;
        if (formVal.hasError) {
            CG.load_nil();
            CG.pop_param(CGType.Addr);
        } else {
            AssertError.check(Math.floorMod(Expr.alignment(actExpr), (long) Target.Byte) == 0, "Formal");
            switch (formVal.mode) {
                case mVALUE -> {
                    Expr.compile(actExpr);
                    genClosure(actExpr, proc);
                    CG.pop_param(Type.cgType(formVal.type, /* in_memory := */ true));
                }
                case mVAR -> {
                    Expr.compileAddress(actExpr, /* traced := */ true);
                    CG.pop_param(CGType.Addr);
                    Expr.noteWrite(actExpr);
                }
                case mREADONLY -> {
                    actSemType = Expr.typeOf(actExpr);
                    if (Expr.isDesignator(actExpr)
                        && Type.isEqual(formVal.type, actSemType, null)
                        && !PackedType.is(actSemType)) {
                        Expr.compileAddress(actExpr, /* traced := */ false);
                        CG.pop_param(CGType.Addr);
                    } else {
                        Expr.compile(actExpr);
                        genClosure(actExpr, proc);
                        genScalarCopy(formVal.type);
                        CG.pop_param(CGType.Addr);
                    }
                }
            } // CASE
        }
    }

    private static void genClosure(Expr.T actExpr, Expr.T proc) {
        M3CG.Var tmp;
        Value.T[] proc_v = new Value.T[1];
        long n_elts;
        long ASIZE = M3.target.Address.size;
        if (requiresClosure(actExpr, proc_v)) {
            // actExpr is a nested procedure literal passed by value
            if (isExternalProcedure(proc)) {
                Error.warn(1, "passing nested procedure to external procedure");
            }

            // allocate space for the closure
            n_elts = Math.floorDiv(M3.rt.CL_SIZE + ASIZE - 1, ASIZE);
            tmp = CG.declare_temp(M3.rt.CL_SIZE, M3.target.Address.align,
                                  CGType.Struct, /* in_memory := */ true);

            // and fill it in
            CG.store_addr(tmp, M3.rt.CL_proc);
            CG.load_intt(M3RT.CL_marker_value);
            CG.store_int(M3.target.Integer.cg_type, tmp, M3.rt.CL_marker);
            Procedure.loadStaticLink((Procedure.T) proc_v[0]);
            CG.store_addr(tmp, M3.rt.CL_frame);
            CG.load_addr_of(tmp, 0, M3.target.Address.align);
        }
    }

    /** Port: proc is VAR. */
    private static boolean requiresClosure(Expr.T e, Value.T[] proc) {
        return UserProc.isProcedureLiteral(e, proc) && Procedure.isNested((Procedure.T) proc[0]);
    }

    private static boolean isExternalProcedure(Expr.T e) {
        Value.T[] proc = new Value.T[1];
        return UserProc.isProcedureLiteral(e, proc) && Value.isExternal(proc[0]);
    }

    /**
     * Not merely typed BITS-FOR and a field or element, but byte instructions
     * cannot pass or copy it.
     */
    private static boolean requiresFetch(T formVal, Expr.T actExpr) {
        long actSize;
        Type.Info formRepTypeInfo = new Type.Info();

        if (OpenArrayType.openDepth(Expr.semTypeOf(actExpr)) > 0) {
            // Actual is open array.
            return false;
        }
        actSize = Expr.staticSize(actExpr);
        if (Math.floorMod(actSize, (long) Target.Byte) != 0) {
            return true;
        }
        if (Math.floorMod(Expr.alignment(actExpr), (long) Target.Byte) != 0) {
            return true;
        }
        Type.checkInfo(formVal.repType, formRepTypeInfo);
        if (OpenArrayType.openDepth(formVal.type) > 0) { // Formal is open array.
            return Expr.alignment(actExpr) < formRepTypeInfo.addr_align;
        } else {
            if (Expr.alignment(actExpr) < formRepTypeInfo.alignment) {
                return true;
            } else if (actSize != formRepTypeInfo.size) {
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Compile and copy a struct that lies within a word, leaving the address
     * of the copy on top of the CG stack.  The original struct may be bitpacked.
     * The copy will be word-aligned.
     */
    private static void compileNCopyStructWInWord(T formVal, Expr.T actExpr, long actSize) {
        M3CG.Var actTempVar;
        actTempVar = CG.declare_temp
           (M3.target.Word.size, M3.target.Word.align, M3.target.Word.cg_type,
            /* in_memory := */ true);
        if (formVal.hasError) {
            CG.load_intt(0);
        } else {
            Expr.compile(actExpr);
            AssertError.check(actSize <= M3.target.Word.size, "Formal");
            if (Type.isStructured(formVal.type)) {
                CG.load_indirect(M3.target.Word.cg_type, 0, actSize);
            }
        }
        CG.store_int(M3.target.Word.cg_type, actTempVar);
        if (M3.target.endian == Target.Endian.Big) {
            CG.load_intt(M3.target.Word.size - actSize);
            CG.shift_left(M3.target.Word.cg_type);
        }
        CG.load_addr_of(actTempVar, 0, M3.target.Word.align);
    }

    private static void genSmallSet(T formVal, Expr.T actExpr) {
        Type.T actSemType;
        if (formVal.hasError) { // Error recovery.
            CG.load_nil();
            CG.pop_param(CGType.Addr);
        } else {
            switch (formVal.mode) {
                case mVALUE -> {
                    Expr.compile(actExpr);
                    CG.pop_param(Type.cgType(formVal.type, /* in_memory := */ true));
                }
                case mVAR -> {
                    Expr.compileAddress(actExpr, /* traced := */ true);
                    CG.pop_param(CGType.Addr);
                    Expr.noteWrite(actExpr);
                }
                case mREADONLY -> {
                    actSemType = Expr.typeOf(actExpr);
                    if (Type.isEqual(formVal.type, actSemType, null)
                        && Expr.isDesignator(actExpr)
                        && !PackedType.is(actSemType)) { // Pass by ref.
                        Expr.compileAddress(actExpr, /* traced := */ false);
                        CG.pop_param(CGType.Addr);
                    } else { // Pass by value, lo-level: copy by reference.
                        Expr.compile(actExpr);
                        genScalarCopy(formVal.type);
                        CG.pop_param(CGType.Addr);
                    }
                }
            } // CASE
        }
    }

    private static void genStruct(T formVal, Expr.T actExpr, boolean formIsArray, boolean formIsOpen) {
        Type.T actSemType, actRepType;
        M3CG.Var actTempVar;
        Type.Info actRepTypeInfo = new Type.Info();
        Type.Info formRepTypeInfo = new Type.Info();
        long actStaticSize; // Excluding dope.
        boolean typeOKForByRef = false;
        if (formVal.hasError) {
            CG.load_nil();
            CG.pop_param(CGType.Addr);
        } else {
            actRepType = Expr.repTypeOf(actExpr);
            // ^May be a BITS-FOR, type, but only if actExpr is a field or element.
            Type.checkInfo(actRepType, actRepTypeInfo);
            Type.checkInfo(formVal.repType, formRepTypeInfo);

            switch (formVal.mode) {
                case mVALUE -> {
                    AssertError.check(formVal.tempCGVal == null, "Formal");
                    if (requiresFetch(formVal, actExpr)) {
                        // Actual is bitpacked, which implies not open array.
                        //   Formal is never bitpacked, but could be open.
                        // Make a byte-aligned copy of bitpacked struct,
                        //   and pass it by value.
                        compileNCopyStructWInWord(formVal, actExpr, actRepTypeInfo.size);
                    } else { // No bit packing.
                        Expr.compile(actExpr);
                    }
                    redepthArray(formVal.repType, actRepType, 0);
                    if (formIsOpen) { // Pass address.  Copy will be made by callee prolog.
                        CG.pop_param(CGType.Addr);
                    } else {
                        Type.compile(formVal.repType);
                        // Port: the uid is a 32-bit value (M3FP.ToInt).
                        CG.pop_struct
                            ((int) Type.globalUID(formVal.repType), formRepTypeInfo.size,
                             formRepTypeInfo.alignment);
                    }

                }
                case mVAR -> {
                    AssertError.check(formVal.tempCGVal == null, "Formal");
                    Expr.compileAddress(actExpr, /* traced := */ true);
                    redepthArray(formVal.repType, actRepType, 0);
                    CG.pop_param(CGType.Addr);
                    Expr.noteWrite(actExpr);

                }
                case mREADONLY -> {
                    actSemType = Expr.typeOf(actExpr);
                    typeOKForByRef
                        = (formIsArray
                             || Type.isEqual(formVal.type, actSemType, null)
                           ) && !PackedType.is(actSemType);
                    if (typeOKForByRef && Expr.isDesignator(actExpr)) {
                        // Pass by reference (2.3.2).  Lo-level, pass by ref also.
                        AssertError.check(formVal.tempCGVal == null, "Formal");
                        Expr.compileAddress(actExpr, /* traced := */ false);
                        redepthArray(formVal.repType, actRepType, 0);
                        CG.pop_param(CGType.Addr);

                    } else if (typeOKForByRef && Expr.isAnonConstructor(actExpr)
                        // ^A constructor need not be a designator to avoid copying.
                        //   This includes function results; constructors; and fields,
                        //   elements, and SUBARRAYs thereof.  Any of these need not be
                        //   copied because this will be its only use.  The formal will
                        //   become a designator (per 2.6.3), but this is OK because it
                        //   will be stored in memory.  This makes it possible to test
                        //   where a constant open array constructor has placed its dope.
                    ) { // Pass by reference (2.3.2).  Lo-level, pass by ref too, but
                        //   do not treat as LValue.
                        AssertError.check(formVal.tempCGVal == null, "Formal");
                        Expr.compile(actExpr);
                        redepthArray(formVal.repType, actRepType, 0);
                        CG.pop_param(CGType.Addr);

                    } else { // Pass by value (2.3.2).  Lo-level, pass a copy by reference.
                        if (requiresFetch(formVal, actExpr)
                            // ^Actual is bitpacked, which implies it is not an open array.
                            //   Formals are never bitpacked.
                        ) {
                            AssertError.check(formVal.tempCGVal == null, "Formal");
                            // Copy within-word value and pass it by reference.
                            compileNCopyStructWInWord(formVal, actExpr, actRepTypeInfo.size);
                            redepthArray(formVal.repType, actRepType, 0);
                            // ^Formal could be open.
                            CG.pop_param(CGType.Addr);
                        } else { // Actual is byte-sized and aligned.  Pass by value (2.3.2).
                                 //   At lo-level, pass a copy by reference.
                            if (!OpenArrayType.is(formVal.repType)) {
                                // Formal gives static size for the copy.
                                //   Could be any kind of struct.
                                AssertError.check(formVal.tempCGVal == null, "Formal");
                                actTempVar = CG.declare_temp
                                  (formRepTypeInfo.size, formRepTypeInfo.alignment,
                                   CGType.Struct, /* in_memory := */ true);
                                // ^Elements only, if array.
                                CG.load_addr_of(actTempVar, 0, formRepTypeInfo.alignment);
                                Expr.compile(actExpr);
                                redepthArray(formVal.repType, actRepType, 0);
                                CG.copy(formRepTypeInfo.size, /* overlap := */ false);
                                CG.load_addr_of(actTempVar, 0, formRepTypeInfo.alignment);
                                CG.pop_param(CGType.Addr);

                            } else { // Formal is open array.
                                actStaticSize = Expr.staticSize(actExpr);
                                if (actStaticSize > 0) {
                                    // Formal is open array.  Actual could be open, but still
                                    //   gives static size for the copy.
                                    AssertError.check(formVal.tempCGVal == null, "Formal");
                                    Expr.compile(actExpr);
                                    redepthArray(formVal.repType, actRepType, actStaticSize);
                                    CG.pop_param(CGType.Addr);
                                } else { // No static size available.
                                    AssertError.check(formVal.tempCGVal != null, "Formal");
                                    CG.push(formVal.tempCGVal);
                                    CG.pop_param(CGType.Addr);
                                }
                            }
                        }
                    }
                }
            } // CASE
        }
    }

    /**
     * PRE: Address of actual is pushed on CG stack.
     * Change TOS array from actType's open depth to formType's open depth, while
     * preserving shape and elements.  Generate any necessary RT length checks.
     * If eltsCopySize &gt; 0, copy the elements into a new temporary with static
     * size eltsCopySize.  Otherwise, make no copy of elements.
     */
    private static void redepthArray(Type.T formType, Type.T actType, long eltsCopySize) {
        long formDepth, actDepth;
        // Port: eltsAlign is read only after it is assigned; Java needs an
        // initial value.
        long eltsAlign = 0;
        Type.T actInnerType, formInnerType;
        Type.T[] indexType = new Type.T[1], eltType = new Type.T[1];
        M3CG.Var dopeTempVar = null, eltsTempVar = null;
        CG.Val actVal = null;
        Type.Info actTypeInfo = new Type.Info(), formTypeInfo = new Type.Info();
        boolean b;
        if (!ArrayType.is(formType)) {
            return;
        }
        formDepth = OpenArrayType.openDepth(formType);
        actDepth = OpenArrayType.openDepth(actType);
        if (eltsCopySize == 0 && formDepth == actDepth) {
            return;
        }
        Type.checkInfo(actType, actTypeInfo);
        Type.checkInfo(formType, formTypeInfo);
        if (eltsCopySize > 0) { // Make a copy of elements.
            actVal = CG.pop();
            eltsAlign = Math.max(actTypeInfo.alignment, formTypeInfo.alignment);
            eltsTempVar
                = CG.declare_temp
                     (eltsCopySize, eltsAlign, CGType.Struct, /* in_memory := */ true);
            CG.load_addr_of(eltsTempVar, 0, eltsAlign);
            CG.push(actVal);
            if (actDepth > 0) {
                CG.open_elt_ptr(actTypeInfo.alignment);
            }
            CG.copy(eltsCopySize, /* overlap := */ false);
        }

        if (eltsCopySize > 0 || formDepth > actDepth) { // New dope needed.
            dopeTempVar = OpenArrayType.declareDopeTemp(formType);

            // Set new dope's elements pointer.
            if (eltsTempVar == null) {
                if (actVal == null) {
                    actVal = CG.pop();
                }
                CG.push(actVal);
                if (actDepth > 0) {
                    CG.open_elt_ptr(actTypeInfo.alignment);
                }
            } else {
                CG.load_addr_of(eltsTempVar, M3.rt.OA_elt_ptr, eltsAlign);
            }
            CG.store_addr(dopeTempVar, M3.rt.OA_elt_ptr);

            // Copy actual/formal-shared open lengths from old to new dope.
            for (long i = 0, last = Math.min(actDepth, formDepth) - 1; i <= last; i++) {
                if (actVal == null) {
                    actVal = CG.pop();
                }
                CG.push(actVal);
                CG.open_size(i);
                CG.store_int
                    (M3.target.Integer.cg_type, dopeTempVar,
                     M3.rt.OA_sizes + i * M3.target.Integer.pack);
            }
        }

        if (formDepth > actDepth) {
            AssertError.check(dopeTempVar != null, "Formal");

            // Skip actual/formal-shared open dimensions of actual type.
            actInnerType = actType;
            for (long i = 0; i <= actDepth - 1; i++) {
                b = ArrayType.split(actInnerType, indexType, eltType);
                AssertError.check(b, "Formal");
                AssertError.check(indexType[0] == null, "Formal");
                actInnerType = eltType[0];
            }

            // Store additional lengths from actual type into new dope.
            for (long i = actDepth; i <= formDepth - 1; i++) {
                b = ArrayType.split(actInnerType, indexType, eltType);
                AssertError.check(b, "Formal");
                AssertError.check(indexType[0] != null, "Formal");
                CG.load_integer(M3.target.Integer.cg_type, Type.number(indexType[0]));
                CG.store_int(M3.target.Integer.cg_type,
                             dopeTempVar, M3.rt.OA_sizes + i * M3.target.Integer.pack);
                actInnerType = eltType[0];
            }
        } else { // formDepth <= actDepth

            // Gen RT checks of deeper actual dimensions against formal's type.
            formInnerType = OpenArrayType.nonopenEltType(formType);
            for (long i = formDepth; i <= actDepth - 1; i++) {
                b = ArrayType.split(formInnerType, indexType, eltType);
                AssertError.check(b, "Formal");
                AssertError.check(indexType[0] != null, "Formal");
                if (actVal == null) {
                    actVal = CG.pop();
                }
                CG.push(actVal);
                CG.open_size(i);
                CG.load_integer(M3.target.Integer.cg_type, Type.number(indexType[0]));
                CG.check_eq(M3.target.Integer.cg_type,
                            M3CG.RuntimeError.IncompatibleArrayShape);
                formInnerType = eltType[0];
            }
        }

        if (dopeTempVar == null) { // Use actual's dope.
            if (actVal != null) {
                CG.push(actVal);
            } else { // Actual address is still undisturbed on TOS.  Use it.
            }
            if (actDepth > 0 && formDepth <= 0) {
                CG.open_elt_ptr(actTypeInfo.alignment);
            }
        } else {
            if (actVal == null) { // Actual address is still on TOS.  Discard it.
                CG.discard(CGType.Addr);
            }
            CG.load_addr_of(dopeTempVar, 0, M3.target.Address.align);
        }
        CG.forceStacked();

        CG.free(actVal);
    }

    /**
     * Load the address of a temporary that holds the value of type 't'
     * that is currently on top of the stack.
     *
     * <p>PRE: Tos is addr of a variable of type 'type'.
     * POST: TOS replaced by addr of a local copy thereof.
     */
    //EXPORTED
    public static void genCopy(Type.T type) {
        Type.Info info = new Type.Info();
        M3CG.Var copyVar;
        int id;
        Type.checkInfo(type, info);
        // Port: the uid is a 32-bit value (M3FP.ToInt).
        id = (int) Type.globalUID(type);
        if (Type.isStructured(type)) {
            copyVar = CG.declare_local(M3ID.NoID, info.size, info.alignment,
                                       CGType.Struct, id, /* in_memory := */ true,
                                       /* up_level := */ false, /* f := */ CG.Never);
            CG.load_addr_of(copyVar, 0, info.alignment);
            CG.swap();
            CG.copy(info.size, /* overlap := */ false);
        } else {
            copyVar = CG.declare_local(M3ID.NoID, info.size, info.alignment,
                                       info.mem_type, id, /* in_memory := */ true,
                                       /* up_level := */ false, /* f := */ CG.Never);
            CG.store(copyVar, 0, info.size, info.alignment, info.stk_type);
        }
        CG.load_addr_of(copyVar, 0, info.alignment);
    }

    static {
        // Port: RTParams.IsPresent ("m3front-debug-formal") is FALSE.
        debug = false;
    }
}
