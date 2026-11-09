// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

/**
 * The externals M3jHttp of the module HTTPClient of m3j (see Externals): an
 * exchange of the client of the Java platform.
 *
 * <p>The texts of errors are fixed: what the platform says can contain
 * data of the peer, and the module makes atoms of the texts, which are never
 * freed.
 *
 * <p>A wait ends with ALERTED if the thread has a pending alert of
 * Modula-3, which the caller takes. Results less than 0 are the negated
 * numbers below, the constants of the interface M3jHttp.
 */
final class Http {
    static final long TIMEOUT = 1;
    static final long ALERTED = 2;
    static final long UNREACHABLE = 3;
    static final long BAD_MESSAGE = 4;
    static final long BAD_URL = 5;
    static final long BAD_CERTIFICATE = 6;
    static final long HANDSHAKE_FAILED = 7;
    static final long CLOSED = 8;

    /** How many pieces of a response may wait for the reader. */
    private static final int WINDOW = 8;
    /** How many pieces of a request may wait for the network. */
    private static final int UPLOAD_PIECES = 16;

    private Http() {}

    static Object newClient(double connectTimeout, boolean follow, String proxy, Object context, ObjRef err) {
        HttpClient.Builder b = HttpClient.newBuilder();
        b.followRedirects(follow ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);
        if (connectTimeout > 0) {
            b.connectTimeout(Duration.ofMillis(millis(connectTimeout)));
        }
        if (proxy != null) {
            int colon = proxy.lastIndexOf(':');
            try {
                b.proxy(ProxySelector.of(new InetSocketAddress(proxy.substring(0, colon),
                                                               Integer.parseInt(proxy.substring(colon + 1)))));
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                err.set("malformed address of a proxy");
                return null;
            }
        }
        if (context != null) {
            b.sslContext((SSLContext) context);
        }
        return b.build();
    }

    static void closeClient(Object client) {
        ((HttpClient) client).shutdownNow();
    }

    private static long millis(double seconds) {
        return Math.max(1, (long) Math.ceil(seconds * 1000));
    }

    /** The bytes of a text: a character up to 255 is a byte, a larger one goes out in UTF-8. */
    private static byte[] bytes(String s) {
        boolean wide = false;
        for (int i = 0; i < s.length(); i++) {
            wide |= s.charAt(i) > 255;
        }
        if (!wide) {
            return s.getBytes(StandardCharsets.ISO_8859_1);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        s.codePoints().forEach(c -> {
            if (c <= 255) {
                out.write(c);
            } else {
                byte[] u = new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8);
                out.write(u, 0, u.length);
            }
        });
        return out.toByteArray();
    }

    private static boolean has(Throwable t, Class<?> k) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (k.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    /** The number of the failure t, with its text in err. */
    private static long classify(Throwable t, ObjRef err) {
        long code;
        String text;
        if (has(t, HttpTimeoutException.class)) {
            code = TIMEOUT;
            text = "the request timed out";
        } else if (has(t, CertificateException.class) || has(t, CertPathValidatorException.class)) {
            code = BAD_CERTIFICATE;
            text = "the certificate of the server is not accepted";
        } else if (has(t, SSLException.class)) {
            code = HANDSHAKE_FAILED;
            text = "the handshake of TLS failed";
        } else if (has(t, UnresolvedAddressException.class)) {
            code = UNREACHABLE;
            text = "unknown host";
        } else if (has(t, ConnectException.class)) {
            code = UNREACHABLE;
            text = "no connection to the server";
        } else if (has(t, ProtocolException.class)) {
            code = BAD_MESSAGE;
            text = "invalid response";
        } else {
            code = UNREACHABLE;
            text = "the connection failed";
        }
        err.set(text);
        return code;
    }

    /** A running exchange, or null with code and err set. */
    static Object start(Object client, String method, String url, String headers, String text, boolean upload,
                        long length, double timeout, LongRef code, ObjRef err) {
        HttpRequest.Builder b;
        URI uri;
        try {
            uri = new URI(url);
            b = HttpRequest.newBuilder(uri);
        } catch (java.net.URISyntaxException | IllegalArgumentException e) {
            code.set(BAD_URL);
            err.set("malformed URL");
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            // otherwise the client asks every server for an upgrade to HTTP/2
            b.version(HttpClient.Version.HTTP_1_1);
        }
        if (timeout > 0) {
            b.timeout(Duration.ofMillis(millis(timeout)));
        }
        Exchange x = new Exchange();
        try {
            for (String line : headers.split("\n")) {
                int colon = line.indexOf(": ");
                if (colon > 0) {
                    b.header(line.substring(0, colon), line.substring(colon + 2));
                }
            }
            if (upload) {
                x.upload = new Upload(length);
                b.method(method, x.upload);
            } else if (text != null) {
                b.method(method, HttpRequest.BodyPublishers.ofByteArray(bytes(text)));
            } else if (method.equals("GET")) {
                b.GET();
            } else if (method.equals("HEAD")) {
                b.HEAD();
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            x.future = ((HttpClient) client).sendAsync(b.build(), info -> {
                x.body = new Body();
                return x.body;
            });
        } catch (IllegalArgumentException | IllegalStateException e) {
            code.set(BAD_MESSAGE);
            err.set("the request is not valid");
            return null;
        }
        return x;
    }

    /** Waits until f is done: 0, or -ALERTED if the thread has a pending alert. */
    private static long await(CompletableFuture<?> f) {
        while (true) {
            // an interrupt that finds no alert belongs to one taken already
            java.lang.Thread.interrupted();
            if (m3.i.Thread.alertPending()) {
                return -ALERTED;
            }
            try {
                f.get();
                return 0;
            } catch (InterruptedException e) {
                // the loop looks for the alert
            } catch (ExecutionException | CancellationException e) {
                return 0;
            }
        }
    }

    /** One exchange of a request and a response. */
    private static final class Exchange {
        CompletableFuture<HttpResponse<Body>> future;
        volatile Upload upload;
        volatile Body body;
    }

    static long awaitResponse(Object o, LongRef status, ObjRef version, ObjRef headers, ObjRef err) {
        Exchange x = (Exchange) o;
        long r = await(x.future);
        if (r < 0) {
            return r;
        }
        HttpResponse<Body> resp;
        try {
            resp = x.future.get(0, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException e) {
            err.set("the connection failed");
            return -UNREACHABLE;
        } catch (ExecutionException e) {
            Upload u = x.upload;
            if (u != null && u.wrongLength()) {
                err.set("the body of the request does not have the declared length");
                return -BAD_MESSAGE;
            }
            return -classify(e.getCause(), err);
        } catch (CancellationException e) {
            err.set("the request was cancelled");
            return -CLOSED;
        }
        status.set(resp.statusCode());
        version.set(resp.version() == HttpClient.Version.HTTP_2 ? "HTTP/2" : "HTTP/1.1");
        headers.set(fields(resp.headers()));
        return 0;
    }

    private static String fields(HttpHeaders h) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, List<String>> e : h.map().entrySet()) {
            for (String v : e.getValue()) {
                b.append(e.getKey()).append(": ").append(v).append('\n');
            }
        }
        return b.toString();
    }

    static long read(Object o, byte[] b, int off, int n, double timeout, ObjRef err) {
        return ((Exchange) o).body.read(b, off, n, timeout, err);
    }

    static long write(Object o, byte[] b, int off, int n) {
        return ((Exchange) o).upload.write(b, off, n);
    }

    static void finishUpload(Object o, boolean failed) {
        ((Exchange) o).upload.finish(failed);
    }

    static void abort(Object o) {
        Exchange x = (Exchange) o;
        Upload u = x.upload;
        if (u != null) {
            u.cancel();
        }
        Body body = x.body;
        if (body != null) {
            body.cancel();
        }
        x.future.cancel(true);
    }

    /** The body of a response: what the network delivers waits for the reader, up to WINDOW pieces. */
    private static final class Body implements HttpResponse.BodySubscriber<Body> {
        private static final Object END = new Object();

        private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        private volatile Flow.Subscription subscription;
        private Iterator<ByteBuffer> pieces;
        private ByteBuffer current;
        private Object last;
        private long failure;

        @Override
        public CompletionStage<Body> getBody() {
            return CompletableFuture.completedStage(this);
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            subscription = s;
            s.request(WINDOW);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            queue.add(item);
        }

        @Override
        public void onError(Throwable t) {
            queue.add(t);
        }

        @Override
        public void onComplete() {
            queue.add(END);
        }

        void cancel() {
            Flow.Subscription s = subscription;
            if (s != null) {
                s.cancel();
            }
        }

        /** The next item, null after timeout, or ALERTED as a marker. */
        private Object next(double timeout) {
            long deadline = timeout > 0 ? System.nanoTime() + (long) (timeout * 1e9) : 0;
            while (true) {
                java.lang.Thread.interrupted();
                if (m3.i.Thread.alertPending()) {
                    return ALERT;
                }
                try {
                    if (timeout < 0) {
                        return queue.take();
                    }
                    if (timeout == 0) {
                        return queue.poll();
                    }
                    long rest = deadline - System.nanoTime();
                    return queue.poll(Math.max(rest, 0), TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    // the loop looks for the alert
                }
            }
        }

        private static final Object ALERT = new Object();

        @SuppressWarnings("unchecked")
        long read(byte[] b, int off, int n, double timeout, ObjRef err) {
            if (n == 0) {
                return 0;
            }
            while (current == null || !current.hasRemaining()) {
                if (pieces != null && pieces.hasNext()) {
                    current = pieces.next();
                    continue;
                }
                if (pieces != null) {
                    pieces = null;
                    subscription.request(1);
                }
                if (last == END) {
                    return 0;
                }
                if (last != null) {
                    err.set("the response is incomplete");
                    return -failure;
                }
                Object item = next(timeout);
                if (item == null) {
                    err.set("timeout in the body of a response");
                    return -TIMEOUT;
                }
                if (item == ALERT) {
                    return -ALERTED;
                }
                if (item == END) {
                    last = END;
                    return 0;
                }
                if (item instanceof Throwable t) {
                    last = t;
                    failure = has(t, java.net.http.HttpTimeoutException.class) ? TIMEOUT : BAD_MESSAGE;
                    err.set("the response is incomplete");
                    return -failure;
                }
                pieces = ((List<ByteBuffer>) item).iterator();
            }
            int k = Math.min(n, current.remaining());
            current.get(b, off, k);
            return k;
        }
    }

    /**
     * The body of a request, taken from a Modula-3 reader by a thread of its
     * own: the thread blocks while the network has UPLOAD_PIECES pieces that
     * are not sent. A body can be sent only once, so a redirect or a retry
     * that needs it again fails.
     */
    private static final class Upload implements HttpRequest.BodyPublisher, Flow.Subscription {
        private final long length;
        private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
        private Flow.Subscriber<? super ByteBuffer> subscriber;
        private long demand;
        private boolean cancelled;
        private boolean ended;
        private boolean failed;
        private boolean signalled;
        private boolean draining;
        private long written;

        Upload(long length) {
            this.length = length;
        }

        @Override
        public long contentLength() {
            return length;
        }

        synchronized boolean wrongLength() {
            return length >= 0 && (written > length || ended && !failed && written != length);
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> s) {
            boolean again;
            synchronized (this) {
                again = subscriber != null;
                if (!again) {
                    subscriber = s;
                }
            }
            if (again) {
                s.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                        // nothing can be delivered
                    }

                    @Override
                    public void cancel() {
                        // nothing to stop
                    }
                });
                s.onError(new IOException("the body of the request cannot be sent twice"));
                return;
            }
            s.onSubscribe(this);
        }

        @Override
        public void request(long n) {
            synchronized (this) {
                demand = demand + n < 0 ? Long.MAX_VALUE : demand + n;
            }
            drain();
        }

        @Override
        public void cancel() {
            synchronized (this) {
                cancelled = true;
                queue.clear();
                notifyAll();
            }
        }

        /** Delivers what the subscriber may take, in one thread at a time. */
        private void drain() {
            while (true) {
                ByteBuffer item = null;
                boolean finish = false;
                boolean fail = false;
                Flow.Subscriber<? super ByteBuffer> s;
                synchronized (this) {
                    if (draining || subscriber == null || cancelled) {
                        return;
                    }
                    s = subscriber;
                    if (demand > 0 && !queue.isEmpty()) {
                        item = queue.poll();
                        demand--;
                        notifyAll();
                    } else if (queue.isEmpty() && ended && !signalled) {
                        signalled = true;
                        finish = !failed;
                        fail = failed;
                    } else {
                        return;
                    }
                    draining = true;
                }
                try {
                    if (item != null) {
                        s.onNext(item);
                    } else if (finish) {
                        s.onComplete();
                    } else if (fail) {
                        s.onError(new IOException("reading the body of the request failed"));
                    }
                } finally {
                    synchronized (this) {
                        draining = false;
                    }
                }
            }
        }

        long write(byte[] b, int off, int n) {
            byte[] copy = java.util.Arrays.copyOfRange(b, off, off + n);
            synchronized (this) {
                written += n;
                while (queue.size() >= UPLOAD_PIECES && !cancelled) {
                    if (m3.i.Thread.alertPending()) {
                        return -ALERTED;
                    }
                    try {
                        wait(100);
                    } catch (InterruptedException e) {
                        // the loop looks for the alert
                    }
                }
                if (cancelled) {
                    return -CLOSED;
                }
                queue.add(ByteBuffer.wrap(copy));
            }
            drain();
            return 0;
        }

        void finish(boolean failure) {
            synchronized (this) {
                ended = true;
                failed = failure;
                notifyAll();
            }
            drain();
        }
    }
}
