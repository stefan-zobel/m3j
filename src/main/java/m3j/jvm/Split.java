// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import m3j.front.BlockStmt;
import m3j.front.CaseStmt;
import m3j.front.Expr;
import m3j.front.ExitStmt;
import m3j.front.ForStmt;
import m3j.front.IfStmt;
import m3j.front.LockStmt;
import m3j.front.LoopStmt;
import m3j.front.NamedExpr;
import m3j.front.RepeatStmt;
import m3j.front.ReturnStmt;
import m3j.front.Scope;
import m3j.front.Stmt;
import m3j.front.TryFinStmt;
import m3j.front.TryStmt;
import m3j.front.TypeCaseStmt;
import m3j.front.Value;
import m3j.front.VarExpr;
import m3j.front.Variable;
import m3j.front.WhileStmt;
import m3j.front.WithStmt;

/**
 * The plan to split a procedure whose method would be too large: runs of
 * statements (chunks) become methods of their own. A chunk method takes the
 * frame of the procedure, which holds every variable that a chunk uses but
 * does not declare, and returns 0 if its statements complete, 1 after a
 * RETURN (the value is in the frame) and 2 after an EXIT of a loop around
 * it. The sizes are estimates; the caller measures the methods and plans
 * again with a smaller limit if one is still too large.
 */
final class Split {

    /** A run of count statements of a list, from first. */
    static final class Chunk {
        final int id;
        final Stmt.T first;
        final int count;
        final boolean returns;
        final boolean exits;

        Chunk(int id, Stmt.T first, int count, boolean returns, boolean exits) {
            this.id = id;
            this.first = first;
            this.count = count;
            this.returns = returns;
            this.exits = exits;
        }
    }

    /** the chunks, by their first statement */
    final Map<Stmt.T, Chunk> chunks = new IdentityHashMap<>();
    /** the variables of the procedure that chunks use but do not declare */
    final Set<Variable.T> frameVars = new LinkedHashSet<>();
    /** true if a chunk returns a value through the frame */
    boolean returnsValue;

    private final int limit;
    /** the variables of the procedure that may live in the frame (not WITH names) */
    private final Set<Variable.T> own;
    /** the variables bound by WITH */
    private final Set<Variable.T> withVars;

    /** The cost of a call of a chunk method (with the dispatch of its result). */
    private static final int CALL = 24;

    private Split(int limit, Set<Variable.T> own, Set<Variable.T> withVars) {
        this.limit = limit;
        this.own = own;
        this.withVars = withVars;
    }

    /**
     * Plans the chunks of the statements body of a procedure (or of the body
     * of the unit) whose variables (formals and locals) are vars, so that no
     * method is estimated larger than limit bytes.
     */
    static Split plan(Stmt.T body, List<Variable.T> vars, int limit) {
        Set<Variable.T> withVars = new HashSet<>();
        Set<Variable.T> own = new HashSet<>(vars);
        Walk.stmts(body, s -> {
            for (Scope.T sc : scopes(s)) {
                for (Value.T v = Scope.toList(sc); v != null; v = v.next) {
                    if (v instanceof Variable.T var) {
                        (s instanceof WithStmt.P ? withVars : own).add(var);
                    }
                }
            }
        }, e -> {});
        Split sp = new Split(limit, own, withVars);
        sp.list(body);
        return sp;
    }

    // --------------------------------------------------------------- sizes

    /** An estimate of the bytecode of a statement list (without chunks). */
    static int estimate(Stmt.T list) {
        int[] n = {0};
        Walk.stmts(list, s -> n[0] += 6, e -> n[0] += 6);
        return n[0];
    }

    /** Plans the list; returns its estimated size afterwards. */
    private int list(Stmt.T list) {
        List<Stmt.T> stmts = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        int total = 0;
        for (Stmt.T t = list; t != null; t = t.next) {
            int sz = stmt(t);
            stmts.add(t);
            sizes.add(sz);
            total += sz;
        }
        if (total <= limit) {
            return total;
        }
        // runs of statements of at most half the limit become chunks
        int result = 0;
        int i = 0;
        while (i < stmts.size()) {
            int j = i, sum = 0;
            while (j < stmts.size() && (j == i || sum + sizes.get(j) <= limit / 2)) {
                sum += sizes.get(j);
                j++;
            }
            if (sum > CALL * 2 && chunk(stmts.get(i), j - i)) {
                result += CALL;
            } else {
                result += sum;
            }
            i = j;
        }
        return result;
    }

    /** Plans the lists in statement s; returns its estimated size afterwards. */
    private int stmt(Stmt.T s) {
        List<Stmt.T> lists = children(s);
        int own = 6;
        int[] n = {0};
        Walk.stmt(s, x -> {}, e -> n[0] += 6);
        // the expressions of s itself: all of them less those of its lists
        for (Stmt.T l : lists) {
            n[0] -= estimateExprs(l);
        }
        own += Math.max(n[0], 0);
        int inner = 0;
        int[] sizes = new int[lists.size()];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = list(lists.get(i));
            inner += sizes[i];
        }
        if (own + inner > limit) {
            // too large even so: each sizable list of s becomes a chunk
            inner = 0;
            for (int i = 0; i < sizes.length; i++) {
                Stmt.T l = lists.get(i);
                if (sizes[i] > CALL * 2 && chunk(l, length(l))) {
                    inner += CALL;
                } else {
                    inner += sizes[i];
                }
            }
        }
        return own + inner;
    }

    private static int estimateExprs(Stmt.T list) {
        int[] n = {0};
        Walk.stmts(list, s -> {}, e -> n[0] += 6);
        return n[0];
    }

    private static int length(Stmt.T list) {
        int n = 0;
        for (Stmt.T t = list; t != null; t = t.next) {
            n++;
        }
        return n;
    }

    /** The statement lists directly in s. */
    private static List<Stmt.T> children(Stmt.T s) {
        List<Stmt.T> r = new ArrayList<>();
        switch (s) {
            case BlockStmt.P p -> r.add(p.body);
            case CaseStmt.P p -> {
                if (p.bodies != null) {
                    for (Stmt.T b : p.bodies) {
                        r.add(b);
                    }
                }
                r.add(p.elseBody);
            }
            case ForStmt.P p -> r.add(p.body);
            case IfStmt.P p -> {
                for (IfStmt.Clause c = p.clauses; c != null; c = c.next) {
                    r.add(c.body);
                }
                r.add(p.elseBody);
            }
            case LockStmt.P p -> r.add(p.body);
            case LoopStmt.P p -> r.add(p.body);
            case RepeatStmt.P p -> r.add(p.body);
            case TryFinStmt.P p -> {
                r.add(p.body);
                r.add(p.finally_);
            }
            case TryStmt.P p -> {
                r.add(p.body);
                for (TryStmt.Handler h = p.handles; h != null; h = h.next) {
                    r.add(h.body);
                }
                r.add(p.elseBody);
            }
            case TypeCaseStmt.P p -> {
                for (TypeCaseStmt.Case c = p.cases; c != null; c = c.next) {
                    r.add(c.stmt);
                }
                r.add(p.elseBody);
            }
            case WhileStmt.P p -> r.add(p.body);
            case WithStmt.P p -> r.add(p.body);
            default -> {}
        }
        r.removeIf(x -> x == null);
        return r;
    }

    /** The scopes that statement s opens. */
    private static List<Scope.T> scopes(Stmt.T s) {
        List<Scope.T> r = new ArrayList<>();
        switch (s) {
            case BlockStmt.P b -> r.add(b.scope);
            case ForStmt.P f -> r.add(f.scope);
            case WithStmt.P w -> r.add(w.scope);
            case TryStmt.P t -> {
                for (TryStmt.Handler h = t.handles; h != null; h = h.next) {
                    r.add(h.scope);
                }
            }
            case TypeCaseStmt.P t -> {
                for (TypeCaseStmt.Case c = t.cases; c != null; c = c.next) {
                    r.add(c.scope);
                }
            }
            default -> {}
        }
        r.removeIf(x -> x == null);
        return r;
    }

    // -------------------------------------------------------------- chunks

    /**
     * Makes the run of count statements from first a chunk, if it can be
     * one: it uses no WITH name bound outside it and no variable that is not
     * the procedure's. Returns true if it did.
     */
    private boolean chunk(Stmt.T first, int count) {
        Chunk old = chunks.get(first);
        if (old != null) {
            return old.count == count;
        }
        Set<Variable.T> declared = new HashSet<>();
        List<Variable.T> used = new ArrayList<>();
        boolean[] ok = {true};
        java.util.function.Consumer<Expr.T> visit = e -> {
            Value.T v = null;
            int[] name = new int[1];
            Value.T[] obj = new Value.T[1];
            if (e instanceof NamedExpr.P && NamedExpr.split(e, name, obj)) {
                v = Value.base(obj[0]);
            } else if (e instanceof VarExpr.P ve) {
                v = ve.v;
                if (v == null) {
                    ok[0] = false;
                }
            }
            if (v instanceof Variable.T var && !Code.isGlobal(var)) {
                used.add(var);
            }
        };
        Stmt.T t = first;
        for (int i = 0; i < count; i++, t = t.next) {
            Walk.stmt(t, s -> {
                for (Scope.T sc : scopes(s)) {
                    for (Value.T v = Scope.toList(sc); v != null; v = v.next) {
                        if (v instanceof Variable.T var) {
                            declared.add(var);
                            if (var.initExpr != null) {
                                Walk.expr(var.initExpr, visit);
                            }
                        }
                    }
                }
            }, visit);
        }
        Set<Variable.T> need = new LinkedHashSet<>();
        for (Variable.T v : used) {
            if (declared.contains(v)) {
                continue;
            }
            if (withVars.contains(v) || !own.contains(v)) {
                ok[0] = false;
            }
            need.add(v);
        }
        if (!ok[0]) {
            return false;
        }
        boolean returns = false;
        boolean exits = false;
        t = first;
        for (int i = 0; i < count; i++, t = t.next) {
            returns |= contains(t, ReturnStmt.P.class);
            exits |= exitsOut(t);
        }
        frameVars.addAll(need);
        if (returns) {
            returnsValue = true;
        }
        chunks.put(first, new Chunk(chunks.size(), first, count, returns, exits));
        return true;
    }

    private static boolean contains(Stmt.T s, Class<?> c) {
        boolean[] r = {false};
        Walk.stmt(s, x -> r[0] |= c.isInstance(x), e -> {});
        return r[0];
    }

    /** True if s has an EXIT that leaves no loop inside s. */
    private static boolean exitsOut(Stmt.T s) {
        switch (s) {
            case ExitStmt.P _ -> {
                return true;
            }
            case ForStmt.P _, LoopStmt.P _, WhileStmt.P _, RepeatStmt.P _ -> {
                return false;
            }
            default -> {
                for (Stmt.T l : children(s)) {
                    for (Stmt.T t = l; t != null; t = t.next) {
                        if (exitsOut(t)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        }
    }
}
