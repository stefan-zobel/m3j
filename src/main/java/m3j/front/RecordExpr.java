// Java port of CM3's m3-sys/m3front/src/exprs/RecordExpr.i3 and RecordExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3CG;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: RecordExpr.i3
// Last Modified On Fri Jun 24 08:48:36 PDT 1994 By kalsow

// File: RecordExpr.m3
// Last modified on Tue Jun 20 15:47:54 PDT 1995 by kalsow
//      modified on Thu Jun 15 12:45:05 PDT 1995 by ericv
//      modified on Wed Nov 28 02:47:43 1990 by muller

/** A record constructor. */
public final class RecordExpr {
    private RecordExpr() {}

    public static final class Info {
        public Value.T field;
        public Type.T type;
        public Expr.T expr;
        public int name;
        public boolean done;

        Info() {}

        Info copy() {
            Info r = new Info();
            r.field = field;
            r.type = type;
            r.expr = expr;
            r.name = name;
            r.done = done;
            return r;
        }
    }

    public static final class P extends Expr.T {
        public Type.T tipe;
        // TODO: Eliminate p.type/p.tipe redundancy.
        public Expr.T[] args = null;
        public Info[] map = null;
        public Expr.T constExpr;
        public CG.Val finalVal = null;
        public long finalValUseCt = 0;
        public long globalOffset = Long.MIN_VALUE; // Means uninitialized.
        public String RTErrorMsg = null;
        public M3CG.RuntimeError RTErrorCode = M3CG.RuntimeError.Unknown;
        public boolean evalAttempted = false;
        // TRUE even if Evaluate was called unsuccessfully.
        public boolean is_const = false;
        // Meaningless if NOT evalAttempted.  Otherwise, Evaluate was successful.
        public boolean broken = false;
        public boolean prepped = false;

        @Override
        public void check(M3.CheckState cs) {
            RecordExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            RecordExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            RecordExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepLV(boolean traced) {
            RecordExpr.prepLV(this, traced);
        }

        @Override
        public void compileLV(boolean traced, boolean StaticOnly) {
            RecordExpr.compileLV(this, traced, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return RecordExpr.evaluate(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public boolean isZeroes(boolean lhs) {
            return RecordExpr.isZeroes(this, lhs);
        }

        @Override
        public void genFPLiteral(M3Buf.T mbuf) {
            RecordExpr.genFPLiteral(this, mbuf);
        }

        @Override
        public void prepLiteral(Type.T type, boolean is_const) {
            RecordExpr.prepLiteral(this, type, is_const);
        }

        @Override
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            RecordExpr.genLiteral(this, offset, type, is_const);
        }

        @Override
        public boolean usesAssignProtocol() {
            return RecordExpr.usesAssignProtocol(this);
        }

        @Override
        public boolean checkUseFailure() {
            return RecordExpr.checkUseFailure(this);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType, prepBR,
        // compileBR := ExprRep.NotBoolean, getBounds := ExprRep.NoBounds,
        // isWritable, isDesignator := ExprRep.IsNever and note_write :=
        // ExprRep.NotWritable restate the inherited methods.
    }

    public static Expr.T new_(Type.T type, Expr.T[] args) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.directAssignableType = true;

        p.type = type;
        p.repType = Type.stripPacked(type);
        p.tipe = type;
        p.args = args;
        p.map = null;
        p.constExpr = null;
        p.finalVal = null;
        p.finalValUseCt = 0;
        p.evalAttempted = false;
        p.is_const = false;
        p.prepped = false;
        p.checked = false;
        p.RTErrorCode = M3CG.RuntimeError.Unknown;
        p.RTErrorMsg = null;
        return p;
    }

    private static Expr.T copy(P p) {
        P new_;
        new_ = new P();

        // Fields inherited from Expr.T:
        new_.origin = p.origin;
        new_.type = p.type;
        new_.align = p.align;
        new_.checked = p.checked;
        new_.directAssignableType = p.directAssignableType;
        new_.doDirectAssign = p.doDirectAssign;
        new_.isNamedConst = p.isNamedConst;

        // Fields of P:
        new_.directAssignableType = p.directAssignableType;
        new_.type = p.type;
        new_.repType = p.repType;
        new_.tipe = p.tipe;
        new_.args = p.args;
        new_.map = p.map;
        new_.constExpr = p.constExpr;
        new_.finalVal = p.finalVal;
        new_.finalValUseCt = p.finalValUseCt;
        new_.evalAttempted = p.evalAttempted;
        new_.is_const = p.is_const;
        new_.prepped = p.prepped;
        new_.checked = p.checked;
        new_.RTErrorCode = p.RTErrorCode;
        new_.RTErrorMsg = p.RTErrorMsg;
        new_.broken = p.broken;
        new_.globalOffset = p.globalOffset;
        // Sic: returns p, not the new copy.
        return p;
    }

    /** Purely syntactic. Will not look through a ConsExpr. */
    public static boolean is(Expr.T e) {
        // Port: TYPECODE (NIL) is not TYPECODE (P).
        return (e != null && e.getClass() == P.class);
    }

    public static boolean qualify(Expr.T record_, int id, Expr.T[] e) {
        // Port: the parameters are named record (here record_), id and
        // result (here e).
        P p;
        Value.T[] val = new Value.T[1];
        Field.Info fieldInfo = new Field.Info();
        Expr.T argExpr;
        int[] argId = new int[1];
        Expr.T[] splitExpr = new Expr.T[1];
        if (record_ == null) {
            return false;
        } else if (record_ instanceof P x) {
            p = x;
        } else {
            return false;
        }
        if (!RecordType.lookUp(p.tipe, id, val)) {
            return false;
        }
        Field.split(val[0], fieldInfo);
        for (int i = 0; i <= p.args.length - 1; i++) {
            argExpr = p.args[i]; // Don't use a folded value of field.
            if ((KeywordExpr.split(argExpr, argId, splitExpr))) {
                if ((argId[0] == id)) {
                    e[0] = splitExpr[0];
                    return true;
                }
            } else if ((i == fieldInfo.index)) {
                e[0] = argExpr;
                return true;
            }
        }
        if (fieldInfo.dfault != null) {
            e[0] = Expr.constValue(fieldInfo.dfault);
            return e[0] != null;
        }
        return false;
    }

    private static void mergeRTError(P p, M3CG.RuntimeError Code, String Msg) {
        if (p.RTErrorMsg == null) {
            p.RTErrorCode = Code;
            p.RTErrorMsg = Msg;
        }
    }

    // Externally dispatched-to:
    private static void check(P p, M3.CheckState cs) {
        long fieldCt;
        long fieldNo;
        Expr.T e;
        Expr.T[] splitExpr = new Expr.T[1];
        Expr.T[] dfault = new Expr.T[1];
        Value.T[] fieldList = new Value.T[1];
        Value.T v;
        int[] Id = new int[1];
        Field.Info fieldInfo = new Field.Info();
        boolean posOK;
        M3CG.RuntimeError[] RTErrorCode = new M3CG.RuntimeError[1];
        String[] RTErrorMsg = new String[1];
        p.tipe = Type.check(p.tipe);
        for (int i = 0; i <= p.args.length - 1; i++) {
            Expr.typeCheck(p.args[i], cs);
        }
        p.type = p.tipe;
        if (!RecordType.split(p.tipe, fieldList)) {
            // CHECK: Can't we ASSERT FALSE here?
            Error.msg("Record constructor must specify a record type (2.6.8)");
            p.broken = true;
            p.checked = true;
            return;
        }

        // Count the fields of the record type.
        v = fieldList[0];
        fieldCt = 0;
        while ((v != null)) {
            fieldCt++;
            v = v.next;
        }

        // build vectors to map field numbers (of the record type) to Info nodes.
        p.map = new Info[(int) fieldCt];
        for (int k = 0; k < p.map.length; k++) {
            p.map[k] = new Info();
        }
        v = fieldList[0];
        fieldNo = 0;
        while ((v != null)) {
            Field.split(v, fieldInfo);
            Info z = p.map[(int) fieldNo];
            z.field = v;
            z.name = fieldInfo.name;
            z.type = fieldInfo.type;
            z.expr = fieldInfo.dfault;
            z.done = false;
            v = v.next;
            fieldNo++;
        }
        posOK = true;
        evaluate(p); // Fold arguments, if p is constant.

        for (int i = 0; i <= p.args.length - 1; i++) {
            e = p.args[i];
            if (RangeExpr.split(e, splitExpr, dfault)) {
                Error.msg("Range expressions not allowed in record constructors (2.6.8).");
                p.broken = true;
            }

            if (KeywordExpr.split(e, Id, splitExpr)) {
                posOK = false;
                fieldNo = 0;
                e = splitExpr[0];
                while (true) {
                    if ((fieldNo >= fieldCt)) {
                        Error.ID(Id[0], "Unknown field name in record constructor (2.6.8).");
                        fieldNo = i;
                        p.broken = true;
                        break;
                    }
                    if ((p.map[(int) fieldNo].name == Id[0])) {
                        break;
                    }
                    fieldNo++;
                }
            } else { // positional parameter
                if ((!posOK)) {
                    Error.msg("Positional values must precede keyword values (2.6.8).");
                }
                if ((i >= fieldCt)) {
                    Error.msg("Too many values in record constructor (2.6.8).");
                    fieldNo = fieldCt - 1;
                    p.broken = true;
                } else {
                    fieldNo = i;
                }
            }

            if ((0 <= fieldNo) && (fieldNo < fieldCt)) {
                Info z = p.map[(int) fieldNo];
                if ((z.done)) {
                    Error.ID(z.name, "Record constructor field previously specified (2.6.8).");
                }
                z.done = true;
                if (!Type.isAssignable(z.type, Expr.typeOf(e))) {
                    Error.ID(z.name,
                             "Expression is not assignable to record constructor field  (2.6.8).");
                } else {
                    ArrayExpr.noteUseTargetVar(e);

                    AssignStmt.checkStaticRTErrExec(z.type, e, cs,
                        /* VAR Code := */ RTErrorCode, /* VAR Msg := */ RTErrorMsg);
                    mergeRTError(p, RTErrorCode[0], RTErrorMsg[0]);
                    z.expr = e;
                }
            } else {
                // some other error, so don't even try
            }
        } // FOR args

        for (fieldNo = 0; fieldNo <= fieldCt - 1; fieldNo++) {
            Info z = p.map[(int) fieldNo];
            if ((!z.done) && (z.expr == null)) {
                Error.ID(z.name, "No value specified for record constructor field (2.6.8).");
            }
        }
        p.checked = true;
    }

    // Externally dispatched-to:
    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        // TODO: This requires exact keyword/isPositional/positionalOrder/default match
        //       to return TRUE.  This is probably too picky.
        P b;
        if (e == null) {
            return false;
        } else if (e instanceof P p) {
            b = p;
        } else {
            return false;
        }
        if ((!Type.isEqual(a.tipe, b.tipe, x))
            || ((a.args == null) != (b.args == null))
            || ((a.args != null) && (a.args.length != b.args.length))) {
            return false;
        }
        for (int i = 0; i <= a.args.length - 1; i++) {
            if (!Expr.isEqual(a.args[i], b.args[i], x)) {
                return false;
            }
        }
        return true;
    }

    // Externally dispatched-to:
    private static void needsAddress(P p) {
        // yep, we already know all records get memory addresses
    }

    // Externally dispatched-to:
    private static boolean usesAssignProtocol(P p) {
        // PRE: p has been checked but not Prepped.
        AssertError.check(p.checked, "RecordExpr");
        evaluate(p);
        if (p.is_const) {
            return false;
        }
        return p.doDirectAssign;
    }

    // Externally dispatched-to:
    private static void prep(P p) {
        prepLV(p, /* traced := */ false);
    }

    // Externally dispatched-to:
    private static void prepLV(P p, boolean traced) {
        if (!usesAssignProtocol(p)) {
            innerPrepLV(p, traced, /* usesAssignProtocol := */ false);
        }
        // ELSE postpone InnerPrepLV until Compile, when LHS will have been pushed.
    }

    private static void innerPrepLV(P p, boolean traced, boolean usesAssignProtocol) {
        // PRE: IF usesAssignProtocol (p), LHS address is compiled and on top of the
        //      CG stack, for us to use.
        Type.Info info = new Type.Info();
        Field.Info field = new Field.Info();
        M3CG.Var resultVar = null;
        p.finalValUseCt++;
        if ((p.finalVal != null) && (p.finalValUseCt > 1)) {
            // We already did this before.
            AssertError.check(!usesAssignProtocol, "RecordExpr");
            p.prepped = true;
            return;
        }
        Type.checkInfo(p.type, info);

        if (p.is_const) {
            p.prepped = true;
            return;
        }
        if (usesAssignProtocol) { // lhs addr is on tos.
            AssertError.check(!traced, "RecordExpr"); // CHECKME?
            p.finalVal = CG.pop();
        } else {
            resultVar = CG.declare_temp(info.size, info.alignment,
                                        CGType.Struct,
                                        /* in_memory := */ true);
        }

        for (int i = 0; i <= p.map.length - 1; i++) {
            Info z = p.map[i];
            Field.split(z.field, field);
            AssignStmt.prepForEmit(field.type, z.expr, /* initializing := */ true);
            if (usesAssignProtocol) {
                CG.push(p.finalVal);
                if ((field.offset != 0)) {
                    CG.add_offset(field.offset);
                }
            } else {
                CG.load_addr_of(resultVar, field.offset, info.alignment);
            }
            // Port: lhs_align is the default Target.Byte.
            AssignStmt.doEmit(field.type, z.expr, Target.Byte,
                              /* initializing := */ true);
        }

        if (usesAssignProtocol) {
            // result is already in p.finalVal
        } else {
            CG.load_addr_of_temp(resultVar, 0, info.alignment);
            p.finalVal = CG.pop();
        }
        p.prepped = true;
    }

    // Externally dispatched-to:
    private static void compile(P p, boolean StaticOnly) {
        compileLV(p, /* traced := */ false, /* StaticOnly := */ StaticOnly);
    }

    // Externally dispatched-to:
    private static void compileLV(P p, boolean traced, boolean StaticOnly) {
        Type.Info info = new Type.Info();
        AssertError.check(!StaticOnly, "RecordExpr");
        // NOTE^ If ever duplicate static records are removed as for arrays, this
        //  will need to change.
        if (usesAssignProtocol(p)) {
            // InnerPrep was postponed until now.
            innerPrepLV(p, traced, /* usesAssignProtocol := */ true);
        }
        if ((p.is_const)) {
            Type.checkInfo(p.type, info);
            p.globalOffset
                = Module.allocate(info.size, info.alignment, true, "*recordConstructor*");
            prepLiteral(p, p.tipe, true);
            genLiteral(p, p.globalOffset, p.tipe, true);
            CG.load_addr_of(Module.globalData(true), p.globalOffset, info.alignment);
        } else {
            CG.push(p.finalVal);
            p.finalValUseCt--;
            if ((p.finalValUseCt <= 0)) {
                CG.free(p.finalVal);
                p.finalVal = null;
            }
        }
    }

    // Externally dispatched-to:
    private static Expr.T evaluate(P p) {
        // Return a constant expr if p is constant, otherwise NIL.

        P new_;
        Expr.T[] constArgs = null;
        boolean anArgFolded;
        if (p.evalAttempted) {
            return p.constExpr;
        } else {
            p.evalAttempted = true;
            constArgs = new Expr.T[p.args.length];
            p.is_const = true;
            anArgFolded = false;
            for (int i = 0; i <= p.args.length - 1; i++) {
                constArgs[i] = Expr.constValue(p.args[i]);
                if (constArgs[i] == null) {
                    p.constExpr = null;
                    p.is_const = false;
                    return null;
                } else if (constArgs[i] != p.args[i]) {
                    anArgFolded = true;
                }
            }
            if (anArgFolded) {
                // Port: Copy returns p (see there), which is a P.
                new_ = (P) copy(p);
                new_.args = constArgs;
                p.constExpr = new_;
                return new_;
            } else {
                p.constExpr = p;
                return p;
            }
        }
    }

    // Externally dispatched-to:
    private static boolean isZeroes(P p, boolean lhs) {
        AssertError.check(p.map != null, "RecordExpr"); // must already be checked
        for (int i = 0; i <= p.map.length - 1; i++) {
            if (!Expr.isZeroes(p.map[i].expr)) {
                return false;
            }
        }
        return true;
    }

    // Externally dispatched-to:
    private static void genFPLiteral(P p, M3Buf.T buf) {
        M3Buf.putText(buf, "RECORD<");
        for (int i = 0; i <= p.map.length - 1; i++) {
            if ((i > 0)) {
                M3Buf.putChar(buf, ',');
            }
            Expr.genFPLiteral(p.map[i].expr, buf);
        }
        M3Buf.putChar(buf, '>');
    }

    // Externally dispatched-to:
    private static void prepLiteral(P p, Type.T type, boolean is_const) {
        Expr.T e;
        Field.Info fieldInfo = new Field.Info();
        AssertError.check(p.map != null, "RecordExpr"); // must already be checked
        for (int i = 0; i <= p.map.length - 1; i++) {
            Info z = p.map[i];
            e = Expr.constValue(z.expr);
            AssertError.check(e != null, "RecordExpr");
            if (!Expr.isZeroes(e)) {
                Field.split(z.field, fieldInfo);
                ArrayExpr.noteTargetType(e, fieldInfo.type);
                Expr.prepLiteral(e, fieldInfo.type, is_const);
            }
        }
    }

    // Externally dispatched-to:
    private static void genLiteral(P p, long offset, Type.T type, boolean is_const) {
        Expr.T e;
        Field.Info fieldInfo = new Field.Info();
        AssertError.check(p.map != null, "RecordExpr"); // must already be checked
        for (int i = 0; i <= p.map.length - 1; i++) {
            Info z = p.map[i];
            e = Expr.constValue(z.expr);
            AssertError.check(e != null, "RecordExpr");
            if (!Expr.isZeroes(e)) {
                Field.split(z.field, fieldInfo);
                Expr.genLiteral(e, offset + fieldInfo.offset, fieldInfo.type, is_const);
            }
        }
    }

    private static P recConstrExpr(Expr.T expr) {
        // Look through a NamedExpr and then a ConsExpr, for a RecordExpr.T.
        // NIL if no RecordExpr.T.

        Expr.T strippedExpr;
        strippedExpr = Expr.stripNamedCons(expr);
        if (strippedExpr == null) {
            return null;
        } else if (strippedExpr instanceof P recordExpr) {
            return recordExpr;
        } else {
            return null;
        }
    }

    /**
     * Set Code and Msg if they are not set and expr is known to produce a
     * statically unconditional runtime error when evaluated.
     */
    public static void checkStaticRTErrEval(Expr.T expr, M3CG.RuntimeError[] Code, String[] Msg) {
        // Return the first-discovered error found and stored during Check.
        P constrExpr;
        if (Code[0] != M3CG.RuntimeError.Unknown) {
            return;
        }
        constrExpr = recConstrExpr(expr);
        if (constrExpr == null) {
            // nothing
        } else {
            P p = constrExpr;
            AssertError.check(p.checked, "RecordExpr");
            if (p.RTErrorCode != M3CG.RuntimeError.Unknown) {
                Code[0] = p.RTErrorCode;
                Msg[0] = p.RTErrorMsg;
            }
        }
    }

    // Externally dispatched-to:
    private static boolean checkUseFailure(P p) {
        AssertError.check(p.checked, "RecordExpr");
        if (AssignStmt.doGenRTAbort(p.RTErrorCode) && evaluate(p) != null) {
            CG.comment(p.globalOffset, true,
                       "Use of record constructor with statically detected runtime error: ",
                       p.RTErrorMsg);
            CG.abort(p.RTErrorCode);
            return false;
        } else {
            return true;
        }
    }
}
