(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* TLS over a connection: a "T" is a "ConnFD.T" that encrypts what passes
   through another "ConnFD.T", usually a "TCP.T". Readers and writers come
   from "ConnRW" or "ConnMsgRW", as for any other connection:

|    ctx := TLS.NewContext ();
|    t := TLS.Client (ctx, TCP.Connect (ep), "www.example.org");
|    rd := ConnRW.NewRd (t);  wr := ConnRW.NewWr (t);

   On the side of a server, a thread for each connection does the
   handshake:

|    ctx := TLS.NewContext ("server.p12", "password");
|    t := TLS.Server (ctx, TCP.Accept (connector));

   The protocol, the checks of certificates and the certificates that are
   trusted by default are those of the Java platform.

   One thread may read a "T" while another one writes it. Errors in the
   handshake raise "IP.Error", later ones "Rd.Failure" and "Wr.Failure",
   with a list of one of the atoms below and a text that describes the
   error; those of the connection below keep their atoms. A timeout in the
   handshake raises "IP.Error" with "TCP.Timeout". A "T" that is closed
   raises the failures with "TCP.Closed", as a "TCP.T" does. *)

INTERFACE TLS;

IMPORT Atom, ConnFD, IP, Thread;

TYPE
  T <: ConnFD.T;
  Context <: ROOT;

VAR
  HandshakeFailed, BadCertificate, BadKeyStore, ProtocolError, Truncated: Atom.T;

(* "HandshakeFailed": the two sides could not agree, or the peer does not
   speak TLS.

   "BadCertificate": the certificate of the peer is not trusted, has
   expired, or is not that of the host.

   "BadKeyStore": a file given to "NewContext" cannot be read.

   "ProtocolError": the peer has broken the protocol after the handshake,
   or has refused the connection by an alert.

   "Truncated": the connection ended without the notice of closing that
   the protocol requires, so the data may be incomplete. *)

PROCEDURE NewContext (keyStore, keyPassword, trustStore, trustPassword: TEXT := NIL): Context
  RAISES {IP.Error};
(* The settings of connections. "keyStore" is a file of format PKCS12 or
   JKS with the key and the certificate of this side, "NIL" for none; a
   server needs one. "trustStore" is such a file, or a file of certificates
   in the format PEM, with the certificates that are trusted, "NIL" for
   those of the platform. *)

PROCEDURE Client (ctx: Context; conn: ConnFD.T; host: TEXT; alpn: TEXT := NIL;
                  waitFor: LONGREAL := -1.0D0): T
  RAISES {IP.Error, Thread.Alerted};
(* Does the handshake of a client over "conn" and returns the connection.
   The certificate of the peer must be valid for "host", a name or an
   address of IP; "host" must not be "NIL". "alpn" lists the protocols
   that are offered to the peer, separated by commas ("h2,http/1.1").
   The handshake takes at most "waitFor" seconds unless that is negative.

   The new connection owns "conn": it closes "conn" when it is closed. If
   the procedure raises an exception, "conn" is closed. *)

PROCEDURE Server (ctx: Context; conn: ConnFD.T; needClientCert := FALSE; alpn: TEXT := NIL;
                  waitFor: LONGREAL := -1.0D0): T
  RAISES {IP.Error, Thread.Alerted};
(* Does the handshake of a server, like "Client". If "needClientCert", the
   client must present a certificate that "ctx" trusts. "alpn" lists the
   protocols that the server accepts, the ones it prefers first. With
   version 1.3 of the protocol a client learns only at its first read
   that a server has refused its certificate. *)

PROCEDURE Protocol (t: T): TEXT;
(* The protocol that the two sides chose by "alpn", or "NIL". *)

PROCEDURE Version (t: T): TEXT;
(* The version of the protocol, for example "TLSv1.3". *)

PROCEDURE Cipher (t: T): TEXT;
(* The name of the cipher suite. *)

PROCEDURE PeerName (t: T): TEXT;
(* The subject of the certificate of the peer, or "NIL" if it has none. *)

(* The method "shutdownOut" sends the notice of closing and shuts down the
   output of the connection below. With version 1.3 of the protocol the
   peer can still send data after that; with version 1.2 the notice ends
   both directions.

   The method "close" tries to send the notice as well, and may block
   until the connection below can take it. A "put" that was alerted or
   that failed leaves the output unusable, for part of a record may have
   been sent: every later "put" raises "Wr.Failure". *)

END TLS.
