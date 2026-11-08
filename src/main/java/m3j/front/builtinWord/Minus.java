// Java port of CM3's m3-sys/m3front/src/builtinWord/Minus.ig and Minus.mg.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front.builtinWord;

import java.util.function.Supplier;

import m3j.front.CG;
import m3j.front.CallExpr;
import m3j.front.Expr;
import m3j.front.Formal;
import m3j.front.IntegerExpr;
import m3j.front.M3;
import m3j.front.ProcType;
import m3j.front.Procedure;
import m3j.front.Type;
import m3j.front.Value;
import m3j.middle.TInt;
import m3j.middle.TWord;

/**
 * Port: the generic module Minus (Rep). An instance of this class is an
 * instantiation: the generic parameter Rep is the constructor parameter,
 * the module variables are instance fields.
 */
public final class Minus {
    /** Port: the generic parameter Rep; {@code Rep.get()} is Rep.T. */
    private final Supplier<Type.T> Rep;

    private CallExpr.MethodList Z;
    private Value.T formals;
    private long rep;

    public Minus(Supplier<Type.T> Rep) {
        this.Rep = Rep;
    }

    /** Port: Rep.T, read when it is used (Rep's Initialize sets it). */
    private Type.T T() {
        return Rep.get();
    }

    private void check(CallExpr.T ce, M3.CheckState cs) {
        // Port: ce.args is the VAR parameter actuals of Formal.CheckArgs.
        Expr.T[][] actuals = {ce.args};
        Formal.checkArgs(cs, actuals, formals, ce.proc);
        ce.args = actuals[0];
        ce.type = T();
    }

    private void compile(CallExpr.T ce) {
        Expr.compile(ce.args[0]);
        Expr.compile(ce.args[1]);
        CG.subtract(M3.targetMap.Word_types[(int) rep].cg_type);
    }

    private Expr.T fold(CallExpr.T ce) {
        int[] w0 = TInt.newInt(), w1 = TInt.newInt(), result = TInt.newInt();
        if (getArgs(ce.args, w0, w1)) {
            TWord.subtract(w0, w1, result);
            TInt.extend(result, (int) M3.targetMap.Word_types[(int) rep].bytes, result);
            return IntegerExpr.new_(T(), result);
        }
        return null;
    }

    private boolean getArgs(Expr.T[] args, int[] w0, int[] w1) {
        Expr.T e0, e1;
        Type.T[] t = new Type.T[1];
        e0 = Expr.constValue(args[0]);
        e1 = Expr.constValue(args[1]);
        return (e0 != null) && IntegerExpr.split(e0, w0, t)
            && (e1 != null) && IntegerExpr.split(e1, w1, t);
    }

    public void initialize(long r) {
        Value.T x0 = Formal.newBuiltin("x", 0, T());
        Value.T y0 = Formal.newBuiltin("y", 1, T());
        Type.T t0 = ProcType.new_(T(), x0, y0);
        rep = r;
        Z = CallExpr.newMethodList(2, 2, true, true, true, T(),
                                   null, null,
                                   CallExpr::notAddressable,
                                   this::check,
                                   CallExpr::prepArgs,
                                   this::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   this::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("Minus", Z, false, t0, /* assignable := */ true);
        formals = ProcType.formals(t0);
    }
}
