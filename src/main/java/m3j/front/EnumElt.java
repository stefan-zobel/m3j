// Java port of CM3's m3-sys/m3front/src/values/EnumElt.i3 and EnumElt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.TInt;

// File: EnumElt.i3
// Last Modified On Fri Jun 24 09:29:39 PDT 1994 By kalsow

// File: EnumElt.m3
// Last modified on Wed Mar  1 08:41:03 PST 1995 by kalsow
//      modified on Fri Apr 20 06:45:16 1990 by muller

public final class EnumElt {
    private EnumElt() {}

    public static final class T extends Value.T {
        public int[] value = TInt.newInt();
        public Type.T parent;

        @Override
        public void typeCheck(M3.CheckState cs) {
            check(this, cs);
        }

        @Override
        public void load() {
            EnumElt.load(this);
        }

        @Override
        public Expr.T toExpr() {
            return EnumElt.toExpr(this);
        }

        @Override
        public Type.T typeOf() {
            return EnumElt.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return EnumElt.typeOf(this);
        }

        @Override
        public long add_fp_tag(M3.FPInfo x) {
            return addFPTag(this, x);
        }

        @Override
        public Type.T fp_type() {
            return fpType(this);
        }

        // Port: the overrides set_globals := ValueRep.NoInit, declare :=
        // ValueRep.Never, const_init := ValueRep.NoInit, need_init :=
        // ValueRep.Never, lang_init, user_init := ValueRep.NoInit, toType
        // := ValueRep.NoType and base := ValueRep.Self restate the
        // inherited methods.
    }

    public static Value.T new_(int name, int[] value, Type.T parent) {
        T t = new T();
        ValueRep.init(t, name, Value.Class.Expr);
        t.readonly = true;
        TInt.assign(t.value, value);
        t.parent   = parent;
        return t;
    }

    public static boolean isEqual(Value.T va, Value.T vb) {
        T a = (T) va;
        T b = (T) vb;
        return (a.name == b.name) && TInt.EQ(a.value, b.value);
    }

    private static void check(T t, M3.CheckState cs) {
        // no checking needed
    }

    private static void load(T t) {
        CG.load_integer(M3.target.Integer.cg_type, t.value);
    }

    private static Expr.T toExpr(T t) {
        return EnumExpr.new_(t.parent, t.value);
    }

    private static Type.T typeOf(T t) {
        return t.parent;
    }

    private static long addFPTag(T t, M3.FPInfo x) {
        ValueRep.fpStart(t, x, "ENUM-ELT ", 0, /* global := */ false);
        return 0;
    }

    private static Type.T fpType(T t) {
        return null;
    }
}
