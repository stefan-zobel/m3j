// Java port of CM3's m3-sys/m3front/src/stmts/RaiseStmt.i3 and RaiseStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3ID;

// File: RaiseStmt.i3
// Last Modified On Fri Jun 24 09:32:48 PDT 1994 By kalsow

// File: RaiseStmt.m3
// Last modified on Fri Jun 24 15:50:11 PDT 1994 by kalsow
//      modified on Sat Jan  5 04:47:38 1991 by muller

public final class RaiseStmt {
    private RaiseStmt() {}

    public static final class P extends Stmt.T {
        public Scope.T scope;
        public final M3.QID qid = new M3.QID();
        public Value.T except;
        public Expr.T arg;

        @Override
        public void check(M3.CheckState cs) {
            RaiseStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return RaiseStmt.compile(this);
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
        Scanner.match(Token.T.tRAISE);
        p.scope = Scope.top();
        p.except = null;
        p.arg = null;
        p.qid.module = M3ID.NoID;
        p.qid.item = Scanner.matchID();
        if ((Scanner.cur.token == Token.T.tDOT)) {
            Scanner.getToken(); // .
            p.qid.module = p.qid.item;
            p.qid.item = Scanner.matchID();
        }
        if ((Scanner.cur.token == Token.T.tLPAREN)) {
            Scanner.getToken(); // (
            p.arg = Expr.parse();
            Scanner.match(Token.T.tRPAREN);
        }
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T t, u;
        Value.T v;
        Expr.typeCheck(p.arg, cs);
        t = Expr.typeOf(p.arg);

        v = Scope.lookUpQID(p.scope, p.qid);
        Value.typeCheck(v, cs);
        if ((v == null)) {
            Error.QID(p.qid, "undefined");
        } else if ((Value.classOf(v) != Value.Class.Exception)) {
            Error.QID(p.qid, "not an exception");
        } else {
            p.except = v;
            ESet.noteException(cs, v);
        }

        u = Exceptionz.argType(p.except);
        if ((p.except == null)) {
            // we've already generated an error...
            Expr.typeCheck(p.arg, cs);
        } else if ((u == null)) {
            // takes no argument
            if ((p.arg != null)) {
                Error.QID(p.qid, "exception takes no argument");
            }
        } else if ((p.arg == null)) {
            Error.QID(p.qid, "exception requires an argument");
        } else if (!Type.isAssignable(u, t)) {
            Error.QID(p.qid, "argument has wrong type");
        } else {
            // We want p.arg compiled into a temp -> No NoteUseTargetVar
            AssignStmt.check(u, p.arg, cs);
        }
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        Exceptionz.emitRaise(p.except, p.arg);
        return EnumSet.noneOf(Stmt.Outcome.class); // Raises
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        return EnumSet.noneOf(Stmt.Outcome.class); // Raises
    }
}
