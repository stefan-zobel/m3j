// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

/** The interface M3jOS of m3j: the operating system whose conventions the program follows. */
public final class M3jOS {
    private M3jOS() {}

    public static final boolean M3$PROVIDED = true;

    public static boolean Posix() {
        return m3j.rt.OS.POSIX;
    }
}
