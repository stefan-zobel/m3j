(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Reference side of the m3middle test: reads cases from stdin and writes
   the results of CM3's TInt, TWord, TFloat, Convert, M3Buf, M3ID and
   Target to stdout. m3j.tools.MidDump writes the same lines for the Java
   port.

   Each input line is an operation code followed by arguments separated by
   single blanks. Arguments:
     INT   a decimal integer
     HEX   a sequence of bytes as pairs of hexadecimal digits, "-" for none
     I     a TInt.Int as its 9 bytes in hexadecimal, byte[0] first
     F     a Target.Float as P:BITS:EXP, P one of S, L, X (the precision),
           BITS the 64 bits of the fraction in hexadecimal, EXP the exponent
     P     a precision: S, L or X
     STYLE a Convert.Style: Flo, AltFlo, Sci, AltSci or Mix
   Booleans are written as T and F. The operations are listed in
   DoLine. Every output line starts with the input line, followed by
   " => " and the result. The state of M3ID and Target carries over from
   line to line. A checked runtime error ends the program. *)

UNSAFE MODULE Main;

IMPORT Rd, Wr, Stdio, Text, Fmt, Scan, Thread, Lex, FloatMode, Word;
IMPORT TInt, TWord, TFloat, Target, TargetMap, M3RT, M3Buf, M3ID, M3FP;
IMPORT Convert;

<*FATAL Rd.Failure, Wr.Failure, Thread.Alerted, Lex.Error, FloatMode.Trap*>

TYPE
  Byte8 = BITS 8 FOR [0..255];
  Bytes4 = ARRAY [0..3] OF Byte8;

VAR out := Stdio.stdout;

(*------------------------------------------------------------ parsing ---*)

PROCEDURE HexDigit (c: CHAR): INTEGER =
  BEGIN
    CASE c OF
    | '0'..'9' => RETURN ORD (c) - ORD ('0');
    | 'a'..'f' => RETURN ORD (c) - ORD ('a') + 10;
    | 'A'..'F' => RETURN ORD (c) - ORD ('A') + 10;
    ELSE <*ASSERT FALSE*>
    END;
  END HexDigit;

PROCEDURE Bytes (hex: TEXT): REF ARRAY OF CHAR =
  VAR n: INTEGER;  r: REF ARRAY OF CHAR;
  BEGIN
    IF Text.Equal (hex, "-") THEN RETURN NEW (REF ARRAY OF CHAR, 0) END;
    n := Text.Length (hex) DIV 2;
    <*ASSERT 2 * n = Text.Length (hex)*>
    r := NEW (REF ARRAY OF CHAR, n);
    FOR i := 0 TO n - 1 DO
      r[i] := VAL (16 * HexDigit (Text.GetChar (hex, 2 * i))
                     + HexDigit (Text.GetChar (hex, 2 * i + 1)), CHAR);
    END;
    RETURN r;
  END Bytes;

PROCEDURE ParseI (hex: TEXT): TInt.Int =
  VAR b := Bytes (hex);  r: TInt.Int;
  BEGIN
    <*ASSERT NUMBER (b^) = NUMBER (r)*>
    FOR i := 0 TO LAST (r) DO r[i] := ORD (b[i]) END;
    RETURN r;
  END ParseI;

PROCEDURE ParseBits (hex: TEXT): INTEGER =
  VAR x := 0;
  BEGIN
    <*ASSERT Text.Length (hex) = 16*>
    FOR i := 0 TO 15 DO
      x := Word.Or (Word.LeftShift (x, 4), HexDigit (Text.GetChar (hex, i)));
    END;
    RETURN x;
  END ParseBits;

PROCEDURE ParseP (t: TEXT): Target.Precision =
  BEGIN
    IF    Text.Equal (t, "S") THEN RETURN Target.Precision.Short;
    ELSIF Text.Equal (t, "L") THEN RETURN Target.Precision.Long;
    ELSIF Text.Equal (t, "X") THEN RETURN Target.Precision.Extended;
    ELSE <*ASSERT FALSE*>
    END;
  END ParseP;

PROCEDURE LongOfBits (x: INTEGER): LONGREAL =
  BEGIN
    RETURN LOOPHOLE (x, LONGREAL);
  END LongOfBits;

PROCEDURE ParseF (t: TEXT): Target.Float =
  VAR f: Target.Float;  pos := 0;
  BEGIN
    f.pre := ParseP (Sub (t, pos, ':'));
    f.fraction := FLOAT (LongOfBits (ParseBits (Sub (t, pos, ':'))), EXTENDED);
    f.exponent := Scan.Int (Sub (t, pos, ':'));
    RETURN f;
  END ParseF;

PROCEDURE ParseStyle (t: TEXT): Convert.Style =
  BEGIN
    FOR s := FIRST (Convert.Style) TO LAST (Convert.Style) DO
      IF Text.Equal (t, StyleNames[s]) THEN RETURN s END;
    END;
    <*ASSERT FALSE*>
  END ParseStyle;

CONST
  StyleNames = ARRAY Convert.Style OF TEXT
    { "Flo", "AltFlo", "Sci", "AltSci", "Mix" };
  ModeNames = ARRAY Target.M3BackendMode_t OF TEXT
    { "IntegratedObject", "IntegratedAssembly", "ExternalObject",
      "ExternalAssembly", "C", "IntLlvmObj", "IntLlvmAsm", "ExtLlvmObj",
      "ExtLlvmAsm", "StAloneLlvmObj", "StAloneLlvmAsm" };
  PNames = ARRAY Target.Precision OF TEXT { "S", "L", "X" };

PROCEDURE ParseMode (t: TEXT): Target.M3BackendMode_t =
  BEGIN
    FOR m := FIRST (Target.M3BackendMode_t) TO LAST (Target.M3BackendMode_t) DO
      IF Text.Equal (t, ModeNames[m]) THEN RETURN m END;
    END;
    <*ASSERT FALSE*>
  END ParseMode;

PROCEDURE Sub (t: TEXT; VAR pos: INTEGER; sep: CHAR): TEXT =
  VAR start := pos;  len := Text.Length (t);
  BEGIN
    WHILE pos < len AND Text.GetChar (t, pos) # sep DO INC (pos) END;
    VAR f := Text.Sub (t, start, pos - start); BEGIN
      IF pos < len THEN INC (pos) END;
      RETURN f;
    END;
  END Sub;

(*----------------------------------------------------------- printing ---*)

PROCEDURE Hex2 (b: INTEGER): TEXT =
  BEGIN
    RETURN Fmt.Pad (Fmt.Int (b, 16), 2, '0');
  END Hex2;

PROCEDURE HexChars (READONLY a: ARRAY OF CHAR): TEXT =
  VAR t := "";
  BEGIN
    IF NUMBER (a) = 0 THEN RETURN "-" END;
    FOR i := FIRST (a) TO LAST (a) DO t := t & Hex2 (ORD (a[i])) END;
    RETURN t;
  END HexChars;

PROCEDURE HexText (x: TEXT): TEXT =
  VAR t := "";
  BEGIN
    IF x = NIL THEN RETURN "NIL" END;
    IF Text.Length (x) = 0 THEN RETURN "-" END;
    FOR i := 0 TO Text.Length (x) - 1 DO
      t := t & Hex2 (ORD (Text.GetChar (x, i)));
    END;
    RETURN t;
  END HexText;

PROCEDURE FPHex (READONLY fp: M3FP.T): TEXT =
  VAR t := "";
  BEGIN
    FOR k := FIRST (fp.byte) TO LAST (fp.byte) DO t := t & Hex2 (fp.byte[k]) END;
    RETURN t;
  END FPHex;

PROCEDURE IText (READONLY i: TInt.Int): TEXT =
  VAR t := "";
  BEGIN
    FOR k := 0 TO LAST (i) DO t := t & Hex2 (i[k]) END;
    RETURN t;
  END IText;

PROCEDURE BitsText (x: INTEGER): TEXT =
  VAR t := "";
  BEGIN
    FOR k := 15 TO 0 BY -1 DO
      t := t & Fmt.Int (Word.Extract (x, 4 * k, 4), 16);
    END;
    RETURN t;
  END BitsText;

PROCEDURE LongBits (x: LONGREAL): INTEGER =
  BEGIN
    RETURN LOOPHOLE (x, INTEGER);
  END LongBits;

PROCEDURE RealBits (x: REAL): TEXT =
  VAR b := LOOPHOLE (x, Bytes4);  t := "";
  BEGIN
    FOR k := 3 TO 0 BY -1 DO t := t & Hex2 (b[k]) END;
    RETURN t;
  END RealBits;

PROCEDURE RealOfHex (hex: TEXT): REAL =
  VAR b: Bytes4;
  BEGIN
    <*ASSERT Text.Length (hex) = 8*>
    FOR k := 0 TO 3 DO
      b[3 - k] := 16 * HexDigit (Text.GetChar (hex, 2 * k))
                    + HexDigit (Text.GetChar (hex, 2 * k + 1));
    END;
    RETURN LOOPHOLE (b, REAL);
  END RealOfHex;

PROCEDURE FText (READONLY f: Target.Float): TEXT =
  BEGIN
    RETURN PNames[f.pre] & ":" & BitsText (LongBits (FLOAT (f.fraction, LONGREAL)))
             & ":" & Fmt.Int (f.exponent);
  END FText;

PROCEDURE B (b: BOOLEAN): TEXT =
  BEGIN
    IF b THEN RETURN "T" ELSE RETURN "F" END;
  END B;

PROCEDURE BI (b: BOOLEAN; READONLY i: TInt.Int): TEXT =
  BEGIN
    RETURN B (b) & " " & IText (i);
  END BI;

PROCEDURE BF (b: BOOLEAN; READONLY f: Target.Float): TEXT =
  BEGIN
    RETURN B (b) & " " & FText (f);
  END BF;

PROCEDURE IntTypeText (READONLY x: Target.Int_type): TEXT =
  BEGIN
    RETURN Fmt.Int (ORD (x.cg_type)) & "," & Fmt.Int (x.size) & ","
             & Fmt.Int (x.align) & "," & Fmt.Int (x.pack) & ","
             & Fmt.Int (x.bytes) & "," & IText (x.min) & "," & IText (x.max);
  END IntTypeText;

PROCEDURE FloatTypeText (READONLY x: Target.Float_type): TEXT =
  BEGIN
    RETURN Fmt.Int (ORD (x.cg_type)) & "," & PNames[x.pre] & ","
             & Fmt.Int (x.size) & "," & Fmt.Int (x.align) & ","
             & Fmt.Int (x.bytes) & "," & FText (x.min) & "," & FText (x.max);
  END FloatTypeText;

PROCEDURE CCText (cc: Target.CallingConvention): TEXT =
  BEGIN
    IF cc = NIL THEN RETURN "NIL" END;
    RETURN cc.name & "," & Fmt.Int (cc.m3cg_id) & ","
             & B (cc.args_left_to_right) & B (cc.results_on_left)
             & B (cc.standard_structs);
  END CCText;

CONST
  CCNames = ARRAY OF TEXT { "__cdecl", "__stdcall", "C", "WINAPIV", "WINAPI",
                            "CALLBACK", "APIENTRY", "APIPRIVATE", "PASCAL",
                            "pascal", "" };

PROCEDURE TargetText (ok: BOOLEAN): TEXT =
  VAR t := B (ok);
  BEGIN
    IF NOT ok THEN RETURN t END;
    t := t & " Address=" & IntTypeText (Target.Address);
    t := t & " Integer=" & IntTypeText (Target.Integer);
    t := t & " Word=" & IntTypeText (Target.Word);
    t := t & " Longint=" & IntTypeText (Target.Longint);
    t := t & " Long=" & IntTypeText (Target.Long);
    t := t & " Longreal=" & FloatTypeText (Target.Longreal);
    t := t & " Extended=" & FloatTypeText (Target.Extended);
    t := t & " Atomic=";
    FOR c := FIRST (Target.Atomic_lock_free) TO LAST (Target.Atomic_lock_free) DO
      t := t & B (Target.Atomic_lock_free[c]);
    END;
    t := t & " endian=" & Fmt.Int (ORD (Target.endian));
    t := t & " packed=" & B (Target.Allow_packed_byte_aligned);
    t := t & " walker=" & B (Target.Has_stack_walker);
    t := t & " sigsetjmp=" & B (Target.Sigsetjmp);
    t := t & " setjmp=" & Target.Setjmp;
    t := t & " typenames=" & B (Target.Typenames);
    t := t & " default=" & CCText (Target.DefaultCall);
    FOR i := FIRST (CCNames) TO LAST (CCNames) DO
      t := t & " cc=" & CCText (Target.FindConvention (CCNames[i]));
    END;
    FOR i := 0 TO 8 DO
      t := t & " id=" & CCText (Target.ConventionFromID (i));
    END;
    t := t & " map=";
    FOR c := FIRST (Target.CGType) TO LAST (Target.CGType) DO
      t := t & Fmt.Int (TargetMap.CG_Align[c]) & ","
             & Fmt.Int (TargetMap.CG_Align_bytes[c]) & ","
             & Fmt.Int (TargetMap.CG_Size[c]) & ","
             & Fmt.Int (TargetMap.CG_Bytes[c]) & ";";
    END;
    FOR i := 0 TO 3 DO
      t := t & " wt=" & IntTypeText (TargetMap.Word_types[i]);
      t := t & " it=" & IntTypeText (TargetMap.Integer_types[i]);
    END;
    FOR p := FIRST (Target.Precision) TO LAST (Target.Precision) DO
      t := t & " ft=" & FloatTypeText (TargetMap.Float_types[p]);
    END;
    t := t & M3RTText ();
    RETURN t;
  END TargetText;

PROCEDURE M3RTText (): TEXT =
  VAR t := "";
  BEGIN
    t := t & " CL_marker=" & Fmt.Int (M3RT.CL_marker) & " CL_proc=" & Fmt.Int (M3RT.CL_proc) & " CL_frame=" & Fmt.Int (M3RT.CL_frame) & " CL_SIZE=" & Fmt.Int (M3RT.CL_SIZE);
    t := t & " ED_uid=" & Fmt.Int (M3RT.ED_uid) & " ED_name=" & Fmt.Int (M3RT.ED_name) & " ED_implicit=" & Fmt.Int (M3RT.ED_implicit) & " ED_SIZE=" & Fmt.Int (M3RT.ED_SIZE);
    t := t & " EX_class=" & Fmt.Int (M3RT.EX_class) & " EX_outermost=" & Fmt.Int (M3RT.EX_outermost) & " EX_end_of_list=" & Fmt.Int (M3RT.EX_end_of_list) & " EX_pad=" & Fmt.Int (M3RT.EX_pad);
    t := t & " EX_start=" & Fmt.Int (M3RT.EX_start) & " EX_stop=" & Fmt.Int (M3RT.EX_stop) & " EX_excepts=" & Fmt.Int (M3RT.EX_excepts) & " EX_offset=" & Fmt.Int (M3RT.EX_offset);
    t := t & " EX_SIZE=" & Fmt.Int (M3RT.EX_SIZE) & " EA_exception=" & Fmt.Int (M3RT.EA_exception) & " EA_arg=" & Fmt.Int (M3RT.EA_arg) & " EA_module=" & Fmt.Int (M3RT.EA_module);
    t := t & " EA_line=" & Fmt.Int (M3RT.EA_line) & " EA_pc=" & Fmt.Int (M3RT.EA_pc) & " EA_info0=" & Fmt.Int (M3RT.EA_info0) & " EA_info1=" & Fmt.Int (M3RT.EA_info1);
    t := t & " EA_un_except=" & Fmt.Int (M3RT.EA_un_except) & " EA_un_arg=" & Fmt.Int (M3RT.EA_un_arg) & " EA_SIZE=" & Fmt.Int (M3RT.EA_SIZE) & " EF_next=" & Fmt.Int (M3RT.EF_next);
    t := t & " EF_class=" & Fmt.Int (M3RT.EF_class) & " EF_SIZE=" & Fmt.Int (M3RT.EF_SIZE) & " EF1_handles=" & Fmt.Int (M3RT.EF1_handles) & " EF1_info=" & Fmt.Int (M3RT.EF1_info);
    t := t & " EF1_jmpbuf=" & Fmt.Int (M3RT.EF1_jmpbuf) & " EF1_SIZE=" & Fmt.Int (M3RT.EF1_SIZE) & " EF2_handler=" & Fmt.Int (M3RT.EF2_handler) & " EF2_frame=" & Fmt.Int (M3RT.EF2_frame);
    t := t & " EF2_info=" & Fmt.Int (M3RT.EF2_info) & " EF2_SIZE=" & Fmt.Int (M3RT.EF2_SIZE) & " EF3_raises=" & Fmt.Int (M3RT.EF3_raises) & " EF3_SIZE=" & Fmt.Int (M3RT.EF3_SIZE);
    t := t & " EF4_mutex=" & Fmt.Int (M3RT.EF4_mutex) & " EF4_SIZE=" & Fmt.Int (M3RT.EF4_SIZE) & " TC_typecode=" & Fmt.Int (M3RT.TC_typecode) & " TC_selfID=" & Fmt.Int (M3RT.TC_selfID);
    t := t & " TC_fp=" & Fmt.Int (M3RT.TC_fp) & " TC_traced=" & Fmt.Int (M3RT.TC_traced) & " TC_kind=" & Fmt.Int (M3RT.TC_kind) & " TC_link_state=" & Fmt.Int (M3RT.TC_link_state);
    t := t & " TC_dataAlignment=" & Fmt.Int (M3RT.TC_dataAlignment) & " TC_dataSize=" & Fmt.Int (M3RT.TC_dataSize) & " TC_type_map=" & Fmt.Int (M3RT.TC_type_map) & " TC_gc_map=" & Fmt.Int (M3RT.TC_gc_map);
    t := t & " TC_type_desc=" & Fmt.Int (M3RT.TC_type_desc) & " TC_initProc=" & Fmt.Int (M3RT.TC_initProc) & " TC_brand=" & Fmt.Int (M3RT.TC_brand) & " TC_name=" & Fmt.Int (M3RT.TC_name);
    t := t & " TC_next=" & Fmt.Int (M3RT.TC_next) & " TC_SIZE=" & Fmt.Int (M3RT.TC_SIZE) & " TC_ALIGN=" & Fmt.Int (M3RT.TC_ALIGN) & " OTC_parentID=" & Fmt.Int (M3RT.OTC_parentID);
    t := t & " OTC_linkProc=" & Fmt.Int (M3RT.OTC_linkProc) & " OTC_dataOffset=" & Fmt.Int (M3RT.OTC_dataOffset) & " OTC_methodOffset=" & Fmt.Int (M3RT.OTC_methodOffset) & " OTC_methodSize=" & Fmt.Int (M3RT.OTC_methodSize);
    t := t & " OTC_defaultMethods=" & Fmt.Int (M3RT.OTC_defaultMethods) & " OTC_parent=" & Fmt.Int (M3RT.OTC_parent) & " OTC_SIZE=" & Fmt.Int (M3RT.OTC_SIZE) & " ATC_nDimensions=" & Fmt.Int (M3RT.ATC_nDimensions);
    t := t & " ATC_elementSize=" & Fmt.Int (M3RT.ATC_elementSize) & " ATC_SIZE=" & Fmt.Int (M3RT.ATC_SIZE) & " RV_lhs_id=" & Fmt.Int (M3RT.RV_lhs_id) & " RV_rhs_id=" & Fmt.Int (M3RT.RV_rhs_id);
    t := t & " RV_SIZE=" & Fmt.Int (M3RT.RV_SIZE) & " OA_elt_ptr=" & Fmt.Int (M3RT.OA_elt_ptr) & " OA_sizes=" & Fmt.Int (M3RT.OA_sizes) & " OA_size_0=" & Fmt.Int (M3RT.OA_size_0);
    t := t & " OA_size_1=" & Fmt.Int (M3RT.OA_size_1) & " MI_file=" & Fmt.Int (M3RT.MI_file) & " MI_type_cells=" & Fmt.Int (M3RT.MI_type_cells) & " MI_type_cell_ptrs=" & Fmt.Int (M3RT.MI_type_cell_ptrs);
    t := t & " MI_full_rev=" & Fmt.Int (M3RT.MI_full_rev) & " MI_part_rev=" & Fmt.Int (M3RT.MI_part_rev) & " MI_proc_info=" & Fmt.Int (M3RT.MI_proc_info) & " MI_try_scopes=" & Fmt.Int (M3RT.MI_try_scopes);
    t := t & " MI_var_map=" & Fmt.Int (M3RT.MI_var_map) & " MI_gc_map=" & Fmt.Int (M3RT.MI_gc_map) & " MI_imports=" & Fmt.Int (M3RT.MI_imports) & " MI_link_state=" & Fmt.Int (M3RT.MI_link_state);
    t := t & " MI_binder=" & Fmt.Int (M3RT.MI_binder) & " MI_gc_flags=" & Fmt.Int (M3RT.MI_gc_flags) & " MI_SIZE=" & Fmt.Int (M3RT.MI_SIZE) & " II_import=" & Fmt.Int (M3RT.II_import);
    t := t & " II_binder=" & Fmt.Int (M3RT.II_binder) & " II_next=" & Fmt.Int (M3RT.II_next) & " II_SIZE=" & Fmt.Int (M3RT.II_SIZE) & " PI_proc=" & Fmt.Int (M3RT.PI_proc);
    t := t & " PI_name=" & Fmt.Int (M3RT.PI_name) & " PI_SIZE=" & Fmt.Int (M3RT.PI_SIZE) & " MUTEX_acquire=" & Fmt.Int (M3RT.MUTEX_acquire) & " MUTEX_release=" & Fmt.Int (M3RT.MUTEX_release);
    RETURN t;
  END M3RTText;

(*--------------------------------------------------------- operations ---*)

PROCEDURE Int (t: TEXT): INTEGER =
  BEGIN
    RETURN Scan.Int (t);
  END Int;

PROCEDURE CharsText (READONLY buf: ARRAY OF CHAR; len: INTEGER): TEXT =
  BEGIN
    IF len < 0 THEN RETURN Fmt.Int (len) END;
    RETURN Fmt.Int (len) & " " & HexChars (SUBARRAY (buf, 0, len));
  END CharsText;

PROCEDURE DoLine (line: TEXT) =
  VAR
    pos := 0;
    op := Sub (line, pos, ' ');
    a := Sub (line, pos, ' ');
    b := Sub (line, pos, ' ');
    c := Sub (line, pos, ' ');
    d := Sub (line, pos, ' ');
    res: TEXT;
    i, j: TInt.Int;
    f: Target.Float;
    x: INTEGER;
    buf: REF ARRAY OF CHAR;
    ok: BOOLEAN;
  BEGIN
    i := TInt.Zero;
    j := TInt.Zero;
    f := TFloat.ZeroR;
    IF Text.Equal (op, "ifi") THEN
      ok := TInt.FromInt (Int (a), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "iti") THEN
      x := 0;
      ok := TInt.ToInt (ParseI (a), x);
      res := B (ok) & " " & Fmt.Int (x);
    ELSIF Text.Equal (op, "inew") THEN
      ok := TInt.New (Bytes (a)^, i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "iadd") THEN
      ok := TInt.Add (ParseI (a), ParseI (b), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "isub") THEN
      ok := TInt.Subtract (ParseI (a), ParseI (b), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "imul") THEN
      ok := TInt.Multiply (ParseI (a), ParseI (b), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "idiv") THEN
      ok := TInt.Div (ParseI (a), ParseI (b), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "imod") THEN
      ok := TInt.Mod (ParseI (a), ParseI (b), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "iinc") THEN
      i := ParseI (a);  ok := TInt.Inc (i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "idec") THEN
      i := ParseI (a);  ok := TInt.Dec (i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "ineg") THEN
      ok := TInt.Negate (ParseI (a), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "iabs") THEN
      ok := TInt.Abs (ParseI (a), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "icmp") THEN
      i := ParseI (a);  j := ParseI (b);
      res := B (TInt.EQ (i, j)) & B (TInt.LT (i, j)) & B (TInt.LE (i, j))
               & B (TInt.NE (i, j)) & B (TInt.GT (i, j)) & B (TInt.GE (i, j));
    ELSIF Text.Equal (op, "itxt") THEN
      res := TInt.ToText (ParseI (a));
    ELSIF Text.Equal (op, "ichr") THEN
      buf := NEW (REF ARRAY OF CHAR, Int (b));
      res := CharsText (buf^, TInt.ToChars (ParseI (a), buf^));
    ELSIF Text.Equal (op, "iub") THEN
      VAR ub: ARRAY [0..8] OF [0..255];  n: CARDINAL;  BEGIN
        n := TInt.ToUnsignedBytes (ParseI (a), ub);
        res := Fmt.Int (n) & " ";
        FOR k := 0 TO n - 1 DO res := res & Hex2 (ub[k]) END;
      END;
    ELSIF Text.Equal (op, "iext") THEN
      ok := TInt.Extend (ParseI (a), Int (b), i);  res := BI (ok, i);

    ELSIF Text.Equal (op, "wnew") THEN
      ok := TWord.New (Bytes (b)^, Int (a), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "wadd") THEN
      TWord.Add (ParseI (a), ParseI (b), i);  res := IText (i);
    ELSIF Text.Equal (op, "wsub") THEN
      TWord.Subtract (ParseI (a), ParseI (b), i);  res := IText (i);
    ELSIF Text.Equal (op, "wmul") THEN
      TWord.Multiply (ParseI (a), ParseI (b), i);  res := IText (i);
    ELSIF Text.Equal (op, "wdiv") THEN
      ok := TWord.Div (ParseI (a), ParseI (b), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "wmod") THEN
      ok := TWord.Mod (ParseI (a), ParseI (b), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "wdm") THEN
      TWord.DivMod (ParseI (a), ParseI (b), i, j);
      res := IText (i) & " " & IText (j);
    ELSIF Text.Equal (op, "wcmp") THEN
      i := ParseI (a);  j := ParseI (b);
      res := B (TWord.LT (i, j)) & B (TWord.LE (i, j))
               & B (TWord.GT (i, j)) & B (TWord.GE (i, j));
    ELSIF Text.Equal (op, "wand") THEN
      TWord.And (ParseI (a), ParseI (b), i);  res := IText (i);
    ELSIF Text.Equal (op, "wor") THEN
      TWord.Or (ParseI (a), ParseI (b), i);  res := IText (i);
    ELSIF Text.Equal (op, "wxor") THEN
      TWord.Xor (ParseI (a), ParseI (b), i);  res := IText (i);
    ELSIF Text.Equal (op, "wnot") THEN
      TWord.Not (ParseI (a), i);  res := IText (i);
    ELSIF Text.Equal (op, "wsh") THEN
      TWord.Shift (ParseI (a), Int (b), i);  res := IText (i);
    ELSIF Text.Equal (op, "wls") THEN
      TWord.LeftShift (ParseI (a), Int (b), i);  res := IText (i);
    ELSIF Text.Equal (op, "wrs") THEN
      TWord.RightShift (ParseI (a), Int (b), i);  res := IText (i);
    ELSIF Text.Equal (op, "wrot") THEN
      TWord.Rotate (ParseI (a), Int (b), Int (c), i);  res := IText (i);
    ELSIF Text.Equal (op, "wext") THEN
      ok := TWord.Extract (ParseI (a), Int (b), Int (c), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "wins") THEN
      ok := TWord.Insert (ParseI (a), ParseI (b), Int (c), Int (d), i);
      res := BI (ok, i);
    ELSIF Text.Equal (op, "wtr") THEN
      ok := TWord.Truncate (ParseI (a), Int (b), i);  res := BI (ok, i);
    ELSIF Text.Equal (op, "wnb") THEN
      TWord.nBitsOnRight (Int (a), i);  res := IText (i);

    ELSIF Text.Equal (op, "fnew") THEN
      ok := TFloat.New (Bytes (b)^, ParseP (a), f);  res := BF (ok, f);
    ELSIF Text.Equal (op, "fadd") THEN
      ok := TFloat.Add (ParseF (a), ParseF (b), f);  res := BF (ok, f);
    ELSIF Text.Equal (op, "fsub") THEN
      ok := TFloat.Subtract (ParseF (a), ParseF (b), f);  res := BF (ok, f);
    ELSIF Text.Equal (op, "fmul") THEN
      ok := TFloat.Multiply (ParseF (a), ParseF (b), f);  res := BF (ok, f);
    ELSIF Text.Equal (op, "fdiv") THEN
      ok := TFloat.Divide (ParseF (a), ParseF (b), f);  res := BF (ok, f);
    ELSIF Text.Equal (op, "fmod") THEN
      ok := TFloat.Mod (ParseF (a), ParseF (b), f);  res := BF (ok, f);
    ELSIF Text.Equal (op, "fcmp") THEN
      VAR fa := ParseF (a);  fb := ParseF (b);  BEGIN
        res := B (TFloat.EQ (fa, fb)) & B (TFloat.LT (fa, fb))
                 & B (TFloat.LE (fa, fb));
      END;
    ELSIF Text.Equal (op, "fff") THEN
      ok := TFloat.FloatF (ParseF (a), ParseP (b), f);  res := BF (ok, f);
    ELSIF Text.Equal (op, "ffi") THEN
      ok := TFloat.FloatI (Int (a), ParseP (b), f);  res := BF (ok, f);
    ELSIF Text.Equal (op, "frnd") THEN
      VAR fa := ParseF (a);  BEGIN
        res := Fmt.Int (TFloat.Trunc (fa)) & " " & Fmt.Int (TFloat.Round (fa))
                 & " " & Fmt.Int (TFloat.Floor (fa))
                 & " " & Fmt.Int (TFloat.Ceiling (fa));
      END;
    ELSIF Text.Equal (op, "fchr") THEN
      buf := NEW (REF ARRAY OF CHAR, Int (b));
      res := CharsText (buf^, TFloat.ToChars (ParseF (a), buf^));
    ELSIF Text.Equal (op, "fbyt") THEN
      VAR fb: ARRAY [0..15] OF TFloat.Byte;  n: INTEGER;  BEGIN
        n := TFloat.ToBytes (ParseF (a), fb);
        res := Fmt.Int (n) & " ";
        FOR k := 0 TO n - 1 DO res := res & Hex2 (fb[k]) END;
      END;
    ELSIF Text.Equal (op, "ffb") THEN
      VAR cb := Bytes (b);  fb := NEW (REF ARRAY OF TFloat.Byte, NUMBER (cb^));
      BEGIN
        FOR k := 0 TO LAST (cb^) DO fb[k] := ORD (cb[k]) END;
        TFloat.FromBytes (fb^, ParseP (a), f);
        res := FText (f);
      END;

    ELSIF Text.Equal (op, "cfi") OR Text.Equal (op, "cfu") THEN
      buf := NEW (REF ARRAY OF CHAR, Int (d));
      TRY
        IF Text.Equal (op, "cfi") THEN
          x := Convert.FromInt (buf^, Int (a), Int (b), Text.Equal (c, "T"));
        ELSE
          x := Convert.FromUnsigned (buf^, Int (a), Int (b), Text.Equal (c, "T"));
        END;
        res := CharsText (buf^, x);
      EXCEPT Convert.Failed => res := "Failed";
      END;
    ELSIF Text.Equal (op, "cff") OR Text.Equal (op, "cfl")
       OR Text.Equal (op, "cfx") THEN
      buf := NEW (REF ARRAY OF CHAR, Int (d));
      TRY
        IF Text.Equal (op, "cff") THEN
          x := Convert.FromFloat (buf^, RealOfHex (c), Int (b), ParseStyle (a));
        ELSIF Text.Equal (op, "cfl") THEN
          x := Convert.FromLongFloat (buf^, LongOfBits (ParseBits (c)),
                                      Int (b), ParseStyle (a));
        ELSE
          x := Convert.FromExtended (buf^, FLOAT (LongOfBits (ParseBits (c)),
                                                  EXTENDED),
                                     Int (b), ParseStyle (a));
        END;
        res := CharsText (buf^, x);
      EXCEPT Convert.Failed => res := "Failed";
      END;
    ELSIF Text.Equal (op, "ctf") THEN
      TRY
        VAR r := Convert.ToFloat (Bytes (a)^, x); BEGIN
          res := RealBits (r) & " " & Fmt.Int (x);
        END;
      EXCEPT Convert.Failed => res := "Failed";
      END;
    ELSIF Text.Equal (op, "ctl") THEN
      TRY
        VAR r := Convert.ToLongFloat (Bytes (a)^, x); BEGIN
          res := BitsText (LongBits (r)) & " " & Fmt.Int (x);
        END;
      EXCEPT Convert.Failed => res := "Failed";
      END;
    ELSIF Text.Equal (op, "ctx") THEN
      TRY
        VAR r := Convert.ToExtended (Bytes (a)^, x); BEGIN
          res := BitsText (LongBits (FLOAT (r, LONGREAL))) & " " & Fmt.Int (x);
        END;
      EXCEPT Convert.Failed => res := "Failed";
      END;

    ELSIF Text.Equal (op, "bint") THEN
      VAR m := M3Buf.New (); BEGIN
        M3Buf.PutInt (m, Int (a));  res := M3Buf.ToText (m);
      END;
    ELSIF Text.Equal (op, "bintt") THEN
      VAR m := M3Buf.New (); BEGIN
        M3Buf.PutIntt (m, ParseI (a));  res := M3Buf.ToText (m);
      END;
    ELSIF Text.Equal (op, "bflt") THEN
      VAR m := M3Buf.New (); BEGIN
        M3Buf.PutFloat (m, ParseF (a));  res := M3Buf.ToText (m);
      END;
    ELSIF Text.Equal (op, "btxt") THEN
      VAR m := M3Buf.New (); BEGIN
        M3Buf.PutSubText (m, Text.FromChars (Bytes (a)^), Int (b), Int (c));
        res := HexText (M3Buf.ToText (m));
      END;
    ELSIF Text.Equal (op, "bbig") THEN
      (* a: number of characters, b: seed; written with PutChar, PutSub and
         PutText in turn, then read back with ToFP and ToText *)
      VAR
        m1 := M3Buf.New ();  m2 := M3Buf.New ();
        n := Int (a);  seed := Int (b);  k := 0;  len, ch: INTEGER;
        piece: REF ARRAY OF CHAR;  txt: TEXT;  fp: M3FP.T;
      BEGIN
        WHILE k < n DO
          len := MIN (n - k, Word.Mod (k * seed, 700) + 1);
          piece := NEW (REF ARRAY OF CHAR, len);
          FOR q := 0 TO len - 1 DO
            ch := Word.Mod ((k + q) * seed + q, 256);
            piece[q] := VAL (ch, CHAR);
          END;
          CASE Word.Mod (k, 3) OF
          | 0 => FOR q := 0 TO len - 1 DO
                   M3Buf.PutChar (m1, piece[q]);  M3Buf.PutChar (m2, piece[q]);
                 END;
          | 1 => M3Buf.PutSub (m1, piece^);  M3Buf.PutSub (m2, piece^);
          | 2 => M3Buf.PutText (m1, Text.FromChars (piece^));
                 M3Buf.PutText (m2, Text.FromChars (piece^));
          ELSE <*ASSERT FALSE*>
          END;
          INC (k, len);
        END;
        fp := M3Buf.ToFP (m1);
        txt := M3Buf.ToText (m2);
        res := FPHex (fp) & " " & Fmt.Int (Text.Length (txt)) & " "
                 & FPHex (M3FP.FromText (txt));
      END;

    ELSIF Text.Equal (op, "dadd") THEN
      res := Fmt.Int (M3ID.Add (Text.FromChars (Bytes (a)^), Int (b)));
    ELSIF Text.Equal (op, "dfs") THEN
      res := Fmt.Int (M3ID.FromStr (Bytes (a)^, Int (b)));
    ELSIF Text.Equal (op, "dtxt") THEN
      res := HexText (M3ID.ToText (Int (a)));
    ELSIF Text.Equal (op, "dcls") THEN
      res := Fmt.Int (M3ID.GetClass (Int (a)));
    ELSIF Text.Equal (op, "dhash") THEN
      res := Fmt.Int (M3ID.Hash (Int (a)));
    ELSIF Text.Equal (op, "dlt") THEN
      res := B (M3ID.IsLT (Int (a), Int (b)));
    ELSIF Text.Equal (op, "dadv") THEN
      M3ID.AdvanceMarks ();  res := "";
    ELSIF Text.Equal (op, "dmark") THEN
      res := B (M3ID.SetMark (Int (a)));
    ELSIF Text.Equal (op, "dput") THEN
      VAR m := M3Buf.New (); BEGIN
        M3ID.Put (m, Int (a));  res := HexText (M3Buf.ToText (m));
      END;

    ELSIF Text.Equal (op, "tinit") THEN
      ok := Target.Init (a, b, ParseMode (c));
      res := TargetText (ok);
    ELSE
      <*ASSERT FALSE*>
    END;
    Wr.PutText (out, line & " => " & res & "\n");
    Wr.Flush (out);
  END DoLine;

BEGIN
  TRY
    LOOP
      DoLine (Rd.GetLine (Stdio.stdin));
    END;
  EXCEPT
  | Rd.EndOfFile =>
  END;
END Main.
