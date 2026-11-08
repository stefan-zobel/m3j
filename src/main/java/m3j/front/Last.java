// Java port of CM3's m3-sys/m3front/src/builtinOps/Last.i3 and Last.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: Last.m3
// Last Modified On Mon Sep 26 09:15:29 PDT 1994 By kalsow
//      Modified On Fri Dec 21 01:34:18 1990 By muller

public final class Last {
    private Last() {}

    private static CallExpr.MethodList Z;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        First.doCheck("LAST", ce);
    }

    private static void prep(CallExpr.T ce) {
        Expr.T e = ce.args[0];
        Type.T[] t = new Type.T[1];
        Type.T[] index = new Type.T[1];
        Type.T[] element = new Type.T[1];
        if (!TypeExpr.split(e, t)) {
            t[0] = Expr.typeOf(e);
        }
        Type.compile(t[0]);
        if (ArrayType.split(t[0], index, element)) {
            t[0] = index[0];
        }

        if ((t[0] == null)) { // open array
            Expr.prep(e);
        }
    }

    private static void compile(CallExpr.T ce) {
        Expr.T e = ce.args[0];
        Type.T[] t = new Type.T[1];
        Type.T[] index = new Type.T[1];
        Type.T[] element = new Type.T[1];
        int[] min = TInt.newInt();
        int[] max = TInt.newInt();
        if (!TypeExpr.split(e, t)) {
            t[0] = Expr.typeOf(e);
        }
        if (ArrayType.split(t[0], index, element)) {
            t[0] = index[0];
        }

        if ((t[0] == null)) { // open array
            Expr.compile(e);
            CG.open_size(0);
            CG.load_integer(M3.target.Integer.cg_type, TInt.One);
            CG.subtract(M3.target.Integer.cg_type);
        } else if (Type.getBounds(t[0], min, max)) { // ordinal type
            if (Type.isSubtype(t[0], LInt.T)) {
                CG.load_integer(M3.target.Longint.cg_type, max);
            } else {
                CG.load_integer(M3.target.Integer.cg_type, max);
            }
        } else if (Type.isEqual(t[0], Reel.T, null)) {
            CG.load_float(Target.Real.max);
        } else if (Type.isEqual(t[0], LReel.T, null)) {
            CG.load_float(M3.target.Longreal.max);
        } else if (Type.isEqual(t[0], EReel.T, null)) {
            CG.load_float(M3.target.Extended.max);
        } else {
            AssertError.check(false, "Last");
        }
    }

    private static Expr.T fold(CallExpr.T ce) {
        Type.T[] t = new Type.T[1];
        Type.T[] index = new Type.T[1];
        Type.T[] elem = new Type.T[1];
        Expr.T e;
        int[] min = TInt.newInt();
        int[] max = TInt.newInt();
        e = ce.args[0];
        if (TypeExpr.split(e, t)) {
            return lastOfType(t[0]);
        }
        t[0] = Expr.typeOf(e);
        if (!ArrayType.split(t[0], index, elem)) {
            return null;
        }
        if ((index[0] == null)) {
            // LAST (open array value) => try for constant open array
            e = Expr.constValue(e);
            if ((e == null)) {
                return null;
            }
            if (ArrayExpr.getBounds(e, min, max)) { // Array constructor.
                return IntegerExpr.new_(Int.T, max);
            } else {
                return null;
            }
        }
        return lastOfType(t[0]);
    }

    private static Expr.T lastOfType(Type.T t_) {
        Type.T[] t = {t_};
        int[] min = TInt.newInt();
        int[] max = TInt.newInt();
        Type.T[] elem = new Type.T[1];
        Type.T t_base;
        if (ArrayType.split(t[0], t, elem) && (t[0] == null)) {
            return null;
        }
        t_base = Type.base(t[0]);
        if (Type.getBounds(t[0], min, max)) {
            if (t_base == Int.T || t_base == LInt.T) {
                return IntegerExpr.new_(t_base, max);
            } else {
                return EnumExpr.new_(t[0], max);
            }
        } else if (t_base == Reel.T) {
            return ReelExpr.new_(Target.Real.max);
        } else if (t_base == LReel.T) {
            return ReelExpr.new_(M3.target.Longreal.max);
        } else if (t_base == EReel.T) {
            return ReelExpr.new_(M3.target.Extended.max);
        } else {
            return null;
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 1, true, false, false, null,
                                   First::typeOf,
                                   First::typeOf,
                                   CallExpr::notAddressable,
                                   Last::check,
                                   Last::prep,
                                   Last::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::prepNoBranch,
                                   CallExpr::noBranch,
                                   Last::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("LAST", Z, true);
    }
}
