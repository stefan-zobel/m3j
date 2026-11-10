(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Files on a server over SFTP (version 3, as OpenSSH speaks it), on a
   session of "SSH".

|    f := SFTP.Open (s);
|    wr := f.create ("report.txt");
|    Wr.PutText (wr, report);
|    Wr.Close (wr);
|    f.close ();

   Paths are characters, sent in UTF-8; a relative path is relative to the
   directory where the server starts the session, usually the home
   directory of the user. "*", "?" and "\\" are ordinary characters in a
   path. The data of files are bytes, one "CHAR" for each byte.

   An "SFTP.T" carries one operation at a time, and while a reader or
   writer of a file is open, the other operations raise "SSH.Error" with
   "SSH.Busy" until it is closed. For transfers in parallel, open several
   on one session.

   Each operation waits up to the "timeout" of the options of the session
   if that is positive; an alert or a timeout ends only the wait, the
   operation goes on, and the "SFTP.T" can still be used. *)

INTERFACE SFTP;

IMPORT Atom, Rd, SSH, Thread, Time, Wr;

VAR NoSuchFile, PermissionDenied, Exists: Atom.T;

(* Atoms of "SSH.Error" in addition to those of "SSH": "NoSuchFile": the
   file or a directory of its path does not exist. "PermissionDenied": the
   server does not allow the operation. "Exists": the target of "mkdir",
   "rename" or "symlink" exists. Other refusals of the server are
   "SSH.Failure", a broken connection "SSH.Disconnected". *)

TYPE
  Kind = {File, Directory, Link, Other};
  Mode = [0 .. 8_7777];

  Attributes = RECORD
    kind: Kind;
    size: INTEGER;
    mode: Mode;
    uid, gid: INTEGER;
    atime, mtime: Time.T;
  END;

(* "mode" has the bits of the permissions, set-user-ID, set-group-ID and
   sticky. The times are whole seconds; a server that does not give a
   value gives 0. *)

  Entry = RECORD
    name: TEXT;
    attr: Attributes;
  END;

  Entries = REF ARRAY OF Entry;

  T <: Public;
  Public = OBJECT
  METHODS
    open (path: TEXT; offset: CARDINAL := 0): Rd.T RAISES {SSH.Error, Thread.Alerted};
    create (path: TEXT; mode: Mode := 8_644; append := FALSE): Wr.T RAISES {SSH.Error, Thread.Alerted};
    stat (path: TEXT; VAR a: Attributes) RAISES {SSH.Error, Thread.Alerted};
    lstat (path: TEXT; VAR a: Attributes) RAISES {SSH.Error, Thread.Alerted};
    list (dir: TEXT): Entries RAISES {SSH.Error, Thread.Alerted};
    mkdir (path: TEXT; mode: Mode := 8_755) RAISES {SSH.Error, Thread.Alerted};
    rmdir (path: TEXT) RAISES {SSH.Error, Thread.Alerted};
    remove (path: TEXT) RAISES {SSH.Error, Thread.Alerted};
    rename (from, to: TEXT) RAISES {SSH.Error, Thread.Alerted};
    symlink (target, link: TEXT) RAISES {SSH.Error, Thread.Alerted};
    readlink (path: TEXT): TEXT RAISES {SSH.Error, Thread.Alerted};
    realpath (path: TEXT): TEXT RAISES {SSH.Error, Thread.Alerted};
    chmod (path: TEXT; mode: Mode) RAISES {SSH.Error, Thread.Alerted};
    setTimes (path: TEXT; atime, mtime: Time.T) RAISES {SSH.Error, Thread.Alerted};
    close ();
  END;

(* "open" returns a reader of the file from byte "offset" on. A broken
   connection makes it fail, never end early.

   "create" returns a writer of a file, which is made with "mode" if it
   does not exist; an existing file is emptied, or with "append" written at
   its end, and keeps its mode. "Wr.Close" raises "Wr.Failure" if the
   server did not take all the data.

   "stat" returns the attributes of a file, those of the target of a link;
   "lstat" those of the link itself.

   "list" returns the entries of a directory without "." and "..", in the
   order of the server; the attributes of a link are its own.

   "mkdir" makes a directory with "mode"; "rmdir" removes an empty one;
   "remove" removes a file or a link.

   "rename" renames a file or directory; where the server has the extension
   "posix-rename@openssh.com" (OpenSSH has), it replaces an existing file
   "to", otherwise that raises "Exists".

   "symlink" makes a symbolic link "link" that points to "target", which is
   taken as it is. "readlink" returns the target of a link.

   "realpath" returns the absolute form of a path, without "." and "..".

   "chmod" sets the mode of a file; "setTimes" its times of access and
   modification, in whole seconds from 1970 up to 2106.

   "close" ends the session of SFTP; open readers and writers fail. *)

PROCEDURE Open (s: SSH.T): T RAISES {SSH.Error, Thread.Alerted};
(* A session of SFTP on "s". *)

END SFTP.
