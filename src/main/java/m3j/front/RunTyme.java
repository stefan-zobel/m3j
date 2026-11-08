// Java port of CM3's m3-sys/m3front/src/misc/RunTyme.i3 and RunTyme.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.TInt;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

/**
 * This interface provides handle on the runtime procedures
 * that the compiler calls directly.
 */
public final class RunTyme {
    private RunTyme() {}

    public enum Hook {
        CheckIsType, ScanTypecase,
        RaiseEx, ResumeRaiseEx, PushEFrame, PopEFrame,
        Concat, MultiCat,
        NewTracedObj, NewTracedRef, NewTracedArray,
        NewUntracedObj, NewUntracedRef, NewUntracedArray,
        DisposeRef, DisposeObj,
        Abort, AssertFailed, DebugMsg,
        TextLitInfo, TextLitGetChar, TextLitGetWideChar,
        TextLitGetChars, TextLitGetWideChars,
        CheckLoadTracedRef, CheckStoreTraced, NoOp
    }

    private static final String RunTimeModuleName = "RTHooks";
    private static final String LocalAlias = "__" + RunTimeModuleName + "__";
    // the automagic import:  IMPORT Runtime AS __RunTime__

    /** Port: indexed by {@code Hook.ordinal()}. */
    private static final String[] HookNames = {
        "CheckIsType", "ScanTypecase",
        "Raise",  "ResumeRaise",  "PushEFrame", "PopEFrame",
        "Concat", "MultiCat",
        "AllocateTracedObj", "AllocateTracedRef", "AllocateOpenArray",
        "AllocateUntracedObj", "AllocateUntracedRef", "AllocateUntracedOpenArray",
        "DisposeUntracedRef", "DisposeUntracedObj",
        "ReportFault", "AssertFailed", "DebugMsg",
        "TextLitInfo", "TextLitGetChar", "TextLitGetWideChar",
        "TextLitGetChars", "TextLitGetWideChars",
        "CheckLoadTracedRef", "CheckStoreTraced", "NoOp"
    };

    private static Module.T hooks = null;
    private static int hooks_name = M3ID.NoID;
    private static int hooks_alias = M3ID.NoID;
    /** Port: indexed by {@code Hook.ordinal()}. */
    private static final Procedure.T[] hook_procs = new Procedure.T[Hook.values().length];

    //-------------------------------------------------------------------------

    public static void reset() {
        hooks = null;
    }

    /** Import the standard interfaces containing runtime hooks. */
    public static void import_() {
        if ((hooks != null)) {
            return;
        }
        hooks_name = M3ID.add(M3.ids, RunTimeModuleName);
        hooks_alias = M3ID.add(M3.ids, LocalAlias);
        hooks = Module.lookUp(hooks_name, /* internal := */ true);
        for (int h = 0; h < hook_procs.length; h++) {
            hook_procs[h] = null;
        }
    }

    /**
     * bind the runtime interface as an import of 'dest'
     *
     * <p>Port: the VAR parameters runtime and id are one-element arrays.
     */
    public static void bind(Module.T dest, Module.T[] runtime, int[] id) {
        if ((dest.name == hooks_name) && Module.isInterface()) {
            runtime[0] = null;
            id[0] = M3ID.NoID;
        } else {
            runtime[0] = hooks;
            id[0] = hooks_alias;
        }
    }

    /** return a handle on the procedure that implements hook 'h' */
    public static Procedure.T lookUpProc(RunTyme.Hook h) {
        Procedure.T p = hook_procs[h.ordinal()];
        AssertError.check(hooks != null, "RunTyme");
        if ((p == null)) {
            p = lookUpNewProc(M3ID.add(M3.ids, HookNames[h.ordinal()]));
            hook_procs[h.ordinal()] = p;
        }
        AssertError.check(p != null, "RunTyme");
        return p;
    }

    //----------------------------------------------------------- internal ---

    private static Procedure.T lookUpNewProc(int name) {
        Value.T v = lookUp(name);
        Value.Class c = Value.classOf(v);
        if ((c != Value.Class.Procedure)) {
            return null;
        }
        Value.declare(v); // force a version stamp
        v = Value.base(v);
        CG.set_runtime_proc(v.name, Procedure.cgName((Procedure.T) v));
        return (Procedure.T) v;
    }

    private static Value.T lookUp(int name) {
        Scope.T syms;
        Value.T v, v2;
        if ((hooks == null)) {
            return null;
        }
        syms = Module.exportScope(hooks);
        if ((syms != null)) {
            v = Scope.lookUp(syms, name, true);
        } else {
            v = null; // probably a circular import!
        }
        if ((v == null)) {
            M3.QID q = new M3.QID();
            q.module = hooks_name;
            q.item = name;
            Error.QID(q, "undefined runtime symbol !!");
        }

        // If possible, use the local explicit declaration...
        syms = Scope.top();
        v2 = Scope.lookUp(syms, name, /* strict := */ false);
        if ((v2 != null) && Procedure.isEqual(v2, v)) {
            v.used = false; // forget about using the version in the interface
            v = v2;
        }

        return v;
    }

    public static void emitCheckLoadTracedRef() {
        Procedure.T proc = lookUpProc(Hook.CheckLoadTracedRef);
        CG.Val ref = CG.pop_temp();
        long skip = CG.next_label();
        CG.push(ref);
        CG.load_nil();
        CG.if_compare(CGType.Addr, M3CG.CompareOp.EQ, skip, CG.Maybe);
        CG.push(ref);
        CG.loophole(CGType.Addr, M3.target.Integer.cg_type);
        CG.load_integer(M3.target.Integer.cg_type, TInt.One);
        CG.and(M3.target.Integer.cg_type);
        CG.if_true(skip, CG.Maybe);
        CG.push(ref);
        CG.ref_to_info(M3RT.RH_gray_offset, M3RT.RH_gray_size);
        CG.if_false(skip, CG.Maybe);
        Procedure.startCall(proc);
        CG.push(ref);
        CG.pop_param(CGType.Addr);
        Procedure.emitCall(proc);
        CG.set_label(skip);
        CG.push(ref);
        CG.free(ref);
    }

    public static void emitCheckStoreTraced() {
        Procedure.T proc = lookUpProc(Hook.CheckStoreTraced);
        CG.Val ref = CG.pop_temp();
        long skip = CG.next_label();
        CG.push(ref);
        CG.ref_to_info(M3RT.RH_dirty_offset, M3RT.RH_dirty_size);
        CG.if_true(skip, CG.Maybe);
        Procedure.startCall(proc);
        CG.push(ref);
        CG.pop_param(CGType.Addr);
        Procedure.emitCall(proc);
        CG.set_label(skip);
        CG.push(ref);
        CG.free(ref);
    }
}
