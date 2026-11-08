// Java port of CM3's m3-sys/m3front/src/stmts/BlockStmt.i3 and BlockStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3ID;

// File: BlockStmt.i3
// Last Modified On Fri Jun 24 08:56:15 PDT 1994 By kalsow

// File: BlockStmt.m3
// Last modified on Fri Jun 24 15:49:52 PDT 1994 by kalsow
//      modified on Fri Feb 23 07:15:45 1990 by muller

public final class BlockStmt {
    private BlockStmt() {}

    public static final class P extends Stmt.T {
        public Scope.T scope;
        public Stmt.T body;
        public ESet.T fails;
        public TraceNode trace;

        @Override
        public void check(M3.CheckState cs) {
            BlockStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return BlockStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    public static Stmt.T parse(boolean needScope) {
        P p;
        ESet.T[] fails = new ESet.T[1];
        p = new P();
        StmtRep.init(p);
        p.fails = null;

        if ((needScope)) {
            p.scope = Scope.pushNew(true, M3ID.NoID, false, /* nested := */ true);
        } else {
            p.scope = null;
        }

        while (Token.DeclStart.contains(Scanner.cur.token)) {
            // Port: p.fails is the VAR parameter; it is passed in fails[0].
            fails[0] = p.fails;
            Decl.parse(false, false, fails);
            p.fails = fails[0];
        }

        Scanner.match(Token.T.tBEGIN);
          p.trace = (TraceNode) parseTrace();
          p.body = Stmt.parse();
        Scanner.match(Token.T.tEND);

        if ((needScope)) {
            Scope.popNew();
        }
        return p;
    }

    public static Stmt.T parse() {
        return parse(true);
    }

    public static ESet.T extractFails(Stmt.T t) {
        ESet.T x;
        if (t == null) {
            return null;
        } else if (t instanceof P p) {
            x = p.fails;
            p.fails = null;
            return x;
        } else {
            return null;
        }
    }

    public static long bodyOffset(Stmt.T t) {
        if (t == null) {
            return Scanner.offset;
        } else if (t instanceof P p) {
            if ((p.body != null)) {
                return p.body.origin;
            } else {
                return Scanner.offset;
            }
        } else {
            return Scanner.offset;
        }
    }

    private static void check(P p, M3.CheckState cs) {
        Scope.T old = null, new_;
        new_ = p.scope;
        if ((new_ != null)) {
            old = Scope.push(new_);
        }
        ESet.typeCheck(p.fails);
        ESet.push(cs, null, p.fails, /* stop := */ false);
          if ((new_ != null)) {
              Scope.typeCheck(new_, cs);
          }
          if ((p.trace != null)) {
              Stmt.typeCheck(p.trace.body, cs);
          }
          Stmt.typeCheck(p.body, cs);
          if ((new_ != null)) {
              Scope.warnUnused(new_);
          }
        ESet.pop(cs, null, p.fails, /* stop := */ false);
        if ((new_ != null)) {
            Scope.pop(old);
        }
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        EnumSet<Stmt.Outcome> oc;
        Scope.T zz;
        if ((p.scope != null)) {
            zz = Scope.push(p.scope);
            Scope.enter(p.scope);
            Scope.initValues(p.scope);
            Tracer.push(p.trace);
            oc = Stmt.compile(p.body);
            Tracer.pop(p.trace);
            Scope.exit(p.scope);
            Scope.pop(zz);
        } else {
            Tracer.push(p.trace);
            oc = Stmt.compile(p.body);
            Tracer.pop(p.trace);
        }
        return oc;
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        return Stmt.getOutcome(p.body);
    }

    //----------------------------------------------------- tracing support ---

    public static final class TraceNode extends Tracer.T {
        public Stmt.T body;

        @Override
        public void apply() {
            doTrace(this);
        }
    }

    public static Tracer.T parseTrace() {
        Stmt.T s;
        if ((Scanner.cur.token != Token.T.tTRACE)) {
            return null;
        }
        Scanner.match(Token.T.tTRACE);
        s = Stmt.parse();
        Scanner.match(Token.T.tENDPRAGMA);
        if ((s == null)) {
            return null;
        }
        TraceNode n = new TraceNode();
        n.body = s;
        return n;
    }

    private static void doTrace(TraceNode x) {
        Stmt.compile(x.body);
    }

    public static void checkTrace(Tracer.T tt, M3.CheckState cs) {
        TraceNode x = (TraceNode) tt;
        if ((tt == null)) {
            return;
        }
        Stmt.typeCheck(x.body, cs);
    }
}
