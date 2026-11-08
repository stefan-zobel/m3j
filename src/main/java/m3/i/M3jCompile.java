// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import m3j.jvm.Compile;
import m3j.rt.Rt;

/**
 * The interface M3jCompile of m3build: the compiler of m3j, run in the
 * program. Only the jar m3j.jar has the compiler.
 */
public final class M3jCompile {
    private M3jCompile() {}

    public static final boolean M3$PROVIDED = true;

    /**
     * Runs the compiler with the nargs arguments separated by NUL in args;
     * relative pathnames are relative to the working directory of the
     * program (the option -wd of the compiler). Its exit code.
     */
    public static long Run(String args, long nargs) {
        String[] given = nargs == 0 ? new String[0] : args.split("\0", -1);
        String[] a = new String[given.length + 2];
        a[0] = "-wd";
        a[1] = Rt.resolve(".");
        System.arraycopy(given, 0, a, 2, given.length);
        try {
            return Compile.run(a);
        } catch (IOException e) {
            System.err.println("m3j: " + e.getMessage());
            return 1;
        }
    }

    /**
     * The directory of m3j: that of the jar of the compiler (or, for the
     * classes of a build, the directory that holds their directory).
     */
    public static String Home() {
        try {
            Path p = Path.of(Compile.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return p.toAbsolutePath().getParent().toString();
        } catch (URISyntaxException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    /** The pathname of the java program that runs this program. */
    public static String Java() {
        Path j = Path.of(System.getProperty("java.home"), "bin", "java");
        return j.toString();
    }

    /** The version of m3j, from the manifest of its jar. */
    public static String Version() {
        return Compile.version();
    }
}
