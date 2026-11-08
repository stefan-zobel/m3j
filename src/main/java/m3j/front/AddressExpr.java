// Java port of CM3's m3-sys/m3front/src/exprs/AddressExpr.i3 and AddressExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.TInt;
import m3j.middle.TWord;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: AddressExpr.m3
// Last modified on Fri Feb 24 16:39:49 PST 1995 by kalsow
//      modified on Fri Apr 27 07:34:00 1990 by muller

/** ADDRESS *Constants* only. */
public final class AddressExpr {
    private AddressExpr() {}

    public static final class P extends Expr.T {
        public final int[] value = TInt.newInt();

        @Override
        public void compile(boolean StaticOnly) {
            AddressExpr.compile(this, StaticOnly);
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
            return AddressExpr.isZeroes(this, lhs);
        }

        @Override
        public void genFPLiteral(M3Buf.T mbuf) {
            AddressExpr.genFPLiteral(this, mbuf);
        }

        @Override
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            AddressExpr.genLiteral(this, offset, type, is_const);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType, check :=
        // ExprRep.NoCheck, need_addr := ExprRep.NotAddressable, prep :=
        // ExprRep.NoPrep, prepLV := ExprRep.NotLValue, compileLV :=
        // ExprRep.NotLValueBool, prepBR, compileBR := ExprRep.NotBoolean,
        // isWritable, isDesignator := ExprRep.IsNever, prepLiteral :=
        // ExprRep.NoPrepLiteral and note_write := ExprRep.NotWritable
        // restate the inherited methods.
    }

    public static Expr.T new_(int[] value) {
        P p;
        p = new P();
        ExprRep.init(p);
        TInt.assign(p.value, value);
        p.checked = true;
        if (TInt.EQ(value, TInt.Zero)) {
            p.type = Null.T;
            p.repType = Null.T;
        } else {
            p.type = Addr.T;
            p.repType = Addr.T;
        }
        return p;
    }

    public static boolean split(Expr.T e, int[] value) {
        if (e == null) {
            return false;
        } else if (e instanceof P p) {
            TInt.assign(value, p.value);
            return true;
        } else {
            return false;
        }
    }

    public static boolean add(Expr.T a, Expr.T b, Expr.T[] c) {
        int[] i = TInt.newInt(), j = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (!IntegerExpr.split(b, i, t)) {
            return false;
        }
        if (a == null) {
            return false;
        } else if (a instanceof P p) {
            TWord.add(p.value, i, j);
            TInt.extend(j, (int) M3.target.Address.bytes, j);
            c[0] = new_(j);
            return true;
        } else {
            return false;
        }
    }

    public static boolean subtract(Expr.T a, Expr.T b, Expr.T[] c) {
        int[] i = TInt.newInt(), j = TInt.newInt(), k = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (a == null) {
            return false;
        } else if (a instanceof P p) {
            TInt.assign(i, p.value);
        } else {
            return false;
        }
        if (IntegerExpr.split(b, j, t)) {
            TWord.subtract(i, j, k);
            TInt.extend(k, (int) M3.target.Address.bytes, k);
            c[0] = new_(k);
        } else { // address - address
            if (b == null) {
                return false;
            } else if (b instanceof P p) {
                TWord.subtract(i, p.value, k);
                TInt.extend(k, (int) M3.target.Address.bytes, k);
                // Sic: t was not set by the failed IntegerExpr.Split, so the
                // integer constant gets the type NIL.
                c[0] = IntegerExpr.new_(t[0], k);
            } else {
                return false;
            }
        }
        return true;
    }

    public static boolean compare(Expr.T a, Expr.T b, long[] sign) {
        int[] x = TInt.newInt(), y = TInt.newInt();
        if (!splitPair(a, b, x, y)) {
            return false;
        }
        TWord.and(x, M3.target.Address.max, x);
        TWord.and(y, M3.target.Address.max, y);
        if (TWord.LT(x, y)) {
            sign[0] = -1;
        } else if (TWord.LT(y, x)) {
            sign[0] = 1;
        } else {
            sign[0] = 0;
        }
        return true;
    }

    private static boolean splitPair(Expr.T a, Expr.T b, int[] x, int[] y) {
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
        long[] val = new long[1];
        boolean b = TInt.toInt(p.value, val);
        AssertError.check(b, "AddressExpr");
        CG.load_nil();
        if ((val[0] != 0)) {
            CG.add_offset(Target.Byte * val[0]);
        }
    }

    private static void bounder(P p, int[] min, int[] max) {
        TInt.assign(min, p.value);
        TInt.assign(max, p.value);
    }

    private static boolean isZeroes(P p, boolean lhs) {
        return p == Null.Nil || TInt.EQ(p.value, TInt.Zero);
    }

    private static void genFPLiteral(P p, M3Buf.T buf) {
        M3Buf.putText(buf, "ADDR<");
        M3Buf.putIntt(buf, p.value);
        M3Buf.putChar(buf, '>');
    }

    private static void genLiteral(P p, long offset, Type.T type,
                                   boolean is_const) {
        if (!TInt.EQ(p.value, TInt.Zero)) {
            CG.init_int(offset, Math.min(M3.target.Integer.size, M3.target.Address.size),
                        p.value, is_const);
        }
    }
}
