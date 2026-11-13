// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import m3j.fingerprint.Fingerprint;
import m3j.front.M3;
import m3j.front.M3Compiler;
import m3j.middle.M3CG;
import m3j.middle.M3CG_BinWr;
import m3j.middle.M3ID;

/**
 * The environment of the front end for the JVM back end: it finds the
 * source of a unit on a search path, caches the trees of the interfaces
 * between compilations, and gives the front end a code generator that
 * writes nowhere.
 *
 * <p>The search path has three kinds of entries: source directories, whose
 * files {@code *.i3}, {@code *.m3}, {@code *.ig} and {@code *.mg} are
 * units; source packages (a directory with the units in {@code src} and
 * its subdirectories, as the library that m3j brings in its jar, {@code
 * lib/NAME}); and package directories of a cm3 installation ({@code
 * pkg/NAME/TARGET}), whose units are those of the {@code .M3EXPORTS} file.
 * The first entry that has a unit wins.
 */
final class FrontEnv extends M3Compiler.Environment {

    /** Where a unit's source is, and whether it is in a source directory. */
    record Source(Path path, boolean local) {}

    private static final Pattern MAP = Pattern.compile(
        "_map_add_(interface|module|generic_interface|generic_module)\\(\"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\", \"([^\"]*)\"\\)");

    private static final Pattern CLASS_PATH = Pattern.compile("java_classpath\\(\"([^\"]*)\"\\)");

    /** the jars that the .M3EXPORTS of the installed packages name, for the bindings of Java classes */
    final List<Path> classPath = new ArrayList<>();

    /** units by file name ("Wr.i3"), in the order of the search path */
    private final Map<String, Source> units = new LinkedHashMap<>();
    /**
     * the names of the units of packages as cm3 reports them, relative to
     * the build directory of their package ("../src/rw/IO.m3")
     */
    private final Map<Path, String> libNames = new HashMap<>();
    private final Map<Integer, Object> astCache = new HashMap<>();
    /**
     * the modules of the package (of a cm3 installation, or a source
     * package given with -pkg) that has the interface of the given path
     */
    private final Map<Path, List<Path>> packageModules = new HashMap<>();
    private final List<InputStream> opened = new ArrayList<>();

    PrintStream out;
    /** while true, diagnostics are not printed (units compiled on trial) */
    boolean quiet;
    /**
     * where the intermediate code of the unit being compiled is written
     * (-mc), or null: then it is thrown away
     */
    Path cgFile;
    /** the stream of the intermediate code of the unit being compiled, if it is written */
    private OutputStream cgOut;

    FrontEnv(PrintStream out) {
        this.out = out;
    }

    /** Adds the units of a source directory. */
    void addSourceDir(Path dir) throws IOException {
        addSourceDir(dir, null);
    }

    /**
     * Adds the units of a source directory; if shown is not null, their
     * names in diagnostics are shown/FILE.
     */
    void addSourceDir(Path dir, String shown) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.{i3,m3,ig,mg}")) {
            List<Path> files = new ArrayList<>();
            ds.forEach(files::add);
            files.sort(null);
            for (Path f : files) {
                String file = f.getFileName().toString();
                if (units.putIfAbsent(file, new Source(f, true)) == null && shown != null) {
                    libNames.put(f, shown + "/" + file);
                }
            }
        }
    }

    /** The resource directory lib of the jar of m3j, null if there is none. */
    static Path bundledRoot() throws IOException {
        java.net.URL u = FrontEnv.class.getResource("/lib/");
        if (u == null) {
            return null;
        }
        try {
            java.net.URI uri = u.toURI();
            if ("jar".equals(uri.getScheme())) {
                java.nio.file.FileSystem fs;
                try {
                    fs = java.nio.file.FileSystems.getFileSystem(uri);
                } catch (java.nio.file.FileSystemNotFoundException e) {
                    fs = java.nio.file.FileSystems.newFileSystem(uri, Map.of());
                }
                return fs.getPath("/lib");
            }
            return Path.of(uri);
        } catch (java.net.URISyntaxException e) {
            throw new IOException(e);
        }
    }

    /**
     * Adds the library that m3j brings (the resource directory lib of its
     * jar): the packages m3core, libm3, tcp, netobj and sysutils of cm3 without their UNSAFE
     * modules and the modules that the runtime implements in Java, with
     * some modules changed for m3j, and the units of m3j's own (m3j).
     */
    void addBundled() throws IOException {
        Path dir = bundledRoot();
        if (dir == null) {
            return;
        }
        List<Path> pkgs = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (Files.isDirectory(p.resolve("src"))) {
                    pkgs.add(p);
                }
            }
        }
        pkgs.sort(null);
        for (Path p : pkgs) {
            addSourcePackage(p, true);
        }
    }

    /**
     * Adds a package: a package directory of a cm3 installation (with a
     * .M3EXPORTS file) or a source package (with a directory src).
     */
    void addPackage(Path dir) throws IOException {
        Path exports = dir.resolve(".M3EXPORTS");
        if (Files.exists(exports)) {
            addInstalledPackage(dir, exports);
        } else if (Files.isDirectory(dir.resolve("src"))) {
            addSourcePackage(dir, true);
        } else {
            throw new IOException("neither .M3EXPORTS nor src in " + dir);
        }
    }

    /**
     * Adds the units of a source package: those in pkg/src and its
     * subdirectories, and the generic instances that a build of cm3 made
     * in the build directory pkg/TARGET or one of m3build in pkg/JVM (cm3
     * compiles them there, so they are named by their file name alone).
     * With index, the modules of the
     * package are noted for its interfaces (packageModules), for the
     * interfaces that modules of other names export.
     */
    private void addSourcePackage(Path pkg, boolean index) throws IOException {
        List<Path> modules = new ArrayList<>();
        List<Path> interfaces = new ArrayList<>();
        for (String sub : new String[] {"src", BUILD, "JVM"}) {
            Path dir = pkg.resolve(sub);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            // the units in a directory of the other system go last: of a unit
            // that cm3 has for both (os/POSIX/OSConfig.i3, os/WIN32/OSConfig.i3)
            // the compiler takes the one of the system it runs on
            String other = m3j.rt.OS.otherDir();
            List<Path> files;
            try (java.util.stream.Stream<Path> s = Files.walk(dir)) {
                files = s.filter(f -> f.getFileName() != null
                                      && f.getFileName().toString().matches(".*\\.(i3|m3|ig|mg)")
                                      && Files.isRegularFile(f))
                         .sorted(java.util.Comparator.comparing((Path f) -> inDir(dir.relativize(f), other))
                                 .thenComparing(java.util.Comparator.naturalOrder()))
                         .toList();
            }
            for (Path f : files) {
                String file = f.getFileName().toString();
                if (units.putIfAbsent(file, new Source(f, false)) == null) {
                    libNames.put(f, !sub.equals("src") ? file
                                    : "../" + pkg.relativize(f).toString().replace('\\', '/'));
                    if (file.endsWith(".m3")) {
                        modules.add(f);
                    } else if (file.endsWith(".i3")) {
                        interfaces.add(f);
                    }
                }
            }
        }
        if (index) {
            for (Path i : interfaces) {
                packageModules.putIfAbsent(i, modules);
            }
        }
    }

    /** Whether the relative path f is in a directory of the given name. */
    private static boolean inDir(Path f, String name) {
        for (int i = 0; i < f.getNameCount() - 1; i++) {
            if (f.getName(i).toString().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** the build directory of a package for the target of m3j */
    private static final String BUILD = "AMD64_NT";

    /** Adds the exported units of a package directory of a cm3 installation. */
    private void addInstalledPackage(Path build, Path exports) throws IOException {
        Path root = build.getParent();
        List<Path> modules = new ArrayList<>();
        List<Path> interfaces = new ArrayList<>();
        for (String line : Files.readAllLines(exports, StandardCharsets.ISO_8859_1)) {
            Matcher cp = CLASS_PATH.matcher(line);
            if (cp.find()) {
                classPath.add(build.resolve(cp.group(1)).toAbsolutePath().normalize());
            }
            Matcher m = MAP.matcher(line);
            if (m.find()) {
                String file = m.group(2);
                Path path = root.resolve(m.group(4)).resolve(file);
                if (m.group(1).equals("module")) {
                    modules.add(path);
                } else if (m.group(1).equals("interface")) {
                    interfaces.add(path);
                }
                if (units.putIfAbsent(file, new Source(path, false)) == null) {
                    libNames.put(path, "../" + m.group(4) + "/" + file);
                }
            }
        }
        for (Path i : interfaces) {
            packageModules.putIfAbsent(i, modules);
        }
    }

    /**
     * The modules of the package (of a cm3 installation, or a source
     * package given with -pkg) that has the interface at the given path;
     * none if the interface is not from such a package.
     */
    List<Path> packageModules(Path intf) {
        return packageModules.getOrDefault(intf, List.of());
    }

    /** The name of source file f in messages: as cm3 reports it for a library unit, else dflt. */
    String nameOf(Path f, String dflt) {
        return libNames.getOrDefault(f, dflt);
    }

    /** The source of the unit with the given file name, or null. */
    Source lookup(String file) {
        return units.get(file);
    }

    void closeOpened() throws IOException {
        for (InputStream in : opened) {
            in.close();
        }
        opened.clear();
    }

    @Override
    public void report_error(String file, long line, String msg) {
        if (quiet) {
            return;
        }
        if (file != null) {
            out.println("\"" + file + "\", line " + line + ": " + msg);
        } else {
            out.println(msg);
        }
    }

    @Override
    public M3Compiler.SourceFile find_source(int unit, boolean interface_, boolean generic) {
        String ext = interface_ ? (generic ? ".ig" : ".i3") : (generic ? ".mg" : ".m3");
        String name = M3ID.toText(M3.ids, unit);
        M3Compiler.SourceFile file = new M3Compiler.SourceFile();
        Source s = units.get(name + ext);
        if (s != null) {
            file.name = nameOf(s.path(), s.path().toString());
            try {
                InputStream in = new BufferedInputStream(Files.newInputStream(s.path()));
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

    /** A source file for the front end. */
    static M3Compiler.SourceFile sourceFile(Path path) throws IOException {
        M3Compiler.SourceFile f = new M3Compiler.SourceFile();
        f.name = path.toString();
        f.contents = new ByteArrayInputStream(Files.readAllBytes(path));
        return f;
    }

    @Override
    public void note_unit(int name, boolean interface_) {}

    @Override
    public void note_comment(String msg) {}

    @Override
    public void note_interface_use(int name, boolean imported) {}

    @Override
    public void note_generic_use(int name) {}

    @Override
    public void note_version_stamp(int unit, int symbol, Fingerprint.T vs,
                                   boolean imported, boolean implemented) {}

    @Override
    public void note_opaque(long type, long super_type, int Name) {}

    @Override
    public void note_revelation(int unit, boolean interface_, long lhs, long rhs,
                                boolean full, boolean imported) {}

    @Override
    public void note_opaque_magic(long type, long super_type, long data_size,
                                  long data_align, long method_size) {}

    @Override
    public boolean find_opaque_magic(long type, long[] super_type, long[] data_size,
                                     long[] data_align, long[] method_size) {
        // The JVM resolves fields and methods by name; the sizes of opaque
        // object types are never needed.
        return false;
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
        // The intermediate code is generated for the diagnostics of the
        // code generation phase, and thrown away (unless -mc asks for it).
        OutputStream o = OutputStream.nullOutputStream();
        if (cgFile != null) {
            try {
                cgOut = new java.io.BufferedOutputStream(Files.newOutputStream(cgFile));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            o = cgOut;
        }
        return M3CG_BinWr.New(o, M3.ids, M3.targetMap);
    }

    /** Closes the stream of the intermediate code of the unit just compiled, if there is one. */
    void closeCode() throws IOException {
        if (cgOut != null) {
            cgOut.close();
            cgOut = null;
        }
    }

    /** the WebInfo of the unit compiled last: its types, for the description of the unit (DebugInfo) */
    String webInfo;

    @Override
    public void note_webinfo(String t) {
        webInfo = t;
    }

    @Override
    public M3Compiler.ImplList get_implementations(int interface_) {
        return null;
    }
}
