// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * The externals M3jTls of the module TLS of m3j (see Externals): one end of
 * a connection of TLS, on an engine of the Java platform.
 *
 * <p>An object does no input or output. The module TLS gives it the bytes
 * that came from the network (feed) and sends the bytes that it asks for
 * (drain), so the connection below can be any ConnFD.T, and its timeouts
 * and alerts stay those of that connection. The state of the protocol
 * moves on (advance) whenever new bytes arrive: what is complete is
 * decrypted at once and waits for take; what the protocol wants to answer
 * waits for drain. So a thread that reads and one that writes need no
 * knowledge of each other.
 *
 * <p>Results less than 0 are the negated numbers below, the constants of
 * the interface M3jTls.
 */
final class Tls {
    static final long NEED_DATA = 1;
    static final long HANDSHAKE_FAILED = 2;
    static final long BAD_CERTIFICATE = 3;
    static final long PROTOCOL_ERROR = 4;
    static final long TRUNCATED = 5;
    static final long CLOSED = 6;

    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);
    private static final int INITIAL = 1 << 15;

    private final SSLEngine engine;
    // all three are kept ready to be written to
    private ByteBuffer netIn = ByteBuffer.allocate(INITIAL);
    private ByteBuffer netOut = ByteBuffer.allocate(INITIAL);
    private ByteBuffer appIn = ByteBuffer.allocate(INITIAL);
    private long failure;
    private String message;
    private boolean eof;
    private boolean established;

    private Tls(SSLEngine engine) {
        this.engine = engine;
    }

    // contexts

    private static char[] chars(String s) {
        return s == null ? null : s.toCharArray();
    }

    /** A store of keys or certificates: PKCS12 or JKS, or if allowed certificates in the format PEM. */
    private static KeyStore store(String name, String password, boolean pem)
        throws IOException, GeneralSecurityException {
        Path p = Path.of(Rt.resolve(name));
        if (pem) {
            byte[] b = Files.readAllBytes(p);
            String head = new String(b, 0, Math.min(b.length, 64), StandardCharsets.ISO_8859_1).strip();
            if (head.startsWith("-----BEGIN")) {
                KeyStore k = KeyStore.getInstance(KeyStore.getDefaultType());
                k.load(null, null);
                int i = 0;
                CertificateFactory f = CertificateFactory.getInstance("X.509");
                for (Certificate c : f.generateCertificates(new ByteArrayInputStream(b))) {
                    k.setCertificateEntry("certificate-" + i++, c);
                }
                if (i == 0) {
                    throw new CertificateException("no certificates in " + name);
                }
                return k;
            }
        }
        return KeyStore.getInstance(new File(p.toString()), chars(password));
    }

    /**
     * A context with the certificate of keyStore (null: none) that trusts
     * the certificates of trustStore (null: those of the platform); null and
     * err set if that fails.
     */
    static Object newContext(String keyStore, String keyPassword, String trustStore, String trustPassword,
                             ObjRef err) {
        try {
            KeyManager[] keys = null;
            if (keyStore != null) {
                KeyManagerFactory f = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                f.init(store(keyStore, keyPassword, false), chars(keyPassword));
                keys = f.getKeyManagers();
            }
            TrustManager[] trust = null;
            if (trustStore != null) {
                TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                f.init(store(trustStore, trustPassword, true));
                trust = f.getTrustManagers();
            }
            SSLContext c = SSLContext.getInstance("TLS");
            c.init(keys, trust, null);
            return c;
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            err.set(e.getMessage() == null ? e.toString() : e.getMessage());
            return null;
        }
    }

    // connections

    /**
     * One end of a connection, its handshake begun. A client checks the
     * certificate of its peer against host. The peer's port is not known
     * here, and the platform resumes sessions only for a known port, so
     * each connection has a full handshake.
     */
    static Object newEngine(Object context, String host, boolean client, boolean needAuth, String alpn) {
        SSLContext c = (SSLContext) context;
        SSLEngine e = client ? c.createSSLEngine(host, -1) : c.createSSLEngine();
        Tls t = new Tls(e);
        try {
            e.setUseClientMode(client);
            SSLParameters p = e.getSSLParameters();
            if (client) {
                p.setEndpointIdentificationAlgorithm("HTTPS");
            } else {
                p.setNeedClientAuth(needAuth);
            }
            if (alpn != null) {
                p.setApplicationProtocols(alpn.split(","));
            }
            e.setSSLParameters(p);
            e.beginHandshake();
        } catch (SSLException | RuntimeException x) {
            t.fail(x);
        }
        return t;
    }

    private static ByteBuffer grow(ByteBuffer b, int more) {
        if (b.remaining() >= more) {
            return b;
        }
        ByteBuffer c = ByteBuffer.allocate(Math.max(b.capacity() * 2, b.position() + more));
        b.flip();
        c.put(b);
        return c;
    }

    private static int copyOut(ByteBuffer from, byte[] b, int off, int n) {
        from.flip();
        int k = Math.min(n, from.remaining());
        from.get(b, off, k);
        from.compact();
        return k;
    }

    private SSLEngineResult wrap(ByteBuffer src) throws SSLException {
        while (true) {
            SSLEngineResult r = engine.wrap(src, netOut);
            if (r.getStatus() != SSLEngineResult.Status.BUFFER_OVERFLOW) {
                return r;
            }
            netOut = grow(netOut, netOut.remaining() + engine.getSession().getPacketBufferSize());
        }
    }

    private SSLEngineResult unwrap() throws SSLException {
        netIn.flip();
        try {
            while (true) {
                SSLEngineResult r = engine.unwrap(netIn, appIn);
                if (r.getStatus() != SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    return r;
                }
                appIn = grow(appIn, appIn.remaining() + engine.getSession().getApplicationBufferSize());
            }
        } finally {
            netIn.compact();
        }
    }

    private static boolean causedByCertificate(Throwable x) {
        for (Throwable t = x; t != null; t = t.getCause()) {
            if (t instanceof CertificateException || t instanceof CertPathValidatorException
                || t instanceof CertPathBuilderException) {
                return true;
            }
        }
        return false;
    }

    /** Records the first failure, and queues the alert that tells the peer about it. */
    private void fail(Exception x) {
        if (failure == 0) {
            failure = causedByCertificate(x) ? BAD_CERTIFICATE : established ? PROTOCOL_ERROR : HANDSHAKE_FAILED;
            message = x.getMessage() == null ? x.toString() : x.getMessage();
        }
        try {
            if (engine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
                wrap(EMPTY);
            }
        } catch (SSLException | RuntimeException e) {
            // the peer will learn of the failure from the end of the connection
        }
    }

    /** One step of the protocol: whether another one may be possible. */
    private boolean step() throws SSLException {
        switch (engine.getHandshakeStatus()) {
            case NEED_TASK:
                for (Runnable t = engine.getDelegatedTask(); t != null; t = engine.getDelegatedTask()) {
                    t.run();
                }
                return true;
            case NEED_WRAP:
                return wrap(EMPTY).bytesProduced() > 0;
            default:
                if (netIn.position() == 0 || engine.isInboundDone()) {
                    return false;
                }
                SSLEngineResult r = unwrap();
                return r.getStatus() != SSLEngineResult.Status.BUFFER_UNDERFLOW
                    && (r.bytesConsumed() > 0 || r.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING);
        }
    }

    private void advance() {
        if (failure != 0) {
            return;
        }
        try {
            while (step()) {
                // until the protocol needs bytes that have not come yet
            }
        } catch (SSLException | RuntimeException x) {
            fail(x);
        }
    }

    /** Takes bytes from the network; none: the connection below has ended. */
    synchronized void feed(byte[] b, int off, int n) {
        if (n == 0) {
            eof = true;
            return;
        }
        netIn = grow(netIn, n);
        netIn.put(b, off, n);
        advance();
    }

    /** Moves the handshake on: 1 if it goes on, 0 if it is done, or an error. */
    synchronized long handshaking() {
        advance();
        if (failure != 0) {
            return -failure;
        }
        if (engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
            established = true;
            return 0;
        }
        if (eof) {
            failure = HANDSHAKE_FAILED;
            message = "the connection ended during the handshake";
            return -failure;
        }
        return 1;
    }

    /** Decrypted bytes: their number, 0 at the end of the data of the peer, or an error. */
    synchronized long take(byte[] b, int off, int n) {
        if (appIn.position() > 0) {
            return copyOut(appIn, b, off, n);
        }
        if (failure != 0) {
            return -failure;
        }
        if (engine.isInboundDone()) {
            return 0;
        }
        return eof ? -TRUNCATED : -NEED_DATA;
    }

    /** Encrypts bytes (at most one record): the number of them taken, or an error. */
    synchronized long give(byte[] b, int off, int n) {
        if (failure != 0) {
            return -failure;
        }
        if (engine.isOutboundDone()) {
            return -CLOSED;
        }
        try {
            ByteBuffer src = ByteBuffer.wrap(b, off, n);
            while (true) {
                SSLEngineResult r = wrap(src);
                if (r.getStatus() == SSLEngineResult.Status.CLOSED) {
                    return -CLOSED;
                }
                advance();
                if (failure != 0) {
                    return -failure;
                }
                if (r.bytesConsumed() > 0) {
                    return r.bytesConsumed();
                }
                if (r.bytesProduced() == 0) {
                    throw new SSLException("the engine takes no data");
                }
            }
        } catch (SSLException | RuntimeException x) {
            fail(x);
            return -failure;
        }
    }

    /** Bytes for the network: their number, 0 if there are none. */
    synchronized long drain(byte[] b, int off, int n) {
        return copyOut(netOut, b, off, n);
    }

    /** Ends the output: queues the notice of closing for the peer. */
    synchronized void closeOutput() {
        if (failure == 0) {
            engine.closeOutbound();
            advance();
        }
    }

    /** The text of the failure, or null. */
    synchronized String message() {
        return message;
    }

    /** 0: the protocol that ALPN chose; 1: the version; 2: the cipher suite; 3: the subject of the peer. */
    synchronized String info(long what) {
        switch ((int) what) {
            case 0:
                String p = engine.getApplicationProtocol();
                return p == null || p.isEmpty() ? null : p;
            case 1:
                return engine.getSession().getProtocol();
            case 2:
                return engine.getSession().getCipherSuite();
            default:
                try {
                    return engine.getSession().getPeerPrincipal().getName();
                } catch (SSLPeerUnverifiedException e) {
                    return null;
                }
        }
    }
}
