// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.jvm;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import m3j.front.Host;
import m3j.front.M3;
import m3j.front.M3Compiler;
import m3j.front.M3Front;
import m3j.front.Module;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.Target;
import m3j.middle.TargetMap;

/**
 * The compiler driver: compiles a Modula-3 program to JVM classes.
 *
 * <pre>
 * java -cp m3j.jar m3j.jvm.Compile [options] FILE.m3... [-run ARG...]
 *   -I DIR       a source directory: imported units are found there, and
 *                the modules there that implement imported interfaces are
 *                compiled with the program (the directories of the files
 *                given come first)
 *   -pkg DIR     a package: a package directory of a cm3 installation
 *                (pkg/NAME/TARGET, its units listed in .M3EXPORTS) or a
 *                source package (its units in DIR/src and below)
 *   -cm3 DIR     a cm3 installation: its packages m3core and libm3 come
 *                after the library of m3j (which has the units of them
 *                that common programs use), so only their other units are
 *                found there
 *   -d OUT       writes the classes to the directory OUT, or to the jar
 *                OUT if it ends with .jar
 *   -cp PATH     the directories and jars of the Java classes that the
 *                bindings of the program name, separated as on the class
 *                path of the JVM
 *   -wd DIR      relative pathnames that follow are relative to DIR (the
 *                working directory of m3build); diagnostics name the
 *                files given and those of the directories of -I as given
 *   -run ARG...  runs the program in memory with the remaining arguments
 *   -v           reports the constructs the back end does not compile yet
 *   -mc DIR      writes the intermediate code (M3CG, binary) of the k-th
 *                unit compiled to DIR/k.cg and the list of the units
 *                compiled to DIR/units (a line each: "Q" if compiled on
 *                trial, else "-", the path and the name in diagnostics,
 *                separated by tabs), and stops after the front end
 *   other options starting with '-' are options of the front end (-NoChecks)
 * </pre>
 */
public final class Compile {

    private static final String BUILD = "AMD64_NT";

    /** Interfaces that the front end builds in and that have no class. */
    private static final Set<String> BUILTIN = Set.of("Word", "Long", "Compiler", "RTHooks",
        "AtomicAddress", "AtomicBoolean", "AtomicChar", "AtomicInteger", "AtomicLongint",
        "AtomicRefany", "AtomicWideChar", "M3_BUILTIN");

    /**
     * What the linker needs to know of a unit. needsModule: an interface that declares procedures,
     * which a module implements; hasVariables: one that declares variables,
     * which the interface initializes, and a module may set.
     */
    record UnitInfo(String name, boolean isInterface, ClassDesc cls, Set<String> imports,
                    Set<String> exports, boolean needsModule, boolean hasVariables) {}

    final FrontEnv env;
    final PrintStream err;
    final List<String> frontOptions = new ArrayList<>();
    final Map<String, byte[]> classes = new LinkedHashMap<>();
    final List<UnitInfo> units = new ArrayList<>();
    final Set<String> scheduled = new HashSet<>();
    final Deque<Path> queue = new ArrayDeque<>();
    /** the units whose imports have been put before them in the queue */
    final Set<Path> scanned = new HashSet<>();
    /** library modules, compiled if they can be (safe ones); others are skipped */
    final Set<Path> optional = new HashSet<>();
    /** the names of the files given, as given (the front end reports them so) */
    final Map<Path, String> names = new HashMap<>();
    boolean verbose;
    /** -mc: the directory of the intermediate code, or null */
    Path mcDir;
    /** the list of the units compiled, for -mc */
    final StringBuilder mcUnits = new StringBuilder();
    /** the number of units compiled, for -mc */
    int mcCount;
    /** -lib: the modules that are not given are optional, as those of libraries */
    boolean library;
    /** -cp: the directories and jars of the Java classes of the bindings */
    List<Path> classPath = List.of();
    Path currentFile;
    int failed;
    int unsupported;

    Compile(PrintStream err) {
        this.err = err;
        this.env = new FrontEnv(err);
    }

    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    /**
     * The version of m3j: the Implementation-Version of the manifest of its
     * jar (build.sh takes it from the file VERSION), "unknown" if the
     * classes do not come from that jar.
     */
    public static String version() {
        String v = Compile.class.getPackage().getImplementationVersion();
        return v == null ? "unknown" : v;
    }

    /** The pathname p, relative to wd if wd is not null. */
    private static Path resolve(Path wd, String p) {
        return wd == null ? Path.of(p) : wd.resolve(p).normalize();
    }

    /** Runs the compiler with the arguments of its command line: its exit code. */
    public static int run(String[] args) throws IOException {
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.ISO_8859_1);
        Java.reset();
        Compile c = new Compile(err);
        List<Path> files = new ArrayList<>();
        List<Path> dirs = new ArrayList<>();
        List<Path> pkgs = new ArrayList<>();
        String out = null;
        String[] runArgs = null;
        // -wd: the directory that relative pathnames are relative to; the
        // files given and those of the source directories are named in
        // diagnostics as given
        Path wd = null;
        Map<Path, String> dirNames = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-wd" -> wd = Path.of(args[++i]);
                case "-I" -> {
                    Path d = resolve(wd, args[++i]);
                    dirs.add(d);
                    if (wd != null) {
                        dirNames.put(d, args[i]);
                    }
                }
                case "-cm3" -> {
                    Path root = resolve(wd, args[++i]);
                    pkgs.add(root.resolve("pkg").resolve("m3core").resolve(BUILD));
                    pkgs.add(root.resolve("pkg").resolve("libm3").resolve(BUILD));
                }
                case "-pkg" -> pkgs.add(resolve(wd, args[++i]));
                case "-cp" -> {
                    List<Path> entries = new ArrayList<>();
                    for (String e : args[++i].split(java.io.File.pathSeparator)) {
                        if (!e.isEmpty()) {
                            entries.add(resolve(wd, e));
                        }
                    }
                    Java.setClassPath(entries);
                    c.classPath = entries;
                }
                case "-d" -> out = resolve(wd, args[++i]).toString();
                case "-v" -> c.verbose = true;
                case "-mc" -> c.mcDir = resolve(wd, args[++i]);
                case "-lib" -> c.library = true;
                case "-version" -> {
                    System.out.println("m3j version " + version());
                    return 0;
                }
                case "-run" -> {
                    runArgs = new String[args.length - i - 1];
                    System.arraycopy(args, i + 1, runArgs, 0, runArgs.length);
                    i = args.length;
                }
                default -> {
                    if (a.startsWith("-")) {
                        c.frontOptions.add(a);
                    } else {
                        Path f = resolve(wd, a);
                        files.add(f);
                        c.names.put(f, a);
                    }
                }
            }
        }
        if (files.isEmpty()) {
            err.println("usage: m3j.jvm.Compile [-I DIR] [-cm3 DIR] [-pkg DIR] [-cp PATH] [-d OUT] [-v] [-lib] FILE.m3... [-run ARG...]");
            err.println("       m3j.jvm.Compile -version");
            return 2;
        }
        for (Path f : files) {
            Path d = f.toAbsolutePath().getParent();
            if (!dirs.contains(d)) {
                c.env.addSourceDir(d);
            }
        }
        for (Path d : dirs) {
            c.env.addSourceDir(d, dirNames.get(d));
        }
        // the library that m3j brings: m3core, libm3, tcp, netobj and sysutils (safe modules, some
        // changed for m3j), tls, http and the units of m3j
        c.env.addBundled();
        for (Path p : pkgs) {
            c.env.addPackage(p);
        }
        if (!c.env.classPath.isEmpty()) {
            List<Path> all = new ArrayList<>(c.classPath);
            for (Path e : c.env.classPath) {
                if (!all.contains(e)) {
                    all.add(e);
                }
            }
            Java.setClassPath(all);
            c.classPath = all;
        }
        if (c.mcDir != null) {
            Files.createDirectories(c.mcDir);
        }
        initTarget();
        for (Path f : files) {
            c.scheduled.add(f.getFileName().toString());
            c.queue.add(f);
        }
        c.compileAll();
        if (c.mcDir != null) {
            Files.writeString(c.mcDir.resolve("units"), c.mcUnits, StandardCharsets.ISO_8859_1);
            return c.failed > 0 ? 1 : 0;
        }
        if (c.failed > 0) {
            return 1;
        }
        c.link();
        int bad = c.verify();
        if (bad > 0) {
            if (out != null) {
                c.write(Path.of(out));
            }
            return 1;
        }
        if (c.verbose && c.unsupported > 0) {
            err.println(c.unsupported + " construct(s) not compiled yet");
        }
        if (out != null) {
            c.write(Path.of(out));
        }
        if (runArgs != null) {
            return c.runInMemory(runArgs);
        }
        return 0;
    }

    /** The target, as cm3 sets it up for AMD64_NT in the C mode. */
    static void initTarget() {
        M3.target = new Target.State();
        M3.targetMap = new TargetMap.State();
        M3.rt = new M3RT.State();
        M3.ids = new M3ID.State();
        Target.setBuild_dir(M3.target, BUILD);
        M3.target.BackendMode = Target.M3BackendMode_t.C;
        M3.target.BackendModeInitialized = true;
        if (!Target.init(M3.target, M3.targetMap, M3.rt, BUILD, "WIN32", Target.M3BackendMode_t.C)) {
            throw new IllegalStateException("Target.init failed");
        }
    }

    // ------------------------------------------------------------- compile

    void compileAll() throws IOException {
        String[] options = new String[frontOptions.size() + 1];
        options[0] = "-w1";
        for (int i = 0; i < frontOptions.size(); i++) {
            options[i + 1] = frontOptions.get(i);
        }
        Types.out = classes;
        M3Front.backEnd = this::generate;
        try {
            while (!queue.isEmpty()) {
                Path f = queue.removeFirst();
                if (scanned.add(f)) {
                    // the interfaces that f imports go first, as in a build of cm3
                    List<Path> first = interfacesFirst(f);
                    if (!first.isEmpty()) {
                        queue.addFirst(f);
                        for (int i = first.size() - 1; i >= 0; i--) {
                            queue.addFirst(first.get(i));
                        }
                        continue;
                    }
                }
                M3Compiler.SourceFile src = FrontEnv.sourceFile(f);
                src.name = names.getOrDefault(f, env.nameOf(f, src.name));
                currentFile = f;
                boolean trial = optional.contains(f);
                env.quiet = trial;
                if (mcDir != null) {
                    mcCount++;
                    env.cgFile = mcDir.resolve(mcCount + ".cg");
                    mcUnits.append(trial ? "Q" : "-").append('\t').append(f.toAbsolutePath())
                           .append('\t').append(src.name).append('\n');
                }
                boolean ok;
                try {
                    ok = M3Front.compile(src, env, options);
                } catch (RuntimeException | Error e) {
                    if (!trial) {
                        err.println("*** " + f + ": " + e);
                        e.printStackTrace(err);
                    }
                    ok = false;
                }
                env.closeOpened();
                env.closeCode();
                env.quiet = false;
                if (!ok && !trial) {
                    failed++;
                }
            }
        } finally {
            M3Front.backEnd = null;
        }
    }

    /** The back end: called by the front end for each unit compiled without errors. */
    void generate(Module.T m) {
        Unit u = new Unit(m, Host.filename);
        if (!m.safe) {
            if (!m.interface_ && !optional.contains(currentFile)) {
                err.println("\"" + Host.filename + "\": UNSAFE modules are not supported");
                failed++;
            }
            return;
        }
        if (m.interface_) {
            Java.resolveAll(m, u);
        }
        if (!(m.interface_ && m.javaClass == null && Unit.provided(u.name) != null)) {
            byte[] b = u.generate();
            classes.put(u.self.descriptorString(), b);
            unsupported += u.unsupported.size();
            for (String s : u.unsupported) {
                if (verbose) {
                    err.println(Host.filename + ": not compiled yet: " + s);
                } else if (s.startsWith("runtime procedure")) {
                    // a procedure of an interface of the runtime that it does not have
                    err.println("m3j: warning: " + Host.filename + ": no " + s);
                }
            }
        }
        failed += u.errors;
        // an interface that declares procedures needs a module (or a class
        // of the runtime); one that declares variables has a module if one
        // was found
        boolean needsModule = false;
        boolean hasVariables = false;
        if (m.interface_ && Unit.provided(u.name) == null) {
            for (m3j.front.Value.T v = m3j.front.Scope.toList(m.localScope); v != null; v = v.next) {
                if (v instanceof m3j.front.Variable.T) {
                    hasVariables = true;
                } else if (v instanceof m3j.front.Procedure.T p && !p.external) {
                    needsModule = true;
                }
            }
        }
        UnitInfo info = new UnitInfo(u.name, u.isInterface, u.self, u.imports(), u.exports(), needsModule,
                                     hasVariables);
        units.add(info);
        for (String i : info.imports()) {
            schedule(i);
        }
        for (String i : info.exports()) {
            schedule(i);
        }
    }

    /**
     * The interfaces that unit f imports or exports and that are still to be
     * compiled, taken out of the queue. cm3 compiles the interfaces of a
     * package before its modules, and its front end depends on that: a
     * module that only imports an interface can get the alignment of a
     * variable of it wrong (it then reads a field as if it straddled two
     * words, and its code generator reports a bad stack).
     */
    private List<Path> interfacesFirst(Path f) {
        List<Path> first = new ArrayList<>();
        List<String> names;
        try {
            names = headerImports(Files.readString(f, StandardCharsets.ISO_8859_1));
        } catch (IOException | RuntimeException e) {
            return first;
        }
        for (String i : names) {
            schedule(i);
            FrontEnv.Source s = env.lookup(i + ".i3");
            if (s != null && !s.path().equals(f) && queue.remove(s.path())) {
                first.add(s.path());
            }
        }
        return first;
    }

    /**
     * The interfaces named in the header of a unit: its exports, the
     * arguments of a generic instance and its imports. Comments and pragmas
     * are skipped; the scan stops at the first token after the imports.
     */
    static List<String> headerImports(String text) {
        return headerImports(headerTokens(text));
    }

    /** The interfaces that the module of source text exports (none if it is an interface). */
    static List<String> moduleExports(String text) {
        List<String> toks = headerTokens(text);
        List<String> out = new ArrayList<>();
        int k = !toks.isEmpty() && toks.get(0).equals("UNSAFE") ? 1 : 0;
        if (k + 1 >= toks.size() || !toks.get(k).equals("MODULE")) {
            return out;
        }
        k += 2;
        if (k < toks.size() && toks.get(k).equals("EXPORTS")) {
            for (k++; k < toks.size() && !toks.get(k).equals(";") && !toks.get(k).equals("="); k++) {
                if (!toks.get(k).equals(",")) {
                    out.add(toks.get(k));
                }
            }
        } else {
            out.add(toks.get(k - 1));
        }
        return out;
    }

    /** The tokens of the header of a unit, up to the first ';' after its imports. */
    private static List<String> headerTokens(String text) {
        List<String> toks = new ArrayList<>();
        int n = text.length();
        int i = 0;
        while (i < n && toks.size() < 4096) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if ((c == '(' || c == '<') && i + 1 < n && text.charAt(i + 1) == '*') {
                // a comment (nested) or a pragma
                char open = c;
                char close = c == '(' ? ')' : '>';
                int depth = 0;
                while (i < n) {
                    if (text.startsWith(open + "*", i)) {
                        depth++;
                        i += 2;
                    } else if (text.startsWith("*" + close, i)) {
                        depth--;
                        i += 2;
                        if (depth == 0) {
                            break;
                        }
                    } else {
                        i++;
                    }
                }
            } else if (Character.isLetter(c)) {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) {
                    j++;
                }
                toks.add(text.substring(i, j));
                i = j;
            } else {
                toks.add(String.valueOf(c));
                i++;
                if (c == ';' && !toks.isEmpty() && isBodyStart(toks)) {
                    break;
                }
            }
        }
        return toks;
    }

    private static List<String> headerImports(List<String> toks) {
        List<String> out = new ArrayList<>();
        int k = 0;
        if (k < toks.size() && toks.get(k).equals("UNSAFE")) {
            k++;
        }
        if (k >= toks.size() || !(toks.get(k).equals("MODULE") || toks.get(k).equals("INTERFACE"))) {
            return out;
        }
        boolean module = toks.get(k).equals("MODULE");
        String self = k + 1 < toks.size() ? toks.get(k + 1) : "";
        k += 2;
        if (module && !(k < toks.size() && toks.get(k).equals("EXPORTS"))) {
            // a module without EXPORTS exports the interface of its name
            out.add(self);
        }
        if (k < toks.size() && toks.get(k).equals("EXPORTS")) {
            k++;
            while (k < toks.size() && !toks.get(k).equals(";") && !toks.get(k).equals("=")) {
                if (!toks.get(k).equals(",")) {
                    out.add(toks.get(k));
                }
                k++;
            }
        }
        if (k < toks.size() && toks.get(k).equals("=")) {
            // a generic instance: INTERFACE X = G (A, B) END X.
            k += 2;
            while (k < toks.size() && !toks.get(k).equals(")")) {
                String t = toks.get(k);
                if (!t.equals("(") && !t.equals(",")) {
                    out.add(t);
                }
                k++;
            }
            return out;
        }
        k++;
        while (k < toks.size()) {
            String t = toks.get(k);
            if (t.equals("IMPORT")) {
                k++;
                while (k < toks.size() && !toks.get(k).equals(";")) {
                    out.add(toks.get(k));
                    k++;
                    if (k < toks.size() && toks.get(k).equals("AS")) {
                        k += 2;
                    }
                    if (k < toks.size() && toks.get(k).equals(",")) {
                        k++;
                    }
                }
                k++;
            } else if (t.equals("FROM") && k + 1 < toks.size()) {
                out.add(toks.get(k + 1));
                while (k < toks.size() && !toks.get(k).equals(";")) {
                    k++;
                }
                k++;
            } else {
                break;
            }
        }
        return out;
    }

    /** Whether the tokens so far end with a ';' that is not part of the header. */
    private static boolean isBodyStart(List<String> toks) {
        // the header ends at the first ';' that does not end the unit's
        // heading or an IMPORT clause; find the start of the last clause
        int j = toks.size() - 2;
        while (j >= 0 && !toks.get(j).equals(";")) {
            j--;
        }
        if (j < 0) {
            return false;
        }
        String t = toks.get(j + 1);
        return !t.equals("IMPORT") && !t.equals("FROM");
    }

    /** the interfaces for which a module file was found */
    private final Set<String> moduleFound = new HashSet<>();

    /** the interfaces that the module files read so far export */
    private final Map<Path, List<String>> exportsCache = new HashMap<>();
    /** the module files read so far that are UNSAFE */
    private final Set<Path> unsafeModules = new HashSet<>();

    /** The interfaces that module file f exports; none if it cannot be read. */
    private List<String> exportsOf(Path f) {
        return exportsCache.computeIfAbsent(f, k -> {
            try {
                String text = Files.readString(k, StandardCharsets.ISO_8859_1);
                List<String> toks = headerTokens(text);
                if (!toks.isEmpty() && toks.get(0).equals("UNSAFE")) {
                    unsafeModules.add(k);
                }
                return moduleExports(text);
            } catch (IOException | RuntimeException e) {
                return List.of();
            }
        });
    }

    /** Schedules interface I and, if it is in a source directory, module I. */
    void schedule(String intf) {
        if (BUILTIN.contains(intf) || !scheduled.add(intf + ".i3")) {
            return;
        }
        FrontEnv.Source s = env.lookup(intf + ".i3");
        if (s != null) {
            queue.add(s.path());
        }
        FrontEnv.Source m = env.lookup(intf + ".m3");
        if (m == null) {
            // a module of cm3 for Windows that exports the interface (NAMEWin32.m3), as a
            // package of a cm3 installation may have it
            m = env.lookup(intf + "Win32.m3");
            if (m != null && !exportsOf(m.path()).contains(intf)) {
                m = null;
            }
        }
        if (m == null && s != null) {
            // the safe modules of the interface's package that export it
            // under other names (M3PathElemWin32.m3 exports M3PathElemOSPriv;
            // two modules of netobj implement NetObjMonInit together); an
            // UNSAFE one would not be compiled, but its imports would be
            // scheduled
            for (Path p : env.packageModules(s.path())) {
                if (exportsOf(p).contains(intf) && !unsafeModules.contains(p)) {
                    FrontEnv.Source e = env.lookup(p.getFileName().toString());
                    if (e != null && e.path().equals(p)) {
                        scheduleModule(e, intf);
                    }
                }
            }
            return;
        }
        if (m != null) {
            scheduleModule(m, intf);
            if (s != null) {
                // the other safe modules of the package in the directory of
                // module m that export the interface too (M3AST_SM_Walk.m3
                // of m3tk implements the procedures Walk_... of M3AST_SM_F,
                // M3AST_SM_F.m3 the others); a module of another system is
                // in another directory
                Path dir = m.path().getParent();
                for (Path p : env.packageModules(s.path())) {
                    if (!p.equals(m.path()) && dir != null && dir.equals(p.getParent())
                            && exportsOf(p).contains(intf) && !unsafeModules.contains(p)) {
                        FrontEnv.Source e = env.lookup(p.getFileName().toString());
                        if (e != null && e.path().equals(p)) {
                            scheduleModule(e, intf);
                        }
                    }
                }
            }
        }
    }

    /** Schedules module m, which exports interface intf. */
    private void scheduleModule(FrontEnv.Source m, String intf) {
        moduleFound.add(intf);
        if ((m.local() || Unit.provided(intf) == null)
                && scheduled.add(m.path().getFileName().toString())) {
            if (!m.local() || library) {
                optional.add(m.path());
            }
            queue.add(m.path());
        }
    }

    // ---------------------------------------------------------------- link

    static final ClassDesc MAIN = ClassDesc.of("m3.Main");

    /** Writes the main class: the link table, then the initialization of the units. */
    void link() {
        Map<String, UnitInfo> intfs = new HashMap<>();
        Map<String, List<UnitInfo>> exporters = new HashMap<>();
        UnitInfo main = null;
        for (UnitInfo u : units) {
            if (u.isInterface()) {
                intfs.put(u.name(), u);
            } else {
                for (String e : u.exports()) {
                    exporters.computeIfAbsent(e, k -> new ArrayList<>()).add(u);
                    if (e.equals("Main")) {
                        main = u;
                    }
                }
            }
        }
        for (UnitInfo u : units) {
            if ((u.needsModule() || u.hasVariables() && moduleFound.contains(u.name()))
                && !exporters.containsKey(u.name())) {
                err.println("m3j: warning: no module of interface " + u.name() + " is compiled (it is UNSAFE"
                            + " or missing): calling its procedures fails, its variables keep their initial values");
            }
        }
        InitOrder io = new InitOrder(intfs, exporters);
        for (UnitInfo u : units) {
            if (!u.isInterface() && u != main) {
                io.visit(u);
            }
        }
        if (main != null) {
            io.visit(main);
        }
        List<UnitInfo> inits = io.order;
        // the classes of the types, for RTTypeFP.FromFingerprint: in
        // M3$TYPES, and if they do not fit in one constant, in M3$TYPES1,
        // M3$TYPES2, ...
        List<String> types = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String d : classes.keySet()) {
            if (d.startsWith("Lm3/t/T")) {
                if (sb.length() > 60000) {
                    types.add(sb.toString());
                    sb.setLength(0);
                }
                sb.append(sb.isEmpty() ? "" : " ").append(d, 1, d.length() - 1);
            }
        }
        types.add(sb.toString());
        byte[] b = ClassFile.of().build(MAIN, clb -> {
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SUPER);
            clb.withInterfaceSymbols(ClassDesc.of("java.lang.Runnable"));
            for (int i = 0; i < types.size(); i++) {
                String v = types.get(i);
                clb.withField(i == 0 ? "M3$TYPES" : "M3$TYPES" + i, ConstantDescs.CD_String,
                    fb -> fb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL)
                            .with(java.lang.classfile.attribute.ConstantValueAttribute.of(v)));
            }
            clb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_PUBLIC, cb -> {
                cb.aload(0);
                cb.invokespecial(ConstantDescs.CD_Object, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
                cb.return_();
            });
            clb.withMethodBody("main", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String.arrayType()),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, cb -> {
                    cb.aload(0);
                    cb.new_(MAIN);
                    cb.dup();
                    cb.invokespecial(MAIN, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void));
                    cb.invokestatic(Code.RT, "main", MethodTypeDesc.of(ConstantDescs.CD_void,
                        ConstantDescs.CD_String.arrayType(), ClassDesc.of("java.lang.Runnable")));
                    cb.return_();
                });
            clb.withMethodBody("run", MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_PUBLIC, cb -> {
                for (UnitInfo u : units) {
                    if (!u.isInterface()) {
                        for (String e : u.exports()) {
                            register(cb, e, u.cls());
                        }
                    }
                }
                for (UnitInfo u : inits) {
                    if (classes.containsKey(u.cls().descriptorString())) {
                        cb.invokestatic(u.cls(), "$init", MethodTypeDesc.of(ConstantDescs.CD_void));
                    }
                }
                cb.return_();
            });
        });
        classes.put(MAIN.descriptorString(), b);
    }

    private static void register(CodeBuilder cb, String intf, ClassDesc module) {
        cb.loadConstant(intf);
        cb.loadConstant(module);
        cb.invokestatic(Unit.CD_Link, "export", MethodTypeDesc.of(ConstantDescs.CD_void,
            ConstantDescs.CD_String, ConstantDescs.CD_Class));
    }

    /**
     * The order in which the units are initialized, as cm3's RTLinker runs
     * them: a unit depends on the interfaces it imports and exports and on
     * the modules that export the interfaces it imports; the strongly
     * connected components of these dependencies (the cycles of imports)
     * come each after the components it depends on. In a component, the
     * interfaces come first (each after the interfaces it imports), since
     * the modules use their variables, which cm3 has in static memory, and
     * then the modules in the order in which the search finished them.
     * Without cycles, this is the order in which the search finishes the
     * units.
     */
    private static final class InitOrder {
        final Map<String, UnitInfo> intfs;
        final Map<String, List<UnitInfo>> exporters;
        final List<UnitInfo> order = new ArrayList<>();
        final Map<UnitInfo, Integer> index = new HashMap<>();
        final Map<UnitInfo, Integer> low = new HashMap<>();
        final Map<UnitInfo, Integer> finished = new HashMap<>();
        final ArrayDeque<UnitInfo> stack = new ArrayDeque<>();
        final Set<UnitInfo> onStack = new HashSet<>();
        final Set<UnitInfo> placed = new HashSet<>();

        InitOrder(Map<String, UnitInfo> intfs, Map<String, List<UnitInfo>> exporters) {
            this.intfs = intfs;
            this.exporters = exporters;
        }

        List<UnitInfo> dependencies(UnitInfo u) {
            List<UnitInfo> out = new ArrayList<>();
            for (String i : u.imports()) {
                UnitInfo iu = intfs.get(i);
                if (iu != null) {
                    out.add(iu);
                }
                out.addAll(exporters.getOrDefault(i, List.of()));
            }
            for (String i : u.exports()) {
                UnitInfo iu = intfs.get(i);
                if (iu != null) {
                    out.add(iu);
                }
            }
            return out;
        }

        void visit(UnitInfo u) {
            if (index.containsKey(u)) {
                return;
            }
            int n = index.size();
            index.put(u, n);
            low.put(u, n);
            stack.push(u);
            onStack.add(u);
            for (UnitInfo v : dependencies(u)) {
                if (!index.containsKey(v)) {
                    visit(v);
                    low.put(u, Math.min(low.get(u), low.get(v)));
                } else if (onStack.contains(v)) {
                    low.put(u, Math.min(low.get(u), index.get(v)));
                }
            }
            finished.put(u, finished.size());
            if (low.get(u).equals(index.get(u))) {
                List<UnitInfo> comp = new ArrayList<>();
                UnitInfo w;
                do {
                    w = stack.pop();
                    onStack.remove(w);
                    comp.add(w);
                } while (w != u);
                comp.sort(Comparator.comparing(finished::get));
                for (UnitInfo c : comp) {
                    if (c.isInterface()) {
                        place(c, comp);
                    }
                }
                for (UnitInfo c : comp) {
                    if (!c.isInterface()) {
                        placed.add(c);
                        order.add(c);
                    }
                }
            }
        }

        /** Adds interface u of the component comp after the interfaces of comp it imports. */
        void place(UnitInfo u, List<UnitInfo> comp) {
            if (!placed.add(u)) {
                return;
            }
            for (String i : u.imports()) {
                UnitInfo iu = intfs.get(i);
                if (iu != null && comp.contains(iu)) {
                    place(iu, comp);
                }
            }
            order.add(u);
        }
    }

    // -------------------------------------------------------------- verify

    /** Verifies all classes; returns the number of errors. */
    int verify() {
        for (String d : classes.keySet()) {
            Types.supers.putIfAbsent(ClassDesc.ofDescriptor(d), ConstantDescs.CD_Object);
        }
        ClassFile cf = Types.classFile();
        int n = 0;
        if (verbose) {
            // HotSpot does not compile methods larger than 8000 bytes
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                for (java.lang.classfile.MethodModel m : cf.parse(e.getValue()).methods()) {
                    m.code().ifPresent(code -> {
                        int len = ((java.lang.classfile.attribute.CodeAttribute) code).codeLength();
                        if (len > 8000) {
                            err.println("m3j: method " + e.getKey() + "." + m.methodName().stringValue()
                                        + " has " + len + " bytes");
                        }
                    });
                }
            }
        }
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            for (VerifyError ve : cf.verify(e.getValue())) {
                err.println("m3j: class " + e.getKey() + " does not verify: " + ve.getMessage());
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- output

    static String fileName(String descriptor) {
        return descriptor.substring(1, descriptor.length() - 1) + ".class";
    }

    /**
     * The Class-Path of the manifest of the jar: URLs relative to the jar
     * where that is possible, so that the jar and its classes can move
     * together.
     */
    private String manifestClassPath(Path jar) {
        Path dir = jar.toAbsolutePath().normalize().getParent();
        java.util.StringJoiner out = new java.util.StringJoiner(" ");
        for (Path e : classPath) {
            Path p = e.toAbsolutePath().normalize();
            String url;
            try {
                url = new java.net.URI(null, null, dir.relativize(p).toString().replace(java.io.File.separatorChar, '/'),
                                       null).toASCIIString();
            } catch (IllegalArgumentException | java.net.URISyntaxException x) {
                url = p.toUri().toString(); // on another drive, or not a relative URL
            }
            out.add(Files.isDirectory(p) && !url.endsWith("/") ? url + "/" : url);
        }
        return out.toString();
    }

    void write(Path out) throws IOException {
        if (out.toString().endsWith(".jar")) {
            Manifest mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "m3.Main");
            if (!classPath.isEmpty()) {
                mf.getMainAttributes().put(Attributes.Name.CLASS_PATH, manifestClassPath(out));
            }
            try (OutputStream os = Files.newOutputStream(out);
                 JarOutputStream jar = new JarOutputStream(os, mf)) {
                for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                    jar.putNextEntry(new JarEntry(fileName(e.getKey())));
                    jar.write(e.getValue());
                    jar.closeEntry();
                }
            }
        } else {
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                Path p = out.resolve(fileName(e.getKey()));
                Files.createDirectories(p.getParent());
                Files.write(p, e.getValue());
            }
        }
    }

    /** Loads the classes in memory and runs the program. */
    int runInMemory(String[] args) {
        Map<String, byte[]> byName = new HashMap<>();
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            String d = e.getKey();
            byName.put(d.substring(1, d.length() - 1).replace('/', '.'), e.getValue());
        }
        ClassLoader loader = new ClassLoader("m3j", Java.loader) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = byName.get(name);
                if (b == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, b, 0, b.length);
            }
        };
        // services of the classes of -cp, such as drivers of JDBC, are found
        // through the class loader of the thread, which threads inherit
        Thread me = Thread.currentThread();
        ClassLoader before = me.getContextClassLoader();
        me.setContextClassLoader(loader);
        try {
            Class<?> c = loader.loadClass("m3.Main");
            c.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (ReflectiveOperationException e) {
            Throwable t = e.getCause() != null ? e.getCause() : e;
            t.printStackTrace(err);
            return 1;
        } finally {
            me.setContextClassLoader(before);
        }
        return 0;
    }
}
