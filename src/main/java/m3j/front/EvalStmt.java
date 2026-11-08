// Java port of CM3's m3-sys/m3front/src/stmts/EvalStmt.i3 and EvalStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

// File: EvalStmt.i3
// Last Modified On Fri Jun 24 09:21:00 PDT 1994 By kalsow

// File: EvalStmt.m3
// Last modified on Fri Jun 24 12:35:51 PDT 1994 by kalsow
//      modified on Tue Apr 10 22:53:33 1990 by muller

public final class EvalStmt {
    private EvalStmt() {}

    public static final class P extends Stmt.T {
        public Expr.T e;

        @Override
        public void check(M3.CheckState cs) {
            EvalStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return EvalStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    public static Stmt.T parse() {
        P p;
        p = new P();
        StmtRep.init(p);
        Scanner.match(Token.T.tEVAL);
        p.e = Expr.parse();
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Expr.typeCheck(p.e, cs);
        if ((Expr.typeOf(p.e) == null)) {
            Error.msg("expression doesn't have a value");
        }
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        Expr.prep(p.e);
        Expr.compile(p.e);
        CG.discard(Type.cgType(Expr.typeOf(p.e)));
        return EnumSet.of(Stmt.Outcome.FallThrough);
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        return EnumSet.of(Stmt.Outcome.FallThrough);
    }
}
