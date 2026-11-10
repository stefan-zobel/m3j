(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Commands on a server over SSH, the client side of version 2 of the
   protocol, on JSch (a copy of its sources is in the runtime of m3j);
   the interface "SFTP" transfers files over the same session.

|    s := SSH.Connect ("build.example.org",
|                      SSH.Options {user := "ci", keyFile := "id_ed25519"});
|    status := s.run ("make -C /src", out, err);
|    s.close ();

   The client offers the key exchanges mlkem768x25519-sha256,
   curve25519-sha256, ecdh-sha2-nistp256/384/521 and
   diffie-hellman-group-exchange-sha256, group16, group18 and group14 with
   SHA-2; host keys and keys of users of the types Ed25519, ECDSA and RSA
   with SHA-2, also as certificates of OpenSSH; the ciphers AES-GCM and
   AES-CTR; MACs with SHA-2 and SHA-1. No compression, no agent, no
   forwarding of ports, no terminal.

   Texts of commands, users, passwords and paths are characters, sent in
   UTF-8; the data of standard input and output and of files are bytes, one
   "CHAR" for each byte. Several threads may use one session at the same
   time; each "Process" and each "SFTP.T" is for one thread at a time. *)

INTERFACE SSH;

IMPORT Atom, AtomList, Rd, Thread, Wr;

VAR
  Refused, Timeout, Disconnected, AuthFailed, HostKeyUnknown, HostKeyChanged, HostKeyRevoked, BadKey,
    Busy, TooLarge, Failure: Atom.T;

(* "Refused": no connection to the host could be made. "Timeout": a limit of
   time has expired. "Disconnected": the connection or the session has
   ended. "AuthFailed": the server accepted none of the ways to log in.
   "HostKeyUnknown": the key of the host is not among the known ones.
   "HostKeyChanged": the known hosts have another key of the same type for
   the host, or the key does not have the fingerprint of the options.
   "HostKeyRevoked": the known hosts mark the key as revoked. "BadKey": the
   key of the user cannot be read or decrypted. "Busy": an "SFTP.T" has an
   open stream. "TooLarge": an output exceeds its limit. "Failure": anything
   else, such as no algorithm in common with the server or a command or
   operation that the server refuses. *)

EXCEPTION Error (AtomList.T);

(* An "Error" carries one of the atoms above (or of "SFTP") and an atom with
   a description; for an error of the key of the host, that includes the
   fingerprint of the key. *)

TYPE
  HostCheck = {KnownHosts, AcceptNew, Fingerprint};

  Options = RECORD
    port: [1 .. 65535] := 22;
    user: TEXT := NIL;
    password: TEXT := NIL;
    keyFile: TEXT := NIL;
    key: TEXT := NIL;
    passphrase: TEXT := NIL;
    hostCheck := HostCheck.KnownHosts;
    knownHosts: TEXT := NIL;
    fingerprint: TEXT := NIL;
    timeout := 30.0D0;
    keepAlive := 0.0D0;
  END;

(* "user" "NIL" is the user of this process.

   The client logs in with the key of the user if there is one, then with
   the password, also as the answer of keyboard-interactive to a single
   prompt. "keyFile" is the name of a file with the private key, "key" the
   text of one; either in the format of OpenSSH, PEM or PKCS#8, encrypted
   with "passphrase" or not. A key of Ed25519 must be in the format of
   OpenSSH, the default of ssh-keygen. A file "keyFile-cert.pub" next to
   the key is its certificate.

   "hostCheck" decides which key of the host is accepted:

   "KnownHosts": one in the file "knownHosts" ("NIL": ~/.ssh/known_hosts),
   which may have hashed names, "@cert-authority" and "@revoked".

   "AcceptNew": the same, but the key of a host that the file does not
   name is added to it as a line, and the file is made if there is none.
   A host with another key of the same type is refused.

   "Fingerprint": the key whose fingerprint is "fingerprint", in the form
   that "ssh-keygen -l" shows ("SHA256:" and Base64). The client asks for a
   key of Ed25519 first, so this must be that key if the server has one;
   "hostKey" shows the fingerprint of a session.

   There is no check that accepts any key.

   "timeout", if it is positive, limits the seconds to connect and log in,
   to open a channel, and each operation of SFTP. "keepAlive", if it is
   positive, sends a message every so many seconds when the server is
   silent, and the session ends after three of them without an answer. *)

CONST Defaults = Options {};

TYPE
  T <: Public;
  Public = OBJECT
  METHODS
    run (command: TEXT; VAR out, err: TEXT; input := ""; maxOutput: CARDINAL := 16_1000000;
         timeout := 0.0D0): INTEGER RAISES {Error, Thread.Alerted};
    start (command: TEXT; maxBuffer: CARDINAL := 16_1000000): Process RAISES {Error, Thread.Alerted};
    hostKey (): TEXT;
    close ();
  END;

(* "run" runs "command" with "input" as its standard input and returns its
   exit status, with its standard output in "out" and its error output in
   "err"; -1 if it ended by a signal, or without a status. Each output may
   have up to "maxOutput" bytes, else the command's channel is closed and
   "Error" raised with "TooLarge". "timeout", if it is positive, limits the
   seconds of the command; then its channel is closed and "Error" raised
   with "Timeout". An alert closes the channel as well.

   "start" starts "command" and returns it while it runs. Its outputs wait
   for their readers in buffers, which never hold up the session; if more
   than "maxBuffer" bytes of one are not read, the command's channel is
   closed and both readers and "wait" raise "TooLarge". The session takes
   the outputs as fast as the server sends them and does not slow the
   command down, so a reader must keep up with it.

   "hostKey" returns the fingerprint of the key of the host, as
   "ssh-keygen -l" shows it.

   "close" ends the session, its commands and its sessions of SFTP. *)

  Process <: ProcessPublic;
  ProcessPublic = OBJECT
    stdin: Wr.T;
    stdout, stderr: Rd.T;
  METHODS
    wait (): INTEGER RAISES {Error, Thread.Alerted};
    signal (): TEXT;
    close ();
  END;

(* "stdin" is the standard input of the command; closing it sends the end of
   the input. "stdout" and "stderr" are its outputs; they fail with
   "Disconnected" if the connection breaks before their end. Reading them
   and writing "stdin" have no limit of time; an alert ends a wait.

   "wait" waits for the end of the command and returns its exit status, -1
   if it ended by a signal or without a status. It raises "Error" with
   "Disconnected" if the connection broke first.

   "signal", after "wait", is the name of the signal that ended the command
   ("TERM", "KILL"), or "NIL".

   "close" closes the channel of the command; what happens to a command that
   still runs is up to the server. *)

PROCEDURE Connect (host: TEXT; READONLY o := Defaults): T RAISES {Error, Thread.Alerted};
(* A session with "host", logged in. *)

END SSH.
