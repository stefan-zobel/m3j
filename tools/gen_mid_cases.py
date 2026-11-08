#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Generate the input of the m3middle test (tests/middle).

Usage: python3 tools/gen_mid_cases.py

Writes tests/middle/cases.txt (cases that both sides must compute alike)
and tests/middle/faults.txt (cases on which CM3 stops with a checked
runtime error, which the port must reproduce; each is run on its own).
The cases are pseudo-random with a fixed seed, so the files are
reproducible. The line format is described in tools/middump/src/Main.m3.
"""

import random
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "tests" / "middle"

SEED = 19930520


def hexs(data):
    return data.hex() if data else "-"


def htext(s):
    return hexs(s.encode("latin-1"))


# ------------------------------------------------------------ integers ---

def I(v):
    """A TInt.Int: 9 bytes two's complement, byte[0] first."""
    return (v % (1 << 72)).to_bytes(9, "little").hex()


EDGES = []
for bits in (8, 16, 32, 64, 72):
    for base in (1 << (bits - 1), 1 << bits):
        for d in (-1, 0, 1):
            EDGES += [base + d, -base + d]
EDGES += [0, 1, -1, 2, -2, 9, 10, 11, 99, 100, 101, 255, 256, 999, 1000, 1001]
EDGES = sorted(set(e for e in EDGES if -(1 << 71) <= e < (1 << 71)))


def rand_int(rng):
    k = rng.randrange(6)
    if k == 0:
        return rng.choice(EDGES)
    if k == 1:
        return rng.randrange(-1000, 1000)
    if k == 2:
        return rng.randrange(-(1 << 31), 1 << 31)
    if k == 3:
        return rng.randrange(-(1 << 63), 1 << 63)
    if k == 4:
        return rng.randrange(-(1 << 71), 1 << 71)
    b = rng.randrange(1, 72)
    return rng.choice((1, -1)) * rng.randrange(1 << b)


def rand_i64(rng):
    v = rand_int(rng)
    return (v + (1 << 63)) % (1 << 64) - (1 << 63)


def rand_digits(rng, n):
    return "".join(rng.choice("0123456789") for _ in range(n))


def int_cases(rng, out):
    for e in EDGES:
        if -(1 << 63) <= e < (1 << 63):
            out.append(f"ifi {e}")
        out.append(f"iti {I(e)}")
        out.append(f"itxt {I(e)}")
        out.append(f"bintt {I(e)}")
        if 0 <= e < (1 << 64):
            out.append(f"iub {I(e)}")
    for _ in range(300):
        out.append(f"ifi {rand_i64(rng)}")
        out.append(f"iti {I(rand_int(rng))}")
    for n in list(range(0, 26)) + [30, 40]:
        for _ in range(4):
            out.append(f"inew {htext(rand_digits(rng, n))}")
    for s in ["a", "5a", ":", "99", "255", "0000000000001", "a5a"]:
        out.append(f"inew {htext(s)}")
    for op in ("iadd", "isub", "imul", "idiv", "imod", "icmp"):
        for _ in range(250):
            out.append(f"{op} {I(rand_int(rng))} {I(rand_int(rng))}")
        for a in (EDGES[::3]):
            for b in (0, 1, -1, 2, -2, 7, -(1 << 71), (1 << 71) - 1):
                out.append(f"{op} {I(a)} {I(b)}")
    for op in ("iinc", "idec", "ineg", "iabs", "itxt"):
        for _ in range(80):
            out.append(f"{op} {I(rand_int(rng))}")
        for e in EDGES:
            out.append(f"{op} {I(e)}")
    for _ in range(100):
        out.append(f"ichr {I(rand_int(rng))} {rng.randrange(0, 26)}")
    for _ in range(100):
        out.append(f"iub {I(abs(rand_int(rng)) % (1 << 64))}")
    for _ in range(200):
        out.append(f"iext {I(rand_int(rng))} {rng.randrange(1, 10)}")


def word_cases(rng, out):
    digits = "0123456789abcdefABCDEF"
    for _ in range(300):
        base = rng.randrange(2, 17)
        n = rng.choice((0, 1, 2, 5, 10, 16, 18, 19, 20, 25, 72, 80))
        valid = [c for c in digits if int(c, 16) < base]
        s = "".join(rng.choice(valid) for _ in range(n))
        if rng.random() < 0.1 and s:
            k = rng.randrange(len(s))
            s = s[:k] + rng.choice("gxz_ .") + s[k + 1:]
        out.append(f"wnew {base} {htext(s)}")
    for op in ("wadd", "wsub", "wmul", "wdiv", "wmod", "wcmp", "wand", "wor", "wxor"):
        for _ in range(200):
            out.append(f"{op} {I(rand_int(rng))} {I(rand_int(rng))}")
        for a in EDGES[::4]:
            for b in (0, 1, -1, 255, 256, (1 << 64) - 1):
                out.append(f"{op} {I(a)} {I(b)}")
    for _ in range(300):
        b = 0
        while b == 0:
            b = rand_int(rng)
        out.append(f"wdm {I(rand_int(rng))} {I(b)}")
    for _ in range(100):
        out.append(f"wnot {I(rand_int(rng))}")
    for _ in range(300):
        out.append(f"wsh {I(rand_int(rng))} {rng.randrange(-80, 81)}")
    for b in (-(1 << 63) + 1, (1 << 63) - 1, 72, -72, 71, -71):
        out.append(f"wsh {I(rand_int(rng))} {b}")
    for op in ("wls", "wrs"):
        for _ in range(150):
            out.append(f"{op} {I(rand_int(rng))} {rng.randrange(0, 72)}")
    for _ in range(300):
        out.append(f"wrot {I(rand_int(rng))} {rng.randrange(-200, 201)} {rng.randrange(1, 10)}")
    for _ in range(300):
        i = rng.randrange(0, 75)
        n = rng.randrange(0, 72)
        if i + n == 72 and n == 72:
            n = 71
        out.append(f"wext {I(rand_int(rng))} {i} {n}")
    for _ in range(300):
        i = rng.randrange(0, 75)
        n = rng.randrange(0, 75)
        out.append(f"wins {I(rand_int(rng))} {I(rand_int(rng))} {i} {n}")
    for _ in range(150):
        out.append(f"wtr {I(rand_int(rng))} {rng.randrange(0, 10)}")
    for n in range(0, 72):
        out.append(f"wnb {n}")


# ------------------------------------------------------- floating point ---

def dbits(x):
    return struct.unpack("<Q", struct.pack("<d", x))[0]


def fbits32(x):
    return struct.unpack("<I", struct.pack("<f", x))[0]


NICE = [0.0, -0.0, 1.0, -1.0, 0.5, 0.1, 0.2, 0.3, 1.5, 2.5, -2.5, 3.5, 0.125,
        0.375, 123.456, 1e22, 1e23, 5e-324, 2.2250738585072014e-308,
        1.7976931348623157e308, 3.4028234663852886e38, 1.401298464324817e-45,
        1.1754943508222875e-38, 9007199254740993.0, 1e100, 1e-100, 0.001,
        100.0, 1234567.0, 9.999999999999999e22, 4.35, 0.15, 2.675,
        float("inf"), float("-inf"), float("nan"),
        2.0 ** 63, -(2.0 ** 63), 2.0 ** 63 - 1024, 1e19, -1e19, 0.49999999999999994]


def rand_double(rng):
    k = rng.randrange(5)
    if k == 0:
        return rng.choice(NICE)
    if k == 1:
        return struct.unpack("<d", struct.pack("<Q", rng.getrandbits(64)))[0]
    if k == 2:
        return rng.uniform(-1e6, 1e6)
    if k == 3:
        return round(rng.uniform(-1000, 1000), rng.randrange(0, 4))
    return rng.choice((1, -1)) * rng.random() * 10.0 ** rng.randrange(-320, 309)


def F(pre, x, exp=0):
    return f"{pre}:{dbits(x):016x}:{exp}"


def rand_F(rng, pre=None):
    pre = pre or rng.choice("SLX")
    x = rand_double(rng)
    if pre == "S" and rng.random() < 0.7:
        x = struct.unpack("<f", struct.pack("<f", x if abs(x) < 3.4e38 or x != x else 1.0))[0]
    return F(pre, x)


def float_literal(rng):
    k = rng.randrange(8)
    ip = rand_digits(rng, rng.choice((1, 1, 2, 3, 5, 10, 17, 20, 30)))
    fp = rand_digits(rng, rng.choice((1, 1, 2, 5, 10, 17, 20, 40)))
    s = ip + "." + fp
    if k >= 3:
        e = rng.choice(("+", "-")) + str(rng.choice((0, 1, 5, 10, 22, 37, 38, 39, 45, 46, 99,
                                                      300, 307, 308, 309, 320, 323, 324, 330,
                                                      400, 19999, 20000, 123456789012)))
        s += "e" + e
    return s


ODD_LITERALS = ["inf", "INF", "Infinity", "infinit", "nan", "NaN", "nan(1)", "-inf",
                "+1.5", " 1.5", "\t2", "1.5 ", ".5", "5.", "1e5", "1.e5", "1.5e",
                "1.5e+", "e5", ".", "-", "+", "", "0", "00", "0.", "0.000",
                "0e5", "000123.4500", "1.5E3", "1.5d3", "1.5D3", "1.5x3", "1.5X3",
                "1.5q", "--1", "1_000", "0.0000000000000000000000000001e-300",
                "1" + "0" * 400 + ".0", "0." + "0" * 400 + "1", "1.0e-400",
                "2.4703282292062327e-324", "2.4703282292062328e-324",
                "4.9406564584124654e-324", "1.7976931348623158e308",
                "1.7976931348623159e308", "3.4028235e38", "3.4028236e38",
                "1.00000005960464477539", "1.000000059604644775390625",
                "9007199254740993", "9007199254740993.0000000001"]


def float_cases(rng, out):
    for s in ODD_LITERALS:
        for p in "SLX":
            out.append(f"fnew {p} {htext(s)}")
        out.append(f"ctf {htext(s)}")
        out.append(f"ctl {htext(s)}")
        out.append(f"ctx {htext(s)}")
    for _ in range(1500):
        s = float_literal(rng)
        out.append(f"fnew {rng.choice('SLX')} {htext(s)}")
        out.append(f"ct{rng.choice('flx')} {htext(s)}")
    for op in ("fadd", "fsub", "fmul", "fdiv", "fmod", "fcmp"):
        for _ in range(250):
            pre = rng.choice("SLX")
            out.append(f"{op} {rand_F(rng, pre)} {rand_F(rng, pre)}")
        for _ in range(20):
            out.append(f"{op} {rand_F(rng)} {rand_F(rng)}")
        for a in NICE[::3]:
            for b in (0.0, 1.0, -0.0, 3.0, float("nan"), float("inf")):
                out.append(f"{op} {F('L', a)} {F('L', b)}")
    out.append(f"fcmp {F('L', 1.0, 1)} {F('L', 1.0, 0)}")
    out.append(f"fadd {F('L', 1.0, 1)} {F('L', 1.0, 0)}")
    for _ in range(200):
        out.append(f"fff {rand_F(rng)} {rng.choice('SLX')}")
    out.append(f"fff {F('L', 2.0, 3)} S")
    for _ in range(200):
        out.append(f"ffi {rand_i64(rng)} {rng.choice('SLX')}")
    for x in NICE + [0.5, 1.5, -0.5, -1.5, 2.5000000000000004, 1e300, -1e300,
                     9.223372036854776e18, -9.223372036854776e18, 1e-5, -1e-5]:
        for p in "SLX":
            out.append(f"frnd {F(p, x)}")
    for _ in range(400):
        out.append(f"frnd {rand_F(rng)}")
    for x in NICE:
        for p in "SLX":
            out.append(f"fchr {F(p, x)} 46")
            out.append(f"bflt {F(p, x)}")
    for _ in range(1500):
        f = rand_F(rng)
        out.append(f"fchr {f} {rng.choice((46, 46, 46, 10, 20, 23, 24, 25))}")
        out.append(f"bflt {f}")
    for _ in range(300):
        out.append(f"fbyt {rand_F(rng)}")
    for _ in range(300):
        p = rng.choice("SLX")
        n = 4 if p == "S" else 8
        out.append(f"ffb {p} {hexs(bytes(rng.randrange(256) for _ in range(n + rng.randrange(3))))}")


def convert_cases(rng, out):
    for _ in range(600):
        v = rand_i64(rng)
        base = rng.randrange(2, 17)
        out.append(f"cfi {v} {base} {rng.choice('TF')} {rng.choice((70, 70, 3, 10, 20, 64, 65, 66, 67))}")
        out.append(f"cfu {v} {base} {rng.choice('TF')} {rng.choice((70, 70, 3, 10, 20, 64, 65, 66, 67))}")
    for v in (0, -(1 << 63), (1 << 63) - 1, -1, 1):
        for base in (2, 8, 10, 16):
            for pre in "TF":
                out.append(f"cfi {v} {base} {pre} 70")
                out.append(f"cfu {v} {base} {pre} 70")
    styles = ("Flo", "AltFlo", "Sci", "AltSci", "Mix")
    for x in NICE:
        for st in styles:
            for p in (0, 1, 2, 6, 13, 17, 20):
                out.append(f"cfl {st} {p} {dbits(x):016x} 1000")
                out.append(f"cfx {st} {p} {dbits(x):016x} 1000")
                fx = struct.unpack("<f", struct.pack("<f", x if abs(x) < 3.4e38 or x != x else 0.0))[0]
                out.append(f"cff {st} {p} {fbits32(fx):08x} 1000")
    for _ in range(3000):
        x = rand_double(rng)
        st = rng.choice(styles)
        p = rng.choice((0, 1, 2, 3, 6, 9, 13, 14, 16, 17, 20, 25, 36))
        # a buffer that is too short is a subscript fault, except for the
        # exponent (Failed): short buffers only for Sci with few digits
        n = 1000
        if st in ("Sci", "AltSci") and p <= 13 and x == x and abs(x) != float("inf")                 and rng.random() < 0.3:
            n = rng.choice((p + 7, p + 8, p + 9, p + 10))
        if rng.random() < 0.4:
            fx = struct.unpack("<f", struct.pack("<f", x if abs(x) < 3.4e38 or x != x else 1.0))[0]
            out.append(f"cff {st} {p} {fbits32(fx):08x} {n}")
        else:
            out.append(f"cf{rng.choice('lx')} {st} {p} {dbits(x):016x} {n}")
    # exact ties for the rounding of digits
    for k in range(1, 60):
        x = (2 * rng.randrange(1, 1 << 20) + 1) / 2 ** k
        for p in (0, 1, 2, 3, 5, 8):
            out.append(f"cfl Flo {p} {dbits(x):016x} 1000")
            out.append(f"cfl Sci {p} {dbits(x):016x} 1000")


def buf_cases(rng, out):
    for v in list(range(-3, 1003, 7)) + EDGES:
        if -(1 << 63) <= v < (1 << 63):
            out.append(f"bint {v}")
    for _ in range(200):
        out.append(f"bint {rand_i64(rng)}")
        out.append(f"bintt {I(rand_int(rng))}")
    for _ in range(100):
        s = bytes(rng.randrange(1, 256) for _ in range(rng.randrange(0, 700)))
        start = rng.randrange(0, len(s) + 3)
        ln = rng.choice((0, 1, 10, 255, 256, 257, 600, 9223372036854775807))
        out.append(f"btxt {hexs(s)} {start} {ln}")
    for n in (0, 1, 1000, 0xFF00 - 1, 0xFF00, 0xFF00 + 1, 2 * 0xFF00, 2 * 0xFF00 + 5,
              200000, 3 * 0xFF00 - 1):
        out.append(f"bbig {n} {rng.randrange(1, 1000)}")


def id_cases(rng, out):
    names = []
    alnum = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_"
    for k in range(5000):
        ln = rng.choice((1, 1, 2, 3, 5, 8, 12, 20, 40, 100))
        nm = "".join(rng.choice(alnum) for _ in range(ln))
        if names and rng.random() < 0.2:
            nm = rng.choice(names)
        names.append(nm)
        r = rng.random()
        if r < 0.5:
            # a class # 0 is stored in an ARRAY [0..200]: only for the first ids
            cls = rng.randrange(1, 256) if k < 150 and rng.random() < 0.5 else 0
            out.append(f"dadd {htext(nm)} {cls}")
        else:
            extra = rng.choice((0, 0, 1, 3))
            buf = nm + "".join(rng.choice(alnum) for _ in range(extra))
            out.append(f"dfs {htext(buf)} {rng.choice((len(nm), len(nm), 99999))}")
        if k % 7 == 0:
            t = rng.randrange(1, 2 + len(set(names)) // 2)
            out.append(f"dtxt {t}")
            out.append(f"dcls {t}")
            out.append(f"dhash {t}")
            out.append(f"dput {t}")
            out.append(f"dlt {t} {rng.randrange(1, 2 + len(set(names)) // 2)}")
        if k % 50 == 0:
            out.append("dadv")
        if k % 3 == 0 and k > 100:
            out.append(f"dmark {rng.randrange(1, 60)}")
    out.append(f"dfs {htext('')} 0")
    out.append(f"dadd {htext('')} 0")
    out.append("dtxt 0")
    out.append("dput 0")
    for t in (199, 200, 201, 202):
        out.append(f"dcls {t}")
    for _ in range(300):
        out.append("dadv")
    out.append("dmark 5")
    out.append("dmark 5")


SYSTEMS = ["AMD64_NT", "I386_NT", "NT386", "AMD64_LINUX", "amd64_linux", "AMD64_DARWIN",
           "ARM64_DARWIN", "I386_LINUX", "LINUXLIBC6", "FreeBSD4", "I386_CYGWIN",
           "I386_INTERIX", "I386_MINGW", "SOLgnu", "SOLSUN", "SPARC32_SOLARIS",
           "SPARC64_SOLARIS", "I386_SOLARIS", "AMD64_SOLARIS", "PA32_HPUX", "PA64_HPUX",
           "MIPS64_OPENBSD", "MIPS64EL_OPENBSD", "MIPSEL_LINUX", "PPC_DARWIN",
           "PPC64_DARWIN", "ALPHA_OSF", "ALPHA32_VMS", "IA64_HPUX", "IA64_LINUX",
           "ARM_LINUX", "RISCV64_LINUX", "x86_64", "i686", "386", "X", ""]
MODES = ["IntegratedObject", "IntegratedAssembly", "ExternalObject", "ExternalAssembly",
         "C", "IntLlvmObj", "IntLlvmAsm", "ExtLlvmObj", "ExtLlvmAsm", "StAloneLlvmObj",
         "StAloneLlvmAsm"]


def target_cases(rng, out):
    for s in SYSTEMS:
        out.append(f"tinit {s} {rng.choice(('POSIX', 'WIN32'))} {rng.choice(MODES)}")
    for m in MODES:
        out.append(f"tinit AMD64_NT WIN32 {m}")
        out.append(f"tinit I386_NT WIN32 {m}")


FAULTS = [
    "inew 20",            # ' ' - '0' < 0
    "inew 2f",            # '/' - '0' < 0
    "inew 6135",          # 10 * 'a' + '5' - 528 > 255
    "inew 7a39",
    "inew 2031",
    "inew 3120202020",
    f"iub {I(-1)}",
    f"iub {I(-(1 << 70))}",
    f"iext {I(5)} 0",
    f"iext {I(5)} 10",
    "wnew 1 31",
    "wnew 17 31",
    f"wsh {I(5)} -9223372036854775808",
    f"wls {I(5)} 72",
    f"wls {I(5)} -1",
    f"wrs {I(5)} 72",
    f"wrot {I(5)} 3 0",
    f"wext {I(5)} 0 72",
    f"wext {I(5)} -1 3",
    f"wins {I(5)} {I(6)} -1 3",
    f"wtr {I(5)} 10",
    "wnb 72",
    "wnb -1",
    "cfi 5 1 F 70",
    "cfi 5 17 F 70",
    "cfu 5 1 F 70",
    "cfl Sci 13 3ff0000000000000 5",
    "cfl Flo 6 3ff0000000000000 3",
    "cfl Sci 0 7ff0000000000000 7",   # "Infinity" does not fit
    f"btxt 616263 1 -1",
    "dadd 616263 256",
    "dadd 616263 -1",
    "dtxt 1",
    "dhash 2",
    "dcls 1",
    "dlt 1 1",
    "dmark 1",
]


def main():
    rng = random.Random(SEED)
    out = []
    target_cases(rng, out)
    out.append("tinit AMD64_NT WIN32 C")
    int_cases(rng, out)
    word_cases(rng, out)
    float_cases(rng, out)
    convert_cases(rng, out)
    buf_cases(rng, out)
    id_cases(rng, out)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "cases.txt").write_text("\n".join(out) + "\n", newline="\n")
    (OUT / "faults.txt").write_text("\n".join(FAULTS) + "\n", newline="\n")
    print(f"{len(out)} cases, {len(FAULTS)} fault cases")


if __name__ == "__main__":
    main()
