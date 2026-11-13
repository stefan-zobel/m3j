// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.Field;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;

/**
 * A debugger for tools/lvt_test.py: runs the main class MAIN (with the
 * class path of the environment variable CLASSPATH), stops at the given
 * lines of the class CLASS and prints the variables that the debugger sees
 * there, sorted by name, with their values.
 *
 *   java Probe MAIN CLASS LINE...
 *
 * The values are printed so that they are the same in every run: no
 * object ids; an object as its class with its fields, two levels deep;
 * an array with its first elements.
 */
public final class Probe {
    private static final PrintStream OUT = new PrintStream(System.out, true, StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        String main = args[0], cls = args[1];
        List<Integer> lines = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            lines.add(Integer.parseInt(args[i]));
        }
        LaunchingConnector lc = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> a = lc.defaultArguments();
        a.get("main").setValue(main);
        VirtualMachine vm = lc.launch(a);
        drain(vm.process().getInputStream());
        drain(vm.process().getErrorStream());
        EventRequestManager erm = vm.eventRequestManager();
        ClassPrepareRequest cpr = erm.createClassPrepareRequest();
        cpr.addClassFilter(cls);
        cpr.enable();
        int hits = 0;
        try {
            loop:
            while (true) {
                EventSet es = vm.eventQueue().remove();
                for (Event e : es) {
                    if (e instanceof ClassPrepareEvent cpe) {
                        ReferenceType t = cpe.referenceType();
                        for (int l : lines) {
                            List<Location> locs;
                            try {
                                locs = t.locationsOfLine(l);
                            } catch (AbsentInformationException x) {
                                locs = List.of();
                            }
                            if (locs.isEmpty()) {
                                OUT.println("no code at line " + l);
                            }
                            for (Location loc : locs) {
                                erm.createBreakpointRequest(loc).enable();
                            }
                        }
                    } else if (e instanceof BreakpointEvent be) {
                        hits++;
                        show(be);
                    } else if (e instanceof VMDeathEvent || e instanceof VMDisconnectEvent) {
                        break loop;
                    }
                }
                es.resume();
            }
        } catch (VMDisconnectedException e) {
            // the program has ended
        }
        int status = vm.process().waitFor();
        OUT.println(hits + " stops, exit status " + status);
    }

    private static void show(BreakpointEvent be) throws Exception {
        Location loc = be.location();
        OUT.println("line " + loc.lineNumber() + " in " + loc.method().name());
        StackFrame f = be.thread().frame(0);
        List<LocalVariable> vs;
        try {
            vs = new ArrayList<>(f.visibleVariables());
        } catch (AbsentInformationException e) {
            OUT.println("  no names");
            return;
        }
        vs.sort(Comparator.comparing(LocalVariable::name));
        for (LocalVariable v : vs) {
            OUT.println("  " + v.name() + ": " + v.typeName() + " = " + value(f.getValue(v), 0));
        }
    }

    private static String value(Value v, int depth) {
        if (v == null) {
            return "null";
        }
        if (v instanceof StringReference s) {
            return "\"" + s.value() + "\"";
        }
        if (v instanceof PrimitiveValue p) {
            return p.toString();
        }
        if (v instanceof ArrayReference a) {
            StringBuilder b = new StringBuilder("[");
            int n = Math.min(a.length(), 8);
            for (int i = 0; i < n; i++) {
                b.append(i > 0 ? ", " : "").append(value(a.getValue(i), depth + 1));
            }
            return b.append(a.length() > n ? ", ...]" : "]").toString();
        }
        ObjectReference o = (ObjectReference) v;
        String name = o.referenceType().name();
        if (name.startsWith("java.") || depth >= 2) {
            return name;
        }
        StringBuilder b = new StringBuilder(name).append(" {");
        boolean first = true;
        for (Field fd : o.referenceType().allFields()) {
            if (!fd.isStatic()) {
                b.append(first ? "" : ", ").append(fd.name()).append(" = ").append(value(o.getValue(fd), depth + 1));
                first = false;
            }
        }
        return b.append("}").toString();
    }

    private static void drain(InputStream in) {
        Thread t = new Thread(() -> {
            try {
                in.transferTo(java.io.OutputStream.nullOutputStream());
            } catch (Exception e) {
                // the program has ended
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
