// Java port of CM3's m3-sys/m3middle/src/M3ID.i3 and M3ID.m3.
// Original: Copyright (C) 1993, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.middle;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import m3j.util.AssertError;
import m3j.util.RangeError;

/**
 * An "M3ID.T" represents a Modula-3 identifier.  Equal identifiers
 * are represented by the same M3ID.T.  Any non-NULL character
 * may be included in an "M3ID.T".
 *
 * <p>Port: T ([-16_7fffffff-1 .. 16_7fffffff]) is an {@code int}. The
 * module variables are the fields of {@link State}. The original keeps the
 * characters of all ids, each followed by a NUL, in shared buffers of
 * MaxLength characters; here each id keeps its characters in an array
 * ending with a NUL, and the comparisons go through the arrays as the
 * original goes through the buffers. An id longer than the buffers is the
 * subscript fault it is in the original. Ids with NUL characters, which
 * the interface excludes, are not supported: the original compares them
 * against the characters of the next id in its buffer. ToStr is omitted: it
 * returns an address.
 */
public final class M3ID {
    private M3ID() {}

    /** an illegal ID, never returned by Add or FromStr */
    public static final int NoID = 0;

    private static final int MaxLength = 8192 - 8; // (* allocator goo *)
    private static final char NullChar = '\000';

    private static final class Desc {
        /** the characters, followed by NullChar (Port: start) */
        char[] start = null;
        long hash = 0;
        String text = null;
    }

    /** The module variables of M3ID. */
    public static final class State {
        int next_t = 1;
        Desc[] ids = newDescs(2000);

        long hashMask = 2047; // == 2^11-1 == 11 bits on
        int[] hashTable = new int[2048];

        int[] classes = new int[201];

        /** Mark = [0..255] */
        int[] marks = null;
        int next_mark = 0;

        /**
         * Where IO.Put writes the text of an id that is too long, before
         * the assertion fails (Port: the original writes to stdout).
         */
        public PrintStream io = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
    }

    private static Desc[] newDescs(int n) {
        Desc[] d = new Desc[n];
        for (int i = 0; i < n; i++) {
            d[i] = new Desc();
        }
        return d;
    }

    /*-------------------------------------------------------------- exported ---*/

    /** Returns the ID representing "x" and sets the class of the ID to "class". */
    public static int add(State s, String x, int clazz) {
        if (clazz < 0 || clazz > 255) {
            throw new RangeError("M3ID");
        }
        int t;
        int len = x.length();
        char[] buf = new char[1025];
        if (len > buf.length) {
            s.io.print(x);
            s.io.flush();
        }
        AssertError.check(len <= buf.length, "M3ID");
        x.getChars(0, len, buf, 0);
        t = fromStr(s, buf, len);
        if (clazz != 0) {
            s.classes[t] = clazz;
        }
        if (s.ids[t].text == null) {
            s.ids[t].text = x;
        }
        return t;
    }

    /** Add with class 0. */
    public static int add(State s, String x) {
        return add(s, x, 0);
    }

    /** Returns the ID representing buf[0 .. MIN(HIGH(buf), length-1)]. */
    public static int fromStr(State s, char[] buf, long length) {
        long hash;
        int n;
        int bucket;
        int t;
        int p0;
        int p1;
        char[] chars;
        length = Math.min(length, buf.length);
        hash = 0;
        for (int i = 0; i <= length - 1; i++) {
            hash = 17 * hash + buf[i];
        }

        bucket = (int) (hash & s.hashMask);
        for (;;) {
            t = s.hashTable[bucket];
            if (t == NoID) { // empty!
                break;
            }
            if (s.ids[t].hash == hash) {
                p0 = 0;
                chars = s.ids[t].start;
                p1 = 0;
                n = (int) length;
                while (n > 0 && buf[p0] == chars[p1]) {
                    n--;
                    p0++;
                    p1++;
                }
                if (n == 0 && chars[p1] == NullChar) {
                    return t;
                }
            }
            bucket++;
            if (bucket >= s.hashTable.length) {
                bucket = 0;
            }
        }

        // we didn't find a match => build a new one
        t = s.next_t;
        s.next_t++;
        if (t >= s.ids.length) {
            expandIDs(s);
        }
        s.hashTable[bucket] = t;

        // make sure we've got room to stuff the characters
        // Sic: an id of MaxLength or more characters does not fit into a
        // fresh buffer either.
        if (length + 1 > MaxLength) {
            throw new ArrayIndexOutOfBoundsException("M3ID: id too long");
        }

        // initialize the descriptor and stuff the characters
        Desc z = s.ids[t];
        z.start = new char[(int) length + 1];
        z.hash = hash;
        z.text = null;
        System.arraycopy(buf, 0, z.start, 0, (int) length);
        z.start[(int) length] = NullChar;

        // make sure we're not overloading the hash table
        if (2L * s.next_t > s.hashTable.length) {
            expandHashTable(s);
        }

        return t;
    }

    /** FromStr with length = 99999. */
    public static int fromStr(State s, char[] buf) {
        return fromStr(s, buf, 99999);
    }

    /** strlen of the characters of t. */
    private static int strlen(char[] chars) {
        int len = 0;
        while (chars[len] != NullChar) {
            len++;
        }
        return len;
    }

    /** Returns a text equal to the one "t" represents. */
    public static String toText(State s, int t) {
        String x;
        AssertError.check(t < s.next_t, "M3ID");
        if (t == NoID) {
            return null;
        }
        x = s.ids[t].text;
        if (x == null) {
            char[] ptr = s.ids[t].start;
            x = new String(ptr, 0, strlen(ptr));
            s.ids[t].text = x;
        }
        return x;
    }

    // Port, omitted: ToStr.

    /** == M3Buf.PutText (ToText(t)), without the implied TEXT allocation */
    public static void put(State s, M3Buf.T wr, int t) {
        char[] ptr = s.ids[t].start;
        AssertError.check(t < s.next_t, "M3ID");
        if (t == NoID) {
            return;
        }
        M3Buf.putSub(wr, ptr, 0, strlen(ptr));
    }

    /** Returns the class assigned to "t". */
    public static int getClass(State s, int t) {
        AssertError.check(t < s.next_t, "M3ID");
        if (t < s.classes.length - 1) {
            return s.classes[t];
        } else {
            AssertError.check(t < s.next_t, "M3ID");
            return 0;
        }
    }

    /** Returns the hash value of "t". */
    public static long hash(State s, int t) {
        AssertError.check(t < s.next_t, "M3ID");
        return s.ids[t].hash;
    }

    /** start using a "new" set of marks.  They're recycled after 256 advances. */
    public static void advanceMarks(State s) {
        if (s.marks == null || s.next_t >= s.marks.length) {
            s.marks = new int[s.ids.length];
            s.next_mark = 0; // reset since the allocator returns zeroed storage
        }
        s.next_mark = (s.next_mark + 1) & 0xff;
    }

    /**
     * Return "TRUE" if "SetMark(t)" hash been called since the last call
     * to "AdvanceMarks".  There's a chance for false positives, so clients
     * must be prepared to check that "t" was indeed marked twice.
     */
    public static boolean setMark(State s, int t) {
        int old;
        AssertError.check(t < s.next_t, "M3ID");
        old = s.marks[t];
        s.marks[t] = s.next_mark;
        return old == s.next_mark;
    }

    /**
     * Return "TRUE" if the text represented by "a" is lexicographically
     * less than the one represented by "b".
     */
    public static boolean isLT(State s, int a, int b) {
        AssertError.check(a < s.next_t && b < s.next_t, "M3ID");
        char[] pa = s.ids[a].start;
        char[] pb = s.ids[b].start;
        // Cstring.strcmp: unsigned characters up to the first NUL.
        int i = 0;
        while (pa[i] == pb[i] && pa[i] != NullChar) {
            i++;
        }
        return pa[i] < pb[i];
    }

    /*-------------------------------------------------------------- internal ---*/

    // Port, omitted: ExpandChars (each id has its own array).

    private static void expandIDs(State s) {
        int n = s.ids.length;
        Desc[] nw = newDescs(n + n);
        System.arraycopy(s.ids, 0, nw, 0, n);
        s.ids = nw;
    }

    private static void expandHashTable(State s) {
        int n_old = s.hashTable.length;
        int n_new = n_old + n_old;
        int[] nw = new int[n_new];
        long newMask = s.hashMask + s.hashMask + 1;
        int t;
        int bucket;
        for (int i = 0; i <= n_new - 1; i++) {
            nw[i] = NoID;
        }

        for (int i = 0; i <= n_old - 1; i++) {
            t = s.hashTable[i];
            if (t != NoID) {
                bucket = (int) (s.ids[t].hash & newMask);
                while (nw[bucket] != NoID) {
                    bucket++;
                    if (bucket >= n_new) {
                        bucket = 0;
                    }
                }
                nw[bucket] = t;
            }
        }

        s.hashMask = newMask;
        s.hashTable = nw;
    }
}
