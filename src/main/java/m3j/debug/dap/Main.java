// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug.dap;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The debug adapter of m3j for the Debug Adapter Protocol, on standard
 * input and output:
 *
 * <pre>
 *   java -cp m3j.jar m3j.debug.dap.Main
 * </pre>
 *
 * It needs a JDK with the module jdk.jdi. The directory of m3j (with
 * m3j-runtime.jar and lib) is the one of the jar it runs from, or the
 * system property m3j.home.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (ModuleLayer.boot().findModule("jdk.jdi").isEmpty()) {
            System.err.println("m3j: the debugger needs a JDK with the module jdk.jdi");
            System.exit(1);
        }
        // the protocol owns standard output; what else is printed goes to standard error
        OutputStream out = new FileOutputStream(FileDescriptor.out);
        System.setOut(System.err);
        new Adapter(System.in, out, home()).run();
        System.exit(0);
    }

    private static Path home() {
        String h = System.getProperty("m3j.home");
        if (h != null) {
            return Path.of(h).toAbsolutePath();
        }
        try {
            Path p = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return (Files.isRegularFile(p) ? p.getParent() : p).toAbsolutePath();
        } catch (Exception e) {
            return Path.of("").toAbsolutePath();
        }
    }
}
