// Java port of CM3's m3-sys/m3front/src/stmts/LockStmt.i3 and LockStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.Target.CGType;

// File: LockStmt.i3
// Last Modified On Fri Jun 24 09:21:38 PDT 1994 By kalsow

// File: LockStmt.m3
// Last modified on Fri Jun 24 15:50:01 PDT 1994 by kalsow
//      modified on Sun Jan 21 07:49:28 1990 by muller

public final class LockStmt {
    private LockStmt() {}

    public static final class P extends Stmt.T {
        public Expr.T mutex;
        public Stmt.T body;
        public long tail;

        @Override
        public void check(M3.CheckState cs) {
            LockStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return LockStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    public static Stmt.T parse() {
        P p = new P();
        StmtRep.init(p);
        Scanner.match(Token.T.tLOCK);
        p.mutex = Expr.parse();
        Scanner.match(Token.T.tDO);
        p.body = Stmt.parse();
        p.tail = Scanner.offset;
        Scanner.match(Token.T.tEND);
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T t;
        Expr.typeCheck(p.mutex, cs);
        t = Expr.typeOf(p.mutex);
        if (!Type.isSubtype(t, Mutex.T)) {
            Error.msg("expression must be a mutex");
        }
        Marker.pushLock(CG.No_label, CG.No_label, null);
        Stmt.typeCheck(p.body, cs);
        Marker.pop();
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        if (M3.target.Has_stack_walker) {
            return compile1(p);
        } else {
            return compile2(p);
        }
    }

    private static EnumSet<Stmt.Outcome> compile1(P p) {
        EnumSet<Stmt.Outcome> oc;
        long lab, xx;
        M3CG.Var info, mu;
        boolean[] returnSeen = new boolean[1], exitSeen = new boolean[1];
        Procedure.T proc;
        int[] catches = new int[] { 0 };
        // declare and initialize the info record
        info = CG.declare_local(M3ID.NoID, M3.target.Address.size, M3.target.Address.align,
                                CGType.Addr, 0, /* in_memory := */ true,
                                /* up_level := */ false, /* f := */ CG.Never);

        CG.load_nil();
        CG.store_addr(info, M3.rt.EA_exception);

        // capture the mutex expression
        Expr.prep(p.mutex);
        Expr.compile(p.mutex);
        mu = CG.declare_local(M3ID.NoID, M3.target.Address.size,
                              M3.target.Address.align, CGType.Addr,
                              (int) Type.globalUID(Mutex.T), /* in_memory := */ true,
                              /* up_level := */ false, /* f := */ CG.Never);
        CG.store_addr(mu);

        // acquire the lock
        Marker.setLock(true, mu, 0);
        Expr.noteWrite(p.mutex);

        // compile the body
        lab = CG.next_label(4);
        CG.set_label(lab, /* barrier := */ true);
        CG.start_try();

        Marker.pushFinally(lab, lab + 1, lab + 2, info);
        Marker.saveFrame();
          oc = Stmt.compile(p.body);
        Marker.popFinally(returnSeen, exitSeen);

        // jump over the exc handler
        CG.jump(lab + 2);

        // End the try block here so that the lock-release and re-raise code
        // (which follows at lab+2) is OUTSIDE the try region.  This prevents
        // exceptions from Thread.Release or ResumeRaiseEx from being re-caught
        // by this same catch clause.  Any such exceptions propagate naturally
        // to an enclosing outer try block instead.
        CG.end_try();

        CG.set_label(lab + 1, /* barrier := */ true);
        CG.landing_pad(lab + 1, catches);
        CG.store_addr(info);
        CG.set_label(lab + 2);

        // release the lock
        Marker.setLock(false, mu, 0);
        Expr.noteWrite(p.mutex);

        if ((exitSeen[0])) {
            xx = CG.next_label();
            CG.load_addr(info, M3.rt.EA_exception, M3.target.Address.align);
            CG.loophole(CGType.Addr, M3.target.Integer.cg_type);
            CG.load_intt(Marker.Exit_exception);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.NE, xx, CG.Always);
            Marker.emitExit();
            CG.set_label(xx);
        }

        if ((returnSeen[0])) {
            xx = CG.next_label();
            CG.load_addr(info, M3.rt.EA_exception, M3.target.Address.align);
            CG.loophole(CGType.Addr, M3.target.Integer.cg_type);
            CG.load_intt(Marker.Return_exception);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.NE, xx, CG.Always);
            Marker.emitReturn(null, /* fromFinally := */ true);
            CG.set_label(xx);
        }

        // resume the exception
        CG.load_addr(info, M3.rt.EA_exception, M3.target.Address.align);
        CG.load_nil();
        CG.if_compare(CGType.Addr, M3CG.CompareOp.EQ, lab + 3, CG.Always);
        proc = RunTyme.lookUpProc(RunTyme.Hook.ResumeRaiseEx);
        Procedure.startCall(proc);
        CG.load_addr(info, 0, M3.target.Address.align);
        CG.pop_param(CGType.Addr);
        Procedure.emitCall(proc);

        CG.gen_location(p.tail);
        CG.set_label(lab + 3, /* barrier := */ true);
        return oc;
    }

    private static EnumSet<Stmt.Outcome> compile2(P p) {
        EnumSet<Stmt.Outcome> oc;
        M3CG.Var frame;
        long l;
        // capture the mutex expression
        Expr.prep(p.mutex);
        Expr.compile(p.mutex);
        frame = CG.declare_local(M3ID.NoID, M3.rt.EF4_SIZE, M3.target.Address.align,
                                 CGType.Struct, 0, /* in_memory := */ true,
                                 /* up_level := */ false, /* f := */ CG.Never);
        CG.store_addr(frame, M3.rt.EF4_mutex);

        // acquire the lock
        Marker.setLock(true, frame, M3.rt.EF4_mutex);
        Expr.noteWrite(p.mutex);

        // set the barrier and link the frame
        l = CG.next_label(2);
        CG.set_label(l, /* barrier := */ true);
        Marker.pushFrame(frame, M3RT.HandlerClass.Lock);

        // compile the body
        Marker.pushLock(l, l + 1, frame);
          oc = Stmt.compile(p.body);
        Marker.pop();

        CG.gen_location(p.tail);
        CG.set_label(l + 1, /* barrier := */ true);

        if (oc.contains(Stmt.Outcome.FallThrough)) {
            // unlink the frame and release the lock
            Marker.popFrame(frame);
            Marker.setLock(false, frame, M3.rt.EF4_mutex);
            Expr.noteWrite(p.mutex);
        }

        return oc;
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        return Stmt.getOutcome(p.body);
    }
}
