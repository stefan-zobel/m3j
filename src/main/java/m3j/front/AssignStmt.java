// Java port of CM3's m3-sys/m3front/src/stmts/AssignStmt.i3 and AssignStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: AssignStmt.i3
// Last Modified On Tue Jun 20 15:23:26 PDT 1995 By kalsow
//      Modified On Thu Jun 15 11:26:34 PDT 1995 By ericv
//      Modified On Tue Mar 20 01:30:09 1990 By muller

// File: AssignStmt.m3
// Last modified on Fri Jul  7 15:10:54 PDT 1995 by kalsow
//      modified on Thu Jun 15 15:12:38 PDT 1995 by ericv
//      modified on Fri Dec 21 01:24:28 1990 by muller

public final class AssignStmt {
    private AssignStmt() {}

    public static final class P extends Stmt.T {
        public Expr.T lhs;
        public Expr.T rhs;

        @Override
        public void check(M3.CheckState cs) {
            checkMethod(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return AssignStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    // EXPORTED:
    public static Stmt.T parse() {
        Expr.T e;
        P p;
        Stmt.T s;
        long here = Scanner.offset;
        e = Expr.parse();
        if ((Scanner.cur.token != Token.T.tASSIGN)) {
            if (!CallExpr.is(e)) {
                Error.msg("Expression is not a statement");
            }
            s = CallStmt.new_(e);
            s.origin = here;
            return s;
        }

        p = new P();
        StmtRep.init(p);
        p.origin = here;
        Scanner.getToken(); // :=
        p.lhs = e;
        p.rhs = Expr.parse();
        return p;
    }

    //------------------------------------------------------- type checking ---

    // Externally dispatched-to:
    private static void checkMethod(P p, M3.CheckState cs) {
        Type.T tlhs;
        Type.Info rhs_info = new Type.Info();
        Expr.typeCheck(p.lhs, cs);
        Expr.typeCheck(p.rhs, cs);

        tlhs = Expr.semTypeOf(p.lhs);
        if (!Expr.isDesignator(p.lhs)) {
            Error.msg("Assignment left-hand side is not a designator (2.3.1).");
        } else {
            Type.checkInfo(Expr.semTypeOf(p.rhs), rhs_info);
            if (!Expr.isWritable(p.lhs, rhs_info.isTraced)) {
                Error.msg("Assignment left-hand side is read-only (2.3.1).");
            }
        }
        Type.check(tlhs);

        check(tlhs, p.rhs, cs, /* IsError := */ false);
    }

    /**
     * Check that rhs is assignable to a variable of type tlhs.
     * Assignable types but Non-assignable value emits a warning, unless IsError.
     */
    // EXPORTED:
    public static void check(Type.T tlhs, Expr.T rhs, M3.CheckState cs, boolean IsError) {
        M3CG.RuntimeError[] Code = new M3CG.RuntimeError[1];
        String[] Msg = new String[1];
        checkStaticRTErrExec(tlhs, rhs, cs, /* OUT */ Code, /* OUT */ Msg, IsError);
    }

    public static void check(Type.T tlhs, Expr.T rhs, M3.CheckState cs) {
        check(tlhs, rhs, cs, false);
    }

    /**
     * Like Check, but if a warning is produced for a runtime error that is
     * statically inevitable whenever this code is executed, return the RT error
     * Code # CG.RuntimeError.Unknown and a message text in Msg.
     */
    // EXPORTED:
    public static void checkStaticRTErrExec(Type.T tlhs, Expr.T rhsExpr, M3.CheckState cs, M3CG.RuntimeError[] Code, String[] Msg, boolean IsError) {
        // Like Check, but if a warning is produced for a runtime error that is
        // statically inevitable whenever its code is executed, return the RT error
        // Code # CG.RuntimeError.Unknown and a message text. in Msg.
        Type.T base_tlhs = Type.base(tlhs); // strip renaming, packing, and subranges.
        Type.T trhs;
        Type.Info lhs_type_info = new Type.Info(), base_lhs_type_info = new Type.Info();
        Type.Class lhsTypeClass;
        Msg[0] = null;
        Code[0] = M3CG.RuntimeError.Unknown;
        tlhs = Type.checkInfo(tlhs, lhs_type_info);
        base_tlhs = Type.checkInfo(base_tlhs, base_lhs_type_info);
        lhsTypeClass = base_lhs_type_info.class_;
        Expr.typeCheck(rhsExpr, cs);
        trhs = Expr.semTypeOf(rhsExpr);

        if (!Type.isAssignable(tlhs, trhs)) {
            if ((tlhs != ErrType.T) && (trhs != ErrType.T)) {
                Error.msg("Types are not assignable in assignment (2.3.1).");
            }
        } else {
            switch (lhsTypeClass) {
                case Enum, Subrange, Integer, Longint ->
                    checkOrdinal(tlhs, rhsExpr, /* OUT */ Code, /* OUT */ Msg, IsError);
                case Ref, Object, Opaque ->
                    checkReference(tlhs, trhs, lhs_type_info);
                case Procedure ->
                    checkProcedure(rhsExpr, /* OUT */ Code, /* OUT */ Msg, IsError);
                case Set ->
                    SetExpr.checkStaticRTErrEval(rhsExpr, /* OUT */ Code, /* OUT */ Msg);
                case Record ->
                    RecordExpr.checkStaticRTErrEval(rhsExpr, /* OUT */ Code, /* OUT */ Msg);
                case Array, OpenArray -> {
                    ArrayExpr.checkStaticRTErrEval(rhsExpr, /* OUT */ Code, /* OUT */ Msg);
                    ArrayExpr.checkStaticRTErrAssign(tlhs, rhsExpr, /* OUT */ Code, /* OUT */ Msg);
                }
                default -> {
                }
            } // CASE
        }
    }

    public static void checkStaticRTErrExec(Type.T tlhs, Expr.T rhsExpr, M3.CheckState cs, M3CG.RuntimeError[] Code, String[] Msg) {
        checkStaticRTErrExec(tlhs, rhsExpr, cs, Code, Msg, false);
    }

    private static void checkOrdinal(Type.T tlhs, Expr.T rhsExpr,
                                     M3CG.RuntimeError[] Code, String[] Msg,
                                     boolean IsError) {
        int[] lmin = TInt.newInt(), lmax = TInt.newInt(),
              rmin = TInt.newInt(), rmax = TInt.newInt();
        Expr.T constant;
        String reason;
        // Range check if rhsExpr is constant.
        constant = Expr.constValue(rhsExpr);
        if (constant != null) {
            rhsExpr = constant;
        }
        Expr.getBounds(rhsExpr, rmin, rmax);
        Type.getBounds(tlhs, lmin, lmax);
        if (TInt.LE(lmin, lmax) && TInt.LE(rmin, rmax)
            && (TInt.LT(lmax, rmin) || TInt.LT(rmax, lmin))) {
            if (constant == null) { // Non-constant RHS, disjoint ranges.
                reason = "(disjoint ranges)";
                // FIXME: This is a mess.  This is a true CT error--the first case of
                //        expression assignability (2.3.1), i.e., assignability of types.
                //        When called by CheckStaticRTErrExec, (which happens for an
                //        assignment statement), that will already have been verified,
                //        so this can't happen.  But for exception with an argument, it
                //        type assignability seems not to be CT checked at all, which
                //        causes this to an if-executed warning.  The whole mechanism for
                //        ordinal range checks is self-inconsistent and inconsistent with
                //        that for procedures.
            } else {
                reason = "(out of range)";
            }
            if (IsError) {
                Error.msg("Constant value not assignable " + reason + " (2.3.1).");
            } else {
                Error.warn(2, "Ordinal value not assignable at runtime " + reason + " (2.3.1).");
                Code[0] = M3CG.RuntimeError.ValueOutOfRange;
                Msg[0] = "value out of range.";
            }
        } else {
        }
    }

    private static void checkReference(Type.T tlhs, Type.T trhs, Type.Info lhs_type_info) {
        // CHECK: Doesn't this just duplicate checks already done by Type.IsAssignable?
        // Other than NIL, which is a member of every reference type, there are
        // no constant reference values to do CT assignability warnings for.
        if (Type.isSubtype(trhs, tlhs)) {
            // ok
        } else if (!Type.isSubtype(tlhs, trhs)) {
            Error.msg("Reference types are not assignable (2.3.1).");
        } else if (Type.isEqual(trhs, Addr.T, null)) {
            // this is legal only in UNSAFE modules
            if (Module.isSafe()) {
                Error.msg("Unsafe implicit NARROW to ADDRESS (2.3.1).");
            }
        } else if (ObjectType.is(trhs)) {
            // ok
        } else if (lhs_type_info.isTraced) {
            // ok
        } else {
            Error.msg("Expression not assignable to reference type (2.3.1).");
        }
        // A non-constant reference value will never be an element/field of a
        // constant constructor, so not in static constant area.  The only
        // constant value of any reference is NIL, and it will never fail a
        // CT narrow check.
    }

    private static void checkProcedure(Expr.T proc, M3CG.RuntimeError[] Code,
                                       String[] Msg, boolean IsError) {
        int[] name = new int[1];
        Value.T[] obj = new Value.T[1];
        Value.Class valueClass;
        boolean nested;
        if (!(NamedExpr.split(proc, name, obj)
              || QualifyExpr.split(proc, obj)
              || ProcExpr.split(proc, obj))) {
            // NIL, or anything else?
            return;
        }
        obj[0] = Value.base(obj[0]);
        valueClass = Value.classOf(obj[0]);
        if (valueClass == Value.Class.Procedure) { // Procedure constant.
            nested = Procedure.isNested((Procedure.T) obj[0]);
            if (nested) {
                // Although we statically know this is an error, the rule (2.3.1) that
                // it violates is one that cannot in general be checked statically.  I
                // believe Modula3 is saying such cases should produce errors only at
                // runtime, if/when the subject code is actually executed.
                // rodney.m.bates@acm.org.
                if (IsError) { // But the mechanism exists to make it a CT error.
                    Error.ID(Value.cName(obj[0]),
                             "Nested procedure not assignable at runtime (2.3.1).");
                } else {
                    Error.warnID(2, Value.cName(obj[0]),
                                 "Nested procedure not assignable at runtime (2.3.1).");
                    Code[0] = M3CG.RuntimeError.NarrowFailed;
                    Msg[0] = "Nested procedure assigned.";
                }
            }
        }
    }

    public enum RTCheckKind { None, Conditional, Fail }

    private static RTCheckKind procRTCheckKind(Expr.T proc) {
        int[] name = new int[1];
        Value.T[] obj = new Value.T[1];
        Value.Class valueClass;
        boolean nested;
        if (!Host.doNarrowChk) {
            return RTCheckKind.None;
        }
        if (!(NamedExpr.split(proc, name, obj)
              || QualifyExpr.split(proc, obj)
              || ProcExpr.split(proc, obj))) {
            // NIL, or anything else?
            return RTCheckKind.None;
        }
        obj[0] = Value.base(obj[0]);
        valueClass = Value.classOf(obj[0]);
        if (valueClass == Value.Class.Procedure) { // Procedure constant.
            nested = Procedure.isNested((Procedure.T) obj[0]);
            if (nested) {
                return RTCheckKind.Fail;
            } else {
                return RTCheckKind.None;
            }
        } else if (valueClass == Value.Class.Var && Variable.hasClosure((Variable.T) obj[0])) {
            // Don't know statically.  RT check will be needed.
            return RTCheckKind.Conditional;
        } else { // non-formal, non-const => no check
            return RTCheckKind.None;
        }
    }

    //----------------------------------------------------- code generation ---

    /**
     * An alternative to calling Expr.Prep(rhs) before calling Emit() below,
     * that tries to avoid unnecessary structure copying on assignments
     * by passing the final destination to operations that can assign
     * the result directly.  "initializing" is TRUE if the lhs is
     * uninitialized storage (i.e. contains no user data).
     */
    // EXPORTED:
    public static void prepForEmit(Type.T tlhs, Expr.T rhs, boolean initializing) {
        // Before Prepping the rhsExpr, give it some info it could need to decide where
        // to build its result and whether it needs a LHS expr on the CG stack.
        // Port: the parameters lhsRepType and rhsExpr are named tlhs and rhs.
        Type.T lhsRepType = tlhs;
        Expr.T rhsExpr = rhs;
        Expr.T baseExpr;
        baseExpr = ConsExpr.base(rhsExpr);
        if (baseExpr != null) {
            rhsExpr = baseExpr;
        }
        ArrayExpr.noteTargetType(rhsExpr, lhsRepType);
        // REVIEW: Is this overconservative when assigning an array constructor?
        if (canAvoidCopy(lhsRepType, rhsExpr, initializing)) {
            Expr.markForDirectAssignment(rhsExpr);
        }
        Expr.prep(rhsExpr);
    }

    private static boolean canAvoidCopy(Type.T lhsRepType, Expr.T rhsExpr,
                                        boolean initializing) {
        // Is it possible to assign directly into a LHS variable?
        Type.T lhsBaseType, rhsRepType;
        Type.Info lhsRepTypeInfo = new Type.Info(), rhsRepTypeInfo = new Type.Info();
        Expr.T rhsBaseExpr;
        if (!Host.direct_struct_assign) {
            return false;
        }
        if (!initializing) {
            return false;
        }
        // If this is not the first assignment to the LHS, evaluation of the RHS
        // could access the LHS, whose contents must remain unchanged until all
        // RHS evaluation is done.  Or, the RHS could raise an exception, and the
        // the LHS must not be changed at all.
        if (!Expr.supportsDirectAssignment(rhsExpr)) {
            // Currently array and record.  Maybe someday multi-word sets.
            return false;
        }
        lhsBaseType = Type.base(lhsRepType);
        if (CallExpr.is(rhsExpr)) {
            if (!ProcType.largeResult(lhsBaseType)) {
                return false;
            }
            // TODO: Allow direct assignment through SUBARRAY and LOOPHOLE.
            if (!CallExpr.isUserProc(rhsExpr)) {
                return false;
            }
            // For user procedures, we can always pass in the true destination.
            // It is the callee's responsibility not to assign to this location
            // until the final procedure outcome is known, and not to overwrite
            // the contents until the entire result value has been computed.
        }

        // Make sure the source and destination are both aligned properly.
        Type.checkInfo(lhsBaseType, lhsRepTypeInfo);
        if (ConsExpr.is(rhsExpr)) {
            rhsBaseExpr = ConsExpr.base(rhsExpr);
        } else {
            rhsBaseExpr = rhsExpr;
        }
        rhsRepType = Type.stripPacked(Expr.repTypeOf(rhsBaseExpr));
        Type.checkInfo(rhsRepType, rhsRepTypeInfo);
        if (lhsRepTypeInfo.alignment != rhsRepTypeInfo.alignment) {
            return false;
        }
        if (lhsRepTypeInfo.class_ != rhsRepTypeInfo.class_) {
            return false;
        }
        if (lhsRepTypeInfo.class_ == Type.Class.Packed) {
            return false;
        }
        return true;
    }

    /**
     * tlhs is the repType.
     * emit code to assign  (s0.A).tlhs := rhs.
     * Note that Emit assumes that TypeOf(rhs) is assignable to tlhs
     * and that Expr.Prep(rhs) or preferably PrepForEmit(tlhs,rhs,initializing)
     * has been called.
     */
    // EXPORTED:
    public static void doEmit(Type.T tlhs, Expr.T rhs, long lhs_align, boolean initializing) {
        // PRE: LHS is Compiled and on TOS. It is dope if an open array.
        // PRE: RHS is Prepped.
        // Port: the parameters lhsRepType, rhsExpr and lhsAlign are named tlhs,
        // rhs and lhs_align.
        Type.T lhsRepType = tlhs;
        Expr.T rhsExpr = rhs;
        long lhsAlign = lhs_align;
        Type.T lhsRepBaseType;
        Type.Info lhsRepTypeInfo = new Type.Info(), lhsRepBaseTypeInfo = new Type.Info();
        Expr.T baseExpr;
        baseExpr = ConsExpr.base(rhsExpr);
        if (baseExpr != null) {
            rhsExpr = baseExpr;
        }
        lhsRepType = Type.checkInfo(lhsRepType, lhsRepTypeInfo);
        lhsRepBaseType = Type.base(lhsRepType);
        // ^Strip renaming, packing, and subranges.
        lhsRepBaseType = Type.checkInfo(lhsRepBaseType, lhsRepBaseTypeInfo);

        Expr.checkUseFailure(rhsExpr);
        switch (lhsRepBaseTypeInfo.class_) {
            case Integer, Longint, Subrange, Enum ->
                assignOrdinal(lhsRepType, rhsExpr, lhsRepTypeInfo);
            case Real, Longreal, Extended ->
                assignFloat(rhsExpr, lhsRepTypeInfo);
            case Object, Opaque, Ref ->
                assignReference(lhsRepType, rhsExpr, lhsRepTypeInfo);
            case Procedure -> {
                assignOrRTCheckProcedure(rhsExpr, /* lhsIsPushed := */ true);
                CG.store_indirect(lhsRepTypeInfo.stk_type, 0, lhsRepTypeInfo.size);
            }
            case Record ->
                assignRecord(rhsExpr, lhsRepTypeInfo, lhsAlign, initializing);
            case Set ->
                assignSet(lhsRepType, rhsExpr, lhsRepTypeInfo, initializing);
            case Array, OpenArray ->
                assignArray(lhsRepType, lhsRepBaseTypeInfo, rhsExpr, lhsAlign, initializing);
            case Error -> {
            }
            default -> throw new AssertError("AssignStmt");
        }
    }

    /**
     * Port: the default lhs_align := Target.Byte, for callers that pass only
     * initializing by name.
     */
    public static void doEmit(Type.T tlhs, Expr.T rhs, boolean initializing) {
        doEmit(tlhs, rhs, Target.Byte, initializing);
    }

    private static void assignOrdinal(Type.T tlhs, Expr.T rhsExpr,
                                      Type.Info lhsTypeInfo) {
        // PRE: LHS is compiled and on TOS.
        // PRE: RHS is prepped.
        int[] min = TInt.newInt(), max = TInt.newInt();
        Type.getBounds(tlhs, min, max);
        CheckExpr.emitChecks(rhsExpr, min, max, M3CG.RuntimeError.ValueOutOfRange);
        // ^Which does Compile(rhsExpr), thus pushing its CG.Val.
        CG.store_indirect(lhsTypeInfo.stk_type, 0, lhsTypeInfo.size);
    }

    private static void assignFloat(Expr.T rhsExpr, Type.Info lhsTypeInfo) {
        // PRE: LHS is compiled and on TOS.
        // PRE: RHS is prepped.
        Expr.compile(rhsExpr);
        CG.store_indirect(lhsTypeInfo.stk_type, 0, lhsTypeInfo.size);
    }

    private static void assignReference(Type.T tlhs, Expr.T rhsExpr,
                                        Type.Info lhsTypeInfo) {
        // PRE: LHS is compiled and on TOS.
        // PRE: RHS is prepped.
        CG.Val lhsVal;
        lhsVal = CG.pop();
        Expr.compile(rhsExpr);
        if (Host.doNarrowChk) {
            Narrow.emit(tlhs, Expr.typeOf(rhsExpr));
        }
        CG.push(lhsVal);
        CG.swap();
        CG.store_indirect(lhsTypeInfo.stk_type, 0, lhsTypeInfo.size);
        CG.free(lhsVal);
    }

    private static void assignSet(Type.T tlhs, Expr.T rhsExpr, Type.Info lhsTypeInfo,
                                  boolean initializing) {
        // PRE: LHS is compiled and on TOS.
        // PRE: RHS is prepped.
        // TODO: Merge AssignSet and AssignRecord.  Or maybe not.
        // Leave the LHS address on the CG stack, regardless of protocol.
        if (Type.isStructured(tlhs)) {
            assertSameSize(tlhs, Expr.typeOf(rhsExpr));
            compileStruct(rhsExpr);
            if (Expr.isMarkedForDirectAssignment(rhsExpr)) {
                CG.discard(CGType.Addr);
            } else {
                CG.copy(lhsTypeInfo.size, /* overlap := */ !initializing);
            }
        } else { // Small set.
            // REVIEW: Do we really need this case, or will CompileStruct handle it?
            Expr.compile(rhsExpr);
            CG.store_indirect(lhsTypeInfo.stk_type, 0, lhsTypeInfo.size);
        }
    }

    private static void compileStruct(Expr.T expr) {
        // This also works for a record, set, or array that is a packed component
        // of something.
        if (Expr.isDesignator(expr)) {
            Expr.compileLValue(expr, /* traced := */ false);
            // CHECK ----------------------------------- ^
        } else {
            Expr.compile(expr);
        }
    }

    private static void copyStruct(long lhsAlign, long rhsAlign, long bitSize,
                                   boolean overlap) {
        // PRE: CGstack: RHS addr on top, LHS addr below.
        // PRE: Using expression protocol.
        //  Will handle a sub-byte-sized suffix.
        if (lhsAlign < Target.Byte
            || rhsAlign < Target.Byte
            || Math.floorMod(bitSize, Target.Byte) != 0) {
            CG.load_indirect(M3.target.Word.cg_type, 0, bitSize);
            CG.store_indirect(M3.target.Word.cg_type, 0, bitSize);
        } else {
            CG.copy(bitSize, overlap);
            // Expression protocol means the value is already in a temporary.
        }
    }

    private static void assignRecord(Expr.T rhsExpr, Type.Info lhsTypeInfo,
                                     long lhsAlign, boolean initializing) {
        // PRE: LHS is compiled and on TOS.
        // PRE: RHS is prepped.
        long rhsAlign;
        // Leave the LHS address on the CG stack, regardless of protocol.
        compileStruct(rhsExpr);
        rhsAlign = Expr.alignment(rhsExpr);
        if (Expr.usesAssignProtocol(rhsExpr)) {
            // Compile will have copied RHS into LHS, leaving only the LHS
            // address on CG stack, which is the expression's result.
            CG.discard(CGType.Addr);
        } else { // Using expression protocol.
            // RHS is on top of CG stack.  LHS is below.
            // Compile will have left LHS alone.
            copyStruct(lhsAlign, rhsAlign, lhsTypeInfo.size, /* overlap := */ !initializing);
        }
    }

    private static void assignArray(Type.T lhsRepType, Type.Info lhsRepTypeInfo,
                                    Expr.T rhsExpr, long lhsAlign,
                                    boolean initializing) {
        // PRE: LHS is compiled and on TOS.
        // PRE: RHS is prepped.
        CG.Val lhsVal, rhsVal;
        long rhsAlign;
        Type.T rhsRepType;
        Type.Info rhsRepTypeInfo = new Type.Info();
        boolean LHSIsOpen, RHSIsOpen;
        if (Expr.usesAssignProtocol(rhsExpr)) {
            compileStruct(rhsExpr);
            // CompileStruct will have done any needed shape check and copied
            // rhsExpr's value into the LHS, leaving only the LHS address on CG
            // stack, which is the expression's result.
            CG.discard(CGType.Addr);
        } else { // Using expression protocol.
            LHSIsOpen = OpenArrayType.is(lhsRepType);
            rhsRepType = Expr.repTypeOf(rhsExpr);
            rhsRepType = Type.stripPacked(rhsRepType);
            RHSIsOpen = OpenArrayType.is(rhsRepType);
            if (!RHSIsOpen && !LHSIsOpen) { // Both sides are fixed length arrays
                compileStruct(rhsExpr);
                rhsAlign = Expr.alignment(rhsExpr);
                // RHS is on top of CG stack.  LHS is below, unmolested by Compile.
                copyStruct(lhsAlign, rhsAlign, lhsRepTypeInfo.size, /* overlap := */ !initializing);
            } else { // Something is open.
                lhsVal = CG.pop();
                compileStruct(rhsExpr);
                rhsAlign = Expr.alignment(rhsExpr);
                // RHS on top of CG stack.
                rhsVal = CG.pop();

                if (ArrayExpr.shapeCheckNeeded(rhsExpr)) {
                    genOpenArrayRTShapeChecks(lhsRepType, lhsVal, rhsRepType, rhsVal);
                    // Assignability check, done in Check will have taken care of
                    // static shape checking.
                }

                if (LHSIsOpen) {
                    if (RHSIsOpen) { // Both sides are open.
                        CG.push(lhsVal);
                        CG.open_elt_ptr(lhsAlign);
                        CG.forceStacked();
                        CG.push(rhsVal);
                        CG.open_elt_ptr(Expr.alignment(rhsExpr));
                        CG.forceStacked();
                        genOpenArrayCopy(lhsVal, lhsRepType, rhsRepType);
                    } else { // LHS is open and RHS is fixed.
                        CG.push(lhsVal);
                        CG.open_elt_ptr(lhsAlign);
                        CG.push(rhsVal);
                        Type.checkInfo(rhsRepType, rhsRepTypeInfo);
                        copyStruct(lhsAlign, rhsAlign, rhsRepTypeInfo.size, /* overlap := */ !initializing);
                    }
                } else { // LHS is fixed and RHS is open.
                    CG.push(lhsVal);
                    CG.push(rhsVal);
                    CG.open_elt_ptr(Expr.alignment(rhsExpr));
                    copyStruct(lhsAlign, rhsAlign, lhsRepTypeInfo.size, /* overlap := */ !initializing);
                }
                CG.free(lhsVal);
                CG.free(rhsVal);
            }
        }
    }

    private static void genOpenArrayRTShapeChecks(Type.T lhsRepType, CG.Val lhsDopeVal,
                                                  Type.T rhsRepType, CG.Val rhsVal) {
        // Leaves the CG stack as it is.
        Type.T[] lhsIndexType = new Type.T[1], rhsIndexType = new Type.T[1],
                 lhsEltType = new Type.T[1], rhsEltType = new Type.T[1];
        long depth = 0;
        if (!Host.doNarrowChk) {
            return;
        }
        lhsRepType = Type.stripPacked(lhsRepType);
        rhsRepType = Type.stripPacked(rhsRepType);
        while (ArrayType.split(lhsRepType, lhsIndexType, lhsEltType)
               && ArrayType.split(rhsRepType, rhsIndexType, rhsEltType)) {
            if (lhsIndexType[0] == null) {
                if (rhsIndexType[0] == null) { // Both LHS and RHS are open.
                    CG.push(lhsDopeVal);
                    CG.open_size(depth);
                    CG.push(rhsVal);
                    CG.open_size(depth);
                    CG.check_eq(M3.target.Integer.cg_type, M3CG.RuntimeError.IncompatibleArrayShape);
                } else { // LHS is open and RHS is fixed.
                    CG.push(lhsDopeVal);
                    CG.open_size(depth);
                    CG.load_integer(M3.target.Integer.cg_type, Type.number(rhsIndexType[0]));
                    CG.check_eq(M3.target.Integer.cg_type, M3CG.RuntimeError.IncompatibleArrayShape);
                }
            } else {
                if (rhsIndexType[0] == null) { // LHS is fixed and RHS is open.
                    CG.push(rhsVal);
                    CG.open_size(depth);
                    CG.load_integer(M3.target.Integer.cg_type, Type.number(lhsIndexType[0]));
                    CG.check_eq(M3.target.Integer.cg_type, M3CG.RuntimeError.IncompatibleArrayShape);
                } else { // Both LHS and RHS are fixed.  Static assignability check will
                         // have ensured equality here and in any inner dimensions.
                    return;
                }
            }

            depth++;
            lhsRepType = lhsEltType[0];
            rhsRepType = rhsEltType[0];
        }
    }

    private static void genOpenArrayCopy(CG.Val lhsDopeVal, Type.T lhsRepType,
                                         Type.T rhsRepType) {
        // PRE: formal lhsDopeVal is the LHS dope.
        // PRE: both lhsRepType and rhsRepType are open array types.
        // PRE: RHS element ptr on TOS and has been ForceStacked.
        // PRE: LHS element ptr below and has been ForceStacked.
        // PRE: Using the assign protocol.
        long lhsDepth, rhsDepth;
        lhsDepth = OpenArrayType.openDepth(lhsRepType);
        AssertError.check(lhsDepth > 0, "AssignStmt");
        rhsDepth = OpenArrayType.openDepth(rhsRepType);
        AssertError.check(rhsDepth > 0, "AssignStmt");
        long limit = Math.min(lhsDepth, rhsDepth) - 1;
        for (long i = 0; i <= limit; i++) {
            CG.push(lhsDopeVal);
            CG.open_size(i);
            if ((i != 0)) {
                CG.multiply(M3.target.Word.cg_type);
            }
        }
        if (lhsDepth < rhsDepth) {
            CG.copy_n(OpenArrayType.eltPack(lhsRepType), /* overlap := */ true);
        } else {
            CG.copy_n(OpenArrayType.eltPack(rhsRepType), /* overlap := */ true);
        }
    }

    private static void assertSameSize(Type.T a, Type.T b) {
        Type.Info a_info = new Type.Info(), b_info = new Type.Info();
        Type.checkInfo(a, a_info);
        Type.checkInfo(b, b_info);
        if ((a_info.size != b_info.size)) {
            Error.msg("INTERNAL ERROR: trying to assign values of differing sizes");
        }
    }

    //-------------------------------------- code generation: checking only ---

    /**
     * Emit code to evaluate "rhs" and generate any runtime
     * checks that would be needed if it were assigned to
     * a value of type 'tlhs'.  Leave the rhs value on the stack.
     * PRE: The CG stack is empty.
     * PRE: TypeOf(rhs) is assignable to tlhs.
     * PRE: Expr.Prep(rhs) has been called.
     * PRE: 'tlhs' is not an open array type.
     */
    // EXPORTED:
    public static void emitRTCheck(Type.T tlhs, Expr.T rhs) {
        // PRE: The CG stack is empty.
        // PRE: The rhsExpr is prepped.
        // POST: RHS is TOS.
        // Port: the parameter rhsExpr is named rhs.
        Expr.T rhsExpr = rhs;
        Type.T t_lhs_base = Type.base(tlhs); // strip renaming and packing
        Type.Info t_lhs_base_info = new Type.Info();
        Expr.checkUseFailure(rhsExpr);

        t_lhs_base = Type.checkInfo(t_lhs_base, t_lhs_base_info);
        switch (t_lhs_base_info.class_) {
            case Integer, Longint, Subrange, Enum ->
                rtCheckOrdinal(tlhs, rhsExpr);
            case Real, Longreal, Extended ->
                rtCheckFloat(rhsExpr);
            case Object, Opaque, Ref ->
                rtCheckReference(tlhs, rhsExpr);
            case Array, OpenArray ->
                rtCheckArray(tlhs, rhsExpr);
            case Procedure ->
                assignOrRTCheckProcedure(rhsExpr, /* lhsIsPushed := */ false);
            case Record ->
                rtCheckRecord(rhsExpr);
            case Set ->
                rtCheckSet(tlhs, rhsExpr);
            default -> throw new AssertError("AssignStmt");
        }
    }

    private static void rtCheckOrdinal(Type.T tlhs, Expr.T rhsExpr) {
        int[] min = TInt.newInt(), max = TInt.newInt();
        Type.getBounds(tlhs, min, max);
        CheckExpr.emitChecks(rhsExpr, min, max, M3CG.RuntimeError.ValueOutOfRange);
    }

    private static void rtCheckFloat(Expr.T rhsExpr) {
        Expr.compile(rhsExpr);
    }

    private static void rtCheckReference(Type.T tlhs, Expr.T rhsExpr) {
        Expr.compile(rhsExpr);
        if (Host.doNarrowChk) {
            Narrow.emit(tlhs, Expr.typeOf(rhsExpr));
        }
    }

    private static void assignOrRTCheckProcedure(Expr.T rhsExpr, boolean lhsIsPushed) {
        // PRE: The CG stack is empty.
        // PRE: The rhsExpr is prepped.
        // POST: RHS is TOS.
        CG.Val lhsVal = null, rhsVal;
        long ok;
        switch (procRTCheckKind(rhsExpr)) {
            case Fail -> {
                CG.abort(M3CG.RuntimeError.NarrowFailed);
                Expr.compile(rhsExpr);
            }
            case Conditional -> {
                // <rant>
                //  Could there ever be a better example than this of what
                //  an utterly dreadful idea an operand stack machine is?
                //
                //  We have to call If_Closure with empty stack.  We have to know how
                //  many items are on it when we are called.  Not only does this involve
                //  backing up through the reverse call graph in many places, checking
                //  undocumented preconditions, but in the end, it is conditional.
                //  If things are there, we have to spill them.
                //
                //  Then, after pushing the RHS, we have to spill it too, pass the
                //  spilled rhs value to If_closure in a parameter, then re-push it
                //  afterwards for further use by our callers.  Finally, conditionally
                //  re-push the previously spilled items.
                //
                //  Or, we could have writen two nearly-same copies of this procedure.
                //
                //  All of this is entirely gratuitous, as it has no actual effect
                //  on the If_closure operation.
                //  </rant>
                if (lhsIsPushed) {
                    lhsVal = CG.pop();
                }
                Expr.compile(rhsExpr);
                rhsVal = CG.pop();
                ok = CG.next_label();
                CG.if_closure(rhsVal, CG.No_label, ok, CG.Always);
                CG.abort(M3CG.RuntimeError.NarrowFailed);
                // TODO^ I think we need another runtime error code for assigning a nested
                //       procedure.
                CG.set_label(ok);
                if (lhsIsPushed) {
                    CG.push(lhsVal);
                    CG.free(lhsVal);
                }
                CG.push(rhsVal);
                CG.free(rhsVal);
            }
            case None ->
                Expr.compile(rhsExpr);
        }
    }

    private static void rtCheckRecord(Expr.T rhsExpr) {
        if (Expr.isDesignator(rhsExpr)) {
            Expr.compileLValue(rhsExpr, /* traced := */ false);
        } else {
            Expr.compile(rhsExpr);
        }
    }

    private static void rtCheckSet(Type.T tlhs, Expr.T rhsExpr) {
        if (Type.isStructured(tlhs)) {
            if (Expr.isDesignator(rhsExpr)) {
                Expr.compileLValue(rhsExpr, /* traced := */ false);
            } else {
                Expr.compile(rhsExpr);
            }
        } else { // small set
            Expr.compile(rhsExpr);
        }
    }

    private static void rtCheckArray(Type.T tlhs, Expr.T rhsExpr) {
        Type.T trhs = Expr.typeOf(rhsExpr);
        boolean LHSIsOpen = OpenArrayType.is(tlhs);
        boolean RHSIsOpen = OpenArrayType.is(trhs);
        CG.Val rhs;
        // evaluate the right-hand side
        if (Expr.isDesignator(rhsExpr)) {
            Expr.compileLValue(rhsExpr, /* traced := */ false);
        } else {
            Expr.compile(rhsExpr);
        }

        if (LHSIsOpen) {
            Error.msg("INTERNAL ERROR: AssignStmt.EmitCheck (OPEN ARRAY)");

        } else if (RHSIsOpen) {
            rhs = CG.pop();
            genOpenArraySizeChk(rhs, tlhs, trhs);
            CG.push(rhs);
            CG.open_elt_ptr(ArrayType.eltAlign(trhs));
            CG.free(rhs);

        } else { // both sides are fixed length arrays
            // no more code to generate

        }
    }

    private static void genOpenArraySizeChk(CG.Val rhsVal, Type.T tlhs, Type.T trhs) {
        Type.T[] lhsIndexType = new Type.T[1], rhsIndexType = new Type.T[1],
                 lhsEltType = new Type.T[1], rhsEltType = new Type.T[1];
        long depth = 0;
        if (!Host.doNarrowChk) {
            return;
        }
        while (ArrayType.split(tlhs, lhsIndexType, lhsEltType)
               && ArrayType.split(trhs, rhsIndexType, rhsEltType)
               && (rhsIndexType[0] == null)) {

            CG.push(rhsVal);
            CG.open_size(depth);
            CG.load_integer(M3.target.Integer.cg_type, Type.number(lhsIndexType[0]));
            CG.check_eq(M3.target.Integer.cg_type, M3CG.RuntimeError.IncompatibleArrayShape);

            depth++;
            tlhs = lhsEltType[0];
            trhs = rhsEltType[0];
        }
    }

    // ------------------------- Compile ------------------------------

    /** Does Code create runtime errors? */
    // EXPORTED:
    public static boolean doGenRTAbort(M3CG.RuntimeError Code) {
        switch (Code) {
            case ValueOutOfRange -> {
                return Host.doRangeChk;
            }
            case NarrowFailed -> {
                return Host.doNarrowChk;
            }
            case IncompatibleArrayShape -> {
                return Host.doNarrowChk;
                // TODO: We should have a separate compiler switch for this one.
            }
            default -> {
                return false;
            }
        }
    }

    // Externally dispatched-to:
    private static EnumSet<Stmt.Outcome> compile(P p) {
        Type.T tlhs = Expr.repTypeOf(p.lhs);
        Type.Info rhs_info = new Type.Info();
        Type.checkInfo(Expr.typeOf(p.rhs), rhs_info);
        Expr.prepLValue(p.lhs, /* traced := */ rhs_info.isTraced);
        prepForEmit(tlhs, p.rhs, /* initializing := */ false);
        Expr.compileLValue(p.lhs, /* traced := */ rhs_info.isTraced);
        doEmit(tlhs, p.rhs, Expr.alignment(p.lhs), /* initializing := */ false);
        Expr.noteWrite(p.lhs);
        return EnumSet.of(Stmt.Outcome.FallThrough);
    }

    // Externally dispatched-to:
    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        return EnumSet.of(Stmt.Outcome.FallThrough);
    }
}
