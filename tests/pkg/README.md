# Packages of the tests

Copies of the CM3 5.12.0 packages that the programs of the differential
tests (`tools/diff_test.py`) use besides those that m3j brings in `lib`
(m3core, libm3, tcp and sysutils), as installed for AMD64_NT: of
each package the units that the tests use, interfaces, generic units and
safe modules (in `src`) and the generic instances that a build of cm3
makes (in `AMD64_NT`). The compiler takes each as a source package
(`-pkg DIR`). The units keep their copyright notices; see also `licenses/`
and the notices in the packages (`COPYRIGHT-CMASS`, `src/COPYRIGHT`,
`arithmetic/copyrite.txt`).
