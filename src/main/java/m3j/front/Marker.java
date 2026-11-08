// Java port of CM3's m3-sys/m3front/src/misc/Marker.i3 and Marker.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: Marker.i3
// Last Modified On Wed Oct 25 11:39:40 PDT 1995 by ericv
//      Modified On Fri May 19 07:41:46 PDT 1995 by kalsow
//      Modified On Sat Jun 10 18:44:15 PDT 1989 by muller

// File: Marker.m3
// Last modified on Tue Jun 20 09:14:19 PDT 1995 by kalsow
// Last modified on Fri Jun 16 17:35:38 PDT 1995 by ericv
//      modified on Fri Feb 15 03:21:08 1991 by muller

public final class Marker {
    private Marker() {}

    public static final long Return_exception = -1;

    public static final long Exit_exception = -2;

    public enum Kind {
        zFINALLY, zFINALLYPROC, zLOCK, zEXIT, zTRY, zTRYELSE,
        zRAISES, zPROC
    }

    // Port: FramePtr = REF Frame is Frame; p^ := z is a copy.

    public static final class Frame {
        public Kind kind;
        public boolean outermost;
        public boolean saved;
        public boolean returnSeen;
        public boolean exitSeen;
        public boolean invokeSeen;
        public M3CG.Var info;
        public long start;
        public long stop;
        public long stopBody;
        /** kind = PROC */
        public Type.T type;
        /** kind = PROC */
        public Variable.T variable;
        /** kind = PROC */
        public M3CG.Var tmp_result;
        /** kind = RAISES, TRY */
        public ESet.T e_set;
        public Frame next;
        public Target.CallingConvention callConv;
        public M3CG.Proc handler;
        public long h_level;

        /** Port: the assignment {@code this := x}. */
        void assign(Frame x) {
            kind = x.kind;
            outermost = x.outermost;
            saved = x.saved;
            returnSeen = x.returnSeen;
            exitSeen = x.exitSeen;
            invokeSeen = x.invokeSeen;
            info = x.info;
            start = x.start;
            stop = x.stop;
            stopBody = x.stopBody;
            type = x.type;
            variable = x.variable;
            tmp_result = x.tmp_result;
            e_set = x.e_set;
            next = x.next;
            callConv = x.callConv;
            handler = x.handler;
            h_level = x.h_level;
        }
    }

    private static final long jmpbufAlign = 128;

    /** Port: indexed by Kind.ordinal(). */
    private static final long[] RT_Kind = {
        M3RT.HandlerClass.Finally.ordinal(),
        M3RT.HandlerClass.FinallyProc.ordinal(),
        M3RT.HandlerClass.Lock.ordinal(),
        -1, // exit
        M3RT.HandlerClass.Except.ordinal(),
        M3RT.HandlerClass.ExceptElse.ordinal(),
        M3RT.HandlerClass.Raises.ordinal(),
        -1  // proc
    };

    private static Frame all_frames = null;
    private static long n_frames = 0;
    private static long save_depth = 0;
    private static int tos = 0;
    private static final Frame[] stack = newStack();

    private static Frame[] newStack() {
        Frame[] s = new Frame[51];
        for (int i = 0; i < s.length; i++) {
            s[i] = new Frame();
        }
        return s;
    }

    //---------------------------------------------------------- marker stack ---

    /**
     * mark and save the top scope so it can be emitted in the
     * global table of scopes
     */
    public static void saveFrame() {
        Frame p = new Frame();
        AssertError.check(save_depth >= 0, "Marker");
        {
            Frame z = stack[tos - 1];
            z.saved = true;
            save_depth++;
            p.assign(z);
            //******
            // p.outermost := (save_depth <= 1);
            //     - this only works if the front-end doesn't inline
            //        nested procedures and the back-end doesn't screw
            //        around reordering labels.
            //*******
            p.next = all_frames;
            all_frames = p;
            n_frames++;
        }
    }

    /** pop to top scope. */
    public static void pop() {
        tos--;
        if ((stack[tos].saved)) {
            save_depth--;
        }
        AssertError.check(save_depth >= 0, "Marker");
    }

    // TRY-FINALLY

    public static void pushFinally(long l_start, long l_stop, long l_stopBody, M3CG.Var info) {
        push(Kind.zFINALLY, l_start, l_stop, info);
        {
            Frame z = stack[tos - 1];
            z.stopBody = l_stopBody;
        }
    }

    public static void pushFinallyProc(long l_start, long l_stop, M3CG.Var info, M3CG.Proc handler, long h_level) {
        push(Kind.zFINALLYPROC, l_start, l_stop, info);
        {
            Frame z = stack[tos - 1];
            z.handler = handler;
            z.h_level = h_level;
        }
    }

    /** Port: the VAR (OUT) parameters are one-element arrays. */
    public static void popFinally(boolean[] returnSeen, boolean[] exitSeen) {
        pop();
        returnSeen[0] = stack[tos].returnSeen;
        exitSeen[0] = stack[tos].exitSeen;
    }

    // LOCK-END

    public static void pushLock(long l_start, long l_stop, M3CG.Var mutex) {
        push(Kind.zLOCK, l_start, l_stop, mutex);
    }

    // TRY-EXCEPT

    public static void pushTry(long l_start, long l_stop, long l_stopBody, M3CG.Var info, ESet.T ex) {
        push(Kind.zTRY, l_start, l_stop, info, ex);
        {
            Frame z = stack[tos - 1];
            z.stopBody = l_stopBody;
        }
    }

    public static void pushTryElse(long l_start, long l_stop, long l_stopBody, M3CG.Var info) {
        push(Kind.zTRYELSE, l_start, l_stop, info);
        {
            Frame z = stack[tos - 1];
            z.stopBody = l_stopBody;
        }
    }

    // LOOP-EXIT

    public static void pushExit(long l_stop) {
        push(Kind.zEXIT, CG.No_label, /* l_stop := */ l_stop);
        {
            Frame z = stack[tos - 1];
            z.stopBody = l_stop;
        }
    }

    // TRY-PASSING (RAISES)

    public static void pushRaises(long l_start, long l_stop, ESet.T ex, M3CG.Var info) {
        push(Kind.zRAISES, l_start, l_stop, info, ex);
    }

    // PROCEDURE-RETURN

    public static void pushProcedure(Type.T t, Variable.T v, Target.CallingConvention cc) {
        AssertError.check((t == null) == (v == null), "Marker");
        push(Kind.zPROC);
        {
            Frame z = stack[tos - 1];
            z.type = t;
            z.variable = v;
            z.callConv = cc;
        }
    }

    private static void push(Kind k, long l_start, long l_stop, M3CG.Var info, ESet.T ex) {
        {
            Frame z = stack[tos];
            z.kind = k;
            z.saved = false;
            z.outermost = false;
            z.returnSeen = false;
            z.exitSeen = false;
            z.invokeSeen = false;
            z.start = l_start;
            z.stop = l_stop;
            z.info = info;
            z.type = null;
            z.variable = null;
            z.tmp_result = null;
            z.e_set = ex;
            z.next = null;
            z.callConv = null;
            z.handler = null;
            z.h_level = 0;
        }
        tos++;
    }

    private static void push(Kind k, long l_start, long l_stop, M3CG.Var info) {
        push(k, l_start, l_stop, info, null);
    }

    private static void push(Kind k, long l_start, long l_stop) {
        push(k, l_start, l_stop, null, null);
    }

    private static void push(Kind k) {
        push(k, CG.No_label, CG.No_label, null, null);
    }

    public static void invoked() {
        stack[tos - 1].invokeSeen = true;
    }

    public static boolean invokeSeen() {
        return stack[tos - 1].invokeSeen;
    }

    //--------------------------------------------- explicit frame operations ---

    /**
     * generate code to link and unlink 'frame' from the global
     * stack of exception frames
     */
    public static void pushFrame(M3CG.Var frame, M3RT.HandlerClass class_) {
        Procedure.T push;
        CG.load_intt(class_.ordinal());
        CG.store_int(M3.target.Integer.cg_type, frame, M3.rt.EF_class);
        push = RunTyme.lookUpProc(RunTyme.Hook.PushEFrame);
        Procedure.startCall(push);
        CG.load_addr_of(frame, 0, M3.target.Address.align);
        CG.pop_param(CGType.Addr);
        Procedure.emitCall(push);
    }

    /**
     * generate code to link and unlink 'frame' from the global
     * stack of exception frames
     */
    public static void popFrame(M3CG.Var frame) {
        Procedure.T pop;
        pop = RunTyme.lookUpProc(RunTyme.Hook.PopEFrame);
        Procedure.startCall(pop);
        CG.load_addr(frame, M3.rt.EF_next, M3.target.Address.align);
        CG.pop_param(CGType.Addr);
        Procedure.emitCall(pop);
    }

    /** generate the call to acquire or release a mutex */
    public static void setLock(boolean acquire, M3CG.Var var_, long offset) {
        long method_offset;
        if (acquire) {
            method_offset = M3.rt.MUTEX_acquire;
        } else {
            method_offset = M3.rt.MUTEX_release;
        }

        CG.start_call_indirect(CGType.Void, M3.target.DefaultCall);

        CG.load_addr(var_, offset, M3.target.Address.align); // mutex object
        CG.pop_param(CGType.Addr);

        CG.load_addr(var_, offset, M3.target.Address.align); // mutex object
        CG.load_indirect(CGType.Addr, 0, M3.target.Address.size); // method list
        CG.boost_addr_alignment(M3.target.Address.align);
        CG.load_indirect(CGType.Addr, method_offset, M3.target.Address.size); // proc
        CG.boost_addr_alignment(M3.target.Address.align);

        CG.gen_Call_indirect(CGType.Void, M3.target.DefaultCall);
    }

    private static void callFinallyHandler(M3CG.Var info,
                                           M3CG.Proc handler, long h_level) {
        if ((handler != null)) {
            CG.start_call_direct(handler, h_level, CGType.Void);
            CG.call_direct(handler, CGType.Void);
        } else {
            CG.start_call_indirect(CGType.Void, M3.target.DefaultCall);
            CG.load_addr(info, M3.rt.EF2_frame, M3.target.Address.align);
            CG.pop_static_link();
            CG.load_addr(info, M3.rt.EF2_handler, M3.target.Address.align);
            CG.gen_Call_indirect(CGType.Void, M3.target.DefaultCall);
        }
    }

    /**
     * frame.jmpbuf = jmpbuf
     * if (setjmp(jmpbuf)) goto handler
     * or
     * if (sigsetjmp(jmpbuf, 0)) goto handler
     */
    public static void captureState(M3CG.Var frame, M3CG.Var jmpbuf, long handler) {
        M3CG.Proc setjmp = Module.getSetjmp(Module.current());

        if (Target.Alloca_jmpbuf) {
            CG.load_addr(jmpbuf, 0, jmpbufAlign);
            CG.store_addr(frame, M3.rt.EF1_jmpbuf);
        }

        CG.start_call_direct(setjmp, 0, M3.target.Integer.cg_type);

        if (Target.Alloca_jmpbuf) {
            // Jmpbuf is allocated with alloca and m3front/m3middle
            // do not know its size.
            CG.load_addr(jmpbuf, 0, jmpbufAlign);
        } else {
            // Inactive path where m3front/m3middle must know size of
            // jmpbuf for each target: faster but much more work to port.
            CG.load_addr_of(frame, M3.rt.EF1_jmpbuf, jmpbufAlign);
        }
        CG.pop_param(CGType.Addr);

        // Call single parameter setjmp/_setjmp or two parameter sigsetjmp?
        // _setjmp is historical non-standard, and gives grief to C backend
        // compiling as C++ on Solaris, but did/does work broadly.
        // sigsetjmp is standard for _setjmp, taking a second parameter
        // indicating to save signal mask.
        //
        // What frontend and C backend call, needs to agree with
        // longjmp in m3core, jmpbuf size in m3core.
        if (M3.target.Sigsetjmp) {
            CG.load_intt(0);             // do not save signal mask
            CG.pop_param(CGType.Int32); // int
        }

        CG.call_direct(setjmp, M3.target.Integer.cg_type);
        CG.if_true(handler, CG.Never);
    }

    //------------------------------------------------------ misc. predicates ---

    public static boolean exitOK() {
        for (int i = tos - 1; i >= 0; i--) {
            Frame z = stack[i];
            if ((z.kind == Kind.zTRYELSE)) {
                Error.warn(1, "EXIT will be caught by TRY EXCEPT ELSE clause");
            }
            if ((z.kind == Kind.zEXIT)) {
                return true;
            }
            if ((z.kind == Kind.zPROC)) {
                return false;
            }
        }
        return false;
    }

    public static boolean returnOK() {
        for (int i = tos - 1; i >= 0; i--) {
            Frame z = stack[i];
            if ((z.kind == Kind.zTRYELSE)) {
                Error.warn(1, "RETURN will be caught by TRY EXCEPT ELSE clause");
            }
            if ((z.kind == Kind.zPROC)) {
                return true;
            }
        }
        return false;
    }

    /** Port: the VAR (OUT) parameters are one-element arrays. */
    public static void returnVar(Type.T[] t, Variable.T[] v) {
        for (int i = tos - 1; i >= 0; i--) {
            Frame z = stack[i];
            if ((z.kind == Kind.zPROC)) {
                t[0] = z.type;
                v[0] = z.variable;
                return;
            }
        }
        AssertError.check(false, "Marker");
    }

    //------------------------------------------------------- code generation ---


    public static void emitExit() {
        int i;
        // mark every frame out to the loop boundary as 'exitSeen'
        i = tos - 1;
        while ((i >= 0)) {
            Frame z = stack[i];
            z.exitSeen = true;
            if ((z.kind == Kind.zEXIT) || (z.kind == Kind.zTRYELSE)) {
                break;
            }
            i--;
        }

        if (M3.target.Has_stack_walker) {
            emitExit1();
        } else {
            emitExit2();
        }
    }

    private static void emitExit1() {
        int i;
        // unwind as far as possible
        i = tos - 1;
        loop:
        while ((i >= 0)) {
            Frame z = stack[i];
            switch (z.kind) {
                case zTRYELSE -> {
                    CG.load_intt(Exit_exception);
                    CG.store_int(M3.target.Integer.cg_type, z.info);
                    CG.jump(z.stopBody);
                    break loop;
                }
                case zFINALLY, zFINALLYPROC -> {
                    CG.load_intt(Exit_exception);
                    CG.store_int(M3.target.Integer.cg_type, z.info);
                    CG.jump(z.stopBody);
                    break loop;
                }
                case zLOCK -> {
                    setLock(false, z.info, 0);
                }
                case zEXIT -> {
                    CG.jump(z.stopBody);
                    break loop;
                }
                case zTRY -> {
                    // ignore
                }
                case zRAISES, zPROC -> {
                    Error.msg("INTERNAL ERROR: EXIT not in loop");
                    AssertError.check(false, "Marker");
                    // EXIT;
                }
            }
            i--;
        }
    }

    private static void emitExit2() {
        int i;
        // unwind as far as possible
        i = tos - 1;
        loop:
        while ((i >= 0)) {
            Frame z = stack[i];
            switch (z.kind) {
                case zTRYELSE, zFINALLY -> {
                    popFrame(z.info);
                    CG.load_intt(Exit_exception);
                    CG.store_int(M3.target.Integer.cg_type,
                                 z.info, M3.rt.EF1_info + M3.rt.EA_exception);
                    CG.jump(z.stop);
                    break loop;
                }
                case zFINALLYPROC -> {
                    popFrame(z.info);
                    callFinallyHandler(z.info, z.handler, z.h_level);
                }
                case zLOCK -> {
                    popFrame(z.info);
                    setLock(false, z.info, M3.rt.EF4_mutex);
                }
                case zEXIT -> {
                    CG.jump(z.stop);
                    break loop;
                }
                case zTRY -> {
                    popFrame(z.info);
                }
                case zRAISES, zPROC -> {
                    Error.msg("INTERNAL ERROR: EXIT not in loop");
                    AssertError.check(false, "Marker");
                    // EXIT;
                }
            }
            i--;
        }
    }

    public static void allocReturnTemp() {
        Type.Info ret_info = new Type.Info();
        // Normally, to return a value from a procedure, we assign to
        // z.variable.  In the case of large-result procedures, z.variable
        // is a hidden VAR parameter that points to a temporary allocated
        // on the caller's stack.  This means that we can safely write
        // to z.variable even if the eventual procedure outcome is an
        // exception rather than a RETURN.
        //
        // However, if direct struct returns are enabled, the caller is
        // allowed to pass the final destination address rather than a
        // temporary, i.e. z.variable may refer to a real variable.  In
        // this case it is not safe to write to z.variable unless the
        // procedure outcome is a RETURN.  This causes problems for
        // RETURN statements in the scope of a TRY-FINALLY, since we
        // need to save the return result somewhere while we execute the
        // FINALLY clause.  And we can't write to z.variable because the
        // FINALLY clause could raise an exception.
        //
        // So, for large-result procedures, we always allocate a
        // variable on the callee's stack to hold the return result.  If
        // there are no RETURNs within TRY-FINALLY or LOCK statements,
        // this storage is never used.  But it is no good to allocate
        // the variable when we see the TRY-FINALLY, because the local
        // variable we allocate must have a scope that extends over the
        // entire procedure.

        if (Host.direct_struct_assign) {
            Frame z = stack[tos - 1];
            AssertError.check(z.kind == Kind.zPROC, "Marker");
            if (ProcType.largeResult(z.type)) {
                Type.checkInfo(z.type, ret_info);
                // Use Declare_local() rather than Declare_temp() because
                // the life of this variable extends over the whole procedure
                // Port: a CG.TypeUID is an int.
                z.tmp_result =
                    CG.declare_local(M3ID.NoID, ret_info.size,
                                     ret_info.alignment, CGType.Struct,
                                     (int) Type.globalUID(z.type), /* in_memory := */ true,
                                     /* up_level := */ false, /* f := */ CG.Maybe);
            }
        }
    }

    public static void emitReturn(Expr.T expr, boolean fromFinally) {
        int i;
        Type.Info ret_info = new Type.Info();
        boolean simple;
        // Sic: is_large is uninitialized in the original when expr is NIL
        // or absorbed by a TRY-EXCEPT-ELSE; here it starts as FALSE.
        boolean is_large = false;
        // mark every frame out to the procedure boundary as 'returnSeen'
        i = tos - 1;
        while ((i >= 0)) {
            Frame z = stack[i];
            z.returnSeen = true;
            if ((z.kind == Kind.zPROC) || (z.kind == Kind.zTRYELSE)) {
                break;
            }
            i--;
        }

        simple = true;
        if ((expr != null)) {
            // check to see if the return value is absorbed by TRY-EXCEPT-ELSE
            // or munged by a finally handler
            i = tos - 1;
            loop:
            while ((i >= 0)) {
                Frame z = stack[i];
                switch (z.kind) {
                    case zTRYELSE -> {
                        // Not as in CM3: an array constructor has no
                        // target here (CM3's code generator found its
                        // stack empty).
                        ArrayExpr.noteNoTargetVar(expr);
                        Expr.prep(expr);
                        Expr.compile(expr);
                        CG.discard(Type.cgType(Expr.typeOf(expr)));
                        expr = null;
                        break loop;
                    }
                    case zFINALLY, zFINALLYPROC, zLOCK -> {
                        simple = false;
                    }
                    case zPROC -> {
                        break loop;
                    }
                    default -> { // ignore
                    }
                } // CASE
                i--;
            }

            if ((expr != null)) {
                Frame z = stack[i];
                // stuff the pending return value
                is_large = ProcType.largeResult(z.type);

                if (Host.direct_struct_assign && is_large && !simple) {
                    AssertError.check(z.tmp_result != null, "Marker");
                    Type.checkInfo(z.type, ret_info);
                    AssignStmt.prepForEmit(z.type, expr, /* initializing := */ true);
                    CG.load_addr_of(z.tmp_result, 0, ret_info.alignment);
                    AssignStmt.doEmit(z.type, expr, /* initializing := */ true);
                } else if (is_large || !simple) {
                    AssignStmt.prepForEmit(z.type, expr, /* initializing := */ false);
                    Variable.loadLValue(z.variable);
                    AssignStmt.doEmit(z.type, expr, /* initializing := */ false);
                } else {
                    Expr.prep(expr);
                }
            }
        }

        if (M3.target.Has_stack_walker) {
            i = emitReturn1();
        } else {
            i = emitReturn2();
        }

        if (i >= 0) {
            Frame z = stack[i];
            if (Host.direct_struct_assign
                && (fromFinally || !simple)
                && ProcType.largeResult(z.type)) {
                // Copy the compiler temp to the return result z.variable
                AssertError.check(z.tmp_result != null, "Marker");
                Type.checkInfo(z.type, ret_info);
                Variable.loadLValue(z.variable);
                CG.load_addr_of_temp(z.tmp_result, 0, ret_info.alignment);
                CG.copy(ret_info.size, /* overlap := */ false);
            }
            if ((z.type == null)) {
                // there's no return value
                CG.exit_proc(CGType.Void);
            } else if (fromFinally) {
                // the return value is stuffed in 'z.variable',
                // and 'expr' is 'NIL' on this call...
                if (!ProcType.largeResult(z.type)) {
                    Variable.load(z.variable);
                    CG.exit_proc(Type.cgType(z.type));
                } else if ((z.callConv.standard_structs)) {
                    CG.exit_proc(CGType.Void);
                } else {
                    Variable.loadLValue(z.variable);
                    CG.exit_proc(CGType.Struct);
                }
            } else if (is_large) {
                if ((z.callConv.standard_structs)) {
                    CG.exit_proc(CGType.Void);
                } else {
                    Variable.loadLValue(z.variable);
                    CG.exit_proc(CGType.Struct);
                }
            } else if (simple) {
                AssignStmt.emitRTCheck(z.type, expr);
                CG.exit_proc(Type.cgType(z.type));
            } else { // small scalar return value
                Variable.load(z.variable);
                CG.exit_proc(Type.cgType(z.type));
            }
        }
    }

    private static int emitReturn1() {
        int i;
        // now, unwind as far as possible
        i = tos - 1;
        loop:
        while ((i >= 0)) {
            Frame z = stack[i];
            switch (z.kind) {
                case zTRYELSE -> {
                    CG.load_intt(Return_exception);
                    CG.store_int(M3.target.Integer.cg_type, z.info);
                    CG.jump(z.stopBody);
                    break loop;
                }
                case zFINALLY, zFINALLYPROC -> {
                    CG.load_intt(Return_exception);
                    CG.store_int(M3.target.Integer.cg_type, z.info);
                    CG.jump(z.stopBody);
                    break loop;
                }
                case zLOCK -> {
                    setLock(false, z.info, 0);
                }
                case zEXIT -> {
                    // ignore
                }
                case zTRY -> {
                    // ignore
                }
                case zRAISES -> {
                    // ignore
                }
                case zPROC -> {
                    return i;
                }
            }
            i--;
        }
        return -1;
    }

    private static int emitReturn2() {
        int i;
        // now, unwind as far as possible
        i = tos - 1;
        loop:
        while ((i >= 0)) {
            Frame z = stack[i];
            switch (z.kind) {
                case zTRYELSE -> {
                    popFrame(z.info);
                    CG.load_nil(); // the current "RETURN" exception is lost
                    CG.store_addr(z.info, M3.rt.EF1_info + M3.rt.EA_exception);
                    CG.jump(z.stop);
                    break loop;
                }
                case zFINALLY -> {
                    popFrame(z.info);
                    CG.load_intt(Return_exception);
                    CG.store_int(M3.target.Integer.cg_type,
                                 z.info, M3.rt.EF1_info + M3.rt.EA_exception);
                    CG.jump(z.stop);
                    break loop;
                }
                case zFINALLYPROC -> {
                    popFrame(z.info);
                    callFinallyHandler(z.info, z.handler, z.h_level);
                }
                case zLOCK -> {
                    popFrame(z.info);
                    setLock(false, z.info, M3.rt.EF4_mutex);
                }
                case zEXIT -> {
                    // ignore
                }
                case zTRY -> {
                    popFrame(z.info);
                }
                case zRAISES -> {
                    popFrame(z.info);
                }
                case zPROC -> {
                    return i;
                }
            }
            i--;
        }
        return -1;
    }

    public static long emitScopeTable(boolean doEmit) {
        long Align = Math.max(M3.target.Address.align, M3.target.Integer.align);
        Frame f = all_frames;
        long base, x, size;
        M3CG.Var[] e_base = new M3CG.Var[1];
        long[] e_offset = new long[1];
        // the default is to not emit the scope table now we can
        // generate gcc_exception tables.
        if (!doEmit) {
            return -1;
        }
        if ((f == null) || (!M3.target.Has_stack_walker)) {
            return -1;
        }

        // make sure that all the exception lists were declared
        while ((f != null)) {
            if ((f.e_set != null)) {
                ESet.declare(f.e_set);
            }
            f = f.next;
        }

        // declare space for the table
        size = n_frames * M3.rt.EX_SIZE;
        base = Module.allocate(size, Align, true, "*exception scopes*");
        CG.comment(base, true, "exception scopes");

        // fill in the table
        f = all_frames;
        x = base;
        while ((f != null)) {
            CG.init_intt(x + M3.rt.EX_class, Target.Char.size, RT_Kind[f.kind.ordinal()], true);
            if ((f.outermost)) {
                CG.init_intt(x + M3.rt.EX_outermost, Target.Char.size, 1, true);
            }
            if ((f.next == null)) {
                CG.init_intt(x + M3.rt.EX_end_of_list, Target.Char.size, 1, true);
            }
            CG.init_label(x + M3.rt.EX_start, f.start, true);
            CG.init_label(x + M3.rt.EX_stop, f.stop, true);
            if ((f.info != null)) {
                CG.init_offset(x + M3.rt.EX_offset, f.info, true);
            }
            if ((f.e_set != null)) {
                ESet.getAddress(f.e_set, e_base, e_offset);
                if ((e_base[0] != null) || (e_offset[0] != 0)) {
                    CG.init_var(x + M3.rt.EX_excepts, e_base[0], e_offset[0], true);
                }
            }
            x += M3.rt.EX_SIZE;
            f = f.next;
        }

        return base;
    }

    public static long emitScopeTable() {
        return emitScopeTable(false);
    }

    public static CG.Val emitExceptionTest(Type.T signature, boolean need_value) {
        int i;
        CG.Val value = null;
        CGType result = ProcType.cgResult(signature);
        //* ex := ProcType.Raises (signature); *
        if (need_value) {
            value = captureResult(result);
        }

        if (!M3.target.Has_stack_walker) {
            return value;
        }

        //* nope -- any procedure could raise an <*IMPLICIT*> exception
        // IF ESet.RaisesNone (ex) THEN  RETURN value;  END;
        //**

        // scan the frame stack looking for the first active handler
        i = tos - 1;
        loop:
        for (;;) {
            if ((i < 0)) {
                return value;
            }
            {
                Frame z = stack[i];
                switch (z.kind) {
                    case zTRYELSE -> {
                        break loop;
                    }
                    case zFINALLYPROC -> {
                        // ignore the runtime does it
                    }
                    case zFINALLY -> {
                        break loop;
                    }
                    case zLOCK -> {
                        // ignore
                    }
                    case zEXIT -> {
                        // ignore
                    }
                    case zTRY -> {
                        break loop;
                    }
                    case zRAISES -> {
                        // ignore
                    }
                    case zPROC -> {
                        return value; // no relevent handlers
                    }
                }
            }
            i--;
        }

        if (!need_value) {
            value = captureResult(result);
        }

        // generate the conditional branch to the handler
        // Dont really need this code after every call - to check if an exception
        //   raised. It only works with the old copy exception model.
        //
        // CG.Load_addr (stack[i].info, M3RT.EA_exception, Target.Address.align);
        // CG.Load_nil ();
        // CG.If_compare (CG.Type.Addr, CG.Cmp.NE, stack[i].stop, CG.Never);
        if (!need_value && (value != null)) {
            CG.push(value);
            CG.free(value);
            value = null;
        }

        return value;
    }

    private static CG.Val captureResult(CGType result) {
        if ((result == CGType.Void)) {
            return null;
        } else if ((result != CGType.Struct)) {
            return CG.pop();
        } else {
            CG.discard(result);
            return null;
        }
    }

    /** Port: the VAR (OUT) parameters are one-element arrays. */
    public static boolean nextHandler(long[] handler, long[] handler_body, M3CG.Var[] info) {
        int i;
        if (!M3.target.Has_stack_walker) {
            return false;
        }

        // scan the frame stack looking for the first active handler
        i = tos - 1;
        loop:
        for (;;) {
            if ((i < 0)) {
                return false;
            }
            {
                Frame z = stack[i];
                switch (z.kind) {
                    case zTRYELSE -> {
                        break loop;
                    }
                    case zFINALLYPROC -> {
                        // ignore the runtime does it
                    }
                    case zFINALLY -> {
                        break loop;
                    }
                    case zLOCK -> {
                        // ignore
                    }
                    case zEXIT -> {
                        // ignore
                    }
                    case zTRY -> {
                        break loop;
                    }
                    case zRAISES -> {
                        // ignore
                    }
                    case zPROC -> {
                        return false; // didn't find any handlers
                    }
                }
            }
            i--;
        }

        handler_body[0] = stack[i].stopBody;
        handler[0] = stack[i].stop;
        info[0] = stack[i].info;
        return true;
    }

    public static ESet.EList excepts() {
        int i;
        ESet.EList e = null, t = null, ret = null;
        if (!M3.target.Has_stack_walker) {
            return null;
        }

        // scan the frame stack getting each frames list of handled exceptions
        i = tos - 1;
        while ((i >= 0)) {
            e = ESet.listE(stack[i].e_set);
            if (e != null) {
                t = e;
                while (t.next != null) {
                    t = t.next;
                }
                t.next = ret;
                ret = e;
            }
            i--;
        }
        return ret;
    }

    //----------------------------------------------------------------- misc. ---

    public static void reset() {
        all_frames = null;
        n_frames = 0;
        save_depth = 0;
        tos = 0;
    }
}
