// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

import java.io.File;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Checks the LocalVariableTable of every method of the classes in the
 * given jars and directories, for tools/lvt_test.py:
 *
 *   java Check JAR-OR-DIRECTORY...
 *
 * An entry must lie in the code (its start before the end, as HotSpot
 * asks), its slot below max_locals, its name a legal name and no entry may
 * come twice; every load, store and iinc of its slot within its range must
 * be of the kind of its descriptor, and none may touch the second half of
 * a long or a double. Prints the number of methods and entries and the
 * errors (the first 50); the exit status is 1 if there is one.
 */
public final class Check {
    private record Access(int bci, int slot, TypeKind kind) {}

    private static int methods, named, entries, errors;

    public static void main(String[] args) throws Exception {
        for (String a : args) {
            File f = new File(a);
            if (f.isDirectory()) {
                try (var s = Files.walk(f.toPath())) {
                    for (var p : (Iterable<java.nio.file.Path>) s::iterator) {
                        if (p.toString().endsWith(".class")) {
                            check(p.toString(), Files.readAllBytes(p));
                        }
                    }
                }
            } else {
                try (JarFile jf = new JarFile(f)) {
                    for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
                        JarEntry je = e.nextElement();
                        if (je.getName().endsWith(".class")) {
                            check(je.getName(), jf.getInputStream(je).readAllBytes());
                        }
                    }
                }
            }
        }
        System.out.println(methods + " methods, " + named + " with names, " + entries + " entries, "
            + errors + " errors");
        System.exit(errors > 0 || entries == 0 ? 1 : 0);
    }

    private static void check(String file, byte[] bytes) {
        ClassModel cm = ClassFile.of().parse(bytes);
        for (MethodModel mm : cm.methods()) {
            if (mm.code().isEmpty()) {
                continue;
            }
            methods++;
            String where = file + " " + mm.methodName().stringValue();
            CodeAttribute code = (CodeAttribute) mm.code().get();
            List<Access> accesses = new ArrayList<>();
            int bci = 0;
            for (CodeElement e : code) {
                if (e instanceof Instruction i) {
                    switch (i) {
                        case LoadInstruction l -> accesses.add(new Access(bci, l.slot(), l.typeKind()));
                        case StoreInstruction s -> accesses.add(new Access(bci, s.slot(), s.typeKind()));
                        case IncrementInstruction n -> accesses.add(new Access(bci, n.slot(), TypeKind.INT));
                        default -> {
                        }
                    }
                    bci += i.sizeInBytes();
                }
            }
            if (bci != code.codeLength()) {
                error(where, "the instructions take " + bci + " bytes, not " + code.codeLength());
                continue;
            }
            var lvt = code.findAttribute(Attributes.localVariableTable());
            if (lvt.isEmpty()) {
                continue;
            }
            named++;
            Set<String> seen = new HashSet<>();
            for (LocalVariableInfo v : lvt.get().localVariables()) {
                entries++;
                String name = v.name().stringValue();
                String desc = v.type().stringValue();
                String what = where + " " + name + " " + desc + " slot " + v.slot() + " at " + v.startPc()
                    + "+" + v.length();
                TypeKind kind = TypeKind.fromDescriptor(desc);
                int start = v.startPc(), end = v.startPc() + v.length();
                if (start >= code.codeLength() || end > code.codeLength()) {
                    error(what, "outside of the code (" + code.codeLength() + " bytes)");
                }
                if (v.slot() + kind.slotSize() > code.maxLocals()) {
                    error(what, "beyond max_locals " + code.maxLocals());
                }
                if (name.isEmpty() || name.chars().anyMatch(c -> c == '.' || c == ';' || c == '[' || c == '/')) {
                    error(what, "not a legal name");
                }
                if (!seen.add(v.slot() + " " + name + " " + start + " " + end)) {
                    error(what, "twice");
                }
                for (Access x : accesses) {
                    if (x.bci() < start || x.bci() >= end) {
                        continue;
                    }
                    if (x.slot() == v.slot() && x.kind() != kind.asLoadable()) {
                        error(what, "accessed as " + x.kind() + " at " + x.bci());
                    }
                    if (kind.slotSize() == 2 && x.slot() == v.slot() + 1) {
                        error(what, "its second slot accessed at " + x.bci());
                    }
                }
            }
        }
    }

    private static void error(String where, String what) {
        if (++errors <= 50) {
            System.out.println("error: " + where + ": " + what);
        }
    }
}
