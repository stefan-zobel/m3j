// Java port of CM3's m3-sys/m3front/src/exprs/ProcExpr.i3 and ProcExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;

// File: ProcExpr.m3
// Last modified on Fri Feb 24 16:48:44 PST 1995 by kalsow
//      modified on Tue Oct 10 18:42:24 1989 by muller

/** A PROCEDURE constant. */
public final class ProcExpr {
    private ProcExpr() {}

    public static final class P extends Expr.T {
        public Value.T proc;

        @Override
        public Type.T typeOf() {
            return ProcExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return ProcExpr.typeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            ProcExpr.check(this, cs);
        }

        @Override
        public void compile(boolean StaticOnly) {
            ProcExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return ExprRep.self(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public void genFPLiteral(M3Buf.T mbuf) {
            ProcExpr.genFPLiteral(this, mbuf);
        }

        @Override
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            ProcExpr.genLiteral(this, offset, type, is_const);
        }

        // Port: the overrides need_addr := ExprRep.NotAddressable, prep :=
        // ExprRep.NoPrep, prepLV := ExprRep.NotLValue, compileLV :=
        // ExprRep.NotLValueBool, prepBR, compileBR := ExprRep.NotBoolean,
        // getBounds := ExprRep.NoBounds, isWritable, isDesignator, isZeroes
        // := ExprRep.IsNever, prepLiteral := ExprRep.NoPrepLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
    }

    public static Expr.T new_(Value.T proc) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.proc = Value.base(proc);
        return p;
    }

    public static boolean split(Expr.T e, Value.T[] proc) {
        if (e == null) {
            return false;
        } else if (e instanceof P p) {
            proc[0] = p.proc;
            return true;
        } else {
            return false;
        }
    }

    public static boolean compare(Expr.T a, Expr.T b, long[] sign) {
        Value.T x, y;
        if (a == null) {
            return false;
        } else if (a instanceof P p) {
            x = p.proc;
        } else {
            return false;
        }
        if (b == null) {
            return false;
        } else if (b instanceof P p) {
            y = p.proc;
        } else {
            return false;
        }
        if ((x == y)) {
            sign[0] = 0;
        } else {
            sign[0] = -3;
        }
        return true;
    }

    private static Type.T typeOf(P p) {
        return Value.typeOf(p.proc);
    }

    private static void check(P p, M3.CheckState cs) {
        Value.typeCheck(p.proc, cs);
        p.type = Value.typeOf(p.proc);
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e == null) {
            return false;
        } else if (e instanceof P b) {
            return Procedure.isEqual(a.proc, b.proc);
        } else {
            return false;
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        if (!StaticOnly) {
            Value.load(p.proc);
        }
    }

    private static void genFPLiteral(P p, M3Buf.T buf) {
        Scope.IDStack s = new Scope.IDStack();
        s.top = 0;
        Scope.nameToPrefix(p.proc, s, /* considerExternal := */ true,
                           /* dots := */ true);
        Scope.putStack(buf, s);
    }

    private static void genLiteral(P p, long offset, Type.T type,
                                   boolean is_const) {
        // Port: Procedure.CGName takes a Procedure.T; the implicit
        // narrowing of the original is a cast.
        CG.init_proc(offset, Procedure.cgName((Procedure.T) p.proc), is_const);
    }
}
