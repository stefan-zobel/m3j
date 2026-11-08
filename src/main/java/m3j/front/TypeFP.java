// Java port of CM3's m3-sys/m3front/src/types/TypeFP.i3 and TypeFP.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.Arrays;

import m3j.convert.Convert;
import m3j.fingerprint.Fingerprint;
import m3j.middle.M3Buf;
import m3j.middle.M3FP;
import m3j.util.AssertError;

// File: TypeFP.i3
// Last modified on Fri Jul 22 10:12:14 PDT 1994 by kalsow

// File: TypeFP.m3
// Last modified on Thu Jan 26 14:21:55 PST 1995 by kalsow
//      modified on Tue May 25 14:38:54 PDT 1993 by muller

/**
 * The fingerprints and unique ids of types.
 *
 * <p>The "scc_id" field of an "Node" "n" is used as follows:
 * <pre>
 *   = NO_SCC =&gt; the "n" has never been visited
 *   &gt; NO_SCC =&gt; the "n" is done, its fingerprint is in "n.fp"
 *   &lt; NO_SCC =&gt; the "n" is on the active stack, stack[-n.scc_id] is its entry.
 * </pre>
 */
public final class TypeFP {
    private TypeFP() {}

    private static final long DFS_not_visited = 0;
    private static final long DFS_reset = DFS_not_visited + 1;
    private static final long NO_SCC = TypeRep.NO_SCC;

    // Port: Node = Type.T.

    public static final class Info {
        public Type.T node;
        public M3.FPInfo info = new M3.FPInfo();
        public long dfs_id;
    }

    // Port: IntList = REF ARRAY OF INTEGER is long[];
    // TypeList = REF ARRAY OF Type.T is Type.T[].

    private static long n_reps = 0;
    private static Type.T[] reps = null;
    private static long[] hash_table = null;
    private static Info[] stack;
    private static long tos = NO_SCC + 1;
    private static long dfs_cnt = DFS_reset;
    private static long next_scc = NO_SCC + 1;
    private static M3Buf.T mbuf = null;
    private static boolean busy = false;

    /** Port: NEW (REF ARRAY OF Info, n); the records are fresh. */
    private static Info[] newInfos(int n) {
        Info[] a = new Info[n];
        for (int i = 0; i < n; i++) {
            a[i] = new Info();
        }
        return a;
    }

    //---------------------------------------------------------------------------

    public static void initialize() {
        stack = newInfos(100);
        mbuf = M3Buf.New();
        reps = new Type.T[256];
        expandHash();
    }

    public static void reset() {
        Type.T t;
        // remove the reps and reinstall them as needed in the next compilation
        for (long i = 0; i <= n_reps - 1; i++) {
            t = reps[(int) i];
            t.rep_id = TypeRep.NO_UID;
            t.scc_id = TypeRep.NO_SCC;
            reps[(int) i] = null;
        }
        // clear the hash table
        for (int i = 0; i <= hash_table.length - 1; i++) {
            hash_table[i] = TypeRep.NO_UID;
        }
        n_reps = 0;
    }

    //-------------------------------------------------------- FP computation ---

    /** Computes and returns 't's fingerprint, and sets 't.fp' and 't.uid'. */
    public static Fingerprint.T fromType(Type.T t) {
        Type.T n;
        AssertError.check(!busy, "TypeFP");
        busy = true;
        if ((t == null)) {
            // Sic: busy stays TRUE.
            return M3FP.Zero;
        }
        n = Type.check(t);
        n = getRep(n);
        if ((n.uid == TypeRep.NO_UID)) {
            dfs_cnt = DFS_reset;
            visit_SCC(n);
        }
        if ((n != t)) {
            t.fp = n.fp;
            t.uid = n.uid;
        }
        busy = false;
        return n.fp;
    }

    private static long visit_SCC(Type.T n) {
        // Find the strongly connected component containing 'n' and
        // compute the fingerprints of its elements
        long here, min, x;
        Type.T ch;
        // push 'n' on the stack
        here = tos;
        tos++;
        if ((tos > stack.length)) {
            expandStack();
        }
        // Port: z is the element object stack[here]; ExpandStack in a
        // recursive call shares the element objects with the new array (see
        // there), and z is only read after such a call, so the result is the
        // same as with the original's binding to the old array.
        Info z = stack[(int) here];
        z.node = n;
        n.scc_id = -here;

        // mark 'n' as visited
        dfs_cnt++;
        z.dfs_id = dfs_cnt;
        min = dfs_cnt;

        // get 'n's tag and list of children
        z.info.tag = null;
        z.info.buf = mbuf;
        n.fprint(z.info);
        if ((z.info.tag == null)) {
            z.info.tag = M3Buf.toText(z.info.buf);
        }
        z.info.buf = null;

        // visit each of 'n's children
        for (long i = 0; i <= z.info.n_nodes - 1; i++) {
            if ((z.info.n_nodes <= z.info.nodes.length)) {
                ch = Type.check(z.info.nodes[(int) i]);
            } else {
                ch = Type.check(z.info.others[(int) i]);
            }
            ch = getRep(ch);
            x = ch.scc_id;
            if ((x == NO_SCC)) {
                // we've never visited 'ch'
                min = Math.min(min, visit_SCC(ch));
            } else if ((x < NO_SCC)) {
                // 'ch' is active
                min = Math.min(min, stack[(int) -x].dfs_id);
            //
            // ELSE (x > 0)
            //   => we've already computed the full fp of 'ch', therefore it's not
            //      part of the strongly connected component containing 'n'.
            //
            }
        }

        if ((min == z.dfs_id)) {
            // stack[tos-1 .. here] is a new strongly connected component
            // Port: SUBARRAY (stack^, here, tos-here) is a new array of the
            // same element objects; the callee writes only their fields.
            finish_SCC(Arrays.copyOfRange(stack, (int) here, (int) tos));
            tos = here;
        }
        // WITH z=stack[here]

        return min;
    }

    public static final class VisitInfo {
        public long dfs;
        public M3Buf.T tags;
        public Fingerprint.T fp;
        public boolean fp_used;
    }

    /**
     * Given the strongly connected component "x" where the fingerprints
     * of all other reachable nodes are computed, compute the fingerprints
     * of each node in "x".
     */
    private static void finish_SCC(Info[] x) {
        long min;
        VisitInfo zz = new VisitInfo();
        long n_missing = 0;
        Type.T n;
        if (x.length <= 0) {
            return;
        }

        // check to see if we've already computed the fingerprints for
        // this component
        for (int i = 0; i <= x.length - 1; i++) {
            if ((x[i].node.uid == TypeRep.NO_UID)) {
                n_missing++;
            }
        }

        if ((n_missing > 0)) {
            if ((Host.vs_debug)) {
                dumpHeader();
            }

            if ((x.length == 1) && (x[0].info.n_nodes == 0)) {
                // a simple leaf node
                n = x[0].node;
                n.fp = fromText(x[0].info.tag);
                n.uid = M3FP.toInt(n.fp);
                x[0].dfs_id = -1;
            } else {
                // tag each node with its index in the component
                for (int i = 0; i <= x.length - 1; i++) {
                    x[i].node.scc_id = -i;
                }

                // find the "least" element
                min = 0;
                for (int i = 1; i <= x.length - 1; i++) {
                    if (compareInfo(x, i, min) < 0) {
                        min = i;
                    }
                }

                // reset the DFS tags
                for (int i = 0; i <= x.length - 1; i++) {
                    x[i].dfs_id = DFS_not_visited;
                }

                // compute this component's fingerprint
                zz.dfs = DFS_reset;
                zz.tags = mbuf;
                zz.fp = M3FP.Zero;
                zz.fp_used = false;
                visit(zz, x, min);
                if ((zz.fp_used)) {
                    zz.fp = fromPair(fromBuf(zz.tags), zz.fp);
                } else {
                    zz.fp = fromBuf(zz.tags);
                }

                // compute the fingerprint of each element of the component
                for (int i = 0; i <= x.length - 1; i++) {
                    Info z = x[i];
                    z.node.fp = M3FP.extendByInt(zz.fp, (int) z.dfs_id);
                    z.node.uid = M3FP.toInt(z.node.fp);
                }
            }

            // dump the final fingerprints
            if ((Host.vs_debug)) {
                for (int i = 0; i <= x.length - 1; i++) {
                    dumpFP(x[i].dfs_id, null, x[i].node.fp);
                }
            }

        } // IF n_missing > 0

        // set the scc_id's and reset the stack nodes
        for (int i = 0; i <= x.length - 1; i++) {
            Info z = x[i];
            for (long j = 0; j <= Math.min(z.info.n_nodes, z.info.nodes.length - 1); j++) {
                z.info.nodes[(int) j] = null;
            }
            z.node.scc_id = next_scc;
            z.node = null;
            z.info.tag = null;
            z.info.buf = null;
            z.info.others = null;
            z.info.n_nodes = 0;
            z.dfs_id = DFS_not_visited;
        }
        next_scc++;
    }

    private static void visit(VisitInfo zz, Info[] x, long n) {
        Type.T ch;
        Info z = x[(int) n];
        if ((z.dfs_id != DFS_not_visited)) {
            M3Buf.putChar(zz.tags, '|');
            M3Buf.putInt(zz.tags, z.dfs_id);
            return;
        }

        // this is the first visit to the node 'z'
        // mark 'z' and add its tag
        z.dfs_id = zz.dfs;
        zz.dfs++;
        M3Buf.putChar(zz.tags, '|');
        M3Buf.putText(zz.tags, z.info.tag);

        // visit each of its children
        for (long i = 0; i <= z.info.n_nodes - 1; i++) {
            if ((z.info.n_nodes <= z.info.nodes.length)) {
                ch = z.info.nodes[(int) i];
            } else {
                ch = z.info.others[(int) i];
            }
            ch = getRep(ch);
            if ((ch.scc_id > NO_SCC)) {
                // child 'i' outside the component
                M3Buf.putChar(zz.tags, '|');
                M3Buf.putChar(zz.tags, '*');
                zz.fp = fromPair(zz.fp, ch.fp);
                zz.fp_used = true;
            } else {
                // child 'i' inside the component
                visit(zz, x, -ch.scc_id);
            }
        }
    }

    /**
     * Determine if "x[na]" is less than or greater than "x[nb]".  Note that
     * an order on the nodes is not defined by the language, but an arbitrary
     * total order that is constant for the duration of "Finish_SCC"
     * will do.  In fact, the order defined by this procedure changes
     * from one call of "Finish_SCC" to the next.
     */
    private static int compareInfo(Info[] x, long na, long nb) {
        long z;
        Type.T ch_a, ch_b;
        Info a = x[(int) na];
        Info b = x[(int) nb];
        // first, use the # of children
        if ((a.info.n_nodes < b.info.n_nodes)) {
            return -1;
        }
        if ((a.info.n_nodes > b.info.n_nodes)) {
            return +1;
        }

        // next, use the tags
        z = textCompare(a.info.tag, b.info.tag);
        if ((z < 0)) {
            return -1;
        }
        if ((z > 0)) {
            return +1;
        }

        // finally, check the children
        for (long i = 0; i <= a.info.n_nodes - 1; i++) {
            if ((a.info.n_nodes <= a.info.nodes.length)) {
                ch_a = a.info.nodes[(int) i];
                ch_b = b.info.nodes[(int) i];
            } else {
                ch_a = a.info.others[(int) i];
                ch_b = b.info.others[(int) i];
            }
            ch_a = getRep(ch_a);
            ch_b = getRep(ch_b);
            z = compareNode(x, ch_a, ch_b);
            if ((z != 0)) {
                return (int) z;
            }
        }
        return 0;
        // WITH
    }

    /**
     * Port: Text.Compare (a, b): -1, 0 or +1 by the order of the
     * characters [0..255], a proper prefix first.
     */
    private static int textCompare(String a, String b) {
        int c = a.compareTo(b);
        return (c < 0) ? -1 : (c > 0) ? +1 : 0;
    }

    private static int compareNode(Info[] x, Type.T a, Type.T b) {
        long xa, xb;
        if ((a == b)) {
            return 0;
        }
        if ((a == null)) {
            return -1;
        }
        if ((b == null)) {
            return +1;
        }

        xa = a.scc_id;
        xb = b.scc_id;

        if ((xa > NO_SCC) && (xb > NO_SCC)) {
            // both nodes have already have fingerprints
            Fingerprint.T fp_a = a.fp;
            Fingerprint.T fp_b = b.fp;
            for (int i = 0; i <= 7; i++) {
                if (fp_a.byteAt(i) < fp_b.byteAt(i)) {
                    return -1;
                }
                if (fp_a.byteAt(i) > fp_b.byteAt(i)) {
                    return +1;
                }
            }
            return 0;
        }

        if (Type.isEqual(a, b, null)) {
            return 0;
        }

        if ((xa > NO_SCC)) {
            return +1;
        }
        if ((xb > NO_SCC)) {
            return -1;
        }
        return compareInfo(x, -xa, -xb);
    }

    /**
     * Port: the elements of the old array are copied to the new one as
     * references; the original copies the records. No element of the old
     * array is written after an expansion, so the results are the same.
     */
    private static void expandStack() {
        int n = stack.length;
        Info[] new_ = newInfos(2 * n);
        System.arraycopy(stack, 0, new_, 0, n);
        stack = new_;
    }

    //---------------------------------------------------------- FP utilities ---

    public static Fingerprint.T fromText(String txt) {
        Fingerprint.T fp = M3FP.fromText(txt);
        if ((Host.vs_debug)) {
            dumpFP(-1, txt, fp);
        }
        return fp;
    }

    public static Fingerprint.T fromPair(Fingerprint.T a, Fingerprint.T b) {
        Fingerprint.T fp = M3FP.combine(a, b);
        if ((Host.vs_debug)) {
            dumpOne(b);
        }
        return fp;
    }

    public static Fingerprint.T fromBuf(M3Buf.T buf) {
        if ((Host.vs_debug)) {
            return fromText(M3Buf.toText(buf));
        } else {
            return M3Buf.toFP(buf); // avoids the TEXT allocation
        }
    }

    //----------------------------------------------------------- unique reps ---

    private static Type.T getRep(Type.T t) {
        long uid;
        long hash;
        Type.T r;
        if ((t == null)) {
            t = ErrType.T;
        }

        if ((t.rep_id != TypeRep.NO_UID)) {
            // this type has a uid, but it might be from a old compilation...
            if ((t.rep_id < n_reps)) {
                r = reps[(int) t.rep_id];
                if ((r == t) || Type.isEqual(r, t, null)) {
                    return r;
                }
            }
            t.rep_id = TypeRep.NO_UID;
        }

        // search the hash table for an existing type that's equal
        hash = Math.floorMod(t.info.hash, (long) hash_table.length);
        while (true) {
            uid = hash_table[(int) hash];
            if ((uid == TypeRep.NO_UID)) {
                // empty bucket
                break;
            }
            r = reps[(int) uid];
            if ((r.info.hash == t.info.hash) && Type.isEqual(r, t, null)) {
                // we found a match
                t.rep_id = uid;
                return r;
            }
            hash++;
            if ((hash > hash_table.length - 1)) {
                hash = 0;
            }
        }

        // a new type!
        uid = n_reps;
        t.rep_id = uid;

        // add a new type to the rep table
        if ((uid > reps.length - 1)) {
            expandReps();
        }
        reps[(int) uid] = t;
        n_reps++;

        // update the hash table
        hash_table[(int) hash] = uid;
        if ((n_reps + n_reps > hash_table.length)) {
            expandHash();
        }
        return t;
    }

    private static void expandReps() {
        int n = reps.length;
        Type.T[] new_ = new Type.T[2 * n];
        // Port: the assignment SUBARRAY (new^, 0, n_reps) := reps^ checks
        // that the shapes agree (n_reps = NUMBER (reps^), which GetRep
        // ensures).
        if (n_reps != n) {
            throw new AssertError("TypeFP");
        }
        System.arraycopy(reps, 0, new_, 0, (int) n_reps);
        reps = new_;
    }

    private static void expandHash() {
        long n, hash;
        long[] new_;
        if ((hash_table == null)) {
            n = 512;
        } else {
            n = 2 * hash_table.length;
        }
        new_ = new long[(int) n];

        // initialize the new table to all empty slots
        for (int i = 0; i <= new_.length - 1; i++) {
            new_[i] = TypeRep.NO_UID;
        }

        // re-insert each type in the new table
        for (long i = 0; i <= n_reps - 1; i++) {
            hash = Math.floorMod(reps[(int) i].info.hash, n);
            while ((new_[(int) hash] != TypeRep.NO_UID)) {
                hash++;
                if ((hash >= n)) {
                    hash = 0;
                }
            }
            new_[(int) hash] = i;
        }

        hash_table = new_;
    }

    //------------------------------------------------------------- debugging ---

    private static M3Buf.T wr = null;

    private static void dumpHeader() {
        Host.env.note_comment("---------------------------------------");
    }

    private static void dumpFP(long i, String tag, Fingerprint.T fp) {
        // <*FATAL Convert.Failed*>
        int len;
        char[] buf = new char[M3FP.CharBufLength];
        long fp_x = M3FP.toInt(fp);
        if ((wr == null)) {
            wr = M3Buf.New();
        }

        if ((tag != null)) {
            Host.env.note_comment(tag);
        }

        try {
            if ((i >= 0)) {
                len = Convert.fromInt(buf, i);
                M3Buf.putSub(wr, buf, 0, len);
                M3Buf.putText(wr, ": ");
            }

            M3Buf.putText(wr, "  FP ==> 16_");
            M3FP.toChars(fp, buf);
            M3Buf.putSub(wr, buf);

            M3Buf.putText(wr, " => 16_");
            len = Convert.fromUnsigned(buf, fp_x, 16, false);
            M3Buf.putSub(wr, buf, 0, len);

            M3Buf.putText(wr, " = ");
            len = Convert.fromInt(buf, fp_x);
            M3Buf.putSub(wr, buf, 0, len);
        } catch (Convert.Failed e) {
            throw new IllegalStateException(e);
        }

        Host.env.note_comment(M3Buf.toText(wr));
    }

    private static void dumpOne(Fingerprint.T fp) {
        char[] buf = new char[M3FP.CharBufLength];
        if ((wr == null)) {
            wr = M3Buf.New();
        }
        M3Buf.putText(wr, "  Combine ==> 16_");
        M3FP.toChars(fp, buf);
        M3Buf.putSub(wr, buf);
        Host.env.note_comment(M3Buf.toText(wr));
    }
}
