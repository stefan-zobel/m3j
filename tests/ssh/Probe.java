// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import m3j.jsch.ChannelExec;
import m3j.jsch.ChannelSftp;
import m3j.jsch.JSch;
import m3j.jsch.Logger;
import m3j.jsch.Session;

/**
 * A helper of tools/ssh_test.py: the JSch of m3j directly, without the
 * library ssh, so that a part left out by tools/jsch_import.py shows.
 *
 * <pre>java -cp m3j-ssh.jar Probe.java HOST PORT USER PASSWORD KNOWN_HOSTS</pre>
 *
 * Each line of the input names a key exchange, a host key algorithm, a
 * cipher and a MAC ("-" for the defaults). For each the probe connects,
 * runs "echo probe", lists the directory of SFTP and prints one line:
 * "ok KEX HOSTKEY CIPHER MAC | OUTPUT STATUS | NAMES" with what was
 * negotiated, or "error CLASS: MESSAGE". The line "mlkem" checks ML-KEM-768
 * without a server instead.
 */
public final class Probe {
  private static final List<String> LOG = new ArrayList<>();

  private Probe() {}

  private static String after(String prefix) {
    synchronized (LOG) {
      for (String m : LOG) {
        if (m.startsWith(prefix)) {
          return m.substring(prefix.length()).trim();
        }
      }
    }
    return "?";
  }

  private static String run(String[] a, String[] f) throws Exception {
    JSch jsch = new JSch();
    jsch.setKnownHosts(a[4]);
    Session s = jsch.getSession(a[2], a[0], Integer.parseInt(a[1]));
    s.setPassword(a[3].getBytes(StandardCharsets.UTF_8));
    s.setConfig("StrictHostKeyChecking", "yes");
    s.setConfig("PreferredAuthentications", "password");
    String[][] keys = {{"kex"}, {"server_host_key"}, {"cipher.s2c", "cipher.c2s"}, {"mac.s2c", "mac.c2s"}};
    for (int i = 0; i < 4; i++) {
      if (!f[i].equals("-")) {
        for (String k : keys[i]) {
          s.setConfig(k, f[i]);
        }
      }
    }
    try {
      s.connect(20000);
      ChannelExec c = (ChannelExec) s.openChannel("exec");
      c.setCommand("echo probe");
      InputStream in = c.getInputStream();
      c.connect(20000);
      String out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
      while (!c.isClosed()) {
        Thread.sleep(10);
      }
      int status = c.getExitStatus();
      c.disconnect();
      ChannelSftp sftp = (ChannelSftp) s.openChannel("sftp");
      sftp.connect(20000);
      TreeSet<String> names = new TreeSet<>();
      for (ChannelSftp.LsEntry e : sftp.ls(".")) {
        if (!e.getFilename().equals(".") && !e.getFilename().equals("..")) {
          names.add(e.getFilename());
        }
      }
      sftp.disconnect();
      // "kex: server->client cipher: aes128-ctr MAC: hmac-sha1 compression: none"
      String[] s2c = after("kex: server->client cipher:").split(" ");
      String mac = s2c.length > 2 ? s2c[2] : "?";
      return "ok " + after("kex: algorithm:") + " " + after("kex: host key algorithm:") + " " + s2c[0] + " "
          + mac + " | " + out + " " + status + " | " + String.join(",", names);
    } finally {
      s.disconnect();
    }
  }

  /**
   * The ML-KEM-768 of JSch against the one of the JDK, without a server
   * (asyncssh does not know it): JSch makes the key pair, the JDK
   * encapsulates, JSch decapsulates, the secrets must agree.
   */
  private static String mlkem() throws Exception {
    String name = JSch.getConfig("mlkem768");
    m3j.jsch.KEM kem =
        Class.forName(name).asSubclass(m3j.jsch.KEM.class).getDeclaredConstructor().newInstance();
    kem.init();
    byte[] raw = kem.getPublicKey();
    // SubjectPublicKeyInfo of ML-KEM-768 (OID 2.16.840.1.101.3.4.4.2) up to the key
    byte[] prefix = HexFormat.of().parseHex("308204b2300b0609608648016503040402038204a100");
    byte[] spki = new byte[prefix.length + raw.length];
    System.arraycopy(prefix, 0, spki, 0, prefix.length);
    System.arraycopy(raw, 0, spki, prefix.length, raw.length);
    PublicKey pub = KeyFactory.getInstance("ML-KEM").generatePublic(new X509EncodedKeySpec(spki));
    javax.crypto.KEM.Encapsulated e = javax.crypto.KEM.getInstance("ML-KEM").newEncapsulator(pub).encapsulate();
    boolean same = Arrays.equals(e.key().getEncoded(), kem.decapsulate(e.encapsulation()));
    return (same ? "ok " : "error secrets differ ") + name + " " + raw.length;
  }

  public static void main(String[] a) throws Exception {
    JSch.setLogger(new Logger() {
      @Override
      public boolean isEnabled(int level) {
        return level >= Logger.INFO;
      }

      @Override
      public void log(int level, String message) {
        synchronized (LOG) {
          LOG.add(message);
        }
      }
    });
    BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    for (String line; (line = r.readLine()) != null; ) {
      String[] f = line.trim().split(" ");
      synchronized (LOG) {
        LOG.clear();
      }
      String answer;
      try {
        answer = f[0].equals("mlkem") ? mlkem() : run(a, f);
      } catch (Throwable t) {
        answer = "error " + t.getClass().getName() + ": " + t.getMessage();
      }
      System.out.println(answer);
      System.out.flush();
    }
  }
}
