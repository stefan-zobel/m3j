(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* SSH and SFTP on JSch, for the library ssh of m3j: the procedures are
   methods of the class m3j.rt.Ssh (through m3j.rt.Externals). A result
   less than 0 is one of the numbers below, negated; err then has a
   description. A timeout that is not positive means no limit. *)

INTERFACE M3jSsh;

CONST
  Timeout = 1;
  Alerted = 2;
  Refused = 3;
  Disconnected = 4;
  AuthFailed = 5;
  HostKeyUnknown = 6;
  HostKeyChanged = 7;
  HostKeyRevoked = 8;
  BadKey = 9;
  Busy = 10;
  TooLarge = 11;
  Failure = 12;
  NoSuchFile = 13;
  PermissionDenied = 14;
  Exists = 15;

  (* checks of the key of the host *)
  KnownHosts = 0;
  AcceptNew = 1;
  Fingerprint = 2;

  (* kinds of files *)
  File = 0;
  Directory = 1;
  Link = 2;
  Other = 3;

  (* operations of "SftpOp" *)
  Mkdir = 0;
  Rmdir = 1;
  Remove = 2;
  Rename = 3;
  Symlink = 4;
  Readlink = 5;
  Realpath = 6;
  Chmod = 7;
  SetTimes = 8;

<*EXTERNAL "M3jSsh__Connect"*>
PROCEDURE Connect (host: TEXT; port: INTEGER; user, password, keyFile, key, passphrase: TEXT;
                   hostCheck: INTEGER; knownHosts, fingerprint: TEXT; timeout, keepAlive: LONGREAL;
                   VAR code: INTEGER; VAR err: TEXT): REFANY;
(* A session, or NIL with code and err set. "user" NIL is the user of the
   process, "knownHosts" NIL is ~/.ssh/known_hosts. *)

<*EXTERNAL "M3jSsh__HostKey"*>
PROCEDURE HostKey (s: REFANY): TEXT;
(* The fingerprint of the key of the host, "SHA256:" and Base64. *)

<*EXTERNAL "M3jSsh__Disconnect"*>
PROCEDURE Disconnect (s: REFANY);
(* Ends the session with all its channels. *)

<*EXTERNAL "M3jSsh__Run"*>
PROCEDURE Run (s: REFANY; command, input: TEXT; maxOutput: INTEGER; timeout, openTimeout: LONGREAL;
               VAR status: INTEGER; VAR signal, out, errOut, err: TEXT): INTEGER;
(* Runs a command with "input" as its standard input, to its end: 0 with
   its status (-1 after a signal), signal (or NIL) and output, or an error.
   Each output may have up to "maxOutput" bytes. *)

<*EXTERNAL "M3jSsh__Start"*>
PROCEDURE Start (s: REFANY; command: TEXT; maxBuffer: INTEGER; openTimeout: LONGREAL; VAR code: INTEGER;
                 VAR err: TEXT): REFANY;
(* A running command, or NIL with code and err set. Each output keeps up to
   "maxBuffer" bytes that are not read. *)

<*EXTERNAL "M3jSsh__ProcRead"*>
PROCEDURE ProcRead (p: REFANY; which: INTEGER; VAR b: ARRAY OF CHAR; timeout: LONGREAL; VAR err: TEXT): INTEGER;
(* The number of bytes of the output "which" (0 standard, 1 error) put
   into b, 0 at its end, or an error. A timeout of 0 does not wait, a
   negative one has no limit. *)

<*EXTERNAL "M3jSsh__ProcDiscard"*>
PROCEDURE ProcDiscard (p: REFANY; which: INTEGER);
(* Drops what the output "which" has and will get. *)

<*EXTERNAL "M3jSsh__ProcWrite"*>
PROCEDURE ProcWrite (p: REFANY; READONLY b: ARRAY OF CHAR; VAR err: TEXT): INTEGER;
(* Adds to the standard input of the command: 0, or an error. *)

<*EXTERNAL "M3jSsh__ProcCloseInput"*>
PROCEDURE ProcCloseInput (p: REFANY);
(* Ends the standard input after what was written. *)

<*EXTERNAL "M3jSsh__ProcWait"*>
PROCEDURE ProcWait (s, p: REFANY; VAR status: INTEGER; VAR signal, err: TEXT): INTEGER;
(* Waits for the end of the command: 0 with status and signal, or an error. *)

<*EXTERNAL "M3jSsh__ProcClose"*>
PROCEDURE ProcClose (p: REFANY);
(* Ends the channel of the command. *)

<*EXTERNAL "M3jSsh__SftpOpen"*>
PROCEDURE SftpOpen (s: REFANY; timeout: LONGREAL; VAR code: INTEGER; VAR err: TEXT): REFANY;
(* A session of SFTP whose operations wait up to "timeout" seconds, or NIL
   with code and err set. *)

<*EXTERNAL "M3jSsh__SftpClose"*>
PROCEDURE SftpClose (f: REFANY);

<*EXTERNAL "M3jSsh__SftpStat"*>
PROCEDURE SftpStat (f: REFANY; path: TEXT; follow: BOOLEAN; VAR kind, size, mode, uid, gid, atime, mtime: INTEGER;
                    VAR err: TEXT): INTEGER;
(* The attributes of a file, of the target of a link if "follow": 0, or an
   error. The times are seconds since 1970. *)

<*EXTERNAL "M3jSsh__SftpList"*>
PROCEDURE SftpList (f: REFANY; dir: TEXT; VAR list: REFANY; VAR err: TEXT): INTEGER;
(* The number of entries of a directory, without "." and "..", or an error. *)

<*EXTERNAL "M3jSsh__SftpEntry"*>
PROCEDURE SftpEntry (list: REFANY; i: INTEGER; VAR kind, size, mode, uid, gid, atime, mtime: INTEGER): TEXT;
(* The name and attributes of entry i of a list. *)

<*EXTERNAL "M3jSsh__SftpOp"*>
PROCEDURE SftpOp (f: REFANY; op: INTEGER; a, b: TEXT; n, m: INTEGER; VAR result, err: TEXT): INTEGER;
(* The operation "op" on the path a: 0, or an error. "Rename" and
   "Symlink" take b; "Mkdir" and "Chmod" take the mode n; "SetTimes" takes
   the times n and m. "Readlink" and "Realpath" put their path into
   result. *)

<*EXTERNAL "M3jSsh__SftpGet"*>
PROCEDURE SftpGet (f: REFANY; path: TEXT; offset: INTEGER; VAR code: INTEGER; VAR err: TEXT): REFANY;
(* A stream that reads a file from "offset" on, or NIL with code and err set. *)

<*EXTERNAL "M3jSsh__SftpPut"*>
PROCEDURE SftpPut (f: REFANY; path: TEXT; mode: INTEGER; append: BOOLEAN; VAR code: INTEGER;
                   VAR err: TEXT): REFANY;
(* A stream that writes a file, which gets the mode if it is new, or NIL
   with code and err set. *)

<*EXTERNAL "M3jSsh__StreamRead"*>
PROCEDURE StreamRead (st: REFANY; VAR b: ARRAY OF CHAR; VAR err: TEXT): INTEGER;
(* The number of bytes put into b, 0 at the end, or an error. *)

<*EXTERNAL "M3jSsh__StreamWrite"*>
PROCEDURE StreamWrite (st: REFANY; READONLY b: ARRAY OF CHAR; VAR err: TEXT): INTEGER;
(* 0, or an error. *)

<*EXTERNAL "M3jSsh__StreamClose"*>
PROCEDURE StreamClose (st: REFANY; VAR err: TEXT): INTEGER;
(* Ends a stream: 0, or an error, which for a written file means that its
   data may not all be there. *)

END M3jSsh.
