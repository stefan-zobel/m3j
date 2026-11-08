// Java port of CM3's m3-sys/m3front/src/builtinWord/Extract.ig and Extract.mg.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front.builtinWord;

import java.util.function.Supplier;

import m3j.front.CG;
import m3j.front.CallExpr;
import m3j.front.Card;
import m3j.front.CheckExpr;
import m3j.front.Error;
import m3j.front.Expr;
import m3j.front.Formal;
import m3j.front.Host;
import m3j.front.IntegerExpr;
import m3j.front.M3;
import m3j.front.ProcType;
import m3j.front.Procedure;
import m3j.front.Type;
import m3j.front.Value;
import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.TWord;
import m3j.util.AssertError;

/**
 * Port: the generic module Extract (Rep). An instance of this class is an
 * instantiation: the generic parameter Rep is the constructor parameter,
 * the module variables are instance fields.
 */
public final class Extract {
    /** Port: the generic parameter Rep; {@code Rep.get()} is Rep.T. */
    private final Supplier<Type.T> Rep;

    private CallExpr.MethodList Z;
    private Value.T formals;
    private long rep;

    public Extract(Supplier<Type.T> Rep) {
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
        CG.Val t1, t2;
        int[] max = TInt.newInt();
        boolean b, x1, x2;
        long[] i1 = new long[1], i2 = new long[1];

        x1 = getBitIndex(ce.args[1], i1);
        x2 = getBitIndex(ce.args[2], i2);

        if (x1 && x2) {
            // we can use the extract_mn operator
            if ((i1[0] + i2[0] > M3.targetMap.Word_types[(int) rep].size)) {
                Error.warn(2, "Word.Extract: i+n value out of range");
                CG.load_integer(M3.target.Integer.cg_type, TInt.One);
                CG.check_hi(M3.target.Integer.cg_type, TInt.Zero,
                            M3CG.RuntimeError.ValueOutOfRange);
            } else {
                Expr.compile(ce.args[0]);
                CG.extract_mn(M3.targetMap.Word_types[(int) rep].cg_type, false, i1[0], i2[0]);
            }

        } else if (x2) {
            // we can use the extract_n operator
            b = TInt.fromInt(M3.targetMap.Word_types[(int) rep].size - i2[0], max);
            AssertError.check(b, "Extract");
            Expr.compile(ce.args[0]);
            CheckExpr.emitChecks(ce.args[1], TInt.Zero, max,
                                 M3CG.RuntimeError.ValueOutOfRange);
            CG.extract_n(M3.targetMap.Word_types[(int) rep].cg_type, false, i2[0]);

        } else if (x1) {
            // we need the general purpose extract operator, but can simplify
            // the range checking code
            b = TInt.fromInt(M3.targetMap.Word_types[(int) rep].size - i1[0], max);
            AssertError.check(b, "Extract");
            Expr.compile(ce.args[0]);
            CG.forceStacked();
            CG.load_intt(i1[0]);
            CheckExpr.emitChecks(ce.args[2], TInt.Zero, max,
                                 M3CG.RuntimeError.ValueOutOfRange);
            CG.extract(M3.targetMap.Word_types[(int) rep].cg_type, /* sign := */ false);

        } else {
            // we need the general purpose extract operator
            CheckExpr.emitChecks(ce.args[1], TInt.Zero, M3.target.Integer.max,
                                 M3CG.RuntimeError.ValueOutOfRange);
            t1 = CG.pop();
            CheckExpr.emitChecks(ce.args[2], TInt.Zero, M3.target.Integer.max,
                                 M3CG.RuntimeError.ValueOutOfRange);
            t2 = CG.pop();
            if (Host.doRangeChk) {
                b = TInt.fromInt(M3.targetMap.Word_types[(int) rep].size, max);
                AssertError.check(b, "Extract");
                CG.push(t1);
                CG.push(t2);
                CG.add(M3.target.Integer.cg_type);
                CG.check_hi(M3.target.Integer.cg_type, max,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
            }
            Expr.compile(ce.args[0]);
            CG.forceStacked();
            CG.push(t1);
            CG.push(t2);
            CG.extract(M3.targetMap.Word_types[(int) rep].cg_type, /* sign := */ false);
            CG.free(t1);
            CG.free(t2);
        }
    }

    private boolean getBitIndex(Expr.T e, long[] i) {
        e = Expr.constValue(e);
        if ((e == null)) {
            return false;
        }
        return IntegerExpr.toInt(e, i)
            && (0 <= i[0]) && (i[0] <= M3.targetMap.Word_types[(int) rep].size);
    }

    private Expr.T fold(CallExpr.T ce) {
        Expr.T e0;
        int[] w0 = TInt.newInt(), result = TInt.newInt();
        long[] i1 = new long[1], i2 = new long[1];
        Type.T[] t = new Type.T[1];
        e0 = Expr.constValue(ce.args[0]);
        if ((e0 == null)
            || !IntegerExpr.split(e0, w0, t)
            || !getBitIndex(ce.args[1], i1)
            || !getBitIndex(ce.args[2], i2)
            || i1[0] + i2[0] > M3.targetMap.Word_types[(int) rep].size
            || !TWord.extract(w0, i1[0], i2[0], result)) {
            return null;
        }
        TInt.extend(result, (int) M3.targetMap.Word_types[(int) rep].bytes, result);
        return IntegerExpr.new_(T(), result);
    }

    private void getBounds(CallExpr.T ce, int[] min, int[] max) {
        int[] min_bits = TInt.newInt(), max_bits = TInt.newInt();
        long[] i = new long[1];
        Expr.getBounds(ce.args[2], min_bits, max_bits);
        if (TInt.toInt(max_bits, i) && 0 <= i[0]
            && i[0] < M3.targetMap.Word_types[(int) rep].size) {
            if (!TWord.extract(TInt.MOne, 0, i[0], max)) {
                Type.getBounds(T(), min, max);
            }
            TInt.assign(min, TInt.Zero);
        } else {
            // possible that we'll preserve all bits
            Expr.getBounds(ce.args[0], min, max);
        }
    }

    public void initialize(long r) {
        Value.T f0 = Formal.newBuiltin("x", 0, T());
        Value.T f1 = Formal.newBuiltin("i", 1, Card.T);
        Value.T f2 = Formal.newBuiltin("n", 2, Card.T);
        Type.T t = ProcType.new_(T(), f0, f1, f2);
        rep = r;
        Z = CallExpr.newMethodList(3, 3, true, true, true, T(),
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
                                   this::getBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("Extract", Z, false, t, /* assignable := */ true);
        formals = ProcType.formals(t);
    }
}
