// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * MUTEX: a lock that knows its holder. Locking a mutex the thread already
 * holds, or releasing one it does not hold, is the client error that cm3
 * reports.
 *
 * <p>Readers and writers lock their mutex for every character, so locking
 * must be cheap. As long as the program runs in one thread (no Thread.Fork
 * yet, and no procedure given to Java, which may call it from any thread),
 * the holder is set and cleared with plain writes. After that, a mutex is
 * taken with a compare-and-set of the holder, and a thread that finds it
 * held waits on its monitor until a release notifies it. The switch is
 * safe: the thread sets the flag before it starts the new thread or hands
 * over the procedure, so the other thread sees the flag and every holder
 * written before.
 */
public class Mutex extends M3Object {
    private static final VarHandle OWNER;

    static {
        try {
            OWNER = MethodHandles.lookup().findVarHandle(Mutex.class, "owner", Thread.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** whether code of the program may run in more than one thread */
    private static boolean threads;

    /** the thread that holds the mutex, or null */
    @SuppressWarnings("unused") // accessed through OWNER
    private Thread owner;
    /** the number of threads waiting in slowAcquire */
    private volatile int waiters;

    public Mutex() {}

    /** Called by Thread.Fork before it starts a thread, and before a procedure is given to Java. */
    public static void threadsStarted() {
        threads = true;
    }

    public final void acquire() {
        Thread t = Thread.currentThread();
        if (!threads) {
            if (OWNER.get(this) == t) {
                selfLocked();
            }
            // the only thread; a mutex held here is held by itself
            OWNER.set(this, t);
        } else if (!OWNER.compareAndSet(this, null, t)) {
            if (OWNER.get(this) == t) {
                selfLocked();
            }
            slowAcquire(t);
        }
    }

    private static void selfLocked() {
        Rt.die("../src/thread/WIN32/ThreadWin32.m3", 192, "Thread client error: ",
               "attempt to lock mutex already locked by self");
    }

    private void slowAcquire(Thread t) {
        boolean interrupted = false;
        synchronized (this) {
            waiters++;
            try {
                while (!OWNER.compareAndSet(this, null, t)) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // an alert does not end the wait for a mutex
                        interrupted = true;
                    }
                }
            } finally {
                waiters--;
            }
        }
        if (interrupted) {
            t.interrupt();
        }
    }

    public final void release() {
        if (OWNER.get(this) != Thread.currentThread()) {
            Rt.die("../src/thread/WIN32/ThreadWin32.m3", 199, "Thread client error: ",
                   "attempt to release an unlocked mutex");
        }
        if (!threads) {
            OWNER.set(this, null);
        } else {
            OWNER.setVolatile(this, null);
            if (waiters > 0) {
                synchronized (this) {
                    notify();
                }
            }
        }
    }
}
