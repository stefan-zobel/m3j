// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import m3j.front.BlockStmt;
import m3j.front.CallExpr;
import m3j.front.Expr;
import m3j.front.ForStmt;
import m3j.front.Formal;
import m3j.front.Module;
import m3j.front.NamedExpr;
import m3j.front.ProcType;
import m3j.front.Procedure;
import m3j.front.Scope;
import m3j.front.Stmt;
import m3j.front.TryStmt;
import m3j.front.Type;
import m3j.front.TypeCaseStmt;
import m3j.front.UserProc;
import m3j.front.Value;
import m3j.front.Variable;
import m3j.front.WithStmt;

/**
 * The analysis of nested procedures of a unit. A procedure whose variables
 * are used by procedures nested in it has a frame: an object of a class
 * generated for it, with a field per such variable and a link to the frame
 * of the nearest enclosing procedure that has one. A nested procedure
 * takes that frame (its static link) as its first parameter. The body of
 * a module counts as a procedure (INIT).
 *
 * <p>Scalar variables that are passed as VAR parameters live in cells.
 */
final class Frames {

    /** The body of the unit, as a procedure. */
    static final Procedure.T INIT = new Procedure.T();

    final Module.T module;
    final ClassDesc unitClass;
    final Map<Scope.T, Procedure.T> procOfScope = new HashMap<>();
    final Map<Procedure.T, Procedure.T> parent = new HashMap<>();
    final List<Procedure.T> procedures = new ArrayList<>();
    final Map<Procedure.T, List<Variable.T>> captured = new LinkedHashMap<>();
    final Map<Variable.T, String> fieldNames = new HashMap<>();
    final Map<Procedure.T, ClassDesc> frameClass = new HashMap<>();
    final Set<Variable.T> addrTaken = new HashSet<>();
    /** the captured WITH names bound to scalar variables: references in the frame */
    final Set<Variable.T> withRefs = new HashSet<>();
    final Map<Procedure.T, String> methodNames;

    Frames(Module.T module, ClassDesc unitClass, Map<Procedure.T, String> methodNames) {
        this.module = module;
        this.unitClass = unitClass;
        this.methodNames = methodNames;
    }

    void analyze() {
        procOfScope.put(module.localScope, null);
        for (Value.T v = Scope.toList(module.localScope); v != null; v = v.next) {
            if (v instanceof Procedure.T p && p.block != null) {
                procedure(p, null);
            }
        }
        if (!module.interface_) {
            blocks(module.block, INIT);
        }
        // the variables of each procedure that nested procedures use
        scanVars(INIT, module.block, null);
        for (Procedure.T p : procedures) {
            scanVars(p, p.block, p.syms);
        }
        // which procedures need a static link
        for (Procedure.T p : procedures) {
            uses(p);
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            // what a nested procedure uses of the procedures enclosing its
            // parent, its parent uses too (it must pass its link on)
            for (Procedure.T c : procedures) {
                Procedure.T par = parent.get(c);
                if (par == null || par == INIT) {
                    continue;
                }
                for (Procedure.T o : usedOwners.get(c)) {
                    if (isAncestor(o, par) && usedOwners.get(par).add(o)) {
                        changed = true;
                    }
                }
            }
            for (Procedure.T p : procedures) {
                if (!needsLink.contains(p) && computeNeedsLink(p)) {
                    needsLink.add(p);
                    changed = true;
                }
            }
            // a call of a procedure that needs a link passes the frame it
            // needs, which the caller uses then (and its parents pass on)
            for (Procedure.T c : procedures) {
                for (Procedure.T r : usedProcs.get(c)) {
                    if (needsLink.contains(r)) {
                        Procedure.T l = nearestFramed(r);
                        if (l != null && l != c && isAncestor(l, c) && usedOwners.get(c).add(l)) {
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    /** the procedures whose code needs the frame of an enclosing procedure */
    final Set<Procedure.T> needsLink = new HashSet<>();
    /** per procedure: the owners of the captured variables it uses, and the nested procedures it uses */
    private final Map<Procedure.T, Set<Procedure.T>> usedOwners = new HashMap<>();
    private final Map<Procedure.T, Set<Procedure.T>> usedProcs = new HashMap<>();

    /** Records what the code of p (its statements and initializers) uses. */
    private void uses(Procedure.T p) {
        Set<Procedure.T> owners = new HashSet<>();
        Set<Procedure.T> procs = new HashSet<>();
        java.util.function.Consumer<Expr.T> visit = e -> {
            Value.T v = null;
            int[] name = new int[1];
            Value.T[] obj = new Value.T[1];
            if (e instanceof NamedExpr.P && NamedExpr.split(e, name, obj)) {
                v = Value.base(obj[0]);
            } else if (e instanceof m3j.front.ProcExpr.P pe) {
                v = Value.base(pe.proc);
            }
            if (v instanceof Variable.T var && isCaptured(var)) {
                owners.add(owner(var));
            } else if (v instanceof Procedure.T q && parent.containsKey(q)) {
                procs.add(q);
            }
        };
        List<Scope.T> scopes = new ArrayList<>();
        scopes.add(p.syms);
        Walk.stmts(p.block, s -> {
            switch (s) {
                case BlockStmt.P b -> scopes.add(b.scope);
                case WithStmt.P w -> scopes.add(w.scope);
                default -> {}
            }
        }, visit);
        for (Scope.T sc : scopes) {
            for (Value.T v = Scope.toList(sc); v != null; v = v.next) {
                if (v instanceof Variable.T var && var.initExpr != null) {
                    Walk.expr(var.initExpr, visit);
                }
            }
        }
        // the nested procedures of p count as used: their links go through p
        for (Procedure.T q : procedures) {
            if (parent.get(q) == p) {
                procs.add(q);
            }
        }
        usedOwners.put(p, owners);
        usedProcs.put(p, procs);
    }

    /**
     * True if p needs the frame of an enclosing procedure: for a captured
     * variable of one, for a procedure it uses that needs one above p, or
     * for a procedure nested in it that does.
     */
    private boolean computeNeedsLink(Procedure.T p) {
        for (Procedure.T o : usedOwners.get(p)) {
            if (o != p && isAncestor(o, p)) {
                return true;
            }
        }
        for (Procedure.T q : usedProcs.get(p)) {
            if (needsLink.contains(q)) {
                Procedure.T l = nearestFramed(q);
                if (l != null && l != p && isAncestor(l, p)) {
                    return true;
                }
                // q is nested in p: what q needs above p, p needs too
                if (parent.get(q) == p || isAncestor(p, q)) {
                    for (Procedure.T o : usedOwners.get(q)) {
                        if (o != p && isAncestor(o, p)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** True if a encloses p (a is INIT, or a procedure p is nested in). */
    private boolean isAncestor(Procedure.T a, Procedure.T p) {
        for (Procedure.T x = parent.get(p); x != null; x = parent.get(x)) {
            if (x == a) {
                return true;
            }
        }
        return false;
    }

    private Procedure.T nearestFramed(Procedure.T p) {
        Procedure.T a = parent.get(p);
        while (a != null && !framed(a)) {
            a = parent.get(a);
        }
        return a;
    }

    private void procedure(Procedure.T p, Procedure.T par) {
        if (parent.containsKey(p)) {
            return;
        }
        parent.put(p, par);
        procedures.add(p);
        procOfScope.put(p.syms, p);
        for (Value.T v = Scope.toList(p.syms); v != null; v = v.next) {
            if (v instanceof Procedure.T q && q.block != null) {
                procedure(q, p);
            }
        }
        blocks(p.block, p);
    }

    /** The procedures declared in the blocks of a statement list. */
    private void blocks(Stmt.T list, Procedure.T owner) {
        Walk.stmts(list, s -> {
            if (s instanceof BlockStmt.P b && b.scope != null) {
                for (Value.T v = Scope.toList(b.scope); v != null; v = v.next) {
                    if (v instanceof Procedure.T q && q.block != null) {
                        procedure(q, owner);
                    }
                }
            }
        }, e -> {});
    }

    /** Notes the local scalar variables that a call passes VAR: they live in cells. */
    private final java.util.function.Consumer<Expr.T> varArgs = e -> {
        if (e instanceof CallExpr.T ce && ce.methods == UserProc.Methods && ce.args != null) {
            Type.T sig = callSignature(ce);
            if (sig == null) {
                return;
            }
            int i = 0;
            for (Value.T f = ProcType.formals(sig); f != null && i < ce.args.length; f = f.next, i++) {
                Formal.T ft = (Formal.T) f;
                if (ft.mode == Formal.Mode.mVAR && isScalar(JType.of(ft.type))) {
                    Variable.T v = namedVariable(ce.args[i]);
                    if (v != null && !Code.isGlobal(v)) {
                        addrTaken.add(v);
                    }
                }
            }
        }
    };

    /** Finds the captured and the address-taken variables of a procedure. */
    private void scanVars(Procedure.T p, Stmt.T body, Scope.T syms) {
        List<Variable.T> vars = new ArrayList<>();
        if (syms != null) {
            scopeVars(syms, vars);
        }
        Walk.stmts(body, s -> {
            switch (s) {
                case BlockStmt.P b -> scopeVars(b.scope, vars);
                case ForStmt.P f -> scopeVars(f.scope, vars);
                case WithStmt.P w -> {
                    scopeVars(w.scope, vars);
                    withRef(w);
                }
                case TryStmt.P t -> {
                    for (TryStmt.Handler h = t.handles; h != null; h = h.next) {
                        scopeVars(h.scope, vars);
                    }
                }
                case TypeCaseStmt.P t -> {
                    for (TypeCaseStmt.Case c = t.cases; c != null; c = c.next) {
                        scopeVars(c.scope, vars);
                    }
                }
                default -> {}
            }
        }, varArgs);
        // the initial values of the variables (of blocks too) may pass variables VAR
        for (Variable.T v : vars) {
            if (v.initExpr != null) {
                Walk.expr(v.initExpr, varArgs);
            }
        }
        List<Variable.T> cap = new ArrayList<>();
        for (Variable.T v : vars) {
            if (v.up_level) {
                cap.add(v);
                fieldNames.put(v, "v" + fieldNames.size() + "_" + Code.id(v.name));
            }
        }
        if (!cap.isEmpty()) {
            captured.put(p, cap);
        }
    }

    /**
     * A WITH name bound to a scalar variable and used by a nested procedure
     * (or by a FINALLY clause, which the front end treats as one) is a
     * reference to the variable in the frame, so that assignments through
     * it change the variable; a local variable it is bound to lives in a
     * cell, so that the reference does not copy it.
     */
    private void withRef(WithStmt.P w) {
        Variable.T var = w.var_;
        if (var == null || !var.up_level || !isScalar(JType.of(var.type))
            || !Expr.isDesignator(w.expr) || w.expr instanceof CallExpr.T) {
            return;
        }
        withRefs.add(var);
        Variable.T root = namedVariable(w.expr);
        if (root != null && !Code.isGlobal(root)) {
            addrTaken.add(root);
        }
    }

    static Type.T callSignature(CallExpr.T ce) {
        Type.T t = Expr.typeOf(ce.proc);
        if (t == null) {
            t = m3j.front.QualifyExpr.methodType(ce.proc);
        }
        return t == null ? null : Type.base(t);
    }

    static Variable.T namedVariable(Expr.T e) {
        int[] name = new int[1];
        Value.T[] obj = new Value.T[1];
        if (e instanceof NamedExpr.P && NamedExpr.split(e, name, obj)) {
            Value.T v = Value.base(obj[0]);
            if (v instanceof Variable.T var) {
                return var;
            }
        }
        return null;
    }

    static boolean isScalar(JType t) {
        return switch (t.kind) {
            case RECORD, ARRAY, OPENARRAY, BIGSET -> false;
            default -> true;
        };
    }

    private static void scopeVars(Scope.T s, List<Variable.T> out) {
        for (Value.T v = Scope.toList(s); v != null; v = v.next) {
            if (v instanceof Variable.T var) {
                out.add(var);
            }
        }
    }

    // --------------------------------------------------------------- queries

    boolean framed(Procedure.T p) {
        return captured.containsKey(p);
    }

    /** The nearest enclosing procedure of p that has a frame, or null. */
    Procedure.T linkOf(Procedure.T p) {
        if (!needsLink.contains(p)) {
            return null;
        }
        return nearestFramed(p);
    }

    /** The procedure (or INIT, or null for a global) that declares v. */
    Procedure.T owner(Variable.T v) {
        Scope.T s = v.scope;
        if (s == null || s.module) {
            return null;
        }
        while (s != null) {
            if (procOfScope.containsKey(s) && s != module.localScope) {
                return procOfScope.get(s);
            }
            if (s.module) {
                return INIT;
            }
            s = s.parent;
        }
        return INIT;
    }

    boolean isCaptured(Variable.T v) {
        return fieldNames.containsKey(v);
    }

    // --------------------------------------------------------- frame classes

    ClassDesc frameClassOf(Procedure.T p) {
        return frameClass.computeIfAbsent(p, q -> {
            String m = q == INIT ? "$init" : methodNames.get(q);
            String u = unitClass.descriptorString();
            u = u.substring(1, u.length() - 1).replace('/', '.');
            return ClassDesc.of(u + "$F$" + m.replace('$', '_'));
        });
    }

    /** The JVM type of the frame field of a captured variable. */
    JType fieldType(Variable.T v) {
        JType t = JType.of(v.type);
        if (v.formal instanceof Formal.T f && f.mode != Formal.Mode.mVALUE && isScalar(t)) {
            return JType.ref(Refs.refClass(t));
        }
        if (withRefs.contains(v) && isScalar(t)) {
            return JType.ref(Refs.refClass(t));
        }
        if (addrTaken.contains(v) && isScalar(t)) {
            return JType.ref(Refs.cellClass(t));
        }
        return t;
    }

    /** the type of the field of the result in the frame of a procedure split in chunks */
    final Map<Procedure.T, JType> resultField = new HashMap<>();

    /**
     * Gives procedure p (split in chunks) a frame, if it has none, with the
     * variables vars (in addition to those it has) and a field for its
     * result if result is not null, and generates its frame class again.
     */
    void addSplit(Procedure.T p, java.util.Collection<Variable.T> vars, JType result) {
        List<Variable.T> cap = captured.computeIfAbsent(p, q -> new ArrayList<>());
        for (Variable.T v : vars) {
            if (!fieldNames.containsKey(v)) {
                cap.add(v);
                fieldNames.put(v, "v" + fieldNames.size() + "_" + Code.id(v.name));
            }
        }
        if (result != null) {
            resultField.put(p, result);
        }
        generate(p, cap);
    }

    /** Generates the frame classes. */
    void generate() {
        for (Map.Entry<Procedure.T, List<Variable.T>> e : captured.entrySet()) {
            generate(e.getKey(), e.getValue());
        }
    }

    private void generate(Procedure.T p, List<Variable.T> vars) {
        {
            ClassDesc cd = frameClassOf(p);
            Procedure.T link = linkOf(p);
            JType res = resultField.get(p);
            byte[] b = ClassFile.of().build(cd, clb -> {
                clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SUPER);
                if (link != null) {
                    clb.withField("up", frameClassOf(link), ClassFile.ACC_PUBLIC);
                }
                if (res != null) {
                    clb.withField("$result", res.desc, ClassFile.ACC_PUBLIC);
                }
                for (Variable.T v : vars) {
                    JType t = fieldType(v);
                    String n = fieldNames.get(v);
                    clb.withField(n, t.desc, ClassFile.ACC_PUBLIC);
                    if (t.kind == JType.Kind.ARRAY || t.kind == JType.Kind.OPENARRAY || t.kind == JType.Kind.BIGSET) {
                        clb.withField(n + "$off", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC);
                        for (int i = 0; i < Aggregates.openDepth(v.type); i++) {
                            clb.withField(n + "$n" + i, ConstantDescs.CD_int, ClassFile.ACC_PUBLIC);
                        }
                    }
                }
                clb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_PUBLIC, cb -> {
                    cb.aload(0);
                    cb.invokespecial(ConstantDescs.CD_Object, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
                    cb.return_();
                });
            });
            Types.supers.put(cd, ConstantDescs.CD_Object);
            Types.out.put(cd.descriptorString(), b);
        }
    }
}
