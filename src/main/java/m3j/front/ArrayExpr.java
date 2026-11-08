// Java port of CM3's m3-sys/m3front/src/exprs/ArrayExpr.i3 and ArrayExpr.m3.
// Original: Copyright 2020, 2021, Rodney M. Bates (rodney.m.bates@acm.org).
// Distributed under the MIT License in licenses/MIT-BATES.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3Buf;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File ArrayExpr.i3
// Modula-3 source code.

// File ArrayExpr.m3
// Modula-3 source code.

/**
 * An array constructor.
 *
 * <p>An array constructor. Built only during semantic procssing.
 *
 * <p>An array constructor can use one of these protocols for getting
 * its value to where it belongs:
 * <pre>
 * constant protocol:   The value is built at compile time, directly
 *                      into one of the static areas.
 * expression protocol: The value is built into a temporary,
 *                      independently of its ultimate destination,
 *                      and used or copied later.
 * assign protocol:     The value is built directly into a runtime
 *                      variable, which must be available (on the
 *                      CG stack) during Prep.
 * </pre>
 */
public final class ArrayExpr {
    private ArrayExpr() {}

    /** Monitor sequencing of operations: */
    public enum StateTyp {
        Fresh, Checking, Checked, Representing, Represented,
        Prepping, Prepped, Compiling, Compiled
    }

    /**
     * Information collected/matched across all cousins at a given
     * multidimensional array nesting depth.
     */
    public static final class LevelInfoTyp {
        public Type.T semType = null;
        public Type.T repType = null; // Packed stripped, top level only.
        public Type.T repIndexType = null;
        public CG.Val fixingLenVal = null;
        // If this is a dynamic level, its runtime length.
        public CG.Val eltBytepackVal = null;
        // If this is a dynamic level, its runtime element
        // packing in bytes.
        public long staticLen = Expr.lengthNonArray;
        public long staticFlatEltCt = 1;
        // Product of non-neg. staticLen values for this
        // and static inner dimensions, flattened down to
        // either a non-array or dynamic-sized open array.
        public long levEltPack;
        // ^Irrelevant when NOT FixedOpenChecked
        // In a multi-dimension open array type, eltPack
        // is for the deepest open array, since lengths are
        // not static.  levEltPack is for this exact level,
        // and may be statically computable, even for an open
        // array, if its length is imposed by a constructor
        // argument count or matching fixed array type.
        // Static for deepestDynDepth and deeper, or all
        // levels, if shallowestDynDepth < 0.
        // -1 if not static.
        public boolean FixedOpenChecked = false;

        LevelInfoTyp() {}

        LevelInfoTyp copy() {
            LevelInfoTyp r = new LevelInfoTyp();
            r.semType = semType;
            r.repType = repType;
            r.repIndexType = repIndexType;
            r.fixingLenVal = fixingLenVal;
            r.eltBytepackVal = eltBytepackVal;
            r.staticLen = staticLen;
            r.staticFlatEltCt = staticFlatEltCt;
            r.levEltPack = levEltPack;
            r.FixedOpenChecked = FixedOpenChecked;
            return r;
        }
    }

    // Port: LevelsTyp = REF ARRAY OF LevelInfoTyp is LevelInfoTyp[].

    /** Port: NEW (LevelsTyp, n). */
    private static LevelInfoTyp[] newLevels(long n) {
        LevelInfoTyp[] r = new LevelInfoTyp[(int) n];
        for (int i = 0; i < r.length; i++) {
            r[i] = new LevelInfoTyp();
        }
        return r;
    }

    /**
     * Different ways of delivering the result expression.
     *
     * <p>Port: ResultKindTyp and its alias RKTyp are this enum.
     */
    public enum RKTyp {
        /** Initial value. */
        RKUnknown,
        /**
         * One of the global data areas.
         * Uses top.containingUnit, top.globalOffset,
         * top.globalEltsOffset, and top.inConstArea.
         */
        RKGlobal,
        /**
         * Caller-provided area, elements only.
         * Uses top.buildEltsAddrVal.
         */
        RKDirectElts,
        /**
         * Caller-provided area, doped.
         * Uses top.buildAddrVal and top.buildEltsAddrVal.
         */
        RKDirectDoped,
        /**
         * Static-sized CG.Var temp, elements only.
         * Uses top.buildTempVar.
         */
        RKTempElts,
        /**
         * Static-sized CG.Var temp, with dope.
         * Uses top.buildTempVar.
         */
        RKTempStatic,
        /**
         * Dynamically sized and allocated temp.
         * Uses top.heapTempPtrVar, top.buildAddrVal,
         * and top.buildEltsAddrVal.
         */
        RKTempDyn
    }

    // Port: RKTypSet = SET OF RKTyp is EnumSet<RKTyp>.

    /**
     * For these result kinds, we are building into a previously uninitialized
     * area whose preexisting contents will never be accessed.
     * Port: a constant; never modified.
     */
    private static final EnumSet<RKTyp> RKTypSetInitializing = EnumSet.of(
        RKTyp.RKGlobal, RKTyp.RKTempElts, RKTyp.RKTempStatic, RKTyp.RKTempDyn);

    /** Properties of an array constructor: */
    public static class T extends Expr.T {
        // Field repType, inherited from Expr.T:
        // The type used in RT representation of the constructor.  Equals targetType
        // (or, for a nested constructor, an element type thereof),
        // if top targetType # NIL.  Otherwise, inferred entirely from the
        // constructor.  Could still be # semType, if semType is open but
        // context fixes the length of some of the dimensions.
        // It is computed in 'Represent'. Packed is stripped, top level only.
        public T top;
        public Type.T tipe;
        // CLEANUP ^ tipe always duplicates field "type", inherited from Expr.T
        public Expr.T[] args;
        public Type.T semType; // Original source code semantic type.
        public Type.T semIndexType;
        public Type.T semEltType;
        public long depth; // Depth this constructor is below the
                    // top-level array constructor.
        public long eltCt = Expr.lengthInvalid;
        public StateTyp state;
        public boolean isNested; // Inside another ArrayExpr/ConsExpr pair, with
                          // no NamedExpr intervening.
        public boolean dots;
        public boolean evalAttempted; // TRUE even if Evaluate was called unsuccessfully.
        public boolean is_const; // Meaningless if NOT evalAttempted.
                          // Otherwise, Evaluate was successful.
        public boolean broken;

        // Only used in top constructor:
        public Type.T targetType; // Requested by user of the constructor.
        public long repOpenDepth;
        public Module.T containingUnit; // For RKGlobal, the unit containing this
                                 // array constructor constant.
        public long globalOffset = Long.MIN_VALUE; // Means uninitialized.
        public long globalEltsOffset = Long.MIN_VALUE; // Means uninitialized.
        public Type.T refType; // If needed, type REF repType.
        public CG.Val finalVal;
        public CG.Val buildAddrVal;
        public CG.Val buildEltsAddrVal;
        public M3CG.Var buildTempVar;
        public CG.Val dynByteOffsetVal = null; // Top only.
        public long shallowestDynDepth = Long.MIN_VALUE;
        public long deepestDynDepth = Long.MIN_VALUE;
        public M3CG.Var heapTempPtrVar = null;
        public long staticEltsSize; // Excluding any dope.  Zero if not fully static.
        public long dopeSize;
        public long totalSize; // Including any dope.
        public Expr.T firstArgExpr = null;
        public long firstArgDepth = 0;
        public CG.Val firstArgDopeVal;
        public Expr.T lastArgExpr = null;
        public LevelInfoTyp[] levels;
        public String RTErrorMsg = null;
        public M3CG.RuntimeError RTErrorCode = M3CG.RuntimeError.Unknown;
        public RKTyp resultKind = RKTyp.RKUnknown;
        public long topRepAlign;
        public long topEltsAlign; // The entire block of elements.
        public boolean inConstArea;
        public boolean fixingInfoComputed;
        public boolean useTargetVar = false;
        public boolean useTargetVarLocked = false;

        @Override
        public Type.T typeOf() {
            return ArrayExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return ArrayExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            ArrayExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            ArrayExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            ArrayExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepLV(boolean traced) {
            ArrayExpr.prepLV(this, traced);
        }

        @Override
        public void compileLV(boolean traced, boolean StaticOnly) {
            ArrayExpr.compileLV(this, traced, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return ArrayExpr.evaluate(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return ArrayExpr.isEqual(this, e, x);
        }

        @Override
        public boolean isZeroes(boolean lhs) {
            return ArrayExpr.isZeroes(this, lhs);
        }

        @Override
        public void genFPLiteral(M3Buf.T mbuf) {
            ArrayExpr.genFPLiteral(this, mbuf);
        }

        @Override
        public void prepLiteral(Type.T type, boolean is_const) {
            ArrayExpr.prepLiteral(this, type, is_const);
        }

        @Override
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            ArrayExpr.genLiteral(this, offset, type, is_const);
        }

        @Override
        public long staticLength() {
            return ArrayExpr.staticLength(this);
        }

        @Override
        public boolean usesAssignProtocol() {
            return ArrayExpr.usesAssignProtocol(this);
        }

        @Override
        public boolean checkUseFailure() {
            return ArrayExpr.checkUseFailure(this);
        }

        // Port: the overrides prepBR, compileBR := ExprRep.NotBoolean,
        // getBounds := ExprRep.NoBounds, isWritable, isDesignator :=
        // ExprRep.IsNever and note_write := ExprRep.NotWritable restate the
        // inherited methods.
    }

    /** Port: MAX on StateTyp. */
    private static StateTyp maxState(StateTyp a, StateTyp b) {
        return (a.compareTo(b) >= 0) ? a : b;
    }

    /** PRE: type is a fixed or open array type. */
    public static Expr.T new_(Type.T type, Expr.T[] args, boolean dots) {
        T constr = new T();
        ExprRep.init(constr);
        constr.resultKind = RKTyp.RKUnknown;
        constr.depth = Long.MIN_VALUE;
        constr.type = type;
        constr.tipe = type;
        constr.semType = type;
        constr.targetType = null;
        constr.useTargetVar = false;
        constr.useTargetVarLocked = false;
        Type.T[] semIndexType = new Type.T[1], semEltType = new Type.T[1];
        boolean b = ArrayType.split(type, semIndexType, semEltType);
        constr.semIndexType = semIndexType[0];
        constr.semEltType = semEltType[0];
        AssertError.check(b, "ArrayExpr"); // Which also implies type is an array type.
        constr.repType = null;
        constr.args = args;
        // REVIEW: Initializations here vs. declared in T.
        constr.dots = dots;
        constr.evalAttempted = false;
        constr.is_const = false;
        constr.broken = false;
        constr.checked = false;
        constr.fixingInfoComputed = false;
        constr.refType = null;
        constr.dynByteOffsetVal = null;
        constr.directAssignableType = true; // Inherited.
        constr.eltCt = Expr.lengthInvalid;
        constr.state = StateTyp.Fresh;
        constr.shallowestDynDepth = Long.MIN_VALUE; // < 0 => none exists.
        constr.deepestDynDepth = Long.MIN_VALUE; // < 0 => none exists.
        constr.heapTempPtrVar = null;
        constr.RTErrorCode = M3CG.RuntimeError.Unknown;
        constr.RTErrorMsg = null;
        return constr;
    }

    /** Look through a ConsExpr for an ArrayExpr.  NIL if not. */
    public static ArrayExpr.T arrayConstrExpr(Expr.T expr) {
        // Look through a NamedExpr and then a ConsExpr, for an ArrayExpr.  NIL if not.

        Expr.T strippedExpr;
        strippedExpr = Expr.stripNamedCons(expr);
        if (strippedExpr == null) {
            return null;
        } else if (strippedExpr instanceof T arrayExpr) {
            return arrayExpr;
        } else {
            return null;
        }
    }

    /**
     * PRE: constr has not been checked.
     * Mark constr as nested (ArrayExpr nested inside an ArrayExpr, directly,
     * except for a possible ConsExpr in between.  In particular, must not
     * be accessed by the outer ArrayExpr through a named constant.
     */
    public static void noteNested(ArrayExpr.T constr) {
        if (constr == null) {
            return;
        }
        AssertError.check(constr.state.compareTo(StateTyp.Checking) < 0, "ArrayExpr");
        constr.isNested = true;
    }

    /** Purely syntactic. Will not look through a ConsExpr. */
    public static boolean is(Expr.T expr) {
        if (expr == null) {
            return false;
        } else if (expr instanceof T) {
            return true;
        } else {
            return false;
        }
    }

    /** expr is an anonymous array constructor. Will look thru' a ConsExpr. */
    public static boolean isAnon(Expr.T expr) {
        Expr.T strippedExpr;
        if (NamedExpr.is(expr)) {
            return false;
        }
        if (ConsExpr.is(expr)) {
            ConsExpr.seal(expr);
            strippedExpr = ConsExpr.base(expr);
        } else {
            strippedExpr = expr;
        }
        return is(strippedExpr);
    }

    /**
     * &lt; 0, if nonstatic.  Can be static, even if open array repType.
     * Does not include dope.
     */
    public static long staticSize(Expr.T expr) {
        T top;
        top = arrayConstrExpr(expr);
        if (top == null) {
            return Expr.lengthNonArray;
        }
        if (top.shallowestDynDepth >= 0) { // Size is dynamic.
            return Expr.lengthNonStatic;
        }
        return top.staticEltsSize;
    }

    // Externally dispatched-to:
    private static void needsAddress(T constr) {
        // We don't need to be told this. It always needs an address.
    }

    // Externally dispatched-to:
    private static boolean isEqual(T a, Expr.T expr, Type.Assumption x) {
        // Purely syntactic.
        if (expr == null) {
            return false;
        } else if (expr instanceof T b) {
            if ((!Type.isEqual(a.semType, b.semType, x))
                || (a.dots != b.dots)
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
        } else {
            return false;
        }
    }

    // ------------------------- Check ------------------------

    private static long argCt(T constr) {
        if (constr.args == null) {
            return 0;
        } else {
            return constr.args.length;
        }
    }

    private static long lengthOfOrdType(Type.T ordType) {
        // PRE: ordType is NIL or an ordinal type.
        long[] length = new long[1];
        int[] lengthTI;
        boolean b;
        if (ordType == null) {
            return Expr.lengthNonStatic;
        }
        AssertError.check(Type.isOrdinal(ordType), "ArrayExpr");
        lengthTI = TInt.copy(Type.number(ordType));
        b = TInt.toInt(lengthTI, length);
        if (!b) {
            Error.msg("CM3 limit: ordinal type has too many elements.");
            return Expr.lengthInvalid;
        }
        return length[0];
    }

    private static void checkArgStaticLength(T constr, LevelInfoTyp levelInfo,
                                             long argLength, long depth) {
        if (argLength < 0) {
            return;
        }
        if (levelInfo.staticLen == Expr.lengthNonStatic) {
            // First-found fixed-array argument.  All cousins at
            // this depth must have this same length.
            levelInfo.staticLen = argLength;
        } else if (argLength != levelInfo.staticLen) {
            Error.int_(depth,
                "Array constructor element's static length differs "
                + "from previous static length (2.6.8), at depth:");
            constr.broken = true;
        }
    }

    // Externally dispatched-to:
    private static void check(T top, M3.CheckState cs) {
        // Must be called IFF top is the top of a tree of array constructors.
        // This will be called from ConsExpr.Check, through Expr.TypeCheck.
        Type.T levelType;
        Type.T[] levelIndexType = new Type.T[1], levelEltType = new Type.T[1];
        boolean b;
        if (top.isNested) {
            return; // Shouldn't happen.
        }
        if (top.checked) {
            return;
        }

        top.type = Type.check(top.type);
        top.tipe = top.type;
        top.semType = top.type;
        // TODO^ Remove redundant type fields.

        // Create the levels array.
        top.levels = newLevels(ArrayType.totalDepth(top.semType) + 1);
        // Plus one for the non-array leaf ----------------------^.

        // Collect level info (semType, staticLen) from top-level semType.
        levelType = top.semType;
        for (int i = 0; i <= top.levels.length - 1 - 1; i++) {
            LevelInfoTyp levelInfo = top.levels[i];
            levelInfo.semType = levelType;
            b = ArrayType.split(levelType, /* VAR */ levelIndexType, /* VAR */ levelEltType);
            AssertError.check(b, "ArrayExpr");
            levelInfo.staticLen = lengthOfOrdType(levelIndexType[0]);
            levelType = levelEltType[0];
        }

        // Initialize level info for the deepest, (non-array) level.
        LevelInfoTyp lastLevelInfo = top.levels[top.levels.length - 1];
        lastLevelInfo.staticLen = Expr.lengthNonArray;
        lastLevelInfo.repType = levelType; // Preserve BITS..FOR.
        lastLevelInfo.semType = levelType;

        // Recursively traverse and check the tree of nested array constructors.
        // Also compute levelInfo.staticLen, top.firstArgExpr, top.firstArgDepth,
        // top.lastArgExpr.
        checkRecurse(top, top, cs, /* depth := */ 0);
        top.state = StateTyp.Checked;
        top.checked = true;
    }

    private static void mergeRTError(T top, M3CG.RuntimeError Code, String Msg) {
        if (Msg != null && top.RTErrorMsg == null) {
            top.RTErrorCode = Code;
            top.RTErrorMsg = Msg;
            // Error.Warn
            //   (2, "Will raise runtime error if executed: " & Msg);
        }
    }

    private static void checkRecurse(T top, T constr, M3.CheckState cs, long depth) {
        long argLength, argDepth, argCt;
        long[] priorErrCt = new long[1], priorWarnCt = new long[1];
        long[] laterErrCt = new long[1], laterWarnCt = new long[1];
        long depthWInArg, depthWInTopConstr;
        Type.T argSemType, argRepType;
        Type.T[] argIndexType = new Type.T[1], argEltType = new Type.T[1];
        Expr.T[] value = new Expr.T[1], minE = new Expr.T[1], maxE = new Expr.T[1];
        T argConstr;
        int[] key = new int[1];
        M3CG.RuntimeError[] RTErrorCode = new M3CG.RuntimeError[1];
        String[] RTErrorMsg = new String[1];
        Type.Info eltTypeInfo = new Type.Info();
        AssertError.check(constr.state == StateTyp.Fresh, "ArrayExpr");
        constr.state = StateTyp.Checking;
        AssertError.check(constr.isNested == (depth > 0), "ArrayExpr");
        constr.depth = depth;
        constr.top = top;
        if (depth > 0) {
            constr.type = Type.check(constr.type);
            constr.tipe = constr.type;
            constr.semType = constr.type;
        }
        Type.checkInfo(constr.semEltType, eltTypeInfo);

        if (constr.type == ErrType.T
            || constr.semIndexType == ErrType.T
            || constr.semEltType == ErrType.T) {
            constr.broken = true;
            constr.state = StateTyp.Checked;
            return;
        }
        if (constr.semIndexType != null) {
            // Don't do Type.Check on constr.semIndexType=NIL. It would turn the NIL
            // into ErrType.T, losing an open array.
            constr.semIndexType = Type.check(constr.semIndexType);
        }

        // Check argument list length against constructor index type
        // and compute constr.eltCt.
        argCt = argCt(constr);
        if (constr.semIndexType != null) { // Fixed array semType.
            constr.eltCt = lengthOfOrdType(constr.semIndexType);
            if (argCt > constr.eltCt) {
                Error.int_(argCt, "Too many values specified in fixed array constructor (2.6.8).");
                for (long i = constr.eltCt; i <= argCt - 1; i++) {
                    constr.args[(int) i] = null;
                }
            } else if (argCt < constr.eltCt && !constr.dots) {
                Error.msg("Too few values specified in fixed array constructor (2.6.8).");
            }
        } else { // Open array semType.
            constr.eltCt = argCt;
            if ((constr.dots)) {
                Error.msg("\"..\" not allowed in open array constructor (2.6.8).");
                // NOTE: ^The language seems to say this is illegal.  It was
                //       previously a warning, so this change could invalidate
                //       existing code.
                constr.dots = false;
            }
        }
        LevelInfoTyp selfLevelInfo = top.levels[(int) depth];
        if (selfLevelInfo.staticLen >= 0) {
            if (constr.eltCt != selfLevelInfo.staticLen) {
                Error.int_(selfLevelInfo.staticLen,
                    "Array constructor argument list length unequal to fixed "
                    + " length of declared type (2.6.8).");
                constr.broken = true;
            }
        } else {
            selfLevelInfo.staticLen = constr.eltCt;
        }
        // WITH

        // Go through the arguments.
        argDepth = depth + 1;
        LevelInfoTyp argLevelInfo = top.levels[(int) argDepth];
        for (int i = 0; i <= argCt - 1; i++) {
            // Port: WITH argExpr = constr.args^ [i]: argExpr is written, so
            // constr.args[i] is used for it.
            if (constr.args[i] != null) {

                // Check for arg forms that are invalid in an array constructor:
                if (KeywordExpr.split(constr.args[i], key, value)) {
                    Error.msg("Keyword values are not allowed in array constructors (2.6.8).");
                    constr.args[i] = null;
                    constr.broken = true;
                } else if (RangeExpr.split(constr.args[i], minE, maxE)) {
                    Error.msg("Range values are not allowed in array constructors (2.6.8).");
                    constr.args[i] = null;
                    constr.broken = true;
                } else { // Valid arg form.
                    argConstr = arrayConstrExpr(constr.args[i]);
                    if (argConstr != null && argConstr.isNested) {
                        // argConstr is an inner array constructor.
                        checkRecurse(top, argConstr, cs, argDepth);
                        argLength = argConstr.eltCt;
                        argSemType = argConstr.semType;
                    } else { // argExpr is a non-array, or non-constructor, or named
                             // constant, possibly with an array constructor as value.
                        Expr.typeCheck(constr.args[i], cs);
                        if (top.firstArgExpr == null) {
                            top.firstArgExpr = constr.args[i];
                            top.firstArgDepth = argDepth;
                        }
                        top.lastArgExpr = constr.args[i];
                        argSemType = Expr.semTypeOf(constr.args[i]);
                    } // TYPECASE

                    // Check type/type assignability of arg to this constructor.
                    if (argSemType != null) {
                        if (!Type.isAssignable(constr.semEltType, argSemType)) {
                            Error.int_(i,
                                "Expression is not assignable to containing array"
                                + " constructor's element type (2.6.8).");
                            constr.args[i] = null;
                            constr.broken = true;
                        }
                        // And to top constructor, if different.
                        if (constr != top
                            && !Type.isAssignable(argLevelInfo.semType, argSemType)) {
                            Error.int_(i,
                                "Expression is not assignable to top-level array"
                                + " constructor's element type (2.6.8).");
                            constr.args[i] = null;
                            constr.broken = true;
                        }
                    }

                    if (!constr.broken) {
                        // Check shape criterion of assignability.
                        if (argConstr != null) {
                            if (argConstr.isNested) {
                                checkArgStaticLength(constr, argLevelInfo, argConstr.eltCt,
                                                     constr.depth + 1);
                            } else {
                                AssertError.check(evaluate(argConstr) == argConstr, "ArrayExpr");
                                // Check argConstr's static lengths against top's.
                                AssertError.check(argConstr.depth == 0, "ArrayExpr");
                                depthWInArg = 0;
                                while (true) { // Thru array levels of arg.
                                    depthWInTopConstr
                                        = constr.depth + 1 /* For arg */ + depthWInArg;
                                    if (depthWInTopConstr >= top.levels.length - 1) {
                                        break;
                                    }
                                    argLength = argConstr.levels[(int) depthWInArg].staticLen;
                                    checkArgStaticLength(constr, top.levels[(int) depthWInTopConstr],
                                                         argLength, depthWInTopConstr);
                                    depthWInArg++;
                                } // LOOP
                                AssignStmt.checkStaticRTErrExec(constr.semEltType, argConstr, cs,
                                    /* VAR Code := */ RTErrorCode, /* VAR Msg := */ RTErrorMsg);
                                mergeRTError(top, RTErrorCode[0], RTErrorMsg[0]);
                            }
                        } else { // Arg is not an array constructor.
                            Error.count(priorErrCt, priorWarnCt);
                            noteUseTargetVar(constr.args[i]);
                            AssignStmt.checkStaticRTErrExec(constr.semEltType, constr.args[i], cs,
                                /* VAR Code := */ RTErrorCode, /* VAR Msg := */ RTErrorMsg);
                            mergeRTError(top, RTErrorCode[0], RTErrorMsg[0]);
                            Error.count(laterErrCt, laterWarnCt);
                            if (laterErrCt[0] > priorErrCt[0]) {
                                constr.args[i] = null;
                            } else {
                                // Not array constructor => no need to call Represent.
                                argRepType = Expr.repTypeOf(constr.args[i]);
                                depthWInArg = 0;
                                while (true) { // Thru array levels of arg.
                                    depthWInTopConstr
                                        = constr.depth + 1 /* For arg */ + depthWInArg;
                                    if (depthWInTopConstr >= top.levels.length - 1) {
                                        break;
                                    }
                                    if (!ArrayType.split(argRepType, /* VAR */ argIndexType,
                                                         /* VAR */ argEltType)) {
                                        break;
                                    }
                                    if (argIndexType[0] != null) {
                                        argLength = lengthOfOrdType(argIndexType[0]);
                                        checkArgStaticLength(constr, top.levels[(int) depthWInTopConstr],
                                                             argLength, depthWInTopConstr);
                                    }
                                    argRepType = argEltType[0];
                                    depthWInArg++;
                                } // LOOP
                            }
                        }
                    } // IF not broken
                }
            } // IF argExpr # NIL
        } // FOR
        // WITH argLevelInfo

        constr.state = StateTyp.Checked;
    }

    // --------- Things dependent on Check having been done. --------

    /**
     * Will look through a ConsExpr.
     *
     * <p>Port: index is the parameter indexExpr, expr the VAR parameter
     * resultExpr.
     */
    public static boolean constSubscript(Expr.T array, Expr.T index, Expr.T[] expr) {
        // PRE: array is Checked.
        // PRE: indexExpr is Checked.
        Expr.T indexExpr = index;
        T constr;
        long[] i = new long[1];
        long n;
        Type.T[] t = new Type.T[1];
        int[] int_ = TInt.newInt(), min = TInt.newInt(), max = TInt.newInt(),
              offs = TInt.newInt();
        boolean b;
        AssertError.check(array.checked, "ArrayExpr");
        constr = arrayConstrExpr(array);
        if (constr == null) {
            return false;
        }
        AssertError.check(indexExpr.checked, "ArrayExpr");

        if (argCt(constr) <= 0) {
            return false;
        }
        n = constr.args.length - 1;
        indexExpr = Expr.constValue(indexExpr);
        if ((!IntegerExpr.split(indexExpr, int_, t))
            && (!EnumExpr.split(indexExpr, int_, t))) {
            return false;
        }
        if (constr.semIndexType == null) { // Open array.
            TInt.assign(min, TInt.Zero);
            b = TInt.fromInt(n, max);
            AssertError.check(b, "ArrayExpr");
        } else {
            Type.getBounds(constr.semIndexType, min, max);
        }
        if (TInt.GT(int_, max)) {
            return false;
        }
        // Subtract lower bound, giving a zero-based subscript.
        if (!TInt.subtract(int_, min, offs)) {
            return false;
        }
        if (TInt.LT(offs, TInt.Zero)) {
            return false;
        }
        if (!TInt.toInt(offs, i)) {
            return false;
        }

        if ((i[0] <= n)) {
            expr[0] = constr.args[(int) i[0]];
            return true;
        }
        if ((constr.dots)) {
            expr[0] = constr.args[(int) n];
            return true;
        }
        // TODO: ^This would benefit from args being changed to fixed. Huh?
        return false;
    }

    /**
     * PRE: If expr is an array constructor, it is top-level and Checked.
     * If expr is an array constructor, assigning expr will require CT or RT array
     * shape check. (Otherwise, ArrayExpr will take care of shape checks.)
     * Will look through a ConsExpr.
     */
    public static boolean shapeCheckNeeded(Expr.T expr) {
        T top;
        top = arrayConstrExpr(expr);
        if (top == null) {
            return false;
        }
        AssertError.check(!top.isNested, "ArrayExpr");
        if (top.isNamedConst) {
            return false;
        }
        AssertError.check(top.state.compareTo(StateTyp.Representing) >= 0, "ArrayExpr");
        // ^So targetType is final.
        if (top.targetType == null) {
            return true;
        }
        if (OpenArrayType.openDepth(top.targetType) == 0) {
            return false;
        }
        return !useTargetVar(top);
    }

    // Externally dispatched-to:
    private static long staticLength(T constr) {
        // PRE: constr.checked.
        AssertError.check(constr.checked, "ArrayExpr");
        return constr.eltCt;
    }

    // Externally dispatched-to:
    private static boolean isZeroes(T constr, boolean lhs) {
        // PRE: constr.checked.
        if (constr.args == null) {
            return true; // Vacuously
        }
        for (int i = 0; i <= constr.args.length - 1; i++) {
            if (!Expr.isZeroes(constr.args[i])) {
                return false;
            }
        }
        return true;
    }

    // Externally dispatched-to:
    private static Expr.T evaluate(T constr) {
        // PRE: constr.checked.
        // Return a constant expr if constr is constant, otherwise NIL.
        // NOTE: This will fold any constant argument in place, even if the
        //       whole constructor is not constant.
        Expr.T expr;
        if (!constr.evalAttempted) {
            constr.evalAttempted = true;
            constr.is_const = true;
            for (int i = 0; i <= constr.args.length - 1; i++) {
                // WITH arg = constr.args^[i]
                expr = Expr.constValue(constr.args[i]);
                // ^This will change arg in place to its constant value, if it has one.
                if (expr == null) {
                    constr.is_const = false;
                } else {
                    constr.args[i] = expr;
                }
            }
        }
        if (constr.is_const) {
            return constr;
        } else {
            return null;
        }
    }

    // Externally dispatched-to:
    private static void genFPLiteral(T constr, M3Buf.T buf) {
        // PRE: constr.checked.
        // "FP" is for "fingerprint".
        long argCt;
        Expr.T expr;
        AssertError.check(constr.checked, "ArrayExpr");
        argCt = argCt(constr);
        M3Buf.putText(buf, "ARRAY<");
        for (int i = 0; i <= argCt - 1; i++) {
            if ((i > 0)) {
                M3Buf.putChar(buf, ',');
            }
            Expr.genFPLiteral(constr.args[i], buf);
        }
        if (constr.semIndexType != null && constr.dots && constr.eltCt > argCt) {
            expr = constr.args[(int) (argCt - 1)];
            expr = Expr.constValue(expr);
            for (long i = argCt; i <= constr.eltCt; i++) {
                // FIXME^ reintroduce the change below by making this loop go to constr.eltCt-1.
                // NOTE: Earlier, this loop incorrectly went to eltCt.  Fixing this
                //       could make earlier-written pickles unreadable by currently-
                //       compiled code.
                M3Buf.putChar(buf, ',');
                Expr.genFPLiteral(expr, buf);
            }
        }
        M3Buf.putChar(buf, '>');
    }

    // --------------------- Representation -------------------

    /**
     * PRE: If expr is an array constructor, it is top-level.
     * If so, arrange for it to be compiled having type 'type'.
     * Will look through a ConsExpr.
     */
    public static void noteTargetType(Expr.T expr, Type.T type) {
        T top;
        Type.T baseType;
        top = arrayConstrExpr(expr);
        if (top == null) {
            return;
        }
        AssertError.check(!top.isNested, "ArrayExpr");
        baseType = Type.stripPacked(type);
        // CHECK: What if it really is packed?  This can happen if the top-level
        //        constructor is a field.
        if (baseType == null) {
            return;
        }
        if (baseType == top.targetType) { // No change.
            return;
        }
        if (top.targetType == null) { // First time noted.
            if (top.state.compareTo(StateTyp.Representing) >= 0) {
                // Too late to use this info.
                Error.info("Target type supplied too late to use -- "
                           + "harmless except for possible efficiency loss.");
                return;
            } else {
                top.targetType = baseType;
            }
        } else {
            Error.info("Target type changed -- "
                       + "harmless except for possible efficiency loss.");
            top.targetType = baseType;
        }
        // Represent (top);
    }

    private static Type.T indexTypeForLength(long length, long origin) {
        // Type [0..length-1]
        Type.T result;
        int[] LastTI = TInt.newInt();
        boolean b = TInt.fromInt(length - 1, LastTI);
        AssertError.check(b, "ArrayExpr");
        result = SubrangeType.new_(TInt.Zero, LastTI, Int.T, /* builtin := */ false);
        // Don't call Type.Check on 'result'.  Type.Check expects syntactic
        // expressions for the bounds, and 'result' doesn't have them.
        result.origin = origin;
        // TODO: Cache these types, possibly inside SubrangeType.
        return result;
    }

    // Special values for checking element packings:
    private static final long eltPackInconsistent = -1;
    private static final long eltPackIrrelevant = -2;

    private static long commonEltPack(T top, Type.T superType, Type.T subType, long depth) {
        // Common eltPack of both types, most interestingly when superType
        // is open and subType is fixed.
        // eltPackIrrelevant, if these are not array types.
        // eltPackInconsistent, if element packings are unequal.
        // otherwise, the common eltPack.
        // PRE: Both superType and subType are assignable to top.levels^[depth].repType
        // PRE: subType <: superType, not necessarily properly.

        long result;
        Type.T[] superIndexType = new Type.T[1], superEltType = new Type.T[1];
        Type.T[] subIndexType = new Type.T[1], subEltType = new Type.T[1];
        long subEltPack, superEltPack, deeperStaticLen, deeperEltPack;
        boolean subIsArray, superIsArray;

        LevelInfoTyp levelInfo = top.levels[(int) depth];
        if (levelInfo.FixedOpenChecked) {
            return levelInfo.levEltPack;
        }
        subIsArray
            = ArrayType.split // Skips naming and packing.
                (subType, /* VAR */ subIndexType, /* VAR */ subEltType);
        superIsArray
            = ArrayType.split(superType, /* VAR */ superIndexType, /* VAR */ superEltType);
        AssertError.check(subIsArray == superIsArray, "ArrayExpr");
        if (!subIsArray) {
            result = eltPackIrrelevant;
        } else {
            if (superIndexType[0] != null) {
                // Both superType and subType are fixed arrays.
                AssertError.check(subIndexType[0] != null, "ArrayExpr");
                subEltPack = ArrayType.eltPack(subType);
                superEltPack = ArrayType.eltPack(superType);
                AssertError.check(subEltPack == superEltPack, "ArrayExpr");
                AssertError.check(subEltPack == levelInfo.levEltPack, "ArrayExpr");
                result = subEltPack; // Both are the same, by assignability.
            } else if (subIndexType[0] == null) {
                // Both superType and subType are open arrays.
                // No negative info about this level, but must check deeper.
                deeperEltPack
                    = commonEltPack(top, superEltType[0], subEltType[0], depth + 1);
                if (deeperEltPack == eltPackInconsistent) {
                    // Inconsistent propagates to the top.
                    result = eltPackInconsistent;
                } else {
                    result = deeperEltPack;
                    // ^Any shallower levels will both be open, and their lengths
                    //  will be ensured equal by other mechanisms, static or dynamic,
                    //  so no need to take the lengths into account.
                }
            } else { // superType is open, subType is fixed.
                // The interesting case.  A fixed/open pair.
                deeperEltPack
                    = commonEltPack(top, superEltType[0], subEltType[0], depth + 1);
                if (deeperEltPack == eltPackInconsistent) {
                    // Inconsistent propagates to the top.
                    result = eltPackInconsistent;
                } else if (deeperEltPack == eltPackIrrelevant) {
                    // This is the deepest array level.
                    subEltPack = ArrayType.eltPack(subType);
                    superEltPack = OpenArrayType.eltPack(superType);
                    AssertError.check(superEltPack == subEltPack, "ArrayExpr"); // By assignability.
                    AssertError.check(levelInfo.levEltPack == subEltPack, "ArrayExpr");
                    result = subEltPack;
                } else { // super has > 1 open levels, all matching fixed sub levels.
                    subEltPack = ArrayType.eltPack(subType);
                    deeperStaticLen = top.levels[(int) (depth + 1)].staticLen;
                    if (deeperStaticLen < 0) {
                        deeperStaticLen = 1;
                    }
                    superEltPack = deeperStaticLen * deeperEltPack;
                    if (superEltPack != subEltPack) { // An inconsistency.
                        result = eltPackInconsistent;
                    } else {
                        AssertError.check(levelInfo.levEltPack == subEltPack, "ArrayExpr");
                        // All fixed-typed cousins have deeperStaticLen.  So if
                        // any of them matches the one open type here, they all will.
                        result = subEltPack;
                    }
                }
            }
        }
        levelInfo.FixedOpenChecked = true;
        return result;
        // WITH
    }

    private static boolean checkFixedOpenEltPack(T top, Type.T typeX, Type.T typeY, long depth) {
        // Result: Not known to be bad.
        // PRE: top is a top-level constructor.
        // PRE: typeX and typeY are both assignable to repType of levels at 'depth'.
        //      Thus they can differ from it and each other only in fixedness vs.
        //      openness in each corresponding dimension.
        // This is all to ensure that fixed and open arrays of same element type and
        // that are copied-between have the same element packing.  This could maybe
        // be inferred from type checking, but it is rather complicated to do so.
        // So we verify it with this and its helper procedure CommonEltPack.
        long eltPack;
        AssertError.check(top.depth == 0, "ArrayExpr");
        if (!ArrayType.is(typeX)) {
            return true;
        }
        if (!ArrayType.is(typeY)) {
            return true;
        }
        if (typeX == null || typeY == null) {
            return true;
        }
        if (typeX == ErrType.T || typeY == ErrType.T) {
            return true;
        }
        if (Type.isEqual(typeX, typeY, null)) {
            return true;
        }
        if (OpenArrayType.is(typeX)) {
            eltPack = commonEltPack(top, typeX, typeY, depth);
        } else {
            eltPack = commonEltPack(top, typeY, typeX, depth);
        }
        return eltPack != eltPackInconsistent;
    }

    private static void eltPackAndCount(T top) {
        // Compute eltPack and static element count product, inside to out.
        // Port: eltPack is uninitialized in the original; it is assigned at
        // the deepest open level before it is read.
        long eltPack = 0, deeperStaticFlatEltCt, repOpenDepth;
        repOpenDepth = OpenArrayType.openDepth(top.repType);
        deeperStaticFlatEltCt = 1;
        for (long depth = top.levels.length - 1 - 1; depth >= 0; depth--) {
            LevelInfoTyp levelInfo = top.levels[(int) depth];
            if (levelInfo.staticLen >= 0) {
                levelInfo.staticFlatEltCt = deeperStaticFlatEltCt * levelInfo.staticLen;
                deeperStaticFlatEltCt = levelInfo.staticFlatEltCt;
            } else { // Start the product over.
                deeperStaticFlatEltCt = 1;
            }

            if (depth >= repOpenDepth) { // Fixed array.
                levelInfo.levEltPack = ArrayType.eltPack(levelInfo.repType);
            } else { // Open array.
                if (depth == repOpenDepth - 1) { // deepest open level.
                    eltPack = OpenArrayType.eltPack(levelInfo.repType);
                }
                levelInfo.levEltPack = eltPack;
                if (eltPack >= 0 && levelInfo.staticLen >= 0) {
                    eltPack = eltPack * levelInfo.staticLen;
                    // ^ Will propagate one level shallower.
                } else {
                    eltPack = eltPackIrrelevant;
                    // Will propagate all levels shallower.
                }
            }
            // WITH
        } // FOR
    }

    private static Type.T newArrayType(Type.T indexType, Type.T eltType, Type.T oldType) {
        Type.T Result;
        Type.Info info = new Type.Info();
        Result = ArrayType.new_(indexType, eltType);
        if (PackedType.is(oldType)) {
            Type.checkInfo(oldType, info);
            Result = PackedType.new_(info.size, Result);
        }
        return Result;
    }

    private static void represent(T top) {
        // PRE: top is the top of a tree of array constructors.
        // Construct the representation for this constructor tree.
        // This is postponed from Check until here to give clients needed opportunity
        // to call NoteTargetType first.
        Type.T levelType;
        Type.T[] levelIndexType = new Type.T[1], levelEltType = new Type.T[1];
        Type.T[] semIndexType = new Type.T[1], semEltType = new Type.T[1];
        Type.T repType, repSuccType;
        Type.T[] repIndexType = new Type.T[1], repEltType = new Type.T[1];
        long locStaticLen, deeperStaticFlatEltCt, deepestArrayDepth, eltsSize;
        long eltPack;
        Type.Info topRepTypeInfo = new Type.Info();
        boolean repSuccIsOpen, repSuccHasChanged, fixedOpenOK, b;
        AssertError.check(top.depth == 0, "ArrayExpr");
        if (top.state.compareTo(StateTyp.Represented) >= 0) {
            return;
        }
        AssertError.check(top.state.compareTo(StateTyp.Checked) >= 0, "ArrayExpr");
        if (top.broken) {
            top.state = StateTyp.Represented;
            return;
        }
        top.state = StateTyp.Representing;
        deepestArrayDepth = top.levels.length - 1 - 1;
        if (top.targetType != null && top.targetType != top.semType) {
            // CHECK: Callers will likely duplicate this assignability check.
            //        Can we eliminate the duplication?
            top.targetType = Type.check(top.targetType);
            if (!Type.isAssignable(top.targetType, top.semType)) {
                Error.msg("Array constructor is not assignable to expected type ( 2.6.8).");
                top.broken = true;
                top.state = StateTyp.Represented;
                return;
            }

            // Collect any static lengths from top.targetType into levels.
            // Do it outside in.
            levelType = top.targetType;
            for (long depth = 0; depth <= deepestArrayDepth; depth++) {
                LevelInfoTyp levelInfo = top.levels[(int) depth];
                b = ArrayType.split(levelType, /* VAR */ levelIndexType, /* VAR */ levelEltType);
                AssertError.check(b, "ArrayExpr");
                locStaticLen = lengthOfOrdType(levelIndexType[0]);
                if (locStaticLen >= 0) {
                    if (levelInfo.staticLen == Expr.lengthNonStatic) {
                        // semType is open in this dimension.  Make level static.
                        levelInfo.staticLen = locStaticLen;
                    } else if (locStaticLen != levelInfo.staticLen) {
                        // This is a statically detected error of normally runtime kind.
                        // This is really not the best place to do this.  It would be
                        // better to do it in Formal and AssignStmt.  But that entails
                        // substantial, widespread rework.  Only because we were given
                        // top.targetType, can we do it here.
                        Error.warn(depth,
                            "Shape mismatch will occur at runtime (2.3.1), at depth "
                            + Long.toString(depth));
                        mergeRTError(top, M3CG.RuntimeError.IncompatibleArrayShape,
                                     "Shape mismatch, depth " + Long.toString(depth));
                        top.broken = true;
                        top.state = StateTyp.Represented;
                        return;
                    }
                }
                levelType = levelEltType[0];
            }
        }

        // Construct the repType for the top-level constructor.
        if (top.targetType == null) {
            // repType comes solely from the array constructor and its semType.
            // Build it inside to out.
            repSuccIsOpen = false; // The innermost, non-array level.
            repSuccHasChanged = false;
            repSuccType = top.levels[top.levels.length - 1].semType;
            deeperStaticFlatEltCt = 1;
            eltPack = ArrayType.eltPack(top.levels[(int) deepestArrayDepth].semType);
            for (long depth = deepestArrayDepth; depth >= 0; depth--) {
                LevelInfoTyp levelInfo = top.levels[(int) depth];
                b = ArrayType.split(levelInfo.semType, /* VAR */ semIndexType, /* VAR */ semEltType);
                AssertError.check(b, "ArrayExpr");
                if (semIndexType[0] == null // SemType is open in this dimension,
                    && levelInfo.staticLen >= 0
                    // but it's statically constrained by context,
                    && !repSuccIsOpen) {
                    // ^and not forced open by an inner open dimension.
                    // Make it fixed in the repType.
                    repIndexType[0] = indexTypeForLength(levelInfo.staticLen, top.origin);
                    levelInfo.repIndexType = repIndexType[0];
                    levelInfo.repType
                        = newArrayType(repIndexType[0], repSuccType, levelInfo.semType);
                    levelInfo.repType = Type.check(levelInfo.repType);
                    repSuccHasChanged = true;
                } else {
                    repIndexType[0] = semIndexType[0];
                    levelInfo.repIndexType = repIndexType[0];
                    if (repSuccHasChanged) {
                        // Same indexType, different eltType pointer.
                        levelInfo.repType
                            = newArrayType(repIndexType[0], repSuccType, levelInfo.semType);
                        levelInfo.repType = Type.check(levelInfo.repType);
                    } else {
                        levelInfo.repType = levelInfo.semType;
                    }
                }

                repSuccType = levelInfo.repType;
                if (repIndexType[0] == null) {
                    repSuccIsOpen = true;
                }
                // WITH
            } // FOR
            top.repType = Type.stripPacked(repSuccType);
            top.levels[0].repType = top.repType;
            eltPackAndCount(top);
            if (top.shallowestDynDepth >= 0) {
                LevelInfoTyp levelInfo = top.levels[(int) top.deepestDynDepth];
                if (Math.floorMod(OpenArrayType.eltPack(levelInfo.repType), Target.Byte) != 0) {
                    Error.int_(top.deepestDynDepth,
                        "CM3 restriction: Elements of dynamic-length array "
                        + "constructor must have byte-multiple size. Dimension:");
                    top.broken = true;
                }
                // WITH
            }
        } else { // repType is targetType. Just copy targetType component pointers.
                 // Do it outside in.
            repType = top.targetType;
            for (long depth = 0; depth <= deepestArrayDepth; depth++) {
                LevelInfoTyp levelInfo = top.levels[(int) depth];
                b = ArrayType.split(repType, /* VAR */ repIndexType, /* VAR */ repEltType);
                AssertError.check(b, "ArrayExpr");
                if (depth == 0) {
                    top.repType = Type.stripPacked(repType);
                    levelInfo.repType = top.repType;
                } else {
                    levelInfo.repType = repType;
                }
                levelInfo.repIndexType = repIndexType[0];

                // Develop shallowestDynDepth and deepestDynDepth.
                if (levelInfo.staticLen == Expr.lengthNonStatic) {
                    // All cousins at 'depth' are open and nonstatic.
                    top.deepestDynDepth = depth;
                    if (top.shallowestDynDepth < 0) {
                        top.shallowestDynDepth = depth;
                    }
                }
                repType = repEltType[0];
                // WITH
            } // FOR

            eltPackAndCount(top);
        } // ELSE targetType # NIL

        // Compute representation sizes.
        if (top.shallowestDynDepth >= 0) {
            // Nonstatic. No static allocation.
            top.staticEltsSize = 0;
        } else {
            eltsSize = ArrayType.eltPack(top.levels[(int) deepestArrayDepth].repType);
            for (long depth = 0; depth <= deepestArrayDepth; depth++) {
                locStaticLen = top.levels[(int) depth].staticLen;
                AssertError.check(locStaticLen >= 0, "ArrayExpr");
                eltsSize = eltsSize * locStaticLen;
            } // FOR
            top.staticEltsSize = eltsSize;
        }
        top.repOpenDepth = OpenArrayType.openDepth(top.repType);
        if (top.repOpenDepth == 0) {
            top.dopeSize = 0;
        } else {
            top.dopeSize
                = M3.target.Address.size + top.repOpenDepth * M3.target.Integer.size;
        }
        top.totalSize = top.dopeSize + top.staticEltsSize;

        // Compute representation alignments.
        Type.checkInfo(top.repType, topRepTypeInfo);
        top.topRepAlign = topRepTypeInfo.alignment;
        top.topEltsAlign = ArrayType.eltAlign(top.repType);
        if (top.topEltsAlign < Target.Word8.align) {
            Error.int_(top.topEltsAlign,
                "CM3 restriction: array constructor cannot have sub-byte alignment.");
            top.topEltsAlign = Target.Word8.align;
        }

        // Make sure matching fixed and open arrays have the same eltPack.
        fixedOpenOK
            = checkFixedOpenEltPack(top, top.semType, top.repType, /* depth := */ 0);
        AssertError.check(fixedOpenOK, "ArrayExpr");

        // Apply repType to the tree of array constructors and Check.
        representRecurse(top);

        // NOTE: Trying to Check top.semType and top.repType now will segfault in
        //       Builder when elements are opaque,  due to Host.env being NIL.

        AssertError.check(top.repType != null, "ArrayExpr");
        top.state = StateTyp.Represented;
    }

    private static void representRecurse(T constr) {
        T top, argConstr;
        long argCt;
        Type.T argRepType;
        Expr.T argExpr;
        boolean fixedOpenOK;
        AssertError.check(constr.state.compareTo(StateTyp.Checked) >= 0, "ArrayExpr");
        constr.state = StateTyp.Representing;
        if (!constr.broken) {
            top = constr.top;
            argCt = argCt(constr);

            // Traverse the arguments, copying representation types into them.
            LevelInfoTyp argLevelInfo = top.levels[(int) (constr.depth + 1)];
            for (int i = 0; i <= argCt - 1; i++) {
                argExpr = constr.args[i];
                if (argExpr != null) {
                    argConstr = arrayConstrExpr(argExpr);
                    if (argConstr != null) {
                        if (argConstr.depth > 0) {
                            // argConstr is an inner array constructor.
                            representRecurse(argConstr);
                        } else {
                            // argConstr is a named-constant, top-level array constructor.
                            represent(argConstr); // In case it's a forward reference.
                        }
                    } else { // argExpr is non-array or non-constructor.
                        argRepType = Expr.repTypeOf(argExpr);
                        fixedOpenOK
                            = checkFixedOpenEltPack(top, argLevelInfo.repType, argRepType,
                                                    constr.depth + 1);
                        AssertError.check(fixedOpenOK, "ArrayExpr");
                    }
                }
            } // FOR args
            // WITH argLevelInfo

            // Copy repType from level to constructor.
            if (constr != top) {
                constr.repType = top.levels[(int) constr.depth].repType;
            }
        }
        constr.state = StateTyp.Represented;
    }

    // ---------------- Dependent on Represent ----------------

    private static void classify(T top) {
        // Classify the method/location for storing the constructor's value.
        // PRE: top.state >= StateTyp.Represented.
        if (top.resultKind != RKTyp.RKUnknown) {
            return;
        }
        AssertError.check(top.depth == 0, "ArrayExpr");
        AssertError.check(top.state.compareTo(StateTyp.Represented) >= 0, "ArrayExpr");
        if (evaluate(top) != null) { // It's a constant.
            top.resultKind = RKTyp.RKGlobal;
        } else if (usesAssignProtocol(top)) {
            // Build value directly into final location.
            if (top.repOpenDepth <= 0) {
                top.resultKind = RKTyp.RKDirectElts;
            } else {
                top.resultKind = RKTyp.RKDirectDoped;
            }
        } else { // Create and build into a temporary, to be copied later.
            if (top.repOpenDepth <= 0) {
                top.resultKind = RKTyp.RKTempElts;
            } else if (top.shallowestDynDepth < 0) {
                top.resultKind = RKTyp.RKTempStatic;
            } else {
                top.resultKind = RKTyp.RKTempDyn;
            }
        }
    }

    /**
     * Will look through a ConsExpr.
     * Result: Success.
     */
    public static boolean getBounds(Expr.T expr, int[] min, int[] max) {
        T constr;
        boolean b;
        constr = arrayConstrExpr(expr);
        if (constr == null) {
            return false;
        }
        if (constr.semIndexType == null) { // open array type
            TInt.assign(min, TInt.Zero);
            b = TInt.fromInt(argCt(constr) - 1, max);
            return b;
        } else {
            return Type.getBounds(constr.semIndexType, min, max);
        }
    }

    // Externally dispatched-to:
    private static Type.T typeOf(T constr) {
        // PRE: constr is Checked.
        AssertError.check(constr.state.compareTo(StateTyp.Checked) >= 0, "ArrayExpr");
        AssertError.check(constr.depth == 0, "ArrayExpr");
        AssertError.check(constr.type != null, "ArrayExpr");
        return constr.type;
    }

    // Externally dispatched-to:
    private static Type.T repTypeOf(T constr) {
        // PRE: constr is Checked.
        AssertError.check(constr.state.compareTo(StateTyp.Checked) >= 0, "ArrayExpr");
        AssertError.check(constr.depth == 0, "ArrayExpr");
        represent(constr.top);
        if (constr.repType == null) {
            AssertError.check(constr.broken, "ArrayExpr");
            return constr.semType;
        }
        return constr.repType;
    }

    // --- Prep ---

    /**
     * NOOP if expr is not an array constructor.  Otherwise:
     * PRE: expr is top-level
     * Arrange to use LHS from the CG stack to set nonstatic shape components.
     * Will look through a ConsExpr.
     */
    public static void noteUseTargetVar(Expr.T expr) {
        T top;
        top = arrayConstrExpr(expr);
        if (top == null) {
            return;
        }
        AssertError.check(!top.isNested, "ArrayExpr");
        if (top.state.compareTo(StateTyp.Prepped) >= 0) { // Called too late.
            Error.info("ArrayExpr.NoteUseTargetVar called too late"
                       + " -- harmless but for possible efficiency loss.");
            return;
        }
        AssertError.check(!top.useTargetVarLocked, "ArrayExpr");
        top.useTargetVar = true;
    }

    /**
     * Not in CM3: NOOP if expr is not an array constructor.  Otherwise,
     * undo NoteUseTargetVar before expr is prepped: its value is computed
     * and discarded, without a target on the CG stack (a RETURN whose
     * value a TRY EXCEPT ELSE absorbs).
     */
    public static void noteNoTargetVar(Expr.T expr) {
        T top;
        top = arrayConstrExpr(expr);
        if (top == null || top.useTargetVarLocked) {
            return;
        }
        top.useTargetVar = false;
    }

    private static boolean useTargetVar(T top) {
        // SIDE EFFECTS !!
        // PRE: top is top-level.
        AssertError.check(!top.isNested, "ArrayExpr");
        if (top.isNamedConst) {
            return false;
        }
        top.useTargetVarLocked = true;
        return top.useTargetVar;
    }

    // Externally dispatched-to:
    private static boolean usesAssignProtocol(Expr.T expr) {
        // PRE: IF expr is an array constructor, THEN it is top-level.
        // Will look through a ConsExpr.
        T top;
        top = arrayConstrExpr(expr);
        if (top == null) {
            return false;
        }
        AssertError.check(!top.isNested, "ArrayExpr");
        if (top.is_const) { // The constant protocol.
            return false;
        }
        if (top.doDirectAssign) {
            return true;
        }
        return useTargetVar(top);
    }

    // Externally dispatched-to:
    private static void prepLV(T top, boolean traced) {
        prep(top);
    }

    // Externally dispatched-to:
    private static void prep(T top) {
        // PRE: top.depth = 0.
        // Must be called IFF top is the top of a tree of array constructors.
        AssertError.check(top.depth == 0, "ArrayExpr");
        if (top.broken) {
            top.state = maxState(top.state, StateTyp.Prepped);
            return;
        }
        if (evaluate(top) != null) { // Won't change if Prep is recalled.
            // It's a constant.
            top.inConstArea = true;
            // Postpone InnerPrep until Compile, when we will have globalOffset
            // and globalEltsOffset to build into.
        } else if (!usesAssignProtocol(top)) {
            innerPrep(top);
        }
        // ELSE postpone InnerPrep until Compile & InnerCompile, when LHS will
        // have been pushed.
    }

    // Externally dispatched-to:
    private static void prepLiteral(T constr, Type.T type, boolean inConstArea) {
        // If NOT inConstArea, set up to compile into the initialized global variable area.
        T top;
        top = constr.top;
        AssertError.check(top.checked, "ArrayExpr");
        if (top.broken) {
            top.state = maxState(top.state, StateTyp.Prepped);
            return;
        }
        AssertError.check(evaluate(top) != null, "ArrayExpr");
        top.targetType = type;
        top.inConstArea = inConstArea;
        represent(top);
        // Postpone InnerPrep until GenLiteral, when we will have
        // globalOffset and globalEltsOffset to build into.
    }

    private static void genEvalFixingInfo(T top, CG.Val fixingDopeVal, long fixingExprDepth) {
        // PRE: top is a top-level array constructor.
        // PRE: NOT top.broken.
        // Gen RT code to compute eltBytepackVal and fixingLenVal, for all
        // fully dynamic levels .
        long depthWInFixingExpr, depthWInTopConstr;
        long eltPack, eltByteSize;
        Type.T repType;
        if (top.shallowestDynDepth < 0) { // No RT fixing info needed.
            return;
        }
        if (top.fixingInfoComputed) { // We already did this.
            return;
        }

        // Go through the dynamic levels inside to out.
        depthWInTopConstr = top.deepestDynDepth;

        repType = top.levels[(int) depthWInTopConstr].repType;
        AssertError.check(OpenArrayType.is(repType), "ArrayExpr");
        eltPack = top.levels[(int) top.deepestDynDepth].levEltPack;
        AssertError.check(Math.floorMod(eltPack, Target.Byte) == 0, "ArrayExpr");
        eltByteSize = Math.floorDiv(eltPack, Target.Byte);
        if (eltByteSize > 1) {
            CG.load_intt(eltByteSize);
        }

        while (true) {
            AssertError.check(top.shallowestDynDepth <= depthWInTopConstr, "ArrayExpr");
            depthWInFixingExpr = depthWInTopConstr - fixingExprDepth;
            LevelInfoTyp levelInfo = top.levels[(int) depthWInTopConstr];
            CG.push(fixingDopeVal);
            CG.open_size(depthWInFixingExpr); // *length*
            levelInfo.fixingLenVal = CG.pop();
            CG.push(levelInfo.fixingLenVal);
            if (depthWInTopConstr < top.deepestDynDepth || eltByteSize > 1) {
                CG.multiply(M3.target.Integer.cg_type);
            }
            levelInfo.eltBytepackVal = CG.pop();
            if (depthWInTopConstr <= top.shallowestDynDepth) {
                break;
            } else {
                CG.push(levelInfo.eltBytepackVal);
                depthWInTopConstr--;
            }
            // WITH
        } // LOOP
        top.fixingInfoComputed = true;
    }

    private static void initVarDope(T top, M3CG.Var var_) {
        // PRE: top.depth = 0.
        long length;
        if (top.repOpenDepth > 0) {
            CG.load_addr_of(var_, top.dopeSize, M3.target.Integer.align /* Irrelevant */);
            CG.store(var_, M3.rt.OA_elt_ptr, M3.target.Address.size,
                     M3.target.Address.align, M3.target.Address.cg_type);
            for (long i = 0; i <= top.repOpenDepth - 1; i++) {
                length = top.levels[(int) i].staticLen;
                AssertError.check(length >= 0, "ArrayExpr");
                CG.load_intt(length);
                CG.store(var_, M3.rt.OA_size_0 + i * M3.target.Integer.size,
                         M3.target.Integer.size, M3.target.Integer.align,
                         M3.target.Integer.cg_type);
            }
        }
    }

    /** Port: eltsAddrVal is the VAR result, passed as a one-element array. */
    private static void initValDope(T top, CG.Val val, CG.Val[] eltsAddrVal) {
        // PRE: top.depth = 0.
        long length;
        if (top.repOpenDepth <= 0) {
            CG.push(val);
            eltsAddrVal[0] = CG.pop();
        } else {
            // Elements pointer.
            CG.push(val);
            CG.boost_addr_alignment(M3.target.Address.align);
            CG.push(val);
            CG.boost_addr_alignment(M3.target.Address.align);
            CG.add_offset(top.dopeSize);
            eltsAddrVal[0] = CG.pop();
            CG.push(eltsAddrVal[0]);
            CG.boost_addr_alignment(top.topEltsAlign);
            CG.store_indirect(M3.target.Address.cg_type, M3.rt.OA_elt_ptr,
                              M3.target.Address.size);

            // Shape.
            for (long i = 0; i <= top.repOpenDepth - 1; i++) {
                length = top.levels[(int) i].staticLen;
                AssertError.check(length >= 0, "ArrayExpr");
                CG.push(val);
                CG.boost_addr_alignment(M3.target.Integer.align);
                CG.load_intt(length);
                CG.store_indirect(M3.target.Integer.cg_type,
                                  M3.rt.OA_size_0 + i * M3.target.Integer.size,
                                  M3.target.Integer.size);
            }
        }
    }

    private static void innerPrep(T top) {
        // PRE: top.depth = 0.
        // Must be called IFF top is the top of a tree of array constructors.
        // PRE LHS addr is on CG stack IFF UsesAssignProtocol.
        // POST If UsesAssignProtocol, LHS has been popped and stored in some field.
        M3CG.Var shapeVar = null;
        long shapeSize, length, offset;
        Procedure.T NewTracedArrayProc;
        Type.Info firstArgTypeInfo = new Type.Info();
        int name;
        if (top.state.compareTo(StateTyp.Prepped) >= 0) {
            return;
        }
        represent(top);
        if (top.broken) {
            top.state = maxState(top.state, StateTyp.Prepped);
            return;
        }
        classify(top);
        top.state = StateTyp.Prepping;

        // Set up result location info.
        switch (top.resultKind) {
            case RKUnknown -> AssertError.check(false, "ArrayExpr");
            case RKGlobal -> AssertError.check(top.globalOffset >= 0, "ArrayExpr"); // Already allocated.

            case RKDirectElts -> { // LHS elements address is atop the CG stack.
                CG.boost_addr_alignment(top.topRepAlign);
                top.buildEltsAddrVal = CG.pop(); // LHS points to elements.
            }

            case RKDirectDoped -> { // LHS dope address is atop the CG stack.
                CG.boost_addr_alignment(M3.target.Address.align);
                top.buildAddrVal = CG.pop(); // LHS points to dope.
                // Port: the VAR argument top.buildEltsAddrVal goes through a
                // one-element array; InitValDope does not read it through top.
                CG.Val[] buildEltsAddrVal = {top.buildEltsAddrVal};
                initValDope(top, top.buildAddrVal, /* OUT */ buildEltsAddrVal);
                top.buildEltsAddrVal = buildEltsAddrVal[0];
                genEvalFixingInfo(top, top.buildAddrVal, /* fixingExprDepth := */ 0);
            }

            case RKTempElts, RKTempStatic -> {
                // Allocate a static-sized CG.Var temp to build elements into.
                top.buildTempVar = CG.declare_temp(top.totalSize, top.topRepAlign,
                                                   CGType.Struct, /* in_memory := */ true);
                // TODO: CG.Declare_whatever, for this and other declared temps.

                if (top.resultKind == RKTyp.RKTempStatic) {
                    initVarDope(top, top.buildTempVar);
                }

                if (useTargetVar(top)) { // LHS address is on the CG stack.
                    CG.boost_addr_alignment(top.topRepAlign);
                    top.finalVal = CG.pop();
                }
            }

            case RKTempDyn -> {

                // Gen code to evaluate shape-derived dynamic info:
                if (useTargetVar(top)) { // LHS address is on the CG stack.
                    CG.boost_addr_alignment(M3.target.Address.align);
                    top.finalVal = CG.pop();
                    genEvalFixingInfo(top, top.finalVal, /* fixingExprDepth := */ 0);
                    // Postpone evaluating top.firstArgExpr.
                } else { // LHS is not pushed yet.  Use first arg to fix dynamic info.
                    Type.checkInfo(Expr.repTypeOf(top.firstArgExpr), firstArgTypeInfo);
                    Expr.prepLValue(top.firstArgExpr, /* traced := */ firstArgTypeInfo.isTraced);
                    Expr.compileAddress(top.firstArgExpr, /* traced := */ firstArgTypeInfo.isTraced);
                    top.firstArgDopeVal = CG.pop();
                    genEvalFixingInfo(top, top.firstArgDopeVal, top.firstArgDepth);
                }

                // Allocate a temporary "shape" array.  This confusing.  It is a
                // one-dimensional open array of integers, complete with contiguous
                // dope, to be passed to RT library procedure NewTracedArray, and
                // its elements are the shape of the n-dimensional array we are
                // building.
                shapeSize = top.dopeSize + M3.target.Integer.size;
                shapeVar = CG.declare_temp(shapeSize, M3.target.Address.align,
                                           CGType.Struct, /* in_memory := */ true);

                // Gen code to initialize the shape array.
                CG.load_addr_of(shapeVar,
                                M3.rt.OA_size_1 /* Not actually a size, rather 0th element. */,
                                M3.target.Integer.align);
                CG.store_addr(shapeVar, M3.rt.OA_elt_ptr); // Elements pointer.

                CG.load_intt(top.repOpenDepth);
                CG.store_int(M3.target.Integer.cg_type, shapeVar, M3.rt.OA_size_0); // Elt count.

                offset = M3.rt.OA_size_1; // 0th element.
                AssertError.check(top.shallowestDynDepth >= 0, "ArrayExpr");

                // Outer levels w/open representation, but a static value is imposed.
                // There will be at least one, for the outermost constructor.
                for (long depth = 0; depth <= top.shallowestDynDepth - 1; depth++) {
                    length = top.levels[(int) depth].staticLen;
                    AssertError.check(length >= 0, "ArrayExpr");
                    CG.load_intt(length);
                    CG.store(shapeVar, offset, M3.target.Integer.size, M3.target.Integer.align,
                             M3.target.Integer.cg_type);
                    offset += M3.target.Integer.size;
                }

                // Levels with dynamic, open length.  There will be at least one.
                for (long depth = top.shallowestDynDepth; depth <= top.deepestDynDepth; depth++) {
                    CG.push(top.levels[(int) depth].fixingLenVal);
                    CG.store(shapeVar, offset, M3.target.Integer.size, M3.target.Integer.align,
                             M3.target.Integer.cg_type);
                    offset += M3.target.Integer.size;
                }

                // Inner levels w/open representation, but a static value is imposed.
                for (long depth = top.deepestDynDepth + 1; depth <= top.repOpenDepth - 1; depth++) {
                    length = top.levels[(int) depth].staticLen;
                    AssertError.check(length >= 0, "ArrayExpr");
                    CG.load_intt(length);
                    CG.store(shapeVar, offset, M3.target.Integer.size, M3.target.Integer.align,
                             M3.target.Integer.cg_type);
                    offset += M3.target.Integer.size;
                }

                // Gen code to heap-allocate space for the Constructor's temp.
                // TODO: Would it be possible & better to allocate this untraced?
                // We didn't know whether refType would be needed, at Check time.
                top.refType = RefType.new_(top.repType, /* traced := */ true, /* brand := */ null);
                top.refType = Type.check(top.refType);
                Type.addCell(top.refType);
                NewTracedArrayProc = RunTyme.lookUpProc(RunTyme.Hook.NewTracedArray);
                Procedure.startCall(NewTracedArrayProc);
                if (M3.target.DefaultCall.args_left_to_right) {
                    Type.loadInfo(top.refType, -1);
                    CG.pop_param(CGType.Addr);
                    CG.load_addr_of(shapeVar, 0, M3.target.Address.align);
                    CG.pop_param(CGType.Addr);
                } else {
                    CG.load_addr_of(shapeVar, 0, M3.target.Address.align);
                    CG.pop_param(CGType.Addr);
                    Type.loadInfo(top.refType, -1);
                    CG.pop_param(CGType.Addr);
                }
                top.buildAddrVal = Procedure.emitValueCall(NewTracedArrayProc);
                // ^Dope portion will have been initialized.
                CG.push(top.buildAddrVal);
                CG.boost_addr_alignment(M3.target.Address.align);
                CG.open_elt_ptr(top.topEltsAlign);
                top.buildEltsAddrVal = CG.pop();

                // There must exist a variable of type top.refType, to get its typecell
                // emitted.  Also, we gen code to store address of heap-allocated temp
                // therein, so GC will leave it alone.
                name = M3ID.add(M3.ids, "Heap temp ptr, line "
                                + Long.toString(Math.floorMod(top.origin, 100000L)));
                // Port: the INTEGER result of Type.GlobalUID is passed as a
                // CG.TypeUID, which is an int.
                top.heapTempPtrVar
                    = CG.declare_local(name, M3.target.Address.size, M3.target.Address.align,
                                       CGType.Addr, (int) Type.globalUID(top.refType),
                                       /* in_memory := */ true, /* up_level := */ false,
                                       /* f := */ CG.Maybe);
                CG.push(top.buildAddrVal);
                CG.boost_addr_alignment(M3.target.Address.align);
                CG.store_addr(top.heapTempPtrVar, /* Offset := */ 0);

            }
        } // CASE top.resultKind

        // If UsesAssignProtocol, LHS addr is now stored in
        // top.buildAddrVar, top.buildEltsAddrVar, or top.finalVal,
        // depending on top.resultKind.

        // Traverse the tree of array constructors.
        prepRecurse(top, top, /* selfFlatOffset := */ 0, /* depth := */ 0);

        // Free temporaries:
        CG.free_temp(shapeVar);
        if (top.shallowestDynDepth >= 0) {
            for (long depth = top.shallowestDynDepth; depth <= top.deepestDynDepth; depth++) {
                CG.free(top.levels[(int) depth].fixingLenVal);
                CG.free(top.levels[(int) depth].eltBytepackVal);
            }
        }
        CG.free(top.dynByteOffsetVal);
        // CHECK: Will Free_temps get done sometime?
        top.state = StateTyp.Prepped;
    }

    private static void genCopyOpenArgValueDyn(T top, T constr, CG.Val argDopeAddrVal,
                                               long eltAlign) {
        // PRE: TOS is a CG.Val for address (w/in LHS) to copy to.
        // PRE: top.shallowestDynDepth >= 0
        long staticFlatEltCt;
        // Target address:
        CG.forceStacked();
        // Source address:
        CG.push(argDopeAddrVal);
        CG.boost_addr_alignment(M3.target.Address.align);
        CG.open_elt_ptr(eltAlign);
        CG.forceStacked();
        // Size in bytes:
        AssertError.check(top.shallowestDynDepth >= 0, "ArrayExpr");
        AssertError.check(constr.depth < top.shallowestDynDepth, "ArrayExpr");
        CG.push(top.levels[(int) top.shallowestDynDepth].eltBytepackVal);
        staticFlatEltCt
            = top.levels[(int) (constr.depth + 1 /* Of Arg. */)].staticFlatEltCt;
        if (staticFlatEltCt != 1) {
            CG.load_intt(staticFlatEltCt);
            CG.multiply(M3.target.Integer.cg_type);
        }
        // TODO: call NoteWrite, as needed.

        CG.copy_n(Target.Byte, /* overlap := */ false);
    }

    private static void genPushLHSEltsAddr(T top, long bitOffset, long eltAlign) {
        // PRE: top.depth = 0
        // Push the address of the element at bitOffset within the elements
        // portion of the temp or LHS location.
        switch (top.resultKind) {
            case RKUnknown -> AssertError.check(false, "ArrayExpr");
            case RKGlobal -> AssertError.check(false, "ArrayExpr");
            case RKDirectElts, RKDirectDoped, RKTempDyn -> {
                CG.push(top.buildEltsAddrVal);
                if (bitOffset != 0) {
                    AssertError.check(Math.floorMod(bitOffset, eltAlign) == 0, "ArrayExpr");
                    CG.add_offset(bitOffset);
                    CG.boost_addr_alignment(eltAlign);
                }
            }
            case RKTempElts -> CG.load_addr_of(top.buildTempVar, bitOffset, eltAlign);
            case RKTempStatic -> CG.load_addr_of(top.buildTempVar, top.dopeSize + bitOffset, eltAlign);
        } // CASE
    }

    private static void pushDynBytesizeVal(T top, long depth) {
        long eltPack, staticFlatEltCt;
        AssertError.check(top.shallowestDynDepth >= 0, "ArrayExpr");
        if (depth > top.deepestDynDepth) {
            eltPack = top.levels[(int) depth].levEltPack;
            AssertError.check(eltPack >= 0, "ArrayExpr");
            AssertError.check(Math.floorMod(eltPack, Target.Byte) == 0, "ArrayExpr");
            CG.load_intt(Math.floorDiv(eltPack, Target.Byte));
        } else if (depth < top.shallowestDynDepth) {
            CG.push(top.levels[(int) top.shallowestDynDepth].eltBytepackVal);
            staticFlatEltCt = top.levels[(int) depth].staticFlatEltCt;
            if (staticFlatEltCt != 1) {
                CG.load_intt(staticFlatEltCt);
                CG.multiply(M3.target.Integer.cg_type);
            }
        } else {
            CG.push(top.levels[(int) depth].eltBytepackVal);
        }
    }

    private static void prepRecurse(T top, T constr, long selfFlatOffset, long depth) {
        // Called only on an array constructor.
        // selfFlatOffset is the flattened offset from the beginning of top to the
        // beginning of constr.
        Type.T argRepType;
        CG.Val dotSsVal = null, argAddrVal = null;
        long topLab;
        long argCt;
        // Port: argFlatOffset and eltAlign are uninitialized in the original.
        // argFlatOffset is assigned in the loop over the explicit arguments
        // before the '..' portion reads it.  Sic: eltAlign is assigned only
        // for an explicit argument that is not a constructor; if there is
        // none, the '..' loop below reads it uninitialized.  It is 0 here.
        long argFlatOffset = 0;
        // ^Meaningful only when there are no dynamic levels.
        long constrEltPack;
        long depthWInArg, depthWInTopConstr, argOpenDepth;
        Expr.T lArgExpr;
        T argConstr;
        long eltAlign = 0;
        Type.Info argRepTypeInfo = new Type.Info();
        // PrepRecurse
        if (constr.broken) {
            return;
        }
        argCt = argCt(constr);
        AssertError.check(constr.state.compareTo(StateTyp.Represented) >= 0, "ArrayExpr");
        AssertError.check(depth < top.levels.length - 1, "ArrayExpr");
        AssertError.check(top.levels[(int) depth].staticLen >= 0, "ArrayExpr");
        constrEltPack = top.levels[(int) depth].levEltPack;
        LevelInfoTyp argLevelInfo = top.levels[(int) (depth + 1)];
        // Shape-check and assign the explicitly-coded arguments.
        for (int i = 0; i <= Math.min(argCt, constr.eltCt) - 1; i++) {
            Expr.T wArgExpr = constr.args[i];
            argFlatOffset = selfFlatOffset + i * constrEltPack;
            if (wArgExpr != null) {
                argConstr = arrayConstrExpr(wArgExpr);
                if (top.resultKind == RKTyp.RKGlobal
                    && argConstr != null
                    && argConstr.isNamedConst) {

                    // It's a named, constant array constructor.

                    // Retraverse it as if it were inline.
                    AssertError.check(argConstr.top == argConstr, "ArrayExpr");
                    AssertError.check(evaluate(argConstr) != null, "ArrayExpr");
                    prepLiteral(argConstr, argLevelInfo.repType, /* inConstArea := */ true);
                    prepRecurse(top, argConstr, argFlatOffset, depth + 1);
                } else if (argConstr != null && !argConstr.isNamedConst) {

                    // It's an inline array constructor.

                    prepRecurse(top, argConstr, argFlatOffset, depth + 1);
                } else {

                    // Not a constructor.  Handle wArgExpr here.

                    argRepType = Expr.repTypeOf(wArgExpr);
                    eltAlign = CG.GCD(top.topEltsAlign,
                                      Math.floorMod(argFlatOffset, M3.target.Word.size));
                    depthWInArg = 0;
                    if (top.resultKind == RKTyp.RKGlobal) {

                        // It's a literal, => no dynamic levels.

                        AssertError.check(top.shallowestDynDepth < 0, "ArrayExpr");
                        Expr.prepLiteral(wArgExpr, argLevelInfo.repType, top.inConstArea);
                        Expr.genLiteral(wArgExpr, top.globalEltsOffset + argFlatOffset,
                                        argLevelInfo.repType, top.inConstArea);

                    } else if (argLevelInfo.staticLen == Expr.lengthNonArray) {

                        // This arg is not an array.

                        AssertError.check(top.shallowestDynDepth < 0, "ArrayExpr");
                        AssertError.check(argLevelInfo.staticFlatEltCt == 1, "ArrayExpr");
                        AssertError.check(top.firstArgDopeVal == null, "ArrayExpr");
                        // TODO: Make do_direct work transitively through here.
                        AssignStmt.prepForEmit(argLevelInfo.repType, wArgExpr,
                            /* initializing := */ RKTypSetInitializing.contains(top.resultKind));
                        // CHECK: Do we need to do any Check[Load|Store]Traced?
                        genPushLHSEltsAddr(top, argFlatOffset, eltAlign);
                        AssignStmt.doEmit(argLevelInfo.repType, wArgExpr, eltAlign,
                                          /* initializing := */ true);
                        // CHECK: Does DoEmit take traced into account, as for the case below
                        // TODO: We are skipping AssignStmt.Compile here, which my fail to do
                        //       an Expr.NoteWrite.
                    } else if (top.shallowestDynDepth < 0) {

                        // Arg is a non-constructor array, possibly open, but made
                        // static by context from constructor.
                        // Top and all descendent constructors are fully static.

                        AssertError.check(top.firstArgDopeVal == null, "ArrayExpr");
                        // TODO: Make do_direct work transitively through here.
                        Expr.prep(wArgExpr);
                        Type.checkInfo(argRepType, /* OUT */ argRepTypeInfo);
                        Expr.compile(wArgExpr);
                        // It's an array, so Prep/Compile will push an address
                        // regardless of whether it's a designator.
                        argAddrVal = CG.pop();
                        argOpenDepth = OpenArrayType.openDepth(argRepType);
                        if (argOpenDepth > 0) {
                            // argAddrVal is address of arg's dope.
                            // Go thru open dimensions of wArgExpr, generating needed RT
                            // checks of any open & dynamic dimension against
                            // this level's static length.
                            depthWInArg = 0;
                            while (true) {
                                if (depthWInArg >= argOpenDepth) {
                                    break;
                                }
                                depthWInTopConstr
                                    = depth + 1 /* For arg */ + depthWInArg;
                                LevelInfoTyp openLevelInfo = top.levels[(int) depthWInTopConstr];
                                // Gen RT check, arg's dynamic vs. top's static length.
                                CG.push(argAddrVal);
                                CG.boost_addr_alignment(M3.target.Integer.align);
                                CG.open_size(depthWInArg);
                                AssertError.check(openLevelInfo.staticLen >= 0, "ArrayExpr");
                                CG.load_intt(openLevelInfo.staticLen);
                                CG.check_eq(M3.target.Integer.cg_type,
                                            M3CG.RuntimeError.IncompatibleArrayShape);
                                // WITH openLevelInfo
                                depthWInArg++;
                            } // LOOP
                        } // IF open element.
                        // Copy in the arg value.
                        genPushLHSEltsAddr(top, argFlatOffset, eltAlign);
                        CG.push(argAddrVal);
                        AssertError.check(argLevelInfo.staticLen >= 0, "ArrayExpr");
                        CG.copy(constrEltPack, /* overlap := */ false);
                    } else {

                        // The dynamic case.  top.shallowestDynDepth >= 0.
                        // Arg is a non-constructor array.  Also, all cousin args
                        // in the entire topmost constructor are open at this level
                        // and, at some depth, nonstatic, thus repType of argLevel
                        // and shallower levels is open array, and so is repType of
                        // wArgExpr.

                        AssertError.check(depth < top.shallowestDynDepth, "ArrayExpr");
                        AssertError.check(OpenArrayType.is(argLevelInfo.repType), "ArrayExpr");
                        AssertError.check(OpenArrayType.is(Expr.repTypeOf(wArgExpr)), "ArrayExpr");
                        AssertError.check(!constr.dots, "ArrayExpr");
                        AssertError.check(top.resultKind != RKTyp.RKGlobal, "ArrayExpr");
                        if (wArgExpr == top.firstArgExpr && top.firstArgDopeVal != null) {
                            // We previously compiled this arg's (dope) address.
                            CG.push(top.firstArgDopeVal);
                            argAddrVal = CG.pop();
                        } else { // Do so now.
                            Type.checkInfo(argRepType, /* OUT */ argRepTypeInfo);
                            Expr.prep(wArgExpr);
                            Expr.compile(wArgExpr);
                            argAddrVal = CG.pop();
                            // This is a bit daring, but no need to RT store in
                            // top.firstArgDopeVal, because we won't come through here
                            // again with wArgExpr = top.firstArgExpr.
                        }
                        // argAddrVal is address of arg's dope.

                        // Sic: MOD applies to the result of GCD.
                        eltAlign = Math.floorMod(
                            CG.GCD(top.topEltsAlign,
                                   OpenArrayType.eltPack(
                                       top.levels[(int) top.deepestDynDepth].repType)),
                            M3.target.Word.size);

                        // Go thru array dimensions of wArgExpr, generating needed
                        // RT checks.
                        AssertError.check(top.fixingInfoComputed, "ArrayExpr");
                        depthWInArg = 0; // Array depth within the argument.
                        argOpenDepth = OpenArrayType.openDepth(argRepType);
                        while (true) { // Through open levels of arg.
                            if (depthWInArg >= argOpenDepth) {
                                break;
                            }
                            depthWInTopConstr
                                = depth + 1 /* For arg */ + depthWInArg;
                            LevelInfoTyp openLevelInfo = top.levels[(int) depthWInTopConstr];
                            AssertError.check(openLevelInfo.staticLen != Expr.lengthNonArray,
                                              "ArrayExpr");
                            if (openLevelInfo.staticLen >= 0) {
                                // Arg is open but statically constrained at this level.
                                // Check that RT Arg length = static constraint.
                                CG.push(argAddrVal);
                                CG.boost_addr_alignment(M3.target.Integer.align);
                                CG.open_size(depthWInArg);
                                CG.load_intt(openLevelInfo.staticLen);
                                CG.check_eq(M3.target.Integer.cg_type,
                                            M3CG.RuntimeError.IncompatibleArrayShape);
                            } else {
                                top.useTargetVarLocked = true;
                                if (useTargetVar(top)
                                    || top.resultKind == RKTyp.RKDirectDoped
                                    || wArgExpr != top.firstArgExpr) {
                                    // Arg is open and dynamically constrained at this level.
                                    // Gen RT check against openLevelInfo.fixingLenVal.
                                    CG.push(argAddrVal);
                                    CG.boost_addr_alignment(M3.target.Integer.align);
                                    CG.open_size(depthWInArg);
                                    AssertError.check(openLevelInfo.fixingLenVal != null,
                                                      "ArrayExpr");
                                    CG.push(openLevelInfo.fixingLenVal);
                                    CG.check_eq(M3.target.Integer.cg_type,
                                                M3CG.RuntimeError.IncompatibleArrayShape);
                                }
                            }
                            // WITH openLevelInfo
                            depthWInArg++;
                        } // LOOP thru' open levels of arg.

                        // Copy arg into LHS and increment LHS dynamic offset.
                        genPushLHSEltsAddr(top, /* bitOffset := */ 0, /* eltAlign := */ eltAlign);
                        if (top.dynByteOffsetVal == null) {
                            // First time. Dynamic offset is zero.
                            genCopyOpenArgValueDyn(top, constr, argAddrVal, eltAlign);
                            pushDynBytesizeVal(top, depth + 1);
                            top.dynByteOffsetVal = CG.pop();
                        } else {
                            CG.forceStacked();
                            CG.push(top.dynByteOffsetVal);
                            CG.index_bytes(Target.Byte);
                            genCopyOpenArgValueDyn(top, constr, argAddrVal, eltAlign);
                            if (wArgExpr != top.lastArgExpr) {
                                CG.push(top.dynByteOffsetVal);
                                pushDynBytesizeVal(top, depth + 1);
                                CG.add(M3.target.Integer.cg_type);
                                top.dynByteOffsetVal = CG.pop();
                            }
                        }

                    } // IF [non]static.
                } // IF argConstr # NIL
            } // IF wArgExpr # NIL..ELSE
            // WITH wArgExpr
        } // FOR args

        if (constr.dots && 0 < argCt && argCt < constr.eltCt) {
            lArgExpr = constr.args[(int) (argCt - 1)];
            if (lArgExpr != null) {

                // Fill in dots portion.
                AssertError.check(top.shallowestDynDepth < 0
                                  || depth + 1 == top.shallowestDynDepth, "ArrayExpr");
                AssertError.check(!OpenArrayType.is(constr.semType), "ArrayExpr");
                AssertError.check(argFlatOffset >= 0, "ArrayExpr");
                argConstr = arrayConstrExpr(lArgExpr);
                switch (top.resultKind) {

                    case RKGlobal -> {
                        if (argConstr != null) {
                            for (long argNo = argCt; argNo <= constr.eltCt - 1; argNo++) {
                                argFlatOffset += constrEltPack;
                                prepRecurse(top, argConstr, argFlatOffset, depth + 1);
                            } // FOR
                        } else { // Not an array constructor.
                            Expr.prepLiteral(lArgExpr, argLevelInfo.repType, top.inConstArea);
                            for (long argNo = argCt; argNo <= constr.eltCt - 1; argNo++) {
                                argFlatOffset += constrEltPack;
                                Expr.genLiteral(lArgExpr, top.globalEltsOffset + argFlatOffset,
                                                argLevelInfo.repType,
                                                top.inConstArea);
                            } // FOR
                        } // IF
                    }
                    default -> { // Generate a RT loop to fill in the '..' section
                        // TODO: Is it worth it to unroll this loop for low iteration counts, or
                        //       just let the back end do it?
                        CG.load_intt(argCt);
                        dotSsVal = CG.pop_temp();
                        topLab = CG.next_label();
                        CG.set_label(topLab);

                        // Gen code for ARRAY[dotSsVal] := ARRAY[argCt-1]
                        LevelInfoTyp constrLevelInfo = top.levels[(int) depth];
                        genPushLHSEltsAddr(top, /* bitOffset := */ 0, /* eltAlign := */ eltAlign);
                        CG.push(dotSsVal);
                        ArrayType.genIndex(constrLevelInfo.repType); // Addr to store to.
                        genPushLHSEltsAddr(top, argFlatOffset, eltAlign);
                        // ^Addr to fetch from -- where last explicit arg was assigned into
                        //  constructor.
                        if (ArrayType.eltsAreBitAddressed(constrLevelInfo.repType)) {
                            CG.load_indirect(M3.target.Integer.cg_type, 0, constrEltPack, eltAlign);
                            // CHECK: That this won't straddle word boundary.
                            CG.store_indirect(M3.target.Integer.cg_type, 0, constrEltPack);
                        } else {
                            CG.copy(constrEltPack, /* overlap := */ false);
                        }
                        // WITH constrLevelInfo

                        // Gen RT code for dotSsVal := dotSsVal + 1
                        CG.push(dotSsVal);
                        CG.load_intt(1);
                        CG.add(M3.target.Integer.cg_type);
                        CG.store_temp(dotSsVal);

                        // Gen RT code for IF (dotSsVal < NUMBER(ARRAY) GOTO topLab
                        CG.push(dotSsVal);
                        CG.load_intt(constr.eltCt);
                        CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.LT, topLab,
                                      CG.Likely);

                        CG.free(dotSsVal);
                    }
                } // CASE
            } // IF lArgExpr # NIL
        } // IF dots, etc.
        // WITH argLevelInfo
        CG.free(argAddrVal);
    }

    // -------------------------- Compile ---------------------

    private static void initLiteralDope(T top, boolean inConstArea) {
        // PRE: top.repOpenDepth > 0
        // Initialize dope fields of literal.
        long shapeOffset, eltCt;
        AssertError.check(top.depth == 0, "ArrayExpr");
        AssertError.check(top.resultKind == RKTyp.RKGlobal, "ArrayExpr");
        AssertError.check(top.repOpenDepth > 0, "ArrayExpr");
        AssertError.check(top.containingUnit == Module.current(), "ArrayExpr");
        CG.init_var(top.globalOffset + M3.rt.OA_elt_ptr,
                    Module.globalData(inConstArea), top.globalEltsOffset, inConstArea);

        shapeOffset = top.globalOffset + M3.rt.OA_size_0;
        for (long i = 0; i <= top.repOpenDepth - 1; i++) {
            eltCt = top.levels[(int) i].staticLen;
            CG.init_intt(shapeOffset, M3.target.Integer.size, eltCt, inConstArea);
            shapeOffset += M3.target.Integer.pack;
        }
    }

    // Externally dispatched-to:
    private static void compileLV(T top, boolean traced, boolean StaticOnly) {
        compile(top, StaticOnly);
    }

    // Externally dispatched-to:
    private static void compile(T top, boolean StaticOnly) {
        // PRE: top.depth = 0
        // PRE: LHS addr is on CG stack IFF UsesAssignProtocol.
        AssertError.check(top.depth == 0, "ArrayExpr");
        represent(top);
        if (top.broken) {
            CG.load_nil();
            top.state = StateTyp.Compiled;
            return;
        }
        classify(top);

        // Allocate static space if needed.
        if (top.resultKind == RKTyp.RKGlobal && top.globalOffset < 0) {
            top.containingUnit = Module.current();
            top.globalOffset
                = Module.allocate(top.totalSize, top.topRepAlign, top.inConstArea,
                                  /* tag := */ "ArrayExpr");
            if (top.repOpenDepth == 0) { // No dope, elements only.
                top.globalEltsOffset = top.globalOffset;
            } else { // Dope and elements.
                top.globalEltsOffset = top.globalOffset + top.dopeSize;
                initLiteralDope(top, top.inConstArea);
            }
        }
        innerPrep(top);
        innerCompile(top, StaticOnly);
        checkUseFailure(top);
    }

    // Externally dispatched-to:
    private static void genLiteral(T top, long globalOffset, Type.T type, boolean inConstArea) {
        // PRE: top.depth = 0
        long allocSize;
        AssertError.check(top.depth == 0, "ArrayExpr");
        AssertError.check(top.checked, "ArrayExpr");
        AssertError.check(evaluate(top) != null, "ArrayExpr");
        AssertError.check(top.is_const, "ArrayExpr");
        // NOTE: top.shallowestDynDepth could be >= 0, but since top.is_const,
        //       this can happen only if the dynamic level(s) have no elements,
        //       due to empty constructor(s) somewhere, i.e. '<type>{}'.
        //       So we don't care here.
        AssertError.check(top.inConstArea == inConstArea, "ArrayExpr");

        represent(top);
        if (top.broken) {
            top.state = StateTyp.Compiled;
            return;
        }
        classify(top);
        AssertError.check(top.resultKind == RKTyp.RKGlobal, "ArrayExpr");
        compileGeneratedTypes(top);
        if (true || top.globalOffset < 0) { // Not yet computed.
            top.containingUnit = Module.current();
            top.globalOffset = globalOffset;
            if (top.repOpenDepth <= 0) {
                // No dope.  globalOffset leads to space in a static area that our
                // caller has allocated for the array elements.
                top.globalEltsOffset = globalOffset;
            } else {
                // globalOffset leads to space in a static area that our
                // caller has allocated for the dope only.
                // Allocate space for the elements now.
                AssertError.check(top.staticEltsSize >= 0, "ArrayExpr");
                if (top.staticEltsSize == 0) {
                    // Just for safety, for a constant (thus static) empty array, let's
                    // allocate storage for one innermost dimension's element, thus an
                    // address that is unique from any other constant.
                    allocSize
                        = ArrayType.eltPack(top.levels[top.levels.length - 1 - 1].repType);
                } else {
                    allocSize = top.staticEltsSize;
                }
                top.globalEltsOffset
                    = Module.allocate(allocSize, top.topEltsAlign, inConstArea,
                                      /* tag := */ "StaticOpenArrayElements");
                initLiteralDope(top, inConstArea);
            }

            top.state = StateTyp.Prepping;
            prepRecurse(top, top, /* selfFlatOffset := */ 0, /* depth := */ 0);
            top.state = StateTyp.Prepped;
        }
    }

    private static void compileGeneratedTypes(T top) {
        // PRE: top.depth = 0
        // POST: result address on TOS, even if called > once.
        if (top.levels != null) {
            for (int i = 0; i <= top.levels.length - 1; i++) {
                LevelInfoTyp levelInfo = top.levels[i];
                Type.compile(levelInfo.repType);
                Type.compile(levelInfo.repIndexType);
            }
        }
        top.semType = Type.check(top.semType);
        Type.compile(top.semType);
        top.repType = Type.check(top.repType);
        Type.compile(top.repType);
        Type.compile(top.refType);
    }

    private static void innerCompile(T top, boolean StaticOnly) {
        // PRE: top.depth = 0
        if (top.broken) {
            if (!StaticOnly) {
                CG.load_nil();
            }
            top.state = StateTyp.Compiled;
            return;
        }
        compileGeneratedTypes(top);

        switch (top.resultKind) {
            case RKUnknown -> AssertError.check(false, "ArrayExpr");

            case RKGlobal -> {
                if (!StaticOnly) {
                    Module.loadGlobalAddr(top.containingUnit, /* offset := */ top.globalOffset,
                                          /* is_const := */ top.inConstArea);
                }
            }

            case RKDirectElts -> {
                AssertError.check(!StaticOnly, "ArrayExpr");
                CG.push(top.buildEltsAddrVal);
                CG.free(top.buildEltsAddrVal);
            }

            case RKDirectDoped -> {
                AssertError.check(!StaticOnly, "ArrayExpr");
                CG.push(top.buildAddrVal);
                CG.free(top.buildAddrVal);
            }

            case RKTempElts, RKTempStatic -> {
                // We built into a static-sized temp Var.
                AssertError.check(!StaticOnly, "ArrayExpr");
                AssertError.check(top.buildTempVar != null, "ArrayExpr");
                AssertError.check((top.finalVal == null) == (!usesAssignProtocol(top)), "ArrayExpr");
                if (top.finalVal == null) {
                    CG.load_addr_of_temp(top.buildTempVar, 0, top.topRepAlign);
                } else {
                    if (top.state.compareTo(StateTyp.Compiled) < 0) { // Don't copy twice.
                        CG.push(top.finalVal);
                        CG.load_addr_of_temp(top.buildTempVar, 0, top.topRepAlign);
                        CG.copy(top.totalSize, /* overlap := */ false);
                    }
                    CG.push(top.finalVal);
                    CG.free(top.finalVal);
                }
            }

            case RKTempDyn -> {
                // We built into a heap-allocated, dynamic-sized, contiguous
                // dope-and-elements temp.
                AssertError.check(!StaticOnly, "ArrayExpr");
                AssertError.check((top.finalVal == null) == (!usesAssignProtocol(top)), "ArrayExpr");
                if (top.finalVal == null) {
                    CG.push(top.buildAddrVal); // Return the temp.
                    // CHECK: Leave top.heapTempPtrVar alone.  If used in a WITH-binding, WithStmt
                    //        will copy only the dope into a variable.  This will maintain only
                    //        a pointer to the elements portion of the temporary.  Is a pointer
                    //        to the interior of a heap object enough to protect it from GC?
                } else { // Copy the temp into the provided LHS area.
                    if (top.state.compareTo(StateTyp.Compiled) < 0) { // Don't copy twice.
                        // CHECK^ Actually, do we want to copy multiple times?
                        CG.push(top.finalVal);
                        CG.push(top.buildAddrVal);
                        CG.load_intt(top.levels[0].staticFlatEltCt);
                        // TODO ^Eliminate multiply by one.
                        AssertError.check(top.shallowestDynDepth >= 0, "ArrayExpr");
                        CG.push(top.levels[(int) top.shallowestDynDepth].eltBytepackVal);
                        CG.multiply(M3.target.Integer.cg_type);
                        CG.load_intt(top.dopeSize);
                        CG.add(M3.target.Integer.cg_type);
                        CG.copy_n(/* s := */ 1 /* Unit size */, /* overlap := */ false);
                    }
                    CG.push(top.finalVal);
                    CG.free(top.finalVal);

                    // Now we can NIL out the heap object pointer, to be collected.
                    if (top.heapTempPtrVar != null) {
                        CG.load_nil();
                        CG.store_addr(top.heapTempPtrVar, /* Offset := */ 0);
                    }
                }
            }
        } // CASE

        top.state = StateTyp.Compiled;
    }

    /**
     * Set Code and Msg if they are not set and expr is known to produce a
     * statically unconditional runtime error when evaluated.
     */
    public static void checkStaticRTErrEval(Expr.T expr, M3CG.RuntimeError[] Code, String[] Msg) {
        // Return the first-discovered error found and stored during Check.
        T constrExpr;
        if (Code[0] != M3CG.RuntimeError.Unknown) {
            return;
        }
        constrExpr = arrayConstrExpr(expr);
        if (constrExpr == null) {
            // nothing
        } else {
            T top = constrExpr;
            AssertError.check(top.state.compareTo(StateTyp.Checked) >= 0, "ArrayExpr");
            if (top.RTErrorCode != M3CG.RuntimeError.Unknown) {
                Code[0] = top.RTErrorCode;
                Msg[0] = top.RTErrorMsg;
            }
        }
    }

    /**
     * PRE: expr has been Checked.
     * Set Code and Msg if they are not set and expr is known to produce a
     * statically unconditional runtime error when assigned to a variable
     * of lhsType.
     */
    public static void checkStaticRTErrAssign(Type.T lhsType, Expr.T expr,
                                              M3CG.RuntimeError[] Code, String[] Msg) {
        // Although runtime shape checks in general require the LHS variable, we
        // are interested here only in statically detectable mismatches, and static
        // components of the LHS shape come entirely from its type.
        T constrExpr;
        Type.T[] indexType = new Type.T[1], eltType = new Type.T[1];
        Type.T lhsInnerType;
        long arrayDepth, lhsNumber;
        String locMsg;
        boolean b;
        constrExpr = arrayConstrExpr(expr);
        if (constrExpr == null) {
            // nothing
        } else {
            T top = constrExpr;
            AssertError.check(top.state.compareTo(StateTyp.Checked) >= 0, "ArrayExpr");
            lhsInnerType = lhsType;
            arrayDepth = top.levels.length - 1;
            for (long depth = 0; depth <= arrayDepth - 1; depth++) {
                LevelInfoTyp levelInfo = top.levels[(int) depth];
                b = ArrayType.split(lhsInnerType, indexType, eltType);
                AssertError.check(b, "ArrayExpr");
                if (indexType[0] != null && levelInfo.staticLen >= 0) {
                    lhsNumber = lengthOfOrdType(indexType[0]);
                    if (levelInfo.staticLen != lhsNumber) {
                        // Found a static runtime shape mismatch.
                        locMsg = "Shape mismatch";
                        if (arrayDepth > 1) {
                            locMsg = locMsg + ", dimension " + Long.toString(depth);
                        }
                        locMsg
                            = locMsg + ", is " + Long.toString(levelInfo.staticLen)
                              + ", must be " + Long.toString(lhsNumber) + "(2.3.1.)";
                        Error.warn(2, "Will raise runtime error if executed: " + locMsg);
                        if (Code[0] == M3CG.RuntimeError.Unknown) { // It's the first.
                            Code[0] = M3CG.RuntimeError.IncompatibleArrayShape;
                            Msg[0] = locMsg;
                        }
                    }
                }
                lhsInnerType = eltType[0];
            }
        }
    }

    // Externally dispatched-to:
    private static boolean checkUseFailure(T top) {
        AssertError.check(top.state.compareTo(StateTyp.Checked) >= 0, "ArrayExpr");
        if (AssignStmt.doGenRTAbort(top.RTErrorCode) && evaluate(top) != null) {
            CG.comment(top.globalOffset, top.inConstArea,
                       "Use of array constructor with statically detected runtime error: ",
                       top.RTErrorMsg);
            CG.abort(top.RTErrorCode);
            return false;
        } else {
            return true;
        }
    }
}
