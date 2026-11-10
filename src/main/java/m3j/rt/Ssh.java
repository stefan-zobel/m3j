// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import m3j.jsch.ChannelExec;
import m3j.jsch.ChannelSftp;
import m3j.jsch.HostKey;
import m3j.jsch.HostKeyRepository;
import m3j.jsch.JSch;
import m3j.jsch.JSchAlgoNegoFailException;
import m3j.jsch.JSchChangedHostKeyException;
import m3j.jsch.JSchException;
import m3j.jsch.JSchRevokedHostKeyException;
import m3j.jsch.JSchUnknownHostKeyException;
import m3j.jsch.KeyPair;
import m3j.jsch.Session;
import m3j.jsch.SftpATTRS;
import m3j.jsch.SftpException;
import m3j.jsch.UIKeyboardInteractive;
import m3j.jsch.UserInfo;

/**
 * The externals M3jSsh of the library ssh of m3j (see Externals): sessions,
 * commands and SFTP on JSch, in the package m3j.jsch.
 *
 * <p>The session thread of JSch carries the data of all channels of a
 * session, so nothing here lets it block: the output of a command goes into
 * buffers of this class, never into the pipes of JSch, and the input of a
 * command comes from a queue that a thread of JSch reads.
 *
 * <p>Each SFTP session has a thread of its own that makes all the calls into
 * JSch: the channel of SFTP reads its replies from a pipe of the platform,
 * which fails once the thread that last read it has ended, and an interrupt
 * in the middle of a reply would leave the rest of it for the next request.
 * A caller only waits for that thread; an alert or a timeout ends the wait,
 * and the operation ends in the background.
 *
 * <p>The texts of errors are fixed, except that a key of a host shows its
 * fingerprint: the module makes atoms of the texts, which are never freed.
 *
 * <p>A wait ends with ALERTED if the thread has a pending alert of
 * Modula-3, which the caller takes. Results less than 0 are the negated
 * numbers below, the constants of the interface M3jSsh.
 */
final class Ssh {
    static final long TIMEOUT = 1;
    static final long ALERTED = 2;
    static final long REFUSED = 3;
    static final long DISCONNECTED = 4;
    static final long AUTH_FAILED = 5;
    static final long HOST_KEY_UNKNOWN = 6;
    static final long HOST_KEY_CHANGED = 7;
    static final long HOST_KEY_REVOKED = 8;
    static final long BAD_KEY = 9;
    static final long BUSY = 10;
    static final long TOO_LARGE = 11;
    static final long FAILURE = 12;
    static final long NO_SUCH_FILE = 13;
    static final long PERMISSION_DENIED = 14;
    static final long EXISTS = 15;

    /** The checks of the key of a host. */
    static final long KNOWN_HOSTS = 0;
    static final long ACCEPT_NEW = 1;
    static final long FINGERPRINT = 2;

    /** The kinds of a file. */
    static final long FILE = 0;
    static final long DIRECTORY = 1;
    static final long LINK = 2;
    static final long OTHER = 3;

    /** The operations of SftpOp. */
    static final long MKDIR = 0;
    static final long RMDIR = 1;
    static final long REMOVE = 2;
    static final long RENAME = 3;
    static final long SYMLINK = 4;
    static final long READLINK = 5;
    static final long REALPATH = 6;
    static final long CHMOD = 7;
    static final long SET_TIMES = 8;

    /** How many pieces of input to a command may wait for the channel. */
    private static final int INPUT_PIECES = 16;

    private static final String KEX = "mlkem768x25519-sha256,curve25519-sha256,curve25519-sha256@libssh.org,"
        + "ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,"
        + "diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha256";
    private static final String KEYS = "ssh-ed25519-cert-v01@openssh.com,ecdsa-sha2-nistp256-cert-v01@openssh.com,"
        + "ecdsa-sha2-nistp384-cert-v01@openssh.com,ecdsa-sha2-nistp521-cert-v01@openssh.com,"
        + "rsa-sha2-512-cert-v01@openssh.com,rsa-sha2-256-cert-v01@openssh.com,ssh-ed25519,ecdsa-sha2-nistp256,"
        + "ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256";
    private static final String CIPHERS =
        "aes128-gcm@openssh.com,aes256-gcm@openssh.com,aes128-ctr,aes192-ctr,aes256-ctr";
    private static final String MACS = "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com,"
        + "hmac-sha1-etm@openssh.com,hmac-sha2-256,hmac-sha2-512,hmac-sha1";

    /**
     * The configuration of every session: the lists of algorithms are fixed
     * here, so that the properties "jsch.*" of the system have no effect.
     */
    private static final String[][] CONFIG = {
        {"kex", KEX},
        {"server_host_key", KEYS},
        {"ca_signature_algorithms",
         "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256"},
        {"prefer_known_host_key_types", "yes"},
        {"enable_strict_kex", "yes"},
        {"require_strict_kex", "no"},
        {"enable_server_sig_algs", "yes"},
        {"enable_ext_info_in_auth", "yes"},
        {"cipher.s2c", CIPHERS},
        {"cipher.c2s", CIPHERS},
        {"mac.s2c", MACS},
        {"mac.c2s", MACS},
        {"compression.s2c", "none"},
        {"compression.c2s", "none"},
        {"lang.s2c", ""},
        {"lang.c2s", ""},
        {"dhgex_min", "2048"},
        {"dhgex_max", "8192"},
        {"dhgex_preferred", "3072"},
        {"StrictHostKeyChecking", "yes"},
        {"HashKnownHosts", "no"},
        {"PreferredAuthentications", "publickey,keyboard-interactive,password"},
        {"PubkeyAcceptedAlgorithms", KEYS},
        {"enable_pubkey_auth_query", "yes"},
        {"try_additional_pubkey_algorithms", "yes"},
        {"enable_auth_none", "yes"},
        {"use_sftp_write_flush_workaround", "yes"},
        {"CheckCiphers", "chacha20-poly1305@openssh.com"},
        {"CheckMacs", ""},
        {"CheckKexes", "mlkem768x25519-sha256,curve25519-sha256,curve25519-sha256@libssh.org"},
        {"CheckSignatures", "ssh-ed25519"},
        {"MaxAuthTries", "6"},
        {"ClearAllForwardings", "yes"},
        {"host_certificate_to_key_fallback", "yes"},
    };

    private Ssh() {}

    private static long millis(double seconds) {
        return Math.max(1, (long) Math.ceil(seconds * 1000));
    }

    /** A timeout in milliseconds for JSch, where 0 means none. */
    private static int jschMillis(double seconds) {
        return seconds > 0 ? (int) Math.min(Integer.MAX_VALUE, millis(seconds)) : 0;
    }

    private static byte[] latin1(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean has(Throwable t, Class<?> k) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (k.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    private static long fail(long code, String text, ObjRef err) {
        err.set(text);
        return -code;
    }

    /** The fingerprint of a key as ssh-keygen -l shows it: SHA256: and the digest in Base64 without padding. */
    static String fingerprint(byte[] blob) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(blob);
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Waits until f is done: 0, -TIMEOUT after timeout seconds if that is positive, or -ALERTED. */
    private static long await(Future<?> f, double timeout) {
        long deadline = timeout > 0 ? System.nanoTime() + (long) (timeout * 1e9) : 0;
        while (true) {
            // an interrupt that finds no alert belongs to one taken already
            java.lang.Thread.interrupted();
            if (m3.i.Thread.alertPending()) {
                return -ALERTED;
            }
            try {
                if (timeout > 0) {
                    long rest = deadline - System.nanoTime();
                    if (rest <= 0) {
                        return f.isDone() ? 0 : -TIMEOUT;
                    }
                    f.get(rest, TimeUnit.NANOSECONDS);
                } else {
                    f.get();
                }
                return 0;
            } catch (InterruptedException e) {
                // the loop looks for the alert
            } catch (TimeoutException e) {
                return -TIMEOUT;
            } catch (ExecutionException | CancellationException e) {
                return 0;
            }
        }
    }

    // sessions

    /** A session and what belongs to it. */
    private static final class Conn {
        final JSch jsch = new JSch();
        Session session;
        Hosts hosts;
        final List<Sftp> sftps = new ArrayList<>();
    }

    /**
     * The keys of hosts, as the check of the options wants them: those of
     * a file known_hosts, those that are added to it on first sight, or a
     * single fingerprint.
     */
    private static final class Hosts implements HostKeyRepository {
        private final long mode;
        private final HostKeyRepository known;
        private final String file;
        private final String want;
        volatile String seen;
        volatile boolean unwritable;

        Hosts(long mode, HostKeyRepository known, String file, String want) {
            this.mode = mode;
            this.known = known;
            this.file = file;
            this.want = want;
        }

        @Override
        public int check(String host, byte[] key) {
            seen = fingerprint(key);
            if (mode == FINGERPRINT) {
                return seen.equals(want) ? OK : CHANGED;
            }
            int r = known.check(host, key);
            if (r == NOT_INCLUDED && mode == ACCEPT_NEW) {
                if (append(host, key)) {
                    return OK;
                }
                unwritable = true;
            }
            return r;
        }

        /** Adds a line for the key to the file, which is never written anew. */
        private boolean append(String host, byte[] key) {
            String type = keyType(key);
            if (type == null) {
                return false;
            }
            String line = host + " " + type + " " + Base64.getEncoder().encodeToString(key) + "\n";
            try {
                Path p = Path.of(file);
                Path dir = p.toAbsolutePath().getParent();
                if (dir != null) {
                    Files.createDirectories(dir);
                }
                if (Files.exists(p) && Files.size(p) > 0) {
                    try (RandomAccessFile f = new RandomAccessFile(p.toFile(), "r")) {
                        f.seek(f.length() - 1);
                        if (f.read() != '\n') {
                            line = "\n" + line;
                        }
                    }
                }
                Files.write(p, line.getBytes(StandardCharsets.US_ASCII), StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                return true;
            } catch (IOException | RuntimeException e) {
                return false;
            }
        }

        /** The name of the type at the start of the blob of a key. */
        private static String keyType(byte[] key) {
            if (key.length < 4) {
                return null;
            }
            int n = ((key[0] & 0xff) << 24) | ((key[1] & 0xff) << 16) | ((key[2] & 0xff) << 8) | (key[3] & 0xff);
            if (n <= 0 || n > key.length - 4) {
                return null;
            }
            String t = new String(key, 4, n, StandardCharsets.US_ASCII);
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c <= ' ' || c > '~') {
                    return null;
                }
            }
            return t;
        }

        @Override
        public void add(HostKey hostkey, UserInfo ui) {
            // keys are added by check alone
        }

        @Override
        public void remove(String host, String type) {
            // the file is never changed but by appending
        }

        @Override
        public void remove(String host, String type, byte[] key) {
            // the file is never changed but by appending
        }

        @Override
        public String getKnownHostsRepositoryID() {
            return file;
        }

        @Override
        public HostKey[] getHostKey() {
            return known == null ? new HostKey[0] : known.getHostKey();
        }

        @Override
        public HostKey[] getHostKey(String host, String type) {
            return known == null ? new HostKey[0] : known.getHostKey(host, type);
        }
    }

    /** What JSch asks of a user: the password, for keyboard-interactive as well. */
    private static final class Answers implements UserInfo, UIKeyboardInteractive {
        private final String password;

        Answers(String password) {
            this.password = password;
        }

        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            return false;
        }

        @Override
        public void showMessage(String message) {
            // nobody to show it to
        }

        @Override
        public String[] promptKeyboardInteractive(String destination, String name, String instruction,
                                                  String[] prompt, boolean[] echo) {
            if (password == null || prompt.length > 1) {
                return null;
            }
            return prompt.length == 0 ? new String[0] : new String[] {password};
        }
    }

    /** Checks the key and adds it to the identities of c: 0, or BAD_KEY with err set. */
    private static long addKey(Conn c, String keyFile, String key, String passphrase, ObjRef err) {
        byte[] pass = passphrase == null ? null : utf8(passphrase);
        KeyPair kp;
        try {
            kp = keyFile != null ? KeyPair.load(c.jsch, keyFile) : KeyPair.load(c.jsch, latin1(key), null);
        } catch (JSchException | RuntimeException | LinkageError e) {
            return fail(BAD_KEY, keyFile != null && has(e, IOException.class) ? "the file of the key cannot be read"
                                                                              : "the key cannot be used", err);
        }
        try {
            if (kp.isEncrypted()) {
                if (pass == null) {
                    return fail(BAD_KEY, "the key needs a passphrase", err);
                }
                if (!kp.decrypt(pass)) {
                    return fail(BAD_KEY, "the passphrase does not fit the key", err);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            return fail(BAD_KEY, "the key cannot be used", err);
        } finally {
            kp.dispose();
        }
        try {
            if (keyFile != null) {
                c.jsch.addIdentity(keyFile, pass);
            } else {
                c.jsch.addIdentity("key", latin1(key), null, pass);
            }
        } catch (JSchException | RuntimeException | LinkageError e) {
            return fail(BAD_KEY, "the key cannot be used", err);
        }
        return 0;
    }

    static Object connect(String host, long port, String user, String password, String keyFile, String key,
                          String passphrase, long hostCheck, String knownHosts, String fingerprint, double timeout,
                          double keepAlive, LongRef code, ObjRef err) {
        Conn c = new Conn();
        if (keyFile != null || key != null) {
            long r = addKey(c, keyFile, key, passphrase, err);
            if (r < 0) {
                code.set(-r);
                return null;
            }
        }
        HostKeyRepository known = null;
        String file = knownHosts;
        if (file == null) {
            file = System.getProperty("user.home") + File.separator + ".ssh" + File.separator + "known_hosts";
        }
        if (hostCheck != FINGERPRINT) {
            try {
                if (new File(file).exists()) {
                    c.jsch.setKnownHosts(file);
                } else {
                    c.jsch.setKnownHosts(new ByteArrayInputStream(new byte[0]));
                }
                known = c.jsch.getHostKeyRepository();
            } catch (JSchException e) {
                code.set(FAILURE);
                err.set("the file of known hosts cannot be read");
                return null;
            }
        }
        Session s;
        try {
            s = c.jsch.getSession(user != null ? user : System.getProperty("user.name"), host, (int) port);
        } catch (JSchException e) {
            code.set(FAILURE);
            err.set("the session cannot be made");
            return null;
        }
        for (String[] kv : CONFIG) {
            s.setConfig(kv[0], kv[1]);
        }
        c.hosts = new Hosts(hostCheck, known, file, fingerprint);
        s.setHostKeyRepository(c.hosts);
        if (password != null) {
            s.setPassword(utf8(password));
        }
        s.setUserInfo(new Answers(password));
        s.setDaemonThread(true);
        if (keepAlive > 0) {
            try {
                s.setServerAliveInterval(jschMillis(keepAlive));
            } catch (JSchException e) {
                // only for a session that is connected already
            }
            s.setServerAliveCountMax(3);
        }
        c.session = s;
        CompletableFuture<Void> done = new CompletableFuture<>();
        java.lang.Thread t = new java.lang.Thread(() -> {
            try {
                s.connect(jschMillis(timeout));
                done.complete(null);
            } catch (Throwable e) {
                done.completeExceptionally(e);
            }
        }, "ssh connect " + host);
        t.setDaemon(true);
        t.start();
        long r = await(done, 0);
        if (r < 0) {
            s.disconnect();
            code.set(-r);
            err.set("connecting was given up");
            return null;
        }
        try {
            done.get();
        } catch (ExecutionException e) {
            s.disconnect();
            code.set(connectFailure(c, e.getCause(), err));
            return null;
        } catch (InterruptedException | CancellationException e) {
            s.disconnect();
            code.set(FAILURE);
            err.set("connecting was given up");
            return null;
        }
        return c;
    }

    /** The number of the failure t of connecting, with its text in err. */
    private static long connectFailure(Conn c, Throwable t, ObjRef err) {
        String seen = c.hosts.seen;
        if (t instanceof JSchRevokedHostKeyException) {
            err.set("the key of the host is revoked: " + seen);
            return HOST_KEY_REVOKED;
        }
        if (t instanceof JSchChangedHostKeyException) {
            err.set("the key of the host is not the known one: " + seen);
            return HOST_KEY_CHANGED;
        }
        if (t instanceof JSchUnknownHostKeyException) {
            err.set(c.hosts.unwritable ? "the key of the host cannot be added to the known hosts: " + seen
                                       : "the key of the host is not known: " + seen);
            return HOST_KEY_UNKNOWN;
        }
        if (t instanceof JSchAlgoNegoFailException) {
            err.set("the server has no algorithm in common with the client");
            return FAILURE;
        }
        String m = t.getMessage() == null ? "" : t.getMessage();
        String k = t.getClass().getName();
        if (k.equals("m3j.jsch.JSchAuthCancelException") || k.equals("m3j.jsch.JSchPartialAuthException")
            || m.startsWith("Auth fail") || m.startsWith("Auth cancel")) {
            err.set("authentication failed");
            return AUTH_FAILED;
        }
        if (has(t, UnknownHostException.class)) {
            err.set("unknown host");
            return REFUSED;
        }
        if (has(t, ConnectException.class) || has(t, NoRouteToHostException.class)) {
            err.set("no connection to the host");
            return REFUSED;
        }
        if (has(t, SocketTimeoutException.class) || m.startsWith("timeout")) {
            err.set("connecting timed out");
            return TIMEOUT;
        }
        err.set("the connection failed");
        return DISCONNECTED;
    }

    static String hostKey(Object o) {
        return ((Conn) o).hosts.seen;
    }

    static void disconnect(Object o) {
        Conn c = (Conn) o;
        c.session.disconnect();
        List<Sftp> all;
        synchronized (c.sftps) {
            all = new ArrayList<>(c.sftps);
        }
        for (Sftp f : all) {
            f.worker.shutdown();
        }
    }

    // commands

    /** A command on a channel: its output in sinks, its input from a feed. */
    private static final class Exec {
        ChannelExec ch;
        Sink out;
        Sink err;
        Feed in;
        long failure;
        boolean closing;
    }

    /** Output of a command: what the session thread delivers waits for the reader, up to a limit. */
    private static final class Sink extends OutputStream {
        private final Exec x;
        private final long limit;
        private final ArrayDeque<byte[]> pieces = new ArrayDeque<>();
        private byte[] current;
        private int pos;
        private long buffered;
        private boolean ended;
        private boolean broken;
        private boolean discarding;

        Sink(Exec x, long limit) {
            this.x = x;
            this.limit = limit;
        }

        @Override
        public void write(int b) {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int n) {
            boolean over = false;
            synchronized (x) {
                if (x.failure != 0 || ended || discarding) {
                    return;
                }
                if (buffered + n > limit) {
                    x.failure = TOO_LARGE;
                    over = true;
                } else if (n > 0) {
                    pieces.add(java.util.Arrays.copyOfRange(b, off, off + n));
                    buffered += n;
                }
                x.notifyAll();
            }
            if (over) {
                // not in the session thread, which is the caller
                ChannelExec ch = x.ch;
                java.lang.Thread t = new java.lang.Thread(ch::disconnect, "ssh close");
                t.setDaemon(true);
                t.start();
            }
        }

        /**
         * The end of the output: by an EOF of the server while the channel
         * is open, otherwise with the channel, which is clean if the command
         * has ended with a status or a signal and not if the connection broke.
         */
        @Override
        public void close() {
            ChannelExec ch = x.ch;
            boolean clean = !ch.isClosed() || ch.getExitStatus() != -1 || ch.getExitSignal() != null;
            synchronized (x) {
                if (!ended) {
                    ended = true;
                    broken = !clean && !x.closing;
                }
                x.notifyAll();
            }
        }

        /** The bytes, 0 at the end, or an error; a timeout of 0 does not wait, a negative one has no limit. */
        long read(byte[] b, int off, int n, double timeout, ObjRef err) {
            long deadline = timeout > 0 ? System.nanoTime() + (long) (timeout * 1e9) : 0;
            synchronized (x) {
                while (true) {
                    java.lang.Thread.interrupted();
                    if (current == null || pos == current.length) {
                        current = pieces.poll();
                        pos = 0;
                    }
                    if (current != null) {
                        int k = Math.min(n, current.length - pos);
                        System.arraycopy(current, pos, b, off, k);
                        pos += k;
                        buffered -= k;
                        return k;
                    }
                    if (x.failure != 0) {
                        return fail(x.failure, "the output of the command exceeds the limit", err);
                    }
                    if (broken) {
                        return fail(DISCONNECTED, "the connection ended", err);
                    }
                    if (ended) {
                        return 0;
                    }
                    if (m3.i.Thread.alertPending()) {
                        return -ALERTED;
                    }
                    try {
                        if (timeout < 0) {
                            x.wait();
                        } else {
                            long rest = deadline - System.nanoTime();
                            if (timeout == 0 || rest <= 0) {
                                return fail(TIMEOUT, "no output in time", err);
                            }
                            TimeUnit.NANOSECONDS.timedWait(x, rest);
                        }
                    } catch (InterruptedException e) {
                        // the loop looks for the alert
                    }
                }
            }
        }

        /** Drops what waits and what comes. */
        void discard() {
            synchronized (x) {
                discarding = true;
                pieces.clear();
                current = null;
                buffered = 0;
                x.notifyAll();
            }
        }

        String text() {
            synchronized (x) {
                ByteArrayOutputStream all = new ByteArrayOutputStream((int) Math.min(buffered, 1 << 20));
                for (byte[] p : pieces) {
                    all.write(p, 0, p.length);
                }
                return all.toString(StandardCharsets.ISO_8859_1);
            }
        }
    }

    /** Input of a command, which a thread of JSch reads and sends. */
    private static final class Feed extends InputStream {
        private final Exec x;
        private final ArrayDeque<byte[]> queue = new ArrayDeque<>();
        private byte[] current;
        private int pos;
        private boolean ended;

        Feed(Exec x) {
            this.x = x;
        }

        @Override
        public int read() {
            byte[] b = new byte[1];
            return read(b, 0, 1) < 0 ? -1 : b[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int n) {
            synchronized (x) {
                while (current == null || pos == current.length) {
                    current = queue.poll();
                    pos = 0;
                    if (current != null) {
                        x.notifyAll();
                    } else if (ended) {
                        return -1;
                    } else {
                        try {
                            x.wait();
                        } catch (InterruptedException e) {
                            return -1;
                        }
                    }
                }
                int k = Math.min(n, current.length - pos);
                System.arraycopy(current, pos, b, off, k);
                pos += k;
                return k;
            }
        }

        /** The end of the input: what waits still goes out, then the EOF. */
        @Override
        public void close() {
            synchronized (x) {
                ended = true;
                x.notifyAll();
            }
        }

        long write(byte[] b, int off, int n, ObjRef err) {
            byte[] copy = java.util.Arrays.copyOfRange(b, off, off + n);
            synchronized (x) {
                while (queue.size() >= INPUT_PIECES && !ended && !x.ch.isClosed()) {
                    java.lang.Thread.interrupted();
                    if (m3.i.Thread.alertPending()) {
                        return -ALERTED;
                    }
                    try {
                        x.wait(100);
                    } catch (InterruptedException e) {
                        // the loop looks for the alert
                    }
                }
                if (ended || x.ch.isClosed()) {
                    return fail(DISCONNECTED, "the command takes no more input", err);
                }
                if (n > 0) {
                    queue.add(copy);
                    x.notifyAll();
                }
                return 0;
            }
        }
    }

    /** Opens the channel of a command: 0, or an error. */
    private static long open(Conn c, Exec x, String command, InputStream in, long limit, double timeout,
                             ObjRef err) {
        try {
            x.ch = (ChannelExec) c.session.openChannel("exec");
        } catch (JSchException | RuntimeException e) {
            return fail(DISCONNECTED, "the session is closed", err);
        }
        x.out = new Sink(x, limit);
        x.err = new Sink(x, limit);
        x.ch.setCommand(command);
        x.ch.setInputStream(in);
        x.ch.setOutputStream(x.out);
        x.ch.setExtOutputStream(x.err);
        try {
            x.ch.connect(jschMillis(timeout));
        } catch (JSchException | RuntimeException e) {
            x.closing = true;
            x.ch.disconnect();
            if (!c.session.isConnected()) {
                return fail(DISCONNECTED, "the connection ended", err);
            }
            return fail(FAILURE, "the server refused the command", err);
        }
        return 0;
    }

    /** Waits for the end of the channel: 0, or an error; a timeout that is not positive has no limit. */
    private static long awaitEnd(Exec x, double timeout, ObjRef err) {
        long deadline = timeout > 0 ? System.nanoTime() + (long) (timeout * 1e9) : 0;
        synchronized (x) {
            while (!x.ch.isClosed()) {
                java.lang.Thread.interrupted();
                if (m3.i.Thread.alertPending()) {
                    return -ALERTED;
                }
                if (x.failure != 0) {
                    return fail(x.failure, "the output of the command exceeds the limit", err);
                }
                long wait = 50;
                if (timeout > 0) {
                    long rest = deadline - System.nanoTime();
                    if (rest <= 0) {
                        return fail(TIMEOUT, "the command did not end in time", err);
                    }
                    wait = Math.min(wait, Math.max(1, rest / 1000000));
                }
                if (x.out.ended) {
                    // the status follows the end of the output closely
                    wait = 1;
                }
                try {
                    x.wait(wait);
                } catch (InterruptedException e) {
                    // the loop looks for the alert
                }
            }
            if (x.failure != 0) {
                return fail(x.failure, "the output of the command exceeds the limit", err);
            }
        }
        return 0;
    }

    /** The status and signal of an ended command: 0, or DISCONNECTED if the connection broke. */
    private static long ending(Conn c, Exec x, LongRef status, ObjRef signal, ObjRef err) {
        String sig = x.ch.getExitSignal();
        int st = x.ch.getExitStatus();
        if (sig == null && st == -1 && !c.session.isConnected()) {
            return fail(DISCONNECTED, "the connection ended", err);
        }
        status.set(sig != null ? -1 : st);
        signal.set(sig);
        return 0;
    }

    static long run(Object o, String command, String input, long maxOutput, double timeout, double openTimeout,
                    LongRef status, ObjRef signal, ObjRef out, ObjRef errOut, ObjRef err) {
        Conn c = (Conn) o;
        Exec x = new Exec();
        byte[] data = input == null ? new byte[0] : latin1(input);
        long r = open(c, x, command, new ByteArrayInputStream(data), maxOutput, openTimeout, err);
        if (r < 0) {
            return r;
        }
        r = awaitEnd(x, timeout, err);
        if (r == 0) {
            r = ending(c, x, status, signal, err);
        }
        synchronized (x) {
            x.closing = true;
        }
        x.ch.disconnect();
        if (r < 0) {
            return r;
        }
        out.set(x.out.text());
        errOut.set(x.err.text());
        return 0;
    }

    static Object start(Object o, String command, long maxBuffer, double openTimeout, LongRef code, ObjRef err) {
        Conn c = (Conn) o;
        Exec x = new Exec();
        x.in = new Feed(x);
        long r = open(c, x, command, x.in, maxBuffer, openTimeout, err);
        if (r < 0) {
            code.set(-r);
            return null;
        }
        return x;
    }

    static long procRead(Object p, long which, byte[] b, int off, int n, double timeout, ObjRef err) {
        Exec x = (Exec) p;
        return (which == 0 ? x.out : x.err).read(b, off, n, timeout, err);
    }

    static void procDiscard(Object p, long which) {
        Exec x = (Exec) p;
        (which == 0 ? x.out : x.err).discard();
    }

    static long procWrite(Object p, byte[] b, int off, int n, ObjRef err) {
        return ((Exec) p).in.write(b, off, n, err);
    }

    static void procCloseInput(Object p) {
        ((Exec) p).in.close();
    }

    static long procWait(Object conn, Object p, LongRef status, ObjRef signal, ObjRef err) {
        Exec x = (Exec) p;
        long r = awaitEnd(x, 0, err);
        if (r < 0) {
            return r;
        }
        return ending((Conn) conn, x, status, signal, err);
    }

    static void procClose(Object p) {
        Exec x = (Exec) p;
        synchronized (x) {
            x.closing = true;
        }
        x.in.close();
        x.ch.disconnect();
    }

    // SFTP

    /** A channel of SFTP with the thread that makes all its calls. */
    private static final class Sftp {
        final Conn conn;
        final double timeout;
        final ExecutorService worker;
        volatile ChannelSftp ch;
        /** The open stream, which only the worker reads and changes. */
        Object stream;

        Sftp(Conn conn, double timeout) {
            this.conn = conn;
            this.timeout = timeout;
            this.worker = Executors.newSingleThreadExecutor(r -> {
                java.lang.Thread t = new java.lang.Thread(r, "ssh sftp");
                t.setDaemon(true);
                return t;
            });
        }
    }

    /** The operation is refused while a stream is open. */
    private static final class Busy extends Exception {
        private static final long serialVersionUID = 1;
    }

    /** The target of an operation exists already. */
    private static final class Exists extends Exception {
        private static final long serialVersionUID = 1;
    }

    /** The target of an operation is not a directory. */
    private static final class NotDirectory extends Exception {
        private static final long serialVersionUID = 1;
    }

    private interface Op<T> {
        T run() throws Exception;
    }

    /** Runs op in the worker of f and waits for it: 0 with its result in box[0], or an error. */
    private static <T> long call(Sftp f, Op<T> op, Object[] box, ObjRef err) {
        Future<T> fu;
        try {
            fu = f.worker.submit(op::run);
        } catch (RejectedExecutionException e) {
            return fail(DISCONNECTED, "the SFTP session is closed", err);
        }
        long r = await(fu, f.timeout);
        if (r == -TIMEOUT) {
            return fail(TIMEOUT, "the server did not answer in time", err);
        }
        if (r < 0) {
            return r;
        }
        return result(f, fu, box, err);
    }

    private static <T> long result(Sftp f, Future<T> fu, Object[] box, ObjRef err) {
        try {
            box[0] = fu.get();
            return 0;
        } catch (ExecutionException e) {
            return sftpFailure(f, e.getCause(), err);
        } catch (InterruptedException | CancellationException e) {
            return fail(FAILURE, "the operation was given up", err);
        }
    }

    /** The number of the failure t of an operation, with its text in err. */
    private static long sftpFailure(Sftp f, Throwable t, ObjRef err) {
        if (t instanceof Busy) {
            return fail(BUSY, "the SFTP session has an open stream", err);
        }
        if (t instanceof Exists) {
            return fail(EXISTS, "the file exists", err);
        }
        if (t instanceof NotDirectory) {
            return fail(FAILURE, "not a directory", err);
        }
        // a stream wraps the status of the server in an IOException
        if (t instanceof IOException && t.getCause() instanceof SftpException) {
            t = t.getCause();
        }
        if (t instanceof SftpException se) {
            if (se.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return fail(NO_SUCH_FILE, "no such file", err);
            }
            if (se.id == ChannelSftp.SSH_FX_PERMISSION_DENIED) {
                return fail(PERMISSION_DENIED, "permission denied", err);
            }
            if (se.id == FILE_ALREADY_EXISTS) {
                return fail(EXISTS, "the file exists", err);
            }
            if (se.id == ChannelSftp.SSH_FX_OP_UNSUPPORTED) {
                return fail(FAILURE, "the server does not support the operation", err);
            }
        }
        ChannelSftp ch = f.ch;
        if (!f.conn.session.isConnected() || ch == null || ch.isClosed() || broken(t)) {
            return fail(DISCONNECTED, "the connection ended", err);
        }
        return fail(FAILURE, "the server refused the operation", err);
    }

    /** The status of SFTP of version 5 and later for an existing target, which some servers send. */
    private static final int FILE_ALREADY_EXISTS = 11;

    /**
     * Is t a failure of the connection? JSch notices that in a request it
     * sends before its session thread does, with an IOException that is not
     * a status of the server, or an SftpException made of one.
     */
    private static boolean broken(Throwable t) {
        if (t instanceof SftpException) {
            return t.getCause() instanceof IOException;
        }
        if (t instanceof IOException) {
            String m = t.getMessage() == null ? "" : t.getMessage();
            return !(m.startsWith("SFTP status error") || m.startsWith("Unexpected server response")
                     || m.startsWith("invalid length") || m.equals("stream already closed") || m.equals("canceled"));
        }
        return false;
    }

    /** A path as JSch takes it literally: it reads *, ? and \ as a pattern. */
    private static String literal(String path) {
        StringBuilder b = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '*' || c == '?' || c == '\\') {
                b.append('\\');
            }
            b.append(c);
        }
        return b.toString();
    }

    private static void idle(Sftp f) throws Busy {
        if (f.stream != null) {
            throw new Busy();
        }
    }

    static Object sftpOpen(Object o, double timeout, LongRef code, ObjRef err) {
        Conn c = (Conn) o;
        Sftp f = new Sftp(c, timeout);
        Object[] box = new Object[1];
        long r = call(f, () -> {
            ChannelSftp ch = (ChannelSftp) c.session.openChannel("sftp");
            f.ch = ch;
            ch.connect(jschMillis(timeout));
            return ch;
        }, box, err);
        if (r < 0) {
            f.worker.submit(() -> {
                ChannelSftp ch = f.ch;
                if (ch != null) {
                    ch.disconnect();
                }
            });
            f.worker.shutdown();
            if (r == -FAILURE) {
                err.set("the server offers no SFTP");
            }
            code.set(-r);
            return null;
        }
        synchronized (c.sftps) {
            c.sftps.add(f);
        }
        return f;
    }

    static void sftpClose(Object o) {
        Sftp f = (Sftp) o;
        ChannelSftp ch = f.ch;
        if (ch != null) {
            // ends a call that waits in the worker
            ch.disconnect();
        }
        f.worker.shutdown();
        synchronized (f.conn.sftps) {
            f.conn.sftps.remove(f);
        }
    }

    private static long kind(SftpATTRS a) {
        if (a.isLink()) {
            return LINK;
        }
        if (a.isDir()) {
            return DIRECTORY;
        }
        if (a.isReg()) {
            return FILE;
        }
        return OTHER;
    }

    private static void attributes(SftpATTRS a, LongRef kind, LongRef size, LongRef mode, LongRef uid, LongRef gid,
                                   LongRef atime, LongRef mtime) {
        kind.set(kind(a));
        size.set(a.getSize());
        mode.set(a.getPermissions() & 07777);
        uid.set(a.getUId() & 0xffffffffL);
        gid.set(a.getGId() & 0xffffffffL);
        atime.set(a.getATime() & 0xffffffffL);
        mtime.set(a.getMTime() & 0xffffffffL);
    }

    static long sftpStat(Object o, String path, boolean follow, LongRef kind, LongRef size, LongRef mode,
                         LongRef uid, LongRef gid, LongRef atime, LongRef mtime, ObjRef err) {
        Sftp f = (Sftp) o;
        Object[] box = new Object[1];
        long r = call(f, () -> {
            idle(f);
            return follow ? f.ch.stat(literal(path)) : f.ch.lstat(literal(path));
        }, box, err);
        if (r < 0) {
            return r;
        }
        attributes((SftpATTRS) box[0], kind, size, mode, uid, gid, atime, mtime);
        return 0;
    }

    /** The entries of a directory, without . and ..; their number, or an error. */
    static long sftpList(Object o, String dir, ObjRef list, ObjRef err) {
        Sftp f = (Sftp) o;
        Object[] box = new Object[1];
        long r = call(f, () -> {
            idle(f);
            String p = literal(dir);
            if (!f.ch.stat(p).isDir()) {
                throw new NotDirectory();
            }
            List<ChannelSftp.LsEntry> all = new ArrayList<>();
            for (ChannelSftp.LsEntry e : f.ch.ls(p)) {
                if (!e.getFilename().equals(".") && !e.getFilename().equals("..")) {
                    all.add(e);
                }
            }
            return all;
        }, box, err);
        if (r < 0) {
            return r;
        }
        list.set(box[0]);
        return ((List<?>) box[0]).size();
    }

    static String sftpEntry(Object list, long i, LongRef kind, LongRef size, LongRef mode, LongRef uid,
                            LongRef gid, LongRef atime, LongRef mtime) {
        ChannelSftp.LsEntry e = (ChannelSftp.LsEntry) ((List<?>) list).get((int) i);
        attributes(e.getAttrs(), kind, size, mode, uid, gid, atime, mtime);
        return e.getFilename();
    }

    /** Raises Exists if the operation failed because path exists. */
    private static void checkExists(Sftp f, SftpException e, String path) throws Exists, SftpException {
        if (e.id == ChannelSftp.SSH_FX_FAILURE) {
            try {
                f.ch.lstat(path);
            } catch (SftpException none) {
                throw e;
            }
            throw new Exists();
        }
        throw e;
    }

    /** One of the operations MKDIR to SET_TIMES on a and b, with n and m: 0, or an error; result for some. */
    static long sftpOp(Object o, long op, String a, String b, long n, long m, ObjRef result, ObjRef err) {
        Sftp f = (Sftp) o;
        Object[] box = new Object[1];
        long r = call(f, () -> {
            idle(f);
            ChannelSftp ch = f.ch;
            String p = literal(a);
            if (op == MKDIR) {
                try {
                    ch.mkdir(p);
                } catch (SftpException e) {
                    checkExists(f, e, p);
                }
                ch.chmod((int) n, p);
            } else if (op == RMDIR) {
                ch.rmdir(p);
            } else if (op == REMOVE) {
                ch.rm(p);
            } else if (op == RENAME) {
                String q = literal(b);
                try {
                    ch.rename(p, q);
                } catch (SftpException e) {
                    checkExists(f, e, q);
                }
            } else if (op == SYMLINK) {
                String q = literal(b);
                try {
                    ch.symlink(p, q);
                } catch (SftpException e) {
                    checkExists(f, e, q);
                }
            } else if (op == READLINK) {
                return ch.readlink(p);
            } else if (op == REALPATH) {
                return ch.realpath(p);
            } else if (op == CHMOD) {
                ch.chmod((int) n, p);
            } else if (op == SET_TIMES) {
                SftpATTRS t = new SftpATTRS();
                t.setACMODTIME((int) n, (int) m);
                ch.setStat(p, t);
            }
            return null;
        }, box, err);
        if (r < 0) {
            return r;
        }
        result.set(box[0]);
        return 0;
    }

    // streams of SFTP

    private static final byte[] EOF = new byte[0];

    /** A file of the server being read. */
    private static final class In {
        final Sftp f;
        InputStream in;
        Future<byte[]> pending;
        int size;
        boolean eof;

        In(Sftp f) {
            this.f = f;
        }
    }

    /** A file of the server being written. */
    private static final class Out {
        final Sftp f;
        OutputStream out;

        Out(Sftp f) {
            this.f = f;
        }
    }

    static Object sftpGet(Object o, String path, long offset, LongRef code, ObjRef err) {
        Sftp f = (Sftp) o;
        In s = new In(f);
        Object[] box = new Object[1];
        long r = call(f, () -> {
            idle(f);
            String p = literal(path);
            if (f.ch.stat(p).isDir()) {
                throw new SftpException(ChannelSftp.SSH_FX_FAILURE, "a directory");
            }
            s.in = f.ch.get(p, null, offset);
            f.stream = s;
            return s;
        }, box, err);
        if (r < 0) {
            code.set(-r);
            return null;
        }
        return s;
    }

    /**
     * Reads at most n bytes into b: their number, 0 at the end, or an error.
     * A read given up by an alert stays pending for the next call, which
     * therefore needs room for at least as many bytes.
     */
    static long streamRead(Object o, byte[] b, int off, int n, ObjRef err) {
        In s = (In) o;
        if (s.eof || n == 0) {
            return 0;
        }
        if (s.pending != null && n < s.size) {
            return fail(FAILURE, "the buffer is shorter than in the read given up", err);
        }
        if (s.pending == null) {
            int size = n;
            s.size = n;
            try {
                s.pending = s.f.worker.submit(() -> {
                    byte[] buf = new byte[size];
                    int k = s.in.read(buf, 0, size);
                    return k < 0 ? EOF : java.util.Arrays.copyOf(buf, k);
                });
            } catch (RejectedExecutionException e) {
                return fail(DISCONNECTED, "the SFTP session is closed", err);
            }
        }
        long r = await(s.pending, s.f.timeout);
        if (r == -TIMEOUT) {
            return fail(TIMEOUT, "the server did not answer in time", err);
        }
        if (r < 0) {
            return r;
        }
        Object[] box = new Object[1];
        r = result(s.f, s.pending, box, err);
        s.pending = null;
        if (r < 0) {
            return r;
        }
        byte[] got = (byte[]) box[0];
        if (got == EOF) {
            s.eof = true;
            return 0;
        }
        System.arraycopy(got, 0, b, off, got.length);
        return got.length;
    }

    static Object sftpPut(Object o, String path, long mode, boolean append, LongRef code, ObjRef err) {
        Sftp f = (Sftp) o;
        Out s = new Out(f);
        Object[] box = new Object[1];
        long r = call(f, () -> {
            idle(f);
            String p = literal(path);
            boolean existed;
            try {
                existed = f.ch.stat(p) != null;
            } catch (SftpException e) {
                if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    throw e;
                }
                existed = false;
            }
            s.out = f.ch.put(p, append ? ChannelSftp.APPEND : ChannelSftp.OVERWRITE);
            f.stream = s;
            if (!existed) {
                try {
                    f.ch.chmod((int) mode, p);
                } catch (SftpException e) {
                    f.stream = null;
                    s.out.close();
                    throw e;
                }
            }
            return s;
        }, box, err);
        if (r < 0) {
            code.set(-r);
            return null;
        }
        return s;
    }

    static long streamWrite(Object o, byte[] b, int off, int n, ObjRef err) {
        Out s = (Out) o;
        byte[] copy = java.util.Arrays.copyOfRange(b, off, off + n);
        return call(s.f, () -> {
            s.out.write(copy);
            return null;
        }, new Object[1], err);
    }

    /** Ends the stream, which frees the SFTP session: 0, or an error of the end of a written file. */
    static long streamClose(Object o, ObjRef err) {
        Sftp f;
        Op<Object> op;
        if (o instanceof In s) {
            f = s.f;
            op = () -> {
                try {
                    s.in.close();
                } finally {
                    f.stream = null;
                }
                return null;
            };
        } else {
            Out s = (Out) o;
            f = s.f;
            op = () -> {
                try {
                    s.out.close();
                } finally {
                    f.stream = null;
                }
                return null;
            };
        }
        return call(f, op, new Object[1], err);
    }
}
