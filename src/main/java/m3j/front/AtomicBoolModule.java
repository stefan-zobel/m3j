// Java port of the instantiation AtomicBoolModule of CM3's
// m3-sys/m3front/src/builtinAtomic/AtomicModule.ig and AtomicModule.mg,
// built by m3-sys/m3front/src/builtinAtomic/m3makefile and atomic.tmpl.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.function.Supplier;

import m3j.front.builtinAtomic.AtomicModule;
import m3j.front.builtinAtomic.CompareSwap;
import m3j.front.builtinAtomic.Fence;
import m3j.front.builtinAtomic.FetchAnd;
import m3j.front.builtinAtomic.FetchDec;
import m3j.front.builtinAtomic.FetchInc;
import m3j.front.builtinAtomic.FetchOr;
import m3j.front.builtinAtomic.FetchXor;
import m3j.front.builtinAtomic.IsLockFree;
import m3j.front.builtinAtomic.Load;
import m3j.front.builtinAtomic.Store;
import m3j.front.builtinAtomic.Swap;

// File: AtomicModule.ig
// Last Modified On Tue Mar  2 09:01:49 PST 1993 By kalsow

/**
 * The instantiation AtomicBoolModule = AtomicModule (Bool, IsLockFreeBool,
 * StoreBool, LoadBool, SwapBool, CompareSwapBool, FenceBool, FetchIncBool,
 * FetchDecBool, FetchOrBool, FetchXorBool, FetchAndBool).
 *
 * <p>Port: the instances of the generic modules are created here, with
 * {@code Bool.T} read when it is needed and the AtomicModule instance as
 * the parameter Atomic of the others. The variables of the interface are
 * the static fields below; Initialize sets them from the AtomicModule
 * instance, whose module variables they are.
 */
public final class AtomicBoolModule {
    private AtomicBoolModule() {}

    public static Module.T M;

    public static Type.T T;

    public static Type.T Order;

    public static Expr.T Sequential;

    private static final AtomicModule instance;

    static {
        Supplier<Type.T> rep = () -> Bool.T;
        Supplier<AtomicModule> atomic = () -> AtomicBoolModule.instance;
        instance = new AtomicModule(rep,
                                    new IsLockFree(rep),
                                    new Store(rep, atomic),
                                    new Load(rep, atomic),
                                    new Swap(rep, atomic),
                                    new CompareSwap(rep, atomic),
                                    new Fence(atomic),
                                    new FetchInc(rep, atomic),
                                    new FetchDec(rep, atomic),
                                    new FetchOr(rep, atomic),
                                    new FetchXor(rep, atomic),
                                    new FetchAnd(rep, atomic));
    }

    public static void initialize(String name) {
        instance.initialize(name);
        M = instance.M;
        T = instance.T;
        Order = instance.Order;
        Sequential = instance.Sequential;
    }
}
