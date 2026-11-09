// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * The externals M3jZip of the modules Deflate and Zip of m3j (see Externals):
 * the compression and the archives of the Java platform.
 *
 * <p>An object does no input or output of a stream of the program. The
 * modules give it bytes and take the results, so that a timeout, an alert or
 * the failure of a reader or writer stays in Modula-3 and never passes through
 * code of the platform.
 *
 * <p>The checks that the platform leaves out are here: ZipFile does not compare
 * the CRC or the length of what it has read.
 *
 * <p>Results less than 0 are the negated numbers below, the constants of the
 * interface M3jZip.
 */
final class Zip {
    static final long CORRUPT = 1;
    static final long UNSUPPORTED = 2;
    static final long DUPLICATE = 3;
    static final long CLOSED = 4;
    static final long DICTIONARY = 5;

    static final long NO_FLUSH = 0;
    static final long SYNC_FLUSH = 1;
    static final long FINISH = 2;

    private static final long MASK = 0xFFFFFFFFL;

    /**
     * The longest name that an entry may have, in bytes. The platform limits
     * the name and the other variable parts of a header together to 65535 less
     * 46 bytes, and it fails only after it has taken the entry on, so that the
     * archive cannot go on: the limit here leaves room for those parts.
     */
    private static final int MAX_NAME_BYTES = 65000;

    private Zip() {}

    // inflating

    private static final class Inflating {
        final Inflater inflater;
        final CRC32 crc = new CRC32();

        Inflating(boolean raw) {
            inflater = new Inflater(raw);
        }
    }

    static Object newInflate(boolean raw) {
        return new Inflating(raw);
    }

    static void inflateFeed(Object z, byte[] b, int off, int n) {
        ((Inflating) z).inflater.setInput(b, off, n);
    }

    /** The number of bytes put into b, which may be 0; the CRC counts them. */
    static long inflate(Object o, byte[] b, int off, int n) {
        Inflating z = (Inflating) o;
        try {
            int got = z.inflater.inflate(b, off, n);
            if (got > 0) {
                z.crc.update(b, off, got);
            } else if (z.inflater.needsDictionary()) {
                return -DICTIONARY;
            }
            return got;
        } catch (DataFormatException e) {
            return -CORRUPT;
        }
    }

    static boolean inflateNeedsInput(Object z) {
        return ((Inflating) z).inflater.needsInput();
    }

    static boolean inflateFinished(Object z) {
        return ((Inflating) z).inflater.finished();
    }

    /** How many of the bytes that were fed the stream did not use. */
    static long inflateRemaining(Object z) {
        return ((Inflating) z).inflater.getRemaining();
    }

    /** The length of the output of this stream, modulo 2^32. */
    static long inflateTotal(Object z) {
        return ((Inflating) z).inflater.getBytesWritten() & MASK;
    }

    /** Makes ready for another stream. */
    static void inflateReset(Object o) {
        Inflating z = (Inflating) o;
        z.inflater.reset();
        z.crc.reset();
    }

    static void inflateEnd(Object z) {
        ((Inflating) z).inflater.end();
    }

    static void crcByte(Object z, long b) {
        ((Inflating) z).crc.update((int) b);
    }

    static long crcValue(Object z) {
        return ((Inflating) z).crc.getValue();
    }

    static void crcReset(Object z) {
        ((Inflating) z).crc.reset();
    }

    // deflating

    private static final class Deflating {
        final Deflater deflater;
        final CRC32 crc = new CRC32();

        Deflating(int level, boolean raw) {
            deflater = new Deflater(level, raw);
        }
    }

    static Object newDeflate(long level, boolean raw) {
        return new Deflating((int) level, raw);
    }

    static void deflateFeed(Object o, byte[] b, int off, int n) {
        Deflating z = (Deflating) o;
        z.crc.update(b, off, n);
        z.deflater.setInput(b, off, n);
    }

    /**
     * The number of bytes put into b. With SYNC_FLUSH the array must be longer
     * than the marker of a flush, or the call never stops producing markers.
     */
    static long deflate(Object o, byte[] b, int off, int n, long mode) {
        Deflating z = (Deflating) o;
        if (mode == FINISH) {
            z.deflater.finish();
        }
        return z.deflater.deflate(b, off, n, mode == SYNC_FLUSH ? Deflater.SYNC_FLUSH : Deflater.NO_FLUSH);
    }

    static boolean deflateFinished(Object z) {
        return ((Deflating) z).deflater.finished();
    }

    static long deflateCrc(Object z) {
        return ((Deflating) z).crc.getValue();
    }

    /** The length of the input of this stream, modulo 2^32. */
    static long deflateTotal(Object z) {
        return ((Deflating) z).deflater.getBytesRead() & MASK;
    }

    static void deflateEnd(Object z) {
        ((Deflating) z).deflater.end();
    }

    // reading archives

    private static final class Archive {
        final ZipFile file;
        final ZipEntry[] entries;
        final Map<String, Integer> index;
        volatile boolean closed;

        Archive(ZipFile file, ZipEntry[] entries, Map<String, Integer> index) {
            this.file = file;
            this.entries = entries;
            this.index = index;
        }
    }

    /**
     * The character set of the names that do not say that they are UTF-8. The
     * format says code page 437; the module that has it is optional in a run
     * time image, and Latin-1 keeps at least the ASCII names of such an archive.
     */
    private static Charset legacy() {
        return Charset.isSupported("IBM437") ? Charset.forName("IBM437") : StandardCharsets.ISO_8859_1;
    }

    private static long classify(IOException e) {
        String m = e.getMessage();
        boolean unsupported = e instanceof ZipException && m != null
                && (m.contains("encrypted") || m.contains("compression method"));
        return unsupported ? UNSUPPORTED : CORRUPT;
    }

    /** The archive at path, or null with code set. */
    static Object openArchive(String path, LongRef code) {
        ZipFile file;
        try {
            file = new ZipFile(FileSystem.path(path).toFile(), ZipFile.OPEN_READ, legacy());
        } catch (IOException e) {
            code.set(classify(e));
            return null;
        } catch (RuntimeException e) {
            code.set(CORRUPT);
            return null;
        }
        try {
            List<ZipEntry> list = new ArrayList<>(file.size());
            Map<String, Integer> index = new HashMap<>();
            Enumeration<? extends ZipEntry> all = file.entries();
            while (all.hasMoreElements()) {
                ZipEntry e = all.nextElement();
                // the platform finds the data of an entry by its name, so one
                // of two entries with the same name could not be read
                if (index.putIfAbsent(e.getName(), list.size()) != null) {
                    code.set(DUPLICATE);
                    close(file);
                    return null;
                }
                list.add(e);
            }
            return new Archive(file, list.toArray(new ZipEntry[0]), index);
        } catch (RuntimeException e) {
            code.set(CORRUPT);
            close(file);
            return null;
        }
    }

    private static void close(ZipFile file) {
        try {
            file.close();
        } catch (IOException e) {
            // nothing depends on it
        }
    }

    static void closeArchive(Object o) {
        Archive a = (Archive) o;
        a.closed = true;
        close(a.file);
    }

    static long archiveCount(Object a) {
        return ((Archive) a).entries.length;
    }

    /** The number of the entry with this name, or -1. */
    static long archiveFind(Object a, String name) {
        Integer i = ((Archive) a).index.get(name);
        return i == null ? -1 : i;
    }

    private static ZipEntry entry(Object a, long i) {
        return ((Archive) a).entries[(int) i];
    }

    static String entryName(Object a, long i) {
        return entry(a, i).getName();
    }

    static long entrySize(Object a, long i) {
        return entry(a, i).getSize();
    }

    static long entryCompressed(Object a, long i) {
        return entry(a, i).getCompressedSize();
    }

    static long entryCrc(Object a, long i) {
        return entry(a, i).getCrc();
    }

    /** The time of the last change in milliseconds since 1970, or -1. */
    static long entryTime(Object a, long i) {
        return entry(a, i).getTime();
    }

    static boolean entryDirectory(Object a, long i) {
        return entry(a, i).isDirectory();
    }

    private static final class Reading {
        final Archive archive;
        final InputStream in;
        final CRC32 crc = new CRC32();
        final long size;
        final long expected;
        long total;

        Reading(Archive archive, InputStream in, long size, long expected) {
            this.archive = archive;
            this.in = in;
            this.size = size;
            this.expected = expected;
        }
    }

    /** A stream of the data of the entry, or null with code set. */
    static Object entryOpen(Object o, long i, LongRef code) {
        Archive a = (Archive) o;
        if (a.closed) {
            code.set(CLOSED);
            return null;
        }
        ZipEntry e = a.entries[(int) i];
        try {
            InputStream in = a.file.getInputStream(e);
            if (in == null) {
                code.set(CORRUPT);
                return null;
            }
            return new Reading(a, in, e.getSize(), e.getCrc());
        } catch (IOException x) {
            code.set(classify(x));
            return null;
        } catch (RuntimeException x) {
            code.set(a.closed ? CLOSED : CORRUPT);
            return null;
        }
    }

    /** The number of bytes put into b, 0 at the end, or an error. */
    static long entryRead(Object o, byte[] b, int off, int n) {
        Reading r = (Reading) o;
        if (r.archive.closed) {
            return -CLOSED;
        }
        try {
            int got = r.in.read(b, off, n);
            if (got < 0) {
                return r.total == r.size && r.crc.getValue() == r.expected ? 0 : -CORRUPT;
            }
            r.crc.update(b, off, got);
            r.total += got;
            return r.total > r.size ? -CORRUPT : got;
        } catch (IOException | RuntimeException x) {
            return r.archive.closed ? -CLOSED : -CORRUPT;
        }
    }

    static void entryClose(Object o) {
        try {
            ((Reading) o).in.close();
        } catch (IOException x) {
            // nothing depends on it
        }
    }

    // writing archives

    /** What the archive has written and the module has not taken yet. */
    private static final class Sink extends ByteArrayOutputStream {
        private int taken;

        Sink() {
            super(1 << 16);
        }

        int take(byte[] b, int off, int n) {
            int m = Math.min(n, count - taken);
            System.arraycopy(buf, taken, b, off, m);
            taken += m;
            if (taken == count) {
                reset();
                taken = 0;
            }
            return m;
        }
    }

    private static final class Writing {
        final Sink sink = new Sink();
        final ZipOutputStream out = new ZipOutputStream(sink);
    }

    static Object newWriter() {
        return new Writing();
    }

    /** Begins an entry and returns 0, or an error; after CLOSED the archive is lost. */
    static long writerAdd(Object o, String name, long millis, long level) {
        Writing w = (Writing) o;
        if (name.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES) {
            return -UNSUPPORTED;
        }
        try {
            ZipEntry e = new ZipEntry(name);
            if (millis != 0) {
                e.setTime(millis);
            }
            w.out.setLevel((int) level);
            w.out.putNextEntry(e);
            return 0;
        } catch (ZipException e) {
            String m = e.getMessage();
            return m != null && m.startsWith("duplicate entry") ? -DUPLICATE : -CLOSED;
        } catch (IOException | RuntimeException e) {
            return -CLOSED;
        }
    }

    static long writerWrite(Object o, byte[] b, int off, int n) {
        try {
            ((Writing) o).out.write(b, off, n);
            return 0;
        } catch (IOException | RuntimeException e) {
            return -UNSUPPORTED;
        }
    }

    static long writerCloseEntry(Object o) {
        try {
            ((Writing) o).out.closeEntry();
            return 0;
        } catch (IOException | RuntimeException e) {
            return -UNSUPPORTED;
        }
    }

    /** Writes the directory of the archive and frees the compressor. */
    static long writerFinish(Object o) {
        try {
            ((Writing) o).out.close();
            return 0;
        } catch (IOException | RuntimeException e) {
            return -UNSUPPORTED;
        }
    }

    static long writerTake(Object o, byte[] b, int off, int n) {
        return ((Writing) o).sink.take(b, off, n);
    }
}
