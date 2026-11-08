// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import m3j.middle.M3CG_BinRd;
import m3j.middle.M3CG_BinWr;
import m3j.middle.M3CG_Wr;
import m3j.middle.M3ID;
import m3j.middle.M3RT;
import m3j.middle.Target;
import m3j.middle.TargetMap;

/**
 * The counterpart of CM3's m3cgcat for binary input: reads the binary
 * intermediate code (a .mc or .ic file written by cm3 -keep) with
 * M3CG_BinRd and writes it as text with M3CG_Wr, or, with
 * {@code -out-binary}, as binary intermediate code with M3CG_BinWr (as
 * {@code m3cgcat -in-binary -out-binary} does).
 *
 * <p>Usage: {@code java -cp m3j.jar m3j.tools.CgCat [-target=T] [-out-binary]
 * [FILE]}. Without FILE the code is read from stdin; the output goes to
 * stdout. The target defaults to AMD64_NT; like m3cgcat, Target.Init gets
 * the default OS name and back-end mode. With {@code -batch=LIST} each line
 * of the file LIST names an input and an output file, separated by a tab;
 * each pair is converted with fresh state, as by a separate run of m3cgcat.
 * The error messages of M3CG_BinRd go to the output for text output (as
 * m3cgcat writes them to stdout) and to stdout for binary output.
 */
public final class CgCat {
    private CgCat() {}

    public static void main(String[] args) throws IOException {
        String target = "AMD64_NT";
        String file = null;
        String batch = null;
        boolean binary = false;
        for (String a : args) {
            if (a.startsWith("-target=")) {
                target = a.substring("-target=".length());
            } else if (a.startsWith("-batch=")) {
                batch = a.substring("-batch=".length());
            } else if (a.equals("-out-binary")) {
                binary = true;
            } else if (file == null) {
                file = a;
            } else {
                System.err.println("usage: CgCat [-target=T] [-out-binary] [-batch=LIST | FILE]");
                System.exit(2);
            }
        }
        PrintStream stdout = new PrintStream(System.out, false, StandardCharsets.ISO_8859_1);
        if (batch != null) {
            int bad = 0;
            for (String line : Files.readAllLines(Path.of(batch), StandardCharsets.UTF_8)) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] io = line.split("\t", -1);
                try (InputStream in = new BufferedInputStream(new FileInputStream(io[0]));
                     PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(io[1])),
                                                       false, StandardCharsets.ISO_8859_1)) {
                    if (!convert(target, binary, in, out, binary ? stdout : out)) {
                        bad++;
                    }
                }
            }
            stdout.flush();
            System.exit(bad == 0 ? 0 : 1);
        }
        try (InputStream in = file == null ? new BufferedInputStream(System.in)
                                           : new BufferedInputStream(new FileInputStream(file))) {
            System.exit(convert(target, binary, in, stdout, stdout) ? 0 : 1);
        }
    }

    /**
     * Converts one unit from 'in' to 'out', as binary code if 'binary', else
     * as text; the reader reports errors on 'msgs'. Returns false after a
     * checked runtime error.
     */
    static boolean convert(String target, boolean binary, InputStream in, OutputStream out,
                                   PrintStream msgs) throws IOException {
        Target.State t = new Target.State();
        TargetMap.State map = new TargetMap.State();
        M3RT.State rt = new M3RT.State();
        if (!Target.init(t, map, rt, target, "POSIX", Target.M3BackendMode_t.ExternalAssembly)) {
            System.err.println("unable to initialize Target: " + target);
            System.exit(1);
        }
        M3ID.State ids = new M3ID.State();
        Writer wr = binary ? null : new OutputStreamWriter(out, StandardCharsets.ISO_8859_1);
        try {
            M3CG_BinRd.inhale(in, binary ? M3CG_BinWr.New(out, ids, map) : M3CG_Wr.New(wr, ids),
                              ids, t, map, msgs);
        } catch (RuntimeException e) {
            if (wr != null) {
                wr.flush();
            }
            out.flush();
            msgs.flush();
            System.err.println("*** runtime error: " + e);
            return false;
        }
        if (wr != null) {
            wr.flush();
        }
        out.flush();
        msgs.flush();
        return true;
    }
}
