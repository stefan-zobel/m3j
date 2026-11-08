// Java port of CM3's m3-sys/m3front/src/misc/Error.i3 and Error.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3ID;
import m3j.util.AssertError;

// File: Error.i3
// Last modified on Fri Jun 24 09:15:20 PDT 1994 by kalsow
//      modified on Fri Mar 22 08:29:20 1991 by muller

// File: Error.m3
// Last modified on Tue Jan 31 08:07:00 PST 1995 by kalsow
//      modified on Fri Mar 22 08:29:36 1991 by muller

/**
 * Error levels:
 *
 * <pre>
 *   0 - informational messages
 *   1 - "fussy" warnings  (i.e. unused formals, incomplete CASE...)
 *   2 - warnings
 *   3 - errors
 * </pre>
 */
public final class Error {
    private Error() {}

    // Port: Level = [0..3] is long; FIRST (Level) = 0, LAST (Level) = 3.
    private static final long Level_First = 0;
    private static final long Level_Last = 3;

    public static final class IgnoreCell {
        public long offs;
        public IgnoreCell next;
    }

    private static final String[] Labels = {
        "info: ",     // informational messages
        "warning: ",  // "fussy" warnings
        "warning: ",  // warnings
        ""            // errors
    };

    private static final long[] count = {0, 0, 0, 0};
    private static M3Buf.T spare = null;
    private static IgnoreCell ignores = null;

    // Msg, Int, ID, QID, Txt are all implicitly level 3.

    public static void msg(String msg) {
        M3Buf.T wr = header();
        out(wr, msg);
        trailer(wr);
    }

    public static void int_(long n, String msg) {
        M3Buf.T wr = header();
        out(wr, msg);
        out(wr, " (");
        out(wr, Long.toString(n));
        out(wr, ")");
        trailer(wr);
    }

    public static void ID(int id, String msg) {
        M3Buf.T wr = header();
        out(wr, msg);
        out(wr, " (");
        outS(wr, id);
        out(wr, ")");
        trailer(wr);
    }

    public static void txt(String id, String msg) {
        M3Buf.T wr = header();
        out(wr, msg);
        out(wr, ": ");
        out(wr, id);
        trailer(wr);
    }

    public static void QID(M3.QID q, String msg) {
        M3Buf.T wr = header();
        out(wr, msg);
        out(wr, " (");
        if ((q.module != M3ID.NoID)) {
            outS(wr, q.module);
            out(wr, ".");
        }
        outS(wr, q.item);
        out(wr, ")");
        trailer(wr);
    }

    // Info, InfoInt are both implicitly level 0.

    public static void info(String msg) {
        if (toss(Level_First)) {
            return;
        }
        {
            M3Buf.T wr = header(Level_First);
            out(wr, msg);
            trailer(wr);
        }
    }

    public static void infoInt(long n, String msg) {
        if (toss(Level_First)) {
            return;
        }
        {
            M3Buf.T wr = header(Level_First);
            out(wr, msg);
            out(wr, " (");
            out(wr, Long.toString(n));
            out(wr, ")");
            trailer(wr);
        }
    }

    // Despite the names Warn and WarnId, these can be any level.

    public static void warn(long level, String msg) {
        if (toss(level)) {
            return;
        }
        {
            M3Buf.T wr = header(level);
            out(wr, msg);
            trailer(wr);
        }
    }

    public static void warnID(long level, int id, String msg) {
        if (toss(level)) {
            return;
        }
        {
            M3Buf.T wr = header(level);
            out(wr, msg);
            out(wr, " (");
            outS(wr, id);
            out(wr, ")");
            trailer(wr);
        }
    }

    private static M3Buf.T header(long level) {
        M3Buf.T wr;
        if ((spare != null)) {
            wr = spare;
            spare = null;
        } else {
            wr = M3Buf.New();
        }
        level = Math.max(Level_First, Math.min(level, Level_Last));
        count[(int) level]++;
        out(wr, Labels[(int) level]);
        return wr;
    }

    private static M3Buf.T header() {
        return header(Long.MAX_VALUE);
    }

    private static void trailer(M3Buf.T wr) {
        long n = 0;
        String[] file = new String[1];
        long[] line = new long[1];
        Scanner.here(file, line);
        Host.env.report_error(file[0], line[0], M3Buf.toText(wr));
        spare = wr;
        if ((Host.errorDie >= 0)) {
            for (int i = 0; i < count.length; i++) {
                n += count[i];
            }
            if ((n >= Host.errorDie)) {
                AssertError.check(false, "Error");
            }
        }
    }

    private static void out(M3Buf.T wr, String t) {
        if ((t != null)) {
            M3Buf.putText(wr, t);
        }
    }

    private static void outS(M3Buf.T wr, int id) {
        M3ID.put(M3.ids, wr, id);
    }

    /**
     * Port: the VAR parameters nErrors and nWarnings are one-element
     * arrays.
     */
    public static void count(long[] nErrors, long[] nWarnings) {
        nErrors[0] = count[count.length - 1];
        nWarnings[0] = 0;
        for (int i = 0 + 1; i <= count.length - 1 - 1; i++) {
            nWarnings[0] += count[i];
        }
    }

    public static void ignoreWarning(long offset) {
        IgnoreCell i = new IgnoreCell();
        i.offs = offset;
        i.next = ignores;
        ignores = i;
    }

    private static boolean toss(long level) {
        IgnoreCell i;
        long here;
        if ((level < Host.warnings)) {
            return true;
        }
        here = Scanner.offset;
        i = ignores;
        while ((i != null)) {
            if ((i.offs == here)) {
                return true;
            }
            i = i.next;
        }
        return false;
    }

    public static void reset() {
        ignores = null;
        for (int i = 0; i < count.length; i++) {
            count[i] = 0;
        }
    }
}
