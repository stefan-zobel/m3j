// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.util.function.Consumer;

import m3j.front.ArrayExpr;
import m3j.front.AssertStmt;
import m3j.front.AssignStmt;
import m3j.front.BlockStmt;
import m3j.front.CallExpr;
import m3j.front.CallStmt;
import m3j.front.CaseStmt;
import m3j.front.CheckExpr;
import m3j.front.ConsExpr;
import m3j.front.EvalStmt;
import m3j.front.Expr;
import m3j.front.ExprRep;
import m3j.front.ForStmt;
import m3j.front.IfStmt;
import m3j.front.KeywordExpr;
import m3j.front.LockStmt;
import m3j.front.LoopStmt;
import m3j.front.NarrowExpr;
import m3j.front.QualifyExpr;
import m3j.front.RaiseStmt;
import m3j.front.RecordExpr;
import m3j.front.RepeatStmt;
import m3j.front.ReturnStmt;
import m3j.front.SetExpr;
import m3j.front.Stmt;
import m3j.front.TryFinStmt;
import m3j.front.TryStmt;
import m3j.front.TypeCaseStmt;
import m3j.front.WhileStmt;
import m3j.front.WithStmt;

/** Visits the statements and expressions of a statement list. */
final class Walk {
    private Walk() {}

    /** Calls s on every statement and e on every expression below the list. */
    static void stmts(Stmt.T list, Consumer<Stmt.T> s, Consumer<Expr.T> e) {
        for (Stmt.T t = list; t != null; t = t.next) {
            stmt(t, s, e);
        }
    }

    static void stmt(Stmt.T t, Consumer<Stmt.T> s, Consumer<Expr.T> e) {
        s.accept(t);
        switch (t) {
            case AssertStmt.P p -> { expr(p.cond, e); expr(p.msg, e); }
            case AssignStmt.P p -> { expr(p.lhs, e); expr(p.rhs, e); }
            case BlockStmt.P p -> stmts(p.body, s, e);
            case CallStmt.P p -> expr(p.e, e);
            case EvalStmt.P p -> expr(p.e, e);
            case CaseStmt.P p -> {
                expr(p.expr, e);
                if (p.bodies != null) {
                    for (Stmt.T b : p.bodies) {
                        stmts(b, s, e);
                    }
                }
                stmts(p.elseBody, s, e);
            }
            case ForStmt.P p -> { expr(p.from, e); expr(p.limit, e); expr(p.step, e); stmts(p.body, s, e); }
            case IfStmt.P p -> {
                for (IfStmt.Clause c = p.clauses; c != null; c = c.next) {
                    expr(c.cond, e);
                    stmts(c.body, s, e);
                }
                stmts(p.elseBody, s, e);
            }
            case LockStmt.P p -> { expr(p.mutex, e); stmts(p.body, s, e); }
            case LoopStmt.P p -> stmts(p.body, s, e);
            case RaiseStmt.P p -> expr(p.arg, e);
            case RepeatStmt.P p -> { stmts(p.body, s, e); expr(p.expr, e); }
            case ReturnStmt.P p -> expr(p.expr, e);
            case TryFinStmt.P p -> { stmts(p.body, s, e); stmts(p.finally_, s, e); }
            case TryStmt.P p -> {
                stmts(p.body, s, e);
                for (TryStmt.Handler h = p.handles; h != null; h = h.next) {
                    stmts(h.body, s, e);
                }
                stmts(p.elseBody, s, e);
            }
            case TypeCaseStmt.P p -> {
                expr(p.expr, e);
                for (TypeCaseStmt.Case c = p.cases; c != null; c = c.next) {
                    stmts(c.stmt, s, e);
                }
                stmts(p.elseBody, s, e);
            }
            case WhileStmt.P p -> { expr(p.cond, e); stmts(p.body, s, e); }
            case WithStmt.P p -> { expr(p.expr, e); stmts(p.body, s, e); }
            default -> {}
        }
    }

    static void expr(Expr.T x, Consumer<Expr.T> e) {
        if (x == null) {
            return;
        }
        e.accept(x);
        switch (x) {
            case ExprRep.Tab p -> { expr(p.a, e); expr(p.b, e); }
            case ExprRep.Ta p -> expr(p.a, e);
            case CallExpr.T p -> {
                expr(p.proc, e);
                if (p.args != null) {
                    for (Expr.T a : p.args) {
                        expr(a, e);
                    }
                }
            }
            case CheckExpr.P p -> expr(p.expr, e);
            case ConsExpr.P p -> expr(p.base, e);
            case ArrayExpr.T p -> {
                if (p.args != null) {
                    for (Expr.T a : p.args) {
                        expr(a, e);
                    }
                }
            }
            case RecordExpr.P p -> {
                if (p.args != null) {
                    for (Expr.T a : p.args) {
                        expr(a, e);
                    }
                }
            }
            case SetExpr.P p -> {
                if (p.args != null) {
                    for (Expr.T a : p.args) {
                        expr(a, e);
                    }
                }
            }
            case QualifyExpr.P p -> expr(p.lhsExpr, e);
            case NarrowExpr.P p -> expr(p.expr, e);
            case KeywordExpr.P p -> expr(p.expr, e);
            default -> {}
        }
    }
}
