// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.util;

/**
 * The checked runtime error of a failed {@code <*ASSERT*>}, where the
 * original Modula-3 code reaches it.
 */
public final class AssertError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** {@code module} names the Modula-3 module in which the error occurs. */
    public AssertError(String module) {
        super("<*ASSERT*> failed (" + module + ").");
    }

    /** Throws an AssertError for {@code module} unless {@code cond} holds. */
    public static void check(boolean cond, String module) {
        if (!cond) {
            throw new AssertError(module);
        }
    }
}
