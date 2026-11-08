// Java port of CM3's m3-sys/m3front/src/stmts/ForStmt.i3 and ForStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.TInt;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: ForStmt.i3
// Last modified On Fri Jun 24 09:32:50 PDT 1994 By kalsow

// File: ForStmt.m3
// Last modified on Fri Feb 24 09:28:39 PST 1995
//  by kalsow
//      modified on Tue Nov 27 23:52:39 1990 by muller

public final class ForStmt {
    private ForStmt() {}

    public static final class P extends Stmt.T {
        public Scope.T scope;
        public Variable.T var_;
        public Expr.T from;
        public Expr.T limit;
        public Expr.T step;
        public Stmt.T body;

        @Override
        public void check(M3.CheckState cs) {
            ForStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return ForStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    public static Stmt.T parse() {
        int id;
        P p;
        Tracer.T trace;
        p = new P();
        StmtRep.init(p);
        Scanner.match(Token.T.tFOR);
        id = Scanner.matchID();
        trace = Variable.parseTrace();
        Scanner.match(Token.T.tASSIGN);
        p.from = Expr.parse();
        Scanner.match(Token.T.tTO);
        p.limit = Expr.parse();
        p.step = null;
        if ((Scanner.cur.token == Token.T.tBY)) {
            Scanner.getToken(); // BY
            p.step = Expr.parse();
        }
        p.var_ = Variable.new_(id, true);
        p.scope = Scope.new1(p.var_);
        Variable.bindTrace(p.var_, trace);
        Scanner.match(Token.T.tDO);
        p.body = Stmt.parse();
        Scanner.match(Token.T.tEND);
        Scope.popNew();
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T tFrom, tTo, tStep;
        int[] minFrom = TInt.newInt(), maxFrom = TInt.newInt();
        int[] minLimit = TInt.newInt(), maxLimit = TInt.newInt();
        int[] minStep = TInt.newInt(), maxStep = TInt.newInt();
        int[] newMin = TInt.newInt(), newMax = TInt.newInt();
        Scope.T zz;
        boolean errored = false;
        Expr.T[] expr = new Expr.T[1];
        Expr.typeCheck(p.from, cs);
        Expr.typeCheck(p.limit, cs);
        tFrom = Type.base(Expr.typeOf(p.from));
        tTo = Type.base(Expr.typeOf(p.limit));

        if ((tFrom == ErrType.T) || (tTo == ErrType.T)) {
            // already an error...
            tFrom = ErrType.T;
            tTo = ErrType.T;
            tStep = Int.T;
            errored = true;
        } else if (EnumType.is(tFrom)) {
            if (!Type.isEqual(tFrom, tTo, null)) {
                Error.msg("'from' and 'to' expressions are incompatible");
                errored = true;
            }
            tStep = Int.T;
        } else if ((tFrom == Int.T) && (tTo == Int.T)) {
            tStep = Int.T;
        } else if ((tFrom == LInt.T) && (tTo == LInt.T)) {
            tStep = LInt.T;
        } else {
            Error.msg("'from' and 'to' expressions must be compatible ordinals");
            errored = true;
            tStep = Int.T;
        }

        if (p.step == null) {
            p.step = IntegerExpr.new_(tStep, TInt.One);
        }
        Expr.typeCheck(p.step, cs);
        if (!Type.isSubtype(Expr.typeOf(p.step), tStep)) {
            Error.msg("'by' expression must be an integer");
            errored = true;
        }

        // set the type of the control variable
        Variable.bindType(p.var_, tFrom, /* indirect := */ false, /* readonly := */ true,
                          /* open_array_ok := */ false, /* needs_init := */ false);

        // determine the ranges of the control values
        // Port: the VAR parameter expr of Reduce is passed in expr[0] and
        // written back to the field.
        expr[0] = p.step;
        boolean reduced = reduce(expr, minStep);
        p.step = expr[0];
        if (reduced) {
            TInt.assign(maxStep, minStep);
        } else {
            Expr.getBounds(p.step, minStep, maxStep);
        }
        expr[0] = p.from;
        reduced = reduce(expr, minFrom);
        p.from = expr[0];
        if (reduced) {
            TInt.assign(maxFrom, minFrom);
        } else {
            Expr.getBounds(p.from, minFrom, maxFrom);
        }
        expr[0] = p.limit;
        reduced = reduce(expr, minLimit);
        p.limit = expr[0];
        if (reduced) {
            TInt.assign(maxLimit, minLimit);
        } else {
            Expr.getBounds(p.limit, minLimit, maxLimit);
        }

        if (TInt.EQ(minStep, TInt.Zero) && TInt.EQ(maxStep, TInt.Zero)) {
            // warning suggested by Ernst A. Heinz <heinze@ira.uka.de>
            //  to catch typos. (March 19, 1995)
            Error.warn(1, "zero 'by' value in FOR loop");
            errored = true;
        }

        // try to tighten up the range of the new index variable
        if (TInt.LE(TInt.Zero, minStep)) {
            // we're counting up!
            TInt.assign(newMin, minFrom);
            TInt.assign(newMax, maxLimit);
        } else if (TInt.LT(maxStep, TInt.Zero)) {
            // we're counting down
            TInt.assign(newMin, minLimit);
            TInt.assign(newMax, maxFrom);
        } else {
            // we might be counting in either direction...
            if (TInt.LT(minFrom, minLimit)) {
                TInt.assign(newMin, minFrom);
            } else {
                TInt.assign(newMin, minLimit);
            }
            if (TInt.LT(maxFrom, maxLimit)) {
                TInt.assign(newMax, maxLimit);
            } else {
                TInt.assign(newMax, maxFrom);
            }
        }
        Variable.setBounds(p.var_, newMin, newMax);

        if (!errored && TInt.LT(newMax, newMin)) {
            Error.warn(1, "FOR loop body is unreachable (empty range)");
        }

        zz = Scope.push(p.scope);
          Scope.typeCheck(p.scope, cs);
          Marker.pushExit(CG.No_label);
          Stmt.typeCheck(p.body, cs);
          Marker.pop();
        Scope.pop(zz);
    }

    private static boolean reduce(Expr.T[] expr, int[] i) {
        Expr.T e;
        Type.T[] t = new Type.T[1];
        e = Expr.constValue(expr[0]);
        if ((e == null)) {
            return false;
        }
        expr[0] = e;
        return IntegerExpr.split(e, i, t) || EnumExpr.split(e, i, t);
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        Expr.T step, limit, from;
        int[] step_val = TInt.newInt(), limit_val = TInt.newInt(), from_val = TInt.newInt();
        int[] step_min = TInt.newInt(), step_max = TInt.newInt();
        Type.T[] t = new Type.T[1];
        EnumSet<Stmt.Outcome> oc;
        Scope.T zz;
        M3CG.Var index, to = null, by = null;
        CG.Val t_index = null, t_to = null, t_by = null;
        long l_top, l_test, l_less, l_exit;
        Type.T[] type = new Type.T[1];
        boolean[] global = new boolean[1], indirect = new boolean[1], lhs = new boolean[1];
        boolean index_copy;
        Type.Info info = new Type.Info();
        long[] offset = new long[1];
        CGType cg_type;
        long uid;
        Variable.split(p.var_, type, global, indirect, lhs);
        if (Type.isSubtype(type[0], LInt.T)) {
            cg_type = M3.target.Longint.cg_type;
            uid = Type.globalUID(LInt.T);
        } else {
            cg_type = M3.target.Integer.cg_type;
            uid = Type.globalUID(Int.T);
        }

        from = Expr.constValue(p.from);
        if ((from == null)) {
            Expr.prep(p.from);
            Expr.compile(p.from);
            t_index = CG.pop_temp();
        } else {
            // lower bound is a constant
            TInt.assign(from_val, TInt.Zero);
            boolean b = /* Port: EVAL */ IntegerExpr.split(from, from_val, t)
                || EnumExpr.split(from, from_val, t);
        }

        limit = Expr.constValue(p.limit);
        if ((limit == null)) {
            Expr.prep(p.limit);
            Expr.compile(p.limit);
            t_to = CG.pop_temp();
        } else { // upper bound is a constant
            TInt.assign(limit_val, TInt.Zero);
            boolean b = /* Port: EVAL */ IntegerExpr.split(limit, limit_val, t)
                || EnumExpr.split(limit, limit_val, t);
        }

        step = Expr.constValue(p.step);
        if ((step == null)) {
            // non-constant step value
            Expr.prep(p.step);
            Expr.compile(p.step);
            t_by = CG.pop_temp();
            Expr.getBounds(p.step, step_min, step_max);
        } else { // step is a constant
            TInt.assign(step_val, TInt.Zero);
            boolean b = /* Port: EVAL */ IntegerExpr.split(step, step_val, t)
                || EnumExpr.split(step, step_val, t);
        }

        l_top = CG.next_label(3);
        l_test = l_top + 1;
        l_exit = l_top + 2;

        zz = Scope.push(p.scope);
          Scope.enter(p.scope);
          Scope.initValues(p.scope);

          if (Type.isEqual(type[0], Int.T, null) || Type.isEqual(type[0], LInt.T, null)) {
              // use the user's variable
              index_copy = false;
              M3CG.Var[] unit = new M3CG.Var[1];
              Variable.localCGName(p.var_, unit, offset);
              index = unit[0];
              AssertError.check(offset[0] == 0, "ForStmt");
          } else {
              // declare a fresh local variable for the index
              // 'cause small variables may overflow at the end of their ranges
              index_copy = true;
              index = CG.declare_local(M3ID.NoID, M3.targetMap.CG_Size[cg_type.ordinal()],
                                       M3.targetMap.CG_Align[cg_type.ordinal()], cg_type,
                                       (int) uid,
                                       /* in_memory := */ false, /* up_level := */ false,
                                       /* f := */ CG.Always);
          }

          if ((from == null)) {
              CG.push(t_index);
              CG.store_int(cg_type, index);
              CG.free(t_index);
          } else {
              CG.load_integer(cg_type, from_val);
              CG.store_int(cg_type, index);
          }

          if ((limit == null)) {
              // declare the local variable
              to = CG.declare_local(M3ID.NoID, M3.targetMap.CG_Size[cg_type.ordinal()],
                                    M3.targetMap.CG_Align[cg_type.ordinal()], cg_type,
                                    (int) uid,
                                    /* in_memory := */ false, /* up_level := */ false,
                                    /* f := */ CG.Maybe);
              CG.push(t_to);
              CG.store_int(cg_type, to);
              CG.free(t_to);
          }

          if ((step == null)) {
              // declare the local variable
              by = CG.declare_local(M3ID.NoID, M3.targetMap.CG_Size[cg_type.ordinal()],
                                    M3.targetMap.CG_Align[cg_type.ordinal()], cg_type,
                                    (int) uid,
                                    /* in_memory := */ false, /* up_level := */ false,
                                    /* f := */ CG.Maybe);
              CG.push(t_by);
              CG.store_int(cg_type, by);
              CG.free(t_by);
          }

          if ((from == null) || (limit == null) || (step == null)) {
              // we don't know all three values...
              CG.jump(l_test);
          } else if (TInt.LE(TInt.Zero, step_val)
                     && TInt.LE(from_val, limit_val)) {
              // we know we'll execute the loop at least once.
          } else if (TInt.LE(step_val, TInt.Zero)
                     && TInt.LE(limit_val, from_val)) {
              // we know we'll execute the loop at least once.
          } else {
              // we won't execute the loop...
              CG.jump(l_test);
          }
          CG.set_label(l_top);

          Marker.pushExit(l_exit);

          if ((index_copy)) {
              // make the user's variable equal to the counter
              Type.checkInfo(type[0], info);
              Variable.loadLValue(p.var_);
              CG.load_int(cg_type, index);
              CG.store_indirect(info.stk_type, 0, info.size);
          }
          Variable.scheduleTrace(p.var_);

          oc = Stmt.compile(p.body);

          // increment the counter
          CG.gen_location(p.origin);
          CG.load_int(cg_type, index);
          if ((step != null)) {
              CG.load_integer(cg_type, step_val);
          } else {
              CG.load_int(cg_type, by);
          }
          CG.add(cg_type);
          CG.store_int(cg_type, index);

          // generate the loop test
          CG.gen_location(p.origin);
          CG.set_label(l_test);
          if ((step != null)) { // constant step value
              CG.load_int(cg_type, index);
              if ((limit != null)) {
                  CG.load_integer(cg_type, limit_val);
              } else {
                  CG.load_int(cg_type, to);
              }
              if (TInt.LE(TInt.Zero, step_val)) {
                  CG.if_compare(cg_type, M3CG.CompareOp.LE, l_top, CG.Likely);
              } else {
                  CG.if_compare(cg_type, M3CG.CompareOp.GE, l_top, CG.Likely);
              }
          } else if (TInt.LE(TInt.Zero, step_min)) {
              // positive, variable step value
              CG.load_int(cg_type, index);
              if ((limit != null)) {
                  CG.load_integer(cg_type, limit_val);
              } else {
                  CG.load_int(cg_type, to);
              }
              CG.if_compare(cg_type, M3CG.CompareOp.LE, l_top, CG.Likely);
          } else if (TInt.LT(step_max, TInt.Zero)) {
              // negative, variable step value
              CG.load_int(cg_type, index);
              if ((limit != null)) {
                  CG.load_integer(cg_type, limit_val);
              } else {
                  CG.load_int(cg_type, to);
              }
              CG.if_compare(cg_type, M3CG.CompareOp.GE, l_top, CG.Likely);
          } else { // variable step value
              l_less = CG.next_label(2);
              CG.load_int(cg_type, by);
              CG.load_integer(cg_type, TInt.Zero);
              CG.if_compare(cg_type, M3CG.CompareOp.LT, l_less, CG.Likely);
              CG.load_int(cg_type, index);
              if ((limit != null)) {
                  CG.load_integer(cg_type, limit_val);
              } else {
                  CG.load_int(cg_type, to);
              }
              CG.if_compare(cg_type, M3CG.CompareOp.LE, l_top, CG.Likely);
              CG.jump(l_less + 1);
              CG.set_label(l_less);
              CG.load_int(cg_type, index);
              if ((limit != null)) {
                  CG.load_integer(cg_type, limit_val);
              } else {
                  CG.load_int(cg_type, to);
              }
              CG.if_compare(cg_type, M3CG.CompareOp.GE, l_top, CG.Likely);
              CG.set_label(l_less + 1);
          }

          Marker.pop();
          CG.set_label(l_exit);

          Scope.exit(p.scope);
        Scope.pop(zz);

        // a FOR can fall through if its index range may be empty
        // or if its body can fall through or exit.  -- Ernst Heinz
        if (oc.contains(Stmt.Outcome.Exits)
            || (from == null) || (limit == null) || (step == null)
            || (!TInt.LT(step_val, TInt.Zero) && TInt.LT(limit_val, from_val))
            || (TInt.LT(step_val, TInt.Zero) && TInt.LT(from_val, limit_val))) {
            oc.add(Stmt.Outcome.FallThrough);
        }

        oc.remove(Stmt.Outcome.Exits);
        return oc;
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        EnumSet<Stmt.Outcome> oc = Stmt.getOutcome(p.body);
        oc.remove(Stmt.Outcome.Exits);
        oc.add(Stmt.Outcome.FallThrough);
        return oc;
    }
}
