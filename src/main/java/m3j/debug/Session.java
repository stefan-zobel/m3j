// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadGroupReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.connect.TransportTimeoutException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.event.ThreadStartEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ThreadDeathRequest;
import com.sun.jdi.request.ThreadStartRequest;

/**
 * A program of Modula-3 under the debugger, on JDI: its breakpoints,
 * threads and stacks in the terms of its sources.
 *
 * <p>The classes of units carry their descriptions (see {@link Info}); a
 * unit is known to the session when its class is prepared. A breakpoint
 * in a file names the units of that file: X.m3 the module X, X.i3 the
 * interface X, G.mg and G.ig the instances of the generic unit G.
 *
 * <p>Threads are numbered by the session from 1, in the order it sees
 * them; the threads of the JVM and the thread that only waits for the
 * program to end are not shown. Frames are numbered from 1 at each stop.
 */
public final class Session {

    /** What the session reports, from its own thread. */
    public interface Listener {
        /** Output of the program, category stdout or stderr. */
        void output(String category, String text);

        /** The program stopped (all its threads), in thread, at the breakpoints. */
        void stopped(String reason, int thread, List<Integer> breakpoints);

        /** A breakpoint changed (it was verified, or its line moved). */
        void breakpoint(Breakpoint b);

        /** A thread started or exited. */
        void thread(String reason, int thread);

        /** The program ended with code. */
        void exited(int code);

        /** The session ended. */
        void terminated();
    }

    /** A breakpoint as the client sees it. */
    public record Breakpoint(int id, boolean verified, int line, String message) {}

    /** A thread of the program. */
    public record ThreadInfo(int id, String name) {}

    /**
     * A frame of Modula-3 code (a procedure with its chunks), or a run of
     * frames of the runtime (name "[runtime]", no file). path is null when
     * the source is not found; subtle frames are shown faintly.
     */
    public record Frame(int id, String name, String file, Path path, int line, boolean subtle) {}

    /** The frames of a stack, from startFrame, and how many there are in all. */
    public record Stack(List<Frame> frames, int total) {}

    /** How to start a program. */
    public record Launch(String java, List<String> classpath, String mainClass, List<String> args, Path cwd,
                         Map<String, String> env, List<String> javaOptions) {}

    /** A failure that the client is told about. */
    public static final class Failure extends Exception {
        private static final long serialVersionUID = 1L;

        public Failure(String message) {
            super(message);
        }
    }

    private record Unit(ReferenceType type, Info info) {}

    /** A frame of the client: frames from..to of the stack of thread. */
    private record FrameRef(ThreadReference thread, int from, int to) {}

    private static final class Bp {
        final int id;
        final String file;
        final int line;
        boolean verified;
        int actual;
        String message;
        final List<BreakpointRequest> requests = new ArrayList<>();

        Bp(int id, String file, int line) {
            this.id = id;
            this.file = file;
            this.line = line;
        }

        Breakpoint view() {
            return new Breakpoint(id, verified, verified ? actual : line, message);
        }
    }

    private static final String BP = "m3j.bp";

    private final VirtualMachine vm;
    private final Process process;
    private final Sources sources;
    private final Listener listener;
    private final ThreadReference initial;
    private final Map<String, Unit> units = new LinkedHashMap<>();
    private final Map<String, List<Bp>> breakpoints = new LinkedHashMap<>();
    private final Map<ThreadReference, Integer> threadIds = new HashMap<>();
    private final Map<Integer, FrameRef> frames = new HashMap<>();
    /** The frames of the threads at this stop. */
    private final Map<ThreadReference, List<Frame>> stacks = new HashMap<>();
    private final List<Thread> drains = new ArrayList<>();
    private int nextBp = 1, nextThread = 1, nextFrame = 1;
    private boolean started, ended, killed;

    private Session(VirtualMachine vm, Process process, Sources sources, Listener listener,
                    ThreadReference initial) {
        this.vm = vm;
        this.process = process;
        this.sources = sources;
        this.listener = listener;
        this.initial = initial;
    }

    /**
     * Starts the program of l, stopped before its first instruction until
     * {@link #start}.
     */
    public static Session launch(Launch l, Sources sources, Listener listener) throws Failure {
        ListeningConnector lc = null;
        for (ListeningConnector c : Bootstrap.virtualMachineManager().listeningConnectors()) {
            if (c.name().equals("com.sun.jdi.SocketListen")) {
                lc = c;
            }
        }
        if (lc == null) {
            throw new Failure("the JDK has no socket connector for a debugger");
        }
        Map<String, Connector.Argument> a = lc.defaultArguments();
        set(a, "localAddress", "127.0.0.1");
        set(a, "port", "0");
        String address;
        try {
            address = lc.startListening(a);
        } catch (IOException | IllegalConnectorArgumentsException e) {
            throw new Failure("cannot listen for the program: " + e.getMessage());
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(l.java());
        cmd.add("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + address);
        cmd.addAll(l.javaOptions());
        cmd.add("-cp");
        cmd.add(String.join(java.io.File.pathSeparator, l.classpath()));
        cmd.add(l.mainClass());
        cmd.addAll(l.args());
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(l.cwd().toFile());
        for (Map.Entry<String, String> e : l.env().entrySet()) {
            if (e.getValue() == null) {
                pb.environment().remove(e.getKey());
            } else {
                pb.environment().put(e.getKey(), e.getValue());
            }
        }
        Process p;
        try {
            p = pb.start();
            p.getOutputStream().close();
        } catch (IOException e) {
            stop(lc, a);
            throw new Failure("cannot start " + l.java() + ": " + e.getMessage());
        }
        List<Thread> drains = List.of(drain(p.getInputStream(), "stdout", listener),
                                      drain(p.getErrorStream(), "stderr", listener));
        VirtualMachine vm = null;
        set(a, "timeout", "500");
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        try {
            while (vm == null && p.isAlive() && System.nanoTime() < end) {
                try {
                    vm = lc.accept(a);
                } catch (TransportTimeoutException e) {
                    // not yet
                }
            }
        } catch (IOException | IllegalConnectorArgumentsException e) {
            p.destroyForcibly();
            throw new Failure("the program did not connect: " + e.getMessage());
        } finally {
            stop(lc, a);
        }
        if (vm == null) {
            p.destroyForcibly();
            join(drains);
            throw new Failure(p.isAlive() ? "the program did not connect"
                : "the program ended before it started, exit code " + exitCode(p));
        }
        ThreadReference initial = null;
        try {
            EventSet es = vm.eventQueue().remove(30000);
            if (es != null) {
                for (Event e : es) {
                    if (e instanceof VMStartEvent s) {
                        initial = s.thread();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Session s = new Session(vm, p, sources, listener, initial);
        s.drains.addAll(drains);
        s.requests();
        Thread t = new Thread(s::loop, "m3j-debug-events");
        t.setDaemon(true);
        t.start();
        return s;
    }

    private static void set(Map<String, Connector.Argument> a, String name, String value) {
        Connector.Argument x = a.get(name);
        if (x != null) {
            x.setValue(value);
        }
    }

    private static void stop(ListeningConnector lc, Map<String, Connector.Argument> a) {
        try {
            lc.stopListening(a);
        } catch (IOException | IllegalConnectorArgumentsException | RuntimeException e) {
            // no longer listening
        }
    }

    private void requests() {
        EventRequestManager erm = vm.eventRequestManager();
        for (String f : List.of("m3.m.*", "m3.i.*")) {
            ClassPrepareRequest r = erm.createClassPrepareRequest();
            r.addClassFilter(f);
            r.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            r.enable();
        }
        ThreadStartRequest ts = erm.createThreadStartRequest();
        ts.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        ts.enable();
        ThreadDeathRequest td = erm.createThreadDeathRequest();
        td.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        td.enable();
        synchronized (this) {
            for (ReferenceType t : vm.allClasses()) {
                if (t.isPrepared()) {
                    prepared(t);
                }
            }
        }
    }

    /** Lets the program run, after the breakpoints are set. */
    public synchronized void start() {
        if (!started && !ended) {
            started = true;
            vm.resume();
        }
    }

    /** Lets the stopped program go on. */
    public synchronized void resume() {
        if (ended) {
            return;
        }
        frames.clear();
        stacks.clear();
        nextFrame = 1;
        try {
            vm.resume();
        } catch (VMDisconnectedException e) {
            // it has ended
        }
    }

    /** Ends the program; the session reports its end. */
    public void terminate() {
        synchronized (this) {
            if (ended) {
                return;
            }
        }
        try {
            vm.exit(1);
        } catch (RuntimeException e) {
            process.destroyForcibly();
        }
    }

    /** Ends the program and the session, without reports. */
    public void kill() {
        synchronized (this) {
            killed = true;
        }
        try {
            vm.exit(1);
        } catch (RuntimeException e) {
            // it has ended
        }
        process.destroyForcibly();
    }

    // ---- breakpoints

    /** Replaces the breakpoints in the file path by those at lines. */
    public synchronized List<Breakpoint> setBreakpoints(String path, List<Integer> lines) {
        List<Bp> old = breakpoints.remove(path);
        if (old != null) {
            for (Bp b : old) {
                for (BreakpointRequest r : b.requests) {
                    try {
                        vm.eventRequestManager().deleteEventRequest(r);
                    } catch (VMDisconnectedException e) {
                        // it has ended
                    }
                }
            }
        }
        String file = Sources.baseName(path);
        List<Bp> bps = new ArrayList<>();
        List<Breakpoint> out = new ArrayList<>();
        for (int line : lines) {
            Bp b = new Bp(nextBp++, file, line);
            if (!isSource(file)) {
                b.message = "not a source of Modula-3";
            } else if (!ended) {
                for (Map.Entry<String, Unit> u : units.entrySet()) {
                    if (matches(file, u.getKey(), u.getValue().info())) {
                        resolve(b, u.getValue());
                    } else if (!b.verified && instance(file, u.getKey(), u.getValue().info())) {
                        b.message = "the code of this instance is in " + u.getValue().info().generic;
                    }
                }
            }
            bps.add(b);
            out.add(b.view());
        }
        breakpoints.put(path, bps);
        return out;
    }

    private static boolean isSource(String file) {
        return file.endsWith(".m3") || file.endsWith(".i3") || file.endsWith(".mg") || file.endsWith(".ig");
    }

    /** Whether the file has code in the class cls (of the unit described by info). */
    private static boolean matches(String file, String cls, Info info) {
        int dot = file.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String base = file.substring(0, dot);
        return switch (file.substring(dot)) {
            case ".m3" -> info.generic == null && cls.equals("m3.m." + base);
            case ".i3" -> info.generic == null && cls.equals("m3.i." + base);
            case ".mg" -> file.equals(info.generic) && cls.startsWith("m3.m.");
            case ".ig" -> file.equals(info.generic) && cls.startsWith("m3.i.");
            default -> false;
        };
    }

    /** Whether the file is that of the instance of a generic unit, the class cls. */
    private static boolean instance(String file, String cls, Info info) {
        int dot = file.lastIndexOf('.');
        if (info.generic == null || dot < 0) {
            return false;
        }
        String base = file.substring(0, dot);
        return switch (file.substring(dot)) {
            case ".m3" -> cls.equals("m3.m." + base);
            case ".i3" -> cls.equals("m3.i." + base);
            default -> false;
        };
    }

    /**
     * Sets the breakpoint b in the class of unit u, at its line or, if that
     * line has no code, at the next line with code of the same procedure.
     * Returns whether b changed.
     */
    private boolean resolve(Bp b, Unit u) {
        Breakpoint before = b.view();
        // the lines with code, and the procedure of each
        TreeMap<Integer, String> lines = new TreeMap<>();
        List<Method> described = new ArrayList<>();
        try {
            for (Method m : u.type().methods()) {
                Info.Method im = u.info().methods.get(m.name());
                if (im == null) {
                    continue;
                }
                described.add(m);
                try {
                    for (Location l : m.allLineLocations()) {
                        lines.putIfAbsent(l.lineNumber(), im.name());
                    }
                } catch (AbsentInformationException e) {
                    // no lines
                }
            }
        } catch (VMDisconnectedException | ObjectCollectedException e) {
            return false;
        }
        int target = -1;
        if (lines.containsKey(b.line)) {
            target = b.line;
        } else {
            Integer p = lines.floorKey(b.line), n = lines.ceilingKey(b.line);
            if (p != null && n != null && lines.get(p).equals(lines.get(n))) {
                target = n;
            }
        }
        if (target < 0) {
            if (!b.verified) {
                b.message = "no code at line " + b.line;
            }
            return !before.equals(b.view());
        }
        EventRequestManager erm = vm.eventRequestManager();
        for (Method m : described) {
            try {
                for (Location l : m.locationsOfLine(target)) {
                    BreakpointRequest r = erm.createBreakpointRequest(l);
                    r.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                    r.putProperty(BP, b.id);
                    r.enable();
                    b.requests.add(r);
                }
            } catch (AbsentInformationException e) {
                // no lines
            }
        }
        if (!b.verified) {
            b.verified = true;
            b.actual = target;
            b.message = null;
        }
        return !before.equals(b.view());
    }

    // ---- events

    private void loop() {
        try {
            while (true) {
                EventSet es = vm.eventQueue().remove();
                List<Integer> hits = new ArrayList<>();
                ThreadReference where = null;
                boolean disconnected = false;
                synchronized (this) {
                    for (Event e : es) {
                        switch (e) {
                            case ClassPrepareEvent c -> prepared(c.referenceType());
                            case BreakpointEvent b -> {
                                Object id = b.request().getProperty(BP);
                                if (id instanceof Integer i && known(i)) {
                                    where = b.thread();
                                    if (!hits.contains(i)) {
                                        hits.add(i);
                                    }
                                }
                            }
                            case ThreadStartEvent t -> {
                                if (shown(t.thread())) {
                                    listener.thread("started", id(t.thread()));
                                }
                            }
                            case ThreadDeathEvent t -> {
                                Integer id = threadIds.remove(t.thread());
                                if (id != null) {
                                    listener.thread("exited", id);
                                }
                            }
                            case VMDisconnectEvent d -> disconnected = true;
                            default -> {
                            }
                        }
                    }
                    if (where != null) {
                        listener.stopped("breakpoint", id(where), hits);
                    }
                }
                if (disconnected) {
                    break;
                }
                if (where == null) {
                    es.resume();
                }
            }
        } catch (InterruptedException | VMDisconnectedException e) {
            // the end
        }
        ended();
    }

    private boolean known(int id) {
        for (List<Bp> l : breakpoints.values()) {
            for (Bp b : l) {
                if (b.id == id) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A class is prepared: a unit, its breakpoints are set. */
    private void prepared(ReferenceType t) {
        String unit = Info.unitOf(t.name());
        if (unit == null || units.containsKey(t.name())) {
            return;
        }
        String text;
        try {
            text = t.sourceDebugExtension();
        } catch (AbsentInformationException | UnsupportedOperationException e) {
            return;
        }
        if (!Info.isInfo(text)) {
            return;
        }
        Unit u = new Unit(t, Info.parse(unit, text));
        units.put(t.name(), u);
        for (List<Bp> l : breakpoints.values()) {
            for (Bp b : l) {
                if (matches(b.file, t.name(), u.info()) && resolve(b, u)) {
                    listener.breakpoint(b.view());
                } else if (!b.verified && b.message == null && instance(b.file, t.name(), u.info())) {
                    b.message = "the code of this instance is in " + u.info().generic;
                    listener.breakpoint(b.view());
                }
            }
        }
    }

    private void ended() {
        synchronized (this) {
            if (ended) {
                return;
            }
            ended = true;
        }
        int code = exitCode(process);
        join(drains);
        boolean quiet;
        synchronized (this) {
            quiet = killed;
        }
        if (!quiet) {
            listener.exited(code);
            listener.terminated();
        }
    }

    private static int exitCode(Process p) {
        try {
            if (p.waitFor(30, TimeUnit.SECONDS)) {
                return p.exitValue();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        p.destroyForcibly();
        return -1;
    }

    // ---- threads

    private int id(ThreadReference t) {
        return threadIds.computeIfAbsent(t, k -> nextThread++);
    }

    /** Whether t is a thread of the program: in the group main, not the one that waits for the program. */
    private boolean shown(ThreadReference t) {
        if (t.equals(initial)) {
            return false;
        }
        try {
            for (ThreadGroupReference g = t.threadGroup(); g != null; g = g.parent()) {
                if (g.name().equals("main")) {
                    return true;
                }
            }
        } catch (ObjectCollectedException | VMDisconnectedException | UnsupportedOperationException e) {
            // gone
        }
        return false;
    }

    /** The threads of the program. */
    public synchronized List<ThreadInfo> threads() {
        if (ended) {
            return List.of();
        }
        List<ThreadInfo> out = new ArrayList<>();
        try {
            for (ThreadReference t : vm.allThreads()) {
                if (shown(t)) {
                    out.add(new ThreadInfo(id(t), t.name()));
                }
            }
        } catch (VMDisconnectedException e) {
            return List.of();
        }
        out.sort(Comparator.comparingInt(ThreadInfo::id));
        return out;
    }

    private ThreadReference thread(int id) throws Failure {
        for (Map.Entry<ThreadReference, Integer> e : threadIds.entrySet()) {
            if (e.getValue() == id) {
                return e.getKey();
            }
        }
        throw new Failure("no thread " + id);
    }

    // ---- stacks

    /** The frames start..start+levels-1 (all if levels is 0) of the stack of thread. */
    public synchronized Stack stack(int thread, int start, int levels) throws Failure {
        if (ended) {
            throw new Failure("the program has ended");
        }
        ThreadReference t = thread(thread);
        List<Frame> all = stacks.get(t);
        if (all == null) {
            all = frames(t);
            stacks.put(t, all);
        }
        int from = Math.min(Math.max(start, 0), all.size());
        int to = levels <= 0 ? all.size() : Math.min(all.size(), from + levels);
        return new Stack(List.copyOf(all.subList(from, to)), all.size());
    }

    /** The frames of the stack of the stopped thread t. */
    private List<Frame> frames(ThreadReference t) throws Failure {
        List<StackFrame> fs;
        try {
            fs = t.frames();
        } catch (IncompatibleThreadStateException e) {
            throw new Failure("the thread is running");
        } catch (VMDisconnectedException | ObjectCollectedException e) {
            throw new Failure("the program has ended");
        }
        List<Frame> all = new ArrayList<>();
        boolean runtime = false;
        for (int i = 0; i < fs.size(); i++) {
            Location loc = fs.get(i).location();
            ReferenceType c = loc.declaringType();
            String mn = loc.method().name();
            Unit u = units.get(c.name());
            Info.Method im = u == null ? null : u.info().methods.get(mn);
            if (im != null) {
                int from = i;
                String root = rootOf(u.info(), mn);
                if (!root.equals(mn)) {
                    // the frames of the chunks of the procedure, up to its own
                    int j = i + 1;
                    while (j < fs.size()) {
                        Location lj = fs.get(j).location();
                        if (!lj.declaringType().equals(c)) {
                            break;
                        }
                        String nj = lj.method().name();
                        if (nj.equals(root)) {
                            j++;
                            break;
                        }
                        if (!u.info().methods.containsKey(nj) || !rootOf(u.info(), nj).equals(root)) {
                            break;
                        }
                        j++;
                    }
                    i = j - 1;
                }
                String file = u.info().generic != null ? u.info().generic : sourceName(c, u.info());
                Path path = sources.find(file);
                int id = nextFrame++;
                frames.put(id, new FrameRef(t, from, i));
                all.add(new Frame(id, im.name(), Sources.baseName(file), path, loc.lineNumber(), path == null));
                runtime = false;
            } else if (!dropped(c.name()) && !runtime) {
                int id = nextFrame++;
                frames.put(id, new FrameRef(t, i, i));
                all.add(new Frame(id, "[runtime]", null, null, 0, true));
                runtime = true;
            }
        }
        return all;
    }

    /** The procedure (or body) of the method mn: itself, or the root of the chunk or loop. */
    private static String rootOf(Info info, String mn) {
        String n = mn;
        for (int k = 0; k < 100; k++) {
            Info.Method m = info.methods.get(n);
            if (m == null || m.kind() != 'C' && m.kind() != 'L' || m.parent() == null) {
                return n;
            }
            n = m.parent();
        }
        return n;
    }

    private static String sourceName(ReferenceType c, Info info) {
        try {
            return c.sourceName();
        } catch (AbsentInformationException e) {
            return info.unit + (c.name().startsWith("m3.i.") ? ".i3" : ".m3");
        }
    }

    /** Whether frames of the class cn are left out of stacks (helpers of the compiler, glue of the JDK). */
    private static boolean dropped(String cn) {
        return cn.startsWith("m3.") || cn.startsWith("java.lang.invoke.") || cn.startsWith("jdk.internal.")
            || cn.contains("$$Lambda") || cn.contains("LambdaForm$");
    }

    // ---- output

    private static Thread drain(InputStream in, String category, Listener listener) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[8192];
            byte[] carry = new byte[0];
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    byte[] b = new byte[carry.length + n];
                    System.arraycopy(carry, 0, b, 0, carry.length);
                    System.arraycopy(buf, 0, b, carry.length, n);
                    int keep = incomplete(b);
                    carry = java.util.Arrays.copyOfRange(b, b.length - keep, b.length);
                    String s = decode(b, b.length - keep);
                    if (!s.isEmpty()) {
                        listener.output(category, s);
                    }
                }
                if (carry.length > 0) {
                    listener.output(category, new String(carry, StandardCharsets.ISO_8859_1));
                }
            } catch (IOException e) {
                // the program has ended
            }
        }, "m3j-debug-" + category);
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** The number of bytes at the end of b that begin a sequence of UTF-8 that is not complete. */
    private static int incomplete(byte[] b) {
        for (int k = 1; k <= 3 && k <= b.length; k++) {
            int c = b[b.length - k] & 0xFF;
            if (c >= 0xC0) {
                int len = c >= 0xF0 ? 4 : c >= 0xE0 ? 3 : 2;
                return len > k ? k : 0;
            }
            if (c < 0x80) {
                return 0;
            }
        }
        return 0;
    }

    /** The first n bytes of b as UTF-8 or, if they are not, as ISO-8859-1. */
    private static String decode(byte[] b, int n) {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(b, 0, n)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, 0, n, StandardCharsets.ISO_8859_1);
        }
    }

    private static void join(List<Thread> ts) {
        for (Thread t : ts) {
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
