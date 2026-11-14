// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import m3j.front.Exceptionz;
import m3j.front.External;
import m3j.front.Module;
import m3j.front.Procedure;
import m3j.front.Revelation;
import m3j.front.Scope;
import m3j.front.Stmt;
import m3j.front.Tipe;
import m3j.front.Value;
import m3j.front.Variable;

/**
 * Generates the class of one unit. A module becomes the class m3.m.NAME,
 * an interface the class m3.i.NAME (unless the runtime provides it), an
 * interface bound to a Java class the class m3.b.NAME.
 */
final class Unit {

    static final ClassDesc CD_Link = ClassDesc.of("m3j.rt.Link");

    final Module.T module;
    final String name;
    final boolean isInterface;
    final ClassDesc self;
    final String sourceFile;
    final List<String> unsupported = new ArrayList<>();
    final Frames frames;
    /** the description of the unit for a debugger */
    final DebugInfo debug;

    /** procedures still to generate, and the method names of all procedures */
    private final Deque<Procedure.T> todo = new ArrayDeque<>();
    private final Map<Procedure.T, String> methodNames = new HashMap<>();
    private final Set<String> usedNames = new HashSet<>();
    /** the procedures whose values are taken: accessor method names */
    private final Map<Procedure.T, String> accessors = new LinkedHashMap<>();
    /** The bridges of the procedures passed to Java, by their adapters. */
    private final Map<Java.Adapter, String> bridges = new LinkedHashMap<>();

    Unit(Module.T module, String sourceFile) {
        this.module = module;
        this.name = Code.id(Module.name(module));
        this.isInterface = module.interface_;
        this.self = classDesc(module);
        this.sourceFile = sourceFile;
        this.frames = new Frames(module, self, methodNames);
        this.debug = new DebugInfo(name);
    }

    // ----------------------------------------------------------------- names

    static ClassDesc classDesc(Module.T m) {
        String n = Code.id(Module.name(m));
        if (m.javaClass != null) {
            return ClassDesc.of("m3.b." + n);
        }
        return m.interface_ ? ClassDesc.of("m3.i." + n) : ClassDesc.of("m3.m." + n);
    }

    /** The number of errors that the back end reported for this unit. */
    int errors;

    private static final Map<String, Class<?>> providedCache = new HashMap<>();

    /**
     * The runtime class that implements the interface of the given name,
     * or null. Such a class has a static field M3$PROVIDED.
     */
    static Class<?> provided(String intf) {
        Class<?> c = providedCache.computeIfAbsent(intf, n -> {
            try {
                Class<?> k = Class.forName("m3.i." + n, false, Unit.class.getClassLoader());
                k.getField("M3$PROVIDED");
                return k;
            } catch (ClassNotFoundException | NoSuchFieldException e) {
                return Void.class;
            } catch (NoClassDefFoundError e) {
                // the class of another interface whose name differs only in
                // case (FingerPrint, Fingerprint), found in a directory of a
                // file system that ignores case
                return Void.class;
            }
        });
        return c == Void.class ? null : c;
    }

    void unsupported(String what) {
        unsupported.add(what);
    }

    /** Reports an error of the back end at the place origin of the source. */
    void error(String message, long origin) {
        long save = m3j.front.Scanner.offset;
        m3j.front.Scanner.offset = origin;
        m3j.front.Error.msg(message);
        m3j.front.Scanner.offset = save;
        errors++;
    }

    /** The name of procedure p for a debugger: Unit.P, nested Unit.P.Q; the body of the unit is Unit. */
    String m3Name(Procedure.T p) {
        if (p == Frames.INIT) {
            return name;
        }
        Procedure.T parent = frames.parent.get(p);
        return (parent == null ? name : m3Name(parent)) + "." + Code.id(p.name);
    }

    String methodName(Procedure.T p) {
        String m = methodNames.get(p);
        if (m == null) {
            schedule(p, frames.parent.get(p));
            m = methodNames.get(p);
        }
        return m;
    }

    /** loops of the body of the unit moved into methods of their own, and their names */
    private final Map<Stmt.T, String> loops = new LinkedHashMap<>();

    /** The name of the method that runs loop s of the body of the unit. */
    String outline(Stmt.T s) {
        return loops.computeIfAbsent(s, k -> "$init$loop" + loops.size());
    }

    /** The name of the method of chunk c of procedure p (or of the body of the unit). */
    String chunkName(Procedure.T p, Split.Chunk c) {
        return (p == Frames.INIT ? "$init" : methodName(p)) + "$$" + c.id;
    }

    // ------------------------------------------------------- method sizes

    /**
     * HotSpot does not compile methods larger than 8000 bytes
     * (DontCompileHugeMethods). The system property m3j.maxMethod sets a
     * smaller limit, so that tests can split small procedures too.
     */
    static final int MAX_METHOD = Math.max(100, Integer.getInteger("m3j.maxMethod", 8000));

    /** Below this estimate a method is not measured. */
    private static final int MEASURE_FROM = Math.min(2000, MAX_METHOD / 4);

    /**
     * The first limit of the plans of a split, and the smallest one tried
     * (below about 200 bytes, runs of small statements no longer become
     * chunks, and the plans get worse).
     */
    private static final int FIRST_LIMIT = MAX_METHOD * 3 / 4, LAST_LIMIT = Math.min(400, Math.max(200, MAX_METHOD / 20));

    /**
     * Emits the method of procedure p (or of the body of the unit), whose
     * code body makes; if it is larger than MAX_METHOD bytes, splits it in
     * chunks (methods of their own, see Split), planned with smaller limits
     * until every method fits. If none fits, the plan whose largest method
     * is smallest is taken (the frame keeps the variables of every plan
     * tried, a superset of what that plan needs).
     */
    private void emitSplittable(ClassBuilder clb, Procedure.T p, String name, MethodTypeDesc d, int flags,
                                java.util.function.Consumer<Code> body, Stmt.T stmts, List<Variable.T> vars) {
        Split sp = null;
        if (Split.estimate(stmts) > MEASURE_FROM
                && measure(d, flags, cb -> body.accept(new Code(this, cb, p))) > MAX_METHOD) {
            JType result = p == Frames.INIT ? JType.VOID
                : JType.of(m3j.front.ProcType.result(m3j.front.Type.base(p.signature)));
            int best = Integer.MAX_VALUE;
            for (int limit = FIRST_LIMIT; ; limit /= 2) {
                Split s = Split.plan(stmts, vars, limit);
                if (s.chunks.isEmpty()) {
                    break;
                }
                frames.addSplit(p, s.frameVars, s.returnsValue && result.kind != JType.Kind.VOID ? result : null);
                int m = measureSplit(p, s, d, flags, body);
                if (m < best) {
                    best = m;
                    sp = s;
                }
                if (m <= MAX_METHOD || limit < LAST_LIMIT) {
                    break;
                }
            }
            if (p == Frames.INIT && sp != null) {
                // the chunks replace the loops moved out of the body
                loops.clear();
            }
        }
        Split fs = sp;
        clb.withMethodBody(name, d, flags, cb -> {
            Code c = new Code(this, cb, p);
            c.split = fs;
            body.accept(c);
            debug.method(name, p == Frames.INIT ? 'B' : 'P', m3Name(p), null, c.debugVars);
        });
        if (fs != null) {
            for (Split.Chunk ch : fs.chunks.values()) {
                clb.withMethodBody(chunkName(p, ch), chunkDescriptor(p), ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                    cb -> {
                        Code c = new Code(this, cb, p);
                        c.split = fs;
                        c.chunkBody(ch);
                        debug.method(chunkName(p, ch), 'C', m3Name(p), name, c.debugVars);
                    });
            }
        }
    }

    private MethodTypeDesc chunkDescriptor(Procedure.T p) {
        return MethodTypeDesc.of(ConstantDescs.CD_int, frames.frameClassOf(p));
    }

    /** The size of the largest method of procedure p split by plan s. */
    private int measureSplit(Procedure.T p, Split s, MethodTypeDesc d, int flags, java.util.function.Consumer<Code> body) {
        int m = measure(d, flags, cb -> {
            Code c = new Code(this, cb, p);
            c.split = s;
            body.accept(c);
        });
        for (Split.Chunk ch : s.chunks.values()) {
            m = Math.max(m, measure(chunkDescriptor(p), ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, cb -> {
                Code c = new Code(this, cb, p);
                c.split = s;
                c.chunkBody(ch);
            }));
        }
        return m;
    }

    /** The size of the code of a method that gen makes (very large if it cannot be built). */
    private int measure(MethodTypeDesc d, int flags, java.util.function.Consumer<CodeBuilder> gen) {
        int reported = unsupported.size();
        try {
            byte[] b = Types.classFile().build(self, clb -> clb.withMethodBody("m", d, flags, gen));
            for (java.lang.classfile.MethodModel mm : ClassFile.of().parse(b).methods()) {
                return mm.code().map(c -> ((java.lang.classfile.attribute.CodeAttribute) c).codeLength()).orElse(0);
            }
            return 0;
        } catch (IllegalArgumentException e) {
            // such as code longer than 64 KB
            return Integer.MAX_VALUE;
        } finally {
            while (unsupported.size() > reported) {
                unsupported.removeLast();
            }
        }
    }

    /** The name of the method that returns the value of procedure p. */
    String procAccessor(Procedure.T p) {
        return accessors.computeIfAbsent(p, q -> "$proc$" + methodName(q));
    }

    /** The name of the method of this class that runs a procedure passed by the adapter a. */
    String bridge(Java.Adapter a) {
        String name = bridges.get(a);
        if (name == null) {
            name = "$bridge$" + bridges.size();
            bridges.put(a, name);
        }
        return name;
    }

    // ------------------------------------------------------------ the class

    byte[] generate() {
        Scope.T zz = Scope.push(module.localScope);
        Revelation.Set yy = Revelation.push(module.revelations);
        Types.currentUnit = this;
        try {
            Types.noteRevelations(module.revelations);
            frames.analyze();
            for (Procedure.T p : frames.procedures) {
                if (frames.parent.get(p) == null) {
                    schedule(p, null);
                }
            }
            for (Procedure.T p : frames.procedures) {
                if (frames.parent.get(p) != null) {
                    schedule(p, frames.parent.get(p));
                }
            }
            frames.generate();
            byte[] b = Types.classFile().build(self, clb -> {
                clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SUPER);
                clb.with(SourceFileAttribute.of(sourceFile));
                fields(clb);
                emitSplittable(clb, Frames.INIT, "$init", Code.MT_V, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                    c -> c.init(module), isInterface ? null : module.block, List.of());
                for (Map.Entry<Stmt.T, String> e : new ArrayList<>(loops.entrySet())) {
                    clb.withMethodBody(e.getValue(), Code.MT_V, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, cb -> {
                        Code c = new Code(this, cb, Frames.INIT);
                        c.outlined = true;
                        c.stmt(e.getKey());
                        cb.return_();
                        debug.method(e.getValue(), 'L', name, "$init", c.debugVars);
                    });
                }
                while (!todo.isEmpty()) {
                    Procedure.T p = todo.removeFirst();
                    emitSplittable(clb, p, methodNames.get(p), descriptor(p),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, Code::procedure, p.block, ownVariables(p));
                }
                // the methods that stand for the procedures bound to Java members where they are values
                for (Value.T v = Scope.toList(module.localScope); v != null; v = v.next) {
                    if (v instanceof Procedure.T p && p.javaBound && Java.resolve(p) != null) {
                        String thunk = Code.id(p.name);
                        methodNames.put(p, thunk);
                        usedNames.add(thunk);
                        accessors.put(p, "$proc$" + thunk);
                        Java.thunk(this, clb, p, thunk);
                    }
                }
                // the accessors of procedure values (and of exported procedures)
                if (!isInterface) {
                    for (Value.T v = Scope.toList(module.localScope); v != null; v = v.next) {
                        if (v instanceof Procedure.T p && p.block != null && p.intf_peer != null) {
                            procAccessor(p);
                        }
                    }
                }
                for (Map.Entry<Procedure.T, String> e : new ArrayList<>(accessors.entrySet())) {
                    Procedure.T p = e.getKey();
                    MethodTypeDesc d = Params.descriptor(p.signature);
                    ClassDesc iface = Types.procInterface(d);
                    clb.withMethodBody(e.getValue(), MethodTypeDesc.of(iface),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, cb -> {
                            Procs.lambda(cb, iface, d, List.of(), self, methodName(p));
                            cb.areturn();
                        });
                }
                bridges.forEach((a, name) -> Java.bridge(this, clb, name, a));
                clb.withField("M3$UNIT", ConstantDescs.CD_String,
                    fb -> fb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL)
                            .with(ConstantValueAttribute.of(metadata())));
            });
            // the exceptions declared in the unit, and the classes that extend
            // Java classes: what is wrong with them is an error of this unit
            for (Value.T v = Scope.toList(module.localScope); v != null; v = v.next) {
                if (v instanceof Exceptionz.T e) {
                    Exceptions.classOf(e);
                } else if (v instanceof Tipe.T t && Java.isJavaObject(t.value)) {
                    Types.classOf(t.value);
                }
            }
            Types.flush();
            debug.roots(Types.flushed);
            Types.flushed.clear();
            debug.genericFile = DebugInfo.genericOf(module.genericFile);
            debug.main = !isInterface && exports().contains("Main");
            return debug.attach(dropDeadNames(b));
        } finally {
            Revelation.pop(yy);
            Scope.pop(zz);
        }
    }

    /**
     * The class b without the entries of a LocalVariableTable whose slot is
     * beyond max_locals: those of variables that only code which cannot be
     * reached uses (after a RETURN, in a branch of a constant condition).
     * The ClassFile API replaces such code with nop and athrow and leaves its
     * locals out of max_locals, and HotSpot rejects a class with such an
     * entry. Only the methods that have one are rebuilt.
     */
    static byte[] dropDeadNames(byte[] b) {
        ClassModel cm = ClassFile.of().parse(b);
        Map<String, Integer> bad = new HashMap<>();
        for (MethodModel mm : cm.methods()) {
            if (mm.code().orElse(null) instanceof CodeAttribute ca) {
                for (LocalVariableTableAttribute t : ca.findAttributes(Attributes.localVariableTable())) {
                    for (LocalVariableInfo v : t.localVariables()) {
                        if (v.slot() + TypeKind.fromDescriptor(v.type().stringValue()).slotSize() > ca.maxLocals()) {
                            bad.put(key(mm), ca.maxLocals());
                        }
                    }
                }
            }
        }
        if (bad.isEmpty()) {
            return b;
        }
        return Types.classFile().transformClass(cm, ClassTransform.transformingMethods(mm -> bad.containsKey(key(mm)),
            (mb, me) -> {
                if (me instanceof CodeModel code) {
                    int max = bad.get(key(code.parent().orElseThrow()));
                    mb.transformCode(code, (cob, coe) -> {
                        if (!(coe instanceof LocalVariable lv
                              && lv.slot() + TypeKind.from(lv.typeSymbol()).slotSize() > max)) {
                            cob.with(coe);
                        }
                    });
                } else {
                    mb.with(me);
                }
            }));
    }

    private static String key(MethodModel mm) {
        return mm.methodName().stringValue() + mm.methodType().stringValue();
    }

    /** The formals and the variables of the scope of procedure p. */
    private static List<Variable.T> ownVariables(Procedure.T p) {
        List<Variable.T> r = new ArrayList<>();
        for (Value.T v = Scope.toList(p.syms); v != null; v = v.next) {
            if (v instanceof Variable.T var) {
                r.add(var);
            }
        }
        return r;
    }

    /** The descriptor of the method of procedure p (with its static link). */
    MethodTypeDesc descriptor(Procedure.T p) {
        MethodTypeDesc d = Params.descriptor(p.signature);
        Procedure.T link = frames.linkOf(p);
        if (link != null) {
            return d.insertParameterTypes(0, frames.frameClassOf(link));
        }
        return d;
    }

    /** The static fields of the unit's variables. */
    private void fields(ClassBuilder clb) {
        for (Value.T v = Scope.toList(module.localScope); v != null; v = v.next) {
            if (v instanceof Variable.T var && !var.javaBound) {
                clb.withField(Code.id(var.name), Code.varType(var).desc,
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
                debug.global(Code.id(var.name), m3j.front.Type.globalUID(var.type));
            }
        }
    }

    /** Schedules a procedure declared in this unit for generation. */
    void schedule(Procedure.T p, Procedure.T parent) {
        if (methodNames.containsKey(p)) {
            return;
        }
        String n = Code.id(p.name);
        if (parent != null && parent != Frames.INIT) {
            n = methodName(parent) + "$" + n;
        } else if (parent == Frames.INIT) {
            n = "$init$" + n;
        }
        String m = n;
        for (int k = 1; !usedNames.add(m); k++) {
            m = n + "$" + k;
        }
        methodNames.put(p, m);
        todo.addLast(p);
    }

    /** Nested procedures are found by the analysis; nothing to do. */
    void nestedProcedures(Scope.T scope, Procedure.T parent) {
    }

    /** "I NAME;imports A B;exports C D" */
    private String metadata() {
        StringBuilder sb = new StringBuilder();
        sb.append(isInterface ? "I " : "M ").append(name).append(";imports");
        for (String s : imports()) {
            sb.append(' ').append(s);
        }
        sb.append(";exports");
        for (String s : exports()) {
            sb.append(' ').append(s);
        }
        return sb.toString();
    }

    Set<String> imports() {
        Set<String> r = new LinkedHashSet<>();
        if (module.externals != null) {
            for (External.Port p = module.externals.imports; p != null; p = p.next) {
                if (p.module != null) {
                    r.add(Code.id(Module.name(p.module)));
                }
            }
        }
        return r;
    }

    Set<String> exports() {
        Set<String> r = new LinkedHashSet<>();
        if (module.externals != null) {
            for (External.Port p = module.externals.exports; p != null; p = p.next) {
                if (p.module != null) {
                    r.add(Code.id(Module.name(p.module)));
                }
            }
        }
        return r;
    }

    // ---------------------------------------------------------------- calls

    /** Calls procedure p; its arguments (and static link) are on the stack. */
    void invoke(CodeBuilder cb, Procedure.T p) {
        if (Java.isBound(p)) {
            Procs.invokeStatic(cb, p);
            return;
        }
        Value.T home = Scope.toUnit(p);
        if (home == module && !p.external) {
            cb.invokestatic(self, methodName(p), descriptor(p));
            return;
        }
        if (home instanceof Module.T hm && hm.interface_) {
            if (p.impl_peer != null && Scope.toUnit(p.impl_peer) == module) {
                invoke(cb, p.impl_peer);
                return;
            }
            String intf = Code.id(Module.name(hm));
            Class<?> c = provided(intf);
            if (c != null) {
                checkProvided(c, Code.id(p.name), Params.erased(Params.descriptor(p.signature)));
            }
        }
        Procs.invokeStatic(cb, p);
    }

    static DirectMethodHandleDesc bootstrap(String name) {
        MethodTypeDesc t = name.equals("call")
            ? MethodTypeDesc.of(ConstantDescs.CD_CallSite, ConstantDescs.CD_MethodHandles_Lookup,
                ConstantDescs.CD_String, ConstantDescs.CD_MethodType, ConstantDescs.CD_String)
            : MethodTypeDesc.of(ConstantDescs.CD_CallSite, ConstantDescs.CD_MethodHandles_Lookup,
                ConstantDescs.CD_String, ConstantDescs.CD_MethodType);
        return MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, CD_Link, name, t);
    }

    /** Reports a construct not compiled yet if the runtime class does not have the procedure. */
    void checkProvided(Class<?> c, String name, MethodTypeDesc d) {
        try {
            MethodType mt = MethodType.fromMethodDescriptorString(d.descriptorString(), c.getClassLoader());
            Method m = c.getMethod(name, mt.parameterArray());
            if (!Modifier.isStatic(m.getModifiers()) || m.getReturnType() != mt.returnType()) {
                throw new NoSuchMethodException();
            }
        } catch (NoSuchMethodException | TypeNotPresentException | IllegalArgumentException e) {
            unsupported("runtime procedure " + c.getSimpleName() + "." + name + d.descriptorString());
        }
    }
}
