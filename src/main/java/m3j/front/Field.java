// Java port of CM3's m3-sys/m3front/src/values/Field.i3 and Field.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: Field.i3
// Last Modified On Fri Jun 24 09:30:30 PDT 1994 By kalsow

// File: Field.m3
// Last modified on Wed Mar  1 08:43:31 PST 1995 by kalsow
//      modified on Fri Apr 20 06:47:07 1990 by muller

public final class Field {
    private Field() {}

    public static class T extends Value.T {
        public long index;
        public long offset;
        public Type.T tipe;
        public Expr.T dfault;

        @Override
        public void typeCheck(M3.CheckState cs) {
            Field.typeCheck(this, cs);
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
            return Field.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return Field.repTypeOf(this);
        }

        @Override
        public long add_fp_tag(M3.FPInfo x) {
            return addFPTag(this, x);
        }

        @Override
        public Type.T fp_type() {
            return Field.typeOf(this);
        }

        // Port: the overrides load := ValueRep.NoLoader, declare :=
        // ValueRep.Never, const_init := ValueRep.NoInit, need_init :=
        // ValueRep.Never, user_init := ValueRep.NoInit, toExpr :=
        // ValueRep.NoExpr, toType := ValueRep.NoType and base :=
        // ValueRep.Self restate the inherited methods.
    }

    public static final class Info {
        public int name;
        /** 0..nFields-1 */
        public long index;
        /** bit offset from beginning of record */
        public long offset;
        public Type.T type;
        public Expr.T dfault;

        public Info() {}

        public Info copy() {
            Info r = new Info();
            r.name = name;
            r.index = index;
            r.offset = offset;
            r.type = type;
            r.dfault = dfault;
            return r;
        }
    }

    // EXPORTED:
    public static Value.T new_(Field.Info info) {
        T t = new T();
        ValueRep.init(t, info.name, Value.Class.Field);
        t.index  = info.index;
        t.offset = info.offset;
        t.tipe   = info.type;
        t.dfault = info.dfault;
        return t;
    }

    /** Returns TRUE iff 'v' is a field */
    // EXPORTED:
    public static boolean is(Value.T v) {
        if (v == null) {
            return false;
        } else if (v instanceof T) {
            return true;
        } else {
            return false;
        }
    }

    // EXPORTED:
    public static void split(Value.T field, Field.Info info) {
        T t = (T) field;
        info.name   = t.name;
        info.index  = t.index;
        info.offset = t.offset;
        info.type   = t.tipe;
        info.dfault = t.dfault;
    }

    // EXPORTED:
    public static void setOffset(Value.T field, long newOffset) {
        T t = (T) field;
        t.offset = newOffset;
    }

    /** emit the C struct member or bit-field for 'field' */
    // EXPORTED:
    public static void emitDeclaration(Value.T field) {
        T t = (T) field;
        Type.Info info = new Type.Info();
        Type.checkInfo(t.tipe, info);
        Type.compile(t.tipe);
        // Port: a type UID is a 32-bit CG.TypeUID.
        CG.declare_field(t.name, t.offset, info.size, (int) Type.globalUID(t.tipe));
    }

    /**
     * Returns "TRUE" if the two lists of values represented by "va" and "vb"
     * have the same length and for each pair of values "a" and "b",
     * "IsEqual(a, b, x, types)" returns "TRUE".  Otherwise, returns "FALSE".
     */
    // EXPORTED:
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
    // EXPORTED:
    public static boolean isEqual(Value.T va, Value.T vb, Type.Assumption x, boolean types) {
        T a = (T) va;
        T b = (T) vb;
        if ((a == null) || (b == null) || (a.name != b.name) || (a.index != b.index)) {
            return false;
        }
        if (!types) {
            return true;
        }

        // now, we'll do the harder type-based checks...
        return Type.isEqual(typeOf(a), typeOf(b), x)
            /* AND Expr.IsEqual (Expr.ConstValue (a.dfault),
                                 Expr.ConstValue (b.dfault), x)*/;
    }

    // Externally dispatched-to:
    private static Type.T typeOf(T t) {
        if ((t.tipe == null)) {
            t.tipe = Expr.typeOf(t.dfault);
        }
        return t.tipe;
    }

    // Externally dispatched-to:
    private static Type.T repTypeOf(T t) {
        if (t.tipe != null) {
            return t.tipe;
        }
        return Expr.repTypeOf(t.dfault);
    }

    // Externally dispatched-to:
    private static void typeCheck(T t, M3.CheckState cs) {
        Type.Info info = new Type.Info();
        t.tipe = Type.checkInfo(typeOf(t), info);
        if ((info.isEmpty)) {
            Error.ID(t.name, "A field's type may not be empty (2.2.4).");
        }
        if ((info.class_ == Type.Class.OpenArray)) {
            Error.ID(t.name, "A field's type may not be open array (2.2.3).");
        }
        t.checked = true;

        if ((t.dfault != null)) {
            // check for assignability!!
            AssignStmt.check(t.tipe, t.dfault, cs);
            Expr.typeCheck(t.dfault, cs);
            if ((Expr.constValue(t.dfault) == null)) {
                Error.ID(t.name, "Default value of a field must be constant (2.2.4).");
            }
            // NOTE: we don't save the constant-folded version of the default,
            // otherwise we'd lose references to large named constants.
        }
    }

    /**
     * Port: o is a VAR (IN OUT) parameter, a one-element array.
     */
    // EXPORTED:
    public static void nameAnonConstr(Value.T[] o, M3.CheckState cs) {
        T field = (T) o[0];
        if (field.dfault != null && Module.isInterface()) {
            // Port: field.dfault is a VAR parameter of Expr.NameAnonConstr.
            Expr.T[] dfault = {field.dfault};
            Expr.nameAnonConstr(dfault, Module.name(null /*current*/), field.name, cs);
            field.dfault = dfault[0];
        }
    }

    /** compile, i.e. declare, the underlying type */
    // EXPORTED, Externally dispatched-to:
    public static void compile(Field.T t) {
        Type.compile(t.tipe);
        // Expr.Compile (t.dfault);
    }

    // Externally dispatched-to:
    private static void setGlobals(T t) {
        // Type.SetGlobals (t.tipe);
        // IF (t.dfault # NIL) THEN Type.SetGlobals (Expr.TypeOf (t.dfault)) END;
    }

    // Externally dispatched-to:
    private static long addFPTag(T t, M3.FPInfo x) {
        ValueRep.fpStart(t, x, "FIELD ", 0, /* global := */ false);
        // No, a default value does not contribute to the type.
        //   IF (t.dfault # NIL) THEN
        //     M3Buf.PutText (x.buf, " := ");
        //     Expr.GenFPLiteral (t.dfault, x.buf);
        //   END;
        return 1;
    }
}
