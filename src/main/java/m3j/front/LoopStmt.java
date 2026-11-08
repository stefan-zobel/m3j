// Java port of CM3's m3-sys/m3front/src/stmts/LoopStmt.i3 and LoopStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

// File: LoopStmt.i3
// Last Modified On Fri Jun 24 09:32:49 PDT 1994 By kalsow

// File: LoopStmt.m3
// Last modified on Fri Jun 24 12:28:38 PDT 1994 by kalsow
//      modified on Mon Oct 16 14:12:01 1989 by muller

public final class LoopStmt {
    private LoopStmt() {}

    public static final class P extends Stmt.T {
        public Stmt.T body;

        @Override
        public void check(M3.CheckState cs) {
            LoopStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return LoopStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    public static Stmt.T parse() {
        P p = new P();
        StmtRep.init(p);
        Scanner.match(Token.T.tLOOP);
        p.body = Stmt.parse();
        Scanner.match(Token.T.tEND);
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Marker.pushExit(CG.No_label);
        Stmt.typeCheck(p.body, cs);
        Marker.pop();
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        EnumSet<Stmt.Outcome> oc;
        long top = CG.next_label(2);
        Marker.pushExit(top + 1);
          CG.set_label(top);
          oc = Stmt.compile(p.body);
          if (oc.contains(Stmt.Outcome.FallThrough)) {
              CG.jump(top);
              oc.remove(Stmt.Outcome.FallThrough);
          }
          CG.set_label(top + 1);
        Marker.pop();
        if (oc.contains(Stmt.Outcome.Exits)) {
            oc.add(Stmt.Outcome.FallThrough);
            oc.remove(Stmt.Outcome.Exits);
        }
        return oc;
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        EnumSet<Stmt.Outcome> oc = Stmt.getOutcome(p.body);
        oc.remove(Stmt.Outcome.FallThrough);
        if (oc.contains(Stmt.Outcome.Exits)) {
            oc.add(Stmt.Outcome.FallThrough);
            oc.remove(Stmt.Outcome.Exits);
        }
        return oc;
    }
}
