// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import m3j.front.AddExpr;
import m3j.front.AndExpr;
import m3j.front.ArrayExpr;
import m3j.front.AssertStmt;
import m3j.front.AssignStmt;
import m3j.front.BlockStmt;
import m3j.front.CallExpr;
import m3j.front.CallStmt;
import m3j.front.CaseStmt;
import m3j.front.CheckExpr;
import m3j.front.CompareExpr;
import m3j.front.ConcatExpr;
import m3j.front.ConsExpr;
import m3j.front.Constant;
import m3j.front.DerefExpr;
import m3j.front.DivExpr;
import m3j.front.DivideExpr;
import m3j.front.EnumElt;
import m3j.front.EnumExpr;
import m3j.front.EqualExpr;
import m3j.front.EvalStmt;
import m3j.front.Exceptionz;
import m3j.front.ExitStmt;
import m3j.front.Expr;
import m3j.front.ForStmt;
import m3j.front.Formal;
import m3j.front.Host;
import m3j.front.IfStmt;
import m3j.front.InExpr;
import m3j.front.IntegerExpr;
import m3j.front.LoopStmt;
import m3j.front.M3;
import m3j.front.M3String;
import m3j.front.M3WString;
import m3j.front.ModExpr;
import m3j.front.Module;
import m3j.front.MultiplyExpr;
import m3j.front.NamedExpr;
import m3j.front.NarrowExpr;
import m3j.front.NegateExpr;
import m3j.front.NilChkExpr;
import m3j.front.NotExpr;
import m3j.front.Null;
import m3j.front.OrExpr;
import m3j.front.PlusExpr;
import m3j.front.ProcExpr;
import m3j.front.ProcType;
import m3j.front.Procedure;
import m3j.front.QualifyExpr;
import m3j.front.RaiseStmt;
import m3j.front.RecordExpr;
import m3j.front.ReelExpr;
import m3j.front.RepeatStmt;
import m3j.front.ReturnStmt;
import m3j.front.Scanner;
import m3j.front.Scope;
import m3j.front.SetExpr;
import m3j.front.Stmt;
import m3j.front.SubscriptExpr;
import m3j.front.SubtractExpr;
import m3j.front.TextExpr;
import m3j.front.TryFinStmt;
import m3j.front.TryStmt;
import m3j.front.Type;
import m3j.front.TypeCaseStmt;
import m3j.front.TypeExpr;
import m3j.front.UserProc;
import m3j.front.Value;
import m3j.front.VarExpr;
import m3j.front.Variable;
import m3j.front.WhileStmt;
import m3j.front.WithStmt;
import m3j.middle.M3ID;
import m3j.middle.TInt;

/**
 * Generates the code of one method: a procedure, or the body of a unit.
 * Statements and expressions are compiled straight from the tree of the
 * front end.
 */
final class Code {

    static final ClassDesc RT = ClassDesc.of("m3j.rt.Rt");
    static final ClassDesc CD_RuntimeException = ClassDesc.of("java.lang.RuntimeException");
    static final ClassDesc CD_M3Exception = ClassDesc.of("m3j.rt.M3Exception");
    static final ClassDesc CD_Throwable = ClassDesc.of("java.lang.Throwable");
    static final MethodTypeDesc MT_V = MethodTypeDesc.of(ConstantDescs.CD_void);

    final Unit unit;
    final Frames frames;
    final CodeBuilder cb;
    /** the procedure, or Frames.INIT for the body of the unit */
    final Procedure.T proc;
    final JType result;
    final Map<Value.T, Loc> locals = new HashMap<>();
    /** EXIT targets (labels) and FINALLY parts, innermost first */
    final Deque<Object> unwind = new ArrayDeque<>();
    /** this procedure's frame, and the frame of the nearest enclosing procedure with one */
    Loc myFrame, staticLink;
    private int lastLine = -1;
    /** The place of the source being compiled. */
    long origin;
    /** the plan if the procedure is split in chunks, else null */
    Split split;
    /** the chunk whose method this is, else null */
    Split.Chunk chunk;

    /** The bottom of the unwind stack of a chunk method: EXIT and RETURN leave the method. */
    private static final Object CHUNK_END = new Object();

    Code(Unit unit, CodeBuilder cb, Procedure.T proc) {
        this.unit = unit;
        this.frames = unit.frames;
        this.cb = cb;
        this.proc = proc;
        this.result = proc == Frames.INIT ? JType.VOID
            : JType.of(ProcType.result(Type.base(proc.signature)));
    }

    static String id(int name) {
        return M3ID.toText(M3.ids, name);
    }

    // ---------------------------------------------------------------- lines

    static int lineOf(long origin) {
        long save = Scanner.offset;
        Scanner.offset = origin;
        String[] file = new String[1];
        long[] line = new long[1];
        Scanner.here(file, line);
        Scanner.offset = save;
        return (int) line[0];
    }

    void line(long origin) {
        this.origin = origin;
        int l = lineOf(origin);
        if (l > 0 && l != lastLine) {
            cb.lineNumber(l);
            lastLine = l;
        }
    }

    // ------------------------------------------------------------- frames

    /** Creates this procedure's frame, if it has one; staticLink must be set. */
    void enterFrame() {
        if (!frames.framed(proc)) {
            return;
        }
        ClassDesc fc = frames.frameClassOf(proc);
        int slot = cb.allocateLocal(TypeKind.REFERENCE);
        cb.new_(fc);
        cb.dup();
        cb.invokespecial(fc, "<init>", MT_V);
        cb.astore(slot);
        myFrame = new Loc.Local(slot, JType.ref(fc));
        debugName("$frame", myFrame, here());
        if (staticLink != null && frames.linkOf(proc) != null) {
            cb.aload(slot);
            staticLink.load(cb);
            cb.putfield(fc, "up", frames.frameClassOf(frames.linkOf(proc)));
        }
    }

    /** A location that loads the frame of procedure p (this one or an enclosing one). */
    Loc frameOf(Procedure.T p) {
        if (p == proc) {
            return myFrame;
        }
        Procedure.T a = frames.linkOf(proc);
        Loc l = staticLink;
        while (a != null && a != p) {
            Procedure.T up = frames.linkOf(a);
            l = new Loc.Field(l, frames.frameClassOf(a), "up", JType.ref(frames.frameClassOf(up)));
            a = up;
        }
        return l;
    }

    /** The location of a captured variable in its frame. */
    Loc frameLoc(Variable.T v, Procedure.T owner) {
        ClassDesc fc = frames.frameClassOf(owner);
        String n = frames.fieldNames.get(v);
        Loc f = frameOf(owner);
        JType ft = frames.fieldType(v);
        JType t = JType.of(v.type);
        Loc field = new Loc.Field(f, fc, n, ft);
        if (ft.desc.equals(Refs.refClass(t)) && Frames.isScalar(t)) {
            return new Loc.VarRef(field, t);
        }
        if (ft.desc.equals(Refs.cellClass(t)) && Frames.isScalar(t)) {
            return new Loc.Cell(field, t);
        }
        if (t.kind == JType.Kind.ARRAY || t.kind == JType.Kind.OPENARRAY || t.kind == JType.Kind.BIGSET) {
            int depth = Aggregates.openDepth(v.type);
            Loc[] dims = new Loc[depth];
            for (int i = 0; i < depth; i++) {
                dims[i] = new Loc.Field(f, fc, n + "$n" + i, JType.INT);
            }
            return new Loc.Arr(field, new Loc.Field(f, fc, n + "$off", JType.INT), dims, v.type, t);
        }
        return field;
    }

    // ------------------------------------------------------------ procedure

    /** Generates the body of the procedure. */
    void procedure() {
        Scope.T zz = Scope.push(proc.syms);
        openScope();
        try {
            int slot = 0;
            if (frames.linkOf(proc) != null) {
                staticLink = new Loc.Local(slot, JType.ref(frames.frameClassOf(frames.linkOf(proc))));
                debugName("$up", staticLink, cb.startLabel());
                slot++;
            }
            // the formals, in the order of the signature
            // (a function with a large result has a hidden formal for it,
            // which is not in the signature)
            List<Variable.T> formals = new ArrayList<>();
            for (Value.T f = ProcType.formals(Type.base(proc.signature)); f != null; f = f.next) {
                for (Value.T v = Scope.toList(proc.syms); v != null; v = v.next) {
                    if (v instanceof Variable.T var && var.formal == f) {
                        formals.add(var);
                        break;
                    }
                }
            }
            Map<Variable.T, Loc> params = new HashMap<>();
            for (Variable.T var : formals) {
                Formal.T f = (Formal.T) var.formal;
                Params.Layout lay = Params.layout(f);
                params.put(var, lay.loc(slot, f));
                slot += lay.slots();
            }
            enterFrame();
            for (Variable.T var : formals) {
                Formal.T f = (Formal.T) var.formal;
                Loc p = params.get(var);
                // open arrays passed by value are copied by the callee
                if (f.mode == Formal.Mode.mVALUE && p instanceof Loc.Arr a && a.isOpen()) {
                    p = copyArr(a);
                }
                if (frames.isCaptured(var)) {
                    Loc fl = frameLoc(var, proc);
                    moveInto(p, fl, var);
                    locals.put(var, fl);
                } else if (frames.addrTaken.contains(var) && f.mode == Formal.Mode.mVALUE
                           && Frames.isScalar(JType.of(var.type))) {
                    Loc cell = newCell(JType.of(var.type));
                    cell.storePre(cb);
                    p.load(cb);
                    cell.storePost(cb);
                    locals.put(var, cell);
                } else {
                    locals.put(var, p);
                }
            }
            // the formals are named where they have their final location (a
            // copy, a cell), not in the slots they are passed in
            Label formalsStart = here();
            for (Variable.T var : formals) {
                debugName(id(var.name), locals.get(var), formalsStart);
            }
            // an exception that leaves the procedure must be in its RAISES clause
            List<ClassDesc> allowed = raisesGuard();
            Label guardStart = cb.newLabel(), guardEnd = cb.newLabel();
            cb.labelBinding(guardStart);
            // the locals of the procedure's own scope
            declareLocals(proc.syms, true);
            unit.nestedProcedures(proc.syms, proc);
            stmts(proc.block);
            // falling off the end
            line(proc.end_origin);
            if (result.kind == JType.Kind.VOID) {
                cb.return_();
            } else if (Host.doReturnChk) {
                runtimeError(6);
            } else {
                defaultValue(result);
                cb.return_(result.typeKind());
            }
            cb.labelBinding(guardEnd);
            if (allowed != null) {
                Label handler = cb.newLabel(), pass = cb.newLabel();
                cb.labelBinding(handler);
                int ex = cb.allocateLocal(TypeKind.REFERENCE);
                cb.astore(ex);
                cb.aload(ex);
                cb.instanceOf(ClassDesc.of("m3.i.RuntimeError$E"));
                cb.ifne(pass);
                cb.aload(ex);
                cb.instanceOf(ClassDesc.of("m3.i.Java$Exception"));
                cb.ifne(pass);
                for (ClassDesc c : allowed) {
                    cb.aload(ex);
                    cb.instanceOf(c);
                    cb.ifne(pass);
                }
                cb.aload(ex);
                cb.invokestatic(RT, "blocked", MethodTypeDesc.of(CD_RuntimeException, CD_M3Exception));
                cb.athrow();
                cb.labelBinding(pass);
                cb.aload(ex);
                cb.athrow();
                cb.exceptionCatch(guardStart, guardEnd, handler, CD_M3Exception);
            }
            closeScope(cb.endLabel());
        } finally {
            Scope.pop(zz);
        }
    }

    /**
     * The classes of the exceptions in the RAISES clause of this procedure if
     * its code needs a check that no other exception leaves it, else null:
     * for RAISES ANY, and when every RAISE and every call in it can only
     * raise exceptions of the clause (a procedure called checks its own).
     */
    List<ClassDesc> raisesGuard() {
        m3j.front.ESet.T r = ProcType.raises(proc.signature);
        if (m3j.front.ESet.raisesAny(r)) {
            return null;
        }
        java.util.Set<Value.T> ok = new java.util.HashSet<>();
        for (m3j.front.ESet.Elt e = r == null ? null : r.elts; e != null; e = e.next) {
            ok.add(Value.base(e.except));
        }
        boolean[] need = {false};
        java.util.function.Consumer<Expr.T> visit = e -> {
            if (e instanceof CallExpr.T ce && ce.methods == UserProc.Methods) {
                Type.T sig = Frames.callSignature(ce);
                m3j.front.ESet.T cr = sig == null ? null : ProcType.raises(sig);
                if (sig == null || m3j.front.ESet.raisesAny(cr)) {
                    need[0] = true;
                    return;
                }
                for (m3j.front.ESet.Elt x = cr == null ? null : cr.elts; x != null; x = x.next) {
                    if (!ok.contains(Value.base(x.except))) {
                        need[0] = true;
                    }
                }
            }
        };
        Walk.stmts(proc.block, st -> {
            if (st instanceof RaiseStmt.P rs && !ok.contains(Value.base(rs.except))) {
                need[0] = true;
            }
            if (st instanceof BlockStmt.P b && b.scope != null) {
                for (Value.T v = Scope.toList(b.scope); v != null; v = v.next) {
                    if (v instanceof Variable.T var && var.initExpr != null) {
                        Walk.expr(var.initExpr, visit);
                    }
                }
            }
        }, visit);
        for (Value.T v = Scope.toList(proc.syms); v != null; v = v.next) {
            if (v instanceof Variable.T var && var.initExpr != null) {
                Walk.expr(var.initExpr, visit);
            }
        }
        if (!need[0]) {
            return null;
        }
        List<ClassDesc> classes = new ArrayList<>();
        for (Value.T v : ok) {
            if (v instanceof m3j.front.Exceptionz.T x) {
                classes.add(Exceptions.classOf(x));
            }
        }
        return classes;
    }

    /** Generates the initialization of the unit: its variables, then its body. */
    void init(Module.T m) {
        openScope();
        enterFrame();
        // as cm3: constant initial values first (they are static data), then
        // the others in order
        for (Value.T v = Scope.toList(m.localScope); v != null; v = v.next) {
            if (v instanceof Variable.T var && !var.javaBound
                    && (var.initExpr == null || Expr.constValue(var.initExpr) != null)) {
                initVariable(var, loc(var));
            }
        }
        for (Value.T v = Scope.toList(m.localScope); v != null; v = v.next) {
            if (v instanceof Variable.T var && !var.javaBound
                    && var.initExpr != null && Expr.constValue(var.initExpr) == null) {
                initVariable(var, loc(var));
            }
        }
        if (!m.interface_) {
            stmts(m.block);
        }
        cb.return_();
        closeScope(cb.endLabel());
    }

    /** Moves a parameter into its frame location. */
    void moveInto(Loc p, Loc fl, Variable.T var) {
        if (p instanceof Loc.Arr a && fl instanceof Loc.Arr f) {
            f.storage().storePre(cb);
            a.storage().load(cb);
            f.storage().storePost(cb);
            f.offset().storePre(cb);
            a.offset().load(cb);
            f.offset().storePost(cb);
            for (int i = 0; i < a.dims().length; i++) {
                f.dims()[i].storePre(cb);
                a.dims()[i].load(cb);
                f.dims()[i].storePost(cb);
            }
            return;
        }
        if (fl instanceof Loc.VarRef vr && p instanceof Loc.VarRef pr) {
            vr.ref().storePre(cb);
            pr.ref().load(cb);
            vr.ref().storePost(cb);
            return;
        }
        JType t = JType.of(var.type);
        if (fl instanceof Loc.Cell cf && Frames.isScalar(t)) {
            // a cell in the frame
            Loc holder = cf.holder();
            holder.storePre(cb);
            newCellObject(t, p);
            holder.storePost(cb);
            return;
        }
        fl.storePre(cb);
        p.load(cb);
        fl.storePost(cb);
    }

    /** A copy of an open array, at offset 0. */
    Loc.Arr copyArr(Loc.Arr a) {
        Aggregates.Leaf l = Aggregates.leaf(Aggregates.nonOpenElement(a.arrayType()));
        Loc n = arrSlots(a);
        a.storage().load(cb);
        a.offset().load(cb);
        a.offset().load(cb);
        n.load(cb);
        cb.iadd();
        copyOfRange(l);
        Loc s = newLocal(a.type());
        s.store(cb);
        return new Loc.Arr(s, new Loc.ConstInt(0), a.dims(), a.arrayType(), a.type());
    }

    /** Arrays.copyOfRange (records element by element): stack (array, from, to) -> (copy). */
    void copyOfRange(Aggregates.Leaf l) {
        ClassDesc arr = l.arrayDesc();
        if (l.type().kind == JType.Kind.RECORD) {
            cb.invokestatic(RT, "copyRecords", MethodTypeDesc.of(ConstantDescs.CD_Object.arrayType(),
                ConstantDescs.CD_Object.arrayType(), ConstantDescs.CD_int, ConstantDescs.CD_int));
            cb.checkcast(arr);
            return;
        }
        ClassDesc arg = l.store() == Aggregates.Store.A ? ConstantDescs.CD_Object.arrayType() : arr;
        cb.invokestatic(ClassDesc.of("java.util.Arrays"), "copyOfRange",
            MethodTypeDesc.of(arg, arg, ConstantDescs.CD_int, ConstantDescs.CD_int));
        if (!arg.equals(arr)) {
            cb.checkcast(arr);
        }
    }

    // ---------------------------------------------------------- locals

    Loc newLocal(JType t) {
        int slot = cb.allocateLocal(t.typeKind());
        return new Loc.Local(slot, t);
    }

    /** A new local cell for a scalar of type t; its location is the cell's field. */
    Loc newCell(JType t) {
        ClassDesc cc = Refs.cellClass(t);
        Loc holder = newLocal(JType.ref(cc));
        cb.new_(cc);
        cb.dup();
        cb.invokespecial(cc, "<init>", MT_V);
        holder.store(cb);
        return new Loc.Cell(holder, t);
    }

    /** Pushes a new cell holding the value of p. */
    void newCellObject(JType t, Loc p) {
        ClassDesc cc = Refs.cellClass(t);
        cb.new_(cc);
        cb.dup();
        p.load(cb);
        cb.invokespecial(cc, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, Refs.valueDesc(t)));
    }

    /**
     * Declares the variables of a scope, then initializes them in order (an
     * initializer may use a variable declared after it, which has its
     * default value then).
     */
    void declareLocals(Scope.T scope, boolean skipFormals) {
        List<Variable.T> vars = new ArrayList<>();
        for (Value.T v = Scope.toList(scope); v != null; v = v.next) {
            if (v instanceof Variable.T var && !(var.formal != null && skipFormals)) {
                vars.add(var);
            }
        }
        for (Variable.T var : vars) {
            Loc l = declare(var);
            if (var.initExpr != null && !(l instanceof Loc.Arr) && Frames.isScalar(l.type())) {
                l.storePre(cb);
                defaultValue(l.type());
                l.storePost(cb);
            }
        }
        for (Variable.T var : vars) {
            initVariable(var, locals.get(var));
        }
        Label start = here();
        for (Variable.T var : vars) {
            debugName(id(var.name), locals.get(var), start);
        }
    }

    // ---------------------------------------------------- debug names

    /** A slot named in the LocalVariableTable, from start to the end of its scope. */
    private record DebugVar(int slot, String name, ClassDesc desc, Label start) {}

    /** the slots named in the scopes that are open, innermost first */
    private final Deque<List<DebugVar>> debugScopes = new ArrayDeque<>();

    /** A label bound at the current position. */
    Label here() {
        return cb.newBoundLabel();
    }

    /** Opens a scope of variables for the LocalVariableTable. */
    void openScope() {
        debugScopes.push(new ArrayList<>());
    }

    /** Closes the innermost scope at end: the slots named in it go into the LocalVariableTable. */
    void closeScope(Label end) {
        for (DebugVar v : debugScopes.pop()) {
            cb.localVariable(v.slot(), v.name(), v.desc(), v.start(), end);
        }
    }

    /** Closes the innermost scope here. */
    void closeScope() {
        if (debugScopes.peek().isEmpty()) {
            debugScopes.pop();
        } else {
            closeScope(here());
        }
    }

    /**
     * Names the slots of location l in the innermost scope, from start to
     * its end: the slot of a local, the slot of the cell or the reference
     * object of a scalar, the storage of an array, with its offset and its
     * open dimensions as name$off and name$n0, name$n1, ... (as in a frame).
     * A location that is not in a slot, such as the field of a frame, is not
     * named; nor is anything outside of a scope, nor a variable of the front
     * end whose name is no identifier (_result).
     */
    void debugName(String name, Loc l, Label start) {
        List<DebugVar> scope = debugScopes.peek();
        if (scope == null || name.startsWith("_")) {
            return;
        }
        switch (l) {
            case Loc.Local x -> scope.add(new DebugVar(x.slot(), name, x.type().desc, start));
            case Loc.Cell c -> debugName(name, c.holder(), start);
            case Loc.VarRef r -> debugName(name, r.ref(), start);
            case Loc.Arr a -> {
                debugName(name, a.storage(), start);
                debugName(name + "$off", a.offset(), start);
                for (int i = 0; i < a.dims().length; i++) {
                    debugName(name + "$n" + i, a.dims()[i], start);
                }
            }
            default -> {
            }
        }
    }

    /** Allocates the location of a local variable. */
    Loc declare(Variable.T var) {
        JType t = varType(var);
        Loc l;
        if (frames.isCaptured(var)) {
            l = frameLoc(var, proc);
            if (l instanceof Loc.Cell cf && Frames.isScalar(t)) {
                Loc holder = cf.holder();
                holder.storePre(cb);
                ClassDesc cc = Refs.cellClass(t);
                cb.new_(cc);
                cb.dup();
                cb.invokespecial(cc, "<init>", MT_V);
                holder.storePost(cb);
            }
        } else if (frames.addrTaken.contains(var) && Frames.isScalar(t)) {
            l = newCell(t);
        } else if (t.kind == JType.Kind.ARRAY || t.kind == JType.Kind.BIGSET) {
            l = new Loc.Arr(newLocal(t), new Loc.ConstInt(0), new Loc[0], var.type, t);
        } else {
            l = newLocal(t);
        }
        locals.put(var, l);
        return l;
    }

    /** The representation of a variable. */
    static JType varType(Variable.T var) {
        return JType.of(var.type);
    }

    /** True if creating a value of type t must set default values of record fields. */
    static boolean hasDefaults(Type.T t) {
        Type.T u = Aggregates.strip(t);
        switch (u.info.class_) {
            case Record -> {
                for (m3j.front.Field.T f : Types.fields(u)) {
                    if (f.dfault != null || hasDefaults(f.tipe)) {
                        return true;
                    }
                }
                return false;
            }
            case Array, OpenArray -> {
                return hasDefaults(arrayElement(u));
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Sets the default values of the fields of the record that rec loads,
     * of type t (the declared type: structurally equal record types share
     * their class but not their defaults).
     */
    void applyDefaults(Loc rec, Type.T t) {
        Type.T u = Aggregates.strip(t);
        ClassDesc cd = Types.classOf(u);
        for (m3j.front.Field.T f : Types.fields(u)) {
            Loc l = fieldLoc(rec, cd, f);
            if (f.dfault != null) {
                if (l instanceof Loc.Arr a) {
                    assignArr(a, f.dfault);
                } else {
                    assignTo(l, f.dfault);
                }
            } else if (hasDefaults(f.tipe)) {
                if (l instanceof Loc.Arr a) {
                    applyArrayDefaults(a);
                } else {
                    Loc r = newLocal(l.type());
                    l.load(cb);
                    r.store(cb);
                    applyDefaults(r, f.tipe);
                }
            }
        }
    }

    /** Sets the defaults of the records in an array (whose leaves are records). */
    void applyArrayDefaults(Loc.Arr a) {
        Aggregates.Leaf l = leafOf(a);
        if (l.type().kind != JType.Kind.RECORD || !hasDefaults(l.leafType())) {
            return;
        }
        Loc n = arrSlots(a);
        Loc i = newLocal(JType.INT);
        Label top = cb.newLabel(), test = cb.newLabel();
        cb.iconst_0();
        i.store(cb);
        cb.goto_(test);
        cb.labelBinding(top);
        Loc r = newLocal(l.type());
        a.storage().load(cb);
        a.offset().load(cb);
        i.load(cb);
        cb.iadd();
        cb.aaload();
        cb.checkcast(l.type().desc);
        r.store(cb);
        applyDefaults(r, l.leafType());
        cb.iinc(((Loc.Local) i).slot(), 1);
        cb.labelBinding(test);
        i.load(cb);
        n.load(cb);
        cb.if_icmplt(top);
    }

    /** Stores the initial value of a variable (owned values are created first). */
    void initVariable(Variable.T var, Loc l) {
        JType t = l.type();
        if (l instanceof Loc.Arr a) {
            a.storage().storePre(cb);
            Aggregates.newValue(cb, var.type);
            a.storage().storePost(cb);
            if (var.initExpr != null) {
                line(var.origin);
                assignArr(a, var.initExpr);
            } else if (hasDefaults(var.type)) {
                applyArrayDefaults(a);
            }
            return;
        }
        if (t.kind == JType.Kind.RECORD) {
            l.storePre(cb);
            Aggregates.newValue(cb, var.type);
            l.storePost(cb);
            if (var.initExpr != null) {
                line(var.origin);
                assignTo(l, var.initExpr);
            } else if (hasDefaults(var.type)) {
                Loc r = l;
                if (!(l instanceof Loc.Local)) {
                    r = newLocal(t);
                    l.load(cb);
                    r.store(cb);
                }
                applyDefaults(r, var.type);
            }
            return;
        }
        l.storePre(cb);
        if (var.initExpr != null) {
            line(var.origin);
            if (t.kind == JType.Kind.PROC) {
                procAssignValue(var.initExpr, t);
            } else {
                exprAs(var.initExpr, t, true);
            }
        } else {
            defaultValue(t);
        }
        l.storePost(cb);
    }

    /** Pushes the value a variable without an initializer starts with. */
    void defaultValue(JType t) {
        pushDefault(cb, t);
    }

    static void pushDefault(CodeBuilder cb, JType t) {
        switch (t.typeKind()) {
            case INT -> cb.loadConstant(t.min > 0 || t.max < 0 ? (int) t.min : 0);
            case LONG -> cb.loadConstant(t.min > 0 || t.max < 0 ? t.min : 0L);
            case FLOAT -> cb.loadConstant(0.0f);
            case DOUBLE -> cb.loadConstant(0.0d);
            case VOID -> {}
            default -> cb.aconst_null();
        }
    }

    /** The location of a variable, in this method, in a frame, or global. */
    Loc loc(Variable.T var) {
        Loc l = locals.get(var);
        if (l != null) {
            return l;
        }
        if (isGlobal(var)) {
            if (var.javaBound) {
                return Java.fieldLoc(this, var);
            }
            Value.T home = Scope.toUnit(var);
            if (home instanceof Module.T m) {
                JType t = varType(var);
                Loc s = new Loc.Static(Unit.classDesc(m), id(var.name), t);
                if (t.kind == JType.Kind.ARRAY || t.kind == JType.Kind.BIGSET) {
                    return new Loc.Arr(s, new Loc.ConstInt(0), new Loc[0], var.type, t);
                }
                return s;
            }
            return null;
        }
        Procedure.T owner = frames.owner(var);
        if (owner != null && (owner != proc || chunk != null) && frames.isCaptured(var)) {
            return frameLoc(var, owner);
        }
        return null;
    }

    static boolean isGlobal(Variable.T var) {
        return var.scope != null && var.scope.module;
    }

    // ----------------------------------------------------------- statements

    void stmts(Stmt.T s) {
        while (s != null) {
            Split.Chunk c = split == null ? null : split.chunks.get(s);
            if (c != null && c != chunk) {
                callChunk(c);
                for (int i = 0; i < c.count; i++) {
                    s = s.next;
                }
            } else {
                stmt(s);
                s = s.next;
            }
        }
    }

    // --------------------------------------------------------------- chunks

    /** The descriptor of the methods of the chunks of this procedure. */
    MethodTypeDesc chunkDescriptor() {
        return frames.framed(proc)
            ? MethodTypeDesc.of(ConstantDescs.CD_int, frames.frameClassOf(proc))
            : MethodTypeDesc.of(ConstantDescs.CD_int);
    }

    /** Calls the method of chunk c and does what its result says. */
    void callChunk(Split.Chunk c) {
        if (frames.framed(proc)) {
            myFrame.load(cb);
        }
        cb.invokestatic(unit.self, unit.chunkName(proc, c), chunkDescriptor());
        if (!c.returns && !c.exits) {
            cb.pop();
            return;
        }
        Loc r = newLocal(JType.INT);
        r.store(cb);
        Label done = cb.newLabel();
        r.load(cb);
        cb.ifeq(done);
        if (c.returns && c.exits) {
            Label ret = cb.newLabel();
            r.load(cb);
            cb.iconst_1();
            cb.if_icmpeq(ret);
            exit();
            cb.labelBinding(ret);
            returnFromFrame();
        } else if (c.returns) {
            returnFromFrame();
        } else {
            exit();
        }
        cb.labelBinding(done);
    }

    /** RETURN with the value a chunk left in the frame. */
    void returnFromFrame() {
        List<Exceptions.Guard> left = new ArrayList<>();
        if (leaveAll(left)) {
            // caught by a TRY EXCEPT ELSE
        } else if (chunk != null) {
            cb.iconst_1();
            cb.ireturn();
        } else if (result.kind == JType.Kind.VOID) {
            cb.return_();
        } else {
            ClassDesc fc = frames.frameClassOf(proc);
            myFrame.load(cb);
            cb.getfield(fc, "$result", result.desc);
            cb.return_(result.typeKind());
        }
        reopen(left);
    }

    /** The method of chunk c: its statements, with the frame of the procedure as parameter. */
    void chunkBody(Split.Chunk c) {
        chunk = c;
        Scope.T zz = proc == Frames.INIT ? null : Scope.push(proc.syms);
        openScope();
        try {
            if (frames.framed(proc)) {
                ClassDesc fc = frames.frameClassOf(proc);
                myFrame = new Loc.Local(cb.parameterSlot(0), JType.ref(fc));
                debugName("$frame", myFrame, cb.startLabel());
                Procedure.T link = frames.linkOf(proc);
                if (link != null) {
                    staticLink = new Loc.Field(myFrame, fc, "up", JType.ref(frames.frameClassOf(link)));
                }
            }
            unwind.push(CHUNK_END);
            Stmt.T s = c.first;
            int i = 0;
            while (i < c.count) {
                // a smaller chunk of the same list, later in this one
                Split.Chunk inner = i == 0 ? null : split.chunks.get(s);
                if (inner != null) {
                    callChunk(inner);
                    for (int k = 0; k < inner.count; k++, i++) {
                        s = s.next;
                    }
                } else {
                    stmt(s);
                    s = s.next;
                    i++;
                }
            }
            cb.iconst_0();
            cb.ireturn();
            closeScope(cb.endLabel());
        } finally {
            if (zz != null) {
                Scope.pop(zz);
            }
        }
    }

    /** true in a loop moved out of the body of the unit (it is not moved again) */
    boolean outlined;

    /**
     * True if loop s in the body of the unit can be a method of its own: it
     * uses no variable of the body that is declared outside it (the body
     * runs once, so the JIT could compile a loop in it only by on-stack
     * replacement, without a profile).
     */
    boolean outlinable(Stmt.T s) {
        if (proc != Frames.INIT || outlined || frames.framed(Frames.INIT)) {
            return false;
        }
        if (!(s instanceof ForStmt.P || s instanceof WhileStmt.P || s instanceof RepeatStmt.P
              || s instanceof LoopStmt.P)) {
            return false;
        }
        boolean[] ok = {true};
        Walk.stmt(s, x -> {}, e -> {
            Value.T v = null;
            int[] name = new int[1];
            Value.T[] obj = new Value.T[1];
            if (e instanceof NamedExpr.P && NamedExpr.split(e, name, obj)) {
                v = Value.base(obj[0]);
            } else if (e instanceof VarExpr.P ve) {
                v = ve.v;
            }
            if (v instanceof Variable.T var && locals.containsKey(var)) {
                ok[0] = false;
            }
        });
        return ok[0];
    }

    void stmt(Stmt.T s) {
        line(s.origin);
        if (outlinable(s)) {
            cb.invokestatic(unit.self, unit.outline(s), MT_V);
            return;
        }
        switch (s) {
            case AssignStmt.P p -> assign(p);
            case CallStmt.P p -> pop(expr(p.e));
            case EvalStmt.P p -> {
                JType t = JType.of(Expr.typeOf(p.e));
                if (t.kind == JType.Kind.ARRAY || t.kind == JType.Kind.OPENARRAY) {
                    arr(p.e);
                } else {
                    pop(expr(p.e));
                }
            }
            case BlockStmt.P p -> block(p);
            case IfStmt.P p -> ifStmt(p);
            case WhileStmt.P p -> {
                Label top = cb.newLabel(), test = cb.newLabel(), exit = cb.newLabel();
                cb.goto_(test);
                cb.labelBinding(top);
                unwind.push(exit);
                stmts(p.body);
                unwind.pop();
                cb.labelBinding(test);
                line(p.origin);
                branch(p.cond, top, true);
                cb.labelBinding(exit);
            }
            case RepeatStmt.P p -> {
                Label top = cb.newLabel(), exit = cb.newLabel();
                cb.labelBinding(top);
                unwind.push(exit);
                stmts(p.body);
                unwind.pop();
                line(p.e_origin);
                branch(p.expr, top, false);
                cb.labelBinding(exit);
            }
            case LoopStmt.P p -> {
                Label top = cb.newLabel(), exit = cb.newLabel();
                cb.labelBinding(top);
                unwind.push(exit);
                stmts(p.body);
                unwind.pop();
                cb.goto_(top);
                cb.labelBinding(exit);
            }
            case ExitStmt.P p -> exit();
            case ForStmt.P p -> forStmt(p);
            case ReturnStmt.P p -> returnStmt(p);
            case CaseStmt.P p -> caseStmt(p);
            case AssertStmt.P p -> assertStmt(p);
            case WithStmt.P p -> withStmt(p);
            case RaiseStmt.P p -> raise(p);
            case TryStmt.P p -> Exceptions.tryExcept(this, p);
            case TryFinStmt.P p -> Exceptions.tryFinally(this, p);
            case TypeCaseStmt.P p -> Exceptions.typeCase(this, p);
            case m3j.front.LockStmt.P p -> {
                expr(p.mutex);
                nilCheck();
                cb.checkcast(Types.CD_Mutex);
                Loc m = newLocal(JType.ref(Types.CD_Mutex));
                m.store(cb);
                m.load(cb);
                cb.invokevirtual(Types.CD_Mutex, "acquire", MT_V);
                Exceptions.protect(this, () -> stmts(p.body), () -> {
                    m.load(cb);
                    cb.invokevirtual(Types.CD_Mutex, "release", MT_V);
                });
            }
            default -> unsupported("statement " + s.getClass().getName());
        }
    }

    void pop(JType t) {
        switch (t.typeKind()) {
            case VOID -> {}
            case LONG, DOUBLE -> cb.pop2();
            default -> cb.pop();
        }
    }

    /** Runs a code generator with the unwind stack as it is, and restores it after. */
    private void keepUnwind(Runnable r) {
        List<Object> saved = new ArrayList<>(unwind);
        try {
            r.run();
        } finally {
            unwind.clear();
            unwind.addAll(saved);
        }
    }

    /** EXIT: runs the FINALLY parts up to the innermost loop, then leaves it. */
    void exit() {
        List<Exceptions.Guard> left = new ArrayList<>();
        keepUnwind(() -> {
            while (!unwind.isEmpty()) {
                Object o = unwind.peek();
                if (o instanceof Label l) {
                    cb.goto_(l);
                    return;
                }
                if (o == CHUNK_END) {
                    // the loop is around the call of this chunk
                    cb.iconst_2();
                    cb.ireturn();
                    return;
                }
                unwind.pop();
                if (leave((Exceptions.Guard) o, left)) {
                    return;
                }
            }
        });
        reopen(left);
    }

    /**
     * Leaves the TRY of g for a jump: ends its protected range and runs its
     * FINALLY part (see Exceptions.Guard). The ELSE handler of a TRY EXCEPT
     * ELSE catches the jump (the language defines EXIT and RETURN as
     * exceptions): then this jumps to the handler and returns true.
     */
    private boolean leave(Exceptions.Guard g, List<Exceptions.Guard> left) {
        g.close(this);
        left.add(g);
        if (g instanceof Exceptions.Finally f) {
            f.fin.run();
        } else if (g.elseEntry != null) {
            cb.goto_(g.elseEntry);
            return true;
        }
        return false;
    }

    /**
     * Leaves the TRY statements of the method (of the chunk) for a RETURN;
     * returns true if a TRY EXCEPT ELSE caught it.
     */
    private boolean leaveAll(List<Exceptions.Guard> left) {
        boolean[] caught = {false};
        keepUnwind(() -> {
            while (!unwind.isEmpty() && unwind.peek() != CHUNK_END) {
                if (unwind.pop() instanceof Exceptions.Guard g && leave(g, left)) {
                    caught[0] = true;
                    return;
                }
            }
        });
        return caught[0];
    }

    /** Protects the code after a jump again by the TRY statements that it left. */
    private void reopen(List<Exceptions.Guard> left) {
        for (Exceptions.Guard g : left) {
            g.reopen(this);
        }
    }

    void block(BlockStmt.P p) {
        if (p.scope == null) {
            stmts(p.body);
            return;
        }
        Scope.T zz = Scope.push(p.scope);
        openScope();
        try {
            declareLocals(p.scope, false);
            unit.nestedProcedures(p.scope, proc);
            stmts(p.body);
            closeScope();
        } finally {
            Scope.pop(zz);
        }
    }

    void ifStmt(IfStmt.P p) {
        Label end = cb.newLabel();
        for (IfStmt.Clause c = p.clauses; c != null; c = c.next) {
            Label next = cb.newLabel();
            line(c.origin);
            branch(c.cond, next, false);
            stmts(c.body);
            cb.goto_(end);
            cb.labelBinding(next);
        }
        stmts(p.elseBody);
        cb.labelBinding(end);
    }

    void returnStmt(ReturnStmt.P p) {
        Loc value = null;
        if (p.expr != null) {
            value = newLocal(result);
            if (result.kind == JType.Kind.RECORD) {
                expr(p.expr);
                if (!isFresh(p.expr)) {
                    copyRecord(Expr.typeOf(p.expr));
                }
            } else if (result.kind == JType.Kind.ARRAY || result.kind == JType.Kind.BIGSET) {
                Loc.Arr a = arr(p.expr);
                Loc.Arr r = freshArr(ProcType.result(Type.base(proc.signature)));
                copyArr(a, r);
                r.storage().load(cb);
            } else {
                exprAs(p.expr, result, true);
            }
            value.store(cb);
        }
        // the FINALLY parts
        List<Exceptions.Guard> left = new ArrayList<>();
        if (leaveAll(left)) {
            // caught by a TRY EXCEPT ELSE
        } else if (chunk != null) {
            // the value goes to the frame; the caller returns it
            if (value != null) {
                myFrame.load(cb);
                value.load(cb);
                cb.putfield(frames.frameClassOf(proc), "$result", result.desc);
            }
            cb.iconst_1();
            cb.ireturn();
        } else if (value == null) {
            cb.return_();
        } else {
            value.load(cb);
            cb.return_(result.typeKind());
        }
        reopen(left);
    }

    void assertStmt(AssertStmt.P p) {
        if (!Host.doAsserts) {
            return;
        }
        Expr.T c = Expr.constValue(p.cond);
        if (c != null && isTrue(c)) {
            return;
        }
        Label ok = cb.newLabel();
        branch(p.cond, ok, true);
        if (p.msg != null) {
            expr(p.msg);
        } else {
            cb.aconst_null();
        }
        cb.invokestatic(RT, "assertFailed", MethodTypeDesc.of(CD_RuntimeException, JType.CD_String));
        cb.athrow();
        cb.labelBinding(ok);
    }

    /** Throws the runtime error with the given code (RuntimeError.T). */
    void runtimeError(int code) {
        cb.loadConstant(code);
        cb.invokestatic(RT, "error", MethodTypeDesc.of(CD_RuntimeException, ConstantDescs.CD_int));
        cb.athrow();
    }

    void raise(RaiseStmt.P p) {
        Exceptionz.T e = (Exceptionz.T) Value.base(p.except);
        ClassDesc ec = Exceptions.classOf(e);
        cb.new_(ec);
        cb.dup();
        cb.invokespecial(ec, "<init>", MT_V);
        if (p.arg != null && e.tipe != null) {
            cb.dup();
            box(p.arg, e.tipe);
            cb.putfield(CD_M3Exception, "arg", ConstantDescs.CD_Object);
        }
        cb.loadConstant(unit.sourceFile);
        cb.loadConstant(lineOf(p.origin));
        cb.invokevirtual(CD_M3Exception, "at", MethodTypeDesc.of(CD_M3Exception, JType.CD_String,
            ConstantDescs.CD_int));
        cb.athrow();
    }

    /** Pushes the value of e as an Object (boxed scalars, copied structures). */
    void box(Expr.T e, Type.T t) {
        JType jt = JType.of(t);
        switch (jt.kind) {
            case ARRAY, BIGSET -> {
                Loc.Arr a = arr(e);
                Loc.Arr r = freshArr(t);
                copyArr(a, r);
                r.storage().load(cb);
            }
            case RECORD -> {
                expr(e);
                copyRecord(t);
            }
            default -> {
                exprAs(e, jt, true);
                boxValue(jt);
            }
        }
    }

    static final ClassDesc CD_Integer = ClassDesc.of("java.lang.Integer");
    static final ClassDesc CD_LongBox = ClassDesc.of("java.lang.Long");
    static final ClassDesc CD_FloatBox = ClassDesc.of("java.lang.Float");
    static final ClassDesc CD_DoubleBox = ClassDesc.of("java.lang.Double");

    void boxValue(JType jt) {
        switch (jt.typeKind()) {
            case INT -> cb.invokestatic(CD_Integer, "valueOf", MethodTypeDesc.of(CD_Integer, ConstantDescs.CD_int));
            case LONG -> cb.invokestatic(CD_LongBox, "valueOf", MethodTypeDesc.of(CD_LongBox, ConstantDescs.CD_long));
            case FLOAT -> cb.invokestatic(CD_FloatBox, "valueOf", MethodTypeDesc.of(CD_FloatBox, ConstantDescs.CD_float));
            case DOUBLE -> cb.invokestatic(CD_DoubleBox, "valueOf", MethodTypeDesc.of(CD_DoubleBox, ConstantDescs.CD_double));
            default -> {}
        }
    }

    /** Converts the Object on the stack back to a value of representation jt. */
    void unboxValue(JType jt) {
        switch (jt.typeKind()) {
            case INT -> {
                cb.checkcast(CD_Integer);
                cb.invokevirtual(CD_Integer, "intValue", MethodTypeDesc.of(ConstantDescs.CD_int));
            }
            case LONG -> {
                cb.checkcast(CD_LongBox);
                cb.invokevirtual(CD_LongBox, "longValue", MethodTypeDesc.of(ConstantDescs.CD_long));
            }
            case FLOAT -> {
                cb.checkcast(CD_FloatBox);
                cb.invokevirtual(CD_FloatBox, "floatValue", MethodTypeDesc.of(ConstantDescs.CD_float));
            }
            case DOUBLE -> {
                cb.checkcast(CD_DoubleBox);
                cb.invokevirtual(CD_DoubleBox, "doubleValue", MethodTypeDesc.of(ConstantDescs.CD_double));
            }
            default -> {
                if (!jt.desc.equals(ConstantDescs.CD_Object)) {
                    cb.checkcast(jt.desc);
                }
            }
        }
    }

    // ------------------------------------------------------------ assignment

    void assign(AssignStmt.P p) {
        JType t = JType.of(Expr.typeOf(p.lhs));
        if (t.kind == JType.Kind.ARRAY || t.kind == JType.Kind.OPENARRAY || t.kind == JType.Kind.BIGSET) {
            Loc.Arr dst = arr(p.lhs);
            assignArr(dst, p.rhs);
            return;
        }
        Loc l = designator(p.lhs);
        if (l == null) {
            unsupported("assignment to " + p.lhs.getClass().getName());
            return;
        }
        assignTo(l, p.rhs);
    }

    /** As cm3: assigning a nested procedure is a checked runtime error. */
    enum ProcCheck { NONE, FAIL, CONDITIONAL }

    static ProcCheck procCheck(Expr.T e) {
        if (!Host.doNarrowChk) {
            return ProcCheck.NONE;
        }
        int[] name = new int[1];
        Value.T[] obj = new Value.T[1];
        if (!(NamedExpr.split(e, name, obj) || QualifyExpr.split(e, obj) || ProcExpr.split(e, obj))) {
            return ProcCheck.NONE;
        }
        Value.T v = Value.base(obj[0]);
        if (v instanceof Procedure.T p) {
            return Procedure.isNested(p) ? ProcCheck.FAIL : ProcCheck.NONE;
        }
        if (v instanceof Variable.T var && Variable.hasClosure(var)) {
            return ProcCheck.CONDITIONAL;
        }
        return ProcCheck.NONE;
    }

    /** Pushes the value of e for an assignment to a procedure location. */
    void procAssignValue(Expr.T e, JType t) {
        switch (procCheck(e)) {
            case FAIL -> {
                runtimeError(5);
                defaultValue(t);
            }
            case CONDITIONAL -> {
                exprAs(e, t, true);
                cb.invokestatic(RT, "notNested", MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object));
            }
            case NONE -> exprAs(e, t, true);
        }
    }

    /** Assigns the value of e to the location l (not an array). */
    void assignTo(Loc l, Expr.T e) {
        if (l.type().kind == JType.Kind.RECORD) {
            l.load(cb);
            expr(e);
            ClassDesc cd = l.type().desc;
            cb.invokevirtual(cd, "copyFrom", MethodTypeDesc.of(ConstantDescs.CD_void, cd));
            return;
        }
        l.storePre(cb);
        if (l.type().kind == JType.Kind.PROC) {
            procAssignValue(e, l.type());
        } else {
            exprAs(e, l.type(), true);
        }
        l.storePost(cb);
    }

    /** Assigns an array (or large set) expression to an array location. */
    void assignArr(Loc.Arr dst, Expr.T e) {
        if (dst.type().kind == JType.Kind.BIGSET && Constructors.bigSetAssign(this, e, dst)) {
            return;
        }
        if (dst.type().kind == JType.Kind.BIGSET && Constructors.isSetOp(e)) {
            // computed into the destination, without a new set
            Constructors.bigSetOpInto(this, e, dst);
            return;
        }
        Loc.Arr src = arr(e);
        copyArr(src, dst);
    }

    /** Copies src to dst, checking that their shapes agree. */
    void copyArr(Loc.Arr src, Loc.Arr dst) {
        checkShape(src, dst);
        Aggregates.Leaf l = leafOf(dst);
        src.storage().load(cb);
        src.offset().load(cb);
        dst.storage().load(cb);
        dst.offset().load(cb);
        arrSlots(dst).load(cb);
        Aggregates.copyRange(cb, l);
    }

    Aggregates.Leaf leafOf(Loc.Arr a) {
        if (a.type().kind == JType.Kind.BIGSET) {
            return new Aggregates.Leaf(Aggregates.Store.J, ConstantDescs.CD_long, JType.LONG, a.arrayType(),
                Aggregates.setWords(a.arrayType()));
        }
        return Aggregates.leaf(Aggregates.nonOpenElement(a.arrayType()));
    }

    /** Checks that two arrays have the same shape. */
    void checkShape(Loc.Arr a, Loc.Arr b) {
        int depth = Math.max(Aggregates.openDepth(a.arrayType()), Aggregates.openDepth(b.arrayType()));
        if (depth == 0 || !Host.doRangeChk) {
            return;
        }
        for (int i = 0; i < depth; i++) {
            dim(a, i).load(cb);
            dim(b, i).load(cb);
            Label ok = cb.newLabel();
            cb.if_icmpeq(ok);
            runtimeError(3);
            cb.labelBinding(ok);
        }
    }

    /** The i-th dimension of an array: open, or from its fixed type. */
    Loc dim(Loc.Arr a, int i) {
        if (i < a.dims().length) {
            return a.dims()[i];
        }
        Type.T t = Aggregates.strip(a.arrayType());
        for (int j = 0; j < i; j++) {
            t = Aggregates.strip(arrayElement(t));
        }
        return new Loc.ConstInt((int) JType.toLong(Type.number(arrayIndex(t))));
    }

    static Type.T arrayIndex(Type.T t) {
        Type.T[] idx = new Type.T[1], elt = new Type.T[1];
        m3j.front.ArrayType.split(Aggregates.strip(t), idx, elt);
        return idx[0];
    }

    static Type.T arrayElement(Type.T t) {
        Type.T u = Aggregates.strip(t);
        if (u.info.class_ == Type.Class.OpenArray) {
            return Types.openArrayElement(u);
        }
        Type.T[] idx = new Type.T[1], elt = new Type.T[1];
        m3j.front.ArrayType.split(u, idx, elt);
        return elt[0];
    }

    /** A location holding the number of leaves of an array. */
    Loc arrSlots(Loc.Arr a) {
        Type.T t = a.arrayType();
        if (JType.of(t).kind == JType.Kind.BIGSET) {
            return new Loc.ConstInt((int) Aggregates.setWords(t));
        }
        if (!a.isOpen()) {
            return new Loc.ConstInt((int) Aggregates.leaf(t).slots());
        }
        Aggregates.Leaf l = Aggregates.leaf(Aggregates.nonOpenElement(t));
        cb.loadConstant((int) l.slots());
        for (Loc d : a.dims()) {
            d.load(cb);
            cb.imul();
        }
        Loc n = newLocal(JType.INT);
        n.store(cb);
        return n;
    }

    /** A new array of type t (fixed array or large set), in a local. */
    Loc.Arr freshArr(Type.T t) {
        JType jt = JType.of(t);
        Loc s = newLocal(jt);
        Aggregates.newValue(cb, t);
        s.store(cb);
        return new Loc.Arr(s, new Loc.ConstInt(0), new Loc[0], t, jt);
    }

    /** Copies the record on the stack: a fresh object. */
    void copyRecord(Type.T t) {
        ClassDesc cd = Types.classOf(t);
        cb.invokevirtual(cd, "copy", MethodTypeDesc.of(cd));
    }

    /** True if e is a value nobody else refers to (no copy needed). */
    static boolean isFresh(Expr.T e) {
        return e instanceof CallExpr.T ce && ce.methods == UserProc.Methods
            || e instanceof ConsExpr.P || e instanceof RecordExpr.P || e instanceof ArrayExpr.T
            || e instanceof SetExpr.P;
    }

    void forStmt(ForStmt.P p) {
        Scope.T zz = Scope.push(p.scope);
        try {
            Variable.T var = p.var_;
            JType vt = JType.of(var.type);
            Long step = constLong(p.step);
            // an int counter where no value of the loop (the last increment
            // included) leaves the int range: a counted loop for the JIT
            boolean isLong = vt.typeKind() == TypeKind.LONG && !intLoop(p, step);
            JType ct = isLong ? JType.LONG : JType.INT;
            Loc counter = newLocal(ct);
            exprAs(p.from, ct, false);
            counter.store(cb);
            Loc limit = newLocal(ct);
            exprAs(p.limit, ct, false);
            limit.store(cb);
            Loc by = null;
            if (step == null) {
                by = newLocal(ct);
                exprAs(p.step, ct, false);
                by.store(cb);
            }
            Label top = cb.newLabel(), test = cb.newLabel(), exit = cb.newLabel();
            cb.goto_(test);
            cb.labelBinding(top);
            if (frames.isCaptured(var)) {
                Loc fl = frameLoc(var, proc);
                fl.storePre(cb);
                counter.load(cb);
                widen(ct.typeKind(), fl.type().typeKind());
                fl.storePost(cb);
                locals.put(var, fl);
            } else {
                locals.put(var, counter);
            }
            openScope();
            debugName(id(var.name), locals.get(var), top);
            unwind.push(exit);
            stmts(p.body);
            unwind.pop();
            line(p.origin);
            counter.load(cb);
            if (step != null) {
                loadConst(isLong, step);
            } else {
                by.load(cb);
            }
            if (isLong) {
                cb.ladd();
            } else {
                cb.iadd();
            }
            counter.store(cb);
            cb.labelBinding(test);
            if (step != null) {
                counter.load(cb);
                limit.load(cb);
                compareJump(isLong, step >= 0 ? CmpOp.LE : CmpOp.GE, top);
            } else {
                Label neg = cb.newLabel();
                by.load(cb);
                if (isLong) {
                    cb.lconst_0();
                    cb.lcmp();
                }
                cb.iflt(neg);
                counter.load(cb);
                limit.load(cb);
                compareJump(isLong, CmpOp.LE, top);
                cb.goto_(exit);
                cb.labelBinding(neg);
                counter.load(cb);
                limit.load(cb);
                compareJump(isLong, CmpOp.GE, top);
            }
            cb.labelBinding(exit);
            closeScope(exit);
        } finally {
            Scope.pop(zz);
        }
    }

    /** True if a FOR loop over INTEGER can count in int (see forStmt). */
    boolean intLoop(ForStmt.P p, Long step) {
        if (step == null || step == 0 || Math.abs(step) > 1 << 20) {
            return false;
        }
        long[] f = bounds(p.from), l = bounds(p.limit);
        if (f == null || l == null) {
            return false;
        }
        // (without overflow at the ends of long, such as for FOR i := LAST(INTEGER) ...)
        long s = Math.abs(step);
        return Math.min(f[0], l[0]) >= Integer.MIN_VALUE + s && Math.max(f[1], l[1]) <= Integer.MAX_VALUE - s;
    }

    /** The static bounds of an integer expression, or null if unknown or not in long. */
    static long[] bounds(Expr.T e) {
        int[] min = TInt.newInt(), max = TInt.newInt();
        Expr.getBounds(e, min, max);
        long[] lo = new long[1], hi = new long[1];
        if (TInt.LT(max, min) || !TInt.toInt(min, lo) || !TInt.toInt(max, hi)) {
            return null;
        }
        return new long[] {lo[0], hi[0]};
    }

    void withStmt(WithStmt.P p) {
        Scope.T zz = Scope.push(p.scope);
        try {
            Variable.T var = p.var_;
            JType t = JType.of(var.type);
            Loc l;
            if (t.kind == JType.Kind.ARRAY || t.kind == JType.Kind.OPENARRAY || t.kind == JType.Kind.BIGSET) {
                l = arr(p.expr);
            } else if (Expr.isDesignator(p.expr) && !(p.expr instanceof CallExpr.T)
                       && designator(p.expr) instanceof Loc d) {
                l = d;
            } else {
                l = newLocal(t);
                if (t.kind == JType.Kind.RECORD) {
                    expr(p.expr);
                    if (!isFresh(p.expr)) {
                        copyRecord(var.type);
                    }
                } else {
                    exprAs(p.expr, t, true);
                }
                l.store(cb);
            }
            if (frames.isCaptured(var) && !(l instanceof Loc.Arr)) {
                Loc fl = frameLoc(var, proc);
                if (fl instanceof Loc.VarRef r) {
                    // a reference to the variable (see Frames.withRef)
                    r.ref().storePre(cb);
                    if (l.type().kind != t.kind) {
                        // an int FOR counter of an INTEGER: a copy (it cannot change)
                        ClassDesc cc = Refs.cellClass(t);
                        cb.new_(cc);
                        cb.dup();
                        l.load(cb);
                        widen(l.type().typeKind(), t.typeKind());
                        cb.invokespecial(cc, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, Refs.valueDesc(t)));
                    } else {
                        Refs.makeRef(this, l);
                    }
                    r.ref().storePost(cb);
                } else {
                    fl.storePre(cb);
                    l.load(cb);
                    fl.storePost(cb);
                }
                l = fl;
            }
            locals.put(var, l);
            openScope();
            debugName(id(var.name), l, here());
            stmts(p.body);
            closeScope();
        } finally {
            Scope.pop(zz);
        }
    }

    enum CmpOp { EQ, NE, LT, LE, GT, GE }

    static CmpOp negate(CmpOp op) {
        return switch (op) {
            case EQ -> CmpOp.NE;
            case NE -> CmpOp.EQ;
            case LT -> CmpOp.GE;
            case LE -> CmpOp.GT;
            case GT -> CmpOp.LE;
            case GE -> CmpOp.LT;
        };
    }

    /** Compares the two ints or longs on the stack and jumps if op holds. */
    void compareJump(boolean isLong, CmpOp op, Label target) {
        if (isLong) {
            cb.lcmp();
            switch (op) {
                case EQ -> cb.ifeq(target);
                case NE -> cb.ifne(target);
                case LT -> cb.iflt(target);
                case LE -> cb.ifle(target);
                case GT -> cb.ifgt(target);
                case GE -> cb.ifge(target);
            }
        } else {
            switch (op) {
                case EQ -> cb.if_icmpeq(target);
                case NE -> cb.if_icmpne(target);
                case LT -> cb.if_icmplt(target);
                case LE -> cb.if_icmple(target);
                case GT -> cb.if_icmpgt(target);
                case GE -> cb.if_icmpge(target);
            }
        }
    }

    void caseStmt(CaseStmt.P p) {
        JType t = expr(p.expr);
        boolean isLong = t.typeKind() == TypeKind.LONG;
        Loc sel = newLocal(isLong ? JType.LONG : JType.INT);
        sel.store(cb);
        Label end = cb.newLabel();
        Label[] bodies = new Label[(int) p.nCases];
        for (int i = 0; i < bodies.length; i++) {
            bodies[i] = cb.newLabel();
        }
        List<CaseStmt.Tree> ranges = new ArrayList<>();
        collect(p.tree, ranges);
        Label dflt = cb.newLabel();
        if (!isLong && dense(ranges)) {
            List<SwitchCase> cases = new ArrayList<>();
            for (CaseStmt.Tree r : ranges) {
                long lo = JType.toLong(r.min), hi = JType.toLong(r.max);
                for (long v = lo; v <= hi; v++) {
                    cases.add(SwitchCase.of((int) v, bodies[(int) r.body]));
                }
            }
            sel.load(cb);
            cb.lookupswitch(dflt, cases);
        } else {
            for (CaseStmt.Tree r : ranges) {
                long lo = JType.toLong(r.min), hi = JType.toLong(r.max);
                Label next = cb.newLabel();
                sel.load(cb);
                loadConst(isLong, lo);
                if (lo == hi) {
                    compareJump(isLong, CmpOp.EQ, bodies[(int) r.body]);
                } else {
                    compareJump(isLong, CmpOp.LT, next);
                    sel.load(cb);
                    loadConst(isLong, hi);
                    compareJump(isLong, CmpOp.LE, bodies[(int) r.body]);
                }
                cb.labelBinding(next);
            }
            cb.goto_(dflt);
        }
        for (int i = 0; i < bodies.length; i++) {
            cb.labelBinding(bodies[i]);
            stmts(p.bodies[i]);
            cb.goto_(end);
        }
        cb.labelBinding(dflt);
        if (p.hasElse) {
            stmts(p.elseBody);
        } else if (Host.doCaseChk) {
            // as cm3: the error is reported at the line of the last
            // statement compiled (of the last case)
            runtimeError(7);
        }
        cb.labelBinding(end);
    }

    /**
     * True if = on arrays of types a and b compares elements of a REAL type
     * as numbers. As cm3 does: fixed arrays (without holes) compare as their
     * bits; if one is open, the elements compare one by one (those of the
     * fixed one, else those that are not open arrays), and an element of a
     * REAL type compares as a number.
     */
    static boolean numericElements(Type.T a, Type.T b) {
        int da = Aggregates.openDepth(a), db = Aggregates.openDepth(b);
        if (da == 0 && db == 0) {
            return false;
        }
        Type.T e;
        if (da > 0 && db > 0) {
            e = m3j.front.OpenArrayType.nonopenEltType(da <= db ? a : b);
        } else {
            Type.T[] index = new Type.T[1], elt = new Type.T[1];
            if (!m3j.front.ArrayType.split(da == 0 ? a : b, index, elt)) {
                return false;
            }
            e = elt[0];
        }
        TypeKind k = JType.of(e).typeKind();
        return k == TypeKind.FLOAT || k == TypeKind.DOUBLE;
    }

    private static boolean dense(List<CaseStmt.Tree> ranges) {
        long n = 0;
        for (CaseStmt.Tree r : ranges) {
            long lo = JType.toLong(r.min), hi = JType.toLong(r.max);
            if (lo < Integer.MIN_VALUE || hi > Integer.MAX_VALUE) {
                return false;
            }
            n += hi - lo + 1;
            if (n > 1024) {
                return false;
            }
        }
        return true;
    }

    /**
     * The labels of a CASE: the code generation of the front end has made
     * the tree a list sorted along greater, with adjacent ranges of the same
     * body merged.
     */
    private static void collect(CaseStmt.Tree t, List<CaseStmt.Tree> out) {
        for (; t != null; t = t.greater) {
            out.add(t);
        }
    }

    void loadConst(boolean isLong, long v) {
        if (isLong) {
            cb.loadConstant(v);
        } else {
            cb.loadConstant((int) v);
        }
    }

    // ----------------------------------------------------------- designators

    /** The location that a designator (not an array) denotes, or null. */
    Loc designator(Expr.T e) {
        switch (e) {
            case NamedExpr.P n -> {
                int[] name = new int[1];
                Value.T[] obj = new Value.T[1];
                NamedExpr.split(n, name, obj);
                return valueLoc(obj[0]);
            }
            case VarExpr.P v -> {
                return valueLoc(v.v);
            }
            case QualifyExpr.P q -> {
                switch (q.class_) {
                    case importDecl -> {
                        return valueLoc(q.rhsValue);
                    }
                    case recField -> {
                        Loc rec = recordOf(q.lhsExpr);
                        m3j.front.Field.T f = (m3j.front.Field.T) q.rhsValue;
                        return fieldLoc(rec, Types.classOf(Expr.typeOf(q.lhsExpr)), f);
                    }
                    case objField -> {
                        m3j.front.Field.T f = (m3j.front.Field.T) q.rhsValue;
                        ClassDesc oc = Types.classOf(q.holder);
                        Loc obj = temp(oc, () -> refBase(q.lhsExpr, oc));
                        return objectFieldLoc(obj, q.holder, f);
                    }
                    default -> {
                        return null;
                    }
                }
            }
            case SubscriptExpr.P s -> {
                Loc.Arr a = arr(s.a);
                return element(a, s.b);
            }
            case DerefExpr.P d -> {
                Type.T rt = Types.target(Type.base(Expr.typeOf(d.a)));
                ClassDesc rc = Types.classOf(Expr.typeOf(d.a));
                JType tt = JType.of(rt);
                Loc ref = temp(rc, () -> refBase(d.a, rc));
                if (tt.kind == JType.Kind.RECORD) {
                    // REF R is a subclass of R: the object is the record
                    return new Loc.Local(((Loc.Local) ref).slot(), tt);
                }
                return new Loc.Field(ref, rc, "v", tt);
            }
            case NilChkExpr.P n -> {
                return designator(n.a);
            }
            case CheckExpr.P c -> {
                return designator(c.expr);
            }
            default -> {
                return null;
            }
        }
    }

    /** The location of the field f, which the object type holder declares, of the object that obj loads. */
    Loc objectFieldLoc(Loc obj, Type.T holder, m3j.front.Field.T f) {
        Loc java = Java.fieldLoc(this, obj, holder, f);
        return java != null ? java : fieldLoc(obj, Types.classOf(holder), f);
    }

    /** The location of a field f of the record or object that rec loads. */
    Loc fieldLoc(Loc rec, ClassDesc owner, m3j.front.Field.T f) {
        JType ft = JType.of(f.tipe);
        Loc l = new Loc.Field(rec, owner, id(f.name), ft);
        if (ft.kind == JType.Kind.ARRAY || ft.kind == JType.Kind.BIGSET) {
            return new Loc.Arr(l, new Loc.ConstInt(0), new Loc[0], f.tipe, ft);
        }
        return l;
    }

    /** A location loading the record object of a record designator or value. */
    Loc recordOf(Expr.T e) {
        ClassDesc rc = Types.classOf(Expr.typeOf(e));
        Loc d = Expr.isDesignator(e) ? designator(e) : null;
        if (d instanceof Loc.Local || d instanceof Loc.Static) {
            return d;
        }
        if (d != null) {
            return temp(rc, () -> d.load(cb));
        }
        return temp(rc, () -> {
            expr(e);
            checkcastIfNeeded(rc);
        });
    }

    /**
     * Pushes the reference a, checked for NIL (once: the front end may have
     * wrapped it in a check already), as class cd.
     */
    void refBase(Expr.T a, ClassDesc cd) {
        JType s = expr(a);
        if (!(a instanceof NilChkExpr.P)) {
            nilCheck();
        }
        if (s == null || s.desc == null || !s.desc.equals(cd)) {
            checkcastIfNeeded(cd);
        }
    }

    void checkcastIfNeeded(ClassDesc cd) {
        if (!cd.equals(ConstantDescs.CD_Object)) {
            cb.checkcast(cd);
        }
    }

    /** Evaluates code that pushes a reference into a new local; its location. */
    Loc temp(ClassDesc cd, Runnable code) {
        code.run();
        Loc l = newLocal(JType.ref(cd));
        l.store(cb);
        return l;
    }

    /** Throws the runtime error for NIL if the reference on the stack is NIL. */
    void nilCheck() {
        if (!Host.doNilChk) {
            return;
        }
        cb.dup();
        Label ok = cb.newLabel();
        cb.ifnonnull(ok);
        runtimeError(4);
        cb.labelBinding(ok);
    }

    Loc valueLoc(Value.T v) {
        v = Value.base(v);
        if (v instanceof Variable.T var) {
            return loc(var);
        }
        return null;
    }

    /** The element a[i]: a scalar or record location, or an array. */
    Loc element(Loc.Arr a, Expr.T index) {
        Type.T at = Aggregates.strip(a.arrayType());
        Type.T elt = arrayElement(at);
        Loc idx = newLocal(JType.INT);
        if (at.info.class_ == Type.Class.OpenArray) {
            JType got = expr(index);
            if (got.typeKind() == TypeKind.INT) {
                a.dims()[0].load(cb);
                cb.invokestatic(RT, "index", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_int,
                    ConstantDescs.CD_int));
            } else {
                widen(got.typeKind(), TypeKind.LONG);
                a.dims()[0].load(cb);
                cb.invokestatic(RT, "index", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_long,
                    ConstantDescs.CD_int));
            }
        } else {
            Type.T it = arrayIndex(at);
            long lo = firstOf(it);
            long n = JType.toLong(Type.number(it));
            JType xt = JType.of(Expr.typeOf(index));
            boolean safe = !Host.doRangeChk || within(index, xt, JType.longType(lo, lo + n - 1));
            JType got = expr(index);
            if (got.typeKind() == TypeKind.INT && lo >= Integer.MIN_VALUE && lo + n - 1 <= Integer.MAX_VALUE) {
                // an int index (a counter, a subrange): checked as int
                if (lo != 0) {
                    cb.loadConstant((int) lo);
                    cb.isub();
                }
                if (!safe) {
                    cb.loadConstant((int) n);
                    cb.invokestatic(RT, "index", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_int,
                        ConstantDescs.CD_int));
                }
            } else if (safe) {
                widen(got.typeKind(), TypeKind.LONG);
                cb.loadConstant(lo);
                cb.lsub();
                cb.l2i();
            } else {
                widen(got.typeKind(), TypeKind.LONG);
                cb.loadConstant(lo);
                cb.loadConstant((int) n);
                cb.invokestatic(RT, "index", MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_long,
                    ConstantDescs.CD_long, ConstantDescs.CD_int));
            }
        }
        // the leaf offset: offset + index * stride
        Aggregates.Leaf el = Aggregates.leaf(Aggregates.nonOpenElement(elt));
        long stride = el.slots();
        Loc[] restDims = a.isOpen() ? java.util.Arrays.copyOfRange(a.dims(), 1, a.dims().length) : new Loc[0];
        if (stride != 1 || restDims.length > 0) {
            cb.loadConstant((int) stride);
            for (Loc d : restDims) {
                d.load(cb);
                cb.imul();
            }
            cb.imul();
        }
        if (!(a.offset() instanceof Loc.ConstInt c && c.value() == 0)) {
            a.offset().load(cb);
            cb.iadd();
        }
        idx.store(cb);
        JType et = JType.of(elt);
        if (et.kind == JType.Kind.ARRAY || et.kind == JType.Kind.OPENARRAY || et.kind == JType.Kind.BIGSET) {
            return new Loc.Arr(a.storage(), idx, restDims, elt, et);
        }
        Loc storage = a.storage() instanceof Loc.Local ? a.storage()
            : temp(el.arrayDesc(), () -> a.storage().load(cb));
        return new Loc.Elem(storage, idx, el.store(), et);
    }

    static long firstOf(Type.T t) {
        int[] min = TInt.newInt(), max = TInt.newInt();
        Type.getBounds(t, min, max);
        return JType.toLong(min);
    }

    /** The array (or large set) that e denotes or computes. */
    Loc.Arr arr(Expr.T e) {
        Type.T t = Expr.typeOf(e);
        JType jt = JType.of(t);
        m3j.middle.M3CG.RuntimeError err = staticError(e);
        if (err != null) {
            runtimeError(err.ordinal());
            Loc st = newLocal(jt);
            cb.aconst_null();
            st.store(cb);
            Loc[] dims = new Loc[Aggregates.openDepth(t)];
            for (int i = 0; i < dims.length; i++) {
                dims[i] = new Loc.ConstInt(0);
            }
            return new Loc.Arr(st, new Loc.ConstInt(0), dims, t, jt);
        }
        switch (e) {
            case NamedExpr.P n -> {
                Value.T[] obj = new Value.T[1];
                int[] name = new int[1];
                Value.T v = NamedExpr.split(n, name, obj) ? Value.base(obj[0]) : null;
                if (v instanceof Variable.T var && loc(var) instanceof Loc.Arr a) {
                    return a;
                }
                if (v instanceof Constant.T c) {
                    Loc.Arr k = Constructors.constArray(this, c.toExpr(), true);
                    return k != null ? k : arr(c.toExpr());
                }
            }
            case VarExpr.P ve -> {
                if (loc(ve.v) instanceof Loc.Arr a) {
                    return a;
                }
            }
            case QualifyExpr.P q -> {
                if (q.class_ == QualifyExpr.Class.importDecl) {
                    Value.T v = Value.base(q.rhsValue);
                    if (v instanceof Variable.T var && loc(var) instanceof Loc.Arr a) {
                        return a;
                    }
                    if (v instanceof Constant.T c) {
                        Loc.Arr k = Constructors.constArray(this, c.toExpr(), true);
                        return k != null ? k : arr(c.toExpr());
                    }
                } else if (designator(e) instanceof Loc.Arr a) {
                    return a;
                }
            }
            case SubscriptExpr.P s -> {
                if (element(arr(s.a), s.b) instanceof Loc.Arr a) {
                    return a;
                }
            }
            case DerefExpr.P d -> {
                Type.T rt = Types.target(Type.base(Expr.typeOf(d.a)));
                ClassDesc rc = Types.classOf(Expr.typeOf(d.a));
                Loc ref = temp(rc, () -> refBase(d.a, rc));
                JType tt = JType.of(rt);
                if (tt.kind == JType.Kind.OPENARRAY) {
                    int depth = Aggregates.openDepth(rt);
                    Loc[] dims = new Loc[depth];
                    for (int i = 0; i < depth; i++) {
                        dims[i] = new Loc.Field(ref, rc, "n" + i, JType.INT);
                    }
                    return new Loc.Arr(new Loc.Field(ref, rc, "a", tt), new Loc.ConstInt(0), dims, rt, tt);
                }
                return new Loc.Arr(new Loc.Field(ref, rc, "v", tt), new Loc.ConstInt(0), new Loc[0], rt, tt);
            }
            case NilChkExpr.P n -> {
                return arr(n.a);
            }
            case CheckExpr.P c -> {
                return arr(c.expr);
            }
            case ConsExpr.P c -> {
                return arr(c.base);
            }
            case ArrayExpr.T a -> {
                return Constructors.array(this, a);
            }
            case SetExpr.P s -> {
                return Constructors.bigSet(this, s);
            }
            case CallExpr.T call -> {
                if (call.methods != UserProc.Methods) {
                    Loc.Arr a = Builtins.arrayCall(this, call);
                    if (a != null) {
                        return a;
                    }
                }
            }
            default -> {}
        }
        Expr.T c = Expr.constValue(e);
        if (c != null && c != e) {
            return arr(c);
        }
        if (jt.kind == JType.Kind.BIGSET && (e instanceof AddExpr.P || e instanceof SubtractExpr.P
                || e instanceof MultiplyExpr.P || e instanceof DivideExpr.P)) {
            return Constructors.bigSetOp(this, e);
        }
        if ((jt.kind == JType.Kind.ARRAY || jt.kind == JType.Kind.BIGSET) && e instanceof CallExpr.T) {
            // a function result: storage at offset 0
            Procs.call(this, (CallExpr.T) e, jt);
            Loc st = newLocal(jt);
            st.store(cb);
            return new Loc.Arr(st, new Loc.ConstInt(0), new Loc[0], t, jt);
        }
        unsupported("array expression " + e.getClass().getName());
        Loc st = newLocal(jt);
        cb.aconst_null();
        st.store(cb);
        Loc[] dims = new Loc[Aggregates.openDepth(t)];
        for (int i = 0; i < dims.length; i++) {
            dims[i] = new Loc.ConstInt(0);
        }
        return new Loc.Arr(st, new Loc.ConstInt(0), dims, t, jt);
    }

    // ---------------------------------------------------------- expressions

    /** Pushes the value of e, converted to t (with a range check if check). */
    void exprAs(Expr.T e, JType t, boolean check) {
        TypeKind tk = t.typeKind();
        if (t.isOrdinal() && (tk == TypeKind.INT || tk == TypeKind.LONG)) {
            // a constant in the range of t: pushed as it is needed
            Long v = constLong(e);
            if (v != null && v >= t.min && v <= t.max && !hasStaticError(e)) {
                if (tk == TypeKind.INT) {
                    cb.loadConstant((int) (long) v);
                } else {
                    cb.loadConstant((long) v);
                }
                return;
            }
        }
        JType s = expr(e);
        convert(e, s, t, check);
    }

    /** Converts the value on the stack from s to t. */
    void convert(Expr.T e, JType s, JType t, boolean check) {
        TypeKind sk = s.typeKind(), tk = t.typeKind();
        if (t.isOrdinal() && s.isOrdinal()) {
            boolean needCheck = check && Host.doRangeChk
                && (t.min > Long.MIN_VALUE || t.max < Long.MAX_VALUE)
                && !(e != null ? within(e, s, t) : (s.min >= t.min && s.max <= t.max));
            if (needCheck) {
                if (sk == TypeKind.LONG && tk == TypeKind.INT) {
                    cb.loadConstant((int) t.min);
                    cb.loadConstant((int) t.max);
                    cb.invokestatic(RT, "rangeJI", MethodTypeDesc.of(ConstantDescs.CD_int,
                        ConstantDescs.CD_long, ConstantDescs.CD_int, ConstantDescs.CD_int));
                    return;
                }
                if (sk == TypeKind.INT && tk == TypeKind.LONG) {
                    cb.i2l();
                    sk = TypeKind.LONG;
                }
                if (sk == TypeKind.LONG) {
                    cb.loadConstant(t.min);
                    cb.loadConstant(t.max);
                    cb.invokestatic(RT, "rangeJ", MethodTypeDesc.of(ConstantDescs.CD_long,
                        ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_long));
                } else {
                    cb.loadConstant((int) t.min);
                    cb.loadConstant((int) t.max);
                    cb.invokestatic(RT, "rangeI", MethodTypeDesc.of(ConstantDescs.CD_int,
                        ConstantDescs.CD_int, ConstantDescs.CD_int, ConstantDescs.CD_int));
                }
                return;
            }
        }
        widen(sk, tk);
        if (sk == TypeKind.REFERENCE && tk == TypeKind.REFERENCE && !t.desc.equals(s.desc)
                && !t.desc.equals(ConstantDescs.CD_Object)) {
            cb.checkcast(t.desc);
        }
    }

    void widen(TypeKind sk, TypeKind tk) {
        if (sk == tk) {
            return;
        }
        if (sk == TypeKind.INT && tk == TypeKind.LONG) {
            cb.i2l();
        } else if (sk == TypeKind.LONG && tk == TypeKind.INT) {
            cb.l2i();
        }
    }

    /** True if the static bounds of e lie within those of t. */
    boolean within(Expr.T e, JType s, JType t) {
        if (s.min >= t.min && s.max <= t.max) {
            return true;
        }
        int[] min = TInt.newInt(), max = TInt.newInt();
        Expr.getBounds(e, min, max);
        if (TInt.LT(max, min)) {
            return false;
        }
        long[] lo = new long[1], hi = new long[1];
        return TInt.toInt(min, lo) && TInt.toInt(max, hi) && lo[0] >= t.min && hi[0] <= t.max;
    }

    /** True if e or an expression in it is a constructor with a checked runtime error. */
    static boolean hasStaticError(Expr.T e) {
        boolean[] r = {false};
        Walk.expr(e, x -> r[0] |= staticError(x) != null);
        return r[0];
    }

    /** The value of a constant integer or enumeration expression, or null. */
    static Long constLong(Expr.T e) {
        Expr.T c = Expr.constValue(e);
        if (c == null) {
            return null;
        }
        int[] v = TInt.newInt();
        Type.T[] t = new Type.T[1];
        if (IntegerExpr.split(c, v, t) || EnumExpr.split(c, v, t)) {
            return JType.toLong(v);
        }
        return null;
    }

    static boolean isTrue(Expr.T c) {
        Long v = constLong(c);
        return v != null && v != 0;
    }

    /**
     * The runtime error the front end found in a constructor that e uses
     * (a value out of range, a wrong number of elements), or null: cm3
     * raises it where the value is used.
     */
    static m3j.middle.M3CG.RuntimeError staticError(Expr.T e) {
        m3j.middle.M3CG.RuntimeError r = switch (e) {
            case ConsExpr.P c -> staticError(c.base);
            case RecordExpr.P rec -> rec.RTErrorCode;
            case ArrayExpr.T a -> (a.top != null ? a.top : a).RTErrorCode;
            case SetExpr.P set -> set.RTErrorCode;
            case QualifyExpr.P q when q.class_ == QualifyExpr.Class.recField -> staticError(q.lhsExpr);
            case SubscriptExpr.P sub -> staticError(sub.a);
            case NamedExpr.P n -> {
                int[] name = new int[1];
                Value.T[] obj = new Value.T[1];
                yield NamedExpr.split(n, name, obj) && Value.base(obj[0]) instanceof Constant.T k
                    ? staticError(k.toExpr()) : null;
            }
            case QualifyExpr.P q when q.class_ == QualifyExpr.Class.importDecl
                    && Value.base(q.rhsValue) instanceof Constant.T k -> staticError(k.toExpr());
            default -> null;
        };
        if (r == null || r == m3j.middle.M3CG.RuntimeError.Unknown
                || !m3j.front.AssignStmt.doGenRTAbort(r)) {
            return null;
        }
        return r;
    }

    /** Pushes the value of e and returns its representation. */
    JType expr(Expr.T e) {
        Type.T et = Expr.typeOf(e);
        JType t = JType.of(et);
        m3j.middle.M3CG.RuntimeError err = staticError(e);
        if (err != null) {
            runtimeError(err.ordinal());
            if (t.kind == JType.Kind.ARRAY || t.kind == JType.Kind.BIGSET) {
                cb.aconst_null();
            } else {
                defaultValue(t);
            }
            return t;
        }
        if (t.kind == JType.Kind.ARRAY || t.kind == JType.Kind.BIGSET) {
            // the storage of a fresh copy at offset 0
            Loc.Arr a = arr(e);
            if (a.offset() instanceof Loc.ConstInt ci && ci.value() == 0 && isFresh(e)) {
                a.storage().load(cb);
            } else {
                Loc.Arr r = freshArr(et);
                copyArr(a, r);
                r.storage().load(cb);
            }
            return t;
        }
        if (!(isFloat(t) && foldDiffers(e)) && literal(e, t)) {
            return t;
        }
        switch (e) {
            case NamedExpr.P n -> {
                int[] name = new int[1];
                Value.T[] obj = new Value.T[1];
                NamedExpr.split(n, name, obj);
                return value(obj[0], t);
            }
            case QualifyExpr.P q -> {
                switch (q.class_) {
                    case importDecl -> {
                        return value(q.rhsValue, t);
                    }
                    case recField, objField -> {
                        Loc l = designator(q);
                        l.load(cb);
                        return l.type();
                    }
                    default -> {
                        return unsupportedValue("qualified " + q.class_, t);
                    }
                }
            }
            case VarExpr.P v -> {
                return value(v.v, t);
            }
            case SubscriptExpr.P _, DerefExpr.P _ -> {
                Loc l = designator(e);
                if (l == null) {
                    return unsupportedValue("designator " + e.getClass().getName(), t);
                }
                l.load(cb);
                return l.type();
            }
            case NilChkExpr.P n -> {
                JType s = expr(n.a);
                nilCheck();
                return s;
            }
            case CheckExpr.P ce -> {
                return checkExpr(ce);
            }
            case AddExpr.P a -> {
                return arith(a.a, a.b, t, Arith.ADD);
            }
            case SubtractExpr.P a -> {
                return arith(a.a, a.b, t, Arith.SUB);
            }
            case MultiplyExpr.P a -> {
                return arith(a.a, a.b, t, Arith.MUL);
            }
            case DivideExpr.P a -> {
                return arith(a.a, a.b, t, Arith.DIVIDE);
            }
            case DivExpr.P a -> {
                return arith(a.a, a.b, t, Arith.DIV);
            }
            case ModExpr.P a -> {
                return arith(a.a, a.b, t, Arith.MOD);
            }
            case NegateExpr.P a -> {
                JType s = expr(a.a);
                switch (s.typeKind()) {
                    case INT -> {
                        cb.i2l();
                        cb.lneg();
                        return JType.LONG;
                    }
                    case LONG -> cb.lneg();
                    case FLOAT -> cb.fneg();
                    case DOUBLE -> cb.dneg();
                    default -> {
                        return unsupportedValue("negate", t);
                    }
                }
                return s.typeKind() == TypeKind.LONG ? JType.LONG : s;
            }
            case PlusExpr.P a -> {
                return expr(a.a);
            }
            case ConcatExpr.P a -> {
                expr(a.a);
                expr(a.b);
                cb.invokestatic(RT, "concat", MethodTypeDesc.of(JType.CD_String, JType.CD_String, JType.CD_String));
                return JType.TEXT;
            }
            case NotExpr.P _, AndExpr.P _, OrExpr.P _, CompareExpr.P _, EqualExpr.P _, InExpr.P _ -> {
                return boolValue(e);
            }
            case CallExpr.T call -> {
                return Procs.call(this, call, t);
            }
            case ProcExpr.P pe -> {
                return Procs.procValue(this, (Procedure.T) Value.base(pe.proc));
            }
            case ConsExpr.P ce -> {
                return expr(ce.base);
            }
            case RecordExpr.P r -> {
                return Constructors.record(this, r);
            }
            case SetExpr.P s -> {
                return Constructors.smallSet(this, s);
            }
            case NarrowExpr.P n -> {
                return Exceptions.narrow(this, n);
            }
            case TypeExpr.P _ -> {
                return unsupportedValue("type as a value", t);
            }
            default -> {
                return unsupportedValue("expression " + e.getClass().getName(), t);
            }
        }
    }

    /**
     * True for a floating-point expression that cm3 computes even when it is
     * constant, and whose folded value can differ from the computed one: the
     * front end folds the arithmetic of REAL in double precision (FLOAT (x,
     * REAL) does not round). cm3 uses the folded value only where the front
     * end puts it in place of the expression (a scalar actual parameter, a
     * constant, an initial value), and then the tree has the folded value,
     * and for a negation that has been folded (NegateExpr.Compile; the
     * negation of 0.0 is 0.0 then).
     */
    static boolean foldDiffers(Expr.T e) {
        if (e instanceof NegateExpr.P n) {
            return n.folded == null;
        }
        if (e instanceof AddExpr.P || e instanceof SubtractExpr.P || e instanceof MultiplyExpr.P
            || e instanceof DivideExpr.P) {
            return true;
        }
        if (e instanceof CallExpr.T ce) {
            String n = Builtins.name(ce);
            return "FLOAT".equals(n) || "ABS".equals(n) || "MIN".equals(n) || "MAX".equals(n);
        }
        return false;
    }

    /** Pushes a literal; returns false if e is not a constant scalar. */
    boolean literal(Expr.T e, JType t) {
        return literalOf(cb, e, t);
    }

    /** True if literalOf would push a literal for e. */
    static boolean isLiteral(Expr.T e, JType t) {
        Expr.T c = Expr.constValue(e);
        if (c == null) {
            return false;
        }
        int[] v = TInt.newInt();
        Type.T[] tt = new Type.T[1];
        if (IntegerExpr.split(c, v, tt) || EnumExpr.split(c, v, tt)) {
            return t.typeKind() == TypeKind.INT || t.typeKind() == TypeKind.LONG;
        }
        if (c instanceof ReelExpr.P) {
            return t.typeKind() == TypeKind.FLOAT || t.typeKind() == TypeKind.DOUBLE;
        }
        M3String.T[] s8 = new M3String.T[1];
        M3WString.T[] s32 = new M3WString.T[1];
        if (TextExpr.split8(c, s8) || TextExpr.split32(c, s32)) {
            return true;
        }
        Type.T ct = Expr.typeOf(c);
        if (ct != null && Type.isEqual(ct, Null.T, null)) {
            return true;
        }
        return c instanceof SetExpr.P se && t.kind == JType.Kind.SET && Constructors.constSmallSet(se) != null;
    }

    static boolean literalOf(CodeBuilder cb, Expr.T e, JType t) {
        Expr.T c = Expr.constValue(e);
        if (c == null) {
            return false;
        }
        int[] v = TInt.newInt();
        Type.T[] tt = new Type.T[1];
        if (IntegerExpr.split(c, v, tt) || EnumExpr.split(c, v, tt)) {
            long x = JType.toLong(v);
            if (t.typeKind() == TypeKind.INT) {
                cb.loadConstant((int) x);
            } else if (t.typeKind() == TypeKind.LONG) {
                cb.loadConstant(x);
            } else {
                return false;
            }
            return true;
        }
        if (c instanceof ReelExpr.P r) {
            double d = r.val.fraction;
            switch (t.typeKind()) {
                case FLOAT -> cb.loadConstant((float) d);
                case DOUBLE -> cb.loadConstant(d);
                default -> {
                    return false;
                }
            }
            return true;
        }
        M3String.T[] s8 = new M3String.T[1];
        M3WString.T[] s32 = new M3WString.T[1];
        if (c instanceof TextExpr.P p && p.posix != null) {
            // a text that depends on the system (Wr.EOL): that of the
            // system the program runs on
            Label win32 = cb.newLabel(), end = cb.newLabel();
            cb.getstatic(OS_CLASS, "POSIX", ConstantDescs.CD_boolean);
            cb.ifeq(win32);
            cb.loadConstant(textOf(p.posix));
            cb.goto_(end);
            cb.labelBinding(win32);
            cb.loadConstant(textOf(p.win32));
            cb.labelBinding(end);
            return true;
        }
        if (TextExpr.split8(c, s8)) {
            cb.loadConstant(M3String.toText(s8[0]));
            return true;
        }
        if (TextExpr.split32(c, s32)) {
            cb.loadConstant(wideText(s32[0]));
            return true;
        }
        Type.T ct = Expr.typeOf(c);
        if (ct != null && Type.isEqual(ct, Null.T, null)) {
            cb.aconst_null();
            return true;
        }
        if (c instanceof SetExpr.P se && t.kind == JType.Kind.SET) {
            Long bits = Constructors.constSmallSet(se);
            if (bits != null) {
                cb.loadConstant(bits.longValue());
                return true;
            }
        }
        return false;
    }

    private static final ClassDesc OS_CLASS = ClassDesc.of("m3j.rt.OS");

    /** The value of a text literal. */
    private static String textOf(TextExpr.P p) {
        return p.value8 != null ? M3String.toText(p.value8) : wideText(p.value32);
    }

    static String wideText(M3WString.T t) {
        StringBuilder sb = new StringBuilder();
        flatten(t, sb);
        return sb.toString();
    }

    private static void flatten(M3WString.T t, StringBuilder sb) {
        if (t == null) {
            return;
        }
        if (t.body != null) {
            for (int i = 0; i < t.length; i++) {
                sb.append((char) t.body[i]);
            }
        } else {
            flatten(t.prefix, sb);
            flatten(t.suffix, sb);
        }
    }

    /** Pushes the value of a named value. */
    JType value(Value.T v, JType t) {
        v = Value.base(v);
        switch (v) {
            case Variable.T var -> {
                Loc l = loc(var);
                if (l == null) {
                    return unsupportedValue("variable " + id(var.name), t);
                }
                l.load(cb);
                return l.type();
            }
            case Constant.T _, EnumElt.T _ -> {
                Expr.T c = v.toExpr();
                if (literal(c, t)) {
                    return t;
                }
                return expr(c);
            }
            case Procedure.T p -> {
                return Procs.procValue(this, p);
            }
            default -> {
                return unsupportedValue("value " + v.getClass().getName(), t);
            }
        }
    }

    JType checkExpr(CheckExpr.P ce) {
        JType s = expr(ce.expr);
        if (!s.isOrdinal()) {
            return s;
        }
        long min = JType.toLong(ce.min), max = JType.toLong(ce.max);
        if (ce.class_ == CheckExpr.Class.cLOWER) {
            max = s.max;
        } else if (ce.class_ == CheckExpr.Class.cUPPER) {
            min = s.min;
        }
        JType target = s.typeKind() == TypeKind.INT
            ? JType.intType(Math.max(min, Integer.MIN_VALUE), Math.min(max, Integer.MAX_VALUE))
            : JType.longType(min, max);
        convert(ce.expr, s, target, true);
        return target;
    }

    enum Arith { ADD, SUB, MUL, DIVIDE, DIV, MOD }

    JType arith(Expr.T a, Expr.T b, JType t, Arith op) {
        switch (t.kind) {
            case LONG, INT -> {
                Long d = op == Arith.DIV || op == Arith.MOD ? constLong(b) : null;
                if (d != null && d > 0 && Long.bitCount(d) == 1 && !hasStaticError(b)) {
                    // by a power of two: DIV and MOD round down, as a shift and a mask do
                    exprAs(a, JType.LONG, false);
                    if (op == Arith.DIV) {
                        cb.loadConstant(Long.numberOfTrailingZeros(d));
                        cb.lshr();
                    } else {
                        cb.loadConstant(d - 1);
                        cb.land();
                    }
                    return JType.LONG;
                }
                exprAs(a, JType.LONG, false);
                exprAs(b, JType.LONG, false);
                switch (op) {
                    case ADD -> cb.ladd();
                    case SUB -> cb.lsub();
                    case MUL -> cb.lmul();
                    case DIV -> cb.invokestatic(RT, "div", MethodTypeDesc.of(ConstantDescs.CD_long,
                        ConstantDescs.CD_long, ConstantDescs.CD_long));
                    case MOD -> cb.invokestatic(RT, "mod", MethodTypeDesc.of(ConstantDescs.CD_long,
                        ConstantDescs.CD_long, ConstantDescs.CD_long));
                    default -> {
                        return unsupportedValue("integer " + op, t);
                    }
                }
                return JType.LONG;
            }
            case FLOAT, DOUBLE -> {
                boolean f = t.kind == JType.Kind.FLOAT;
                expr(a);
                expr(b);
                switch (op) {
                    case ADD -> { if (f) cb.fadd(); else cb.dadd(); }
                    case SUB -> { if (f) cb.fsub(); else cb.dsub(); }
                    case MUL -> { if (f) cb.fmul(); else cb.dmul(); }
                    case DIVIDE -> { if (f) cb.fdiv(); else cb.ddiv(); }
                    case MOD -> {
                        ClassDesc d = f ? ConstantDescs.CD_float : ConstantDescs.CD_double;
                        cb.invokestatic(RT, "mod", MethodTypeDesc.of(d, d, d));
                    }
                    default -> {
                        return unsupportedValue("real " + op, t);
                    }
                }
                return t;
            }
            case SET -> {
                expr(a);
                expr(b);
                switch (op) {
                    case ADD -> cb.lor();
                    case MUL -> cb.land();
                    case DIVIDE -> cb.lxor();
                    case SUB -> {
                        cb.loadConstant(-1L);
                        cb.lxor();
                        cb.land();
                    }
                    default -> {
                        return unsupportedValue("set " + op, t);
                    }
                }
                return t;
            }
            default -> {
                return unsupportedValue("arithmetic on " + t.kind, t);
            }
        }
    }

    /** Pushes a BOOLEAN expression as 0 or 1. */
    JType boolValue(Expr.T e) {
        Label f = cb.newLabel(), end = cb.newLabel();
        branch(e, f, false);
        cb.iconst_1();
        cb.goto_(end);
        cb.labelBinding(f);
        cb.iconst_0();
        cb.labelBinding(end);
        return JType.BOOL;
    }

    /** Jumps to target if e has the value when. */
    void branch(Expr.T e, Label target, boolean when) {
        Long v = constLong(e);
        if (v != null && JType.of(Expr.typeOf(e)).kind == JType.Kind.BOOL) {
            if ((v != 0) == when) {
                cb.goto_(target);
            }
            return;
        }
        switch (e) {
            case NotExpr.P n -> branch(n.a, target, !when);
            case AndExpr.P a -> {
                if (when) {
                    Label skip = cb.newLabel();
                    branch(a.a, skip, false);
                    branch(a.b, target, true);
                    cb.labelBinding(skip);
                } else {
                    branch(a.a, target, false);
                    branch(a.b, target, false);
                }
            }
            case OrExpr.P o -> {
                if (when) {
                    branch(o.a, target, true);
                    branch(o.b, target, true);
                } else {
                    Label skip = cb.newLabel();
                    branch(o.a, skip, true);
                    branch(o.b, target, false);
                    cb.labelBinding(skip);
                }
            }
            case CompareExpr.P ce -> compare(ce.a, ce.b, op(ce.op), target, when);
            case EqualExpr.P ee -> compare(ee.a, ee.b, op(ee.op), target, when);
            case InExpr.P in -> {
                Constructors.in(this, in.a, in.b);
                if (when) {
                    cb.ifne(target);
                } else {
                    cb.ifeq(target);
                }
            }
            default -> {
                JType t = expr(e);
                widen(t.typeKind(), TypeKind.INT);
                if (when) {
                    cb.ifne(target);
                } else {
                    cb.ifeq(target);
                }
            }
        }
    }

    static CmpOp op(m3j.middle.M3CG.CompareOp op) {
        return switch (op) {
            case EQ -> CmpOp.EQ;
            case NE -> CmpOp.NE;
            case LT -> CmpOp.LT;
            case LE -> CmpOp.LE;
            case GT -> CmpOp.GT;
            case GE -> CmpOp.GE;
        };
    }

    void compare(Expr.T a, Expr.T b, CmpOp op, Label target, boolean when) {
        CmpOp orig = op;
        if (!when) {
            op = negate(op);
        }
        JType ta = JType.of(Expr.typeOf(a)), tb = JType.of(Expr.typeOf(b));
        if (ta.isOrdinal() && tb.isOrdinal()) {
            boolean isLong = ta.typeKind() == TypeKind.LONG || tb.typeKind() == TypeKind.LONG;
            JType w = isLong ? JType.LONG : JType.INT;
            exprAs(a, w, false);
            exprAs(b, w, false);
            compareJump(isLong, op, target);
            return;
        }
        if (isFloat(ta) && isFloat(tb)) {
            boolean f = ta.kind == JType.Kind.FLOAT;
            expr(a);
            expr(b);
            // NaN: every comparison but # is FALSE, as in C; so the jump is
            // taken for NaN iff (orig = #) = when. fcmpl gives -1 for NaN,
            // fcmpg 1: pick the one that makes the test of op do that.
            boolean jumpOnNaN = (orig == CmpOp.NE) == when;
            boolean less = op == CmpOp.LT || op == CmpOp.LE;
            boolean useL = less == jumpOnNaN;
            if (f) {
                if (useL) cb.fcmpl(); else cb.fcmpg();
            } else {
                if (useL) cb.dcmpl(); else cb.dcmpg();
            }
            switch (op) {
                case EQ -> cb.ifeq(target);
                case NE -> cb.ifne(target);
                case LT -> cb.iflt(target);
                case LE -> cb.ifle(target);
                case GT -> cb.ifgt(target);
                case GE -> cb.ifge(target);
            }
            return;
        }
        if (ta.kind == JType.Kind.SET && tb.kind == JType.Kind.SET) {
            expr(a);
            expr(b);
            Constructors.setCompare(this, orig, target, when);
            return;
        }
        if (ta.kind == JType.Kind.BIGSET || ta.kind == JType.Kind.ARRAY || ta.kind == JType.Kind.OPENARRAY) {
            if (ta.kind == JType.Kind.BIGSET && op != CmpOp.EQ && op != CmpOp.NE) {
                Constructors.bigSetCompare(this, arr(a), arr(b), orig, target, when);
                return;
            }
            Loc.Arr x = arr(a), y = arr(b);
            Label differ = cb.newLabel(), done = cb.newLabel();
            int depth = Math.max(Aggregates.openDepth(x.arrayType()), Aggregates.openDepth(y.arrayType()));
            for (int i = 0; i < depth; i++) {
                dim(x, i).load(cb);
                dim(y, i).load(cb);
                cb.if_icmpne(differ);
            }
            Aggregates.Leaf l = leafOf(x);
            x.storage().load(cb);
            x.offset().load(cb);
            y.storage().load(cb);
            y.offset().load(cb);
            arrSlots(x).load(cb);
            Aggregates.equalRange(cb, l, ta.kind != JType.Kind.BIGSET
                && numericElements(x.arrayType(), y.arrayType()));
            if (op == CmpOp.EQ) {
                cb.ifne(target);
                cb.labelBinding(differ);
            } else {
                cb.ifeq(target);
                cb.goto_(done);
                cb.labelBinding(differ);
                cb.goto_(target);
                cb.labelBinding(done);
            }
            return;
        }
        if (ta.kind == JType.Kind.RECORD) {
            expr(a);
            expr(b);
            ClassDesc cd = ta.desc;
            cb.invokevirtual(cd, "eq", MethodTypeDesc.of(ConstantDescs.CD_boolean, cd));
            if (op == CmpOp.EQ) {
                cb.ifne(target);
            } else {
                cb.ifeq(target);
            }
            return;
        }
        if ((ta.kind == JType.Kind.PROC || tb.kind == JType.Kind.PROC) && (op == CmpOp.EQ || op == CmpOp.NE)) {
            // closures of nested procedures are equal if procedure and frame are
            expr(a);
            expr(b);
            cb.invokestatic(ClassDesc.of("java.util.Objects"), "equals", MethodTypeDesc.of(ConstantDescs.CD_boolean,
                ConstantDescs.CD_Object, ConstantDescs.CD_Object));
            if (op == CmpOp.EQ) {
                cb.ifne(target);
            } else {
                cb.ifeq(target);
            }
            return;
        }
        if (ta.typeKind() == TypeKind.REFERENCE && tb.typeKind() == TypeKind.REFERENCE
                && (op == CmpOp.EQ || op == CmpOp.NE)) {
            expr(a);
            expr(b);
            if (op == CmpOp.EQ) {
                cb.if_acmpeq(target);
            } else {
                cb.if_acmpne(target);
            }
            return;
        }
        unsupported("comparison of " + ta.kind + " and " + tb.kind);
    }

    static boolean isFloat(JType t) {
        return t.kind == JType.Kind.FLOAT || t.kind == JType.Kind.DOUBLE;
    }

    // ---------------------------------------------------------- unsupported

    void unsupported(String what) {
        unit.unsupported(what);
        cb.loadConstant(what);
        cb.invokestatic(RT, "notImplemented", MethodTypeDesc.of(CD_RuntimeException, JType.CD_String));
        cb.athrow();
    }

    JType unsupportedValue(String what, JType t) {
        unsupported(what);
        if (t.typeKind() != TypeKind.VOID) {
            defaultValue(t);
        }
        return t;
    }
}
