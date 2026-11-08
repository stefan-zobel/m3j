// Java port of CM3's m3-sys/m3front/src/exprs/ExprRep.i3 and of the
// procedures of Expr.m3 that it exports for ExprRep.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: ExprRep.i3
// Last Modified On Tue Jun 20 15:20:23 PDT 1995 By kalsow
//      Modified On Thu Jun 15 12:45:56 PDT 1995 By ericv
//      Modified On Thu Nov 29 03:45:22 1990 By muller

// File: Expr.m3
// Last Modified On Tue Jun 20 15:44:34 PDT 1995 By kalsow
//      Modified On Thu Jun 15 13:07:24 PDT 1995 By ericv
//      Modified On Fri Dec 21 01:21:51 1990 By muller

public final class ExprRep {
    private ExprRep() {}

    // Port: the revelation of M3.Expr is the class Expr.T.

    public static class Ta extends Expr.T {
        public Expr.T a;

        @Override
        public boolean isEqual(Expr.T x, Type.Assumption z) {
            return eqCheckA(this, x, z);
        }
    }

    public static class Tab extends Expr.T {
        public Expr.T a, b;

        @Override
        public boolean isEqual(Expr.T x, Type.Assumption z) {
            return eqCheckAB(this, x, z);
        }
    }

    public static class Tabc extends Tab {
        public long class_;

        @Override
        public boolean isEqual(Expr.T x, Type.Assumption z) {
            return eqCheckAB(this, x, z);
        }
    }

    /** initializes the common part of an Expr.T */
    public static void init(Expr.T e) {
        e.origin = Scanner.offset;
        e.type = null;
        e.align = Target.UnknownAlign; // => uncached.
        e.checked = false;
        e.directAssignableType = false;
        e.doDirectAssign = false;
        e.isNamedConst = false;
    }

    //******************************************************************

    public static long staticLengthDefault(Expr.T e) {
        Type.T exprType = Expr.repTypeOf(e);
        if (exprType == null) {
            return Expr.lengthInvalid;
        } else if (OpenArrayType.is(exprType)) {
            return Expr.lengthNonStatic;
        } else if (!ArrayType.is(exprType)) {
            return Expr.lengthNonArray;
        } else {
            return Expr.lengthInvalid;
        }
    }

    public static boolean usesAssignProtocolDefault(Expr.T e) {
        return false;
    }

    public static boolean defaultCheckUseFailure(Expr.T e) {
        return true;
    }

    //******************************************* Alignments ************

    // Multi-use overrides for exprAlign, declared in ExprRep:

    /** Take it from the type.  Strip packed. */
    public static long exprAlignDefault(Expr.T e) {
        // Strips packed.
        Type.T type;
        Type.Info info = new Type.Info();
        type = Type.stripPacked(e.type);
        // ^Assume we are not in a packing environment.
        if (type != null) {
            Type.checkInfo(type, info);
            return info.alignment;
        }
        return Target.Word8.align;
    }

    public static long exprAddrAlign(Expr.T e) {
        return M3.target.Address.align;
    }

    public static long exprBoolAlign(Expr.T e) {
        Type.Info info = new Type.Info();
        Type.checkInfo(Bool.T, info);
        return info.alignment;
    }

    public static long exprIntAlign(Expr.T e) {
        Type.Info info = new Type.Info();
        Type.checkInfo(Int.T, info);
        return info.alignment;
    }

    /** Inherit alignment from argument zero. */
    public static long exprAlignArg0(ExprRep.Ta e) {
        return Expr.alignment(e.a);
    }

    //********************* default methods ************************************

    public static Type.T noType(Expr.T e) {
        Error.msg("Internal compiler error Expr.NoType");
        AssertError.check(false, "Expr");
        return null;
    }

    public static void noCheck(Expr.T e, M3.CheckState cs) {
    }

    public static Expr.T noValue(Expr.T e) {
        return null;
    }

    public static void noFPLiteral(Expr.T e, M3Buf.T mbuf) {
        AssertError.check(false, "Expr");
    }

    public static Expr.T self(Expr.T e) {
        return e;
    }

    public static void noBounds(Expr.T e, int[] min, int[] max) {
        Type.getBounds(e.type, min, max);
    }

    public static boolean isNever(Expr.T e, boolean lhs) {
        return false;
    }

    public static boolean isAlways(Expr.T e, boolean lhs) {
        return true;
    }

    public static boolean neverEq(Expr.T e, Expr.T x, Type.Assumption z) {
        return false;
    }

    public static void noPrepLiteral(Expr.T e, Type.T type, boolean is_const) {
    }

    public static void noLiteral(Expr.T e, long offset, Type.T type, boolean is_const) {
        AssertError.check(false, "Expr");
    }

    public static void noPrep(Expr.T e) {
    }

    public static void noCompile(Expr.T e, boolean StaticOnly) {
        AssertError.check(false, "Expr");
    }

    public static void notLValue(Expr.T e, boolean traced) {
        AssertError.check(false, "Expr");
    }

    public static void notLValueBool(Expr.T e, boolean traced, boolean StaticOnly) {
        AssertError.check(false, "Expr");
    }

    public static void notBoolean(Expr.T e, long t, long f, int freq) {
        AssertError.check(false, "Expr");
    }

    public static void prepNoBranch(Expr.T e, long t, long f, int freq) {
        e.prep();
        e.compile(/* StaticOnly := */ false);
        if ((t == CG.No_label)) {
            CG.if_false(f, freq);
        } else {
            CG.if_true(t, freq);
        }
    }

    public static void noBranch(Expr.T e, long t, long f, int freq) {
        // prep did all the work
    }

    public static void notAddressable(Expr.T e) {
        AssertError.check(false, "Expr");
    }

    public static void notWritable(Expr.T e) {
        // skip
    }

    public static boolean eqCheckA(ExprRep.Ta e, Expr.T x, Type.Assumption z) {
        if (x == null) {
            return false;
        } else if (x instanceof Ta b) {
            return (e.getClass() == x.getClass()) && Expr.isEqual(e.a, b.a, z);
        } else {
            return false;
        }
    }

    public static boolean eqCheckAB(ExprRep.Tab e, Expr.T x, Type.Assumption z) {
        if (x == null) {
            return false;
        } else if (x instanceof Tab b) {
            return (e.getClass() == b.getClass())
                && Expr.isEqual(e.a, b.a, z) && Expr.isEqual(e.b, b.b, z);
        } else {
            return false;
        }
    }
}
