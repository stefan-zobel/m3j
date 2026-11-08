// Java port of CM3's m3-sys/m3front/src/exprs/EqualExpr.i3 and EqualExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: EqualExpr.i3
// Last Modified On Thu Aug  3 16:25:34 1989 By kalsow

// File: EqualExpr.m3
// Last modified on Thu Jun 29 14:03:25 PDT 1995 by kalsow
//      modified on Thu Nov 29 03:33:19 1990 by muller

public final class EqualExpr {
    private EqualExpr() {}

    /** max # of iterations in an unrolled loop */
    private static final long Max_unroll = 4;

    public enum Kind { SimpleScalar, SimpleStruct, Complex }

    // Port: Op = [ CG.Cmp.EQ .. CG.Cmp.NE ] is M3CG.CompareOp, restricted
    // to EQ and NE.

    /** Port: indexed by {@code op.ordinal() - M3CG.CompareOp.EQ.ordinal()}. */
    private static final String[] OpName = { "'='", "'#'" };

    public static final class P extends ExprRep.Tabc {
        public M3CG.CompareOp op;
        public Kind kind;
        public CG.Val tmp;

        @Override
        public void check(M3.CheckState cs) {
            EqualExpr.check(this, cs);
        }

        @Override
        public void prep() {
            EqualExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            EqualExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepBR(long true_, long false_, int freq) {
            EqualExpr.prepBR(this, true_, false_, freq);
        }

        @Override
        public void compileBR(long true_, long false_, int freq) {
            ExprRep.noBranch(this, true_, false_, freq);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public long exprAlign() {
            return ExprRep.exprBoolAlign(this);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType, need_addr
        // := ExprRep.NotAddressable, prepLV := ExprRep.NotLValue, compileLV
        // := ExprRep.NotLValueBool, getBounds := ExprRep.NoBounds,
        // isWritable, isDesignator, isZeroes := ExprRep.IsNever,
        // genFPLiteral := ExprRep.NoFPLiteral, prepLiteral :=
        // ExprRep.NoPrepLiteral, genLiteral := ExprRep.NoLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
    }

    private static String opName(M3CG.CompareOp op) {
        return OpName[op.ordinal() - M3CG.CompareOp.EQ.ordinal()];
    }

    /** Port: op is in [ CG.Cmp.EQ .. CG.Cmp.NE ]. */
    public static Expr.T new_(Expr.T a, Expr.T b, M3CG.CompareOp op) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.a = a;
        p.b = b;
        p.op = op;
        p.type = Bool.T;
        p.repType = Bool.T;
        p.kind = Kind.SimpleScalar;
        p.tmp = null;
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T ta, tb;
        M3String.T[] str = new M3String.T[1];
        M3WString.T[] wstr = new M3WString.T[1];
        Expr.typeCheck(p.a, cs);
        Expr.typeCheck(p.b, cs);
        ta = Type.base(Expr.typeOf(p.a));
        tb = Type.base(Expr.typeOf(p.b));
        if ((ta == null) || (tb == null)
            || !(Type.isAssignable(ta, tb) || Type.isAssignable(tb, ta))) {
            p.type = Expr.badOperands(opName(p.op), ta, tb);
        }
        p.kind = classify(ta, tb);
        if ((p.kind == Kind.SimpleScalar) && ((ta == Textt.T) || (tb == Textt.T))) {
            if (TextExpr.split8(p.a, str) || TextExpr.split32(p.a, wstr)
                || TextExpr.split8(p.b, str) || TextExpr.split32(p.b, wstr)) {
                Error.warn(1, "comparing pointers, use Text.Equal to compare strings");
            }
        }
    }

    private static Kind classify(Type.T ta, Type.T tb) {
        Type.Info ia = new Type.Info(), ib = new Type.Info();
        if ((ta == Int.T) || (ta == Reel.T) || (ta == LReel.T) || (ta == EReel.T)) {
            return Kind.SimpleScalar;
        }

        Type.checkInfo(ta, ia);

        switch (ia.class_) {
            case Error,
                 Integer,
                 Longint,
                 Real,
                 Longreal,
                 Extended,
                 Enum,
                 Object,
                 Opaque,
                 Ref,
                 Subrange -> {
                return Kind.SimpleScalar;
            }
            case Set -> {
                if ((ia.size <= M3.target.Integer.size)) {
                    return Kind.SimpleScalar;
                }
            }
            default -> {
                // skip
            }
        }

        Type.checkInfo(tb, ib);

        if (simpleStructType(ia) && simpleStructType(ib)) {
            return Kind.SimpleStruct;
        }

        return Kind.Complex;
    }

    private static boolean simpleStructType(Type.Info info) {
        return (info.isSolid)
            && (info.class_ != Type.Class.Procedure)
            && (info.class_ != Type.Class.OpenArray)
            && (info.size >= 0)
            && (Math.floorMod(info.size, info.alignment) == 0)
            && (Math.floorDiv(info.size, info.alignment) <= Max_unroll)
            && (findCompareType(info.size, info.alignment) != CGType.Void);
    }

    private static final CGType[] Z = { CGType.Word64, CGType.Word32,
                                        CGType.Word16, CGType.Word8 };

    private static CGType findCompareType(long size, long align) {
        CGType t;
        for (int i = 0; i <= Z.length - 1; i++) {
            t = Z[i];
            if ((align == M3.targetMap.CG_Align[t.ordinal()])
                && (Math.floorMod(size, M3.targetMap.CG_Size[t.ordinal()]) == 0)
                && (M3.target.Word.size >= M3.targetMap.CG_Size[t.ordinal()])) {
                return t;
            }
        }
        return CGType.Void;
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e instanceof P b) {
            return (a.op == b.op)
                && Expr.isEqual(a.a, b.a, x)
                && Expr.isEqual(a.b, b.b, x);
        } else {
            return false;
        }
    }

    private static void prep(P p) {
        long false_;
        Expr.prep(p.a);
        Expr.prep(p.b);
        if ((p.kind == Kind.Complex)) {
            // use the branching code to compute a value
            false_ = CG.next_label(2);
            prepBR(p, CG.No_label, false_, CG.Maybe);
            Value.load(Bool.True);
            p.tmp = CG.pop_temp();
            CG.jump(false_ + 1);
            CG.set_label(false_);
            Value.load(Bool.False);
            CG.store_temp(p.tmp);
            CG.set_label(false_ + 1);
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        Type.T t = p.a.type;
        AssertError.check(!StaticOnly, "EqualExpr");
        if ((p.kind == Kind.SimpleScalar)) {
            Expr.compile(p.a);
            Expr.compile(p.b);
            CG.compare(Type.cgType(t), p.op);
        } else if ((p.kind == Kind.SimpleStruct)) {
            compileSolidUnrolled(p);
        } else {
            // used the branching code to compute a value
            CG.push(p.tmp);
            CG.free(p.tmp);
            p.tmp = null;
        }
    }

    private static void compileSolidUnrolled(P p) {
        Type.Info info = new Type.Info();
        CG.Val xa, xb;
        CGType cmp_type;
        long chunk_size;
        long n_chunks;
        Expr.compile(p.a);
        xa = CG.pop();
        Expr.compile(p.b);
        xb = CG.pop();
        Type.checkInfo(p.a.type, info);

        cmp_type = findCompareType(info.size, info.alignment);
        AssertError.check(cmp_type != CGType.Void, "EqualExpr");
        chunk_size = M3.targetMap.CG_Size[cmp_type.ordinal()];
        n_chunks = Math.floorDiv(info.size, chunk_size);

        for (long i = 0; i <= n_chunks - 1; i++) {
            CG.push(xa);
            CG.load_indirect(cmp_type, i * chunk_size, chunk_size, info.alignment);
            CG.push(xb);
            CG.load_indirect(cmp_type, i * chunk_size, chunk_size, info.alignment);
            CG.compare(M3.target.Word.cg_type, p.op);
            if ((i > 0)) {
                if ((p.op == M3CG.CompareOp.EQ)) {
                    CG.and(M3.target.Word.cg_type);
                } else {
                    CG.or(M3.target.Word.cg_type);
                }
            }
        }

        CG.free(xa);
        CG.free(xb);
    }

    private static void prepBR(P p, long true_, long false_, int freq) {
        Type.T ta = Type.base(p.a.type);
        Type.T tb = Type.base(p.b.type);

        long skip;
        CG.Val xa, xb;
        Type.Info info = new Type.Info();
        Expr.prep(p.a);
        Expr.prep(p.b);
        ta = Type.checkInfo(ta, info);
        if ((p.kind == Kind.SimpleScalar)) {
            Expr.compile(p.a);
            Expr.compile(p.b);
            CG.if_then(info.stk_type, p.op, true_, false_, freq);
            return;

        //************ better to generate "If_eq" than  "eq; if_true"
        //ELSIF (p.kind = Kind.SimpleStruct) THEN
        //  CompileSolidUnrolled (p);
        //  IF (true # CG.No_label)
        //    THEN CG.If_true (true, freq);
        //    ELSE CG.If_false (false, freq);
        //  END;
        //  RETURN;
        //***************************************************************

        } else if ((info.class_ == Type.Class.Set)) {
            Expr.compile(p.a);
            Expr.compile(p.b);
            CG.set_compare(info.size, p.op);
            if ((true_ == CG.No_label)) {
                CG.if_false(false_, freq);
            } else {
                CG.if_true(true_, freq);
            }
            return;

        } else if ((info.class_ == Type.Class.Procedure) || ProcType.is(tb)) {
            if ((p.op == M3CG.CompareOp.EQ)) {
                compileProcs(p, true_, false_, freq);
            } else {
                compileProcs(p, false_, true_, freq);
            }

        } else if ((info.class_ == Type.Class.Record)
                   || (info.class_ == Type.Class.Array)
                   || (info.class_ == Type.Class.OpenArray)) {
            Expr.compile(p.a);
            xa = CG.pop();
            Expr.compile(p.b);
            xb = CG.pop();
            if ((p.op == M3CG.CompareOp.NE)) { // swap true and false labels
                skip = true_;
                true_ = false_;
                false_ = skip;
            }
            if ((false_ == CG.No_label)) {
                skip = CG.next_label();
                compileTest(xa, xb, ta, tb, skip, CG.Always - freq);
                CG.jump(true_);
                CG.set_label(skip);
            } else {
                compileTest(xa, xb, ta, tb, false_, freq);
            }
            CG.free(xa);
            CG.free(xb);

        } else {
            // typechecking removed the other cases.
            Expr.badOperands(opName(p.op), ta, tb);
        }
    }

    private static void compileProcs(P p, long true_, long false_, int freq) {
        Value.T[] procA = new Value.T[1], procB = new Value.T[1];
        long classA, classB;
        CG.Val t1, t2;
        long skip, no_closure, nope;
        // first we classify the two arguments:
        //   class 0: NIL, global proc, or non-formal variable => no frame pointer
        //   class 1: nested proc => fixed frame pointer
        //   class 2: formal variable => may be closure => may have frame
        //
        // Note: procedures pointers are always aligned!
        classA = 0;
        if (UserProc.isProcedureLiteral(p.a, procA)
            && Procedure.isNested((Procedure.T) procA[0])) {
            classA = 1;
        } else if (canHaveFrame(p.a)) {
            classA = 2;
        }
        classB = 0;
        if (UserProc.isProcedureLiteral(p.b, procB)
            && Procedure.isNested((Procedure.T) procB[0])) {
            classB = 1;
        } else if (canHaveFrame(p.b)) {
            classB = 2;
        }

        // normalize the pair so that classA <= classB
        if ((classB < classA)) {
            {
                long tmp = classA;
                classA = classB;
                classB = tmp;
            }
            {
                Expr.T tmp = p.a;
                p.a = p.b;
                p.b = tmp;
            }
            {
                Value.T tmp = procA[0];
                procA[0] = procB[0];
                procB[0] = tmp;
            }
        }

        // finally, we generate the tests, based on the classes
        switch ((int) (classA * 3 + classB)) {

            case 0,   // 0, 0
                 4 -> { // 1, 1
                Expr.compile(p.a);
                Expr.compile(p.b);
                CG.if_then(CGType.Addr, M3CG.CompareOp.EQ, true_, false_, freq);
            }

            case 1 -> { // 0, 1 => never equal
                // constant FALSE
                if ((true_ == CG.No_label)) {
                    CG.jump(false_);
                } else {
                    // fall through
                }
            }

            case 2 -> { // 0, 2
                skip = CG.next_label();
                nope = skip;
                if ((true_ == CG.No_label)) {
                    nope = false_;
                }
                Expr.compile(p.b);
                t1 = CG.pop();
                CG.if_closure(t1, nope, CG.No_label, CG.Always - freq);
                Expr.compile(p.a);
                CG.push(t1);
                CG.if_then(CGType.Addr, M3CG.CompareOp.EQ, true_, false_, freq);
                CG.set_label(skip);
                CG.free(t1);
            }

            case 5 -> { // 1, 2
                skip = CG.next_label();
                nope = skip;
                if ((true_ == CG.No_label)) {
                    nope = false_;
                }
                Expr.compile(p.b);
                t1 = CG.pop();
                CG.if_closure(t1, CG.No_label, nope, freq);
                Expr.compile(p.a);
                CG.push(t1);
                CG.closure_proc();
                CG.if_compare(CGType.Addr, M3CG.CompareOp.NE, nope, freq);
                Procedure.loadStaticLink((Procedure.T) procA[0]);
                CG.push(t1);
                CG.closure_frame();
                CG.if_then(CGType.Addr, M3CG.CompareOp.EQ, true_, false_, freq);
                CG.set_label(skip);
                CG.free(t1);
            }

            case 8 -> { // 2, 2
                no_closure = CG.next_label(2);
                skip = no_closure + 1;
                nope = skip;
                if ((true_ == CG.No_label)) {
                    nope = false_;
                }

                Expr.compile(p.a);
                t1 = CG.pop();
                Expr.compile(p.b);
                t2 = CG.pop();

                CG.if_closure(t1, CG.No_label, no_closure, CG.Maybe);
                // A is a closure...

                CG.if_closure(t2, nope, CG.No_label, CG.Always - freq);

                // both A and B are closures
                CG.push(t1);
                CG.closure_proc();
                CG.push(t2);
                CG.closure_proc();
                CG.if_compare(CGType.Addr, M3CG.CompareOp.NE, nope, CG.Always - freq);
                CG.push(t1);
                CG.closure_frame();
                CG.push(t2);
                CG.closure_frame();
                CG.if_compare(CGType.Addr, M3CG.CompareOp.NE, nope, CG.Always - freq);

                // A is not a closure
                CG.set_label(no_closure);
                CG.if_closure(t2, nope, CG.No_label, CG.Always - freq);

                // neither A nor B is a closure
                CG.push(t1);
                CG.push(t2);
                CG.if_then(CGType.Addr, M3CG.CompareOp.EQ, true_, false_, freq);
                CG.set_label(skip);
                CG.free(t1);
                CG.free(t2);
            }

            default -> throw new AssertError("EqualExpr");
        }
    }

    private static boolean canHaveFrame(Expr.T e) {
        int[] name = new int[1];
        Value.T[] obj = new Value.T[1];
        if (!(NamedExpr.split(e, name, obj) || QualifyExpr.split(e, obj))) {
            // non-constant, non-variable => no frame
            return false;
        } else if ((Value.classOf(obj[0]) == Value.Class.Procedure)) {
            // constant: no frame
            return false;
        } else if ((Value.classOf(obj[0]) == Value.Class.Var)
                   && Variable.hasClosure((Variable.T) Value.base(obj[0]))) {
            return true;
        } else { // non-formal, non-const => frame = NIL
            return false;
        }
    }

    private static void compileTest(CG.Val x1, CG.Val x2,
                                    Type.T t1, Type.T t2,
                                    long false_,
                                    int freq) {
        Type.Info u1_info = new Type.Info(), u2_info = new Type.Info();
        Type.T u1 = Type.base(t1); // strip the BITS FOR
        Type.T u2 = Type.base(t2);
        Type.checkInfo(u1, u1_info);
        Type.checkInfo(u2, u2_info);
        if ((u1_info.class_ == Type.Class.Record)) {
            compileRecord(x1, x2, u1, false_, freq);

        } else if ((u1_info.class_ == Type.Class.Array)
                   || (u1_info.class_ == Type.Class.OpenArray)) {
            compileArray(x1, x2, u1, u2, false_, freq);

        } else if ((u1_info.class_ == Type.Class.Set)) {
            CG.push(x1);
            if ((u1_info.size <= M3.target.Integer.size)) {
                CG.load_indirect(M3.target.Word.cg_type, 0, M3.target.Integer.size,
                                 u1_info.alignment);
            }
            CG.push(x2);
            if ((u1_info.size <= M3.target.Integer.size)) {
                CG.load_indirect(M3.target.Word.cg_type, 0, M3.target.Integer.size,
                                 u2_info.alignment);
            }
            CG.set_compare(u1_info.size, M3CG.CompareOp.EQ);
            CG.if_false(false_, freq);

        } else if ((u1_info.class_ == Type.Class.Procedure)
                   || (u2_info.class_ == Type.Class.Procedure)) {
            // we're already inside some variable => no frame pointers
            CG.push(x1);
            CG.load_indirect(CGType.Addr, 0, M3.target.Address.size, CG.procAlign());
            CG.push(x2);
            CG.load_indirect(CGType.Addr, 0, M3.target.Address.size, CG.procAlign());
            CG.if_compare(CGType.Addr, M3CG.CompareOp.NE, false_, freq);

        } else { // simple scalars
            Type.checkInfo(t1, u1_info); // can't ignore BITS FOR
            Type.checkInfo(t2, u2_info);
            CG.push(x1);
            CG.boost_addr_alignment(M3.target.Address.align);
            CG.load_indirect(u1_info.stk_type, 0, u1_info.size, u1_info.alignment);
            CG.push(x2);
            CG.boost_addr_alignment(M3.target.Address.align);
            CG.load_indirect(u2_info.stk_type, 0, u2_info.size, u2_info.alignment);
            CG.if_compare(u1_info.stk_type, M3CG.CompareOp.NE, false_, freq);
        }
    }

    private static void compileArray(CG.Val p1, CG.Val p2,
                                     Type.T t1, Type.T t2,
                                     long false_,
                                     int freq) {
        Type.T[] i1 = new Type.T[1], i2 = new Type.T[1], e1 = new Type.T[1],
                 e2 = new Type.T[1];
        if (compileSolid(p1, p2, t1, t2, false_, freq)) {
            return;
        }
        ArrayType.split(t1, i1, e1);
        ArrayType.split(t2, i2, e2);
        genShapeCheck(p1, p2, i1[0], e1[0], i2[0], e2[0], false_, freq);
        genValueCheck(t1, i1[0], e1[0], p1, t2, i2[0], e2[0], p2, false_, freq);
    }

    private static void genShapeCheck(CG.Val p1, CG.Val p2,
                                      Type.T i1_, Type.T e1_,
                                      Type.T i2_, Type.T e2_,
                                      long false_,
                                      int freq) {
        // Port: the parameters i1, e1, i2 and e2 are written; they are
        // the elements 0 of these arrays.
        Type.T[] i1 = { i1_ }, e1 = { e1_ }, i2 = { i2_ }, e2 = { e2_ };
        long n = 0;
        while (true) {
            if ((i1[0] != null) && (i2[0] != null)) {
                return;
            }

            if ((i1[0] == null)) {
                CG.push(p1);
                CG.open_size(n);
            } else {
                CG.load_integer(M3.target.Integer.cg_type, Type.number(i1[0]));
            }
            if ((i2[0] == null)) {
                CG.push(p2);
                CG.open_size(n);
            } else {
                CG.load_integer(M3.target.Integer.cg_type, Type.number(i2[0]));
            }
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.NE, false_, freq);

            if (!ArrayType.split(e1[0], i1, e1)) {
                return;
            }
            if (!ArrayType.split(e2[0], i2, e2)) {
                return;
            }
            n = n + 1;
        }
    }

    private static void genValueCheck(Type.T t1, Type.T i1, Type.T e1, CG.Val p1,
                                      Type.T t2, Type.T i2, Type.T e2, CG.Val p2,
                                      long false_,
                                      int freq) {
        long d1 = OpenArrayType.openDepth(t1);
        long d2 = OpenArrayType.openDepth(t2);
        CG.Val x;
        if ((d1 > 0) && (d2 > 0)) {
            if ((d1 <= d2)) {
                genOpenValueCheck(t1, p1, p2, false_, freq);
            } else {
                genOpenValueCheck(t2, p1, p2, false_, freq);
            }
        } else if ((d1 > 0)) {
            CG.push(p1);
            CG.open_elt_ptr(OpenArrayType.eltAlign(t1));
            x = CG.pop();
            genFixedValueCheck(t2, i2, e2, x, p2, false_, freq);
            CG.free(x);
        } else if ((d2 > 0)) {
            CG.push(p2);
            CG.open_elt_ptr(OpenArrayType.eltAlign(t2));
            x = CG.pop();
            genFixedValueCheck(t1, i1, e1, p1, x, false_, freq);
            CG.free(x);
        } else { // d1 = 0 AND d2 = 0
            genFixedValueCheck(t1, i1, e1, p1, p2, false_, freq);
        }
    }

    private static void genOpenValueCheck(Type.T t1, CG.Val p1, CG.Val p2,
                                          long false_, int freq) {
        long d1 = OpenArrayType.openDepth(t1);
        Type.T elt = OpenArrayType.nonopenEltType(t1);
        CG.Val cnt;
        long elt_align;
        long elt_pack;
        long top;
        CG.Val o1, o2;
        Type.Info info = new Type.Info();
        elt = Type.checkInfo(elt, info);
        elt_align = info.alignment;
        elt_pack = Math.floorDiv(info.size + elt_align - 1, elt_align) * elt_align;

        // compute the total number of elements that need to be compared
        for (long i = 0; i <= d1 - 1; i++) {
            CG.push(p1);
            CG.open_size(i);
            if ((i != 0)) {
                CG.multiply(M3.target.Integer.cg_type);
            }
        }
        CG.load_intt(1);
        CG.subtract(M3.target.Integer.cg_type);
        cnt = CG.pop_temp();

        top = CG.next_label(2);
        CG.jump(top + 1); // test for empty arrays

        CG.set_label(top);

        // compute the address of the elements
        CG.push(p1);
        CG.open_elt_ptr(elt_align);
        CG.push(cnt);
        CG.index_bytes(elt_pack);
        o1 = CG.pop();

        CG.push(p2);
        CG.open_elt_ptr(elt_align);
        CG.push(cnt);
        CG.index_bytes(elt_pack);
        o2 = CG.pop();

        compileTest(o1, o2, elt, elt, false_, freq);

        // free the element pointers
        CG.free(o1);
        CG.free(o2);

        // decrement the count
        CG.push(cnt);
        CG.load_integer(M3.target.Integer.cg_type, TInt.One);
        CG.subtract(M3.target.Integer.cg_type);
        CG.store_temp(cnt);

        // test for completion
        CG.set_label(top + 1);
        CG.push(cnt);
        CG.load_integer(M3.target.Integer.cg_type, TInt.Zero);
        CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.GE, top, CG.Likely);

        CG.free(cnt);
    }

    private static void genFixedValueCheck(Type.T t1, Type.T i1, Type.T e1,
                                           CG.Val p1, CG.Val p2,
                                           long false_, int freq) {
        CG.Val cnt;
        long[] n_elts = new long[1];
        boolean b;
        long top;
        CG.Val o1, o2;
        // compute the total number of elements that need to be compared
        b = TInt.toInt(Type.number(i1), n_elts);
        AssertError.check(b, "EqualExpr");
        if ((n_elts[0] <= 0)) {
            return;
        }
        CG.load_intt(n_elts[0] - 1);
        cnt = CG.pop_temp();

        top = CG.next_label(2);
        CG.set_label(top);

        // compute the address of the elements
        CG.push(p1);
        CG.push(cnt);
        ArrayType.genIndex(t1);
        o1 = CG.pop();

        CG.push(p2);
        CG.push(cnt);
        ArrayType.genIndex(t1);
        o2 = CG.pop();

        compileTest(o1, o2, e1, e1, false_, freq);

        // free the element pointers
        CG.free(o1);
        CG.free(o2);

        // decrement the count
        CG.push(cnt);
        CG.load_integer(M3.target.Integer.cg_type, TInt.One);
        CG.subtract(M3.target.Integer.cg_type);
        CG.store_temp(cnt);

        // test for completion
        CG.set_label(top + 1);
        CG.push(cnt);
        CG.load_integer(M3.target.Integer.cg_type, TInt.Zero);
        CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.GE, top, CG.Likely);

        CG.free(cnt);
    }

    private static void compileRecord(CG.Val p1, CG.Val p2, Type.T t,
                                      long false_, int freq) {
        Value.T[] v = new Value.T[1];
        Field.Info field = new Field.Info();
        CG.Val o1, o2;
        if (compileSolid(p1, p2, t, t, false_, freq)) {
            return;
        }
        RecordType.split(t, v);
        while ((v[0] != null)) {
            Field.split(v[0], field);
            CG.push(p1);
            CG.add_offset(field.offset);
            o1 = CG.pop();
            CG.push(p2);
            CG.add_offset(field.offset);
            o2 = CG.pop();
            compileTest(o1, o2, field.type, field.type, false_, freq);
            CG.free(o1);
            CG.free(o2);
            v[0] = v[0].next;
        }
    }

    private static boolean compileSolid(CG.Val p1, CG.Val p2, Type.T t1, Type.T t2,
                                        long false_, int freq) {
        Type.Info info1 = new Type.Info(), info2 = new Type.Info();
        CGType cmp_type;
        long chunk_align;
        long chunk_size;
        long n_chunks;
        CG.Val cnt;
        long top;
        Type.checkInfo(t1, info1);
        Type.checkInfo(t2, info2);
        if ((!info1.isSolid) || (!info2.isSolid)) {
            return false;
        }
        if ((info1.size < 0) || (info1.size != info2.size)) {
            return false;
        }

        chunk_align = Math.min(info1.alignment, info2.alignment);
        cmp_type = findCompareType(info1.size, chunk_align);
        if ((cmp_type == CGType.Void)) {
            return false;
        }
        chunk_size = M3.targetMap.CG_Size[cmp_type.ordinal()];
        n_chunks = Math.floorDiv(info1.size, chunk_size);

        if ((n_chunks <= Max_unroll)) {

            // unroll the loop of comparisons
            for (long i = 0; i <= n_chunks - 1; i++) {
                CG.push(p1);
                CG.load_indirect(cmp_type, i * chunk_size, chunk_size, chunk_align);
                CG.push(p2);
                CG.load_indirect(cmp_type, i * chunk_size, chunk_size, chunk_align);
                CG.if_compare(M3.target.Word.cg_type, M3CG.CompareOp.NE, false_, freq);
            }

        } else {
            // generate a loop of comparisons
            CG.load_intt(n_chunks - 1);
            cnt = CG.pop_temp();

            top = CG.next_label(2);
            CG.set_label(top);

            // compute the address of the elements and load them
            CG.push(p1);
            CG.push(cnt);
            CG.index_bytes(chunk_size);
            CG.load_indirect(cmp_type, 0, chunk_size, chunk_align);

            CG.push(p2);
            CG.push(cnt);
            CG.index_bytes(chunk_size);
            CG.load_indirect(cmp_type, 0, chunk_size, chunk_align);

            // do the comparison
            CG.if_compare(M3.target.Word.cg_type, M3CG.CompareOp.NE, false_, freq);

            // decrement the count
            CG.push(cnt);
            CG.load_integer(M3.target.Integer.cg_type, TInt.One);
            CG.subtract(M3.target.Integer.cg_type);
            CG.store_temp(cnt);

            // test for completion
            CG.set_label(top + 1);
            CG.push(cnt);
            CG.load_integer(M3.target.Integer.cg_type, TInt.Zero);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.GE, top, CG.Likely);

            CG.free(cnt);
        }

        return true;
    }

    //*************************
    // PROCEDURE MinFieldAlignment (f: Scope.T): INTEGER =
    //     (* compute the minimum field alignment that's needed *)
    //   VAR
    //     n, min  : INTEGER;
    //     fields  : Scope.ValueList;
    //     index   : INTEGER;
    //     offset  : INTEGER;
    //     type    : Type.T;
    //   BEGIN
    //     Scope.ToList (f, fields, n);
    //     min := MAX (Target.Address.align, Target.Integer.align);
    //     FOR i := 0 TO n-1 DO
    //       Field.Split (fields[i], index, offset, type);
    //       min := GCD (min, offset);
    //     END;
    //     RETURN min;
    //   END MinFieldAlignment;
    //
    // PROCEDURE GCD (x, y: INTEGER): INTEGER =
    //   BEGIN
    //     IF (x <= 0) OR (y <= 0) THEN RETURN 1 END;
    //     LOOP
    //       IF    (x = 0) THEN RETURN y;
    //       ELSIF (y = 0) THEN RETURN x;
    //       ELSIF (x < y) THEN y := y MOD x;
    //       ELSIF (y < x) THEN x := x MOD y;
    //       ELSE  RETURN x;
    //       END;
    //     END;
    //   END GCD;
    //*******************************

    private static Expr.T fold(P p) {
        Expr.T e1, e2;
        long[] s = new long[1];
        e1 = Expr.constValue(p.a);
        if ((e1 == null)) {
            return null;
        }
        e2 = Expr.constValue(p.b);
        if ((e2 == null)) {
            return null;
        }
        if (IntegerExpr.compare(e1, e2, s)
            || EnumExpr.compare(e1, e2, s)
            || ReelExpr.compare(e1, e2, s)
            || AddressExpr.compare(e1, e2, s)
            || SetExpr.compare(e1, e2, s)
            || ProcExpr.compare(e1, e2, s)) {
            return Bool.Map[((p.op == M3CG.CompareOp.EQ) == (s[0] == 0)) ? 1 : 0];
        }
        return null;
    }
}
