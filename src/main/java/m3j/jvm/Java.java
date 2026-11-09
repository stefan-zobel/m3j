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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import m3j.front.CallExpr;
import m3j.front.Expr;
import m3j.front.Formal;
import m3j.front.Module;
import m3j.front.ObjectType;
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
 * is an error of the compilation. A call is the JVM instruction that calls
 * the member; the fields that the declaration of T lists are instance fields
 * of the class. The class of the interface has a method for each bound
 * procedure, which stands for it where it is a value.
 *
 * <p>An object type can have the type T of a binding as its supertype. Its
 * class then extends the Java class, or implements the Java interface, and
 * the methods that the type declares with the names of Java methods
 * implement those: each gets a JVM method with the Java name and
 * descriptor that calls the Modula-3 method.
 */
final class Java {
    private Java() {}

    private static final ClassLoader BASE = Java.class.getClassLoader();

    /** The class loader of the classes that bindings name. */
    static ClassLoader loader = BASE;

    /** The procedures resolved so far; an unresolved one maps to a Bad. */
    private static final Map<Procedure.T, Object> bindings = new IdentityHashMap<>();
    private static final Map<Variable.T, Object> fields = new IdentityHashMap<>();
    private static final Map<m3j.front.Field.T, Object> instanceFields = new IdentityHashMap<>();

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
        instanceFields.clear();
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
        String n = Module.javaClassOf(u);
        return n == null ? null : ClassDesc.of(n);
    }

    /**
     * The name of the nearest Java class among the supertypes of u (u
     * itself included), or null if u has none: an object type can extend
     * the type T of a binding.
     */
    private static String ancestorName(Type.T t) {
        for (Type.T u = t == null ? null : strip(t); u != null; ) {
            String n = Module.javaClassOf(u);
            if (n != null) {
                return n;
            }
            Type.T s = switch (u.info.class_) {
                case Object -> ObjectType.super_(u);
                case Opaque -> OpaqueType.super_(u);
                default -> null;
            };
            u = s == null ? null : strip(s);
        }
        return null;
    }

    /** True if t is an object type with a Java class among its supertypes. */
    static boolean isJavaObject(Type.T t) {
        return strip(t).info.class_ == Type.Class.Object && ancestorName(t) != null;
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
        String n = ancestorName(u);
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

    /** Instead of a type: the Java value is an array that a Modula-3 array is copied to or from. */
    private static final JType ARRAY = JType.ref(ConstantDescs.CD_Object);

    /** Instead of a type: the Java value is an array that the storage of a REF ARRAY is copied to or from. */
    private static final JType REF_ARRAY = JType.ref(ConstantDescs.CD_Object);

    private static boolean isArray(Type.T u) {
        return u.info.class_ == Type.Class.Array || u.info.class_ == Type.Class.OpenArray;
    }

    /** True if the Modula-3 array type u has the storage of the Java array type c, and any element of c is one of u. */
    private static boolean arrayFits(Type.T u, Class<?> c) {
        return c.isArray() && JType.of(u).desc.equals(ClassDesc.ofDescriptor(c.descriptorString()))
            && elementFits(strip(Code.arrayElement(u)), c.getComponentType());
    }

    private static boolean elementFits(Type.T u, Class<?> c) {
        if (isReference(u)) {
            return !c.isPrimitive();
        }
        JType j = JType.of(u);
        return switch (j.kind) {
            case LONG -> isInteger(u) && j.min == Long.MIN_VALUE && j.max == Long.MAX_VALUE;
            case INT -> isInteger(u)
                && (c == int.class && j.min == Integer.MIN_VALUE && j.max == Integer.MAX_VALUE
                    || c == short.class && j.min == Short.MIN_VALUE && j.max == Short.MAX_VALUE
                    || c == char.class && j.min == Character.MIN_VALUE && j.max == Character.MAX_VALUE
                    || c == byte.class && (j.min == Byte.MIN_VALUE && j.max == Byte.MAX_VALUE
                                           || j.min == 0 && j.max == 255));
            case BOOL, CHAR, WCHAR, FLOAT, DOUBLE -> true;
            default -> false;
        };
    }

    /** True if u is a REF ARRAY OF E and arrays of E have the storage of the Java array type c. */
    private static boolean refArrayFits(Type.T u, Class<?> c) {
        if (u.info.class_ != Type.Class.Ref || Types.target(u) == null) {
            return false;
        }
        Type.T target = strip(Types.target(u));
        return target.info.class_ == Type.Class.OpenArray && arrayFits(target, c);
    }

    private static JType paramType(Type.T f, Class<?> p) {
        Type.T u = strip(f);
        if (isArray(u)) {
            return arrayFits(u, p) ? ARRAY : NONE;
        }
        if (refArrayFits(u, p)) {
            return REF_ARRAY;
        }
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
        if (isArray(u)) {
            return arrayFits(u, p);
        }
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
        if (isArray(u)) {
            return u.info.class_ == Type.Class.OpenArray && arrayFits(u, r) ? ARRAY : NONE;
        }
        if (refArrayFits(u, r)) {
            return REF_ARRAY;
        }
        if (isReference(u)) {
            if (r.isPrimitive()) {
                return NONE;
            }
            Class<?> c = referenceClass(u);
            if (c.isAssignableFrom(r) && JType.of(u).desc.equals(ClassDesc.ofDescriptor(c.descriptorString()))) {
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
     * conversion. An array is copied: ARRAY in args, REF_ARRAY in result.
     * adapters has the adapter of each parameter that takes a procedure,
     * null for the others.
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

    /** The instance field of a field listed by the type T of a binding. */
    record InstanceField(ClassDesc owner, String name, ClassDesc desc, boolean isFinal) {}

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
            if (f.mode != Formal.Mode.mVALUE && !isArray(strip(f.type))) {
                throw new Bad("a parameter of a Java procedure is VAR or READONLY only if it is an array");
            }
            formals.add(f.type);
        }
        Type.T result = ProcType.result(sig);
        ExtName n = parseName(p, p.extName);
        Class<?> owner = load(n.cls());
        Module.T m = (Module.T) Scope.toUnit(p);
        boolean ctor = n.member().equals("<init>") || m.javaClass != null && n.desc() == null && n.member().equals("New");
        Map<String, Candidate> all = new LinkedHashMap<>();
        List<Class<?>[]> tried = new ArrayList<>();
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
                if (n.desc() == null || n.desc().equals(MethodType.methodType(void.class, k.getParameterTypes()).toMethodDescriptorString())) {
                    tried.add(k.getParameterTypes());
                }
                consider(all, Kind.CTOR, owner, "<init>", k.getParameterTypes(), owner, n.desc(), formals, result);
            }
        } else {
            for (Method k : methods(owner)) {
                if (k.getName().equals(n.member())) {
                    named = true;
                    if (n.desc() == null || n.desc().equals(descriptor(k).descriptorString())) {
                        tried.add(k.getParameterTypes());
                    }
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
            throw new Bad("no overload of " + what + " fits this declaration" + arrayHint(tried, formals));
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

    /**
     * What to say about an array parameter of the wrong element type, if
     * only one of the overloads that were tried has the number of parameters
     * of the declaration.
     */
    private static String arrayHint(List<Class<?>[]> tried, List<Type.T> formals) {
        Class<?>[] only = null;
        for (Class<?>[] ps : tried) {
            if (ps.length == formals.size() || ps.length == formals.size() - 1) {
                if (only != null) {
                    return "";
                }
                only = ps;
            }
        }
        if (only == null) {
            return "";
        }
        int first = formals.size() - only.length;
        for (int i = 0; i < only.length; i++) {
            Type.T f = strip(formals.get(i + first));
            if (isArray(f) && only[i].isArray() && !only[i].getComponentType().isArray() && !arrayFits(f, only[i])) {
                return ": a Java " + only[i].getSimpleName() + " is an ARRAY OF " + typeName(only[i].getComponentType());
            }
        }
        return "";
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
            String key = key(k);
            Method o = out.get(key);
            if (o == null
                    || (o.isBridge() != k.isBridge() ? o.isBridge()
                        : o.getReturnType().isAssignableFrom(k.getReturnType()))) {
                out.put(key, k);
            }
        }
        return new ArrayList<>(out.values());
    }

    /** The name and the parameters of k: what makes one method override another. */
    private static String key(Method k) {
        return k.getName() + MethodType.methodType(void.class, k.getParameterTypes()).toMethodDescriptorString();
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
                if (a == NONE || a == REF_ARRAY) {
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
        Shape s = shape(sig, sam.getParameterTypes(), sam.getReturnType());
        if (s == null) {
            return null;
        }
        return new Adapter(ClassDesc.ofDescriptor(p.descriptorString()), sam.getName(), descriptor(sam), sig,
                           s.args, s.result);
    }

    private static MethodTypeDesc descriptor(Method k) {
        return MethodTypeDesc.ofDescriptor(
            MethodType.methodType(k.getReturnType(), k.getParameterTypes()).toMethodDescriptorString());
    }

    /**
     * How Java calls a Modula-3 procedure or method: args converts each
     * argument to the type of its formal, as a result of a Java member,
     * and result converts the result of the procedure, as an actual of a
     * Java member.
     */
    private record Shape(JType[] args, JType result) {}

    /** The shape of a procedure of signature sig called as a Java method (ps)r, or null if it does not fit. */
    private static Shape shape(Type.T sig, Class<?>[] ps, Class<?> r) {
        JType[] args = new JType[ps.length];
        int i = 0;
        for (Value.T v = ProcType.formals(sig); v != null; v = v.next, i++) {
            Formal.T f = (Formal.T) v;
            if (i >= ps.length || f.mode == Formal.Mode.mVAR
                    || f.mode == Formal.Mode.mREADONLY && strip(f.type).info.class_ != Type.Class.OpenArray) {
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
        Type.T res = ProcType.result(sig);
        JType result = null;
        if (r == void.class ? res != null : res == null) {
            return null;
        }
        if (res != null) {
            result = paramType(res, r);
            if (result == NONE || result == ARRAY) {
                return null;
            }
        }
        return new Shape(args, result);
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

    /** The instance field of the field f of the object type holder, null if it has an error. */
    private static InstanceField instanceField(Type.T holder, m3j.front.Field.T f) {
        Object r = instanceFields.get(f);
        if (r == null) {
            try {
                r = instanceField0(holder, f);
            } catch (Bad e) {
                r = e;
            }
            instanceFields.put(f, r);
        }
        return r instanceof InstanceField k ? k : null;
    }

    private static InstanceField instanceField0(Type.T holder, m3j.front.Field.T f) {
        String cls = Module.javaClassOf(strip(holder));
        String name = Code.id(f.name);
        Class<?> owner = load(cls);
        Field k;
        try {
            k = owner.getField(name);
        } catch (NoSuchFieldException | LinkageError e) {
            throw new Bad("no public Java field " + cls + "." + name);
        }
        if (Modifier.isStatic(k.getModifiers())) {
            throw new Bad("Java field " + cls + "." + name + " is static: bind it as a variable of the interface");
        }
        if (!fieldFits(k, f.tipe)) {
            throw new Bad("the type of Java field " + cls + "." + name + " is "
                          + k.getType().getName() + ": declare it as " + typeName(k.getType()));
        }
        return new InstanceField(ClassDesc.ofDescriptor(owner.descriptorString()), name,
                                 ClassDesc.ofDescriptor(k.getType().descriptorString()),
                                 Modifier.isFinal(k.getModifiers()));
    }

    /**
     * True if a variable of Modula-3 type t can stand for the field f: a
     * value of the field is a value of t without a conversion and, if the
     * field can be assigned, a value of t is one of the field.
     */
    private static boolean fieldFits(Field f, Type.T t) {
        JType s = resultType(f.getType(), t);
        if (s == NONE || s == ARRAY || s == REF_ARRAY) {
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
             : c == Object.class ? "REFANY"
             : c.isArray() ? "REFANY, if it is final"
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
            if (m.javaClass != null && v instanceof Tipe.T t && t.value instanceof OpaqueType.P op) {
                if (Code.id(v.name).equals("T")) {
                    checkFields(op, u);
                } else {
                    u.error("the opaque type of a Java binding is named T", v.origin);
                }
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

    /** Reports the errors of the object type that lists the fields of the type T of a binding. */
    private static void checkFields(OpaqueType.P op, Unit u) {
        Type.T s = strip(op.super_);
        if (s.info.class_ != Type.Class.Object || Module.javaClassOf(s) == null) {
            return;
        }
        for (m3j.front.Method.T k : Types.methods(s)) {
            u.error("the type of a Java binding lists fields only: a procedure of the interface is a method of the class",
                    k.origin);
        }
        for (m3j.front.Field.T f : Types.objectFields(s)) {
            if (f.dfault != null) {
                u.error("a field of a Java binding has no default value: " + Code.id(f.name), f.origin);
            } else if (instanceField(s, f) == null) {
                u.error(((Bad) instanceFields.get(f)).getMessage(), f.origin);
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
        List<CopyBack> back = new ArrayList<>();
        for (int i = 0; v != null; v = v.next, i++) {
            arg(c, (Formal.T) v, b, i, ce.args[i + first], back);
        }
        finish(c, b, ProcType.result(sig), back);
        return res;
    }

    /** The JVM call of b, its arguments on the stack; leaves the result as one of the Modula-3 type rt. */
    private static void finish(Code c, Binding b, Type.T rt, List<CopyBack> back) {
        invoke(c.cb, b);
        for (CopyBack k : back) {
            k.emit(c.cb);
        }
        if (b.result == REF_ARRAY) {
            arrayToRef(c, rt, b.desc.returnType());
        } else if (b.result != null) {
            c.convert(null, b.result, JType.of(rt), true);
        }
    }

    /** What is left to do after a call for an array passed VAR: the copy that Java saw goes back. */
    private record CopyBack(Loc copy, Loc storage, Loc offset, Loc length) {
        void emit(CodeBuilder cb) {
            copy.load(cb);
            cb.iconst_0();
            storage.load(cb);
            offset.load(cb);
            length.load(cb);
            cb.invokestatic(ClassDesc.of("java.lang.System"), "arraycopy",
                MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object, ConstantDescs.CD_int,
                                  ConstantDescs.CD_Object, ConstantDescs.CD_int, ConstantDescs.CD_int));
        }
    }

    private static final ClassDesc CD_Arrays = ClassDesc.of("java.util.Arrays");
    private static final ClassDesc CD_Objects = ConstantDescs.CD_Object.arrayType();

    private static boolean isPrimitiveArray(ClassDesc p) {
        return p.componentType().isPrimitive();
    }

    /** Pushes a copy of the elements of x for the parameter of Java array type p. */
    private static void pushArray(Code c, Formal.T f, Loc.Arr x, ClassDesc p, List<CopyBack> back) {
        CodeBuilder cb = c.cb;
        JType t = JType.of(f.type);
        if (t.kind == JType.Kind.ARRAY) {
            c.checkShape(x, new Loc.Arr(x.storage(), x.offset(), new Loc[0], f.type, t));
        }
        Loc n = c.dim(x, 0);
        x.storage().load(cb);
        x.offset().load(cb);
        x.offset().load(cb);
        n.load(cb);
        cb.iadd();
        ClassDesc a = isPrimitiveArray(p) ? p : CD_Objects;
        cb.invokestatic(CD_Arrays, "copyOfRange", MethodTypeDesc.of(a, a, ConstantDescs.CD_int, ConstantDescs.CD_int));
        if (!a.equals(p)) {
            cb.checkcast(p);
        }
        if (f.mode == Formal.Mode.mVAR) {
            Loc copy = c.newLocal(JType.ref(p));
            cb.dup();
            copy.store(cb);
            back.add(new CopyBack(copy, x.storage(), x.offset(), n));
        }
    }

    /** Replaces the Java array of type p on the stack by a copy of it, of exactly that type. */
    private static void copyArray(CodeBuilder cb, ClassDesc p) {
        if (isPrimitiveArray(p)) {
            cb.invokevirtual(p, "clone", MethodTypeDesc.of(ConstantDescs.CD_Object));
        } else {
            cb.dup();
            cb.arraylength();
            cb.loadConstant(p);
            cb.invokestatic(CD_Arrays, "copyOf",
                MethodTypeDesc.of(CD_Objects, CD_Objects, ConstantDescs.CD_int, ConstantDescs.CD_Class));
        }
        cb.checkcast(p);
    }

    /** Replaces the Java array of type p on the stack by a new object of the type REF ARRAY t with a copy of it: null stays NIL. */
    private static void arrayToRef(Code c, Type.T t, ClassDesc p) {
        CodeBuilder cb = c.cb;
        ClassDesc rc = Types.classOf(t);
        Label some = cb.newLabel(), done = cb.newLabel();
        cb.dup();
        cb.ifnonnull(some);
        cb.pop();
        cb.aconst_null();
        cb.goto_(done);
        cb.labelBinding(some);
        copyArray(cb, p);
        cb.new_(rc);
        cb.dup_x1();
        cb.swap();
        cb.invokespecial(rc, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, p));
        cb.labelBinding(done);
    }

    /** Replaces the REF ARRAY of type t on the stack by a copy of its storage, a Java array of type p: NIL stays null. */
    private static void refToArray(Code c, Type.T t, ClassDesc p) {
        CodeBuilder cb = c.cb;
        Label some = cb.newLabel(), done = cb.newLabel();
        cb.dup();
        cb.ifnonnull(some);
        cb.pop();
        cb.aconst_null();
        cb.goto_(done);
        cb.labelBinding(some);
        cb.getfield(Types.classOf(t), "a", p);
        copyArray(cb, p);
        cb.labelBinding(done);
    }

    /**
     * The method of the class of the interface of p that stands for the
     * bound procedure p where it is a value: it has the signature of p and
     * makes the JVM call that a call of p is.
     */
    static void thunk(Unit u, ClassBuilder clb, Procedure.T p, String name) {
        Binding b = resolve(p);
        Type.T sig = Type.base(p.signature);
        MethodTypeDesc d = Params.descriptor(sig);
        clb.withMethodBody(name, d, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, cb -> {
            Code c = new Code(u, cb, Frames.INIT);
            Value.T v = ProcType.formals(sig);
            int slot = 0;
            if (b.kind == Kind.CTOR) {
                cb.new_(b.owner);
                cb.dup();
            } else if (b.kind != Kind.STATIC) {
                JType self = JType.of(((Formal.T) v).type);
                cb.aload(0);
                c.nilCheck();
                if (!self.desc.equals(b.owner)) {
                    cb.checkcast(b.owner);
                }
                v = v.next;
                slot = 1;
            }
            List<CopyBack> back = new ArrayList<>();
            for (int i = 0; v != null; v = v.next, i++) {
                Formal.T f = (Formal.T) v;
                Params.Layout lay = Params.layout(f);
                Loc l = lay.loc(slot, f);
                slot += lay.slots();
                if (b.adapters[i] != null) {
                    l.load(cb);
                    adapt(c, b.adapters[i]);
                } else if (b.args[i] == ARRAY) {
                    pushArray(c, f, (Loc.Arr) l, b.params[i], back);
                } else {
                    JType ft = JType.of(f.type);
                    JType t = b.args[i] != null ? b.args[i] : ft;
                    l.load(cb);
                    c.convert(null, ft, t, true);
                    narrow(cb, t, b.params[i]);
                }
            }
            finish(c, b, ProcType.result(sig), back);
            cb.return_(TypeKind.from(d.returnType()));
        });
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

    /** The location of the field f of the object that obj loads if holder is the field list of a binding, else null. */
    static Loc fieldLoc(Code c, Loc obj, Type.T holder, m3j.front.Field.T f) {
        Type.T s = strip(holder);
        if (Module.javaClassOf(s) == null) {
            return null;
        }
        InstanceField k = instanceField(s, f);
        JType t = JType.of(f.tipe);
        if (k == null) {
            // the error is reported when the interface is compiled
            return new Loc.Field(obj, Types.classOf(s), Code.id(f.name), t);
        }
        JType jt = JType.withDesc(t, k.desc);
        return k.isFinal ? new Loc.FinalField(obj, k.owner, k.name, jt, c) : new Loc.Field(obj, k.owner, k.name, jt);
    }

    private static void arg(Code c, Formal.T f, Binding b, int i, Expr.T e, List<CopyBack> back) {
        if (b.adapters[i] != null) {
            c.exprAs(e, JType.of(f.type), true);
            adapt(c, b.adapters[i]);
            return;
        }
        if (b.args[i] == ARRAY) {
            pushArray(c, f, c.arr(e), b.params[i], back);
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

    /** Replaces the procedure value on the stack by an object of the interface of a: NIL stays null. */
    private static void adapt(Code c, Adapter a) {
        CodeBuilder cb = c.cb;
        ClassDesc iface = Types.procInterface(Params.descriptor(a.sig));
        Label some = cb.newLabel(), done = cb.newLabel();
        cb.dup();
        cb.ifnonnull(some);
        cb.pop();
        cb.aconst_null();
        cb.goto_(done);
        cb.labelBinding(some);
        threadsStarted(cb);
        cb.checkcast(iface);
        Procs.lambda(cb, a.iface, a.samDesc, List.of(iface), c.unit.self, c.unit.bridge(a), a.sam);
        cb.labelBinding(done);
    }

    /** From here on Java may run Modula-3 code on a thread of its own: mutexes must be atomic. */
    static void threadsStarted(CodeBuilder cb) {
        cb.invokestatic(ClassDesc.of("m3j.rt.Mutex"), "threadsStarted", MethodTypeDesc.of(ConstantDescs.CD_void));
    }

    /** The method that an object made by an adapter runs: it takes the procedure value and the arguments. */
    static void bridge(Unit u, ClassBuilder clb, String name, Adapter a) {
        MethodTypeDesc d = Params.descriptor(a.sig);
        ClassDesc iface = Types.procInterface(d);
        MethodTypeDesc bd = a.samDesc.insertParameterTypes(0, iface);
        clb.withMethodBody(name, bd, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_SYNTHETIC, cb ->
            entry(u, cb, a.sig, new Shape(a.args, a.result), a.samDesc, () -> cb.invokeinterface(iface, "call", d)));
    }

    /**
     * The body of a method that Java calls and Modula-3 implements. Local 0
     * is the procedure value or the object that call needs below the
     * arguments, which follow it in the locals as desc says. A Modula-3
     * exception that the procedure raises passes through the Java caller
     * and comes back to the call that made the Java code run as what it
     * is. So that a thread that Java started can tell what the program did
     * not handle, a JVM exception that stands for a checked runtime error
     * leaves here as that runtime error.
     */
    private static void entry(Unit u, CodeBuilder cb, Type.T sig, Shape s, MethodTypeDesc desc, Runnable call) {
        Code c = new Code(u, cb, Frames.INIT);
        Label start = cb.newLabel(), end = cb.newLabel(), handler = cb.newLabel();
        cb.labelBinding(start);
        cb.aload(0);
        int slot = 1;
        int i = 0;
        for (Value.T v = ProcType.formals(sig); v != null; v = v.next, i++) {
            ClassDesc p = desc.parameterType(i);
            TypeKind k = TypeKind.from(p);
            Type.T ft = ((Formal.T) v).type;
            cb.loadLocal(k, slot);
            if (s.args[i] == ARRAY) {
                copyArray(cb, p);
                cb.iconst_0();
                cb.loadLocal(k, slot);
                cb.arraylength();
            } else if (s.args[i] == REF_ARRAY) {
                arrayToRef(c, ft, p);
            } else {
                c.convert(null, s.args[i] != null ? s.args[i] : JType.ref(p), JType.of(ft), true);
            }
            slot += k.slotSize();
        }
        call.run();
        ClassDesc r = desc.returnType();
        Type.T rt = ProcType.result(sig);
        if (rt != null) {
            JType res = JType.of(rt);
            if (s.result == REF_ARRAY) {
                refToArray(c, rt, r);
            } else {
                if (s.result != null) {
                    c.convert(null, res, s.result, true);
                }
                narrow(cb, s.result != null ? s.result : res, r);
            }
        }
        cb.labelBinding(end);
        cb.return_(TypeKind.from(r));
        cb.labelBinding(handler);
        cb.invokestatic(CD_Java, "caught", MT_CAUGHT);
        cb.athrow();
        cb.exceptionCatch(start, end, handler, Code.CD_Throwable);
    }

    // ------------------------------------------------------------- subtypes

    /** The name of the Java class that is the direct supertype of the object type t, or null. */
    private static String directJava(Type.T t) {
        Type.T s = ObjectType.super_(t);
        return s == null ? null : Module.javaClassOf(strip(s));
    }

    /** The superclass of the class of the object type t if its direct supertype is a Java class, else null. */
    static ClassDesc superClass(Type.T t) {
        String n = directJava(t);
        if (n == null) {
            return null;
        }
        Class<?> c;
        try {
            c = load(n);
        } catch (Bad e) {
            return ClassDesc.of(n); // the error is reported by checkSuper
        }
        return c.isInterface() ? ConstantDescs.CD_Object : ClassDesc.ofDescriptor(c.descriptorString());
    }

    /** True if u is the type of a binding of a Java interface. */
    static boolean isInterface(Type.T u) {
        String n = Module.javaClassOf(u);
        if (n == null) {
            return false;
        }
        try {
            return load(n).isInterface();
        } catch (Bad e) {
            return false;
        }
    }

    /** The interface that the class of the object type t implements, or null. */
    static ClassDesc interfaceOf(Type.T t) {
        String n = directJava(t);
        if (n == null) {
            return null;
        }
        try {
            Class<?> c = load(n);
            return c.isInterface() ? ClassDesc.ofDescriptor(c.descriptorString()) : null;
        } catch (Bad e) {
            return null;
        }
    }

    /**
     * Reports what keeps the direct Java supertype of the object type t from
     * being extended: the class must let a class in another package extend
     * it and call its constructor without parameters.
     */
    static void checkSuper(Unit u, Type.T t) {
        String n = directJava(t);
        if (n == null) {
            return;
        }
        try {
            Class<?> c = load(n);
            if (c.isInterface() && !c.isSealed()) {
                return;
            }
            if (Modifier.isFinal(c.getModifiers()) || c.isSealed()) {
                throw new Bad("Java class " + n + " cannot be extended: it is final or sealed");
            }
            int mods;
            try {
                mods = c.getDeclaredConstructor().getModifiers();
            } catch (NoSuchMethodException | LinkageError e) {
                throw new Bad("Java class " + n + " has no constructor without parameters to extend it with");
            }
            if (!Modifier.isPublic(mods) && !Modifier.isProtected(mods)) {
                throw new Bad("the constructor without parameters of Java class " + n + " is not accessible");
            }
        } catch (Bad e) {
            u.error(e.getMessage(), t.origin);
        }
    }

    /**
     * The methods of c that a subclass can override: public and protected,
     * not static and not final. Those of an interface are joined by the
     * ones of Object that any class has.
     */
    private static List<Method> overridable(Class<?> c) {
        List<Method> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Method k : methods(c)) {
            seen.add(key(k));
            if (!Modifier.isStatic(k.getModifiers()) && !Modifier.isFinal(k.getModifiers())) {
                out.add(k);
            }
        }
        Class<?> top = c.isInterface() ? Object.class : c;
        if (c.isInterface()) {
            for (Method k : methods(Object.class)) {
                if (seen.add(key(k)) && !Modifier.isStatic(k.getModifiers()) && !Modifier.isFinal(k.getModifiers())) {
                    out.add(k);
                }
            }
        }
        try {
            for (Class<?> s = top; s != null; s = s.getSuperclass()) {
                for (Method k : s.getDeclaredMethods()) {
                    int mods = k.getModifiers();
                    if (Modifier.isProtected(mods) && !k.isSynthetic() && seen.add(key(k))
                            && !Modifier.isStatic(mods) && !Modifier.isFinal(mods)) {
                        out.add(k);
                    }
                }
            }
        } catch (LinkageError e) {
            throw new Bad("the methods of Java class " + c.getName() + " need a class that is missing: " + e.getMessage());
        }
        return out;
    }

    private static String javaName(Method k) {
        return k.getName() + descriptor(k).descriptorString();
    }

    /**
     * The Java method that the method m implements, or null if no method
     * of the class has the name of m. A method of that name that fits no
     * Java method, or more than one, is a mistake: the name says that it
     * means one of them.
     */
    private static Method implemented(m3j.front.Method.T m, Class<?> owner, List<Method> all) {
        String name = Code.id(m.name);
        Type.T sig = Type.base(m.signature);
        boolean named = false;
        List<Method> fit = new ArrayList<>();
        StringJoiner forms = new StringJoiner(", ");
        for (Method k : all) {
            if (k.getName().equals(name)) {
                named = true;
                forms.add(javaName(k));
                if (shape(sig, k.getParameterTypes(), k.getReturnType()) != null) {
                    fit.add(k);
                }
            }
        }
        if (!named) {
            return null;
        }
        if (fit.isEmpty()) {
            throw new Bad("method " + name + " does not fit Java method of " + owner.getName() + ": " + forms);
        }
        if (fit.size() > 1) {
            List<Method> exact = new ArrayList<>();
            for (Method k : fit) {
                if (isExactMethod(sig, k)) {
                    exact.add(k);
                }
            }
            if (exact.size() != 1) {
                throw new Bad("method " + name + " fits more than one Java method of " + owner.getName() + ": " + forms);
            }
            return exact.get(0);
        }
        return fit.get(0);
    }

    private static boolean isExactMethod(Type.T sig, Method k) {
        Class<?>[] ps = k.getParameterTypes();
        int i = 0;
        for (Value.T v = ProcType.formals(sig); v != null; v = v.next, i++) {
            if (!isExact(((Formal.T) v).type, ps[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gives the class of the object type t the Java methods that its own
     * methods implement. Each calls the Modula-3 method, which dispatches
     * as any method does, so that an override in a subtype or in a NEW
     * applies to the Java method too. A Java method that returns a
     * supertype of the implemented method's result also has a method of
     * its own: the JVM tells them apart by the descriptor.
     */
    static void overrides(Unit u, Type.T t, ClassBuilder clb, ClassDesc cd, List<m3j.front.Method.T> ms) {
        String n = ancestorName(t);
        if (n == null) {
            return;
        }
        List<Method> all;
        Class<?> owner;
        try {
            owner = load(n);
            all = overridable(owner);
        } catch (Bad e) {
            u.error(e.getMessage(), t.origin);
            return;
        }
        for (m3j.front.Method.T m : ms) {
            if (m.override) {
                continue;
            }
            try {
                Method k = implemented(m, owner, all);
                if (k != null) {
                    override(u, clb, cd, m, k, owner);
                }
            } catch (Bad e) {
                u.error(e.getMessage(), m.origin);
            }
        }
    }

    private static void override(Unit u, ClassBuilder clb, ClassDesc cd, m3j.front.Method.T m, Method k,
                                 Class<?> owner) {
        Type.T sig = Type.base(m.signature);
        Shape s = shape(sig, k.getParameterTypes(), k.getReturnType());
        String target = Types.methodName(m);
        MethodTypeDesc td = Types.methodDescriptor(m);
        Set<String> done = new HashSet<>();
        List<Method> forms = covariant(owner, k);
        forms.add(0, k);
        for (Method o : forms) {
            MethodTypeDesc d = descriptor(o);
            if (done.add(d.descriptorString())) {
                clb.withMethodBody(k.getName(), d, ClassFile.ACC_PUBLIC, cb ->
                    entry(u, cb, sig, s, d, () -> cb.invokevirtual(cd, target, td)));
            }
        }
    }

    /** The overridable public methods of c with the name and the parameters of k that return a supertype of its result. */
    private static List<Method> covariant(Class<?> c, Method k) {
        List<Method> out = new ArrayList<>();
        for (Method o : c.getMethods()) {
            if (key(o).equals(key(k)) && o.getReturnType().isAssignableFrom(k.getReturnType())
                    && !Modifier.isStatic(o.getModifiers()) && !Modifier.isFinal(o.getModifiers())) {
                out.add(o);
            }
        }
        return out;
    }

    /**
     * The errors of NEW (t) for an object type t with a Java class among
     * its supertypes: the Java methods that are abstract must be
     * implemented somewhere on the way down to t.
     */
    static void checkComplete(Unit u, Type.T t, long origin) {
        String n = ancestorName(t);
        if (n == null) {
            return;
        }
        Class<?> owner;
        List<Method> all;
        try {
            owner = load(n);
            all = overridable(owner);
        } catch (Bad e) {
            return; // reported where the type is declared
        }
        Set<String> implemented = new HashSet<>();
        Type.T x = strip(t);
        while (x.info.class_ == Type.Class.Object) {
            for (m3j.front.Method.T m : Types.methods(x)) {
                if (m.override) {
                    continue;
                }
                try {
                    Method k = implemented(m, owner, all);
                    if (k != null) {
                        implemented.add(key(k));
                    }
                } catch (Bad e) {
                    // reported where the method is declared
                }
            }
            x = strip(ObjectType.super_(x));
        }
        if (Module.javaClassOf(x) == null) {
            return; // an opaque type on the way: what it implements is not known here
        }
        Method first = null;
        int missing = 0;
        for (Method k : all) {
            if (Modifier.isAbstract(k.getModifiers()) && !(owner.isInterface() && isObjectMethod(k))
                    && !implemented.contains(key(k))) {
                if (first == null) {
                    first = k;
                }
                missing++;
            }
        }
        if (first != null) {
            u.error("NEW: Java method " + javaName(first) + " of " + owner.getName() + " is not implemented"
                    + (missing > 1 ? " (and " + (missing - 1) + " more)" : ""), origin);
        }
    }
}
