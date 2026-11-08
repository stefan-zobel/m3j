// Java port of CM3's m3-sys/m3front/src/builtinOps/Inc.i3 and Inc.m3.
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

// File: Inc.m3
// Last Modified On Tue May 23 15:31:58 PDT 1995 By kalsow
//      Modified On Tue Apr  2 03:47:06 1991 By muller

public final class Inc {
    private Inc() {}

    private static CallExpr.MethodList Z;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Dec.doCheck("INC", ce, cs);
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
        Expr.T inc;
        int check = 0;
        CG.Val lvalue;
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
        if ((ce.args.length > 1)) {
            inc = ce.args[1];
        } else if (tlhs == LInt.T) {
            inc = IntegerExpr.new_(LInt.T, TInt.One);
            Expr.prep(inc);
        } else {
            inc = IntegerExpr.new_(Int.T, TInt.One);
            Expr.prep(inc);
        }
        Expr.getBounds(lhs, bmin, bmax);

        if (Host.doRangeChk) {
            if (tlhs == LInt.T) {
                if (TInt.LT(M3.target.Longint.min, bmin)) {
                    check++;
                }
                if (TInt.LT(bmax, M3.target.Longint.max)) {
                    check += 2;
                }
            } else {
                if (TInt.LT(M3.target.Integer.min, bmin)) {
                    check++;
                }
                if (TInt.LT(bmax, M3.target.Integer.max)) {
                    check += 2;
                }
            }
        }

        Expr.compileLValue(lhs, /* traced := */ false);
        lvalue = CG.pop();
        CG.push(lvalue);

        CG.push(lvalue);
        CG.load_indirect(info.stk_type, 0, info.size, info.alignment);
        Expr.compile(inc);

        if ((info.stk_type == CGType.Addr)) {
            CG.index_bytes(Target.Byte);
            check = 0;
        } else {
            CG.add(cg_type);
        }

        switch (check) {
            case 0 -> { } // no range checking
            case 1 -> CG.check_lo(cg_type, bmin, M3CG.RuntimeError.ValueOutOfRange);
            case 2 -> CG.check_hi(cg_type, bmax, M3CG.RuntimeError.ValueOutOfRange);
            case 3 -> CG.check_range(cg_type, bmin, bmax,
                                     M3CG.RuntimeError.ValueOutOfRange);
            default -> throw new AssertError("Inc");
        }

        CG.store_indirect(info.stk_type, 0, info.size);
        CG.free(lvalue);
        Expr.noteWrite(lhs);
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 2, false, false, true, null,
                                   null, null,
                                   CallExpr::notAddressable,
                                   Inc::check,
                                   Inc::prep,
                                   Inc::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   CallExpr::noValue,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("INC", Z, true);
    }
}
