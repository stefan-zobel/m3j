# CM3's front end as a test program

The front end of CM3 5.12.0, compiled by m3j and run on the JVM
(`tools/m3front_test.py`): for the same units it must give the same
intermediate code (binary M3CG), byte for byte, and the same diagnostics
as m3j's front end, which is a Java port of it. Each directory is a source
package for `-pkg`, as in `tests/pkg`:

- `m3front`: the package m3front of cm3, its sources in `src` and the
  generic instances that a build of cm3 makes in `AMD64_NT`.
- `m3middle`: the units of the package m3middle that the front end uses,
  in their AMD64_NT variant (`ext64`). `TextUtils` of the package
  sysutils, which m3middle's `Target` uses, is in the library of m3j.
- `driver`: the main module, written for m3j: an environment of the front
  end (`M3Compiler.Environment`) that finds the units as m3j's driver does
  (`m3j.jvm.FrontEnv`), and a loop that compiles a list of units in one
  session.

The units keep their copyright notices (`COPYRIGHT-CMASS`,
`src/COPYRIGHT`); see also
`licenses/`.

These units are changed for m3j (each says so and why at its top):

| Unit | Change |
|---|---|
| `m3front/src/misc/Scanner.m3` | safe: the list of pragmas to ignore is a traced reference |
| `m3front/src/values/Module.m3` | the keyword `UNSAFE`, which it does not need, left out |
| `m3front/src/misc/M3.i3`, `m3front/src/types/Type.m3` | safe: the assumptions of `Type.IsEqual` are allocated instead of being the addresses of records on the stack (`EqAssumption <: REFANY`) |
| `m3middle/src/M3FP.m3` | safe: `Combine` copies the bytes instead of taking them with a `LOOPHOLE` |
| `m3middle/src/M3ID.i3`, `m3middle/src/M3ID.m3` | safe: each id has its own array of characters; `ToStr` left out |
| `m3middle/src/TFloat.m3` | safe: the bits of `REAL` and `LONGREAL` from `M3jFloat` |
| `m3middle/src/M3Buf.m3` | the text made with `Text.FromChars` instead of `Text8.Create` |
