// Java port of CM3's m3-sys/m3front/src/builtinWord/Rotate.ig and Rotate.mg.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front.builtinWord;

import java.util.function.Supplier;

import m3j.front.CG;
import m3j.front.CallExpr;
import m3j.front.CheckExpr;
import m3j.front.Expr;
import m3j.front.Formal;
import m3j.front.Int;
import m3j.front.IntegerExpr;
import m3j.front.M3;
import m3j.front.ProcType;
import m3j.front.Procedure;
import m3j.front.SubrangeType;
import m3j.front.Type;
import m3j.front.Value;
import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.TWord;
import m3j.util.AssertError;

/**
 * Port: the generic module Rotate (Rep). An instance of this class is an
 * instantiation: the generic parameter Rep is the constructor parameter,
 * the module variables are instance fields.
 */
public final class Rotate {
    /** Port: the generic parameter Rep; {@code Rep.get()} is Rep.T. */
    private final Supplier<Type.T> Rep;

    private CallExpr.MethodList Z, ZL, ZR;
    private Value.T formals, formalsL, formalsR;
    private long rep;
    private final int[] max = TInt.newInt();

    public Rotate(Supplier<Type.T> Rep) {
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

    private void checkL(CallExpr.T ce, M3.CheckState cs) {
        // Port: ce.args is the VAR parameter actuals of Formal.CheckArgs.
        Expr.T[][] actuals = {ce.args};
        Formal.checkArgs(cs, actuals, formalsL, ce.proc);
        ce.args = actuals[0];
        ce.type = T();
    }

    private void checkR(CallExpr.T ce, M3.CheckState cs) {
        // Port: ce.args is the VAR parameter actuals of Formal.CheckArgs.
        Expr.T[][] actuals = {ce.args};
        Formal.checkArgs(cs, actuals, formalsR, ce.proc);
        ce.args = actuals[0];
        ce.type = T();
    }

    private void compile(CallExpr.T ce) {
        Expr.compile(ce.args[0]);
        Expr.compile(ce.args[1]);
        CG.rotate(M3.targetMap.Word_types[(int) rep].cg_type);
    }

    private void compileL(CallExpr.T ce) {
        Expr.compile(ce.args[0]);
        CheckExpr.emitChecks(ce.args[1], TInt.Zero, max,
                             M3CG.RuntimeError.ValueOutOfRange);
        CG.rotate_left(M3.targetMap.Word_types[(int) rep].cg_type);
    }

    private void compileR(CallExpr.T ce) {
        Expr.compile(ce.args[0]);
        CheckExpr.emitChecks(ce.args[1], TInt.Zero, max,
                             M3CG.RuntimeError.ValueOutOfRange);
        CG.rotate_right(M3.targetMap.Word_types[(int) rep].cg_type);
    }

    private Expr.T fold(CallExpr.T ce) {
        Expr.T e0, e1;
        int[] w0 = TInt.newInt(), result = TInt.newInt();
        long[] i1 = new long[1];
        Type.T[] t = new Type.T[1];
        e0 = Expr.constValue(ce.args[0]);
        e1 = Expr.constValue(ce.args[1]);
        if ((e0 != null) && IntegerExpr.split(e0, w0, t)
            && (e1 != null) && IntegerExpr.toInt(e1, i1)) {
            TWord.and(w0, M3.targetMap.Word_types[(int) rep].max, w0);
            TWord.rotate(w0, i1[0], M3.targetMap.Word_types[(int) rep].bytes, result);
            TInt.extend(result, (int) M3.targetMap.Word_types[(int) rep].bytes, result);
            return IntegerExpr.new_(T(), result);
        }
        return null;
    }

    private Expr.T foldL(CallExpr.T ce) {
        Expr.T e0, e1;
        int[] w0 = TInt.newInt(), result = TInt.newInt();
        long[] i1 = new long[1];
        Type.T[] t = new Type.T[1];
        e0 = Expr.constValue(ce.args[0]);
        e1 = Expr.constValue(ce.args[1]);
        if ((e0 != null) && IntegerExpr.split(e0, w0, t)
            && (e1 != null) && IntegerExpr.toInt(e1, i1)
            && 0 <= i1[0] && i1[0] < M3.targetMap.Word_types[(int) rep].size) {
            TWord.and(w0, M3.targetMap.Word_types[(int) rep].max, w0);
            TWord.rotate(w0, i1[0], M3.targetMap.Word_types[(int) rep].bytes, result);
            TInt.extend(result, (int) M3.targetMap.Word_types[(int) rep].bytes, result);
            return IntegerExpr.new_(T(), result);
        }
        return null;
    }

    private Expr.T foldR(CallExpr.T ce) {
        Expr.T e0, e1;
        int[] w0 = TInt.newInt(), result = TInt.newInt();
        long[] i1 = new long[1];
        Type.T[] t = new Type.T[1];
        e0 = Expr.constValue(ce.args[0]);
        e1 = Expr.constValue(ce.args[1]);
        if ((e0 != null) && IntegerExpr.split(e0, w0, t)
            && (e1 != null) && IntegerExpr.toInt(e1, i1)
            && 0 <= i1[0] && i1[0] < M3.targetMap.Word_types[(int) rep].size) {
            TWord.and(w0, M3.targetMap.Word_types[(int) rep].max, w0);
            TWord.rotate(w0, -i1[0], M3.targetMap.Word_types[(int) rep].bytes, result);
            TInt.extend(result, (int) M3.targetMap.Word_types[(int) rep].bytes, result);
            return IntegerExpr.new_(T(), result);
        }
        return null;
    }

    public void initialize(long r) {
        boolean b = TInt.fromInt(M3.targetMap.Word_types[(int) r].size - 1, max);
        Type.T sub = SubrangeType.new_(TInt.Zero, max, Int.T, false);

        Value.T f0 = Formal.newBuiltin("x", 0, T());
        Value.T f1 = Formal.newBuiltin("n", 1, Int.T);
        Type.T t = ProcType.new_(T(), f0, f1);

        Value.T Lf0 = Formal.newBuiltin("x", 0, T());
        Value.T Lf1 = Formal.newBuiltin("n", 1, sub);
        Type.T Lt = ProcType.new_(T(), Lf0, Lf1);

        Value.T Rf0 = Formal.newBuiltin("x", 0, T());
        Value.T Rf1 = Formal.newBuiltin("n", 1, sub);
        Type.T Rt = ProcType.new_(T(), Rf0, Rf1);

        rep = r;
        AssertError.check(b, "Rotate");
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
        Procedure.definePredefined("Rotate", Z, false, t, /* assignable := */ true);
        formals = ProcType.formals(t);

        ZL = CallExpr.newMethodList(2, 2, true, true, true, T(),
                                    null, null,
                                    CallExpr::notAddressable,
                                    this::checkL,
                                    CallExpr::prepArgs,
                                    this::compileL,
                                    CallExpr::noLValue,
                                    CallExpr::noLValue,
                                    CallExpr::notBoolean,
                                    CallExpr::notBoolean,
                                    this::foldL,
                                    CallExpr::noBounds,
                                    CallExpr::isNever, // writable
                                    CallExpr::isNever, // designator
                                    CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("LeftRotate", ZL, false, Lt, /* assignable := */ true);
        formalsL = ProcType.formals(Lt);

        ZR = CallExpr.newMethodList(2, 2, true, true, true, T(),
                                    null, null,
                                    CallExpr::notAddressable,
                                    this::checkR,
                                    CallExpr::prepArgs,
                                    this::compileR,
                                    CallExpr::noLValue,
                                    CallExpr::noLValue,
                                    CallExpr::notBoolean,
                                    CallExpr::notBoolean,
                                    this::foldR,
                                    CallExpr::noBounds,
                                    CallExpr::isNever, // writable
                                    CallExpr::isNever, // designator
                                    CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("RightRotate", ZR, false, Rt, /* assignable := */ true);
        formalsR = ProcType.formals(Rt);
    }
}
