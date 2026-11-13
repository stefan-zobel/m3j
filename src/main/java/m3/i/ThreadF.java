// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/**
 * The interface ThreadF of m3core, as far as it is not tied to cm3's
 * implementation of threads: the identity of a thread. The id is that of
 * the Java thread, as cm3 gives that of the thread of the system.
 */
public final class ThreadF {
    private ThreadF() {}

    public static final boolean M3$PROVIDED = true;

    public static long MyId() {
        return java.lang.Thread.currentThread().threadId();
    }
}
