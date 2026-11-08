// Java port of CM3's m3-sys/m3front/src/exprs/KeywordExpr.i3 and KeywordExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: KeywordExpr.m3
// Last modified on Fri Feb 24 16:43:45 PST 1995 by kalsow
//      modified on Sat Dec  8 00:21:11 1990 by muller

public final class KeywordExpr {
    private KeywordExpr() {}

    public static final class P extends Expr.T {
        public Expr.T expr;
        public int key;

        @Override
        public Type.T typeOf() {
            return KeywordExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return KeywordExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            KeywordExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            KeywordExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            KeywordExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepLV(boolean traced) {
            KeywordExpr.prepLV(this, traced);
        }

        @Override
        public void compileLV(boolean traced, boolean StaticOnly) {
            KeywordExpr.compileLV(this, traced, StaticOnly);
        }

        @Override
        public void prepBR(long true_, long false_, int freq) {
            ExprRep.prepNoBranch(this, true_, false_, freq);
        }

        @Override
        public void compileBR(long true_, long false_, int freq) {
            ExprRep.noBranch(this, true_, false_, freq);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public void getBounds(int[] min, int[] max) {
            bounder(this, min, max);
        }

        @Override
        public boolean isWritable(boolean lhs) {
            return KeywordExpr.isWritable(this, lhs);
        }

        @Override
        public boolean isDesignator(boolean lhs) {
            return KeywordExpr.isDesignator(this, lhs);
        }

        @Override
        public boolean isZeroes(boolean lhs) {
            return KeywordExpr.isZeroes(this, lhs);
        }

        @Override
        public void prepLiteral(Type.T type, boolean is_const) {
            KeywordExpr.prepLiteral(this, type, is_const);
        }

        @Override
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            KeywordExpr.genLiteral(this, offset, type, is_const);
        }

        @Override
        public void note_write() {
            noteWrites(this);
        }

        @Override
        public long exprAlign() {
            return keywordExprAlign(this);
        }

        // Port: the override genFPLiteral := ExprRep.NoFPLiteral restates
        // the inherited method.
    }

    public static Expr.T new_(int key, Expr.T arg) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.key  = key;
        p.expr = arg;
        return p;
    }

    public static boolean is(Expr.T e) {
        if (e == null) {
            return false;
        } else if (e instanceof P) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean split(Expr.T e, int[] key, Expr.T[] value) {
        if (e == null) {
            return false;
        } else if (e instanceof P p) {
            key[0] = p.key;
            value[0] = p.expr;
            return true;
        } else {
            return false;
        }
    }

    private static Type.T typeOf(P p) {
        return Expr.typeOf(p.expr);
    }

    private static Type.T repTypeOf(P p) {
        return Expr.repTypeOf(p.expr);
    }

    private static void check(P p, M3.CheckState cs) {
        Expr.typeCheck(p.expr, cs);
        p.type = Expr.typeOf(p.expr);
    }

    /** Just inherit from operand expression. */
    private static long keywordExprAlign(P p) {
        return Expr.alignment(p.expr);
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e == null) {
            return false;
        } else if (e instanceof P b) {
            return Expr.isEqual(a.expr, b.expr, x);
        } else {
            return Expr.isEqual(a.expr, e, x);
        }
    }

    private static void needsAddress(P p) {
        Expr.needsAddress(p.expr);
    }

    private static void prep(P p) {
        Expr.prep(p.expr);
    }

    private static void compile(P p, boolean StaticOnly) {
        Expr.compile(p.expr, StaticOnly);
    }

    private static void prepLV(P p, boolean traced) {
        Expr.prepLValue(p.expr, traced);
    }

    private static void compileLV(P p, boolean traced, boolean StaticOnly) {
        Expr.compileLValue(p.expr, traced, StaticOnly);
    }

    private static void bounder(P p, int[] min, int[] max) {
        Expr.getBounds(p.expr, min, max);
    }

    private static boolean isDesignator(P p, boolean lhs) {
        return Expr.isDesignator(p.expr);
    }

    private static boolean isWritable(P p, boolean lhs) {
        return Expr.isWritable(p.expr, lhs);
    }

    private static Expr.T fold(P p) {
        Expr.T e = Expr.constValue(p.expr);
        if (e == null) {
            return null;
        } else {
            p.expr = e;
            return p;
        }
    }

    private static boolean isZeroes(P p, boolean lhs) {
        return Expr.isZeroes(p.expr);
    }

    private static void prepLiteral(P p, Type.T type, boolean is_const) {
        Expr.prepLiteral(p.expr, type, is_const);
    }

    private static void genLiteral(P p, long offset, Type.T type, boolean is_const) {
        Expr.genLiteral(p.expr, offset, type, is_const);
    }

    private static void noteWrites(P p) {
        Expr.noteWrite(p.expr);
    }
}
