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

    // M3jTls (m3j's module TLS): see Tls
    public static Object M3jTls__NewContext(String keyStore, String keyPassword, String trustStore,
                                            String trustPassword, ObjRef err) {
        return Tls.newContext(keyStore, keyPassword, trustStore, trustPassword, err);
    }

    public static Object M3jTls__NewEngine(Object context, String host, boolean client, boolean needAuth,
                                           String alpn) {
        return Tls.newEngine(context, host, client, needAuth, alpn);
    }

    public static void M3jTls__Feed(Object e, byte[] b, int off, int n) {
        ((Tls) e).feed(b, off, n);
    }

    public static long M3jTls__Handshaking(Object e) {
        return ((Tls) e).handshaking();
    }

    public static long M3jTls__Take(Object e, byte[] b, int off, int n) {
        return ((Tls) e).take(b, off, n);
    }

    public static long M3jTls__Give(Object e, byte[] b, int off, int n) {
        return ((Tls) e).give(b, off, n);
    }

    public static long M3jTls__Drain(Object e, byte[] b, int off, int n) {
        return ((Tls) e).drain(b, off, n);
    }

    public static void M3jTls__CloseOutput(Object e) {
        ((Tls) e).closeOutput();
    }

    public static String M3jTls__Message(Object e) {
        return ((Tls) e).message();
    }

    public static String M3jTls__Info(Object e, long what) {
        return ((Tls) e).info(what);
    }

    // M3jHttp (m3j's module HTTPClient): see Http

    public static Object M3jHttp__NewClient(double connectTimeout, boolean follow, String proxy, Object context,
                                            ObjRef err) {
        return Http.newClient(connectTimeout, follow, proxy, context, err);
    }

    public static void M3jHttp__CloseClient(Object client) {
        Http.closeClient(client);
    }

    public static Object M3jHttp__Start(Object client, String method, String url, String headers, String text,
                                        boolean upload, long length, double timeout, LongRef code, ObjRef err) {
        return Http.start(client, method, url, headers, text, upload, length, timeout, code, err);
    }

    public static long M3jHttp__AwaitResponse(Object x, LongRef status, ObjRef version, ObjRef headers, ObjRef err) {
        return Http.awaitResponse(x, status, version, headers, err);
    }

    public static long M3jHttp__Read(Object x, byte[] b, int off, int n, double timeout, ObjRef err) {
        return Http.read(x, b, off, n, timeout, err);
    }

    public static long M3jHttp__Write(Object x, byte[] b, int off, int n) {
        return Http.write(x, b, off, n);
    }

    public static void M3jHttp__FinishUpload(Object x, boolean failed) {
        Http.finishUpload(x, failed);
    }

    public static void M3jHttp__Abort(Object x) {
        Http.abort(x);
    }

    // M3jSsh (m3j's library ssh): see Ssh

    public static Object M3jSsh__Connect(String host, long port, String user, String password, String keyFile,
                                         String key, String passphrase, long hostCheck, String knownHosts,
                                         String fingerprint, double timeout, double keepAlive, LongRef code,
                                         ObjRef err) {
        return Ssh.connect(host, port, user, password, keyFile, key, passphrase, hostCheck, knownHosts, fingerprint,
                           timeout, keepAlive, code, err);
    }

    public static String M3jSsh__HostKey(Object s) {
        return Ssh.hostKey(s);
    }

    public static void M3jSsh__Disconnect(Object s) {
        Ssh.disconnect(s);
    }

    public static long M3jSsh__Run(Object s, String command, String input, long maxOutput, double timeout,
                                   double openTimeout, LongRef status, ObjRef signal, ObjRef out, ObjRef errOut,
                                   ObjRef err) {
        return Ssh.run(s, command, input, maxOutput, timeout, openTimeout, status, signal, out, errOut, err);
    }

    public static Object M3jSsh__Start(Object s, String command, long maxBuffer, double openTimeout, LongRef code,
                                       ObjRef err) {
        return Ssh.start(s, command, maxBuffer, openTimeout, code, err);
    }

    public static long M3jSsh__ProcRead(Object p, long which, byte[] b, int off, int n, double timeout, ObjRef err) {
        return Ssh.procRead(p, which, b, off, n, timeout, err);
    }

    public static void M3jSsh__ProcDiscard(Object p, long which) {
        Ssh.procDiscard(p, which);
    }

    public static long M3jSsh__ProcWrite(Object p, byte[] b, int off, int n, ObjRef err) {
        return Ssh.procWrite(p, b, off, n, err);
    }

    public static void M3jSsh__ProcCloseInput(Object p) {
        Ssh.procCloseInput(p);
    }

    public static long M3jSsh__ProcWait(Object s, Object p, LongRef status, ObjRef signal, ObjRef err) {
        return Ssh.procWait(s, p, status, signal, err);
    }

    public static void M3jSsh__ProcClose(Object p) {
        Ssh.procClose(p);
    }

    public static Object M3jSsh__SftpOpen(Object s, double timeout, LongRef code, ObjRef err) {
        return Ssh.sftpOpen(s, timeout, code, err);
    }

    public static void M3jSsh__SftpClose(Object f) {
        Ssh.sftpClose(f);
    }

    public static long M3jSsh__SftpStat(Object f, String path, boolean follow, LongRef kind, LongRef size,
                                        LongRef mode, LongRef uid, LongRef gid, LongRef atime, LongRef mtime,
                                        ObjRef err) {
        return Ssh.sftpStat(f, path, follow, kind, size, mode, uid, gid, atime, mtime, err);
    }

    public static long M3jSsh__SftpList(Object f, String dir, ObjRef list, ObjRef err) {
        return Ssh.sftpList(f, dir, list, err);
    }

    public static String M3jSsh__SftpEntry(Object list, long i, LongRef kind, LongRef size, LongRef mode,
                                           LongRef uid, LongRef gid, LongRef atime, LongRef mtime) {
        return Ssh.sftpEntry(list, i, kind, size, mode, uid, gid, atime, mtime);
    }

    public static long M3jSsh__SftpOp(Object f, long op, String a, String b, long n, long m, ObjRef result,
                                      ObjRef err) {
        return Ssh.sftpOp(f, op, a, b, n, m, result, err);
    }

    public static Object M3jSsh__SftpGet(Object f, String path, long offset, LongRef code, ObjRef err) {
        return Ssh.sftpGet(f, path, offset, code, err);
    }

    public static Object M3jSsh__SftpPut(Object f, String path, long mode, boolean append, LongRef code,
                                         ObjRef err) {
        return Ssh.sftpPut(f, path, mode, append, code, err);
    }

    public static long M3jSsh__StreamRead(Object s, byte[] b, int off, int n, ObjRef err) {
        return Ssh.streamRead(s, b, off, n, err);
    }

    public static long M3jSsh__StreamWrite(Object s, byte[] b, int off, int n, ObjRef err) {
        return Ssh.streamWrite(s, b, off, n, err);
    }

    public static long M3jSsh__StreamClose(Object s, ObjRef err) {
        return Ssh.streamClose(s, err);
    }

    // M3jSql (m3j's library sql): see Sql

    public static Object M3jSql__NewProperties() {
        return Sql.newProperties();
    }

    public static void M3jSql__SetProperty(Object p, String name, String value) {
        Sql.setProperty(p, name, value);
    }

    public static Object M3jSql__Connect(String url, Object props, String driver, double timeout, long fetch,
                                         ObjRef fail) {
        return Sql.connect(url, props, driver, timeout, fetch, fail);
    }

    public static void M3jSql__Close(Object c) {
        Sql.close(c);
    }

    public static long M3jSql__Exec(Object c, String sql, LongRef count, ObjRef fail) {
        return Sql.exec(c, sql, count, fail);
    }

    public static long M3jSql__Query(Object c, String sql, ObjRef rows, ObjRef fail) {
        return Sql.query(c, sql, rows, fail);
    }

    public static long M3jSql__Transaction(Object c, long op, ObjRef fail) {
        return Sql.transaction(c, op, fail);
    }

    public static long M3jSql__Prepare(Object c, String sql, boolean keys, ObjRef stmt, ObjRef fail) {
        return Sql.prepare(c, sql, keys, stmt, fail);
    }

    public static long M3jSql__Bind(Object s, long i, long type, long n, double x, String t, ObjRef fail) {
        return Sql.bind(s, i, type, n, x, t, fail);
    }

    public static long M3jSql__BindTime(Object s, long i, long type, long year, long month, long day, long hour,
                                        long minute, long second, long nanos, ObjRef fail) {
        return Sql.bindTime(s, i, type, year, month, day, hour, minute, second, nanos, fail);
    }

    public static long M3jSql__Add(Object s, ObjRef fail) {
        return Sql.add(s, fail);
    }

    public static long M3jSql__Execute(Object s, long op, LongRef count, ObjRef rows, ObjRef fail) {
        return Sql.execute(s, op, count, rows, fail);
    }

    public static long M3jSql__BatchCount(Object s, long i) {
        return Sql.batchCount(s, i);
    }

    public static void M3jSql__CloseStmt(Object s) {
        Sql.closeStmt(s);
    }

    public static long M3jSql__Columns(Object r) {
        return Sql.columns(r);
    }

    public static long M3jSql__Column(Object r, long i, ObjRef name, ObjRef typeName, LongRef type,
                                      LongRef precision, LongRef scale, LongRef nullable, ObjRef fail) {
        return Sql.column(r, i, name, typeName, type, precision, scale, nullable, fail);
    }

    public static long M3jSql__Find(Object r, String name) {
        return Sql.find(r, name);
    }

    public static long M3jSql__Next(Object r, ObjRef fail) {
        return Sql.next(r, fail);
    }

    public static long M3jSql__Get(Object r, long i, long type, LongRef n, DoubleRef x, ObjRef t, ObjRef fail) {
        return Sql.get(r, i, type, n, x, t, fail);
    }

    public static long M3jSql__GetTime(Object r, long i, long type, LongRef year, LongRef month, LongRef day,
                                       LongRef hour, LongRef minute, LongRef second, LongRef nanos, ObjRef fail) {
        return Sql.getTime(r, i, type, year, month, day, hour, minute, second, nanos, fail);
    }

    public static void M3jSql__CloseRows(Object r) {
        Sql.closeRows(r);
    }

    public static long M3jSql__Kind(Object f) {
        return Sql.failKind(f);
    }

    public static String M3jSql__Describe(Object f, ObjRef state, LongRef code) {
        return Sql.failText(f, state, code);
    }

    // M3jZip (m3j's modules Deflate and Zip): see Zip
    public static Object M3jZip__NewInflate(boolean raw) {
        return Zip.newInflate(raw);
    }

    public static void M3jZip__InflateFeed(Object z, byte[] b, int off, int n) {
        Zip.inflateFeed(z, b, off, n);
    }

    public static long M3jZip__Inflate(Object z, byte[] b, int off, int n) {
        return Zip.inflate(z, b, off, n);
    }

    public static boolean M3jZip__InflateNeedsInput(Object z) {
        return Zip.inflateNeedsInput(z);
    }

    public static boolean M3jZip__InflateFinished(Object z) {
        return Zip.inflateFinished(z);
    }

    public static long M3jZip__InflateRemaining(Object z) {
        return Zip.inflateRemaining(z);
    }

    public static long M3jZip__InflateTotal(Object z) {
        return Zip.inflateTotal(z);
    }

    public static void M3jZip__InflateReset(Object z) {
        Zip.inflateReset(z);
    }

    public static void M3jZip__InflateEnd(Object z) {
        Zip.inflateEnd(z);
    }

    public static void M3jZip__CrcByte(Object z, long b) {
        Zip.crcByte(z, b);
    }

    public static long M3jZip__CrcValue(Object z) {
        return Zip.crcValue(z);
    }

    public static void M3jZip__CrcReset(Object z) {
        Zip.crcReset(z);
    }

    public static Object M3jZip__NewDeflate(long level, boolean raw) {
        return Zip.newDeflate(level, raw);
    }

    public static void M3jZip__DeflateFeed(Object z, byte[] b, int off, int n) {
        Zip.deflateFeed(z, b, off, n);
    }

    public static long M3jZip__Deflate(Object z, byte[] b, int off, int n, long mode) {
        return Zip.deflate(z, b, off, n, mode);
    }

    public static boolean M3jZip__DeflateFinished(Object z) {
        return Zip.deflateFinished(z);
    }

    public static long M3jZip__DeflateCrc(Object z) {
        return Zip.deflateCrc(z);
    }

    public static long M3jZip__DeflateTotal(Object z) {
        return Zip.deflateTotal(z);
    }

    public static void M3jZip__DeflateEnd(Object z) {
        Zip.deflateEnd(z);
    }

    public static Object M3jZip__OpenArchive(String path, LongRef code) {
        return Zip.openArchive(path, code);
    }

    public static void M3jZip__CloseArchive(Object a) {
        Zip.closeArchive(a);
    }

    public static long M3jZip__ArchiveCount(Object a) {
        return Zip.archiveCount(a);
    }

    public static long M3jZip__ArchiveFind(Object a, String name) {
        return Zip.archiveFind(a, name);
    }

    public static String M3jZip__EntryName(Object a, long i) {
        return Zip.entryName(a, i);
    }

    public static long M3jZip__EntrySize(Object a, long i) {
        return Zip.entrySize(a, i);
    }

    public static long M3jZip__EntryCompressed(Object a, long i) {
        return Zip.entryCompressed(a, i);
    }

    public static long M3jZip__EntryCrc(Object a, long i) {
        return Zip.entryCrc(a, i);
    }

    public static long M3jZip__EntryTime(Object a, long i) {
        return Zip.entryTime(a, i);
    }

    public static boolean M3jZip__EntryDirectory(Object a, long i) {
        return Zip.entryDirectory(a, i);
    }

    public static Object M3jZip__EntryOpen(Object a, long i, LongRef code) {
        return Zip.entryOpen(a, i, code);
    }

    public static long M3jZip__EntryRead(Object x, byte[] b, int off, int n) {
        return Zip.entryRead(x, b, off, n);
    }

    public static void M3jZip__EntryClose(Object x) {
        Zip.entryClose(x);
    }

    public static Object M3jZip__NewWriter() {
        return Zip.newWriter();
    }

    public static long M3jZip__WriterAdd(Object w, String name, long millis, long level) {
        return Zip.writerAdd(w, name, millis, level);
    }

    public static long M3jZip__WriterWrite(Object w, byte[] b, int off, int n) {
        return Zip.writerWrite(w, b, off, n);
    }

    public static long M3jZip__WriterCloseEntry(Object w) {
        return Zip.writerCloseEntry(w);
    }

    public static long M3jZip__WriterFinish(Object w) {
        return Zip.writerFinish(w);
    }

    public static long M3jZip__WriterTake(Object w, byte[] b, int off, int n) {
        return Zip.writerTake(w, b, off, n);
    }

    // M3jCrypto (m3j's package crypto): see Crypto
    public static String M3jCrypto__Random(long n) {
        return Crypto.random(n);
    }

    public static void M3jCrypto__RandomBytes(byte[] b, int off, int n) {
        Crypto.randomBytes(b, off, n);
    }

    public static boolean M3jCrypto__Equal(String a, String b) {
        return Crypto.equal(a, b);
    }

    public static Object M3jCrypto__DigestNew(long alg) {
        return Crypto.digestNew(alg);
    }

    public static Object M3jCrypto__HmacNew(long alg, String key) {
        return Crypto.hmacNew(alg, key);
    }

    public static void M3jCrypto__DigestUpdate(Object d, byte[] b, int off, int n) {
        Crypto.digestUpdate(d, b, off, n);
    }

    public static String M3jCrypto__DigestFinish(Object d) {
        return Crypto.digestFinish(d);
    }

    public static String M3jCrypto__DigestOf(long alg, String data) {
        return Crypto.digestOf(alg, data);
    }

    public static String M3jCrypto__Hmac(long alg, String key, String data) {
        return Crypto.hmac(alg, key, data);
    }

    public static String M3jCrypto__AeadSeal(long alg, String key, String nonce, String plain, String aad,
                                             LongRef code) {
        return Crypto.aeadSeal(alg, key, nonce, plain, aad, code);
    }

    public static String M3jCrypto__AeadOpen(long alg, String key, String nonce, String sealed, String aad,
                                             LongRef code) {
        return Crypto.aeadOpen(alg, key, nonce, sealed, aad, code);
    }

    public static String M3jCrypto__Pbkdf2(long alg, String password, String salt, long iterations, long length,
                                           LongRef code) {
        return Crypto.pbkdf2(alg, password, salt, iterations, length, code);
    }

    public static String M3jCrypto__Hkdf(long alg, String ikm, String salt, String info, long length, LongRef code) {
        return Crypto.hkdf(alg, ikm, salt, info, length, code);
    }

    public static Object M3jCrypto__Ed25519Public(String raw, LongRef code) {
        return Crypto.ed25519Public(raw, code);
    }

    public static Object M3jCrypto__RsaPublic(String n, String e, LongRef code) {
        return Crypto.rsaPublic(n, e, code);
    }

    public static Object M3jCrypto__EcPublic(long curve, String x, String y, LongRef code) {
        return Crypto.ecPublic(curve, x, y, code);
    }

    public static Object M3jCrypto__PublicFromDer(String der, LongRef code) {
        return Crypto.fromDer(der, false, code);
    }

    public static Object M3jCrypto__PrivateFromDer(String der, LongRef code) {
        return Crypto.fromDer(der, true, code);
    }

    public static long M3jCrypto__KeyKind(Object k) {
        return Crypto.keyKind(k);
    }

    public static String M3jCrypto__KeyDer(Object k) {
        return Crypto.keyDer(k);
    }

    public static String M3jCrypto__PublicPart(Object k, long i) {
        return Crypto.publicPart(k, i);
    }

    public static Object M3jCrypto__Generate(long kind, long bits, ObjRef priv, LongRef code) {
        return Crypto.generate(kind, bits, priv, code);
    }

    public static String M3jCrypto__Sign(long alg, Object k, String data, LongRef code) {
        return Crypto.sign(alg, k, data, code);
    }

    public static boolean M3jCrypto__Verify(long alg, Object k, String data, String sig) {
        return Crypto.verify(alg, k, data, sig);
    }

    public static String M3jCrypto__X25519(String scalar, String u, LongRef code) {
        return Crypto.x25519(scalar, u, code);
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
