// Java port of CM3's m3-sys/m3front/src/builtinOps/Dispose.i3 and Dispose.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.Target.CGType;

// File: Dispose.m3
// Last Modified On Wed Jun 29 17:01:08 PDT 1994 By kalsow
//      Modified On Tue Feb 12 11:52:16 1991 By muller

public final class Dispose {
    private Dispose() {}

    private static CallExpr.MethodList Z;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T t;
        Type.Info info = new Type.Info();
        t = Expr.typeOf(ce.args[0]);
        t = Type.checkInfo(t, info);
        if (Module.isSafe()) {
            Error.msg("DISPOSE: unsafe operation");
        }
        if ((!Type.isSubtype(t, Reff.T))
            && (!Type.isSubtype(t, Addr.T))) {
            Error.msg("DISPOSE: must be applied to a reference type");
        } else if (Type.isEqual(t, Reff.T, null)
                   || Type.isEqual(t, Addr.T, null)
                   || Type.isEqual(t, ObjectRef.T, null)
                   || Type.isEqual(t, ObjectAdr.T, null)) {
            Error.msg("DISPOSE: must be applied to a fixed reference type");
        } else if (!Expr.isWritable(ce.args[0], /* traced := */ false)) {
            Error.msg("DISPOSE: must be applied to a writable designator");
        } else if (!info.isTraced) {
            Expr.needsAddress(ce.args[0]);
        }
        ce.type = null;
    }

    /**
     * Port: the constant PHook of Prep, indexed by {@code ORD (b)} for a
     * Boolean {@code b}.
     */
    private static final RunTyme.Hook[] PHook = {
        RunTyme.Hook.DisposeRef, RunTyme.Hook.DisposeObj };

    private static void prep(CallExpr.T ce) {
        Expr.T e = ce.args[0];
        Type.T t = Type.base(Expr.typeOf(e));
        Procedure.T proc;
        Type.Info info = new Type.Info();
        Expr.prepLValue(e, /* traced := */ false);
        t = Type.checkInfo(t, info);
        if (info.isTraced) {
            Expr.compileLValue(e, /* traced := */ false);
            CG.load_nil();
            CG.store_indirect(CGType.Addr, 0, M3.target.Address.size);
        } else {
            proc = RunTyme.lookUpProc(PHook[Type.isSubtype(t, ObjectAdr.T) ? 1 : 0]);
            Procedure.startCall(proc);
            Expr.compileAddress(e, /* traced := */ false);
            CG.pop_param(CGType.Addr);
            Procedure.emitCall(proc);
        }
        Expr.noteWrite(e);
    }

    private static void compile(CallExpr.T ce) {
        // all the work was done by Prep
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 1, false, false, true, null,
                                   null, null,
                                   CallExpr::notAddressable,
                                   Dispose::check,
                                   Dispose::prep,
                                   Dispose::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   CallExpr::noValue,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("DISPOSE", Z, true);
    }
}
