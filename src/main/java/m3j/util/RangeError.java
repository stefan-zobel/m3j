// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.util;

/**
 * The checked runtime error of an enumeration or subrange value out of
 * range, where the original Modula-3 code reaches it.
 */
public final class RangeError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** {@code module} names the Modula-3 module in which the error occurs. */
    public RangeError(String module) {
        super("An enumeration or subrange value was out of range (" + module + ").");
    }
}
