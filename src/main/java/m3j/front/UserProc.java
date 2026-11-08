// Java port of CM3's m3-sys/m3front/src/types/UserProc.i3 and UserProc.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: UserProc.m3
// Last Modified On Tue Jun 20 15:59:11 PDT 1995 By kalsow
//      Modified On Thu Jun 15 12:16:47 PDT 1995 By ericv

public final class UserProc {
    private UserProc() {}

    // READONLY
    public static CallExpr.MethodList Methods;

    // Externally dispatched-to, using a field of Methods:
    private static Type.T typeOf(CallExpr.T ce) {
        Type.T t;
        Expr.T proc = ce.proc;
        t = Expr.typeOf(proc);
        if ((t == ErrType.T)) {
            return t;
        }
        if ((t == null)) {
            t = QualifyExpr.methodType(proc);
        }
        return ProcType.result(Type.base(t));
    }

    // Externally dispatched-to, using a field of Methods:
    private static void needsAddress(CallExpr.T ce) {
        // Port: ce is <*UNUSED*>.
        // ya, my result is a structure that needs an address...
    }

    // Externally dispatched-to, using a field of Methods:
    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T t;
        Value.T formals;
        t = Expr.typeOf(ce.proc);
        if ((t == null)) {
            t = QualifyExpr.methodType(ce.proc);
        }
        t = Type.base(t);
        formals = ProcType.formals(t);
        // Port: ce.args is the VAR parameter actuals of Formal.CheckArgs.
        Expr.T[][] actuals = {ce.args};
        boolean ok = Formal.checkArgs(cs, actuals, formals, ce.proc);
        ce.args = actuals[0];
        if (ok) {
            // nothing
        }
        ce.type = ProcType.result(t);
    }

    private static void innerPrep(CallExpr.T ce) {
        Expr.T proc = ce.proc;
        Expr.T[] args = ce.args;
        Value.T[] p_value = new Value.T[1];
        Type.T p_type;
        CG.Val p_temp = null;
        Type.T t_result;
        CGType cg_result;
        M3CG.Var tmp_result = null;
        CG.Val lhs_result = null;
        long align_result;
        boolean large_result = false;
        Value.T formals, v;
        long n;
        Type.Info result_info = new Type.Info();
        Target.CallingConvention callConv;
        long[] handler = new long[1];
        long[] handler_body = new long[1];
        M3CG.Var[] info = new M3CG.Var[1];
        // If this is a direct structure return, the LHS has already
        // been prepped and compiled -- save it.
        if (ce.doDirectAssign) {
            lhs_result = CG.pop();
        }

        p_type = Expr.typeOf(proc);
        if ((p_type == null)) {
            p_type = QualifyExpr.methodType(proc);
        }
        p_type = Type.base(p_type);

        // grab the formals list
        formals = ProcType.formals(p_type);
        callConv = ProcType.callConv(p_type);

        // prep the target
        if (!isProcedureLiteral(proc, p_value)) {
            Expr.prep(proc);
            p_value[0] = null;
        }

        // prep the actuals
        if (callConv.args_left_to_right) {
            v = formals;
            n = 0;
            while ((v != null)) {
                Formal.prepArg(v, args[(int) n]);
                v = v.next;
                n++;
            }
        } else {
            // use the runtime stack to reverse the list...
            prepRightToLeft(formals, args, 0);
        }

        // get the result type and allocate the temp for return results
        t_result = Type.checkInfo(ProcType.result(p_type), result_info);
        cg_result = ProcType.cgResult(p_type);
        align_result = result_info.alignment;
        large_result = ProcType.largeResult(t_result);
        if (large_result && !ce.doDirectAssign) {
            tmp_result = CG.declare_temp(result_info.size, align_result,
                                         CGType.Struct, /* in_memory := */ true);
        }

        // start the call
        if ((p_value[0] != null)) {
            Procedure.startCall((Procedure.T) p_value[0]);
        } else if (couldBeClosure(proc)) {
            Expr.compile(proc);
            p_temp = CG.pop_temp();
            CG.start_call_indirect(cg_result, callConv);
        } else {
            Expr.compile(proc);
            p_temp = CG.pop();
            CG.start_call_indirect(cg_result, callConv);
        }

        // pass the arguments
        if (large_result
            && (callConv.args_left_to_right == callConv.results_on_left)) {
            genResultArg(lhs_result, tmp_result, align_result);
        }
        if (callConv.args_left_to_right) {
            QualifyExpr.passObject(proc);
            v = formals;
            n = 0;
            while ((v != null)) {
                Formal.emitArg(proc, v, args[(int) n]);
                v = v.next;
                n++;
            }
        } else { // evaluate args right to left
            // use the runtime stack to reverse the list...
            emitRightToLeft(proc, formals, args, 0);
            QualifyExpr.passObject(proc);
        }
        if (large_result
            && (callConv.args_left_to_right != callConv.results_on_left)) {
            genResultArg(lhs_result, tmp_result, align_result);
        }

        // generate the call
        if ((p_value[0] != null)) {
            ce.tmp = Procedure.emitValueCall((Procedure.T) p_value[0]);
        } else if (couldBeClosure(proc)) {
            ce.tmp = genClosureCall(p_temp, cg_result, p_type, callConv);
            CG.free(p_temp);
        } else {
            CG.push(p_temp);
            if (Marker.nextHandler(handler, handler_body, info)) {
                CG.invoke_indirect(cg_result, callConv, handler[0]);
                Marker.invoked();
            } else {
                CG.gen_Call_indirect(cg_result, callConv);
            }
            ce.tmp = Marker.emitExceptionTest(p_type, /* need_value := */ true);
            CG.free(p_temp);
        }

        ce.align = align_result;
        if ((large_result)) {
            AssertError.check(ce.tmp == null, "UserProc");
            if (ce.doDirectAssign) {
                ce.tmp = lhs_result;
            } else {
                CG.load_addr_of_temp(tmp_result, 0, align_result);
                ce.tmp = CG.pop();
            }
        }
    }

    // Externally dispatched-to, using a field of Methods:
    private static void prep(CallExpr.T ce) {
        if (!ce.doDirectAssign) {
            innerPrep(ce);
            // ELSE postpone InnerPrep until Compile, when LHS will have been pushed.
        }
    }

    // Externally dispatched-to:
    private static void compile(CallExpr.T ce) {
        if (ce.doDirectAssign) {
            // InnerPrep was postponed until now.
            innerPrep(ce);
        }
        if ((ce.tmp != null)) {
            CG.push(ce.tmp);
            CG.boost_addr_alignment(ce.align);
            CG.free(ce.tmp);
            ce.tmp = null;
        }
    }

    private static void prepRightToLeft(Value.T formal, Expr.T[] args, long cnt) {
        if ((formal != null)) {
            prepRightToLeft(formal.next, args, cnt + 1);
            Formal.prepArg(formal, args[(int) cnt]);
        }
    }

    private static void emitRightToLeft(Expr.T proc, Value.T formal,
                                        Expr.T[] args, long cnt) {
        if ((formal != null)) {
            emitRightToLeft(proc, formal.next, args, cnt + 1);
            Formal.emitArg(proc, formal, args[(int) cnt]);
        }
    }

    private static void genResultArg(CG.Val lhs, M3CG.Var tmp, long align) {
        if ((lhs != null)) {
            CG.push(lhs);
        } else {
            CG.load_addr_of(tmp, 0, align);
        }
        CG.pop_param(CGType.Addr);
    }

    private static CG.Val genClosureCall(CG.Val p_temp, CGType result,
                                         Type.T sig, Target.CallingConvention cc) {
        long skip = CG.next_label();
        long[] handler = new long[1], handler_body = new long[1];
        M3CG.Var[] info = new M3CG.Var[1];
        CG.if_closure(p_temp, CG.No_label, skip, CG.Maybe);
        CG.push(p_temp);
        CG.closure_frame();
        CG.pop_static_link();
        CG.push(p_temp);
        CG.closure_proc();
        CG.store_temp(p_temp);
        CG.set_label(skip);
        CG.push(p_temp);
        if (Marker.nextHandler(handler, handler_body, info)) {
            CG.invoke_indirect(result, cc, handler[0]);
            Marker.invoked();
        } else {
            CG.gen_Call_indirect(result, cc);
        }
        return Marker.emitExceptionTest(sig, /* need_value := */ true);
    }

    private static boolean couldBeClosure(Expr.T proc) {
        int[] name = new int[1];
        Value.T[] value = new Value.T[1];
        return (NamedExpr.split(proc, name, value))
            && (Value.classOf(value[0]) == Value.Class.Var)
            && (Variable.hasClosure((Variable.T) value[0]));
    }

    /** Port: VAR proc is {@code proc[0]}. */
    // EXPORTED:
    public static boolean isProcedureLiteral(Expr.T e, Value.T[] proc) {
        int[] name = new int[1];
        Value.T[] v = new Value.T[1];
        Value.Class vc;
        e = Expr.constValue(e);
        if ((e == null)) {
            return false;
        }
        if (!(NamedExpr.split(e, name, v)
              || QualifyExpr.split(e, v)
              || ProcExpr.split(e, v))) {
            return false;
        }
        vc = Value.classOf(v[0]);
        if ((vc == Value.Class.Procedure)) {
            proc[0] = Value.base(v[0]);
            return true;
        } else {
            return false;
        }

        //******
        // RETURN (NamedExpr.Split (e, name, proc)
        //           OR QualifyExpr.Split (e, proc)
        //           OR ProcExpr.Split (e, proc))
        //       AND (Value.ClassOf (proc) = Value.Class.Procedure);
        //******
    }

    // EXPORTED:
    public static void initialize() {
        Methods = CallExpr.newMethodList(0, 99999, false, true, true, null,
                                         UserProc::typeOf,
                                         UserProc::typeOf,
                                         UserProc::needsAddress,
                                         UserProc::check,
                                         UserProc::prep,
                                         UserProc::compile,
                                         CallExpr::noLValue,
                                         CallExpr::noLValue,
                                         CallExpr::prepNoBranch,
                                         CallExpr::noBranch,
                                         CallExpr::noValue,
                                         CallExpr::noBounds,
                                         CallExpr::isNever, // writable
                                         CallExpr::isNever, // designator
                                         CallExpr::notWritable); // noteWriter
    }
}
