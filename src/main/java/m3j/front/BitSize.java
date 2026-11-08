// Java port of CM3's m3-sys/m3front/src/builtinOps/BitSize.i3 and BitSize.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.util.AssertError;

// File: BitSize.m3
// Last Modified On Wed Jun 29 17:00:26 PDT 1994 By kalsow
//      Modified On Sat Dec  8 00:54:27 1990 By muller

public final class BitSize {
    private BitSize() {}

    private static CallExpr.MethodList Z;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        doCheck("BITSIZE", ce, cs);
    }

    private static void prep(CallExpr.T ce) {
        doPrep(ce.args[0]);
    }

    private static void compile(CallExpr.T ce) {
        doCompile(ce.args[0], 1);
    }

    private static Expr.T fold(CallExpr.T ce) {
        return doFold(ce.args[0], 1);
    }

    public static void doCheck(String name, CallExpr.T ce, M3.CheckState cs) {
        Type.T[] t = new Type.T[1];
        Expr.T e = ce.args[0];
        Type.Info info = new Type.Info();
        if (Expr.isDesignator(e)) {
            // ok
            t[0] = Type.checkInfo(Expr.typeOf(e), info);
        } else if (TypeExpr.split(e, t)) {
            if (OpenArrayType.is(t[0])) {
                Error.txt(name, "argument cannot be an open array type");
            }
        } else {
            Error.txt(name, "argument must be a designator or type");
        }
        ce.type = Card.T;
    }

    public static void doPrep(Expr.T e) {
        Type.T[] t = new Type.T[1];
        Type.Info info = new Type.Info();
        if (TypeExpr.split(e, t)) {
            return;
        }

        t[0] = Type.checkInfo(Expr.typeOf(e), info);
        if ((info.class_ != Type.Class.OpenArray)) {
            return;
        }

        // ELSE, open array
        Expr.prep(e);
    }

    public static void doCompile(Expr.T e, long unit) {
        Type.T[] t = new Type.T[1];
        long sz;
        long align;
        CG.Val t_array;
        Type.Info info = new Type.Info();
        if (TypeExpr.split(e, t)) {
            Type.compile(t[0]);
            Type.checkInfo(t[0], info);
            AssertError.check(info.size > 0, "BitSize");
            CG.load_intt(Math.floorDiv(info.size + unit - 1, unit));
            return;
        }

        t[0] = Type.checkInfo(Expr.typeOf(e), info);
        if ((info.class_ != Type.Class.OpenArray)) {
            CG.load_intt(Math.floorDiv(info.size + unit - 1, unit));
            return;
        }

        // ELSE, open array
        align = info.alignment;
        Expr.compile(e);
        t_array = CG.pop();
        for (long i = 0, last = OpenArrayType.openDepth(t[0]) - 1; i <= last; i++) {
            CG.push(t_array);
            CG.open_size(i);
            if ((i != 0)) {
                CG.multiply(M3.target.Integer.cg_type);
            }
        }
        CG.free(t_array);

        sz = OpenArrayType.eltPack(t[0]);
        if (Math.floorMod(sz, unit) == 0) {
            CG.load_intt(Math.floorDiv(sz, unit));
            CG.multiply(M3.target.Integer.cg_type);
        } else { // array elements aren't "unit"-aligned
            CG.load_intt(sz);
            CG.multiply(M3.target.Integer.cg_type);
            CG.load_intt(unit - 1);
            CG.add(M3.target.Integer.cg_type);
            CG.load_intt(unit);
            CG.div(M3.target.Integer.cg_type, M3CG.Sign.Positive, M3CG.Sign.Positive);
        }
    }

    public static Expr.T doFold(Expr.T e, long unit) {
        Type.T[] t = new Type.T[1];
        int[] size = TInt.newInt();
        int[] a = TInt.newInt();
        int[] b = TInt.newInt();
        int[] c = TInt.newInt();
        int[] d = TInt.newInt();
        Type.Info info = new Type.Info();
        if (!TypeExpr.split(e, t)) {
            t[0] = Type.checkInfo(Expr.typeOf(e), info);
            if ((info.class_ == Type.Class.OpenArray)) {
                return null;
            }
        }
        t[0] = Type.checkInfo(t[0], info);
        if (TInt.fromInt(info.size, size)
            && TInt.fromInt(unit, a)
            && TInt.fromInt(unit - 1, b)
            && TInt.add(size, b, c)
            && TInt.div(c, a, d)
            && !TInt.LT(d, M3.target.Integer.min)
            && !TInt.LT(M3.target.Integer.max, d)) {
            return IntegerExpr.new_(Int.T, d);
        } else {
            return null;
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 1, true, false, false, Card.T,
                                   null, null,
                                   CallExpr::notAddressable,
                                   BitSize::check,
                                   BitSize::prep,
                                   BitSize::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   BitSize::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("BITSIZE", Z, true);
    }
}
