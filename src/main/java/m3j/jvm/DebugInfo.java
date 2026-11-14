// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.SourceDebugExtensionAttribute;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import m3j.front.Addr;
import m3j.front.Bool;
import m3j.front.Card;
import m3j.front.Charr;
import m3j.front.EReel;
import m3j.front.Int;
import m3j.front.LCard;
import m3j.front.LInt;
import m3j.front.LReel;
import m3j.front.Mutex;
import m3j.front.Null;
import m3j.front.ObjectAdr;
import m3j.front.ObjectRef;
import m3j.front.Reel;
import m3j.front.Reff;
import m3j.front.Textt;
import m3j.front.Type;
import m3j.front.WCharr;

/**
 * The description of a unit for a debugger: the Modula-3 view of the
 * class of the unit, which the class file alone does not give. It is
 * written as the SourceDebugExtension attribute of the class of the unit,
 * where a debugger reads it through JDI (ReferenceType
 * .sourceDebugExtension) without needing the class file.
 *
 * <p>The text is ASCII, one item per line, its fields separated by single
 * spaces. The first line is {@code M3J 1} (the format and its version;
 * since it does not start with SMAP, a Java debugger does not take it for
 * a source map of JSR-45). A TYPE below is the name of a builtin type
 * (INTEGER, LONGINT, CARDINAL, LONGCARD, BOOLEAN, CHAR, WIDECHAR, REAL,
 * LONGREAL, EXTENDED, TEXT, MUTEX, REFANY, ROOT, UNTRACED-ROOT, ADDRESS,
 * NULL), else the UID of a type in 8 hex digits. Then, in this order:
 * <pre>
 *   g FILE                  the unit is an instance of the generic unit
 *                           in FILE (a file name without directory); the
 *                           line numbers of the class are lines of FILE
 *   s NAME TYPE             a variable of the unit: the static field NAME
 *                           of the class of the unit
 *   m METHOD P [NAME]       the method METHOD of the class is the
 *                           procedure NAME, by default UNIT.METHOD with
 *                           each $ as a dot (UNIT the name of the unit; a
 *                           nested procedure is UNIT.Outer.Inner)
 *   m METHOD B              the method is the body of the unit
 *   m METHOD C PARENT       the method is a chunk of the procedure or body
 *                           whose method is PARENT (see Split)
 *   m METHOD L PARENT       the method is a loop of the body moved into a
 *                           method of its own (PARENT is $init)
 *   v NAME TYPE KIND HOME [FROM TO]
 *                           a variable of the method of the m line above:
 *                           KIND is l (a local), v, r or o (a formal passed
 *                           by VALUE, VAR or READONLY), f (the variable of
 *                           a FOR), w (a name bound by WITH), e (the
 *                           argument of an exception handler) or t (the
 *                           variable of a TYPECASE arm); HOME is . (the
 *                           slot of the same name in the LocalVariableTable,
 *                           which gives its scope), the name of a field of
 *                           the frame of the procedure ($frame, or $up in
 *                           a nested procedure, see Frames), or - (a WITH
 *                           name for a designator, without a home of its
 *                           own); FROM and TO are the lines of the scope
 *                           of a variable not in a slot (0: from the start)
 *   t FROM TO CLASS...      a TRY EXCEPT in the method of the m line above:
 *                           its statements are on the lines FROM to TO, its
 *                           handlers handle the exceptions of the classes
 *                           CLASS (binary names), * stands for ELSE
 *   r [CLASS...]            the procedure of the method of the m line above
 *                           checks that only the exceptions of the classes
 *                           CLASS (and runtime errors) leave it: any other
 *                           becomes the runtime error of an exception not in
 *                           its RAISES clause
 * </pre>
 * A {@code p} line after the first (and the g line) says that the unit is
 * the main module of a program (the module that exports Main).
 * Methods without an m line are helpers of the compiler. Then come the
 * lines of cm3's WebInfo for the types that the variables need, which
 * start with a capital letter and the UID of the type they are about (but
 * I and L, which belong to the line before them), and name other types as
 * TYPE: E (the name of a type), F (array: index, element, size), G (open
 * array: element, size), H and I (enumeration: number of elements, size;
 * then each element), J (packed: size, base), K and L (record: size,
 * number of fields; then each field: name, offset, size, type), M (set:
 * base, size), N (subrange: base, first, last, size), O and P (untraced
 * and traced reference: target), Q (indirect: target), U and V (untraced
 * and traced object: supertype, number of fields, of methods, of
 * overrides, field size; then its fields as L lines), Y (opaque type:
 * supertype), Z (revelation: concrete type). The source file names, the
 * brands, the procedure types and the methods of objects of WebInfo are
 * left out.
 */
final class DebugInfo {

    static final String MAGIC = "M3J 1";

    /** A variable of a method. */
    static final class Var {
        final String name;
        final long uid;
        final char kind;
        final int from;
        final String home;
        int to;

        Var(String name, long uid, char kind, int from, String home) {
            this.name = name;
            this.uid = uid & 0xFFFFFFFFL;
            this.kind = kind;
            this.from = from;
            this.home = home;
        }
    }

    /** A method of Modula-3 code, its variables and the lines t and r about it. */
    record Method(char kind, String name, String parent, List<Var> vars, List<String> lines) {}

    /** the methods, by their names (a method built twice keeps the last) */
    private final Map<String, Method> methods = new LinkedHashMap<>();
    /** the variables of the unit: name and type */
    private final Map<String, Long> globals = new LinkedHashMap<>();
    /** the name of the unit */
    final String unit;

    DebugInfo(String unit) {
        this.unit = unit;
    }

    /** the WebInfo of the unit, or null */
    String webInfo;
    /** the generic unit of an instance, or null */
    String genericFile;
    /** whether the unit is the main module of a program */
    boolean main;

    void method(String method, char kind, String name, String parent, Scopes s) {
        methods.put(method, new Method(kind, name, parent, List.copyOf(s.all), List.copyOf(s.lines)));
    }

    /** The binary name of the class cd. */
    static String className(java.lang.constant.ClassDesc cd) {
        String p = cd.packageName();
        return p.isEmpty() ? cd.displayName() : p + "." + cd.displayName();
    }

    void global(String name, long uid) {
        globals.put(name, uid & 0xFFFFFFFFL);
    }

    static String hex(long uid) {
        return Types.hex(uid);
    }

    /** The class b with this description as its SourceDebugExtension. */
    byte[] attach(byte[] b) {
        ClassModel cm = ClassFile.of().parse(b);
        Set<String> present = new HashSet<>();
        for (MethodModel mm : cm.methods()) {
            present.add(mm.methodName().stringValue());
        }
        String text = text(present);
        return Types.classFile().transformClass(cm, ClassTransform.endHandler(clb ->
            clb.with(SourceDebugExtensionAttribute.of(text.getBytes(StandardCharsets.US_ASCII)))));
    }

    /** The text of the description, with the methods of names in present. */
    String text(Set<String> present) {
        StringBuilder body = new StringBuilder();
        Set<Long> used = new HashSet<>();
        for (Map.Entry<String, Long> g : globals.entrySet()) {
            body.append("s ").append(g.getKey()).append(' ').append(type(g.getValue())).append('\n');
            used.add(g.getValue());
        }
        // the methods in the order they were made, each chunk after the
        // method of its procedure, in the order of their numbers (Split
        // makes them in no fixed order)
        List<Map.Entry<String, Method>> order = new ArrayList<>();
        for (Map.Entry<String, Method> e : methods.entrySet()) {
            if (e.getValue().kind() != 'C') {
                order.add(e);
                methods.entrySet().stream()
                    .filter(c -> c.getValue().kind() == 'C' && c.getValue().parent().equals(e.getKey()))
                    .sorted(java.util.Comparator.comparingInt(c -> chunkNumber(c.getKey())))
                    .forEach(order::add);
            }
        }
        for (Map.Entry<String, Method> e : order) {
            if (!present.contains(e.getKey())) {
                continue;
            }
            Method m = e.getValue();
            body.append("m ").append(e.getKey()).append(' ').append(m.kind());
            if (m.parent() != null) {
                body.append(' ').append(m.parent());
            } else if (m.kind() == 'P' && !m.name().equals(unit + "." + e.getKey().replace('$', '.'))) {
                body.append(' ').append(m.name());
            }
            body.append('\n');
            for (Var v : m.vars()) {
                body.append("v ").append(v.name).append(' ').append(type(v.uid)).append(' ').append(v.kind)
                    .append(' ').append(v.home);
                if (!v.home.equals(".")) {
                    body.append(' ').append(v.from).append(' ').append(v.to);
                }
                body.append('\n');
                used.add(v.uid);
            }
            for (String l : m.lines()) {
                body.append(l).append('\n');
            }
        }
        if (webInfo != null) {
            types(body, used);
        }
        StringBuilder sb = new StringBuilder(MAGIC).append('\n');
        if (genericFile != null) {
            sb.append("g ").append(genericFile).append('\n');
        }
        if (main) {
            sb.append("p\n");
        }
        sb.append(body);
        return ascii(sb.toString());
    }

    /** The number of the chunk of method name (P$$N). */
    private static int chunkNumber(String name) {
        try {
            return Integer.parseInt(name.substring(name.lastIndexOf("$$") + 2));
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** A type in the description: the name of a builtin type, else the UID. */
    private static String type(long uid) {
        String b = builtins().get(uid & 0xFFFFFFFFL);
        return b != null ? b : hex(uid);
    }

    /**
     * Appends the lines of WebInfo of the types that the variables of the
     * unit (the UIDs in used) and the classes of types generated with the
     * unit (roots) need: the types they name, and the types that those
     * name, without the brands; the UIDs of the types appended are added
     * to used. Procedure types are left out (with the formals and raises
     * that follow them), as are the methods and overrides of objects.
     */
    private void types(StringBuilder out, Set<Long> used) {
        // the blocks of lines of each type: its line and the lines that
        // follow it without a UID of their own (I, L, S, T, W, X)
        Map<Long, List<String>> blocks = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        List<String> block = null;
        for (String line : webInfo.split("\\R")) {
            if (line.isEmpty()) {
                continue;
            }
            char tag = line.charAt(0);
            if (tag >= 'E' && tag <= 'Z' && "ILSTWX".indexOf(tag) < 0) {
                String[] t = line.split(" ", -1);
                long uid = parseUid(t[0].substring(1));
                String l = withoutBrand(line, t);
                if (uid < 0 || tag == 'R' || !seen.add(l)) {
                    // a type declared twice is described once
                    block = null;
                    continue;
                }
                block = blocks.computeIfAbsent(uid, k -> new ArrayList<>());
                block.add(l);
            } else if (block != null && (tag == 'I' || tag == 'L')) {
                block.add(line);
            }
        }
        java.util.ArrayDeque<Long> todo = new java.util.ArrayDeque<>(used);
        todo.addAll(roots);
        Set<Long> done = new HashSet<>();
        while (!todo.isEmpty()) {
            long uid = todo.removeFirst();
            if (!done.add(uid)) {
                continue;
            }
            used.add(uid);
            List<String> b = blocks.get(uid);
            if (b == null || builtins().containsKey(uid)) {
                continue;
            }
            for (String line : b) {
                String[] t = line.split(" ", -1);
                for (int i : referencePositions(line.charAt(0), t.length)) {
                    long r = parseUid(t[i]);
                    if (r >= 0) {
                        todo.add(r);
                        t[i] = type(r);
                    }
                }
                out.append(String.join(" ", t)).append('\n');
            }
        }
    }

    /** the UIDs of the types whose classes were generated with the unit */
    private final List<Long> roots = new ArrayList<>();

    void roots(List<Long> uids) {
        for (long u : uids) {
            roots.add(u & 0xFFFFFFFFL);
        }
    }

    /**
     * The positions of the fields of a line of WebInfo with tag and n
     * fields (the first is the tag with the UID of the type that the line
     * is about) that name other types.
     */
    private static int[] referencePositions(char tag, int n) {
        int[] at = switch (tag) {
            case 'F' -> new int[] {1, 2};
            case 'G', 'M', 'N', 'O', 'P', 'Q', 'U', 'V', 'Y', 'Z' -> new int[] {1};
            case 'J' -> new int[] {2};
            case 'L' -> new int[] {n - 1};
            default -> new int[0];
        };
        return java.util.Arrays.stream(at).filter(i -> i > 0 && i < n).toArray();
    }

    /** A UID of 8 hex digits, else -1. */
    private static long parseUid(String s) {
        if (s.length() != 8) {
            return -1;
        }
        try {
            return Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** A line of WebInfo (split in t) without the brand of a reference or an object type. */
    private static String withoutBrand(String line, String[] t) {
        int keep = switch (line.charAt(0)) {
            case 'O', 'P' -> 2;     // tag and UID, target
            case 'U', 'V' -> 6;     // tag and UID, super, fields, methods, overrides, size
            default -> Integer.MAX_VALUE;
        };
        return t.length <= keep ? line : String.join(" ", java.util.Arrays.copyOf(t, keep));
    }

    /** Characters other than printable ASCII as \\uXXXX (names are ASCII, this is for safety). */
    private static String ascii(String s) {
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = c == '\n' || c >= ' ' && c < 0x7F && c != '\\';
            if (!ok && sb == null) {
                sb = new StringBuilder(s.substring(0, i));
            }
            if (sb != null) {
                if (ok) {
                    sb.append(c);
                } else {
                    sb.append(String.format("\\u%04x", (int) c));
                }
            }
        }
        return sb == null ? s : sb.toString();
    }

    private static Map<Long, String> builtins;

    /** The UIDs of the builtin types, and their names. */
    private static Map<Long, String> builtins() {
        if (builtins == null) {
            Map<Long, String> m = new TreeMap<>();
            Object[][] all = {
                {Int.T, "INTEGER"}, {LInt.T, "LONGINT"}, {Card.T, "CARDINAL"}, {LCard.T, "LONGCARD"},
                {Bool.T, "BOOLEAN"}, {Reel.T, "REAL"}, {LReel.T, "LONGREAL"}, {EReel.T, "EXTENDED"},
                {Charr.T, "CHAR"}, {WCharr.T, "WIDECHAR"}, {Null.T, "NULL"}, {Addr.T, "ADDRESS"},
                {Reff.T, "REFANY"}, {ObjectRef.T, "ROOT"}, {ObjectAdr.T, "UNTRACED-ROOT"},
                {Textt.T, "TEXT"}, {Mutex.T, "MUTEX"},
            };
            for (Object[] x : all) {
                m.put(Type.globalUID((Type.T) x[0]) & 0xFFFFFFFFL, (String) x[1]);
            }
            builtins = m;
        }
        return builtins;
    }

    /** The file of a generic unit from the name that the front end gives an instance ("I.m3 => dir/G.mg"). */
    static String genericOf(String filename) {
        if (filename == null) {
            return null;
        }
        int a = filename.lastIndexOf(" => ");
        if (a < 0) {
            return null;
        }
        String f = filename.substring(a + 4);
        int s = Math.max(f.lastIndexOf('/'), f.lastIndexOf('\\'));
        return f.substring(s + 1);
    }

    /**
     * The variables of the scopes of a method that are open, and the lines
     * about its TRY statements and its RAISES clause.
     */
    static final class Scopes {
        final List<Var> all = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        private final java.util.ArrayDeque<List<Var>> open = new java.util.ArrayDeque<>();

        void open() {
            open.push(new ArrayList<>());
        }

        boolean innermostEmpty() {
            return open.isEmpty() || open.peek().isEmpty();
        }

        void close(int line) {
            if (open.isEmpty()) {
                return;
            }
            for (Var v : open.pop()) {
                v.to = Math.max(line, v.from);
            }
        }

        boolean add(Var v) {
            if (open.isEmpty()) {
                return false;
            }
            open.peek().add(v);
            all.add(v);
            return true;
        }
    }
}
