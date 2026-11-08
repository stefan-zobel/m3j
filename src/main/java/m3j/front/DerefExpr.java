// Java port of CM3's m3-sys/m3front/src/exprs/DerefExpr.i3 and DerefExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.util.AssertError;

// File: DerefExpr.m3
// Last modified on Fri Feb 24 07:43:48 PST 1995 by kalsow
//      modified on Thu Nov 29 06:04:10 1990 by muller

public final class DerefExpr {
    private DerefExpr() {}

    public static final class P extends ExprRep.Ta {
        public CG.Val tmp;

        @Override
        public Type.T typeOf() {
            return DerefExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return DerefExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            DerefExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            DerefExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            DerefExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepLV(boolean traced) {
            DerefExpr.prepLV(this, traced);
        }

        @Override
        public void compileLV(boolean traced, boolean StaticOnly) {
            DerefExpr.compileLV(this, traced, StaticOnly);
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
        public boolean isWritable(boolean lhs) {
            return ExprRep.isAlways(this, lhs);
        }

        @Override
        public boolean isDesignator(boolean lhs) {
            return ExprRep.isAlways(this, lhs);
        }

        @Override
        public void note_write() {
            noteWrites(this);
        }

        // Port: the overrides evaluate := ExprRep.NoValue, isEqual :=
        // ExprRep.EqCheckA, getBounds := ExprRep.NoBounds, isZeroes :=
        // ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral, prepLiteral
        // := ExprRep.NoPrepLiteral and genLiteral := ExprRep.NoLiteral
        // restate the inherited methods.
    }

    public static Expr.T new_(Expr.T a) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.a = NilChkExpr.new_(a);
        p.origin = p.a.origin;
        p.tmp = null;
        return p;
    }

    public static void setOffset(Expr.T e, long n) {
        if (e == null) {
            // nothing
        } else if (e instanceof P p) {
            NilChkExpr.setOffset(p.a, n);
        } else {
            // nothing
        }
    }

    private static Type.T typeOf(P p) {
        Type.T ta;
        Type.T[] target = new Type.T[1];
        ta = Expr.typeOf(p.a);
        if (RefType.split(ta, target)) {
            return target[0];
        } else {
            return ErrType.T;
        }
    }

    private static Type.T repTypeOf(P p) {
        Type.T ta;
        Type.T[] target = new Type.T[1];
        ta = Expr.repTypeOf(p.a);
        if (RefType.split(ta, target)) {
            return Type.stripPacked(target[0]);
        } else {
            return ErrType.T;
        }
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T tx, ta;
        Type.T[] target = new Type.T[1];
        long[] err0 = new long[1], err1 = new long[1], warn = new long[1];
        Error.count(err0, warn);
        Expr.typeCheck(p.a, cs);
        tx = Expr.typeOf(p.a);
        Error.count(err1, warn);
        ta = Type.base(tx);
        target[0] = null;
        if (((tx == null) || (tx == ErrType.T)) && (err0[0] != err1[0])) {
            // already an error, don't generate any more
            target[0] = ErrType.T;
        } else if (!RefType.split(ta, target)) {
            Error.msg("cannot dereference a non-REF value");
            target[0] = ErrType.T;
        } else if ((target[0] == null)) {
            Error.msg("cannot dereference REFANY, ADDRESS, or NULL");
            target[0] = ErrType.T;
        }
        p.type = target[0];
    }

    private static void needsAddress(P p) {
        // ok
    }

    private static void prep(P p) {
        Type.Info info = new Type.Info();
        Expr.prep(p.a);
        if (Host.doIncGC) {
            Type.checkInfo(p.type, info);
            if (info.isTraced) {
                switch (info.class_) {
                    case Object, Opaque, Ref -> {
                        compile(p, /* StaticOnly := */ false);
                        RunTyme.emitCheckLoadTracedRef();
                        p.tmp = CG.pop();
                    }
                    default -> {
                        // no check
                    }
                }
            }
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        Type.T t = p.type;
        Type.Info info = new Type.Info();
        AssertError.check(!StaticOnly, "DerefExpr");
        if (p.tmp != null) {
            CG.push(p.tmp);
            CG.free(p.tmp);
            p.tmp = null;
            return;
        }
        Expr.compile(p.a, StaticOnly);
        Type.checkInfo(t, info);
        CG.forceStacked();
        // ^'cause alignment applies to the referent, not the pointer
        CG.boost_addr_alignment(info.alignment);
        Type.loadScalar(t);
    }

    private static void prepLV(P p, boolean traced) {
        Type.Info info = new Type.Info();
        Expr.prep(p.a);
        if (traced && Host.doGenGC) {
            Type.checkInfo(p.type, info);
            if (!info.isTraced) {
                return;
            }
            Type.checkInfo(Expr.typeOf(p.a), info);
            if (!info.isTraced) {
                return;
            }
            Expr.compile(p.a);
            RunTyme.emitCheckStoreTraced();
            p.tmp = CG.pop();
        }
    }

    private static void compileLV(P p, boolean traced, boolean StaticOnly) {
        Type.Info info = new Type.Info();
        AssertError.check(!StaticOnly, "DerefExpr");
        if (p.tmp != null) {
            AssertError.check(traced, "DerefExpr");
            CG.push(p.tmp);
            CG.free(p.tmp);
            p.tmp = null;
        } else {
            Expr.compile(p.a);
        }
        Type.checkInfo(p.type, info);
        CG.forceStacked();
        // ^ Because alignment applies to the referent, not the pointer
        CG.boost_addr_alignment(info.alignment);
    }

    private static void noteWrites(P p) {
        Expr.noteWrite(p.a);
    }
}
