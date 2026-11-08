// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * The externals M3jFS of the module FS of m3j (see Externals): the file
 * system of the Java platform. A String result, or a value set into an
 * ObjRef "err", is null on success and else the message of the error.
 * The handles 0, 1 and 2 are the standard input, output and error output;
 * the others are files opened with open and the ends of pipes.
 *
 * The Java platform cannot change the working directory of the process, so
 * the working directory of the program is kept here (cwd): relative path
 * names are resolved against it, and processes that the program creates
 * start in it.
 */
final class FileSystem {
    private FileSystem() {}

    /** The working directory of the program, an absolute path. */
    private static volatile Path cwd = Path.of("").toAbsolutePath();

    /** The path p, resolved against the working directory of the program. */
    static Path path(String p) {
        return cwd.resolve(p);
    }

    static String getWorkingDirectory() {
        return cwd.toString();
    }

    static String setWorkingDirectory(String p) {
        try {
            Path d = path(p).normalize();
            if (!Files.isDirectory(d)) {
                return "not a directory: " + p;
            }
            cwd = d;
            return null;
        } catch (RuntimeException e) {
            return message(e);
        }
    }

    /**
     * A channel of pipes (Pipe.Open): the bytes written to its write ends
     * are read from its read ends. It counts the references to its ends
     * (the handles of the program and the transfers to and from processes
     * that it creates): the readers see the end when no writer is left, and
     * writing fails when no reader is left. Waiting for the channel is not
     * ended by Thread.Alert (the interrupt is kept for later).
     */
    static final class Channel {
        private final byte[] buf = new byte[65536];
        private int head;
        private int count;
        private int readers;
        private int writers;

        synchronized void acquire(boolean write) {
            if (write) {
                writers++;
            } else {
                readers++;
            }
        }

        synchronized void release(boolean write) {
            if (write) {
                writers--;
            } else {
                readers--;
            }
            notifyAll();
        }

        /** The number of bytes read, 0 at the end, -1 if none is ready and not mayBlock. */
        int read(byte[] b, int off, int n, boolean mayBlock) {
            boolean interrupted = false;
            try {
                synchronized (this) {
                    while (count == 0 && writers > 0) {
                        if (!mayBlock) {
                            return -1;
                        }
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                    int k = Math.min(n, count);
                    for (int i = 0; i < k; i++) {
                        b[off + i] = buf[(head + i) % buf.length];
                    }
                    head = (head + k) % buf.length;
                    count -= k;
                    notifyAll();
                    return k;
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        void write(byte[] b, int off, int n) throws IOException {
            boolean interrupted = false;
            try {
                synchronized (this) {
                    while (n > 0) {
                        while (count == buf.length && readers > 0) {
                            try {
                                wait();
                            } catch (InterruptedException e) {
                                interrupted = true;
                            }
                        }
                        if (readers == 0) {
                            throw new IOException("broken pipe");
                        }
                        int k = Math.min(n, buf.length - count);
                        for (int i = 0; i < k; i++) {
                            buf[(head + count + i) % buf.length] = b[off + i];
                        }
                        count += k;
                        off += k;
                        n -= k;
                        notifyAll();
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** An end of a pipe: its channel and whether it is the write end. */
    record End(Channel channel, boolean write) {}

    private static final Map<Long, End> pipes = new ConcurrentHashMap<>();

    /**
     * Whether the file p exists (mode 0) and the program may read it
     * (mode 1), write it (mode 2) or execute it (mode 3), as access of
     * POSIX tells.
     */
    static boolean access(String p, long mode) {
        try {
            Path f = path(p);
            return switch ((int) mode) {
                case 1 -> Files.isReadable(f);
                case 2 -> Files.isWritable(f);
                case 3 -> Files.isExecutable(f);
                default -> Files.exists(f);
            };
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Pipe.Open: a new channel, its read end hr and write end hw. */
    static String pipe(LongRef hr, LongRef hw) {
        Channel c = new Channel();
        c.acquire(false);
        c.acquire(true);
        long r = next.getAndIncrement();
        long w = next.getAndIncrement();
        pipes.put(r, new End(c, false));
        pipes.put(w, new End(c, true));
        hr.set(r);
        hw.set(w);
        return null;
    }

    /** The end of a pipe of handle h, or null. */
    static End pipeEnd(long h) {
        return pipes.get(h);
    }

    /** The path of the file of handle h, or null. */
    static Path filePath(long h) {
        return files.containsKey(h) ? paths.get(h) : null;
    }

    /** Whether h is a standard handle that the program has not closed. */
    static boolean standardOpen(long h) {
        return isStandard(h) && !stdClosed[(int) h];
    }

    /**
     * After a process that appended to the file of handle h has ended:
     * the position of the handle moves to the end of the file, as if the
     * process had shared it.
     */
    static void appended(long h) {
        RandomAccessFile r = files.get(h);
        if (r != null) {
            try {
                if (r.getFilePointer() < r.length()) {
                    r.seek(r.length());
                }
            } catch (IOException e) {
                // the handle keeps its position
            }
        }
    }

    /** The position of the file of handle h, 0 if it has none. */
    static long position(long h) {
        RandomAccessFile r = files.get(h);
        try {
            return r == null ? 0 : r.getFilePointer();
        } catch (IOException e) {
            return 0;
        }
    }

    private static final Map<Long, RandomAccessFile> files = new ConcurrentHashMap<>();
    private static final Map<Long, Path> paths = new ConcurrentHashMap<>();
    /** the handles of files opened read-only */
    private static final Set<Long> readonlyFiles = ConcurrentHashMap.newKeySet();
    private static final AtomicLong next = new AtomicLong(3);

    private static final InputStream STDIN = new FileInputStream(FileDescriptor.in);
    private static final OutputStream[] STDOUT = {
        null, new FileOutputStream(FileDescriptor.out), new FileOutputStream(FileDescriptor.err)
    };
    private static final boolean[] stdClosed = new boolean[3];

    private static boolean isStandard(long h) {
        return h >= 0 && h <= 2;
    }

    private static void checkOpen(long h) throws IOException {
        if (stdClosed[(int) h]) {
            throw new IOException("file closed");
        }
    }

    /** Whether the program runs in a terminal (its standard streams are a console). */
    static boolean isTerminal() {
        java.io.Console c = System.console();
        return c != null && c.isTerminal();
    }

    static String message(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    static String status(String p, LongRef type, DoubleRef time, LongRef size) {
        try {
            BasicFileAttributes a = Files.readAttributes(path(p), BasicFileAttributes.class);
            type.set(a.isRegularFile() ? 0 : a.isDirectory() ? 1 : 2);
            time.set(a.lastModifiedTime().toMillis() / 1000.0);
            size.set(a.size());
            return null;
        } catch (IOException | RuntimeException e) {
            return message(e);
        }
    }

    static String setModificationTime(String p, double t) {
        try {
            Files.setLastModifiedTime(path(p), FileTime.fromMillis(Math.round(t * 1000.0)));
            return null;
        } catch (IOException | RuntimeException e) {
            return message(e);
        }
    }

    static String absolute(String p, ObjRef err) {
        try {
            return path(p).normalize().toString();
        } catch (RuntimeException e) {
            err.set(message(e));
            return null;
        }
    }

    static String createDirectory(String p) {
        try {
            Files.createDirectory(path(p));
            return null;
        } catch (IOException | RuntimeException e) {
            return message(e);
        }
    }

    static String deleteDirectory(String p) {
        Path d = path(p);
        if (!Files.isDirectory(d, LinkOption.NOFOLLOW_LINKS)) {
            return "not a directory: " + p;
        }
        return delete(d);
    }

    static String deleteFile(String p) {
        Path f = path(p);
        if (Files.isDirectory(f, LinkOption.NOFOLLOW_LINKS)) {
            return "a directory: " + p;
        }
        return delete(f);
    }

    private static String delete(Path f) {
        try {
            Files.delete(f);
            return null;
        } catch (IOException | RuntimeException e) {
            return message(e);
        }
    }

    /** Renames p0 to p1; as in cm3 (MoveFileEx, rename), a file p1 is replaced. */
    static String rename(String p0, String p1) {
        try {
            Files.move(path(p0), path(p1), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return null;
        } catch (IOException | RuntimeException e) {
            return message(e);
        }
    }

    static String entries(String p, ObjRef err) {
        try (Stream<Path> s = Files.list(path(p))) {
            StringBuilder sb = new StringBuilder();
            s.map(x -> x.getFileName().toString()).sorted().forEach(n -> sb.append(n).append('\0'));
            return sb.toString();
        } catch (IOException | RuntimeException e) {
            err.set(message(e));
            return null;
        }
    }

    static long open(String p, boolean readonly, boolean truncate, long create, ObjRef err) {
        try {
            File f = path(p).toFile();
            boolean exists = f.exists();
            if (create == 0 && !exists) {
                err.set("file not found: " + p);
                return -1;
            }
            if (create == 2 && exists) {
                err.set("file exists: " + p);
                return -1;
            }
            RandomAccessFile r = new RandomAccessFile(f, readonly ? "r" : "rw");
            if (truncate && !readonly) {
                r.setLength(0);
            }
            long h = next.getAndIncrement();
            files.put(h, r);
            paths.put(h, f.toPath());
            if (readonly) {
                readonlyFiles.add(h);
            }
            return h;
        } catch (IOException | RuntimeException e) {
            err.set(message(e));
            return -1;
        }
    }

    private static RandomAccessFile file(long h) throws IOException {
        RandomAccessFile r = files.get(h);
        if (r == null) {
            throw new IOException("file closed");
        }
        return r;
    }

    /** The number of bytes read, 0 at the end, -1 if none is ready and not mayBlock. */
    static long read(long h, byte[] b, int off, int n, boolean mayBlock, ObjRef err) {
        try {
            if (n == 0) {
                return 0;
            }
            int k;
            if (isStandard(h)) {
                checkOpen(h);
                if (h != 0) {
                    throw new IOException("not readable");
                }
                if (!mayBlock && STDIN.available() == 0) {
                    return -1;
                }
                k = STDIN.read(b, off, n);
            } else if (pipes.containsKey(h)) {
                End e = pipes.get(h);
                if (e.write()) {
                    throw new IOException("not readable");
                }
                return e.channel().read(b, off, n, mayBlock);
            } else {
                k = file(h).read(b, off, n);
            }
            return k < 0 ? 0 : k;
        } catch (IOException | RuntimeException e) {
            err.set(message(e));
            return 0;
        }
    }

    static String write(long h, byte[] b, int off, int n) {
        try {
            if (isStandard(h)) {
                checkOpen(h);
                if (h == 0) {
                    throw new IOException("not writable");
                }
                STDOUT[(int) h].write(b, off, n);
                return null;
            }
            End e = pipes.get(h);
            if (e != null) {
                if (!e.write()) {
                    throw new IOException("not writable");
                }
                e.channel().write(b, off, n);
                return null;
            }
            file(h).write(b, off, n);
            return null;
        } catch (IOException | RuntimeException e) {
            return message(e);
        }
    }

    static long seek(long h, long origin, long offset, ObjRef err) {
        try {
            if (isStandard(h) || pipes.containsKey(h)) {
                throw new IOException("not seekable");
            }
            RandomAccessFile r = file(h);
            long base = origin == 0 ? 0 : origin == 1 ? r.getFilePointer() : r.length();
            r.seek(base + offset);
            return r.getFilePointer();
        } catch (IOException | RuntimeException e) {
            err.set(message(e));
            return 0;
        }
    }

    static String handleStatus(long h, DoubleRef time, LongRef size) {
        try {
            if (isStandard(h) || pipes.containsKey(h)) {
                if (isStandard(h)) {
                    checkOpen(h);
                }
                size.set(0);
                time.set(0);
                return null;
            }
            RandomAccessFile r = file(h);
            size.set(r.length());
            time.set(Files.getLastModifiedTime(paths.get(h)).toMillis() / 1000.0);
            return null;
        } catch (IOException | RuntimeException e) {
            return message(e);
        }
    }

    /**
     * Whether handle h may be read: not the standard output and error
     * output. A closed handle may (reading it reports the error).
     */
    static boolean readable(long h) {
        End e = pipes.get(h);
        return e != null ? !e.write() : h != 1 && h != 2;
    }

    /**
     * Whether handle h may be written: not the standard input and not a
     * file opened read-only. A closed handle may (writing it reports the error).
     */
    static boolean writable(long h) {
        End e = pipes.get(h);
        return e != null ? e.write() : h != 0 && !readonlyFiles.contains(h);
    }

    static String close(long h) {
        if (isStandard(h)) {
            // the stream stays open for the runtime's own messages
            if (stdClosed[(int) h]) {
                return "file closed";
            }
            stdClosed[(int) h] = true;
            return null;
        }
        End pe = pipes.remove(h);
        if (pe != null) {
            pe.channel().release(pe.write());
            return null;
        }
        RandomAccessFile r = files.remove(h);
        paths.remove(h);
        readonlyFiles.remove(h);
        if (r == null) {
            return "file closed";
        }
        try {
            r.close();
            return null;
        } catch (IOException e) {
            return message(e);
        }
    }
}
