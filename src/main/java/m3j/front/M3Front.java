// Java port of CM3's m3-sys/m3front/src/misc/M3Front.i3 and M3Front.m3.
// Original: Copyright (C) 1994, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.math.BigDecimal;
import java.math.RoundingMode;
import m3j.middle.M3ID;

// File: M3Front.i3
// Last modified on Thu Apr 11 1996 by kalsow@cmass.com

// File: M3Front.m3
// Last modified on Tue Dec  6 08:16:20 PST 1994 by kalsow
//      modified on Sun Jan 21 06:56:46 1990 by muller

/**
 * This module defines the front-end interface of the Modula-3
 * compiler.  The compiler is a monitor -- only one compilation
 * can be active at a time.
 *
 * <p>The recognized options are:
 *
 * <pre>
 *   -v             verbose mode
 *   -g             generate debugging information
 *   -S             don't generate version stamps
 *   -wX            print warnings at level "X" and above
 *   -w             don't print warnings  == -w99
 *   -Z             generate line-based profiling.
 *   -En            die on the "n-th" error message.
 *   -NoChecks      disable all runtime checks
 *   -NoAsserts     disable &lt;*ASSERT*&gt; checks
 *   -NoNarrowChk   disable narrow checks
 *   -NoRangeChk    disable subscript and range checks
 *   -NoReturnChk   disable checks for functions that fail to return values
 *   -NoCaseChk     disable checks for unhandled CASE selectors
 *   -NoTypecaseChk disable checks for unhandled TYPECASE selectors
 *   -NoNilChk      disable explicit NIL checks
 *   -NoRaisesChk   disable checks for unhandled exceptions
 *   -InitFloats    initialize all floating point values to zero
 *   -vsdebug       generate a trace of the fingerprint computations
 *   -builtins      "compile" and emit the builtin symbols
 *   -times         print the elapsed time profile
 *   -load_map      generate the load map comment in the output
 *   -No_load_map   don't generate the load map comment in the output
 *   -IncGC         generate software read barrier for incremental GC
 *   -GenGC         generate software write barrier for generational GC
 *   -widechar16    WIDECHAR has 16-bit range
 *   -widecharuni   WIDECHAR has Unicode range
 * </pre>
 *
 * <p>Port: the monitor's mutex mu is the class lock of M3Front (the
 * public procedures are synchronized). The module body runs in the static
 * initializer, that is, at the first call of ParseImports or Compile; the
 * driver must set the m3middle state in {@link M3} before that.
 */
public final class M3Front {
    private M3Front() {}

    public static final class IDList {
        public int interface_;
        public M3Front.IDList next;
    }

    // Port: SourceFile = M3Compiler.SourceFile.

    // Port: Environment = M3Compiler.Environment.

    // Port: VAR mu: MUTEX := NEW (MUTEX) is the class lock.
    private static Module.T builtins = null;

    /**
     * Port, addition for the JVM back end: if set, it is called with the
     * unit after a compilation without errors, while the state of the
     * front end is still that of the unit (the next compilation resets
     * it).
     */
    public static java.util.function.Consumer<Module.T> backEnd = null;

    /** Port, addition: the unit compiled without errors, for backEnd. */
    private static Module.T compiled = null;

    /** Returns the names of the interfaces directly imported by 'input'. */
    public static synchronized M3Front.IDList parseImports(M3Compiler.SourceFile input, M3Compiler.Environment env) {
        IDList ids = null;
        // make the arguments globally visible
        Host.env = env;
        Host.source = input.contents;
        Host.filename = input.name;

        if ((builtins == null)) {
            initialize();
        }

        Scanner.push(Host.filename, Host.source, /* is_main := */ true);
        ids = M3Header.parse();
        Scanner.pop();
        return ids;
    }

    /**
     * Reads and compiles a Modula-3 unit from "input".  Evironmental queries
     * and reports are made through "env".  The listing and diagnostic options
     * of the compiler are set by "options".  Returns "TRUE" iff the compilation
     * succeeded with no errors.  It is the caller's responsibility to
     * initialize the Target interface prior to calling Compile.
     */
    public static synchronized boolean compile(M3Compiler.SourceFile input, M3Compiler.Environment env, String[] options) {
        boolean ok;
        double start;
        start = timeNow();

        // make the arguments globally visible
        Host.env = env;
        Host.source = input.contents;
        Host.filename = input.name;

        if (!Host.initialize(options)) {
            return false;
        }

        if (!Host.stack_walker) {
            // command line override...
            M3.target.Has_stack_walker = false;
        }

        if ((builtins == null)) {
            initialize();
        }

        compiled = null;
        reset();
        doCompile();
        ok = finalize_();
        if (ok && backEnd != null && compiled != null) {
            backEnd.accept(compiled);
        }

        if ((Host.report_stats)) {
            dumpStats(start, timeNow());
        }
        return ok;
    }

    /**
     * Port: Time.Now (); only differences of its results are used, so a
     * monotonic clock in seconds serves.
     */
    private static double timeNow() {
        return System.nanoTime() / 1.0e9;
    }

    private static void initialize() {
        // this list is ordered!
        Type.initialize();
        TypeFP.initialize();

        Scanner.push("M3_BUILTIN", null, /* is_main := */ Host.emitBuiltins);
        builtins = Module.newDefn("M3_BUILTIN", true, Scope.Initial);
        BuiltinTypes.initialize();
        BuiltinOps.initialize();
        Scanner.pop();

        Scanner.push("Word.i3", null, /* is_main := */ Host.emitBuiltins);
        WordModule.initialize("Word");
        Scanner.pop();

        Scanner.push("Long.i3", null, /* is_main := */ Host.emitBuiltins);
        LongModule.initialize("Long");
        Scanner.pop();

        Scanner.push("AtomicAddress.i3", null, /* is_main := */ Host.emitBuiltins);
        AtomicAddrModule.initialize("AtomicAddress");
        Scanner.pop();

        Scanner.push("AtomicBoolean.i3", null, /* is_main := */ Host.emitBuiltins);
        AtomicBoolModule.initialize("AtomicBoolean");
        Scanner.pop();

        Scanner.push("AtomicChar.i3", null, /* is_main := */ Host.emitBuiltins);
        AtomicCharrModule.initialize("AtomicChar");
        Scanner.pop();

        Scanner.push("AtomicInteger.i3", null, /* is_main := */ Host.emitBuiltins);
        AtomicIntModule.initialize("AtomicInteger");
        Scanner.pop();

        Scanner.push("AtomicLongint.i3", null, /* is_main := */ Host.emitBuiltins);
        AtomicLIntModule.initialize("AtomicLongint");
        Scanner.pop();

        Scanner.push("AtomicRefany.i3", null, /* is_main := */ Host.emitBuiltins);
        AtomicReffModule.initialize("AtomicRefany");
        Scanner.pop();

        Scanner.push("AtomicWideChar.i3", null, /* is_main := */ Host.emitBuiltins);
        AtomicWCharrModule.initialize("AtomicWideChar");
        Scanner.pop();

        Scanner.push("Compiler.i3", null, /* is_main := */ Host.emitBuiltins);
        InfoModule.initialize();
        Scanner.pop();
    }

    private static void reset() {
        // this list is ordered!
        M3String.reset();
        M3WString.reset();
        Scanner.reset();
        Scope.reset();
        Coverage.reset();
        Error.reset();
        Marker.reset();
        ESet.reset();
        ProcBody.reset();
        RunTyme.reset();
        TipeDesc.reset();
        Tracer.reset();
        Type.reset();
        TypeFP.reset();
        Brand.reset();
        Value.reset();
        Module.reset();
        Ident.reset();
        TextExpr.reset();
        Procedure.reset();
        Variable.reset();
        SetExpr.init();
        InfoModule.reset();
    }


    private static void doCompile() {
        Module.T m;
        M3.CheckState cs = M3.OuterCheckState.copy();
        int m_name, filename;
        Scanner.push(Host.filename, Host.source, /* is_main := */ true);

        startPhase("initializing builtins");
        checkBuiltins();

        startPhase("parsing");
        m = Module.parse();

        // check that the module name matches the file name
        m_name = Module.name(m);
        filename = M3ID.add(M3.ids, pathnameLastBase(Host.filename));
        if ((m_name != filename)) {
            // This can trigger due to forward/backward slash confusion,
            // so do a looser check here.
            long LastForwardSlash = Host.filename.lastIndexOf('/');
            long LastBackwardSlash = Host.filename.lastIndexOf('\\');
            long LastSlash = Math.max(LastForwardSlash, LastBackwardSlash);
            long Dot;
            String Name1;
            String Name2;
            if (LastSlash != -1) {
                Dot = Host.filename.lastIndexOf('.');
                if (Dot < LastSlash) {
                    Dot = Host.filename.length();
                }
                Name1 = textSub(Host.filename, LastSlash + 1, Dot - LastSlash - 1);
                Name2 = M3ID.toText(M3.ids, m_name);
                if (Name1.equals(Name2)) {
                    m_name = filename;
                }
            }
        }
        if ((m_name != filename)) {
            Error.warn(2, "file name (" + pathnameLast(Host.filename)
                          + ") doesn't match module name ("
                          + M3ID.toText(M3.ids, m_name) + ")");
        }
        if (failed()) {
            return;
        }

        startPhase("type checking");
        Module.typeCheck(m, true, cs);
        if (failed()) {
            return;
        }

        startPhase("emitting code");
        CG.init();
        if (failed()) {
            return;
        }
        if ((Host.emitBuiltins)) {
            Module.makeCurrent(builtins);
            Module.makeCurrent(WordModule.M);
            Module.makeCurrent(LongModule.M);
            Module.makeCurrent(InfoModule.M);
            Module.compile(builtins);
            Module.compile(WordModule.M);
            Module.compile(LongModule.M);
            Module.compile(InfoModule.M);
        } else {
            Module.compile(m);
        }
        if (failed()) {
            return;
        }
        if (!Host.emitBuiltins) {
            compiled = m;
        }
    }

    /** Port: Text.Sub (t, start, length), which clips to the text. */
    private static String textSub(String t, long start, long length) {
        long n = t.length();
        if (start >= n) {
            return "";
        }
        return t.substring((int) start, (int) Math.min(n, start + length));
    }

    // Port: Pathname.Last and Pathname.LastBase follow libm3's
    // PathnameWin32, the implementation of the reference compiler.

    private static boolean isDirSepChar(char ch) {
        return (ch == '\\' || ch == '/');
    }

    /**
     * Port: PathnameWin32.NameSections; returns {baseLwb, baseUpb,
     * extUpb}.
     */
    private static long[] nameSections(String pn) {
        long baseLwb, baseUpb, extUpb;
        long pos;
        char ch;
        boolean extSepSeen = false;
        extUpb = pn.length();
        pos = extUpb;
        baseUpb = extUpb;
        for (;;) {
            if (pos > 0) {
                pos--;
                ch = pn.charAt((int) pos);
                if (isDirSepChar(ch) || ch == ':') {
                    baseLwb = pos + 1;
                    break;
                } else if (ch == '.') {
                    if (!extSepSeen) {
                        baseUpb = pos;
                        extSepSeen = true;
                    }
                }
            } else {
                baseLwb = 0;
                break;
            }
        }
        return new long[] {baseLwb, baseUpb, extUpb};
    }

    /** Port: PathnameWin32.Legal. */
    private static boolean legal(char c) {
        return '\001' <= c && c <= '\177'
            && c != '\\' && c != '/' && c != ':' && c != '?' && c != '*';
    }

    /**
     * Port: PathnameWin32.ParseRoot; returns -1 where it raises
     * Pathname.Invalid.
     */
    private static long parseRoot(String t) {
        int n = t.length();
        if (n == 0) {
            return 0;
        }
        char c = t.charAt(0);
        if (isDirSepChar(c)) {
            if (n == 1 || !isDirSepChar(t.charAt(1))) {
                return 1;
            }
            boolean seenName = false, seenShare = false;
            for (int i = 2; i <= n - 1; i++) {
                char cc = t.charAt(i);
                if (isDirSepChar(cc)) {
                    if (seenShare) {
                        if (seenName) {
                            return i + 1;
                        }
                        return -1;
                    }
                    seenShare = true;
                    seenName = false;
                } else {
                    if (!legal(cc)) {
                        return -1;
                    }
                    seenName = true;
                }
            }
            if (!seenShare || !seenName) {
                return -1;
            }
            return n;
        }
        if (('a' <= c && c <= 'z' || 'A' <= c && c <= 'Z')
            && n > 1 && t.charAt(1) == ':') {
            if (n > 2 && isDirSepChar(t.charAt(2))) {
                return 3;
            }
            return 2;
        }
        return 0;
    }

    /** Port: PathnameWin32.Last. */
    private static String pathnameLast(String pn) {
        long[] s = nameSections(pn);
        long baseLwb = s[0];
        long extUpb = s[2];
        long nRoot = parseRoot(pn);
        if (nRoot < 0) {
            return "";
        }
        if (baseLwb <= nRoot) {
            baseLwb = nRoot;
        }
        return textSub(pn, baseLwb, extUpb - baseLwb);
    }

    /** Port: PathnameWin32.LastBase. */
    private static String pathnameLastBase(String pn) {
        long[] s = nameSections(pn);
        return textSub(pn, s[0], s[1] - s[0]);
    }

    private static void checkBuiltins() {
        M3.CheckState cs = M3.OuterCheckState.copy();
        Value.typeCheck(builtins, cs);
        Value.typeCheck(WordModule.M, cs);
        Value.typeCheck(LongModule.M, cs);
        Value.typeCheck(InfoModule.M, cs);
    }

    private static void startPhase(String tag) {
        if ((Host.verbose)) {
            Host.env.report_error(null, 0, tag + "...");
        }
    }

    private static boolean failed() {
        long[] errs = new long[1], warns = new long[1];
        Error.count(errs, warns);
        return (errs[0] > 0);
    }

    /** Port: a TextWr.T is a StringBuilder. */
    private static void dumpStats(double start, double stop) {
        StringBuilder wr = new StringBuilder();
        double elapsed = Math.max(stop - start, 1.0e-6);
        double speed = ((double) Scanner.nLines) / elapsed;
        wr.append("  ");
        wr.append(Long.toString(Scanner.nLines));
        wr.append(" lines (");
        wr.append(Long.toString(Scanner.nPushed));
        wr.append(" files) scanned, ");
        wr.append(fmtFix(elapsed, 2));
        wr.append(" seconds, ");
        wr.append(fmtFix(speed, 1));
        wr.append(" lines / second.");
        Host.env.report_error(null, 0, wr.toString());
    }

    /**
     * Port: Fmt.LongReal (x, Fmt.Style.Fix, prec) for the positive finite
     * values of DumpStats: prec digits after the point, rounded to nearest
     * else even on the exact binary value.
     */
    private static String fmtFix(double x, int prec) {
        return new BigDecimal(x).setScale(prec, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** Port: named finalize_ so as not to hide Object.finalize. */
    private static boolean finalize_() {
        long[] errs = new long[1], warns = new long[1];
        StringBuilder wr;
        Scanner.pop();

        Error.count(errs, warns);
        if ((errs[0] + warns[0] > 0)) {
            wr = new StringBuilder();
            if ((errs[0] > 0)) {
                wr.append(Long.toString(errs[0]));
                wr.append(" error");
                if ((errs[0] > 1)) {
                    wr.append("s");
                }
            }
            if ((warns[0] > 0)) {
                if ((errs[0] > 0)) {
                    wr.append(" and ");
                }
                wr.append(Long.toString(warns[0]));
                wr.append(" warning");
                if ((warns[0] > 1)) {
                    wr.append("s");
                }
            }
            wr.append(" encountered");
            Host.env.report_error(null, 0, wr.toString());
        }

        return (errs[0] <= 0);
    }

    static {
        M3String.initialize();
        M3WString.initialize();
        Token.initialize();
        Scanner.initialize();
        Scope.initialize();
    }
}
