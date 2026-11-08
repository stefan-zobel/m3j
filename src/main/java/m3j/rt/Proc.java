// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The externals M3jProc of the module Process of m3j (see Externals):
 * processes of the Java platform.
 *
 * A file handle given to a new process for its standard input, output or
 * error output reaches it in one of these ways: a standard handle of the
 * program in its own place is inherited; the file of a regular file
 * handle is opened by name (the process reads it from the position of
 * the handle; it writes an empty file, and when it has ended the handle
 * moves to the end of the file); the output to a file that is not empty
 * goes through a thread that writes it at the position of the handle (a
 * file opened only to append, as the Java platform opens it, cannot be
 * written by programs of MSYS and Cygwin on Windows); any other handle (the end of a pipe, a standard
 * handle in another place) is connected by a thread that copies the bytes
 * between the handle and the process, and that holds a reference to the
 * end of a pipe until it is done, as the operating system would hold the
 * handle that the process inherits. Without a handle (NIL), the process
 * reads an empty input, and writing its output fails (it is a pipe that
 * nobody reads).
 */
final class Proc {
    private Proc() {}

    /** A process that the program created, and the copying threads that Wait waits for. */
    private record Child(Process process, List<Thread> joins, List<Long> appends) {}

    private static final Map<Long, Child> children = new ConcurrentHashMap<>();
    private static final AtomicLong next = new AtomicLong(1);

    /** The n texts separated by NUL in s. */
    private static List<String> split(String s, long n) {
        if (n == 0) {
            return new ArrayList<>();
        }
        List<String> l = new ArrayList<>(Arrays.asList(s.split("\0", -1)));
        if (l.size() != n) {
            throw new IllegalArgumentException("a parameter or variable has the character NUL");
        }
        return l;
    }

    private static boolean windows() {
        return File.separatorChar == '\\';
    }

    /**
     * The command: a pathname of more than one arc is resolved against the
     * working directory of the program; a single arc is looked up by the
     * system (the directories of PATH). cm3's leading "`" (on Windows, quote
     * the parameters) is dropped: the Java platform passes each parameter
     * as one.
     */
    private static String command(String cmd) {
        if (cmd.startsWith("`")) {
            cmd = cmd.substring(1);
        }
        boolean arc = cmd.indexOf('/') < 0
            && !(windows() && (cmd.indexOf('\\') >= 0 || cmd.indexOf(':') >= 0));
        return arc ? cmd : FileSystem.path(cmd).toString();
    }

    /** The file that the system would execute for cmd (POSIX: the directories of PATH). */
    private static String lookUp(String cmd) {
        if (cmd.indexOf('/') >= 0) {
            return cmd;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String d : path.split(File.pathSeparator)) {
                Path f = Path.of(d.isEmpty() ? "." : d, cmd);
                if (Files.isRegularFile(f) && Files.isExecutable(f)) {
                    return f.toString();
                }
            }
        }
        return cmd;
    }

    private interface Source {
        int read(byte[] b) throws IOException;
    }

    private interface Sink {
        void write(byte[] b, int n) throws IOException;
    }

    /**
     * A thread that copies from src to dst until the end of src, then runs
     * done. When dst fails, it goes on reading src (and drops the bytes), so
     * that a process that writes is not blocked.
     */
    private static Thread copy(String name, Source src, Sink dst, Runnable done) {
        Thread t = new Thread(() -> {
            byte[] b = new byte[8192];
            boolean ok = true;
            try {
                int n;
                while ((n = src.read(b)) > 0) {
                    if (ok) {
                        try {
                            dst.write(b, n);
                        } catch (IOException e) {
                            ok = false;
                        }
                    }
                }
            } catch (IOException e) {
                // the end of the source
            } finally {
                done.run();
            }
        }, name);
        t.setDaemon(true);
        return t;
    }

    private static void close(java.io.Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            // nothing to do
        }
    }

    /**
     * A pipe that is closed at once: for no handle or one that cannot be
     * used in place k, the process reads an empty input or cannot write.
     */
    private static ProcessBuilder.Redirect unusable(int k, List<Runnable> starts, Process[] proc) {
        if (k != 0) {
            starts.add(() -> close(k == 1 ? proc[0].getInputStream() : proc[0].getErrorStream()));
        }
        return ProcessBuilder.Redirect.PIPE;
    }

    /**
     * What happens with the handle h in place k (0 input, 1 output, 2 error
     * output): the redirection, and in starts what runs once the process
     * proc[0] has started; copied[0] is set when a thread copies to the
     * input of the process (else an input of PIPE is closed at once).
     */
    private static ProcessBuilder.Redirect redirect(long h, int k, List<Runnable> starts, Process[] proc,
                                                    List<Thread> joins, List<Long> appends,
                                                    List<FileSystem.End> held, boolean[] copied)
            throws IOException {
        if (h < 0) {
            return unusable(k, starts, proc);
        }
        if (h == k && FileSystem.standardOpen(h)) {
            return ProcessBuilder.Redirect.INHERIT;
        }
        Path file = FileSystem.filePath(h);
        if (file != null) {
            if (k != 0) {
                if (file.toFile().length() == 0) {
                    appends.add(h);
                    return ProcessBuilder.Redirect.to(file.toFile());
                }
                return written(h, k, starts, proc, joins);
            }
            long pos = FileSystem.position(h);
            if (pos == 0) {
                return ProcessBuilder.Redirect.from(file.toFile());
            }
            RandomAccessFile r = new RandomAccessFile(file.toFile(), "r");
            r.seek(pos);
            copied[0] = true;
            starts.add(() -> {
                OutputStream o = proc[0].getOutputStream();
                copy("m3j process input", r::read, (b, n) -> {
                    o.write(b, 0, n);
                    o.flush();
                }, () -> {
                    close(r);
                    close(o);
                }).start();
            });
            return ProcessBuilder.Redirect.PIPE;
        }
        FileSystem.End e = FileSystem.pipeEnd(h);
        if (e != null) {
            FileSystem.Channel c = e.channel();
            if (k == 0 ? e.write() : !e.write()) {
                return unusable(k, starts, proc);
            }
            c.acquire(e.write());
            held.add(e);
            if (k == 0) {
                copied[0] = true;
                starts.add(() -> {
                    OutputStream o = proc[0].getOutputStream();
                    copy("m3j process input", b -> c.read(b, 0, b.length, true), (b, n) -> {
                        o.write(b, 0, n);
                        o.flush();
                    }, () -> {
                        c.release(false);
                        close(o);
                    }).start();
                });
            } else {
                starts.add(() -> {
                    InputStream i = k == 1 ? proc[0].getInputStream() : proc[0].getErrorStream();
                    copy("m3j process output", i::read, (b, n) -> c.write(b, 0, n),
                         () -> c.release(true)).start();
                });
            }
            return ProcessBuilder.Redirect.PIPE;
        }
        if (FileSystem.standardOpen(h)) {
            if (k == 0 || h == 0) {
                return unusable(k, starts, proc);
            }
            return written(h, k, starts, proc, joins);
        }
        throw new IOException("file closed");
    }

    /**
     * The output in place k (1 or 2) of the process proc[0] goes through a
     * thread that writes it to the handle h, which Wait joins.
     */
    private static ProcessBuilder.Redirect written(long h, int k, List<Runnable> starts, Process[] proc,
                                                   List<Thread> joins) {
        starts.add(() -> {
            InputStream i = k == 1 ? proc[0].getInputStream() : proc[0].getErrorStream();
            Thread t = copy("m3j process output", i::read, (b, n) -> {
                String m = FileSystem.write(h, b, 0, n);
                if (m != null) {
                    throw new IOException(m);
                }
            }, () -> {});
            joins.add(t);
            t.start();
        });
        return ProcessBuilder.Redirect.PIPE;
    }

    /**
     * Process.Create: the handle of a new process, its identifier in id;
     * params and env are nparams and nenv texts separated by NUL (env null:
     * the environment of the program); wd null: the working directory of
     * the program; a file handle -1: none.
     */
    static long create(String cmd, String params, long nparams, String env, long nenv, String wd,
                       long in, long out, long err, LongRef id, ObjRef error) {
        List<FileSystem.End> held = new ArrayList<>();
        try {
            List<String> command = new ArrayList<>();
            command.add(command(cmd));
            command.addAll(split(params, nparams));
            ProcessBuilder pb = new ProcessBuilder(command);
            if (env != null) {
                Map<String, String> m = pb.environment();
                m.clear();
                for (String v : split(env, nenv)) {
                    int i = v.indexOf('=', 1);
                    if (i > 0) {
                        m.put(v.substring(0, i), v.substring(i + 1));
                    } else if (!v.isEmpty()) {
                        m.put(v, "");
                    }
                }
            }
            pb.directory(FileSystem.path(wd == null ? "" : wd).toFile());
            List<Runnable> starts = new ArrayList<>();
            List<Thread> joins = new ArrayList<>();
            List<Long> appends = new ArrayList<>();
            Process[] proc = new Process[1];
            long[] hs = {in, out, err};
            ProcessBuilder.Redirect[] r = new ProcessBuilder.Redirect[3];
            boolean[] copied = new boolean[1];
            // the output and the error output to the same file: one stream
            boolean merged = err == out && FileSystem.filePath(out) != null;
            for (int k = 0; k < (merged ? 2 : 3); k++) {
                r[k] = redirect(hs[k], k, starts, proc, joins, appends, held, copied);
            }
            pb.redirectInput(r[0]).redirectOutput(r[1]);
            if (merged) {
                pb.redirectErrorStream(true);
            } else {
                pb.redirectError(r[2]);
            }
            try {
                proc[0] = pb.start();
            } catch (IOException e) {
                String m = e.getMessage();
                if (windows() || m == null || !m.contains("error=8,")) {
                    throw e;
                }
                // ENOEXEC: as cm3 on POSIX, the shell runs the file
                command.set(0, lookUp(command.get(0)));
                command.add(0, "/bin/sh");
                proc[0] = pb.command(command).start();
            }
            if (r[0] == ProcessBuilder.Redirect.PIPE && !copied[0]) {
                close(proc[0].getOutputStream());
            }
            for (Runnable s : starts) {
                s.run();
            }
            long p = next.getAndIncrement();
            children.put(p, new Child(proc[0], joins, appends));
            id.set(proc[0].pid());
            return p;
        } catch (IOException | RuntimeException e) {
            for (FileSystem.End e1 : held) {
                e1.channel().release(e1.write());
            }
            error.set(FileSystem.message(e));
            return 0;
        }
    }

    /**
     * Process.Wait: waits for the process p and the copying of its output
     * to standard handles; returns its exit code (that of Windows MOD 2^31;
     * on POSIX, for a process ended by a signal, the code of the Java
     * platform, 128 + the signal). Thread.Alert does not end the wait.
     */
    static long await(long p) {
        Child c = children.remove(p);
        if (c == null) {
            return 0;
        }
        boolean interrupted = false;
        int code;
        while (true) {
            try {
                code = c.process().waitFor();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        for (Thread t : c.joins()) {
            while (true) {
                try {
                    t.join();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        for (long h : c.appends()) {
            FileSystem.appended(h);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return code & 0x7FFFFFFFL;
    }
}
