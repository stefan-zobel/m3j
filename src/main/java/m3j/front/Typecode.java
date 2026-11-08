// Java port of CM3's m3-sys/m3front/src/builtinOps/Typecode.i3 and Typecode.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.M3RT;
import m3j.middle.TInt;
import m3j.middle.Target.CGType;

// File: Typecode.m3
// Last Modified On Tue May  3 16:33:20 PDT 1994 By kalsow
//      Modified On Fri Mar 15 03:50:01 1991 By muller

public final class Typecode {
    private Typecode() {}

    private static CallExpr.MethodList Z;

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T[] t = new Type.T[1];
        if (TypeExpr.split(ce.args[0], t)) {
            if ((ObjectType.is(t[0]))) {
                // ok
            } else if ((Type.isEqual(t[0], Reff.T, null))) {
                Error.msg("TYPECODE: T must be a fixed reference type");
            } else if ((!Type.isSubtype(t[0], Reff.T))) {
                Error.msg("TYPECODE: T must be a traced reference type");
            }
        } else {
            t[0] = Expr.typeOf(ce.args[0]);
            if (!Type.isSubtype(t[0], Reff.T) && !ObjectType.is(t[0])) {
                Error.msg("TYPECODE: r must be a traced reference or object");
            }
        }
        ce.type = Card.T;
    }

    private static void prep(CallExpr.T ce) {
        Expr.T e = ce.args[0];
        Type.T[] t = new Type.T[1];
        long nil, tagged;
        if (TypeExpr.split(e, t)) {
            // get the typecode from the typecell
        } else {
            // get the typecode from the REF's header
            Expr.prep(e);
            Expr.compile(e);
            ce.tmp = CG.pop_temp();
            tagged = CG.next_label();
            nil = CG.next_label();

            CG.push(ce.tmp);
            CG.load_nil();
            CG.if_compare(CGType.Addr, M3CG.CompareOp.EQ, nil, CG.Never);

            CG.push(ce.tmp);
            CG.loophole(CGType.Addr, M3.target.Word.cg_type);
            CG.load_integer(M3.target.Word.cg_type, TInt.One);
            CG.and(M3.target.Word.cg_type);
            CG.if_true(tagged, CG.Maybe);

            CG.push(ce.tmp);
            CG.ref_to_info(M3RT.RH_typecode_offset, M3RT.RH_typecode_size);
            CG.loophole(M3.target.Integer.cg_type, CGType.Addr);
            CG.store_temp(ce.tmp);
            CG.jump(nil);

            CG.set_label(tagged);
            CG.load_intt(M3RT.REFANY_typecode);
            CG.loophole(M3.target.Integer.cg_type, CGType.Addr);
            CG.store_temp(ce.tmp);

            CG.set_label(nil);
        }
    }

    private static void compile(CallExpr.T ce) {
        Expr.T e = ce.args[0];
        Type.T[] t = new Type.T[1];
        if (TypeExpr.split(e, t)) {
            // get the typecode from the typecell
            Type.compile(t[0]);
            Type.loadInfo(t[0], M3.rt.TC_typecode);
        } else {
            // get the typecode from the REF's header
            CG.push(ce.tmp);
            CG.loophole(CGType.Addr, M3.target.Integer.cg_type);
            CG.free(ce.tmp);
            ce.tmp = null;
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, 1, true, false, true, Card.T,
                                   null, null,
                                   CallExpr::notAddressable,
                                   Typecode::check,
                                   Typecode::prep,
                                   Typecode::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   CallExpr::noValue, // fold
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("TYPECODE", Z, true);
    }
}
