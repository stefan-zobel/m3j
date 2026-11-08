// Java port of CM3's m3-sys/m3front/src/stmts/DebugStmt.i3 and DebugStmt.m3.
// Original: Copyright 1996-2000, Critical Mass, Inc.  All rights reserved.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

public final class DebugStmt {
    private DebugStmt() {}

    public static final class P extends Stmt.T {
        public Expr.T cond;
        public long n_msgs;
        public Expr.T[] msgs;

        @Override
        public void check(M3.CheckState cs) {
            DebugStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return DebugStmt.compile(this);
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
        Scanner.match(Token.T.tDEBUG);
        p.cond = null;
        p.n_msgs = 0;
        p.msgs = new Expr.T[4];
        while ((Scanner.cur.token != Token.T.tENDPRAGMA)) {
            if ((p.n_msgs >= p.msgs.length)) {
                expandMsgs(p);
            }
            p.msgs[(int) p.n_msgs] = Expr.parse();
            p.n_msgs++;
            if ((p.n_msgs == 1)) {
                if (Scanner.cur.token == Token.T.tWITH) {
                    p.cond = p.msgs[0];
                    p.n_msgs = 0;
                } else if (Scanner.cur.token != Token.T.tCOMMA) {
                    break;
                }
            } else {
                if (Scanner.cur.token != Token.T.tCOMMA) {
                    break;
                }
            }
            Scanner.getToken(); // "," or "WITH"
        }
        if ((Scanner.cur.token != Token.T.tENDPRAGMA)) {
            Scanner.fail("missing '*>'");
        }
        Scanner.cur.token = Token.T.tSEMI; // for the statement parser
        return p;
    }

    private static void expandMsgs(P p) {
        int n = p.msgs.length;
        Expr.T[] new_ = new Expr.T[n + n];
        System.arraycopy(p.msgs, 0, new_, 0, n);
        p.msgs = new_;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T t;
        boolean shift = false;
        if ((p.cond != null)) {
            Expr.typeCheck(p.cond, cs);
            t = Type.base(Expr.typeOf(p.cond));
            if ((t != Bool.T) && (t != ErrType.T)) {
                Error.msg("ASSERT condition must be a BOOLEAN");
            }
        }

        for (long i = 0; i <= p.n_msgs - 1; i++) {
            Expr.typeCheck(p.msgs[(int) i], cs);
            t = Type.base(Expr.typeOf(p.msgs[(int) i]));
            if ((i == 0) && (p.cond == null) && (t == Bool.T)) {
                // the first "msg" is really the condition to test
                shift = true;
            } else if (Type.isAssignable(Textt.T, t)) {
                p.msgs[(int) i] = checkArg(Textt.T, t, p.msgs[(int) i], cs);
            } else {
                Error.msg("DEBUG message must be assignable to TEXT");
            }
        }

        if ((shift)) {
            p.cond = p.msgs[0];
            // Sic: when n_msgs = NUMBER (msgs^), msgs[n_msgs] is a subscript
            // out of range.
            for (long i = 0; i <= p.n_msgs - 1; i++) {
                p.msgs[(int) i] = p.msgs[(int) (i + 1)];
            }
            p.n_msgs--;
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
        if (Host.doDebugs) {
            TInt.assign(i, TInt.MOne);
            v = Expr.constValue(p.cond);
            if ((p.cond == null)) {
                // DEBUG with no test condition
                prepMsgs(p);
                emitDebug(p);
            } else if ((v == null)) {
                // DEBUG with non-constant test condition
                skip = CG.next_label();
                prepMsgs(p);
                Expr.prepBranch(p.cond, CG.No_label, skip, CG.Always);
                Expr.compileBranch(p.cond, CG.No_label, skip, CG.Always);
                emitDebug(p);
                CG.set_label(skip);
            } else if (EnumExpr.split(v, i, u) && TInt.EQ(i, TInt.Zero)) {
                // DEBUG (FALSE)
            } else {
                AssertError.check(TInt.EQ(i, TInt.One), "DebugStmt");
                // DEBUG (TRUE)
                prepMsgs(p);
                emitDebug(p);
            }
        }
        return EnumSet.of(Stmt.Outcome.FallThrough);
    }

    private static void prepMsgs(P p) {
        for (long i = 0; i <= p.n_msgs - 1; i++) {
            Expr.prep(p.msgs[(int) i]);
        }
    }

    private static void emitDebug(P p) {
        Procedure.T proc;
        String[] this_file = new String[1];
        long[] this_line = new long[1];
        M3CG.Var msgs =
            CG.declare_temp(M3.target.Address.pack + M3.target.Integer.pack
                              + p.n_msgs * M3.target.Address.pack,
                            M3.target.Address.align, CGType.Struct,
                            /* in_memory := */ true);
        long offset;
        Scanner.here(this_file, this_line);

        // initialize the open-array pointer to the msgs
        CG.load_addr_of(msgs, M3.rt.OA_size_1, M3.target.Address.align);
        CG.store_addr(msgs, M3.rt.OA_elt_ptr);

        // initialize the count of array sizes
        CG.load_intt(p.n_msgs);
        CG.store_int(M3.target.Integer.cg_type, msgs, M3.rt.OA_size_0);

        // initialize each message
        offset = M3.rt.OA_size_1;
        for (long i = 0; i <= p.n_msgs - 1; i++) {
            Expr.compile(p.msgs[(int) i]);
            CG.store_addr(msgs, offset);
            offset += M3.target.Address.pack;
        }

        proc = RunTyme.lookUpProc(RunTyme.Hook.DebugMsg);
        Procedure.startCall(proc);
        if (M3.target.DefaultCall.args_left_to_right) {
            // module data pointer
              CG.load_addr_of(Module.globalData(false), 0, CG.Max_alignment);
              CG.pop_param(CGType.Addr);
            // line number
              CG.load_intt(this_line[0]);
              CG.pop_param(M3.target.Integer.cg_type);
            // messages
              CG.load_addr_of(msgs, 0, M3.target.Address.align);
              CG.pop_param(CGType.Addr);
        } else {
            // messages
              CG.load_addr_of(msgs, 0, M3.target.Address.align);
              CG.pop_param(CGType.Addr);
            // line number
              CG.load_intt(this_line[0]);
              CG.pop_param(M3.target.Integer.cg_type);
            // module data pointer
              CG.load_addr_of(Module.globalData(false), 0, CG.Max_alignment);
              CG.pop_param(CGType.Addr);
        }
        Procedure.emitCall(proc);
        CG.free_temp(msgs);
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        return EnumSet.of(Stmt.Outcome.FallThrough);
    }
}
