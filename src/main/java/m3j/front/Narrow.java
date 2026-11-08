// Java port of CM3's m3-sys/m3front/src/builtinOps/Narrow.i3 and Narrow.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.M3RT;
import m3j.middle.TInt;
import m3j.middle.Target.CGType;

// File: Narrow.m3
// Last Modified On Wed Jun 29 17:29:43 PDT 1994 By kalsow
//      Modified On Sat Dec  8 00:54:20 1990 By muller

public final class Narrow {
    private Narrow() {}

    private static CallExpr.MethodList Z;

    private static Type.T typeOf(CallExpr.T ce) {
        Type.T[] t = new Type.T[1];
        if (TypeExpr.split(ce.args[1], t)) {
            return t[0];
        } else {
            return Expr.typeOf(ce.args[0]);
        }
    }

    private static Type.T repTypeOf(CallExpr.T ce) {
        Type.T[] t = new Type.T[1];
        if (TypeExpr.split(ce.args[1], t)) {
            return t[0];
        } else {
            return Expr.repTypeOf(ce.args[0]);
        }
    }

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T[] dest = new Type.T[1];
        Type.T src = Expr.typeOf(ce.args[0]);
        if (!TypeExpr.split(ce.args[1], dest)) {
            Error.msg("NARROW: second argument must be a type");
            dest[0] = src;
        }

        if (!Type.isAssignable(dest[0], src)) {
            Error.msg("NARROW: types must be assignable");
        } else if (ObjectType.is(dest[0]) || Type.isSubtype(dest[0], Reff.T)) {
            // ok
        } else { // untraced ref type
            Error.msg("NARROW: must be a traced reference or object type");
        }

        ce.type = dest[0];
    }

    private static void prep(CallExpr.T ce) {
        Type.T[] t = new Type.T[1];
        TypeExpr.split(ce.args[1], t);
        Type.compile(t[0]);
        Expr.prep(ce.args[0]);
        Expr.compile(ce.args[0]);
        ce.tmp = emitCore(t[0], Expr.typeOf(ce.args[0]));
        if ((ce.tmp == null)) {
            // capture the ref value
            ce.tmp = CG.pop_temp();
        }
    }

    private static void compile(CallExpr.T ce) {
        // all the work was done by Prep
        CG.push(ce.tmp);
        CG.free(ce.tmp);
        ce.tmp = null;
    }

    /**
     * generate  "NARROW (s0.trhs, tlhs)"  -- Note: the generated code
     * may include a procedure call.
     */
    public static void emit(Type.T tlhs, Type.T trhs) {
        CG.Val tmp = emitCore(tlhs, trhs);
        if ((tmp != null)) {
            // reload the ref value on the stack
            CG.push(tmp);
            CG.free(tmp);
        }
    }

    private static CG.Val emitCore(Type.T tlhs, Type.T trhs) {
        long ok, tagged;
        CG.Val ref;
        boolean is_object = ObjectType.is(tlhs);
        Type.T[] target = new Type.T[1];
        long align;
        Type.Info lhs_info = new Type.Info();
        Type.Info info = new Type.Info();
        Procedure.T proc;
        tlhs = Type.checkInfo(tlhs, lhs_info);
        if (is_object) {
            align = ObjectType.fieldAlignment(tlhs);
            CG.boost_addr_alignment(align);
        } else if (RefType.split(tlhs, target)) {
            target[0] = Type.checkInfo(target[0], info);
            align = info.alignment;
            CG.boost_addr_alignment(align);
        }

        // CT test for the no-check cases...
        if (Type.isSubtype(trhs, tlhs)) {
            return null;
        }
        if ((!is_object) && (!lhs_info.isTraced)) {
            return null;
        }

        // capture the right-hand side
        ref = CG.pop();
        ok = CG.next_label();

        // RT check for ref = NIL
        CG.push(ref);
        CG.load_nil();
        CG.if_compare(CGType.Addr, M3CG.CompareOp.EQ, ok, CG.Maybe);

        if (!Type.isEqual(tlhs, Null.T, null)) {
            tagged = CG.next_label();

            // RT check for ref is tagged.
            CG.push(ref);
            CG.loophole(CGType.Addr, M3.target.Word.cg_type);
            CG.load_integer(M3.target.Word.cg_type, TInt.One);
            CG.and(M3.target.Word.cg_type);
            CG.if_true(tagged, CG.Maybe);

            // RT check for TYPECODE(ref) = TYPECODE(type)
            CG.push(ref);
            CG.ref_to_info(M3RT.RH_typecode_offset, M3RT.RH_typecode_size);
            Type.loadInfo(tlhs, M3.rt.TC_typecode);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.EQ, ok, CG.Always);

            if (!RefType.is(tlhs)) {
                // Note: When T is unrevealed <: REFANY, both ObjectType(T) and
                //       RefType.Is(T) return FALSE.  We need to call the runtime in
                //       in this case.
                // finally, call the runtime to figure it out...
                proc = RunTyme.lookUpProc(RunTyme.Hook.CheckIsType);
                Procedure.startCall(proc);
                if (M3.target.DefaultCall.args_left_to_right) {
                    CG.push(ref);
                    CG.pop_param(CGType.Addr);
                    Type.loadInfo(tlhs, -1);
                    CG.pop_param(CGType.Addr);
                } else {
                    Type.loadInfo(tlhs, -1);
                    CG.pop_param(CGType.Addr);
                    CG.push(ref);
                    CG.pop_param(CGType.Addr);
                }
                Procedure.emitCall(proc);
                CG.if_true(ok, CG.Always);
            }
            CG.abort(M3CG.RuntimeError.NarrowFailed);

            CG.set_label(tagged);
            CG.load_intt(M3RT.REFANY_typecode);
            Type.loadInfo(tlhs, M3.rt.TC_typecode);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.EQ, ok, CG.Always);
        }

        CG.abort(M3CG.RuntimeError.NarrowFailed);
        CG.set_label(ok);

        return ref;
    }

    private static Expr.T fold(CallExpr.T ce) {
        return Expr.constValue(ce.args[0]);
    }

    private static void noteWrites(CallExpr.T ce) {
        Expr.noteWrite(ce.args[0]);
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(2, 2, true, false, true, null,
                                   Narrow::typeOf,
                                   Narrow::repTypeOf,
                                   CallExpr::notAddressable,
                                   Narrow::check,
                                   Narrow::prep,
                                   Narrow::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   Narrow::fold,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   Narrow::noteWrites);
        Procedure.definePredefined("NARROW", Z, true);
    }
}
