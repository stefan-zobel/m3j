// Java port of CM3's m3-sys/m3front/src/builtinOps/IsType.i3 and IsType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.M3RT;
import m3j.middle.TInt;
import m3j.middle.Target.CGType;

// File: IsType.m3
// Last Modified On Tue May  3 16:31:06 PDT 1994 By kalsow
//      Modified On Sat Dec  8 00:54:22 1990 By muller

public final class IsType {
    private IsType() {}

    private static CallExpr.MethodList Z;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T[] t = new Type.T[1];
        Type.T u;
        if (!TypeExpr.split(ce.args[1], t)) {
            Error.msg("ISTYPE: second argument must be a type");
            t[0] = Expr.typeOf(ce.args[0]);
        }
        t[0] = Type.base(t[0]);
        u = Expr.typeOf(ce.args[0]);

        if (!Type.isAssignable(t[0], u)) {
            Error.msg("ISTYPE: types must be assignable");
        } else if (ObjectType.is(t[0]) || Type.isSubtype(t[0], Reff.T)) {
            // ok
        } else { // untraced ref type
            Error.msg("ISTYPE: must be a traced reference or object type");
        }

        ce.type = Bool.T;
    }

    private static void prep(CallExpr.T ce) {
        Expr.T e = ce.args[0];
        Type.T[] t = new Type.T[1];
        Type.T u;
        CG.Val ptr;
        long true_, false_, tagged;
        Procedure.T proc;
        if (!TypeExpr.split(ce.args[1], t)) {
            t[0] = Expr.typeOf(e);
        }
        Type.compile(t[0]);
        t[0] = Type.base(t[0]);
        u = Expr.typeOf(e);

        Expr.prep(ce.args[0]);
        if (Type.isSubtype(u, t[0])) {
            // the test succeeds statically
            Expr.compile(ce.args[0]);
            CG.discard(CGType.Addr);
            Value.load(Bool.True);
            ce.tmp = CG.pop();

        } else if (Type.isEqual(t[0], Null.T, null)) {
            Expr.compile(ce.args[0]);
            CG.load_nil();
            CG.compare(CGType.Addr, M3CG.CompareOp.EQ);
            ce.tmp = CG.pop();

        } else if (RefType.is(t[0])) {
            Expr.compile(ce.args[0]);
            tagged = CG.next_label();
            false_ = CG.next_label();
            true_ = CG.next_label();
            ptr = CG.pop();
            Value.load(Bool.True);
            CG.forceStacked(); // we need a temp
            ce.tmp = CG.pop_temp();
            CG.push(ptr);
            CG.load_nil();
            CG.if_compare(CGType.Addr, M3CG.CompareOp.EQ, true_, CG.Maybe);

            CG.push(ptr);
            CG.loophole(CGType.Addr, M3.target.Word.cg_type);
            CG.load_integer(M3.target.Word.cg_type, TInt.One);
            CG.and(M3.target.Word.cg_type);
            CG.if_true(tagged, CG.Maybe);

            CG.push(ptr);
            CG.ref_to_info(M3RT.RH_typecode_offset, M3RT.RH_typecode_size);
            Type.loadInfo(t[0], M3.rt.TC_typecode);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.EQ, true_, CG.Always);
            CG.jump(false_);

            CG.set_label(tagged);
            CG.load_intt(M3RT.REFANY_typecode);
            Type.loadInfo(t[0], M3.rt.TC_typecode);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.EQ, true_, CG.Always);

            CG.set_label(false_);
            Value.load(Bool.False);
            CG.store_temp(ce.tmp);

            CG.set_label(true_);
            CG.free(ptr);

        } else { // general object type
            proc = RunTyme.lookUpProc(RunTyme.Hook.CheckIsType);
            Procedure.startCall(proc);
            if (M3.target.DefaultCall.args_left_to_right) {
                Expr.compile(ce.args[0]);
                CG.pop_param(CGType.Addr);
                Type.loadInfo(t[0], -1);
                CG.pop_param(CGType.Addr);
            } else {
                Type.loadInfo(t[0], -1);
                CG.pop_param(CGType.Addr);
                Expr.compile(ce.args[0]);
                CG.pop_param(CGType.Addr);
            }
            ce.tmp = Procedure.emitValueCall(proc);
        }
    }

    private static void compile(CallExpr.T ce) {
        // all the work was done by Prep
        CG.push(ce.tmp);
        CG.free(ce.tmp);
        ce.tmp = null;
    }

    private static void prepBR(CallExpr.T ce, long true_, long false_, int freq) {
        Expr.T e = ce.args[0];
        Type.T[] t = new Type.T[1];
        Type.T u;
        CG.Val ptr;
        long skip, tagged;
        Procedure.T proc;
        if (!TypeExpr.split(ce.args[1], t)) {
            t[0] = Expr.typeOf(e);
        }
        Type.compile(t[0]);
        t[0] = Type.base(t[0]);
        u = Expr.typeOf(e);

        Expr.prep(ce.args[0]);
        if (Type.isSubtype(u, t[0])) {
            // the test succeeds statically
            Expr.compile(ce.args[0]);
            CG.discard(CGType.Addr);
            if ((true_ != CG.No_label)) {
                CG.jump(true_);
            // ELSE fall through
            }

        } else if (Type.isEqual(t[0], Null.T, null)) {
            Expr.compile(ce.args[0]);
            CG.load_nil();
            CG.if_then(CGType.Addr, M3CG.CompareOp.EQ, true_, false_, freq);

        } else if (RefType.is(t[0])) {
            Expr.compile(ce.args[0]);
            tagged = CG.next_label();
            skip = CG.next_label();
            ptr = CG.pop();
            CG.push(ptr);
            CG.load_nil();
            if ((true_ != CG.No_label)) {
                CG.if_compare(CGType.Addr, M3CG.CompareOp.EQ, true_, CG.Maybe);
            } else {
                CG.if_compare(CGType.Addr, M3CG.CompareOp.EQ, skip, CG.Maybe);
            }

            CG.push(ptr);
            CG.loophole(CGType.Addr, M3.target.Word.cg_type);
            CG.load_integer(M3.target.Word.cg_type, TInt.One);
            CG.and(M3.target.Word.cg_type);
            CG.if_true(tagged, CG.Maybe);

            CG.push(ptr);
            CG.ref_to_info(M3RT.RH_typecode_offset, M3RT.RH_typecode_size);
            Type.loadInfo(t[0], M3.rt.TC_typecode);
            CG.if_then(M3.target.Integer.cg_type, M3CG.CompareOp.EQ, true_, false_, freq);
            CG.jump(skip);

            CG.set_label(tagged);
            CG.load_intt(M3RT.REFANY_typecode);
            Type.loadInfo(t[0], M3.rt.TC_typecode);
            CG.if_then(M3.target.Integer.cg_type, M3CG.CompareOp.EQ, true_, false_, freq);
            CG.set_label(skip);
            CG.free(ptr);

        } else { // general object type
            proc = RunTyme.lookUpProc(RunTyme.Hook.CheckIsType);
            Procedure.startCall(proc);
            if (M3.target.DefaultCall.args_left_to_right) {
                Expr.compile(ce.args[0]);
                CG.pop_param(CGType.Addr);
                Type.loadInfo(t[0], -1);
                CG.pop_param(CGType.Addr);
            } else {
                Type.loadInfo(t[0], -1);
                CG.pop_param(CGType.Addr);
                Expr.compile(ce.args[0]);
                CG.pop_param(CGType.Addr);
            }
            Procedure.emitCall(proc);
            if ((true_ != CG.No_label)) {
                CG.if_true(true_, CG.Always);
            } else {
                CG.if_false(false_, CG.Never);
            }
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(2, 2, true, false, true, Bool.T,
                                   null, null,
                                   CallExpr::notAddressable,
                                   IsType::check,
                                   IsType::prep,
                                   IsType::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   IsType::prepBR,
                                   CallExpr::noBranch,
                                   CallExpr::noValue, // fold
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("ISTYPE", Z, true);
    }
}
