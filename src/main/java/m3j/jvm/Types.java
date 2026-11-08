// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import m3j.front.ArrayType;
import m3j.front.Field;
import m3j.front.Method;
import m3j.front.ObjectType;
import m3j.front.OpaqueType;
import m3j.front.OpenArrayType;
import m3j.front.Procedure;
import m3j.front.RecordType;
import m3j.front.RefType;
import m3j.front.Revelation;
import m3j.front.Scope;
import m3j.front.Type;
import m3j.front.Value;

/**
 * The classes of Modula-3 types: one class per record, reference and object
 * type, named after the type's UID (m3.t.T1a2b3c4d), and one functional
 * interface per procedure descriptor. Structurally equal types have the
 * same UID, so every unit that needs a class generates the same class, and
 * one copy is kept.
 */
final class Types {
    private Types() {}

    static final ClassDesc CD_M3Object = ClassDesc.of("m3j.rt.M3Object");
    static final ClassDesc CD_Rec = ClassDesc.of("m3j.rt.Rec");
    static final ClassDesc CD_Mutex = ClassDesc.of("m3j.rt.Mutex");

    /** the classes generated so far, by descriptor */
    static Map<String, byte[]> out = new LinkedHashMap<>();
    /** the super classes of the generated classes, for stack maps and verification */
    static final Map<ClassDesc, ClassDesc> supers = new HashMap<>();
    static final Set<ClassDesc> interfaces = new HashSet<>();
    /** the UID of the object or reference type of a full revelation -> the UID of the opaque type */
    static final Map<Long, Long> revealed = new HashMap<>();
    private static final Deque<Type.T> pending = new ArrayDeque<>();
    private static final Set<String> scheduled = new HashSet<>();

    static String hex(long uid) {
        return String.format("%08x", uid & 0xFFFFFFFFL);
    }

    /** Records the full revelations of a set (of a unit and its imports). */
    static void noteRevelations(Revelation.Set s) {
        if (s == null) {
            return;
        }
        // before the type check the revelations are a list, after it a hash table
        noteList(s.idents);
        if (s.hash != null) {
            for (Revelation.Node n : s.hash) {
                for (; n != null; n = n.next) {
                    noteList(n.contents);
                }
            }
        }
    }

    private static void noteList(Revelation.List l) {
        for (; l != null; l = l.next) {
            Revelation.T r = l.ident;
            if (r != null && r.equal && r.lhs != null && r.rhs != null) {
                long lhs = Type.globalUID(r.lhs);
                long rhs = Type.globalUID(Type.stripPacked(Type.check(r.rhs)));
                revealed.put(rhs, lhs);
                if (l.local) {
                    // the unit with the full revelation generates the class
                    classOf(r.rhs);
                }
            }
        }
    }

    /** The class of a record, reference or object type. */
    static ClassDesc classOf(Type.T t) {
        Type.T u = Type.stripPacked(Type.check(t));
        if (u == Type.stripPacked(Type.check(m3j.front.Mutex.T))) {
            return CD_Mutex;
        }
        long uid = Type.globalUID(u);
        if (u.info.class_ == Type.Class.Opaque) {
            ClassDesc pc = JType.providedClass(u);
            if (pc != null) {
                return pc;
            }
            // the class of the opaque type is the class of its revealed
            // type, named after the opaque type; if the revelation is
            // visible here, the class is generated here
            Type.T rev = Revelation.lookUp(u);
            if (rev != null) {
                Type.T ru = Type.stripPacked(Type.check(rev));
                revealed.put(Type.globalUID(ru), uid);
                if (ru.info.class_ != Type.Class.Opaque) {
                    return classOf(ru);
                }
            }
            ClassDesc oc = ClassDesc.of("m3.t.T" + hex(uid));
            if (!supers.containsKey(oc)) {
                // for the stack maps and the verifier: an opaque type is at
                // least its declared supertype, and at least the strongest
                // of the partial revelations visible here
                Type.T sup = OpaqueType.super_(u);
                Revelation.TypeSet all = new Revelation.TypeSet();
                Revelation.lookUpAll(u, all);
                for (int i = 0; i < all.cnt && i < all.types.length; i++) {
                    Type.T r = all.types[i];
                    if (r != null && (sup == null || Type.isSubtype(r, sup))) {
                        sup = r;
                    }
                }
                Type.T su = sup == null ? null : Type.stripPacked(Type.check(sup));
                supers.put(oc, su == null || su.info.class_ == Type.Class.Ref && target(su) == null
                    ? ConstantDescs.CD_Object
                    : su.info.class_ == Type.Class.Object && ObjectType.super_(su) == null
                        && objectFields(su).isEmpty() && methods(su).isEmpty()
                        ? CD_M3Object : JType.of(su).desc);
            }
            return oc;
        }
        Long o = revealed.get(uid);
        if (o != null) {
            uid = o;
        }
        String name = "m3.t.T" + hex(uid);
        if (scheduled.add(name)) {
            pending.add(u);
        }
        return ClassDesc.of(name);
    }

    /** Generates the classes of the types used since the last call. */
    static void flush() {
        while (!pending.isEmpty()) {
            Type.T t = pending.removeFirst();
            fingerprints.put(classOf(t), fingerprint(t));
            layouts.put(classOf(t), Layouts.describe(t));
            String n = t instanceof ObjectType.P op ? op.user_name
                : t instanceof RefType.P rp ? rp.user_name : null;
            if (n != null) {
                names.put(classOf(t), n);
            }
            switch (t.info.class_) {
                case Record -> record(t);
                case Ref -> ref(t);
                case Object -> object(t);
                default -> {}
            }
        }
    }

    static ClassFile classFile() {
        return ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(resolver()));
    }

    static ClassHierarchyResolver resolver() {
        return ClassHierarchyResolver.defaultResolver()
            .orElse(Types::known)
            .orElse(ClassHierarchyResolver.ofClassLoading(Java.loader));
    }

    /**
     * The superclass of a class made by the compiler, for the stack maps. A
     * type that the code of a unit is the first to use has its class made
     * only after the unit's class (flush); when the stack maps of the unit
     * merge two such classes, as when a REFANY variable is assigned a REF
     * CHAR on one path and a REF INTEGER on the other, the classes are made
     * now.
     */
    private static ClassHierarchyResolver.ClassHierarchyInfo known(ClassDesc cd) {
        if (!supers.containsKey(cd) && !interfaces.contains(cd)) {
            String pkg = cd.packageName();
            if (scheduled.contains(pkg.isEmpty() ? cd.displayName() : pkg + "." + cd.displayName())) {
                flush();
            }
        }
        if (interfaces.contains(cd)) {
            return ClassHierarchyResolver.ClassHierarchyInfo.ofInterface();
        }
        ClassDesc sup = supers.get(cd);
        return sup == null ? null : ClassHierarchyResolver.ClassHierarchyInfo.ofClass(sup);
    }

    static void put(ClassDesc cd, ClassDesc sup, byte[] b) {
        supers.put(cd, sup);
        Long fp = fingerprints.get(cd);
        String layout = layouts.get(cd);
        String name = names.get(cd);
        if (fp != null || layout != null || name != null) {
            // the fingerprint of the type, for RTTypeFP, the layout of its
            // referents in CM3, for Pickle, and the name of the type, for
            // RTTypeSRC
            b = classFile().transformClass(classFile().parse(b),
                java.lang.classfile.ClassTransform.endHandler(clb -> {
                    int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL;
                    if (fp != null) {
                        clb.withField("M3$FP", ConstantDescs.CD_long, fb -> fb.withFlags(flags)
                            .with(java.lang.classfile.attribute.ConstantValueAttribute.of(fp)));
                    }
                    if (layout != null) {
                        clb.withField("M3$PKL", ConstantDescs.CD_String, fb -> fb.withFlags(flags)
                            .with(java.lang.classfile.attribute.ConstantValueAttribute.of(layout)));
                    }
                    if (name != null) {
                        clb.withField("M3$NAME", ConstantDescs.CD_String, fb -> fb.withFlags(flags)
                            .with(java.lang.classfile.attribute.ConstantValueAttribute.of(name)));
                    }
                }));
        }
        out.putIfAbsent(cd.descriptorString(), b);
    }

    /** the fingerprints of the types of the classes generated */
    private static final Map<ClassDesc, Long> fingerprints = new HashMap<>();

    /** the layouts of the types of the classes generated (Layouts) */
    private static final Map<ClassDesc, String> layouts = new HashMap<>();

    /**
     * the names of the reference and object types of the classes generated
     * that a declaration of the unit compiled names ("M3AST_AS.EXP"), as
     * cm3 puts them in its type cells
     */
    private static final Map<ClassDesc, String> names = new HashMap<>();

    /** The fingerprint of type t as a number (its first byte the highest). */
    static long fingerprint(Type.T t) {
        Type.globalUID(t);
        long v = 0;
        for (int x : t.fp.bytes()) {
            v = (v << 8) | (x & 0xFF);
        }
        return v;
    }

    // ---------------------------------------------------------------- records

    /** The fields of a record type, in order. */
    static List<Field.T> fields(Type.T rec) {
        List<Field.T> l = new ArrayList<>();
        Value.T[] fs = new Value.T[1];
        if (RecordType.split(rec, fs)) {
            for (Value.T v = fs[0]; v != null; v = v.next) {
                l.add((Field.T) v);
            }
        }
        return l;
    }

    private static void record(Type.T t) {
        ClassDesc cd = classOf(t);
        List<Field.T> fs = fields(t);
        supers.put(cd, ConstantDescs.CD_Object);
        byte[] b = classFile().build(cd, clb -> {
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER);
            clb.withSuperclass(ConstantDescs.CD_Object);
            clb.withInterfaceSymbols(CD_Rec);
            for (Field.T f : fs) {
                clb.withField(Code.id(f.name), JType.of(f.tipe).desc, ClassFile.ACC_PUBLIC);
            }
            clb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_PUBLIC, cb -> {
                cb.aload(0);
                cb.invokespecial(ConstantDescs.CD_Object, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
                initFields(cb, cd, fs, false);
                cb.return_();
            });
            copyMethods(clb, cd, fs, t);
        });
        put(cd, ConstantDescs.CD_Object, b);
    }

    /** Initializes the fields of the object in local 0: owned parts and defaults. */
    static void initFields(CodeBuilder cb, ClassDesc cd, List<Field.T> fs, boolean defaults) {
        for (Field.T f : fs) {
            JType ft = JType.of(f.tipe);
            boolean owned = Aggregates.isOwned(ft);
            if (owned) {
                cb.aload(0);
                Aggregates.newValue(cb, f.tipe);
                cb.putfield(cd, Code.id(f.name), ft.desc);
            }
            if (f.dfault == null || !defaults) {
                if (!owned && needsDefault(ft)) {
                    cb.aload(0);
                    Code.pushDefault(cb, ft);
                    cb.putfield(cd, Code.id(f.name), ft.desc);
                }
                if (owned && defaults && currentUnit != null && Code.hasDefaults(f.tipe)) {
                    // the defaults of the records in a field of an object
                    Code c = new Code(currentUnit, cb, Frames.INIT);
                    Loc l = c.fieldLoc(new Loc.Local(0, JType.ref(cd)), cd, f);
                    if (l instanceof Loc.Arr a) {
                        c.applyArrayDefaults(a);
                    } else {
                        Loc r = c.newLocal(l.type());
                        l.load(cb);
                        r.store(cb);
                        c.applyDefaults(r, f.tipe);
                    }
                }
                continue;
            }
            if (!owned && Code.isLiteral(f.dfault, ft)) {
                cb.aload(0);
                Code.literalOf(cb, f.dfault, ft);
                cb.putfield(cd, Code.id(f.name), ft.desc);
                continue;
            }
            // any other default value: compiled like an assignment
            if (currentUnit == null) {
                continue;
            }
            Code c = new Code(currentUnit, cb, Frames.INIT);
            Loc l = c.fieldLoc(new Loc.Local(0, JType.ref(cd)), cd, f);
            if (l instanceof Loc.Arr a) {
                c.assignArr(a, f.dfault);
            } else {
                c.assignTo(l, f.dfault);
            }
        }
    }

    /** The unit being generated, whose context compiles default values. */
    static Unit currentUnit;

    static boolean needsDefault(JType t) {
        return t.isOrdinal() && (t.min > 0 || t.max < 0);
    }

    /** copyFrom, copy and eq of a record class. */
    private static void copyMethods(ClassBuilder clb, ClassDesc cd, List<Field.T> fs, Type.T t) {
        clb.withMethodBody("copyFrom", MethodTypeDesc.of(ConstantDescs.CD_void, cd), ClassFile.ACC_PUBLIC, cb -> {
            for (Field.T f : fs) {
                JType ft = JType.of(f.tipe);
                String n = Code.id(f.name);
                if (Aggregates.isOwned(ft)) {
                    // this.f := src.f, deeply
                    cb.aload(1);
                    cb.getfield(cd, n, ft.desc);
                    cb.aload(0);
                    cb.getfield(cd, n, ft.desc);
                    Aggregates.copyInto(cb, f.tipe);
                } else {
                    cb.aload(0);
                    cb.aload(1);
                    cb.getfield(cd, n, ft.desc);
                    cb.putfield(cd, n, ft.desc);
                }
            }
            cb.return_();
        });
        clb.withMethodBody("copy", MethodTypeDesc.of(cd), ClassFile.ACC_PUBLIC, cb -> {
            cb.new_(cd);
            cb.dup();
            cb.invokespecial(cd, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
            cb.dup();
            cb.aload(0);
            cb.invokevirtual(cd, "copyFrom", MethodTypeDesc.of(ConstantDescs.CD_void, cd));
            cb.areturn();
        });
        // the methods of m3j.rt.Rec
        clb.withMethodBody("assign", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object),
            ClassFile.ACC_PUBLIC, cb -> {
                cb.aload(0);
                cb.aload(1);
                cb.checkcast(cd);
                cb.invokevirtual(cd, "copyFrom", MethodTypeDesc.of(ConstantDescs.CD_void, cd));
                cb.return_();
            });
        clb.withMethodBody("same", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object),
            ClassFile.ACC_PUBLIC, cb -> {
                cb.aload(0);
                cb.aload(1);
                cb.checkcast(cd);
                cb.invokevirtual(cd, "eq", MethodTypeDesc.of(ConstantDescs.CD_boolean, cd));
                cb.ireturn();
            });
        clb.withMethodBody("duplicate", MethodTypeDesc.of(ConstantDescs.CD_Object), ClassFile.ACC_PUBLIC, cb -> {
            cb.aload(0);
            cb.invokevirtual(cd, "copy", MethodTypeDesc.of(cd));
            cb.areturn();
        });
        // as cm3: a record without holes compares as its bits; the fields of
        // REAL types of any other record compare as numbers
        Type.Info info = new Type.Info();
        Type.checkInfo(t, info);
        boolean numeric = !info.isSolid;
        clb.withMethodBody("eq", MethodTypeDesc.of(ConstantDescs.CD_boolean, cd), ClassFile.ACC_PUBLIC, cb -> {
            Label no = cb.newLabel();
            for (Field.T f : fs) {
                JType ft = JType.of(f.tipe);
                String n = Code.id(f.name);
                cb.aload(0);
                cb.getfield(cd, n, ft.desc);
                cb.aload(1);
                cb.getfield(cd, n, ft.desc);
                Aggregates.equalsJump(cb, f.tipe, ft, no, numeric);
            }
            cb.iconst_1();
            cb.ireturn();
            cb.labelBinding(no);
            cb.iconst_0();
            cb.ireturn();
        });
    }

    // ------------------------------------------------------------ references

    /** The type a reference type points to. */
    static Type.T target(Type.T ref) {
        Type.T[] t = new Type.T[1];
        if (RefType.split(ref, t)) {
            return t[0];
        }
        return null;
    }

    private static void ref(Type.T t) {
        ClassDesc cd = classOf(t);
        Type.T target = target(t);
        Type.T tu = target == null ? null : Type.stripPacked(Type.check(target));
        if (tu != null && tu.info.class_ == Type.Class.Record) {
            // REF R is a subclass of the class of R: r^ is the object itself
            ClassDesc rc = classOf(tu);
            byte[] b = classFile().build(cd, clb -> {
                clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SUPER);
                clb.withSuperclass(rc);
                clb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_PUBLIC, cb -> {
                    cb.aload(0);
                    cb.invokespecial(rc, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
                    cb.return_();
                });
            });
            put(cd, rc, b);
            return;
        }
        if (tu != null && tu.info.class_ == Type.Class.OpenArray) {
            Aggregates.openArrayRefClass(cd, tu);
            return;
        }
        // REF T for other T: the referent in the field v
        JType jt = tu == null ? JType.ANYREF : JType.of(tu);
        byte[] b = classFile().build(cd, clb -> {
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SUPER);
            clb.withField("v", jt.desc, ClassFile.ACC_PUBLIC);
            clb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_PUBLIC, cb -> {
                cb.aload(0);
                cb.invokespecial(ConstantDescs.CD_Object, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
                if (tu != null && (Aggregates.isOwned(jt) || needsDefault(jt))) {
                    cb.aload(0);
                    if (Aggregates.isOwned(jt)) {
                        Aggregates.newValue(cb, tu);
                    } else {
                        Code.pushDefault(cb, jt);
                    }
                    cb.putfield(cd, "v", jt.desc);
                }
                cb.return_();
            });
        });
        put(cd, ConstantDescs.CD_Object, b);
    }

    // --------------------------------------------------------------- objects

    static List<Field.T> objectFields(Type.T obj) {
        List<Field.T> l = new ArrayList<>();
        if (obj instanceof ObjectType.P p) {
            for (Value.T v = Scope.toList(p.fields); v != null; v = v.next) {
                if (v instanceof Field.T f) {
                    l.add(f);
                }
            }
        }
        return l;
    }

    static List<Method.T> methods(Type.T obj) {
        List<Method.T> l = new ArrayList<>();
        if (obj instanceof ObjectType.P p) {
            for (Value.T v = Scope.toList(p.methods); v != null; v = v.next) {
                if (v instanceof Method.T m) {
                    l.add(m);
                }
            }
        }
        return l;
    }

    /** The super class of an object type. */
    static ClassDesc superClass(Type.T obj) {
        Type.T s = ObjectType.super_(obj);
        if (s == null) {
            return CD_M3Object;
        }
        Type.T su = Type.stripPacked(Type.check(s));
        if (su.info.class_ == Type.Class.Object && ObjectType.super_(su) == null && objectFields(su).isEmpty()
                && methods(su).isEmpty()) {
            return CD_M3Object; // ROOT
        }
        return classOf(su);
    }

    /** The JVM name of a method: its name and the UID of the type that declares it. */
    static String methodName(Method.T m) {
        Type.T decl = m.parent;
        return Code.id(m.name) + "_" + hex(Type.globalUID(decl));
    }

    /** The JVM descriptor of a method (without the object). */
    static MethodTypeDesc methodDescriptor(Method.T m) {
        return Params.descriptor(m.signature);
    }

    private static void object(Type.T t) {
        ClassDesc cd = classOf(t);
        ClassDesc sup = superClass(t);
        List<Field.T> fs = objectFields(t);
        List<Method.T> ms = methods(t);
        supers.put(cd, sup);
        byte[] b = classFile().build(cd, clb -> {
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER);
            clb.withSuperclass(sup);
            for (Field.T f : fs) {
                clb.withField(Code.id(f.name), JType.of(f.tipe).desc, ClassFile.ACC_PUBLIC);
            }
            clb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_PUBLIC, cb -> {
                cb.aload(0);
                cb.invokespecial(sup, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
                initFields(cb, cd, fs, true);
                cb.return_();
            });
            for (Method.T m : ms) {
                methodBody(clb, cd, m);
            }
        });
        put(cd, sup, b);
    }

    /**
     * The JVM method of a method or an override: it calls the procedure bound
     * to the method, or raises the error of a NIL method.
     */
    static void methodBody(ClassBuilder clb, ClassDesc cd, Method.T m) {
        String name = methodName(declaring(m));
        MethodTypeDesc d = methodDescriptor(declaring(m));
        clb.withMethodBody(name, d, ClassFile.ACC_PUBLIC, cb -> {
            Value.T p = m.dfault == null ? null : Value.base(m.dfault);
            if (p instanceof Procedure.T bound && Java.isBound(bound)) {
                if (currentUnit != null) {
                    currentUnit.error("a procedure bound to a Java member cannot be a method: "
                                      + Code.id(bound.name), m.origin);
                }
                p = null;
            }
            if (p instanceof Procedure.T proc) {
                // the procedure takes the object first
                cb.aload(0);
                MethodTypeDesc pd = Params.descriptor(proc.signature);
                if (!pd.parameterList().isEmpty() && !pd.parameterType(0).equals(cd)) {
                    cb.checkcast(pd.parameterType(0));
                }
                int slot = 1;
                for (ClassDesc pt : d.parameterList()) {
                    TypeKind k = TypeKind.from(pt);
                    cb.loadLocal(k, slot);
                    slot += k.slotSize();
                }
                Procs.invokeStatic(cb, proc);
                cb.return_(TypeKind.from(d.returnType()));
            } else {
                cb.loadConstant(26);
                cb.invokestatic(Code.RT, "error", MethodTypeDesc.of(Code.CD_RuntimeException, ConstantDescs.CD_int));
                cb.athrow();
            }
        });
    }

    /** The method that m overrides, or m itself. */
    static Method.T declaring(Method.T m) {
        if (!m.override) {
            return m;
        }
        // the overridden method: look it up in the supertype
        Value.T[] v = new Value.T[1];
        Type.T[] vis = new Type.T[1];
        Type.T sup = ObjectType.super_(m.parent);
        if (sup != null && ObjectType.lookUp(sup, m.name, v, vis) && v[0] instanceof Method.T sm) {
            return declaring(sm);
        }
        return m;
    }

    // ---------------------------------------------------- procedure types

    /** The longest name of a procedure interface that is spelled out from its descriptor. */
    private static final int MAX_NAME = 96;

    /** A hash of s in hex (the first 16 bytes of its SHA-256). */
    private static String hash(String s) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h, 0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The functional interface of procedures with the given descriptor. */
    static ClassDesc procInterface(MethodTypeDesc d) {
        String s = d.descriptorString();
        StringBuilder sb = new StringBuilder("m3.p.P");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '(' -> {}
                case ')' -> sb.append("$$");
                case '/' -> sb.append('_');
                case ';' -> sb.append('$');
                case '[' -> sb.append('A');
                default -> sb.append(c);
            }
        }
        String name = sb.toString();
        if (name.length() > MAX_NAME) {
            // a class file of this name would not fit in a path of Windows
            name = "m3.p.H" + hash(s);
        }
        ClassDesc cd = ClassDesc.of(name);
        if (scheduled.add(name)) {
            interfaces.add(cd);
            byte[] b = ClassFile.of().build(cd, clb -> {
                clb.withFlags(AccessFlag.PUBLIC, AccessFlag.INTERFACE, AccessFlag.ABSTRACT);
                clb.withSuperclass(ConstantDescs.CD_Object);
                clb.withMethod("call", d, ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, mb -> {});
            });
            out.putIfAbsent(cd.descriptorString(), b);
        }
        return cd;
    }

    static Type.T openArrayElement(Type.T t) {
        return OpenArrayType.is(t) ? ((OpenArrayType.P) Type.stripPacked(Type.check(t))).EltType : null;
    }

    static boolean isArray(Type.T t) {
        return ArrayType.is(t);
    }
}
