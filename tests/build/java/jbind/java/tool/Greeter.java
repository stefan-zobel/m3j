// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package tool;

/** A class in the jar of a package that m3build builds. */
public final class Greeter {
    private Greeter() {}

    public static String greet(String who) {
        return "hello, " + who;
    }
}
