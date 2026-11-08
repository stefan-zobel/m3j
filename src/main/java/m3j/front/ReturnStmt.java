// Java port of CM3's m3-sys/m3front/src/stmts/ReturnStmt.i3 and ReturnStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

// File: ReturnStmt.i3
// Last Modified On Fri Jun 24 09:22:09 PDT 1994 By kalsow

// File: ReturnStmt.m3
// Last modified on Fri Jun 24 12:30:34 PDT 1994 by kalsow
//      modified on Thu Dec  5 17:22:32 PST 1991 by muller

public final class ReturnStmt {
    private ReturnStmt() {}

    public static final class P extends Stmt.T {
        public Expr.T expr;

        @Override
        public void check(M3.CheckState cs) {
            ReturnStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return ReturnStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    public static Stmt.T parse() {
        P p = new P();
        StmtRep.init(p);
        p.expr = null;
        Scanner.match(Token.T.tRETURN);
        if (Token.ExprStart.contains(Scanner.cur.token)) {
            p.expr = Expr.parse();
        }
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T[] t = new Type.T[1];
        Variable.T[] v = new Variable.T[1];
        Expr.typeCheck(p.expr, cs);
        if (!Marker.returnOK()) {
            Error.msg("RETURN not in a procedure");
            return;
        }
        Marker.returnVar(t, v);
        if ((p.expr == null)) {
            if ((t[0] != null)) {
                Error.msg("missing return result");
            }
        } else if ((t[0] == null)) {
            Error.msg("procedure does not have a return result");
        } else {
            ArrayExpr.noteUseTargetVar(p.expr);
            // ^Marker.EmitReturn will either build result value directly or
            //  provide a temp.
            AssignStmt.check(t[0], p.expr, cs);
        }
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        Marker.emitReturn(p.expr, /* fromFinally := */ false);
        return EnumSet.of(Stmt.Outcome.Returns);
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        return EnumSet.of(Stmt.Outcome.Returns);
    }
}
