// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.io.IOException;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * m3jbind: writes the interfaces that bind Java classes to Modula-3.
 *
 * <pre>
 * java -cp m3j.jar m3j.jvm.Bind [-cp PATH] [-d DIR] [-prefix P]
 *      [-name a.b.C=Name]... a.b.C ...
 * </pre>
 *
 * <p>Each class gets an interface DIR/Name.i3 with its public constructors,
 * the public methods that it declares, and its public constants. A method
 * that the class inherits is in the binding of the class that declares it,
 * which the type T of the binding is a subtype of, if that class is bound
 * too. A parameter or result whose class is bound (in this run, or by an
 * interface that is already in DIR) has the type of the binding, any other
 * class is REFANY; so is a class that the interface cannot import, as Java
 * classes may refer to each other but interfaces may not import each
 * other.
 */
public final class Bind {
    private static final Set<String> RESERVED = Set.of(
        "AND", "ANY", "ARRAY", "AS", "BEGIN", "BITS", "BRANDED", "BY", "CASE", "CONST", "DIV", "DO", "ELSE",
        "ELSIF", "END", "EVAL", "EXCEPT", "EXCEPTION", "EXIT", "EXPORTS", "FINALLY", "FOR", "FROM", "GENERIC",
        "IF", "IMPORT", "IN", "INTERFACE", "LOCK", "LOOP", "METHODS", "MOD", "MODULE", "NOT", "OBJECT", "OF",
        "OR", "OVERRIDES", "PROCEDURE", "RAISE", "RAISES", "READONLY", "RECORD", "REF", "REPEAT", "RETURN",
        "REVEAL", "ROOT", "SET", "THEN", "TO", "TRY", "TYPE", "TYPECASE", "UNSAFE", "UNTIL", "UNTRACED",
        "VALUE", "VAR", "WHILE", "WITH",
        "ABS", "ADDRESS", "ADR", "ADRSIZE", "BITSIZE", "BOOLEAN", "BYTESIZE", "CARDINAL", "CEILING", "CHAR",
        "DEC", "DISPOSE", "EXTENDED", "FALSE", "FIRST", "FLOAT", "FLOOR", "INC", "INTEGER", "ISTYPE", "LAST",
        "LONGCARD", "LONGINT", "LONGREAL", "LOOPHOLE", "MAX", "MIN", "MUTEX", "NARROW", "NEW", "NIL", "NULL",
        "NUMBER", "ORD", "REAL", "REFANY", "ROUND", "SUBARRAY", "TEXT", "TRUE", "TRUNC", "TYPECODE", "VAL",
        "WIDECHAR");

    private static final String USAGE =
        "usage: m3j.jvm.Bind [-cp PATH] [-d DIR] [-prefix P] [-name a.b.C=Name]... a.b.C ...";

    private static final Pattern BINDING =
        Pattern.compile("<\\*\\s*EXTERNAL\\s+\"([^\"]+)\"\\s*:\\s*JAVA\\s*\\*>\\s*INTERFACE\\s+(\\w+)");
    private static final Pattern IMPORTS = Pattern.compile("(?m)^\\s*IMPORT\\s+([^;]+);");

    /** A class to bind, and the name of its interface. */
    private record Target(Class<?> cls, String name) {}

    /** The classes that have an interface, and the name of that interface. */
    private final Map<Class<?>, String> known = new HashMap<>();

    /** The imports of the interfaces, to keep them free of cycles. */
    private final Map<String, Set<String>> imports = new HashMap<>();

    /** The names of all the interfaces that a binding might import. */
    private final Set<String> interfaces = new HashSet<>();

    private Bind(Path dir, Map<String, String> names, String prefix) {
        this.dir = dir;
        this.names = names;
        this.prefix = prefix;
    }

    private final Path dir;
    private final Map<String, String> names;
    private final String prefix;

    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    static int run(String[] args) throws IOException {
        Path dir = Path.of(".");
        Map<String, String> names = new HashMap<>();
        String prefix = "";
        List<String> classes = new ArrayList<>();
        Java.reset();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("-cp") || a.equals("-d") || a.equals("-prefix") || a.equals("-name")) {
                if (++i == args.length) {
                    System.err.println(USAGE);
                    return 2;
                }
                switch (a) {
                    case "-cp" -> {
                        List<Path> entries = new ArrayList<>();
                        for (String e : args[i].split(java.io.File.pathSeparator)) {
                            if (!e.isEmpty()) {
                                entries.add(Path.of(e).toAbsolutePath());
                            }
                        }
                        Java.setClassPath(entries);
                    }
                    case "-d" -> dir = Path.of(args[i]);
                    case "-prefix" -> prefix = args[i];
                    default -> {
                        int eq = args[i].indexOf('=');
                        if (eq <= 0) {
                            System.err.println(USAGE);
                            return 2;
                        }
                        names.put(args[i].substring(0, eq), args[i].substring(eq + 1));
                    }
                }
            } else if (a.startsWith("-")) {
                System.err.println(USAGE);
                return 2;
            } else {
                classes.add(a);
            }
        }
        if (classes.isEmpty()) {
            System.err.println(USAGE);
            return 2;
        }
        try {
            new Bind(dir, names, prefix).bind(classes);
        } catch (Java.Bad e) {
            System.err.println("m3jbind: " + e.getMessage());
            return 1;
        } catch (LinkageError e) {
            System.err.println("m3jbind: a class that a bound class needs is missing: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    private void bind(List<String> classes) throws IOException {
        List<Target> targets = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        Set<String> taken = new HashSet<>();
        for (String c : classes) {
            Class<?> k = load(c);
            if (seen.add(k)) {
                String name = names.getOrDefault(k.getName(), names.getOrDefault(c, prefix + simpleName(k)));
                if (!name.matches("[A-Za-z][A-Za-z0-9_]*") || RESERVED.contains(name)) {
                    throw new Java.Bad("not the name of an interface: " + name);
                }
                if (!taken.add(name)) {
                    throw new Java.Bad("two classes have the interface name " + name + ": use -name");
                }
                targets.add(new Target(k, name));
            }
        }
        known.put(Object.class, "JavaObject");
        known.put(Throwable.class, "JavaThrowable");
        addEdges("JavaThrowable", Set.of("JavaObject"));
        scanDirectory(targets);
        for (Target t : targets) {
            known.put(t.cls, t.name);
            imports.put(t.name, new TreeSet<>());
        }
        interfaces.addAll(known.values());
        interfaces.add("Java");
        Set<String> library = libraryNames();
        for (Target t : targets) {
            if (library.contains(t.name)) {
                System.err.println("m3jbind: warning: the library has an interface " + t.name
                                   + " too: use -prefix or -name");
            }
        }
        // the supertypes first: without them an interface does not compile
        Map<Target, String> supers = new LinkedHashMap<>();
        for (Target t : targets) {
            String s = superType(t);
            supers.put(t, s);
            if (!s.equals("REFANY")) {
                imports.get(t.name).add(s.substring(0, s.indexOf('.')));
            }
        }
        Files.createDirectories(dir);
        for (Target t : targets) {
            String text = new Writer(t, supers.get(t)).text();
            Files.write(dir.resolve(t.name + ".i3"), text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** The class of a name in the form of the binary name, or with a dot before a nested class. */
    private static Class<?> load(String name) {
        String n = name;
        while (true) {
            try {
                return Java.load(n);
            } catch (Java.Bad e) {
                int dot = n.lastIndexOf('.');
                if (dot < 0 || !Character.isUpperCase(n.charAt(n.lastIndexOf('.', dot - 1) + 1))) {
                    throw new Java.Bad("Java class not found or not accessible: " + name);
                }
                n = n.substring(0, dot) + "$" + n.substring(dot + 1);
            }
        }
    }

    /** The simple name of c, after the names of the classes around it: Map_Entry. */
    private static String simpleName(Class<?> c) {
        String n = c.getName();
        return n.substring(n.lastIndexOf('.') + 1).replace('$', '_');
    }

    /** Learns the bindings and the imports of the interfaces that are already in the directory. */
    private void scanDirectory(List<Target> targets) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        Set<String> replaced = new HashSet<>();
        for (Target t : targets) {
            replaced.add(t.name);
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.i3")) {
            for (Path p : ds) {
                String text = Files.readString(p, StandardCharsets.ISO_8859_1);
                Matcher m = BINDING.matcher(text);
                if (!m.find() || replaced.contains(m.group(2))) {
                    continue;
                }
                try {
                    known.put(Java.load(m.group(1)), m.group(2));
                } catch (Java.Bad e) {
                    continue; // a class that is gone: nothing can refer to it
                }
                Set<String> used = new TreeSet<>();
                Matcher im = IMPORTS.matcher(text);
                while (im.find()) {
                    used.addAll(Arrays.asList(im.group(1).trim().split("\\s*,\\s*")));
                }
                addEdges(m.group(2), used);
            }
        }
    }

    private void addEdges(String from, Set<String> to) {
        imports.computeIfAbsent(from, k -> new TreeSet<>()).addAll(to);
    }

    private boolean reaches(String from, String to) {
        Deque<String> work = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        work.add(from);
        while (!work.isEmpty()) {
            String n = work.remove();
            if (n.equals(to)) {
                return true;
            }
            if (seen.add(n)) {
                work.addAll(imports.getOrDefault(n, Set.of()));
            }
        }
        return false;
    }

    private static Set<String> libraryNames() throws IOException {
        Set<String> out = new HashSet<>();
        Path root = FrontEnv.bundledRoot();
        if (root == null) {
            return out;
        }
        try (Stream<Path> files = Files.walk(root)) {
            files.forEach(p -> {
                String f = p.getFileName() == null ? "" : p.getFileName().toString();
                if (f.endsWith(".i3")) {
                    out.add(f.substring(0, f.length() - 3));
                }
            });
        }
        return out;
    }

    /** The type that T of the binding of t is a subtype of: the nearest bound ancestor. */
    private String superType(Target t) {
        Class<?> c = t.cls;
        if (c == Object.class) {
            return "REFANY";
        }
        Deque<Class<?>> level = new ArrayDeque<>();
        Set<Class<?>> seen = new HashSet<>();
        add(level, seen, c.getSuperclass());
        level.addAll(Arrays.asList(c.getInterfaces()));
        while (!level.isEmpty()) {
            Class<?> a = level.remove();
            String n = a == Object.class ? null : known.get(a);
            if (n != null) {
                return n + ".T";
            }
            add(level, seen, a.getSuperclass());
            for (Class<?> i : a.getInterfaces()) {
                add(level, seen, i);
            }
        }
        return known.get(Object.class) + ".T";
    }

    private static void add(Deque<Class<?>> level, Set<Class<?>> seen, Class<?> c) {
        if (c != null && seen.add(c)) {
            level.add(c);
        }
    }

    /** A member of a class, with the name that it has in the binding. */
    private record Member(String javaName, String m3Name, Class<?>[] params, Class<?> result, String desc,
                          boolean isStatic, boolean isCtor) {}

    private static String descriptor(Class<?> r, Class<?>[] ps) {
        return MethodType.methodType(r, ps).toMethodDescriptorString();
    }

    private static final Comparator<Class<?>[]> BY_LENGTH = Comparator.comparingInt(a -> a.length);

    /** The public methods of c that the class declares, in an order that does not depend on the JVM. */
    private static List<Method> methods(Class<?> c) {
        Map<String, Method> out = new LinkedHashMap<>();
        for (Method k : c.getDeclaredMethods()) {
            if (!Modifier.isPublic(k.getModifiers()) || (k.isSynthetic() && !isVisibilityBridge(c, k))) {
                continue;
            }
            String key = k.getName() + descriptor(void.class, k.getParameterTypes());
            Method o = out.get(key);
            if (o == null || (o.isSynthetic() != k.isSynthetic() ? o.isSynthetic()
                              : o.getReturnType().isAssignableFrom(k.getReturnType()))) {
                out.put(key, k);
            }
        }
        List<Method> list = new ArrayList<>(out.values());
        list.sort(Comparator.comparing(Method::getName)
                  .thenComparing(Method::getParameterTypes, BY_LENGTH)
                  .thenComparing(m -> descriptor(m.getReturnType(), m.getParameterTypes())));
        return list;
    }

    /**
     * True if the bridge k makes a public method of a superclass that is
     * not public callable from outside (StringBuilder.length), and is not
     * one of the bridges that erasure makes (String.compareTo (Object)).
     */
    private static boolean isVisibilityBridge(Class<?> c, Method k) {
        if (!k.isBridge()) {
            return false;
        }
        for (Class<?> s = c.getSuperclass(); s != null; s = s.getSuperclass()) {
            if (Modifier.isPublic(s.getModifiers())) {
                continue;
            }
            for (Method m : s.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers()) && m.getName().equals(k.getName())
                        && Arrays.equals(m.getParameterTypes(), k.getParameterTypes())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** An identifier of Modula-3 as close to the Java name s as there is. */
    private static String identifier(String s) {
        String t = s.replaceAll("[^A-Za-z0-9_]", "_");
        if (!Character.isLetter(t.charAt(0)) || t.charAt(0) > 'z') {
            t = "x" + t;
        }
        return RESERVED.contains(t) ? t + "_" : t;
    }

    private static String typeSuffix(Class<?> c) {
        return c.isArray() ? typeSuffix(c.getComponentType()) + "Array" : identifier(c.getSimpleName());
    }

    /** The interface of one class. */
    private final class Writer {
        private final Target target;
        private final String superT;
        private final Set<String> used = new HashSet<>();
        private final Set<String> importedHere = new TreeSet<>();

        Writer(Target target, String superT) {
            this.target = target;
            this.superT = superT;
            used.add("T");
            used.addAll(interfaces);
            if (!superT.equals("REFANY") && target.cls != String.class) {
                importedHere.add(superT.substring(0, superT.indexOf('.')));
            }
        }

        private String unique(String name) {
            String n = name;
            if (used.contains(n)) {
                n = name + "_";
            }
            for (int i = 2; !used.add(n); i++) {
                n = name + "_" + i;
            }
            return n;
        }

        private boolean isString() {
            return target.cls == String.class;
        }

        private String selfType() {
            return isString() ? "TEXT" : "T";
        }

        /** The Modula-3 type for values of the Java type c. */
        private String typeOf(Class<?> c) {
            if (c == long.class) {
                return "INTEGER";
            } else if (c == int.class || c == short.class || c == byte.class || c == char.class) {
                importedHere.add("Java");
                return c == int.class ? "Java.Int" : c == short.class ? "Java.Short"
                     : c == byte.class ? "Java.Byte" : "Java.Char";
            } else if (c == boolean.class) {
                return "BOOLEAN";
            } else if (c == float.class) {
                return "REAL";
            } else if (c == double.class) {
                return "LONGREAL";
            } else if (c == String.class) {
                return "TEXT";
            } else if (c == target.cls) {
                return "T";
            } else if (c == Object.class || c.isArray() || !known.containsKey(c)) {
                return "REFANY";
            }
            String n = known.get(c);
            if (!importedHere.contains(n)) {
                if (reaches(n, target.name)) {
                    return "REFANY"; // the two interfaces would import each other
                }
                importedHere.add(n);
                imports.get(target.name).add(n);
            }
            return n + ".T";
        }

        private List<Member> members() {
            List<Member> out = new ArrayList<>();
            Class<?> c = target.cls;
            if (!c.isInterface() && !Modifier.isAbstract(c.getModifiers())) {
                List<Constructor<?>> ctors = new ArrayList<>();
                for (Constructor<?> k : c.getDeclaredConstructors()) {
                    if (Modifier.isPublic(k.getModifiers()) && !k.isSynthetic()) {
                        ctors.add(k);
                    }
                }
                ctors.sort(Comparator.<Constructor<?>, Class<?>[]>comparing(Constructor::getParameterTypes, BY_LENGTH)
                           .thenComparing(k -> descriptor(void.class, k.getParameterTypes())));
                int i = 1;
                for (Constructor<?> k : ctors) {
                    String name = unique(i == 1 ? "New" : "New" + i);
                    out.add(new Member("<init>", name, k.getParameterTypes(), void.class,
                                       descriptor(void.class, k.getParameterTypes()), true, true));
                    i++;
                }
            }
            List<Method> ms = methods(c);
            Map<String, Integer> count = new HashMap<>();
            for (Method m : ms) {
                count.merge(m.getName(), 1, Integer::sum);
            }
            for (Method m : ms) {
                Class<?>[] ps = m.getParameterTypes();
                StringBuilder name = new StringBuilder(identifier(m.getName()));
                if (count.get(m.getName()) > 1) {
                    for (Class<?> p : ps) {
                        name.append('_').append(typeSuffix(p));
                    }
                }
                out.add(new Member(m.getName(), unique(name.toString()), ps, m.getReturnType(),
                                   descriptor(m.getReturnType(), ps), Modifier.isStatic(m.getModifiers()), false));
            }
            return out;
        }

        /** The element type of the Modula-3 array that stands for the Java array type c, null if there is none. */
        private String element(Class<?> c) {
            if (!c.isArray() || c.getComponentType().isArray()) {
                return null;
            }
            Class<?> e = c.getComponentType();
            if (e == byte.class) {
                return "CHAR";
            }
            String t = typeOf(e);
            return t.equals("REFANY") && e != Object.class ? null : t;
        }

        private String procedure(Member m) {
            StringBuilder b = new StringBuilder();
            b.append("<*EXTERNAL \"").append(m.javaName).append(m.desc).append("\"*>\n");
            b.append("PROCEDURE ").append(m.m3Name).append(" (");
            String sep = "";
            if (!m.isStatic) {
                b.append("self: ").append(selfType());
                sep = "; ";
            }
            int i = 1;
            for (Class<?> p : m.params) {
                String e = element(p);
                b.append(sep).append(e != null ? "READONLY a" : "a").append(i++).append(": ")
                 .append(e != null ? "ARRAY OF " + e : typeOf(p));
                sep = "; ";
            }
            b.append(')');
            if (m.isCtor) {
                b.append(": ").append(selfType());
            } else if (m.result != void.class) {
                String e = element(m.result);
                b.append(": ").append(e != null ? "REF ARRAY OF " + e : typeOf(m.result));
            }
            return b.append(";\n").toString();
        }

        /** The declaration of the constant f, or null if it is of a type that a binding has no name for. */
        private String field(Field f) {
            Class<?> t = f.getType();
            boolean named = t.isPrimitive() || t == String.class || t == target.cls;
            if (!named) {
                return null;
            }
            String name = unique(identifier(f.getName()));
            String pragma = name.equals(f.getName()) ? "<*EXTERNAL*>" : "<*EXTERNAL \"" + f.getName() + "\"*>";
            return pragma + " VAR " + name + ": " + (t == target.cls ? selfType() : typeOf(t)) + ";\n";
        }

        /**
         * The fields that the type T lists: the public instance fields that
         * the class declares. A field stays out if it has no name in
         * Modula-3, or if a value of its Modula-3 type could not be assigned
         * to it.
         */
        private String fieldList(List<Field> fields) {
            StringBuilder b = new StringBuilder();
            for (Field f : fields) {
                int mod = f.getModifiers();
                if (!Modifier.isPublic(mod) || Modifier.isStatic(mod) || f.isSynthetic()) {
                    continue;
                }
                String name = f.getName();
                if (!identifier(name).equals(name)) {
                    System.err.println("m3jbind: warning: the field " + target.cls.getName() + "." + name
                                       + " has no name in Modula-3: left out");
                    continue;
                }
                Class<?> c = f.getType();
                String t = typeOf(c);
                if (!t.equals("REFANY") || Modifier.isFinal(mod) || c == Object.class) {
                    b.append("  ").append(name).append(": ").append(t).append(";\n");
                }
            }
            return b.toString();
        }

        String text() {
            List<Member> members = members();
            List<Field> fields = new ArrayList<>(Arrays.asList(target.cls.getDeclaredFields()));
            fields.sort(Comparator.comparing(Field::getName));
            StringBuilder body = new StringBuilder();
            if (!isString()) {
                String list = fieldList(fields);
                body.append("TYPE T <: ").append(superT);
                if (!list.isEmpty()) {
                    body.append(" OBJECT\n").append(list).append("END");
                }
                body.append(";\n\n");
            }
            for (Member m : members) {
                body.append(procedure(m)).append('\n');
            }
            int before = body.length();
            for (Field f : fields) {
                int mod = f.getModifiers();
                if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod) && !f.isSynthetic()) {
                    String d = field(f);
                    if (d != null) {
                        body.append(d);
                    }
                }
            }
            if (body.length() > before) {
                body.append('\n');
            }
            StringBuilder out = new StringBuilder();
            out.append("(* The Java class ").append(target.cls.getName()).append(", as bound by m3jbind. *)\n\n");
            out.append("<*EXTERNAL \"").append(target.cls.getName()).append("\":JAVA*> INTERFACE ")
               .append(target.name).append(";\n\n");
            if (!importedHere.isEmpty()) {
                out.append("IMPORT ").append(String.join(", ", importedHere)).append(";\n\n");
            }
            out.append(body);
            out.append("END ").append(target.name).append(".\n");
            return out.toString();
        }
    }
}
