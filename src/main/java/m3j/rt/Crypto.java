// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The externals M3jCrypto of the package crypto of m3j (see Externals): the
 * digests, ciphers, signatures and key agreement of the Java platform.
 *
 * <p>A TEXT stands for bytes, one character for each byte; the modules make
 * sure that no character is above 255. Nothing here throws to the program: a
 * failure is a code, the constants of the interface M3jCrypto.
 *
 * <p>The checks that the platform leaves out are here: the platform takes an
 * elliptic curve point that is not on the curve, RSA keys of any size, nonces
 * of other lengths than 12 bytes for GCM, and no empty key for HMAC.
 */
final class Crypto {
    static final long BAD_KEY = 1;
    static final long BAD_NONCE = 2;
    static final long BAD_TAG = 3;
    static final long TOO_LARGE = 4;
    static final long UNSUPPORTED = 5;

    // the kinds of keys, the order of Signature.Kind
    static final int ED25519 = 0;
    static final int RSA = 1;
    static final int P256 = 2;
    static final int P384 = 3;
    static final int P521 = 4;

    // the order of Digest.Algorithm
    private static final String[] DIGESTS = {
        "MD5", "SHA-1", "SHA-256", "SHA-384", "SHA-512", "SHA3-256", "SHA3-384", "SHA3-512"
    };
    private static final String[] MACS = {
        "HmacMD5", "HmacSHA1", "HmacSHA256", "HmacSHA384", "HmacSHA512", "HmacSHA3-256", "HmacSHA3-384",
        "HmacSHA3-512"
    };

    // the order of Signature.Algorithm
    private static final String[] SIGNATURES = {
        "Ed25519", "SHA256withRSA", "SHA384withRSA", "SHA512withRSA", "RSASSA-PSS", "RSASSA-PSS", "RSASSA-PSS",
        "SHA256withECDSAinP1363Format", "SHA384withECDSAinP1363Format", "SHA512withECDSAinP1363Format"
    };
    private static final int[] PSS_HASH = {0, 0, 0, 0, 32, 48, 64, 0, 0, 0};

    private static final String[] CURVES = {"secp256r1", "secp384r1", "secp521r1"};
    private static final ECParameterSpec[] CURVE_PARAMS = new ECParameterSpec[3];

    private static final int MIN_RSA_BITS = 2048;
    private static final int MAX_RSA_BITS = 16384;
    private static final int MAX_RSA_GENERATE = 8192;
    private static final long MAX_ITERATIONS = 100_000_000;
    private static final int MAX_PBKDF2_LENGTH = 1024;
    private static final int TAG = 16;
    private static final int NONCE = 12;

    // the algorithms in the encoded keys: rsaEncryption, id-ecPublicKey, id-Ed25519
    private static final byte[] OID_RSA = {0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01};
    private static final byte[] OID_EC = {0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x02, 0x01};
    private static final byte[] OID_ED25519 = {0x2b, 0x65, 0x70};
    // the named curves P-256, P-384 and P-521
    private static final byte[][] OID_CURVES = {
        {0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x03, 0x01, 0x07},
        {0x2b, (byte) 0x81, 0x04, 0x00, 0x22},
        {0x2b, (byte) 0x81, 0x04, 0x00, 0x23}
    };
    private static final byte[] ED25519_PREFIX = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigInteger P25519 = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19));

    static {
        try {
            for (int i = 0; i < CURVES.length; i++) {
                AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
                p.init(new ECGenParameterSpec(CURVES[i]));
                CURVE_PARAMS[i] = p.getParameterSpec(ECParameterSpec.class);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private Crypto() {}

    private static byte[] bytes(String t) {
        return t.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String text(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    // randomness and comparison

    static String random(long n) {
        byte[] b = new byte[(int) n];
        RANDOM.nextBytes(b);
        return text(b);
    }

    static void randomBytes(byte[] b, int off, int n) {
        if (off == 0 && n == b.length) {
            RANDOM.nextBytes(b);
        } else {
            byte[] r = new byte[n];
            RANDOM.nextBytes(r);
            System.arraycopy(r, 0, b, off, n);
        }
    }

    /** Equal texts; the time depends on the lengths only. */
    static boolean equal(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int d = 0;
        for (int i = 0; i < a.length(); i++) {
            d |= a.charAt(i) ^ b.charAt(i);
        }
        return d == 0;
    }

    // digests and HMAC

    /** A digest or a MAC in progress; the module locks it. */
    private static final class Hash {
        final MessageDigest digest;
        final Mac mac;

        Hash(MessageDigest digest, Mac mac) {
            this.digest = digest;
            this.mac = mac;
        }

        void update(byte[] b, int off, int n) {
            if (digest != null) {
                digest.update(b, off, n);
            } else {
                mac.update(b, off, n);
            }
        }

        byte[] finish() {
            return digest != null ? digest.digest() : mac.doFinal();
        }
    }

    private static MessageDigest messageDigest(long alg) {
        try {
            return MessageDigest.getInstance(DIGESTS[(int) alg]);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A MAC with the key k. The platform takes no empty key; HMAC fills a key
     * up with zero bytes, so that one zero byte is the same key.
     */
    private static Mac mac(long alg, byte[] k) {
        try {
            Mac m = Mac.getInstance(MACS[(int) alg]);
            m.init(new SecretKeySpec(k.length == 0 ? new byte[1] : k, "HMAC"));
            return m;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static Object digestNew(long alg) {
        return new Hash(messageDigest(alg), null);
    }

    static Object hmacNew(long alg, String key) {
        return new Hash(null, mac(alg, bytes(key)));
    }

    static void digestUpdate(Object h, byte[] b, int off, int n) {
        ((Hash) h).update(b, off, n);
    }

    static String digestFinish(Object h) {
        return text(((Hash) h).finish());
    }

    static String digestOf(long alg, String data) {
        return text(messageDigest(alg).digest(bytes(data)));
    }

    static String hmac(long alg, String key, String data) {
        return text(mac(alg, bytes(key)).doFinal(bytes(data)));
    }

    // authenticated encryption

    /** The cipher for alg (0 AES-GCM, 1 ChaCha20-Poly1305), or null with code set. */
    private static Cipher aead(long alg, int mode, String key, String nonce, LongRef code) throws GeneralSecurityException {
        int k = key.length();
        if (alg == 0 ? k != 16 && k != 24 && k != 32 : k != 32) {
            code.set(BAD_KEY);
            return null;
        }
        if (nonce.length() != NONCE) {
            code.set(BAD_NONCE);
            return null;
        }
        Cipher c;
        if (alg == 0) {
            c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(mode, new SecretKeySpec(bytes(key), "AES"), new GCMParameterSpec(TAG * 8, bytes(nonce)));
        } else {
            c = Cipher.getInstance("ChaCha20-Poly1305");
            c.init(mode, new SecretKeySpec(bytes(key), "ChaCha20"), new IvParameterSpec(bytes(nonce)));
        }
        return c;
    }

    /** The ciphertext and the tag, or null with code set. */
    static String aeadSeal(long alg, String key, String nonce, String plain, String aad, LongRef code) {
        if (plain.length() > Integer.MAX_VALUE - 64) {
            code.set(TOO_LARGE);
            return null;
        }
        try {
            Cipher c = aead(alg, Cipher.ENCRYPT_MODE, key, nonce, code);
            if (c == null) {
                return null;
            }
            if (!aad.isEmpty()) {
                c.updateAAD(bytes(aad));
            }
            return text(c.doFinal(bytes(plain)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The plain text, or null with code set; never a part of it. */
    static String aeadOpen(long alg, String key, String nonce, String sealed, String aad, LongRef code) {
        try {
            Cipher c = aead(alg, Cipher.DECRYPT_MODE, key, nonce, code);
            if (c == null) {
                return null;
            }
            if (sealed.length() < TAG) {
                code.set(BAD_TAG);
                return null;
            }
            if (!aad.isEmpty()) {
                c.updateAAD(bytes(aad));
            }
            return text(c.doFinal(bytes(sealed)));
        } catch (AEADBadTagException e) {
            code.set(BAD_TAG);
            return null;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // key derivation

    /** PBKDF2 of RFC 8018 with HMAC, or null with code set. */
    static String pbkdf2(long alg, String password, String salt, long iterations, long length, LongRef code) {
        if (iterations < 1) {
            code.set(UNSUPPORTED);
            return null;
        }
        if (iterations > MAX_ITERATIONS || length > MAX_PBKDF2_LENGTH) {
            code.set(TOO_LARGE);
            return null;
        }
        Mac m = mac(alg, bytes(password));
        int h = m.getMacLength();
        byte[] s = bytes(salt);
        byte[] out = new byte[(int) length];
        byte[] u = new byte[h];
        byte[] t = new byte[h];
        try {
            for (int block = 1, done = 0; done < length; block++, done += h) {
                m.update(s);
                m.update(new byte[] {(byte) (block >>> 24), (byte) (block >>> 16), (byte) (block >>> 8), (byte) block});
                m.doFinal(u, 0);
                System.arraycopy(u, 0, t, 0, h);
                for (long i = 1; i < iterations; i++) {
                    m.update(u);
                    m.doFinal(u, 0);
                    for (int j = 0; j < h; j++) {
                        t[j] ^= u[j];
                    }
                }
                System.arraycopy(t, 0, out, done, (int) Math.min(h, length - done));
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        return text(out);
    }

    /** HKDF of RFC 5869, or null with code set. An empty salt is HashLen zeros, which is the same key. */
    static String hkdf(long alg, String ikm, String salt, String info, long length, LongRef code) {
        Mac m = mac(alg, bytes(salt));
        int h = m.getMacLength();
        if (length > 255L * h) {
            code.set(TOO_LARGE);
            return null;
        }
        byte[] prk = m.doFinal(bytes(ikm));
        Mac e = mac(alg, prk);
        byte[] in = bytes(info);
        byte[] out = new byte[(int) length];
        byte[] t = new byte[0];
        for (int i = 1, done = 0; done < length; i++, done += h) {
            e.update(t);
            e.update(in);
            e.update((byte) i);
            t = e.doFinal();
            System.arraycopy(t, 0, out, done, (int) Math.min(h, length - done));
        }
        return text(out);
    }

    // keys

    private static int curveOf(ECParameterSpec p) {
        for (int i = 0; i < CURVE_PARAMS.length; i++) {
            ECParameterSpec c = CURVE_PARAMS[i];
            if (c.getOrder().equals(p.getOrder()) && c.getCofactor() == p.getCofactor()
                    && c.getCurve().equals(p.getCurve()) && c.getGenerator().equals(p.getGenerator())) {
                return i;
            }
        }
        return -1;
    }

    private static int coordinateBytes(int curve) {
        return (((ECFieldFp) CURVE_PARAMS[curve].getCurve().getField()).getP().bitLength() + 7) / 8;
    }

    /** Is (x, y) a point of the curve, with coordinates less than its prime? */
    private static boolean onCurve(ECParameterSpec c, ECPoint w) {
        if (w.equals(ECPoint.POINT_INFINITY)) {
            return false;
        }
        BigInteger p = ((ECFieldFp) c.getCurve().getField()).getP();
        BigInteger x = w.getAffineX();
        BigInteger y = w.getAffineY();
        if (x.signum() < 0 || y.signum() < 0 || x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
            return false;
        }
        BigInteger right = x.pow(3).add(c.getCurve().getA().multiply(x)).add(c.getCurve().getB());
        return y.multiply(y).subtract(right).mod(p).signum() == 0;
    }

    private static boolean rsaSize(RSAKey k) {
        int bits = k.getModulus().bitLength();
        return bits >= MIN_RSA_BITS && bits <= MAX_RSA_BITS;
    }

    /** The kind of a key, or -1 if it is none of ours or breaks a rule. Every key passes here. */
    private static int check(Key k) {
        if (k instanceof RSAPublicKey r) {
            BigInteger e = r.getPublicExponent();
            boolean ok = rsaSize(r) && e.testBit(0) && e.compareTo(BigInteger.ONE) > 0
                    && e.compareTo(r.getModulus()) < 0;
            return ok ? RSA : -1;
        } else if (k instanceof RSAKey r && k instanceof PrivateKey) {
            return rsaSize(r) ? RSA : -1;
        } else if (k instanceof ECPublicKey e) {
            int c = curveOf(e.getParams());
            return c >= 0 && onCurve(CURVE_PARAMS[c], e.getW()) ? P256 + c : -1;
        } else if (k instanceof ECPrivateKey e) {
            int c = curveOf(e.getParams());
            BigInteger s = e.getS();
            return c >= 0 && s.signum() > 0 && s.compareTo(CURVE_PARAMS[c].getOrder()) < 0 ? P256 + c : -1;
        } else if (k.getAlgorithm().equals("Ed25519") || k.getAlgorithm().equals("EdDSA")) {
            return ED25519;
        }
        return -1;
    }

    /** The key, if it passes the checks, or null with code BAD_KEY. */
    private static <K extends Key> K checked(K k, LongRef code) {
        if (check(k) < 0) {
            code.set(BAD_KEY);
            return null;
        }
        return k;
    }

    private static byte[] unsigned(BigInteger v) {
        byte[] b = v.toByteArray();
        return b.length > 1 && b[0] == 0 ? Arrays.copyOfRange(b, 1, b.length) : b;
    }

    private static byte[] fixed(BigInteger v, int n) {
        byte[] b = unsigned(v);
        byte[] r = new byte[n];
        System.arraycopy(b, 0, r, n - b.length, b.length);
        return r;
    }

    static Object ed25519Public(String raw, LongRef code) {
        if (raw.length() != 32) {
            code.set(BAD_KEY);
            return null;
        }
        byte[] der = Arrays.copyOf(ED25519_PREFIX, ED25519_PREFIX.length + 32);
        System.arraycopy(bytes(raw), 0, der, ED25519_PREFIX.length, 32);
        try {
            return checked(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der)), code);
        } catch (GeneralSecurityException | RuntimeException e) {
            code.set(BAD_KEY);
            return null;
        }
    }

    static Object rsaPublic(String n, String e, LongRef code) {
        try {
            RSAPublicKeySpec s = new RSAPublicKeySpec(new BigInteger(1, bytes(n)), new BigInteger(1, bytes(e)));
            return checked(KeyFactory.getInstance("RSA").generatePublic(s), code);
        } catch (GeneralSecurityException | RuntimeException x) {
            code.set(BAD_KEY);
            return null;
        }
    }

    static Object ecPublic(long curve, String x, String y, LongRef code) {
        int c = (int) curve;
        int n = coordinateBytes(c);
        if (x.length() != n || y.length() != n) {
            code.set(BAD_KEY);
            return null;
        }
        ECPoint w = new ECPoint(new BigInteger(1, bytes(x)), new BigInteger(1, bytes(y)));
        if (!onCurve(CURVE_PARAMS[c], w)) {
            code.set(BAD_KEY);
            return null;
        }
        try {
            return checked(KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, CURVE_PARAMS[c])), code);
        } catch (GeneralSecurityException | RuntimeException e) {
            code.set(BAD_KEY);
            return null;
        }
    }

    /** Reads the tag and the length of a DER element at pos[0]; the length, or -1. */
    private static int element(byte[] d, int[] pos, int tag, int end) {
        int i = pos[0];
        if (i + 2 > end || (d[i] & 0xff) != tag) {
            return -1;
        }
        int len = d[i + 1] & 0xff;
        i += 2;
        if (len >= 0x80) {
            int k = len - 0x80;
            if (k < 1 || k > 3 || i + k > end) {
                return -1;
            }
            len = 0;
            for (int j = 0; j < k; j++) {
                len = len * 256 + (d[i + j] & 0xff);
            }
            i += k;
        }
        if (len > end - i) {
            return -1;
        }
        pos[0] = i;
        return len;
    }

    /**
     * The identifier of the algorithm of an encoded key (SubjectPublicKeyInfo,
     * or PrivateKeyInfo if private) and that of its parameters, if they are
     * one, or null if the structure is wrong.
     */
    private static byte[][] algorithm(byte[] d, boolean priv) {
        int[] pos = {0};
        int len = element(d, pos, 0x30, d.length);
        if (len < 0 || pos[0] + len != d.length) {
            return null;
        }
        if (priv) {
            int v = element(d, pos, 0x02, d.length);
            if (v < 0) {
                return null;
            }
            pos[0] += v;
        }
        int a = element(d, pos, 0x30, d.length);
        if (a < 0) {
            return null;
        }
        int end = pos[0] + a;
        int o = element(d, pos, 0x06, end);
        if (o < 0) {
            return null;
        }
        byte[] oid = Arrays.copyOfRange(d, pos[0], pos[0] + o);
        pos[0] += o;
        int p = element(d, pos, 0x06, end);
        return new byte[][] {oid, p < 0 ? null : Arrays.copyOfRange(d, pos[0], pos[0] + p)};
    }

    /** The name of the key factory for an algorithm, or null if it is not one of ours. */
    private static String factory(byte[][] alg) {
        if (Arrays.equals(alg[0], OID_RSA)) {
            return "RSA";
        } else if (Arrays.equals(alg[0], OID_ED25519)) {
            return "Ed25519";
        } else if (Arrays.equals(alg[0], OID_EC) && alg[1] != null) {
            for (byte[] c : OID_CURVES) {
                if (Arrays.equals(alg[1], c)) {
                    return "EC";
                }
            }
        }
        return null;
    }

    /** A key from its DER encoding (SubjectPublicKeyInfo or PrivateKeyInfo), or null with code set. */
    static Object fromDer(String der, boolean priv, LongRef code) {
        byte[] d = bytes(der);
        byte[][] alg = algorithm(d, priv);
        if (alg == null) {
            code.set(BAD_KEY);
            return null;
        }
        String f = factory(alg);
        if (f == null) {
            code.set(UNSUPPORTED);
            return null;
        }
        try {
            KeyFactory kf = KeyFactory.getInstance(f);
            Key k = priv ? kf.generatePrivate(new PKCS8EncodedKeySpec(d)) : kf.generatePublic(new X509EncodedKeySpec(d));
            return checked(k, code);
        } catch (GeneralSecurityException | RuntimeException e) {
            code.set(BAD_KEY);
            return null;
        }
    }

    static long keyKind(Object k) {
        return check((Key) k);
    }

    static String keyDer(Object k) {
        return text(((Key) k).getEncoded());
    }

    /** Part i (0 or 1) of a public key: n and e, x and y, or the raw key and null. */
    static String publicPart(Object o, long i) {
        Key k = (Key) o;
        if (k instanceof RSAPublicKey r) {
            return text(unsigned(i == 0 ? r.getModulus() : r.getPublicExponent()));
        } else if (k instanceof ECPublicKey e) {
            int n = coordinateBytes(curveOf(e.getParams()));
            return text(fixed(i == 0 ? e.getW().getAffineX() : e.getW().getAffineY(), n));
        } else if (k instanceof EdECPublicKey) {
            byte[] d = k.getEncoded();
            return i == 0 ? text(Arrays.copyOfRange(d, d.length - 32, d.length)) : null;
        }
        return null;
    }

    /** A new pair of keys of the kind, or null with code set; the private key goes to priv. */
    static Object generate(long kind, long bits, ObjRef priv, LongRef code) {
        try {
            KeyPairGenerator g;
            if (kind == ED25519) {
                g = KeyPairGenerator.getInstance("Ed25519");
            } else if (kind == RSA) {
                if (bits < MIN_RSA_BITS || bits > MAX_RSA_GENERATE) {
                    code.set(BAD_KEY);
                    return null;
                }
                g = KeyPairGenerator.getInstance("RSA");
                g.initialize((int) bits, RANDOM);
            } else {
                g = KeyPairGenerator.getInstance("EC");
                g.initialize(new ECGenParameterSpec(CURVES[(int) kind - P256]), RANDOM);
            }
            KeyPair p = g.generateKeyPair();
            priv.set(p.getPrivate());
            return p.getPublic();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Signature signature(long alg) throws GeneralSecurityException {
        Signature s = Signature.getInstance(SIGNATURES[(int) alg]);
        int h = PSS_HASH[(int) alg];
        if (h != 0) {
            String name = "SHA-" + h * 8;
            MGF1ParameterSpec mgf = h == 32 ? MGF1ParameterSpec.SHA256
                    : h == 48 ? MGF1ParameterSpec.SHA384 : MGF1ParameterSpec.SHA512;
            s.setParameter(new PSSParameterSpec(name, "MGF1", mgf, h, PSSParameterSpec.TRAILER_FIELD_BC));
        }
        return s;
    }

    /** The signature of data, or null with code set. The module has matched the key to alg. */
    static String sign(long alg, Object k, String data, LongRef code) {
        try {
            Signature s = signature(alg);
            s.initSign((PrivateKey) k, RANDOM);
            s.update(bytes(data));
            return text(s.sign());
        } catch (InvalidKeyException e) {
            code.set(BAD_KEY);
            return null;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Does sig sign data? Whatever is wrong with sig is a "no". */
    static boolean verify(long alg, Object k, String data, String sig) {
        try {
            Signature s = signature(alg);
            s.initVerify((PublicKey) k);
            s.update(bytes(data));
            return s.verify(bytes(sig));
        } catch (GeneralSecurityException | RuntimeException e) {
            return false;
        }
    }

    // X25519

    private static byte[] reversed(byte[] b) {
        byte[] r = new byte[b.length];
        for (int i = 0; i < b.length; i++) {
            r[i] = b[b.length - 1 - i];
        }
        return r;
    }

    /**
     * The function X25519 of RFC 7748 of a scalar and a u-coordinate, both 32
     * bytes, or null with code set. The highest bit of u is ignored and u is
     * taken modulo p, as the RFC says; a result of zeros (a point of small
     * order) is BAD_KEY.
     */
    static String x25519(String scalar, String u, LongRef code) {
        if (scalar.length() != 32 || u.length() != 32) {
            code.set(BAD_KEY);
            return null;
        }
        byte[] b = bytes(u);
        b[31] &= 0x7f;
        try {
            KeyFactory f = KeyFactory.getInstance("XDH");
            PrivateKey k = f.generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, bytes(scalar)));
            PublicKey p = f.generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519,
                    new BigInteger(1, reversed(b)).mod(P25519)));
            KeyAgreement a = KeyAgreement.getInstance("XDH");
            a.init(k);
            a.doPhase(p, true);
            return text(a.generateSecret());
        } catch (GeneralSecurityException | RuntimeException e) {
            code.set(BAD_KEY);
            return null;
        }
    }
}
