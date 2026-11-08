// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import m3j.fingerprint.Fingerprint;
import m3j.front.M3;
import m3j.front.M3Compiler;
import m3j.front.M3Front;
import m3j.middle.M3CG;
import m3j.middle.M3CG_BinWr;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.Target;
import m3j.middle.TargetMap;

/**
 * Runs the front end on the units of a package that cm3 has built, the way
 * cm3's builder runs it, and writes the intermediate code of each unit as
 * M3CG text (the format of m3cgcat).
 *
 * <p>Usage: {@code java -cp m3j.jar m3j.tools.FrontDump -install=DIR
 * [-pkg=NAME=DIR]... [-out=DIR] PACKAGE}.
 *
 * <p>PACKAGE is the package directory; cm3 must have built it with
 * {@code cm3 -keep -commands}, its output in PACKAGE/build.log. The
 * front-end runs are taken from the lines {@code m3front FILE OPTIONS...}
 * of that log, in order, with the same options; the units are found
 * through the {@code .M3EXPORTS} files of the package and of the packages
 * it imports (in DIR/pkg of the cm3 installation, or as given with
 * {@code -pkg}), and the sizes of opaque object types through the link
 * info ({@code .m3x}) of the imported packages and the units compiled
 * before, as in a fresh build by cm3. The code of the unit
 * Foo.m3 (Foo.i3) is written to OUT/Foo.mc.txt (Foo.ic.txt); OUT defaults
 * to PACKAGE/AMD64_NT. All runs share the state of one front end, as in
 * cm3. Diagnostics go to stdout as cm3 writes them.
 */
public final class FrontDump {
    private FrontDump() {}

    private static final String BUILD = "AMD64_NT";

    /** M3_FRONT_FLAGS of cm3's configuration (cm3cfg.common) in the C mode. */
    private static final String[] FRONT_FLAGS = {"-unfold_nested_procs", "-check_procs"};

    /** A unit known to the builder: its path as the front end sees it. */
    private record Unit(String path) {}

    /** The object type sizes from the link info ("O" lines). */
    private record Magic(long superType, long dataSize, long dataAlign, long methodSize) {}

    public static void main(String[] args) throws IOException {
        String install = null;
        String out = null;
        String pkgDir = null;
        Map<String, Path> pkgs = new HashMap<>();
        for (String a : args) {
            if (a.startsWith("-install=")) {
                install = a.substring(9);
            } else if (a.startsWith("-out=")) {
                out = a.substring(5);
            } else if (a.startsWith("-pkg=")) {
                String[] nv = a.substring(5).split("=", 2);
                pkgs.put(nv[0], Path.of(nv[1]));
            } else if (pkgDir == null) {
                pkgDir = a;
            } else {
                usage();
            }
        }
        if (install == null || pkgDir == null) {
            usage();
        }
        Path pkg = Path.of(pkgDir);
        Path build = pkg.resolve(BUILD);
        Path outDir = out == null ? build : Path.of(out);
        Files.createDirectories(outDir);

        // the front-end runs of cm3
        List<String[]> runs = new ArrayList<>();
        for (String line : Files.readAllLines(pkg.resolve("build.log"), StandardCharsets.ISO_8859_1)) {
            if (line.startsWith("m3front ") && !line.startsWith("m3front failed")) {
                runs.add(line.substring(8).trim().split(" +"));
            }
        }

        // the target, as Builder.SetupNamingConventions... sets it up
        M3.target = new Target.State();
        M3.targetMap = new TargetMap.State();
        M3.rt = new M3RT.State();
        M3.ids = new M3ID.State();
        // cm3's Main sets the build directory; Builder sets the back-end mode
        Target.setBuild_dir(M3.target, BUILD);
        M3.target.BackendMode = Target.M3BackendMode_t.C;
        M3.target.BackendModeInitialized = true;
        if (!Target.init(M3.target, M3.targetMap, M3.rt, "AMD64_NT", "WIN32",
                         Target.M3BackendMode_t.C)) {
            throw new IllegalStateException("Target.init failed");
        }

        Env env = new Env();
        env.readExports(build, true, Path.of(install), pkgs);
        PrintStream stdout = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
        env.stdout = stdout;

        int failed = 0;
        for (String[] run : runs) {
            String file = run[0];
            // Builder passes M3_FRONT_FLAGS (not in the log at -commands),
            // then the options in the log (M3_OPTIONS)
            String[] options = new String[FRONT_FLAGS.length + run.length - 1];
            System.arraycopy(FRONT_FLAGS, 0, options, 0, FRONT_FLAGS.length);
            System.arraycopy(run, 1, options, FRONT_FLAGS.length, run.length - 1);
            String base = Path.of(file).getFileName().toString();
            String txt = base.endsWith(".i3") || base.endsWith(".ig")
                ? base.substring(0, base.length() - 3) + ".ic.txt"
                : base.substring(0, base.length() - 3) + ".mc.txt";
            Path target = outDir.resolve(txt);
            M3Compiler.SourceFile source = new M3Compiler.SourceFile();
            source.name = file;
            env.output = null;
            env.current = base;
            try (InputStream in = new BufferedInputStream(new FileInputStream(build.resolve(file).toFile()))) {
                source.contents = in;
                boolean ok;
                try {
                    ok = M3Front.compile(source, env, options);
                } catch (RuntimeException | Error e) {
                    stdout.println("*** " + file + ": " + e);
                    e.printStackTrace(stdout);
                    ok = false;
                }
                if (!ok) {
                    failed++;
                }
                env.closeOpened();
            }
            if (env.output != null) {
                // as m3cgcat converts the binary intermediate code cm3 keeps
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
                    CgCat.convert(BUILD, false, new ByteArrayInputStream(env.output.toByteArray()),
                                  os, stdout);
                }
            } else {
                Files.deleteIfExists(target);
            }
        }
        stdout.flush();
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void usage() {
        System.err.println("usage: FrontDump -install=DIR [-pkg=NAME=DIR]... [-out=DIR] PACKAGE");
        System.exit(2);
    }

    /** The environment of the front end, after Builder's Pass0 procedures. */
    private static final class Env extends M3Compiler.Environment {
        /** units by name and kind: ".i3", ".m3", ".ig", ".mg" */
        final Map<String, Unit> units = new HashMap<>();
        final Map<Long, Magic> magic = new HashMap<>();
        final Map<Integer, Object> astCache = new HashMap<>();
        final List<InputStream> opened = new ArrayList<>();
        /** the local modules that export an interface, newest first */
        final Map<String, List<String>> exporters = new HashMap<>();
        /** the local units, in the order of the package's exports */
        final List<String> localUnits = new ArrayList<>();
        /** the name of the unit being compiled, with its extension */
        String current;
        Path buildDir;
        PrintStream stdout;
        /** the binary intermediate code of the current unit, if any */
        ByteArrayOutputStream output;

        private static final Pattern MAP = Pattern.compile(
            "_map_add_(interface|module|generic_interface|generic_module)\\(\"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\"\\)");
        private static final Pattern OVERRIDE = Pattern.compile("override\\(\"([^\"]*)\", \"([^\"]*)\"\\)");
        /** the packages that the exports place with override() */
        final Map<String, Path> overrides = new HashMap<>();
        private static final Pattern IMPORT = Pattern.compile("import_version\\(\"([^\"]*)\", \"([^\"]*)\"\\)");

        /** Reads the exports of the package in build and of all packages it imports. */
        void readExports(Path build, boolean local, Path install, Map<String, Path> pkgs) throws IOException {
            ArrayDeque<String> todo = new ArrayDeque<>();
            Set<String> seen = new LinkedHashSet<>();
            buildDir = build;
            // the magic of the package's own units accrues as they are
            // compiled (Pass0_AddMagic), as in a fresh build
            readOne(build, true, todo);
            findLocalExporters();
            while (!todo.isEmpty()) {
                String p = todo.removeFirst();
                if (!seen.add(p)) {
                    continue;
                }
                Path root = pkgs.getOrDefault(p, overrides.getOrDefault(p, install.resolve("pkg").resolve(p)));
                Path b = root.resolve(BUILD);
                if (!Files.exists(b.resolve(".M3EXPORTS"))) {
                    throw new IOException("no exports for package " + p + " in " + b);
                }
                readOne(b, false, todo);
                readMagic(b);
            }
        }

        private void readOne(Path build, boolean local, ArrayDeque<String> todo) throws IOException {
            Path root = build.getParent();
            for (String line : Files.readAllLines(build.resolve(".M3EXPORTS"), StandardCharsets.ISO_8859_1)) {
                Matcher m = MAP.matcher(line);
                if (m.find()) {
                    String file = m.group(2);
                    String dir = m.group(4);
                    boolean hidden = m.group(5).equals("hidden");
                    if (!local && hidden) {
                        continue;
                    }
                    String path;
                    // the spelling of cm3's M3Path.Join on Windows, as the
                    // front end reports it (set_source_file of generics)
                    if (local) {
                        path = dir.equals(".") ? "../" + file
                                               : "../" + dir.replace('/', '\\') + "/" + file;
                    } else {
                        path = root + "\\" + dir + "/" + file;
                    }
                    int dot = file.lastIndexOf('.');
                    String key = file.substring(0, dot) + file.substring(dot);
                    units.putIfAbsent(key, new Unit(path));
                    if (local) {
                        localUnits.add(key);
                    }
                    continue;
                }
                m = OVERRIDE.matcher(line);
                if (m.find()) {
                    // the package m.group(1) is in the directory m.group(2)
                    overrides.putIfAbsent(m.group(1), Path.of(m.group(2)).resolve(m.group(1)));
                    continue;
                }
                m = IMPORT.matcher(line);
                if (m.find()) {
                    todo.addLast(m.group(1));
                }
            }
        }

        /**
         * Port of Builder.FindLocalExporters for a fresh build (no link
         * info): guesses that each local module exports the local interface
         * with its name.
         */
        private void findLocalExporters() {
            for (String u : localUnits) {
                if (u.endsWith(".m3")) {
                    String name = u.substring(0, u.length() - 3);
                    if (localUnits.contains(name + ".i3") && !name.equals("Main")) {
                        exporters.computeIfAbsent(name, k -> new ArrayList<>()).add(0, name);
                    }
                }
            }
        }

        private void readMagic(Path build) throws IOException {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(build, "*.{mx,m3x}")) {
                for (Path f : ds) {
                    for (String line : Files.readAllLines(f, StandardCharsets.ISO_8859_1)) {
                        if (line.startsWith("O")) {
                            String[] w = line.substring(1).trim().split(" +");
                            long type = (int) Long.parseLong(w[0], 16);
                            long sup = (int) Long.parseLong(w[1], 16);
                            magic.put(type, new Magic(sup, Long.parseLong(w[2]),
                                                      Long.parseLong(w[3]), Long.parseLong(w[4])));
                        }
                    }
                }
            }
        }

        void closeOpened() throws IOException {
            for (InputStream in : opened) {
                in.close();
            }
            opened.clear();
        }

        @Override
        public void report_error(String file, long line, String msg) {
            String eol = System.lineSeparator();
            if (file != null) {
                stdout.print("\"" + file + "\", line " + line + ": " + msg + eol);
            } else {
                stdout.print(msg + eol);
            }
        }

        @Override
        public M3Compiler.SourceFile find_source(int unit, boolean interface_, boolean generic) {
            String ext = interface_ ? (generic ? ".ig" : ".i3") : (generic ? ".mg" : ".m3");
            String name = M3ID.toText(M3.ids, unit);
            M3Compiler.SourceFile file = new M3Compiler.SourceFile();
            Unit u = units.get(name + ext);
            if (u != null) {
                file.name = u.path();
                try {
                    Path p = Path.of(u.path());
                    if (!p.isAbsolute()) {
                        p = buildDir.resolve(p);
                    }
                    InputStream in = new BufferedInputStream(new FileInputStream(p.toFile()));
                    opened.add(in);
                    file.contents = in;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                file.name = name + ext;
                file.contents = null;
            }
            return file;
        }

        @Override
        public void note_unit(int name, boolean interface_) {}

        @Override
        public void note_comment(String msg) {}

        @Override
        public void note_interface_use(int name, boolean imported) {
            // Port of Builder.NoteExporter, called by Pass0_NoteInterface
            String intf = M3ID.toText(M3.ids, name);
            if (imported || current == null || !current.endsWith(".m3")
                || intf.equals("Main") || !localUnits.contains(intf + ".i3")) {
                return;
            }
            String module = current.substring(0, current.length() - 3);
            List<String> ex = exporters.computeIfAbsent(intf, k -> new ArrayList<>());
            if (!ex.contains(module)) {
                ex.add(0, module);
            }
        }

        @Override
        public void note_version_stamp(int unit, int symbol, Fingerprint.T vs,
                                       boolean imported, boolean implemented) {
            note_interface_use(unit, imported);
        }

        @Override
        public void note_generic_use(int name) {}

        @Override
        public void note_opaque(long type, long super_type, int Name) {}

        @Override
        public void note_revelation(int unit, boolean interface_, long lhs, long rhs,
                                    boolean full, boolean imported) {}

        @Override
        public void note_opaque_magic(long type, long super_type, long data_size,
                                      long data_align, long method_size) {
            magic.put(type, new Magic(super_type, data_size, data_align, method_size));
        }

        @Override
        public boolean find_opaque_magic(long type, long[] super_type, long[] data_size,
                                         long[] data_align, long[] method_size) {
            Magic m = magic.get(type);
            if (m == null) {
                return false;
            }
            super_type[0] = m.superType();
            data_size[0] = m.dataSize();
            data_align[0] = m.dataAlign();
            method_size[0] = m.methodSize();
            return true;
        }

        @Override
        public void note_ast(int unit, Object ast) {
            astCache.put(unit, ast);
        }

        @Override
        public Object find_ast(int unit) {
            return astCache.get(unit);
        }

        @Override
        public void note_type(long type, boolean imported) {}

        @Override
        public M3CG.T init_code_generator() {
            // Port of Builder.Pass0_InitCodeGenerator with -keep: the side
            // file of binary intermediate code
            output = new ByteArrayOutputStream();
            return M3CG_BinWr.New(output, M3.ids, M3.targetMap);
        }

        @Override
        public void note_webinfo(String t) {}

        @Override
        public M3Compiler.ImplList get_implementations(int interface_) {
            // Builder's GetExporters reverses the list of exporters
            M3Compiler.ImplList xx = null;
            if (current == null || !current.equals(M3ID.toText(M3.ids, interface_) + ".i3")) {
                return null;
            }
            for (String m : exporters.getOrDefault(M3ID.toText(M3.ids, interface_), List.of())) {
                M3Compiler.ImplList x = new M3Compiler.ImplList();
                x.impl = M3ID.add(M3.ids, m);
                x.next = xx;
                xx = x;
            }
            return xx;
        }
    }
}
