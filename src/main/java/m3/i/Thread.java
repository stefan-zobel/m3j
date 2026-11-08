// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import m3j.rt.M3Exception;
import m3j.rt.M3Object;
import m3j.rt.Mutex;
import m3j.rt.Rt;

/**
 * The interface Thread of m3core. A Thread.T is a platform thread with a
 * large stack; a Condition has its own monitor; alerts are a flag per
 * thread together with Thread.interrupt.
 *
 * <p>The flag is the alert: a procedure that can be alerted tests it before
 * it waits, and takes it when an interrupt ends the wait. Alert sets the
 * flag before it interrupts, so an interrupt that finds the flag clear
 * belongs to an alert that the thread has taken already (by TestAlert,
 * between the two steps of Alert): it is ignored. Procedures that cannot be
 * alerted leave the flag as it is.
 */
public final class Thread {
    private Thread() {}

    public static final boolean M3$PROVIDED = true;

    /** The stack size of forked threads, in bytes. */
    private static long stackSize = 64L << 20;

    /** Thread.T */
    public static final class T extends M3Object {
        java.lang.Thread thread;
        Object result;
        volatile boolean alerted;
        boolean joined;
    }

    /** Thread.Condition */
    public static final class Condition extends M3Object {
        public Condition() {}
    }

    /** EXCEPTION Alerted */
    public static final class Alerted extends M3Exception {
        private static final long serialVersionUID = 1L;

        @Override
        public String m3Name() {
            return "Thread.Alerted";
        }
    }

    private static final ThreadLocal<T> self = ThreadLocal.withInitial(() -> {
        T t = new T();
        t.thread = java.lang.Thread.currentThread();
        return t;
    });

    /** The method apply of a closure: the method whose name starts with apply_. */
    private static final ClassValue<MethodHandle> apply = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> c) {
            for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods()) {
                    if (m.getName().startsWith("apply_") && m.getParameterCount() == 0) {
                        try {
                            return MethodHandles.publicLookup().findVirtual(k, m.getName(),
                                MethodType.methodType(m.getReturnType()));
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
            }
            throw new IllegalStateException("no method apply in " + c);
        }
    };

    public static T Fork(Object cl) {
        T t = new T();
        MethodHandle h = apply.get(cl.getClass());
        java.lang.Thread th = new java.lang.Thread(null, () -> {
            self.set(t);
            try {
                t.result = h.invoke(cl);
            } catch (M3Exception e) {
                Rt.crashAndExit(e);
            } catch (RuntimeException | Error e) {
                Rt.crashAndExit(Rt.toM3(e));
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        }, "m3", stackSize);
        t.thread = th;
        Mutex.threadsStarted();
        th.start();
        return t;
    }

    public static Object Join(T t) {
        return join(t, false);
    }

    public static Object AlertJoin(T t) {
        return join(t, true);
    }

    private static Object join(T t, boolean alertable) {
        if (t.joined) {
            Rt.die("../src/thread/WIN32/ThreadWin32.m3", 666, "Thread client error: ",
                   "attempt to join with thread twice");
        }
        T me = self.get();
        // a thread that has ended is joined even if an alert is pending
        if (alertable && t.thread.isAlive() && takeAlert(me)) {
            throw new Alerted();
        }
        while (true) {
            try {
                t.thread.join();
                break;
            } catch (InterruptedException e) {
                if (alertable && takeAlert(me)) {
                    throw new Alerted();
                }
            }
        }
        t.joined = true;
        return t.result;
    }

    public static void Acquire(Mutex m) {
        m.acquire();
    }

    public static void Release(Mutex m) {
        m.release();
    }

    public static void Wait(Mutex m, Condition c) {
        synchronized (c) {
            m.release();
            try {
                c.wait();
            } catch (InterruptedException e) {
                // not alertable: an early return, which callers of Wait allow
                // for (waiting again could lose a notify that came with the
                // interrupt)
            }
        }
        m.acquire();
    }

    public static void AlertWait(Mutex m, Condition c) {
        T me = self.get();
        boolean alert = false;
        synchronized (c) {
            m.release();
            if (takeAlert(me)) {
                alert = true;
            } else {
                try {
                    c.wait();
                } catch (InterruptedException e) {
                    // without an alert: an early return, as in Wait
                    alert = takeAlert(me);
                }
            }
        }
        m.acquire();
        if (alert) {
            throw new Alerted();
        }
    }

    public static void Signal(Condition c) {
        synchronized (c) {
            c.notify();
        }
    }

    public static void Broadcast(Condition c) {
        synchronized (c) {
            c.notifyAll();
        }
    }

    public static void Pause(double n) {
        pause(n, false);
    }

    public static void AlertPause(double n) {
        pause(n, true);
    }

    private static void pause(double n, boolean alertable) {
        T me = alertable ? self.get() : null;
        if (alertable && takeAlert(me)) {
            throw new Alerted();
        }
        // at most about 31 years, so that the end does not overflow
        double secs = java.lang.Math.min(java.lang.Math.max(n, 0.0), 1.0e9);
        long end = System.nanoTime() + (long) (secs * 1e9);
        while (true) {
            long left = end - System.nanoTime();
            if (left <= 0) {
                return;
            }
            try {
                java.lang.Thread.sleep(left / 1_000_000, (int) (left % 1_000_000));
            } catch (InterruptedException e) {
                if (alertable && takeAlert(me)) {
                    throw new Alerted();
                }
            }
        }
    }

    public static T Self() {
        return self.get();
    }

    public static void Alert(T t) {
        t.alerted = true;
        t.thread.interrupt();
    }

    public static boolean TestAlert() {
        return takeAlert(self.get());
    }

    /**
     * Whether the calling thread is alerted, without taking the alert: for
     * the waits of the runtime that an interrupt ends (m3j.rt.Net), which
     * return to a caller that takes it with TestAlert.
     */
    public static boolean alertPending() {
        return self.get().alerted;
    }

    /**
     * If thread me (the calling thread) is marked alerted: unmarks it, clears
     * the interrupt of the alert if it has come, and returns true.
     */
    private static boolean takeAlert(T me) {
        if (!me.alerted) {
            return false;
        }
        me.alerted = false;
        java.lang.Thread.interrupted();
        return true;
    }

    public static long GetDefaultStackSize() {
        return stackSize / 8;
    }

    public static void MinDefaultStackSize(long min) {
        stackSize = java.lang.Math.max(stackSize, min * 8);
    }

    public static void IncDefaultStackSize(long inc) {
        stackSize += inc * 8;
    }
}
