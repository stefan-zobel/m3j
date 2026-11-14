// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug.dap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import m3j.debug.Session;
import m3j.debug.Session.Failure;
import m3j.debug.Sources;

/**
 * The debug adapter: the Debug Adapter Protocol on the input and output
 * it is given, for a {@link Session}.
 *
 * <p>The request launch starts a program: a jar (the attribute jar), or
 * the program that m3build makes of a package in its directory JVM (the
 * attribute package; program chooses one if there are several), built
 * first unless build is false, with the class path and the options of
 * java that its m3makefile names (java_classpath, java_option). The
 * program runs with its output in the console of the client (console
 * internalConsole), or in a terminal that the client opens for it by the
 * request runInTerminal (integratedTerminal, externalTerminal), so that
 * it can read its standard input. The request attach connects to a
 * program that runs with the agent of JDWP as a server
 * (-agentlib:jdwp=transport=dt_socket,server=y,address=PORT), at host and
 * port; disconnect leaves it running unless terminateDebuggee is true.
 */
public final class Adapter implements Session.Listener {
    private final InputStream in;
    private final OutputStream out;
    private final Path home;
    private int seq = 1;
    private volatile Session session;
    /** the program that the client is to start in a terminal, until it connects */
    private volatile Session.Waiting waiting;
    /** the number of the request runInTerminal */
    private int terminalRequest = -1;
    /** whether the client can run a program in a terminal */
    private boolean runInTerminal;
    /** whether the session is attached to a program that runs on its own */
    private boolean attached;
    private boolean done;
    /** what to do after the response to the request being served */
    private Runnable after;
    /** whether the response to the request being served comes later */
    private boolean deferred;

    public Adapter(InputStream in, OutputStream out, Path home) {
        this.in = in;
        this.out = out;
        this.home = home;
    }

    /** Serves requests until the client disconnects or its input ends. */
    public void run() throws IOException {
        while (!done) {
            String text = read();
            if (text == null) {
                break;
            }
            handle(text);
        }
        end(!attached);
    }

    /** Ends the session: the program too if kill, else it runs on. */
    private void end(boolean kill) {
        Session.Waiting w = waiting;
        if (w != null) {
            w.cancel("the client is gone");
        }
        Session s = session;
        session = null;
        if (s != null) {
            if (kill) {
                s.kill();
            } else {
                s.detach();
            }
        }
    }

    // ---- messages

    /** The content of the next message, or null at the end of the input. */
    private String read() throws IOException {
        int length = -1;
        while (true) {
            String line = line();
            if (line == null) {
                return null;
            }
            if (line.isEmpty()) {
                if (length >= 0) {
                    break;
                }
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                try {
                    length = Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    length = -1;
                }
            }
        }
        byte[] b = in.readNBytes(length);
        if (b.length < length) {
            return null;
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    private String line() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0 && c != '\n') {
            if (c != '\r') {
                b.write(c);
            }
        }
        if (c < 0 && b.size() == 0) {
            return null;
        }
        return b.toString(StandardCharsets.ISO_8859_1);
    }

    /** Sends the message m: its number. */
    private synchronized int send(Map<String, Object> m) {
        int n = seq++;
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("seq", n);
        msg.putAll(m);
        byte[] b = Json.write(msg).getBytes(StandardCharsets.UTF_8);
        try {
            out.write(("Content-Length: " + b.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(b);
            out.flush();
        } catch (IOException e) {
            // the client is gone
        }
        return n;
    }

    private void event(String name, Map<String, Object> body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "event");
        m.put("event", name);
        if (body != null) {
            m.put("body", body);
        }
        send(m);
    }

    private void respond(Object requestSeq, String command, Object body, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "response");
        m.put("request_seq", requestSeq);
        m.put("success", error == null);
        m.put("command", command);
        if (error != null) {
            m.put("message", error);
        }
        if (body != null) {
            m.put("body", body);
        }
        send(m);
    }

    private void handle(String text) {
        Object o;
        try {
            o = Json.parse(text);
        } catch (IllegalArgumentException e) {
            event("output", obj("category", "stderr", "output", "m3j debug adapter: " + e.getMessage() + "\n"));
            return;
        }
        if (!(o instanceof Map<?, ?> m)) {
            return;
        }
        if ("response".equals(m.get("type"))) {
            answered(m);
            return;
        }
        if (!"request".equals(m.get("type"))) {
            return;
        }
        Object rseq = m.get("seq");
        String command = m.get("command") instanceof String s ? s : "";
        Map<?, ?> args = m.get("arguments") instanceof Map<?, ?> a ? a : Map.of();
        Object body;
        after = null;
        deferred = false;
        try {
            body = dispatch(command, args, rseq);
        } catch (Failure e) {
            respond(rseq, command, null, e.getMessage());
            return;
        } catch (RuntimeException e) {
            respond(rseq, command, null, "internal error: " + e);
            return;
        }
        if (deferred) {
            return;
        }
        respond(rseq, command, body, null);
        if (after != null) {
            after.run();
        }
        switch (command) {
            case "launch", "attach" -> event("initialized", null);
            case "disconnect" -> done = true;
            default -> {
            }
        }
    }

    private Object dispatch(String command, Map<?, ?> args, Object rseq) throws Failure {
        return switch (command) {
            case "initialize" -> initialize(args);
            case "launch" -> launch(args, rseq);
            case "attach" -> attach(args);
            case "setBreakpoints" -> setBreakpoints(args);
            case "setExceptionBreakpoints" -> {
                List<String> f = strings(args, "filters");
                session().exceptionFilters(f.contains("raise"), f.contains("error"), f.contains("unhandled"));
                yield null;
            }
            case "exceptionInfo" -> {
                Session.ExceptionDetails d = session().exception(integer(args, "threadId", 0));
                yield obj("exceptionId", d.id(), "description", d.description(), "breakMode", d.breakMode(),
                          "details", obj("message", d.description(), "typeName", d.id()));
            }
            case "configurationDone" -> {
                session().start();
                yield null;
            }
            case "threads" -> threads();
            case "stackTrace" -> stackTrace(args);
            case "scopes" -> scopes(args);
            case "variables" -> variables(args);
            case "evaluate" -> {
                Session.Variable v = session().evaluate(string(args, "expression", ""), integer(args, "frameId", 0));
                Map<String, Object> m = obj("result", v.value());
                yield value(m, v);
            }
            case "setExpression" -> {
                Session.Variable v = session().setExpression(string(args, "expression", ""),
                                                             string(args, "value", ""), integer(args, "frameId", 0));
                yield value(obj("value", v.value()), v);
            }
            case "setVariable" -> {
                Session.Variable v = session().setVariable(integer(args, "variablesReference", 0),
                                                           string(args, "name", ""), string(args, "value", ""));
                yield value(obj("value", v.value()), v);
            }
            case "continue" -> {
                after = session()::resume;
                yield obj("allThreadsContinued", true);
            }
            case "next" -> step(args, Session.StepKind.OVER);
            case "stepIn" -> step(args, Session.StepKind.IN);
            case "stepOut" -> step(args, Session.StepKind.OUT);
            case "pause" -> {
                int t = session().pause(integer(args, "threadId", 0));
                if (t != 0) {
                    after = () -> stopped("pause", t, List.of(), null);
                }
                yield null;
            }
            case "terminate" -> {
                session().terminate();
                yield null;
            }
            case "disconnect" -> {
                end(args.get("terminateDebuggee") instanceof Boolean b ? b : !attached);
                yield null;
            }
            default -> throw new Failure("unknown request " + command);
        };
    }

    private Object initialize(Map<?, ?> args) {
        runInTerminal = Boolean.TRUE.equals(args.get("supportsRunInTerminalRequest"));
        return obj("supportsConfigurationDoneRequest", true,
                   "supportsTerminateRequest", true,
                   "supportsExceptionInfoRequest", true,
                   "supportsEvaluateForHovers", true,
                   "supportsSetVariable", true,
                   "supportsSetExpression", true,
                   "exceptionBreakpointFilters", List.of(
                       obj("filter", "raise", "label", "Every RAISE", "default", false,
                           "description", "Stop where an exception is raised."),
                       obj("filter", "error", "label", "Runtime errors", "default", false,
                           "description", "Stop at a checked runtime error, also if it is handled."),
                       obj("filter", "unhandled", "label", "Unhandled exceptions", "default", true,
                           "description", "Stop where an exception or a runtime error is raised"
                               + " that no TRY EXCEPT handles.")));
    }

    private Object step(Map<?, ?> args, Session.StepKind kind) throws Failure {
        Session s = session();
        s.step(integer(args, "threadId", 0), kind);
        after = s::resume;
        return null;
    }

    private Session session() throws Failure {
        if (session == null) {
            throw new Failure("no program: launch first");
        }
        return session;
    }

    // ---- requests

    private Object launch(Map<?, ?> args, Object rseq) throws Failure {
        if (session != null || waiting != null) {
            throw new Failure("a program is running");
        }
        String cwdName = string(args, "cwd", null);
        Path dir = Path.of(cwdName == null ? System.getProperty("user.dir") : cwdName).toAbsolutePath().normalize();
        String pkgName = string(args, "package", null);
        String jarName = string(args, "jar", null);
        Path pkg = null;
        Program prog;
        if (pkgName != null) {
            if (jarName != null) {
                throw new Failure("launch takes the attribute package or jar, not both");
            }
            pkg = dir.resolve(pkgName).toAbsolutePath().normalize();
            if (!Files.isDirectory(pkg)) {
                throw new Failure("no such package: " + pkg);
            }
            if (!Boolean.FALSE.equals(args.get("build"))) {
                build(pkg);
            }
            prog = program(pkg, string(args, "program", null));
        } else if (jarName != null) {
            Path jar = dir.resolve(jarName).toAbsolutePath().normalize();
            if (!Files.isRegularFile(jar)) {
                throw new Failure("no such jar: " + jar);
            }
            prog = new Program(jar, List.of(), List.of());
        } else {
            throw new Failure("launch needs the attribute package or jar");
        }
        Path cwd = cwdName == null && pkg != null ? pkg : dir;
        String main = "m3.Main";
        try (JarFile jf = new JarFile(prog.jar().toFile())) {
            Manifest mf = jf.getManifest();
            String m = mf == null ? null : mf.getMainAttributes().getValue("Main-Class");
            if (m != null) {
                main = m.trim();
            }
        } catch (IOException e) {
            throw new Failure("not a jar: " + prog.jar());
        }
        List<String> cp = new ArrayList<>();
        Path runtime = home.resolve("m3j-runtime.jar");
        cp.add((Files.isRegularFile(runtime) ? runtime : home.resolve("m3j.jar")).toString());
        cp.add(prog.jar().toString());
        cp.addAll(prog.classpath());
        for (String c : strings(args, "classpath")) {
            cp.add(dir.resolve(c).toString());
        }
        List<String> options = new ArrayList<>(prog.options());
        options.addAll(strings(args, "javaOptions"));
        Map<String, String> env = new LinkedHashMap<>();
        if (args.get("env") instanceof Map<?, ?> e) {
            for (Map.Entry<?, ?> x : e.entrySet()) {
                env.put(String.valueOf(x.getKey()), x.getValue() == null ? null : String.valueOf(x.getValue()));
            }
        }
        List<Path> roots = new ArrayList<>();
        for (String r : strings(args, "sourceRoots")) {
            roots.add(dir.resolve(r));
        }
        Path jdir = prog.jar().getParent();
        if (pkg != null) {
            roots.add(pkg);
        } else if (jdir != null && jdir.getFileName() != null && jdir.getFileName().toString().equals("JVM")
                   && jdir.getParent() != null) {
            // the package of a program that m3build made
            roots.add(jdir.getParent());
        }
        roots.add(home.resolve("lib"));
        Session.Launch l = new Session.Launch(java(), cp, main, strings(args, "args"), cwd, env, options);
        boolean entry = Boolean.TRUE.equals(args.get("stopOnEntry"));
        Sources sources = new Sources(roots, cwd);
        String console = string(args, "console", "internalConsole");
        switch (console) {
            case "internalConsole" -> {
                Session s = Session.launch(l, sources, this);
                s.stopOnEntry(entry);
                session = s;
            }
            case "integratedTerminal", "externalTerminal" -> {
                if (!runInTerminal) {
                    throw new Failure("the client cannot run a program in a terminal: use the console internalConsole");
                }
                terminal(l, console.equals("integratedTerminal") ? "integrated" : "external", sources, entry, rseq);
            }
            default -> throw new Failure("the console is internalConsole, integratedTerminal or externalTerminal,"
                                         + " not " + console);
        }
        return null;
    }

    /**
     * Has the client run the program of l in a terminal of kind; the
     * response to the request launch (rseq) comes when the program has
     * connected.
     */
    private void terminal(Session.Launch l, String kind, Sources sources, boolean entry, Object rseq)
        throws Failure {
        Session.Waiting w = Session.listen(l);
        waiting = w;
        Map<String, Object> a = obj("kind", kind, "title", "m3j: " + name(l), "cwd", l.cwd().toString(),
                                    "args", new ArrayList<Object>(w.command()));
        if (!l.env().isEmpty()) {
            a.put("env", new LinkedHashMap<String, Object>(l.env()));
        }
        Map<String, Object> m = obj("type", "request", "command", "runInTerminal", "arguments", a);
        terminalRequest = send(m);
        deferred = true;
        Thread t = new Thread(() -> {
            try {
                Session s = w.accept(sources, this);
                s.stopOnEntry(entry);
                session = s;
                waiting = null;
                respond(rseq, "launch", null, null);
                event("initialized", null);
            } catch (Failure e) {
                waiting = null;
                respond(rseq, "launch", null, e.getMessage());
            }
        }, "m3j-debug-terminal");
        t.setDaemon(true);
        t.start();
    }

    /** The name of the program of l: its jar without .jar. */
    private static String name(Session.Launch l) {
        String jar = l.classpath().get(1);
        String n = Path.of(jar).getFileName().toString();
        return n.endsWith(".jar") ? n.substring(0, n.length() - 4) : n;
    }

    /** A response of the client to a request of the adapter. */
    private void answered(Map<?, ?> m) {
        Session.Waiting w = waiting;
        if (w != null && m.get("request_seq") instanceof Number n && n.intValue() == terminalRequest
            && !Boolean.TRUE.equals(m.get("success"))) {
            w.cancel("the client did not run the program in a terminal"
                     + (m.get("message") instanceof String s ? ": " + s : ""));
        }
    }

    private Object attach(Map<?, ?> args) throws Failure {
        if (session != null || waiting != null) {
            throw new Failure("a program is running");
        }
        String host = string(args, "host", "localhost");
        int port = integer(args, "port", 0);
        if (port <= 0 || port > 65535) {
            throw new Failure("attach needs the attribute port, the port of the agent of the program");
        }
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> roots = new ArrayList<>();
        for (String r : strings(args, "sourceRoots")) {
            roots.add(dir.resolve(r));
        }
        String pkg = string(args, "package", null);
        if (pkg != null) {
            roots.add(dir.resolve(pkg));
        }
        roots.add(home.resolve("lib"));
        session = Session.attach(host, port, new Sources(roots, dir), this);
        attached = true;
        return null;
    }

    /** Builds the package pkg with m3build, with its output in the console. */
    private void build(Path pkg) throws Failure {
        Path m3build = home.resolve("m3build.jar");
        if (!Files.isRegularFile(m3build)) {
            throw new Failure("there is no m3build.jar in " + home + " to build " + pkg);
        }
        int code = Session.run(List.of(java(), "-jar", m3build.toString()), pkg, "console", this);
        if (code != 0) {
            throw new Failure("m3build failed in " + pkg + " (exit code " + code + ")");
        }
    }

    /** A program that m3build made: its jar, the class path of its bindings, the options of java. */
    private record Program(Path jar, List<String> classpath, List<String> options) {}

    /**
     * The program called name (null: the only one) of the package pkg,
     * which m3build made in pkg/JVM. Next to NAME.jar, m3build keeps in
     * NAME.m3j the arguments of the compiler on the first line, separated by
     * blanks (the class path of java_classpath after -cp, before -I), and
     * the options of java_option on the lines after it.
     */
    private static Program program(Path pkg, String name) throws Failure {
        Path dir = pkg.resolve("JVM");
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.m3j")) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                names.add(n.substring(0, n.length() - 4));
            }
        } catch (IOException e) {
            // no programs
        }
        names.sort(null);
        if (name == null) {
            if (names.isEmpty()) {
                throw new Failure("there is no program in " + dir + ": build the package with m3build");
            }
            if (names.size() > 1) {
                throw new Failure("the package " + pkg + " has the programs " + String.join(", ", names)
                                  + ": the attribute program chooses one");
            }
            name = names.get(0);
        } else if (!names.contains(name)) {
            throw new Failure("there is no program " + name + " in " + dir);
        }
        Path jar = dir.resolve(name + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new Failure("no such jar: " + jar);
        }
        String stamp;
        try {
            stamp = Files.readString(dir.resolve(name + ".m3j"), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new Failure("cannot read " + dir.resolve(name + ".m3j") + ": " + e.getMessage());
        }
        String[] lines = stamp.split("\r?\n");
        List<String> cp = new ArrayList<>();
        String first = lines.length > 0 ? lines[0] : "";
        if (first.startsWith("-cp ")) {
            int end = first.length();
            for (String next : List.of(" -I ", " -pkg ", " -d ")) {
                int k = first.indexOf(next, 4);
                if (k >= 0 && k < end) {
                    end = k;
                }
            }
            for (String e : first.substring(4, end).split(java.io.File.pathSeparator)) {
                if (!e.isEmpty()) {
                    cp.add(e);
                }
            }
        }
        List<String> options = new ArrayList<>();
        for (int k = 1; k < lines.length; k++) {
            if (!lines[k].isEmpty()) {
                options.add(lines[k]);
            }
        }
        return new Program(jar, cp, options);
    }

    /** The java program that runs the adapter. */
    private static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private Object setBreakpoints(Map<?, ?> args) throws Failure {
        Map<?, ?> source = args.get("source") instanceof Map<?, ?> s ? s : Map.of();
        String path = string(source, "path", null);
        if (path == null) {
            throw new Failure("setBreakpoints needs a source with a path");
        }
        List<Integer> lines = new ArrayList<>();
        if (args.get("breakpoints") instanceof List<?> bs) {
            for (Object b : bs) {
                if (b instanceof Map<?, ?> bm && bm.get("line") instanceof Number n) {
                    lines.add(n.intValue());
                }
            }
        } else if (args.get("lines") instanceof List<?> ls) {
            for (Object x : ls) {
                if (x instanceof Number n) {
                    lines.add(n.intValue());
                }
            }
        }
        List<Object> out = new ArrayList<>();
        for (Session.Breakpoint b : session().setBreakpoints(path, lines)) {
            out.add(json(b));
        }
        return obj("breakpoints", out);
    }

    private static Map<String, Object> json(Session.Breakpoint b) {
        Map<String, Object> m = obj("id", b.id(), "verified", b.verified(), "line", b.line());
        if (b.message() != null) {
            m.put("message", b.message());
        }
        return m;
    }

    private Object threads() {
        List<Object> out = new ArrayList<>();
        if (session != null) {
            for (Session.ThreadInfo t : session.threads()) {
                out.add(obj("id", t.id(), "name", t.name()));
            }
        }
        return obj("threads", out);
    }

    private Object stackTrace(Map<?, ?> args) throws Failure {
        int thread = integer(args, "threadId", 0);
        Session.Stack s = session().stack(thread, integer(args, "startFrame", 0), integer(args, "levels", 0));
        List<Object> out = new ArrayList<>();
        for (Session.Frame f : s.frames()) {
            Map<String, Object> m = obj("id", f.id(), "name", f.name());
            if (f.file() != null) {
                Map<String, Object> src = obj("name", f.file());
                if (f.path() != null) {
                    src.put("path", f.path().toString());
                } else {
                    src.put("presentationHint", "deemphasize");
                }
                m.put("source", src);
            }
            m.put("line", f.line());
            m.put("column", f.line() > 0 ? 1 : 0);
            if (f.subtle()) {
                m.put("presentationHint", "subtle");
            }
            out.add(m);
        }
        return obj("stackFrames", out, "totalFrames", s.total());
    }

    private Object scopes(Map<?, ?> args) throws Failure {
        List<Object> out = new ArrayList<>();
        for (Session.Scope s : session().scopes(integer(args, "frameId", 0))) {
            Map<String, Object> m = obj("name", s.name());
            if (s.locals()) {
                m.put("presentationHint", "locals");
            }
            m.put("variablesReference", s.ref());
            m.put("expensive", s.expensive());
            out.add(m);
        }
        return obj("scopes", out);
    }

    private Object variables(Map<?, ?> args) throws Failure {
        List<Object> out = new ArrayList<>();
        for (Session.Variable v : session().variables(integer(args, "variablesReference", 0),
                                                      string(args, "filter", null),
                                                      integer(args, "start", 0), integer(args, "count", 0))) {
            Map<String, Object> m = obj("name", v.name(), "value", v.value(), "type", v.type(),
                                        "variablesReference", v.ref());
            if (v.ref() != 0 && v.indexed() > 0) {
                m.put("indexedVariables", v.indexed());
            }
            m.put("evaluateName", v.designator());
            out.add(m);
        }
        return obj("variables", out);
    }

    /** m with the type of v and the reference to its parts. */
    private static Map<String, Object> value(Map<String, Object> m, Session.Variable v) {
        m.put("type", v.type());
        m.put("variablesReference", v.ref());
        if (v.ref() != 0 && v.indexed() > 0) {
            m.put("indexedVariables", v.indexed());
        }
        return m;
    }

    // ---- events of the session

    @Override
    public void output(String category, String text) {
        event("output", obj("category", category, "output", text));
    }

    @Override
    public void stopped(String reason, int thread, List<Integer> breakpoints, String text) {
        Map<String, Object> b = obj("reason", reason, "threadId", thread, "allThreadsStopped", true);
        if (!breakpoints.isEmpty()) {
            b.put("hitBreakpointIds", new ArrayList<Object>(breakpoints));
        }
        if (text != null) {
            b.put("text", text);
        }
        event("stopped", b);
    }

    @Override
    public void breakpoint(Session.Breakpoint b) {
        event("breakpoint", obj("reason", "changed", "breakpoint", json(b)));
    }

    @Override
    public void thread(String reason, int thread) {
        event("thread", obj("reason", reason, "threadId", thread));
    }

    @Override
    public void exited(int code) {
        event("exited", obj("exitCode", code));
    }

    @Override
    public void terminated() {
        event("terminated", null);
    }

    // ---- helpers

    /** An object of the pairs name, value. */
    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int k = 0; k < kv.length; k += 2) {
            m.put((String) kv[k], kv[k + 1]);
        }
        return m;
    }

    private static String string(Map<?, ?> m, String name, String dflt) {
        return m.get(name) instanceof String s ? s : dflt;
    }

    private static int integer(Map<?, ?> m, String name, int dflt) {
        return m.get(name) instanceof Number n ? n.intValue() : dflt;
    }

    private static List<String> strings(Map<?, ?> m, String name) throws Failure {
        Object v = m.get(name);
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> l)) {
            throw new Failure("the attribute " + name + " must be a list of strings");
        }
        List<String> out = new ArrayList<>();
        for (Object x : l) {
            if (!(x instanceof String s)) {
                throw new Failure("the attribute " + name + " must be a list of strings");
            }
            out.add(s);
        }
        return out;
    }
}
