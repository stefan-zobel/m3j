// Java port of CM3's m3-sys/m3front/src/exprs/Expr.i3 and the Expr part of
// Expr.m3 (the ExprRep part is in ExprRep.java).
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: Expr.i3
// Last Modified On Tue Jun 20 15:17:31 PDT 1995 By kalsow

// File: Expr.m3
// Last Modified On Tue Jun 20 15:44:34 PDT 1995 By kalsow
//      Modified On Thu Jun 15 13:07:24 PDT 1995 By ericv
//      Modified On Fri Dec 21 01:21:51 1990 By muller

public final class Expr {
    private Expr() {}

    /**
     * Port: M3.Expr, with the fields and methods of its revelation in
     * ExprRep.
     */
    public static class T extends M3.Node {
        public Type.T type;
        public Type.T repType;
        public long align;
        public boolean checked;
        public boolean directAssignableType;
        public boolean doDirectAssign;
        public boolean isNamedConst;

        public Type.T typeOf() {
            return ExprRep.noType(this);
        }

        public Type.T repTypeOf() {
            return ExprRep.noType(this);
        }

        public void check(M3.CheckState cs) {
            ExprRep.noCheck(this, cs);
        }

        public boolean isEqual(T e, Type.Assumption x) {
            return ExprRep.neverEq(this, e, x);
        }

        public T evaluate() {
            return ExprRep.noValue(this);
        }

        public void getBounds(int[] min, int[] max) {
            ExprRep.noBounds(this, min, max);
        }

        public boolean isWritable(boolean lhs) {
            return ExprRep.isNever(this, lhs);
        }

        public boolean isDesignator(boolean lhs) {
            return ExprRep.isNever(this, lhs);
        }

        public final boolean isDesignator() {
            return isDesignator(false);
        }

        public boolean isZeroes(boolean lhs) {
            return ExprRep.isNever(this, lhs);
        }

        public final boolean isZeroes() {
            return isZeroes(false);
        }

        public void need_addr() {
            ExprRep.notAddressable(this);
        }

        public void genFPLiteral(M3Buf.T mbuf) {
            ExprRep.noFPLiteral(this, mbuf);
        }

        public void prepLiteral(Type.T type, boolean is_const) {
            ExprRep.noPrepLiteral(this, type, is_const);
        }

        /**
         * Puts literal value in global area of current unit, if it's constant and
         * structured.  Does not leave address on CG stack.
         */
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            ExprRep.noLiteral(this, offset, type, is_const);
        }

        public void prep() {
            ExprRep.noPrep(this);
        }

        public void compile(boolean StaticOnly) {
            ExprRep.noCompile(this, StaticOnly);
        }

        public void prepLV(boolean traced) {
            ExprRep.notLValue(this, traced);
        }

        public void compileLV(boolean traced, boolean StaticOnly) {
            ExprRep.notLValueBool(this, traced, StaticOnly);
        }

        public void prepBR(long true_, long false_, int freq) {
            ExprRep.notBoolean(this, true_, false_, freq);
        }

        public void compileBR(long true_, long false_, int freq) {
            ExprRep.notBoolean(this, true_, false_, freq);
        }

        public void note_write() {
            ExprRep.notWritable(this);
        }

        public long exprAlign() {
            return ExprRep.exprAlignDefault(this);
        }

        public long staticLength() {
            return ExprRep.staticLengthDefault(this);
        }

        public boolean usesAssignProtocol() {
            return ExprRep.usesAssignProtocolDefault(this);
        }

        public boolean checkUseFailure() {
            return ExprRep.defaultCheckUseFailure(this);
        }
    }

    // Port: List = REF ARRAY OF T is T[].

    // Port: CheckState = M3.CheckState.

    //*** phase 1 ***

    //******************************************************************

    /** parses an expression */
    public static Expr.T parse() {
        return ExprParse.E0(false);
    }

    // Port: Init is ExprRep.init.

    //******************************************************************

    //*** phase 2 ***

    /**
     * returns the type of the expression.
     * Not reliable in general until t has been 'Check'ed.
     */
    public static Type.T typeOf(Expr.T t) {
        // This is a confused mess.  Some expression kinds eagerly compute the type
        // in 'New'.  Some give it a tentative non-nil value in 'New', but change it
        // in 'Check', sometimes only to ErrType.T, sometimes to the real type.
        // 'TypeOf' will return the tentative value if called prior to 'Check'.
        // All such would ASSERT FALSE, if their 'typeOf' method were called, which
        // fortunately, can't happen.  For the others, 'TypeOf' computes and caches
        // the type on-demand, using the expression's 'typeOf' method.
        if ((t == null)) {
            return ErrType.T;
        }
        if ((t.type == null)) {
            t.type = t.typeOf();
        }
        return t.type;
    }

    /**
     * returns the semantic type of the expression, as in source code.
     * Not reliable in general until t has been 'Check'ed.
     */
    public static Type.T semTypeOf(Expr.T t) {
        return typeOf(t);
    }

    /**
     * returns the representation type of the expression, as in memory.
     * PRE: t is Checked.
     */
    public static Type.T repTypeOf(Expr.T t) {
        // Works the same way as TypeOf.
        Expr.T stripped;
        stripped = stripNamedCons(t);
        if (stripped == null) {
            stripped = t;
        }
        if (stripped.repType == null) {
            stripped.repType = stripped.repTypeOf();
        }
        return stripped.repType;
    }

    // Port: lengthTyp = INTEGER is long.
    // NOTE: Modula3 uses "length" to mean an array element count.

    public static final long lengthNonStatic = -1;

    public static final long lengthNonArray = -2;

    public static final long lengthInvalid = -3;

    public static long staticLength(Expr.T t) {
        if ((t == null)) {
            return lengthInvalid;
        }
        return t.staticLength();
    }

    // Port: StaticLengthDefault is ExprRep.staticLengthDefault.

    /** See ArrayExpr.m3 for explanation of protocols. */
    public static boolean usesAssignProtocol(Expr.T rhs) {
        if (rhs == null) {
            return false;
        }
        if (CallExpr.isUserProc(rhs)) {
            return rhs.doDirectAssign;
        }

        // TODO ^This one-liner is an easy temporary way to sidestep having to add
        //       massive and widely scattered apparatus for three different levels of
        //       dispatching, using two different mechanisms, just to get
        //       rhs.usesAssignProtocol to handle this case.
        return rhs.usesAssignProtocol();
    }

    // Port: UsesAssignProtocolDefault and DefaultCheckUseFailure are in
    // ExprRep.

    /** typechecks the expression. */
    public static void typeCheck(Expr.T t, M3.CheckState cs) {
        long save;
        if ((t == null)) {
            return;
        }
        if ((t.checked)) {
            return;
        }
        save = Scanner.offset;
        Scanner.offset = t.origin;
        t.check(cs);
        Scanner.offset = save;
        t.checked = true;
    }

    //******************************************************************

    //*** phase 3 ***

    /**
     * Returns NIL if t is not a constant, otherwise returns a simplified
     * expression that denotes t.  May be called before the expression
     * is typechecked.
     */
    public static Expr.T constValue(Expr.T t) {
        Expr.T new_;
        M3.CheckState cs;
        if ((t == null)) {
            return null;
        }
        //*** NOT necessarily: <* ASSERT t.checked *> ***
        new_ = t.evaluate();
        if ((new_ != t)) {
            cs = M3.OuterCheckState.copy(); // OK since constants don't raise exceptions
            typeCheck(new_, cs);
        }
        return new_;
    }

    /** returns upper and lower bounds for the value of the expression. */
    public static void getBounds(Expr.T t, int[] min, int[] max) {
        if ((t == null)) {
            TInt.assign(min, TInt.Zero);
            TInt.assign(max, TInt.MOne);
            return;
        }
        AssertError.check(t.checked, "Expr");
        t.getBounds(min, max);
    }

    /** TRUE iff t is a designator */
    public static boolean isDesignator(Expr.T t) {
        if ((t == null)) {
            return true;
        }
        AssertError.check(t.checked, "Expr");
        return t.isDesignator();
    }

    /** TRUE iff t is a writable designator */
    public static boolean isWritable(Expr.T t, boolean traced) {
        // Port: the parameter is named lhs in the module.
        if ((t == null)) {
            return true;
        }
        AssertError.check(t.checked, "Expr");
        return t.isWritable(traced);
    }

    /**
     * PRE: t is checked.
     * TRUE if t's binary representation is all zeroes
     */
    public static boolean isZeroes(Expr.T t) {
        // PRE: t is checked.
        if ((t == null)) {
            return true;
        }
        AssertError.check(t.checked, "Expr");
        return t.isZeroes();
    }

    /** returns the best guess of t's sign */
    public static M3CG.Sign getSign(Expr.T t) {
        int[] min = TInt.newInt(), max = TInt.newInt();
        getBounds(t, min, max);
        if (TInt.LE(TInt.Zero, min)) {
            return M3CG.Sign.Positive;
        } else if (TInt.LE(max, TInt.Zero)) {
            return M3CG.Sign.Negative;
        } else {
            return M3CG.Sign.Unknown;
        }
    }

    //******************************************************************

    /** marks t as needing a memory address */
    public static void needsAddress(Expr.T t) {
        if ((t == null)) {
            return;
        }
        AssertError.check(t.checked, "Expr");
        t.need_addr();
    }

    //******************************************************************

    /** returns "TRUE" if "t" supports direct assignments. */
    public static boolean supportsDirectAssignment(Expr.T t) {
        Expr.T baseExpr;
        if (t == null) {
            return false;
        }
        if (ConsExpr.is(t)) {
            baseExpr = ConsExpr.base(t);
        } else {
            baseExpr = t;
        }

        return baseExpr != null && baseExpr.directAssignableType;
    }

    /**
     * If called, must be before Prep(t).
     * mark "t" so that when compiled it will assume the code generator
     * stack contains the address of a LHS that is to be assigned-to, and
     * it will perform the assignment.
     */
    public static void markForDirectAssignment(Expr.T t) {
        // If called, must be before Prep(t).
        AssertError.check(t.directAssignableType, "Expr");
        t.doDirectAssign = true;
    }

    /** returns "TRUE" if "t" is marked for direct assignment */
    public static boolean isMarkedForDirectAssignment(Expr.T t) {
        return (t != null) && (t.doDirectAssign);
    }

    /** t is a non-named array, record, or set constructor. */
    public static boolean isAnonConstructor(Expr.T t) {
        // t is a non-named array, record, or set constructor.
        Expr.T locExpr;
        // Let's avoid creating yet another dispatching method with 83
        // potential override sites to be checked by some poor maintainer.
        locExpr = t;
        if (locExpr == null) {
            return false;
        }
        if (NamedExpr.is(locExpr)) {
            return false;
        }
        if (ConsExpr.is(locExpr)) {
            return true;
        }
        if (ArrayExpr.is(locExpr)) {
            return true;
        }
        if (RecordExpr.is(locExpr)) {
            return true;
        }
        if (SetExpr.is(locExpr)) {
            return true;
        }
        return false;
    }

    private static String idToNonNilText(int Id) {
        String Result;
        Result = M3ID.toText(M3.ids, Id);
        if (Result == null) {
            Result = "NIL";
        }
        return Result;
    }

    /**
     * If Constr is an anonymous constructor expression, change it to a named
     * expression, already resolved to a constant value.  This so it can be
     * referenced from a different compilation unit.  Give it a concocted,
     * non-Modula3 name, for IR information only.
     *
     * <p>Port: Constr is VAR (IN OUT), passed as a one-element array.
     */
    public static void nameAnonConstr(Expr.T[] Constr, int unitId, int constId, M3.CheckState cs) {
        // To use the value of an anonymous constructor from a different unit,
        // we need a Constant.T ahead of the constructor expression to
        // provide mechanism for that, and a NamedExpr.T ahead of the
        // Constant.T to turn it back into an expression.  This will look
        // like a named constant with a non-Modula3 name.

        String name;
        Constant.T namedConst;
        Expr.T newExpr;
        if (ArrayExpr.isAnon(Constr[0])) {
            AssertError.check(Constr[0].checked, "Expr");
            name = "%Anon%" + idToNonNilText(unitId) + "%" + idToNonNilText(constId);
            namedConst = Constant.declareGlobal(name, Constr[0]);
            newExpr = NamedExpr.fromValue(namedConst);
            typeCheck(newExpr, cs);
            Constr[0] = newExpr;
        }
    }

    //***************************************** Alignments ************

    /**
     * A bit alignment that t is guaranteed to have.  Hopefully maximum, or
     * nearly so.  Always a true alignment, possibly as small as 1 bit.
     * Expression alignments are more precise than type alignments in that they
     * can take into account properties of an expression that the expression's
     * type does not necessarily have in general.  Particularly, if a value is
     * a field or element, they can depend on its containing record, object,
     * or array.  For an open array expression, this is the alignment of the
     * elements, not the dope.
     * Compare to Type.T.info.alignment.
     */
    public static long alignment(Expr.T t) {
        if (t == null) {
            return Target.Word8.align;
        }
        if (t.align == Target.UnknownAlign) {
            // Uninitialized. Compute and cache it.
            t.align = t.exprAlign();
        }
        return t.align;
    }

    // Port: the multi-use overrides for exprAlign, declared in ExprRep, are
    // in ExprRep.

    //******************************************************************

    //*** phase 4 ****

    // Expressions are compiled in two steps:
    //   Prep: emit any code that includes branches or stores
    //   Compile: emit the rest of the code

    public static void prep(Expr.T t) {
        if ((t == null)) {
            return;
        }
        AssertError.check(t.checked, "Expr");
        t.prep();
    }

    /**
     * Emit code to evaluate the expression onto the top of stack. For some
     * types, this could be an address (arrays, records, bit sets).
     * StaticOnly means construct static data but do not evaluate.
     */
    public static void compile(Expr.T t, boolean StaticOnly) {
        if ((t == null)) {
            return;
        }
        AssertError.check(CG.isInsideProc() || StaticOnly, "Expr");
        AssertError.check(t.checked, "Expr");
        t.compile(StaticOnly);
    }

    public static void compile(Expr.T t) {
        compile(t, false);
    }

    public static void prepLValue(Expr.T t, boolean traced) {
        if ((t == null)) {
            return;
        }
        Type.compile(t.type);
        AssertError.check(t.checked, "Expr");
        t.prepLV(traced);
    }

    /**
     * Emit code to evaluate 't's L-value into s0.A. 't' must be a designator.
     * StaticOnly means construct static data but do not evaluate.
     */
    public static void compileLValue(Expr.T t, boolean traced, boolean StaticOnly) {
        if ((t == null)) {
            return;
        }
        AssertError.check(CG.isInsideProc() || StaticOnly, "Expr");
        AssertError.check(t.checked, "Expr");
        t.compileLV(traced, StaticOnly);
    }

    public static void compileLValue(Expr.T t, boolean traced) {
        compileLValue(t, traced, false);
    }

    /**
     * emits code to evaluate 't's byte address onto the top of stack.
     * Equivalent to CompileLValue, followed by a runtime check that it
     * is byte aligned.  Use PrepLValue to prep these expressions.
     * StaticOnly means construct static data but do not evaluate.
     */
    public static void compileAddress(Expr.T t, boolean traced, boolean StaticOnly) {
        if ((t == null)) {
            return;
        }
        AssertError.check(CG.isInsideProc() || StaticOnly, "Expr");
        AssertError.check(t.checked, "Expr");
        t.compileLV(traced, StaticOnly);
        CG.check_byte_aligned();
    }

    public static void compileAddress(Expr.T t, boolean traced) {
        compileAddress(t, traced, false);
    }

    public static void prepBranch(Expr.T t, long true_, long false_, int freq) {
        if ((t == null)) {
            return;
        }
        AssertError.check(t.checked, "Expr");
        AssertError.check((true_ == CG.No_label) || (false_ == CG.No_label), "Expr");
        Type.compile(t.type);
        t.prepBR(true_, false_, freq);
    }

    /**
     * emits code to evaluate the expression and conditionally branch to 'true'
     * or 'false' depending on its boolean value.  'freq' is the estimated
     * frequency that the specified branch will be taken.
     */
    public static void compileBranch(Expr.T t, long true_, long false_, int freq) {
        if ((t == null)) {
            return;
        }
        AssertError.check(t.checked, "Expr");
        AssertError.check((true_ == CG.No_label) || (false_ == CG.No_label), "Expr");
        t.compileBR(true_, false_, freq);
    }

    /** generates any tracing implied by a write to 't' */
    public static void noteWrite(Expr.T t) {
        if ((t == null)) {
            return;
        }
        t.note_write();
    }

    /**
     * TRUE iff (value(a) = value(b)), assuming a constant global store
     * and the type equalities represented by 'x'.
     */
    public static boolean isEqual(Expr.T a, Expr.T b, Type.Assumption x) {
        if ((a == b)) {
            return true;
        }
        if ((a == null) || (b == null)) {
            return false;
        }
        return a.isEqual(b, x);
    }

    /** prepares constant values for GenLiteral */
    public static void prepLiteral(Expr.T t, Type.T type, boolean is_const) {
        if ((t == null)) {
            return;
        }
        AssertError.check(t.checked, "Expr");
        Type.compile(t.type);
        t.prepLiteral(type, is_const);
    }

    /**
     * initializes the global storage at ADR(x)+offset in the global data segment
     * or constant pool to the constant value t.  For any expression t, PrepLiteral(t)
     * must be called before GenLiteral (t).
     */
    public static void genLiteral(Expr.T t, long offset, Type.T type, boolean is_const) {
        if ((t == null)) {
            return;
        }
        AssertError.check(t.checked, "Expr");
        Type.compile(t.type);
        t.genLiteral(offset, type, is_const);
    }

    /** add the string denoting the literal value of 't' to 'mbuf' */
    public static void genFPLiteral(Expr.T t, M3Buf.T mbuf) {
        Expr.T u = constValue(t);
        if ((u == null)) {
            Error.msg("INTERNAL ERROR: fingerprint of a non-constant expression");
        }
        // Sic: if u is NIL, the ASSERT dereferences NIL (a checked runtime
        // error); here it is a NullPointerException.
        AssertError.check(u.checked, "Expr");
        u.genFPLiteral(mbuf);
    }

    /**
     * generate an "illegal operands" error message if neither 'a' nor 'b'
     * is the contagious error type and return the error type
     */
    public static Type.T badOperands(String op, Type.T a, Type.T b) {
        if ((a != ErrType.T) && (b != ErrType.T)) {
            Error.msg("illegal operand(s) for " + op);
        }
        return ErrType.T;
    }

    public static Type.T badOperands(String op, Type.T a) {
        return badOperands(op, a, null);
    }

    public static Type.T badOperands(String op) {
        return badOperands(op, null, null);
    }

    // Port: the default methods are in ExprRep.

    /** Look through a NamedExpr and then a ConsExpr, for an Expr.T.  NIL if not. */
    public static Expr.T stripNamedCons(Expr.T expr) {
        // Look through a NamedExpr and then a ConsExpr, for an Expr.T.  NIL if not.

        int[] ident = new int[1];
        Value.T[] val = new Value.T[1];
        Expr.T unnamedExpr, resultExpr;
        if (NamedExpr.split(expr, ident, val)) {
            if (Value.classOf(val[0]) != Value.Class.Expr) {
                return null;
            }
            unnamedExpr = Value.toExpr(val[0]);
        } else {
            unnamedExpr = expr;
        }
        ConsExpr.seal(unnamedExpr);
        // DO NOT allow ConsExpr to Check unnamedExpr.  That could make a (should be
        // top-level) call to ArrayExpr.Check on a nested array constructor.
        // But it's OK if ConsExpr previously checked unnamedExpr.
        resultExpr = ConsExpr.base(unnamedExpr);
        if (resultExpr == null) {
            resultExpr = unnamedExpr;
        }
        return resultExpr;
    }

    /**
     * &lt; 0, if nonstatic.  Can be static, even if repType is open array.
     * Does not include dope.
     */
    public static long staticSize(Expr.T expr) {
        // < 0, if nonstatic.  Can be static, even if open array repType.
        // Does not include dope.
        Expr.T stripped;
        Type.Info info = new Type.Info();
        stripped = stripNamedCons(expr);
        if (stripped != null && OpenArrayType.is(repTypeOf(stripped))) {
            // It's an array constructor.
            return ArrayExpr.staticSize(stripped);
        }
        Type.checkInfo(repTypeOf(expr), info);
        return info.size;
    }

    /**
     * Generate runtime actions prior to a use of t that does not call Compile.
     * Return TRUE IFF following code is reachable.
     */
    public static boolean checkUseFailure(Expr.T t) {
        Expr.T strippedExpr;
        strippedExpr = stripNamedCons(t);
        if (strippedExpr == null) {
            strippedExpr = t;
            return true;
            // FIXME ^Remove this RETURN and fix so checkUseFailure works on
            //       fields, formals, variables.  Currently (2020-4-24), it
            //       fails because Value.toExpr has no overrides for these.
        }
        AssertError.check(strippedExpr.checked, "Expr");
        return strippedExpr.checkUseFailure();
    }
}
