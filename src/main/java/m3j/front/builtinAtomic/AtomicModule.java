// Java port of CM3's m3-sys/m3front/src/builtinAtomic/AtomicModule.ig and
// AtomicModule.mg.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front.builtinAtomic;

import java.util.function.Supplier;

import m3j.front.EnumElt;
import m3j.front.EnumExpr;
import m3j.front.EnumType;
import m3j.front.Expr;
import m3j.front.Field;
import m3j.front.M3;
import m3j.front.Module;
import m3j.front.RecordType;
import m3j.front.Scope;
import m3j.front.Tipe;
import m3j.front.Type;
import m3j.middle.M3ID;

// File: AtomicModule.ig
// Last Modified On Tue Mar  2 09:01:49 PST 1993 By kalsow

/**
 * The generic module AtomicModule; an instance is one instantiation.
 *
 * <p>Port: the generic parameters are the constructor parameters. Rep is
 * the variable Rep.T of the instantiating interface, read when it is
 * needed. The module variables are instance fields.
 */
public final class AtomicModule {
    private final Supplier<Type.T> Rep;
    private final IsLockFree IsLockFree;
    private final Store Store;
    private final Load Load;
    private final Swap Swap;
    private final CompareSwap CompareSwap;
    private final Fence Fence;
    private final FetchInc FetchInc;
    private final FetchDec FetchDec;
    private final FetchOr FetchOr;
    private final FetchXor FetchXor;
    private final FetchAnd FetchAnd;

    public Module.T M;

    public Type.T T;

    public Type.T Order;

    public Expr.T Sequential;

    public AtomicModule(Supplier<Type.T> Rep,
                        IsLockFree IsLockFree,
                        Store Store, Load Load, Swap Swap,
                        CompareSwap CompareSwap,
                        Fence Fence,
                        FetchInc FetchInc, FetchDec FetchDec,
                        FetchOr FetchOr, FetchXor FetchXor,
                        FetchAnd FetchAnd) {
        this.Rep = Rep;
        this.IsLockFree = IsLockFree;
        this.Store = Store;
        this.Load = Load;
        this.Swap = Swap;
        this.CompareSwap = CompareSwap;
        this.Fence = Fence;
        this.FetchInc = FetchInc;
        this.FetchDec = FetchDec;
        this.FetchOr = FetchOr;
        this.FetchXor = FetchXor;
        this.FetchAnd = FetchAnd;
    }

    // File: AtomicModule.mg

    public void initialize(String name) {
        final int[] Zero  = {0, 0, 0, 0, 0, 0, 0, 0, 0};
        final int[] One   = {1, 0, 0, 0, 0, 0, 0, 0, 0};
        final int[] Two   = {2, 0, 0, 0, 0, 0, 0, 0, 0};
        final int[] Three = {3, 0, 0, 0, 0, 0, 0, 0, 0};
        final int[] Four  = {4, 0, 0, 0, 0, 0, 0, 0, 0};
        Scope.T zz, elts;
        Field.Info rep;
        M3.CheckState cs = new M3.CheckState();

        M = Module.newDefn(name, true, null);

        // WARNING: The following list must be in the same order
        //  as the actual Atomic.ig file, otherwise the version
        //  stamps will be messed up!

        zz = Scope.push(Module.exportScope(M));

        elts = Scope.pushNew(false, M3ID.NoID);
        rep = new Field.Info();
        rep.name = M3ID.add(M3.ids, "rep");
        rep.index = 0;
        rep.offset = 0;
        rep.type = Rep.get();
        rep.dfault = null;
        Scope.insert(Field.new_(rep));
        Scope.popNew();
        Scope.typeCheck(elts, cs);
        T = RecordType.new_(elts);
        Tipe.define("T", T, false);

        elts = Scope.pushNew(false, M3ID.NoID);
        Order = EnumType.new_(5, elts);
        Scope.insert(EnumElt.new_(M3ID.add(M3.ids, "Relaxed"),        Zero,  Order));
        Scope.insert(EnumElt.new_(M3ID.add(M3.ids, "Release"),        One,   Order));
        Scope.insert(EnumElt.new_(M3ID.add(M3.ids, "Acquire"),        Two,   Order));
        Scope.insert(EnumElt.new_(M3ID.add(M3.ids, "AcquireRelease"), Three, Order));
        Scope.insert(EnumElt.new_(M3ID.add(M3.ids, "Sequential"),     Four,  Order));
        Scope.popNew();
        Scope.typeCheck(elts, cs);
        Tipe.define("Order", Order, false);

        Sequential = EnumExpr.new_(Order, Four);

        IsLockFree.initialize();
        Store.initialize();
        Load.initialize();
        Swap.initialize();
        CompareSwap.initialize();
        Fence.initialize();
        FetchInc.initialize();
        FetchDec.initialize();
        FetchOr.initialize();
        FetchXor.initialize();
        FetchAnd.initialize();
        Scope.pop(zz);
    }
}
