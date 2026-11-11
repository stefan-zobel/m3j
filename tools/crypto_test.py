#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of the library crypto of m3j, through tests/crypto/src/Main.m3,
against hashlib, hmac and uuid of Python, the packages cryptography and
PyNaCl, openssl and the tables of the RFCs. They need no connection to the
internet.

  python3 tools/crypto_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                               [--keep] [--timeout SECONDS] [--speed]

The cases:

  random    random bytes and the comparison of secrets
  digest    all digests against hashlib, whole, in pieces and from readers
  hmac      RFC 4231 and all digests against hmac
  threads   threads on one digest; failures and alerts of a reader
  aead      AES-GCM and ChaCha20-Poly1305: vectors, both directions, every
            changed byte, lengths of keys, nonces and messages
  kdf       PBKDF2 and HKDF: RFC 6070 and 5869, hashlib, limits
  ed25519   RFC 8032, cryptography and PyNaCl, damaged signatures
  rsa       PKCS #1 v1.5 and PSS against cryptography, rules for keys
  ecdsa     P-256, P-384, P-521 against cryptography, points off the curve
  mismatch  every algorithm with every kind of key
  pem       keys of openssl and cryptography, damaged and foreign PEM texts
  x25519    RFC 7748, cryptography, points of small order
  uuid      versions 4 and 7 against the module uuid
  notbytes  characters above 255 everywhere

--speed adds measurements, which are only reported.
"""

import argparse
import hashlib
import hmac
import os
import random
import shutil
import subprocess
import sys
import tempfile
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "crypto"
RNG = random.Random(20261110)

DIGESTS = ["md5", "sha1", "sha256", "sha384", "sha512", "sha3_256", "sha3_384", "sha3_512"]
BLOCK = {"md5": 64, "sha1": 64, "sha256": 64, "sha384": 128, "sha512": 128, "sha3_256": 136, "sha3_384": 104,
         "sha3_512": 72}
SIZE = {h: hashlib.new(h).digest_size for h in DIGESTS}
# Signature.Algorithm and Signature.Kind
ED, RS256, RS384, RS512, PS256, PS384, PS512, ES256, ES384, ES512 = range(10)
K_ED, K_RSA, K_P256, K_P384, K_P521 = range(5)
KIND_OF = [K_ED, K_RSA, K_RSA, K_RSA, K_RSA, K_RSA, K_RSA, K_P256, K_P384, K_P521]


class Failed(Exception):
    pass


def need():
    """The packages that the cases need; a clear failure if one is missing."""
    try:
        import cryptography  # noqa: F401
        import nacl  # noqa: F401
    except ImportError as e:
        raise Failed("the Python packages cryptography and PyNaCl are needed: %s" % e)


def x(b):
    if isinstance(b, str):
        b = b.encode("latin-1")
    return "x" + b.hex()


def w(s):
    d = s.encode("utf-16-be", "surrogatepass")
    return "w" + d.hex()


def err(name):
    return "err Crypto." + name


def rnd(n):
    return RNG.randbytes(n)


def flip(b, i, bit=None):
    b = bytearray(b)
    b[i] ^= 1 << (i % 8 if bit is None else bit)
    return bytes(b)


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.java = args.java
        self.cp = str(work / "classes") + os.pathsep + args.jar
        self.batches = 0

    def compile(self):
        cmd = [self.java, "-Xss64m", "-cp", self.args.jar, "m3j.jvm.Compile", str(TESTS / "src" / "Main.m3"),
               "-d", str(self.work / "classes")]
        r = subprocess.run(cmd, cwd=self.work, capture_output=True, text=True, stdin=subprocess.DEVNULL)
        if r.returncode or "no module" in r.stdout + r.stderr or "warning" in r.stdout + r.stderr:
            raise Failed("compile:\n" + r.stdout + r.stderr)

    def batch(self, lines):
        """The result line of each of the lines."""
        self.batches += 1
        name = "b%d" % self.batches
        (self.work / (name + ".in")).write_text("\n".join(lines) + "\n", newline="\n")
        r = subprocess.run([self.java, "-cp", self.cp, "m3.Main", "batch", name + ".in", name + ".out"],
                           cwd=self.work, capture_output=True, timeout=self.args.timeout)
        if r.returncode or r.stderr:
            raise Failed("exit %d: %s" % (r.returncode, (r.stdout + r.stderr).decode("latin-1")[:2000]))
        out = (self.work / (name + ".out")).read_text().splitlines()
        if len(out) != len(lines):
            raise Failed("%d results for %d lines" % (len(out), len(lines)))
        return out


def check(got, want, what):
    if got != want:
        raise Failed("%s: got %r, want %r" % (what, got, want))


def compare(env, lines, wants, shows=None):
    for i, (got, want) in enumerate(zip(env.batch(lines), wants)):
        if want is None:
            continue
        ok = want(got) if callable(want) else got == want
        if not ok:
            raise Failed("%s: got %r, want %r" % (shows[i] if shows else lines[i][:200], got[:300],
                                                  "(a condition)" if callable(want) else want[:300]))


def unx(t):
    if not t.startswith("x"):
        raise Failed("not bytes: %r" % t[:200])
    return bytes.fromhex(t[1:])


def is_bytes(n=None):
    return lambda t: t.startswith("x") and (n is None or len(t) == 1 + 2 * n)


# randomness and comparison

def case_random(env):
    lines = ["rn 0", "rn 1", "rn 1000", "rn 1000", "rb 0", "rb 16", "rb 16", "rn 100000",
             "eq x x", "eq x00 x00", "eq x0102 x0102", "eq x0102 x0103", "eq x0102 x010200", "eq x x00",
             "eq " + w("中") + " " + w("中"), "eq " + w("中") + " " + w("丮"),
             "eq x" + "ab" * 1000 + " x" + "ab" * 999 + "ac"]
    r = env.batch(lines)
    check([len(t) for t in r[:7]], [1, 3, 2001, 2001, 1, 33, 33], "lengths")
    check(r[2] != r[3] and r[5] != r[6], True, "random results differ")
    counts = [0] * 256
    for b in unx(r[7]):
        counts[b] += 1
    if min(counts) < 250 or max(counts) > 550:
        raise Failed("the bytes of 100000 random ones are not spread: %d .. %d" % (min(counts), max(counts)))
    check(r[8:], ["yes", "yes", "yes", "no", "no", "no", "yes", "no", "no"], "Equal")


# digests and HMAC

def case_digest(env):
    lines, wants = [], []
    for i, h in enumerate(DIGESTS):
        b = BLOCK[h]
        sizes = sorted(set(list(range(0, 300, 7)) + [b - 9, b - 8, b - 1, b, b + 1, 2 * b - 1, 2 * b, 100000]))
        for n in sizes:
            d = rnd(n)
            lines.append("dg %d %s" % (i, x(d)))
            wants.append(x(hashlib.new(h, d).digest()))
        for _ in range(3):
            d = rnd(1000)
            cuts = sorted(RNG.sample(range(1, 1000), 9))
            parts = [d[a:c] for a, c in zip([0] + cuts, cuts + [1000])]
            parts.insert(RNG.randrange(len(parts)), b"")
            want = x(hashlib.new(h, d).digest())
            lines.append("dc %d %s" % (i, " ".join(x(p) for p in parts)))
            wants.append(want + " " + want)
        want = x(hashlib.new(h).digest())
        lines.append("dc %d" % i)
        wants.append(want + " " + want)
        for size in (-1, 0, 1, 5, 4096):
            d = rnd(RNG.choice([0, 1, 70001, 100000]))
            lines.append("dr %d %s %d" % (i, x(d), size))
            wants.append(x(hashlib.new(h, d).digest()))
    # the bytes that a decoder of UTF-8 would take apart
    d = bytes(range(256)) * 3
    lines.append("dg 2 " + x(d))
    wants.append(x(hashlib.sha256(d).digest()))
    compare(env, lines, wants)


RFC4231 = [
    (b"\x0b" * 20, b"Hi There", {
        "sha256": "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
        "sha384": "afd03944d84895626b0825f4ab46907f15f9dadbe4101ec682aa034c7cebc59cfaea9ea9076ede7f4af152e8b2fa9cb6",
        "sha512": "87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cdedaa833b7d6b8a702038b274eaea3f4e4be"
                  "9d914eeb61f1702e696c203a126854"}),
    (b"Jefe", b"what do ya want for nothing?", {
        "sha256": "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
        "sha384": "af45d2e376484031617f78d2b58a6b1b9c7ef464f5a01b47e42ec3736322445e8e2240ca5e69e2c78b3239ecfab21649",
        "sha512": "164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea2505549758bf75c05a994a6d034f65f8f0e6fd"
                  "caeab1a34d4a6b4b636e070a38bce737"}),
    (b"\xaa" * 131, b"Test Using Larger Than Block-Size Key - Hash Key First", {
        "sha256": "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
        "sha384": "4ece084485813e9088d2c63a041bc5b44f9ef1012a2b588f3cd11f05033ac4c60c2ef6ab4030fe8296248df163f44952",
        "sha512": "80b24263c7c1a3ebb71493c1dd7be8b49b46d1f41b4aeec1121b013783f8f3526b56d037e05f2598bd0fd2215d6a1e52"
                  "95e64f73f63f0aec8b915a985d786598"}),
]


def case_hmac(env):
    lines, wants = [], []
    for key, data, macs in RFC4231:
        for h, mac in macs.items():
            check(hmac.new(key, data, h).hexdigest(), mac, "table of RFC 4231")
            lines.append("hm %d %s %s" % (DIGESTS.index(h), x(key), x(data)))
            wants.append("x" + mac)
    for i, h in enumerate(DIGESTS):
        b = BLOCK[h]
        for kn in (0, 1, b - 1, b, b + 1, 2 * b + 3):
            for dn in (0, 1, 55, 64, 200):
                key, data = rnd(kn), rnd(dn)
                lines.append("hm %d %s %s" % (i, x(key), x(data)))
                wants.append(x(hmac.new(key, data, h).digest()))
        # a key of zeros is the empty key
        lines.append("hm %d x00 x61" % i)
        wants.append(x(hmac.new(b"", b"a", h).digest()))
        key, d = rnd(RNG.choice([0, 20, 200])), rnd(500)
        cuts = sorted(RNG.sample(range(1, 500), 5))
        parts = [d[a:c] for a, c in zip([0] + cuts, cuts + [500])]
        want = x(hmac.new(key, d, h).digest())
        lines.append("hc %d %s %s" % (i, x(key), " ".join(x(p) for p in parts)))
        wants.append(want + " " + want)
    compare(env, lines, wants)


def case_threads(env):
    lines = ["dt 2 8 2000 10", "dt 6 16 500 7", "df 2 1", "df 2 0"]
    wants = [x(hashlib.sha256(b"a" * 160000).digest()), x(hashlib.sha3_384(b"a" * 56000).digest()), "failure",
             "alerted"]
    compare(env, lines, wants)


# authenticated encryption

GCM = [  # key, nonce, plain, aad, ciphertext, tag: the test cases of the specification of GCM
    ("00" * 16, "00" * 12, "", "", "", "58e2fccefa7e3061367f1d57a4e7455a"),
    ("00" * 16, "00" * 12, "00" * 16, "", "0388dace60b6a392f328c2b971b2fe78", "ab6e47d42cec13bdf53a67b21257bddf"),
    ("feffe9928665731c6d6a8f9467308308", "cafebabefacedbaddecaf888",
     "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657"
     "ba637b39", "feedfacedeadbeeffeedfacedeadbeefabaddad2",
     "42831ec2217774244b7221b784d0d49ce3aa212f2c02a4e035c17e2329aca12e21d514b25466931c7d8f6a5aac84aa051ba30b396a0aac97"
     "3d58e091", "5bc94fbc3221a5db94fae95ae7121a47"),
    ("00" * 32, "00" * 12, "", "", "", "530f8afbc74536b9a963b4f1c4cb738b"),
    ("00" * 32, "00" * 12, "00" * 16, "", "cea7403d4d606b6e074ec5d3baf39d18", "d0d1c8a799996bf0265b98b5d48ab919"),
]

CHACHA_KEY = bytes(range(0x80, 0xa0))
CHACHA_NONCE = bytes.fromhex("070000004041424344454647")
CHACHA_AAD = bytes.fromhex("50515253c0c1c2c3c4c5c6c7")
CHACHA_PLAIN = (b"Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, "
                b"sunscreen would be it.")
CHACHA_TAG = "1ae10b594f09e26a7e902ecbd0600691"


def ciphers():
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM, ChaCha20Poly1305
    return [AESGCM, ChaCha20Poly1305]


def case_aead(env):
    need()
    cls = ciphers()
    lines, wants = [], []
    for k, n, p, a, c, t in GCM:
        k, n, p, a = (bytes.fromhex(v) for v in (k, n, p, a))
        check(cls[0](k).encrypt(n, p, a).hex(), c + t, "table of GCM")
        lines.append("as 0 %s %s %s %s" % (x(k), x(n), x(p), x(a)))
        wants.append("x" + c + t)
        lines.append("ao 0 %s %s %s %s" % (x(k), x(n), "x" + c + t, x(a)))
        wants.append(x(p))
    sealed = cls[1](CHACHA_KEY).encrypt(CHACHA_NONCE, CHACHA_PLAIN, CHACHA_AAD)
    check(sealed[-16:].hex(), CHACHA_TAG, "tag of RFC 8439")
    lines.append("as 1 %s %s %s %s" % (x(CHACHA_KEY), x(CHACHA_NONCE), x(CHACHA_PLAIN), x(CHACHA_AAD)))
    wants.append(x(sealed))

    # both directions, keys of all lengths, sizes around the blocks
    for alg, keylens in ((0, (16, 24, 32)), (1, (32,))):
        for kl in keylens:
            for n in list(range(0, 70)) + [127, 128, 129, 255, 256, 257, 4096, 65537]:
                key, nonce, plain, aad = rnd(kl), rnd(12), rnd(n), rnd(RNG.choice([0, 1, 13, 64]))
                s = cls[alg](key).encrypt(nonce, plain, aad or None)
                lines.append("as %d %s %s %s %s" % (alg, x(key), x(nonce), x(plain), x(aad)))
                wants.append(x(s))
                lines.append("ao %d %s %s %s %s" % (alg, x(key), x(nonce), x(s), x(aad)))
                wants.append(x(plain))
    big = rnd(1 << 20)
    key, nonce = rnd(32), rnd(12)
    for alg in (0, 1):
        s = cls[alg](key).encrypt(nonce, big, None)
        lines.append("ao %d %s %s %s x" % (alg, x(key), x(nonce), x(s)))
        wants.append(x(big))

    # every changed byte of nonce, ciphertext, tag and associated data
    for alg in (0, 1):
        key, nonce, plain, aad = rnd(32), rnd(12), rnd(30), rnd(13)
        s = cls[alg](key).encrypt(nonce, plain, aad)
        for i in range(12):
            for bit in (0, 7):
                lines.append("ao %d %s %s %s %s" % (alg, x(key), x(flip(nonce, i, bit)), x(s), x(aad)))
                wants.append(err("BadTag"))
        for i in range(len(s)):
            for bit in (0, 7):
                lines.append("ao %d %s %s %s %s" % (alg, x(key), x(nonce), x(flip(s, i, bit)), x(aad)))
                wants.append(err("BadTag"))
        for i in range(len(aad)):
            lines.append("ao %d %s %s %s %s" % (alg, x(key), x(nonce), x(s), x(flip(aad, i))))
            wants.append(err("BadTag"))
        for k in range(len(s)):
            lines.append("ao %d %s %s %s %s" % (alg, x(key), x(nonce), x(s[:k]), x(aad)))
            wants.append(err("BadTag"))
        for extra in (b"\x00", rnd(16)):
            lines.append("ao %d %s %s %s %s" % (alg, x(key), x(nonce), x(s + extra), x(aad)))
            wants.append(err("BadTag"))
        lines.append("ao %d %s %s %s x" % (alg, x(key), x(nonce), x(s)))
        wants.append(err("BadTag"))
        lines.append("ao %d %s %s %s %s" % (alg, x(flip(key, 31)), x(nonce), x(s), x(aad)))
        wants.append(err("BadTag"))
        # the format of Seal: nonce, ciphertext, tag
        full = nonce + s
        lines.append("aO %d %s %s %s" % (alg, x(key), x(full), x(aad)))
        wants.append(x(plain))
        for i in range(len(full)):
            lines.append("aO %d %s %s %s" % (alg, x(key), x(flip(full, i)), x(aad)))
            wants.append(err("BadTag"))
        for k in range(len(full)):
            lines.append("aO %d %s %s %s" % (alg, x(key), x(full[:k]), x(aad)))
            wants.append(err("BadTag"))

    # lengths of keys and nonces
    for alg, good in ((0, (16, 24, 32)), (1, (32,))):
        for kl in range(0, 41):
            key, nonce, plain = rnd(kl), rnd(12), rnd(5)
            if kl in good:
                s = cls[alg](key).encrypt(nonce, plain, None)
                wants += [x(s), x(plain)]
            else:
                s = rnd(21)
                wants += [err("BadKey"), err("BadKey")]
            lines.append("as %d %s %s %s x" % (alg, x(key), x(nonce), x(plain)))
            lines.append("ao %d %s %s %s x" % (alg, x(key), x(nonce), x(s)))
        for nl in range(0, 21):
            if nl == 12:
                continue
            key, nonce = rnd(32), rnd(nl)
            lines.append("as %d %s %s x0102 x" % (alg, x(key), x(nonce)))
            lines.append("ao %d %s %s %s x" % (alg, x(key), x(nonce), x(rnd(18))))
            wants += [err("BadNonce"), err("BadNonce")]
        # a bad key comes before a missing tag
        for k in (0, 5, 27):
            lines.append("aO %d %s %s x" % (alg, x(rnd(7)), x(rnd(k))))
            wants.append(err("BadKey"))
    compare(env, lines, wants)

    # random nonces
    lines, keys = [], []
    for alg in (0, 1):
        for n in (0, 1, 100):
            key, plain, aad = rnd(32), rnd(n), rnd(RNG.choice([0, 9]))
            keys.append((alg, key, plain, aad))
            lines.append("aS %d %s %s %s" % (alg, x(key), x(plain), x(aad)))
            lines.append("aS %d %s %s %s" % (alg, x(key), x(plain), x(aad)))
    r = env.batch(lines)
    for i, (alg, key, plain, aad) in enumerate(keys):
        a, b = unx(r[2 * i]), unx(r[2 * i + 1])
        check(len(a), 12 + len(plain) + 16, "length of a sealed message")
        check(a[:12] != b[:12], True, "two nonces differ")
        for s in (a, b):
            check(cls[alg](key).decrypt(s[:12], s[12:], aad or None), plain, "decrypted by cryptography")


# key derivation

RFC6070 = [(b"password", b"salt", 1, 20, "0c60c80f961f0e71f3a9b524af6012062fe037a6"),
           (b"password", b"salt", 2, 20, "ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957"),
           (b"password", b"salt", 4096, 20, "4b007901b765489abead49d926f721d065a429c1"),
           (b"passwordPASSWORDpassword", b"saltSALTsaltSALTsaltSALTsaltSALTsalt", 4096, 25,
            "3d2eec4fe41c849b80c8d83662c0e44a8b291a964cf2f07038"),
           (b"pass\0word", b"sa\0lt", 4096, 16, "56fa6aa75548099dcc37d7f03425e0c3")]

RFC5869 = [("sha256", "0b" * 22, "000102030405060708090a0b0c", "f0f1f2f3f4f5f6f7f8f9", 42,
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
           ("sha256", "0b" * 22, "", "", 42,
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"),
           ("sha1", "0b" * 11, "000102030405060708090a0b0c", "f0f1f2f3f4f5f6f7f8f9", 42,
            "085a01ea1b10f36933068b56efa5ad81a4f14b822f5b091568a9cdd4f155fda2c22e422478d305f3f896"),
           ("sha1", "0c" * 22, "", "", 42,
            "2c91117204d745f3500d636a62f64f0ab3bae548aa53d423b0d1f27ebba6f5e5673a081d70cce7acfc48")]


def pbkdf2(h, password, salt, iterations, length):
    try:
        return hashlib.pbkdf2_hmac(h, password, salt, iterations, length)
    except ValueError:
        out, block = b"", 1
        while len(out) < length:
            u = hmac.new(password, salt + block.to_bytes(4, "big"), h).digest()
            t = bytearray(u)
            for _ in range(iterations - 1):
                u = hmac.new(password, u, h).digest()
                t = bytearray(a ^ b for a, b in zip(t, u))
            out += bytes(t)
            block += 1
        return out[:length]


def hkdf(h, ikm, salt, info, length):
    prk = hmac.new(salt or bytes(SIZE[h]), ikm, h).digest()
    out, t, i = b"", b"", 1
    while len(out) < length:
        t = hmac.new(prk, t + info + bytes([i]), h).digest()
        out += t
        i += 1
    return out[:length]


def case_kdf(env):
    lines, wants = [], []
    for p, s, c, n, want in RFC6070:
        check(hashlib.pbkdf2_hmac("sha1", p, s, c, n).hex(), want, "table of RFC 6070")
        lines.append("pb 1 %s %s %d %d" % (x(p), x(s), c, n))
        wants.append("x" + want)
    for h, ikm, salt, info, n, want in RFC5869:
        ikm, salt, info = bytes.fromhex(ikm), bytes.fromhex(salt), bytes.fromhex(info)
        check(hkdf(h, ikm, salt, info, n).hex(), want, "table of RFC 5869")
        lines.append("hk %d %s %s %s %d" % (DIGESTS.index(h), x(ikm), x(salt), x(info), n))
        wants.append("x" + want)
    for i, h in enumerate(DIGESTS):
        hl = SIZE[h]
        for c in (1, 2, 3, 100):
            for n in (1, hl - 1, hl, hl + 1, 2 * hl + 5):
                p, s = rnd(RNG.choice([0, 8, 200])), rnd(RNG.choice([0, 16]))
                lines.append("pb %d %s %s %d %d" % (i, x(p), x(s), c, n))
                wants.append(x(pbkdf2(h, p, s, c, n)))
        lines.append("pb %d x70 x73 1000 1024" % i)
        wants.append(x(pbkdf2(h, b"p", b"s", 1000, 1024)))
        lines.append("pb %d x70 x73 1 0" % i)
        wants.append("x")
        lines.append("pb %d x70 x73 1 1025" % i)
        wants.append(err("TooLarge"))
        lines.append("pb %d x70 x73 0 32" % i)
        wants.append(err("Unsupported"))
        lines.append("pb %d x70 x73 100000001 32" % i)
        wants.append(err("TooLarge"))
        for n in (0, 1, hl - 1, hl, hl + 1, 3 * hl, 255 * hl):
            ikm, salt, info = rnd(RNG.choice([0, 22, 100])), rnd(RNG.choice([0, 13, 200])), rnd(RNG.choice([0, 10]))
            lines.append("hk %d %s %s %s %d" % (i, x(ikm), x(salt), x(info), n))
            wants.append(x(hkdf(h, ikm, salt, info, n)))
        lines.append("hk %d x01 x x %d" % (i, 255 * hl + 1))
        wants.append(err("TooLarge"))
    compare(env, lines, wants)


# signatures

def pem_private(k):
    from cryptography.hazmat.primitives import serialization as S
    return k.private_bytes(S.Encoding.PEM, S.PrivateFormat.PKCS8, S.NoEncryption())


def pem_public(k):
    from cryptography.hazmat.primitives import serialization as S
    return k.public_bytes(S.Encoding.PEM, S.PublicFormat.SubjectPublicKeyInfo)


def der_public(k):
    from cryptography.hazmat.primitives import serialization as S
    return k.public_bytes(S.Encoding.DER, S.PublicFormat.SubjectPublicKeyInfo)


def armor(der, label):
    import base64
    b = base64.b64encode(der).decode()
    return ("-----BEGIN %s-----\n" % label + "".join(b[i:i + 64] + "\n" for i in range(0, len(b), 64)) +
            "-----END %s-----\n" % label).encode()


RFC8032 = [("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
            "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", "",
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe"
            "24655141438e7a100b"),
           ("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb",
            "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c", "72",
            "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302a"
            "eeb00d291612bb0c00"),
           ("c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7",
            "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025", "af82",
            "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed2"
            "8dc027beceea1ec40a")]
ED_ORDER = 2 ** 252 + 27742317777372353535851937790883648493


def case_ed25519(env):
    need()
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
    from cryptography.hazmat.primitives import serialization as S
    import nacl.signing
    import nacl.exceptions
    lines, wants = [], []
    for i, (secret, public, msg, sig) in enumerate(RFC8032):
        k = Ed25519PrivateKey.from_private_bytes(bytes.fromhex(secret))
        check(k.public_key().public_bytes(S.Encoding.Raw, S.PublicFormat.Raw).hex(), public, "table of RFC 8032")
        check(k.sign(bytes.fromhex(msg)).hex(), sig, "table of RFC 8032")
        n = "r%d" % i
        lines += ["kq %s %s" % (n, x(pem_private(k))), "ke %s x%s" % (n, public), "kx " + n,
                  "ks 0 %s x%s" % (n, msg), "kv 0 %s x%s x%s" % (n, msg, sig)]
        wants += ["ok 0", "ok 0", "x%s -" % public, "x" + sig, "yes"]
    for i in range(30):
        k = Ed25519PrivateKey.generate()
        raw = k.public_key().public_bytes(S.Encoding.Raw, S.PublicFormat.Raw)
        msg = rnd(RNG.choice([0, 1, 33, 1000]))
        sig = k.sign(msg)
        n = "k%d" % i
        lines += ["kq %s %s" % (n, x(pem_private(k))), "kp %s %s" % (n, x(pem_public(k.public_key()))),
                  "ks 0 %s %s" % (n, x(msg)), "kv 0 %s %s %s" % (n, x(msg), x(sig))]
        wants += ["ok 0", "ok 0", x(sig), "yes"]
        if i == 0:
            for j in range(64):
                lines.append("kv 0 %s %s %s" % (n, x(msg), x(flip(sig, j))))
                wants.append("no")
            for j in range(len(msg)):
                lines.append("kv 0 %s %s %s" % (n, x(flip(msg, j)), x(sig)))
                wants.append("no")
            for m in list(range(0, 64)) + [65, 70, 128]:
                lines.append("kv 0 %s %s %s" % (n, x(msg), x((sig + bytes(64))[:m])))
                wants.append("no")
            # S + L: the same S modulo L, which RFC 8032 refuses
            s = int.from_bytes(sig[32:], "little") + ED_ORDER
            lines.append("kv 0 %s %s %s" % (n, x(msg), x(sig[:32] + s.to_bytes(32, "little"))))
            wants.append("no")
            lines.append("kv 0 %s %s %s" % (n, x(msg + b"!"), x(sig)))
            wants.append("no")
    for m in list(range(0, 32)) + [33, 64]:
        lines.append("ke e %s" % x(rnd(m)))
        wants.append(err("BadKey"))
    # PyNaCl: the clear cases
    sk = nacl.signing.SigningKey.generate()
    msg = rnd(50)
    signed = sk.sign(msg)
    seed_key = Ed25519PrivateKey.from_private_bytes(bytes(sk))
    lines += ["ke n %s" % x(bytes(sk.verify_key)), "kv 0 n %s %s" % (x(msg), x(signed.signature)),
              "kq n %s" % x(pem_private(seed_key)), "ks 0 n %s" % x(msg)]
    wants += ["ok 0", "yes", "ok 0", x(signed.signature)]
    # a generated pair, checked by cryptography
    lines += ["kg g 0 0", "kP g", "kQ g", "ks 0 g x616263", "kx g"]
    wants += ["ok 0 0", None, None, None, None]
    r = env.batch(lines)
    for i, (got, want) in enumerate(zip(r, wants)):
        if want is not None and got != want:
            raise Failed("%s: got %r, want %r" % (lines[i][:200], got[:300], want[:300]))
    pub = S.load_pem_public_key(unx(r[-4]))
    priv = S.load_pem_private_key(unx(r[-3]), None)
    pub.verify(unx(r[-2]), b"abc")
    check(r[-1], x(pub.public_bytes(S.Encoding.Raw, S.PublicFormat.Raw)) + " -", "PublicParts")
    check(priv.public_key().public_bytes(S.Encoding.Raw, S.PublicFormat.Raw),
          pub.public_bytes(S.Encoding.Raw, S.PublicFormat.Raw), "private and public key")
    check(isinstance(pub, Ed25519PublicKey), True, "kind")


def unsigned(v):
    return v.to_bytes(max(1, (v.bit_length() + 7) // 8), "big")


def hash_of(alg):
    from cryptography.hazmat.primitives import hashes
    return {RS256: hashes.SHA256(), RS384: hashes.SHA384(), RS512: hashes.SHA512(), PS256: hashes.SHA256(),
            PS384: hashes.SHA384(), PS512: hashes.SHA512(), ES256: hashes.SHA256(), ES384: hashes.SHA384(),
            ES512: hashes.SHA512()}[alg]


def pss(alg, salt=None):
    from cryptography.hazmat.primitives.asymmetric import padding
    h = hash_of(alg)
    return padding.PSS(mgf=padding.MGF1(h), salt_length=h.digest_size if salt is None else salt)


def case_rsa(env):
    need()
    from cryptography.hazmat.primitives.asymmetric import rsa, padding
    from cryptography.hazmat.primitives import serialization as S
    from cryptography.exceptions import InvalidSignature
    keys = [rsa.generate_private_key(65537, bits) for bits in (2048, 2048, 3072, 4096)]
    keys.append(rsa.generate_private_key(3, 2048))
    lines, wants, later = [], [], []
    for i, k in enumerate(keys):
        n = "k%d" % i
        pn = k.public_key().public_numbers()
        lines += ["kq %s %s" % (n, x(pem_private(k))), "kp %s %s" % (n, x(pem_public(k.public_key()))),
                  "kx " + n, "kP " + n, "kr r%d %s %s" % (i, x(unsigned(pn.n)), x(unsigned(pn.e))),
                  "kr z%d %s %s" % (i, x(b"\0\0" + unsigned(pn.n)), x(b"\0" + unsigned(pn.e)))]
        wants += ["ok 1", "ok 1", "%s %s" % (x(unsigned(pn.n)), x(unsigned(pn.e))), x(pem_public(k.public_key())),
                  "ok 1", "ok 1"]
        for alg in (RS256, RS384, RS512):
            msg = rnd(RNG.choice([0, 1, 100]))
            sig = k.sign(msg, padding.PKCS1v15(), hash_of(alg))
            lines += ["ks %d %s %s" % (alg, n, x(msg)), "kv %d r%d %s %s" % (alg, i, x(msg), x(sig)),
                      "kv %d %s %s %s" % (alg, n, x(msg + b"."), x(sig))]
            wants += [x(sig), "yes", "no"]
            # another digest
            other = RS384 if alg == RS256 else RS256
            lines.append("kv %d %s %s %s" % (other, n, x(msg), x(sig)))
            wants.append("no")
        for alg in (PS256, PS384, PS512):
            msg = rnd(77)
            sig = k.sign(msg, pss(alg), hash_of(alg))
            lines += ["kv %d %s %s %s" % (alg, n, x(msg), x(sig)), "ks %d %s %s" % (alg, n, x(msg))]
            wants += ["yes", None]
            later.append((len(lines) - 1, k, alg, msg))
            # another length of the salt
            sig = k.sign(msg, pss(alg, padding.PSS.MAX_LENGTH), hash_of(alg))
            lines.append("kv %d %s %s %s" % (alg, n, x(msg), x(sig)))
            wants.append("no")
            # the same signature as PKCS #1 v1.5
            lines.append("kv %d %s %s %s" % (alg - 3, n, x(msg), x(sig)))
            wants.append("no")
    k = keys[0]
    msg = rnd(40)
    sig = k.sign(msg, padding.PKCS1v15(), hash_of(RS256))
    for j in range(len(sig)):
        lines.append("kv 1 k0 %s %s" % (x(msg), x(flip(sig, j))))
        wants.append("no")
    for s in (sig[:-1], sig + b"\0", b"\0" + sig, b"", sig[1:]):
        lines.append("kv 1 k0 %s %s" % (x(msg), x(s)))
        wants.append("no")
    sig = k.sign(msg, pss(PS256), hash_of(PS256))
    for j in range(0, len(sig), 3):
        lines.append("kv 4 k0 %s %s" % (x(msg), x(flip(sig, j))))
        wants.append("no")

    # the rules for keys
    small = rsa.generate_private_key(65537, 1024)
    pn = keys[0].public_key().public_numbers()
    odd = lambda bits: (RNG.getrandbits(bits) | (1 << (bits - 1)) | 1)
    lines += ["kq s %s" % x(pem_private(small)), "kp s %s" % x(pem_public(small.public_key())),
              "kr s %s x010001" % x(unsigned(small.public_key().public_numbers().n)),
              "kr s %s %s" % (x(unsigned(pn.n)), x(unsigned(65538))), "kr s %s x01" % x(unsigned(pn.n)),
              "kr s %s x" % x(unsigned(pn.n)), "kr s %s x00" % x(unsigned(pn.n)),
              "kr s %s %s" % (x(unsigned(pn.n)), x(unsigned(pn.n))), "kr s %s %s" % (x(unsigned(pn.n)), x(unsigned(pn.n + 2))),
              "kr s x x010001", "kr s x00 x010001",
              "kr s %s x010001" % x(unsigned(odd(2047))), "kr s %s x010001" % x(unsigned(odd(2048))),
              "kr s %s x010001" % x(unsigned(odd(16384))), "kr s %s x010001" % x(unsigned(odd(16385))),
              "kg g 1 2048", "kP g", "kQ g", "ks 1 g x616263", "ks 6 g x616263",
              "kg h 1 2047", "kg h 1 8193", "kg h 1 1024"]
    wants += [err("BadKey")] * 9 + [err("BadKey")] * 2 + [err("BadKey"), "ok 1", "ok 1", err("BadKey"),
                                                          "ok 1 1", None, None, None, None] + [err("BadKey")] * 3
    r = env.batch(lines)
    for i, (got, want) in enumerate(zip(r, wants)):
        if want is not None and got != want:
            raise Failed("%s: got %r, want %r" % (lines[i][:200], got[:300], want[:300]))
    for i, k, alg, msg in later:
        try:
            k.public_key().verify(unx(r[i]), msg, pss(alg), hash_of(alg))
        except InvalidSignature:
            raise Failed("PSS signature of m3j refused by cryptography")
    j = lines.index("kP g")
    pub = S.load_pem_public_key(unx(r[j]))
    priv = S.load_pem_private_key(unx(r[j + 1]), None)
    check(priv.public_key().public_numbers(), pub.public_numbers(), "generated pair")
    check(pub.key_size, 2048, "size of a generated key")
    pub.verify(unx(r[j + 2]), b"abc", padding.PKCS1v15(), hash_of(RS256))
    pub.verify(unx(r[j + 3]), b"abc", pss(PS512), hash_of(PS512))


CURVES = [("SECP256R1", 32, ES256), ("SECP384R1", 48, ES384), ("SECP521R1", 66, ES512)]
PRIMES = [2 ** 256 - 2 ** 224 + 2 ** 192 + 2 ** 96 - 1, 2 ** 384 - 2 ** 128 - 2 ** 96 + 2 ** 32 - 1, 2 ** 521 - 1]
ORDERS = [0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551,
          0xffffffffffffffffffffffffffffffffffffffffffffffffc7634d81f4372ddf581a0db248b0a77aecec196accc52973,
          int("01ff" + "ffffffff" * 7 + "fffffffa" + "51868783bf2f966b7fcc0148f709a5d03bb5c9b8899c47aebb6fb71e91386409", 16)]


def case_ecdsa(env):
    need()
    from cryptography.hazmat.primitives.asymmetric import ec, utils
    from cryptography.hazmat.primitives import serialization as S
    lines, wants, later = [], [], []
    for c, (name, size, alg) in enumerate(CURVES):
        curve = getattr(ec, name)()
        p = PRIMES[c]
        for i in range(6):
            k = ec.generate_private_key(curve)
            pn = k.public_key().public_numbers()
            check(pn.curve.key_size <= size * 8, True, "size")
            n = "k%d_%d" % (c, i)
            xb, yb = pn.x.to_bytes(size, "big"), pn.y.to_bytes(size, "big")
            lines += ["kq %s %s" % (n, x(pem_private(k))), "kp %s %s" % (n, x(pem_public(k.public_key()))),
                      "kc c%s %d %s %s" % (n, c, x(xb), x(yb)), "kx " + n, "kP " + n]
            wants += ["ok %d" % (2 + c)] * 3 + ["%s %s" % (x(xb), x(yb)), x(pem_public(k.public_key()))]
            msg = rnd(RNG.choice([0, 1, 100]))
            r_, s_ = utils.decode_dss_signature(k.sign(msg, ec.ECDSA(hash_of(alg))))
            sig = r_.to_bytes(size, "big") + s_.to_bytes(size, "big")
            lines += ["kv %d c%s %s %s" % (alg, n, x(msg), x(sig)), "ks %d %s %s" % (alg, n, x(msg))]
            wants += ["yes", None]
            later.append((len(lines) - 1, k, alg, msg, size))
            if i == 0:
                for j in range(len(sig)):
                    lines.append("kv %d %s %s %s" % (alg, n, x(msg), x(flip(sig, j))))
                    wants.append("no")
                q = ORDERS[c]
                for rr, ss in ((0, s_), (r_, 0), (r_ + q, s_), (r_, s_ + q), (q, s_), (r_, q - s_)):
                    if rr >= 1 << (8 * size) or ss >= 1 << (8 * size):
                        continue
                    lines.append("kv %d %s %s %s" % (alg, n, x(msg), x(rr.to_bytes(size, "big") + ss.to_bytes(size, "big"))))
                    # (r, n - s) is the other valid signature of ECDSA
                    wants.append("yes" if (rr, ss) == (r_, q - s_) else "no")
                der = k.sign(msg, ec.ECDSA(hash_of(alg)))
                for s in (sig[:-1], sig + b"\0", b"\0" + sig, b"", der):
                    lines.append("kv %d %s %s %s" % (alg, n, x(msg), x(s)))
                    wants.append("no")
                # points off the curve and coordinates of other lengths
                for xx, yy in ((xb, flip(yb, size - 1, 0)), (flip(xb, 0, 0), yb), (bytes(size), bytes(size)),
                               (p.to_bytes(size, "big"), yb), (xb[1:], yb), (xb, b"\0" + yb), (b"\0" + xb, b"\0" + yb)):
                    lines.append("kc bad %d %s %s" % (c, x(xx), x(yy)))
                    wants.append(err("BadKey"))
                # a coordinate that begins with a zero byte, given without it: the right
                # point, but not the length that RFC 7518 wants
                for _ in range(5000):
                    z = ec.generate_private_key(curve).public_key().public_numbers()
                    zx, zy = z.x.to_bytes(size, "big"), z.y.to_bytes(size, "big")
                    if zx[0] == 0 or zy[0] == 0:
                        break
                else:
                    raise Failed("no coordinate with a leading zero byte")
                lines += ["kc short %d %s %s" % (c, x(zx), x(zy)),
                          "kc short %d %s %s" % (c, x(zx[1:] if zx[0] == 0 else zx), x(zy[1:] if zy[0] == 0 else zy))]
                wants += ["ok %d" % (2 + c), err("BadKey")]
                if c == 2:
                    # the coordinates plus the prime: the same point modulo p, but not less than p
                    lines.append("kc bad 2 %s %s" % (x((pn.x + p).to_bytes(size, "big")), x(yb)))
                    lines.append("kc bad 2 %s %s" % (x(xb), x((pn.y + p).to_bytes(size, "big"))))
                    wants += [err("BadKey"), err("BadKey")]
                # a PEM text with a point off the curve
                der_ = der_public(k.public_key())
                lines.append("kp bad %s" % x(armor(flip(der_, len(der_) - 1, 0), "PUBLIC KEY")))
                wants.append(err("BadKey"))
                # another curve
                other = (c + 1) % 3
                lines.append("kc bad %d %s %s" % (other, x(xb), x(yb)))
                wants.append(err("BadKey"))
        lines.append("kg g%d %d 0" % (c, 2 + c))
        wants.append("ok %d %d" % (2 + c, 2 + c))
        lines += ["kP g%d" % c, "kQ g%d" % c, "ks %d g%d x616263" % (alg, c)]
        wants += [None, None, None]
    r = env.batch(lines)
    for i, (got, want) in enumerate(zip(r, wants)):
        if want is not None and got != want:
            raise Failed("%s: got %r, want %r" % (lines[i][:200], got[:300], want[:300]))
    for i, k, alg, msg, size in later:
        sig = unx(r[i])
        check(len(sig), 2 * size, "length of a signature")
        der = utils.encode_dss_signature(int.from_bytes(sig[:size], "big"), int.from_bytes(sig[size:], "big"))
        k.public_key().verify(der, msg, ec.ECDSA(hash_of(alg)))
    for c, (name, size, alg) in enumerate(CURVES):
        j = lines.index("kP g%d" % c)
        pub = S.load_pem_public_key(unx(r[j]))
        priv = S.load_pem_private_key(unx(r[j + 1]), None)
        check(priv.public_key().public_numbers(), pub.public_numbers(), "generated pair")
        check(pub.curve.name, getattr(ec, name).name, "curve")
        sig = unx(r[j + 2])
        der = utils.encode_dss_signature(int.from_bytes(sig[:size], "big"), int.from_bytes(sig[size:], "big"))
        pub.verify(der, b"abc", ec.ECDSA(hash_of(alg)))


def case_mismatch(env):
    need()
    lines, wants = [], []
    for kind in range(5):
        lines.append("kg k%d %d 2048" % (kind, kind))
        wants.append("ok %d %d" % (kind, kind))
    for kind in range(5):
        for alg in range(10):
            ok = KIND_OF[alg] == kind
            lines.append("ks %d k%d x6d" % (alg, kind))
            wants.append(is_bytes() if ok else err("BadKey"))
            lines.append("kv %d k%d x6d x" % (alg, kind))
            wants.append("no" if ok else err("BadKey"))
    compare(env, lines, wants)


def openssl(*a, data=None):
    """The output of openssl, with line feeds only."""
    r = subprocess.run(["openssl", *a], input=data, capture_output=True)
    if r.returncode:
        raise Failed("openssl %s: %s" % (" ".join(a), r.stderr.decode("latin-1")[:500]))
    return r.stdout.replace(b"\r\n", b"\n")


def case_pem(env):
    need()
    from cryptography.hazmat.primitives.asymmetric import ec, rsa, ed25519, ed448, x25519, x448, dsa
    from cryptography.hazmat.primitives import serialization as S
    if not shutil.which("openssl"):
        raise Failed("openssl is needed")
    lines, wants = [], []
    for alg, kind in (("ED25519", 0), ("RSA", 1), ("EC", 2)):
        opts = {"RSA": ["-pkeyopt", "rsa_keygen_bits:2048"], "EC": ["-pkeyopt", "ec_paramgen_curve:P-256"]}.get(alg, [])
        priv = openssl("genpkey", "-algorithm", alg, *opts)
        pub = openssl("pkey", "-pubout", data=priv)
        lines += ["kq o%d %s" % (kind, x(priv)), "kp o%d %s" % (kind, x(pub)), "kP o%d" % kind]
        wants += ["ok %d" % kind, "ok %d" % kind, x(pub)]
        # the ways in which PEM texts differ
        for variant in (pub.replace(b"\n", b"\r\n"), b"a key:\n" + pub + b"that was it\n", pub.rstrip(b"\n"),
                        b"\n\n" + pub.replace(b"\n", b"\n  ") + b"\n\n",
                        armor(S.load_pem_public_key(pub).public_bytes(S.Encoding.DER,
                                                                      S.PublicFormat.SubjectPublicKeyInfo), "PUBLIC KEY")
                        .replace(b"\n", b"").replace(b"-----BEGIN PUBLIC KEY-----", b"-----BEGIN PUBLIC KEY-----\n")):
            lines.append("kp v %s" % x(variant))
            wants.append("ok %d" % kind)
        der = S.load_pem_public_key(pub).public_bytes(S.Encoding.DER, S.PublicFormat.SubjectPublicKeyInfo)
        for bad in (pub.replace(b"-----BEGIN", b"----BEGIN"), pub.replace(b"-----END PUBLIC KEY-----", b""),
                    pub.replace(b"-----END PUBLIC KEY", b"-----END PRIVATE KEY"),
                    pub.replace(b"\n", b"\n*", 2), armor(der + b"\0", "PUBLIC KEY"), armor(der[:-1], "PUBLIC KEY"),
                    armor(der[:10], "PUBLIC KEY"), armor(b"\x30\x00", "PUBLIC KEY"), armor(b"", "PUBLIC KEY"),
                    armor(b"\x30\x80" + der[2:], "PUBLIC KEY"), b"", b"-----BEGIN PUBLIC KEY-----"):
            lines.append("kp bad %s" % x(bad))
            wants.append(err("BadKey"))
        lines += ["kp bad %s" % x(priv), "kq bad %s" % x(pub)]
        wants += [err("Unsupported")] * 2
        pder = S.load_pem_private_key(priv, None).private_bytes(S.Encoding.DER, S.PrivateFormat.PKCS8,
                                                                 S.NoEncryption())
        for bad in (armor(pder + b"\0", "PRIVATE KEY"), armor(pder + pder, "PRIVATE KEY"),
                    armor(pder[:-1], "PRIVATE KEY")):
            lines.append("kq bad %s" % x(bad))
            wants.append(err("BadKey"))
    # Base64 with bits that should be zero: the SPKI of Ed25519 has 44 bytes
    pub = openssl("pkey", "-pubout", data=openssl("genpkey", "-algorithm", "ED25519"))
    body = pub.split(b"\n")[1]
    check(body.endswith(b"="), True, "padding")
    last = body[-2:-1]
    alphabet = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    j = alphabet.index(last) | 1
    changed = alphabet[j:j + 1]
    if changed != last:
        lines.append("kp bad %s" % x(pub.replace(body, body[:-2] + changed + b"=")))
        wants.append(err("BadKey"))

    # older forms and other algorithms
    r2 = rsa.generate_private_key(65537, 2048)
    e2 = ec.generate_private_key(ec.SECP256R1())
    trad = S.PrivateFormat.TraditionalOpenSSL
    for t in (r2.private_bytes(S.Encoding.PEM, trad, S.NoEncryption()),
              e2.private_bytes(S.Encoding.PEM, trad, S.NoEncryption()),
              r2.private_bytes(S.Encoding.PEM, S.PrivateFormat.PKCS8, S.BestAvailableEncryption(b"pw")),
              pem_private(ed448.Ed448PrivateKey.generate()), pem_private(x25519.X25519PrivateKey.generate()),
              pem_private(x448.X448PrivateKey.generate()), pem_private(dsa.generate_private_key(2048)),
              openssl("genpkey", "-algorithm", "RSA-PSS", "-pkeyopt", "rsa_keygen_bits:2048"),
              pem_private(ec.generate_private_key(ec.SECP256K1())), pem_private(ec.generate_private_key(ec.SECP224R1()))):
        lines.append("kq bad %s" % x(t))
        wants.append(err("Unsupported"))
    for t in (r2.public_key().public_bytes(S.Encoding.PEM, S.PublicFormat.PKCS1),
              pem_public(ed448.Ed448PrivateKey.generate().public_key()),
              pem_public(x25519.X25519PrivateKey.generate().public_key()),
              pem_public(dsa.generate_private_key(2048).public_key()),
              pem_public(ec.generate_private_key(ec.SECP256K1()).public_key()),
              pem_public(ec.generate_private_key(ec.SECP521R1()).public_key()).replace(b"PUBLIC", b"EC PUBLIC")):
        lines.append("kp bad %s" % x(t))
        wants.append(err("Unsupported"))

    # private scalars out of range
    for c, (name, size, alg) in enumerate(CURVES):
        k = ec.generate_private_key(getattr(ec, name)())
        der = k.private_bytes(S.Encoding.DER, S.PrivateFormat.PKCS8, S.NoEncryption())
        d = k.private_numbers().private_value.to_bytes(size, "big")
        check(der.count(d), 1, "the scalar in the DER")
        for v in (0, ORDERS[c], ORDERS[c] + 1):
            if v < 1 << (8 * size):
                lines.append("kq bad %s" % x(armor(der.replace(d, v.to_bytes(size, "big")), "PRIVATE KEY")))
                wants.append(err("BadKey"))
        v = ORDERS[c] - 1
        lines.append("kq top %s" % x(armor(der.replace(d, v.to_bytes(size, "big")), "PRIVATE KEY")))
        wants.append("ok %d" % (2 + c))

    # round trips: what m3j writes, cryptography reads
    keys = [ed25519.Ed25519PrivateKey.generate(), rsa.generate_private_key(65537, 2048),
            ec.generate_private_key(ec.SECP256R1()), ec.generate_private_key(ec.SECP384R1()),
            ec.generate_private_key(ec.SECP521R1())]
    for i, k in enumerate(keys):
        lines += ["kq t%d %s" % (i, x(pem_private(k))), "kQ t%d" % i]
        wants += ["ok %d" % i, None]
    r = env.batch(lines)
    for i, (got, want) in enumerate(zip(r, wants)):
        if want is not None and got != want:
            raise Failed("%s: got %r, want %r" % (lines[i][:200], got[:300], want[:300]))
    for i, k in enumerate(keys):
        t = unx(r[lines.index("kQ t%d" % i)])
        check(t.startswith(b"-----BEGIN PRIVATE KEY-----\n") and t.endswith(b"-----END PRIVATE KEY-----\n"), True,
              "armor")
        check(max(len(l) for l in t.split(b"\n")) <= 64, True, "lines of 64")
        back = S.load_pem_private_key(t, None)
        check(der_public(back.public_key()), der_public(k.public_key()), "private key read back")


RFC7748 = [("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4",
            "e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c",
            "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552"),
           ("4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d",
            "e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493",
            "95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957")]
ALICE = ("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a",
         "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
BOB = ("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb",
       "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
SHARED = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"
P25519 = 2 ** 255 - 19
SMALL_ORDER = [0, 1, 325606250916557431795983626356110631294008115727848805560023387167927233504,
               39382357235489614581723060781553021112529911719440698176882885853963445705823, P25519 - 1, P25519,
               P25519 + 1]


def x25519(k, u):
    from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey, X25519PublicKey
    try:
        return X25519PrivateKey.from_private_bytes(k).exchange(X25519PublicKey.from_public_bytes(u))
    except ValueError:
        return None


def case_x25519(env):
    need()
    from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey
    from cryptography.hazmat.primitives import serialization as S
    lines, wants = [], []
    for k, u, want in RFC7748:
        check(x25519(bytes.fromhex(k), bytes.fromhex(u)).hex(), want, "table of RFC 7748")
        lines.append("xa x%s x%s" % (k, u))
        wants.append("x" + want)
    lines += ["xp x" + ALICE[0], "xp x" + BOB[0], "xa x%s x%s" % (ALICE[0], BOB[1]), "xa x%s x%s" % (BOB[0], ALICE[1])]
    wants += ["x" + ALICE[1], "x" + BOB[1], "x" + SHARED, "x" + SHARED]
    nine = "09" + "00" * 31
    lines += ["xi 1 x" + nine, "xi 1000 x" + nine]
    wants += ["x422c8e7a6227d7bca1350b3e2bb7279f7897b87bb6854b783c60e80311ae3079",
              "x684cf59ba83309552800ef566f2f4d3c1c3887c49360e3875f2eb94d99532c51"]
    for i in range(200):
        k, u = rnd(32), rnd(32)
        lines.append("xa %s %s" % (x(k), x(u)))
        got = x25519(k, u)
        wants.append(err("BadKey") if got is None else x(got))
        lines.append("xp " + x(k))
        wants.append(x(X25519PrivateKey.from_private_bytes(k).public_key().public_bytes(S.Encoding.Raw,
                                                                                         S.PublicFormat.Raw)))
    # points of small order, with and without the highest bit, and u from p to 2^255 - 1
    for v in SMALL_ORDER + list(range(P25519, 2 ** 255)):
        for top in (0, 1 << 255):
            u = ((v + top) % (1 << 256)).to_bytes(32, "little")
            k = rnd(32)
            got = x25519(k, u)
            lines.append("xa %s %s" % (x(k), x(u)))
            wants.append(err("BadKey") if got is None else x(got))
    for m in (0, 31, 33):
        lines += ["xa %s %s" % (x(rnd(m)), x(rnd(32))), "xa %s %s" % (x(rnd(32)), x(rnd(m))), "xp " + x(rnd(m))]
        wants += [err("BadKey")] * 3
    lines += ["xn", "xn"]
    wants += [is_bytes(32), is_bytes(32)]
    compare(env, lines, wants)


def random_bits(texts, mask, what):
    """Checks that each bit of mask takes both values in the identifiers."""
    ones, zeros = 0, 0
    for t in texts:
        v = uuid.UUID(t).int
        ones |= v
        zeros |= ~v
    if ones & mask != mask or zeros & mask != mask:
        raise Failed("%s: random bits that never change: %032x" % (what, mask & ~(ones & zeros)))


def case_uuid(env):
    t0 = time.time()
    r = env.batch(["u4 100000", "u7 2000"])
    t1 = time.time()
    v4 = r[0].split(",")
    check(len(set(v4)), 100000, "different identifiers of version 4")
    for t in v4[:2000] + v4[-100:]:
        u = uuid.UUID(t)
        check((str(u), u.version, u.variant), (t, 4, uuid.RFC_4122), "version 4")
    random_bits(v4, 0xffffffff_ffff_0fff_3fff_ffffffffffff, "version 4")
    v7 = r[1].split(",")
    check(len(set(v7)), 2000, "different identifiers of version 7")
    random_bits(v7, 0x00000000_0000_0fff_3fff_ffffffffffff, "version 7")
    prev = 0
    for t in v7:
        u = uuid.UUID(t)
        check((str(u), u.version, u.variant), (t, 7, uuid.RFC_4122), "version 7")
        ms = int.from_bytes(u.bytes[:6], "big")
        if not (int(t0 * 1000) - 1 <= ms <= int(t1 * 1000) + 1):
            raise Failed("time of %s: %d not in %d .. %d" % (t, ms, t0 * 1000, t1 * 1000))
        if ms < prev:
            raise Failed("version 7 out of order: %s" % t)
        prev = ms
    s = sorted(v7, key=lambda t: t[:13])
    check([t[:13] for t in s], [t[:13] for t in v7], "order of the text")
    lines, wants = [], []
    for _ in range(300):
        b = rnd(16)
        u = uuid.UUID(bytes=b)
        lines += ["uf " + x(b), "up " + x(str(u)), "up " + x(str(u).upper()), "uv " + x(b)]
        wants += [str(u), x(b), x(b), str(b[6] >> 4)]
        mixed = "".join(ch.upper() if RNG.random() < 0.5 else ch for ch in str(u))
        lines.append("up " + x(mixed))
        wants.append(x(b))
    good = str(uuid.UUID(bytes=rnd(16)))
    for bad in (good[:-1], good + "0", good.replace("-", ""), "{" + good + "}", "urn:uuid:" + good,
                good[:8] + "0" + good[9:], good[:9] + "-" + good[10:], good[:35] + "g", "g" + good[1:],
                good[:13] + " " + good[14:], " " + good[1:], "", good[:8] + good[9:] + "0", good[:14] + "+" + good[15:]):
        lines.append("up " + x(bad))
        wants.append("no")
    lines.append("up " + w(good[:35] + "١"))
    wants.append("no")
    compare(env, lines, wants)


def case_notbytes(env):
    W = w("中")
    k32, n12 = x(bytes(32)), x(bytes(12))
    lines = ["dg 2 " + W, "hm 2 %s x" % W, "hm 2 x %s" % W, "hc 2 " + W,
             "as 0 %s %s x x" % (W, n12), "as 0 %s %s x x" % (k32, W), "as 0 %s %s %s x" % (k32, n12, W),
             "as 0 %s %s x %s" % (k32, n12, W), "ao 1 %s %s x x" % (W, n12), "ao 1 %s %s %s x" % (k32, n12, W),
             "ao 1 %s %s x %s" % (k32, n12, W), "aS 0 %s %s x" % (k32, W), "aO 0 %s %s x" % (k32, W),
             "aO 0 %s x %s" % (k32, W), "pb 2 %s x 1 1" % W, "pb 2 x %s 1 1" % W, "hk 2 %s x x 1" % W,
             "hk 2 x %s x 1" % W, "hk 2 x x %s 1" % W, "ke e " + W, "kr r %s x03" % W, "kr r x03 %s" % W,
             "kc c 0 %s %s" % (W, k32), "kc c 0 %s %s" % (k32, W), "kp p " + W, "kq q " + W,
             "xa %s %s" % (W, k32), "xa %s %s" % (k32, W), "xp " + W]
    wants = [err("NotBytes")] * len(lines)
    lines += ["kg g 0 0", "ks 0 g " + W, "kv 0 g %s x" % W, "kv 0 g x %s" % W]
    wants += ["ok 0 0", err("NotBytes"), err("NotBytes"), "no"]
    compare(env, lines, wants)


CASES = {"random": case_random, "digest": case_digest, "hmac": case_hmac, "threads": case_threads,
         "aead": case_aead, "kdf": case_kdf, "ed25519": case_ed25519, "rsa": case_rsa, "ecdsa": case_ecdsa,
         "mismatch": case_mismatch, "pem": case_pem, "x25519": case_x25519, "uuid": case_uuid,
         "notbytes": case_notbytes}


def speed(env):
    mb = 1 << 20
    lines = ["sd 2 %d 64" % mb, "sd 5 %d 64" % mb, "sa 0 %d 64" % mb, "sa 1 %d 64" % mb, "ss 0 1 2000",
             "ss 1 1 2000"]
    r = env.batch(lines)
    for what, ms, n in (("SHA-256", r[0], 64), ("SHA3-256", r[1], 64), ("AES-256-GCM", r[2], 64),
                        ("ChaCha20-Poly1305", r[3], 64)):
        print("speed %-18s %7.0f MB/s" % (what, n / max(int(ms), 1) * 1000))
    for what, ms in (("Ed25519 sign", r[4]), ("ES256 sign", r[5])):
        print("speed %-18s %7.0f /s" % (what, 2000 / max(int(ms), 1) * 1000))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=str(Path(os.environ["JDK25"]) / "bin" / "java") if os.environ.get("JDK25")
                    else shutil.which("java") or "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=float, default=600.0)
    ap.add_argument("--speed", action="store_true")
    args = ap.parse_args()
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="crypto_test"))
    work.mkdir(parents=True, exist_ok=True)
    env = Env(args, work)
    failed = []
    try:
        env.compile()
        for name in args.cases or CASES:
            t0 = time.time()
            try:
                CASES[name](env)
                print("ok    %-9s %.1f s" % (name, time.time() - t0))
            except Exception as e:
                failed.append(name)
                print("FAIL  %-9s %s" % (name, str(e)[:3000].encode("ascii", "backslashreplace").decode("ascii")))
        if args.speed:
            speed(env)
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print("%d failed" % len(failed) if failed else "all passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
