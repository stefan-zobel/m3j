// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/**
 * A helper of tools/tls_test.py.
 *
 * <pre>
 * Peer export STORE PASSWORD OUT     the key and the certificates of STORE as PEM
 * Peer rekey STORE PASSWORD VERSION  a server that prints its port and echoes one
 *                                    client; after the first bytes it starts a new
 *                                    handshake: under TLSv1.2 a renegotiation,
 *                                    under TLSv1.3 an update of the keys
 * </pre>
 */
public final class Peer {
    private static String pem(String what, byte[] der) {
        return "-----BEGIN " + what + "-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der)
            + "\n-----END " + what + "-----\n";
    }

    private static void export(String store, char[] password, String out) throws Exception {
        KeyStore k = KeyStore.getInstance(new File(store), password);
        String alias = k.aliases().nextElement();
        for (var e = k.aliases(); e.hasMoreElements();) {
            String a = e.nextElement();
            if (k.isKeyEntry(a)) {
                alias = a;
            }
        }
        Key key = k.getKey(alias, password);
        StringBuilder s = new StringBuilder(pem("PRIVATE KEY", key.getEncoded()));
        for (Certificate c : k.getCertificateChain(alias)) {
            s.append(pem("CERTIFICATE", c.getEncoded()));
        }
        Files.writeString(Path.of(out), s.toString(), StandardCharsets.US_ASCII);
    }

    private static void rekey(String store, char[] password, String version) throws Exception {
        KeyManagerFactory f = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        f.init(KeyStore.getInstance(new File(store), password), password);
        SSLContext c = SSLContext.getInstance("TLS");
        c.init(f.getKeyManagers(), null, null);
        try (SSLServerSocket l = (SSLServerSocket) c.getServerSocketFactory().createServerSocket(0)) {
            System.out.println("port " + l.getLocalPort());
            System.out.flush();
            l.setEnabledProtocols(new String[] {version});
            try (SSLSocket s = (SSLSocket) l.accept()) {
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                byte[] b = new byte[8192];
                boolean again = true;
                long total = 0;
                for (int n = in.read(b); n > 0; n = in.read(b)) {
                    out.write(b, 0, n);
                    out.flush();
                    total += n;
                    if (again && total > 20000) {
                        again = false;
                        s.startHandshake();
                    }
                }
                System.out.println("bytes " + total + " handshake again " + !again);
            }
        }
    }

    public static void main(String[] a) throws Exception {
        if (a[0].equals("export")) {
            export(a[1], a[2].toCharArray(), a[3]);
        } else {
            rekey(a[1], a[2].toCharArray(), a[3]);
        }
    }
}
