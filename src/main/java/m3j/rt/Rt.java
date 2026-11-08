// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import m3.i.RuntimeError;

/**
 * The runtime support of compiled Modula-3 code: checks, arithmetic with
 * the semantics of cm3 on AMD64_NT, runtime errors, and the start and end
 * of a program.
 */
public final class Rt {
    private Rt() {}

    // ----------------------------------------------------------- range checks

    public static long rangeJ(long v, long min, long max) {
        if (v < min || v > max) {
            throw error(RuntimeError.ValueOutOfRange);
        }
        return v;
    }

    public static int rangeI(int v, int min, int max) {
        if (v < min || v > max) {
            throw error(RuntimeError.ValueOutOfRange);
        }
        return v;
    }

    /** Checks a long against int bounds and narrows it. */
    public static int rangeJI(long v, int min, int max) {
        if (v < min || v > max) {
            throw error(RuntimeError.ValueOutOfRange);
        }
        return (int) v;
    }

    // ------------------------------------------------------------- arithmetic

    public static long div(long a, long b) {
        if (b == 0) {
            throw error(RuntimeError.IntegerDivideByZero);
        }
        return Math.floorDiv(a, b);
    }

    public static long mod(long a, long b) {
        if (b == 0) {
            throw error(RuntimeError.IntegerDivideByZero);
        }
        return Math.floorMod(a, b);
    }

    /** x MOD y for REAL: x - y * FLOOR (x / y). */
    public static float mod(float x, float y) {
        return x - y * (float) Math.floor(x / y);
    }

    public static double mod(double x, double y) {
        return x - y * Math.floor(x / y);
    }

    public static long udiv(long a, long b) {
        if (b == 0) {
            throw error(RuntimeError.IntegerDivideByZero);
        }
        return Long.divideUnsigned(a, b);
    }

    public static long umod(long a, long b) {
        if (b == 0) {
            throw error(RuntimeError.IntegerDivideByZero);
        }
        return Long.remainderUnsigned(a, b);
    }

    public static float min(float a, float b) {
        return a < b ? a : b;
    }

    public static float max(float a, float b) {
        return a > b ? a : b;
    }

    public static double min(double a, double b) {
        return a < b ? a : b;
    }

    public static double max(double a, double b) {
        return a > b ? a : b;
    }

    /** ABS of a REAL as cm3 computes it: -0.0 stays -0.0. */
    public static float abs(float a) {
        return a < 0 ? -a : a;
    }

    /** ABS of a LONGREAL or EXTENDED as cm3 computes it: -0.0 stays -0.0. */
    public static double abs(double a) {
        return a < 0 ? -a : a;
    }

    /** Word.Shift: left for n &gt; 0, right for n &lt; 0, 0 for |n| &gt;= 64. */
    public static long shift(long x, long n) {
        if (n >= 0) {
            return n >= 64 ? 0 : x << n;
        }
        return n <= -64 ? 0 : x >>> -n;
    }

    /** Word.Rotate: bit i of the result is bit (i - n) MOD 64 of x. */
    public static long rotate(long x, long n) {
        return Long.rotateLeft(x, (int) Math.floorMod(n, 64L));
    }

    /** Word.Extract (x, i, n); i + n &lt;= 64 is checked. */
    public static long extract(long x, long i, long n) {
        if (i < 0 || n < 0 || i + n > 64) {
            throw error(RuntimeError.ValueOutOfRange);
        }
        if (n == 0) {
            return 0;
        }
        long v = x >>> i;
        return n == 64 ? v : v & ((1L << n) - 1);
    }

    /** Word.Insert (x, y, i, n); i + n &lt;= 64 is checked. */
    public static long insert(long x, long y, long i, long n) {
        if (i < 0 || n < 0 || i + n > 64) {
            throw error(RuntimeError.ValueOutOfRange);
        }
        if (n == 0) {
            return x;
        }
        long mask = (n == 64 ? -1L : (1L << n) - 1) << i;
        return (x & ~mask) | ((y << i) & mask);
    }

    // ---------------------------------------------- conversions of reals

    /**
     * The conversion of the reference (cvttsd2si): values that do not fit,
     * and NaN, give FIRST(INTEGER).
     */
    private static long toInteger(double d) {
        if (d >= -9.223372036854775808E18 && d < 9.223372036854775808E18) {
            return (long) d;
        }
        return Long.MIN_VALUE;
    }

    public static long trunc(double d) {
        return toInteger(d);
    }

    /** ROUND: C's round, halves away from zero. */
    public static long round(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return toInteger(d);
        }
        double a = Math.abs(d);
        double t = Math.floor(a);
        double r = a - t >= 0.5 ? t + 1.0 : t;
        return toInteger(Math.copySign(r, d));
    }

    public static long floor(double d) {
        return toInteger(Math.floor(d));
    }

    public static long ceiling(double d) {
        return toInteger(Math.ceil(d));
    }

    // --------------------------------------------------------- runtime errors

    /** A runtime error with the given code (RuntimeError.T) at the caller's place. */
    public static RuntimeException error(int code) {
        RuntimeError.E e = new RuntimeError.E(code);
        locate(e);
        return e;
    }

    /** The error of a failed &lt;*ASSERT*&gt;; msg is the text of the condition or the message. */
    public static RuntimeException assertFailed(String msg) {
        RuntimeError.E e = new RuntimeError.E(RuntimeError.AssertFailed);
        e.info = msg;
        locate(e);
        return e;
    }

    /** The value of a procedure variable to be assigned: not a nested procedure. */
    public static Object notNested(Object p) {
        if (p instanceof Nested) {
            throw error(RuntimeError.NarrowFailed);
        }
        return p;
    }

    /** A construct the back end does not compile yet. */
    public static RuntimeException notImplemented(String what) {
        return new UnsupportedOperationException("m3j: not implemented: " + what);
    }

    /** Sets the place of e to the innermost frame of compiled Modula-3 code. */
    public static void locate(M3Exception e) {
        StackWalker.getInstance().walk(s -> s.filter(f -> isM3(f.getFileName())).findFirst())
            .ifPresent(f -> e.at(f.getFileName(), f.getLineNumber()));
    }

    static boolean isM3(String file) {
        return file != null && (file.endsWith(".m3") || file.endsWith(".i3")
                                || file.endsWith(".mg") || file.endsWith(".ig"));
    }

    // ------------------------------------------------------- start and exit

    private static final List<Runnable> exitors = new ArrayList<>();
    private static String[] args = new String[0];

    /** The arguments of the program, without the program name. */
    public static String[] args() {
        return args;
    }

    /** The pathname p resolved against the working directory of the program. */
    public static String resolve(String p) {
        return FileSystem.path(p).toString();
    }

    /**
     * Registers a procedure that runs when the program ends: normally, by
     * exit, or by a runtime error (as RTProcess.Crash runs the exitors).
     */
    public static synchronized void atExit(Runnable r) {
        exitors.add(r);
    }

    /** Runs the exitors (in reverse order) and ends the program. */
    public static void exit(int status) {
        runExitors();
        System.exit(status);
    }

    public static void runExitors() {
        List<Runnable> l;
        synchronized (Rt.class) {
            l = new ArrayList<>(exitors);
            exitors.clear();
        }
        for (int i = l.size() - 1; i >= 0; i--) {
            try {
                l.get(i).run();
            } catch (RuntimeException e) {
                // ignore
            }
        }
    }

    /** The stack size of the main thread of a program, in bytes. */
    static final long STACK = 1L << 30;

    /** the main class of the program (m3.Main) */
    public static volatile Class<?> program;

    /**
     * Runs a program: body initializes the units in order. Runs in a thread
     * with a large stack; a runtime error or an unhandled exception ends
     * the program as with cm3.
     */
    public static void main(String[] argv, Runnable body) {
        args = argv;
        program = body.getClass();
        int[] status = {0};
        Thread t = new Thread(null, () -> {
            try {
                body.run();
                runExitors();
            } catch (M3Exception e) {
                status[0] = crash(e);
            } catch (UnsupportedOperationException | LinkageError e) {
                e.printStackTrace();
                status[0] = 1;
            } catch (StackOverflowError e) {
                // a program of cm3 on Windows ends without a message
                runExitors();
                status[0] = 1;
            } catch (RuntimeException | Error e) {
                status[0] = crash(toM3(e));
            }
        }, "main", STACK);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.flush();
        System.exit(status[0]);
    }

    /**
     * The checked runtime error of exception e leaving a procedure whose
     * RAISES clause does not list it (at the place of the RAISE).
     */
    public static RuntimeException blocked(M3Exception e) {
        RuntimeError.E r = new RuntimeError.E(RuntimeError.BlockedException);
        r.info = e.m3Name();
        r.at(e.file, e.line);
        return r;
    }

    /** Prints the message of an unhandled exception as cm3 does; the exit status. */
    static int crash(M3Exception e) {
        String pre, name, post = "";
        if (e instanceof RuntimeError.E re && re.code == RuntimeError.BlockedException && re.info != null) {
            pre = "Exception \"";
            name = re.info;
            post = "\" not in RAISES list";
        } else if (e instanceof RuntimeError.E re && re.code != RuntimeError.UnhandledException) {
            if (re.code == RuntimeError.AssertFailed && re.info != null) {
                pre = "<*ASSERT*> failed: ";
                name = re.info;
            } else {
                pre = "";
                name = RuntimeError.Tag(re.code);
            }
        } else {
            pre = "Unhandled exception: ";
            name = e.m3Name();
        }
        runExitors();
        PrintStream err = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err),
                                          true, StandardCharsets.ISO_8859_1);
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n***\n*** runtime error:\n***    ").append(pre).append(name).append(post);
        if (e.file != null) {
            sb.append("\n***    file \"").append(e.file).append('"');
            if (e.line != 0) {
                sb.append(", line ").append(e.line);
            }
        }
        if (e instanceof m3.i.Java.Exception && e.arg != null) {
            sb.append("\n***    ").append(e.arg);
        }
        sb.append("\n***\n\n");
        err.print(sb);
        err.flush();
        return CRASH_STATUS;
    }

    /** The exit status after a runtime error, as cm3's programs on Windows have it. */
    static final int CRASH_STATUS = -1;

    // ------------------------------------------------------------- texts

    /** t &amp; u */
    public static String concat(String t, String u) {
        if (t == null || u == null) {
            throw error(RuntimeError.BadMemoryReference);
        }
        return t.concat(u);
    }

    // ----------------------------------------------------- subscripts, sizes

    /** The index i - lo of an array of n elements, checked. */
    public static int index(long i, long lo, int n) {
        long k = i - lo;
        if (i < lo || k >= n) {
            throw error(RuntimeError.SubscriptOutOfRange);
        }
        return (int) k;
    }

    /** The index i of an array of n elements (from 0), checked. */
    public static int index(int i, int n) {
        if (i < 0 || i >= n) {
            throw error(RuntimeError.SubscriptOutOfRange);
        }
        return i;
    }

    /** The index i of an open array of n elements, checked. */
    public static int index(long i, int n) {
        if (i < 0 || i >= n) {
            throw error(RuntimeError.SubscriptOutOfRange);
        }
        return (int) i;
    }

    /**
     * SUBARRAY (a, from, len) of an array of n elements: from, checked (as
     * cm3 does, a range error: from and len must not be negative, and
     * from + len must not exceed n).
     */
    public static int subarray(long from, long len, int n) {
        if (from < 0 || len < 0 || from > n || len > n - from) {
            throw error(RuntimeError.ValueOutOfRange);
        }
        return (int) from;
    }

    /** A dimension given to NEW: a count that must not be negative. */
    public static int count(long n) {
        if (n < 0 || n > Integer.MAX_VALUE) {
            // cm3 checks the count in its allocator, and reports its place
            RuntimeError.E e = new RuntimeError.E(RuntimeError.ValueOutOfRange);
            e.at("../src/runtime/common/RTAllocator.m3", 344);
            throw e;
        }
        return (int) n;
    }

    /** acc * n, the number of elements of an open array so far. */
    public static long dimension(long acc, long n) {
        try {
            return Math.multiplyExact(acc, n);
        } catch (ArithmeticException e) {
            throw error(RuntimeError.OutOfMemory);
        }
    }

    /** The size of storage: n leaves. */
    public static int size(long n) {
        if (n > Integer.MAX_VALUE - 8) {
            throw error(RuntimeError.OutOfMemory);
        }
        return (int) n;
    }

    // ------------------------------------------------------- arrays of records

    /** Copies n records element by element. */
    public static void copyRecords(Object[] src, int so, Object[] dst, int d0, int n) {
        if (src == dst && so < d0) {
            for (int i = n - 1; i >= 0; i--) {
                ((Rec) dst[d0 + i]).assign(src[so + i]);
            }
        } else {
            for (int i = 0; i < n; i++) {
                ((Rec) dst[d0 + i]).assign(src[so + i]);
            }
        }
    }

    /** A copy of the records src[from..to-1]. */
    public static Object[] copyRecords(Object[] src, int from, int to) {
        Object[] r = java.util.Arrays.copyOfRange(src, from, to);
        for (int i = 0; i < r.length; i++) {
            r[i] = ((Rec) r[i]).duplicate();
        }
        return r;
    }

    public static boolean equalRecords(Object[] a, int ao, Object[] b, int bo, int n) {
        for (int i = 0; i < n; i++) {
            if (!((Rec) a[ao + i]).same(b[bo + i])) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ equality

    public static boolean equal(boolean[] a, int ao, boolean[] b, int bo, int n) {
        return java.util.Arrays.equals(a, ao, ao + n, b, bo, bo + n);
    }

    public static boolean equal(byte[] a, int ao, byte[] b, int bo, int n) {
        return java.util.Arrays.equals(a, ao, ao + n, b, bo, bo + n);
    }

    public static boolean equal(short[] a, int ao, short[] b, int bo, int n) {
        return java.util.Arrays.equals(a, ao, ao + n, b, bo, bo + n);
    }

    public static boolean equal(char[] a, int ao, char[] b, int bo, int n) {
        return java.util.Arrays.equals(a, ao, ao + n, b, bo, bo + n);
    }

    public static boolean equal(int[] a, int ao, int[] b, int bo, int n) {
        return java.util.Arrays.equals(a, ao, ao + n, b, bo, bo + n);
    }

    public static boolean equal(long[] a, int ao, long[] b, int bo, int n) {
        return java.util.Arrays.equals(a, ao, ao + n, b, bo, bo + n);
    }

    /** Floats compare as their bits, as cm3 compares fixed arrays (without holes). */
    public static boolean equal(float[] a, int ao, float[] b, int bo, int n) {
        for (int i = 0; i < n; i++) {
            if (Float.floatToRawIntBits(a[ao + i]) != Float.floatToRawIntBits(b[bo + i])) {
                return false;
            }
        }
        return true;
    }

    public static boolean equal(double[] a, int ao, double[] b, int bo, int n) {
        for (int i = 0; i < n; i++) {
            if (Double.doubleToRawLongBits(a[ao + i]) != Double.doubleToRawLongBits(b[bo + i])) {
                return false;
            }
        }
        return true;
    }

    /** Floats compare as numbers: 0.0 = -0.0, a NaN equals nothing. */
    public static boolean equalNumbers(float[] a, int ao, float[] b, int bo, int n) {
        for (int i = 0; i < n; i++) {
            if (a[ao + i] != b[bo + i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean equalNumbers(double[] a, int ao, double[] b, int bo, int n) {
        for (int i = 0; i < n; i++) {
            if (a[ao + i] != b[bo + i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean equal(Object[] a, int ao, Object[] b, int bo, int n) {
        for (int i = 0; i < n; i++) {
            if (a[ao + i] != b[bo + i]) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ sets

    /** The bits of [a..b] in a small set whose first element is lo. */
    public static long setRange(long a, long b, long lo) {
        if (a > b) {
            return 0;
        }
        long x = a - lo, y = b - lo;
        long hi = y >= 63 ? -1L : (1L << (y + 1)) - 1;
        return hi & (-1L << x);
    }

    /** Adds [a..b] to a large set. */
    public static void setRangeBig(long[] s, long a, long b, long lo) {
        setRangeBig(s, 0, a, b, lo);
    }

    /** Adds [a..b] to the large set at s[off..]. */
    public static void setRangeBig(long[] s, int off, long a, long b, long lo) {
        if (a > b) {
            return;
        }
        long x = a - lo, y = b - lo;
        int wa = off + (int) (x >>> 6), wb = off + (int) (y >>> 6);
        long ma = -1L << (x & 63), mb = -1L >>> (63 - (y & 63));
        if (wa == wb) {
            s[wa] |= ma & mb;
            return;
        }
        s[wa] |= ma;
        java.util.Arrays.fill(s, wa + 1, wb, -1L);
        s[wb] |= mb;
    }

    /** Removes [a..b] from the large set at s[off..]. */
    public static void clearRangeBig(long[] s, int off, long a, long b, long lo) {
        if (a > b) {
            return;
        }
        long x = a - lo, y = b - lo;
        int wa = off + (int) (x >>> 6), wb = off + (int) (y >>> 6);
        long ma = -1L << (x & 63), mb = -1L >>> (63 - (y & 63));
        if (wa == wb) {
            s[wa] &= ~(ma & mb);
            return;
        }
        s[wa] &= ~ma;
        java.util.Arrays.fill(s, wa + 1, wb, 0L);
        s[wb] &= ~mb;
    }

    /** Empties the large set of n words at s[off..]. */
    public static void setClear(long[] s, int off, int n) {
        java.util.Arrays.fill(s, off, off + n, 0L);
    }

    /** Copies a large set of n words (nothing to do if it is the same set). */
    public static void setCopy(long[] a, int ao, long[] r, int ro, int n) {
        if (a != r || ao != ro) {
            System.arraycopy(a, ao, r, ro, n);
        }
    }

    public static void setUnion(long[] a, int ao, long[] b, int bo, long[] r, int ro, int n) {
        for (int i = 0; i < n; i++) {
            r[ro + i] = a[ao + i] | b[bo + i];
        }
    }

    public static void setDiff(long[] a, int ao, long[] b, int bo, long[] r, int ro, int n) {
        for (int i = 0; i < n; i++) {
            r[ro + i] = a[ao + i] & ~b[bo + i];
        }
    }

    public static void setInter(long[] a, int ao, long[] b, int bo, long[] r, int ro, int n) {
        for (int i = 0; i < n; i++) {
            r[ro + i] = a[ao + i] & b[bo + i];
        }
    }

    public static void setSymDiff(long[] a, int ao, long[] b, int bo, long[] r, int ro, int n) {
        for (int i = 0; i < n; i++) {
            r[ro + i] = a[ao + i] ^ b[bo + i];
        }
    }

    /** v IN s, for a small set of n elements from lo. */
    public static boolean in(long s, long v, long lo, long n) {
        long k = v - lo;
        if (v < lo || k >= n) {
            return false;
        }
        return ((s >>> k) & 1) != 0;
    }

    public static boolean inBig(long[] s, int off, long v, long lo, long n) {
        long k = v - lo;
        if (v < lo || k >= n) {
            return false;
        }
        return ((s[off + (int) (k >>> 6)] >>> (k & 63)) & 1) != 0;
    }

    public static boolean setLE(long a, long b) {
        return (a & ~b) == 0;
    }

    public static boolean setLT(long a, long b) {
        return (a & ~b) == 0 && a != b;
    }

    public static boolean setGE(long a, long b) {
        return (b & ~a) == 0;
    }

    public static boolean setGT(long a, long b) {
        return (b & ~a) == 0 && a != b;
    }

    public static boolean bigSetLE(long[] a, int ao, long[] b, int bo, int n) {
        for (int i = 0; i < n; i++) {
            if ((a[ao + i] & ~b[bo + i]) != 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean bigSetLT(long[] a, int ao, long[] b, int bo, int n) {
        return bigSetLE(a, ao, b, bo, n) && !equal(a, ao, b, bo, n);
    }

    // ------------------------------------------------------------ exceptions

    /**
     * The Modula-3 exception for an exception caught by a handler: itself,
     * or for a JVM exception that stands for a checked runtime error, that
     * runtime error. Other throwables are thrown again.
     */
    public static M3Exception toM3(Throwable t) {
        if (t instanceof M3Exception e) {
            return e;
        }
        int code;
        if (t instanceof NullPointerException) {
            code = RuntimeError.BadMemoryReference;
        } else if (t instanceof ArithmeticException) {
            code = RuntimeError.IntegerDivideByZero;
        } else if (t instanceof IndexOutOfBoundsException) {
            code = RuntimeError.SubscriptOutOfRange;
        } else if (t instanceof ClassCastException) {
            code = RuntimeError.NarrowFailed;
        } else if (t instanceof StackOverflowError) {
            code = RuntimeError.StackOverflow;
        } else if (t instanceof OutOfMemoryError) {
            code = RuntimeError.OutOfMemory;
        } else if (t instanceof RuntimeException r) {
            throw r;
        } else if (t instanceof Error r) {
            throw r;
        } else {
            throw new IllegalStateException(t);
        }
        RuntimeError.E e = new RuntimeError.E(code);
        StackTraceElement[] st = t.getStackTrace();
        for (StackTraceElement f : st) {
            if (isM3(f.getFileName())) {
                e.at(f.getFileName(), f.getLineNumber());
                break;
            }
        }
        return e;
    }

    /**
     * Process.Crash and RTProcess.Crash: the message (as "*** msg" on a line
     * of its own, after an empty line), then the exitors, then the end with
     * the status of a crash.
     */
    public static void crashProcess(String msg) {
        System.out.flush();
        if (msg != null) {
            System.err.print("\n*** " + msg + "\n");
            System.err.flush();
        }
        runExitors();
        System.out.flush();
        Runtime.getRuntime().halt(CRASH_STATUS);
    }

    /** A checked error the runtime reports and that ends the program (RTError.Msg). */
    public static void die(String file, int line, String msgA, String msgB) {
        runExitors();
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n***\n*** runtime error:\n***    ").append(msgA).append(msgB);
        sb.append("\n***    file \"").append(file).append("\", line ").append(line);
        sb.append("\n***\n\n");
        System.err.print(sb);
        System.err.flush();
        Runtime.getRuntime().halt(CRASH_STATUS);
    }

    /** Ends the program for an exception not handled in a thread. */
    public static void crashAndExit(M3Exception e) {
        int status = crash(e);
        System.out.flush();
        Runtime.getRuntime().halt(status);
    }

    /**
     * The method "call" of type t of the class c of a procedure value (its
     * functional interface is generated for the program, so the runtime
     * library finds it by its name).
     */
    public static java.lang.invoke.MethodHandle procHandle(Class<?> c,
                                                           java.lang.invoke.MethodType t) {
        for (Class<?> i : c.getInterfaces()) {
            try {
                return java.lang.invoke.MethodHandles.publicLookup().findVirtual(i, "call", t);
            } catch (ReflectiveOperationException e) {
                // try the next one
            }
        }
        throw new IllegalStateException("not a procedure of type " + t + ": " + c);
    }

    private static final ClassValue<java.lang.invoke.MethodHandle> call0 = new ClassValue<>() {
        @Override
        protected java.lang.invoke.MethodHandle computeValue(Class<?> c) {
            return procHandle(c, java.lang.invoke.MethodType.methodType(void.class));
        }
    };

    /** Calls a procedure value of type PROCEDURE () (for the runtime library). */
    public static void callProc0(Object p) {
        try {
            call0.get(p.getClass()).invoke(p);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /** Registers a procedure value of type PROCEDURE () to run at the exit. */
    public static void registerExitor(Object p) {
        atExit(() -> callProc0(p));
    }
}
