// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The externals M3jWeak of the module WeakRef of m3j (see Externals): weak
 * references of the Java platform.
 *
 * A weak reference is a number greater than 0, never used again, for a
 * java.lang.ref.WeakReference to the node. The Java platform clears it
 * when the node is no longer strongly reachable; from then on the weak
 * reference is dead.
 *
 * A weak reference with a cleanup procedure also has a keeper: an object
 * that refers to the node and to which only the node refers (the field
 * m3$keeper of ROOT), so that the keeper becomes unreachable when the
 * node does. The Java platform then calls the keeper's finalize method,
 * which can still reach the node: it puts the weak reference on the list
 * of the dead, from which the thread of the module WeakRef takes it and
 * calls the cleanup procedure with the node. Only objects have the field;
 * a node of another type (a TEXT or the referent of a REF type) with a
 * cleanup procedure is held for ever, so its cleanup procedure is never
 * called.
 *
 * The Java platform finalizes keepers in no particular order: the
 * cleanups of nodes that reach each other may run in any order, and a
 * weak reference to a node that only another node with a cleanup
 * procedure reaches dies with that node.
 */
final class Weak {
    private Weak() {}

    /** A weak reference: the Java weak reference to its node, and its number. */
    private static final class Entry extends WeakReference<Object> {
        final long id;

        Entry(Object node, long id) {
            super(node, cleared);
            this.id = id;
        }
    }

    /** The keeper of a node with a cleanup procedure. */
    private static final class Keeper {
        final long id;
        Object node;

        Keeper(long id, Object node) {
            this.id = id;
            this.node = node;
        }

        @SuppressWarnings("removal")
        @Override
        protected void finalize() {
            // the node may now get a cleanup procedure again
            KEEPER.compareAndSet((M3Object) node, (Object) this, (Object) null);
            dead.add(this);
        }
    }

    private static final VarHandle KEEPER;

    static {
        try {
            KEEPER = MethodHandles.lookup().findVarHandle(M3Object.class, "m3$keeper", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final ReferenceQueue<Object> cleared = new ReferenceQueue<>();
    private static final Map<Long, Entry> entries = new ConcurrentHashMap<>();
    private static final AtomicLong next = new AtomicLong(1);

    /** The weak references whose cleanup procedure is due, with their nodes. */
    private static final LinkedBlockingQueue<Keeper> dead = new LinkedBlockingQueue<>();

    /** The nodes taken from the list of the dead, until the cleanup takes them. */
    private static final Map<Long, Object> due = new ConcurrentHashMap<>();

    /** The nodes that are not objects and have a cleanup procedure: held for ever. */
    private static final Set<Object> held = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Forgets the weak references that the Java platform has cleared. */
    private static void expunge() {
        for (Object e; (e = cleared.poll()) != null; ) {
            entries.remove(((Entry) e).id);
        }
    }

    /**
     * A new weak reference to node; with a keeper if cleanup. 0 if the node
     * already has a cleanup procedure.
     */
    static long create(Object node, boolean cleanup) {
        expunge();
        long id = next.getAndIncrement();
        if (cleanup) {
            if (node instanceof M3Object o) {
                if (!KEEPER.compareAndSet(o, (Object) null, (Object) new Keeper(id, node))) {
                    return 0;
                }
            } else {
                synchronized (held) {
                    if (!held.add(node)) {
                        return 0;
                    }
                }
            }
        }
        entries.put(id, new Entry(node, id));
        return id;
    }

    /** The node of the weak reference id, or null if it is dead. */
    static Object get(long id) {
        Entry e = entries.get(id);
        return e == null ? null : e.get();
    }

    /**
     * Waits until a weak reference with a cleanup procedure has died; its
     * number. Its node is kept until take gives it.
     */
    static long next() {
        while (true) {
            try {
                Keeper k = dead.take();
                due.put(k.id, k.node);
                return k.id;
            } catch (InterruptedException e) {
                // the thread of WeakRef runs as long as the program does
            }
        }
    }

    /** The node of the dead weak reference id, which next gave. */
    static Object take(long id) {
        return due.remove(id);
    }

    /** Asks the Java platform for a garbage collection and finalization. */
    @SuppressWarnings("removal")
    static void collect() {
        System.gc();
        System.runFinalization();
    }
}
