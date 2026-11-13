(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Outline;

IMPORT Text;
IMPORT M3Context, M3CUnit, M3CId, M3CSrcPos;
IMPORT M3AST_AS, M3AST_AS_F, M3AST_LX_F, M3AST_FE_F, M3AST_SM_F;
IMPORT SeqM3AST_AS_DECL_REVL, SeqM3AST_AS_Const_decl,
       SeqM3AST_AS_TYPE_DECL, SeqM3AST_AS_Var_decl, SeqM3AST_AS_Var_id,
       SeqM3AST_AS_Exc_decl, SeqM3AST_AS_REVELATION, SeqM3AST_AS_Enum_id,
       SeqM3AST_AS_Fields, SeqM3AST_AS_Field_id, SeqM3AST_AS_Method,
       SeqM3AST_AS_Override;
IMPORT Feature;

CONST
  (* a detail longer than this many bytes gets no more tokens *)
  MaxDetail = 160;

TYPE
  (* the text that m3tk compiled *)
  Src = Source;

  (* the tokens of Modula-3 that the ends of declarations depend on *)
  Tok = {Eof, Ident, Semi, Colon, Assign, Eq, Open, Close, Other};

  Scanner = RECORD
    s         : Src;
    i         : INTEGER;                (* the next character *)
    kind      : Tok     := Tok.Eof;
    start, end: INTEGER := 0;           (* the token *)
  END;

  List = RECORD
    n: CARDINAL := 0;
    a: Symbols  := NIL;
  END;

  UnitClosure = M3Context.Closure OBJECT
                  file: TEXT;
                  cu  : M3AST_AS.Compilation_Unit := NIL;
                OVERRIDES
                  callback := FindUnit;
                END;

REVEAL
  Source = BRANDED REF RECORD
    a    : REF ARRAY OF CHAR;
    lines: REF ARRAY OF INTEGER;        (* the offset of each line *)
  END;

(* The text *)

PROCEDURE NewSrc (text: TEXT): Src =
  VAR
    s     := NEW(Src);
    n     := Text.Length(text);
    count := 1;
  BEGIN
    s.a := NEW(REF ARRAY OF CHAR, n);
    Text.SetChars(s.a^, text);
    FOR i := 0 TO n - 1 DO
      IF s.a[i] = '\n' THEN INC(count); END;
    END;
    s.lines := NEW(REF ARRAY OF INTEGER, count);
    s.lines[0] := 0;
    count := 1;
    FOR i := 0 TO n - 1 DO
      IF s.a[i] = '\n' THEN s.lines[count] := i + 1; INC(count); END;
    END;
    RETURN s;
  END NewSrc;

(* the offset of a position of m3tk (lines from 1), or -1 if it has
   none *)
PROCEDURE Offset (s: Src; READONLY p: M3CSrcPos.T): INTEGER =
  BEGIN
    IF p.line < 1 OR p.col < 0 THEN RETURN -1; END;
    IF p.line > NUMBER(s.lines^) THEN RETURN NUMBER(s.a^); END;
    RETURN MIN(s.lines[p.line - 1] + p.col, NUMBER(s.a^));
  END Offset;

PROCEDURE ToPos (s: Src; off: INTEGER): Pos =
  VAR lo := 0; hi := LAST(s.lines^); mid: INTEGER;
  BEGIN
    (* the last line that begins at or before off *)
    WHILE lo < hi DO
      mid := (lo + hi + 1) DIV 2;
      IF s.lines[mid] <= off THEN lo := mid; ELSE hi := mid - 1; END;
    END;
    RETURN Pos{line := lo, col := off - s.lines[lo]};
  END ToPos;

PROCEDURE PosOffset (s: Src; READONLY p: Pos): INTEGER =
  BEGIN
    RETURN s.lines[p.line] + p.col;
  END PosOffset;

PROCEDURE Less (READONLY a, b: Pos): BOOLEAN =
  BEGIN
    RETURN a.line < b.line OR a.line = b.line AND a.col < b.col;
  END Less;

(* The scanner *)

PROCEDURE IsLetter (c: CHAR): BOOLEAN =
  BEGIN
    RETURN 'a' <= c AND c <= 'z' OR 'A' <= c AND c <= 'Z';
  END IsLetter;

PROCEDURE IsDigit (c: CHAR): BOOLEAN =
  BEGIN
    RETURN '0' <= c AND c <= '9';
  END IsDigit;

(* after the comment that begins at i, comments nested *)
PROCEDURE SkipComment (READONLY a: ARRAY OF CHAR; i: INTEGER): INTEGER =
  VAR depth := 0; n := NUMBER(a);
  BEGIN
    WHILE i < n DO
      IF a[i] = '(' AND i + 1 < n AND a[i + 1] = '*' THEN
        INC(depth);
        INC(i, 2);
      ELSIF a[i] = '*' AND i + 1 < n AND a[i + 1] = ')' THEN
        DEC(depth);
        INC(i, 2);
        IF depth = 0 THEN RETURN i; END;
      ELSE
        INC(i);
      END;
    END;
    RETURN n;
  END SkipComment;

(* after the pragma that begins at i *)
PROCEDURE SkipPragma (READONLY a: ARRAY OF CHAR; i: INTEGER): INTEGER =
  VAR n := NUMBER(a);
  BEGIN
    INC(i, 2);
    WHILE i < n DO
      IF a[i] = '*' AND i + 1 < n AND a[i + 1] = '>' THEN RETURN i + 2; END;
      INC(i);
    END;
    RETURN n;
  END SkipPragma;

(* the next token, after blanks, comments and pragmas *)
PROCEDURE Next (VAR sc: Scanner) =
  VAR
    a          := sc.s.a;
    n          := NUMBER(a^);
    i          := sc.i;
    c    : CHAR;
  BEGIN
    LOOP
      IF i >= n THEN
        sc.kind := Tok.Eof;
        sc.start := n;
        sc.end := n;
        sc.i := n;
        RETURN;
      END;
      c := a[i];
      IF c = ' ' OR c = '\t' OR c = '\n' OR c = '\r' OR c = '\f' THEN
        INC(i);
      ELSIF c = '(' AND i + 1 < n AND a[i + 1] = '*' THEN
        i := SkipComment(a^, i);
      ELSIF c = '<' AND i + 1 < n AND a[i + 1] = '*' THEN
        i := SkipPragma(a^, i);
      ELSE
        EXIT;
      END;
    END;
    sc.start := i;
    IF IsLetter(c) OR IsDigit(c) THEN
      (* identifiers, reserved words and numbers; a number such as 1.5
         may become several tokens, which does not matter here *)
      WHILE i < n AND (IsLetter(a[i]) OR IsDigit(a[i]) OR a[i] = '_') DO
        INC(i);
      END;
      IF IsLetter(c) THEN sc.kind := Tok.Ident; ELSE sc.kind := Tok.Other; END;
    ELSIF c = '"' OR c = '\'' THEN
      INC(i);
      WHILE i < n AND a[i] # c AND a[i] # '\n' DO
        IF a[i] = '\\' AND i + 1 < n THEN INC(i); END;
        INC(i);
      END;
      IF i < n AND a[i] = c THEN INC(i); END;
      sc.kind := Tok.Other;
    ELSE
      INC(i);
      CASE c OF
      | ';' => sc.kind := Tok.Semi;
      | '(', '[', '{' => sc.kind := Tok.Open;
      | ')', ']', '}' => sc.kind := Tok.Close;
      | ':' =>
          IF i < n AND a[i] = '=' THEN
            INC(i);
            sc.kind := Tok.Assign;
          ELSE
            sc.kind := Tok.Colon;
          END;
      | '=' =>
          IF i < n AND a[i] = '>' THEN
            INC(i);
            sc.kind := Tok.Other;
          ELSE
            sc.kind := Tok.Eq;
          END;
      | '<', '>' =>
          (* <=, >=, <: *)
          IF i < n AND (a[i] = '=' OR a[i] = ':') THEN INC(i); END;
          sc.kind := Tok.Other;
      ELSE
        sc.kind := Tok.Other;
      END;
    END;
    sc.end := i;
    sc.i := i;
  END Next;

(* the token is the identifier or reserved word w *)
PROCEDURE Is (READONLY sc: Scanner; w: TEXT): BOOLEAN =
  VAR len := Text.Length(w);
  BEGIN
    IF sc.kind # Tok.Ident OR sc.end - sc.start # len THEN RETURN FALSE; END;
    FOR k := 0 TO len - 1 DO
      IF sc.s.a[sc.start + k] # Text.GetChar(w, k) THEN RETURN FALSE; END;
    END;
    RETURN TRUE;
  END Is;

(* a reserved word that cannot be inside a declaration but at a depth
   of brackets, so that it ends one before it, also after an error *)
PROCEDURE StopWord (READONLY sc: Scanner): BOOLEAN =
  BEGIN
    RETURN sc.kind = Tok.Ident
             AND (Is(sc, "METHODS") OR Is(sc, "OVERRIDES")
                    OR Is(sc, "BEGIN") OR Is(sc, "CONST") OR Is(sc, "TYPE")
                    OR Is(sc, "VAR") OR Is(sc, "EXCEPTION")
                    OR Is(sc, "REVEAL"));
  END StopWord;

(* the depth of brackets and of RECORD or OBJECT ... END after the token *)
PROCEDURE Nest (READONLY sc: Scanner; VAR depth: INTEGER) =
  BEGIN
    CASE sc.kind OF
    | Tok.Open => INC(depth);
    | Tok.Close => IF depth > 0 THEN DEC(depth); END;
    | Tok.Ident =>
        IF Is(sc, "RECORD") OR Is(sc, "OBJECT") THEN
          INC(depth);
        ELSIF Is(sc, "END") AND depth > 0 THEN
          DEC(depth);
        END;
    ELSE
    END;
  END Nest;

(* the end of the token at off *)
PROCEDURE TokenEnd (s: Src; off: INTEGER): INTEGER =
  VAR sc := Scanner{s := s, i := off};
  BEGIN
    Next(sc);
    RETURN sc.end;
  END TokenEnd;

(* the end of the declaration whose identifier is at off: after the
   first ";" outside of brackets, RECORD and OBJECT, or before an END,
   METHODS or OVERRIDES that ends the list it is in, or the end of the
   text *)
PROCEDURE DeclEnd (s: Src; off: INTEGER): INTEGER =
  VAR
    sc    := Scanner{s := s, i := off};
    depth := 0;
    last  := off;
  BEGIN
    LOOP
      Next(sc);
      IF sc.kind = Tok.Eof THEN RETURN last; END;
      IF depth = 0 THEN
        IF sc.kind = Tok.Semi THEN RETURN sc.end; END;
        IF Is(sc, "END") OR StopWord(sc) THEN RETURN last; END;
      END;
      Nest(sc, depth);
      last := sc.end;
    END;
  END DeclEnd;

(* the end of the procedure name whose body begins at or before off: after
   the ";" of the first END name from off on, or the end of the text *)
PROCEDURE ProcEnd (s: Src; off: INTEGER; name: TEXT): INTEGER =
  VAR
    sc            := Scanner{s := s, i := off};
    afterEnd      := FALSE;
    nameEnd       := -1;
  BEGIN
    LOOP
      Next(sc);
      IF nameEnd >= 0 THEN
        IF sc.kind = Tok.Semi THEN RETURN sc.end; END;
        RETURN nameEnd;
      END;
      IF sc.kind = Tok.Eof THEN RETURN NUMBER(s.a^); END;
      IF afterEnd AND Is(sc, name) THEN
        nameEnd := sc.end;
      ELSE
        afterEnd := Is(sc, "END");
      END;
    END;
  END ProcEnd;

(* The text of a signature or a type, from the tokens that follow the
   identifier at off: all of them, or (afterColon) those after the first
   ":"; up to a ";", ":=", END, a reserved word of StopWord, (atEq) an
   "=" outside of brackets, or a closing bracket that has no opening one
   (after the last formal parameter). NIL if there are none. *)
PROCEDURE Detail (s: Src; off: INTEGER; afterColon, atEq: BOOLEAN): TEXT =
  VAR
    sc            := Scanner{s := s, i := off};
    depth         := 0;
    started       := NOT afterColon;
    skip: BOOLEAN;
    res : TEXT    := "";
    prev          := -1;
  BEGIN
    Next(sc);                    (* the identifier *)
    LOOP
      Next(sc);
      IF sc.kind = Tok.Eof THEN EXIT; END;
      IF depth = 0 THEN
        IF sc.kind = Tok.Semi OR sc.kind = Tok.Assign
             OR atEq AND sc.kind = Tok.Eq OR Is(sc, "END")
             OR StopWord(sc) OR sc.kind = Tok.Close THEN
          EXIT;
        END;
      END;
      skip := NOT started;
      IF NOT started AND depth = 0 AND sc.kind = Tok.Colon THEN
        started := TRUE;
      END;
      Nest(sc, depth);
      IF NOT skip AND Text.Length(res) <= MaxDetail THEN
        IF prev >= 0 AND sc.start > prev THEN res := res & " "; END;
        res := res & Text.FromChars(SUBARRAY(s.a^, sc.start, sc.end - sc.start));
        prev := sc.end;
      END;
    END;
    IF Text.Length(res) = 0 THEN RETURN NIL; END;
    IF Text.Length(res) > MaxDetail THEN res := res & " ..."; END;
    RETURN res;
  END Detail;

(* The symbols *)

PROCEDURE Add (VAR l: List; sym: Symbol) =
  VAR new: Symbols;
  BEGIN
    IF sym = NIL THEN RETURN; END;
    IF l.a = NIL THEN
      l.a := NEW(Symbols, 8);
    ELSIF l.n = NUMBER(l.a^) THEN
      new := NEW(Symbols, 2 * l.n);
      SUBARRAY(new^, 0, l.n) := l.a^;
      l.a := new;
    END;
    l.a[l.n] := sym;
    INC(l.n);
  END Add;

(* the symbols of l, or NIL if there are none *)
PROCEDURE Children (READONLY l: List): Symbols =
  VAR res: Symbols;
  BEGIN
    IF l.n = 0 THEN RETURN NIL; END;
    res := NEW(Symbols, l.n);
    res^ := SUBARRAY(l.a^, 0, l.n);
    RETURN res;
  END Children;

PROCEDURE Name (id: M3AST_AS.ID): TEXT =
  BEGIN
    IF id = NIL OR id.lx_symrep = NIL THEN RETURN NIL; END;
    RETURN M3CId.ToText(id.lx_symrep);
  END Name;

(* a symbol whose identifier is from selStart to selEnd and whose
   declaration begins at node, if node has a position before it; NIL if
   it has no name or position *)
PROCEDURE Make (s       : Src;
                name    : TEXT;
                selStart: INTEGER;
                node    : M3AST_AS.SRC_NODE;
                kind    : INTEGER           ): Symbol =
  VAR
    sym  : Symbol;
    start           := selStart;
    nodeOff: INTEGER;
  BEGIN
    IF name = NIL OR selStart < 0 THEN RETURN NIL; END;
    IF node # NIL THEN
      nodeOff := Offset(s, node.lx_srcpos);
      IF nodeOff >= 0 AND nodeOff < start THEN start := nodeOff; END;
    END;
    sym := NEW(Symbol, name := name, kind := kind);
    sym.selection.start := ToPos(s, selStart);
    sym.selection.end := ToPos(s, TokenEnd(s, selStart));
    sym.range.start := ToPos(s, start);
    sym.range.end := sym.selection.end;
    RETURN sym;
  END Make;

(* the range of sym ends at off, and holds its identifier and children *)
PROCEDURE Finish (s: Src; sym: Symbol; off: INTEGER) =
  VAR end := ToPos(s, off);
  BEGIN
    IF Less(sym.range.end, end) THEN sym.range.end := end; END;
    IF sym.children # NIL THEN
      FOR i := 0 TO LAST(sym.children^) DO
        WITH c = sym.children[i] DO
          IF Less(c.range.start, sym.range.start) THEN
            sym.range.start := c.range.start;
          END;
          IF Less(sym.range.end, c.range.end) THEN
            sym.range.end := c.range.end;
          END;
        END;
      END;
    END;
  END Finish;

(* a declaration of one identifier id, in node, that ends at its ";" *)
PROCEDURE Simple (s         : Src;
                  id        : M3AST_AS.ID;
                  node      : M3AST_AS.SRC_NODE;
                  kind      : INTEGER;
                  afterColon: BOOLEAN;
                  atEq      : BOOLEAN            ): Symbol =
  VAR
    off: INTEGER;
    sym: Symbol;
  BEGIN
    IF id = NIL THEN RETURN NIL; END;
    off := Offset(s, id.lx_srcpos);
    sym := Make(s, Name(id), off, node, kind);
    IF sym = NIL THEN RETURN NIL; END;
    sym.detail := Detail(s, off, afterColon, atEq);
    Finish(s, sym, DeclEnd(s, off));
    RETURN sym;
  END Simple;

PROCEDURE FieldList (s: Src; fs: SeqM3AST_AS_Fields.T; VAR l: List) =
  VAR
    iter := SeqM3AST_AS_Fields.NewIter(fs);
    f    : M3AST_AS.Fields;
    ids  : SeqM3AST_AS_Field_id.Iter;
    id   : M3AST_AS.Field_id;
  BEGIN
    WHILE SeqM3AST_AS_Fields.Next(iter, f) DO
      IF f # NIL THEN
        ids := SeqM3AST_AS_Field_id.NewIter(f.as_id_s);
        WHILE SeqM3AST_AS_Field_id.Next(ids, id) DO
          Add(l, Simple(s, id, f, Field, afterColon := TRUE, atEq := FALSE));
        END;
      END;
    END;
  END FieldList;

PROCEDURE Members (s: Src; o: M3AST_AS.Object_type; VAR l: List) =
  VAR
    methods   := SeqM3AST_AS_Method.NewIter(o.as_method_s);
    overrides := SeqM3AST_AS_Override.NewIter(o.as_override_s);
    m         : M3AST_AS.Method;
    ov        : M3AST_AS.Override;
    sym       : Symbol;
  BEGIN
    FieldList(s, o.as_fields_s, l);
    WHILE SeqM3AST_AS_Method.Next(methods, m) DO
      IF m # NIL THEN
        Add(l, Simple(s, m.as_id, m, Method, afterColon := FALSE,
                      atEq := FALSE));
      END;
    END;
    WHILE SeqM3AST_AS_Override.Next(overrides, ov) DO
      IF ov # NIL THEN
        sym := Simple(s, ov.as_id, ov, Method, afterColon := FALSE,
                      atEq := FALSE);
        IF sym # NIL THEN sym.detail := "OVERRIDES"; END;
        Add(l, sym);
      END;
    END;
  END Members;

(* the kind of a type and the symbols inside it; opaque: the type is
   that of a declaration T <: U *)
PROCEDURE TypeKind (s     : Src;
                    type  : M3AST_AS.M3TYPE;
                    opaque: BOOLEAN;
                    VAR l : List             ): INTEGER =
  VAR ids: SeqM3AST_AS_Enum_id.Iter; e: M3AST_AS.Enum_id;
  BEGIN
    TYPECASE type OF
    | NULL =>
    | M3AST_AS.Opaque_type (o) => RETURN TypeKind(s, o.as_type, TRUE, l);
    | M3AST_AS.Object_type (o) => Members(s, o, l); RETURN Class;
    | M3AST_AS.Record_type (r) => FieldList(s, r.as_fields_s, l); RETURN Struct;
    | M3AST_AS.Ref_type (r) =>
        TYPECASE r.as_type OF
        | NULL =>
        | M3AST_AS.Record_type (rec) =>
            FieldList(s, rec.as_fields_s, l);
            RETURN Struct;
        ELSE
        END;
    | M3AST_AS.Enumeration_type (en) =>
        ids := SeqM3AST_AS_Enum_id.NewIter(en.as_id_s);
        WHILE SeqM3AST_AS_Enum_id.Next(ids, e) DO
          IF e # NIL THEN
            Add(l, Make(s, Name(e), Offset(s, e.lx_srcpos), NIL, EnumMember));
          END;
        END;
        RETURN Enum;
    ELSE
    END;
    IF opaque THEN RETURN Class; END;
    RETURN TypeParam;
  END TypeKind;

PROCEDURE TypeDecl (s: Src; t: M3AST_AS.TYPE_DECL): Symbol =
  VAR
    l   : List;
    kind: INTEGER;
    off : INTEGER;
    sym : Symbol;
  BEGIN
    IF t.as_id = NIL THEN RETURN NIL; END;
    off := Offset(s, t.as_id.lx_srcpos);
    kind := TypeKind(s, t.as_type, ISTYPE(t, M3AST_AS.Subtype_decl), l);
    sym := Make(s, Name(t.as_id), off, t, kind);
    IF sym = NIL THEN RETURN NIL; END;
    sym.children := Children(l);
    Finish(s, sym, DeclEnd(s, off));
    RETURN sym;
  END TypeDecl;

PROCEDURE Reveal (s: Src; r: M3AST_AS.REVELATION): Symbol =
  VAR
    q    := r.as_qual_id;
    l    : List;
    kind : INTEGER;
    name : TEXT;
    off  : INTEGER;
    sym  : Symbol;
  BEGIN
    IF q = NIL OR q.as_id = NIL THEN RETURN NIL; END;
    name := Name(q.as_id);
    off := Offset(s, q.as_id.lx_srcpos);
    IF q.as_intf_id # NIL AND name # NIL THEN
      name := Name(q.as_intf_id) & "." & name;
      off := Offset(s, q.as_intf_id.lx_srcpos);
    END;
    kind := TypeKind(s, r.as_type, TRUE, l);
    sym := Make(s, name, off, r, kind);
    IF sym = NIL THEN RETURN NIL; END;
    (* the selection is the whole name I.T *)
    sym.selection.end := ToPos(s, TokenEnd(s, Offset(s, q.as_id.lx_srcpos)));
    sym.detail := "REVEAL";
    sym.children := Children(l);
    Finish(s, sym, DeclEnd(s, off));
    RETURN sym;
  END Reveal;

PROCEDURE Proc (s: Src; p: M3AST_AS.Proc_decl): Symbol =
  VAR
    l   : List;
    off : INTEGER;
    from: INTEGER;
    sym : Symbol;
  BEGIN
    IF p.as_id = NIL THEN RETURN NIL; END;
    off := Offset(s, p.as_id.lx_srcpos);
    sym := Make(s, Name(p.as_id), off, p, Function);
    IF sym = NIL THEN RETURN NIL; END;
    sym.detail := Detail(s, off, afterColon := FALSE, atEq := TRUE);
    IF p.as_body = NIL THEN
      Finish(s, sym, DeclEnd(s, off));
    ELSE
      Decls(s, p.as_body.as_decl_s, TRUE, l);
      sym.children := Children(l);
      (* the END of the procedure comes after its nested procedures,
         which may have its name *)
      from := TokenEnd(s, off);
      IF l.n > 0 THEN
        from := MAX(from, PosOffset(s, l.a[l.n - 1].range.end));
      END;
      Finish(s, sym, ProcEnd(s, from, sym.name));
    END;
    RETURN sym;
  END Proc;

(* the symbols of the declarations ds; procsOnly: only the procedures,
   in the body of a procedure *)
PROCEDURE Decls (s        : Src;
                 ds       : SeqM3AST_AS_DECL_REVL.T;
                 procsOnly: BOOLEAN;
                 VAR l    : List                    ) =
  VAR
    iter := SeqM3AST_AS_DECL_REVL.NewIter(ds);
    d    : M3AST_AS.DECL_REVL;
  BEGIN
    WHILE SeqM3AST_AS_DECL_REVL.Next(iter, d) DO
      TYPECASE d OF
      | NULL =>
      | M3AST_AS.Proc_decl (p) => Add(l, Proc(s, p));
      ELSE
        IF NOT procsOnly THEN Decl(s, d, l); END;
      END;
    END;
  END Decls;

PROCEDURE Decl (s: Src; d: M3AST_AS.DECL_REVL; VAR l: List) =
  BEGIN
    TYPECASE d OF
    | NULL =>
    | M3AST_AS.Const_decl_s (cs) =>
        VAR
          iter := SeqM3AST_AS_Const_decl.NewIter(cs.as_const_decl_s);
          c    : M3AST_AS.Const_decl;
        BEGIN
          WHILE SeqM3AST_AS_Const_decl.Next(iter, c) DO
            IF c # NIL THEN
              Add(l, Simple(s, c.as_id, c, Constant, afterColon := TRUE,
                            atEq := TRUE));
            END;
          END;
        END;
    | M3AST_AS.Type_decl_s (ts) =>
        VAR
          iter := SeqM3AST_AS_TYPE_DECL.NewIter(ts.as_type_decl_s);
          t    : M3AST_AS.TYPE_DECL;
        BEGIN
          WHILE SeqM3AST_AS_TYPE_DECL.Next(iter, t) DO
            IF t # NIL THEN Add(l, TypeDecl(s, t)); END;
          END;
        END;
    | M3AST_AS.Var_decl_s (vs) =>
        VAR
          iter := SeqM3AST_AS_Var_decl.NewIter(vs.as_var_decl_s);
          v    : M3AST_AS.Var_decl;
          ids  : SeqM3AST_AS_Var_id.Iter;
          id   : M3AST_AS.Var_id;
        BEGIN
          WHILE SeqM3AST_AS_Var_decl.Next(iter, v) DO
            IF v # NIL THEN
              ids := SeqM3AST_AS_Var_id.NewIter(v.as_id_s);
              WHILE SeqM3AST_AS_Var_id.Next(ids, id) DO
                Add(l, Simple(s, id, v, Variable, afterColon := TRUE,
                              atEq := FALSE));
              END;
            END;
          END;
        END;
    | M3AST_AS.Exc_decl_s (es) =>
        VAR
          iter := SeqM3AST_AS_Exc_decl.NewIter(es.as_exc_decl_s);
          e    : M3AST_AS.Exc_decl;
        BEGIN
          WHILE SeqM3AST_AS_Exc_decl.Next(iter, e) DO
            IF e # NIL THEN
              Add(l, Simple(s, e.as_id, e, Event, afterColon := FALSE,
                            atEq := FALSE));
            END;
          END;
        END;
    | M3AST_AS.Revelation_s (rs) =>
        VAR
          iter := SeqM3AST_AS_REVELATION.NewIter(rs.as_reveal_s);
          r    : M3AST_AS.REVELATION;
        BEGIN
          WHILE SeqM3AST_AS_REVELATION.Next(iter, r) DO
            IF r # NIL THEN Add(l, Reveal(s, r)); END;
          END;
        END;
    ELSE
    END;
  END Decl;

(* The unit *)

PROCEDURE FindUnit (           cl  : UnitClosure;
                    <*UNUSED*> ut  : M3CUnit.Type;
                    <*UNUSED*> name: TEXT;
                               cu  : M3AST_AS.Compilation_Unit) =
  BEGIN
    IF cl.cu = NIL
         AND Feature.SameFile(M3CUnit.TextName(cu.fe_uid), cl.file) THEN
      cl.cu := cu;
    END;
  END FindUnit;

PROCEDURE Unit (c: M3Context.T; file: TEXT): M3AST_AS.Compilation_Unit =
  <* FATAL ANY *>
  VAR cl := NEW(UnitClosure, file := file);
  BEGIN
    M3Context.Apply(c, cl, findStandard := FALSE);
    RETURN cl.cu;
  END Unit;

PROCEDURE OfUnit (cu: M3AST_AS.Compilation_Unit; text: TEXT): Symbols =
  VAR
    s  : Src;
    l  : List;
    res: Symbols;
  BEGIN
    s := NewSrc(text);
    TYPECASE cu.as_root OF
    | NULL =>
    | M3AST_AS.UNIT_WITH_BODY (u) =>
        IF u.as_block # NIL THEN Decls(s, u.as_block.as_decl_s, FALSE, l); END;
    ELSE
    END;
    res := Children(l);
    IF res = NIL THEN res := NEW(Symbols, 0); END;
    RETURN res;
  END OfUnit;

PROCEDURE Get (c: M3Context.T; file, text: TEXT): Symbols =
  VAR cu := Unit(c, file);
  BEGIN
    IF cu = NIL THEN RETURN NIL; END;
    RETURN OfUnit(cu, text);
  END Get;

(* Code *)

(* the comment or pragma that begins at i and ends before j is closed by
   the characters x and y *)
PROCEDURE Closed (READONLY a: ARRAY OF CHAR; i, j: INTEGER; x, y: CHAR):
  BOOLEAN =
  BEGIN
    RETURN j - 2 >= i + 2 AND a[j - 2] = x AND a[j - 1] = y;
  END Closed;

PROCEDURE InCode (text: TEXT; offset: INTEGER): BOOLEAN =
  VAR
    n             := Text.Length(text);
    a             := NEW(REF ARRAY OF CHAR, n);
    i             := 0;
    j   : INTEGER;
    c   : CHAR;
    closed: BOOLEAN;
  BEGIN
    Text.SetChars(a^, text);
    WHILE i < offset AND i < n DO
      c := a[i];
      IF c = '(' AND i + 1 < n AND a[i + 1] = '*' THEN
        j := SkipComment(a^, i);
        IF offset < j OR NOT Closed(a^, i, j, '*', ')') THEN RETURN FALSE; END;
        i := j;
      ELSIF c = '<' AND i + 1 < n AND a[i + 1] = '*' THEN
        j := SkipPragma(a^, i);
        IF offset < j OR NOT Closed(a^, i, j, '*', '>') THEN RETURN FALSE; END;
        i := j;
      ELSIF c = '"' OR c = '\'' THEN
        (* a text or a character ends at its quote or at the end of the
           line; the cursor at the end of one that is not closed is in it *)
        j := i + 1;
        closed := FALSE;
        WHILE NOT closed AND j < n AND a[j] # '\n' DO
          IF a[j] = '\\' AND j + 1 < n THEN
            INC(j, 2);
          ELSE
            closed := a[j] = c;
            INC(j);
          END;
        END;
        IF offset < j OR NOT closed AND offset = j THEN RETURN FALSE; END;
        i := j;
      ELSIF IsLetter(c) OR IsDigit(c) THEN
        WHILE i < n AND (IsLetter(a[i]) OR IsDigit(a[i]) OR a[i] = '_') DO
          INC(i);
        END;
      ELSE
        INC(i);
      END;
    END;
    RETURN TRUE;
  END InCode;

(* Completion *)

PROCEDURE OfType (type: M3AST_AS.M3TYPE; text: TEXT): Symbols =
  VAR
    l  : List;
    res: Symbols;
  BEGIN
    EVAL TypeKind(NewSrc(text), type, FALSE, l);
    res := Children(l);
    IF res = NIL THEN res := NEW(Symbols, 0); END;
    RETURN res;
  END OfType;

PROCEDURE NewSource (text: TEXT): Source =
  BEGIN
    RETURN NewSrc(text);
  END NewSource;

PROCEDURE SourceOffset (s: Source; READONLY p: M3CSrcPos.T): INTEGER =
  BEGIN
    RETURN Offset(s, p);
  END SourceOffset;

(* the type construction that the type m3type denotes, or NIL *)
PROCEDURE Spec (m3type: M3AST_AS.M3TYPE): M3AST_AS.M3TYPE =
  BEGIN
    TYPECASE m3type OF
    | NULL => RETURN NIL;
    | M3AST_AS.Named_type (n) => RETURN n.sm_type_spec;
    ELSE
      RETURN m3type;
    END;
  END Spec;

(* the kind of symbol of a type with the construction ts *)
PROCEDURE SpecKind (ts: M3AST_AS.M3TYPE): INTEGER =
  BEGIN
    TYPECASE ts OF
    | NULL =>
    | M3AST_AS.Object_type, M3AST_AS.Opaque_type => RETURN Class;
    | M3AST_AS.Record_type => RETURN Struct;
    | M3AST_AS.Enumeration_type => RETURN Enum;
    | M3AST_AS.Ref_type (r) =>
        IF ISTYPE(Spec(r.as_type), M3AST_AS.Record_type) THEN
          RETURN Struct;
        END;
    ELSE
    END;
    RETURN TypeParam;
  END SpecKind;

PROCEDURE OfId (s: Source; id: M3AST_AS.DEF_ID): Symbol =
  VAR
    off       : INTEGER;
    sym       : Symbol;
    kind                := Variable;
    afterColon          := TRUE;
    atEq                := FALSE;
    detail              := TRUE;
  BEGIN
    IF id = NIL THEN RETURN NIL; END;
    off := Offset(s, id.lx_srcpos);
    TYPECASE id OF
    | M3AST_AS.Proc_id =>
        kind := Function;
        afterColon := FALSE;
        atEq := TRUE;
    | M3AST_AS.Const_id => kind := Constant; atEq := TRUE;
    | M3AST_AS.Type_id (t) => kind := SpecKind(t.sm_type_spec); detail := FALSE;
    | M3AST_AS.Exc_id => kind := Event; afterColon := FALSE;
    | M3AST_AS.With_id, M3AST_AS.For_id, M3AST_AS.Tcase_id,
      M3AST_AS.Handler_id =>
        detail := FALSE;
    ELSE
    END;
    sym := Make(s, Name(id), off, NIL, kind);
    IF sym # NIL AND detail THEN
      sym.detail := Detail(s, off, afterColon, atEq);
    END;
    RETURN sym;
  END OfId;

(* the token is one of the reserved words that begin something that ends
   with END (or UNTIL), and is a statement or a body (stmt), or a type *)
PROCEDURE Opens (READONLY sc: Scanner; VAR stmt: BOOLEAN): BOOLEAN =
  BEGIN
    IF sc.kind # Tok.Ident THEN RETURN FALSE; END;
    stmt := TRUE;
    IF Is(sc, "IF") OR Is(sc, "WHILE") OR Is(sc, "FOR") OR Is(sc, "WITH")
         OR Is(sc, "LOOP") OR Is(sc, "LOCK") OR Is(sc, "CASE")
         OR Is(sc, "TYPECASE") OR Is(sc, "TRY") OR Is(sc, "BEGIN")
         OR Is(sc, "REPEAT") THEN
      RETURN TRUE;
    END;
    stmt := FALSE;
    RETURN Is(sc, "RECORD") OR Is(sc, "OBJECT");
  END Opens;

(* the token closes what Opens opened *)
PROCEDURE Closes (READONLY sc: Scanner): BOOLEAN =
  BEGIN
    RETURN Is(sc, "END") OR Is(sc, "UNTIL");
  END Closes;

(* the token is the character c *)
PROCEDURE IsChar (READONLY sc: Scanner; c: CHAR): BOOLEAN =
  BEGIN
    RETURN sc.end - sc.start = 1 AND sc.s.a[sc.start] = c;
  END IsChar;

(* after the END of the statement or block statement at off: the first
   END (or UNTIL) that closes a statement or body opened at the depth of
   the start; the RECORD and OBJECT types of declarations before it are
   skipped *)
PROCEDURE StmtEnd (s: Src; off: INTEGER): INTEGER =
  CONST Max = 256;
  VAR
    sc    := Scanner{s := s, i := off};
    depth := 0;
    kinds : ARRAY [0 .. Max - 1] OF BOOLEAN;  (* stmt of each depth *)
    stmt  : BOOLEAN;
  BEGIN
    LOOP
      Next(sc);
      IF sc.kind = Tok.Eof THEN RETURN NUMBER(s.a^); END;
      IF Opens(sc, stmt) THEN
        IF depth < Max THEN kinds[depth] := stmt; END;
        INC(depth);
      ELSIF Closes(sc) THEN
        IF depth = 0 THEN RETURN sc.start; END;
        DEC(depth);
        IF depth = 0 AND kinds[0] THEN
          RETURN sc.end;
        END;
      END;
    END;
  END StmtEnd;

(* before the "|", ELSE or END after the arm at off of a TYPECASE or of
   the EXCEPT of a TRY *)
PROCEDURE ArmEnd (s: Src; off: INTEGER): INTEGER =
  VAR
    sc    := Scanner{s := s, i := off};
    depth := 0;
    stmt  : BOOLEAN;
  BEGIN
    LOOP
      Next(sc);
      IF sc.kind = Tok.Eof THEN RETURN NUMBER(s.a^); END;
      IF depth = 0 AND (IsChar(sc, '|') OR Is(sc, "ELSE") OR Closes(sc)) THEN
        RETURN sc.start;
      END;
      IF Opens(sc, stmt) OR sc.kind = Tok.Open THEN
        INC(depth);
      ELSIF (Closes(sc) OR sc.kind = Tok.Close) AND depth > 0 THEN
        DEC(depth);
      END;
    END;
  END ArmEnd;

(* before the "," or DO after the binding of WITH at off *)
PROCEDURE BindingEnd (s: Src; off: INTEGER): INTEGER =
  VAR
    sc    := Scanner{s := s, i := off};
    depth := 0;
    stmt  : BOOLEAN;
  BEGIN
    LOOP
      Next(sc);
      IF sc.kind = Tok.Eof THEN RETURN NUMBER(s.a^); END;
      IF depth = 0 AND (IsChar(sc, ',') OR Is(sc, "DO")) THEN
        RETURN sc.start;
      END;
      IF Opens(sc, stmt) OR sc.kind = Tok.Open THEN
        INC(depth);
      ELSIF (Closes(sc) OR sc.kind = Tok.Close) AND depth > 0 THEN
        DEC(depth);
      END;
    END;
  END BindingEnd;

PROCEDURE ScopeEnd (s: Source; node: M3AST_AS.SRC_NODE): INTEGER =
  VAR off: INTEGER;
  BEGIN
    IF node = NIL THEN RETURN -1; END;
    off := Offset(s, node.lx_srcpos);
    IF off < 0 THEN RETURN -1; END;
    TYPECASE node OF
    | M3AST_AS.Proc_decl (p) =>
        IF p.as_id = NIL OR Name(p.as_id) = NIL THEN RETURN -1; END;
        off := Offset(s, p.as_id.lx_srcpos);
        IF off < 0 THEN RETURN -1; END;
        RETURN ProcEnd(s, off, Name(p.as_id));
    | M3AST_AS.Tcase, M3AST_AS.Handler => RETURN ArmEnd(s, off);
    | M3AST_AS.Binding => RETURN BindingEnd(s, off);
    ELSE
      RETURN StmtEnd(s, off);
    END;
  END ScopeEnd;

BEGIN
END Outline.
