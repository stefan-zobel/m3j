(* The module IP of m3j: in cm3 it is UNSAFE and calls C (WIN32/IP.m3,
   IPCommon.m3, IPInternal.m3 and IP_c.c, which ask the resolver of the
   system); here the Java platform looks up the names (M3jNet). As in
   cm3, the port of an endpoint of GetAddrInfo and GetNameInfo is in
   network byte order. M3jNet knows only some services by name (ftp,
   ssh, telnet, smtp, domain, http, pop3, nntp, imap, https); any other
   service must be given by its number. *)

(* Copyright (C) 1995, Digital Equipment Corporation.       *)
(* All rights reserved.                                     *)
(* See the file COPYRIGHT for a full description.           *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

MODULE IP;

IMPORT IPError, M3jNet;

PROCEDURE ToInt (READONLY a: Address): INTEGER =
  BEGIN
    RETURN ((a.a[0] * 256 + a.a[1]) * 256 + a.a[2]) * 256 + a.a[3];
  END ToInt;

PROCEDURE FromInt (i: INTEGER): Address =
  VAR r: Address;
  BEGIN
    FOR k := 3 TO 0 BY -1 DO
      r.a[k] := i MOD 256;
      i := i DIV 256;
    END;
    RETURN r;
  END FromInt;

PROCEDURE Swap16 (p: Port): Port =
  (* between host (little-endian) and network byte order *)
  BEGIN
    RETURN p MOD 256 * 256 + p DIV 256;
  END Swap16;

PROCEDURE GetHostByName(nm: TEXT; VAR (*out*) res: Address): BOOLEAN
    RAISES {Error} =
  VAR a: INTEGER;
  BEGIN
    IF M3jNet.HostByName(nm, a) = 0 THEN RETURN FALSE; END;
    res := FromInt(a);
    RETURN TRUE;
  END GetHostByName;

PROCEDURE GetCanonicalByName(nm: TEXT): TEXT RAISES {Error} =
  BEGIN
    RETURN M3jNet.CanonicalByName(nm);
  END GetCanonicalByName;

PROCEDURE GetCanonicalByAddr(addr: Address): TEXT RAISES {Error} =
  BEGIN
    RETURN M3jNet.CanonicalByAddr(ToInt(addr));
  END GetCanonicalByAddr;

PROCEDURE GetHostAddr(): Address =
  BEGIN
    RETURN FromInt(M3jNet.HostAddr());
  END GetHostAddr;

(* Return the first usable address - there could be many - and more likely an IP4 address. *)
PROCEDURE GetAddrInfo(READONLY name,service : TEXT) : EP RAISES {Error} =
  VAR
    family, port: INTEGER;
    a: ARRAY [0..15] OF CHAR;
    err: INTEGER;
    ep4: Endpoint4;
    ep6: Endpoint16;
  BEGIN
    IF name = NIL AND service = NIL THEN
      IPError.Raise (LookupFailure);
    END;

    err := M3jNet.AddrInfo(name, service, family, port, a);

    IF err # 0 THEN
      IPError.Raise (LookupFailure, err);
    END;

    IF family = 4 THEN
      ep4 := NEW(Endpoint4, port := Swap16(port));
      FOR i := 0 TO 3 DO ep4.adr.a[i] := ORD(a[i]); END;
      RETURN ep4;
    ELSE
      ep6 := NEW(Endpoint16, port := Swap16(port));
      FOR i := 0 TO 15 DO ep6.adr.a[i] := ORD(a[i]); END;
      RETURN ep6;
    END;
  END GetAddrInfo;

PROCEDURE GetNameInfo(ep : EP; VAR (*out*) host,service : TEXT) RAISES {Error} =
  VAR
    family := 4;
    a: ARRAY [0..15] OF CHAR;
    err: INTEGER;
  BEGIN
    TYPECASE ep OF
    | Endpoint4(ep4) =>
       family := 4;
       FOR i := 0 TO 3 DO a[i] := VAL(ep4.adr.a[i], CHAR); END;
    | Endpoint16(ep6) =>
       family := 6;
       FOR i := 0 TO 15 DO a[i] := VAL(ep6.adr.a[i], CHAR); END;
    ELSE
        IPError.Raise (LookupFailure);
    END;

    err := M3jNet.NameInfo(family, Swap16(ep.port), a, host, service);

    IF err # 0 THEN
      IPError.Raise (LookupFailure, err);
    END;
  END GetNameInfo;

BEGIN
END IP.
