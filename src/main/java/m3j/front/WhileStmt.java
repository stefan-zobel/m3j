// Java port of CM3's m3-sys/m3front/src/stmts/WhileStmt.i3 and WhileStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

// File: WhileStmt.i3
// Last Modified On Fri Jun 24 09:22:43 PDT 1994 By kalsow

// File: WhileStmt.m3
// Last modified on Wed Nov  9 14:36:06 PST 1994 by kalsow
//      modified on Tue Oct 10 18:42:00 1989 by muller

public final class WhileStmt {
    private WhileStmt() {}

    public static final class P extends Stmt.T {
        public Expr.T cond;
        public Stmt.T body;

        @Override
        public void check(M3.CheckState cs) {
            WhileStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return WhileStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    public static Stmt.T parse() {
        P p = new P();
        StmtRep.init(p);
        Scanner.match(Token.T.tWHILE);
        p.cond = Expr.parse();
        Scanner.match(Token.T.tDO);
        p.body = Stmt.parse();
        Scanner.match(Token.T.tEND);
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T t;
        Expr.typeCheck(p.cond, cs);
        t = Expr.typeOf(p.cond);
        if ((Type.base(t) != Bool.T) && (t != ErrType.T)) {
            Error.msg("WHILE condition must be a BOOLEAN");
        }
        Marker.pushExit(CG.No_label);
        Stmt.typeCheck(p.body, cs);
        Marker.pop();
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        EnumSet<Stmt.Outcome> oc;
        long top = CG.next_label(3);
        Marker.pushExit(top + 2);
          CG.jump(top + 1);
          CG.set_label(top);
          oc = Stmt.compile(p.body);
          CG.gen_location(p.origin);
          CG.set_label(top + 1);
          Expr.prepBranch(p.cond, top, CG.No_label, CG.Likely);
          Expr.compileBranch(p.cond, top, CG.No_label, CG.Likely);
          CG.set_label(top + 2);
        Marker.pop();

        // A WHILE statement can always FallThrough; consider the case where
        // the condition is initially FALSE
        oc.add(Stmt.Outcome.FallThrough);
        oc.remove(Stmt.Outcome.Exits);
        return oc;
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        EnumSet<Stmt.Outcome> oc = Stmt.getOutcome(p.body);
        oc.add(Stmt.Outcome.FallThrough);
        oc.remove(Stmt.Outcome.Exits);
        return oc;
    }
}
