// Java port of CM3's m3-sys/m3front/src/exprs/TypeExpr.i3 and TypeExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.Target;

// File: TypeExpr.m3
// Last modified on Fri Feb 24 16:47:54 PST 1995 by kalsow
//      modified on Tue Aug 28 06:03:04 1990 by muller

public final class TypeExpr {
    private TypeExpr() {}

    public static final class P extends Expr.T {
        public Type.T t;

        @Override
        public Type.T typeOf() {
            return TypeExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return TypeExpr.typeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            TypeExpr.check(this, cs);
        }

        @Override
        public Expr.T evaluate() {
            return ExprRep.self(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public long exprAlign() {
            return typeExprAlign(this);
        }

        // Port: the overrides need_addr := ExprRep.NotAddressable, prep :=
        // ExprRep.NoPrep, compile := ExprRep.NoCompile, prepLV :=
        // ExprRep.NotLValue, compileLV := ExprRep.NotLValueBool, prepBR,
        // compileBR := ExprRep.NotBoolean, getBounds := ExprRep.NoBounds,
        // isWritable, isDesignator, isZeroes := ExprRep.IsNever,
        // genFPLiteral := ExprRep.NoFPLiteral, prepLiteral :=
        // ExprRep.NoPrepLiteral, genLiteral := ExprRep.NoLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
    }

    public static Expr.T new_(Type.T t) {
        P p = new P();
        ExprRep.init(p);
        p.t = t;
        p.type = null;
        return p;
    }

    public static boolean split(Expr.T e, Type.T[] t) {
        Value.T[] obj = new Value.T[1];
        int[] name = new int[1];
        if (e == null) {
            return false;
        } else if (e instanceof P p) {
            t[0] = p.t;
            return true;
        } else {
            if ((NamedExpr.split(e, name, obj) || QualifyExpr.split(e, obj))
                && (Value.classOf(obj[0]) == Value.Class.Type)) {
                t[0] = Value.toType(obj[0]);
                return true;
            } else {
                return false;
            }
        }
    }

    private static Type.T typeOf(P p) {
        return null;
    }

    private static void check(P p, M3.CheckState cs) {
        p.t = Type.check(p.t);
    }

    private static long typeExprAlign(P p) {
        Type.T type;
        Type.Info typeInfo = new Type.Info();
        type = Type.stripPacked(p.t);
        if (type != null) {
            Type.checkInfo(type, typeInfo);
            return typeInfo.alignment;
        }
        return Target.Word8.align;
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e == null) {
            return false;
        } else if (e instanceof P b) {
            return Type.isEqual(a.t, b.t, x);
        } else {
            return false;
        }
    }
}
