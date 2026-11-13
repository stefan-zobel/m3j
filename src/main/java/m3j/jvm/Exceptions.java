// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;

import m3j.front.Exceptionz;
import m3j.front.Module;
import m3j.front.NarrowExpr;
import m3j.front.ObjectRef;
import m3j.front.Reff;
import m3j.front.Scope;
import m3j.front.Textt;
import m3j.front.TryFinStmt;
import m3j.front.TryStmt;
import m3j.front.Type;
import m3j.front.TypeCaseStmt;
import m3j.front.Value;

/**
 * Exceptions (TRY EXCEPT, TRY FINALLY, RAISE), and the type tests of
 * TYPECASE and NARROW.
 */
final class Exceptions {
    private Exceptions() {}

    static final ClassDesc CD_M3Object = ClassDesc.of("m3j.rt.M3Object");

    /** The class of an exception: m3.i.Thread$Alerted, m3.m.Main$a. */
    static ClassDesc classOf(Exceptionz.T e) {
        Value.T home = Scope.toUnit(e);
        String n = Code.id(e.name);
        if (home instanceof Module.T m) {
            String u = Code.id(Module.name(m));
            if (m.interface_ && Unit.provided(u) != null) {
                return ClassDesc.of("m3.i." + u + "$" + n);
            }
            String base = (m.interface_ ? "m3.i." : "m3.m.") + u + "$" + n;
            if (!(e.scope != null && e.scope.module)) {
                base += "$" + Code.lineOf(e.origin);
            }
            ClassDesc cd = ClassDesc.of(base);
            generate(cd, Value.globalName(e));
            return cd;
        }
        return ClassDesc.of("m3j.rt.M3Exception");
    }

    /** Generates the class of an exception. */
    static void generate(ClassDesc cd, String m3Name) {
        String key = cd.descriptorString();
        if (Types.out.containsKey(key)) {
            return;
        }
        byte[] b = ClassFile.of().build(cd, clb -> {
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SUPER);
            clb.withSuperclass(Code.CD_M3Exception);
            clb.withMethodBody("<init>", Code.MT_V, ClassFile.ACC_PUBLIC, cb -> {
                cb.aload(0);
                cb.invokespecial(Code.CD_M3Exception, "<init>", Code.MT_V);
                cb.return_();
            });
            clb.withMethodBody("m3Name", MethodTypeDesc.of(ConstantDescs.CD_String), ClassFile.ACC_PUBLIC, cb -> {
                cb.loadConstant(m3Name);
                cb.areturn();
            });
        });
        Types.supers.put(cd, Code.CD_M3Exception);
        Types.out.put(key, b);
    }

    // -------------------------------------------------------------- TRY

    static final MethodTypeDesc TO_M3 = MethodTypeDesc.of(Code.CD_M3Exception, Code.CD_Throwable);

    /**
     * TRY EXCEPT: one handler for everything; the exception (a JVM
     * exception mapped to RuntimeError.E) is matched against the handlers
     * in order, and thrown again if none matches.
     */
    static void tryExcept(Code c, TryStmt.P p) {
        CodeBuilder cb = c.cb;
        Label start = cb.newLabel(), handler = cb.newLabel(), after = cb.newLabel();
        cb.labelBinding(start);
        // the JVM rejects an empty range of an exception handler, and the
        // body may give no code at all
        cb.nop();
        Guard g = new Guard(start);
        if (p.hasElse) {
            g.elseEntry = cb.newLabel();
        }
        c.unwind.push(g);
        c.stmts(p.body);
        c.unwind.pop();
        g.close(c);
        cb.goto_(after);
        cb.labelBinding(handler);
        cb.invokestatic(Code.RT, "toM3", TO_M3);
        Loc ex = c.newLocal(JType.ref(Code.CD_M3Exception));
        ex.store(cb);
        Scope.T outer = null;
        for (TryStmt.Handler h = p.handles; h != null; h = h.next) {
            Label next = cb.newLabel(), body = cb.newLabel();
            for (TryStmt.Except t = h.tags; t != null; t = t.next) {
                Exceptionz.T e = (Exceptionz.T) Value.base(t.obj);
                ex.load(cb);
                cb.instanceOf(classOf(e));
                cb.ifne(body);
            }
            cb.goto_(next);
            cb.labelBinding(body);
            if (h.scope != null) {
                outer = Scope.push(h.scope);
            }
            c.openScope();
            try {
                if (h.var_ != null) {
                    Loc v = c.declare(h.var_);
                    JType vt = v.type();
                    if (v instanceof Loc.Arr a) {
                        a.storage().storePre(cb);
                        ex.load(cb);
                        cb.getfield(Code.CD_M3Exception, "arg", ConstantDescs.CD_Object);
                        cb.checkcast(vt.desc);
                        a.storage().storePost(cb);
                    } else {
                        v.storePre(cb);
                        ex.load(cb);
                        cb.getfield(Code.CD_M3Exception, "arg", ConstantDescs.CD_Object);
                        c.unboxValue(vt);
                        v.storePost(cb);
                    }
                    c.debugVar(h.var_, v, c.here(), 'e');
                }
                c.line(h.origin);
                c.stmts(h.body);
                c.closeScope();
            } finally {
                if (h.scope != null) {
                    Scope.pop(outer);
                }
            }
            cb.goto_(after);
            cb.labelBinding(next);
        }
        if (p.hasElse) {
            cb.labelBinding(g.elseEntry);
            c.stmts(p.elseBody);
            cb.goto_(after);
        } else {
            ex.load(cb);
            cb.athrow();
        }
        g.catchAll(cb, handler);
        cb.labelBinding(after);
    }

    /**
     * The code ranges that the handler of a TRY protects. A jump out of the
     * TRY (EXIT, RETURN) leaves them: the FINALLY parts that it runs, of this
     * TRY and of those around it, and the jump itself are not protected by
     * the handlers of the TRY statements that the jump leaves, which protect
     * the code again after the jump.
     */
    static class Guard {
        final List<Label[]> ranges = new ArrayList<>();
        Label start;
        /**
         * the ELSE handler of a TRY EXCEPT ELSE, which catches EXIT and RETURN
         * as well (the language defines them as exceptions), or null
         */
        Label elseEntry;

        Guard(Label start) {
            this.start = start;
        }

        /** Ends the current range here. */
        void close(Code c) {
            Label end = c.cb.newLabel();
            c.cb.labelBinding(end);
            ranges.add(new Label[] {start, end});
        }

        /**
         * Starts a new range here, after a jump (with a nop: the JVM rejects
         * an empty range, as when the TRY ends right after the jump; the
         * class file library removes the dead code and the ranges of it).
         */
        void reopen(Code c) {
            start = c.cb.newLabel();
            c.cb.labelBinding(start);
            c.cb.nop();
        }

        void catchAll(CodeBuilder cb, Label handler) {
            for (Label[] r : ranges) {
                cb.exceptionCatch(r[0], r[1], handler, Code.CD_Throwable);
            }
        }
    }

    /** A FINALLY part, and the code ranges it protects. */
    static final class Finally extends Guard {
        final Runnable fin;

        Finally(Runnable fin, Label start) {
            super(start);
            this.fin = fin;
        }
    }

    static void tryFinally(Code c, TryFinStmt.P p) {
        protect(c, () -> c.stmts(p.body), () -> c.stmts(p.finally_));
    }

    /**
     * Runs body; the code of fin runs after it, on every exit: normal, by
     * EXIT or RETURN (inlined there), or by an exception (then thrown again).
     */
    static void protect(Code c, Runnable body, Runnable fin) {
        CodeBuilder cb = c.cb;
        Label start = cb.newLabel(), handler = cb.newLabel(), after = cb.newLabel();
        cb.labelBinding(start);
        // the JVM rejects an empty range of an exception handler, and the
        // body may give no code at all (LOCK m DO END)
        cb.nop();
        Finally f = new Finally(fin, start);
        c.unwind.push(f);
        body.run();
        c.unwind.pop();
        f.close(c);
        fin.run();
        cb.goto_(after);
        cb.labelBinding(handler);
        Loc ex = c.newLocal(JType.ref(Code.CD_Throwable));
        ex.store(cb);
        fin.run();
        ex.load(cb);
        cb.athrow();
        f.catchAll(cb, handler);
        cb.labelBinding(after);
    }

    // ------------------------------------------------------------ TYPECASE

    /** Tests the reference on the stack (not NIL) against type t; jumps to yes if it is one. */
    static void typeTest(Code c, Type.T t, Label yes) {
        CodeBuilder cb = c.cb;
        Type.T u = Type.stripPacked(Type.check(t));
        if (u == strip(m3j.front.Null.T)) {
            // only NIL, and the value is not NIL
            cb.pop();
            return;
        }
        if (u == strip(Reff.T)) {
            cb.pop();
            cb.goto_(yes);
            return;
        }
        if (u == strip(ObjectRef.T)) {
            // ROOT: objects and texts
            cb.dup();
            cb.instanceOf(CD_M3Object);
            Label no = cb.newLabel();
            cb.ifeq(no);
            cb.pop();
            cb.goto_(yes);
            cb.labelBinding(no);
            cb.instanceOf(ConstantDescs.CD_String);
            cb.ifne(yes);
            return;
        }
        if (Type.isEqual(u, Textt.T, null)) {
            cb.instanceOf(ConstantDescs.CD_String);
            cb.ifne(yes);
            return;
        }
        JType jt = JType.of(u);
        cb.instanceOf(jt.desc);
        cb.ifne(yes);
    }

    static Type.T strip(Type.T t) {
        return Type.stripPacked(Type.check(t));
    }

    static void typeCase(Code c, TypeCaseStmt.P p) {
        CodeBuilder cb = c.cb;
        c.expr(p.expr);
        Loc x = c.newLocal(JType.ANYREF);
        x.store(cb);
        Label after = cb.newLabel();
        for (TypeCaseStmt.Case k = p.cases; k != null; k = k.next) {
            Label body = cb.newLabel(), next = cb.newLabel();
            for (int i = 0; i < k.nTags; i++) {
                x.load(cb);
                cb.ifnull(body);
                x.load(cb);
                typeTest(c, k.tags[i], body);
            }
            cb.goto_(next);
            cb.labelBinding(body);
            c.line(k.origin);
            Scope.T zz = k.scope != null ? Scope.push(k.scope) : null;
            c.openScope();
            try {
                if (k.var_ != null) {
                    Loc v = c.declare(k.var_);
                    v.storePre(cb);
                    x.load(cb);
                    c.checkcastIfNeeded(v.type().desc);
                    v.storePost(cb);
                    c.debugVar(k.var_, v, c.here(), 't');
                }
                c.stmts(k.stmt);
                c.closeScope();
            } finally {
                if (zz != null) {
                    Scope.pop(zz);
                }
            }
            cb.goto_(after);
            cb.labelBinding(next);
        }
        if (p.hasElse) {
            c.stmts(p.elseBody);
        } else if (m3j.front.Host.doTCaseChk) {
            // as cm3: the error is reported at the line of the last
            // statement compiled (of the last case)
            c.runtimeError(8);
        }
        cb.labelBinding(after);
    }

    /** NARROW (x, T): x, if it is NIL or a T; else the runtime error. */
    static JType narrow(Code c, NarrowExpr.P n) {
        CodeBuilder cb = c.cb;
        JType t = JType.of(n.tipe);
        c.expr(n.expr);
        if (m3j.front.Host.doNarrowChk) {
            Label ok = cb.newLabel(), fail = cb.newLabel();
            cb.dup();
            cb.ifnull(ok);
            cb.dup();
            typeTest(c, n.tipe, ok);
            cb.labelBinding(fail);
            c.runtimeError(5);
            cb.labelBinding(ok);
        }
        c.checkcastIfNeeded(t.desc);
        return t;
    }

}
