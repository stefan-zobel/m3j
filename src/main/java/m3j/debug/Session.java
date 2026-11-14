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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadGroupReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.connect.TransportTimeoutException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.event.ThreadStartEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;
import com.sun.jdi.request.StepRequest;
import com.sun.jdi.request.ThreadDeathRequest;
import com.sun.jdi.request.ThreadStartRequest;

/**
 * A program of Modula-3 under the debugger, on JDI: its breakpoints,
 * threads and stacks in the terms of its sources.
 *
 * <p>The session starts the program itself ({@link #launch}), or someone
 * else does with the command that {@link #listen} gives (a terminal of
 * the client), or it attaches to a program that runs with the agent of
 * JDWP as a server ({@link #attach}). Either way the program is stopped
 * until {@link #start}. The exit code of a program that the session did
 * not start is the status it passes to Runtime.exit or Runtime.halt.
 *
 * <p>The classes of units carry their descriptions (see {@link Info}); a
 * unit is known to the session when its class is prepared. A breakpoint
 * in a file names the units of that file: X.m3 the module X, X.i3 the
 * interface X, G.mg and G.ig the instances of the generic unit G.
 *
 * <p>Threads are numbered by the session from 1, in the order it sees
 * them; the threads of the JVM and the thread that only waits for the
 * program to end are not shown. Frames are numbered from 1 at each stop,
 * and so are the references to scopes and values whose parts can be
 * listed.
 *
 * <p>The scopes of a frame are its locals (the variables of the procedure
 * that are in scope at its line, the formals among them), the variables
 * of each enclosing procedure that its nested procedures use (only those
 * live in a frame that the nested ones reach), and the variables of its
 * unit (with those of the interface of the same name).
 *
 * <p>A step is made of steps of the JVM from line to line, until the
 * thread is on a new line of the same procedure, in a procedure it called
 * (step in) or in a procedure that called it: the chunks of a procedure
 * count as the procedure, and the code of the runtime and the JDK is run
 * through. Where a step returns from a chunk into its procedure it goes
 * on, as the line there is still that of the statement before the chunk.
 *
 * <p>The program stops at exceptions by three filters: every RAISE, every
 * runtime error (a checked error of the language, including those that
 * the JVM finds, such as a subscript out of range), and every exception or
 * runtime error that no TRY EXCEPT on the stack handles. Whether one does
 * is judged by the lines of the TRY statements in the descriptions of the
 * units (an exception raised on the line of the TRY counts as raised in
 * it) and by the RAISES clauses that turn an exception into a runtime
 * error; the stack is that of the RAISE, not unwound.
 *
 * <p>An expression is a designator, or FIRST, LAST or NUMBER of one (see
 * {@link Eval}); its names are those of the scopes of the frame, and
 * Unit.name for a variable of any unit. A variable or a part of a value
 * of a scalar type (an ordinal, a real type, TEXT, a reference) can be
 * set to a literal of its type, a reference only to NIL; a value is
 * checked against the range of its type, not against the rules of
 * READONLY.
 */
public final class Session {

    /** What the session reports, from its own thread. */
    public interface Listener {
        /** Output of the program, category stdout or stderr. */
        void output(String category, String text);

        /**
         * The program stopped (all its threads), in thread, at the
         * breakpoints; text says more about an exception, or is null.
         */
        void stopped(String reason, int thread, List<Integer> breakpoints, String text);

        /** A breakpoint changed (it was verified, or its line moved). */
        void breakpoint(Breakpoint b);

        /** A thread started or exited. */
        void thread(String reason, int thread);

        /** The program ended with code (not reported if the code is not known). */
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

    /** A scope of a frame: its variables are listed by ref; locals are those of the procedure itself. */
    public record Scope(String name, boolean locals, int ref, boolean expensive) {}

    /**
     * A variable, or a part of a value: its value in one line, its type,
     * the reference to its parts (0 if it has none), the number of its
     * elements if it is an array, and the designator that names it.
     */
    public record Variable(String name, String value, String type, int ref, int indexed, String designator) {}

    /** How far a step goes. */
    public enum StepKind {
        /** to the next line of the procedure, or of the procedure that called it */
        OVER,
        /** like OVER, or into a procedure that is called */
        IN,
        /** to the procedure that called this one */
        OUT
    }

    /**
     * The exception the program stopped at: its name (Main.Oops,
     * RuntimeError.E), what happened, and why the program stopped (always:
     * by the filter of every RAISE or every runtime error, unhandled: no
     * handler).
     */
    public record ExceptionDetails(String id, String description, String breakMode) {}

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

    /** The parts of a scope, read when they are asked for. */
    private interface Parts {
        List<Values.Part> get();
    }

    /** What a reference stands for: a scope, or a value with parts. */
    private sealed interface Container permits ScopeRef, ValueRef {}

    private record ScopeRef(Parts parts) implements Container {}

    private record ValueRef(Values.Part part) implements Container {}

    /** At most this many elements of an array when the client asks for all of them. */
    private static final int ELEMENTS = 10000;

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

    /** A step under way: where it started (its depth in frames of Modula-3 code, its line). */
    private static final class Step {
        final ThreadReference thread;
        final StepKind kind;
        final int depth;
        final int line;
        /** the reason of the stop at its end */
        final String reason;
        /** the number of frames of the JVM at the last event */
        int frames;
        StepRequest request;

        Step(ThreadReference thread, StepKind kind, int depth, int line, String reason, int frames) {
            this.thread = thread;
            this.kind = kind;
            this.depth = depth;
            this.line = line;
            this.reason = reason;
            this.frames = frames;
        }
    }

    /** The frames from..to of a stack, the top one first: a procedure (root) with its chunks. */
    private record Run(int from, int to, Unit unit, Info.Method method, String root) {}

    /** The property of a breakpoint of the client: its id. */
    private static final String BP = "m3j.bp";
    /** The property of the other requests of the session: what they are for. */
    private static final String KIND = "m3j.kind";

    /** The class of runtime errors. */
    private static final String RUNTIME_ERROR = "m3.i.RuntimeError$E";
    /** The class of Java exceptions caught by Modula-3 code. */
    private static final String JAVA_EXCEPTION = "m3.i.Java$Exception";
    /** The exceptions of the JVM that are runtime errors in Modula-3 code (see m3j.rt.Rt.toM3). */
    private static final Map<String, Integer> JVM_ERRORS = Map.of(
        "java.lang.NullPointerException", m3.i.RuntimeError.BadMemoryReference,
        "java.lang.ArithmeticException", m3.i.RuntimeError.IntegerDivideByZero,
        "java.lang.IndexOutOfBoundsException", m3.i.RuntimeError.SubscriptOutOfRange,
        "java.lang.ClassCastException", m3.i.RuntimeError.NarrowFailed,
        "java.lang.StackOverflowError", m3.i.RuntimeError.StackOverflow);
    /** The classes whose frames end a thread of the program: what they catch is not handled. */
    private static final Set<String> TOPS = Set.of("m3j.rt.Rt", "m3.i.Thread");
    /** The classes a step runs through without stopping. */
    private static final List<String> RUN_THROUGH = List.of(
        "java.*", "javax.*", "jdk.*", "sun.*", "com.sun.*", "m3j.*", "m3.t.*", "m3.p.*", "m3.r.*", "m3.Main");

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
    /** The references of this stop. */
    private final Map<Integer, Container> refs = new HashMap<>();
    private final TypeTable types = new TypeTable();
    private final Values values = new Values(types, this::procedure);
    private final List<Thread> drains = new ArrayList<>();
    private int nextBp = 1, nextThread = 1, nextFrame = 1, nextRef = 1;
    private boolean started, ended, killed;
    /**
     * the status that the program gave to exit or halt of java.lang.Runtime,
     * if the session did not start it (else its process tells), or null
     */
    private Integer status;
    /** whether the program is stopped (by the session, for the client) */
    private boolean stopped;
    /** the filters of exceptions, and stopping at the start of the main module */
    private boolean onRaise, onError, onUnhandled, onEntry;
    /** the requests in the runtime for the filters of exceptions */
    private final List<EventRequest> raiseHooks = new ArrayList<>(), errorHooks = new ArrayList<>();
    /** the classes whose requests are made */
    private final Set<String> hooked = new HashSet<>();
    /** the exceptions of the JVM seen (their ids), so that one thrown again is not reported again */
    private final Set<Long> seen = new LinkedHashSet<>();
    /** the step under way, or null */
    private Step step;
    /** the exception that each thread stopped at, at this stop */
    private final Map<ThreadReference, ExceptionDetails> exceptions = new HashMap<>();

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
        Waiting w = listen(l);
        ProcessBuilder pb = new ProcessBuilder(w.command()).directory(l.cwd().toFile());
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
            stop(w.lc, w.args);
            throw new Failure("cannot start " + l.java() + ": " + e.getMessage());
        }
        List<Thread> drains = List.of(drain(p.getInputStream(), "stdout", listener),
                                      drain(p.getErrorStream(), "stderr", listener));
        return w.accept(p, drains, sources, listener);
    }

    /**
     * A program that someone else starts (the client, in a terminal): it is
     * to run the command, which connects the program to the session.
     */
    public static final class Waiting {
        private final ListeningConnector lc;
        private final Map<String, Connector.Argument> args;
        private final List<String> command;
        private volatile String cancelled;

        private Waiting(ListeningConnector lc, Map<String, Connector.Argument> args, List<String> command) {
            this.lc = lc;
            this.args = args;
            this.command = List.copyOf(command);
        }

        /** The command that starts the program. */
        public List<String> command() {
            return command;
        }

        /** Gives up waiting for the program, because of why. */
        public void cancel(String why) {
            cancelled = why;
        }

        /**
         * The session of the program, once it has connected (within a
         * minute), stopped before its first instruction until {@link #start}.
         */
        public Session accept(Sources sources, Listener listener) throws Failure {
            return accept(null, List.of(), sources, listener);
        }

        private Session accept(Process p, List<Thread> drains, Sources sources, Listener listener)
            throws Failure {
            VirtualMachine vm = null;
            set(args, "timeout", "500");
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            try {
                while (vm == null && (p == null || p.isAlive()) && cancelled == null
                       && System.nanoTime() < end) {
                    try {
                        vm = lc.accept(args);
                    } catch (TransportTimeoutException e) {
                        // not yet
                    }
                }
            } catch (IOException | IllegalConnectorArgumentsException e) {
                if (p != null) {
                    p.destroyForcibly();
                }
                throw new Failure("the program did not connect: " + e.getMessage());
            } finally {
                stop(lc, args);
            }
            if (vm == null) {
                if (p == null) {
                    throw new Failure(cancelled != null ? cancelled : "the program did not connect");
                }
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
            return open(vm, p, drains, sources, listener, initial);
        }
    }

    /**
     * Listens for the program of l, which the command of the result starts
     * and connects to the session.
     */
    public static Waiting listen(Launch l) throws Failure {
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
        return new Waiting(lc, a, cmd);
    }

    /**
     * Attaches to the program that runs with the agent of JDWP as a server
     * at host:port. The program is stopped until {@link #start}.
     */
    public static Session attach(String host, int port, Sources sources, Listener listener) throws Failure {
        AttachingConnector ac = null;
        for (AttachingConnector c : Bootstrap.virtualMachineManager().attachingConnectors()) {
            if (c.name().equals("com.sun.jdi.SocketAttach")) {
                ac = c;
            }
        }
        if (ac == null) {
            throw new Failure("the JDK has no socket connector for a debugger");
        }
        Map<String, Connector.Argument> a = ac.defaultArguments();
        set(a, "hostname", host);
        set(a, "port", Integer.toString(port));
        set(a, "timeout", "10000");
        VirtualMachine vm;
        try {
            vm = ac.attach(a);
        } catch (IOException | IllegalConnectorArgumentsException e) {
            throw new Failure("cannot attach to " + host + ":" + port + ": " + e.getMessage());
        }
        ThreadReference initial = null;
        try {
            // a program that waits for the debugger (suspend=y) reports its start, stopped
            EventSet es = vm.eventQueue().remove(1000);
            boolean stopped = false;
            if (es != null) {
                for (Event e : es) {
                    if (e instanceof VMStartEvent s) {
                        initial = s.thread();
                        stopped = es.suspendPolicy() == EventRequest.SUSPEND_ALL;
                    }
                }
            }
            if (!stopped) {
                vm.suspend();
            }
            if (initial == null) {
                initial = launcher(vm);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (VMDisconnectedException e) {
            throw new Failure("the program at " + host + ":" + port + " has ended");
        }
        return open(vm, null, List.of(), sources, listener, initial);
    }

    /** The thread that started the program and waits for it to end (its first frame is m3.Main), or null. */
    private static ThreadReference launcher(VirtualMachine vm) {
        for (ThreadReference t : vm.allThreads()) {
            try {
                int n = t.frameCount();
                if (t.name().equals("main") && n > 0
                    && t.frame(n - 1).location().declaringType().name().equals("m3.Main")) {
                    return t;
                }
            } catch (IncompatibleThreadStateException | ObjectCollectedException e) {
                // not this one
            }
        }
        return null;
    }

    private static Session open(VirtualMachine vm, Process p, List<Thread> drains, Sources sources,
                                Listener listener, ThreadReference initial) {
        Session s = new Session(vm, p, sources, listener, initial);
        s.drains.addAll(drains);
        s.requests();
        Thread t = new Thread(s::loop, "m3j-debug-events");
        t.setDaemon(true);
        t.start();
        return s;
    }

    /**
     * Runs cmd in dir to its end, with what it prints (also on standard
     * error) as output of category: its exit code.
     */
    public static int run(List<String> cmd, Path dir, String category, Listener listener) throws Failure {
        Process p;
        try {
            p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
            p.getOutputStream().close();
        } catch (IOException e) {
            throw new Failure("cannot start " + cmd.get(0) + ": " + e.getMessage());
        }
        Thread d = drain(p.getInputStream(), category, listener);
        try {
            int code = p.waitFor();
            d.join();
            return code;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            return -1;
        }
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
        List<String> prepare = new ArrayList<>(List.of("m3.m.*", "m3.i.*", "m3j.rt.M3Exception", "m3j.rt.Rt"));
        prepare.addAll(JVM_ERRORS.keySet());
        if (process == null) {
            prepare.add("java.lang.Runtime");
        }
        for (String f : prepare) {
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

    /** Lets the stopped program go on (and the step, if one is made). */
    public synchronized void resume() {
        if (ended) {
            return;
        }
        frames.clear();
        stacks.clear();
        refs.clear();
        exceptions.clear();
        nextFrame = 1;
        nextRef = 1;
        stopped = false;
        try {
            vm.resume();
        } catch (VMDisconnectedException e) {
            // it has ended
        }
    }

    /** Whether the program stops at the first line of its main module; set before {@link #start}. */
    public synchronized void stopOnEntry(boolean on) {
        onEntry = on;
    }

    /**
     * Sets the filters of exceptions: stop at every RAISE, at every runtime
     * error, at every exception and runtime error that is not handled.
     */
    public synchronized void exceptionFilters(boolean raise, boolean error, boolean unhandled) {
        onRaise = raise;
        onError = error;
        onUnhandled = unhandled;
        if (!ended) {
            try {
                filters();
            } catch (VMDisconnectedException e) {
                // it has ended
            }
        }
    }

    private void filters() {
        for (EventRequest r : raiseHooks) {
            r.setEnabled(onRaise || onUnhandled);
        }
        for (EventRequest r : errorHooks) {
            r.setEnabled(onError || onUnhandled);
        }
    }

    /**
     * Prepares a step of thread; it is made by {@link #resume}, and the
     * program stops at its end.
     */
    public synchronized void step(int thread, StepKind kind) throws Failure {
        if (ended) {
            throw new Failure("the program has ended");
        }
        if (!stopped) {
            throw new Failure("the program is running");
        }
        ThreadReference t = thread(thread);
        try {
            List<StackFrame> fs = t.frames();
            List<Run> rs = runs(fs);
            int line = rs.isEmpty() ? -1 : fs.get(rs.get(0).from()).location().lineNumber();
            cancelStep();
            step = new Step(t, kind, rs.size(), line, "step", fs.size());
            boolean m3 = !rs.isEmpty() && rs.get(0).from() == 0;
            step.request = request(t, kind == StepKind.OUT || !m3 && !helper(fs.get(0).location())
                ? StepRequest.STEP_OUT : StepRequest.STEP_INTO);
        } catch (IncompatibleThreadStateException | InvalidStackFrameException e) {
            throw new Failure("the thread is running");
        } catch (VMDisconnectedException | ObjectCollectedException e) {
            throw new Failure("the program has ended");
        }
    }

    /**
     * Stops the program if it runs, and returns the thread to report the
     * stop in (thread, if it is one); 0 if the program was stopped.
     */
    public synchronized int pause(int thread) throws Failure {
        if (ended) {
            throw new Failure("the program has ended");
        }
        if (!started) {
            throw new Failure("the program has not started: configurationDone first");
        }
        if (stopped) {
            return 0;
        }
        try {
            vm.suspend();
            List<ThreadInfo> ts = threads();
            if (ts.isEmpty()) {
                vm.resume();
                throw new Failure("the program has no threads");
            }
            cancelStep();
            stopped = true;
            return threadIds.containsValue(thread) ? thread : ts.get(0).id();
        } catch (VMDisconnectedException e) {
            throw new Failure("the program has ended");
        }
    }

    /** The exception that thread stopped at. */
    public synchronized ExceptionDetails exception(int thread) throws Failure {
        ExceptionDetails d = exceptions.get(thread(thread));
        if (d == null) {
            throw new Failure("the thread did not stop at an exception");
        }
        return d;
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
            if (process != null) {
                process.destroyForcibly();
            }
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
        if (process != null) {
            process.destroyForcibly();
        }
    }

    /** Leaves the program to run on without the debugger, and ends the session without reports. */
    public void detach() {
        synchronized (this) {
            killed = true;
        }
        try {
            vm.dispose();
        } catch (RuntimeException e) {
            // it has ended
        }
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
                StepEvent stepped = null;
                List<Event> others = new ArrayList<>();
                boolean disconnected = false, resume = true;
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
                                } else if (b.request().getProperty(KIND) != null) {
                                    others.add(b);
                                }
                            }
                            case StepEvent s -> stepped = s;
                            case ExceptionEvent x -> others.add(x);
                            case ThreadStartEvent t -> {
                                if (shown(t.thread())) {
                                    listener.thread("started", id(t.thread()));
                                }
                            }
                            case ThreadDeathEvent t -> {
                                if (step != null && step.thread.equals(t.thread())) {
                                    cancelStep();
                                }
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
                    if (!disconnected) {
                        try {
                            if (where != null) {
                                stop("breakpoint", where, hits, null);
                                resume = false;
                            }
                            for (int k = 0; resume && k < others.size(); k++) {
                                resume = !special(others.get(k));
                            }
                            if (resume && stepped != null) {
                                resume = !stepped(stepped);
                            }
                        } catch (IncompatibleThreadStateException | InvalidStackFrameException
                                 | ObjectCollectedException e) {
                            // the thread runs or is gone: no stop
                        }
                    }
                }
                if (disconnected) {
                    break;
                }
                if (resume) {
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

    /** A class is prepared: a unit, its breakpoints are set; a class of the runtime that the filters need. */
    private void prepared(ReferenceType t) {
        if (hook(t)) {
            return;
        }
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
        types.add(u.info());
        if (onEntry && u.info().main) {
            for (Method m : t.methodsByName("$init")) {
                BreakpointRequest r = vm.eventRequestManager().createBreakpointRequest(m.location());
                r.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                r.putProperty(KIND, "entry");
                r.enable();
            }
        }
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

    /**
     * Makes the requests in the class t of the runtime that the filters of
     * exceptions need; whether t is such a class.
     */
    private boolean hook(ReferenceType t) {
        String n = t.name();
        if (n.equals("java.lang.Runtime")) {
            if (process == null && hooked.add(n)) {
                // the exit status of a program that the session did not start
                for (Method m : t.methods()) {
                    if ((m.name().equals("exit") || m.name().equals("halt")) && m.location() != null) {
                        BreakpointRequest r = vm.eventRequestManager().createBreakpointRequest(m.location());
                        r.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                        r.putProperty(KIND, "exit");
                        r.enable();
                    }
                }
            }
            return true;
        }
        boolean runtime = n.equals("m3j.rt.M3Exception") || n.equals("m3j.rt.Rt");
        if (!runtime && !JVM_ERRORS.containsKey(n)) {
            return false;
        }
        if (!hooked.add(n)) {
            return true;
        }
        EventRequestManager erm = vm.eventRequestManager();
        switch (n) {
            case "m3j.rt.M3Exception" -> hook(t, "at", "raise", raiseHooks);
            case "m3j.rt.Rt" -> {
                hook(t, "error", "error", errorHooks);
                hook(t, "assertFailed", "assert", errorHooks);
            }
            default -> {
                ExceptionRequest r = erm.createExceptionRequest(t, true, true);
                r.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                r.putProperty(KIND, "jvm");
                errorHooks.add(r);
            }
        }
        filters();
        return true;
    }

    /** A breakpoint at the start of the methods name of t, for kind. */
    private void hook(ReferenceType t, String name, String kind, List<EventRequest> to) {
        for (Method m : t.methodsByName(name)) {
            Location l = m.location();
            if (l != null) {
                BreakpointRequest r = vm.eventRequestManager().createBreakpointRequest(l);
                r.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                r.putProperty(KIND, kind);
                to.add(r);
            }
        }
    }

    /** Reports a stop of the program, in t. */
    private void stop(String reason, ThreadReference t, List<Integer> hits, String text) {
        cancelStep();
        stopped = true;
        listener.stopped(reason, id(t), hits, text);
    }

    // ---- steps

    private void cancelStep() {
        if (step != null) {
            if (step.request != null) {
                try {
                    vm.eventRequestManager().deleteEventRequest(step.request);
                } catch (VMDisconnectedException e) {
                    // it has ended
                }
            }
            step = null;
        }
    }

    /** A request for a step of t from line to line, into calls or out of the frame (depth). */
    private StepRequest request(ThreadReference t, int depth) {
        StepRequest r = vm.eventRequestManager().createStepRequest(t, StepRequest.STEP_LINE, depth);
        for (String f : RUN_THROUGH) {
            r.addClassExclusionFilter(f);
        }
        r.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        r.enable();
        return r;
    }

    /**
     * Whether l is in code that the compiler made for a unit without lines
     * of its own (a bridge, a procedure value): a step goes through it into
     * what it calls, where it goes out of other code (of the runtime).
     */
    private boolean helper(Location l) {
        String n = l.declaringType().name();
        int k = n.indexOf('$');
        return units.containsKey(k < 0 ? n : n.substring(0, k));
    }

    /** The step of s went on to e; whether the program stopped. */
    private boolean stepped(StepEvent e) throws IncompatibleThreadStateException {
        Step s = step;
        if (s == null || !e.thread().equals(s.thread)) {
            vm.eventRequestManager().deleteEventRequest(e.request());
            return false;
        }
        vm.eventRequestManager().deleteEventRequest(s.request);
        s.request = null;
        List<StackFrame> fs = s.thread.frames();
        List<Run> rs = runs(fs);
        int d = rs.size();
        if (d == 0) {
            // no Modula-3 code is left in the thread: it runs to its end
            step = null;
            return false;
        }
        Location loc = e.location();
        boolean m3 = rs.get(0).from() == 0 && loc.lineNumber() > 0;
        boolean returned = fs.size() < s.frames;
        s.frames = fs.size();
        int depth;
        if (s.kind == StepKind.OUT) {
            if (m3 && d < s.depth) {
                return stepEnd(s);
            }
            depth = StepRequest.STEP_OUT;
        } else if (!m3) {
            depth = !helper(loc) || d > s.depth && s.kind == StepKind.OVER ? StepRequest.STEP_OUT
                : StepRequest.STEP_INTO;
        } else if (d > s.depth) {
            if (s.kind == StepKind.IN) {
                return stepEnd(s);
            }
            depth = StepRequest.STEP_OUT;
        } else if (d < s.depth || lineStart(loc) || !returned && loc.lineNumber() != s.line) {
            return stepEnd(s);
        } else {
            depth = StepRequest.STEP_INTO;
        }
        s.request = request(s.thread, depth);
        return false;
    }

    private boolean stepEnd(Step s) {
        stop(s.reason, s.thread, List.of(), null);
        return true;
    }

    /** Whether l is where the code of a line starts (and not in the middle of a line). */
    private static boolean lineStart(Location l) {
        try {
            for (Location x : l.method().locationsOfLine(l.lineNumber())) {
                if (x.codeIndex() == l.codeIndex()) {
                    return true;
                }
            }
        } catch (AbsentInformationException e) {
            // no lines
        }
        return false;
    }

    // ---- exceptions

    /**
     * The event e of a request of the session: the start of the main
     * module, an exception or a runtime error; whether the program stopped.
     */
    private boolean special(Event e) throws IncompatibleThreadStateException {
        ThreadReference t;
        String kind;
        if (e instanceof BreakpointEvent b) {
            t = b.thread();
            kind = (String) b.request().getProperty(KIND);
        } else if (e instanceof ExceptionEvent x) {
            t = x.thread();
            kind = "jvm";
        } else {
            return false;
        }
        if (kind.equals("exit")) {
            List<Value> a = t.frame(0).getArgumentValues();
            if (!a.isEmpty() && a.get(0) instanceof IntegerValue i) {
                status = i.value();
            }
            return false;
        }
        List<StackFrame> fs = t.frames();
        List<Run> rs = runs(fs);
        if (kind.equals("entry")) {
            vm.eventRequestManager().deleteEventRequest(((BreakpointEvent) e).request());
            Location l = fs.get(0).location();
            if (l.lineNumber() > 0 && lineStart(l)) {
                stop("entry", t, List.of(), null);
                return true;
            }
            cancelStep();
            step = new Step(t, StepKind.OVER, rs.size(), -1, "entry", fs.size());
            step.request = request(t, StepRequest.STEP_INTO);
            return false;
        }
        if (rs.isEmpty()) {
            // not in Modula-3 code
            return false;
        }
        String id = "RuntimeError.E";
        String what;
        List<String> classes = List.of(RUNTIME_ERROR);
        boolean always = onError;
        switch (kind) {
            case "raise" -> {
                if (rs.get(0).from() != 1) {
                    // the place of a runtime error or of an exception that a RAISES clause stopped
                    return false;
                }
                ObjectReference x = fs.get(0).thisObject();
                classes = classes(x.referenceType());
                id = m3Name(x.referenceType().name());
                String arg = argument(x);
                what = arg == null ? id : id + " (" + arg + ")";
                always = onRaise;
            }
            case "error" -> {
                List<Value> a = fs.get(0).getArgumentValues();
                what = tag(!a.isEmpty() && a.get(0) instanceof IntegerValue i ? i.value() : -1);
            }
            case "assert" -> {
                List<Value> a = fs.get(0).getArgumentValues();
                what = "<*ASSERT*> failed" + (!a.isEmpty() && a.get(0) instanceof StringReference m
                    ? ": " + m.value() : "");
            }
            default -> {
                ExceptionEvent x = (ExceptionEvent) e;
                ObjectReference o = x.exception();
                if (!seen.add(o.uniqueID())) {
                    return false;
                }
                if (seen.size() > 1000) {
                    seen.remove(seen.iterator().next());
                }
                Location c = x.catchLocation();
                if (c != null && !units.containsKey(c.declaringType().name())
                    && !TOPS.contains(c.declaringType().name())) {
                    // caught where it is thrown, in the runtime or the JDK
                    return false;
                }
                Integer code = null;
                for (String n : classes(o.referenceType())) {
                    code = code != null ? code : JVM_ERRORS.get(n);
                }
                what = tag(code == null ? -1 : code);
            }
        }
        if (!always && (!onUnhandled || handled(fs, rs, classes))) {
            return false;
        }
        exceptions.put(t, new ExceptionDetails(id, what, always ? "always" : "unhandled"));
        stop("exception", t, List.of(), what);
        return true;
    }

    /**
     * Whether an exception of one of classes (a class and its superclasses)
     * raised in the stack fs (with the runs rs) is handled by a TRY EXCEPT.
     */
    private static boolean handled(List<StackFrame> fs, List<Run> rs, List<String> classes) {
        List<String> cs = classes;
        for (Run r : rs) {
            int line = fs.get(r.from()).location().lineNumber();
            Info info = r.unit().info();
            for (Info.Method m : info.methods.values()) {
                if (m.tries().isEmpty() || !rootOf(info, m.method()).equals(r.root())) {
                    continue;
                }
                for (Info.Try t : m.tries()) {
                    for (String c : cs) {
                        if (t.handles(c, line)) {
                            return true;
                        }
                    }
                }
            }
            Info.Method root = info.methods.get(r.root());
            if (root != null && root.raises() != null && !cs.contains(RUNTIME_ERROR) && !cs.contains(JAVA_EXCEPTION)
                && cs.stream().noneMatch(root.raises()::contains)) {
                // not in the RAISES clause: a runtime error from here on
                cs = List.of(RUNTIME_ERROR);
            }
        }
        return false;
    }

    /** The names of class t and its superclasses. */
    private static List<String> classes(ReferenceType t) {
        List<String> out = new ArrayList<>();
        for (ReferenceType c = t; c != null; c = c instanceof ClassType ct ? ct.superclass() : null) {
            out.add(c.name());
        }
        return out;
    }

    /** The name of the exception of class cls as Modula-3 has it: m3.m.Main$Oops$35 is Main.Oops. */
    static String m3Name(String cls) {
        String s = cls.startsWith("m3.m.") || cls.startsWith("m3.i.") ? cls.substring(5) : cls;
        String[] p = s.split("\\$");
        return p.length >= 2 ? p[0] + "." + p[1] : s;
    }

    /** The argument of the exception x, shown, or null if it has none. */
    private String argument(ObjectReference x) {
        com.sun.jdi.Field f = x.referenceType().fieldByName("arg");
        Value v = f == null ? null : x.getValue(f);
        if (!(v instanceof ObjectReference o)) {
            return null;
        }
        if (o instanceof StringReference s) {
            return Values.text(s.value());
        }
        com.sun.jdi.Field value = o.referenceType().name().startsWith("java.lang.")
            ? o.referenceType().fieldByName("value") : null;
        if (value != null && o.getValue(value) instanceof com.sun.jdi.PrimitiveValue p) {
            return p.toString();
        }
        return values.show("REFANY", new Values.Plain(o));
    }

    /** The text of the runtime error code. */
    private static String tag(long code) {
        try {
            return m3.i.RuntimeError.Tag((int) code);
        } catch (RuntimeException e) {
            return "runtime error " + code;
        }
    }

    private void ended() {
        synchronized (this) {
            if (ended) {
                return;
            }
            ended = true;
        }
        Integer code = process != null ? Integer.valueOf(exitCode(process)) : null;
        join(drains);
        boolean quiet;
        synchronized (this) {
            quiet = killed;
            if (code == null) {
                code = status;
            }
        }
        if (!quiet) {
            if (code != null) {
                listener.exited(code);
            }
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
        // at an exception, the frames of the runtime above the Modula-3 code
        // are those of the RAISE: not shown
        boolean top = exceptions.containsKey(t);
        int i = 0;
        List<Run> rs = runs(fs);
        for (Run r : rs) {
            if (!top) {
                runtime(t, fs, i, r.from(), all);
            }
            top = false;
            Location loc = fs.get(r.from()).location();
            ReferenceType c = loc.declaringType();
            Info info = r.unit().info();
            String file = info.generic != null ? info.generic : sourceName(c, info);
            Path path = sources.find(file);
            int id = nextFrame++;
            frames.put(id, new FrameRef(t, r.from(), r.to()));
            all.add(new Frame(id, r.method().name(), Sources.baseName(file), path, loc.lineNumber(), path == null));
            i = r.to() + 1;
        }
        runtime(t, fs, i, fs.size(), all);
        return all;
    }

    /** Adds a frame [runtime] for the frames from..to-1 of t (fs) if any of them is shown. */
    private void runtime(ThreadReference t, List<StackFrame> fs, int from, int to, List<Frame> all) {
        for (int i = from; i < to; i++) {
            if (!dropped(fs.get(i).location().declaringType().name())) {
                int id = nextFrame++;
                frames.put(id, new FrameRef(t, i, i));
                all.add(new Frame(id, "[runtime]", null, null, 0, true));
                return;
            }
        }
    }

    /** The runs of frames of Modula-3 code in the stack fs, the top one first. */
    private List<Run> runs(List<StackFrame> fs) {
        List<Run> out = new ArrayList<>();
        for (int i = 0; i < fs.size(); i++) {
            Location loc = fs.get(i).location();
            ReferenceType c = loc.declaringType();
            String mn = loc.method().name();
            Unit u = units.get(c.name());
            Info.Method im = u == null ? null : u.info().methods.get(mn);
            if (im == null) {
                continue;
            }
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
            out.add(new Run(from, i, u, im, root));
        }
        return out;
    }

    private FrameRef frameRef(int frame) throws Failure {
        if (ended) {
            throw new Failure("the program has ended");
        }
        FrameRef f = frames.get(frame);
        if (f == null) {
            throw new Failure("no frame " + frame);
        }
        return f;
    }

    // ---- variables

    /** The scopes of frame. */
    public synchronized List<Scope> scopes(int frame) throws Failure {
        List<Scope> out = new ArrayList<>();
        for (Level l : levels(frameRef(frame))) {
            out.add(new Scope(l.name(), l.locals(), ref(new ScopeRef(l.parts())), l.expensive()));
        }
        return out;
    }

    /** A scope of a frame before it is given a reference. */
    private record Level(String name, boolean locals, Parts parts, boolean expensive) {}

    /** The scopes of the frame f: its locals, those of the enclosing procedures, those of its unit. */
    private List<Level> levels(FrameRef f) throws Failure {
        try {
            ThreadReference t = f.thread();
            Location loc = t.frame(f.from()).location();
            Unit u = units.get(loc.declaringType().name());
            if (u == null || !u.info().methods.containsKey(loc.method().name())) {
                return List.of();
            }
            int line = loc.lineNumber();
            String root = rootOf(u.info(), loc.method().name());
            List<StackFrame> run = new ArrayList<>();
            for (int i = f.from(); i <= f.to(); i++) {
                run.add(t.frame(i));
            }
            List<Level> out = new ArrayList<>();
            out.add(new Level("Locals", true, () -> locals(u, root, run, line), false));
            ObjectReference up = link(run);
            for (int k = 0; up != null && k < 100; k++) {
                ObjectReference fo = up;
                Unit fu = frameUnit(fo);
                Info.Method fm = fu == null ? null : frameMethod(fu, fo);
                if (fm == null) {
                    break;
                }
                out.add(new Level(fm.name(), false, () -> enclosing(fu, fm.method(), fo, line), false));
                Values.Home h = Values.fields(fo);
                up = h.has("up") && h.get("up") instanceof ObjectReference o ? o : null;
            }
            boolean module = u.type().name().startsWith("m3.m.");
            out.add(new Level((module ? "Module " : "Interface ") + u.info().unit, false, () -> globals(u), true));
            return out;
        } catch (IncompatibleThreadStateException | InvalidStackFrameException e) {
            throw new Failure("the thread is running");
        } catch (VMDisconnectedException | ObjectCollectedException e) {
            throw new Failure("the program has ended");
        }
    }

    private int ref(Container c) {
        int id = nextRef++;
        refs.put(id, c);
        return id;
    }

    // ---- expressions

    /**
     * The value of the expression (see {@link Eval}) in frame, or only of
     * the variables of units (Unit.name) if frame is 0.
     */
    public synchronized Variable evaluate(String expression, int frame) throws Failure {
        Values.Part p = part(expression, frame);
        return variable(p.designator(), p);
    }

    /** Sets the variable or the part of a value that expression names in frame to value. */
    public synchronized Variable setExpression(String expression, String value, int frame) throws Failure {
        Values.Part p = part(expression, frame);
        return variable(p.designator(), set(p, value));
    }

    /** Sets the variable or the part name of the scope or value ref to value. */
    public synchronized Variable setVariable(int ref, String name, String value) throws Failure {
        if (ended) {
            throw new Failure("the program has ended");
        }
        Container c = refs.get(ref);
        if (c == null) {
            throw new Failure("no variables " + ref);
        }
        Values.Part p = null;
        try {
            switch (c) {
                case ScopeRef s -> {
                    for (Values.Part x : s.parts().get()) {
                        if (x.name().equals(name)) {
                            p = x;
                            break;
                        }
                    }
                    if (p == null) {
                        throw new Failure("no variable " + name);
                    }
                }
                case ValueRef v -> {
                    Eval e = new Eval(values, types, names(List.of()));
                    p = e.select(v.part(), name.startsWith("[") || name.startsWith("^") ? name : "." + name);
                }
            }
        } catch (IllegalArgumentException e) {
            throw new Failure(e.getMessage());
        } catch (InvalidStackFrameException e) {
            throw new Failure("the thread is running");
        } catch (VMDisconnectedException | ObjectCollectedException e) {
            throw new Failure("the program has ended");
        }
        return variable(p.name(), set(p, value));
    }

    /** The part that expression names in frame (0: no frame). */
    private Values.Part part(String expression, int frame) throws Failure {
        if (ended) {
            throw new Failure("the program has ended");
        }
        if (!stopped) {
            throw new Failure("the program is running");
        }
        List<Level> ls = frame == 0 ? List.of() : levels(frameRef(frame));
        try {
            return new Eval(values, types, names(ls)).evaluate(expression);
        } catch (IllegalArgumentException e) {
            throw new Failure(e.getMessage());
        } catch (InvalidStackFrameException e) {
            throw new Failure("the thread is running");
        } catch (VMDisconnectedException | ObjectCollectedException e) {
            throw new Failure("the program has ended");
        }
    }

    /** p set to value. */
    private Values.Part set(Values.Part p, String value) throws Failure {
        try {
            return values.set(p, value);
        } catch (IllegalArgumentException e) {
            throw new Failure(e.getMessage());
        } catch (InvalidStackFrameException e) {
            throw new Failure("the thread is running");
        } catch (VMDisconnectedException | ObjectCollectedException e) {
            throw new Failure("the program has ended");
        }
    }

    /** The names of the scopes ls (the innermost first), and of the units. */
    private Eval.Names names(List<Level> ls) {
        return new Eval.Names() {
            @Override
            public Values.Part variable(String name) {
                for (Level l : ls) {
                    for (Values.Part p : l.parts().get()) {
                        if (p.name().equals(name)) {
                            return p;
                        }
                    }
                }
                return null;
            }

            @Override
            public Values.Part global(String unit, String name) {
                for (String prefix : List.of("m3.i.", "m3.m.")) {
                    Unit u = units.get(prefix + unit);
                    String t = u == null ? null : u.info().globals.get(name);
                    if (t != null) {
                        Values.Home h = Values.statics(u.type());
                        return new Values.Part(name, t, values.read(t, h, name), unit + "." + name);
                    }
                }
                return null;
            }

            @Override
            public boolean isUnit(String name) {
                return units.containsKey("m3.i." + name) || units.containsKey("m3.m." + name);
            }
        };
    }

    /** The variable or value p, called name. */
    private Variable variable(String name, Values.Part p) {
        int n = values.count(p.type(), p.value());
        int r = values.expandable(p.type(), p.value()) ? ref(new ValueRef(p)) : 0;
        return new Variable(name, values.show(p.type(), p.value()), types.show(p.type()), r, Math.max(n, 0),
                            p.designator());
    }

    /**
     * The parts of the scope or value ref: with filter "indexed" the
     * elements start.. (count of them, 0: all) of an array, with "named"
     * the parts that are no elements, else all.
     */
    public synchronized List<Variable> variables(int ref, String filter, int start, int count) throws Failure {
        if (ended) {
            throw new Failure("the program has ended");
        }
        Container c = refs.get(ref);
        if (c == null) {
            throw new Failure("no variables " + ref);
        }
        List<Values.Part> parts;
        try {
            switch (c) {
                case ScopeRef s -> parts = "indexed".equals(filter) ? List.of() : s.parts().get();
                case ValueRef v -> {
                    Values.Part p = v.part();
                    boolean array = values.count(p.type(), p.value()) >= 0;
                    if (array ? "named".equals(filter) : "indexed".equals(filter)) {
                        parts = List.of();
                    } else {
                        parts = values.parts(p.type(), p.value(), p.designator(), array ? start : 0,
                                             array && count <= 0 ? ELEMENTS : count);
                    }
                }
            }
        } catch (InvalidStackFrameException e) {
            throw new Failure("the thread is running");
        } catch (VMDisconnectedException | ObjectCollectedException e) {
            throw new Failure("the program has ended");
        }
        List<Variable> out = new ArrayList<>();
        for (Values.Part p : parts) {
            out.add(variable(p.name(), p));
        }
        return out;
    }

    /**
     * The variables of the procedure root of unit u in scope at line: in
     * the slots of the frames of run (root and its chunks), in the frame
     * of the procedure.
     */
    private List<Values.Part> locals(Unit u, String root, List<StackFrame> run, int line) {
        Map<String, StackFrame> byMethod = new HashMap<>();
        ObjectReference frame = null;
        for (StackFrame sf : run) {
            byMethod.putIfAbsent(sf.location().method().name(), sf);
            if (frame == null && Values.slots(sf).get("$frame") instanceof ObjectReference o) {
                frame = o;
            }
        }
        Map<String, Values.Part> out = new LinkedHashMap<>();
        for (Info.Method m : u.info().methods.values()) {
            if (!rootOf(u.info(), m.method()).equals(root)) {
                continue;
            }
            StackFrame sf = byMethod.get(m.method());
            for (Info.Var v : m.vars()) {
                Values.V val;
                if (v.inSlot()) {
                    if (sf == null) {
                        continue;
                    }
                    Values.Home h = Values.slots(sf);
                    if (!h.has(v.name())) {
                        continue;
                    }
                    val = values.read(v.type(), h, v.name());
                } else if (!covers(v, line)) {
                    continue;
                } else if (v.inFrame()) {
                    if (frame == null) {
                        continue;
                    }
                    val = values.read(v.type(), Values.fields(frame), v.home());
                } else {
                    val = new Values.None("an alias, without a value of its own");
                }
                out.put(v.name(), new Values.Part(v.name(), v.type(), val, v.name()));
            }
        }
        return new ArrayList<>(out.values());
    }

    /** The variables of the procedure method of unit u, in scope at line, in its frame fo. */
    private List<Values.Part> enclosing(Unit u, String method, ObjectReference fo, int line) {
        Map<String, Values.Part> out = new LinkedHashMap<>();
        for (Info.Method m : u.info().methods.values()) {
            if (!rootOf(u.info(), m.method()).equals(method)) {
                continue;
            }
            for (Info.Var v : m.vars()) {
                if (v.inFrame() && covers(v, line)) {
                    out.put(v.name(), new Values.Part(v.name(), v.type(),
                                                      values.read(v.type(), Values.fields(fo), v.home()), v.name()));
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    /** The variables of unit u, and of the interface of the same name. */
    private List<Values.Part> globals(Unit u) {
        List<Values.Part> out = new ArrayList<>();
        List<Unit> us = new ArrayList<>();
        us.add(u);
        Unit i = units.get("m3.i." + u.info().unit);
        if (u.type().name().startsWith("m3.m.") && i != null) {
            us.add(i);
        }
        for (Unit x : us) {
            Values.Home h = Values.statics(x.type());
            for (Map.Entry<String, String> g : x.info().globals.entrySet()) {
                out.add(new Values.Part(g.getKey(), g.getValue(), values.read(g.getValue(), h, g.getKey()), g.getKey()));
            }
        }
        return out;
    }

    private static boolean covers(Info.Var v, int line) {
        return (v.from() <= 0 || v.from() <= line) && (v.to() <= 0 || line <= v.to());
    }

    /** The frame of the procedure that encloses that of run: $up, or the field up of $frame. */
    private static ObjectReference link(List<StackFrame> run) {
        for (StackFrame sf : run) {
            if (Values.slots(sf).get("$up") instanceof ObjectReference o) {
                return o;
            }
        }
        for (StackFrame sf : run) {
            if (Values.slots(sf).get("$frame") instanceof ObjectReference o) {
                Values.Home h = Values.fields(o);
                return h.has("up") && h.get("up") instanceof ObjectReference u ? u : null;
            }
        }
        return null;
    }

    /** The unit of the frame object fo, of class UNITCLASS$F$METHOD. */
    private Unit frameUnit(ObjectReference fo) {
        String n = fo.referenceType().name();
        int k = n.indexOf("$F$");
        return k < 0 ? null : units.get(n.substring(0, k));
    }

    /** The procedure of the frame object fo of unit u. */
    private static Info.Method frameMethod(Unit u, ObjectReference fo) {
        String n = fo.referenceType().name();
        String m = n.substring(n.indexOf("$F$") + 3);
        for (Info.Method im : u.info().methods.values()) {
            if ((im.kind() == 'P' || im.kind() == 'B') && im.method().replace('$', '_').equals(m)) {
                return im;
            }
        }
        return null;
    }

    /**
     * The name of the procedure of a procedure value of class c, or null:
     * a closure of a nested procedure is of class UNITCLASS$C$METHOD, a
     * top-level procedure a lambda whose class calls the method.
     */
    private String procedure(ClassType c) {
        String cls = c.name();
        int k = cls.indexOf("$C$");
        if (k > 0) {
            Unit u = units.get(cls.substring(0, k));
            if (u != null) {
                String m = cls.substring(k + 3);
                for (Info.Method im : u.info().methods.values()) {
                    if (im.kind() == 'P' && im.method().replace('$', '_').equals(m)) {
                        return im.name();
                    }
                }
            }
            return null;
        }
        if (!cls.contains("$$Lambda")) {
            return null;
        }
        try {
            for (String[] r : Values.methodRefs(c)) {
                Unit u = units.get(r[0]);
                Info.Method im = u == null ? null : u.info().methods.get(r[1]);
                if (im != null && im.kind() == 'P') {
                    return im.name();
                }
            }
        } catch (RuntimeException e) {
            // no constant pool
        }
        return null;
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
