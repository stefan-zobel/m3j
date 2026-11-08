// Java port of CM3's m3-sys/m3front/src/values/PersistentRevelation.i3 and
// PersistentRevelation.m3, and of the instantiations PersistentRevelationSeq
// and PersistentRevelationArraySort (m3-sys/m3front/src/values/m3makefile)
// of m3-libs/libm3/src/sequence/Sequence.ig, Sequence.mg, SequenceRep.ig
// and m3-libs/libm3/src/sort/ArraySort.ig, ArraySort.mg.
// Original: PersistentRevelation carries no copyright line; m3front is
// distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Sequence: Copyright 1993 Digital Equipment Corporation.
// ArraySort: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: Sequence.mg
// Last modified on Thu Sep 22 19:45:14 PDT 1994 by heydon
//      modified on Thu Sep  8 15:15:04 PDT 1994 by kalsow
//      modified on Tue Aug  9 15:58:24 PDT 1994 by detlefs
//      modified on Mon Oct 25 11:00:36 PDT 1993 by mcjones
//      modified on Mon Jun 28 13:20:59 PDT 1993 by gnelson

// File: ArraySort.mg
// Last modified on Tue Dec 13 14:36:35 PST 1994 by kalsow
//      modified on Thu Apr 22 15:17:40 PDT 1993 by mcjones

/** A PersistentRevelation is a pair of integers written into object code. */
public final class PersistentRevelation {
    private PersistentRevelation() {}

    public static final class T {
        public long lhs_id = 0;
        public long rhs_id = 0;

        public T() {}

        public T copy() {
            T r = new T();
            r.lhs_id = lhs_id;
            r.rhs_id = rhs_id;
            return r;
        }
    }

    public static final String Brand = "PersistentRevelation";

    private static long compareInt(long a, long b) {
        if (a < b) {
            return -1;
        } else if (a > b) {
            return 1;
        }
        return 0;
    }

    public static long compare(PersistentRevelation.T a, PersistentRevelation.T b) {
        long c = compareInt(a.lhs_id, b.lhs_id);
        if (c == 0) {
            c = compareInt(a.rhs_id, b.rhs_id);
        }
        return c;
    }

    //------------------------------------------- PersistentRevelationSeq ---

    /**
     * Port: PersistentRevelationSeq.T with the fields of its representation
     * in PersistentRevelationSeqRep, reduced to the methods the front end
     * calls (init and addhi).
     *
     * <p>Element "i" of "s" is stored in
     * {@code s.elem[(s.st + i) MOD NUMBER(s.elem^)]}.
     */
    public static final class Seq {
        public T[] elem;
        public long st = 0;
        public long sz = 0;

        public Seq() {}

        /** Port: init (sizeHint := 5). */
        public Seq init() {
            return seqInit(this, 5);
        }

        public void addhi(T x) {
            seqAddhi(this, x);
        }
    }

    // Port: Modula-3 requires "zero" to be initialized to a value of type
    // "Elem.T"; NEW initializes the elements of a new array with the
    // default values of T.

    /** Port: NEW (Rep.RefArray, n). */
    private static T[] newRefArray(long n) {
        T[] a = new T[(int) n];
        for (int i = 0; i < a.length; i++) {
            a[i] = new T();
        }
        return a;
    }

    private static Seq seqInit(Seq s, long sizeHint) {
        if (s.elem == null || s.elem.length == 0) {
            s.elem = newRefArray(Math.max(sizeHint, 1));
        } else {
            // Clear the previous entries to help the GC
            for (long i = s.st; i <= Math.min(s.st + s.sz - 1, s.elem.length - 1); i++) {
                s.elem[(int) i] = new T();
            }
            for (long i = 0; i <= Math.max(-1, s.st + s.sz - 1 - s.elem.length); i++) {
                s.elem[(int) i] = new T();
            }
        }
        s.sz = 0;
        s.st = 0;
        return s;
    }

    private static void seqAddhi(Seq s, T x) {
        if (s.sz == s.elem.length) {
            seqExpand(s);
        }
        {
            long i = s.st + s.sz;
            if (i >= s.elem.length) {
                i = i - s.elem.length;
            }
            s.elem[(int) i] = x.copy();
        }
        s.sz++;
    }

    private static void seqExpand(Seq s) {
        long n = s.elem.length;
        T[] new_ = newRefArray(2 * n);
        long m = n - s.st;
        // Port: the elements are never modified in place, so moving the
        // references is the same as copying the values.
        System.arraycopy(s.elem, (int) s.st, new_, 0, (int) m);
        System.arraycopy(s.elem, 0, new_, (int) m, (int) s.st);
        s.st = 0;
        s.elem = new_;
    }

    //------------------------------------- PersistentRevelationArraySort ---

    /**
     * Port: PersistentRevelationArraySort.Sort (SUBARRAY (a^, 0, n)) with
     * cmp = PersistentRevelation.Compare. Sort the elements of "a" using
     * the order defined by "cmp". The elements of {@code a[0..n-1]} are
     * permuted; they are never modified in place, so moving the references
     * is the same as copying the values.
     */
    public static void sort(T[] a, long n) {
        quickSort(a, 0, n);
        insertionSort(a, 0, n);
    }

    private static void quickSort(T[] a, long lo, long hi) {
        final long CutOff = 9;
        long i, j;
        T key, tmp;
        while ((hi - lo > CutOff)) { // sort a[lo..hi)

            // use median-of-3 to select a key
            i = Math.floorDiv(hi + lo, 2);
            if (compare(a[(int) lo], a[(int) i]) < 0) {
                if (compare(a[(int) i], a[(int) (hi - 1)]) < 0) {
                    key = a[(int) i];
                } else if (compare(a[(int) lo], a[(int) (hi - 1)]) < 0) {
                    key = a[(int) (hi - 1)];
                    a[(int) (hi - 1)] = a[(int) i];
                    a[(int) i] = key;
                } else {
                    key = a[(int) lo];
                    a[(int) lo] = a[(int) (hi - 1)];
                    a[(int) (hi - 1)] = a[(int) i];
                    a[(int) i] = key;
                }
            } else { // a[lo] >= a[i]
                if (compare(a[(int) (hi - 1)], a[(int) i]) < 0) {
                    key = a[(int) i];
                    tmp = a[(int) (hi - 1)];
                    a[(int) (hi - 1)] = a[(int) lo];
                    a[(int) lo] = tmp;
                } else if (compare(a[(int) lo], a[(int) (hi - 1)]) < 0) {
                    key = a[(int) lo];
                    a[(int) lo] = a[(int) i];
                    a[(int) i] = key;
                } else {
                    key = a[(int) (hi - 1)];
                    a[(int) (hi - 1)] = a[(int) lo];
                    a[(int) lo] = a[(int) i];
                    a[(int) i] = key;
                }
            }

            // partition the array
            i = lo + 1;
            j = hi - 2;

            // find the first hole
            while (compare(a[(int) j], key) > 0) {
                j--;
            }
            tmp = a[(int) j];
            j--;

            while (true) {
                if ((i > j)) {
                    break;
                }

                while (i < hi && compare(a[(int) i], key) < 0) {
                    i++;
                }
                if ((i > j)) {
                    break;
                }
                a[(int) (j + 1)] = a[(int) i];
                i++;

                while (j > lo && compare(a[(int) j], key) > 0) {
                    j--;
                }
                if ((i > j)) {
                    if ((j == i - 1)) {
                        j--;
                    }
                    break;
                }
                a[(int) (i - 1)] = a[(int) j];
                j--;
            }

            // fill in the last hole
            a[(int) (j + 1)] = tmp;
            i = j + 2;

            // then, recursively sort the smaller subfile
            if ((i - lo < hi - i)) {
                quickSort(a, lo, i - 1);
                lo = i;
            } else {
                quickSort(a, i, hi);
                hi = i - 1;
            }

        } // WHILE (hi-lo > CutOff)
    }

    private static void insertionSort(T[] a, long lo, long hi) {
        long j;
        T key;
        for (long i = lo + 1; i <= hi - 1; i++) {
            key = a[(int) i];
            j = i - 1;
            while ((j >= lo) && compare(key, a[(int) j]) < 0) {
                a[(int) (j + 1)] = a[(int) j];
                j--;
            }
            a[(int) (j + 1)] = key;
        }
    }
}
