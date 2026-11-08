// Java port of CM3's m3-sys/m3front/src/stmts/CaseStmt.i3 and CaseStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.util.AssertError;

// File: CaseStmt.i3
// Last Modified On Fri Jun 24 09:20:53 PDT 1994 By kalsow

// File: CaseStmt.m3
// Last modified on Fri Oct 21 14:37:48 PDT 1994 by kalsow
//      modified on Fri Feb 15 04:03:38 1991 by muller

public final class CaseStmt {
    private CaseStmt() {}

    public static final class P extends Stmt.T {
        public Expr.T expr = null;
        public Tree tree = null;
        public Stmt.T[] bodies = null;
        public boolean complete = false;
        public boolean hasElse = false;
        public boolean badLabels = false;
        public Stmt.T elseBody = null;
        public long nCases = 0;

        @Override
        public void check(M3.CheckState cs) {
            CaseStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return CaseStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    /** Port: Tree = REF RECORD ... END. */
    public static final class Tree {
        public Tree less;
        public Tree greater;
        public Expr.T emin;
        public Expr.T emax;
        public final int[] min = TInt.newInt();
        public final int[] max = TInt.newInt();
        public long body;
        public long origin;
        public boolean bad;

        /** Port: the assignment {@code this^ := x^}. */
        void assign(Tree x) {
            less = x.less;
            greater = x.greater;
            emin = x.emin;
            emax = x.emax;
            TInt.assign(min, x.min);
            TInt.assign(max, x.max);
            body = x.body;
            origin = x.origin;
            bad = x.bad;
        }
    }

    // Port: StmtList = REF ARRAY OF Stmt.T is Stmt.T[].

    public static Stmt.T parse() {
        P p;
        boolean bar;
        p = new P();
        StmtRep.init(p);
        p.bodies = new Stmt.T[8];

        Scanner.match(Token.T.tCASE);
        p.expr = Expr.parse();
        Scanner.match(Token.T.tOF);
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
            parseCase(p);
            if ((Scanner.cur.token != Token.T.tBAR)) {
                break;
            }
            bar = true;
            Scanner.getToken(); // |
        }
        if ((bar)) {
            Scanner.fail("missing case");
        }
        if ((Scanner.cur.token == Token.T.tELSE)) {
            Scanner.getToken(); // ELSE
            p.hasElse = true;
            p.elseBody = Stmt.parse();
        }
        Scanner.match(Token.T.tEND);

        return p;
    }

    private static void parseCase(P p) {
        Tree t;
        while (true) {
            t = new Tree();
            t.origin = Scanner.offset;
            t.less = p.tree;
            p.tree = t;
            t.greater = null;
            t.emin = Expr.parse();
            t.emax = null;
            t.body = p.nCases;
            t.bad = false;
            if ((Scanner.cur.token == Token.T.tDOTDOT)) {
                Scanner.getToken(); // ..
                t.emax = Expr.parse();
            }
            if ((Scanner.cur.token != Token.T.tCOMMA)) {
                break;
            }
            Scanner.getToken(); // ,
        }
        Scanner.match(Token.T.tIMPLIES);
        if ((p.nCases > p.bodies.length - 1)) {
            expandBodies(p);
        }
        p.bodies[(int) p.nCases] = Stmt.parse();
        p.nCases++;
    }

    private static void expandBodies(P p) {
        Stmt.T[] old, new_;
        old = p.bodies;
        new_ = new Stmt.T[old.length * 2];
        for (int i = 0; i <= old.length - 1; i++) {
            new_[i] = old[i];
        }
        p.bodies = new_;
    }

    private static void check(P p, M3.CheckState cs) {
        Tree t, u, v;
        Type.T type;
        int[] min = TInt.newInt(), max = TInt.newInt(),
              minE = TInt.newInt(), maxE = TInt.newInt();
        // check out the selector
        Expr.typeCheck(p.expr, cs);
        type = Expr.typeOf(p.expr);
        if (!Type.isOrdinal(type)) {
            Error.msg("invalid expression type for case selector");
            p.badLabels = true;
            type = ErrType.T;
        }

        // reverse the tree nodes so they're in source order
        t = p.tree;
        u = null;
        while ((t != null)) {
            v = t.less;
            t.less = u;
            u = t;
            t = v;
        }
        p.tree = u;

        // type check the cases & build a tree
        Type.getBounds(type, min, max);
        t = p.tree;
        p.tree = null;
        while ((t != null)) {
            Scanner.offset = t.origin;
            u = t.less;
            p.tree = addNode(p, p.tree, t, type, min, max, cs);
            t = u;
        }

        // type check the bodies
        for (long i = 0; i <= p.nCases - 1; i++) {
            Stmt.typeCheck(p.bodies[(int) i], cs);
        }

        // check the else clause
        if ((p.hasElse)) {
            Stmt.typeCheck(p.elseBody, cs);
        }

        // check for a complete tree
        Expr.getBounds(p.expr, minE, maxE);
        p.complete = p.hasElse || completeTree(p.tree, minE, maxE);
        if ((!p.complete) && (!p.badLabels)) {
            Scanner.offset = p.origin;
            Error.warn(1, "CASE statement does not handle all possible values");
        }
    }

    private static Tree addNode(P p, Tree old, Tree new_, Type.T type,
                                int[] min, int[] max,
                                M3.CheckState cs) {
        TInt.assign(new_.min, checkLabel(p, new_, new_.emin, type, cs));
        if ((new_.emax != null)) {
            TInt.assign(new_.max, checkLabel(p, new_, new_.emax, type, cs));
        } else {
            TInt.assign(new_.max, new_.min);
        }

        if ((new_.bad) || (p.badLabels)) {
            // don't generate another error message
        } else if (TInt.LT(new_.min, min) || TInt.LT(max, new_.max)) {
            Error.msg("case labels out of range");
            new_.bad = true;
        }

        return addToTree(p, old, new_);
    }

    private static int[] checkLabel(P p, Tree tree, Expr.T e, Type.T type,
                                    M3.CheckState cs) {
        Type.T[] t = new Type.T[1];
        int[] i = TInt.newInt();
        long[] n_errs = new long[1], n_xxx = new long[1], n_warns = new long[1];
        Error.count(n_errs, n_warns);
          Expr.typeCheck(e, cs);
        Error.count(n_xxx, n_warns);
        if ((n_xxx[0] > n_errs[0])) {
            tree.bad = true;
            return TInt.copy(TInt.Zero);
        }

        t[0] = Expr.typeOf(e);
        if ((!p.badLabels) && !Type.isAssignable(type, t[0])) {
            Error.msg("case label not compatible with selector");
            tree.bad = true;
        }

        e = Expr.constValue(e);
        if ((e == null) && (!tree.bad)) {
            Error.msg("case label must be constant");
            tree.bad = true;
        }
        TInt.assign(i, TInt.Zero);
        if (IntegerExpr.split(e, i, t) || EnumExpr.split(e, i, t)) {
            // nothing
        }
        return i;
    }

    private static Tree addToTree(P p, Tree old, Tree new_) {
        Tree z;
        boolean new_min, new_max;
        new_.less = null;
        new_.greater = null;
        if ((new_.bad) || (p.badLabels)) {
            // ignore this node
        } else if ((old == null)) {
            old = new_;
        } else if (TInt.LT(new_.max, old.min)) {
            old.less = addToTree(p, old.less, new_);
        } else if (TInt.LT(old.max, new_.min)) {
            old.greater = addToTree(p, old.greater, new_);
        } else if ((new_.body != old.body)) {
            Error.msg("duplicate labels in case statement");
            new_.bad = true;
        } else {
            // new and old overlap, but are in the same case arm
            Error.warn(2, "repeated labels in case arm");
            new_min = TInt.LT(new_.min, old.min);
            new_max = TInt.LT(old.max, new_.max);
            if (new_min && new_max) {
                z = new Tree();
                z.assign(new_);
                TInt.subtract(old.min, TInt.One, z.max);
                old.less = addToTree(p, old.less, z);
                TInt.add(old.max, TInt.One, new_.min);
                old.greater = addToTree(p, old.greater, new_);
            } else if (new_min) {
                TInt.subtract(old.min, TInt.One, new_.max);
                old.less = addToTree(p, old.less, new_);
            } else if (new_max) {
                TInt.add(old.max, TInt.One, new_.min);
                old.greater = addToTree(p, old.greater, new_);
            }
        }
        return old;
    }

    private static boolean completeTree(Tree t, int[] min_, int[] max_) {
        // Port: min and max are value parameters that the procedure
        // writes; they are copied here.
        int[] min = TInt.copy(min_), max = TInt.copy(max_);
        int[] x = TInt.newInt(), y = TInt.newInt();
        while ((t != null)) {
            if (TInt.LT(t.max, min) || TInt.LT(max, t.min)) {
                return TInt.LT(max, min);
            }
            if (TInt.subtract(t.min, min, x)
                && TInt.subtract(max, t.max, y)
                && TInt.LT(y, x)) {
                if (TInt.add(t.max, TInt.One, x)
                    && !TInt.LT(x, M3.target.Integer.min)
                    && !TInt.LT(M3.target.Integer.max, x)) {
                    if (!completeTree(t.greater, x, max)) {
                        return false;
                    }
                }
                // Sic: the range test is on x, not on the new max.
                if (!TInt.subtract(t.min, TInt.One, max)
                    || TInt.LT(x, M3.target.Integer.min)
                    || TInt.LT(M3.target.Integer.max, x)) {
                    return true;
                }
                t = t.less;
            } else {
                if (TInt.subtract(t.min, TInt.One, x)
                    && !TInt.LT(x, M3.target.Integer.min)
                    && !TInt.LT(M3.target.Integer.max, x)) {
                    if (!completeTree(t.less, min, x)) {
                        return false;
                    }
                }
                // Sic: the range test is on x, not on the new min.
                if (!TInt.add(t.max, TInt.One, min)
                    || TInt.LT(x, M3.target.Integer.min)
                    || TInt.LT(M3.target.Integer.max, x)) {
                    return true;
                }
                t = t.greater;
            }
        }
        return TInt.LT(max, min);
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        int[] minL = TInt.newInt(), maxL = TInt.newInt();
        Tree t;
        EnumSet<Stmt.Outcome> oc;
        long[] min_L = new long[1], max_L = new long[1];
        // find the smallest label
        TInt.assign(minL, M3.target.Integer.max);
        t = p.tree;
        while ((t != null)) {
            TInt.assign(minL, t.min);
            t = t.less;
        }

        // find the largest label
        TInt.assign(maxL, M3.target.Integer.min);
        t = p.tree;
        while ((t != null)) {
            TInt.assign(maxL, t.max);
            t = t.greater;
        }

        // collapse adjacent tree nodes
        p.tree = flattenTree(p.tree, null);

        if (TInt.toInt(minL, min_L) && TInt.toInt(maxL, max_L)
            && shouldBeIndexed(p, max_L[0], min_L[0])) {
            // generate an indexed table branch
            oc = genIndexedBranch(p, min_L[0], max_L[0], minL, maxL);
        } else {
            // generate an IF-ELSE structure
            oc = genIfTable(p);
            // ELSE generate a binary search table...
        }
        return oc;
    }

    private static Tree flattenTree(Tree t, Tree tail) {
        if ((t == null)) {
            return tail;
        }
        t.greater = flattenTree(t.greater, tail);
        return flattenTree(t.less, t);
    }

    private static boolean shouldBeIndexed(P p, long maxL, long minL) {
        Tree t;
        int[] last = TInt.newInt(), zz = TInt.newInt();
        long n_tests;
        long n_slots = maxL - minL; // Port: Word.Minus
        // don't bother with huge tables
        if ((n_slots > 4096) || (n_slots < 0)) {
            return false;
        }

        // don't bother with tiny tables
        // => count the number of IF tests that would be needed
        n_tests = 0;
        TInt.assign(last, M3.target.Integer.min);
        t = p.tree;
        while ((t != null)) {
            if (TInt.subtract(t.min, TInt.One, zz)
                && TInt.LT(last, zz)) {
                n_tests++;
            }
            n_tests++;
            TInt.assign(last, t.max);
            t = t.greater;
        }
        if ((n_tests < 8)) {
            return false;
        }

        // always build small tables
        if ((maxL - minL) <= 256) {
            return true;
        }

        // otherwise, use a table if the density is at least 0.05
        return (p.nCases * 20) > (maxL - minL);
    }

    private static EnumSet<Stmt.Outcome> genIndexedBranch(P p, long l_min, long l_max,
                                                          int[] L_min, int[] L_max) {
        Tree t;
        CG.Val x;
        boolean b;
        EnumSet<Stmt.Outcome> oc, xc;
        int[] e_min = TInt.newInt(), e_max = TInt.newInt();
        long l_else, l_end, l_case, l_bodies;
        long[] labels;
        long[] min = new long[1], max = new long[1];
        long j;
        Expr.getBounds(p.expr, e_min, e_max);

        // allocate the label's we need
        l_bodies = CG.next_label(p.nCases);
        l_else = CG.next_label();
        labels = new long[(int) (l_max - l_min + 1)];
        for (int i = 0; i <= labels.length - 1; i++) {
            labels[i] = l_else;
        }
        l_end = CG.next_label();

        // initialize the label table
        t = p.tree;
        while ((t != null)) {
            l_case = l_bodies + t.body;
            b = TInt.toInt(t.min, min);
            AssertError.check(b, "CaseStmt");
            b = TInt.toInt(t.max, max);
            AssertError.check(b, "CaseStmt");
            j = min[0] - l_min;
            while ((j <= max[0] - l_min)) {
                labels[(int) j] = l_case;
                if ((j == max[0] - l_min)) {
                    break;
                }
                j++;
            }
            t = t.greater;
        }

        // compute the index and translate it to a zero base
        Expr.prep(p.expr);
        Expr.compile(p.expr);
        if ((l_min != 0)) {
            // translate  [l_min .. l_max] => [0 .. l_max - l_min]
            CG.load_intt(l_min);
            CG.subtract(M3.target.Integer.cg_type);
        }

        // range check the index expression
        if (TInt.LE(L_min, e_min) && TInt.LE(e_max, L_max)) {
            // no range checking is required
        } else if (TInt.LE(L_min, e_min)) {
            // lower bound is OK
            x = CG.pop();
            CG.push(x);
            CG.load_intt(l_max - l_min);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.GT, l_else, CG.Never);
            CG.push(x);
            CG.free(x);
        } else if (TInt.LE(e_max, L_max)) {
            // upper bound is OK
            x = CG.pop();
            CG.push(x);
            CG.load_integer(M3.target.Integer.cg_type, TInt.Zero);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.LT, l_else, CG.Never);
            CG.push(x);
            CG.free(x);
        } else {
            // need to check both bounds
            x = CG.pop();
            CG.push(x);
            CG.load_integer(M3.target.Integer.cg_type, TInt.Zero);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.LT, l_else, CG.Never);
            CG.push(x);
            CG.load_intt(l_max - l_min);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.GT, l_else, CG.Never);
            CG.push(x);
            CG.free(x);
        }

        // generate the branch
        CG.case_jump(labels);

        // generate the table entries
        oc = EnumSet.noneOf(Stmt.Outcome.class);
        for (long i = 0; i <= p.nCases - 1; i++) {
            CG.set_label(l_bodies + i);
            xc = Stmt.compile(p.bodies[(int) i]);
            oc.addAll(xc);
            if (xc.contains(Stmt.Outcome.FallThrough)) {
                CG.jump(l_end);
            }
        }

        // generate the else clause
        CG.set_label(l_else);
        if ((p.hasElse)) {
            oc.addAll(Stmt.compile(p.elseBody));
        } else if ((!p.complete) && (Host.doCaseChk)) {
            CG.abort(M3CG.RuntimeError.UnhandledCase);
        }

        CG.set_label(l_end);
        return oc;
    }

    private static EnumSet<Stmt.Outcome> genIfTable(P p) {
        Tree t;
        CG.Val x;
        int[] e_min = TInt.newInt(), e_max = TInt.newInt();
        int[] next = TInt.newInt();
        EnumSet<Stmt.Outcome> oc, xc;
        long l_bodies, l_else, l_end;
        p.tree = collapseTree(p.tree);
        l_bodies = CG.next_label(p.nCases);
        l_else = CG.next_label();
        l_end = CG.next_label();
        oc = EnumSet.noneOf(Stmt.Outcome.class);

        // compile the tests & branches
        Expr.prep(p.expr);
        Expr.compile(p.expr);
        x = CG.pop();

        // walk the list of labels generating the goto's
        Expr.getBounds(p.expr, e_min, e_max);
        TInt.assign(next, e_min);
        t = p.tree;
        while ((t != null)) {
            CG.gen_location(t.origin);
            if (TInt.LT(next, t.min)) {
                CG.push(x);
                CG.load_integer(M3.target.Integer.cg_type, t.min);
                CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.LT, l_else, CG.Never);
            }
            CG.push(x);
            CG.load_integer(M3.target.Integer.cg_type, t.max);
            CG.if_compare(M3.target.Integer.cg_type, M3CG.CompareOp.LE, l_bodies + t.body, CG.Maybe);
            if (!TInt.add(t.max, TInt.One, next)
                || TInt.LT(next, M3.target.Integer.min)
                || TInt.LT(M3.target.Integer.max, next)) {
                if ((t.greater != null)) {
                    Error.msg("case label too large");
                }
                TInt.assign(next, t.max);
            }
            t = t.greater;
        }
        if (TInt.LE(next, e_max)) {
            CG.jump(l_else);
        }
        CG.free(x);

        // generate the bodies
        for (long i = 0; i <= p.nCases - 1; i++) {
            CG.set_label(l_bodies + i);
            xc = Stmt.compile(p.bodies[(int) i]);
            oc.addAll(xc);
            if (xc.contains(Stmt.Outcome.FallThrough)) {
                CG.jump(l_end);
            }
        }

        // generate the else clause
        CG.set_label(l_else);
        if ((p.hasElse)) {
            oc.addAll(Stmt.compile(p.elseBody));
        } else if ((!p.complete) && (Host.doCaseChk)) {
            CG.abort(M3CG.RuntimeError.UnhandledCase);
        }

        CG.set_label(l_end);
        return oc;
    }

    private static Tree collapseTree(Tree t) {
        Tree t1, t2;
        long c;
        int[] x = TInt.newInt(), xx = TInt.newInt();
        t1 = t;
        while ((t1 != null)) {
            c = t1.body;
            TInt.assign(x, t1.max);
            t2 = t1.greater;
            while ((t2 != null) && (t2.body == c)
                   && TInt.add(x, TInt.One, xx)
                   && TInt.EQ(xx, t2.min)) {
                TInt.assign(x, t2.max);
                t2 = t2.greater;
            }
            t1.greater = t2;
            TInt.assign(t1.max, x);
            t1 = t2;
        }
        return t;
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        EnumSet<Stmt.Outcome> oc = EnumSet.noneOf(Stmt.Outcome.class);
        for (long i = 0; i <= p.nCases - 1; i++) {
            oc.addAll(Stmt.getOutcome(p.bodies[(int) i]));
        }
        if ((p.hasElse)) {
            oc.addAll(Stmt.getOutcome(p.elseBody));
        }
        return oc;
    }
}
