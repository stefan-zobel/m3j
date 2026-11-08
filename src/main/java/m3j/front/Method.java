// Java port of CM3's m3-sys/m3front/src/values/Method.i3 and Method.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.util.AssertError;

// File: Method.i3
// Last Modified On Fri Jun 24 09:31:38 PDT 1994 By kalsow
//      Modified On Wed Mar 20 00:26:12 1991 By muller

// File: Method.m3
// Last modified on Wed Mar  1 08:44:03 PST 1995 by kalsow
//      modified on Fri Mar 22 08:34:06 1991 by muller

public final class Method {
    private Method() {}

    public static final class Info {
        public int name;
        public long offset;
        public Type.T parent;
        public Type.T signature;
        public Expr.T dfault;
        public boolean override;

        public Info() {}

        public Info copy() {
            Info r = new Info();
            r.name = name;
            r.offset = offset;
            r.parent = parent;
            r.signature = signature;
            r.dfault = dfault;
            r.override = override;
            return r;
        }
    }

    public static final class T extends Value.T {
        public long offset;
        public boolean override;
        public Type.T parent;
        public Type.T signature;
        public Expr.T dfaultE;
        public Value.T dfault;

        @Override
        public void typeCheck(M3.CheckState cs) {
            check(this, cs);
        }

        @Override
        public void set_globals() {
            setGlobals(this);
        }

        @Override
        public void lang_init() {
            compile(this);
        }

        @Override
        public Type.T typeOf() {
            return Method.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return Method.typeOf(this);
        }

        @Override
        public long add_fp_tag(M3.FPInfo x) {
            return addFPTag(this, x);
        }

        @Override
        public Type.T fp_type() {
            return fpType(this);
        }

        // Port: the overrides load := ValueRep.NoLoader, declare :=
        // ValueRep.Never, const_init := ValueRep.NoInit, need_init :=
        // ValueRep.Never, user_init := ValueRep.NoInit, toExpr :=
        // ValueRep.NoExpr, toType := ValueRep.NoType and base :=
        // ValueRep.Self restate the inherited methods.
    }

    public static Value.T new_(Method.Info info) {
        T t = new T();
        ValueRep.init(t, info.name, Value.Class.Method);
        t.readonly   = true;
        t.offset     = info.offset;
        t.override   = info.override;
        t.parent     = info.parent;
        t.signature  = info.signature;
        t.dfaultE    = info.dfault;
        t.dfault     = null;
        Scope.insert(t);
        return t;
    }

    public static boolean split(Value.T method, Method.Info info) {
        if (method == null) {
            return false;

        } else if (method instanceof T t) {
            info.name      = t.name;
            info.offset    = t.offset;
            info.parent    = t.parent;
            info.signature = t.signature;
            info.dfault    = t.dfaultE;
            info.override  = t.override;
            return true;

        } else {
            return false;
        }
    }

    public static void splitX(Value.T method, Method.Info info) {
        boolean b = split(method, info);
        AssertError.check(b, "Method");
    }

    public static void noteOverride(Value.T newV, Value.T oldV) {
        T new_ = (T) newV;
        T old = (T) oldV;
        AssertError.check(new_.override, "Method");
        AssertError.check(old.signature != null, "Method");
        new_.signature = old.signature;
        new_.offset    = old.offset;
    }

    private static void resolveDefault(T t) {
        Type.T default_type;
        if ((t.dfault != null)) {
            return;
        }
        if ((t.dfaultE == null)) {
            return;
        }
        // Port: t.dfault is a VAR parameter of UserProc.IsProcedureLiteral.
        Value.T[] dfault = {t.dfault};
        boolean isLiteral = UserProc.isProcedureLiteral(t.dfaultE, dfault);
        t.dfault = dfault[0];
        if (isLiteral) {
            return;
        }
        default_type = Expr.typeOf(t.dfaultE);
        if (Type.isEqual(default_type, Null.T, null)) {
            return; // ok
        } else if (!ProcType.is(default_type)) {
            Error.ID(t.name, "Method default must be a procedure (2.2.9).");
        } else {
            Error.ID(t.name, "Method default must be a procedure constant (2.2.9).");
        }
    }

    /**
     * Returns "TRUE" if the two lists of values represented by "va" and "vb"
     * have the same length and for each pair of values "a" and "b",
     * "IsEqual(a, b, x, types)" returns "TRUE".  Otherwise, returns "FALSE".
     */
    public static boolean isEqualList(Value.T a, Value.T b, Type.Assumption x, boolean types) {
        while ((a != null) && (b != null)) {
            if (!isEqual(a, b, x, types)) {
                return false;
            }
            a = a.next;
            b = b.next;
        }
        return (a == null) && (b == null);
    }

    /**
     * If "types" is "FALSE", only the surface syntax (name & field index) are
     * checked.  Otherwise, the field types and default values are checked too.
     */
    public static boolean isEqual(Value.T va, Value.T vb, Type.Assumption x, boolean types) {
        T a = (T) va;
        T b = (T) vb;
        if ((a == null) || (b == null) || (a.name != b.name) || (a.override != b.override)) {
            return false;
        }
        if (!types) {
            return true;
        }

        // now, we'll do the harder type-based checks...
        resolveDefault(a);
        resolveDefault(b);
        return Type.isEqual(a.signature, b.signature, x)
            && (Value.base(a.dfault) == Value.base(b.dfault)) /*CHEAT, BUG!*/;
    }

    private static void check(T t, M3.CheckState cs) {
        Value.T proc;
        Type.T procType;
        if ((t.signature != null)) {
            t.signature = Type.check(t.signature);
            if (Module.isInterface()) {
                Formal.nameDefaultConstructors(t.signature, t.name, cs);
            }
        }

        if ((t.dfaultE != null)) {
            Expr.typeCheck(t.dfaultE, cs);
            resolveDefault(t);
        }

        proc = t.dfault;
        if ((proc != null)) {
            Value.typeCheck(proc, cs);
            procType = Value.typeOf(proc);
            if ((procType == Null.T)) {
                t.dfault = null;
            } else if ((Value.classOf(proc) != Value.Class.Procedure)) {
                Error.ID(t.name, "Method default must be a procedure (2.2.9).");
            } else if (Procedure.isNested((Procedure.T) proc)) {
                Error.ID(t.name, "Method default must not be a nested procedure (2.2.9).");
            } else if (!ProcType.isCompatible(procType, t.parent, t.signature)) {
                Error.ID(t.name, "Method default must be compatible with method signature  (2.2.9).");
            }
        }
    }

    private static Type.T typeOf(T t) {
        return t.signature;
    }

    private static void compile(T t) {
        Type.compile(t.signature);
    }

    private static void setGlobals(T t) {
        // Type.SetGlobals (t.signature);
        // IF (t.dfaultE # NIL) THEN Type.SetGlobals (Expr.TypeOf (t.dfaultE)) END;
    }

    /** Port: Tags and Cnt are indexed by ORD (t.override). */
    private static final String[] Tags = { "METHOD ", "OVERRIDE " };
    private static final long[] Cnt = { 1, 0 };

    private static long addFPTag(T t, M3.FPInfo x) {
        ValueRep.fpStart(t, x, Tags[t.override ? 1 : 0], 0, /* global := */ false);
        if ((t.dfault != null)) {
            M3Buf.putText(x.buf, " := ");
            Expr.genFPLiteral(t.dfaultE, x.buf);
        }
        return Cnt[t.override ? 1 : 0];
    }

    private static Type.T fpType(T t) {
        if ((t.override)) {
            return null;
        } else {
            return t.signature;
        }
    }
}
