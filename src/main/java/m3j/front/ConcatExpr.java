// Java port of CM3's m3-sys/m3front/src/exprs/ConcatExpr.i3 and ConcatExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: ConcatExpr.m3
// Last modified on Tue Dec 20 14:25:23 PST 1994 by kalsow
//      modified on Thu Nov 29 03:30:53 1990 by muller

public final class ConcatExpr {
    private ConcatExpr() {}

    public static final class P extends ExprRep.Tab {
        public Expr.T folded;
        public CG.Val tmp;

        @Override
        public void check(M3.CheckState cs) {
            ConcatExpr.check(this, cs);
        }

        @Override
        public void prep() {
            ConcatExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            ConcatExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        @Override
        public long exprAlign() {
            return ExprRep.exprAddrAlign(this);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType,
        // need_addr := ExprRep.NotAddressable, prepLV := ExprRep.NotLValue,
        // compileLV := ExprRep.NotLValueBool, prepBR, compileBR :=
        // ExprRep.NotBoolean, isEqual := ExprRep.EqCheckAB, getBounds :=
        // ExprRep.NoBounds, isWritable, isDesignator, isZeroes :=
        // ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral, prepLiteral
        // := ExprRep.NoPrepLiteral, genLiteral := ExprRep.NoLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
    }

    public static Expr.T new_(Expr.T a, Expr.T b) {
        P p = new P();
        ExprRep.init(p);
        p.a = a;
        p.b = b;
        p.folded = null;
        p.type = Textt.T;
        p.repType = Textt.T;
        p.tmp = null;
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T ta, tb;
        long[] a = new long[1], b = new long[1];
        Expr.typeCheck(p.a, cs);
        Expr.typeCheck(p.b, cs);
        ta = Type.base(Expr.typeOf(p.a));
        tb = Type.base(Expr.typeOf(p.b));
        if (Type.isAssignable(Textt.T, ta) && Type.isAssignable(Textt.T, tb)) {
            p.a = checkArg(Textt.T, ta, p.a, cs);
            p.b = checkArg(Textt.T, tb, p.b, cs);
        } else {
            p.type = Expr.badOperands("'&'", ta, tb);
            p.repType = p.type;
        }
        Error.count(a, b);
        if ((a[0] == 0)) {
            fold(p); // try folding all concatenations
        }
        if ((p.folded != null)) {
            Expr.typeCheck(p.folded, cs);
        }
    }

    private static Expr.T checkArg(Type.T tlhs, Type.T trhs, Expr.T e,
                                   M3.CheckState cs) {
        AssignStmt.check(tlhs, e, cs);
        if (Host.doNarrowChk && !Type.isSubtype(trhs, tlhs)) {
            e = NarrowExpr.new_(e, tlhs);
            Expr.typeCheck(e, cs);
        }
        return e;
    }

    private static void prep(P p) {
        Procedure.T proc;
        if ((p.folded != null)) {
            Expr.prep(p.folded);
        } else {
            Expr.prep(p.a);
            Expr.prep(p.b);
            proc = RunTyme.lookUpProc(RunTyme.Hook.Concat);
            Procedure.startCall(proc);
            if (M3.target.DefaultCall.args_left_to_right) {
                Expr.compile(p.a);
                CG.pop_param(CGType.Addr);
                Expr.compile(p.b);
                CG.pop_param(CGType.Addr);
            } else {
                Expr.compile(p.b);
                CG.pop_param(CGType.Addr);
                Expr.compile(p.a);
                CG.pop_param(CGType.Addr);
            }
            p.tmp = Procedure.emitValueCall(proc);
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        AssertError.check(!StaticOnly, "ConcatExpr");
        if ((p.folded != null)) {
            Expr.compile(p.folded, StaticOnly);
        } else {
            CG.push(p.tmp);
            CG.boost_addr_alignment(M3.target.Address.align);
            CG.free(p.tmp);
            p.tmp = null;
        }
    }

    private static Expr.T fold(P p) {
        Expr.T e1, e2;
        Expr.T[] e3 = new Expr.T[1];
        if ((p.folded != null)) {
            return p.folded;
        }
        e1 = Expr.constValue(p.a);
        if ((e1 == null)) {
            return null;
        }
        p.a = e1;
        e2 = Expr.constValue(p.b);
        if ((e2 == null)) {
            return null;
        }
        p.b = e2;
        e3[0] = null;
        if (TextExpr.cat(e1, e2, e3)) {
            p.folded = e3[0];
        }
        return e3[0];
    }
}
