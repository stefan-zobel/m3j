// Java port of CM3's m3-sys/m3front/src/exprs/LoopholeExpr.i3 and LoopholeExpr.m3.
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
import m3j.middle.Target.CGType;

// File: LoopholeExpr.i3
// Last Modified On Thu Apr 30 13:24:03 PDT 1992 By kalsow

// File: LoopholeExpr.m3
// Last Modified On Tue May 23 15:33:47 PDT 1995 By kalsow
//      Modified On Sun Dec 23 08:07:22 1990 By muller

public final class LoopholeExpr {
    private LoopholeExpr() {}

    public enum Kind {
        /** code generator cannot tell the difference */
        Noop,
        /** designator -> open array */
        D_to_A,
        /** structure -> open array */
        S_to_A,
        /** value -> open array */
        V_to_A,
        /** designator -> structure */
        D_to_S,
        /** structure -> structure */
        S_to_S,
        /** value -> structure */
        V_to_S,
        /** designator -> value */
        D_to_V,
        /** structure -> value */
        S_to_V,
        /** value -> value */
        V_to_V
    }

    public static final class P extends Expr.T {
        public Kind kind;
        public Expr.T expr;
        public Type.T tipe;
        public M3CG.Var tmp;
        public long tmp_cnt;

        @Override
        public void check(M3.CheckState cs) {
            LoopholeExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            LoopholeExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            LoopholeExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepLV(boolean traced) {
            LoopholeExpr.prepLV(this, traced);
        }

        @Override
        public void compileLV(boolean traced, boolean StaticOnly) {
            LoopholeExpr.compileLV(this, traced, StaticOnly);
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
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public void getBounds(int[] min, int[] max) {
            bounder(this, min, max);
        }

        @Override
        public boolean isWritable(boolean lhs) {
            return LoopholeExpr.isWritable(this, lhs);
        }

        @Override
        public boolean isDesignator(boolean lhs) {
            return LoopholeExpr.isDesignator(this, lhs);
        }

        @Override
        public void genFPLiteral(M3Buf.T buf) {
            LoopholeExpr.genFPLiteral(this, buf);
        }

        @Override
        public void note_write() {
            noteWrites(this);
        }

        @Override
        public long exprAlign() {
            return loopholeExprAlign(this);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType, isZeroes
        // := ExprRep.IsNever, prepLiteral := ExprRep.NoPrepLiteral and
        // genLiteral := ExprRep.NoLiteral restate the inherited methods.
    }

    // EXPORTED:
    public static Expr.T new_(Expr.T a, Type.T t) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.origin = a.origin;
        p.expr = a;
        p.tipe = t;
        p.type = t;
        p.repType = Type.stripPacked(t);
        p.tmp = null;
        p.tmp_cnt = 0;
        return p;
    }

    // Dynamically dispatched-to:
    private static void check(P p, M3.CheckState cs) {
        Type.T src, dest;
        Type.T[] elt = new Type.T[1];
        long sz0, sz1;
        boolean open_array_out, desig_in, struct_in, struct_out;
        long align_in, align_out;
        Type.Info dest_info = new Type.Info(), src_info = new Type.Info(),
                  elt_info = new Type.Info();
        Expr.typeCheck(p.expr, cs);
        p.tipe = Type.checkInfo(p.tipe, dest_info);

        src = Type.checkInfo(Expr.typeOf(p.expr), src_info);
        dest = p.tipe;
        desig_in = Expr.isDesignator(p.expr);
        struct_in = Type.isStructured(src);
        struct_out = Type.isStructured(dest);
        open_array_out = OpenArrayType.split(dest, elt);
        align_in = src_info.alignment;
        align_out = dest_info.alignment;

        // check to see that the value is legal
        if ((src_info.class_ == Type.Class.OpenArray)) {
            Error.msg("LOOPHOLE: first argument cannot be an open array (2.7).");
        }
        sz0 = src_info.size;

        // check to see that the destination type is legal
        if (open_array_out) {
            // open array type
            elt[0] = Type.checkInfo(elt[0], elt_info);
            if ((elt_info.class_ == Type.Class.OpenArray)) {
                Error.msg("Cannot LOOPHOLE to a multidimensional open array type (2.7).");
            }
            sz1 = elt_info.size;
            if ((sz1 <= 0) || (Math.floorMod(sz0, sz1) != 0)) {
                Error.msg("LOOPHOLE: expression's size ("
                          + Long.toString(sz0) + ") is not a multiple of open array type's ("
                          + Long.toString(sz1) + ") (2.7).");
            }
            align_out = elt_info.alignment;
        } else {
            // fixed size type
            sz1 = dest_info.size;
            if ((sz0 != sz1)) {
                Error.msg("LOOPHOLE: expression's size ("
                          + Long.toString(sz0) + ") differs from type's ("
                          + Long.toString(sz1) + ") (2.7).");
            }
        }

        // check for alignment problems
        if ((align_in < align_out) || (Math.floorMod(align_in, align_out) != 0)) {
            Error.warn(1, "LOOPHOLE: expression's alignment may differ from type's");
        }

        // classify the type of LOOPHOLE operation

        if (open_array_out) {
            if (desig_in) {
                p.kind = Kind.D_to_A;
            } else if (struct_in) {
                p.kind = Kind.S_to_A;
            } else {
                p.kind = Kind.V_to_A;
            }
        } else if ((src_info.stk_type == dest_info.stk_type)) {
            p.kind = Kind.Noop;
        } else if (struct_out) {
            if (desig_in) {
                p.kind = Kind.D_to_S;
            } else if (struct_in) {
                p.kind = Kind.S_to_S;
            } else {
                p.kind = Kind.V_to_S;
            }
        } else if (struct_in) {
            p.kind = Kind.S_to_V;
        } else if (desig_in) {
            p.kind = Kind.D_to_V;
        } else {
            p.kind = Kind.V_to_V;
        }

        if ((p.kind == Kind.D_to_A) || (p.kind == Kind.D_to_S)) {
            // we're going to take the address of this value
            Expr.needsAddress(p.expr);
        }
    }

    // Dynamically dispatched-to:
    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e instanceof P b) {
            return Type.isEqual(a.tipe, b.tipe, x)
                && Expr.isEqual(a.expr, b.expr, x);
        } else {
            return false;
        }
    }

    // Dynamically dispatched-to:
    private static void needsAddress(P p) {
        switch (p.kind) {
            case Noop,
                 D_to_S,
                 S_to_S,
                 D_to_V,
                 S_to_V,
                 V_to_V -> Expr.needsAddress(p.expr);

            case D_to_A,
                 S_to_A,
                 V_to_A,
                 V_to_S -> {
                // ok, because we build a temporary
            }
        }
    }

    // Dynamically dispatched-to:
    private static void prep(P p) {
        Expr.T e = p.expr;
        Type.T u = Expr.typeOf(e);
        Type.T t = p.tipe;
        M3CG.Var t1;
        long sz, t_align, u_align, z_align;
        Type.Info u_info = new Type.Info(), t_info = new Type.Info();
        if ((p.tmp_cnt > 0)) {
            p.tmp_cnt++;
            return;
        }
        u = Type.checkInfo(u, u_info);
        t = Type.checkInfo(t, t_info);
        t_align = t_info.alignment;
        u_align = u_info.alignment;
        sz = u_info.size;
        Type.compile(t);
        Type.compile(u);
        z_align = Math.max(t_align, u_align);

        switch (p.kind) {
            case Noop -> Expr.prep(e);
            case D_to_A -> {
                p.tmp_cnt++;
                Expr.prepLValue(e, /* traced := */ false);
                Expr.compileAddress(e, /* traced := */ false);
                p.tmp = buildArray(p, sz);
            }
            case S_to_A -> {
                p.tmp_cnt++;
                Expr.prep(e);
                Expr.compile(e);
                p.tmp = buildArray(p, sz);
            }
            case V_to_A -> {
                // copy the value to a temporary
                p.tmp_cnt++;
                Expr.prep(e);
                t1 = CG.declare_local(M3ID.NoID, sz, z_align, CGType.Struct,
                                      (int) Type.globalUID(u),
                                      /* in_memory := */ true, /* up_level := */ false,
                                      /* f := */ CG.Never);
                Expr.compile(e);
                CG.store(t1, 0, sz, z_align, u_info.stk_type);
                CG.load_addr_of(t1, 0, z_align);
                p.tmp = buildArray(p, sz);
            }
            case D_to_S -> Expr.prepLValue(e, /* traced := */ false);
            case S_to_S -> Expr.prep(e);
            case V_to_S -> {
                p.tmp_cnt++;
                Expr.prep(e);
                p.tmp = CG.declare_temp(sz, z_align, CGType.Struct, /* in_memory := */ true);
                Expr.compile(e);
                CG.store(p.tmp, 0, sz, z_align, u_info.stk_type);
            }
            case D_to_V -> Expr.prep(e);
            case S_to_V -> Expr.prep(e);
            case V_to_V -> Expr.prep(e);
        }
    }

    // Dynamically dispatched-to:
    private static long loopholeExprAlign(P p) {
        Type.T type;
        Type.Info typeInfo = new Type.Info();
        type = Type.stripPacked(p.tipe);
        type = Type.checkInfo(type, typeInfo);
        return typeInfo.alignment;
    }

    // Dynamically dispatched-to:
    private static void compile(P p, boolean StaticOnly) {
        Expr.T e = p.expr;
        Type.T u = Expr.typeOf(e);
        Type.T t = p.tipe;
        long sz, t_align, u_align, z_align;
        CGType t_stk_type, u_stk_type;
        Type.Info u_info = new Type.Info(), t_info = new Type.Info();
        u = Type.checkInfo(u, u_info);
        t = Type.checkInfo(t, t_info);
        t_stk_type = t_info.stk_type;
        t_align = t_info.alignment;
        u_stk_type = u_info.stk_type;
        u_align = u_info.alignment;
        sz = u_info.size;
        Type.compile(t);
        Type.compile(u);
        z_align = Math.max(t_align, u_align);

        switch (p.kind) {
            case Noop -> {
                Expr.compile(e);
                CG.boost_addr_alignment(t_align);
            }
            case D_to_A,
                 S_to_A,
                 V_to_A -> pushTmp(p, z_align);
            case D_to_S -> {
                Expr.compileAddress(e, /* traced := */ false);
                CG.boost_addr_alignment(t_align);
            }
            case S_to_S -> {
                Expr.compile(e);
                CG.boost_addr_alignment(t_align);
            }
            case V_to_S -> pushTmp(p, z_align);
            case D_to_V -> {
                Expr.compile(e);
                CG.loophole(u_stk_type, t_stk_type);
                //*** back-ends have problems with this because floating-point
                //   variables may be in floating-point registers...
                //Expr.PrepLValue (e);
                //Expr.CompileLValue (e);
                //CG.Boost_addr_alignment (t_align);
                //CG.Load_indirect (t_stk_type, 0, sz, t_align);
                //******
            }
            case S_to_V -> {
                Expr.compile(e);
                CG.boost_addr_alignment(t_align);
                CG.load_indirect(t_stk_type, 0, sz, t_align);
            }
            case V_to_V -> {
                Expr.compile(e);
                CG.loophole(u_stk_type, t_stk_type);
            }
        }
    }

    private static void pushTmp(P p, long align) {
        p.tmp_cnt--;
        if ((p.tmp_cnt <= 0)) {
            CG.load_addr_of_temp(p.tmp, 0, align);
            p.tmp = null;
        } else {
            CG.load_addr_of(p.tmp, 0, align);
        }
    }

    /** PRE: Address of elements is on top of CG stack. */
    private static M3CG.Var buildArray(P p, long src_size) {
        M3CG.Var array;
        Type.T elt_type = OpenArrayType.nonopenEltType(p.tipe);
        Type.Info elt_info = new Type.Info();
        elt_type = Type.checkInfo(elt_type, elt_info);
        //* CG.Check_byte_aligned (); *
        array = OpenArrayType.declareDopeTemp(p.tipe);
        CG.store_addr(array, M3.rt.OA_elt_ptr);
        CG.load_intt(Math.floorDiv(src_size, elt_info.size));
        CG.store_int(M3.target.Integer.cg_type, array, M3.rt.OA_size_0);
        return array;
    }

    // Dynamically dispatched-to:
    private static void prepLV(P p, boolean traced) {
        Expr.T e = p.expr;
        Type.T u = Expr.typeOf(e);
        Type.T t = p.tipe;
        long sz, t_align, u_align, z_align;
        M3CG.Var t1;
        Type.Info u_info = new Type.Info(), t_info = new Type.Info();
        if ((p.tmp_cnt > 0)) {
            p.tmp_cnt++;
            return;
        }
        u = Type.checkInfo(u, u_info);
        t = Type.checkInfo(t, t_info);
        t_align = t_info.alignment;
        u_align = u_info.alignment;
        z_align = Math.max(t_align, u_align);
        sz = u_info.size;
        Type.compile(t);
        Type.compile(u);

        switch (p.kind) {
            case Noop,
                 D_to_S,
                 S_to_S,
                 D_to_V,
                 S_to_V,
                 V_to_V -> Expr.prepLValue(p.expr, traced);

            case D_to_A -> {
                p.tmp_cnt++;
                Expr.prepLValue(e, traced);
                Expr.compileLValue(e, traced);
                p.tmp = buildArray(p, sz);
            }
            case S_to_A -> {
                p.tmp_cnt++;
                Expr.prep(e);
                Expr.compile(e);
                p.tmp = buildArray(p, sz);
            }
            case V_to_A -> {
                // copy the value to a temporary
                p.tmp_cnt++;
                Expr.prep(e);
                t1 = CG.declare_local(M3ID.NoID, sz, z_align, CGType.Struct,
                                      (int) Type.globalUID(u),
                                      /* in_memory := */ true, /* up_level := */ false,
                                      /* f := */ CG.Never);
                Expr.compile(e);
                CG.store(t1, 0, sz, z_align, u_info.stk_type);
                CG.load_addr_of(t1, 0, z_align);
                p.tmp = buildArray(p, sz);
            }
            case V_to_S -> {
                p.tmp_cnt++;
                Expr.prep(e);
                p.tmp = CG.declare_temp(sz, z_align, CGType.Struct, /* in_memory := */ true);
                Expr.compile(e);
                CG.store(p.tmp, 0, sz, z_align, u_info.stk_type);
            }
        }
    }

    // Dynamically dispatched-to:
    private static void compileLV(P p, boolean traced, boolean StaticOnly) {
        Expr.T e = p.expr;
        Type.T u = Expr.typeOf(e);
        Type.T t = p.tipe;
        long sz, t_align, u_align, z_align;
        Type.Info u_info = new Type.Info(), t_info = new Type.Info();
        u = Type.checkInfo(u, u_info);
        t = Type.checkInfo(t, t_info);
        t_align = t_info.alignment;
        u_align = u_info.alignment;
        z_align = Math.max(t_align, u_align);
        sz = u_info.size;
        Type.compile(t);
        Type.compile(u);

        switch (p.kind) {
            case Noop,
                 S_to_S,
                 D_to_V,
                 S_to_V,
                 V_to_V -> {
                Expr.compileLValue(p.expr, traced);
                CG.boost_addr_alignment(t_align);
            }

            case D_to_S -> {
                Expr.compileLValue(p.expr, traced);
                CG.boost_addr_alignment(t_align);

                // Inhibit some optimization that causes compilation to fail. e.g.:
                // m3core/src/float/IEEE/RealFloat.m3:
                // In function 'RealFloat__CopySign':
                // internal compiler error: in convert_move, at expr.c:371
                //
                // ?ForceStacked() inhibits optimizations done by m3front/src/misc/CG.m3?
                // ?The particular optimizations are removal of address taken and
                // pointer indirections? ?Leaving the address taken inhibits
                // gcc optimization? ?Given that this is LOOPHOLE and floating point,
                // inhibiting optimization is very ok?
                if (Target.FloatType[t_info.stk_type.ordinal()]
                    != Target.FloatType[u_info.stk_type.ordinal()]) {
                    CG.forceStacked();
                }
            }

            case D_to_A,
                 S_to_A,
                 V_to_A -> pushTmp(p, z_align);
            case V_to_S -> pushTmp(p, z_align);
        }
    }

    // Dynamically dispatched-to:
    private static Expr.T fold(P p) {
        Expr.T e;
        e = Expr.constValue(p.expr);
        if ((e == null)) {
            return null;
        }
        p.expr = e;
        return p;
    }

    // Dynamically dispatched-to:
    private static void bounder(P p, int[] min, int[] max) {
        int[] min1 = TInt.newInt(), max1 = TInt.newInt();
        Expr.getBounds(p.expr, min, max);
        Type.getBounds(p.tipe, min1, max1);
        if (TInt.LT(min, min1)) {
            TInt.assign(min, min1);
        }
        if (TInt.LT(max1, max)) {
            TInt.assign(max, max1);
        }
    }

    // Dynamically dispatched-to:
    private static boolean isDesignator(P p, boolean lhs) {
        return Expr.isDesignator(p.expr);
    }

    // Dynamically dispatched-to:
    private static boolean isWritable(P p, boolean lhs) {
        return Expr.isWritable(p.expr, lhs);
    }

    // Dynamically dispatched-to:
    private static void genFPLiteral(P p, M3Buf.T buf) {
        Expr.genFPLiteral(p.expr, buf);
    }

    // Dynamically dispatched-to:
    private static void noteWrites(P p) {
        Expr.noteWrite(p.expr);
    }
}
