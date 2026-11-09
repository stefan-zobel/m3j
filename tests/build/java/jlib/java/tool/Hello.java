// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package tool;

/** The class that a library binds. */
public final class Hello {
    private Hello() {}

    public static String hi(String who) {
        return "hi, " + who;
    }
}
