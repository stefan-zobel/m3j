// Java port of CM3's m3-sys/m3front/src/exprs/NilChkExpr.i3 and NilChkExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: NilChkExpr.m3
// Last Modified On Thu Jun 30 08:50:41 PDT 1994 By kalsow
//      Modified On Thu Nov 29 03:28:30 1990 By muller

public final class NilChkExpr {
    private NilChkExpr() {}

    public static final class P extends ExprRep.Ta {
        public long offset;

        @Override
        public Type.T typeOf() {
            return NilChkExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return NilChkExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            NilChkExpr.check(this, cs);
        }

        @Override
        public void prep() {
            NilChkExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            NilChkExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        @Override
        public void getBounds(int[] min, int[] max) {
            bounder(this, min, max);
        }

        @Override
        public void note_write() {
            noteWrites(this);
        }

        // Port: the overrides need_addr := ExprRep.NotAddressable,
        // prepLV := ExprRep.NotLValue, compileLV := ExprRep.NotLValueBool,
        // prepBR, compileBR := ExprRep.NotBoolean, isEqual :=
        // ExprRep.EqCheckA, isWritable, isDesignator, isZeroes :=
        // ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral, prepLiteral
        // := ExprRep.NoPrepLiteral and genLiteral := ExprRep.NoLiteral
        // restate the inherited methods.
    }

    public static Expr.T new_(Expr.T a) {
        P p;
        // Port: TYPECODE (a) = TYPECODE (P) is an instanceof test, since P
        // has no subtypes and NIL does not have the typecode of P.
        if ((!Host.doNilChk) || (a instanceof P)) {
            return a;
        }
        p = new P();
        ExprRep.init(p);
        p.origin = a.origin;
        p.a = a;
        p.offset = Long.MAX_VALUE;
        return p;
    }

    public static void setOffset(Expr.T a, long offset) {
        if (a == null) {
            // skip
        } else if (a instanceof P p) {
            p.offset = Math.min(p.offset, offset);
        } else {
            // skip
        }
    }

    private static Type.T typeOf(P p) {
        return Expr.typeOf(p.a);
    }

    private static Type.T repTypeOf(P p) {
        return Expr.repTypeOf(p.a);
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T[] target = new Type.T[1];
        long x;
        Type.Info info = new Type.Info();
        Expr.typeCheck(p.a, cs);
        p.type = Type.check(Expr.typeOf(p.a));
        if (RefType.split(p.type, target)) {
            if (OpenArrayType.is(target[0])) {
                x = Math.min(M3.rt.OA_elt_ptr, M3.rt.OA_size_0);
            } else {
                Type.checkInfo(target[0], info);
                x = info.size;
            }
            p.offset = Math.min(p.offset, x);
        }
    }

    private static void prep(P p) {
        Expr.prep(p.a);
    }

    private static void compile(P p, boolean StaticOnly) {
        long EasyCheck = Target.First_readable_addr * Target.Char.size;
        AssertError.check(!StaticOnly, "NilChkExpr");
        Expr.compile(p.a);
        if ((p.offset < 0) || (EasyCheck <= p.offset)) {
            CG.check_nil(M3CG.RuntimeError.BadMemoryReference);
        }
    }

    private static Expr.T fold(P p) {
        Expr.T e;
        int[] i = TInt.newInt();
        e = Expr.constValue(p.a);
        if ((e == null)) {
            return null;
        }
        if (!AddressExpr.split(e, i)) {
            return null;
        }
        if (TInt.EQ(i, TInt.Zero)) {
            return null;
        }
        return e;
    }

    private static void bounder(P p, int[] min, int[] max) {
        Expr.getBounds(p.a, min, max);
    }

    private static void noteWrites(P p) {
        Expr.noteWrite(p.a);
    }
}
