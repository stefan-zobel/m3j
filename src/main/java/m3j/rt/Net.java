// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The externals M3jNet of the modules TCP and IP of m3j (see Externals):
 * TCP sockets and the names of hosts of the Java platform.
 *
 * <p>A socket is a handle (a number greater than 0) for a non-blocking
 * channel. An operation that cannot go on without blocking returns
 * -WOULD_BLOCK; the caller waits with await and tries again, as cm3's
 * modules do with select. Errors are negative numbers too: the number of
 * the error of Windows Sockets that the error of the platform stands for
 * (cm3 puts it into the lists of atoms of its exceptions), or -1 for
 * another error.
 *
 * <p>Each socket has two selectors: one to wait for reading, accepting and
 * connecting, one to wait for writing, so a thread that reads and one that
 * writes the same connection do not wait for each other. A wait that
 * Thread.Alert may end is woken by its interrupt.
 *
 * <p>An address of IP version 4 is an integer, its first byte the most
 * significant one: 127.0.0.1 is 16_7F000001.
 */
final class Net {
    private Net() {}

    static final long WOULD_BLOCK = 10035;   // WSAEWOULDBLOCK
    static final long NOT_SOCKET = 10038;    // WSAENOTSOCK
    static final long ADDR_IN_USE = 10048;   // WSAEADDRINUSE
    static final long ADDR_NOT_AVAIL = 10049; // WSAEADDRNOTAVAIL
    static final long NET_DOWN = 10050;      // WSAENETDOWN
    static final long NET_UNREACH = 10051;   // WSAENETUNREACH
    static final long CONN_ABORTED = 10053;  // WSAECONNABORTED
    static final long CONN_RESET = 10054;    // WSAECONNRESET
    static final long NO_BUFS = 10055;       // WSAENOBUFS
    static final long TIMED_OUT = 10060;     // WSAETIMEDOUT
    static final long CONN_REFUSED = 10061;  // WSAECONNREFUSED
    static final long HOST_UNREACH = 10065;  // WSAEHOSTUNREACH
    static final long TOO_MANY = 10024;      // WSAEMFILE

    /** A socket: a listening one (server) or a connection. */
    private static final class Sock {
        final SelectableChannel ch;
        final Selector rsel;
        Selector wsel;
        SelectionKey rkey;
        /** a byte read ahead by eof, or -1 */
        int ahead = -1;
        volatile boolean closed;

        Sock(SelectableChannel ch) throws IOException {
            this.ch = ch;
            this.rsel = Selector.open();
        }

        SocketChannel conn() throws ClosedChannelException {
            if (closed) {
                throw new ClosedChannelException();
            }
            return (SocketChannel) ch;
        }
    }

    private static final Map<Long, Sock> socks = new ConcurrentHashMap<>();
    private static final AtomicLong next = new AtomicLong(1);

    private static Sock sock(long h) throws ClosedChannelException {
        Sock s = socks.get(h);
        if (s == null || s.closed) {
            throw new ClosedChannelException();
        }
        return s;
    }

    private static long add(Sock s) {
        long h = next.getAndIncrement();
        socks.put(h, s);
        return h;
    }

    /** The number of the error of Windows Sockets for e (negated by the callers). */
    static long code(Throwable e) {
        if (e instanceof ClosedChannelException || e instanceof ClosedSelectorException) {
            return NOT_SOCKET;
        }
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (m.contains("already in use")) {
            return ADDR_IN_USE;
        } else if (m.contains("cannot assign") || m.contains("not available")) {
            return ADDR_NOT_AVAIL;
        } else if (m.contains("refused")) {
            return CONN_REFUSED;
        } else if (m.contains("timed out")) {
            return TIMED_OUT;
        } else if (m.contains("network is unreachable")) {
            return NET_UNREACH;
        } else if (m.contains("no route") || m.contains("unreachable")) {
            return HOST_UNREACH;
        } else if (m.contains("network is down")) {
            return NET_DOWN;
        } else if (m.contains("reset")) {
            return CONN_RESET;
        } else if (m.contains("abort") || m.contains("broken pipe")) {
            return CONN_ABORTED;
        } else if (m.contains("too many open files")) {
            return TOO_MANY;
        } else if (m.contains("buffer space")) {
            return NO_BUFS;
        }
        return 1;
    }

    static InetAddress inet(long a) {
        byte[] b = {(byte) (a >>> 24), (byte) (a >>> 16), (byte) (a >>> 8), (byte) a};
        try {
            return InetAddress.getByAddress(b);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    static long addr(InetAddress a) {
        byte[] b = a.getAddress();
        if (b.length != 4) {
            return 0;
        }
        return (b[0] & 0xFFL) << 24 | (b[1] & 0xFFL) << 16 | (b[2] & 0xFFL) << 8 | (b[3] & 0xFFL);
    }

    /** A connector on addr and port (0 for any): its handle. */
    static long listen(long addr, long port) {
        ServerSocketChannel c = null;
        try {
            c = ServerSocketChannel.open();
            c.configureBlocking(false);
            c.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            c.bind(new InetSocketAddress(inet(addr), (int) port), 8);
            Sock s = new Sock(c);
            s.rkey = c.register(s.rsel, SelectionKey.OP_ACCEPT);
            return add(s);
        } catch (IOException | RuntimeException e) {
            closeQuietly(c);
            return -code(e);
        }
    }

    /** Sets addr and port to the local endpoint of socket h; 0 or an error. */
    static long localEndpoint(long h, LongRef addr, LongRef port) {
        try {
            InetSocketAddress a = (InetSocketAddress) (sock(h).ch instanceof ServerSocketChannel s
                ? s.getLocalAddress() : sock(h).conn().getLocalAddress());
            addr.set(addr(a.getAddress()));
            port.set(a.getPort());
            return 0;
        } catch (IOException | RuntimeException e) {
            return -code(e);
        }
    }

    /** Sets addr and port to the remote endpoint of connection h; 0 or an error. */
    static long peerEndpoint(long h, LongRef addr, LongRef port) {
        try {
            InetSocketAddress a = (InetSocketAddress) sock(h).conn().getRemoteAddress();
            if (a == null) {
                return -NOT_SOCKET;
            }
            addr.set(addr(a.getAddress()));
            port.set(a.getPort());
            return 0;
        } catch (IOException | RuntimeException e) {
            return -code(e);
        }
    }

    private static long connection(SocketChannel c) throws IOException {
        c.configureBlocking(false);
        if (c.isConnected()) {
            // a connection that connect starts has the option already
            c.setOption(StandardSocketOptions.TCP_NODELAY, true);
        }
        Sock s = new Sock(c);
        s.rkey = c.register(s.rsel, c.isConnected() ? SelectionKey.OP_READ : SelectionKey.OP_CONNECT);
        return add(s);
    }

    /** A connection accepted by connector h, without blocking: its handle; addr and port are its peer's. */
    static long accept(long h, LongRef addr, LongRef port) {
        SocketChannel c = null;
        try {
            c = ((ServerSocketChannel) sock(h).ch).accept();
            if (c == null) {
                return -WOULD_BLOCK;
            }
            InetSocketAddress a = (InetSocketAddress) c.getRemoteAddress();
            addr.set(addr(a.getAddress()));
            port.set(a.getPort());
            return connection(c);
        } catch (IOException | RuntimeException e) {
            closeQuietly(c);
            return -code(e);
        }
    }

    /**
     * Starts to connect to addr and port, from fromAddr and fromPort if
     * either is not 0: the handle of the connection, which finishConnect
     * completes.
     */
    static long connect(long addr, long port, long fromAddr, long fromPort) {
        SocketChannel c = null;
        try {
            c = SocketChannel.open();
            c.configureBlocking(false);
            // before the connection is made, as cm3 does: while Windows
            // tries again to connect to a listener whose queue was full,
            // the option cannot be set
            c.setOption(StandardSocketOptions.TCP_NODELAY, true);
            if (fromAddr != 0 || fromPort != 0) {
                c.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                c.bind(new InetSocketAddress(inet(fromAddr), (int) fromPort));
            }
            c.connect(new InetSocketAddress(inet(addr), (int) port));
            return connection(c);
        } catch (IOException | RuntimeException e) {
            closeQuietly(c);
            return -code(e);
        }
    }

    /** 1 if connection h is made, 0 if not yet, else the error of making it. */
    static long finishConnect(long h) {
        try {
            Sock s = sock(h);
            SocketChannel c = s.conn();
            synchronized (s) {
                if (c.isConnected()) {
                    return 1;
                }
                if (!c.finishConnect()) {
                    return 0;
                }
                s.rkey.interestOps(SelectionKey.OP_READ);
                return 1;
            }
        } catch (IOException | RuntimeException e) {
            return -code(e);
        }
    }

    /** Reads from connection h without blocking: the number of bytes read, 0 at the end. */
    static long recv(long h, byte[] b, int off, int n) {
        try {
            Sock s = sock(h);
            SocketChannel c = s.conn();
            if (n == 0) {
                return 0;
            }
            int k = 0;
            synchronized (s) {
                if (s.ahead >= 0) {
                    b[off] = (byte) s.ahead;
                    s.ahead = -1;
                    k = 1;
                    if (n == 1) {
                        return 1;
                    }
                }
            }
            int r = c.read(ByteBuffer.wrap(b, off + k, n - k));
            if (r < 0) {
                return k;
            }
            if (r == 0 && k == 0) {
                return -WOULD_BLOCK;
            }
            return k + r;
        } catch (IOException | RuntimeException e) {
            return -code(e);
        }
    }

    /** Writes to connection h without blocking: the number of bytes written. */
    static long send(long h, byte[] b, int off, int n) {
        try {
            int r = sock(h).conn().write(ByteBuffer.wrap(b, off, n));
            return r == 0 && n > 0 ? -WOULD_BLOCK : r;
        } catch (IOException e) {
            // On Windows the text of this error is the system's, in its
            // language, so code may not know it; on a connection that was
            // made, it means that the connection is lost.
            long c = code(e);
            return -(c == 1 ? CONN_RESET : c);
        } catch (RuntimeException e) {
            return -code(e);
        }
    }

    /**
     * Whether connection h is at its end: the other side has closed it and
     * all bytes have been read; does not block. 1 or 0, or an error.
     */
    static long eof(long h) {
        try {
            Sock s = sock(h);
            SocketChannel c = s.conn();
            synchronized (s) {
                if (s.ahead >= 0) {
                    return 0;
                }
                ByteBuffer one = ByteBuffer.allocate(1);
                int r = c.read(one);
                if (r < 0) {
                    return 1;
                }
                if (r == 1) {
                    s.ahead = one.get(0) & 0xFF;
                }
                return 0;
            }
        } catch (IOException | RuntimeException e) {
            return -code(e);
        }
    }

    /**
     * Waits until socket h is ready: for writing if write, else for reading
     * (accepting, connecting); at most timeout seconds if timeout is not
     * negative. 1 if it is ready (or closed), 0 at the timeout, 2 if the
     * calling thread is alerted and alertable (Thread.TestAlert takes the
     * alert). A wait that cannot be alerted keeps the interrupt of an alert
     * for later.
     */
    static long await(long h, boolean write, double timeout, boolean alertable) {
        Sock s = socks.get(h);
        if (s == null || s.closed) {
            return 1;
        }
        boolean interrupted = false;
        try {
            Selector sel;
            if (write) {
                synchronized (s) {
                    if (s.wsel == null) {
                        s.wsel = Selector.open();
                        s.ch.register(s.wsel, SelectionKey.OP_WRITE);
                    }
                    sel = s.wsel;
                }
            } else {
                sel = s.rsel;
            }
            long deadline = timeout < 0 ? 0 : System.nanoTime() + (long) (timeout * 1e9);
            while (true) {
                // an interrupt that finds no alert belongs to one taken already
                if (java.lang.Thread.interrupted()) {
                    interrupted = !alertable;
                }
                if (alertable && m3.i.Thread.alertPending()) {
                    return 2;
                }
                int n;
                if (timeout < 0) {
                    n = sel.select();
                } else {
                    long rest = deadline - System.nanoTime();
                    if (rest <= 0) {
                        n = sel.selectNow();
                        sel.selectedKeys().clear();
                        return n > 0 ? 1 : 0;
                    }
                    n = sel.select(Math.max(1, (rest + 999_999) / 1_000_000));
                }
                sel.selectedKeys().clear();
                if (n > 0 || s.closed) {
                    return 1;
                }
            }
        } catch (IOException | RuntimeException e) {
            // a closed socket or selector: the caller finds the error
            return 1;
        } finally {
            if (interrupted) {
                java.lang.Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Sets an option of connection h: 0 TCP_NODELAY, 1 SO_KEEPALIVE, 2
     * SO_LINGER (one second if on); 0 or an error.
     */
    static long setOption(long h, long option, boolean on) {
        try {
            SocketChannel c = sock(h).conn();
            switch ((int) option) {
                case 0 -> c.setOption(StandardSocketOptions.TCP_NODELAY, on);
                case 1 -> c.setOption(StandardSocketOptions.SO_KEEPALIVE, on);
                default -> c.setOption(StandardSocketOptions.SO_LINGER, on ? 1 : -1);
            }
            return 0;
        } catch (IOException | RuntimeException e) {
            return -code(e);
        }
    }

    /** Shuts down the input (0) or output (1) of connection h; 0 or an error. */
    static long shutdown(long h, long how) {
        try {
            SocketChannel c = sock(h).conn();
            if (how == 0) {
                c.shutdownInput();
            } else {
                c.shutdownOutput();
            }
            return 0;
        } catch (IOException | RuntimeException e) {
            return -code(e);
        }
    }

    /** Closes socket h; a thread that waits for it returns. */
    static void close(long h) {
        Sock s = socks.remove(h);
        if (s == null) {
            return;
        }
        s.closed = true;
        closeQuietly(s.ch);
        try {
            s.rsel.close();
            synchronized (s) {
                if (s.wsel != null) {
                    s.wsel.close();
                }
            }
        } catch (IOException e) {
            // nothing more to free
        }
    }

    private static void closeQuietly(java.nio.channels.Channel c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                // closed as far as it can be
            }
        }
    }

    // names of hosts

    /** The first address of IP version 4 of host nm: 1 and addr set, or 0 if there is none. */
    static long hostByName(String nm, LongRef addr) {
        try {
            for (InetAddress a : InetAddress.getAllByName(nm)) {
                if (a instanceof Inet4Address) {
                    addr.set(addr(a));
                    return 1;
                }
            }
            return 0;
        } catch (UnknownHostException | RuntimeException e) {
            return 0;
        }
    }

    /** The canonical name of host nm, or null if there is none. */
    static String canonicalByName(String nm) {
        try {
            InetAddress a = InetAddress.getByName(nm);
            String c = a.getCanonicalHostName();
            return c.equals(a.getHostAddress()) ? null : c;
        } catch (UnknownHostException | RuntimeException e) {
            return null;
        }
    }

    /** The name of the host of address a, or null if there is none. */
    static String canonicalByAddr(long a) {
        InetAddress i = inet(a);
        String c = i.getCanonicalHostName();
        return c.equals(i.getHostAddress()) ? null : c;
    }

    /** An address of IP version 4 of the machine that runs the program (127.0.0.1 if it has none). */
    static long hostAddr() {
        try {
            for (InetAddress a : InetAddress.getAllByName(InetAddress.getLocalHost().getHostName())) {
                if (a instanceof Inet4Address) {
                    return addr(a);
                }
            }
        } catch (UnknownHostException | RuntimeException e) {
            // the loopback address below
        }
        return 0x7F000001L;
    }

    /**
     * The name of the machine that runs the program, as gethostname gives
     * it, or null if there is none.
     */
    static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            // the message starts with the name that has no address: "name: name: ..."
            String m = e.getMessage();
            int i = m == null ? -1 : m.indexOf(':');
            return i > 0 ? m.substring(0, i) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Some well-known services, for want of the database of services of the system. */
    private static final String[][] SERVICES = {
        {"ftp", "21"}, {"ssh", "22"}, {"telnet", "23"}, {"smtp", "25"}, {"domain", "53"},
        {"http", "80"}, {"pop3", "110"}, {"nntp", "119"}, {"imap", "143"}, {"https", "443"},
    };

    private static long servicePort(String s) {
        if (s == null) {
            return 0;
        }
        if (s.matches("[0-9]+")) {
            long p = Long.parseLong(s);
            return p <= 65535 ? p : -1;
        }
        for (String[] e : SERVICES) {
            if (e[0].equals(s)) {
                return Long.parseLong(e[1]);
            }
        }
        return -1;
    }

    /**
     * The first address of name (null: the loopback address) and the port of
     * service (null: 0): family 4 or 6, the port, the 4 or 16 bytes of the
     * address in a; 0, or an error of the lookup (11001, WSAHOST_NOT_FOUND).
     */
    static long addrInfo(String name, String service, LongRef family, LongRef port, byte[] a, int off, int n) {
        long p = servicePort(service);
        if (p < 0) {
            return 10109;   // WSATYPE_NOT_FOUND
        }
        try {
            InetAddress i = name == null ? InetAddress.getLoopbackAddress() : InetAddress.getAllByName(name)[0];
            byte[] b = i.getAddress();
            family.set(b.length == 4 ? 4 : 6);
            port.set(p);
            System.arraycopy(b, 0, a, off, Math.min(b.length, n));
            return 0;
        } catch (UnknownHostException | RuntimeException e) {
            return 11001;
        }
    }

    /**
     * The name of the host of the address in a (4 or 16 bytes, after
     * family) and the name of the service of port (null if port is 0); 0
     * or an error.
     */
    static long nameInfo(long family, long port, byte[] a, int off, int n, ObjRef host, ObjRef service) {
        try {
            byte[] b = java.util.Arrays.copyOfRange(a, off, off + (family == 4 ? 4 : 16));
            InetAddress i = family == 4 ? InetAddress.getByAddress(b) : Inet6Address.getByAddress(null, b, -1);
            host.set(i.getCanonicalHostName());
            if (port != 0) {
                String s = Long.toString(port);
                for (String[] e : SERVICES) {
                    if (e[1].equals(s)) {
                        s = e[0];
                    }
                }
                service.set(s);
            }
            return 0;
        } catch (UnknownHostException | RuntimeException e) {
            return 11001;
        }
    }
}
