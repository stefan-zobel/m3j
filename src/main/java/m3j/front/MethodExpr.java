// Java port of CM3's m3-sys/m3front/src/exprs/MethodExpr.i3 and MethodExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: MethodExpr.m3
// Last modified on Fri Feb 24 16:44:09 PST 1995 by kalsow
//      modified on Tue Feb 19 01:32:23 1991 by muller

public final class MethodExpr {
    private MethodExpr() {}

    public static final class P extends Expr.T {
        public Type.T object;
        public Value.T method;
        public Type.T holder;
        public int name;

        @Override
        public Type.T typeOf() {
            return MethodExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return MethodExpr.typeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            MethodExpr.check(this, cs);
        }

        @Override
        public void compile(boolean StaticOnly) {
            MethodExpr.compile(this, StaticOnly);
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
        public void genFPLiteral(M3Buf.T mbuf) {
            MethodExpr.genFPLiteral(this, mbuf);
        }

        @Override
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            MethodExpr.genLiteral(this, offset, type, is_const);
        }

        @Override
        public long exprAlign() {
            return ExprRep.exprAddrAlign(this);
        }

        // Port: the overrides need_addr := ExprRep.NotAddressable, prep :=
        // ExprRep.NoPrep, prepLV := ExprRep.NotLValue, compileLV :=
        // ExprRep.NotLValueBool, prepBR, compileBR := ExprRep.NotBoolean,
        // getBounds := ExprRep.NoBounds, isWritable, isDesignator, isZeroes
        // := ExprRep.IsNever, prepLiteral := ExprRep.NoPrepLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
    }

    /** evaluates to the default for the given method */
    public static Expr.T new_(Type.T object, int name, Value.T method, Type.T holder) {
        P p = new P();
        ExprRep.init(p);
        p.object  = object;
        p.name    = name;
        p.method  = method;
        p.holder  = holder;
        return p;
    }

    private static Type.T typeOf(P p) {
        Method.Info m = new Method.Info();
        Method.splitX(p.method, m);
        return ProcType.methodSigAsProcSig(m.signature, p.object);
    }

    private static void check(P p, M3.CheckState cs) {
        p.object = Type.check(p.object);
        p.holder = Type.check(p.holder);
        Value.typeCheck(p.method, cs);
        Type.check(typeOf(p));
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e == null) {
            return false;
        } else if (e instanceof P b) {
            return (a.name == b.name)
                && Type.isEqual(a.object, b.object, x);
        } else {
            return false;
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        long x = ObjectType.methodOffset(p.holder);
        Method.Info method = new Method.Info();
        AssertError.check(!StaticOnly, "MethodExpr");
        Type.compile(p.object);
        Method.splitX(p.method, method);
        Type.loadInfo(p.object, M3.rt.OTC_defaultMethods, /* addr := */ true);
        if ((x >= 0)) {
            method.offset += x;
        } else { // runtime offset to methods
            Type.loadInfo(p.holder, M3.rt.OTC_methodOffset);
            CG.index_bytes(Target.Byte);
        }
        CG.boost_addr_alignment(M3.target.Address.align);
        CG.load_indirect(CGType.Addr, method.offset, M3.target.Address.size);
        CG.boost_addr_alignment(CG.procAlign());
    }

    private static void genFPLiteral(P p, M3Buf.T buf) {
        M3Buf.putText(buf, "METHOD<");
        //** M3Buf.PutText (buf, Type.Name (p.object)); **
        M3ID.put(M3.ids, buf, p.name);
        M3Buf.putChar(buf, '>');
    }

    private static void genLiteral(P p, long offset, Type.T type, boolean is_const) {
        String m_name = Value.globalName(p.method, /* dots := */ false);
        String t_name = Type.name(p.object);
        String name   = t_name + "_" + m_name;
        long uid      = Type.globalUID(type);
        M3CG.Var var  = CG.import_global(M3ID.add(M3.ids, name), M3.target.Address.size,
                                         M3.target.Address.align, CGType.Addr, (int) uid);
        CG.init_var(offset, var, 0, is_const);
        Type.compile(type);
        Error.ID(p.name,
        "CM3 restriction: default method must be a compile-time constant");
    }
}
