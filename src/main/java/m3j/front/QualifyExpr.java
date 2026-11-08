// Java port of CM3's m3-sys/m3front/src/exprs/QualifyExpr.i3 and QualifyExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3CG;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: QualifyExpr.i3
// Last Modified On Fri Jun 24 08:57:39 PDT 1994 By kalsow
//      Modified On Sat Aug 18 01:13:38 1990 By muller

// File: QualifyExpr.m3
// Last modified on Fri Feb 24 16:45:51 PST 1995 by kalsow
//      modified on Tue Feb 19 01:32:23 1991 by muller

public final class QualifyExpr {
    private QualifyExpr() {}

    public enum Class {
        /** {@literal <importedInterface>.<anyId>} */
        importDecl,
        /** {@literal <enumType>.<Id>} */
        enumLit,
        /** {@literal <objectType>.<methodId>} */
        objTypeMethod,
        /** {@literal <recordExpr>.<fieldId>} */
        recField,
        /** {@literal <objectExpr>.<fieldId>} */
        objField,
        /** {@literal <objectExpr>.<methodId>} */
        objMethod,
        unknown
    }

    // Port: VC = Value.Class is written Value.Class.

    public static final class P extends Expr.T {
        public Expr.T lhsExpr;
        public Value.T rhsValue;
        /** Visible supertype of the Q-expr. */
        public Type.T holder;
        public Type.T objType;
        public CG.Val temp;
        public int name;
        public Class class_;
        /** ^For lhsExpr with object type, alignment of the referent. */
        public long addr_align = Target.Word8.align;
        public boolean inFold;
        public boolean inIsZeroes;
        public boolean inGetBounds;
        public boolean inTypeOf;

        @Override
        public Type.T typeOf() {
            return QualifyExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return QualifyExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            QualifyExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            QualifyExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            QualifyExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepLV(boolean traced) {
            QualifyExpr.prepLV(this, traced);
        }

        @Override
        public void compileLV(boolean traced, boolean StaticOnly) {
            QualifyExpr.compileLV(this, traced, StaticOnly);
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
            return QualifyExpr.isWritable(this, lhs);
        }

        @Override
        public boolean isDesignator(boolean lhs) {
            return QualifyExpr.isDesignator(this, lhs);
        }

        @Override
        public boolean isZeroes(boolean lhs) {
            return QualifyExpr.isZeroes(this, lhs);
        }

        @Override
        public void note_write() {
            noteWrites(this);
        }

        @Override
        public long exprAlign() {
            return qualifyExprAlign(this);
        }

        // Port: the overrides genFPLiteral := ExprRep.NoFPLiteral,
        // prepLiteral := ExprRep.NoPrepLiteral and genLiteral :=
        // ExprRep.NoLiteral restate the inherited methods.
    }

    public static Expr.T new_(Expr.T a, int id) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.lhsExpr = a;
        p.name = id;
        p.rhsValue = null;
        p.class_ = Class.unknown;
        p.holder = null;
        p.objType = null;
        p.inFold = false;
        p.inIsZeroes = false;
        p.inGetBounds = false;
        p.inTypeOf = false;
        return p;
    }

    /** Port: VAR rhsValue is {@code rhsValue[0]}. */
    public static boolean split(Expr.T e, Value.T[] rhsValue) {
        if (e instanceof P p) {
            resolve(p);
            rhsValue[0] = p.rhsValue;
            return true;
        } else {
            return false;
        }
    }

    /** Port: VAR module is {@code module[0]}, VAR item is {@code item[0]}. */
    public static boolean splitQID(Expr.T e, int[] module, int[] item) {
        if (e instanceof P p) {
            if (NamedExpr.splitName(p.lhsExpr, module)) {
                item[0] = p.name;
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean passObject(Expr.T e) {
        if (e == null) {
            // nothing
        } else if (e instanceof P p) {
            if ((p.class_ == Class.objMethod)) {
                CG.push(p.temp);
                CG.pop_param(CGType.Addr);
                CG.free(p.temp);
                p.temp = null;
                return true;
            }
        } else {
            // nothing
        }
        return false;
    }

    public static Type.T methodType(Expr.T e) {
        if (e == null) {
            // nothing
        } else if (e instanceof P p) {
            resolve(p);
            if ((p.class_ == Class.objMethod)) {
                return Value.typeOf(p.rhsValue);
            }
        } else {
            // nothing
        }
        return null;
    }

    private static void bounder(P p, int[] min, int[] max) {
        resolve(p);
        if ((p.inGetBounds)) {
            Value.illegalRecursion(p.rhsValue);
            Type.getBounds(p.type, min, max);
            return;
        }
        p.inGetBounds = true;
        switch (Value.classOf(p.rhsValue)) {
            case Expr -> Expr.getBounds(Value.toExpr(p.rhsValue), min, max);
            case Var -> Variable.getBounds((Variable.T) p.rhsValue, min, max);
            default -> Type.getBounds(p.type, min, max);
        }
        p.inGetBounds = false;
    }

    private static void makeDummy(P p) {
        p.class_ = Class.importDecl;
        p.rhsValue = VarExpr.obj(VarExpr.new_(ErrType.T, p.name));
    }

    private static void resolve(P p) {
        Type.T[] t = new Type.T[1];
        Type.T baseType;
        Scope.T s;
        Value.T[] rhsValue = new Value.T[1];
        int[] name = new int[1];
        Type.Info baseTypeInfo = new Type.Info();
        // Port: VAR p.rhsValue and p.holder are passed as the elements 0
        // of v and h and copied back after each call.
        Value.T[] v = new Value.T[1];
        Type.T[] h = new Type.T[1];
        boolean ok;
        if ((p.class_ != Class.unknown)) {
            return;
        }

        t[0] = Expr.typeOf(p.lhsExpr);

        if (RefType.is(t[0])) {
            // auto-magic dereference
            p.lhsExpr = DerefExpr.new_(p.lhsExpr);
            p.lhsExpr.origin = p.origin;
            t[0] = Expr.typeOf(p.lhsExpr);
        }

        p.holder = t[0];
        p.rhsValue = null;
        baseType = Type.base(t[0]);

        if ((t[0] == ErrType.T)) {
            // the lhs already contains an error => silently make it look like
            // everything is ok.
            makeDummy(p);

        } else if ((t[0] == null)) {
            // p.lhsExpr *has* no type, so it *is* either a module or type
            if (TypeExpr.split(p.lhsExpr, t)) {
                v[0] = p.rhsValue;
                ok = EnumType.lookUp(t[0], p.name, v);
                p.rhsValue = v[0];
                if (ok) {
                    p.class_ = Class.enumLit;
                } else {
                    v[0] = p.rhsValue;
                    h[0] = p.holder;
                    ok = ObjectType.lookUp(t[0], p.name, v, h);
                    p.rhsValue = v[0];
                    p.holder = h[0];
                    if (ok) {
                        p.objType = t[0]; // Used?
                        p.class_ = Class.objTypeMethod;
                    }
                }
            } else if (NamedExpr.split(p.lhsExpr, name, rhsValue)) {
                if ((Value.classOf(rhsValue[0]) == Value.Class.Module)) {
                    p.class_ = Class.importDecl;
                    s = Module.exportScope((Module.T) Value.base(rhsValue[0]));
                    p.rhsValue = Scope.lookUp(s, p.name, true);
                }
            }

        } else {
            v[0] = p.rhsValue;
            ok = RecordType.lookUp(baseType, p.name, v);
            p.rhsValue = v[0];
            if (ok) {
                p.class_ = Class.recField;

            } else {
                v[0] = p.rhsValue;
                h[0] = p.holder;
                ok = ObjectType.lookUp(baseType, p.name, v, h);
                p.rhsValue = v[0];
                p.holder = h[0];
                if (ok) {
                    Type.checkInfo(baseType, baseTypeInfo);
                    p.addr_align = baseTypeInfo.addr_align;
                    if ((Value.classOf(p.rhsValue) == Value.Class.Field)) {
                        p.class_ = Class.objField;
                    } else {
                        p.class_ = Class.objMethod;
                    }
                }
            }
        }
    }

    private static void resolveTypes(P p) {
        Type.T[] objType = new Type.T[1];
        resolve(p);
        if ((p.inTypeOf)) {
            Value.illegalRecursion(p.rhsValue);
            p.type = ErrType.T;
            p.repType = p.type;
            return;
        } else {
            p.inTypeOf = true;
            p.type = Value.typeOf(p.rhsValue);
            if (p.type == ErrType.T) {
                p.repType = ErrType.T;
            } else if (p.class_ == Class.objMethod) {
                p.type = null;
                p.repType = null;
            } else if (p.class_ == Class.objTypeMethod) {
                boolean b = TypeExpr.split(p.lhsExpr, objType);
                AssertError.check(b, "QualifyExpr");
                p.type = ProcType.methodSigAsProcSig(p.type, objType[0]);
                p.repType = p.type;
            } else {
                p.repType = Value.repTypeOf(p.rhsValue);
            }
        }
        p.inTypeOf = false;
    }

    private static Type.T typeOf(P p) {
        resolveTypes(p);
        return p.type;
    }

    private static Type.T repTypeOf(P p) {
        resolveTypes(p);
        return p.repType;
    }

    private static void check(P p, M3.CheckState cs) {
        long[] nErrs0 = new long[1], nErrs1 = new long[1], nWarns = new long[1];
        Type.Info info = new Type.Info();
        Error.count(nErrs0, nWarns);
        Expr.typeCheck(p.lhsExpr, cs);
        resolve(p);
        Expr.typeCheck(p.lhsExpr, cs);
        Error.count(nErrs1, nWarns);

        if ((p.rhsValue == null)) {
            if ((nErrs0[0] == nErrs1[0])) {
                Error.ID(p.name, "unknown qualification '.'");
            }
            makeDummy(p);
        } else if ((p.class_ == Class.recField)) {
            Type.checkInfo(p.holder, info);
            DerefExpr.setOffset(p.lhsExpr, info.size);
        } else if ((p.class_ == Class.objTypeMethod)
                   && (Value.classOf(p.rhsValue) != Value.Class.Method)) {
            Error.ID(p.name, "doesn't name a method");
        }

        Value.typeCheck(p.rhsValue, cs);
        typeOf(p);
        if ((p.type != null)) {
            p.type = Type.check(p.type);
        }
    }

    private static long qualifyExprAlign(P p) {
        Field.Info fieldInfo = new Field.Info();
        Type.T rhsRepType, lhsRepType;
        Type.Info typeInfo = new Type.Info();
        long fieldTypeAlign, fieldsAlign, result;
        switch (p.class_) {
            case objMethod -> {
                return M3.target.Address.align;
            }
            case importDecl,
                 enumLit,
                 objTypeMethod -> {
                rhsRepType = Value.typeOf(p.rhsValue);
                Type.checkInfo(rhsRepType, typeInfo);
                return typeInfo.alignment;
            }
            default -> {
            }
        } // CASE

        // It's a field.  get its alignment from its type, respecting packing.
        rhsRepType = Type.strip(Value.typeOf(p.rhsValue)); // Remove named.
        rhsRepType = Type.checkInfo(rhsRepType, /*OUT*/ typeInfo);
        if (typeInfo.class_ == Type.Class.Packed) {
            fieldTypeAlign = 1;
        } else {
            fieldTypeAlign = typeInfo.alignment;
        }
        Field.split(p.rhsValue, fieldInfo);

        switch (p.class_) {
            case recField -> {
                result = CG.GCD(Expr.alignment(p.lhsExpr), fieldInfo.offset);
                AssertError.check(Math.floorMod(result, fieldTypeAlign) == 0, "QualifyExpr");
                return result;
            }
            case objField -> {
                fieldsAlign = M3.target.Address.align;
                // ^Will be alignment of the whole block of fields.
                if (fieldsAlign < Target.MaxAlign) {
                    // It's possible on a 32-bit target, that the heap referent's
                    // alignment is 64.  Does the object type indicate so?
                    lhsRepType = Expr.repTypeOf(p.lhsExpr);
                    while (OpaqueType.is(lhsRepType)) {
                        // This could have a revelation with MaxAlign, but we won't
                        // know that now.  So conservatively assume not.
                        lhsRepType = OpaqueType.super_(lhsRepType);
                    }
                    fieldsAlign =
                        Math.max(fieldsAlign, ObjectType.fieldAlignment(lhsRepType));
                }
                result = CG.GCD(Math.max(fieldsAlign, fieldTypeAlign), fieldInfo.offset);
                AssertError.check(Math.floorMod(result, fieldTypeAlign) == 0, "QualifyExpr");
                return result;
            }
            default -> throw new AssertError("QualifyExpr");
        } // CASE
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if (e instanceof P b) {
            resolve(a);
            resolve(b);
            return (a.rhsValue == b.rhsValue)
                && (a.class_ == b.class_)
                && Expr.isEqual(a.lhsExpr, b.lhsExpr, x);
        } else {
            return false;
        }
    }

    private static void needsAddress(P p) {
        Value.Class c;
        switch (p.class_) {
            case importDecl -> {
                c = Value.classOf(p.rhsValue);
                if ((c == Value.Class.Var)) {
                    Variable.needsAddress((Variable.T) p.rhsValue);
                } else if ((c == Value.Class.Expr)) {
                    Expr.needsAddress(Value.toExpr(p.rhsValue));
                }
            }
            case recField -> Expr.needsAddress(p.lhsExpr);
            case objField -> {
                // ok, all objects have addresses
            }
            case enumLit,
                 objTypeMethod,
                 objMethod,
                 unknown -> throw new AssertError("QualifyExpr");
        }
    }

    private static void prep(P p) {
        Field.Info field = new Field.Info();
        Type.Info info = new Type.Info();
        switch (p.class_) {
            case importDecl -> {
                if (Host.doIncGC && Value.classOf(p.rhsValue) == Value.Class.Var) {
                    Type.checkInfo(p.type, info);
                    if (info.isTraced) {
                        switch (info.class_) {
                            case Object, Opaque, Ref -> {
                                Variable.load((Variable.T) p.rhsValue);
                                RunTyme.emitCheckLoadTracedRef();
                                p.temp = CG.pop();
                            }
                            default -> {
                                // no check
                            }
                        }
                    }
                }
            }
            case enumLit -> {
                // skip
            }
            case objTypeMethod -> {
                // skip
            }
            case recField -> {
                if (Expr.isDesignator(p.lhsExpr)) {
                    Expr.prepLValue(p.lhsExpr, /* traced := */ false);
                } else {
                    Expr.checkUseFailure(p.lhsExpr);
                    Expr.prep(p.lhsExpr);
                }
                Field.split(p.rhsValue, field);
                Type.checkInfo(field.type, info);
                if (Host.doIncGC && info.isTraced) {
                    switch (info.class_) {
                        case Object, Opaque, Ref -> {
                            compile(p, /* StaticOnly := */ false);
                            RunTyme.emitCheckLoadTracedRef();
                            p.temp = CG.pop();
                        }
                        default -> {
                            // no check
                        }
                    }
                }
            }
            case objField -> {
                Expr.prep(p.lhsExpr);
                Field.split(p.rhsValue, field);
                Type.checkInfo(field.type, info);
                if (Host.doIncGC && info.isTraced) {
                    switch (info.class_) {
                        case Object, Opaque, Ref -> {
                            compile(p, /* StaticOnly := */ false);
                            RunTyme.emitCheckLoadTracedRef();
                            p.temp = CG.pop();
                        }
                        default -> {
                            // no check
                        }
                    }
                }
            }
            case objMethod -> {
                Expr.prep(p.lhsExpr);
                Expr.compile(p.lhsExpr);
                p.temp = CG.pop();
            }
            case unknown -> throw new AssertError("QualifyExpr");
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        long obj_offset;
        long[] obj_offset_a = new long[1], obj_align = new long[1];
        Field.Info fieldInfo = new Field.Info();
        Method.Info method = new Method.Info();
        if (StaticOnly) {
            return;
        }
        switch (p.class_) {
            case importDecl -> {
                if (p.temp != null) {
                    CG.push(p.temp);
                    CG.free(p.temp);
                    p.temp = null;
                    return;
                }
                // Do we need to Compile p.rhsValue, if it is a constant?
                Value.load(p.rhsValue);
            }
            case enumLit -> Value.load(p.rhsValue);
            case objTypeMethod -> {
                Type.compile(p.holder);
                Type.compile(p.objType);
                Method.splitX(p.rhsValue, method);
                Type.loadInfo(p.objType, M3.rt.OTC_defaultMethods, /* addr := */ true);
                obj_offset = ObjectType.methodOffset(p.holder);
                if ((obj_offset >= 0)) {
                    method.offset += obj_offset;
                } else {
                    Type.loadInfo(p.holder, M3.rt.OTC_methodOffset);
                    CG.index_bytes(Target.Byte);
                }
                CG.boost_addr_alignment(M3.target.Address.align);
                CG.load_indirect(CGType.Addr, method.offset, M3.target.Address.size);
                CG.boost_addr_alignment(M3.target.Address.align);
            }
            case recField -> {
                if (p.temp != null) {
                    CG.push(p.temp);
                    CG.free(p.temp);
                    p.temp = null;
                    return;
                }
                Field.split(p.rhsValue, fieldInfo);
                if (Expr.isDesignator(p.lhsExpr)) {
                    Expr.compileLValue(p.lhsExpr, /* traced := */ false);
                } else {
                    Expr.compile(p.lhsExpr);
                }
                CG.add_offset(fieldInfo.offset);
                Type.loadScalar(fieldInfo.type);
            }
            case objField -> {
                if (p.temp != null) {
                    CG.push(p.temp);
                    CG.free(p.temp);
                    p.temp = null;
                    return;
                }
                Field.split(p.rhsValue, fieldInfo);
                Expr.compile(p.lhsExpr);
                CG.boost_addr_alignment(p.addr_align);
                ObjectType.getFieldsOffsetAndAlign(p.holder, obj_offset_a, obj_align);
                obj_offset = obj_offset_a[0];
                if ((obj_offset >= 0)) {
                    fieldInfo.offset += obj_offset;
                } else {
                    if (Host.doNilChk) {
                        CG.check_nil(M3CG.RuntimeError.BadMemoryReference);
                    }
                    Type.loadInfo(p.holder, M3.rt.OTC_dataOffset);
                    CG.index_bytes(Target.Byte);
                }
                CG.add_offset(fieldInfo.offset);
                CG.boost_addr_alignment(obj_align[0]);
                Type.loadScalar(fieldInfo.type);
            }
            case objMethod -> {
                Method.splitX(p.rhsValue, method);
                CG.push(p.temp);
                CG.boost_addr_alignment(M3.target.Address.align);
                CG.load_indirect(CGType.Addr, 0, M3.target.Address.size);
                CG.boost_addr_alignment(M3.target.Address.align);
                obj_offset = ObjectType.methodOffset(p.holder);
                if ((obj_offset >= 0)) {
                    method.offset += obj_offset;
                } else {
                    Type.loadInfo(p.holder, M3.rt.OTC_methodOffset);
                    CG.index_bytes(Target.Byte);
                }
                CG.boost_addr_alignment(M3.target.Address.align);
                CG.load_indirect(CGType.Addr, method.offset, M3.target.Address.size);
                CG.boost_addr_alignment(M3.target.Address.align);
            }
            case unknown -> throw new AssertError("QualifyExpr");
        }
    }

    private static void prepLV(P p, boolean traced) {
        Type.Info info = new Type.Info();
        switch (p.class_) {
            case importDecl, enumLit -> {
                // skip
            }
            case objTypeMethod -> {
                // skip
            }
            case recField -> {
                if (Expr.isDesignator(p.lhsExpr)) {
                    Expr.prepLValue(p.lhsExpr, traced);
                } else {
                    Expr.checkUseFailure(p.lhsExpr);
                    Expr.prep(p.lhsExpr);
                }
            }
            case objField -> {
                Expr.prep(p.lhsExpr);
                if (traced && Host.doGenGC) {
                    Type.checkInfo(p.type, info);
                    if (!info.isTraced) {
                        return;
                    }
                    Type.checkInfo(Expr.typeOf(p.lhsExpr), info);
                    if (!info.isTraced) {
                        return;
                    }
                    Expr.compile(p.lhsExpr);
                    RunTyme.emitCheckStoreTraced();
                    p.temp = CG.pop();
                }
            }
            case objMethod -> {
                Expr.prep(p.lhsExpr);
                Expr.compile(p.lhsExpr);
                p.temp = CG.pop();
            }
            case unknown -> throw new AssertError("QualifyExpr");
        }
    }

    private static void compileLV(P p, boolean traced, boolean StaticOnly) {
        long[] obj_offset = new long[1], obj_align = new long[1];
        Field.Info field = new Field.Info();
        AssertError.check(!StaticOnly, "QualifyExpr");
        switch (p.class_) {
            case importDecl -> {
                switch (Value.classOf(p.rhsValue)) {
                    case Expr -> Value.load(p.rhsValue);
                    case Var -> Variable.loadLValue((Variable.T) p.rhsValue);
                    default -> throw new AssertError("QualifyExpr");
                }
            }
            case recField -> {
                Field.split(p.rhsValue, field);
                Expr.compileLValue(p.lhsExpr, traced);
                CG.add_offset(field.offset);
            }
            case objField -> {
                Field.split(p.rhsValue, field);
                if (p.temp != null) {
                    AssertError.check(traced, "QualifyExpr");
                    CG.push(p.temp);
                    CG.free(p.temp);
                    p.temp = null;
                } else {
                    Expr.compile(p.lhsExpr);
                }
                ObjectType.getFieldsOffsetAndAlign(p.holder, obj_offset, obj_align);
                if ((obj_offset[0] >= 0)) {
                    field.offset += obj_offset[0];
                } else {
                    if (Host.doNilChk) {
                        CG.check_nil(M3CG.RuntimeError.BadMemoryReference);
                    }
                    Type.loadInfo(p.holder, M3.rt.OTC_dataOffset);
                    CG.index_bytes(Target.Byte);
                }
                CG.add_offset(field.offset);
                CG.boost_addr_alignment(obj_align[0]);
            }
            case enumLit,
                 objTypeMethod,
                 objMethod,
                 unknown -> throw new AssertError("QualifyExpr");
        }
    }

    public enum Kind { Value, Expr, Type, None }

    public static final class LHS {
        public Kind kind;
        public Value.T value;
        public Expr.T expr;
        public Type.T type;

        LHS() {}
    }

    private static Expr.T fold(P p) {
        LHS lhs = new LHS();
        Expr.T e;
        if ((p.inFold)) {
            Value.illegalRecursion(p.rhsValue);
            return null;
        }
        p.inFold = true;

        // evaluate the qualified expression
        lhs.kind = Kind.Expr;
        lhs.expr = p.lhsExpr;
        doQualify(lhs, p.name);

        // finally, simplify the result to an Expr.T if possible
        switch (lhs.kind) {
            case None -> e = null;
            case Expr -> e = Expr.constValue(lhs.expr);
            case Type -> e = TypeExpr.new_(lhs.type);
            case Value -> {
                switch (Value.classOf(lhs.value)) {
                    case Expr -> e = Expr.constValue(Value.toExpr(lhs.value));
                    case Type -> e = null; // TypeExpr.New (Value.ToType (lhs.value));
                    case Procedure -> {
                        e = ProcExpr.new_(lhs.value);
                        // lhs.value is a procedure
                    }
                    default -> e = null; // not possible to convert to an expression
                }
            }
            default -> throw new AssertError("QualifyExpr");
        }

        p.inFold = false;
        return e;
    }

    /** Port: VAR lhs is the record itself. */
    private static void doQualify(LHS lhs, int name) {
        Expr.T[] e = new Expr.T[1];
        Value.T[] v = new Value.T[1];
        P p;
        Scope.T s;
        Type.T[] t = new Type.T[1], t1 = new Type.T[1];
        int[] n = new int[1];
        switch (lhs.kind) {
            case None -> {
                // don't even try
            }
            case Expr -> {
                if (lhs.expr == null) {
                    lhs.kind = Kind.None; //FINAL
                } else if ((lhs.expr.getClass() == P.class)) {
                    p = (P) lhs.expr;
                    lhs.kind = Kind.Expr;
                    lhs.expr = p.lhsExpr;
                    doQualify(lhs, p.name);
                    doQualify(lhs, name);
                } else if (TypeExpr.split(lhs.expr, t)) {
                    lhs.kind = Kind.Type;
                    lhs.type = t[0];
                    doQualify(lhs, name);
                } else if (NamedExpr.split(lhs.expr, n, v)) {
                    lhs.kind = Kind.Value;
                    lhs.value = v[0];
                    doQualify(lhs, name);
                } else if (RecordExpr.qualify(lhs.expr, name, e)) {
                    lhs.kind = Kind.Expr; //FINAL
                    lhs.expr = e[0];
                } else {
                    e[0] = Expr.constValue(lhs.expr);
                    if ((e[0] != lhs.expr)) {
                        // try qualifying the constant value
                        lhs.kind = Kind.Expr;
                        lhs.expr = Expr.constValue(lhs.expr);
                        doQualify(lhs, name);
                    } else {
                        lhs.kind = Kind.None; //FINAL
                    }
                }
            }
            case Type -> {
                t[0] = Type.strip(lhs.type);
                if (EnumType.lookUp(t[0], name, v)) {
                    lhs.kind = Kind.Expr; //FINAL
                    lhs.expr = Value.toExpr(v[0]);
                } else if (ObjectType.lookUp(t[0], name, v, t1)
                           && (Value.classOf(v[0]) == Value.Class.Method)) {
                    lhs.kind = Kind.Expr; //FINAL
                    lhs.expr = MethodExpr.new_(t[0], name, v[0], t1[0]);
                } else { // type that can't be qualified
                    lhs.kind = Kind.None; //FINAL
                }
            }
            case Value -> {
                switch (Value.classOf(lhs.value)) {
                    case Expr -> {
                        lhs.kind = Kind.Expr;
                        lhs.expr = Value.toExpr(lhs.value);
                        doQualify(lhs, name);
                    }
                    case Type -> {
                        lhs.kind = Kind.Type;
                        lhs.type = Value.toType(lhs.value);
                        doQualify(lhs, name);
                    }
                    case Module -> {
                        s = Module.exportScope((Module.T) Value.base(lhs.value));
                        lhs.kind = Kind.Value; //FINAL
                        lhs.value = Scope.lookUp(s, name, true);
                    }
                    default -> {
                        // can't qualify this kind of value
                        lhs.kind = Kind.None; //FINAL
                    }
                }
            }
        }
    }

    private static boolean isDesignator(P p, boolean lhs) {
        return switch (p.class_) {
            case importDecl -> (Value.classOf(p.rhsValue) == Value.Class.Var);
            case enumLit -> false;
            case objTypeMethod -> false;
            case recField -> Expr.isDesignator(p.lhsExpr);
            case objField -> true;
            case objMethod -> false;
            case unknown -> false;
        };
    }

    private static boolean isWritable(P p, boolean lhs) {
        return switch (p.class_) {
            case importDecl -> Value.isWritable(p.rhsValue, lhs);
            case enumLit -> false;
            case objTypeMethod -> false;
            case recField -> Expr.isWritable(p.lhsExpr, lhs);
            case objField -> true;
            case objMethod -> false;
            case unknown -> false;
        };
    }

    private static boolean isZeroes(P p, boolean l) {
        LHS lhs = new LHS();
        boolean b;
        if ((p.inIsZeroes)) {
            Value.illegalRecursion(p.rhsValue);
            return false;
        }
        p.inIsZeroes = true;

        // evaluate the qualified expression
        lhs.kind = Kind.Expr;
        lhs.expr = p.lhsExpr;
        doQualify(lhs, p.name);

        // finally, simplify the result to an Expr.T if possible
        switch (lhs.kind) {
            case None -> b = false;
            case Expr -> b = Expr.isZeroes(lhs.expr);
            case Type -> b = false;
            case Value -> b = (Value.classOf(lhs.value) == Value.Class.Expr)
                              && Expr.isZeroes(Value.toExpr(lhs.value));
            default -> throw new AssertError("QualifyExpr");
        }

        p.inIsZeroes = false;
        return b;
    }

    private static void noteWrites(P p) {
        switch (p.class_) {
            case enumLit -> {
                //skip
            }
            case objTypeMethod -> {
                //skip
            }
            case objMethod -> {
                //skip
            }
            case unknown -> {
                //skip
            }
            case recField -> Expr.noteWrite(p.lhsExpr);
            case objField -> Expr.noteWrite(p.lhsExpr);
            case importDecl -> {
                if ((Value.classOf(p.rhsValue) == Value.Class.Var)) {
                    Variable.scheduleTrace((Variable.T) Value.base(p.rhsValue));
                }
            }
        }
    }
}
