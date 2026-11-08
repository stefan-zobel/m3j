// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/**
 * The operating system whose conventions m3j follows: POSIX (Linux, macOS
 * and the like) or WIN32. It is the system that the JVM runs on, unless
 * the system property m3j.os names one ("POSIX" or "WIN32"). A program
 * decides with it at run time how pathnames are formed (Pathname) and what
 * OSConfig reports; the compiler decides with it which of the units that
 * cm3 has for both systems it uses (OSConfig.i3, with the constants that
 * Wr.EOL is made of).
 */
public final class OS {
    private OS() {}

    /** true for POSIX, false for WIN32 */
    public static final boolean POSIX = posix();

    /** The name of the directories of cm3's sources for the other system. */
    public static String otherDir() {
        return POSIX ? "WIN32" : "POSIX";
    }

    private static boolean posix() {
        String os = System.getProperty("m3j.os");
        if (os == null) {
            return !System.getProperty("os.name", "").startsWith("Windows");
        }
        return switch (os) {
            case "POSIX" -> true;
            case "WIN32" -> false;
            default -> throw new IllegalArgumentException("m3j.os must be POSIX or WIN32, not " + os);
        };
    }
}
