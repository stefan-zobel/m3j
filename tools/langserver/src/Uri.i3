(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* File URIs, as the clients of the language server name files
   ("file:///d%3A/work/pkg/src/Main.m3" on Windows,
   "file:///home/user/pkg/src/Main.m3" on POSIX), and the pathnames of
   the files that they name. *)

INTERFACE Uri;

PROCEDURE ToPath (uri: TEXT): TEXT;
(* The pathname of the file that the file URI "uri" names: its path,
   with the escapes "%XX" decoded; on Windows the slash before a drive
   letter is left out and the separators are backslashes. A text that
   does not start with "file:" is taken as a pathname and returned as
   it is. *)

PROCEDURE FromPath (path: TEXT): TEXT;
(* The file URI of the absolute pathname "path": its separators are
   slashes, a drive letter follows a slash, and the characters that may
   not be in the path of a URI are escaped as "%XX". *)

END Uri.
