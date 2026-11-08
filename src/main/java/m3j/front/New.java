// Java port of CM3's m3-sys/m3front/src/builtinOps/New.i3 and New.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: New.m3
// Last Modified On Tue Jun 20 08:30:57 PDT 1995 By kalsow
//      Modified On Thu Jun 15 12:45:06 PDT 1995 By ericv
//      Modified On Fri Jan 25 08:10:52 1991 By muller

public final class New {
    private New() {}

    private static CallExpr.MethodList Z;

    private static Type.T typeOf(CallExpr.T ce) {
        Type.T[] t = new Type.T[1];
        if (!TypeExpr.split(ce.args[0], t)) {
            return Null.T;
        } else if (RefType.is(t[0])) {
            // ok
        } else if (ObjectType.is(t[0])) {
            // sleazy bug!!  ignore method overrides
        } else if (OpaqueType.is(t[0])) {
            // sleazy bug!!  ignore method overrides
        } else {
            return Null.T;
        }
        return Type.stripPacked(t[0]);
    }

    private static void check(CallExpr.T ce, M3.CheckState cs) {
        Type.T[] t = new Type.T[1];
        Type.T[] r = new Type.T[1];
        if (KeywordExpr.is(ce.args[0])) {
            Error.msg("NEW: keyword bindings not allowed for type");
        }
        if (!TypeExpr.split(ce.args[0], t)) {
            Error.msg("NEW must be applied to a reference type");
            t[0] = Null.T;
        } else if ((RefType.split(t[0], r))) {
            checkRef(r[0], ce, cs);
        } else if ((ObjectType.is(t[0]))) {
            r[0] = checkObject(t[0], ce, cs);
            if ((r[0] != t[0])) {
                ce.args[0] = TypeExpr.new_(r[0]);
                Expr.typeCheck(ce.args[0], cs);
                t[0] = r[0];
            }
        } else if ((OpaqueType.is(t[0]))) {
            r[0] = checkOpaque(t[0], ce, cs);
            if ((r[0] != t[0])) {
                ce.args[0] = TypeExpr.new_(r[0]);
                Expr.typeCheck(ce.args[0], cs);
                t[0] = r[0];
            }
        } else if ((t[0] != ErrType.T)) {
            Error.msg("NEW must be applied to a reference type");
        }
        ce.type = t[0];
    }

    private static void checkRef(Type.T r, CallExpr.T ce, M3.CheckState cs) {
        Type.T base;
        Value.T[] fields = new Value.T[1];
        Type.Info info = new Type.Info();
        if ((r == null)) {
            Error.msg("cannot NEW a variable of type REFANY, ADDRESS, or NULL");
            return;
        }
        r = Type.check(r);
        base = Type.base(r);
        base = Type.checkInfo(base, info);
        if ((info.isEmpty)) {
            Error.msg("cannot allocate variables of empty types");
        } else if ((info.class_ == Type.Class.OpenArray)) {
            checkOpenArray(base, ce);
        } else if ((info.class_ == Type.Class.Record)) {
            checkRecord(base, ce, cs);
        } else if (RecordType.split(base, fields)) {
            checkRecord(base, ce, cs);
        } else if ((ce.args.length > 1)) {
            Error.msg("too many arguments to NEW");
        }
    }

    private static void checkOpenArray(Type.T r, CallExpr.T ce) {
        Type.T x;
        Type.T[] elt = new Type.T[1];
        for (int i = 1; i <= ce.args.length - 1; i++) {
            x = Type.base(Expr.typeOf(ce.args[i]));
            if (KeywordExpr.is(ce.args[i])) {
                Error.msg("NEW: not a procedure; keyword bindings not allowed for array dimensions");
            }
            if (!Type.isEqual(x, Int.T, null)) {
                Error.int_(i, "argument must be an integer");
            } else if ((!OpenArrayType.split(r, elt))) {
                Error.int_(i, "too many dimensions specified");
            } else { // ok
                r = elt[0];
            }
        }
        if (OpenArrayType.is(r)) {
            Error.msg("not enough dimensions specified");
        }
    }

    private static void checkRecord(Type.T t, CallExpr.T ce, M3.CheckState cs) {
        Type.T x;
        int[] key = new int[1];
        Expr.T[] value = new Expr.T[1];
        Value.T[] field = new Value.T[1];
        for (int i = 1; i <= ce.args.length - 1; i++) {
            x = Expr.typeOf(ce.args[i]);
            if (!KeywordExpr.split(ce.args[i], key, value)) {
                Error.msg("extra arguments to NEW must include keywords (2.6.9)");
            } else if (!RecordType.lookUp(t, key[0], field)) {
                Error.ID(key[0], "unknown record field");
            } else if (!Field.is(field[0])) {
                Error.ID(key[0], "undefined field?");
            } else if (!Type.isAssignable(Value.typeOf(field[0]), x)) {
                Error.ID(key[0], "value is not assignable to field");
            } else {
                ArrayExpr.noteUseTargetVar(value[0]);
                AssignStmt.check(Value.typeOf(field[0]), value[0], cs);
            }
        }
    }

    private static Type.T checkObject(Type.T t, CallExpr.T ce, M3.CheckState cs) {
        Type.T x;
        int[] key = new int[1];
        Expr.T[] value = new Expr.T[1];
        Value.T[] v = new Value.T[1];
        Type.T[] visible = new Type.T[1];
        Type.T newType = null;
        Scope.T fields;
        Scope.T overrides = null;
        long n_overrides = 0;
        Scope.T zz;
        Method.Info method = new Method.Info();
        Field.Info field = new Field.Info();
        Type.Info info = new Type.Info();
        t = Type.checkInfo(t, info);

        // first pass, remove the method overrides & build a new object type
        for (int i = 1; i <= ce.args.length - 1; i++) {
            x = Expr.typeOf(ce.args[i]);
            if (KeywordExpr.split(ce.args[i], key, value)
                && ObjectType.lookUp(t, key[0], v, visible)
                && Method.split(v[0], method)) {
                if ((newType == null)) {
                    fields = Scope.pushNew(false, M3ID.NoID); Scope.popNew();
                    overrides = Scope.pushNew(false, M3ID.NoID); Scope.popNew();
                    newType = ObjectType.new_(t, info.isTraced, null, fields, overrides);
                }
                zz = Scope.push(overrides);
                  method.name      = key[0];
                  method.offset    = n_overrides * M3.target.Address.size;
                  method.parent    = newType;
                  method.signature = null;
                  method.dfault    = value[0];
                  method.override  = true;
                  Method.noteOverride(Method.new_(method), v[0]);
                  n_overrides++;
                Scope.pop(zz);
            }
        }

        if ((newType != null)) {
            newType = Type.check(newType);
            t = newType;
        }

        // second pass, do the checking
        for (int i = 1; i <= ce.args.length - 1; i++) {
            x = Expr.typeOf(ce.args[i]);
            if (!KeywordExpr.split(ce.args[i], key, value)) {
                Error.msg("extra arguments must include keywords");
            } else if (!ObjectType.lookUp(t, key[0], v, visible)) {
                Error.ID(key[0], "unknown object field or method");
            } else if (Method.split(v[0], method)) {
                if (!ProcType.isCompatible(x, t, method.signature)) {
                    Error.ID(key[0], "procedure is not compatible with method");
                }
                ce.args[i] = null;
            } else if (Field.is(v[0])) {
                Field.split(v[0], field);
                if (!Type.isAssignable(field.type, x)) {
                    Error.ID(key[0], "value is not assignable to field");
                } else {
                    ArrayExpr.noteUseTargetVar(value[0]);
                    AssignStmt.check(field.type, value[0], cs);
                }
            } else {
                Error.ID(key[0], "undefined?");
            }
        }

        return t;
    }

    /**
     * we already know that t is not an object, so we only need to
     * check for a full revelation that says it's a REF
     */
    private static Type.T checkOpaque(Type.T t, CallExpr.T ce, M3.CheckState cs) {
        Type.T x = Revelation.lookUp(t);
        Type.T[] r = new Type.T[1];
        if ((x == null)) {
            Error.msg("cannot apply NEW to non-object, opaque types");
        } else if (RefType.split(x, r)) {
            // full revelation => t is a REF
            checkRef(r[0], ce, cs);
        } else {
            Error.msg("cannot apply NEW to this type");
        }
        return t;
    }

    private static void prep(CallExpr.T ce) {
        gen(ce);
    }

    private static void compile(CallExpr.T ce) {
        // all the work was done by Prep
        CG.push(ce.tmp);
        CG.boost_addr_alignment(ce.align);
        CG.free(ce.tmp);
        ce.tmp = null;
    }

    private static void gen(CallExpr.T ce) {
        Type.T[] t = new Type.T[1];
        Type.T[] r = new Type.T[1];
        {
            boolean b = TypeExpr.split(ce.args[0], t);
            AssertError.check(b, "New");
        }
        Type.compile(t[0]);
        t[0] = Type.stripPacked(t[0]);
        if ((RefType.split(t[0], r))) {
            genRef(t[0], Type.stripPacked(r[0]), ce);
        } else if ((ObjectType.is(t[0]))) {
            genObject(t[0], ce);
        } else if ((OpaqueType.is(t[0]))) {
            genOpaque(t[0], ce);
        } else {
            Error.msg("NEW must name a reference type");
        }
    }

    private static void genRef(Type.T t, Type.T r, CallExpr.T ce) {
        // Port: ARRAY BOOLEAN OF RunTyme.Hook, indexed by (b ? 1 : 0).
        final RunTyme.Hook[] PHook = { RunTyme.Hook.NewUntracedRef,
                                       RunTyme.Hook.NewTracedRef };
        Type.T base = Type.base(r);
        Value.T[] fields = new Value.T[1];
        Procedure.T proc;
        Type.Info t_info = new Type.Info();
        Type.Info r_info = new Type.Info();
        t = Type.checkInfo(t, t_info);
        r = Type.check(r);
        r = Type.stripPacked(r);
        r = Type.checkInfo(r, r_info);

        if ((r_info.class_ == Type.Class.OpenArray)) {
            genOpenArray(t, t_info.isTraced, r_info, ce);

        } else if (RecordType.split(base, fields)) {
            genRecord(t, base, t_info.isTraced, r_info, ce);

        } else {
            proc = RunTyme.lookUpProc(PHook[t_info.isTraced ? 1 : 0]);
            Procedure.startCall(proc);
            Type.loadInfo(t, -1);
            CG.pop_param(CGType.Addr);
            ce.tmp = Procedure.emitValueCall(proc);
            ce.align = r_info.alignment;
        }
    }

    private static void genOpenArray(Type.T t, boolean traced,
                                     Type.Info r_info, CallExpr.T ce) {
        // Port: ARRAY BOOLEAN OF RunTyme.Hook, indexed by (b ? 1 : 0).
        final RunTyme.Hook[] PHook = { RunTyme.Hook.NewUntracedArray,
                                       RunTyme.Hook.NewTracedArray };
        long n = ce.args.length - 1; // number of open dimensions
        long sizesAlign = Math.max(M3.target.Address.align, M3.target.Integer.align);
        M3CG.Var sizes = CG.declare_temp(M3.target.Address.pack + (n + 1) * M3.target.Integer.pack,
        // TODO:                       ^RefType.InitTypecell and possibly other places
        //                             handle possible alignment padding between the
        //                             elements-address and the shape, in case Integer
        //                             has higher alignment than Address.
                                         sizesAlign, CGType.Struct, true);
        long offset;
        Procedure.T proc = RunTyme.lookUpProc(PHook[traced ? 1 : 0]);
        // initialize the pointer to the array sizes
        CG.load_addr_of(sizes, M3.rt.OA_size_1, M3.target.Integer.align);
        CG.store_addr(sizes, M3.rt.OA_elt_ptr);

        // initialize the count of array sizes
        CG.load_intt(n);
        CG.store_int(M3.target.Integer.cg_type, sizes, M3.rt.OA_size_0);

        // initialize each array size
        offset = M3.rt.OA_size_1;
        for (long i = 1; i <= n; i++) {
            Expr.prep(ce.args[(int) i]);
            Expr.compile(ce.args[(int) i]);
            CG.store_int(M3.target.Integer.cg_type, sizes, offset);
            offset += M3.target.Integer.pack;
        }

        // allocate the storage
        Procedure.startCall(proc);
        if (M3.target.DefaultCall.args_left_to_right) {
            Type.loadInfo(t, -1);
            CG.pop_param(CGType.Addr);
            CG.load_addr_of(sizes, 0, sizesAlign);
            CG.pop_param(CGType.Addr);
        } else {
            CG.load_addr_of(sizes, 0, sizesAlign);
            CG.pop_param(CGType.Addr);
            Type.loadInfo(t, -1);
            CG.pop_param(CGType.Addr);
        }
        ce.tmp = Procedure.emitValueCall(proc);
        ce.align = r_info.alignment;

        CG.free_temp(sizes);
    }

    private static void genRecord(Type.T t, Type.T r, boolean traced,
                                  Type.Info r_info, CallExpr.T ce) {
        // Port: ARRAY BOOLEAN OF RunTyme.Hook, indexed by (b ? 1 : 0).
        final RunTyme.Hook[] PHook = { RunTyme.Hook.NewUntracedRef,
                                       RunTyme.Hook.NewTracedRef };
        int[] key = new int[1];
        Expr.T[] value = new Expr.T[1];
        Field.Info field = new Field.Info();
        Value.T[] v = new Value.T[1];
        long align = r_info.alignment;
        Procedure.T proc = RunTyme.lookUpProc(PHook[traced ? 1 : 0]);
        boolean b;
        // allocate the record's storage
        Procedure.startCall(proc);
        Type.loadInfo(t, -1);
        CG.pop_param(CGType.Addr);
        ce.tmp = Procedure.emitValueCall(proc);
        ce.align = align;

        // do the user specified initialization
        for (int i = 1; i <= ce.args.length - 1; i++) {
            b = KeywordExpr.split(ce.args[i], key, value);
            AssertError.check(b, "New");
            // Sic: field.type is used before Field.Split sets field: it is
            // NIL in the first iteration and the previous field's type later.
            AssignStmt.prepForEmit(field.type, value[0], true);
            RecordType.lookUp(r, key[0], v);
            Field.split(v[0], field);
            CG.push(ce.tmp);
            CG.boost_addr_alignment(align);
            CG.add_offset(field.offset);
            // Port: lhs_align takes its default, Target.Byte.
            AssignStmt.doEmit(field.type, value[0], Target.Byte, true);
        }
    }

    private static void genObject(Type.T t, CallExpr.T ce) {
        // Port: ARRAY BOOLEAN OF RunTyme.Hook, indexed by (b ? 1 : 0).
        final RunTyme.Hook[] PHook = { RunTyme.Hook.NewUntracedObj,
                                       RunTyme.Hook.NewTracedObj };
        int[] key = new int[1];
        Expr.T[] value = new Expr.T[1];
        Field.Info field = new Field.Info();
        Value.T[] v = new Value.T[1];
        Type.T[] visible = new Type.T[1];
        long[] obj_offset = new long[1];
        long[] obj_align = new long[1];
        Procedure.T proc;
        Type.Info info = new Type.Info();
        boolean b;
        t = Type.checkInfo(t, info);
        proc = RunTyme.lookUpProc(PHook[info.isTraced ? 1 : 0]);

        // allocate the object's storage
        Procedure.startCall(proc);
        Type.loadInfo(t, -1);
        CG.pop_param(CGType.Addr);
        ce.tmp = Procedure.emitValueCall(proc);
        ce.align = info.alignment;

        // do the user specified initialization
        for (int i = 1; i <= ce.args.length - 1; i++) {
            if ((ce.args[i] != null)) {
                b = KeywordExpr.split(ce.args[i], key, value);
                AssertError.check(b, "New");
                b = ObjectType.lookUp(t, key[0], v, visible);
                AssertError.check(b, "New");
                Field.split(v[0], field);
                AssignStmt.prepForEmit(field.type, value[0], true);
                CG.push(ce.tmp);
                ObjectType.getFieldsOffsetAndAlign(visible[0], obj_offset, obj_align);
                if ((obj_offset[0] >= 0)) {
                    field.offset += obj_offset[0];
                } else {
                    Type.loadInfo(visible[0], M3.rt.OTC_dataOffset);
                    CG.index_bytes(Target.Byte);
                }
                CG.add_offset(field.offset);
                CG.boost_addr_alignment(obj_align[0]);
                // Port: lhs_align takes its default, Target.Byte.
                AssignStmt.doEmit(field.type, value[0], Target.Byte, true);
            }
        }
    }

    private static void genOpaque(Type.T t, CallExpr.T ce) {
        Type.T x = Revelation.lookUp(t);
        Type.T[] r = new Type.T[1];
        if ((x == null)) {
            AssertError.check(false, "New");
        } else if (RefType.split(x, r)) {
            // full revelation => t is a REF
            genRef(x, Type.stripPacked(r[0]), ce);
        } else {
            AssertError.check(false, "New");
        }
    }

    public static void initialize() {
        Z = CallExpr.newMethodList(1, Long.MAX_VALUE, true, true, true, null,
                                   New::typeOf,
                                   New::typeOf,
                                   CallExpr::notAddressable,
                                   New::check,
                                   New::prep,
                                   New::compile,
                                   CallExpr::noLValue,
                                   CallExpr::noLValue,
                                   CallExpr::notBoolean,
                                   CallExpr::notBoolean,
                                   CallExpr::noValue,
                                   CallExpr::noBounds,
                                   CallExpr::isNever, // writable
                                   CallExpr::isNever, // designator
                                   CallExpr::notWritable); // noteWriter
        Procedure.definePredefined("NEW", Z, true);
    }
}
