(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* TCP sockets and the names of hosts of the Java platform, for the
   modules TCP and IP of m3j: the procedures are methods of the class
   m3j.rt.Net (through m3j.rt.Externals).

   A socket is a handle, a number greater than 0, for a socket that does
   not block. A result less than 0 is an error: the number of the error
   of Windows Sockets that it stands for, negated, or -1 for another
   error; -WouldBlock if the operation cannot go on without blocking (the
   caller waits with "Wait" and tries again). An address of IP version 4
   is an integer whose most significant byte is the first byte of the
   address: 127.0.0.1 is 16_7F000001. *)

INTERFACE M3jNet;

CONST
  WouldBlock = 10035;   (* WSAEWOULDBLOCK *)
  NotSocket = 10038;    (* WSAENOTSOCK *)
  AddrInUse = 10048;    (* WSAEADDRINUSE *)
  AddrNotAvail = 10049; (* WSAEADDRNOTAVAIL *)
  NetDown = 10050;      (* WSAENETDOWN *)
  NetUnreach = 10051;   (* WSAENETUNREACH *)
  ConnAborted = 10053;  (* WSAECONNABORTED *)
  ConnReset = 10054;    (* WSAECONNRESET *)
  TimedOut = 10060;     (* WSAETIMEDOUT *)
  ConnRefused = 10061;  (* WSAECONNREFUSED *)
  HostUnreach = 10065;  (* WSAEHOSTUNREACH *)
  TooMany = 10024;      (* WSAEMFILE *)

<*EXTERNAL "M3jNet__Listen"*>
PROCEDURE Listen (addr, port: INTEGER): INTEGER;
(* A socket that accepts connections on addr (0 for any of the machine's)
   and port (0: one that the system chooses). *)

<*EXTERNAL "M3jNet__LocalEndpoint"*>
PROCEDURE LocalEndpoint (h: INTEGER; VAR addr, port: INTEGER): INTEGER;
(* The local endpoint of socket h; 0 or an error. *)

<*EXTERNAL "M3jNet__PeerEndpoint"*>
PROCEDURE PeerEndpoint (h: INTEGER; VAR addr, port: INTEGER): INTEGER;
(* The endpoint of the other side of connection h; 0 or an error. *)

<*EXTERNAL "M3jNet__Accept"*>
PROCEDURE Accept (h: INTEGER; VAR addr, port: INTEGER): INTEGER;
(* A connection that socket h accepts, and the endpoint of its other side. *)

<*EXTERNAL "M3jNet__Connect"*>
PROCEDURE Connect (addr, port, fromAddr, fromPort: INTEGER): INTEGER;
(* A connection to addr and port, from fromAddr and fromPort if either
   is not 0, which "FinishConnect" completes. *)

<*EXTERNAL "M3jNet__FinishConnect"*>
PROCEDURE FinishConnect (h: INTEGER): INTEGER;
(* 1 if connection h is made, 0 if not yet, else the error of making it. *)

<*EXTERNAL "M3jNet__Recv"*>
PROCEDURE Recv (h: INTEGER; VAR b: ARRAY OF CHAR): INTEGER;
(* The number of bytes read from connection h into b, 0 at its end. *)

<*EXTERNAL "M3jNet__Send"*>
PROCEDURE Send (h: INTEGER; READONLY b: ARRAY OF CHAR): INTEGER;
(* The number of bytes of b written to connection h. *)

<*EXTERNAL "M3jNet__EOF"*>
PROCEDURE EOF (h: INTEGER): INTEGER;
(* 1 if connection h is closed by its other side and all of its bytes
   have been read, else 0 (or an error); does not block. *)

<*EXTERNAL "M3jNet__Wait"*>
PROCEDURE Wait (h: INTEGER; write: BOOLEAN; timeout: LONGREAL; alertable: BOOLEAN): INTEGER;
(* Waits until socket h is ready for writing if write, else for reading,
   accepting or connecting, for at most timeout seconds unless timeout is
   negative: 1 if it is ready or closed, 0 at the timeout, 2 if alertable
   and the calling thread is alerted (Thread.TestAlert takes the alert). *)

CONST NoDelay = 0;  KeepAlive = 1;  Linger = 2;

<*EXTERNAL "M3jNet__SetOption"*>
PROCEDURE SetOption (h: INTEGER; option: INTEGER; on: BOOLEAN): INTEGER;
(* Sets an option of connection h (Linger: one second); 0 or an error. *)

<*EXTERNAL "M3jNet__Shutdown"*>
PROCEDURE Shutdown (h: INTEGER; how: INTEGER): INTEGER;
(* Shuts down the input (how = 0) or output (how = 1) of connection h. *)

<*EXTERNAL "M3jNet__Close"*>
PROCEDURE Close (h: INTEGER);
(* Closes socket h; a thread that waits for it returns. *)

<*EXTERNAL "M3jNet__HostByName"*>
PROCEDURE HostByName (nm: TEXT; VAR addr: INTEGER): INTEGER;
(* 1 and the first address of IP version 4 of host nm, or 0 if it has none. *)

<*EXTERNAL "M3jNet__CanonicalByName"*>
PROCEDURE CanonicalByName (nm: TEXT): TEXT;
(* The canonical name of host nm, or NIL. *)

<*EXTERNAL "M3jNet__CanonicalByAddr"*>
PROCEDURE CanonicalByAddr (addr: INTEGER): TEXT;
(* The name of the host of addr, or NIL. *)

<*EXTERNAL "M3jNet__HostAddr"*>
PROCEDURE HostAddr (): INTEGER;
(* An address of the machine that runs the program (127.0.0.1 if none). *)

<*EXTERNAL "M3jNet__HostName"*>
PROCEDURE HostName (): TEXT;
(* The name of the machine that runs the program, or NIL. *)

<*EXTERNAL "M3jNet__AddrInfo"*>
PROCEDURE AddrInfo (name, service: TEXT; VAR family, port: INTEGER;
                    VAR a: ARRAY OF CHAR): INTEGER;
(* The first address of host name (NIL: the loopback address) and the
   port of service (NIL: 0; a number or a well-known name): family 4 or
   6, port, the bytes of the address in a (4 or 16); 0 or an error. *)

<*EXTERNAL "M3jNet__NameInfo"*>
PROCEDURE NameInfo (family, port: INTEGER; READONLY a: ARRAY OF CHAR;
                    VAR host, service: TEXT): INTEGER;
(* The name of the host of the address in a and that of the service of
   port (not set if port is 0); 0 or an error. *)

END M3jNet.
