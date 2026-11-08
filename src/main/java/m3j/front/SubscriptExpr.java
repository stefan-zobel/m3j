// Java port of CM3's m3-sys/m3front/src/exprs/SubscriptExpr.i3 and SubscriptExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.util.AssertError;

// File: SubscriptExpr.i3
// Last Modified On Mon Jul 31 13:22:38 1989 By kalsow

// File: SubscriptExpr.m3
// Last modified on Thu Nov 10 12:06:15 PST 1994 by kalsow
//      modified on Thu Mar  7 01:44:07 1991 by muller

public final class SubscriptExpr {
    private SubscriptExpr() {}

    public static final class P extends ExprRep.Tab {
        /** Subscript minus lowerBound. */
        public Expr.T biased_b;
        /** Open depth of lhs's type. */
        public long lhsOpenDepth;
        public CG.Val tmp;
        /** Dope of outermost array, if it's open. */
        public CG.Val OMDopeVal;
        /** Open depth of outermost array. */
        public long OMOpenDepth = 0;
        public Type.T taBase;
        /** p.a if it's a P; NIL otherwise. */
        public P leftSs;
        /**
         * 1-origin, R to L depth of this subscript in an
         * unbroken sequence of subscripts.
         */
        public long ssDepth;
        /**
         * L to R shape subscript corresponding to
         * this array subscript, in OMDopeVal.
         */
        public long shapeSs;
        public boolean checkedPass1;

        @Override
        public Type.T typeOf() {
            return SubscriptExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return SubscriptExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            SubscriptExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            SubscriptExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            SubscriptExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepLV(boolean traced) {
            SubscriptExpr.prepLV(this, traced);
        }

        @Override
        public void compileLV(boolean traced, boolean StaticOnly) {
            SubscriptExpr.compileLV(this, traced, StaticOnly);
        }

        @Override
        public void prepBR(long true_, long false_, int freq) {
            ExprRep.prepNoBranch(this, true_, false_, freq);
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
        public boolean isWritable(boolean lhs) {
            return SubscriptExpr.isWritable(this, lhs);
        }

        @Override
        public boolean isDesignator(boolean lhs) {
            return SubscriptExpr.isDesignator(this, lhs);
        }

        @Override
        public void note_write() {
            noteWrites(this);
        }

        @Override
        public long exprAlign() {
            return subscriptExprAlign(this);
        }

        // Port: the overrides isEqual := ExprRep.EqCheckAB, getBounds :=
        // ExprRep.NoBounds, isZeroes := ExprRep.IsNever, genFPLiteral :=
        // ExprRep.NoFPLiteral, prepLiteral := ExprRep.NoPrepLiteral and
        // genLiteral := ExprRep.NoLiteral restate the inherited methods.
    }

    // EXPORTED:
    public static Expr.T new_(Expr.T a, Expr.T b) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.a = a;
        p.b = b;
        p.leftSs = null;
        p.ssDepth = 0;
        p.shapeSs = 0;
        p.biased_b = null;
        p.lhsOpenDepth = 0;
        p.tmp = null;
        p.checkedPass1 = false;
        p.checked = false;
        return p;
    }

    // Externally dispatched-to:
    private static Type.T typeOf(P p) {
        Type.T ta;
        Type.T[] ti = new Type.T[1], te = new Type.T[1];
        ta = Type.base(Expr.typeOf(p.a));

        if (RefType.is(ta)) {
            // auto-magic dereference
            p.a = DerefExpr.new_(p.a);
            p.a.origin = p.origin;
            ta = Type.base(Expr.typeOf(p.a));
        }

        if (ArrayType.split(ta, ti, te)) {
            return te[0];
        } else {
            return ta;
        }
    }

    // Externally dispatched-to:
    private static Type.T repTypeOf(P p) {
        Type.T ta;
        Type.T[] ti = new Type.T[1], te = new Type.T[1];
        ta = Type.base(Expr.repTypeOf(p.a));

        if (RefType.is(ta)) {
            // auto-magic dereference
            p.a = DerefExpr.new_(p.a);
            p.a.origin = p.origin;
            ta = Type.base(Expr.repTypeOf(p.a));
        }

        if (ArrayType.split(ta, ti, te)) {
            return te[0];
        } else {
            return ta;
        }
    }

    private static void genRangeCheck(P p, int[] mini, int[] maxi, int[] minb,
                                      int[] maxb, M3.CheckState cs) {
        int[] z = TInt.newInt();
        boolean b;
        if (TInt.LT(minb, mini) && TInt.LT(maxi, maxb)) {
            b = TInt.subtract(maxi, mini, z);
            AssertError.check(b, "SubscriptExpr");
            p.biased_b = CheckExpr.new_(p.biased_b, TInt.Zero, z,
                                        M3CG.RuntimeError.SubscriptOutOfRange);
            p.biased_b.origin = p.origin;
            Expr.typeCheck(p.biased_b, cs);
        } else if (TInt.LT(minb, mini)) {
            if (TInt.LT(maxb, mini)) {
                Error.warn(2, "Will raise runtime error if executed: "
                           + "subscript is below array index type.");
            }
            p.biased_b = CheckExpr.newLower(p.biased_b, TInt.Zero,
                                            M3CG.RuntimeError.SubscriptOutOfRange);
            p.biased_b.origin = p.origin;
            Expr.typeCheck(p.biased_b, cs);
        } else if (TInt.LT(maxi, maxb)) {
            if (TInt.LT(maxi, minb)) {
                Error.warn(2, "Will raise runtime error if executed: "
                           + "subscript is above array index type.");
            }
            b = TInt.subtract(maxi, mini, z);
            AssertError.check(b, "SubscriptExpr");
            p.biased_b = CheckExpr.newUpper(p.biased_b, z,
                                            M3CG.RuntimeError.SubscriptOutOfRange);
            p.biased_b.origin = p.origin;
            Expr.typeCheck(p.biased_b, cs);
        }
    }

    // Externally dispatched-to:
    /**
     * Called only with the rightmost (topmost) of an uninterrupted sequence
     * of subscripts.  An implicit dereference counts as an interruption.
     */
    private static void check(P p, M3.CheckState cs) {
        directCheckPass1(p, cs);
        directCheckPass2(p, cs, /* ssDepth := */ 1);
    }

    /**
     * For this SubscriptExpr, do just enough to ascertain whether an implied
     * dereference of its left operand is needed.  Fully check deeper LHS
     * subexpression nodes that are not Ps.
     * POST: p.leftSs and p.type are computed.
     * POST: p.checkedPass1.
     */
    private static void directCheckPass1(P p, M3.CheckState cs) {
        Type.T ta;
        Type.T[] ti = new Type.T[1];
        Type.T[] te = new Type.T[1];
        if (p == null) {
            return;
        }
        if (p.checkedPass1) {
            return;
        }
        if (p.a == null) {
            p.type = ErrType.T;
            p.checkedPass1 = true;
            return;
        } else if (p.a instanceof P pa) { // Left operand is another subscript expression node.
            directCheckPass1(pa, cs);
            ta = Type.check(Type.base(Expr.typeOf(p.a)));
            if (RefType.is(ta)) { // Insert an implicit dereference.
                // But first finish subscript node pa, as top-level.
                directCheckPass2(pa, cs, /* ssDepth := */ 1);
                p.leftSs = null;
                p.a = DerefExpr.new_(pa);
                p.a.origin = p.origin;
                Expr.typeCheck(p.a, cs);
                ta = Type.check(Type.base(Expr.typeOf(p.a)));
            } else {
                p.leftSs = pa;
            }
        } else {
            Expr.typeCheck(p.a, cs);
            ta = Type.check(Type.base(Expr.typeOf(p.a)));
            if (RefType.is(ta)) { // Insert an implicit dereference.
                p.a = DerefExpr.new_(p.a);
                p.a.origin = p.origin;
                Expr.typeCheck(p.a, cs);
                ta = Type.check(Type.base(Expr.typeOf(p.a)));
            }
        }
        // Port: VAR p.type is te[0], copied back after the call.
        te[0] = p.type;
        boolean ok = ArrayType.split(ta, ti, te);
        p.type = te[0];
        if (!ok) {
            Error.msg("Subscripted expression must be an array or reference thereto.");
            p.type = ErrType.T;
            ta = ErrType.T;
        }
        p.taBase = ta;
        p.checkedPass1 = true;
    }

    /**
     * We need the ssDepth parameter for recursing between immediately
     * successive subscript nodes.
     */
    private static void directCheckPass2(P p, M3.CheckState cs, long ssDepth) {
        Type.T[] ti = new Type.T[1], te = new Type.T[1];
        Type.T tb;
        int[] mini = TInt.newInt(), maxi = TInt.newInt(), minb = TInt.newInt(),
              maxb = TInt.newInt();
        boolean b;
        if (p == null) {
            return;
        }
        if (p.checked) {
            return;
        }
        p.ssDepth = ssDepth;
        if (p.leftSs != null) {
            directCheckPass2(p.leftSs, cs, ssDepth + 1);
        }

        if (p.taBase == null || p.taBase == ErrType.T) {
            p.checked = true;
            return;
        }

        p.lhsOpenDepth = OpenArrayType.openDepth(p.taBase);
        if (p.leftSs == null) {
            p.shapeSs = 0;
        } else {
            p.shapeSs = p.leftSs.shapeSs + 1;
        }

        if (p.ssDepth == 1 && p.lhsOpenDepth > 1
            && OpenArrayType.eltsAreBitAddressed(p.type)) {
            Error.msg("CM3 restriction: Open array of non-byte-aligned elements cannot "
                      + "be partially subscripted (2.2.5).");
            p.type = ErrType.T;
        }

        Expr.needsAddress(p.a);
        b = ArrayType.split(p.taBase, ti, te);
        AssertError.check(b, "SubscriptExpr");

        Type.getBounds(ti[0], mini, maxi);
        Expr.typeCheck(p.b, cs);
        tb = Expr.typeOf(p.b);

        Expr.getBounds(p.b, minb, maxb);

        p.biased_b = p.b;
        if ((ti[0] == null)) { // p.a has open array type.
            if (!Type.isSubtype(tb, Int.T)) {
                Error.msg("open arrays must be indexed by INTEGER expressions");
                tb = Int.T;
            }
        } else if (Type.isSubtype(tb, Type.base(ti[0]))) {
            // the index value's type has a common base type with the index type
            if (!TInt.EQ(mini, TInt.Zero)) {
                if (Type.isSubtype(tb, LInt.T)) {
                    ti[0] = LInt.T;
                } else {
                    ti[0] = Int.T;
                }
                p.biased_b =
                    SubtractExpr.new_(p.b, IntegerExpr.new_(ti[0], mini), true);
                p.biased_b.origin = p.origin;
                Expr.typeCheck(p.biased_b, cs);
            }
            genRangeCheck(p, mini, maxi, minb, maxb, cs);

        } else {
            Error.msg("Subscript not assignable to array's index type.");
        }
        p.checked = true;
    }

    // Externally dispatched-to:
    private static void needsAddress(P p) {
        Expr.needsAddress(p.a);
    }

    private static long subscriptExprAlign(P p) {
        long arrayAlign, eltPack;
        Type.T ta;
        Type.T[] ti = new Type.T[1], te = new Type.T[1], targetType = new Type.T[1];
        int[] minb = TInt.newInt(), maxb = TInt.newInt();
        arrayAlign = Expr.alignment(p.a);
        ta = Type.base(Expr.typeOf(p.a));
        if (RefType.split(ta, /*OUT*/ targetType)) { // Implicit dereference.
            ta = Type.base(targetType[0]);
        }
        eltPack = ArrayType.eltPack(ta);
        if (ArrayType.split(ta, ti, te)) {
            if (ti[0] != null) { // Fixed array.
                Expr.getBounds(p.b, minb, maxb);
                if (TInt.LT(maxb, minb)) { // no element accessible.
                    return arrayAlign;
                }
                if (TInt.EQ(maxb, minb)) { // one element accessible.
                    // Not as in CM3: the element is aligned as the array
                    // only if it is the first; else at its offset (CM3
                    // took a packed element at a constant index as aligned
                    // to the array, and its code generator failed).
                    int[] mini = TInt.newInt(), maxi = TInt.newInt(), d = TInt.newInt();
                    long[] n = new long[1];
                    if (Type.getBounds(ti[0], mini, maxi)
                        && TInt.subtract(minb, mini, d)
                        && TInt.toInt(d, n)
                        && 0 <= n[0] && n[0] <= Long.MAX_VALUE / Math.max(eltPack, 1)) {
                        return CG.GCD(arrayAlign, n[0] * eltPack);
                    }
                    return CG.GCD(arrayAlign, eltPack);
                }
            }
            return CG.GCD(arrayAlign, eltPack);
        }
        return Expr.alignment(p.a);
    }

    // Externally dispatched-to:
    private static void prep(P p) {
        Type.Info info = new Type.Info();
        prepLV(p, /* traced := */ false);
        Type.checkInfo(p.type, info);
        if (Host.doIncGC && info.isTraced) {
            switch (info.class_) {
                case Object, Opaque, Ref -> {
                    compile(p, /* StaticOnly := */ false);
                    RunTyme.emitCheckLoadTracedRef();
                    p.tmp = CG.pop();
                }
                default -> {
                    // no check
                }
            }
        }
    }

    // Externally dispatched-to:
    private static void prepLV(P p, boolean traced) {
        Expr.T e = Expr.constValue(p.biased_b);
        if ((e != null)) {
            p.biased_b = e;
        }
        Expr.checkUseFailure(p.a);
        if (Expr.isDesignator(p.a)) {
            Expr.prepLValue(p.a, traced);
        } else {
            Expr.prep(p.a);
        }
        Expr.prep(p.biased_b);
    }

    // Externally dispatched-to:
    private static void compile(P p, boolean StaticOnly) {
        AssertError.check(!StaticOnly, "SubscriptExpr");
        if (p.tmp == null) {
            compileLV(p, /* traced := */ false, /* StaticOnly := */ StaticOnly);
            Type.loadScalar(p.type); // Loads only if not a struct.
        } else {
            CG.push(p.tmp);
            CG.free(p.tmp);
            p.tmp = null;
        }
    }

    /** It's static. Port: VAR subscript is {@code subscript[0]}. */
    private static boolean staticSs(P p, long[] subscript) {
        Expr.T e;
        boolean ssIsStatic = false;
        Type.T[] t = new Type.T[1];
        int[] subs = TInt.newInt();
        e = Expr.constValue(p.biased_b);
        if (e == null) {
            return false;
        } else {
            ssIsStatic =
                IntegerExpr.split(e, subs, t) || EnumExpr.split(e, subs, t);
            ssIsStatic = ssIsStatic && TInt.toInt(subs, subscript);
            return ssIsStatic;
        }
    }

    // Externally dispatched-to:
    /** Port: the default of traced is FALSE. */
    private static void compileLV(P p, boolean traced, boolean StaticOnly) {
        Type.T[] ti = new Type.T[1], te = new Type.T[1];
        long[] subscript = new long[1];
        long newShapeLast;
        Type.T ta = Type.base(Expr.typeOf(p.a));
        Type.T tb = Type.base(Expr.typeOf(p.b));
        long elt_pack = ArrayType.eltPack(ta);
        M3CG.Var newDopeVar;
        boolean b = ArrayType.split(ta, ti, te);
        AssertError.check(!StaticOnly, "SubscriptExpr");
        AssertError.check(b, "SubscriptExpr");

        if (Expr.isDesignator(p.a)) {
            Expr.compileLValue(p.a, traced);
        } else {
            Expr.compile(p.a);
        }

        if ((p.lhsOpenDepth == 0)) {
            // p.a has fixed array type.
            // Top of CG stack is address of the elements.
            // Replace it by address of subscripted element.

            if (staticSs(p, subscript)) {
                if (subscript[0] != 0) {
                    CG.add_offset(subscript[0] * elt_pack /* bits. */);
                }
            } else {
                Expr.compile(p.biased_b);
                if (Type.isSubtype(tb, LInt.T)) {
                    CG.loophole(M3.target.Longint.cg_type, M3.target.Integer.cg_type);
                }
                ArrayType.genIndex(ta);
                CG.boost_addr_alignment(ArrayType.eltAlign(ta));
            }

        } else { // p.a has open array type.
            if (p.leftSs == null) {
                // This is the leftmost subscript, to the outermost array.  Top of CG stack
                // is address of the dope.  Save the dope in p.OMDopeVal and replace it
                // on the CG stack by the elements' addr.
                p.OMDopeVal = CG.pop();
                CG.push(p.OMDopeVal);
                CG.open_elt_ptr(ArrayType.eltAlign(ta));
                p.OMOpenDepth = p.lhsOpenDepth;
            } else { // Copy OMDopeVal and OMOpenDepth up from the subscript to the left.
                p.OMDopeVal = p.leftSs.OMDopeVal;
                // Top of CG stack is already the address of the elements.
                p.OMOpenDepth = p.leftSs.OMOpenDepth;
            }

            if (p.ssDepth == 1 && p.lhsOpenDepth > 1) {

                // This is the RM subscript, but p.a has additional open dimensions after
                // this one. The final result will be an open array, so must build new
                // dope and leave its address on the CG stack.  Check will have prevented
                // non-open elements from being non-byte-aligned.

                // allocate a new dope vector
                newDopeVar = OpenArrayType.declareDopeTemp(te[0]);
                newShapeLast = p.OMOpenDepth - p.shapeSs - 2;

                // copy the suffix of the shape portion of the dope vector
                for (long i = 0; i <= newShapeLast; i++) {
                    CG.push(p.OMDopeVal);
                    CG.open_size(i + p.shapeSs + 1);
                    CG.store_int(M3.target.Integer.cg_type, newDopeVar,
                                 M3.rt.OA_sizes + i * M3.target.Integer.pack);
                }

                // build the new data pointer

                Expr.compile(p.biased_b);
                if (Host.doRangeChk) { // range check the subscript
                    CG.push(p.OMDopeVal);
                    CG.open_size(p.shapeSs);
                    CG.check_index(M3CG.RuntimeError.SubscriptOutOfRange);
                }

                for (long i = 0; i <= newShapeLast; i++) {
                    CG.load_int(M3.target.Integer.cg_type,
                                newDopeVar, M3.rt.OA_sizes + i * M3.target.Integer.pack);
                    CG.multiply(M3.target.Word.cg_type);
                }
                // This is where we cannot handle sub-byte elements.  newDopeVar can only
                // hold a byte-aligned address or better, and Index_bytes requires a
                // bit size that is a multiple of byte size.
                CG.index_bytes(elt_pack);
                CG.store_addr(newDopeVar, M3.rt.OA_elt_ptr);
                CG.load_addr_of_temp(newDopeVar, 0, M3.target.Address.align);
            } else {
                // Either there is another subscript to the right, or the element
                // is not an array.  Either way, leave the address of the element
                // on top of the CG stack.  It will be a CG.Val, and can represent
                // a bit-level component.

                Expr.compile(p.biased_b);
                if (Host.doRangeChk) { // range check the subscript
                    CG.push(p.OMDopeVal);
                    CG.open_size(p.shapeSs);
                    CG.check_index(M3CG.RuntimeError.SubscriptOutOfRange);
                }
                for (long I = p.shapeSs + 1; I <= p.OMOpenDepth - 1; I++) {
                    CG.push(p.OMDopeVal);
                    CG.open_size(I);
                    CG.multiply(M3.target.Word.cg_type);
                }
                // CHECK: Can we just use some CG op directly here? See CG.Index_bits.
                ArrayType.genIndex(ta);
                CG.boost_addr_alignment(OpenArrayType.eltAlign(ta));
            }
        }
    }

    // Externally dispatched-to:
    private static boolean isDesignator(P p, boolean lhs) {
        return Expr.isDesignator(p.a);
    }

    // Externally dispatched-to:
    private static boolean isWritable(P p, boolean lhs) {
        return Expr.isWritable(p.a, lhs);
    }

    // Externally dispatched-to:
    private static Expr.T fold(P p) {
        Expr.T e1, e2;
        Expr.T[] e3 = new Expr.T[1];
        e1 = Expr.constValue(p.a);
        e2 = Expr.constValue(p.b);
        e3[0] = null;
        if (e1 == null || e2 == null) {
            return null;
        } else if (ArrayExpr.constSubscript(e1, e2, e3)) {
            return e3[0];
        } else {
            return null;
        }
    }

    private static void noteWrites(P p) {
        Expr.noteWrite(p.a);
    }
}
