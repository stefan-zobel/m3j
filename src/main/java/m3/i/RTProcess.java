// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import m3j.rt.Rt;

/** The interface RTProcess of m3core. */
public final class RTProcess {
    private RTProcess() {}

    public static final boolean M3$PROVIDED = true;

    public static void RegisterExitor(Object p) {
        Rt.registerExitor(p);
    }

    public static void Exit(long n) {
        Rt.exit((int) n);
    }

    public static void Crash(String msg) {
        Rt.crashProcess(msg);
    }

    public static void InvokeExitors() {
        Rt.runExitors();
    }

    // Ctl-C interrupts, as in cm3: OnInterrupt calls the procedures of
    // RegisterInterruptSetup when the handler is set or removed; those of
    // m3j install and remove a handler of the signal INT of the Java
    // platform, which calls the handler of the program as cm3 on Windows
    // does (removed while it runs, and installed again). The classes
    // sun.misc.Signal and SignalHandler (module jdk.unsupported) are used
    // by reflection, since javac warns of every use of them.

    private static Object curHandler;
    private static Object curEnable;
    private static Object curDisable;
    private static boolean enabled;
    private static boolean setupRegistered;
    /** The handler of the signal INT before the first install. */
    private static Object saved;

    /** Signal.handle (new Signal ("INT"), h): the handler before; null if there is no such signal. */
    private static Object handle(Object h) {
        try {
            Class<?> s = Class.forName("sun.misc.Signal");
            Class<?> hc = Class.forName("sun.misc.SignalHandler");
            Object sig = s.getConstructor(String.class).newInstance("INT");
            return s.getMethod("handle", s, hc).invoke(null, sig, h);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // the system or the Java platform has no signal INT for the program
            return null;
        }
    }

    private static void interrupted() {
        Object p = OnInterrupt(null);
        if (p != null) {
            OnInterrupt(p);
            Rt.callProc0(p);
        } else {
            Rt.exit(130);
        }
    }

    private static synchronized void install() {
        try {
            Class<?> hc = Class.forName("sun.misc.SignalHandler");
            Object h = java.lang.reflect.Proxy.newProxyInstance(hc.getClassLoader(), new Class<?>[] {hc},
                (proxy, m, args) -> switch (m.getName()) {
                    case "handle" -> {
                        interrupted();
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "the interrupt handler of the program";
                });
            Object old = handle(h);
            if (saved == null) {
                saved = old;
            }
        } catch (ClassNotFoundException | RuntimeException e) {
            // the Java platform has no signal handlers for the program
        }
    }

    private static synchronized void restore() {
        if (saved != null) {
            handle(saved);
        }
    }

    /** OnInterrupt (p: InterruptHandler): InterruptHandler */
    public static synchronized Object OnInterrupt(Object p) {
        Object old = curHandler;
        if (enabled == (p == null)) {
            if (enabled) {
                if (setupRegistered) {
                    if (curDisable != null) {
                        Rt.callProc0(curDisable);
                    }
                } else {
                    restore();
                }
            } else {
                if (setupRegistered) {
                    if (curEnable != null) {
                        Rt.callProc0(curEnable);
                    }
                } else {
                    install();
                }
            }
            enabled = !enabled;
        }
        curHandler = p;
        return old;
    }

    /** RegisterInterruptSetup (enable, disable: PROCEDURE ()) */
    public static synchronized void RegisterInterruptSetup(Object enable, Object disable) {
        curEnable = enable;
        curDisable = disable;
        setupRegistered = true;
    }

    /**
     * RegisterForkHandlers (prep, parent, child: ForkHandler): INTEGER:
     * the Java platform cannot fork, so the handlers are never called; 0.
     */
    public static long RegisterForkHandlers(Object prep, Object parent, Object child) {
        return 0;
    }

    /** Fork (): INTEGER: the Java platform cannot fork the process; -1, as a failed fork. */
    public static long Fork() {
        return -1;
    }
}
