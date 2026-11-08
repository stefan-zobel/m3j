// Java port of CM3's m3-sys/m3front/src/stmts/AssertStmt.i3 and AssertStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: AssertStmt.i3
// Last Modified On Fri Jun 24 08:55:56 PDT 1994 By kalsow
//      Modified On Fri Oct 13 18:09:17 1989 By muller

// File: AssertStmt.m3
// Last modified on Sat Nov 19 10:15:44 PST 1994 by kalsow
//      modified on Sat Mar 16 02:01:05 1991 by muller

public final class AssertStmt {
    private AssertStmt() {}

    public static final class P extends Stmt.T {
        public Expr.T cond;
        public Expr.T msg;

        @Override
        public void check(M3.CheckState cs) {
            AssertStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return AssertStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    private static final boolean includeTextInAsserts = true;

    public static Stmt.T parse() {
        P p;
        char[][] text = new char[][] { null };
        long[] size = new long[] { 0 };
        p = new P();
        StmtRep.init(p);
        if (includeTextInAsserts) {
            Scanner.enableTextCapture();
        }
        Scanner.match(Token.T.tASSERT);
        p.cond = Expr.parse();
        p.msg = null;
        if ((Scanner.cur.token == Token.T.tWITH)
            || (Scanner.cur.token == Token.T.tCOMMA)) {
            Scanner.getToken(); // "," or "WITH"
            p.msg = Expr.parse();
        }
        if (includeTextInAsserts) {
            Scanner.disableTextCapture(text, size);
            if (p.msg == null && size[0] > 3) { // 3 is to remove "*>"
                // Port: text^ dereferences text[0]; a NIL text is a
                // NullPointerException as the original's checked runtime error.
                p.msg = TextExpr.new8(M3String.fromStr(text[0], size[0] - 3));
            }
        }
        if ((Scanner.cur.token != Token.T.tENDPRAGMA)) {
            Scanner.fail("missing '*>'");
        }
        Scanner.cur.token = Token.T.tSEMI; // for the statement parser
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T t;
        Expr.typeCheck(p.cond, cs);
        t = Type.base(Expr.typeOf(p.cond));
        if ((t != Bool.T) && (t != ErrType.T)) {
            Error.msg("ASSERT condition must be a BOOLEAN");
        }
        if ((p.msg != null)) {
            Expr.typeCheck(p.msg, cs);
            t = Type.base(Expr.typeOf(p.msg));
            if (Type.isAssignable(Textt.T, t)) {
                p.msg = checkArg(Textt.T, t, p.msg, cs);
            } else {
                Error.msg("ASSERT message must be assignable to TEXT");
            }
        }
    }

    private static Expr.T checkArg(Type.T tlhs, Type.T trhs, Expr.T e,
                                   M3.CheckState cs) {
        AssignStmt.check(tlhs, e, cs);
        if (Host.doNarrowChk && !Type.isSubtype(trhs, tlhs)) {
            e = NarrowExpr.new_(e, tlhs);
            Expr.typeCheck(e, cs);
        }
        return e;
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        Expr.T v;
        int[] i = TInt.newInt();
        Type.T[] u = new Type.T[1];
        long skip;
        if (Host.doAsserts) {
            TInt.assign(i, TInt.MOne);
            v = Expr.constValue(p.cond);
            if ((v == null)) {
                skip = CG.next_label();
                if ((p.msg != null)) {
                    Expr.prep(p.msg);
                }
                Expr.prepBranch(p.cond, skip, CG.No_label, CG.Always);
                Expr.compileBranch(p.cond, skip, CG.No_label, CG.Always);
                crash(p);
                CG.set_label(skip);
            } else if (EnumExpr.split(v, i, u) && TInt.EQ(i, TInt.Zero)) {
                // ASSERT (FALSE)
                if ((p.msg != null)) {
                    Expr.prep(p.msg);
                }
                crash(p);
                return EnumSet.noneOf(Stmt.Outcome.class);
            } else {
                AssertError.check(TInt.EQ(i, TInt.One), "AssertStmt");
                // ASSERT (TRUE)
            }
        }
        return EnumSet.of(Stmt.Outcome.FallThrough);
    }

    private static void crash(P p) {
        Procedure.T proc;
        String[] this_file = new String[1];
        long[] this_line = new long[1];
        if ((p.msg == null)) {
            CG.abort(M3CG.RuntimeError.AssertFailed);
            return;
        }

        Scanner.here(this_file, this_line);
        proc = RunTyme.lookUpProc(RunTyme.Hook.AssertFailed);
        Procedure.startCall(proc);
        if (M3.target.DefaultCall.args_left_to_right) {
            // module data pointer
              CG.load_addr_of(Module.globalData(false), 0, CG.Max_alignment);
              CG.pop_param(CGType.Addr);
            // line number
              CG.load_intt(this_line[0]);
              CG.pop_param(M3.target.Integer.cg_type);
            // message
              Expr.compile(p.msg);
              CG.pop_param(CGType.Addr);
        } else {
            // message
              Expr.compile(p.msg);
              CG.pop_param(CGType.Addr);
            // line number
              CG.load_intt(this_line[0]);
              CG.pop_param(M3.target.Integer.cg_type);
            // module data pointer
              CG.load_addr_of(Module.globalData(false), 0, CG.Max_alignment);
              CG.pop_param(CGType.Addr);
        }
        Procedure.emitCall(proc);
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        return EnumSet.of(Stmt.Outcome.FallThrough);
    }
}
