# CM3's stub generator for network objects as a test program

`stubgen` of CM3 5.12.0, compiled by m3j and run on the JVM
(`tools/stubgen_test.py`): for the network object types of CM3's
packages it must write the same stubs, byte for byte, the same output
and the same exit code as `stubgen` built by cm3. The program is built on
the toolkit m3tk, a front end of its own for Modula-3 (about 64,000
lines): it parses and checks the interfaces, and stubgen walks their
trees.

- `stubgen`: the sources of stubgen (`m3-comm/stubgen/src` of CM3).
- `m3tk`, `m3tk-misc`: the packages of cm3 that stubgen is built on, the
  units of their `.M3EXPORTS` for AMD64_NT, with the generic instances
  that a build of cm3 makes in `AMD64_NT`; source packages for `-pkg`.
- `cases.list`: the cases, one per line: a directory of `cases`, a
  network object type and, for `netobjrv1`, the interface that `-i`
  names. They are the calls of `netobj` and its variants in the
  m3makefiles of CM3's sources, except one whose package imports a
  package that CM3 does not have (`tracker`, `logitech`) and three of
  packages without a clear license (`m3readline`, `mscheme`,
  `rsrcservice`).
- `cases/PKG`: the interfaces of the case's own package that the
  network object types import, directly or not (`netobjtest/WeakRefList.i3`
  is the generic instance that the m3makefile of its package has a build
  make).
- `intf`: the other interfaces they import, from the packages that their
  package imports (m3core, libm3, netobj, tcp, sharedobj, obliq and
  others).
- `expected/PKG/INTF.T-vN`: what stubgen built by cm3 writes for the
  case with `-vN` (`tools/stubgen_test.py --record`), and `run.txt`, its
  exit code and output.

The units keep their copyright notices (`m3tk/src/COPYRIGHT`,
`m3tk-misc/src/COPYRIGHT`, `stubgen/src/COPYRIGHT`); see also `licenses/`
(`COPYRIGHT-DEC`, `COPYRIGHT-CMASS`, `COPYRIGHT-COLUMBIA`, and for m3tk
`COPYRIGHT-OLIVETTI` and `COPYRIGHT-SUN`).

These units are changed for m3j (each says so and why at its top):

| Unit | Change |
|---|---|
| `stubgen/src/FRefRefTbl.m3` | safe: the hash of a key is that of its identity (`M3jRef.Hash`) instead of its address |
| `m3tk/src/fe/WiredStandard.m3` | the keyword `UNSAFE`, which it does not need, left out |
| `m3tk/src/files/Common/M3DirFindFile.m3` | the name of a directory in a file of imports (`.M3IMPTAB`, which the language server reads) is the rest of its line, so it may have spaces |

Of m3tk, `M3LFingerPrint` and `M3LTypeHash`, and of m3tk-misc,
`FingerPrint`, are UNSAFE modules that stubgen does not use.
