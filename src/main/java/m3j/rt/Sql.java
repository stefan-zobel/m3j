// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DataTruncation;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransactionRollbackException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.sql.Types;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The externals M3jSql of the module SQL of m3j: JDBC.
 *
 * <p>All calls of the driver run on one thread of each connection, the
 * worker: an alert of Modula-3 interrupts the thread of the caller, and a
 * driver that sees an interrupt in its input or output may close its files
 * or its connection. The caller waits for the worker; when an alert comes
 * or the time of the call is over, it cancels the statement of the call and
 * gives the worker a few seconds more to end it, then aborts the
 * connection.
 *
 * <p>Values for parameters are kept here and given to the driver on the
 * worker; rows are fetched in blocks and converted to a few Java types
 * (Boolean, Long, Double, BigDecimal, String, byte[], LocalDate, LocalTime,
 * LocalDateTime, OffsetTime, OffsetDateTime), which the getters read
 * without the driver.
 *
 * <p>A result less than 0 is the negated kind of the error, a constant of
 * the interface M3jSql; fail then has a Failure.
 */
final class Sql {
    // the kinds of errors: ORD (SQL.Kind) + 1
    static final long NO_DRIVER = 1;
    static final long CONNECTION = 2;
    static final long AUTH = 3;
    static final long SYNTAX = 4;
    static final long CONSTRAINT = 5;
    static final long ROLLBACK = 6;
    static final long DATA = 7;
    static final long TIMEOUT = 8;
    static final long NOT_SUPPORTED = 9;
    static final long NULL = 10;
    static final long TYPE = 11;
    static final long INDEX = 12;
    static final long UNBOUND = 13;
    static final long CLOSED = 14;
    static final long OTHER = 15;
    static final long ALERTED = 16;

    // the types of values: ORD (SQL.Type)
    static final int T_NULL = 0;
    static final int T_BOOLEAN = 1;
    static final int T_INTEGER = 2;
    static final int T_REAL = 3;
    static final int T_DECIMAL = 4;
    static final int T_TEXT = 5;
    static final int T_BYTES = 6;
    static final int T_DATE = 7;
    static final int T_TIME = 8;
    static final int T_TIMESTAMP = 9;
    static final int T_TIMESTAMP_ZONE = 10;
    static final int T_OTHER = 11;

    // the operations of execute
    static final long X_UPDATE = 0;
    static final long X_QUERY = 1;
    static final long X_BATCH = 2;
    static final long X_KEYS = 3;

    // the operations of transaction
    static final long AUTO_COMMIT_OFF = 0;
    static final long AUTO_COMMIT_ON = 1;
    static final long COMMIT = 2;
    static final long ROLLBACK_TX = 3;

    /** How long the worker may take to end a call after it was cancelled. */
    private static final long GRACE = 5_000_000_000L;
    /** How long a waiting thread spins before it parks. */
    private static final long SPIN = 50_000L;

    private static final Object FAILED = new Object();
    private static final Object ALERT = new Object();
    private static final Object EXPIRED = new Object();

    private static final Executor ABORTER = r -> {
        java.lang.Thread t = new java.lang.Thread(r, "m3j-sql-abort");
        t.setDaemon(true);
        t.start();
    };

    private Sql() {}

    // ---------------------------------------------------------------- failures

    /** What went wrong: the kind, and the SQLSTATE, code and text of the driver. */
    static final class Failure {
        final long kind;
        final String state;
        final long code;
        final String text;

        Failure(long kind, String state, long code, String text) {
            this.kind = kind;
            this.state = state;
            this.code = code;
            this.text = text;
        }
    }

    /** A failure found here, not by the driver. */
    private static final class Fail extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final transient Failure failure;

        Fail(long kind, String text) {
            super(text, null, false, false);
            failure = new Failure(kind, null, 0, text);
        }
    }

    private static long failed(ObjRef fail, long kind, String text) {
        fail.set(new Failure(kind, null, 0, text));
        return -kind;
    }

    private static Object failedObject(ObjRef fail, long kind, String text) {
        fail.set(new Failure(kind, null, 0, text));
        return FAILED;
    }

    /** The failure of a throwable of the driver or of a call here. */
    static Failure failure(Throwable t) {
        if (t instanceof Fail f) {
            return f.failure;
        }
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException e) {
                String text = e.getMessage();
                return new Failure(kindOf(e), e.getSQLState(), e.getErrorCode(),
                                   text != null ? text : "the database reported an error");
            }
        }
        String text = t.getMessage();
        return new Failure(OTHER, null, 0, text != null ? text : t.getClass().getName());
    }

    /** The kind of an error of the driver: by its class, its SQLSTATE, or its code for SQLite. */
    static long kindOf(SQLException e) {
        if (e instanceof SQLTimeoutException) {
            return TIMEOUT;
        } else if (e instanceof SQLIntegrityConstraintViolationException) {
            return CONSTRAINT;
        } else if (e instanceof SQLTransactionRollbackException) {
            return ROLLBACK;
        } else if (e instanceof SQLSyntaxErrorException) {
            return SYNTAX;
        } else if (e instanceof SQLInvalidAuthorizationSpecException) {
            return AUTH;
        } else if (e instanceof SQLFeatureNotSupportedException) {
            return NOT_SUPPORTED;
        } else if (e instanceof SQLDataException || e instanceof DataTruncation) {
            return DATA;
        } else if (e instanceof SQLRecoverableException || e instanceof SQLNonTransientConnectionException
                   || e instanceof SQLTransientConnectionException) {
            return CONNECTION;
        }
        String s = e.getSQLState();
        if (s != null && s.length() >= 2) {
            switch (s.substring(0, 2)) {
                case "08": return CONNECTION;
                case "22": return DATA;
                case "23": return CONSTRAINT;
                case "28": return AUTH;
                case "40": return ROLLBACK;
                case "42": return SYNTAX;
                case "0A": return NOT_SUPPORTED;
                default:
                    if (s.equals("HYT00") || s.equals("HYT01")) {
                        return TIMEOUT;
                    }
                    return OTHER;
            }
        }
        if (s == null && e.getClass().getName().equals("org.sqlite.SQLiteException")) {
            // the primary result codes of SQLite; it has no SQLSTATE
            switch (e.getErrorCode() & 0xFF) {
                case 19: return CONSTRAINT;     // SQLITE_CONSTRAINT
                case 5, 6: return TIMEOUT;      // SQLITE_BUSY, SQLITE_LOCKED
                case 23: return AUTH;           // SQLITE_AUTH
                case 18, 20: return DATA;       // SQLITE_TOOBIG, SQLITE_MISMATCH
                case 25: return INDEX;          // SQLITE_RANGE
                case 14, 26: return CONNECTION; // SQLITE_CANTOPEN, SQLITE_NOTADB
                default: return OTHER;
            }
        }
        return OTHER;
    }

    static long failKind(Object f) {
        return ((Failure) f).kind;
    }

    static String failText(Object f, ObjRef state, LongRef code) {
        Failure x = (Failure) f;
        state.set(x.state);
        code.set(x.code);
        return x.text;
    }

    // ---------------------------------------------------------------- the worker

    private interface Op {
        Object run() throws Exception;
    }

    /** A call of the worker. */
    private static final class Call {
        final Op op;
        final java.lang.Thread caller;
        final boolean last;
        volatile boolean done;
        Object value;
        Throwable thrown;

        Call(Op op, boolean last) {
            this.op = op;
            this.caller = java.lang.Thread.currentThread();
            this.last = last;
        }
    }

    /** The thread that makes all calls of the driver for one connection. */
    private static final class Worker extends java.lang.Thread {
        final LinkedBlockingQueue<Call> queue = new LinkedBlockingQueue<>();

        Worker() {
            super("m3j-sql");
            setDaemon(true);
        }

        private Call next() {
            long end = System.nanoTime() + SPIN;
            do {
                Call k = queue.poll();
                if (k != null) {
                    return k;
                }
                java.lang.Thread.onSpinWait();
            } while (System.nanoTime() < end);
            while (true) {
                try {
                    return queue.take();
                } catch (InterruptedException e) {
                    // nobody interrupts the worker on purpose
                }
            }
        }

        @Override
        public void run() {
            while (true) {
                Call k = next();
                try {
                    k.value = k.op.run();
                } catch (Throwable t) {
                    k.thrown = t;
                }
                k.done = true;
                LockSupport.unpark(k.caller);
                if (k.last) {
                    return;
                }
            }
        }
    }

    /** A connection with its worker. */
    static final class Conn {
        final ReentrantLock lock = new ReentrantLock();
        final Worker worker = new Worker();
        final long timeout;
        final int fetch;
        /** Set by the worker once connected; null until then. */
        Connection c;
        volatile boolean closed;
        /** Set by close before it cancels the current call. */
        volatile boolean closing;
        /** Whether the driver is sqlite-jdbc (see dropKeys). */
        boolean sqlite;
        /** The statement that the current call executes, for cancel. */
        volatile Statement running;

        Conn(double timeout, long fetch) {
            this.timeout = nanos(timeout);
            this.fetch = (int) Math.max(1, Math.min(fetch, 1 << 20));
            worker.start();
        }
    }

    /** Seconds as nanoseconds for a wait; 0 (no limit) if not positive. */
    private static long nanos(double seconds) {
        return seconds > 0 ? Math.max(1, (long) Math.ceil(Math.min(seconds, 1e8) * 1e9)) : 0;
    }

    /** Waits for k: null when it is done, ALERT or EXPIRED when the wait was given up. */
    private static Object await(Call k, long deadline) {
        long spin = System.nanoTime() + SPIN;
        while (!k.done && System.nanoTime() < spin) {
            java.lang.Thread.onSpinWait();
        }
        while (!k.done) {
            // an interrupt that finds no alert belongs to one taken already
            java.lang.Thread.interrupted();
            if (m3.i.Thread.alertPending()) {
                return ALERT;
            }
            if (deadline == 0) {
                LockSupport.park(k);
            } else {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    return EXPIRED;
                }
                LockSupport.parkNanos(k, left);
            }
        }
        return null;
    }

    /** Waits up to nanos for k without regard to alerts: whether it is done. */
    private static boolean awaitQuietly(Call k, long nanos) {
        long deadline = System.nanoTime() + nanos;
        while (!k.done) {
            java.lang.Thread.interrupted();
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            LockSupport.parkNanos(k, left);
        }
        return true;
    }

    /** Takes the lock of c, unless an alert comes first. */
    private static boolean lock(Conn c) {
        while (true) {
            java.lang.Thread.interrupted();
            if (m3.i.Thread.alertPending()) {
                return false;
            }
            try {
                c.lock.lockInterruptibly();
                return true;
            } catch (InterruptedException e) {
                // the loop looks for the alert
            }
        }
    }

    /** Takes the lock of c within nanos, without regard to alerts. */
    private static boolean lockQuietly(Conn c, long nanos) {
        long deadline = System.nanoTime() + nanos;
        while (true) {
            java.lang.Thread.interrupted();
            try {
                return c.lock.tryLock(Math.max(0, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                if (System.nanoTime() >= deadline) {
                    return false;
                }
            }
        }
    }

    /** Ends c at once: the worker closes the connection when its call ends. */
    private static void abort(Conn c) {
        Connection x;
        synchronized (c) {
            c.closed = true;
            x = c.c;
        }
        if (x != null) {
            try {
                x.abort(ABORTER);
            } catch (SQLException | RuntimeException | AbstractMethodError e) {
                // the worker closes it
            }
        }
        c.worker.queue.add(new Call(() -> closeConnection(c), true));
    }

    private static Object closeConnection(Conn c) {
        Connection x;
        synchronized (c) {
            c.closed = true;
            x = c.c;
        }
        if (x != null) {
            try {
                x.close();
            } catch (SQLException | RuntimeException e) {
                // nothing left to do
            }
        }
        return null;
    }

    /**
     * Runs op on the worker of c: its result, or FAILED with fail set. When
     * the wait is given up, the statement in c.running is cancelled; a call
     * that does not end within the grace (0: none) aborts the connection.
     * What a call that was given up returns is closed.
     */
    private static Object run(Conn c, Op op, long grace, ObjRef fail) {
        return run(c, op, grace, c.timeout, fail);
    }

    /** As run, with a time for the call in nanoseconds (0: none). */
    private static Object run(Conn c, Op op, long grace, long timeout, ObjRef fail) {
        if (c.closed || c.closing) {
            return failedObject(fail, CLOSED, "the connection is closed");
        }
        if (!lock(c)) {
            return failedObject(fail, ALERTED, null);
        }
        try {
            if (c.closed || c.closing) {
                return failedObject(fail, CLOSED, "the connection is closed");
            }
            c.running = null;
            Call k = new Call(op, false);
            c.worker.queue.add(k);
            Object w = await(k, timeout > 0 ? System.nanoTime() + timeout : 0);
            if (w == null) {
                c.running = null;
                if (k.thrown != null) {
                    if ((c.closed || c.closing) && !(k.thrown instanceof Fail)) {
                        // the call was cancelled by close in another thread
                        return failedObject(fail, CLOSED, "the connection was closed");
                    }
                    fail.set(failure(k.thrown));
                    return FAILED;
                }
                return k.value;
            }
            Statement s = c.running;
            if (s != null) {
                try {
                    s.cancel();
                } catch (SQLException | RuntimeException e) {
                    // the grace decides
                }
            }
            if (grace > 0 && awaitQuietly(k, grace)) {
                c.running = null;
                discard(c, k.value);
            } else {
                abort(c);
            }
            return w == ALERT ? failedObject(fail, ALERTED, null)
                              : failedObject(fail, TIMEOUT, "the call did not end in time");
        } finally {
            c.lock.unlock();
        }
    }

    /** Closes what a call that was given up made. */
    private static void discard(Conn c, Object v) {
        if (v instanceof Rows r) {
            Call k = new Call(() -> closeRowsNow(r), false);
            c.worker.queue.add(k);
            if (!awaitQuietly(k, GRACE)) {
                abort(c);
            }
        } else if (v instanceof Stmt s) {
            Call k = new Call(() -> closeStmtNow(s), false);
            c.worker.queue.add(k);
            if (!awaitQuietly(k, GRACE)) {
                abort(c);
            }
        }
    }

    /** Runs op quietly (close): no alerts, no errors; aborts c if it takes too long. */
    private static void runQuietly(Conn c, Op op) {
        if (c.closed) {
            return;
        }
        if (!lockQuietly(c, GRACE)) {
            abort(c);
            return;
        }
        try {
            if (c.closed) {
                return;
            }
            Call k = new Call(op, false);
            c.worker.queue.add(k);
            if (!awaitQuietly(k, GRACE)) {
                abort(c);
            }
        } finally {
            c.lock.unlock();
        }
    }

    // ---------------------------------------------------------------- connections

    static Object newProperties() {
        return new Properties();
    }

    static void setProperty(Object p, String name, String value) {
        if (name != null && value != null) {
            ((Properties) p).setProperty(name, value);
        }
    }

    /** The beginning of a URL up to its second colon: never a password. */
    private static String scheme(String url) {
        int a = url.indexOf(':');
        int b = a < 0 ? -1 : url.indexOf(':', a + 1);
        return b < 0 ? "this URL" : url.substring(0, b + 1);
    }

    /** The driver for url: the class given, a service of the class loader of the thread, or DriverManager. */
    private static Driver driver(String url, String className) {
        ClassLoader l = java.lang.Thread.currentThread().getContextClassLoader();
        if (l == null) {
            l = ClassLoader.getSystemClassLoader();
        }
        if (className != null) {
            try {
                Object d = Class.forName(className, true, l).getDeclaredConstructor().newInstance();
                if (d instanceof Driver x) {
                    return x;
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // reported below
            }
            throw new Fail(NO_DRIVER, "the driver " + className + " cannot be loaded");
        }
        Iterator<Driver> it = ServiceLoader.load(Driver.class, l).iterator();
        while (true) {
            try {
                if (!it.hasNext()) {
                    break;
                }
                Driver d = it.next();
                if (d.acceptsURL(url)) {
                    return d;
                }
            } catch (ServiceConfigurationError | SQLException | RuntimeException e) {
                // a broken entry of some jar: the next one
            }
        }
        try {
            return DriverManager.getDriver(url);
        } catch (SQLException e) {
            return null;
        }
    }

    static Object connect(String url, Object props, String driverClass, double connectTimeout, double timeout,
                          long fetch, ObjRef fail) {
        if (url == null) {
            failed(fail, NO_DRIVER, "no URL");
            return null;
        }
        Properties p = new Properties();
        p.putAll((Properties) props);
        Conn c = new Conn(timeout, fetch);
        Object r = run(c, () -> {
            Driver d = driver(url, driverClass);
            if (d == null) {
                throw new Fail(NO_DRIVER, "no driver for " + scheme(url));
            }
            Connection x = d.connect(url, p);
            if (x == null) {
                throw new Fail(NO_DRIVER, "the driver does not accept " + scheme(url));
            }
            synchronized (c) {
                if (c.closed) {
                    x.close();
                    return null;
                }
                c.c = x;
                c.sqlite = x.getClass().getName().startsWith("org.sqlite.");
            }
            return c;
        }, 0, nanos(connectTimeout), fail);
        if (r == FAILED) {
            boolean stop;
            synchronized (c) {
                stop = !c.closed;
                c.closed = true;
            }
            if (stop) {
                c.worker.queue.add(new Call(() -> closeConnection(c), true));
            }
            return null;
        }
        return c;
    }

    static void close(Object o) {
        Conn c = (Conn) o;
        synchronized (c) {
            if (c.closed || c.closing) {
                return;
            }
            c.closing = true;
        }
        Statement s = c.running;
        if (s != null) {
            try {
                s.cancel();
            } catch (SQLException | RuntimeException e) {
                // the grace decides
            }
        }
        if (!lockQuietly(c, GRACE)) {
            abort(c);
            return;
        }
        try {
            if (c.closed) {
                return;
            }
            Call k = new Call(() -> closeConnection(c), true);
            c.worker.queue.add(k);
            if (!awaitQuietly(k, GRACE)) {
                abort(c);
            }
        } finally {
            c.lock.unlock();
        }
    }

    private static void fetchSize(Conn c, Statement s) {
        try {
            s.setFetchSize(c.fetch);
        } catch (SQLException | RuntimeException e) {
            // only a hint
        }
    }

    static long exec(Object o, String sql, LongRef count, ObjRef fail) {
        Conn c = (Conn) o;
        if (sql == null) {
            return failed(fail, SYNTAX, "no statement");
        }
        Object r = run(c, () -> {
            try (Statement s = c.c.createStatement()) {
                c.running = s;
                return (long) s.executeUpdate(sql);
            }
        }, GRACE, fail);
        if (r == FAILED) {
            return -((Failure) fail.get()).kind;
        }
        count.set((Long) r);
        return 0;
    }

    static long query(Object o, String sql, ObjRef rows, ObjRef fail) {
        Conn c = (Conn) o;
        if (sql == null) {
            return failed(fail, SYNTAX, "no statement");
        }
        Object r = run(c, () -> {
            Statement s = c.c.createStatement();
            try {
                fetchSize(c, s);
                c.running = s;
                return new Rows(c, s.executeQuery(sql), s, s);
            } catch (SQLException | RuntimeException e) {
                s.close();
                throw e;
            }
        }, GRACE, fail);
        if (r == FAILED) {
            return -((Failure) fail.get()).kind;
        }
        rows.set(r);
        return 0;
    }

    static long transaction(Object o, long op, ObjRef fail) {
        Conn c = (Conn) o;
        Object r = run(c, () -> {
            if (op == AUTO_COMMIT_OFF || op == AUTO_COMMIT_ON) {
                c.c.setAutoCommit(op == AUTO_COMMIT_ON);
            } else if (op == COMMIT) {
                c.c.commit();
            } else {
                c.c.rollback();
            }
            return null;
        }, GRACE, fail);
        return r == FAILED ? -((Failure) fail.get()).kind : 0;
    }

    // ---------------------------------------------------------------- statements

    /** The value NULL of a parameter, with its type for the driver. */
    private record Null(int sqlType) {}

    /** A prepared statement with its values. */
    static final class Stmt {
        final Conn conn;
        final PreparedStatement ps;
        final boolean keys;
        /** The number of parameters, -1 if the driver does not tell. */
        final int params;
        Object[] values;
        final List<Object[]> batch = new ArrayList<>();
        long[] counts = new long[0];
        Rows rows;
        boolean executed;
        volatile boolean closed;

        Stmt(Conn conn, PreparedStatement ps, boolean keys, int params) {
            this.conn = conn;
            this.ps = ps;
            this.keys = keys;
            this.params = params;
            this.values = new Object[Math.max(params, 0)];
        }
    }

    static long prepare(Object o, String sql, boolean keys, ObjRef stmt, ObjRef fail) {
        Conn c = (Conn) o;
        if (sql == null) {
            return failed(fail, SYNTAX, "no statement");
        }
        Object r = run(c, () -> {
            PreparedStatement ps = c.c.prepareStatement(sql, keys ? Statement.RETURN_GENERATED_KEYS
                                                                  : Statement.NO_GENERATED_KEYS);
            int n;
            try {
                n = ps.getParameterMetaData().getParameterCount();
            } catch (SQLException | RuntimeException e) {
                n = -1;
            }
            fetchSize(c, ps);
            return new Stmt(c, ps, keys, n);
        }, GRACE, fail);
        if (r == FAILED) {
            return -((Failure) fail.get()).kind;
        }
        stmt.set(r);
        return 0;
    }

    private static int sqlType(long type) {
        return switch ((int) type) {
            case T_BOOLEAN -> Types.BOOLEAN;
            case T_INTEGER -> Types.BIGINT;
            case T_REAL -> Types.DOUBLE;
            case T_DECIMAL -> Types.DECIMAL;
            case T_TEXT -> Types.VARCHAR;
            case T_BYTES -> Types.VARBINARY;
            case T_DATE -> Types.DATE;
            case T_TIME -> Types.TIME;
            case T_TIMESTAMP -> Types.TIMESTAMP;
            case T_TIMESTAMP_ZONE -> Types.TIMESTAMP_WITH_TIMEZONE;
            default -> Types.NULL;
        };
    }

    /** Keeps value v for parameter i of s. */
    private static long put(Stmt s, long i, Object v, ObjRef fail) {
        synchronized (s) {
            if (s.closed || s.conn.closed) {
                return failed(fail, CLOSED, "the statement is closed");
            }
            if (i < 1 || s.params >= 0 && i > s.params || i > 65535) {
                return failed(fail, INDEX, "no parameter " + i);
            }
            if (i > s.values.length) {
                s.values = Arrays.copyOf(s.values, (int) i);
            }
            s.values[(int) i - 1] = v;
            return 0;
        }
    }

    static long bind(Object o, long i, long type, long n, double x, String t, ObjRef fail) {
        Stmt s = (Stmt) o;
        Object v;
        switch ((int) type) {
            case T_NULL -> v = new Null(sqlType(n));
            case T_BOOLEAN -> v = n != 0;
            case T_INTEGER -> v = n;
            case T_REAL -> v = x;
            case T_DECIMAL -> {
                if (t == null) {
                    v = new Null(Types.DECIMAL);
                } else {
                    try {
                        v = new BigDecimal(t.trim());
                    } catch (NumberFormatException e) {
                        return failed(fail, DATA, "not a decimal number");
                    }
                }
            }
            case T_TEXT -> v = t == null ? new Null(Types.VARCHAR) : t;
            case T_BYTES -> {
                if (t == null) {
                    v = new Null(Types.VARBINARY);
                } else {
                    for (int k = 0; k < t.length(); k++) {
                        if (t.charAt(k) > 255) {
                            return failed(fail, DATA, "a character above 255 among bytes");
                        }
                    }
                    v = t.getBytes(StandardCharsets.ISO_8859_1);
                }
            }
            case T_TIMESTAMP_ZONE -> {
                if (Double.isNaN(x) || Double.isInfinite(x)) {
                    return failed(fail, DATA, "not a time");
                }
                double sec = Math.floor(x);
                long nanos = Math.min(999_999_999L, Math.round((x - sec) * 1e9));
                try {
                    v = OffsetDateTime.ofInstant(Instant.ofEpochSecond((long) sec, nanos), ZoneOffset.UTC);
                } catch (DateTimeException | ArithmeticException e) {
                    return failed(fail, DATA, "the time is out of range");
                }
            }
            default -> {
                return failed(fail, TYPE, "no such type of a value");
            }
        }
        return put(s, i, v, fail);
    }

    static long bindTime(Object o, long i, long type, long year, long month, long day, long hour, long minute,
                         long second, long nanos, ObjRef fail) {
        Stmt s = (Stmt) o;
        Object v;
        try {
            switch ((int) type) {
                case T_DATE -> v = LocalDate.of(Math.toIntExact(year), (int) month, (int) day);
                case T_TIME -> v = LocalTime.of((int) hour, (int) minute, (int) second, (int) nanos);
                case T_TIMESTAMP -> v = LocalDateTime.of(Math.toIntExact(year), (int) month, (int) day, (int) hour,
                                                         (int) minute, (int) second, (int) nanos);
                default -> {
                    return failed(fail, TYPE, "no such type of a value");
                }
            }
        } catch (DateTimeException | ArithmeticException e) {
            return failed(fail, DATA, "no such date or time");
        }
        return put(s, i, v, fail);
    }

    /** The values of s for an execution: a copy, or a Fail if one is missing. */
    private static Object[] values(Stmt s) {
        synchronized (s) {
            if (s.closed) {
                throw new Fail(CLOSED, "the statement is closed");
            }
            int n = s.params >= 0 ? s.params : s.values.length;
            for (int k = 0; k < n; k++) {
                if (s.values[k] == null) {
                    throw new Fail(UNBOUND, "parameter " + (k + 1) + " has no value");
                }
            }
            return s.values.clone();
        }
    }

    private static void apply(PreparedStatement ps, Object[] v) throws SQLException {
        for (int k = 0; k < v.length; k++) {
            int i = k + 1;
            Object x = v[k];
            if (x instanceof Null z) {
                ps.setNull(i, z.sqlType());
            } else if (x instanceof Boolean b) {
                ps.setBoolean(i, b);
            } else if (x instanceof Long l) {
                ps.setLong(i, l);
            } else if (x instanceof Double d) {
                ps.setDouble(i, d);
            } else if (x instanceof BigDecimal d) {
                ps.setBigDecimal(i, d);
            } else if (x instanceof String t) {
                ps.setString(i, t);
            } else if (x instanceof byte[] b) {
                ps.setBytes(i, b);
            } else {
                ps.setObject(i, x);
            }
        }
    }

    static long add(Object o, ObjRef fail) {
        Stmt s = (Stmt) o;
        try {
            Object[] v = values(s);
            synchronized (s) {
                s.batch.add(v);
            }
            return 0;
        } catch (Fail f) {
            fail.set(f.failure);
            return -f.failure.kind;
        }
    }

    static long execute(Object o, long op, LongRef count, ObjRef rows, ObjRef fail) {
        Stmt s = (Stmt) o;
        Conn c = s.conn;
        Object[] v = null;
        List<Object[]> batch = null;
        try {
            if (op == X_UPDATE || op == X_QUERY) {
                v = values(s);
            } else if (op == X_BATCH) {
                synchronized (s) {
                    if (s.closed) {
                        throw new Fail(CLOSED, "the statement is closed");
                    }
                    batch = new ArrayList<>(s.batch);
                    s.batch.clear();
                }
            } else if (s.closed) {
                throw new Fail(CLOSED, "the statement is closed");
            } else if (!s.keys) {
                throw new Fail(OTHER, "the statement was not prepared for generated keys");
            } else if (!s.executed) {
                throw new Fail(OTHER, "the statement has not been executed");
            }
        } catch (Fail f) {
            fail.set(f.failure);
            return -f.failure.kind;
        }
        Object[] values = v;
        List<Object[]> list = batch;
        Object r = run(c, () -> {
            PreparedStatement ps = s.ps;
            if (op == X_KEYS) {
                return new Rows(c, ps.getGeneratedKeys(), null, null);
            }
            Rows old = s.rows;
            if (old != null) {
                old.closed = true;
                s.rows = null;
            }
            if (op == X_BATCH) {
                try {
                    for (Object[] x : list) {
                        apply(ps, x);
                        ps.addBatch();
                    }
                    c.running = ps;
                    int[] n = ps.executeBatch();
                    dropKeys(s);
                    long[] counts = new long[n.length];
                    for (int k = 0; k < n.length; k++) {
                        counts[k] = n[k] >= 0 ? n[k] : -1;
                    }
                    s.counts = counts;
                    s.executed = true;
                    return (long) n.length;
                } finally {
                    try {
                        ps.clearBatch();
                    } catch (SQLException | RuntimeException e) {
                        // the batch is gone with the statement
                    }
                }
            }
            apply(ps, values);
            c.running = ps;
            if (op == X_QUERY) {
                Rows x = new Rows(c, ps.executeQuery(), ps, null);
                s.rows = x;
                s.executed = true;
                return x;
            }
            long n = ps.executeUpdate();
            dropKeys(s);
            s.executed = true;
            return n;
        }, GRACE, fail);
        if (r == FAILED) {
            return -((Failure) fail.get()).kind;
        }
        if (r instanceof Rows x) {
            rows.set(x);
        } else {
            count.set((Long) r);
        }
        return 0;
    }

    /**
     * sqlite-jdbc runs a query for the generated keys after each INSERT,
     * also when no keys were asked for, and keeps it open. An open query
     * keeps a cancel (sqlite3_interrupt) in effect: every later statement of
     * the connection would fail. So it is closed here unless the keys are
     * wanted.
     */
    private static void dropKeys(Stmt s) {
        if (s.conn.sqlite && !s.keys) {
            try {
                ResultSet k = s.ps.getGeneratedKeys();
                if (k != null) {
                    k.close();
                }
            } catch (SQLException | RuntimeException e) {
                // nothing open
            }
        }
    }

    static long batchCount(Object o, long i) {
        Stmt s = (Stmt) o;
        long[] n = s.counts;
        return i >= 0 && i < n.length ? n[(int) i] : -1;
    }

    private static Object closeStmtNow(Stmt s) {
        s.closed = true;
        if (s.rows != null) {
            s.rows.closed = true;
        }
        try {
            s.ps.close();
        } catch (SQLException | RuntimeException e) {
            // nothing left to do
        }
        return null;
    }

    static void closeStmt(Object o) {
        Stmt s = (Stmt) o;
        synchronized (s) {
            if (s.closed) {
                return;
            }
        }
        runQuietly(s.conn, () -> closeStmtNow(s));
        s.closed = true;
    }

    // ---------------------------------------------------------------- rows

    /** A result: its columns and the block of rows fetched last. */
    static final class Rows {
        final Conn conn;
        final ResultSet rs;
        /** The statement to cancel while rows are fetched. */
        final Statement stmt;
        /** The statement to close with the rows, if the rows own it. */
        final Statement owned;
        final int n;
        final int[] types;
        final String[] names;
        final String[] typeNames;
        final int[] precision;
        final int[] scale;
        final int[] nullable;
        Object[][] block = new Object[0][];
        int count;
        int pos = -1;
        boolean end;
        volatile boolean closed;

        Rows(Conn conn, ResultSet rs, Statement stmt, Statement owned) throws SQLException {
            this.conn = conn;
            this.rs = rs;
            this.stmt = stmt;
            this.owned = owned;
            ResultSetMetaData md = rs.getMetaData();
            n = md == null ? 0 : md.getColumnCount();
            types = new int[n];
            names = new String[n];
            typeNames = new String[n];
            precision = new int[n];
            scale = new int[n];
            nullable = new int[n];
            for (int k = 0; k < n; k++) {
                int i = k + 1;
                types[k] = md.getColumnType(i);
                names[k] = md.getColumnLabel(i);
                typeNames[k] = md.getColumnTypeName(i);
                precision[k] = md.getPrecision(i);
                scale[k] = md.getScale(i);
                nullable[k] = md.isNullable(i);
            }
        }

        /** The value of column i of the current row. */
        Object value(long i) {
            if (closed || conn.closed) {
                throw new Fail(CLOSED, "the rows are closed");
            }
            if (i < 1 || i > n) {
                throw new Fail(INDEX, "no column " + i);
            }
            if (pos < 0 || pos >= count) {
                throw new Fail(OTHER, "no current row");
            }
            return block[pos][(int) i - 1];
        }
    }

    /** The value of column i of the current row of rs, converted. */
    private static Object read(ResultSet rs, int i, int type) throws SQLException {
        switch (type) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
                 Types.CLOB, Types.NCLOB:
                return rs.getString(i);
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB:
                return rs.getBytes(i);
            case Types.DATE:
                return temporal(rs, i, LocalDate.class);
            case Types.TIME:
                return temporal(rs, i, LocalTime.class);
            case Types.TIME_WITH_TIMEZONE:
                return temporal(rs, i, OffsetTime.class);
            case Types.TIMESTAMP:
                return temporal(rs, i, LocalDateTime.class);
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return temporal(rs, i, OffsetDateTime.class);
            default:
                return normal(rs.getObject(i));
        }
    }

    private static Object temporal(ResultSet rs, int i, Class<?> k) throws SQLException {
        try {
            return rs.getObject(i, k);
        } catch (SQLException | RuntimeException e) {
            return normal(rs.getObject(i));
        }
    }

    /** A value of the driver as one of the few types that the getters know. */
    private static Object normal(Object o) throws SQLException {
        if (o == null || o instanceof Boolean || o instanceof Long || o instanceof Double || o instanceof BigDecimal
            || o instanceof String || o instanceof byte[] || o instanceof LocalDate || o instanceof LocalTime
            || o instanceof LocalDateTime || o instanceof OffsetDateTime || o instanceof OffsetTime) {
            return o;
        } else if (o instanceof Integer || o instanceof Short || o instanceof Byte) {
            return ((Number) o).longValue();
        } else if (o instanceof Float f) {
            return Double.parseDouble(f.toString());
        } else if (o instanceof BigInteger b) {
            return new BigDecimal(b);
        } else if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        } else if (o instanceof java.sql.Time t) {
            return t.toLocalTime();
        } else if (o instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime();
        } else if (o instanceof ZonedDateTime z) {
            return z.toOffsetDateTime();
        } else if (o instanceof Instant t) {
            return t.atOffset(ZoneOffset.UTC);
        } else if (o instanceof Clob c) {
            long n = c.length();
            return c.getSubString(1, (int) Math.min(n, Integer.MAX_VALUE));
        } else if (o instanceof Blob b) {
            long n = b.length();
            return b.getBytes(1, (int) Math.min(n, Integer.MAX_VALUE));
        }
        return o.toString();
    }

    /** Fetches the next block of r on the worker. */
    private static Object fetch(Rows r) throws SQLException {
        r.conn.running = r.stmt;
        int max = r.conn.fetch;
        List<Object[]> got = new ArrayList<>(Math.min(max, 1024));
        boolean end = false;
        while (got.size() < max) {
            if (!r.rs.next()) {
                end = true;
                break;
            }
            Object[] row = new Object[r.n];
            for (int k = 0; k < r.n; k++) {
                row[k] = read(r.rs, k + 1, r.types[k]);
            }
            got.add(row);
        }
        if (end) {
            release(r);
        }
        synchronized (r) {
            r.block = got.toArray(new Object[0][]);
            r.count = r.block.length;
            r.pos = -1;
            r.end = end;
        }
        return null;
    }

    private static Object closeRowsNow(Rows r) {
        r.closed = true;
        release(r);
        return null;
    }

    /** Closes the result of the driver and the statement that r owns. */
    private static void release(Rows r) {
        try {
            r.rs.close();
        } catch (SQLException | RuntimeException e) {
            // nothing left to do
        }
        if (r.owned != null) {
            try {
                r.owned.close();
            } catch (SQLException | RuntimeException e) {
                // nothing left to do
            }
        }
    }

    static long columns(Object o) {
        return ((Rows) o).n;
    }

    static long column(Object o, long i, ObjRef name, ObjRef typeName, LongRef type, LongRef precision,
                       LongRef scale, LongRef nullable, ObjRef fail) {
        Rows r = (Rows) o;
        if (i < 1 || i > r.n) {
            return failed(fail, INDEX, "no column " + i);
        }
        int k = (int) i - 1;
        name.set(r.names[k]);
        typeName.set(r.typeNames[k]);
        type.set(typeOf(r.types[k]));
        precision.set(r.precision[k]);
        scale.set(r.scale[k]);
        nullable.set(r.nullable[k] == ResultSetMetaData.columnNoNulls ? 0
                     : r.nullable[k] == ResultSetMetaData.columnNullable ? 1 : 2);
        return 0;
    }

    /** The type of a column for SQL.Type. */
    static long typeOf(int t) {
        return switch (t) {
            case Types.NULL -> T_NULL;
            case Types.BOOLEAN, Types.BIT -> T_BOOLEAN;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> T_INTEGER;
            case Types.REAL, Types.FLOAT, Types.DOUBLE -> T_REAL;
            case Types.NUMERIC, Types.DECIMAL -> T_DECIMAL;
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
                 Types.CLOB, Types.NCLOB -> T_TEXT;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> T_BYTES;
            case Types.DATE -> T_DATE;
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> T_TIME;
            case Types.TIMESTAMP -> T_TIMESTAMP;
            case Types.TIMESTAMP_WITH_TIMEZONE -> T_TIMESTAMP_ZONE;
            default -> T_OTHER;
        };
    }

    static long find(Object o, String name) {
        Rows r = (Rows) o;
        if (name != null) {
            for (int k = 0; k < r.n; k++) {
                if (name.equalsIgnoreCase(r.names[k])) {
                    return k + 1;
                }
            }
        }
        return 0;
    }

    /** 1 with the next row current, 0 at the end. */
    static long next(Object o, ObjRef fail) {
        Rows r = (Rows) o;
        synchronized (r) {
            if (r.closed || r.conn.closed) {
                return failed(fail, CLOSED, "the rows are closed");
            }
            if (r.pos + 1 < r.count) {
                r.pos++;
                return 1;
            }
            if (r.end) {
                r.pos = r.count;
                return 0;
            }
        }
        Object x = run(r.conn, () -> {
            if (r.closed) {
                throw new Fail(CLOSED, "the rows are closed");
            }
            return fetch(r);
        }, GRACE, fail);
        if (x == FAILED) {
            return -((Failure) fail.get()).kind;
        }
        synchronized (r) {
            if (r.count > 0) {
                r.pos = 0;
                return 1;
            }
            r.pos = 0;
            return 0;
        }
    }

    static void closeRows(Object o) {
        Rows r = (Rows) o;
        if (r.closed) {
            return;
        }
        synchronized (r) {
            if (r.end) {
                r.closed = true;
                return;
            }
        }
        runQuietly(r.conn, () -> closeRowsNow(r));
        r.closed = true;
    }

    // ---------------------------------------------------------------- getters

    private static String text(Object v) {
        if (v instanceof String s) {
            return s;
        } else if (v instanceof BigDecimal d) {
            return d.toPlainString();
        } else if (v instanceof Boolean b) {
            return b ? "TRUE" : "FALSE";
        }
        return v.toString();
    }

    /** A temporal value of a text, as SQLite keeps them; null if it is none. */
    private static Object parse(String s) {
        String u = s.trim();
        if (u.length() > 10 && u.charAt(10) == ' ') {
            u = u.substring(0, 10) + 'T' + u.substring(11);
        }
        try {
            return OffsetDateTime.parse(u);
        } catch (DateTimeParseException e) {
            // the next form
        }
        try {
            return LocalDateTime.parse(u);
        } catch (DateTimeParseException e) {
            // the next form
        }
        try {
            return LocalDate.parse(u);
        } catch (DateTimeParseException e) {
            // the next form
        }
        try {
            return OffsetTime.parse(u);
        } catch (DateTimeParseException e) {
            // the next form
        }
        try {
            return LocalTime.parse(u);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static long wrong(ObjRef fail, Object v, String what) {
        return failed(fail, TYPE, "a value of " + kindName(v) + " is not " + what);
    }

    private static String kindName(Object v) {
        if (v instanceof Boolean) {
            return "a boolean";
        } else if (v instanceof Long) {
            return "an integer";
        } else if (v instanceof Double) {
            return "a real number";
        } else if (v instanceof BigDecimal) {
            return "a decimal number";
        } else if (v instanceof String) {
            return "a text";
        } else if (v instanceof byte[]) {
            return "bytes";
        }
        return "a date or time";
    }

    /**
     * The value of column i as type: T_NULL gives n = 1 for NULL, else 0;
     * T_BOOLEAN and T_INTEGER give n, T_REAL and T_TIMESTAMP_ZONE (seconds
     * since 1970) give x, T_DECIMAL, T_TEXT and T_BYTES give t (null for
     * NULL).
     */
    static long get(Object o, long i, long type, LongRef n, DoubleRef x, ObjRef t, ObjRef fail) {
        Rows r = (Rows) o;
        Object v;
        synchronized (r) {
            try {
                v = r.value(i);
            } catch (Fail f) {
                fail.set(f.failure);
                return -f.failure.kind;
            }
        }
        if (type == T_NULL) {
            n.set(v == null ? 1 : 0);
            return 0;
        }
        if (v == null) {
            if (type == T_DECIMAL || type == T_TEXT || type == T_BYTES) {
                t.set(null);
                return 0;
            }
            return failed(fail, NULL, "column " + i + " is NULL");
        }
        switch ((int) type) {
            case T_BOOLEAN -> {
                if (v instanceof Boolean b) {
                    n.set(b ? 1 : 0);
                } else if (v instanceof Long l && (l == 0 || l == 1)) {
                    n.set(l);
                } else {
                    return wrong(fail, v, "a boolean");
                }
            }
            case T_INTEGER -> {
                if (v instanceof Long l) {
                    n.set(l);
                } else if (v instanceof BigDecimal d) {
                    try {
                        n.set(d.longValueExact());
                    } catch (ArithmeticException e) {
                        return wrong(fail, v, "an integer in range");
                    }
                } else {
                    return wrong(fail, v, "an integer");
                }
            }
            case T_REAL -> {
                if (v instanceof Number d) {
                    x.set(d.doubleValue());
                } else {
                    return wrong(fail, v, "a number");
                }
            }
            case T_DECIMAL -> {
                if (v instanceof Long || v instanceof BigDecimal) {
                    t.set(text(v));
                } else if (v instanceof Double d && !d.isNaN() && !d.isInfinite()) {
                    t.set(new BigDecimal(d.toString()).toPlainString());
                } else {
                    return wrong(fail, v, "a number");
                }
            }
            case T_TEXT -> {
                if (v instanceof byte[]) {
                    return wrong(fail, v, "a text");
                }
                t.set(text(v));
            }
            case T_BYTES -> {
                if (v instanceof byte[] b) {
                    t.set(new String(b, StandardCharsets.ISO_8859_1));
                } else {
                    return wrong(fail, v, "bytes");
                }
            }
            case T_TIMESTAMP_ZONE -> {
                Object w = v instanceof String s ? parse(s) : v;
                if (w instanceof OffsetDateTime d) {
                    x.set(d.toEpochSecond() + d.getNano() / 1e9);
                } else {
                    return wrong(fail, v, "a time with a time zone");
                }
            }
            default -> {
                return failed(fail, TYPE, "no such type of a value");
            }
        }
        return 0;
    }

    /** The value of column i as a date (T_DATE), a time of day (T_TIME) or both (T_TIMESTAMP). */
    static long getTime(Object o, long i, long type, LongRef year, LongRef month, LongRef day, LongRef hour,
                        LongRef minute, LongRef second, LongRef nanos, ObjRef fail) {
        Rows r = (Rows) o;
        Object v;
        synchronized (r) {
            try {
                v = r.value(i);
            } catch (Fail f) {
                fail.set(f.failure);
                return -f.failure.kind;
            }
        }
        if (v == null) {
            return failed(fail, NULL, "column " + i + " is NULL");
        }
        Object w = v instanceof String s ? parse(s) : v;
        LocalDate d = null;
        LocalTime t = null;
        if (w instanceof LocalDate x) {
            d = x;
        } else if (w instanceof LocalDateTime x) {
            d = x.toLocalDate();
            t = x.toLocalTime();
        } else if (w instanceof OffsetDateTime x) {
            d = x.toLocalDate();
            t = x.toLocalTime();
        } else if (w instanceof LocalTime x) {
            t = x;
        } else if (w instanceof OffsetTime x) {
            t = x.toLocalTime();
        }
        if (type == T_DATE && d == null) {
            return wrong(fail, v, "a date");
        } else if (type == T_TIME && t == null) {
            return wrong(fail, v, "a time of day");
        } else if (type == T_TIMESTAMP && d == null) {
            return wrong(fail, v, "a date and time");
        } else if (type != T_DATE && type != T_TIME && type != T_TIMESTAMP) {
            return failed(fail, TYPE, "no such type of a value");
        }
        if (d != null) {
            year.set(d.getYear());
            month.set(d.getMonthValue());
            day.set(d.getDayOfMonth());
        }
        if (t == null) {
            t = LocalTime.MIDNIGHT;
        }
        hour.set(t.getHour());
        minute.set(t.getMinute());
        second.set(t.getSecond());
        nanos.set(t.getNano());
        return 0;
    }
}
