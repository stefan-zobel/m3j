// Java port of CM3's m3-sys/m3front/src/values/Constant.i3 and Constant.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3ID;
import m3j.middle.Target.CGType;

// File: Constant.i3
// Last Modified On Tue Dec 20 14:57:43 PST 1994 By kalsow
//      Modified On Thu Dec 13 01:35:56 1990 By muller

// File: Constant.m3
// Last Modified On Tue Feb 28 16:58:43 PST 1995 By kalsow

public final class Constant {
    private Constant() {}

    public static class T extends Value.T {
        public Type.T tipe;
        public Type.T repType;
        public Expr.T valExpr;
        public String qualName;
        public long size;
        /**
         * Of a pointer, located within the static variable area, to the
         * constant's value, which is located in the static constant area.
         */
        public long offset;
        /**
         * Of space in the static constant area for the constant's value.
         * If constant has open array representation, this will be
         * for dope only.
         */
        public long coffset;
        public long calign;
        public boolean structured;
        public boolean gen_init;

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
            Constant.load(this);
        }

        @Override
        public boolean declare() {
            return declarer(this);
        }

        @Override
        public void const_init() {
            constInit(this);
        }

        @Override
        public Expr.T toExpr() {
            return Constant.toExpr(this);
        }

        @Override
        public Type.T typeOf() {
            return Constant.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return Constant.repTypeOf(this);
        }

        @Override
        public long add_fp_tag(M3.FPInfo x) {
            return addFPTag(this, x);
        }

        @Override
        public Type.T fp_type() {
            return Constant.typeOf(this);
        }

        // Port: the overrides need_init := ValueRep.Never, lang_init,
        // user_init := ValueRep.NoInit, toType := ValueRep.NoType and base
        // := ValueRep.Self restate the inherited methods.
    }

    // EXPORTED:
    public static void parseDecl(Decl.Attributes att) {
        // Port: TK = Token.T.
        T t;
        int id;
        Scanner.match(Token.T.tCONST);
        while ((Scanner.cur.token == Token.T.tIDENT)) {
            id = Scanner.matchID();
            t = create(id);
            t.unused = att.isUnused;
            t.obsolete = att.isObsolete;
            if ((Scanner.cur.token == Token.T.tCOLON)) {
                Scanner.getToken(); // :
                t.tipe = Type.parse();
            }
            Scanner.match(Token.T.tEQUAL);
            t.valExpr = Expr.parse();
            Scope.insert(t);
            Scanner.match(Token.T.tSEMI);
        }
    }

    private static T create(int name) {
        T t;
        t = new T();
        ValueRep.init(t, name, Value.Class.Expr);
        t.readonly   = true;
        t.tipe       = null;
        t.valExpr    = null;
        t.offset     = 0;
        t.coffset    = 0;
        t.calign     = 0;
        t.structured = false;
        t.gen_init   = false;
        return t;
    }

    /** Declare in the current scope. */
    // EXPORTED:
    public static Constant.T declare(String name, Expr.T valExpr, boolean reserved) {
        T t;
        t = create(M3ID.add(M3.ids, name));
        t.tipe = Expr.typeOf(valExpr);
        t.valExpr = valExpr;
        Scope.insert(t);
        if ((reserved)) {
            Scanner.noteReserved(t.name, t);
        }
        return t;
    }

    /** Declare in the global scope. */
    // EXPORTED:
    public static Constant.T declareGlobal(String name, Expr.T valExpr, boolean reserved) {
        T t;
        t = create(M3ID.add(M3.ids, name));
        t.tipe = Expr.typeOf(valExpr);
        t.valExpr = valExpr;
        Scope.insertScope(t, Module.exportScope(Module.current()));
        if ((reserved)) {
            Scanner.noteReserved(t.name, t);
        }
        return t;
    }

    public static Constant.T declareGlobal(String name, Expr.T valExpr) {
        return declareGlobal(name, valExpr, false);
    }

    // Externally dispatched-to:
    private static Type.T typeOf(T t) {
        if ((t.tipe == null)) {
            t.tipe = Expr.typeOf(t.valExpr);
        }
        return t.tipe;
    }

    // Externally dispatched-to:
    private static Type.T repTypeOf(T t) {
        if (t.repType == null) {
            t.repType = Expr.repTypeOf(t.valExpr);
        }
        return t.repType;
    }

    // Externally dispatched-to:
    private static void check(T t, M3.CheckState cs) {
        Expr.T e;
        Value.T[] proc = new Value.T[1];
        long[] n_errs0 = new long[1], n_errs1 = new long[1], n_warns = new long[1];
        Error.count(n_errs0, n_warns);
        Expr.typeCheck(t.valExpr, cs);
        if (t.tipe == null) {
            t.tipe = Expr.typeOf(t.valExpr);
        }
        t.tipe = Type.check(t.tipe);
        Error.count(n_errs1, n_warns);

        if (ProcType.is(t.tipe)
            && UserProc.isProcedureLiteral(t.valExpr, proc)
            && Procedure.isNested((Procedure.T) proc[0])) {
            Error.msg("Nested procedures are not constant (2.6.15).");
        }

        if ((t.tipe == ErrType.T)) {
            // there's no way that we can evaluate the constant
            t.structured = false;
            if ((n_errs1[0] <= n_errs0[0])) {
                // no error was generated, but we don't have a type!
                Error.msg("Value is not a constant expression (2.4.2).");
            }
        } else if (t.valExpr != null) {
            t.valExpr.isNamedConst = true;
            AssignStmt.check(t.tipe, t.valExpr, cs);
            e = Expr.constValue(t.valExpr);
            // N.B. ^This will strip away both a NamedExpr.T and a ConsExpr.T from
            //       above a constant expression, importantly, an ArrayExpr.T.
            if (e == null) {
                Error.msg("Value of CONST is not constant (2.4.2, 2.6.15).");
            } else {
                e.isNamedConst = true;
                ArrayExpr.noteTargetType(e, t.tipe);
                t.valExpr = e;
            }
            t.structured = Type.isStructured(t.tipe);
        }
        t.qualName = Value.globalName(t);
    }

    // Externally dispatched-to:
    private static void setGlobals(T t) {
        long align, depth;
        Type.T repType;
        Type.Info info = new Type.Info();
        int valID, addrID;
        // Type.SetGlobals (t.tipe);
        if ((t.offset != 0) || (!t.structured)) {
            return;
        }

        repType = Expr.repTypeOf(t.valExpr);
        Type.checkInfo(repType, info);
        t.size  = info.size;
        align = info.alignment;
        depth = OpenArrayType.openDepth(repType);

        if ((depth > 0)) { // repType is an open array
            // Set up to allocate space below for the dope only.
            // See ArrayExpr.GenLiteral for allocation of elements' space.
            t.size = M3.target.Address.pack + depth * M3.target.Integer.pack;
            align = Math.max(M3.target.Address.align, M3.target.Integer.align);
        }

        t.calign  = align;
        // TODO: Eliminate duplicate copies of same value.
        valID = M3ID.add(M3.ids, t.qualName);
        t.coffset = Module.allocate(t.size, align, true, "constant ", /* id := */ valID);
        addrID = M3ID.add(M3.ids, t.qualName + "_ADDR_");
        t.offset  = Module.allocate(M3.target.Address.size, M3.target.Address.align,
                                    false, "constant", /* id := */ addrID);
    }

    /**
     * Note: because a named constant may be the default value for
     * a procedure parameter, it is possible for a structured constant
     * to be used in a compilation unit without anywhere mentioning
     * its name => its use would not be detected => it wouldn't be
     * imported.  To prevent this, we force the import here by calling
     * Scope.ToUnit.
     */
    // Externally dispatched-to:
    private static void load(T t) {
        if ((t.structured)) {
            setGlobals(t);
            Expr.checkUseFailure(t.valExpr);
            if ((t.imported)) {
                Module.loadGlobalAddr((Module.T) Scope.toUnit(t), t.offset, /* is_const := */ false);
                CG.load_indirect(CGType.Addr, 0, M3.target.Address.size);
                CG.boost_addr_alignment(t.calign);
            } else {
                Module.loadGlobalAddr((Module.T) Scope.toUnit(t), t.coffset, /* is_const := */ true);
            }
        } else {
            Expr.prep(t.valExpr);
            Expr.checkUseFailure(t.valExpr);
            Expr.compile(t.valExpr);
        }
    }

    // Externally dispatched-to:
    private static boolean declarer(T t) {
        int typeUID;
        long size, depth;
        Type.Info info = new Type.Info();
        if ((t.exported)) {
            Type.compile(t.tipe);
        }
        if ((!t.structured)) {
            return true;
        }

        Type.checkInfo(t.tipe, info);
        Type.compile(t.tipe);
        // Port: a type UID is a 32-bit CG.TypeUID.
        typeUID  = (int) Type.globalUID(t.tipe);
        depth = OpenArrayType.openDepth(t.tipe);

        if ((t.imported)) {
            Scope.toUnit(t); // force the module to be imported
        } else {
            setGlobals(t);
            CG.declare_global_field(t.name, t.offset, M3.target.Address.size,
                                    CG.declare_indirect(typeUID), /* is_const := */ false);
            if (depth > 0) { // t.tipe is an open array
                size = M3.target.Address.pack + depth * M3.target.Integer.pack;
            } else {
                size  = info.size;
            }
            CG.declare_global_field(t.name, t.coffset, size, typeUID, /* is_const := */ true);
            t.gen_init = true;
        }

        return true;
    }

    // Externally dispatched-to:
    private static void constInit(T t) {
        if (t.gen_init) {
            t.gen_init = false;
            CG.comment(t.offset, false, "Address of constant ", t.qualName);
            CG.init_var(t.offset, Module.globalData(true), t.coffset, false);

            CG.comment(t.coffset, true, "Contents of constant ", t.qualName);
            Expr.prepLiteral(t.valExpr, t.tipe, true);
            Expr.genLiteral(t.valExpr, t.coffset, t.tipe, true);
        }
    }

    // Externally dispatched-to:
    private static Expr.T toExpr(T t) {
        return t.valExpr;
    }

    // Externally dispatched-to:
    private static long addFPTag(T t, M3.FPInfo x) {
        ValueRep.fpStart(t, x, "CONST ", t.offset, /* global := */ true);
        M3Buf.putText(x.buf, " = ");
        Expr.genFPLiteral(t.valExpr, x.buf);
        return 1;
    }
}
