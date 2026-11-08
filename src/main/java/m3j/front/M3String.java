// Java port of CM3's m3-sys/m3front/src/misc/M3String.i3 and M3String.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.Target;
import m3j.util.AssertError;
import m3j.util.RangeError;

// File: M3String.i3
// Last modified on Mon Jun 27 10:33:54 PDT 1994 by kalsow
//      modified on Sat Feb 24 01:03:39 1990 by muller

// File: M3String.m3
// Last modified on Fri Sep 16 14:14:22 PDT 1994 by kalsow
//      modified on Wed Nov 28 02:23:29 1990 by muller

public final class M3String {
    private M3String() {}

    /**
     * There are two variants of a String.T:
     * <pre>
     *   (body # NIL)  =&gt; the characters are in body
     *   ELSE          =&gt; prefix &amp; suffix
     * </pre>
     */
    public static final class T {
        public T prefix = null;
        public T suffix = null;
        public String body = null;
        public long length = 0;
        public long hash = 0;
        public long uid = 0;
        public T next = null;
    }

    // Port: Buf = ARRAY OF CHAR is char[]; a SUBARRAY (buf, 0, n) is
    // passed as the array and n. HashTable = REF ARRAY OF T is T[].

    private static final char[] Digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    private static long hashMask = 511; // == 2^9-1 == 9 bits on
    private static T[] hashTable = null;
    private static T next_t = null;
    private static long nStrings = 0;

    //------------------------------------------------------------- exported ---

    /** Returns a string equal to 'x' */
    public static M3String.T add(String x) {
        char[] buf = new char[256];
        char[] ref;
        if ((next_t == null)) {
            next_t = new T();
        }
        next_t.prefix = null;
        next_t.suffix = null;
        next_t.body = x;
        next_t.length = x.length();
        next_t.uid = NO_UID;
        if ((next_t.length <= buf.length)) {
            setChars(buf, x);
            return intern(buf, next_t.length);
        } else {
            ref = new char[(int) next_t.length];
            setChars(ref, x);
            return intern(ref, ref.length);
        }
    }

    /** Returns a string equal to buf[0 .. MIN(HIGH(buf), length-1)]. */
    public static M3String.T fromStr(char[] buf, long length) {
        T t;
        if ((next_t == null)) {
            next_t = new T();
        }
        next_t.prefix = null;
        next_t.suffix = null;
        next_t.body = null; // for now
        next_t.length = Math.min(length, buf.length);
        next_t.uid = NO_UID;
        t = intern(buf, next_t.length);
        return t;
    }

    public static M3String.T fromStr(char[] buf) {
        return fromStr(buf, 99999);
    }

    /** Returns the concatenation of two strings */
    public static M3String.T concat(M3String.T a, M3String.T b) {
        char[] buf = new char[4];
        if ((a == null) || (a.length == 0)) {
            return b;
        }
        if ((b == null) || (b.length == 0)) {
            return a;
        }
        if ((next_t == null)) {
            next_t = new T();
        }
        next_t.prefix = a;
        next_t.suffix = b;
        next_t.body = null;
        next_t.length = a.length + b.length;
        next_t.uid = NO_UID;
        return intern(buf, buf.length);
    }

    /** Returns a text equal to t */
    public static String toText(M3String.T t) {
        char[] buf = new char[256];
        char[] ref;
        if ((t == null)) {
            return null;
        } else if ((t.body != null)) {
            // already done.
        } else if ((t.length <= buf.length)) {
            flatten(t, buf, 0);
            t.body = new String(buf, 0, (int) t.length);
        } else {
            ref = new char[(int) t.length];
            flatten(t, ref, 0);
            t.body = new String(ref);
        }
        return t.body;
    }

    /** writes the string on the writer */
    public static void put(M3Buf.T wr, M3String.T t) {
        if ((t == null)) {
            // done
        } else if ((t.body != null)) {
            for (long i = 0; i <= t.length - 1; i++) {
                emitChar(wr, getChar(t.body, i));
            }
        } else {
            put(wr, t.prefix);
            put(wr, t.suffix);
        }
    }

    /**
     * initializes the current variable at 'offset' in the global
     * data or constant pool with the characters of 't'.
     */
    public static void init_chars(long offset, M3String.T t, boolean is_const) {
        if ((t == null)) {
            // done
        } else if ((t.body != null)) {
            CG.init_chars(offset, t.body, is_const);
        } else {
            init_chars(offset, t.prefix, is_const);
            init_chars(offset + t.prefix.length * Target.Char.size, t.suffix, is_const);
        }
    }

    /** returns the length of the string */
    public static long length(M3String.T t) {
        if ((t == null)) {
            return 0;
        } else {
            return t.length;
        }
    }

    // NOTE! These UIDs have nothing to do with the UIDs that are hashes
    //       and used many places.

    public static final long NO_UID = -1;

    /** returns the string's UID. NO_UID if SetUID never called. */
    public static long getUID(M3String.T t) {
        return t.uid;
    }

    /** sets the string's UID */
    public static void setUID(M3String.T t, long uid) {
        t.uid = uid;
    }

    /** returns a hash value for the string */
    public static long hash(M3String.T t) {
        if ((t == null)) {
            return 953;
        } else {
            return t.hash;
        }
    }

    //------------------------------------------------------------- internal ---

    /** Port: buf[0 .. n-1] is the original's open array. */
    private static T intern(char[] buf, long n) {
        long hash, bucket;
        T t;
        // search the hash table
        next_t.hash = 0;
        hash = internHash(next_t, 0, buf);
        bucket = hash & hashMask;
        t = hashTable[(int) bucket];
        while ((t != null)) {
            if ((t.hash == hash) && equal(t, next_t, buf)) {
                return t;
            }
            t = t.next;
        }

        // we didn't find the string => add it to the hash table
        t = next_t;
        t.hash = hash;
        t.next = hashTable[(int) bucket];
        hashTable[(int) bucket] = t;
        next_t = null; // since we've used it!

        if ((t.prefix == null) && (t.body == null)) {
            t.body = new String(buf, 0, (int) n);
        }

        nStrings++;
        if ((nStrings > 2 * hashTable.length)) {
            expandHashTable();
        }
        return t;
    }

    private static void expandHashTable() {
        int n_old = hashTable.length;
        int n_new = n_old + n_old;
        T[] new_ = new T[n_new];
        long newMask = hashMask + hashMask + 1;
        T t, u;
        long x;
        for (int i = 0; i <= n_new - 1; i++) {
            new_[i] = null;
        }

        for (int i = 0; i <= n_old - 1; i++) {
            t = hashTable[i];
            while ((t != null)) {
                u = t.next;
                x = t.hash & newMask;
                t.next = new_[(int) x];
                new_[(int) x] = t;
                t = u;
            }
        }

        hashMask = newMask;
        hashTable = new_;
    }

    private static long internHash(T t, long hash, char[] buf) {
        if ((t == null)) {
            return 0;
        }
        if ((hash == 0) && (t.hash != 0)) {
            return t.hash;
        }

        if ((t.body != null)) {
            for (long i = 0; i <= t.length - 1; i++) {
                hash = 2 * hash + getChar(t.body, i);
            }
        } else if ((t.prefix != null)) {
            // a concatentation
            hash = internHash(t.prefix, hash, buf);
            hash = internHash(t.suffix, hash, buf);
        } else { // use the buffer
            for (long i = 0; i <= t.length - 1; i++) {
                hash = 2 * hash + buf[(int) i];
            }
        }

        return hash;
    }

    private static boolean equal(T a, T b, char[] buf) {
        if ((a.length != b.length)) {
            return false;
        }
        for (long i = 0; i <= a.length - 1; i++) {
            if (getCh(a, buf, i) != getCh(b, buf, i)) {
                return false;
            }
        }
        return true;
    }

    private static char getCh(T t, char[] buf, long i) {
        T u;
        // walk the tree to find the right segment
        while ((t.prefix != null)) {
            u = t.prefix;
            if ((u.length > i)) {
                t = t.prefix;
            } else {
                t = t.suffix;
                i -= u.length;
            }
        }

        if ((t.body != null)) {
            return getChar(t.body, i);
        } else {
            return buf[(int) i];
        }
    }

    private static void flatten(T t, char[] buf, long start) {
        if ((t == null)) {
            // done
        } else if ((t.body != null)) {
            // Port: Text.SetChars (SUBARRAY (buf, start, t.length), t.body).
            t.body.getChars(0, (int) t.length, buf, (int) start);
        } else {
            while ((t != null) && (t.body == null)) {
                flatten(t.suffix, buf, start + length(t.prefix));
                t = t.prefix;
            }
            flatten(t, buf, start);
        }
    }

    private static void emitChar(M3Buf.T wr, char c) {
        long i;
        if ((c < ' ') || (c == '"') || (c == '\'') || ('~' < c) || (c == '\\')) {
            i = c & 255;
            M3Buf.putChar(wr, '\\');
            M3Buf.putChar(wr, Digits[(int) Math.floorDiv(i, 64)]);
            i = i & 63;
            M3Buf.putChar(wr, Digits[(int) Math.floorDiv(i, 8)]);
            i = i & 7;
            M3Buf.putChar(wr, Digits[(int) i]);
        } else { // simple graphic character
            M3Buf.putChar(wr, c);
        }
    }

    /**
     * Port: Text.GetChar; a character above 255 is the checked runtime
     * error of the original.
     */
    private static char getChar(String t, long i) {
        char c = t.charAt((int) i);
        if (c > 255) {
            throw new RangeError("M3String");
        }
        return c;
    }

    /** Port: Text.SetChars (a, t) for a text not longer than a. */
    private static void setChars(char[] a, String t) {
        for (int i = 0; i < t.length(); i++) {
            a[i] = getChar(t, i);
        }
    }

    //------------------------------------------------------- initialization ---

    public static void initialize() {
        AssertError.check(hashTable == null, "M3String");
        hashTable = new T[(int) (hashMask + 1)];
        for (int i = 0; i <= hashTable.length - 1; i++) {
            hashTable[i] = null;
        }
    }

    public static void reset() {
        T t;
        for (int i = 0; i <= hashTable.length - 1; i++) {
            t = hashTable[i];
            while ((t != null)) {
                t.uid = NO_UID;
                t = t.next;
            }
        }
    }
}
