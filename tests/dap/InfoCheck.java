// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.attribute.SourceDebugExtensionAttribute;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import m3j.debug.Info;
import m3j.debug.TypeTable;

/**
 * A check of tools/dap_test.py: the descriptions for a debugger that m3j
 * writes into the classes of units (m3j.debug.Info) against the classes,
 * in the directories and jars given.
 *
 *   java -cp m3j.jar:DIR InfoCheck DIR|JAR...
 *
 * For each class of a unit: it has a description, every line of which is
 * of a known kind; every method with line numbers is described (as code
 * of Modula-3); every name of a variable in the LocalVariableTable of a
 * described method (but $frame, $up and the parts NAME$off, NAME$nI of
 * arrays) has a variable in the slot of that name, and every such
 * variable has its name in the table (but variables only dead code uses,
 * whose names the compiler drops); every variable in the frame of its
 * procedure names a field of the class of the frame; every variable of
 * the unit names a static field. The types of the variables that none of
 * the units describes or names are counted, not errors (procedure types
 * are not described).
 */
public final class InfoCheck {
    private static final String TAGS = "gsmvEFGHIJKLMNOPQUVYZ";

    private static final List<String> errors = new ArrayList<>();
    private static int units, methods, vars, unresolved;
    private static final List<String> unresolvedExamples = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        for (String a : args) {
            Map<String, byte[]> classes = read(Path.of(a));
            check(a, classes);
        }
        for (String e : errors.subList(0, Math.min(errors.size(), 40))) {
            System.out.println(e);
        }
        if (!errors.isEmpty()) {
            System.out.println(errors.size() + " errors");
            System.exit(1);
        }
        System.out.println(units + " units, " + methods + " methods, " + vars + " variables; "
            + unresolved + " types of variables not described" + (unresolvedExamples.isEmpty() ? ""
            : " (" + String.join(", ", unresolvedExamples) + ")"));
    }

    private static Map<String, byte[]> read(Path p) throws Exception {
        Map<String, byte[]> m = new HashMap<>();
        if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.walk(p)) {
                for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".class"))::iterator) {
                    m.put(p.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
                }
            }
        } else {
            try (JarFile jf = new JarFile(p.toFile())) {
                for (var e : Collections.list(jf.entries())) {
                    if (e.getName().endsWith(".class")) {
                        try (var in = jf.getInputStream(e)) {
                            m.put(e.getName(), in.readAllBytes());
                        }
                    }
                }
            }
        }
        return m;
    }

    private static void check(String where, Map<String, byte[]> classes) {
        Map<String, Info> infos = new HashMap<>();
        Map<String, ClassModel> models = new HashMap<>();
        TypeTable table = new TypeTable();
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            String cls = e.getKey().substring(0, e.getKey().length() - 6).replace('/', '.');
            String unit = Info.unitOf(cls);
            if (unit == null) {
                continue;
            }
            ClassModel cm = ClassFile.of().parse(e.getValue());
            models.put(cls, cm);
            String text = null;
            for (SourceDebugExtensionAttribute a : cm.findAttributes(Attributes.sourceDebugExtension())) {
                text = new String(a.contents(), StandardCharsets.UTF_8);
            }
            if (!Info.isInfo(text)) {
                // a class that the runtime provides is not compiled
                if (cm.fields().stream().anyMatch(f -> f.fieldName().equalsString("M3$UNIT"))) {
                    errors.add(where + ": " + cls + ": no description");
                }
                continue;
            }
            String[] lines = text.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String l = lines[i];
                if (l.isEmpty() || TAGS.indexOf(l.charAt(0)) < 0
                    || Character.isLowerCase(l.charAt(0)) && (l.length() < 2 || l.charAt(1) != ' ')) {
                    errors.add(where + ": " + cls + ": line " + (i + 1) + " of an unknown kind: " + l);
                }
            }
            Info info = Info.parse(unit, text);
            infos.put(cls, info);
            table.add(info);
        }
        for (Map.Entry<String, Info> e : infos.entrySet()) {
            units++;
            checkUnit(where, e.getKey(), e.getValue(), models.get(e.getKey()), classes, table);
        }
    }

    private static void checkUnit(String where, String cls, Info info, ClassModel cm, Map<String, byte[]> classes,
                                  TypeTable table) {
        String at = where + ": " + cls;
        Set<String> statics = new HashSet<>();
        for (FieldModel f : cm.fields()) {
            statics.add(f.fieldName().stringValue());
        }
        for (Map.Entry<String, String> g : info.globals.entrySet()) {
            vars++;
            if (!statics.contains(g.getKey())) {
                errors.add(at + ": variable " + g.getKey() + " is no field");
            }
            type(g.getValue(), table);
        }
        for (MethodModel mm : cm.methods()) {
            String name = mm.methodName().stringValue();
            CodeAttribute code = (CodeAttribute) mm.code().orElse(null);
            if (code == null) {
                continue;
            }
            Info.Method m = info.methods.get(name);
            boolean hasLines = !code.findAttributes(Attributes.lineNumberTable()).isEmpty()
                && code.findAttributes(Attributes.lineNumberTable()).stream()
                       .anyMatch(t -> !t.lineNumbers().isEmpty());
            if (m == null) {
                if (hasLines) {
                    errors.add(at + "." + name + ": a method with lines is not described");
                }
                continue;
            }
            methods++;
            Map<String, String> slots = new HashMap<>();
            for (LocalVariableTableAttribute t : code.findAttributes(Attributes.localVariableTable())) {
                for (LocalVariableInfo v : t.localVariables()) {
                    slots.put(v.name().stringValue(), v.type().stringValue());
                }
            }
            Set<String> described = new HashSet<>();
            String frame = slots.get("$frame");
            Set<String> frameFields = frame == null ? Set.of() : fields(frame, classes);
            for (Info.Var v : m.vars()) {
                vars++;
                type(v.type(), table);
                if (v.inSlot()) {
                    described.add(v.name());
                    // a variable that only dead code uses is not named
                } else if (v.inFrame()) {
                    if (!frameFields.contains(v.home())) {
                        errors.add(at + "." + name + ": " + v.name() + " is not in the frame as " + v.home());
                    }
                }
            }
            for (String s : slots.keySet()) {
                if (!s.startsWith("$") && !s.contains("$") && !described.contains(s)) {
                    errors.add(at + "." + name + ": the slot " + s + " is not described");
                }
            }
        }
    }

    /** The fields of the class of descriptor d (Lpkg/Name;) in classes. */
    private static Set<String> fields(String d, Map<String, byte[]> classes) {
        byte[] b = classes.get(d.substring(1, d.length() - 1) + ".class");
        Set<String> s = new HashSet<>();
        if (b != null) {
            for (FieldModel f : ClassFile.of().parse(b).fields()) {
                s.add(f.fieldName().stringValue());
            }
        }
        return s;
    }

    private static void type(String t, TypeTable table) {
        if (table.name(t) == null && table.get(t) == null) {
            unresolved++;
            if (unresolvedExamples.size() < 3 && !unresolvedExamples.contains(t)) {
                unresolvedExamples.add(t);
            }
        }
    }
}
