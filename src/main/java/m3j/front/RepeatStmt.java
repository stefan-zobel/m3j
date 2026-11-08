// Java port of CM3's m3-sys/m3front/src/stmts/RepeatStmt.i3 and RepeatStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

// File: RepeatStmt.i3
// Last Modified On Fri Jun 24 09:32:47 PDT 1994 By kalsow

// File: RepeatStmt.m3
// Last modified on Fri Jun 24 14:00:24 PDT 1994 by kalsow
//      modified on Tue Oct 10 18:42:03 1989 by muller

public final class RepeatStmt {
    private RepeatStmt() {}

    public static final class P extends Stmt.T {
        public Stmt.T body;
        public Expr.T expr;
        public long e_origin;

        @Override
        public void check(M3.CheckState cs) {
            RepeatStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return RepeatStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    public static Stmt.T parse() {
        P p = new P();
        StmtRep.init(p);
        Scanner.match(Token.T.tREPEAT);
        p.body = Stmt.parse();
        Scanner.match(Token.T.tUNTIL);
        p.e_origin = Scanner.offset;
        p.expr = Expr.parse();
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T t;
        Marker.pushExit(CG.No_label);
        Stmt.typeCheck(p.body, cs);
        Marker.pop();
        Expr.typeCheck(p.expr, cs);
        t = Expr.typeOf(p.expr);
        if ((Type.base(t) != Bool.T)) {
            Error.msg("REPEAT condition must be a BOOLEAN");
        }
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        EnumSet<Stmt.Outcome> oc;
        long top = CG.next_label(2);
        Marker.pushExit(top + 1);
          CG.set_label(top);
          oc = Stmt.compile(p.body);
          if (oc.contains(Stmt.Outcome.FallThrough)) {
              CG.gen_location(p.e_origin);
              Expr.prepBranch(p.expr, CG.No_label, top, CG.Likely);
              Expr.compileBranch(p.expr, CG.No_label, top, CG.Likely);
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
        if (oc.contains(Stmt.Outcome.Exits)) {
            oc.add(Stmt.Outcome.FallThrough);
            oc.remove(Stmt.Outcome.Exits);
        }
        return oc;
    }
}
