// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import m3j.front.CallExpr;
import m3j.front.Expr;
import m3j.front.Formal;
import m3j.front.Module;
import m3j.front.OpaqueType;
import m3j.front.ProcType;
import m3j.front.Procedure;
import m3j.front.Reff;
import m3j.front.Scope;
import m3j.front.Textt;
import m3j.front.Tipe;
import m3j.front.Type;
import m3j.front.Value;
import m3j.front.Variable;

/**
 * Bindings of Modula-3 declarations to Java classes.
 *
 * <p>An interface {@code <*EXTERNAL "a.b.C":JAVA*> INTERFACE X} stands for
 * the class a.b.C: its type T is that class, and its procedures and
 * variables are the methods and static fields of the class. A single
 * procedure of any other interface is bound by
 * {@code <*EXTERNAL "a.b.C.m":JAVA*>}. The Java member is found by
 * reflection when the interface is compiled, so that a mistake in a binding
 * is an error of the compilation. No class is generated for a bound
 * interface: a call is the JVM instruction that calls the member.
 */
final class Java {
    private Java() {}

    private static final ClassLoader BASE = Java.class.getClassLoader();

    /** The class loader of the classes that bindings name. */
    static ClassLoader loader = BASE;

    /** The procedures resolved so far; an unresolved one maps to a Bad. */
    private static final Map<Procedure.T, Object> bindings = new IdentityHashMap<>();
    private static final Map<Variable.T, Object> fields = new IdentityHashMap<>();

    /** A binding that cannot be resolved; its message is the error of the declaration. */
    static final class Bad extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Bad(String message) {
            super(message, null, false, false);
        }
    }

    /** Forgets the class path and the bindings of an earlier compilation in this JVM. */
    static void reset() {
        loader = BASE;
        bindings.clear();
        fields.clear();
    }

    /** Makes the classes of the given directories and jars available to bindings. */
    static void setClassPath(List<Path> entries) throws MalformedURLException {
        URL[] urls = new URL[entries.size()];
        for (int i = 0; i < urls.length; i++) {
            urls[i] = entries.get(i).toUri().toURL();
        }
        loader = new URLClassLoader(urls, BASE);
    }

    // ---------------------------------------------------------------- types

    /** True if v is a procedure or variable bound to a member of a Java class. */
    static boolean isBound(Value.T v) {
        return v.javaBound;
    }

    /**
     * The Java class of the type u, or null: the opaque type T of a bound
     * interface is the class that the interface names.
     */
    static ClassDesc classOf(Type.T u) {
        String n = className(u);
        return n == null ? null : ClassDesc.of(n);
    }

    private static String className(Type.T u) {
        if (u instanceof OpaqueType.P op && op.declared != null
                && Scope.toUnit(op.declared) instanceof Module.T m && m.javaClass != null
                && Code.id(op.declared.name).equals("T")) {
            return m.javaClass;
        }
        return null;
    }

    static Class<?> load(String name) {
        Class<?> c;
        try {
            c = Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new Bad("Java class not found: " + name);
        }
        if (!Modifier.isPublic(c.getModifiers()) || !c.getModule().isExported(c.getPackageName())) {
            throw new Bad("Java class is not accessible: " + name);
        }
        return c;
    }

    private static Type.T strip(Type.T t) {
        return Type.stripPacked(Type.check(t));
    }

    private static boolean isReference(Type.T u) {
        return switch (u.info.class_) {
            case Opaque, Object, Ref -> true;
            default -> false;
        };
    }

    /** The class of a value of the reference type t, as far as the compiler knows it. */
    private static Class<?> referenceClass(Type.T t) {
        Type.T u = strip(t);
        if (Type.isEqual(u, Textt.T, null)) {
            return String.class;
        }
        String n = className(u);
        return n == null ? Object.class : load(n);
    }

    /** True if t is INTEGER, LONGINT or a subrange of them. */
    private static boolean isInteger(Type.T t) {
        Type.T b = strip(Type.base(strip(t)));
        return b.info.class_ == Type.Class.Integer || b.info.class_ == Type.Class.Longint;
    }

    private static final JType SHORT =
        JType.withDesc(JType.intType(Short.MIN_VALUE, Short.MAX_VALUE), java.lang.constant.ConstantDescs.CD_short);
    private static final JType BYTE =
        JType.withDesc(JType.intType(Byte.MIN_VALUE, Byte.MAX_VALUE), java.lang.constant.ConstantDescs.CD_byte);
    private static final JType CHAR =
        JType.withDesc(JType.intType(Character.MIN_VALUE, Character.MAX_VALUE), java.lang.constant.ConstantDescs.CD_char);

    /**
     * How an actual of Modula-3 type f is passed to a parameter of Java
     * type p: the type to convert it to (null for a reference, which stays
     * of its Modula-3 type), or NONE if it cannot be passed.
     */
    private static final JType NONE = JType.VOID;

    private static JType paramType(Type.T f, Class<?> p) {
        Type.T u = strip(f);
        if (isReference(u)) {
            // a REFANY can hold what the compiler has no type for (a Java array, a class without a binding)
            return !p.isPrimitive() && (u == strip(Reff.T) || p.isAssignableFrom(referenceClass(u))) ? null : NONE;
        }
        if (u.info.class_ == Type.Class.Procedure) {
            return NONE;
        }
        JType j = JType.of(u);
        switch (j.kind) {
            case LONG, INT -> {
                if (!isInteger(u)) {
                    return NONE;
                }
                return p == long.class ? JType.LONG
                     : p == int.class ? JType.INT
                     : p == short.class ? SHORT
                     : p == byte.class ? BYTE
                     : NONE;
            }
            case BOOL -> {
                return p == boolean.class ? j : NONE;
            }
            case CHAR, WCHAR -> {
                return p == char.class ? j : NONE;
            }
            case FLOAT -> {
                return p == float.class || p == double.class ? j : NONE;
            }
            case DOUBLE -> {
                return p == double.class ? j : NONE;
            }
            default -> {
                return NONE;
            }
        }
    }

    /** True if an actual of Modula-3 type f has exactly the Java type p. */
    private static boolean isExact(Type.T f, Class<?> p) {
        Type.T u = strip(f);
        if (isReference(u)) {
            return p == referenceClass(u);
        }
        if (u.info.class_ == Type.Class.Procedure) {
            return true;
        }
        return switch (JType.of(u).kind) {
            case LONG -> p == long.class;
            case INT -> p == int.class;
            case BOOL -> p == boolean.class;
            case CHAR, WCHAR -> p == char.class;
            case FLOAT -> p == float.class;
            case DOUBLE -> p == double.class;
            default -> false;
        };
    }

    /**
     * The type of the value that a Java member of type r leaves on the
     * stack, to be converted to the Modula-3 type t (null if it needs no
     * conversion, as for void), or NONE if r does not fit t.
     */
    private static JType resultType(Class<?> r, Type.T t) {
        if (t == null) {
            return r == void.class ? null : NONE;
        }
        if (r == void.class) {
            return NONE;
        }
        Type.T u = strip(t);
        if (isReference(u)) {
            if (r.isPrimitive()) {
                return NONE;
            }
            Class<?> c = referenceClass(u);
            if (c.isAssignableFrom(r) && (c != Object.class || JType.of(u).desc.equals(ConstantDescs.CD_Object))) {
                return null;
            }
            boolean castable = r.isAssignableFrom(c) || r.isInterface() || c.isInterface();
            return castable ? JType.ref(ClassDesc.ofDescriptor(r.descriptorString())) : NONE;
        }
        if (u.info.class_ == Type.Class.Procedure) {
            return NONE;
        }
        JType j = JType.of(u);
        boolean integer = (j.kind == JType.Kind.LONG || j.kind == JType.Kind.INT) && isInteger(u);
        boolean character = j.kind == JType.Kind.CHAR || j.kind == JType.Kind.WCHAR;
        if (r == long.class) {
            return integer ? JType.LONG : NONE;
        } else if (r == int.class) {
            return integer ? JType.INT : NONE;
        } else if (r == short.class) {
            return integer ? SHORT : NONE;
        } else if (r == byte.class) {
            return integer ? BYTE : NONE;
        } else if (r == char.class) {
            return integer || character ? CHAR : NONE;
        } else if (r == boolean.class) {
            return j.kind == JType.Kind.BOOL ? JType.BOOL : NONE;
        } else if (r == float.class) {
            return j.kind == JType.Kind.FLOAT ? JType.FLOAT : NONE;
        } else if (r == double.class) {
            return j.kind == JType.Kind.DOUBLE ? JType.DOUBLE : NONE;
        }
        return NONE;
    }

    // ----------------------------------------------------------- resolution

    enum Kind { STATIC, VIRTUAL, INTERFACE, CTOR }

    /**
     * The JVM call of a bound procedure. args has an element per Java
     * parameter (the receiver of an instance method is not one of them):
     * the type to convert the actual to, null for a reference. result is
     * the type that the call leaves on the stack, null if that needs no
     * conversion. adapters has the adapter of each parameter that takes a
     * procedure, null for the others.
     */
    record Binding(Kind kind, ClassDesc owner, boolean ownerIsInterface, String name,
                   MethodTypeDesc desc, JType[] args, ClassDesc[] params, JType result,
                   Adapter[] adapters) {}

    /**
     * How a procedure value is passed for a parameter of the functional
     * interface iface, whose abstract method is sam of type samDesc. sig
     * is the signature of the procedure; args and result are as in Binding,
     * for the opposite direction: args converts the arguments of the Java
     * caller, result the result of the procedure.
     */
    record Adapter(ClassDesc iface, String sam, MethodTypeDesc samDesc, Type.T sig, JType[] args, JType result) {}

    /** The static field of a bound variable. */
    record StaticField(ClassDesc owner, String name, ClassDesc desc, boolean isFinal) {}

    /** The binding of the bound procedure p, null if it has an error. */
    static Binding resolve(Procedure.T p) {
        Object r = bindings.get(p);
        if (r == null) {
            try {
                r = resolve0(p);
            } catch (Bad e) {
                r = e;
            }
            bindings.put(p, r);
        }
        return r instanceof Binding b ? b : null;
    }

    /** The parts of an external name: the class, the member, the descriptor or null. */
    private record ExtName(String cls, String member, String desc) {}

    private static ExtName parseName(Value.T v, int extName) {
        String s = Code.id(extName);
        String desc = null;
        int par = s.indexOf('(');
        if (par >= 0) {
            desc = s.substring(par);
            s = s.substring(0, par);
        }
        Module.T m = (Module.T) Scope.toUnit(v);
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            return new ExtName(s.substring(0, dot), s.substring(dot + 1), desc);
        }
        if (m.javaClass == null) {
            throw new Bad("a Java binding names the class and the member: a.b.C.member");
        }
        return new ExtName(m.javaClass, s, desc);
    }

    private static Binding resolve0(Procedure.T p) {
        Type.T sig = Type.base(p.signature);
        List<Type.T> formals = new ArrayList<>();
        for (Value.T v = ProcType.formals(sig); v != null; v = v.next) {
            Formal.T f = (Formal.T) v;
            if (f.mode != Formal.Mode.mVALUE) {
                throw new Bad("Java procedures take VALUE parameters only");
            }
            formals.add(f.type);
        }
        Type.T result = ProcType.result(sig);
        ExtName n = parseName(p, p.extName);
        Class<?> owner = load(n.cls());
        Module.T m = (Module.T) Scope.toUnit(p);
        boolean ctor = n.member().equals("<init>") || m.javaClass != null && n.desc() == null && n.member().equals("New");
        Map<String, Candidate> all = new LinkedHashMap<>();
        boolean named = false;
        if (ctor) {
            if (Modifier.isAbstract(owner.getModifiers()) && !owner.isInterface()) {
                throw new Bad("Java class " + n.cls() + " is abstract: it has no objects of its own");
            }
            Constructor<?>[] constructors;
            try {
                constructors = owner.getConstructors();
            } catch (LinkageError e) {
                throw new Bad("the constructors of Java class " + n.cls() + " need a class that is missing: "
                              + e.getMessage());
            }
            for (Constructor<?> k : constructors) {
                named = true;
                consider(all, Kind.CTOR, owner, "<init>", k.getParameterTypes(), owner, n.desc(), formals, result);
            }
        } else {
            for (Method k : methods(owner)) {
                if (k.getName().equals(n.member())) {
                    named = true;
                    Kind kind = Modifier.isStatic(k.getModifiers()) ? Kind.STATIC
                              : owner.isInterface() ? Kind.INTERFACE : Kind.VIRTUAL;
                    consider(all, kind, owner, k.getName(), k.getParameterTypes(), k.getReturnType(),
                             n.desc(), formals, result);
                }
            }
        }
        String what = "Java " + (ctor ? "constructor of " + n.cls() : "method " + n.cls() + "." + n.member());
        if (!named) {
            throw new Bad("no public " + what);
        }
        List<Candidate> found = new ArrayList<>(all.values());
        if (found.isEmpty()) {
            throw new Bad("no overload of " + what + " fits this declaration");
        }
        if (found.size() > 1) {
            List<Candidate> exact = new ArrayList<>();
            for (Candidate c : found) {
                if (c.exact) {
                    exact.add(c);
                }
            }
            if (exact.size() != 1) {
                StringJoiner forms = new StringJoiner(", ");
                for (Candidate c : found) {
                    forms.add(c.binding.name + c.binding.desc.descriptorString());
                }
                throw new Bad("ambiguous " + what + ": give the descriptor, one of " + forms);
            }
            return exact.get(0).binding;
        }
        return found.get(0).binding;
    }

    private record Candidate(Binding binding, boolean exact) {}

    /**
     * The public methods of c, and of those with the same parameters only
     * the one with the most specific result. A bridge counts only if it is
     * alone: a public class has bridges for the public methods that it
     * inherits from a class that is not public (StringBuilder.length).
     */
    private static List<Method> methods(Class<?> c) {
        Map<String, Method> out = new LinkedHashMap<>();
        Method[] all;
        try {
            all = c.getMethods();
        } catch (LinkageError e) {
            throw new Bad("the methods of Java class " + c.getName() + " need a class that is missing: " + e.getMessage());
        }
        for (Method k : all) {
            if (k.isSynthetic() && !k.isBridge()) {
                continue;
            }
            String key = k.getName() + MethodType.methodType(void.class, k.getParameterTypes()).toMethodDescriptorString();
            Method o = out.get(key);
            if (o == null
                    || (o.isBridge() != k.isBridge() ? o.isBridge()
                        : o.getReturnType().isAssignableFrom(k.getReturnType()))) {
                out.put(key, k);
            }
        }
        return new ArrayList<>(out.values());
    }

    private static void consider(Map<String, Candidate> out, Kind kind, Class<?> owner, String name,
                                 Class<?>[] ps, Class<?> ret, String desc, List<Type.T> formals, Type.T result) {
        boolean ctor = kind == Kind.CTOR;
        MethodType mt = MethodType.methodType(ctor ? void.class : ret, ps);
        String d = mt.toMethodDescriptorString();
        if (desc != null && !desc.equals(d)) {
            return;
        }
        boolean instance = kind == Kind.VIRTUAL || kind == Kind.INTERFACE;
        int first = instance ? 1 : 0;
        if (formals.size() != ps.length + first) {
            return;
        }
        if (instance) {
            Type.T self = strip(formals.get(0));
            if (!isReference(self) || !owner.isAssignableFrom(referenceClass(self))) {
                return;
            }
        }
        JType[] args = new JType[ps.length];
        ClassDesc[] params = new ClassDesc[ps.length];
        Adapter[] adapters = new Adapter[ps.length];
        boolean exact = true;
        for (int i = 0; i < ps.length; i++) {
            Type.T f = formals.get(i + first);
            if (strip(f).info.class_ == Type.Class.Procedure) {
                adapters[i] = adapter(strip(f), ps[i]);
                if (adapters[i] == null) {
                    return;
                }
            } else {
                JType a = paramType(f, ps[i]);
                if (a == NONE) {
                    return;
                }
                args[i] = a;
            }
            params[i] = ClassDesc.ofDescriptor(ps[i].descriptorString());
            exact &= isExact(f, ps[i]);
        }
        JType r = resultType(ret, result);
        if (r == NONE) {
            return;
        }
        ClassDesc od = ClassDesc.ofDescriptor(owner.descriptorString());
        Binding b = new Binding(kind, od, owner.isInterface(), name, MethodTypeDesc.ofDescriptor(d), args, params, r,
                                adapters);
        out.put(d, new Candidate(b, exact));
    }

    /** The only abstract method of the interface c, or null if c is not a functional interface. */
    private static Method samOf(Class<?> c) {
        if (!c.isInterface() || !Modifier.isPublic(c.getModifiers())) {
            return null;
        }
        Method found = null;
        Method[] all;
        try {
            all = c.getMethods();
        } catch (LinkageError e) {
            return null;
        }
        for (Method k : all) {
            if (Modifier.isAbstract(k.getModifiers()) && !isObjectMethod(k)) {
                if (found != null) {
                    return null;
                }
                found = k;
            }
        }
        return found;
    }

    /** True if the abstract method k restates a public method of Object, like Comparator.equals. */
    private static boolean isObjectMethod(Method k) {
        try {
            Object.class.getMethod(k.getName(), k.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** The adapter for a procedure of type t at a parameter of Java type p, or null if t does not fit p. */
    private static Adapter adapter(Type.T t, Class<?> p) {
        Method sam = samOf(p);
        if (sam == null) {
            return null;
        }
        Type.T sig = Type.base(t);
        Class<?>[] ps = sam.getParameterTypes();
        JType[] args = new JType[ps.length];
        int i = 0;
        for (Value.T v = ProcType.formals(sig); v != null; v = v.next, i++) {
            Formal.T f = (Formal.T) v;
            if (i >= ps.length || f.mode != Formal.Mode.mVALUE) {
                return null;
            }
            args[i] = resultType(ps[i], f.type);
            if (args[i] == NONE) {
                return null;
            }
        }
        if (i != ps.length) {
            return null;
        }
        Class<?> r = sam.getReturnType();
        Type.T res = ProcType.result(sig);
        JType result = null;
        if (r == void.class ? res != null : res == null) {
            return null;
        }
        if (res != null) {
            result = strip(res).info.class_ == Type.Class.Procedure ? NONE : paramType(res, r);
            if (result == NONE) {
                return null;
            }
        }
        MethodType mt = MethodType.methodType(r, ps);
        return new Adapter(ClassDesc.ofDescriptor(p.descriptorString()), sam.getName(),
                           MethodTypeDesc.ofDescriptor(mt.toMethodDescriptorString()), sig, args, result);
    }

    /** The static field of the bound variable v, null if it has an error. */
    static StaticField resolveField(Variable.T v) {
        Object r = fields.get(v);
        if (r == null) {
            try {
                r = resolveField0(v);
            } catch (Bad e) {
                r = e;
            }
            fields.put(v, r);
        }
        return r instanceof StaticField f ? f : null;
    }

    private static StaticField resolveField0(Variable.T v) {
        ExtName n = parseName(v, v.extName);
        Class<?> owner = load(n.cls());
        Field f;
        try {
            f = owner.getField(n.member());
        } catch (NoSuchFieldException | LinkageError e) {
            throw new Bad("no public Java field " + n.cls() + "." + n.member());
        }
        if (!Modifier.isStatic(f.getModifiers())) {
            throw new Bad("Java field " + n.cls() + "." + n.member() + " is not static");
        }
        if (v.type == null || !fieldFits(f, v.type)) {
            throw new Bad("the type of Java field " + n.cls() + "." + n.member() + " is "
                          + f.getType().getName() + ": declare it as " + typeName(f.getType()));
        }
        return new StaticField(ClassDesc.ofDescriptor(owner.descriptorString()), n.member(),
                               ClassDesc.ofDescriptor(f.getType().descriptorString()),
                               Modifier.isFinal(f.getModifiers()));
    }

    /**
     * True if a variable of Modula-3 type t can stand for the field f: a
     * value of the field is a value of t without a conversion and, if the
     * field can be assigned, a value of t is one of the field.
     */
    private static boolean fieldFits(Field f, Type.T t) {
        JType s = resultType(f.getType(), t);
        if (s == NONE) {
            return false;
        }
        boolean writable = !Modifier.isFinal(f.getModifiers());
        Type.T u = strip(t);
        if (isReference(u)) {
            return s == null && (!writable || referenceClass(u) == f.getType());
        }
        JType j = JType.of(u);
        if (s.typeKind() != j.typeKind() || j.min > s.min || j.max < s.max) {
            return false;
        }
        return !writable || j.kind == JType.Kind.WCHAR || (j.min == s.min && j.max == s.max);
    }

    /** The Modula-3 type to declare a Java field of type c with. */
    private static String typeName(Class<?> c) {
        return c == long.class ? "INTEGER"
             : c == int.class ? "Java.Int"
             : c == short.class ? "Java.Short"
             : c == byte.class ? "Java.Byte"
             : c == char.class ? "Java.Char"
             : c == boolean.class ? "BOOLEAN"
             : c == float.class ? "REAL"
             : c == double.class ? "LONGREAL"
             : c == String.class ? "TEXT"
             : "the type T of its binding";
    }

    /** Reports the errors of the bindings of the interface m, once and at their declarations. */
    static void resolveAll(Module.T m, Unit u) {
        if (m.javaClass != null) {
            try {
                load(m.javaClass);
            } catch (Bad e) {
                u.error(e.getMessage(), m.origin);
                return;
            }
        }
        for (Value.T v = Scope.toList(m.localScope); v != null; v = v.next) {
            if (m.javaClass != null && v instanceof Tipe.T t && t.value instanceof OpaqueType.P
                    && !Code.id(v.name).equals("T")) {
                u.error("the opaque type of a Java binding is named T", v.origin);
            } else if (v instanceof Procedure.T p && p.javaBound) {
                if (resolve(p) == null) {
                    u.error(((Bad) bindings.get(p)).getMessage(), p.origin);
                }
            } else if (v instanceof Variable.T x && x.javaBound) {
                if (resolveField(x) == null) {
                    u.error(((Bad) fields.get(x)).getMessage(), x.origin);
                }
            }
        }
    }

    // ---------------------------------------------------------------- calls

    /** A call of the bound procedure p; the result is on the stack. */
    static JType call(Code c, CallExpr.T ce, Procedure.T p) {
        CodeBuilder cb = c.cb;
        Type.T sig = Type.base(p.signature);
        JType res = JType.of(ProcType.result(sig));
        Binding b = resolve(p);
        if (b == null) {
            // the error is reported when the interface is compiled
            Code.pushDefault(cb, res);
            return res;
        }
        Value.T v = ProcType.formals(sig);
        int first = 0;
        if (b.kind == Kind.CTOR) {
            cb.new_(b.owner);
            cb.dup();
        } else if (b.kind != Kind.STATIC) {
            JType self = JType.of(((Formal.T) v).type);
            c.exprAs(ce.args[0], self, true);
            c.nilCheck();
            if (!self.desc.equals(b.owner)) {
                cb.checkcast(b.owner);
            }
            v = v.next;
            first = 1;
        }
        for (int i = 0; v != null; v = v.next, i++) {
            arg(c, (Formal.T) v, b, i, ce.args[i + first]);
        }
        invoke(cb, b);
        if (b.result != null) {
            c.convert(null, b.result, res, true);
        }
        return res;
    }

    private static final ClassDesc CD_Java = ClassDesc.of("m3.i.Java");
    private static final MethodTypeDesc MT_CAUGHT = MethodTypeDesc.of(Code.CD_M3Exception, Code.CD_Throwable);

    /**
     * The JVM call of b, its arguments on the stack. A throwable that ends
     * the call becomes a Modula-3 exception here: the handlers of TRY and
     * the runtime know only those and the JVM exceptions of checked
     * runtime errors.
     */
    private static void invoke(CodeBuilder cb, Binding b) {
        Label start = cb.newLabel(), end = cb.newLabel(), handler = cb.newLabel(), after = cb.newLabel();
        cb.labelBinding(start);
        switch (b.kind) {
            case STATIC -> cb.invokestatic(b.owner, b.name, b.desc, b.ownerIsInterface);
            case VIRTUAL -> cb.invokevirtual(b.owner, b.name, b.desc);
            case INTERFACE -> cb.invokeinterface(b.owner, b.name, b.desc);
            case CTOR -> cb.invokespecial(b.owner, b.name, b.desc);
        }
        cb.labelBinding(end);
        cb.goto_(after);
        cb.labelBinding(handler);
        cb.invokestatic(CD_Java, "caught", MT_CAUGHT);
        cb.athrow();
        cb.exceptionCatch(start, end, handler, Code.CD_Throwable);
        cb.labelBinding(after);
    }

    /** The location of the bound variable v. */
    static Loc fieldLoc(Code c, Variable.T v) {
        JType t = JType.of(v.type);
        StaticField f = resolveField(v);
        if (f == null) {
            // the error is reported when the interface is compiled
            return new Loc.Static(Unit.classDesc((Module.T) Scope.toUnit(v)), Code.id(v.name), t);
        }
        JType jt = JType.withDesc(t, f.desc);
        return f.isFinal ? new Loc.FinalStatic(f.owner, f.name, jt, c) : new Loc.Static(f.owner, f.name, jt);
    }

    private static void arg(Code c, Formal.T f, Binding b, int i, Expr.T e) {
        if (b.adapters[i] != null) {
            adapt(c, f, b.adapters[i], e);
            return;
        }
        JType t = b.args[i] != null ? b.args[i] : JType.of(f.type);
        c.exprAs(e, t, true);
        narrow(c.cb, t, b.params[i]);
    }

    /** Makes the value of type t on the stack one of the Java type p. */
    private static void narrow(CodeBuilder cb, JType t, ClassDesc p) {
        String d = p.descriptorString();
        if (t.kind == JType.Kind.FLOAT && d.equals("D")) {
            cb.f2d();
        } else if (d.equals("S")) {
            cb.i2s();
        } else if (d.equals("B")) {
            cb.i2b();
        } else if (t.desc.equals(ConstantDescs.CD_Object) && !p.equals(ConstantDescs.CD_Object)) {
            cb.checkcast(p);
        }
    }

    /** Pushes the procedure e as an object of the interface of a: NIL stays null. */
    private static void adapt(Code c, Formal.T f, Adapter a, Expr.T e) {
        CodeBuilder cb = c.cb;
        ClassDesc iface = Types.procInterface(Params.descriptor(a.sig));
        Label some = cb.newLabel(), done = cb.newLabel();
        c.exprAs(e, JType.of(f.type), true);
        cb.dup();
        cb.ifnonnull(some);
        cb.pop();
        cb.aconst_null();
        cb.goto_(done);
        cb.labelBinding(some);
        cb.checkcast(iface);
        Procs.lambda(cb, a.iface, a.samDesc, List.of(iface), c.unit.self, c.unit.bridge(a), a.sam);
        cb.labelBinding(done);
    }

    /**
     * The method that an object made by an adapter runs: it takes the
     * procedure value and the arguments of the abstract method, and calls
     * the procedure. A Modula-3 exception that the procedure raises passes
     * through the Java caller and comes back to the call that made the
     * Java code run as what it is.
     */
    static void bridge(Unit u, ClassBuilder clb, String name, Adapter a) {
        MethodTypeDesc d = Params.descriptor(a.sig);
        ClassDesc iface = Types.procInterface(d);
        MethodTypeDesc bd = a.samDesc.insertParameterTypes(0, iface);
        clb.withMethodBody(name, bd, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_SYNTHETIC, cb -> {
            Code c = new Code(u, cb, Frames.INIT);
            cb.aload(0);
            int slot = 1;
            int i = 0;
            for (Value.T v = ProcType.formals(a.sig); v != null; v = v.next, i++) {
                ClassDesc p = a.samDesc.parameterType(i);
                TypeKind k = TypeKind.from(p);
                cb.loadLocal(k, slot);
                slot += k.slotSize();
                c.convert(null, a.args[i] != null ? a.args[i] : JType.ref(p), JType.of(((Formal.T) v).type), true);
            }
            cb.invokeinterface(iface, "call", d);
            ClassDesc r = a.samDesc.returnType();
            Type.T rt = ProcType.result(a.sig);
            if (rt != null) {
                JType res = JType.of(rt);
                if (a.result != null) {
                    c.convert(null, res, a.result, true);
                }
                narrow(cb, a.result != null ? a.result : res, r);
            }
            cb.return_(TypeKind.from(r));
        });
    }
}
