// Java port of CM3's m3-sys/m3front/src/stmts/ExitStmt.i3 and ExitStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

// File: ExitStmt.i3
// Last Modified On Fri Jun 24 09:21:11 PDT 1994 by kalsow
//      Modified On Sat Jun  3 15:29:59 PDT 1989 by muller

// File: ExitStmt.m3
// Last modified on Fri Jun 24 12:35:50 PDT 1994 by kalsow
//      modified on Tue Apr 10 22:54:44 1990 by muller

public final class ExitStmt {
    private ExitStmt() {}

    public static final class P extends Stmt.T {
        @Override
        public void check(M3.CheckState cs) {
            ExitStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return ExitStmt.compile(this);
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
        Scanner.match(Token.T.tEXIT);
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        if (!Marker.exitOK()) {
            Error.msg("EXIT not contained in a loop");
        }
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        Marker.emitExit();
        return EnumSet.of(Stmt.Outcome.Exits);
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        return EnumSet.of(Stmt.Outcome.Exits);
    }
}
