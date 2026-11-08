(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The version of m3build. cm3 writes this interface when it is built
   (version_impl of version.quake): here it is written by hand, with the
   version of cm3 whose sources m3build and m3j follow, so that the
   quake variables CM3_RELEASE, CM3_VERSION and CM3_CHANGED, which some
   m3makefiles test, are those of that cm3; M3j returns the version of
   m3j. *)

INTERFACE Version;

CONST
  Text = "d5.11.9";
  Number = "051109";
  LastChanged = "2022-02-05";
  GitInfo = "unknown";
  Sources = "d5.12.0 (sources of commit 20a295f)";
  (* The release of cm3 whose sources m3build and m3j follow, as -version
     shows it. Text is what that cm3 says of itself: its scripts/version
     still names d5.11.9. *)

PROCEDURE Created (): TEXT;
(* When m3build was built: unknown for m3j. *)

PROCEDURE M3j (): TEXT;
(* The version of m3j. *)

END Version.
