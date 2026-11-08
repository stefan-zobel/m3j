(* Copyright (C) 2023, Peter McKinna *)
(* All rights reserved. *)
(* Licensed under the MIT license. *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: UnEncode decodes all the escapes of JSON (\uXXXX as
   UTF-8, \/ and \b too) instead of failing on some; Encode, the escapes
   of a text for JSON; Replace of a range over several lines by a text
   of one line replaces the whole range (it took the end of the range on
   the first line). *)

MODULE Utils;

IMPORT Text, Wr, Stdio, TextSeq, TextUtils;
IMPORT Debug, Fmt;

TYPE RefChar = REF ARRAY OF CHAR;

REVEAL
  Incr = IncrPublic BRANDED OBJECT
         OVERRIDES
           delete  := Delete;
           insert  := Insert;
           replace := Replace;
         END;

<*UNUSED*>
PROCEDURE DumpSeq(lab : TEXT; st : TextSeq.T) =
  VAR
    sa : REF ARRAY OF TEXT;
  BEGIN
    Debug.Write("\nDump" & lab & "\n");
    sa := TextUtils.TextSeqToArray(st);
    FOR i := 0 TO LAST(sa^) DO
      Debug.Write(Fmt.Int(i) & ">>" & sa[i] & "<<\n")
    END;
    Debug.Write("\nEnd Dump\n");
  END DumpSeq;

PROCEDURE Delete (<*UNUSED*>self: Incr; VAR st: TextSeq.T; sr, er: Range) =
  VAR
    t, t1, t2: TEXT;
    ts1, ts2 : TextSeq.T;
  BEGIN
    (* delete *)
    t := st.get(sr.line);
    IF sr.line = er.line THEN    (* single line *)
      t := Text.Sub(t, 0, sr.col) & Text.Sub(t, er.col);
      st.put(sr.line, t);
    ELSE                         (* multi line *)
      (* do the first and last lines then remove intervening lines via sub
         and cat *)
      (* DumpSeq("del start", st); *)
      t1 := st.get(sr.line);
      t2 := st.get(er.line);
      t := Text.Sub(t1, 0, sr.col) & Text.Sub(t2, er.col); (* till end of
                                                              txt *)
      st.put(sr.line, t);
      ts1 := TextSeq.Sub(st, 0, sr.line + 1);
      ts2 := TextSeq.Sub(st, er.line + 1); (* to end of seq *)
      st := TextSeq.Cat(ts1, ts2);
    END;
  END Delete;

PROCEDURE Insert
  (<*UNUSED*>self: Incr; VAR st: TextSeq.T; incrTxt: TEXT; sr: Range) =
  VAR
    t, t1, t2, t3    : TEXT;
    it, ts1, ts2, ts3, ts4: TextSeq.T;
    itLen            : INTEGER;
  BEGIN
    (* insert *)
    it := TextUtils.Split(incrTxt, "\n");
    itLen := it.size();
    IF itLen = 1 THEN
      (* single line *)
      t := st.get(sr.line);
      t := Text.Sub(t, 0, sr.col) & incrTxt & Text.Sub(t, sr.col);
      st.put(sr.line, t);
    ELSE
      (* multi line *)
      <* ASSERT itLen > 1 *>
      t1 := st.get(sr.line);
      t2 := it.get(0);
      (* sr line is first part of orig cat first line of incr *)
      t := Text.Sub(t1, 0, sr.col) & t2;
      it.put(0, t);

      (* change last line of it seq to have last part of sr line as
         suffix *)
      t3 := it.get(itLen - 1);
      t := t3 & Text.Sub(t1, sr.col);
      it.put(itLen - 1, t);

      (* then cat the sub seq 0 to srline of st, all of it then srline to
         end of st *)
      ts1 := TextSeq.Sub(st, 0, sr.line); (* first part of st *)
      ts2 := TextSeq.Sub(it, 0); (* increment *)
      ts3 := TextSeq.Sub(st, sr.line + 1); (* rest of st *)

      ts4 := TextSeq.Cat(ts1, ts2);
      st := TextSeq.Cat(ts4, ts3);
    END;
  END Insert;

PROCEDURE Replace
  (<*UNUSED*>self: Incr; VAR st: TextSeq.T; incrTxt: TEXT; sr, er: Range) =
  VAR
    t, t1, t2, t3    : TEXT;
    it, ts1, ts2, ts3: TextSeq.T;
    itLen            : INTEGER;
  BEGIN
    (* replace *)
    <* ASSERT sr # er *>
    it := TextUtils.Split(incrTxt, "\n");
    itLen := it.size();
    IF itLen = 1 AND sr.line = er.line THEN
      (* single line *)
      t := st.get(sr.line);
      t := Text.Sub(t, 0, sr.col) & incrTxt & Text.Sub(t, er.col);
      st.put(sr.line, t);
    ELSE
      (* multi line *)
      t1 := st.get(sr.line);
      t2 := it.get(0);
      (* sr line is first part of orig cat first line of incr *)
      t := Text.Sub(t1, 0, sr.col) & t2;
      it.put(0, t);

      (* change last line of it seq to have last part of er line as
         suffix *)
      t2 := st.get(er.line);
      t3 := it.get(itLen - 1);
      t := t3 & Text.Sub(t2, er.col);
      it.put(itLen - 1, t);

      ts1 := TextSeq.Sub(st, 0, sr.line); (* first part of st *)
      ts2 := TextSeq.Sub(it, 0); (* all of increment *)
      ts3 := TextSeq.Sub(st, er.line + 1); (* rest of st from er *)
      st := TextSeq.Cat(ts1, ts2);
      st := TextSeq.Cat(st, ts3);
    END;
  END Replace;

(* The json text of a module is escaped for special characters.  We need
   updated text to match the original file so that we can match the line
   and col positions of messages like hover.*)

PROCEDURE UnEncode (s: TEXT): TEXT =
  VAR
    len          : INTEGER;
    out          : RefChar;
    index, i, cp : INTEGER := 0;
    ch           : CHAR;

  PROCEDURE Out (ch: CHAR) =
    BEGIN
      out[index] := ch;
      INC(index);
    END Out;

  PROCEDURE Hex4 (at: INTEGER): INTEGER =
    (* the value of the four hexadecimal digits at "at", or -1 *)
    VAR v := 0;  d: INTEGER;  c: CHAR;
    BEGIN
      IF at + 4 > len THEN RETURN -1; END;
      FOR k := at TO at + 3 DO
        c := Text.GetChar(s, k);
        CASE c OF
        | '0' .. '9' => d := ORD(c) - ORD('0');
        | 'a' .. 'f' => d := ORD(c) - ORD('a') + 10;
        | 'A' .. 'F' => d := ORD(c) - ORD('A') + 10;
        ELSE
          RETURN -1;
        END;
        v := v * 16 + d;
      END;
      RETURN v;
    END Hex4;

  PROCEDURE OutUtf8 (cp: INTEGER) =
    BEGIN
      IF cp < 16_80 THEN
        Out(VAL(cp, CHAR));
      ELSIF cp < 16_800 THEN
        Out(VAL(16_C0 + cp DIV 64, CHAR));
        Out(VAL(16_80 + cp MOD 64, CHAR));
      ELSIF cp < 16_10000 THEN
        Out(VAL(16_E0 + cp DIV 4096, CHAR));
        Out(VAL(16_80 + (cp DIV 64) MOD 64, CHAR));
        Out(VAL(16_80 + cp MOD 64, CHAR));
      ELSE
        Out(VAL(16_F0 + cp DIV 262144, CHAR));
        Out(VAL(16_80 + (cp DIV 4096) MOD 64, CHAR));
        Out(VAL(16_80 + (cp DIV 64) MOD 64, CHAR));
        Out(VAL(16_80 + cp MOD 64, CHAR));
      END;
    END OutUtf8;

  BEGIN
    IF s = NIL THEN RETURN ""; END;
    len := Text.Length(s);
    IF len = 0 THEN RETURN ""; END;
    (* a character of UTF-8 takes at most 4 bytes, its escape 6 *)
    out := NEW(RefChar, len);
    WHILE i < len DO
      ch := Text.GetChar(s, i);
      INC(i);
      IF ch # '\\' OR i >= len THEN
        Out(ch);
      ELSE
        ch := Text.GetChar(s, i);
        INC(i);
        CASE ch OF
        | 'n' => Out('\n');
        | 't' => Out('\t');
        | 'f' => Out('\f');
        | 'r' => Out('\r');
        | 'b' => Out('\010');
        | '\"', '\\', '/' => Out(ch);
        | 'u' =>
            cp := Hex4(i);
            IF cp < 0 THEN
              Out('\\');
              Out('u');
            ELSE
              INC(i, 4);
              (* a surrogate pair is one character *)
              IF cp >= 16_D800 AND cp < 16_DC00 AND i + 6 <= len
                   AND Text.GetChar(s, i) = '\\'
                   AND Text.GetChar(s, i + 1) = 'u' THEN
                WITH lo = Hex4(i + 2) DO
                  IF lo >= 16_DC00 AND lo < 16_E000 THEN
                    cp := 16_10000 + (cp - 16_D800) * 1024 + (lo - 16_DC00);
                    INC(i, 6);
                  END;
                END;
              END;
              OutUtf8(cp);
            END;
        ELSE
          (* not an escape of JSON: as it is *)
          Out('\\');
          Out(ch);
        END;
      END;
    END;
    RETURN Text.FromChars(SUBARRAY(out^, 0, index));
  END UnEncode;

PROCEDURE Encode (s: TEXT): TEXT =
  CONST Hex = "0123456789abcdef";
  VAR
    len        := Text.Length(s);
    out        := NEW(RefChar, 6 * len);
    index      := 0;
    ch  : CHAR;

  PROCEDURE Out (ch: CHAR) =
    BEGIN
      out[index] := ch;
      INC(index);
    END Out;

  BEGIN
    FOR i := 0 TO len - 1 DO
      ch := Text.GetChar(s, i);
      CASE ch OF
      | '\"', '\\' => Out('\\'); Out(ch);
      | '\n' => Out('\\'); Out('n');
      | '\r' => Out('\\'); Out('r');
      | '\t' => Out('\\'); Out('t');
      | '\f' => Out('\\'); Out('f');
      | '\010' => Out('\\'); Out('b');
      | '\000' .. '\007', '\013', '\016' .. '\037' =>
          Out('\\'); Out('u'); Out('0'); Out('0');
          Out(Text.GetChar(Hex, ORD(ch) DIV 16));
          Out(Text.GetChar(Hex, ORD(ch) MOD 16));
      ELSE
        Out(ch);
      END;
    END;
    RETURN Text.FromChars(SUBARRAY(out^, 0, index));
  END Encode;

PROCEDURE Msg(msg : TEXT) =
  BEGIN
    Wr.PutText(Stdio.stderr, msg);
    Wr.Flush(Stdio.stderr);
  END Msg;

BEGIN
END Utils.

