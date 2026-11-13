// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.SourceDebugExtensionAttribute;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Prints the descriptions for a debugger that m3j writes into the classes
 * of the units of a program (see Info), as Modula-3:
 *
 * <pre>
 *   java -cp m3j.jar m3j.debug.Dump [-unit NAME]... [-raw] DIR|JAR...
 * </pre>
 *
 * The classes are those in the directories and jars given. With -unit
 * only the units named are printed (the types of all units are used);
 * with -raw the descriptions are printed as they are in the classes.
 */
public final class Dump {
    private Dump() {}

    public static void main(String[] args) throws IOException {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Set<String> only = new LinkedHashSet<>();
        List<Path> paths = new ArrayList<>();
        boolean raw = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-unit" -> only.add(args[++i]);
                case "-raw" -> raw = true;
                default -> paths.add(Path.of(args[i]));
            }
        }
        if (paths.isEmpty()) {
            System.err.println("usage: java -cp m3j.jar m3j.debug.Dump [-unit NAME]... [-raw] DIR|JAR...");
            System.exit(2);
        }
        Map<String, String> texts = new TreeMap<>();
        for (Path p : paths) {
            read(p, texts);
        }
        TypeTable table = new TypeTable();
        Map<String, Info> infos = new TreeMap<>();
        for (Map.Entry<String, String> e : texts.entrySet()) {
            Info info = Info.parse(Info.unitOf(e.getKey()), e.getValue());
            infos.put(e.getKey(), info);
            table.add(info);
        }
        for (Map.Entry<String, Info> e : infos.entrySet()) {
            Info info = e.getValue();
            if (!only.isEmpty() && !only.contains(info.unit)) {
                continue;
            }
            if (raw) {
                out.println(e.getKey());
                out.print(texts.get(e.getKey()));
                out.println();
            } else {
                print(out, e.getKey(), info, table);
            }
        }
    }

    /** Reads the descriptions of the classes of the units in directory or jar p into texts, by class name. */
    static void read(Path p, Map<String, String> texts) throws IOException {
        if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.walk(p)) {
                for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".class"))::iterator) {
                    take(Files.readAllBytes(f), texts);
                }
            }
        } else {
            try (JarFile jf = new JarFile(p.toFile())) {
                for (var e : Collections.list(jf.entries())) {
                    if (e.getName().endsWith(".class")) {
                        try (var in = jf.getInputStream(e)) {
                            take(in.readAllBytes(), texts);
                        }
                    }
                }
            }
        }
    }

    private static void take(byte[] b, Map<String, String> texts) {
        ClassModel cm = ClassFile.of().parse(b);
        String cls = cm.thisClass().asInternalName().replace('/', '.');
        if (Info.unitOf(cls) == null) {
            return;
        }
        for (SourceDebugExtensionAttribute a : cm.findAttributes(Attributes.sourceDebugExtension())) {
            String t = new String(a.contents(), StandardCharsets.UTF_8);
            if (Info.isInfo(t)) {
                texts.put(cls, t);
            }
        }
    }

    private static final Map<Character, String> KINDS = Map.of(
        'l', "local", 'v', "VALUE", 'r', "VAR", 'o', "READONLY", 'f', "FOR", 'w', "WITH", 'e', "EXCEPT",
        't', "TYPECASE");

    static void print(PrintStream out, String cls, Info info, TypeTable table) {
        out.println(cls + ": " + (cls.startsWith("m3.m.") ? "module " : "interface ") + info.unit);
        if (info.generic != null) {
            out.println("  generic " + info.generic);
        }
        for (Map.Entry<String, String> g : info.globals.entrySet()) {
            out.println("  variable " + g.getKey() + ": " + table.show(g.getValue()));
        }
        for (Info.Method m : info.methods.values()) {
            String what = switch (m.kind()) {
                case 'P' -> "procedure " + m.name();
                case 'B' -> "body of " + m.name();
                case 'C' -> "chunk of " + m.name() + " (in " + m.parent() + ")";
                default -> "loop of " + m.name() + " (in " + m.parent() + ")";
            };
            out.println("  " + what + ": method " + m.method());
            for (Info.Var v : m.vars()) {
                String home = v.inSlot() ? "" : v.inFrame() ? " in the frame as " + v.home() + ", lines "
                    + v.from() + "-" + v.to() : " (no home)";
                out.println("    " + KINDS.getOrDefault(v.kind(), "?") + " " + v.name() + ": "
                    + table.show(v.type()) + home);
            }
        }
        for (String uid : info.types.keySet()) {
            String n = table.name(uid);
            out.println("  type " + uid + (n != null ? " " + n : "") + " = " + structure(uid, table));
        }
    }

    /** The structure of type uid (not its name). */
    private static String structure(String uid, TypeTable table) {
        Info.Type t = table.get(uid);
        if (t instanceof Info.Opaque o) {
            return "<: " + (o.supertype() == null ? "?" : table.show(o.supertype()))
                + (o.concrete() != null ? ", revealed " + o.concrete() : "");
        }
        return new Structure(table).show(uid);
    }

    /** Shows a type by its structure at the top, by names below. */
    private record Structure(TypeTable table) {
        String show(String uid) {
            Info.Type d = table.get(uid);
            if (d == null) {
                return "?";
            }
            return switch (d) {
                case Info.Enum e -> "{" + String.join(", ", e.elements()) + "}";
                case Info.Subrange s -> "[" + table.ordinal(s.base(), s.first()) + ".."
                    + table.ordinal(s.base(), s.last()) + "] OF " + table.show(s.base());
                case Info.Array a -> "ARRAY " + table.show(a.index()) + " OF " + table.show(a.element());
                case Info.OpenArray a -> "ARRAY OF " + table.show(a.element());
                case Info.SetType s -> "SET OF " + table.show(s.base());
                case Info.Record r -> "RECORD" + fields(r.fields()) + " END";
                case Info.Ref r -> (r.traced() ? "REF " : "UNTRACED REF ") + table.show(r.target());
                case Info.ObjectType o -> table.show(o.supertype()) + " OBJECT" + fields(o.fields()) + " END";
                case Info.Opaque o -> "<: " + o.supertype();
                case Info.Packed p -> "BITS " + p.bits() + " FOR " + table.show(p.base());
                case Info.Indirect x -> "VAR " + table.show(x.target());
            };
        }

        private String fields(List<Info.Field> fs) {
            StringBuilder sb = new StringBuilder();
            for (Info.Field f : fs) {
                sb.append(' ').append(f.name()).append(": ").append(table.show(f.type())).append(';');
            }
            return sb.toString();
        }
    }
}
