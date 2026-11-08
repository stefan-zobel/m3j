// Java port of CM3's m3-sys/m3front/src/builtinOps/Dec.i3 and Dec.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: Dec.m3
// Last Modified On Tue May 23 15:31:47 PDT 1995 By kalsow
//      Modified On Tue Apr  2 03:46:13 1991 By muller

public final class Dec {
    private Dec() {}

    private static CallExpr.MethodList Z;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        doCheck("DEC", ce, cs);
    }

    public static void doCheck(String name, CallExpr.T ce, M3.CheckState cs) {
        Type.T t;
        Expr.T e;
        int[] nm = new int[1];
        e = ce.args[0];
        t = Type.base(Expr.typeOf(e));
        if (!Type.isOrdinal(t)) {
            if (Type.isSubtype(t, Addr.T)) {
                if (Module.isSafe()) {
                    Error.txt(name, "unsafe operation");
                }
            } else {
                Error.txt(name, "first argument must be of an ordinal type");
            }
        }
        if ((!Expr.isDesignator(e))) {
            Error.txt(name, "first argument must be a variable");
        } else if ((!Expr.isWritable(e, /* traced := */ false))) {
            Error.txt(name, "first argument must be writable");
        } else if (NamedExpr.splitName(e, nm)) {
            // simple scalar => we don't need an explicit address
            //    -- demanded by Eric Veach 9/17/93
        } else {
            Expr.needsAddress(e);
        }
        if ((ce.args.length > 1)) {
            if (Type.isSubtype(t, LInt.T)) {
                t = Type.base(Expr.typeOf(ce.args[1]));
                if ((t != LInt.T)) {
                    Error.txt(name, "second argument must be a LONGINT");
                }
            } else {
                t = Type.base(Expr.typeOf(ce.args[1]));
                if ((t != Int.T)) {
                    Error.txt(name, "second argument must be an INTEGER");
                }
            }
        }
        ce.type = null;
    }

    private static void prep(CallExpr.T ce) {
        Expr.prepLValue(ce.args[0], /* traced := */ false);
        if ((ce.args.length > 1)) {
            Expr.prep(ce.args[1]);
        }
    }

    private static void compile(CallExpr.T ce) {
        Expr.T lhs = ce.args[0];
        Type.T tlhs = Expr.typeOf(lhs);
        Type.Info info = new Type.Info();
        Expr.T dec;
        int check = 0;
        CG.Val lvalue;
        int[] tmin = TInt.newInt();
        int[] tmax = TInt.newInt();
        int[] bmin = TInt.newInt();
        int[] bmax = TInt.newInt();
        CGType cg_type;
        tlhs = Type.checkInfo(tlhs, info);
        if (Type.isSubtype(tlhs, LInt.T)) {
            tlhs = LInt.T;
            cg_type = M3.target.Longint.cg_type;
        } else {
            tlhs = Int.T;
            cg_type = M3.target.Integer.cg_type;
        }
        Type.getBounds(tlhs, tmin, tmax);
        if ((ce.args.length > 1)) {
            dec = ce.args[1];
        } else {
            dec = IntegerExpr.new_(tlhs, TInt.One);
            Expr.prep(dec);
        }
        Expr.getBounds(lhs, bmin, bmax);

        if (Host.doRangeChk) {
            if (TInt.LT(tmin, bmin)) {
                check++;
            }
            if (TInt.LT(bmax, tmax)) {
                check += 2;
            }
        }

        Expr.compileLValue(lhs, /* traced := */ false);
        lvalue = CG.pop();
        CG.push(lvalue);

        CG.push(lvalue);
        CG.load_indirect(info.stk_type, 0, info.size, info.alignment);
        Expr.compile(dec);

        if ((info.stk_type == CGType.Addr)) {
            CG.index_bytes(-Target.Byte);
            check = 0;
        } else {
            CG.subtract(cg_type);
        }

        switch (check) {
            case 0 -> { } // no range checking
            case 1 -> CG.check_lo(cg_type, bmin, M3CG.RuntimeError.ValueOutOfRange);
            case 2 -> CG.check_hi(cg_type, bmax, M3CG.RuntimeError.ValueOutOfRange);
            case 3 -> CG.check_range(cg_type, bmin, bmax,
                                     M3CG.RuntimeError.ValueOutOfRange);
            default -> throw new AssertError("Dec");
        }

        CG.store_indirect(info.stk_type, 0, info.size);
        CG.free(lvalue);
        Expr.noteWrite(lhs);
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 2, false, false, true, null,
                                   null, null,
                                   CallExpr::notAddressable,
                                   Dec::check,
                                   Dec::prep,
                                   Dec::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   CallExpr::noValue,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("DEC", Z, true);
    }
}
