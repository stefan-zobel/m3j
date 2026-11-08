// Java port of CM3's m3-sys/m3front/src/stmts/TryFinStmt.i3 and TryFinStmt.m3.
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
import m3j.util.AssertError;
import m3j.util.RangeError;

// File: TryFinStmt.i3
// Last Modified On Fri Jun 24 09:32:47 PDT 1994 By kalsow
//      Modified On Mon Nov 27 16:24:34 1989 By muller

// File: TryFinStmt.m3
// Last modified on Fri May 19 07:50:09 PDT 1995 by kalsow
//      modified on Thu Dec  5 17:19:13 PST 1991 by muller

public final class TryFinStmt {
    private TryFinStmt() {}

    public static final class P extends Stmt.T {
        public Stmt.T body;
        public Stmt.T finally_;
        public long forigin;
        public boolean viaProc;
        public Scope.T scope;
        public HandlerProc handler;
        public final Jmpbufs.Try jmpbufs = new Jmpbufs.Try();

        @Override
        public void check(M3.CheckState cs) {
            TryFinStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return TryFinStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    public static final class HandlerProc extends ProcBody.T {
        public P self;
        public M3CG.Var activation;
        public Jmpbufs.Proc jmpbufs;

        @Override
        public void gen_decl() {
            emitDecl(this);
        }

        @Override
        public void gen_body() {
            emitBody(this);
        }
    }

    private static long last_name = 0;
    private static long next_uid = 0;

    public static Stmt.T parse(Stmt.T body) {
        P p = new P();
        StmtRep.init(p);
        p.body = body;
        Scanner.match(Token.T.tFINALLY);
        p.forigin = Scanner.offset;
        if (M3.target.Has_stack_walker) {
            p.viaProc = false;
            p.scope = null;
            p.finally_ = Stmt.parse();
        } else {
            p.handler = new HandlerProc();
            p.handler.self = p;
            ProcBody.push(p.handler);
            p.scope = Scope.pushNew(true, M3ID.NoID);
            p.finally_ = Stmt.parse();
            Scope.popNew();
            ProcBody.pop();
        }
        Scanner.match(Token.T.tEND);
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Scope.T zz;
        EnumSet<Stmt.Outcome> oc;
        long name;
        Jmpbufs.checkTry(cs.jmpbufs, p.jmpbufs);
        Marker.pushFinally(CG.No_label, CG.No_label, CG.No_label, null);
        Stmt.typeCheck(p.body, cs);
        Marker.pop();
        TryStmt.pushHandler(null, 0, false);
        if (M3.target.Has_stack_walker) {
            Stmt.typeCheck(p.finally_, cs);
        } else {
            oc = Stmt.getOutcome(p.finally_);
            if (oc.contains(Stmt.Outcome.Exits) || oc.contains(Stmt.Outcome.Returns)) {
                p.viaProc = false;
                Stmt.typeCheck(p.finally_, cs);
            } else {
                p.viaProc = true;
                name = Math.floorMod(p.forigin, 10000L);
                p.handler.name = handlerName(name);
                if ((name == last_name)) {
                    next_uid++;
                    p.handler.name = p.handler.name + "_" + Long.toString(next_uid);
                } else {
                    last_name = name;
                    next_uid = 0;
                }
                zz = Scope.push(p.scope);
                  p.handler.jmpbufs = Jmpbufs.checkProcPush(cs.jmpbufs,
                                                            M3ID.add(M3.ids, p.handler.name));
                  Scope.typeCheck(p.scope, cs);
                  Stmt.typeCheck(p.finally_, cs);
                  Jmpbufs.checkProcPop(cs.jmpbufs, p.handler.jmpbufs);
                Scope.pop(zz);
            }
        }
        TryStmt.popHandler();
    }

    /** Port: ARRAY BOOLEAN OF TEXT, indexed by ORD (b). */
    private static final String[] Insert = { "_M3_LINE_", "_I3_LINE_" };

    private static String handlerName(long uid) {
        return M3ID.toText(M3.ids, Module.name(null))
               + Insert[Module.isInterface() ? 1 : 0]
               + Long.toString(uid);
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        if (M3.target.Has_stack_walker) {
            return compile1(p);
        } else if (p.viaProc) {
            return compile2(p);
        } else {
            return compile3(p);
        }
    }

    private static EnumSet<Stmt.Outcome> compile1(P p) {
        EnumSet<Stmt.Outcome> oc, xc, o;
        long lab, xx;
        M3CG.Var info;
        Procedure.T proc;
        boolean[] returnSeen = new boolean[1], exitSeen = new boolean[1];
        int[] catches = new int[] { 0 };
        // declare and initialize the info record
        info = CG.declare_local(M3ID.NoID, M3.target.Address.size, M3.target.Address.align,
                                CGType.Addr, 0, /* in_memory := */ true,
                                /* up_level := */ false, /* f := */ CG.Never);
        CG.load_nil();
        CG.store_addr(info, M3.rt.EA_exception);

        // compile the body
        lab = CG.next_label(4);
        CG.set_label(lab, /* barrier := */ true);
        CG.start_try();

        Marker.pushFinally(lab, lab + 1, lab + 2, info);
        Marker.saveFrame();
          oc = Stmt.compile(p.body);
        Marker.popFinally(returnSeen, exitSeen);

        CG.jump(lab + 2);

        // End the try block before the landing pad so that the finally handler
        // code (which follows at lab+2) is OUTSIDE the inner try region.  This
        // prevents exceptions raised inside the finally clause (e.g. by
        // ResumeRaiseEx) from being re-caught by this same catch, which would
        // create an infinite loop.  Any such exceptions propagate naturally to
        // an enclosing outer try block instead.
        CG.end_try();

        CG.set_label(lab + 1);
        CG.landing_pad(lab + 1, catches);
        CG.store_addr(info);
        CG.set_label(lab + 2);

        // set the "Compiler.ThisException()" globals
        TryStmt.pushHandler(info, 0, /* direct := */ false);

        // compile the handler
        Scanner.offset = p.forigin;
        CG.gen_location(p.forigin);
          xc = Stmt.compile(p.finally_);

        if (xc.contains(Stmt.Outcome.FallThrough)) {
            // exceptional outcome?
            CG.load_addr(info, M3.rt.EA_exception, M3.target.Address.align);
            CG.load_nil();
            CG.if_compare(CGType.Addr, M3CG.CompareOp.EQ, lab + 3, CG.Always);

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
            proc = RunTyme.lookUpProc(RunTyme.Hook.ResumeRaiseEx);
            Procedure.startCall(proc);
            CG.load_addr(info, 0, M3.target.Address.align);
            CG.pop_param(CGType.Addr);
            Procedure.emitCall(proc);
            CG.set_label(lab + 3, /* barrier := */ true);
        }

        // restore the "Compiler.ThisException()" globals
        TryStmt.popHandler();

        o = EnumSet.noneOf(Stmt.Outcome.class);
        if (xc.contains(Stmt.Outcome.FallThrough)) {
            o = EnumSet.copyOf(oc);
        }
        if (xc.contains(Stmt.Outcome.Exits)) {
            o.add(Stmt.Outcome.Exits);
        }
        if (xc.contains(Stmt.Outcome.Returns)) {
            o.add(Stmt.Outcome.Returns);
        }
        return o;
    }

    private static EnumSet<Stmt.Outcome> compile2(P p) {
        EnumSet<Stmt.Outcome> oc, xc, o;
        long lab;
        M3CG.Var frame;
        AssertError.check(p.viaProc, "TryFinStmt");

        // declare and initialize the info record
        frame = CG.declare_local(M3ID.NoID, M3.rt.EF2_SIZE, M3.target.Address.align,
                                 CGType.Struct, 0, /* in_memory := */ true,
                                 /* up_level := */ false, /* f := */ CG.Never);
        CG.load_procedure(p.handler.cg_proc);
        CG.store_addr(frame, M3.rt.EF2_handler);
        CG.load_static_link(p.handler.cg_proc);
        CG.store_addr(frame, M3.rt.EF2_frame);

        // compile the body
        lab = CG.next_label(2);
        CG.set_label(lab, /* barrier := */ true);
        Marker.pushFrame(frame, M3RT.HandlerClass.FinallyProc);
        Marker.pushFinallyProc(lab, lab + 1, frame, p.handler.cg_proc, p.handler.level);
          oc = Stmt.compile(p.body);
        Marker.pop();
        if (oc.contains(Stmt.Outcome.FallThrough)) {
            Marker.popFrame(frame);
            CG.start_call_direct(p.handler.cg_proc, p.handler.level, CGType.Void);
            // Shouldn't we pass the activation parameter here?
            // What value do we pass?
            CG.call_direct(p.handler.cg_proc, CGType.Void);
        }
        CG.set_label(lab + 1, /* barrier := */ true);

        // set the "Compiler.ThisException()" globals
        TryStmt.pushHandler(p.handler.activation, 0, /* direct := */ false);

        Scanner.offset = p.forigin;
        CG.gen_location(p.forigin);
        if ((Host.inline_nested_procs)) {
            CG.begin_procedure(p.handler.cg_proc);
            Jmpbufs.compileProcAllocateJmpbufs(p.handler.jmpbufs);
            xc = Stmt.compile(p.finally_);
            CG.exit_proc(CGType.Void);
            CG.end_procedure(p.handler.cg_proc);
        } else {
            CG.note_procedure_origin(p.handler.cg_proc);
            xc = Stmt.getOutcome(p.finally_);
        }

        // restore the "Compiler.ThisException()" globals
        TryStmt.popHandler();

        o = EnumSet.noneOf(Stmt.Outcome.class);
        if (xc.contains(Stmt.Outcome.FallThrough)) {
            o = EnumSet.copyOf(oc);
        }
        if (xc.contains(Stmt.Outcome.Exits)) {
            o.add(Stmt.Outcome.Exits);
        }
        if (xc.contains(Stmt.Outcome.Returns)) {
            o.add(Stmt.Outcome.Returns);
        }
        return o;
    }

    private static void emitDecl(HandlerProc x) {
        P p = x.self;
        M3CG.Proc par = null;
        long uid;
        if ((p.viaProc)) {
            if ((x.parent != null)) {
                par = x.parent.cg_proc;
            }
            x.cg_proc = CG.declare_procedure(M3ID.add(M3.ids, x.name), 1, CGType.Void,
                                             x.level, M3.target.DefaultCall,
                                             /* exported := */ false, /* parent := */ par);
            // Port: the assignment of Type.GlobalUID to a CG.TypeUID is
            // range-checked.
            uid = Type.globalUID(Addr.T);
            if (uid < Integer.MIN_VALUE || uid > Integer.MAX_VALUE) {
                throw new RangeError("TryFinStmt");
            }
            x.activation = CG.declare_param(M3ID.NoID, M3.target.Address.size,
                                            M3.target.Address.align, CGType.Addr,
                                            (int) uid,
                                            /* in_memory := */ false, /* up_level := */ false,
                                            /* f := */ CG.Always);
        }
    }

    private static void emitBody(HandlerProc x) {
        P p = x.self;
        if ((p.viaProc) && (!Host.inline_nested_procs)) {

            // set the "Compiler.ThisException()" globals
            TryStmt.pushHandler(x.activation, 0, /* direct := */ false);

            // generate the actual procedure
            Scanner.offset = p.forigin;
            CG.gen_location(p.forigin);
            CG.begin_procedure(x.cg_proc);
            Jmpbufs.compileProcAllocateJmpbufs(x.jmpbufs);
            Stmt.compile(p.finally_);
            CG.exit_proc(CGType.Void);
            CG.end_procedure(x.cg_proc);

            // restore the "Compiler.ThisException()" globals
            TryStmt.popHandler();

        }
    }

    private static EnumSet<Stmt.Outcome> compile3(P p) {
        EnumSet<Stmt.Outcome> oc, xc, o;
        long lab, xx;
        M3CG.Var frame;
        boolean[] returnSeen = new boolean[1], exitSeen = new boolean[1];
        Procedure.T proc;
        AssertError.check(!p.viaProc, "TryFinStmt");

        // declare and initialize the info record
        frame = CG.declare_local(M3ID.NoID, M3.rt.EF1_SIZE, M3.target.Address.align,
                                 CGType.Struct, 0, /* in_memory := */ true,
                                 /* up_level := */ false, /* f := */ CG.Never);
        CG.load_nil();
        CG.store_addr(frame, M3.rt.EF1_info + M3.rt.EA_exception);

        lab = CG.next_label(3);
        CG.set_label(lab, /* barrier := */ true);
        Marker.pushFrame(frame, M3RT.HandlerClass.Finally);
        Marker.captureState(frame, Jmpbufs.compileTryGetJmpbuf(p.jmpbufs), lab + 1);

        // compile the body
        Marker.pushFinally(lab, lab + 1, CG.No_label, frame);
          oc = Stmt.compile(p.body);
        Marker.popFinally(returnSeen, exitSeen);
        if (oc.contains(Stmt.Outcome.FallThrough)) {
            Marker.popFrame(frame);
        }
        CG.set_label(lab + 1, /* barrier := */ true);

        // set the "Compiler.ThisException()" globals
        TryStmt.pushHandler(frame, M3.rt.EF1_info, /* direct := */ true);

        // compile the handler
        Scanner.offset = p.forigin;
        CG.gen_location(p.forigin);
        xc = Stmt.compile(p.finally_);

        if (xc.contains(Stmt.Outcome.FallThrough)) {
            // generate the bizzare end-tests

            // exceptional outcome?
            CG.load_addr(frame, M3.rt.EF1_info + M3.rt.EA_exception, M3.target.Address.align);
            CG.load_nil();
            CG.if_compare(CGType.Addr, M3CG.CompareOp.EQ, lab + 2, CG.Always);

            if ((exitSeen[0])) {
                xx = CG.next_label();
                CG.load_int(M3.target.Integer.cg_type,
                            frame, M3.rt.EF1_info + M3.rt.EA_exception);
                CG.load_intt(Marker.Exit_exception);
                CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.NE, xx, CG.Always);
                Marker.emitExit();
                CG.set_label(xx);
            }

            if ((returnSeen[0])) {
                xx = CG.next_label();
                CG.load_int(M3.target.Integer.cg_type,
                            frame, M3.rt.EF1_info + M3.rt.EA_exception);
                CG.load_intt(Marker.Return_exception);
                CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.NE, xx, CG.Always);
                Marker.emitReturn(null, /* fromFinally := */ true);
                CG.set_label(xx);
            }

            // ELSE, a real exception is being raised => resume it
            proc = RunTyme.lookUpProc(RunTyme.Hook.ResumeRaiseEx);
            Procedure.startCall(proc);
            CG.load_addr_of(frame, M3.rt.EF1_info, M3.target.Address.align);
            CG.pop_param(CGType.Addr);
            Procedure.emitCall(proc);

            CG.set_label(lab + 2, /* barrier := */ true);
        }

        // restore the "Compiler.ThisException()" globals
        TryStmt.popHandler();

        o = EnumSet.noneOf(Stmt.Outcome.class);
        if (xc.contains(Stmt.Outcome.FallThrough)) {
            o = EnumSet.copyOf(oc);
        }
        if (xc.contains(Stmt.Outcome.Exits)) {
            o.add(Stmt.Outcome.Exits);
        }
        if (xc.contains(Stmt.Outcome.Returns)) {
            o.add(Stmt.Outcome.Returns);
        }
        return o;
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        EnumSet<Stmt.Outcome> oc, xc, o;
        oc = Stmt.getOutcome(p.body);
        xc = Stmt.getOutcome(p.finally_);
        o = EnumSet.noneOf(Stmt.Outcome.class);
        if (xc.contains(Stmt.Outcome.FallThrough)) {
            o = EnumSet.copyOf(oc);
        }
        if (xc.contains(Stmt.Outcome.Exits)) {
            o.add(Stmt.Outcome.Exits);
        }
        if (xc.contains(Stmt.Outcome.Returns)) {
            o.add(Stmt.Outcome.Returns);
        }
        return o;
    }
}
