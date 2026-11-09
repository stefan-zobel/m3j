// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/**
 * The &lt;*EXTERNAL*&gt; procedures that safe Modula-3 programs and the
 * library use, implemented in Java. A method's name is the external name
 * of the procedure, its descriptor that of the Modula-3 signature.
 */
public final class Externals {
    private Externals() {}

    /** Cstdlib.exit (status: int) */
    public static void Cstdlib__exit(int status) {
        Rt.exit(status);
    }

    /**
     * Test.CheckFloatsAndTypes (READONLY t: T; size := BYTESIZE (T)) of
     * m3tests: compares the layout of a record with that of C, which the
     * JVM does not have; nothing to check.
     */
    public static void Test__CheckFloatsAndTypes(Object t, long size) {
    }

    /**
     * M3jDate.Fields (m3j's module Date): the date of time t (seconds since
     * 1970 UTC) in the local time zone or in UTC; offset is UTC minus local
     * time in seconds; the name of the time zone.
     */
    public static String M3jDate__Fields(double t, boolean local, LongRef year, LongRef month, LongRef day,
                                         LongRef hour, LongRef minute, LongRef second, LongRef offset,
                                         LongRef weekDay) {
        long sec = (long) Math.floor(t);
        java.time.Instant i = java.time.Instant.ofEpochSecond(sec);
        java.time.ZoneId z = local ? java.time.ZoneId.systemDefault() : java.time.ZoneOffset.UTC;
        java.time.ZonedDateTime d = i.atZone(z);
        year.set(d.getYear());
        month.set(d.getMonthValue() - 1);
        day.set(d.getDayOfMonth());
        hour.set(d.getHour());
        minute.set(d.getMinute());
        second.set(d.getSecond());
        offset.set(-d.getOffset().getTotalSeconds());
        weekDay.set(d.getDayOfWeek().getValue() % 7);
        if (!local) {
            return "UTC";
        }
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone(z);
        return tz.getDisplayName(tz.inDaylightTime(java.util.Date.from(i)), java.util.TimeZone.LONG);
    }

    /** M3jDate.ToTime: the time of a date given in UTC plus offset seconds; ok 0 if there is none. */
    public static double M3jDate__ToTime(long year, long month, long day, long hour, long minute, long second,
                                         long offset, LongRef ok) {
        try {
            java.time.LocalDateTime d = java.time.LocalDateTime.of((int) year, (int) month + 1, (int) day,
                (int) hour, (int) minute, (int) Math.min(second, 59));
            long s = d.toEpochSecond(java.time.ZoneOffset.UTC) + offset + (second > 59 ? 1 : 0);
            ok.set(1);
            return s;
        } catch (java.time.DateTimeException | ArithmeticException e) {
            ok.set(0);
            return 0;
        }
    }

    // M3jFS (m3j's module FS): see FileSystem

    public static String M3jFS__Status(String p, LongRef type, DoubleRef time, LongRef size) {
        return FileSystem.status(p, type, time, size);
    }

    public static String M3jFS__SetModificationTime(String p, double t) {
        return FileSystem.setModificationTime(p, t);
    }

    public static String M3jFS__Absolute(String p, ObjRef err) {
        return FileSystem.absolute(p, err);
    }

    public static String M3jFS__CreateDirectory(String p) {
        return FileSystem.createDirectory(p);
    }

    public static String M3jFS__DeleteDirectory(String p) {
        return FileSystem.deleteDirectory(p);
    }

    public static String M3jFS__DeleteFile(String p) {
        return FileSystem.deleteFile(p);
    }

    public static String M3jFS__Rename(String p0, String p1) {
        return FileSystem.rename(p0, p1);
    }

    public static String M3jFS__Entries(String p, ObjRef err) {
        return FileSystem.entries(p, err);
    }

    public static long M3jFS__Open(String p, boolean readonly, boolean truncate, long create, ObjRef err) {
        return FileSystem.open(p, readonly, truncate, create, err);
    }

    public static long M3jFS__Read(long h, byte[] b, int off, int n, boolean mayBlock, ObjRef err) {
        return FileSystem.read(h, b, off, n, mayBlock, err);
    }

    public static long M3jFS__ReadChars(long h, byte[] b, int off, int n, boolean mayBlock, ObjRef err) {
        return FileSystem.read(h, b, off, n, mayBlock, err);
    }

    public static String M3jFS__WriteChars(long h, byte[] b, int off, int n) {
        return FileSystem.write(h, b, off, n);
    }

    public static boolean M3jFS__IsTerminal() {
        return FileSystem.isTerminal();
    }

    public static String M3jFS__Write(long h, byte[] b, int off, int n) {
        return FileSystem.write(h, b, off, n);
    }

    public static long M3jFS__Seek(long h, long origin, long offset, ObjRef err) {
        return FileSystem.seek(h, origin, offset, err);
    }

    public static String M3jFS__HandleStatus(long h, DoubleRef time, LongRef size) {
        return FileSystem.handleStatus(h, time, size);
    }

    public static boolean M3jFS__Readable(long h) {
        return FileSystem.readable(h);
    }

    public static boolean M3jFS__Writable(long h) {
        return FileSystem.writable(h);
    }

    public static String M3jFS__Close(long h) {
        return FileSystem.close(h);
    }

    public static String M3jFS__Pipe(LongRef hr, LongRef hw) {
        return FileSystem.pipe(hr, hw);
    }

    public static boolean M3jFS__Access(String p, long mode) {
        return FileSystem.access(p, mode);
    }

    // M3jProc (m3j's modules Process and RTProcess): see Proc
    public static long M3jProc__Create(String cmd, String params, long nparams, String env, long nenv,
                                       String wd, long stdin, long stdout, long stderr, LongRef id,
                                       ObjRef err) {
        return Proc.create(cmd, params, nparams, env, nenv, wd, stdin, stdout, stderr, id, err);
    }

    public static long M3jProc__Wait(long p) {
        return Proc.await(p);
    }

    public static long M3jProc__MyID() {
        return ProcessHandle.current().pid();
    }

    public static String M3jProc__GetWorkingDirectory() {
        return FileSystem.getWorkingDirectory();
    }

    public static String M3jProc__SetWorkingDirectory(String p) {
        return FileSystem.setWorkingDirectory(p);
    }

    // M3jNet (m3j's modules TCP and IP): see Net
    public static long M3jNet__Listen(long addr, long port) {
        return Net.listen(addr, port);
    }

    public static long M3jNet__LocalEndpoint(long h, LongRef addr, LongRef port) {
        return Net.localEndpoint(h, addr, port);
    }

    public static long M3jNet__PeerEndpoint(long h, LongRef addr, LongRef port) {
        return Net.peerEndpoint(h, addr, port);
    }

    public static long M3jNet__Accept(long h, LongRef addr, LongRef port) {
        return Net.accept(h, addr, port);
    }

    public static long M3jNet__Connect(long addr, long port, long fromAddr, long fromPort) {
        return Net.connect(addr, port, fromAddr, fromPort);
    }

    public static long M3jNet__FinishConnect(long h) {
        return Net.finishConnect(h);
    }

    public static long M3jNet__Recv(long h, byte[] b, int off, int n) {
        return Net.recv(h, b, off, n);
    }

    public static long M3jNet__Send(long h, byte[] b, int off, int n) {
        return Net.send(h, b, off, n);
    }

    public static long M3jNet__EOF(long h) {
        return Net.eof(h);
    }

    public static long M3jNet__Wait(long h, boolean write, double timeout, boolean alertable) {
        return Net.await(h, write, timeout, alertable);
    }

    public static long M3jNet__SetOption(long h, long option, boolean on) {
        return Net.setOption(h, option, on);
    }

    public static long M3jNet__Shutdown(long h, long how) {
        return Net.shutdown(h, how);
    }

    public static void M3jNet__Close(long h) {
        Net.close(h);
    }

    public static long M3jNet__HostByName(String nm, LongRef addr) {
        return Net.hostByName(nm, addr);
    }

    public static String M3jNet__CanonicalByName(String nm) {
        return Net.canonicalByName(nm);
    }

    public static String M3jNet__CanonicalByAddr(long a) {
        return Net.canonicalByAddr(a);
    }

    public static long M3jNet__HostAddr() {
        return Net.hostAddr();
    }

    public static String M3jNet__HostName() {
        return Net.hostName();
    }

    public static long M3jNet__AddrInfo(String name, String service, LongRef family, LongRef port,
                                        byte[] a, int off, int n) {
        return Net.addrInfo(name, service, family, port, a, off, n);
    }

    public static long M3jNet__NameInfo(long family, long port, byte[] a, int off, int n, ObjRef host,
                                        ObjRef service) {
        return Net.nameInfo(family, port, a, off, n, host, service);
    }

    /**
     * M3jHash.Digest (m3j's modules MD5 and SHA256): the digest of the
     * characters of t with the algorithm alg ("MD5", "SHA-256"), in hex.
     */
    public static String M3jHash__Digest(String alg, String t) {
        return java.util.HexFormat.of().formatHex(
                digest(alg).digest(t.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
    }

    /** M3jHash.New: a digest in progress. */
    public static Object M3jHash__New(String alg) {
        return digest(alg);
    }

    private static java.security.MessageDigest digest(String alg) {
        try {
            return java.security.MessageDigest.getInstance(alg);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** M3jHash.Update: adds n characters of b, from off, to the digest d. */
    public static void M3jHash__Update(Object d, byte[] b, int off, int n) {
        ((java.security.MessageDigest) d).update(b, off, n);
    }

    /** M3jHash.Finish: the digest of all the characters given to d, in hex. */
    public static String M3jHash__Finish(Object d) {
        return java.util.HexFormat.of().formatHex(((java.security.MessageDigest) d).digest());
    }

    /** M3jRef.Hash: a hash of the identity of a reference. */
    public static long M3jRef__Hash(Object r) {
        return System.identityHashCode(r);
    }

    // M3jWeak (m3j's module WeakRef): see Weak
    public static long M3jWeak__New(Object r, boolean cleanup) {
        return Weak.create(r, cleanup);
    }

    public static Object M3jWeak__Get(long id) {
        return Weak.get(id);
    }

    public static long M3jWeak__Next() {
        return Weak.next();
    }

    public static Object M3jWeak__Take(long id) {
        return Weak.take(id);
    }

    public static void M3jWeak__Collect() {
        Weak.collect();
    }

    /** M3jFloat.RealBits (m3j's TFloat of m3middle): the bits of a REAL. */
    public static long M3jFloat__RealBits(float x) {
        return Float.floatToRawIntBits(x) & 0xFFFFFFFFL;
    }

    /** M3jFloat.LongBits: the bits of a LONGREAL. */
    public static long M3jFloat__LongBits(double x) {
        return Double.doubleToRawLongBits(x);
    }

    /** M3jFloat.RealFromBits: the REAL with the bits b MOD 2^32. */
    public static float M3jFloat__RealFromBits(long b) {
        return Float.intBitsToFloat((int) b);
    }

    /** M3jFloat.LongFromBits: the LONGREAL with the bits b. */
    public static double M3jFloat__LongFromBits(long b) {
        return Double.longBitsToDouble(b);
    }

    /** Cstdlib.abort () */
    public static void Cstdlib__abort() {
        Rt.exit(3);
    }

    // The functions of TestC.c in m3tests: the sign and zero extension of
    // small results.

    public static int NegativeInt8() {
        return -1;
    }

    public static int NegativeUInt8() {
        return 0xFE;
    }

    public static int NegativeInt16() {
        return -3;
    }

    public static int NegativeUInt16() {
        return 0xFFFC;
    }

    public static int NegativeInt32() {
        return -5;
    }

    public static long NegativeUInt32() {
        return 0xFFFFFFFAL;
    }

    public static long NegativeInt64() {
        return -7L;
    }

    public static long NegativeUInt64() {
        return -8L;
    }

    public static int PositiveInt8() {
        return 1;
    }

    public static int PositiveUInt8() {
        return 2;
    }

    public static int PositiveInt16() {
        return 3;
    }

    public static int PositiveUInt16() {
        return 4;
    }

    public static int PositiveInt32() {
        return 5;
    }

    public static long PositiveUInt32() {
        return 6L;
    }

    public static long PositiveInt64() {
        return 7L;
    }

    public static long PositiveUInt64() {
        return 8L;
    }
}
