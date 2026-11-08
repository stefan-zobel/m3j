// Java port of CM3's m3-sys/m3front/src/builtinOps/First.i3 and First.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: First.m3
// Last Modified On Fri Jun 24 12:35:52 PDT 1994 By kalsow
//      Modified On Fri Dec 21 01:35:21 1990 By muller

public final class First {
    private First() {}

    private static CallExpr.MethodList Z;

    public static Type.T typeOf(CallExpr.T ce) {
        Expr.T e;
        Type.T[] t = new Type.T[1];
        Type.T[] index = new Type.T[1];
        Type.T[] element = new Type.T[1];
        e = ce.args[0];
        t[0] = Expr.typeOf(e);
        index[0] = null;
        if (ArrayType.split(t[0], index, element)) {
            if ((index[0] == null)) {
                index[0] = Int.T;
            }
        } else if (TypeExpr.split(e, t)) {
            if (!ArrayType.split(t[0], index, element)) {
                index[0] = t[0];
            }
        }
        if ((index[0] == null)) {
            index[0] = Int.T;
        }
        return Type.base(index[0]);
    }

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        doCheck("FIRST", ce);
    }

    public static void doCheck(String name, CallExpr.T ce) {
        Expr.T e;
        Type.T[] t = new Type.T[1];
        Type.T[] index = new Type.T[1];
        Type.T[] element = new Type.T[1];
        e = ce.args[0];
        t[0] = Expr.typeOf(e);
        if (ArrayType.split(t[0], index, element)) {
            if ((index[0] == null)) {
                index[0] = Int.T;
            }
        } else if (TypeExpr.split(e, t)) {
            if (ArrayType.split(t[0], index, element)) {
                if ((index[0] == null)) {
                    Error.txt(name, "argument cannot be an open array type");
                    index[0] = Int.T;
                }
            } else {
                index[0] = t[0];
            }
        } else {
            Error.txt(name, "argument must be a type or array");
            index[0] = Int.T;
        }
        if (EnumType.is(index[0])) {
            if (!TInt.LT(TInt.Zero, Type.number(index[0]))) {
                Error.txt(name, "empty enumeration type");
            }
        } else if (Type.isOrdinal(index[0])) {
            // ordinal type => OK
        } else if (Type.isEqual(index[0], Reel.T, null)) {
            // OK
        } else if (Type.isEqual(index[0], LReel.T, null)) {
            // OK
        } else if (Type.isEqual(index[0], EReel.T, null)) {
            // OK
        } else {
            Error.txt(name, "argument must be an ordinal type, floating type, array type or array");
        }
        ce.type = Type.base(index[0]);
    }

    private static void prep(CallExpr.T ce) {
        // skip
    }

    private static void compile(CallExpr.T ce) {
        Expr.T e = ce.args[0];
        int[] min = TInt.newInt();
        int[] max = TInt.newInt();
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
            CG.load_integer(M3.target.Integer.cg_type, TInt.Zero);
        } else if (Type.getBounds(t[0], min, max)) { // ordinal type
            if (Type.isSubtype(t[0], LInt.T)) {
                CG.load_integer(M3.target.Longint.cg_type, min);
            } else {
                CG.load_integer(M3.target.Integer.cg_type, min);
            }
        } else if (Type.isEqual(t[0], Reel.T, null)) {
            CG.load_float(Target.Real.min);
        } else if (Type.isEqual(t[0], LReel.T, null)) {
            CG.load_float(M3.target.Longreal.min);
        } else if (Type.isEqual(t[0], EReel.T, null)) {
            CG.load_float(M3.target.Extended.min);
        } else {
            AssertError.check(false, "First");
        }
    }

    private static Expr.T fold(CallExpr.T ce) {
        Type.T[] t = new Type.T[1];
        Type.T[] index = new Type.T[1];
        Type.T[] elem = new Type.T[1];
        Expr.T e;
        e = ce.args[0];
        if (TypeExpr.split(e, t)) {
            return firstOfType(t[0]);
        }
        t[0] = Expr.typeOf(e);
        if (!ArrayType.split(t[0], index, elem)) {
            return null;
        }
        return firstOfType(t[0]);
    }

    private static Expr.T firstOfType(Type.T t_) {
        Type.T[] t = {t_};
        int[] min = TInt.newInt();
        int[] max = TInt.newInt();
        Type.T[] elem = new Type.T[1];
        Type.T t_base;
        if (ArrayType.split(t[0], t, elem) && (t[0] == null)) {
            return IntegerExpr.new_(Int.T, TInt.Zero);
        }
        t_base = Type.base(t[0]);
        if (Type.getBounds(t[0], min, max)) {
            if (t_base == Int.T || t_base == LInt.T) {
                return IntegerExpr.new_(t_base, min);
            } else {
                return EnumExpr.new_(t[0], min);
            }
        } else if (t_base == Reel.T) {
            return ReelExpr.new_(Target.Real.min);
        } else if (t_base == LReel.T) {
            return ReelExpr.new_(M3.target.Longreal.min);
        } else if (t_base == EReel.T) {
            return ReelExpr.new_(M3.target.Extended.min);
        } else {
            return null;
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 1, true, false, false, null,
                                   First::typeOf,
                                   First::typeOf,
                                   CallExpr::notAddressable,
                                   First::check,
                                   First::prep,
                                   First::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::prepNoBranch,
                                   CallExpr::noBranch,
                                   First::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("FIRST", Z, true);
    }
}
