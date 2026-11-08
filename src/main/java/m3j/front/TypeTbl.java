// Java port of CM3's m3-sys/m3front/src/types/TypeTbl.i3 and TypeTbl.m3.
// Original: Copyright (C) 1994, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.util.AssertError;

// File: TypeTbl.i3
// Last modified on Fri Jul 29 13:49:37 PDT 1994 by kalsow

// File: TypeTbl.i3 (sic, in TypeTbl.m3)
// Last modified on Fri Jul 29 13:51:30 PDT 1994 by kalsow

/** A hash table keyed by types, with values of any reference type. */
public final class TypeTbl {
    private TypeTbl() {}

    public static class T {
        public long cnt = 0;
        public long mask = 0;
        public long shift = 0;
        public Entry[] map = null;
    }

    /** Port: an element of Map = REF ARRAY OF RECORD key: Type.T;  value: REFANY;  END. */
    public static final class Entry {
        public Type.T key;
        public Object value;
    }

    /** Port: NEW (Map, n); the records are fresh, with NIL fields. */
    private static Entry[] newMap(int n) {
        Entry[] m = new Entry[n];
        for (int i = 0; i < n; i++) {
            m[i] = new Entry();
        }
        return m;
    }

    /**
     * Add the pair "(key,value)" to "t" and return the previous value.
     * If "t" is "NIL", create a fresh one.
     */
    public static Object put(TypeTbl.T[] t, Type.T key, Object value) {
        long uid = Type.globalUID(key);
        long hash = uid;
        Object old;
        long x, shift, mask, cnt;
        AssertError.check(uid == key.uid, "TypeTbl");
        if ((t[0] == null)) {
            t[0] = new T();
            t[0].mask = 255;
            t[0].shift = 24;
            t[0].map = newMap(256);
        }
        while (true) {
            shift = t[0].shift >>> 1;
            mask = t[0].mask;
            cnt = t[0].map.length;
            do {
                x = (hash >>> shift) & mask;
                Entry z = t[0].map[(int) x];
                if ((z.key == null)) {
                    z.key = key;
                    z.value = value;
                    t[0].cnt++;
                    if ((2 * t[0].cnt > t[0].map.length)) {
                        expand(t[0]);
                    }
                    return null;
                } else if ((z.key.uid == uid)) {
                    old = z.value;
                    z.value = value;
                    return old;
                }
                hash = hash * 0x9e3779b9L /*== 2^32/phi*/ + 1;
                cnt--;
            } while (!(cnt <= 0));
            // Sic: after the expansion the probe sequence continues from
            // the current hash, not from uid.
            expand(t[0]);
        }
    }

    /**
     * If a pair "(u,v)" exists such that Type.IsEqual(u,key), return "v".
     * Othewise, return NIL
     */
    public static Object get(TypeTbl.T t, Type.T key) {
        long uid = Type.globalUID(key);
        long hash = uid;
        long x, shift, mask, cnt;
        AssertError.check(uid == key.uid, "TypeTbl");
        if ((t == null)) {
            return null;
        }

        shift = t.shift >>> 1;
        mask = t.mask;
        cnt = t.map.length;
        do {
            x = (hash >>> shift) & mask;
            Entry z = t.map[(int) x];
            if ((z.key == null)) {
                return null;
            } else if ((z.key.uid == uid)) {
                return z.value;
            }
            hash = hash * 0x9e3779b9L /*== 2^32/phi*/ + 1;
            cnt--;
        } while (!(cnt <= 0));

        return null;
    }

    /** Remove all entries from 't' */
    public static void reset(TypeTbl.T t) {
        if ((t == null)) {
            return;
        }
        t.cnt = 0;
        for (int i = 0; i <= t.map.length - 1; i++) {
            Entry z = t.map[i];
            z.key = null;
            z.value = null;
        }
    }

    private static void expand(T t) {
        int n = t.map.length;
        Entry[] old = t.map;
        t.cnt = 0;
        t.map = newMap(n + n);
        t.mask = t.mask + t.mask + 1;
        t.shift = t.shift - 1;
        for (int i = 0; i <= n - 1; i++) {
            Entry z = old[i];
            if (z.key != null) {
                put(new T[] { t }, z.key, z.value);
            }
        }
    }
}
