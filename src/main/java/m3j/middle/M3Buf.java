// Java port of CM3's m3-sys/m3middle/src/M3Buf.i3 and M3Buf.m3.
// Original: Copyright (C) 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

import m3j.convert.Convert;
import m3j.fingerprint.Fingerprint;
import m3j.util.AssertError;

/**
 * An "M3Buf.T" is an in-memory, append-only buffer.  It's
 * cheaper to write strings and numbers to an "M3Buf.T" than
 * a "Wr.T".
 *
 * <p>It's an unchecked runtime error to concurrently call procedures
 * in this interface on a single "M3Buf.T".
 *
 * <p>Port: a Wr.T is a {@link Writer} that writes each character (in
 * [0..255]) as one byte, such as an OutputStreamWriter for ISO-8859-1;
 * Wr.Failure is an {@link UncheckedIOException} (the original declares it
 * FATAL).
 */
public final class M3Buf {
    private M3Buf() {}

    /** approx. 64K; leave some slop for the 'next' pointer & the allocator overhead */
    private static final int ChunkSize = 0xFF00;

    private static final class Chunk {
        Chunk next = null;
        final char[] buf = new char[ChunkSize];
    }

    /** BRANDED "MBuf.T" REF RECORD. */
    public static final class T {
        int nFull;
        int next;
        Chunk head;
        Chunk tail;
        Writer drain;

        private T() {}
    }

    public static T New() {
        T t = new T();
        t.nFull = 0;
        t.next = 0;
        t.head = new Chunk();
        t.tail = t.head;
        t.drain = null;
        return t;
    }

    public static void putChar(T t, char ch) {
        if (t.next >= ChunkSize) {
            expand(t);
        }
        t.tail.buf[t.next] = ch;
        t.next++;
    }

    /** PutSub on SUBARRAY (x, start, len). */
    public static void putSub(T t, char[] x, int start, int len) {
        int next = 0;
        int n;
        if (len < ChunkSize - t.next) {
            System.arraycopy(x, start, t.tail.buf, t.next, len);
            t.next += len;
        } else {
            while (len > 0) {
                n = Math.min(len, ChunkSize - t.next);
                System.arraycopy(x, start + next, t.tail.buf, t.next, n);
                len -= n;
                next += n;
                t.next += n;
                if (len > 0) {
                    expand(t);
                }
            }
        }
    }

    public static void putSub(T t, char[] x) {
        putSub(t, x, 0, x.length);
    }

    public static void putText(T t, String txt) {
        putSubText(t, txt, 0, Long.MAX_VALUE);
    }

    public static void putSubText(T t, String txt, long start, long len) {
        if (start < 0 || len < 0) {
            throw new m3j.util.RangeError("M3Buf");
        }
        long cnt = Math.min(len, txt.length() - start);
        char[] buf = new char[256];
        while (cnt > 0) {
            // Text.SetChars (buf, txt, start)
            int k = (int) Math.min(buf.length, txt.length() - start);
            txt.getChars((int) start, (int) start + k, buf, 0);
            putSub(t, buf, 0, (int) Math.min(buf.length, cnt));
            start += buf.length;
            cnt -= buf.length;
        }
    }

    private static final char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    public static void putInt(T t, long i) {
        int len;
        char[] buf = new char[64 + 4];
        if (0 <= i) {
            if (i < 10) {
                putChar(t, digits[(int) i]);
                return;
            } else if (i < 100) {
                // digits_100B[i], digits_100A[i]
                putChar(t, digits[(int) (i / 10)]);
                putChar(t, digits[(int) (i % 10)]);
                return;
            } else if (i < 1000) {
                putChar(t, digits[(int) (i / 100)]);
                putChar(t, digits[(int) ((i / 10) % 10)]);
                putChar(t, digits[(int) (i % 10)]);
                return;
            }
        }
        try {
            len = Convert.fromInt(buf, i, 10, false);
        } catch (Convert.Failed e) {
            // <*FATAL Convert.Failed*>
            throw new IllegalStateException(e);
        }
        putSub(t, buf, 0, len);
    }

    public static void putIntt(T t, int[] i) {
        long[] j = new long[1];
        char[] buf = new char[TInt.Size + 1];
        if (TInt.toInt(i, j)) {
            putInt(t, j[0]);
        } else {
            int n = TInt.toChars(i, buf);
            putSub(t, buf, 0, n);
        }
    }

    public static void putFloat(T t, Target.Float f) {
        int len;
        char[] buf = new char[4 * 32 + 4];
        AssertError.check(f.exponent == 0, "M3Buf");
        try {
            if (f.pre == Target.Precision.Short) {
                len = Convert.fromFloat(buf, (float) f.fraction, 13, Convert.Style.Sci);
            } else if (f.pre == Target.Precision.Long) {
                len = Convert.fromLongFloat(buf, f.fraction, 13, Convert.Style.Sci);
            } else {
                len = Convert.fromExtended(buf, f.fraction, 36, Convert.Style.Sci);
            }
        } catch (Convert.Failed e) {
            // <*FATAL Convert.Failed*>
            throw new IllegalStateException(e);
        }
        putSub(t, buf, 0, len);
    }

    public static String toText(T t) {
        String txt;
        if (t.nFull == 0) {
            txt = new String(t.head.buf, 0, t.next);
        } else {
            txt = messyToText(t);
        }
        reset(t);
        return txt;
    }

    private static String messyToText(T t) {
        int len = t.nFull * ChunkSize + t.next;
        char[] txt = new char[len];
        Chunk c = t.head;
        int n = 0;
        for (int i = 1; i <= t.nFull; i++) {
            System.arraycopy(c.buf, 0, txt, n, ChunkSize);
            c = c.next;
            n += ChunkSize;
        }
        if (t.next != 0) {
            System.arraycopy(c.buf, 0, txt, n, t.next);
        }
        return new String(txt);
    }

    public static Fingerprint.T toFP(T t) {
        Fingerprint.T fp = M3FP.OfEmpty;
        Chunk c = t.head;
        for (int i = 1; i <= t.nFull; i++) {
            fp = M3FP.fromChars(c.buf, fp);
            c = c.next;
        }
        if (t.next != 0) {
            fp = M3FP.fromChars(c.buf, 0, t.next, fp);
        }
        reset(t);
        return fp;
    }

    public static void flush(T t, Writer wr) {
        // <*FATAL Wr.Failure, Thread.Alerted*>
        Chunk c = t.head;
        try {
            for (int i = 1; i <= t.nFull; i++) {
                wr.write(c.buf, 0, ChunkSize);
                c = c.next;
            }
            if (t.next != 0) {
                wr.write(c.buf, 0, t.next);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        reset(t);
    }

    // Port, omitted: Append (commented out in the original).

    /**
     * Buffers with non-NIL drains attached will dump their contents to the
     * drain rather than allocate more memory.  ToText and ToFP are not useful
     * on buffers with non-NIL drains.
     */
    public static void attachDrain(T t, Writer wr) {
        t.drain = wr;
    }

    /*------------------------------------ internal ----------------------------*/

    private static void expand(T t) {
        AssertError.check(t.next == ChunkSize, "M3Buf");
        if (t.drain != null) {
            t.next = 0;
            t.nFull++;
            flush(t, t.drain);
        } else {
            if (t.tail.next == null) {
                t.tail.next = new Chunk();
            }
            t.tail = t.tail.next;
            t.next = 0;
            t.nFull++;
        }
    }

    private static void reset(T t) {
        // NOTE: we're not freeing the allocated chunks...
        t.tail = t.head;
        t.nFull = 0;
        t.next = 0;
    }
}
