// Java port of CM3's m3-sys/m3front/src/exprs/EnumExpr.i3 and EnumExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.TInt;
import m3j.util.AssertError;

// File: EnumExpr.m3
// Last modified on Fri Feb 24 16:42:47 PST 1995 by kalsow
//      modified on Fri Nov 30 21:02:21 1990 by muller

public final class EnumExpr {
    private EnumExpr() {}

    public static final class P extends Expr.T {
        public final int[] value = TInt.newInt();

        @Override
        public void compile(boolean StaticOnly) {
            EnumExpr.compile(this, StaticOnly);
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
            return ExprRep.self(this);
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
        public boolean isZeroes(boolean lhs) {
            return EnumExpr.isZeroes(this, lhs);
        }

        @Override
        public void genFPLiteral(M3Buf.T mbuf) {
            EnumExpr.genFPLiteral(this, mbuf);
        }

        @Override
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            EnumExpr.genLiteral(this, offset, type, is_const);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType, check :=
        // ExprRep.NoCheck, need_addr := ExprRep.NotAddressable, prep :=
        // ExprRep.NoPrep, prepLV := ExprRep.NotLValue, compileLV :=
        // ExprRep.NotLValueBool, isWritable, isDesignator :=
        // ExprRep.IsNever, prepLiteral := ExprRep.NoPrepLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
    }

    public static Expr.T new_(Type.T type, int[] value) {
        P p;
        p = new P();
        ExprRep.init(p);
        TInt.assign(p.value, value);
        p.type       = type;
        p.repType    = type;
        p.checked    = true;
        return p;
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e == null) {
            return false;
        } else if (e instanceof P b) {
            return TInt.EQ(a.value, b.value);
        } else {
            return false;
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        AssertError.check(!StaticOnly, "EnumExpr");
        CG.load_integer(M3.target.Integer.cg_type, p.value);
    }

    private static void bounder(P p, int[] min, int[] max) {
        TInt.assign(min, p.value);
        TInt.assign(max, p.value);
    }

    public static boolean compare(Expr.T a, Expr.T b, long[] sign) {
        int[] x = TInt.newInt(), y = TInt.newInt();
        if (!splitPair(a, b, x, y)) {
            return false;
        }
        if (TInt.LT(x, y)) {
            sign[0] = -1;
        } else if (TInt.LT(y, x)) {
            sign[0] = 1;
        } else {
            sign[0] = 0;
        }
        return true;
    }

    public static boolean split(Expr.T e, int[] value, Type.T[] t) {
        if (e == null) {
            return false;
        } else if (e instanceof P p) {
            TInt.assign(value, p.value);
            t[0] = Type.base(p.type);
            return true;
        } else {
            return false;
        }
    }

    public static boolean splitPair(Expr.T a, Expr.T b, int[] x, int[] y) {
        if (a == null) {
            return false;
        } else if (a instanceof P p) {
            TInt.assign(x, p.value);
        } else {
            return false;
        }
        if (b == null) {
            return false;
        } else if (b instanceof P p) {
            TInt.assign(y, p.value);
        } else {
            return false;
        }
        return true;
    }

    private static boolean isZeroes(P p, boolean lhs) {
        return TInt.EQ(p.value, TInt.Zero);
    }

    private static void genFPLiteral(P p, M3Buf.T buf) {
        M3Buf.putText(buf, "ENUM<");
        M3Buf.putIntt(buf, p.value);
        M3Buf.putChar(buf, '>');
    }

    private static void genLiteral(P p, long offset, Type.T type, boolean is_const) {
        Type.Info info = new Type.Info();
        Type.checkInfo(type, info);
        CG.init_int(offset, info.size, p.value, is_const);
    }
}
