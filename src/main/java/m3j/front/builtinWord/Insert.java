// Java port of CM3's m3-sys/m3front/src/builtinWord/Insert.ig and Insert.mg.
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
 * Port: the generic module Insert (Rep). An instance of this class is an
 * instantiation: the generic parameter Rep is the constructor parameter,
 * the module variables are instance fields.
 */
public final class Insert {
    /** Port: the generic parameter Rep; {@code Rep.get()} is Rep.T. */
    private final Supplier<Type.T> Rep;

    private CallExpr.MethodList Z;
    private Value.T formals;
    private long rep;

    public Insert(Supplier<Type.T> Rep) {
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
        CG.Val t2, t3;
        int[] max = TInt.newInt();
        boolean b, x2, x3;
        long[] i2 = new long[1], i3 = new long[1];

        x2 = getBitIndex(ce.args[2], i2);
        x3 = getBitIndex(ce.args[3], i3);

        if (x2 && x3) {
            // we can use the insert_mn operator
            if ((i2[0] + i3[0] > M3.targetMap.Word_types[(int) rep].size)) {
                Error.warn(2, "Word.Insert: i+n value out of range");
                CG.load_integer(M3.target.Integer.cg_type, TInt.One);
                CG.check_hi(M3.target.Integer.cg_type, TInt.Zero,
                            M3CG.RuntimeError.ValueOutOfRange);
            } else {
                Expr.compile(ce.args[0]);
                Expr.compile(ce.args[1]);
                CG.insert_mn(M3.targetMap.Word_types[(int) rep].cg_type, i2[0], i3[0]);
            }

        } else if (x3) {
            // we can use the insert_n operator
            b = TInt.fromInt(M3.targetMap.Word_types[(int) rep].size - i3[0], max);
            AssertError.check(b, "Insert");
            Expr.compile(ce.args[0]);
            CG.forceStacked();
            Expr.compile(ce.args[1]);
            CheckExpr.emitChecks(ce.args[2], TInt.Zero, max,
                                 M3CG.RuntimeError.ValueOutOfRange);
            CG.insert_n(M3.targetMap.Word_types[(int) rep].cg_type, i3[0]);

        } else if (x2) {
            // we need the general purpose insert operator, but can simplify
            // the range checking code
            b = TInt.fromInt(M3.targetMap.Word_types[(int) rep].size - i2[0], max);
            AssertError.check(b, "Insert");
            Expr.compile(ce.args[0]);
            CG.forceStacked();
            Expr.compile(ce.args[1]);
            CG.forceStacked();
            CG.load_intt(i2[0]);
            CheckExpr.emitChecks(ce.args[3], TInt.Zero, max,
                                 M3CG.RuntimeError.ValueOutOfRange);
            CG.insert(M3.targetMap.Word_types[(int) rep].cg_type);

        } else {
            // we need the general purpose insert operator
            CheckExpr.emitChecks(ce.args[2], TInt.Zero, M3.target.Integer.max,
                                 M3CG.RuntimeError.ValueOutOfRange);
            t2 = CG.pop();
            CheckExpr.emitChecks(ce.args[3], TInt.Zero, M3.target.Integer.max,
                                 M3CG.RuntimeError.ValueOutOfRange);
            t3 = CG.pop();
            if (Host.doRangeChk) {
                b = TInt.fromInt(M3.targetMap.Word_types[(int) rep].size, max);
                AssertError.check(b, "Insert");
                CG.push(t2);
                CG.push(t3);
                CG.add(M3.target.Integer.cg_type);
                CG.check_hi(M3.target.Integer.cg_type, max,
                            M3CG.RuntimeError.ValueOutOfRange);
                CG.discard(M3.target.Integer.cg_type);
            }
            Expr.compile(ce.args[0]);
            CG.forceStacked();
            Expr.compile(ce.args[1]);
            CG.forceStacked();
            CG.push(t2);
            CG.push(t3);
            CG.insert(M3.targetMap.Word_types[(int) rep].cg_type);
            CG.free(t2);
            CG.free(t3);
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
        Expr.T e0, e1;
        int[] w0 = TInt.newInt(), w1 = TInt.newInt(), result = TInt.newInt();
        long[] i2 = new long[1], i3 = new long[1];
        Type.T[] t = new Type.T[1];
        e0 = Expr.constValue(ce.args[0]);
        e1 = Expr.constValue(ce.args[1]);
        if ((e0 == null) || (e1 == null)
            || !IntegerExpr.split(e0, w0, t)
            || !IntegerExpr.split(e1, w1, t)
            || !getBitIndex(ce.args[2], i2)
            || !getBitIndex(ce.args[3], i3)
            || i2[0] + i3[0] > M3.targetMap.Word_types[(int) rep].size
            || !TWord.insert(w0, w1, i2[0], i3[0], result)) {
            return null;
        }
        TInt.extend(result, (int) M3.targetMap.Word_types[(int) rep].bytes, result);
        return IntegerExpr.new_(T(), result);
    }

    public void initialize(long r) {
        Value.T f0 = Formal.newBuiltin("x", 0, T());
        Value.T f1 = Formal.newBuiltin("y", 1, T());
        Value.T f2 = Formal.newBuiltin("i", 2, Card.T);
        Value.T f3 = Formal.newBuiltin("n", 3, Card.T);
        Type.T t = ProcType.new_(T(), f0, f1, f2, f3);
        rep = r;
        Z = CallExpr.newMethodList(4, 4, true, true, true, T(),
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
        Procedure.definePredefined("Insert", Z, false, t, /* assignable := */ true);
        formals = ProcType.formals(t);
    }
}
