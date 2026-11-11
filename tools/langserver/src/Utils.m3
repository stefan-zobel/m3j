(* Copyright (C) 2023, Peter McKinna *)
(* All rights reserved. *)
(* Licensed under the MIT license. *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: no UnEncode, since JsonReader decodes the strings of
   a message; Replace of a range over several lines by a text of one
   line replaces the whole range (it took the end of the range on the
   first line). *)

MODULE Utils;

IMPORT Text, Wr, Stdio, TextSeq, TextUtils;
IMPORT Debug, Fmt;

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

PROCEDURE Msg(msg : TEXT) =
  BEGIN
    Wr.PutText(Stdio.stderr, msg);
    Wr.Flush(Stdio.stderr);
  END Msg;

BEGIN
END Utils.

