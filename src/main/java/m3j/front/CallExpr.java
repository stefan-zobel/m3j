// Java port of CM3's m3-sys/m3front/src/exprs/CallExpr.i3 and CallExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.util.AssertError;

// File: CallExpr.i3
// Last Modified On Tue Jun 20 15:17:26 PDT 1995 By kalsow
//      Modified On Thu Jun 15 10:59:14 PDT 1995 By ericv
//      Modified On Fri Aug  3 02:44:12 1990 By muller

// File: CallExpr.m3
// Last modified on Tue Jun 20 15:09:15 PDT 1995 by kalsow
//      modified on Thu Jun 15 11:11:20 PDT 1995 by ericv
//      modified on Wed Nov  7 01:30:54 1990 by muller

/**
 * NOTE: Notwithstanding its name, this interface handles only
 *       calls on builtin procedures.
 */
public final class CallExpr {
    private CallExpr() {}

    // NOTE: Notwithstanding its name, this module handles only
    //       calls on builtin procedures.

    // ******************************************************************

    /**
     * Port: T_ and the full revelation of T (branded "CallExpr.P") are the
     * class T.
     */
    public static class T extends Expr.T {
        public Expr.T proc;
        public Expr.T[] args;
        /** for use by the Prep methods */
        public CG.Val tmp;
        /** for use by the Prep methods */
        public long align;
        public boolean hasError = false;
        public MethodList methods;
        public Type.T proc_type;

        @Override
        public Type.T typeOf() {
            return CallExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return CallExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            CallExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            CallExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            CallExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepLV(boolean traced) {
            CallExpr.prepLV(this, traced);
        }

        @Override
        public void compileLV(boolean traced, boolean StaticOnly) {
            CallExpr.compileLV(this, traced, StaticOnly);
        }

        @Override
        public void prepBR(long true_, long false_, int freq) {
            CallExpr.prepBR(this, true_, false_, freq);
        }

        @Override
        public void compileBR(long true_, long false_, int freq) {
            CallExpr.compileBR(this, true_, false_, freq);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        @Override
        public void getBounds(int[] min, int[] max) {
            CallExpr.getBounds(this, min, max);
        }

        @Override
        public boolean isWritable(boolean lhs) {
            return CallExpr.isWritable(this, lhs);
        }

        @Override
        public boolean isDesignator(boolean lhs) {
            return CallExpr.isDesignator(this, lhs);
        }

        @Override
        public void note_write() {
            noteWrites(this);
        }

        @Override
        public long exprAlign() {
            return callExprAlign(this);
        }

        @Override
        public boolean usesAssignProtocol() {
            return CallExpr.usesAssignProtocol(this);
        }

        // Port: the overrides isEqual := ExprRep.NeverEq, isZeroes :=
        // ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral, prepLiteral
        // := ExprRep.NoPrepLiteral and genLiteral := ExprRep.NoLiteral
        // restate the inherited methods.
    }

    /** Port: the revelation of MethodList (branded "CallExpr.MethodList"). */
    public static class MethodList {
        public long minArgs;
        public long maxArgs;
        public boolean functional;
        public boolean keywords;
        public boolean strict;
        public Type.T fixedType;
        public Typer typeOf;
        public Typer repTypeOf;
        public Visitor need_addr;
        public TypeChecker checker;
        public Compiler prep;
        public Compiler compiler;
        public CompilerLV prepLV;
        public CompilerLV compilerLV;
        public CompilerBR prepBR;
        public CompilerBR compilerBR;
        public Evaluator evaluator;
        public Bounder bounder;
        public Predicate isWritable;
        public Predicate isDesignator;
        public NoteWriter noteWriter;
        public Predicate isIndirect;
        public BuiltinAlign builtinAlign;
    }

    @FunctionalInterface
    public interface Typer { Type.T apply(T t); }

    @FunctionalInterface
    public interface Visitor { void apply(T t); }

    @FunctionalInterface
    public interface TypeChecker { void apply(T t, M3.CheckState cs); }

    @FunctionalInterface
    public interface Evaluator { Expr.T apply(T t); }

    @FunctionalInterface
    public interface Bounder { void apply(T t, int[] min, int[] max); }

    /** Port: the default of traced is FALSE. */
    @FunctionalInterface
    public interface Predicate { boolean apply(T t, boolean traced); }

    @FunctionalInterface
    public interface Compiler { void apply(T t); }

    @FunctionalInterface
    public interface CompilerLV { void apply(T t, boolean traced); }

    @FunctionalInterface
    public interface CompilerBR { void apply(T t, long true_, long false_, int freq); }

    @FunctionalInterface
    public interface NoteWriter { void apply(T t); }

    @FunctionalInterface
    public interface BuiltinAlign { long apply(T t); }

    public static Expr.T new_(Expr.T proc, Expr.T[] args) {
        T p = new T();
        ExprRep.init(p);
        p.proc = proc;
        p.args = args;
        p.tmp = null;
        p.methods = null;
        p.proc_type = null;
        p.directAssignableType = true;
        return p;
    }

    public static boolean is(Expr.T e) {
        if (e instanceof T) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isUserProc(Expr.T e) {
        if (e instanceof T t) {
            resolve(t);
            return (t.methods == UserProc.Methods);
        } else {
            return false;
        }
    }

    public static MethodList newMethodList(long minArgs, long maxArgs, boolean functional, boolean keywords, boolean strict, Type.T fixedType, CallExpr.Typer typeOf, CallExpr.Typer repTypeOf, CallExpr.Visitor need_addr, CallExpr.TypeChecker checker, CallExpr.Compiler prep, CallExpr.Compiler compiler, CallExpr.CompilerLV prepLV, CallExpr.CompilerLV compilerLV, CallExpr.CompilerBR prepBR, CallExpr.CompilerBR compilerBR, CallExpr.Evaluator evaluator, CallExpr.Bounder bounder, CallExpr.Predicate isWritable, CallExpr.Predicate isDesignator, CallExpr.NoteWriter noteWriter, CallExpr.BuiltinAlign builtinAlign) {
        // usesAssignProtocol : Predicate;
        MethodList m;
        m = new MethodList();
        m.minArgs = minArgs;
        m.maxArgs = maxArgs;
        m.functional = functional;
        m.keywords = keywords;
        m.strict = strict;
        m.fixedType = fixedType;
        m.typeOf = typeOf;
        m.repTypeOf = repTypeOf;
        m.need_addr = need_addr;
        m.checker = checker;
        m.prep = prep;
        m.compiler = compiler;
        m.prepLV = prepLV;
        m.compilerLV = compilerLV;
        m.prepBR = prepBR;
        m.compilerBR = compilerBR;
        m.evaluator = evaluator;
        m.bounder = bounder;
        m.isWritable = isWritable;
        m.isDesignator = isDesignator;
        m.noteWriter = noteWriter;
        m.builtinAlign = builtinAlign;
        return m;
    }

    public static MethodList newMethodList(long minArgs, long maxArgs, boolean functional, boolean keywords, boolean strict, Type.T fixedType, CallExpr.Typer typeOf, CallExpr.Typer repTypeOf, CallExpr.Visitor need_addr, CallExpr.TypeChecker checker, CallExpr.Compiler prep, CallExpr.Compiler compiler, CallExpr.CompilerLV prepLV, CallExpr.CompilerLV compilerLV, CallExpr.CompilerBR prepBR, CallExpr.CompilerBR compilerBR, CallExpr.Evaluator evaluator, CallExpr.Bounder bounder, CallExpr.Predicate isWritable, CallExpr.Predicate isDesignator, CallExpr.NoteWriter noteWriter) {
        return newMethodList(minArgs, maxArgs, functional, keywords, strict, fixedType, typeOf, repTypeOf, need_addr, checker, prep, compiler, prepLV, compilerLV, prepBR, compilerBR, evaluator, bounder, isWritable, isDesignator, noteWriter, CallExpr::builtinAlignDefault);
    }

    public static boolean isNever(CallExpr.T t, boolean lhs) {
        return false;
    }

    public static boolean isAlways(CallExpr.T t) {
        return true;
    }

    public static Expr.T noValue(CallExpr.T t) {
        return null;
    }

    public static void noBounds(CallExpr.T t, int[] min, int[] max) {
        ExprRep.noBounds(t, min, max);
    }

    public static void notAddressable(CallExpr.T t) {
        Error.msg("Internal compiler error CallExpr.NotAddressable");
        throw new AssertError("CallExpr");
    }

    public static void prepArgs(CallExpr.T t) {
        for (int i = 0; i <= t.args.length - 1; i++) {
            Expr.prep(t.args[i]);
        }
    }

    public static void noLValue(CallExpr.T t, boolean traced) {
        Error.msg("Internal compiler error CallExpr.NoLValue");
        throw new AssertError("CallExpr");
    }

    public static void notBoolean(CallExpr.T t, long true_, long false_, int freq) {
        Error.msg("Internal compiler error CallExpr.NotBoolean");
        throw new AssertError("CallExpr");
    }

    public static void prepNoBranch(CallExpr.T t, long true_, long false_, int freq) {
        t.prep();
        t.compile(/* StaticOnly := */ false);
        if ((true_ != CG.No_label)) {
            CG.if_true(true_, freq);
        } else {
            CG.if_false(false_, freq);
        }
    }

    public static void noBranch(CallExpr.T t, long true_, long false_, int freq) {
        // all the work was done by prep
    }

    public static void notWritable(CallExpr.T t) {
        // skip
    }

    // **********************************************************************

    private static void resolve(T p) {
        Type.T t;
        if ((p.methods != null)) {
            return;
        }
        t = Expr.typeOf(p.proc);
        if ((t == null)) {
            t = QualifyExpr.methodType(p.proc);
            // we need this hack because "TypeOf(obj.method)" returns NIL
            // so that you can't use it as a vanilla procedure value.
        }
        p.methods = ProcType.methods(t);
        p.proc_type = t;
    }

    private static void computeTypes(T p) {
        resolve(p);
        if (p.methods == null) {
            p.type = ErrType.T;
            p.repType = ErrType.T;
        } else if ((p.methods.fixedType != null) || (p.methods.typeOf == null)) {
            p.type = p.methods.fixedType;
            p.repType = p.type;
        } else {
            fixArgs(p);
            p.type = p.methods.typeOf.apply(p);
            p.repType = p.methods.repTypeOf.apply(p);
        }
    }

    private static Type.T typeOf(T p) {
        computeTypes(p);
        return p.type;
    }

    private static Type.T repTypeOf(T p) {
        computeTypes(p);
        return p.repType;
    }

    private static void check(T p, M3.CheckState cs) {
        long[] nErrs0 = new long[1], nErrs1 = new long[1], nWarns = new long[1];
        Expr.T arg;
        boolean keywords;
        // check the procedure
        Error.count(nErrs0, nWarns);
        Expr.typeCheck(p.proc, cs);
        resolve(p);
        Error.count(nErrs1, nWarns);
        if ((p.methods == null)) {
            if ((nErrs0[0] == nErrs1[0]) && (Expr.typeOf(p.proc) != ErrType.T)) {
                Error.msg("attempting to call a non-procedure" + procName(p));
            }
            p.type = ErrType.T;
        }

        // check its args
        keywords = (p.methods == null) || (p.methods.keywords);
        for (int i = 0; i <= p.args.length - 1; i++) {
            arg = p.args[i];
            Expr.typeCheck(arg, cs);
            if ((Expr.typeOf(arg) == ErrType.T)) {
                p.type = ErrType.T;
            } else if ((!keywords) && KeywordExpr.is(arg)) {
                Error.msg("keyword parameters not allowed on builtin operations"
                          + procName(p));
            }
        }

        // finally, do the procedure specific checking
        if ((p.type != ErrType.T) && (p.methods != null)) {
            fixArgs(p);
            p.methods.checker.apply(p, cs);
        }

        // check the exceptions
        ESet.noteExceptions(cs, ProcType.raises(p.proc_type));
    }

    private static void fixArgs(T p) {
        Expr.T[] z;
        if ((p.args.length < p.methods.minArgs)) {
            Error.msg("too few arguments" + procName(p));
            z = new Expr.T[(int) p.methods.minArgs];
            for (int i = 0; i <= p.args.length - 1; i++) {
                z[i] = p.args[i];
            }
            p.args = z;
        } else if ((p.args.length > p.methods.maxArgs)) {
            Error.msg("too many arguments" + procName(p));
            z = new Expr.T[(int) p.methods.maxArgs];
            for (int i = 0; i <= p.methods.maxArgs - 1; i++) {
                z[i] = p.args[i];
            }
            p.args = z;
        }
    }

    private static String procName(T p) {
        Value.T[] v = new Value.T[1];
        if ((p.proc != null) && UserProc.isProcedureLiteral(p.proc, v)) {
            return ": " + Value.globalName(v[0]);
        } else {
            return "";
        }
    }

    private static void needsAddress(T p) {
        if ((p.methods != null)) {
            p.methods.need_addr.apply(p);
        }
    }

    private static long callExprAlign(T p) {
        Type.T resultType;
        Type.Info typeInfo = new Type.Info();
        if (p.methods == null) { // User proc, has non-nil proc_type.
            resultType = ProcType.result(p.proc_type);
            resultType = Type.stripPacked(resultType);
            Type.checkInfo(resultType, typeInfo);
            return typeInfo.alignment;
        } else {
            return p.methods.builtinAlign.apply(p);
        }
    }

    public static long builtinAlignDefault(CallExpr.T p) {
        Type.T resultType;
        Type.Info resultInfo = new Type.Info();
        resultType = Type.checkInfo(typeOf(p), resultInfo);
        return resultInfo.alignment;
    }

    private static boolean usesAssignProtocol(T p) {
        return false;
    }

    private static void prep(T p) {
        p.methods.prep.apply(p);
    }

    private static void compile(T p, boolean StaticOnly) {
        p.methods.compiler.apply(p);
    }

    private static void prepLV(T p, boolean traced) {
        p.methods.prepLV.apply(p, traced);
    }

    private static void compileLV(T p, boolean traced, boolean StaticOnly) {
        p.methods.compilerLV.apply(p, traced);
    }

    private static void prepBR(T p, long true_, long false_, int freq) {
        p.methods.prepBR.apply(p, true_, false_, freq);
    }

    private static void compileBR(T p, long true_, long false_, int freq) {
        p.methods.compilerBR.apply(p, true_, false_, freq);
    }

    private static void noteWrites(T p) {
        if (p.methods != null) {
            p.methods.noteWriter.apply(p);
        }
    }

    private static Expr.T fold(T p) {
        resolve(p);
        if (p.type == ErrType.T) {
            return null;
        }
        if ((p.methods == null)) {
            return null;
        }
        return p.methods.evaluator.apply(p);
    }

    private static void getBounds(T p, int[] min, int[] max) {
        Expr.T e = fold(p);
        if ((e != null) && (e != p)) {
            Expr.getBounds(e, min, max);
        } else if (p.type == ErrType.T || p.methods == null) {
            ExprRep.noBounds(p, min, max);
        } else {
            p.methods.bounder.apply(p, min, max);
        }
    }

    private static boolean isDesignator(T p, boolean lhs) {
        resolve(p);
        if (p.methods == null) {
            return false;
        }
        return p.methods.isDesignator.apply(p, /* traced := */ false);
    }

    private static boolean isWritable(T p, boolean lhs) {
        resolve(p);
        if (p.methods == null) {
            return false;
        }
        return p.methods.isWritable.apply(p, lhs);
    }
}
