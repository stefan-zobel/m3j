// Java port of CM3's m3-sys/m3front/src/misc/M3WString.i3 and M3WString.m3.
// Original: Copyright 1996-2000, Critical Mass, Inc.  All rights reserved.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.Target;
import m3j.util.AssertError;

/** "M3WString" manages WTEXT literals */
public final class M3WString {
    private M3WString() {}

    public static final class T {
        public T prefix = null;
        public T suffix = null;
        /** Port: WText_T = REF Buf. */
        public int[] body = null;
        public long length = 0;
        public long hash = 0;
        public long uid = 0;
        public T next = null;
    }

    // Port: Char = BITS 32 FOR [0..16_10ffff] is int.

    // Port: Buf = ARRAY OF Char is int[]; a SUBARRAY (buf, 0, n) is passed
    // as the array and n. HashTable = REF ARRAY OF T is T[].

    private static final char[] Digits = {'0', '1', '2', '3', '4', '5', '6', '7',
                                          '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private static long hashMask = 511; // == 2^9-1 == 9 bits on
    private static T[] hashTable = null;
    private static T next_t = null;
    private static long nStrings = 0;

    //------------------------------------------------------------- exported ---

    /** Returns a string equal to 'x' */
    public static M3WString.T add(String x) {
        long len = x.length();
        int[] body = new int[(int) (len + 1)];
        // Sic: the terminator goes to body[len-1], which is overwritten
        // below; body[len] stays 0. For len = 0 the subscript is out of range.
        body[(int) (len - 1)] = 0;
        for (long i = 0; i <= len - 1; i++) {
            // Port: Text.GetWideChar.
            body[(int) i] = x.charAt((int) i);
        }
        if ((next_t == null)) {
            next_t = new T();
        }
        next_t.prefix = null;
        next_t.suffix = null;
        next_t.body = body;
        next_t.length = len;
        next_t.uid = NO_UID;
        return intern(body, body.length);
    }

    /** Returns a string equal to buf[0 .. MIN(HIGH(buf), length-1)]. */
    public static M3WString.T fromStr(int[] buf, long length) {
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

    public static M3WString.T fromStr(int[] buf) {
        return fromStr(buf, 99999);
    }

    /** Returns the concatenation of two strings */
    public static M3WString.T concat(M3WString.T a, M3WString.T b) {
        int[] buf = new int[4];
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

    /** Returns a literal equal to t */
    public static String toLiteral(M3WString.T t) {
        long len = literalLength(t) + 3;
        char[] buf = new char[256];
        char[] ref;
        if ((t == null)) {
            return null;
        }
        if ((len <= buf.length)) {
            buf[0] = 'W';
            buf[1] = '"';
            flatten(t, buf, 2);
            buf[(int) (len - 1)] = '"';
            return new String(buf, 0, (int) len);
        } else {
            ref = new char[(int) len];
            ref[0] = 'W';
            ref[1] = '"';
            flatten(t, ref, 2);
            ref[(int) (len - 1)] = '"';
            return new String(ref);
        }
    }

    /** writes a literal representation of the string on the writer */
    public static void putLiteral(M3Buf.T wr, M3WString.T t) {
        M3Buf.putChar(wr, 'W');
        M3Buf.putChar(wr, '"');
        putChars(wr, t);
        M3Buf.putChar(wr, '"');
    }

    /**
     * initializes the current variable at 'offset' in global data or
     * constant pool with the characters of 't'.
     */
    public static void init_chars(long offset, M3WString.T t, boolean is_const) {
        long TargetWCBitsize;
        if ((t == null)) {
            // done
        } else {
            TargetWCBitsize = Target.wideCharSize(M3.target);
            if ((t.body != null)) {
                for (long i = 0; i <= t.length - 1; i++) {
                    CG.init_intt(offset, TargetWCBitsize, t.body[(int) i], is_const);
                    offset += TargetWCBitsize;
                }
            } else {
                init_chars(offset, t.prefix, is_const);
                init_chars(offset + t.prefix.length * TargetWCBitsize,
                           t.suffix, is_const);
            }
        }
    }

    /** returns the length of the string */
    public static long length(M3WString.T t) {
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
    public static long getUID(M3WString.T t) {
        return t.uid;
    }

    /** sets the string's UID */
    public static void setUID(M3WString.T t, long uid) {
        t.uid = uid;
    }

    /** returns a hash value for the string */
    public static long hash(M3WString.T t) {
        if ((t == null)) {
            return 953;
        } else {
            return t.hash;
        }
    }

    //------------------------------------------------------------- internal ---

    /** Port: buf[0 .. n-1] is the original's open array. */
    private static T intern(int[] buf, long n) {
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
            t.body = new int[(int) (n + 1)];
            System.arraycopy(buf, 0, t.body, 0, (int) n);
            t.body[t.body.length - 1] = 0;
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

    private static long internHash(T t, long hash, int[] buf) {
        if ((t == null)) {
            return 0;
        }
        if ((hash == 0) && (t.hash != 0)) {
            return t.hash;
        }

        if ((t.body != null)) {
            for (long i = 0; i <= t.length - 1; i++) {
                hash = 2 * hash + t.body[(int) i];
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

    private static boolean equal(T a, T b, int[] buf) {
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

    private static int getCh(T t, int[] buf, long i) {
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
            return t.body[(int) i];
        } else {
            return buf[(int) i];
        }
    }

    private static long literalLength(T t) {
        if ((t == null)) {
            return 0;
        } else if ((t.body != null)) {
            long len = 0;
            char[] lit = new char[8];
            for (long i = 0; i <= t.length - 1; i++) {
                len += charLiteral(t.body[(int) i], /* OUT, Unused */ lit);
            }
            return len;
        } else {
            return literalLength(t.prefix) + literalLength(t.suffix);
        }
    }

    private static void flatten(T t, char[] buf, long start) {
        if ((t == null)) {
            // done
        } else if ((t.body != null)) {
            long x;
            char[] lit = new char[8];
            for (long i = 0; i <= t.length - 1; i++) {
                x = charLiteral(t.body[(int) i], /* OUT */ lit);
                for (int j = 0; j <= x - 1; j++) {
                    buf[(int) start] = lit[j];
                    start++;
                }
            }
        } else {
            while ((t != null) && (t.body == null)) {
                flatten(t.suffix, buf, start + literalLength(t.prefix));
                t = t.prefix;
            }
            flatten(t, buf, start);
        }
    }

    private static void putChars(M3Buf.T wr, T t) {
        while ((t != null)) {
            if ((t.body != null)) {
                for (long i = 0; i <= t.length - 1; i++) {
                    emitChar(wr, t.body[(int) i]);
                }
                t = null;
            } else {
                putChars(wr, t.prefix);
                t = t.suffix;
            }
        }
    }

    private static void emitChar(M3Buf.T wr, int c) {
        char[] lit = new char[8];
        for (int i = 0; i <= charLiteral(c, /* OUT */ lit) - 1; i++) {
            M3Buf.putChar(wr, lit[i]);
        }
    }

    // Port: CharBuf = ARRAY [0..7] OF CHAR is char[8]. The original leaves
    // the local buffers uninitialized; here they start with 0 characters.

    /** Port: Word.Extract (x, i, n) for a nonnegative x. */
    private static int extract(int x, int i, int n) {
        return (x >>> i) & ((1 << n) - 1);
    }

    private static long charLiteral(int ch, char[] lit) {
        if (ch > Target.WideChar16Max) {
            lit[0] = '\\';
            lit[1] = 'U';
            lit[2] = Digits[extract(ch, 20, 1)];
            lit[3] = Digits[extract(ch, 16, 4)];
            // Sic: lit[3] is assigned twice and lit[4] not at all; lit[4]
            // keeps what the buffer held before.
            lit[3] = Digits[extract(ch, 12, 4)];
            lit[5] = Digits[extract(ch, 8, 4)];
            lit[6] = Digits[extract(ch, 4, 4)];
            lit[7] = Digits[extract(ch, 0, 4)];
            return 8;
        } else if ((ch == '\n')) {
            lit[0] = '\\';
            lit[1] = 'n';
            return 2;
        } else if ((ch == '\t')) {
            lit[0] = '\\';
            lit[1] = 't';
            return 2;
        } else if ((ch == '\r')) {
            lit[0] = '\\';
            lit[1] = 'r';
            return 2;
        } else if ((ch == '\f')) {
            lit[0] = '\\';
            lit[1] = 'f';
            return 2;
        } else if ((ch == '\\')) {
            lit[0] = '\\';
            lit[1] = '\\';
            return 2;
        } else if ((ch == '\'')) {
            lit[0] = '\\';
            lit[1] = '\'';
            return 2;
        } else if ((ch == '"')) {
            lit[0] = '\\';
            lit[1] = '"';
            return 2;
        } else if ((ch < ' ') || ('~' < ch)) {
            lit[0] = '\\';
            lit[1] = 'x';
            lit[2] = Digits[extract(ch, 12, 4)];
            lit[3] = Digits[extract(ch, 8, 4)];
            lit[4] = Digits[extract(ch, 4, 4)];
            lit[5] = Digits[extract(ch, 0, 4)];
            return 6;
        } else {
            lit[0] = (char) ch;
            return 1;
        }
    }

    //------------------------------------------------------- initialization ---

    public static void initialize() {
        AssertError.check(hashTable == null, "M3WString");
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
