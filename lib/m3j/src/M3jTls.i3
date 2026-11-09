(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The engine of TLS of the Java platform, for the module TLS of m3j: the
   procedures are methods of the class m3j.rt.Tls (through
   m3j.rt.Externals). An engine does no input or output: it gets the bytes
   from the network with "Feed" and gives those for the network with
   "Drain". A result less than 0 is one of the numbers below, negated. *)

INTERFACE M3jTls;

CONST
  NeedData = 1;
  HandshakeFailed = 2;
  BadCertificate = 3;
  ProtocolError = 4;
  Truncated = 5;
  Closed = 6;

<*EXTERNAL "M3jTls__NewContext"*>
PROCEDURE NewContext (keyStore, keyPassword, trustStore, trustPassword: TEXT;
                      VAR err: TEXT): REFANY;
(* A context with the certificate of keyStore (NIL: none) that trusts the
   certificates of trustStore (NIL: those of the platform); NIL and err
   set if that fails. *)

<*EXTERNAL "M3jTls__NewEngine"*>
PROCEDURE NewEngine (context: REFANY; host: TEXT; client, needAuth: BOOLEAN;
                     alpn: TEXT): REFANY;
(* One end of a connection, with its handshake begun. *)

<*EXTERNAL "M3jTls__Feed"*>
PROCEDURE Feed (e: REFANY; READONLY b: ARRAY OF CHAR);
(* Gives e bytes from the network; none: the connection has ended. *)

<*EXTERNAL "M3jTls__Handshaking"*>
PROCEDURE Handshaking (e: REFANY): INTEGER;
(* 1 if the handshake goes on, 0 if it is done, or an error. *)

<*EXTERNAL "M3jTls__Take"*>
PROCEDURE Take (e: REFANY; VAR b: ARRAY OF CHAR): INTEGER;
(* The number of decrypted bytes put into b, 0 at the end of the data of
   the peer, or an error (-NeedData: more must come from the network). *)

<*EXTERNAL "M3jTls__Give"*>
PROCEDURE Give (e: REFANY; READONLY b: ARRAY OF CHAR): INTEGER;
(* Encrypts bytes of b, at least one if b is not empty: their number, or
   an error. *)

<*EXTERNAL "M3jTls__Drain"*>
PROCEDURE Drain (e: REFANY; VAR b: ARRAY OF CHAR): INTEGER;
(* The number of bytes for the network put into b, 0 if there are none. *)

<*EXTERNAL "M3jTls__CloseOutput"*>
PROCEDURE CloseOutput (e: REFANY);
(* Ends the output of e: "Drain" gives the notice to the peer. *)

<*EXTERNAL "M3jTls__Message"*>
PROCEDURE Message (e: REFANY): TEXT;
(* The text of the failure of e, or NIL. *)

CONST
  InfoProtocol = 0;
  InfoVersion = 1;
  InfoCipher = 2;
  InfoPeer = 3;

<*EXTERNAL "M3jTls__Info"*>
PROCEDURE Info (e: REFANY; what: INTEGER): TEXT;
(* What the handshake has found, or NIL. *)

END M3jTls.
