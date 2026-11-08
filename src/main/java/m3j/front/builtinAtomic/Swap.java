// Java port of CM3's m3-sys/m3front/src/builtinAtomic/Swap.ig and Swap.mg.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front.builtinAtomic;

import java.util.function.Supplier;

import m3j.front.CG;
import m3j.front.CallExpr;
import m3j.front.EnumExpr;
import m3j.front.Error;
import m3j.front.Expr;
import m3j.front.Formal;
import m3j.front.M3;
import m3j.front.ProcType;
import m3j.front.Procedure;
import m3j.front.Type;
import m3j.front.Value;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.TInt;

// File: Swap.ig
// Last Modified On Thu Aug 20 15:15:16 PDT 1992 By kalsow

/**
 * The generic module Swap; an instance is one instantiation.
 *
 * <p>Port: the generic parameters are the constructor parameters. Rep is
 * the variable Rep.T of the instantiating interface and Atomic the
 * instance of AtomicModule (the instances refer to each other), both read
 * when they are needed. The module variables are instance fields.
 */
public final class Swap {
    private final Supplier<Type.T> Rep;
    private final Supplier<AtomicModule> Atomic;

    public Swap(Supplier<Type.T> Rep, Supplier<AtomicModule> Atomic) {
        this.Rep = Rep;
        this.Atomic = Atomic;
    }

    // File: Swap.mg

    private CallExpr.MethodList Z;
    private Value.T formals;

    private void check(CallExpr.T ce, M3.CheckState cs) {
        int[] order = TInt.newInt();
        Type.T[] t = new Type.T[1];
        long[] z = new long[1];
        // Port: args is a VAR parameter of CheckArgs.
        Expr.T[][] args = {ce.args};
        Formal.checkArgs(cs, args, formals, ce.proc);
        ce.args = args[0];
        if (EnumExpr.split(ce.args[2], order, t) && TInt.toInt(order, z)) {
            if (z[0] == M3CG.MemoryOrder.Sequential.ordinal()) {
                // ok
            } else {
                // Sic: the message names Store.
                Error.warn(0, "Store currently only supports Order.Sequential");
            }
        } else {
            Error.msg("order must be an enumeration constant");
        }
        ce.type = Rep.get();
    }

    private void prep(CallExpr.T ce) {
        Expr.prepLValue(ce.args[0], true);
        Expr.prep(ce.args[1]);
    }

    private void compile(CallExpr.T ce) {
        int[] order = TInt.newInt();
        long[] z = new long[1];
        Type.T[] t = new Type.T[1];
        Expr.compileAddress(ce.args[0], true);
        Expr.compile(ce.args[1]);
        EnumExpr.split(ce.args[2], order, t);
        TInt.toInt(order, z);
        CG.exchange(Type.cgType(Rep.get(), true),
                    M3CG.MemoryOrder.values()[(int) z[0]]);
        Expr.noteWrite(ce.args[0]);
    }

    public void initialize() {
        Formal.Info var = new Formal.Info();
        var.name = M3ID.add(M3.ids, "var");
        var.mode = Formal.Mode.mVAR;
        var.offset = 0;
        var.type = Atomic.get().T;
        var.dfault = null;
        var.unused = false;
        var.trace = null;
        Value.T val = Formal.newBuiltin("val", 1, Rep.get());
        Formal.Info order = new Formal.Info();
        order.name = M3ID.add(M3.ids, "order");
        order.mode = Formal.Mode.mVALUE;
        order.offset = 2;
        order.type = Atomic.get().Order;
        order.dfault = Atomic.get().Sequential;
        order.unused = false;
        order.trace = null;
        Type.T t0 = ProcType.new_(Rep.get(), Formal.new_(var), val, Formal.new_(order));
        Z = CallExpr.newMethodList(2, 3, false, true, true, Rep.get(),
                                   null, null,
                                   CallExpr::notAddressable,
                                   this::check,
                                   this::prep,
                                   this::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   CallExpr::noValue,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("Swap", Z, false, t0);
        formals = ProcType.formals(t0);
    }
}
