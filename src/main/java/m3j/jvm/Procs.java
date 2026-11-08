// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

import m3j.front.CallExpr;
import m3j.front.Expr;
import m3j.front.Formal;
import m3j.front.Method;
import m3j.front.Module;
import m3j.front.ProcType;
import m3j.front.Procedure;
import m3j.front.QualifyExpr;
import m3j.front.Scope;
import m3j.front.Type;
import m3j.front.UserProc;
import m3j.front.Value;

/** Calls of procedures and methods, and procedure values. */
final class Procs {
    private Procs() {}

    static final ClassDesc CD_LMF = ClassDesc.of("java.lang.invoke.LambdaMetafactory");
    static final DirectMethodHandleDesc BSM_LMF = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC,
        CD_LMF, "metafactory", MethodTypeDesc.of(ConstantDescs.CD_CallSite, ConstantDescs.CD_MethodHandles_Lookup,
            ConstantDescs.CD_String, ConstantDescs.CD_MethodType, ConstantDescs.CD_MethodType,
            ConstantDescs.CD_MethodHandle, ConstantDescs.CD_MethodType));

    /** A call: of a built-in, a method, a procedure, or a procedure value. */
    static JType call(Code c, CallExpr.T ce, JType t) {
        CodeBuilder cb = c.cb;
        if (ce.methods != UserProc.Methods) {
            return Builtins.call(c, ce, t);
        }
        if (ce.proc instanceof QualifyExpr.P q && q.class_ == QualifyExpr.Class.objMethod) {
            return methodCall(c, ce, q);
        }
        Value.T[] pv = new Value.T[1];
        if (UserProc.isProcedureLiteral(ce.proc, pv) && Value.base(pv[0]) instanceof Procedure.T p) {
            if (Java.isBound(p)) {
                return Java.call(c, ce, p);
            }
            if (c.unit.frames.parent.containsKey(p) && c.frames.linkOf(p) != null) {
                c.frameOf(c.frames.linkOf(p)).load(cb);
            }
            Type.T sig = Type.base(p.signature);
            args(c, sig, ce.args);
            c.unit.invoke(cb, p);
            return JType.of(ProcType.result(sig));
        }
        if (ce.proc instanceof QualifyExpr.P q && q.class_ == QualifyExpr.Class.objTypeMethod
                && q.rhsValue instanceof Method.T m) {
            Type.T objType = Type.stripPacked(Type.check(q.objType));
            if (m.dfault != null && Value.base(m.dfault) instanceof Procedure.T bound
                    && objType.info.class_ == Type.Class.Object && m.parent == objType) {
                // bound in T itself, which is not opaque: the procedure
                Type.T sig = Type.base(bound.signature);
                args(c, sig, ce.args);
                c.unit.invoke(cb, bound);
                return JType.of(ProcType.result(sig));
            }
            // the implementation that the class of T has, found when the call is linked
            Method.T decl = Types.declaring(m);
            ClassDesc owner = Types.classOf(q.objType);
            Type.T sig = Type.base(Expr.typeOf(ce.proc));
            args(c, sig, ce.args);
            MethodTypeDesc d = Types.methodDescriptor(decl).insertParameterTypes(0, owner);
            cb.invokedynamic(java.lang.constant.DynamicCallSiteDesc.of(
                MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, Unit.CD_Link, "special",
                    MethodTypeDesc.of(ConstantDescs.CD_CallSite, ConstantDescs.CD_MethodHandles_Lookup,
                        ConstantDescs.CD_String, ConstantDescs.CD_MethodType, ConstantDescs.CD_Class)),
                Types.methodName(decl), d, owner));
            return JType.of(ProcType.result(sig));
        }
        // a procedure value
        Type.T sig = Type.base(Expr.typeOf(ce.proc));
        MethodTypeDesc d = Params.descriptor(sig);
        ClassDesc iface = Types.procInterface(d);
        c.expr(ce.proc);
        c.nilCheck();
        cb.checkcast(iface);
        args(c, sig, ce.args);
        cb.invokeinterface(iface, "call", d);
        return JType.of(ProcType.result(sig));
    }

    /** obj.m (args) */
    static JType methodCall(Code c, CallExpr.T ce, QualifyExpr.P q) {
        CodeBuilder cb = c.cb;
        Method.T m = (Method.T) q.rhsValue;
        Method.T decl = Types.declaring(m);
        ClassDesc owner = Types.classOf(decl.parent);
        MethodTypeDesc d = Params.descriptor(decl.signature);
        c.expr(q.lhsExpr);
        c.nilCheck();
        cb.checkcast(owner);
        args(c, Type.base(m.signature), ce.args);
        cb.invokevirtual(owner, Types.methodName(decl), d);
        return JType.of(ProcType.result(Type.base(m.signature)));
    }

    /** Pushes the arguments of a call. */
    static void args(Code c, Type.T sig, Expr.T[] args) {
        int i = 0;
        for (Value.T v = ProcType.formals(sig); v != null; v = v.next, i++) {
            arg(c, (Formal.T) v, args[i]);
        }
    }

    static void arg(Code c, Formal.T f, Expr.T a) {
        CodeBuilder cb = c.cb;
        Params.Layout lay = Params.layout(f);
        JType t = lay.type();
        switch (t.kind) {
            case RECORD -> {
                c.expr(a);
                if (f.mode == Formal.Mode.mVALUE && !Code.isFresh(a)) {
                    c.copyRecord(f.type);
                }
            }
            case ARRAY, BIGSET -> {
                Loc.Arr x = c.arr(a);
                Loc.Arr formal = new Loc.Arr(x.storage(), x.offset(), new Loc[0], f.type, t);
                c.checkShape(x, formal);
                if (f.mode == Formal.Mode.mVALUE && !(Code.isFresh(a) && x.offset() instanceof Loc.ConstInt)) {
                    Loc.Arr r = c.freshArr(f.type);
                    c.copyArr(x, r);
                    r.storage().load(cb);
                    cb.iconst_0();
                } else {
                    x.storage().load(cb);
                    x.offset().load(cb);
                }
            }
            case OPENARRAY -> {
                Loc.Arr x = c.arr(a);
                x.storage().load(cb);
                x.offset().load(cb);
                for (int i = 0; i < Aggregates.openDepth(f.type); i++) {
                    c.dim(x, i).load(cb);
                }
            }
            default -> {
                if (f.mode == Formal.Mode.mVAR) {
                    Loc d = c.designator(a);
                    if (d == null) {
                        c.unsupported("VAR actual " + a.getClass().getName());
                        cb.aconst_null();
                        return;
                    }
                    Refs.makeRef(c, d);
                } else if (f.mode == Formal.Mode.mREADONLY) {
                    // a designator of the same type by reference, else a copy
                    Loc d = Expr.isDesignator(a) && Type.isEqual(f.type, Expr.typeOf(a), null)
                        && !(a instanceof CallExpr.T) ? c.designator(a) : null;
                    if (d != null && d.type().kind != t.kind) {
                        // an int FOR counter for an INTEGER formal: a copy (it cannot change)
                        ClassDesc cc = Refs.cellClass(t);
                        cb.new_(cc);
                        cb.dup();
                        d.load(cb);
                        c.widen(d.type().typeKind(), t.typeKind());
                        cb.invokespecial(cc, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, Refs.valueDesc(t)));
                    } else if (d != null) {
                        Refs.makeRef(c, d);
                    } else {
                        ClassDesc cc = Refs.cellClass(t);
                        cb.new_(cc);
                        cb.dup();
                        c.exprAs(a, t, true);
                        cb.invokespecial(cc, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, Refs.valueDesc(t)));
                    }
                } else {
                    c.exprAs(a, t, true);
                }
            }
        }
    }

    // ---------------------------------------------------------- invocations

    /** Calls the top-level procedure p (in any unit) with the arguments on the stack. */
    static void invokeStatic(CodeBuilder cb, Procedure.T p) {
        MethodTypeDesc d = Params.descriptor(p.signature);
        String name = Code.id(p.name);
        if (p.external && Scope.toUnit(p) instanceof Module.T em && em.interface_
                && Unit.provided(Code.id(Module.name(em))) != null) {
            MethodTypeDesc e = Params.erased(d);
            cb.invokestatic(ClassDesc.of(Unit.provided(Code.id(Module.name(em))).getName()), name, e);
            if (!e.returnType().equals(d.returnType())) {
                cb.checkcast(d.returnType());
            }
            return;
        }
        if (p.external) {
            // the Java implementation takes and returns Object for Modula-3 types
            MethodTypeDesc e = Params.erased(d);
            cb.invokedynamic(DynamicCallSiteDesc.of(Unit.bootstrap("external"), Code.id(p.extName), e));
            if (!e.returnType().equals(d.returnType())) {
                cb.checkcast(d.returnType());
            }
            return;
        }
        Value.T home = Scope.toUnit(p);
        if (home instanceof Module.T m && m.interface_) {
            if (p.impl_peer != null && Scope.toUnit(p.impl_peer) instanceof Module.T im && !im.interface_) {
                cb.invokestatic(Unit.classDesc(im), name, d);
                return;
            }
            String intf = Code.id(Module.name(m));
            Class<?> pc = Unit.provided(intf);
            if (pc != null) {
                MethodTypeDesc e = Params.erased(d);
                cb.invokestatic(ClassDesc.of(pc.getName()), name, e);
                if (!e.returnType().equals(d.returnType())) {
                    cb.checkcast(d.returnType());
                }
                return;
            }
            cb.invokedynamic(DynamicCallSiteDesc.of(Unit.bootstrap("call"), name, d, intf));
            return;
        }
        if (home instanceof Module.T m) {
            cb.invokestatic(Unit.classDesc(m), name, d);
        }
    }

    // ----------------------------------------------------- procedure values

    /** Pushes the value of procedure p: an object of its functional interface. */
    static JType procValue(Code c, Procedure.T p) {
        CodeBuilder cb = c.cb;
        if (Java.isBound(p)) {
            c.unit.error("a procedure bound to a Java member cannot be used as a value: " + Code.id(p.name), c.origin);
            cb.aconst_null();
            return JType.PROC;
        }
        MethodTypeDesc d = Params.descriptor(p.signature);
        ClassDesc iface = Types.procInterface(d);
        Unit u = c.unit;
        Value.T home = Scope.toUnit(p);
        boolean nested = u.frames.parent.containsKey(p) && u.frames.parent.get(p) != null;
        if (nested) {
            // a closure: the procedure and the frame; equal if both are
            Procedure.T link = u.frames.linkOf(p);
            ClassDesc fc = link == null ? ConstantDescs.CD_Object : u.frames.frameClassOf(link);
            ClassDesc cc = closureClass(u, p, iface, d, fc, link != null);
            cb.new_(cc);
            cb.dup();
            if (link != null) {
                c.frameOf(link).load(cb);
            } else {
                cb.aconst_null();
            }
            cb.invokespecial(cc, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, fc));
            return JType.PROC;
        }
        if (home == u.module) {
            cb.invokestatic(u.self, u.procAccessor(p), MethodTypeDesc.of(iface));
            return JType.PROC;
        }
        if (home instanceof Module.T m && m.interface_) {
            if (p.impl_peer != null && Scope.toUnit(p.impl_peer) == u.module) {
                return procValue(c, p.impl_peer);
            }
            String intf = Code.id(Module.name(m));
            Class<?> pc = Unit.provided(intf);
            if (pc != null) {
                lambda(cb, iface, d, List.of(), ClassDesc.of(pc.getName()), Code.id(p.name));
                return JType.PROC;
            }
            if (!p.external) {
                cb.invokedynamic(DynamicCallSiteDesc.of(Unit.bootstrap("call"), "$proc$" + Code.id(p.name),
                    MethodTypeDesc.of(iface), intf));
                return JType.PROC;
            }
        }
        return c.unsupportedValue("procedure value " + Code.id(p.name), JType.PROC);
    }

    /** The class of the closures of nested procedure p. */
    static ClassDesc closureClass(Unit u, Procedure.T p, ClassDesc iface, MethodTypeDesc d, ClassDesc fc,
                                  boolean hasLink) {
        String m = u.methodName(p);
        String us = u.self.descriptorString();
        ClassDesc cd = ClassDesc.of(us.substring(1, us.length() - 1).replace('/', '.') + "$C$" + m.replace('$', '_'));
        if (Types.out.containsKey(cd.descriptorString())) {
            return cd;
        }
        MethodTypeDesc impl = hasLink ? d.insertParameterTypes(0, fc) : d;
        byte[] b = java.lang.classfile.ClassFile.of().build(cd, clb -> {
            clb.withFlags(java.lang.reflect.AccessFlag.PUBLIC, java.lang.reflect.AccessFlag.FINAL,
                java.lang.reflect.AccessFlag.SUPER);
            clb.withInterfaceSymbols(iface, ClassDesc.of("m3j.rt.Nested"));
            clb.withField("link", fc, java.lang.classfile.ClassFile.ACC_PRIVATE | java.lang.classfile.ClassFile.ACC_FINAL);
            clb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void, fc),
                java.lang.classfile.ClassFile.ACC_PUBLIC, cb -> {
                    cb.aload(0);
                    cb.invokespecial(ConstantDescs.CD_Object, "<init>", Code.MT_V);
                    cb.aload(0);
                    cb.aload(1);
                    cb.putfield(cd, "link", fc);
                    cb.return_();
                });
            clb.withMethodBody("call", d, java.lang.classfile.ClassFile.ACC_PUBLIC, cb -> {
                if (hasLink) {
                    cb.aload(0);
                    cb.getfield(cd, "link", fc);
                }
                int slot = 1;
                for (ClassDesc pt : d.parameterList()) {
                    java.lang.classfile.TypeKind k = java.lang.classfile.TypeKind.from(pt);
                    cb.loadLocal(k, slot);
                    slot += k.slotSize();
                }
                cb.invokestatic(u.self, m, impl);
                cb.return_(java.lang.classfile.TypeKind.from(d.returnType()));
            });
            clb.withMethodBody("equals", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object),
                java.lang.classfile.ClassFile.ACC_PUBLIC, cb -> {
                    java.lang.classfile.Label no = cb.newLabel();
                    cb.aload(1);
                    cb.instanceOf(cd);
                    cb.ifeq(no);
                    cb.aload(1);
                    cb.checkcast(cd);
                    cb.getfield(cd, "link", fc);
                    cb.aload(0);
                    cb.getfield(cd, "link", fc);
                    cb.if_acmpne(no);
                    cb.iconst_1();
                    cb.ireturn();
                    cb.labelBinding(no);
                    cb.iconst_0();
                    cb.ireturn();
                });
            clb.withMethodBody("hashCode", MethodTypeDesc.of(ConstantDescs.CD_int),
                java.lang.classfile.ClassFile.ACC_PUBLIC, cb -> {
                    cb.aload(0);
                    cb.getfield(cd, "link", fc);
                    cb.invokestatic(ClassDesc.of("java.lang.System"), "identityHashCode",
                        MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_Object));
                    cb.ireturn();
                });
        });
        Types.supers.put(cd, ConstantDescs.CD_Object);
        Types.out.put(cd.descriptorString(), b);
        return cd;
    }

    /** An object of iface whose call runs owner.name (captured..., params). */
    static void lambda(CodeBuilder cb, ClassDesc iface, MethodTypeDesc d, List<ClassDesc> captured,
                       ClassDesc owner, String name) {
        lambda(cb, iface, d, captured, owner, name, "call");
    }

    /** An object of iface whose abstract method sam, of type d, runs owner.name (captured..., params). */
    static void lambda(CodeBuilder cb, ClassDesc iface, MethodTypeDesc d, List<ClassDesc> captured,
                       ClassDesc owner, String name, String sam) {
        List<ClassDesc> implParams = new ArrayList<>(captured);
        implParams.addAll(d.parameterList());
        MethodTypeDesc implType = MethodTypeDesc.of(d.returnType(), implParams);
        DirectMethodHandleDesc impl = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, owner,
            name, implType);
        cb.invokedynamic(DynamicCallSiteDesc.of(BSM_LMF, sam, MethodTypeDesc.of(iface, captured), d, impl, d));
    }
}
