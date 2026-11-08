// Java port of CM3's m3-sys/m3front/src/builtinAtomic/FetchOr.ig and
// FetchOr.mg.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front.builtinAtomic;

import java.util.function.Supplier;

import m3j.front.Addr;
import m3j.front.CG;
import m3j.front.CallExpr;
import m3j.front.EnumExpr;
import m3j.front.Error;
import m3j.front.Expr;
import m3j.front.Formal;
import m3j.front.M3;
import m3j.front.Module;
import m3j.front.ProcType;
import m3j.front.Procedure;
import m3j.front.Type;
import m3j.front.Value;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.TInt;

// File: FetchOr.ig
// Last Modified On Thu Aug 20 15:15:16 PDT 1992 By kalsow

/**
 * The generic module FetchOr; an instance is one instantiation.
 *
 * <p>Port: the generic parameters are the constructor parameters. Rep is
 * the variable Rep.T of the instantiating interface and Atomic the
 * instance of AtomicModule (the instances refer to each other), both read
 * when they are needed. The module variables are instance fields.
 */
public final class FetchOr {
    private final Supplier<Type.T> Rep;
    private final Supplier<AtomicModule> Atomic;

    public FetchOr(Supplier<Type.T> Rep, Supplier<AtomicModule> Atomic) {
        this.Rep = Rep;
        this.Atomic = Atomic;
    }

    // File: FetchOr.mg

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
        t[0] = Type.base(Rep.get());
        if (!Type.isOrdinal(t[0])) {
            if (Type.isSubtype(t[0], Addr.T)) {
                if (Module.name(Module.current()) == M3ID.add(M3.ids, "AtomicAddress")) {
                    // allow it
                } else if (Module.isSafe()) {
                    Error.msg("unsafe operation");
                }
            } else if (Module.name(Module.current()) == M3ID.add(M3.ids, "AtomicRefany")) {
                // generate a run-time error
            } else {
                Error.msg("first argument must be of an ordinal type");
            }
        } else if (EnumExpr.split(ce.args[2], order, t) && TInt.toInt(order, z)) {
            if (z[0] == M3CG.MemoryOrder.Sequential.ordinal()) {
                // ok
            } else {
                Error.warn(0, "FetchOr currently only supports Order.Sequential");
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
        Type.T[] t = new Type.T[1];
        long[] z = new long[1];
        if (Module.name(Module.current()) == M3ID.add(M3.ids, "AtomicRefany")) {
            // generate a run-time error
            CG.abort(M3CG.RuntimeError.ValueOutOfRange);
        }
        Expr.compileAddress(ce.args[0], true);
        Expr.compile(ce.args[1]);
        EnumExpr.split(ce.args[2], order, t);
        TInt.toInt(order, z);
        CG.fetch_and_op(M3CG.AtomicOp.Or,
                        Type.cgType(Rep.get(), true),
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
        Value.T mask = Formal.newBuiltin("mask", 1, Rep.get());
        Formal.Info order = new Formal.Info();
        order.name = M3ID.add(M3.ids, "order");
        order.mode = Formal.Mode.mVALUE;
        order.offset = 2;
        order.type = Atomic.get().Order;
        order.dfault = Atomic.get().Sequential;
        order.unused = false;
        order.trace = null;
        Type.T t0 = ProcType.new_(Rep.get(), Formal.new_(var), mask, Formal.new_(order));
        Z = CallExpr.newMethodList(1, 3, false, true, true, Rep.get(),
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
        Procedure.definePredefined("FetchOr", Z, false, t0);
        formals = ProcType.formals(t0);
    }
}
