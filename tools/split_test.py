#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Test of the splitting of large methods: generates programs whose
procedures are too large for one method (beyond 8000 bytes, the limit of
HotSpot's JIT, and beyond 64 KB, the limit of the JVM), compiles them with
m3j, checks that no method is larger than 8000 bytes, runs them and
compares the output with tests/split/NAME.expected (the output of the same
programs built by cm3).

  python3 tools/split_test.py [--java JAVA] [--jar JAR] [--keep] [--jvm-arg=ARG]...

blocks: a procedure of 600 blocks with every kind of control flow (RETURN
and EXIT out of chunks, inside TRY FINALLY and LOOP; WITH, FOR, CASE, a
nested procedure, VAR and READONLY formals) and a large body of the unit.
case: an interpreter loop with a CASE of 300 arms.
stmts: a procedure whose large parts are the bodies of WHILE, REPEAT,
TRY EXCEPT (body and handler), TYPECASE (an arm) and LOCK, with
RETURN and EXIT out of them and exceptions raised in them.
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXPECTED = ROOT / "tests" / "split"
BS = chr(92)


def block(k, inloop):
    b = [
        f"(* block {k} *)", "i := 0;", "LOOP", "INC (i);", f"IF i > {k % 5 + 2} THEN EXIT END;",
        f"s := s + a[(i + {k}) MOD NUMBER (a)] * {k};", "r.x := r.x + i;", "END;",
        "FOR j := 1 TO 3 DO", f"s := s + j * {k} MOD 7;", f"IF s > {1000 + k * 37} THEN s := s DIV 3 END;",
        "END;", f"WITH e = arr[{k % 10}] DO e := e + s MOD 11; s := s + e END;",
        "TRY", f"INC (v, {k % 3});", f"IF n = {k} THEN RETURN s + 1000 END;", "FINALLY", "INC (fin);", "END;",
        f"t := t & Fmt.Int ({k % 10});", f"s := s + Nested ({k});",
    ]
    if inloop:
        b.append(f"IF n = -{k} THEN EXIT END;")
    b += [f"CASE (s + {k}) MOD 4 OF", "| 0 => INC (r.x)", "| 1 => DEC (r.x)", f"| 2 => r.x := r.x + {k}",
          "ELSE r.x := r.x DIV 2", "END;"]
    # one line per block: cm3's C back end names the procedures of FINALLY
    # parts after their line, modulo 10000
    return "      " + " ".join(b)


def blocks_program(n=600):
    L = ["MODULE Main;", "IMPORT IO, Fmt, Text;",
         "VAR arr: ARRAY [0 .. 9] OF INTEGER;  fin := 0;  log := \"\";", "",
         "PROCEDURE Big (VAR v: INTEGER; READONLY a: ARRAY OF INTEGER; n: INTEGER): INTEGER =",
         "  VAR s, i := 0;  r: RECORD x: INTEGER END;  t := \"\";",
         "  PROCEDURE Nested (k: INTEGER): INTEGER =", "    BEGIN", "      RETURN (s + k) MOD 13;",
         "    END Nested;", "  BEGIN", "    r.x := 0;", "    LOOP"]
    L += [block(k, True) for k in range(n // 2)]
    L += ["      EXIT;", "    END;"]
    L += [block(k, False) for k in range(n // 2, n)]
    L += ["    RETURN s + Text.Length (t) + r.x;", "  END Big;", "",
          "VAR v := 0;  a := ARRAY [0 .. 6] OF INTEGER {3, 1, 4, 1, 5, 9, 2};  q: INTEGER;", "BEGIN"]
    for m in [5, n // 2 + 3, -10, 1000, n - 1, 0]:
        L.append(f"  q := Big (v, a, {m});")
        L.append(f"  IO.Put (Fmt.Int ({m}) & \": \" & Fmt.Int (q) & \" \" & Fmt.Int (v) & \" \" & Fmt.Int (fin)"
                 f" & \"{BS}n\");")
    for k in range(n * 3):
        L.append(f"  arr[{k % 10}] := (arr[{(k + 3) % 10}] + {k}) MOD 1000;")
        if k % 7 == 0:
            L.append(f"  FOR j := 0 TO {k % 4} DO INC (q, arr[j]) END;")
        if k % 11 == 0:
            L.append("  log := log & Fmt.Int (q MOD 10);")
    L += [f"  IO.Put (Fmt.Int (q) & \" \" & log & \"{BS}n\");",
          "  FOR k := 0 TO 9 DO IO.Put (Fmt.Int (arr[k]) & \" \") END;", f"  IO.Put (\"{BS}n\");", "END Main."]
    return "\n".join(L) + "\n"


def case_program(arms=300):
    L = ["MODULE Main;", "IMPORT IO, Fmt;", "VAR mem: ARRAY [0..255] OF INTEGER;",
         "PROCEDURE Run (steps: INTEGER): INTEGER =", "  VAR acc, pc, x := 0;", "  BEGIN",
         "    FOR s := 1 TO steps DO", f"      CASE pc MOD {arms} OF"]
    for k in range(arms):
        L.append(f"      | {k} => acc := acc + mem[(pc + {k}) MOD 256] * {k % 13 + 1}; "
                 f"x := (x * 31 + acc) MOD 1000003; mem[(x + {k}) MOD 256] := acc MOD 997; "
                 f"IF acc > {100000 + k} THEN acc := acc DIV 7 END; pc := pc + 1 + (x MOD {k % 5 + 1});")
    L += ["      END;", "    END;", "    RETURN acc + x;", "  END Run;", "BEGIN",
          "  FOR i := 0 TO 255 DO mem[i] := i END;", f"  IO.Put (Fmt.Int (Run (30000000)) & \"{BS}n\");",
          "END Main."]
    return "\n".join(L) + "\n"


def small_block(k):
    """A block of statements for the bodies of stmts_program, on one line."""
    b = [f"(* {k} *)", "i := 0;", f"WHILE i < {k % 4 + 1} DO INC (i); s := s + i * {k} MOD 17 END;",
         f"REPEAT DEC (i); s := s + {k % 5} UNTIL i <= 0;",
         f"TRY IF (s + {k}) MOD 3 = 0 THEN RAISE E ({k}) END; INC (s) EXCEPT E (x) => s := s + x MOD 11 END;",
         f"TYPECASE objs[{k % 4}] OF NULL => INC (s, 2) | O2 (o) => s := s + o.k | O (o) => s := s - o.k MOD 5"
         " ELSE INC (s) END;",
         f"IF n = {k} THEN RETURN s END;", "s := s MOD 100003;"]
    return "      " + " ".join(b)


def stmts_program(n=150):
    L = ["MODULE Main;", "IMPORT IO, Fmt;", "EXCEPTION E (INTEGER);",
         "TYPE O = OBJECT k: INTEGER END;  O2 = O OBJECT END;",
         "VAR mu := NEW (MUTEX);  objs: ARRAY [0 .. 3] OF REFANY;  cnt := 0;", "",
         "PROCEDURE Big (n: INTEGER): INTEGER =", "  VAR s, i, w := 0;", "  BEGIN",
         "    WHILE w < 2 DO", "      INC (w);"]
    L += [small_block(k) for k in range(n)]
    L += ["      IF n = -1 THEN EXIT END;", "    END;", "    REPEAT"]
    L += [small_block(k) for k in range(n, 2 * n)]
    L += ["    UNTIL w > 0;", "    TRY"]
    L += [small_block(k) for k in range(2 * n, 3 * n)]
    L += ["      IF n = -2 THEN RAISE E (s) END;", "    EXCEPT", "    | E (x) =>", "      s := s + x;"]
    L += [small_block(k) for k in range(3 * n, 3 * n + n // 2)]
    L += ["    END;", "    TYPECASE objs[1] OF", "    | O2 =>", "      INC (s);", "    | O (o) =>",
          "      s := s + o.k;"]
    L += [small_block(k) for k in range(3 * n + n // 2, 4 * n + n // 2)]
    L += ["    ELSE", "      INC (s, 3);", "    END;", "    LOCK mu DO", "      INC (cnt);"]
    L += [small_block(k) for k in range(4 * n + n // 2, 5 * n + n // 2)]
    L += ["    END;", "    RETURN s;", "  END Big;", "",
          "VAR q: INTEGER;", "BEGIN", "  objs[0] := NEW (O, k := 3);  objs[1] := NEW (O, k := 7);",
          "  objs[2] := NEW (O2, k := 11);  objs[3] := NIL;"]
    for m in [5, n + 7, 2 * n + 1, 3 * n + 3, 4 * n, 5 * n + 2, -1, -2, 0, 100000]:
        # Big first: the order in which C evaluates the operands of & is not fixed
        L.append(f"  q := Big ({m});")
        L.append(f"  IO.Put (Fmt.Int ({m}) & \": \" & Fmt.Int (q) & \" \" & Fmt.Int (cnt) & \"{BS}n\");")
    L.append("END Main.")
    return "\n".join(L) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--jvm-arg", action="append", default=[],
                    help="an option for every JVM that the test starts (repeatable; --jvm-arg=-X...)")
    args = ap.parse_args()
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    sep = ";" if os.name == "nt" else ":"
    work = Path(tempfile.mkdtemp(prefix="m3j-split-"))
    failed = 0
    try:
        for name, text in [("blocks", blocks_program()), ("case", case_program()),
                           ("stmts", stmts_program())]:
            d = work / name
            d.mkdir()
            (d / "Main.m3").write_text(text)
            c = subprocess.run([args.java] + args.jvm_arg + ["-Xss64m", "-cp", args.jar, "m3j.jvm.Compile",
                                "-v", "Main.m3", "-d", "classes"], cwd=d, capture_output=True, text=True)
            msg = (c.stdout + c.stderr).strip()
            if c.returncode != 0 or "bytes" in msg:
                print(f"FAIL {name}: {msg[:500]}")
                failed += 1
                continue
            r = subprocess.run([args.java] + args.jvm_arg + ["-cp", args.jar + sep + "classes", "m3.Main"], cwd=d,
                               capture_output=True, text=True)
            exp = (EXPECTED / (name + ".expected")).read_text()
            if r.stdout.replace("\r\n", "\n") != exp:
                print(f"FAIL {name}: output differs:\n{r.stdout[:500]}{r.stderr[:500]}")
                failed += 1
            else:
                print(f"PASS {name}")
    finally:
        if args.keep:
            print("work directory:", work)
        else:
            shutil.rmtree(work, ignore_errors=True)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
