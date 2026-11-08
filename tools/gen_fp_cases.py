#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Generate the input of the fingerprint test (tests/fingerprint).

Usage: python3 tools/gen_fp_cases.py

Writes tests/fingerprint/cases.txt (cases that both sides must compute
alike) and tests/fingerprint/faults.txt (cases on which CM3 stops with a
checked runtime error, which the port must reproduce). The cases are
pseudo-random with a fixed seed, so the files are reproducible. The line
format is described in tools/fpdump/src/Main.m3.
"""

import random
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "tests" / "fingerprint"


def hexs(data):
    return data.hex() if data else "-"


def rand_bytes(rng, n):
    return bytes(rng.randrange(256) for _ in range(n))


def lengths(rng):
    # Short texts, the boundaries of the word loop and of the 256-byte
    # buffer of FromText, and some long ones.
    ls = list(range(0, 40))
    ls += [252, 253, 254, 255, 256, 257, 258, 259, 260,
           511, 512, 513, 767, 768, 769, 1000, 1024, 4096]
    ls += [rng.randrange(40, 2000) for _ in range(40)]
    return ls


def main():
    rng = random.Random(19861212)
    cases = []
    add = cases.append

    # FromText: every single byte, ASCII words, random texts.
    for b in range(256):
        add(f"t {hexs(bytes([b]))}")
    for w in ["", "a", "abc", "Main", "INTEGER", "Text.T", "M3FP", "_m3main",
              "Hello, world!", "0123456789abcdef"]:
        add(f"t {hexs(w.encode('latin-1'))}")
    for n in lengths(rng):
        add(f"t {hexs(rand_bytes(rng, n))}")

    # Combine (Fingerprint and M3FP).
    for _ in range(300):
        a = rand_bytes(rng, rng.randrange(0, 40))
        b = rand_bytes(rng, rng.randrange(0, 40))
        add(f"c {hexs(a)} {hexs(b)}")
        add(f"m {hexs(a)} {hexs(b)}")

    # FromChars on top of a text's fingerprint.
    for _ in range(300):
        a = rand_bytes(rng, rng.randrange(0, 30))
        b = rand_bytes(rng, rng.choice([0, 1, 2, 3, 4, 5, 7, 8, 9, 16, 300]))
        add(f"f {hexs(a)} {hexs(b)}")

    # ExtendByInt with values of Int that are not negative.
    ints = [0, 1, 2, 255, 256, 65535, 65536, 2**24 - 1, 2**24, 2**31 - 1]
    ints += [rng.randrange(0, 2**31) for _ in range(200)]
    for i in ints:
        add(f"x {hexs(rand_bytes(rng, rng.randrange(0, 20)))} {i}")

    # FromInt on INTEGER (64 bits).
    nints = [0, 1, -1, 255, 256, 2**31 - 1, -2**31, 2**32 - 1, 2**32,
             2**63 - 1, -2**63]
    nints += [rng.randrange(-2**63, 2**63) for _ in range(200)]
    for i in nints:
        add(f"n {i}")

    # Power where no product is computed: d with at most one non-zero byte.
    for k in range(256):
        add(f"p {k}")
    for shift in (8, 16, 24):
        for k in range(1, 256 if shift < 24 else 128):
            add(f"p {k << shift}")

    faults = []
    # ExtendByInt with a negative Int.
    for i in [-1, -2, -256, -2**31] + [rng.randrange(-2**31, 0) for _ in range(5)]:
        faults.append(f"x {hexs(rand_bytes(rng, 3))} {i}")
    # Power with a product, and Product itself.
    for d in [257, 300, 65537, 70000, 123456789, 2**31 - 1]:
        faults.append(f"p {d}")
    for _ in range(5):
        faults.append(f"r {hexs(rand_bytes(rng, 8))} {hexs(rand_bytes(rng, 8))}")

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "cases.txt").write_text("\n".join(cases) + "\n", newline="\n")
    (OUT / "faults.txt").write_text("\n".join(faults) + "\n", newline="\n")
    print(f"wrote {len(cases)} cases and {len(faults)} fault cases to {OUT}")


if __name__ == "__main__":
    main()
