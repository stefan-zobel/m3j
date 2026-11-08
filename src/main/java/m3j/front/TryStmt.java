// Java port of CM3's m3-sys/m3front/src/stmts/TryStmt.i3 and TryStmt.m3.
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

// File: TryStmt.i3
// Last Modified On Fri Jun 24 09:32:46 PDT 1994 By kalsow

// File: TryStmt.m3
// Last modified on Tue May 23 15:36:40 PDT 1995 by kalsow
//      modified on Fri Oct  5 06:40:21 1990 by muller

public final class TryStmt {
    private TryStmt() {}

    public static final class P extends Stmt.T {
        public Scope.T scope;
        public Stmt.T body;
        public long h_origin;
        public Handler handles;
        public boolean hasElse;
        public Stmt.T elseBody;
        public ESet.T handled;
        public final Jmpbufs.Try jmpbufs = new Jmpbufs.Try();

        @Override
        public void check(M3.CheckState cs) {
            TryStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return TryStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    /** Port: Handler = REF RECORD ... END. */
    public static final class Handler {
        public Handler next;
        public Except tags;
        public Type.T type;
        public Variable.T var_;
        public Scope.T scope;
        public Stmt.T body;
        public long origin;
    }

    /** Port: Except = REF RECORD ... END. */
    public static final class Except {
        public Except next;
        public final M3.QID name = new M3.QID();
        public Value.T obj;
    }

    // Port: Caught = REF ARRAY OF CG.TypeUID is int[].

    public static Stmt.T parse() {
        Stmt.T s;
        P p;
        boolean bar;
        long here = Scanner.offset;
        Scanner.match(Token.T.tTRY);
        s = Stmt.parse();
        if ((Scanner.cur.token == Token.T.tFINALLY)) {
            s = TryFinStmt.parse(s);
            s.origin = here;
            return s;
        }

        p = new P();
        StmtRep.init(p);
        p.origin = here;
        p.scope = Scope.top();
        p.body = s;
        p.hasElse = false;
        p.elseBody = null;
        p.handled = null;
        p.handles = null;

        Scanner.match(Token.T.tEXCEPT);
        p.h_origin = Scanner.offset;
        bar = (Scanner.cur.token == Token.T.tBAR);
        if ((bar)) {
            Scanner.getToken(); // |
        }
        while (true) {
            if ((Scanner.cur.token == Token.T.tELSE)) {
                break;
            }
            if ((Scanner.cur.token == Token.T.tEND)) {
                break;
            }
            bar = false;
            parseHandler(p);
            if ((Scanner.cur.token != Token.T.tBAR)) {
                break;
            }
            bar = true;
            Scanner.getToken(); // |
        }

        reverseHandlers(p);
        if ((bar)) {
            Scanner.fail("missing handler");
        }

        if ((Scanner.cur.token == Token.T.tELSE)) {
            Scanner.getToken(); // ELSE
            p.hasElse = true;
            p.elseBody = Stmt.parse();
        }

        Scanner.match(Token.T.tEND);
        return p;
    }

    private static void parseHandler(P p) {
        Handler h;
        Except e;
        int id;
        Tracer.T trace;
        boolean u = false; // Exception argument Id has UNUSED pragma.
        h = new Handler();
        h.next = p.handles;
        p.handles = h;
        h.tags = null;
        h.type = null;
        h.var_ = null;
        h.scope = null;
        h.body = null;
        h.origin = Scanner.offset;
        while (true) {
            e = new Except();
            e.next = h.tags;
            h.tags = e;
            e.obj = null;
            e.name.module = M3ID.NoID;
            e.name.item = Scanner.matchID();
            if ((Scanner.cur.token == Token.T.tDOT)) {
                Scanner.getToken(); // .
                e.name.module = e.name.item;
                e.name.item = Scanner.matchID();
            }
            if ((Scanner.cur.token != Token.T.tCOMMA)) {
                break;
            }
            Scanner.getToken(); // ,
        }
        if ((Scanner.cur.token == Token.T.tLPAREN)) {
            Scanner.getToken(); // (
            if (Scanner.cur.token == Token.T.tUNUSED) {
                u = true;
                Scanner.getToken(); // UNUSED
                Scanner.match(Token.T.tENDPRAGMA);
            }
            id = Scanner.matchID();
            trace = Variable.parseTrace();
            Scanner.match(Token.T.tRPAREN);
            h.var_ = Variable.new_(id, u);
            h.scope = Scope.new1(h.var_);
            Variable.bindTrace(h.var_, trace);
            Scanner.match(Token.T.tIMPLIES);
            h.body = Stmt.parse();
            Scope.popNew();
        } else {
            Scanner.match(Token.T.tIMPLIES);
            h.body = Stmt.parse();
        }
    }

    private static void reverseHandlers(P p) {
        Handler h1, h2, h3;
        h1 = p.handles;
        h3 = null;
        while ((h1 != null)) {
            h2 = h1.next;
            h1.next = h3;
            h3 = h1;
            h1 = h2;
        }
        p.handles = h3;
    }

    private static void check(P p, M3.CheckState cs) {
        Handler h;
        ESet.T handled;
        Jmpbufs.checkTry(cs.jmpbufs, p.jmpbufs);
        h = p.handles;
        while ((h != null)) {
            checkLabels(h, p.scope, cs);
            h = h.next;
        }

        if ((p.hasElse)) {
            Marker.pushTryElse(CG.No_label, CG.No_label, CG.No_label, null);
            handled = ESet.newAny();
        } else {
            handled = ESet.newEmpty(p.scope);
            h = p.handles;
            while ((h != null)) {
                pushRaises(h, handled);
                h = h.next;
            }
            p.handled = handled;
            Marker.pushTry(CG.No_label, CG.No_label, CG.No_label, null, handled);
        }
        ESet.typeCheck(handled);

        ESet.push(cs, handled, null, /* stop := */ false);
        Stmt.typeCheck(p.body, cs);
        ESet.pop(cs, handled, null, /* stop := */ false);
        Marker.pop();

        // check each handler
        pushHandler(null, 0, false);
          h = p.handles;
          while ((h != null)) {
              checkHandler(h, cs);
              h = h.next;
          }
          Stmt.typeCheck(p.elseBody, cs);
        popHandler();
    }

    private static void checkLabels(Handler h, Scope.T scope, M3.CheckState cs) {
        Except e;
        Value.T obj;
        Type.T t;
        Scanner.offset = h.origin;
        e = h.tags;
        while ((e != null)) {
            obj = Scope.lookUpQID(scope, e.name);
            if ((obj == null)) {
                Error.QID(e.name, "undefined");
            }
            e.obj = obj;
            Value.typeCheck(obj, cs);
            if ((Value.classOf(obj) != Value.Class.Exception)) {
                Error.QID(e.name, "is not an exception");
            } else {
                if ((h.scope != null)) {
                    t = Exceptionz.argType(obj);
                    if ((e == h.tags)) { // first one
                        h.type = t;
                    } else if (!Type.isEqual(t, h.type, null)) {
                        Error.msg("exceptions have incompatible types");
                    }
                }
            }
            e = e.next;
        }
    }

    private static void pushRaises(Handler h, ESet.T handled) {
        Except e;
        Scanner.offset = h.origin;
        e = h.tags;
        while ((e != null)) {
            ESet.add(handled, e.name, e.obj);
            e = e.next;
        }
    }

    private static void checkHandler(Handler h, M3.CheckState cs) {
        Scope.T zz;
        Scanner.offset = h.origin;
        if ((h.scope != null)) {
            if ((h.type == null)) {
                Error.msg("exception(s) don't have a return argument");
            }
            Variable.bindType(h.var_, h.type, /* indirect := */ false, /* readonly := */ false,
                              /* open_array_ok := */ false, /* needs_init := */ false);
            if (Exceptionz.argByReference(h.type)) {
                Variable.needsAddress(h.var_);
            }
            Scope.typeCheck(h.scope, cs);
            zz = Scope.push(h.scope);
              Stmt.typeCheck(h.body, cs);
              Scope.warnUnused(h.scope);
            Scope.pop(zz);
        } else {
            Stmt.typeCheck(h.body, cs);
        }
    }

    private static int[] handled(P p) {
        ESet.EList el, ol, e;
        int[] catches;
        long n, i;
        boolean f = false;
        long uid;
        el = ESet.listE(p.handled);
        // add enclosing exception type uids
        ol = Marker.excepts();
        if (el == null) {
            el = ol;
            f = true;
        }

        // calc size of array
        e = el;
        n = 0;
        while (e != null) {
            if ((e.next == null) && (!f)) {
                // Sic: this appends ol to the list that ESet.ListE returned.
                e.next = ol;
                f = true;
            }
            e = e.next;
            n++;
        }

        if (n == 0) {
            catches = new int[1];
            catches[0] = 0;
        } else {
            catches = new int[(int) n];
            e = el;
            i = 0;
            while (e != null) {
                // Port: the assignment to a CG.TypeUID is range-checked.
                uid = Exceptionz.UID(e.e);
                if (uid < Integer.MIN_VALUE || uid > Integer.MAX_VALUE) {
                    throw new RangeError("TryStmt");
                }
                catches[(int) i] = (int) uid;
                e = e.next;
                i++;
            }
        }
        return catches;
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        if ((p.handles == null) && (!p.hasElse)) {
            return Stmt.compile(p.body);
        }
        if (M3.target.Has_stack_walker) {
            return compile1(p);
        } else {
            return compile2(p);
        }
    }

    private static EnumSet<Stmt.Outcome> compile1(P p) {
        EnumSet<Stmt.Outcome> oc;
        long l;
        long[] next_handler = new long[1], handler_body = new long[1];
        M3CG.Var info;
        M3CG.Var[] next_info = new M3CG.Var[1];
        Handler h;
        boolean another;
        int[] catches;
        // declare and initialize the info record

        info = CG.declare_local(M3ID.NoID, M3.target.Address.size,
                                M3.target.Address.align, CGType.Addr, 0,
                                /* in_memory := */ true, /* up_level := */ false,
                                /* f := */ CG.Never);

        CG.load_nil();
        CG.store_addr(info, M3.rt.EA_exception);

        // compile the body
        l = CG.next_label(4);
        CG.set_label(l, /* barrier := */ true);
        CG.start_try();
        if ((p.hasElse)) {
            Marker.pushTryElse(l, l + 1, l + 2, info);
        } else {
            Marker.pushTry(l, l + 1, l + 2, info, p.handled);
        }
        Marker.saveFrame();
          oc = Stmt.compile(p.body);
        Marker.pop();

        if (oc.contains(Stmt.Outcome.FallThrough)) {
            CG.jump(l + 3);
        }

        // End the try block here so handler code is OUTSIDE the try region.
        // The catch clause generated by End_try jumps to the landing pad (l+1).
        // Since handler code follows at the same lexical scope, any re-throw
        // from an unmatched handler is caught by an enclosing outer try block
        // (if any), not by this one -- avoiding the infinite-loop problem.
        CG.end_try();

        if ((p.hasElse)) {
            // EXITs and RETURNs from the body are caught by the ELSE clause
            oc.removeAll(EnumSet.of(Stmt.Outcome.Returns, Stmt.Outcome.Exits));
        }

        // check for enclosing handlers
        another = Marker.nextHandler(next_handler, handler_body, next_info);

        // set the "Compiler.ThisException()" globals
        pushHandler(info, 0, /* direct := */ false);

        CG.set_label(l + 1, /* barrier := */ true);
        Scanner.offset = p.h_origin;
        CG.gen_location(p.h_origin);

        // generate the landing pad
        catches = handled(p);
        CG.landing_pad(l + 1, catches);
        CG.store_addr(info);

        // set the body label
        CG.set_label(l + 2);

        // compile each of the handlers.
        // In C++ EH (ex_stack), we always emit the type check for every handler,
        // including the last one.  The C++ catch(_M3Exc&) catches ALL M3 exceptions
        // so unmatched ones must be explicitly re-raised -- passing last=FALSE to
        // CompileHandler1 ensures the type-check jump is always emitted.
        h = p.handles;
        while ((h != null)) {
            oc.addAll(compileHandler1(h, info, l + 3, false));
            h = h.next;
        }

        if ((p.hasElse)) {
            oc.addAll(Stmt.compile(p.elseBody));
        } else if (another) {
            // we didn't eat this exception => mark and invoke the next handler
            CG.load_addr(info, 0, M3.target.Address.align);
            CG.store_addr(next_info[0]);
            CG.jump(handler_body[0]);
        } else {
            // No handler matched, no outer handler, no ELSE clause.
            // The C++ catch already consumed the throw; re-raise it so that
            // enclosing C++ frames (e.g. FATAL ANY in the module body) can
            // process it.
            Procedure.T proc = RunTyme.lookUpProc(RunTyme.Hook.ResumeRaiseEx);
            Procedure.startCall(proc);
            CG.load_addr(info, 0, M3.target.Address.align);
            CG.pop_param(CGType.Addr);
            Procedure.emitCall(proc);
        }

        // restore the "Compiler.ThisException()" globals
        popHandler();
        CG.set_label(l + 3);
        return oc;
    }

    private static EnumSet<Stmt.Outcome> compileHandler1(Handler h, M3CG.Var info,
                                                         long resume, boolean last) {
        Except e;
        EnumSet<Stmt.Outcome> oc;
        Scope.T zz;
        long top;
        boolean need_top = false;
        Type.Info t_info = new Type.Info();
        top = CG.next_label(2);

        CG.gen_location(h.origin);

        if ((!last)) {
            // check for a match
            e = h.tags;
            AssertError.check(e != null, "TryStmt");
            while ((e != null)) {
                CG.load_addr(info, M3.rt.EA_exception, M3.target.Address.align);
                CG.boost_addr_alignment(M3.target.Address.align);
                CG.load_indirect(CGType.Addr, 0, M3.target.Address.size);
                CG.boost_addr_alignment(M3.target.Integer.align);
                CG.load_indirect(M3.target.Integer.cg_type, 0, M3.target.Integer.size);
                CG.load_intt(Exceptionz.UID(e.obj));
                e = e.next;
                if ((e != null)) {
                    CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.EQ, top, CG.Maybe);
                    need_top = true;
                } else {
                    CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.NE, top + 1, CG.Maybe);
                }
            }
        }

        if ((need_top)) {
            CG.set_label(top);
        }

        if ((h.scope != null)) {
            zz = Scope.push(h.scope);
              Scope.enter(h.scope);
              Scope.initValues(h.scope);
              Variable.loadLValue(h.var_);
              Type.checkInfo(h.type, t_info);
              if (Exceptionz.argByReference(h.type)) {
                  CG.load_addr(info, M3.rt.EA_exception, M3.target.Address.align);
                  CG.boost_addr_alignment(M3.target.Address.align);
                  CG.load_indirect(CGType.Addr, M3.rt.EA_arg, M3.target.Address.size);
                  CG.boost_addr_alignment(t_info.alignment);
                  CG.copy(t_info.size, /* overlap := */ false);
              } else {
                  CG.load_addr(info, M3.rt.EA_exception, M3.target.Address.align);
                  CG.boost_addr_alignment(M3.target.Address.align);
                  CG.load_indirect(CGType.Addr, M3.rt.EA_arg, M3.target.Address.size);
                  CG.loophole(CGType.Addr, t_info.stk_type);
                  CG.store_indirect(t_info.stk_type, 0, t_info.size);
              }
              Variable.scheduleTrace(h.var_);
              oc = Stmt.compile(h.body);
              if (oc.contains(Stmt.Outcome.FallThrough)) {
                  CG.jump(resume);
              }
              // for the debugger's sake, this Jump should be inside the scope
              Scope.exit(h.scope);
            Scope.pop(zz);
        } else {
            oc = Stmt.compile(h.body);
            if (oc.contains(Stmt.Outcome.FallThrough)) {
                CG.jump(resume);
            }
        }

        CG.set_label(top + 1);
        return oc;
    }

    private static EnumSet<Stmt.Outcome> compile2(P p) {
        EnumSet<Stmt.Outcome> oc;
        Handler h;
        long l;
        M3CG.Var frame;
        M3CG.Var[] eset = new M3CG.Var[1];
        long[] eoffset = new long[1];
        l = CG.next_label(3);
        CG.set_label(l, /* barrier := */ true);

        // declare and initialize the info record
        frame = CG.declare_local(M3ID.NoID, M3.rt.EF1_SIZE, M3.target.Address.align,
                                 CGType.Struct, 0, /* in_memory := */ true,
                                 /* up_level := */ false, /* f := */ CG.Never);

        // *********************************************
        // (* mark the frame: no exception happened yet *)
        // CG.Load_nil ();
        // CG.Store_addr (frame, M3RT.EF1_exception);
        // *********************************************

        if ((p.hasElse)) {
            Marker.pushTryElse(l, l + 1, 0, frame);
            Marker.pushFrame(frame, M3RT.HandlerClass.ExceptElse);
        } else {
            Marker.pushTry(l, l + 1, 0, frame, p.handled);
            ESet.getAddress(p.handled, eset, eoffset);
            CG.load_addr_of(eset[0], eoffset[0], M3.target.Integer.align);
            CG.store_addr(frame, M3.rt.EF1_handles);
            Marker.pushFrame(frame, M3RT.HandlerClass.Except);
        }

        // capture the machine state
        Marker.captureState(frame, Jmpbufs.compileTryGetJmpbuf(p.jmpbufs), l + 1);

        // compile the body
        oc = Stmt.compile(p.body);
        Marker.pop();
        if (oc.contains(Stmt.Outcome.FallThrough)) {
            Marker.popFrame(frame);
            CG.jump(l + 2);
        }

        if ((p.hasElse)) {
            // EXITs and RETURNs from the body are caught by the ELSE clause
            oc.removeAll(EnumSet.of(Stmt.Outcome.Returns, Stmt.Outcome.Exits));
        }

        // set the "Compiler.ThisException()" globals
        pushHandler(frame, M3.rt.EF1_info, /* direct := */ true);

        // compile each of the handlers
        CG.set_label(l + 1, /* barrier := */ true);
        Scanner.offset = p.h_origin;
        CG.gen_location(p.h_origin);
        h = p.handles;
        while ((h != null)) {
            oc.addAll(compileHandler2(h, frame, l + 2,
                                      (!p.hasElse) && (h.next == null)));
            h = h.next;
        }

        if ((p.hasElse)) {
            oc.addAll(Stmt.compile(p.elseBody));
        }

        // restore the "Compiler.ThisException()" globals
        popHandler();

        CG.set_label(l + 2);

        return oc;
    }

    private static EnumSet<Stmt.Outcome> compileHandler2(Handler h, M3CG.Var frame,
                                                         long resume, boolean last) {
        Except e;
        EnumSet<Stmt.Outcome> oc;
        Scope.T zz;
        long top;
        boolean need_top = false;
        Type.Info t_info = new Type.Info();
        top = CG.next_label(2);

        CG.gen_location(h.origin);

        if ((!last)) {
            // check for a match
            e = h.tags;
            AssertError.check(e != null, "TryStmt");
            while ((e != null)) {
                CG.load_addr(frame, M3.rt.EF1_info + M3.rt.EA_exception, M3.target.Address.align);
                CG.boost_addr_alignment(M3.target.Integer.align);
                CG.load_indirect(M3.target.Integer.cg_type, 0, M3.target.Integer.size);
                CG.load_intt(Exceptionz.UID(e.obj)); // Value.Load (e.obj);
                e = e.next;
                if ((e != null)) {
                    CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.EQ, top, CG.Maybe);
                    need_top = true;
                } else {
                    CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.NE, top + 1, CG.Maybe);
                }
            }
            // CG.Jump (top+1);
        }

        if ((need_top)) {
            CG.set_label(top);
        }

        if ((h.scope != null)) {
            zz = Scope.push(h.scope);
              Scope.enter(h.scope);
              Scope.initValues(h.scope);
              Variable.loadLValue(h.var_);
              Type.checkInfo(h.type, t_info);
              if (Exceptionz.argByReference(h.type)) {
                  CG.load_addr(frame, M3.rt.EF1_info + M3.rt.EA_arg, M3.target.Address.align);
                  CG.boost_addr_alignment(t_info.alignment);
                  CG.copy(t_info.size, /* overlap := */ false);
              } else {
                  CG.load_addr(frame, M3.rt.EF1_info + M3.rt.EA_arg, M3.target.Address.align);
                  CG.loophole(CGType.Addr, t_info.stk_type);
                  CG.store_indirect(t_info.stk_type, 0, t_info.size);
              }
              Variable.scheduleTrace(h.var_);
              oc = Stmt.compile(h.body);
              if (oc.contains(Stmt.Outcome.FallThrough)) {
                  CG.jump(resume);
              }
              // for the debugger's sake, this Jump should be inside the scope
              Scope.exit(h.scope);
            Scope.pop(zz);
        } else {
            oc = Stmt.compile(h.body);
            if (oc.contains(Stmt.Outcome.FallThrough)) {
                CG.jump(resume);
            }
        }

        CG.set_label(top + 1);
        return oc;
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        Handler h;
        EnumSet<Stmt.Outcome> oc = Stmt.getOutcome(p.body);
        if ((p.hasElse)) {
            // EXITs and RETURNs from the body are caught by the ELSE clause
            oc.removeAll(EnumSet.of(Stmt.Outcome.Returns, Stmt.Outcome.Exits));
        }

        h = p.handles;
        while ((h != null)) {
            oc.addAll(Stmt.getOutcome(h.body));
            h = h.next;
        }

        if ((p.hasElse)) {
            oc.addAll(Stmt.getOutcome(p.elseBody));
        }

        return oc;
    }

    //--------------------------------- handler info for Compiler.ThisException ---

    public static final class HandlerInfo {
        public M3CG.Var info;
        public long offset;
        public boolean direct;
    }

    // for "Compiler.ThisException()"
    private static long tos = 0;
    private static final HandlerInfo[] stack = newStack();

    /** Port: ARRAY [0..50] OF HandlerInfo, every element a fresh record. */
    private static HandlerInfo[] newStack() {
        HandlerInfo[] s = new HandlerInfo[51];
        for (int i = 0; i < s.length; i++) {
            s[i] = new HandlerInfo();
        }
        return s;
    }

    /** Records the entry &amp; exit from handler code */
    public static void pushHandler(M3CG.Var info, long offset, boolean direct) {
        HandlerInfo z = stack[(int) tos];
        z.info = info;
        z.offset = offset;
        z.direct = direct;
        tos++;
    }

    public static void popHandler() {
        tos--;
        stack[(int) tos].info = null;
    }

    /** Returns "TRUE" if we're currently typechecking the body of a handler */
    public static boolean inHandler() {
        return (tos > 0);
    }

    /**
     * Load the address of the RT0.RaiseActivation record passed
     * to the handler that's currently being compiled.
     */
    public static void loadInfoPtr() {
        if ((tos <= 0)) {
            CG.load_nil();
            return;
        }
        HandlerInfo z = stack[(int) (tos - 1)];
        if (z.direct) {
            CG.load_addr_of(z.info, z.offset, M3.target.Address.align);
        } else {
            CG.load_addr(z.info, z.offset, M3.target.Address.align);
        }
    }
}
