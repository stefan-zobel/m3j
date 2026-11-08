// Java port of CM3's m3-sys/m3front/src/builtinOps/Number.i3 and Number.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;

// File: Number.m3
// Last Modified On Tue May  3 16:32:23 PDT 1994 By kalsow
//      Modified On Fri Dec 21 01:16:23 1990 By muller

public final class Number {
    private Number() {}

    private static CallExpr.MethodList Z;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
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
                    Error.msg("NUMBER: argument cannot be an open array type");
                    index[0] = Int.T;
                }
            } else {
                index[0] = t[0];
            }
        } else {
            Error.msg("NUMBER: argument must be a type or array");
            index[0] = Int.T;
        }
        if (EnumType.is(index[0])) {
            if (!TInt.LT(TInt.Zero, Type.number(index[0]))) {
                Error.msg("NUMBER: empty enumeration type");
            }
        } else if (Type.isOrdinal(index[0])) {
            // ordinal type => OK
        } else {
            Error.msg("NUMBER: argument must be an ordinal type, array type or array");
        }
        ce.type = Card.T;
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
        int[] tmp = TInt.newInt();
        int[] num = TInt.newInt();
        if (!TypeExpr.split(e, t)) {
            t[0] = Expr.typeOf(e);
        }
        if (ArrayType.split(t[0], index, element)) {
            t[0] = index[0];
        }

        if ((t[0] == null)) { // open array
            Expr.compile(e);
            CG.open_size(0);
        } else {
            Type.getBounds(t[0], min, max);
            if (TInt.LT(max, min)) {
                CG.load_integer(M3.target.Integer.cg_type, TInt.Zero);
            } else if (TInt.subtract(max, min, tmp)
                       && TInt.add(tmp, TInt.One, num)
                       && !TInt.LT(M3.target.Integer.max, num)) {
                CG.load_integer(M3.target.Integer.cg_type, num);
            } else {
                Error.warn(2, "result of NUMBER too large");
                CG.load_integer(M3.target.Integer.cg_type, M3.target.Integer.max);
                CG.check_hi(M3.target.Integer.cg_type, TInt.Zero,
                            M3CG.RuntimeError.ValueOutOfRange);
            }
        }
    }

    private static Expr.T fold(CallExpr.T ce) {
        int[] min = TInt.newInt();
        int[] max = TInt.newInt();
        int[] tmp = TInt.newInt();
        int[] num = TInt.newInt();
        Type.T[] t = new Type.T[1];
        Type.T[] index = new Type.T[1];
        Type.T[] elem = new Type.T[1];
        Expr.T e;
        e = ce.args[0];
        if (!TypeExpr.split(e, t)) {
            t[0] = Expr.typeOf(e);
            if (!ArrayType.split(t[0], index, elem)) {
                return null;
            }
            if ((index[0] == null)) {
                // NUMBER (open array value) => try for constant open array
                e = Expr.constValue(e);
                if ((e == null)) {
                    return null;
                }
                if (ArrayExpr.getBounds(e, min, max)
                    && TInt.subtract(max, min, tmp)
                    && TInt.add(tmp, TInt.One, num)
                    && !TInt.LT(num, M3.target.Integer.min)
                    && !TInt.LT(M3.target.Integer.max, num)) {
                    return IntegerExpr.new_(Int.T, num);
                } else {
                    return null;
                }
            }
        }
        if (ArrayType.split(t[0], t, elem) && (t[0] == null)) {
            return null;
        }
        if (!Type.getBounds(t[0], min, max)) {
            return null;
        }

        if (TInt.LT(max, min)) {
            return IntegerExpr.new_(Int.T, TInt.Zero);
        } else if (TInt.subtract(max, min, tmp)
                   && TInt.add(tmp, TInt.One, num)
                   && !TInt.LT(M3.target.Integer.max, num)) {
            return IntegerExpr.new_(Int.T, num);
        } else {
            return null;
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 1, true, false, false, Card.T,
                                   null, null,
                                   CallExpr::notAddressable,
                                   Number::check,
                                   Number::prep,
                                   Number::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   Number::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("NUMBER", Z, true);
    }
}
