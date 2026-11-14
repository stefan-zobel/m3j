// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug.dap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
 */
public final class Adapter implements Session.Listener {
    private final InputStream in;
    private final OutputStream out;
    private final Path home;
    private int seq = 1;
    private Session session;
    private boolean done;

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
        if (session != null) {
            session.kill();
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

    private synchronized void send(Map<String, Object> m) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("seq", seq++);
        msg.putAll(m);
        byte[] b = Json.write(msg).getBytes(StandardCharsets.UTF_8);
        try {
            out.write(("Content-Length: " + b.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(b);
            out.flush();
        } catch (IOException e) {
            // the client is gone
        }
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
        if (!(o instanceof Map<?, ?> m) || !"request".equals(m.get("type"))) {
            // a response to a request of the adapter, or not a message
            return;
        }
        Object rseq = m.get("seq");
        String command = m.get("command") instanceof String s ? s : "";
        Map<?, ?> args = m.get("arguments") instanceof Map<?, ?> a ? a : Map.of();
        Object body;
        try {
            body = dispatch(command, args);
        } catch (Failure e) {
            respond(rseq, command, null, e.getMessage());
            return;
        } catch (RuntimeException e) {
            respond(rseq, command, null, "internal error: " + e);
            return;
        }
        respond(rseq, command, body, null);
        switch (command) {
            case "launch" -> event("initialized", null);
            case "disconnect" -> done = true;
            default -> {
            }
        }
    }

    private Object dispatch(String command, Map<?, ?> args) throws Failure {
        return switch (command) {
            case "initialize" -> obj("supportsConfigurationDoneRequest", true,
                                     "supportsTerminateRequest", true);
            case "launch" -> launch(args);
            case "setBreakpoints" -> setBreakpoints(args);
            case "configurationDone" -> {
                session().start();
                yield null;
            }
            case "threads" -> threads();
            case "stackTrace" -> stackTrace(args);
            case "continue" -> {
                session().resume();
                yield obj("allThreadsContinued", true);
            }
            case "terminate" -> {
                session().terminate();
                yield null;
            }
            case "disconnect" -> {
                if (session != null) {
                    session.kill();
                }
                yield null;
            }
            default -> throw new Failure("unknown request " + command);
        };
    }

    private Session session() throws Failure {
        if (session == null) {
            throw new Failure("no program: launch first");
        }
        return session;
    }

    // ---- requests

    private Object launch(Map<?, ?> args) throws Failure {
        if (session != null) {
            throw new Failure("a program is running");
        }
        Path cwd = Path.of(string(args, "cwd", System.getProperty("user.dir"))).toAbsolutePath().normalize();
        String jarName = string(args, "jar", null);
        if (jarName == null) {
            throw new Failure("launch needs the attribute jar");
        }
        Path jar = cwd.resolve(jarName).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar)) {
            throw new Failure("no such jar: " + jar);
        }
        String main = "m3.Main";
        try (JarFile jf = new JarFile(jar.toFile())) {
            Manifest mf = jf.getManifest();
            String m = mf == null ? null : mf.getMainAttributes().getValue("Main-Class");
            if (m != null) {
                main = m.trim();
            }
        } catch (IOException e) {
            throw new Failure("not a jar: " + jar);
        }
        List<String> cp = new ArrayList<>();
        Path runtime = home.resolve("m3j-runtime.jar");
        cp.add((Files.isRegularFile(runtime) ? runtime : home.resolve("m3j.jar")).toString());
        cp.add(jar.toString());
        for (String c : strings(args, "classpath")) {
            cp.add(cwd.resolve(c).toString());
        }
        Map<String, String> env = new LinkedHashMap<>();
        if (args.get("env") instanceof Map<?, ?> e) {
            for (Map.Entry<?, ?> x : e.entrySet()) {
                env.put(String.valueOf(x.getKey()), x.getValue() == null ? null : String.valueOf(x.getValue()));
            }
        }
        List<Path> roots = new ArrayList<>();
        for (String r : strings(args, "sourceRoots")) {
            roots.add(cwd.resolve(r));
        }
        Path dir = jar.getParent();
        if (dir != null && dir.getFileName() != null && dir.getFileName().toString().equals("JVM")
            && dir.getParent() != null) {
            // the package of a program that m3build made
            roots.add(dir.getParent());
        }
        roots.add(home.resolve("lib"));
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Session.Launch l = new Session.Launch(java, cp, main, strings(args, "args"), cwd, env,
                                              strings(args, "javaOptions"));
        session = Session.launch(l, new Sources(roots, cwd), this);
        return null;
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

    // ---- events of the session

    @Override
    public void output(String category, String text) {
        event("output", obj("category", category, "output", text));
    }

    @Override
    public void stopped(String reason, int thread, List<Integer> breakpoints) {
        Map<String, Object> b = obj("reason", reason, "threadId", thread, "allThreadsStopped", true);
        if (!breakpoints.isEmpty()) {
            b.put("hitBreakpointIds", new ArrayList<Object>(breakpoints));
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
