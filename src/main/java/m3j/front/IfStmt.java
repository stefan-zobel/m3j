// Java port of CM3's m3-sys/m3front/src/stmts/IfStmt.i3 and IfStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

// File: IfStmt.i3
// Last modified On Fri Jun 24 09:21:30 PDT 1994 By kalsow

// File: IfStmt.m3
// Last modified on Thu Nov 10 13:30:29 PST 1994 by kalsow
//      modified on Wed Feb 27 04:00:55 1991 by muller

public final class IfStmt {
    private IfStmt() {}

    public static final class P extends Stmt.T {
        public Clause clauses;
        public Stmt.T elseBody;

        @Override
        public void check(M3.CheckState cs) {
            IfStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return IfStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    /** Port: Clause = REF RECORD ... END. */
    public static final class Clause {
        public long origin;
        public Clause next;
        public Expr.T cond;
        public Stmt.T body;
    }

    public static Stmt.T parse() {
        P p = new P();
        Clause c, last;
        StmtRep.init(p);

        Scanner.match(Token.T.tIF);
        c = new Clause();
        c.origin = Scanner.offset;
        c.next = null;
        c.cond = Expr.parse();
        Scanner.match(Token.T.tTHEN);
        c.body = Stmt.parse();
        p.clauses = c;
        p.elseBody = null;
        last = c;

        while ((Scanner.cur.token == Token.T.tELSIF)) {
            Scanner.getToken(); // ELSIF
            c = new Clause();
            c.origin = Scanner.offset;
            c.next = null;
            c.cond = Expr.parse();
            Scanner.match(Token.T.tTHEN);
            c.body = Stmt.parse();
            last.next = c;
            last = c;
        }

        if ((Scanner.cur.token == Token.T.tELSE)) {
            Scanner.getToken(); // ELSE
            p.elseBody = Stmt.parse();
        }

        Scanner.match(Token.T.tEND);
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Clause c;
        Type.T t;
        c = p.clauses;
        while ((c != null)) {
            Expr.typeCheck(c.cond, cs);
            t = Expr.typeOf(c.cond);
            if ((Type.base(t) != Bool.T) && (t != ErrType.T)) {
                Scanner.offset = c.origin;
                Error.msg("IF condition must be a BOOLEAN");
            }
            Stmt.typeCheck(c.body, cs);
            c = c.next;
        }
        Stmt.typeCheck(p.elseBody, cs);
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        Clause c;
        long l_end;
        long l_next;
        EnumSet<Stmt.Outcome> oc, xc;
        boolean gotoEnd = false;
        l_end = CG.next_label();

        c = p.clauses;
        oc = EnumSet.noneOf(Stmt.Outcome.class);
        while ((c != null)) {
            l_next = CG.next_label();
            Scanner.offset = c.origin;
            CG.gen_location(Scanner.offset);
            Expr.prepBranch(c.cond, CG.No_label, l_next, CG.Always - CG.Likely);
            Expr.compileBranch(c.cond, CG.No_label, l_next, CG.Always - CG.Likely);
            xc = Stmt.compile(c.body);
            oc.addAll(xc);
            if (xc.contains(Stmt.Outcome.FallThrough)
                && ((c.next != null) || (p.elseBody != null))) {
                CG.jump(l_end);
                gotoEnd = true;
            }
            CG.set_label(l_next);
            c = c.next;
        }

        if ((p.elseBody == null)) {
            oc.add(Stmt.Outcome.FallThrough);
        } else {
            oc.addAll(Stmt.compile(p.elseBody));
        }

        if ((gotoEnd)) {
            CG.set_label(l_end);
        }
        return oc;
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        Clause c;
        EnumSet<Stmt.Outcome> oc = EnumSet.noneOf(Stmt.Outcome.class);
        c = p.clauses;
        while ((c != null)) {
            oc.addAll(Stmt.getOutcome(c.body));
            c = c.next;
        }
        if ((p.elseBody == null)) {
            oc.add(Stmt.Outcome.FallThrough);
        } else {
            oc.addAll(Stmt.getOutcome(p.elseBody));
        }
        return oc;
    }
}
