// Java port of CM3's m3-sys/m3front/src/misc/Coverage.i3 and Coverage.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.Arrays;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.TInt;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: Coverage.i3
// Last modified on Fri Aug 21 17:28:49 PDT 1992 by kalsow

// File: Coverage.m3
// Last modified on Tue Jan  3 14:45:34 PST 1995 by kalsow
//      modified on Wed Mar 13 01:32:19 1991 by muller

/**
 * Coverage generates the tables and code necessary to instrument
 * a module for coverage analysis.
 */
public final class Coverage {
    private Coverage() {}

    public static final class ProcHead {
        public ProcHead next;
        public Value.T proc;
        public String name;
        public long offset;
    }

    private static final long MaxLine = 100000;

    private static String Header;
    private static String Trailer;

    public enum LineSeen { no, yes, generated }

    private static long minLine = Long.MAX_VALUE;
    private static long maxLine = Long.MIN_VALUE;
    private static LineSeen[] used = null;
    private static ProcHead procs = null;
    private static long nProcs = 0;
    private static M3CG.Var tbl = null;
    private static long lines_offset;

    //***** phase 0 - during Binding *****

    /** note that the current line (Scanner.offset) will be counted */
    public static void noteLine() {
        long[] line = new long[1];
        String[] file = new String[1];
        if ((!Host.coverage)) {
            return;
        }
        Scanner.here(file, line);
        if ((line[0] > MaxLine)) {
            return;
        }
        if (!file[0].equals(Host.filename)) {
            return;
        }
        minLine = Math.min(minLine, line[0]);
        maxLine = Math.max(maxLine, line[0]);
        while ((used == null) || (used.length - 1 < line[0])) {
            expand();
        }
        used[(int) line[0]] = LineSeen.yes;
    }

    private static void expand() {
        if ((used == null)) {
            used = new LineSeen[100];
            // Port: the new elements are zero (LineSeen.no) as in CM3.
            Arrays.fill(used, LineSeen.no);
        } else {
            LineSeen[] new_ = new LineSeen[2 * used.length];
            // Port: the new elements are zero (LineSeen.no) as in CM3.
            Arrays.fill(new_, LineSeen.no);
            for (int i = 0; i <= used.length - 1; i++) {
                new_[i] = used[i];
            }
            used = new_;
        }
    }

    /** note that procedure v will be counted */
    public static void noteProcedure(Value.T v) {
        if ((!Host.coverage)) {
            return;
        }
        ProcHead p = new ProcHead();
        p.next = procs;
        p.proc = v;
        p.offset = -1;
        procs = p;
        nProcs++;
    }

    //***** phase 1 - before code generation *****

    /** produce the C declarations necessary to hold the profile information */
    public static void generateTables() {
        // Sic: with no line noted, maxLine - minLine overflows silently
        // (as in CM3) and nLines is 2.
        long nLines = Math.max(0, maxLine - minLine) + 1;
        long l_header = tLen(Header);
        long l_fname = tLen(Host.filename);
        long l_trailer = tLen(Trailer);
        long size;
        ProcHead p;
        long i, len;
        if ((!Host.coverage)) {
            return;
        }

        // compute the size of the coverrage tables
        size = 0;
        size += l_header * Target.Char.size;        // header
        size += M3.target.Integer.size;             // timestamp
        size += M3.target.Integer.size;             // fileLen
        size += l_fname * Target.Char.size;         // file
        size += M3.target.Integer.size;             // firstLine
        size += M3.target.Integer.size;             // nLines
        size += nLines * M3.target.Integer.size;    // lines
        size += M3.target.Integer.size;             // nProcs
        p = procs;
        i = 0;
        while ((p != null)) {
            if ((p.proc != null)) {
                p.name = M3ID.toText(M3.ids, Value.cName(p.proc));
                len = tLen(p.name);
                size += M3.target.Integer.size;     // len[p]
                size += len * Target.Char.size;     // pname[p]
                size += M3.target.Integer.size;     // cnt[p]
                i++;
            }
            p = p.next;
        }
        size += l_trailer * Target.Char.size;       // trailer

        // allocate the variable
        tbl = CG.declare_global(M3ID.NoID, size, M3.target.Address.align,
                                CGType.Struct, 0, /* exported := */ false, /* init := */ true);

        // initialize the coverage tables
        CG.begin_init(tbl);
        size = 0;

        CG.init_chars(size, Header, false);
        size += l_header * Target.Char.size;        // header

        // CG.Init_int (size, Target.Integer.size, TInt.Zero, FALSE);
        size += M3.target.Integer.size;             // timestamp

        CG.init_intt(size, M3.target.Integer.size, Host.filename.length(), false);
        size += M3.target.Integer.size;             // fileLen

        CG.init_chars(size, Host.filename, false);
        size += l_fname * Target.Char.size;         // file

        CG.init_intt(size, M3.target.Integer.size, minLine, false);
        size += M3.target.Integer.size;             // firstLine

        CG.init_intt(size, M3.target.Integer.size, nLines, false);
        size += M3.target.Integer.size;             // nLines

        lines_offset = size;
        for (long x = 0; x <= nLines - 1; x++) {
            if ((used != null) && (used[(int) (x + minLine)] != LineSeen.no)) {
                len = 0;
            } else {
                len = -1;
            }
            CG.init_intt(size, M3.target.Integer.size, len, false);
            size += M3.target.Integer.size;         // lines[x]
        }

        CG.init_intt(size, M3.target.Integer.size, i, false);
        size += M3.target.Integer.size;             // nProcs

        p = procs;
        while ((p != null)) {
            if ((p.proc != null)) {
                len = tLen(p.name);

                CG.init_intt(size, M3.target.Integer.size, p.name.length(), false);
                size += M3.target.Integer.size;     // len[p]

                CG.init_chars(size, p.name, false);
                size += len * Target.Char.size;     // pname[p]

                // CG.Init_int (size, Target.Integer.size, 0, FALSE);
                p.offset = size;
                size += M3.target.Integer.size;     // cnt[p]
            }
            p = p.next;
        }

        CG.init_chars(size, Trailer, false);
        size += l_trailer * Target.Char.size;       // trailer

        CG.end_init(tbl);
    }

    private static long tLen(String t) {
        long Grain = Math.floorDiv(M3.target.Integer.size, Target.Char.size);
        return Math.floorDiv(t.length() + Grain - 1, Grain) * Grain;
    }

    //***** phase 2 - during code generation *****

    /**
     * generate code to increment the counter associated with the current
     * line (Scanner.offset).  For any particular line, NoteLine must be
     * called prior to CountLine.
     */
    public static void countLine() {
        long[] line = new long[1];
        long offset;
        String[] file = new String[1];
        if ((!Host.coverage)) {
            return;
        }
        Scanner.here(file, line);
        if ((line[0] > MaxLine)) {
            return;
        }
        if (!file[0].equals(Host.filename)) {
            return;
        }
        if (used[(int) line[0]] == LineSeen.generated) {
            return;
        }
        AssertError.check(tbl != null, "Coverage");
        offset = lines_offset + (line[0] - minLine) * M3.target.Integer.size;
        CG.load_int(M3.target.Integer.cg_type, tbl, offset);
        CG.load_integer(M3.target.Integer.cg_type, TInt.One);
        CG.add(M3.target.Word.cg_type);
        CG.store_int(M3.target.Integer.cg_type, tbl, offset);
        used[(int) line[0]] = LineSeen.generated;
    }

    /**
     * generate code to increment the counter associated with procedure v.
     * For any procedure v, NoteProcedure must be called prior to
     * CountProcedure.
     */
    public static void countProcedure(Value.T v) {
        ProcHead p;
        if ((!Host.coverage)) {
            return;
        }
        AssertError.check(tbl != null, "Coverage");

        // find the corresponding Proc
        p = procs;
        while ((p != null)) {
            if ((p.proc == v)) {
                break;
            }
            p = p.next;
        }
        AssertError.check(p != null, "Coverage");

        CG.load_int(M3.target.Integer.cg_type, tbl, p.offset);
        CG.load_integer(M3.target.Integer.cg_type, TInt.One);
        CG.add(M3.target.Word.cg_type);
        CG.store_int(M3.target.Integer.cg_type, tbl, p.offset);
    }

    public static void reset() {
        minLine = Long.MAX_VALUE;
        maxLine = Long.MIN_VALUE;
        used = null;
        procs = null;
        nProcs = 0;
        tbl = null;
    }

    static {
        // This initialization is designed so that the special header and trailer
        // strings recognized by the coverage analyzer don't appear as literals
        // in this module.
        Header = "<<";
        Header = Header + "<<Coverage 1.0";
        Trailer = "Coverage 1.0>>";
        Trailer = Trailer + ">>";
    }
}
