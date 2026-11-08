// Java port of CM3's m3-sys/m3front/src/stmts/CallStmt.i3 and CallStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

// File: CallStmt.i3
// Last Modified On Mon Jul 24 11:02:27 1989 By kalsow

// File: CallStmt.m3
// Last modified on Tue Jun 22 14:29:40 PDT 1993 by kalsow
//      modified on Tue Dec 18 04:49:06 1990 by muller

public final class CallStmt {
    private CallStmt() {}

    public static final class P extends Stmt.T {
        public Expr.T e;

        @Override
        public void check(M3.CheckState cs) {
            CallStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return CallStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    public static Stmt.T new_(Expr.T e) {
        P p;
        p = new P();
        StmtRep.init(p);
        p.e = e;
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T t;
        Expr.typeCheck(p.e, cs);
        t = Type.base(Expr.typeOf(p.e));
        if ((t != null) && (t != ErrType.T)) {
            Error.msg("Expression is not a statement");
        }
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        Expr.prep(p.e);
        Expr.compile(p.e);
        return EnumSet.of(Stmt.Outcome.FallThrough);
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        return EnumSet.of(Stmt.Outcome.FallThrough);
    }
}
