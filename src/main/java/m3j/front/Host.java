// Java port of CM3's m3-sys/m3front/src/misc/Host.i3 and Host.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.io.IOException;
import java.io.InputStream;

// File: Host.i3
// Last modified on Tue Jun 20 15:21:32 PDT 1995 by kalsow
// Last modified on Thu Jun 15 11:49:31 PDT 1995 by ericv
//      modified on Sat May 12 07:05:52 1990 by muller

// File: Host.m3
// Last modified on Thu Jun 29 13:35:46 PDT 1995 by kalsow
//      modified on Thu Jun 15 11:50:20 PDT 1995 by ericv
//      modified on Tue May 25 14:27:57 PDT 1993 by muller

/** Port: File.T is java.io.InputStream. */
public final class Host {
    private Host() {}

    // parameters to the top-level compile

    public static String filename = null;

    public static InputStream source;

    public static M3Compiler.Environment env;

    // misc. options

    /** => diagnostic listing of phases */
    public static boolean verbose = false;

    /** N>=0 => abort on the Nth error */
    public static long errorDie = -1;

    /** => ignore levels less than this value */
    public static long warnings = 2;

    /** => generate coverage counts */
    public static boolean coverage = false;

    /** do emit version stamps */
    public static boolean versionStamps = true;

    /** don't compile, emit the builtins */
    public static boolean emitBuiltins = false;

    /** initialize all floats to zero */
    public static boolean init_floats = false;

    /** print version stamp debugging */
    public static boolean vs_debug = false;

    /** print unit's load map as a comment */
    public static boolean load_map = true;

    /** use the Target specified stack walker */
    public static boolean stack_walker = true;

    /** can calls to procedures be nested? */
    public static boolean nested_calls = false;

    /** should nested procedures be generated before or after their parent */
    public static boolean nested_procs_first = true;

    /** should the code for nested procedures be generated inline */
    public static boolean inline_nested_procs = true;

    /**
     * avoid unnecessary copying of records and arrays (where possible)
     * by passing the final destination to constructors and large-result
     * procedures
     */
    public static boolean direct_struct_assign = true;

    /** must the stack be empty after every store */
    public static boolean clean_stores = false;

    /** must the stack be empty before every jump or call */
    public static boolean clean_jumps = true;

    // runtime checks

    public static boolean doNarrowChk = true;

    public static boolean doRangeChk = true;

    public static boolean doReturnChk = true;

    public static boolean doCaseChk = true;

    public static boolean doTCaseChk = true;

    public static boolean doAsserts = true;

    public static boolean doNilChk = true;

    public static boolean doRaisesChk = true;

    public static boolean doProcChk = false;

    public static boolean doDebugs = true;

    // GC checks

    public static boolean doIncGC = true;

    public static boolean doGenGC = true;

    /** TRUE =>  "ADR (t: T): UNTRACED REF T" */
    public static boolean new_adr = false;

    public static boolean report_stats = false;

    public static boolean initialize(String[] options) {
        verbose = false;
        errorDie = -1;
        warnings = 2;
        coverage = false;
        versionStamps = true;
        emitBuiltins = false;
        init_floats = false;
        vs_debug = false;
        load_map = true;
        stack_walker = true;
        nested_calls = false;
        nested_procs_first = false;
        inline_nested_procs = true;
        direct_struct_assign = true;
        clean_stores = false;
        clean_jumps = true;
        doNarrowChk = true;
        doRangeChk = true;
        doReturnChk = true;
        doCaseChk = true;
        doTCaseChk = true;
        doAsserts = true;
        doNilChk = true;
        doRaisesChk = true;
        doProcChk = false;
        doDebugs = true;
        new_adr = false;
        report_stats = false;
        doIncGC = true;
        doGenGC = true;

        for (int i = 0; i <= options.length - 1; i++) {
            if (!processArg(options[i])) {
                return false;
            }
        }
        return true;
    }

    //------------------------------------------------ command line arguments ---

    private static boolean processArg(String t) {
        String key;
        if ((t == null)) {
            return true;
        }
        // Port: Text.Sub (t, 0, 2) clips the length to the text.
        key = t.substring(0, Math.min(2, t.length()));
        if ((t.equals("-v"))) {
            verbose = true;
            vs_debug = true;
            warnings = -1;
        } else if ((key.equals("-t"))) {
            // ignore the old -tTARGET option which is still in the config files
        } else if ((t.equals("-g"))) {
            // generate debugging, which we always do anyway!
        } else if ((t.equals("-S"))) {
            versionStamps = false; // don't generate version stamps
        } else if ((t.equals("-w"))) {
            warnings = 99;
        } else if ((key.equals("-w"))) {
            warnings = getInt(t, 2);
        } else if ((t.equals("-builtins"))) {
            emitBuiltins = true; // emit the predefined scopes
        } else if ((t.equals("-Z"))) {
            coverage = true; // generate line profiling
        } else if ((key.equals("-E"))) {
            errorDie = getInt(t, 2);
        } else if ((t.equals("-NoAsserts"))) {
            doAsserts = false;
        } else if ((t.equals("-NoDebug"))) {
            doDebugs = false;
        } else if ((t.equals("-NoNarrowChk"))) {
            doNarrowChk = false;
        } else if ((t.equals("-NoRangeChk"))) {
            doRangeChk = false;
        } else if ((t.equals("-NoReturnChk"))) {
            doReturnChk = false;
        } else if ((t.equals("-NoCaseChk"))) {
            doCaseChk = false;
        } else if ((t.equals("-NoTypecaseChk"))) {
            doTCaseChk = false;
        } else if ((t.equals("-NoNilChk"))) {
            doNilChk = false;
        } else if ((t.equals("-NoRaisesChk"))) {
            doRaisesChk = false;
        } else if ((t.equals("-NoChecks"))) {
            doAsserts = false;
            doNarrowChk = false;
            doRangeChk = false;
            doReturnChk = false;
            doCaseChk = false;
            doTCaseChk = false;
            doNilChk = false;
            doRaisesChk = false;
            doDebugs = false;
            doProcChk = false;
        } else if ((t.equals("-NoIncGC"))) {
            doIncGC = false;
        } else if ((t.equals("-NoGenGC"))) {
            doGenGC = false;
        } else if ((t.equals("-InitFloats"))) {
            init_floats = true;
        } else if ((t.equals("-load_map"))) {
            load_map = true;
        } else if ((t.equals("-No_load_map"))) {
            load_map = false;
        } else if ((t.equals("-No_stack_walker"))) {
            stack_walker = false;
        } else if ((t.equals("-nested_calls"))) {
            nested_calls = true;
        } else if ((t.equals("-no_nested_calls"))) {
            nested_calls = false;
        } else if ((t.equals("-nested_procs_first"))) {
            // nested_procs_first := TRUE;
            // THIS IS BOGUS:  nested_procs_first => the nested procedure
            //   cannot reference local variables of its parent that
            //   weren't declared at the outermost level.
        } else if ((t.equals("-nested_procs_last"))) {
            nested_procs_first = false;
        } else if ((t.equals("-inline_nested_procs"))) {
            inline_nested_procs = true;
        } else if ((t.equals("-unfold_nested_procs"))) {
            inline_nested_procs = false;
        } else if ((t.equals("-direct_struct_assign"))) {
            direct_struct_assign = true;
        } else if ((t.equals("-copying_struct_assign"))) {
            direct_struct_assign = false;
        } else if ((t.equals("-clean_stores"))) {
            clean_stores = true;
        } else if ((t.equals("-dirty_stores"))) {
            clean_stores = false;
        } else if ((t.equals("-clean_jumps"))) {
            clean_jumps = true;
        } else if ((t.equals("-check_procs"))) {
            doProcChk = true;
        } else if ((t.equals("-dirty_jumps"))) {
            clean_jumps = false;
        } else if ((t.equals("-vsdebug"))) {
            vs_debug = true;
        } else if ((t.equals("-new_adr"))) {
            new_adr = true;
        } else if ((t.equals("-old_adr"))) {
            new_adr = false;
        } else if ((t.equals("-stats"))) {
            report_stats = true;
        } else {
            env.report_error(null, 0, "m3c: unknown option, \"" + t + "\"");
            return false;
        }
        return true;
    }

    private static long getInt(String t, long start) {
        char c;
        long n = 0;
        for (long j = start; j <= t.length() - 1; j++) {
            c = t.charAt((int) j);
            if ((c < '0') || ('9' < c)) {
                return n;
            }
            n = n * 10 + c - '0';
        }
        return n;
    }

    //-------------------------------------------------- misc file operations ---

    /** Port: the VAR (OUT) parameter filename is a one-element array. */
    public static InputStream openUnit(int name, boolean interface_, boolean generic, String[] filename) {
        M3Compiler.SourceFile file;
        // ETimer.Push (M3Timers.search);
        file = env.find_source(name, interface_, generic);
        filename[0] = file.name;
        // ETimer.Pop ();
        return file.contents;
    }

    /** Port: TRY ... EXCEPT ELSE END catches the IOException of close. */
    public static void closeFile(InputStream rd) {
        if ((rd != null)) {
            try {
                rd.close();
            } catch (IOException e) {
                // ignored
            }
        }
    }
}
