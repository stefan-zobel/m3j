(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Complete;

IMPORT Rd, FileRd, OSError, Thread, TextRefTbl;
IMPORT M3Context, M3CUnit, M3CId;
IMPORT M3AST_AS, M3AST_AS_F, M3AST_LX_F, M3AST_FE_F;
IMPORT SeqM3AST_AS_IMPORTED, SeqM3AST_AS_Import_item,
       SeqM3AST_AS_Used_def_id, SeqM3AST_AS_Used_interface_id,
       SeqM3AST_AS_F_Interface_id;
IMPORT Outline;
IMPORT Text;

TYPE
  List = RECORD
    n   : CARDINAL      := 0;
    a   : Items         := NIL;
    seen: TextRefTbl.T  := NIL;         (* the labels in a *)
  END;

  (* an interface that the unit imports, by the name it has in the unit *)
  Import = RECORD name, intf: TEXT; END;
  Imports = REF ARRAY OF Import;

CONST
  (* the predeclared identifiers that a safe unit can use *)
  Types = ARRAY OF
            TEXT{"ADDRESS", "BOOLEAN", "CARDINAL", "CHAR", "EXTENDED",
                 "INTEGER", "LONGCARD", "LONGINT", "LONGREAL", "MUTEX",
                 "NULL", "REAL", "REFANY", "ROOT", "TEXT", "WIDECHAR"};
  Constants = ARRAY OF TEXT{"FALSE", "NIL", "TRUE"};
  Procedures = ARRAY OF
                 TEXT{"ABS", "ADRSIZE", "BITSIZE", "BYTESIZE", "CEILING",
                      "DEC", "FIRST", "FLOAT", "FLOOR", "INC", "ISTYPE",
                      "LAST", "MAX", "MIN", "NARROW", "NEW", "NUMBER",
                      "ORD", "ROUND", "SUBARRAY", "TRUNC", "TYPECODE",
                      "VAL"};

  (* the reserved words, but UNSAFE *)
  Keywords = ARRAY OF
               TEXT{"AND", "ANY", "ARRAY", "AS", "BEGIN", "BITS",
                    "BRANDED", "BY", "CASE", "CONST", "DIV", "DO", "ELSE",
                    "ELSIF", "END", "EVAL", "EXCEPT", "EXCEPTION", "EXIT",
                    "EXPORTS", "FINALLY", "FOR", "FROM", "GENERIC", "IF",
                    "IMPORT", "IN", "INTERFACE", "LOCK", "LOOP", "METHODS",
                    "MOD", "MODULE", "NOT", "OBJECT", "OF", "OR",
                    "OVERRIDES", "PROCEDURE", "RAISE", "RAISES",
                    "READONLY", "RECORD", "REF", "REPEAT", "RETURN",
                    "REVEAL", "SET", "THEN", "TO", "TRY", "TYPE",
                    "TYPECASE", "UNTIL", "UNTRACED", "VALUE", "VAR",
                    "WHILE", "WITH"};

VAR
  (* the declarations of each file (its pathname) in the last unit of it
     that m3tk parsed without errors: after an error the parser of m3tk
     drops the declarations that follow it *)
  parsed := NEW(TextRefTbl.Default).init(20);

(* The text *)

PROCEDURE IsLetter (c: CHAR): BOOLEAN =
  BEGIN
    RETURN 'a' <= c AND c <= 'z' OR 'A' <= c AND c <= 'Z';
  END IsLetter;

PROCEDURE IsDigit (c: CHAR): BOOLEAN =
  BEGIN
    RETURN '0' <= c AND c <= '9';
  END IsDigit;

PROCEDURE IsIdChar (c: CHAR): BOOLEAN =
  BEGIN
    RETURN IsLetter(c) OR IsDigit(c) OR c = '_';
  END IsIdChar;

PROCEDURE IsBlank (c: CHAR): BOOLEAN =
  BEGIN
    RETURN c = ' ' OR c = '\t' OR c = '\n' OR c = '\r' OR c = '\f';
  END IsBlank;

(* the offset of line and col in a, at most the end of the line *)
PROCEDURE Offset (READONLY a: ARRAY OF CHAR; line, col: INTEGER): INTEGER =
  VAR i := 0; n := NUMBER(a);
  BEGIN
    WHILE line > 0 AND i < n DO
      IF a[i] = '\n' THEN DEC(line); END;
      INC(i);
    END;
    WHILE col > 0 AND i < n AND a[i] # '\n' DO DEC(col); INC(i); END;
    RETURN i;
  END Offset;

(* the text of the file of a unit: of the editor, else of the file, else
   empty *)
PROCEDURE FileText (file: TEXT; texts: Texts): TEXT =
  VAR
    text: TEXT := NIL;
    rd  : Rd.T;
  BEGIN
    IF texts # NIL THEN text := texts.get(file); END;
    IF text # NIL THEN RETURN text; END;
    TRY
      rd := FileRd.Open(file);
      TRY
        text := Rd.GetText(rd, LAST(CARDINAL));
      FINALLY
        Rd.Close(rd);
      END;
    EXCEPT
    | OSError.E, Rd.Failure, Thread.Alerted => text := "";
    END;
    RETURN text;
  END FileText;

(* The items *)

PROCEDURE Add (VAR l   : List;
                   label: TEXT;
                   detail: TEXT;
                   kind : INTEGER;
                   sort : TEXT     ) =
  VAR
    new: Items;
    ref: REFANY;
  BEGIN
    IF label = NIL THEN RETURN; END;
    IF l.seen = NIL THEN l.seen := NEW(TextRefTbl.Default).init(64); END;
    IF l.seen.get(label, ref) THEN RETURN; END;
    EVAL l.seen.put(label, NIL);
    IF l.a = NIL THEN
      l.a := NEW(Items, 64);
    ELSIF l.n = NUMBER(l.a^) THEN
      new := NEW(Items, 2 * l.n);
      SUBARRAY(new^, 0, l.n) := l.a^;
      l.a := new;
    END;
    l.a[l.n] := Item{label := label, detail := detail, kind := kind,
                     sort := sort};
    INC(l.n);
  END Add;

PROCEDURE Result (READONLY l: List): Items =
  VAR res := NEW(Items, l.n);
  BEGIN
    IF l.n > 0 THEN res^ := SUBARRAY(l.a^, 0, l.n); END;
    RETURN res;
  END Result;

(* the kind of item of a kind of symbol of the outline *)
PROCEDURE KindOf (symbol: INTEGER): INTEGER =
  BEGIN
    CASE symbol OF
    | Outline.Class => RETURN Class;
    | Outline.Method => RETURN Method;
    | Outline.Field => RETURN Field;
    | Outline.Enum => RETURN Enum;
    | Outline.Function => RETURN Function;
    | Outline.Variable => RETURN Variable;
    | Outline.Constant => RETURN Constant;
    | Outline.EnumMember => RETURN EnumMember;
    | Outline.Struct => RETURN Struct;
    | Outline.Event => RETURN Event;
    | Outline.TypeParam => RETURN TypeParameter;
    ELSE
      RETURN TextItem;
    END;
  END KindOf;

(* the declarations of an outline, but its revelations *)
PROCEDURE AddSymbols (VAR l: List; syms: Outline.Symbols; sort: TEXT) =
  BEGIN
    IF syms = NIL THEN RETURN; END;
    FOR i := 0 TO LAST(syms^) DO
      WITH s = syms[i] DO
        IF s.detail = NIL OR NOT Text.Equal(s.detail, "REVEAL") THEN
          Add(l, s.name, s.detail, KindOf(s.kind), sort);
        END;
      END;
    END;
  END AddSymbols;

PROCEDURE AddNames (VAR      l    : List;
                    READONLY names: ARRAY OF TEXT;
                             kind : INTEGER;
                             sort : TEXT           ) =
  BEGIN
    FOR i := 0 TO LAST(names) DO Add(l, names[i], NIL, kind, sort); END;
  END AddNames;

(* The units *)

PROCEDURE Name (id: M3AST_AS.ID): TEXT =
  BEGIN
    IF id = NIL OR id.lx_symrep = NIL THEN RETURN NIL; END;
    RETURN M3CId.ToText(id.lx_symrep);
  END Name;

(* the declarations of the interface name in c, or NIL if c has none;
   those of a generic instance are those of the generic interface, in
   the text of it *)
PROCEDURE Interface (c: M3Context.T; name: TEXT; texts: Texts):
  Outline.Symbols =
  VAR
    cu, gen: M3AST_AS.Compilation_Unit;
    ut              := M3CUnit.Type.Interface;
    file   : TEXT;
  BEGIN
    IF name = NIL OR NOT M3Context.Find(c, name, ut, cu) OR cu = NIL
         OR cu.as_root = NIL THEN
      RETURN NIL;
    END;
    TYPECASE cu.as_root OF
    | M3AST_AS.UNIT_GEN_INS (ins) =>
        IF NOT M3Context.FindExact(c, Name(ins.as_gen_id),
                                   M3CUnit.Type.Interface_gen_def, gen)
             OR gen = NIL THEN
          RETURN NIL;
        END;
        file := M3CUnit.TextName(gen.fe_uid);
        cu := M3CUnit.ToGenIns(cu, ut);
        IF cu = NIL THEN RETURN NIL; END;
    ELSE
      file := M3CUnit.TextName(cu.fe_uid);
    END;
    RETURN Outline.OfUnit(cu, FileText(file, texts));
  END Interface;

(* the symbol name of syms, or NIL *)
PROCEDURE Find (syms: Outline.Symbols; name: TEXT): Outline.Symbol =
  BEGIN
    IF syms = NIL OR name = NIL THEN RETURN NIL; END;
    FOR i := 0 TO LAST(syms^) DO
      IF Text.Equal(syms[i].name, name) THEN RETURN syms[i]; END;
    END;
    RETURN NIL;
  END Find;

(* the interfaces that the unit u imports, with the names they have in
   it, and (from) the identifiers that it imports with FROM *)
PROCEDURE ImportsOf (    u    : M3AST_AS.UNIT_WITH_BODY;
                     VAR from : List;
                         c    : M3Context.T;
                         texts: Texts                    ): Imports =
  VAR
    iter := SeqM3AST_AS_IMPORTED.NewIter(u.as_import_s);
    imp  : M3AST_AS.IMPORTED;
    res  := NEW(Imports, 8);
    n    := 0;
    new  : Imports;
    items: SeqM3AST_AS_Import_item.Iter;
    item : M3AST_AS.Import_item;
    ids  : SeqM3AST_AS_Used_def_id.Iter;
    id   : M3AST_AS.Used_def_id;
    intf : TEXT;
    syms : Outline.Symbols;
    sym  : Outline.Symbol;

  PROCEDURE Put (name, intf: TEXT) =
    BEGIN
      IF name = NIL OR intf = NIL THEN RETURN; END;
      IF n = NUMBER(res^) THEN
        new := NEW(Imports, 2 * n);
        SUBARRAY(new^, 0, n) := res^;
        res := new;
      END;
      res[n] := Import{name := name, intf := intf};
      INC(n);
    END Put;

  BEGIN
    WHILE SeqM3AST_AS_IMPORTED.Next(iter, imp) DO
      TYPECASE imp OF
      | NULL =>
      | M3AST_AS.Simple_import (s) =>
          items := SeqM3AST_AS_Import_item.NewIter(s.as_import_item_s);
          WHILE SeqM3AST_AS_Import_item.Next(items, item) DO
            IF item # NIL THEN
              intf := Name(item.as_intf_id);
              IF item.as_id # NIL AND Name(item.as_id) # NIL THEN
                Put(Name(item.as_id), intf);
              ELSE
                Put(intf, intf);
              END;
            END;
          END;
      | M3AST_AS.From_import (f) =>
          syms := Interface(c, Name(f.as_intf_id), texts);
          ids := SeqM3AST_AS_Used_def_id.NewIter(f.as_id_s);
          WHILE SeqM3AST_AS_Used_def_id.Next(ids, id) DO
            sym := Find(syms, Name(id));
            IF sym # NIL THEN
              Add(from, sym.name, sym.detail, KindOf(sym.kind), "1");
            ELSE
              Add(from, Name(id), NIL, TextItem, "1");
            END;
          END;
      ELSE
      END;
    END;
    new := NEW(Imports, n);
    new^ := SUBARRAY(res^, 0, n);
    RETURN new;
  END ImportsOf;

(* the interfaces whose declarations a module can use without a name:
   those it exports, or that of its name *)
PROCEDURE AddExports (VAR l    : List;
                          root : M3AST_AS.UNIT;
                          c    : M3Context.T;
                          texts: Texts          ) =
  VAR
    exports: SeqM3AST_AS_Used_interface_id.T := NIL;
    iter   : SeqM3AST_AS_Used_interface_id.Iter;
    id     : M3AST_AS.Used_interface_id;
    any                                      := FALSE;
  BEGIN
    TYPECASE root OF
    | NULL => RETURN;
    | M3AST_AS.Module (m) => exports := m.as_export_s;
    | M3AST_AS.Module_gen_def =>
    ELSE
      RETURN;
    END;
    IF exports # NIL THEN
      iter := SeqM3AST_AS_Used_interface_id.NewIter(exports);
      WHILE SeqM3AST_AS_Used_interface_id.Next(iter, id) DO
        IF id # NIL THEN
          AddSymbols(l, Interface(c, Name(id), texts), "0");
          any := TRUE;
        END;
      END;
    END;
    IF NOT any THEN
      AddSymbols(l, Interface(c, Name(root.as_id), texts), "0");
    END;
  END AddExports;

(* The place of the cursor *)

(* the name before the "." before the word that ends at off, or NIL if
   there is no "."; "" if the "." follows something else than a name
   (a bracket, a number) *)
PROCEDURE Qualifier (READONLY a: ARRAY OF CHAR; off: INTEGER): TEXT =
  VAR i := off; end: INTEGER;
  BEGIN
    WHILE i > 0 AND IsIdChar(a[i - 1]) DO DEC(i); END;
    WHILE i > 0 AND IsBlank(a[i - 1]) DO DEC(i); END;
    (* not a "." or the range ".." *)
    IF i = 0 OR a[i - 1] # '.' THEN RETURN NIL; END;
    DEC(i);
    IF i > 0 AND a[i - 1] = '.' THEN RETURN NIL; END;
    WHILE i > 0 AND IsBlank(a[i - 1]) DO DEC(i); END;
    end := i;
    WHILE i > 0 AND IsIdChar(a[i - 1]) DO DEC(i); END;
    IF i = end OR NOT IsLetter(a[i]) THEN RETURN ""; END;
    RETURN Text.FromChars(SUBARRAY(a, i, end - i));
  END Qualifier;

(* the word before off begins with a digit: a number *)
PROCEDURE InNumber (READONLY a: ARRAY OF CHAR; off: INTEGER): BOOLEAN =
  VAR i := off;
  BEGIN
    WHILE i > 0 AND IsIdChar(a[i - 1]) DO DEC(i); END;
    RETURN i < off AND IsDigit(a[i]);
  END InNumber;

(* The completion *)

PROCEDURE Parsed (c: M3Context.T; file, text: TEXT) =
  VAR cu := Outline.Unit(c, file);
  BEGIN
    IF cu # NIL AND NOT M3CUnit.State.PErrors IN cu.fe_status THEN
      EVAL parsed.put(file, Outline.OfUnit(cu, text));
    END;
  END Parsed;

PROCEDURE Get (c    : M3Context.T;
               file : TEXT;
               text : TEXT;
               line : INTEGER;
               col  : INTEGER;
               texts: Texts        ): Items =
  VAR
    cu     : M3AST_AS.Compilation_Unit;
    n                                  := Text.Length(text);
    a                                  := NEW(REF ARRAY OF CHAR, n);
    off    : INTEGER;
    qual   : TEXT;
    l, from: List;
    imports                            := NEW(Imports, 0);
    ref    : REFANY;
  BEGIN
    cu := Outline.Unit(c, file);
    IF cu = NIL THEN RETURN NIL; END;
    Text.SetChars(a^, text);
    off := Offset(a^, line, col);
    IF NOT Outline.InCode(text, off) OR InNumber(a^, off) THEN
      RETURN NEW(Items, 0);
    END;
    TYPECASE cu.as_root OF
    | NULL =>
    | M3AST_AS.UNIT_WITH_BODY (u) => imports := ImportsOf(u, from, c, texts);
    ELSE
    END;

    qual := Qualifier(a^, off);
    IF qual # NIL THEN
      (* I.: the declarations of the interface imported as I *)
      FOR i := 0 TO LAST(imports^) DO
        IF Text.Equal(imports[i].name, qual) THEN
          AddSymbols(l, Interface(c, imports[i].intf, texts), "0");
          EXIT;
        END;
      END;
      RETURN Result(l);
    END;

    (* the declarations of the unit, also those of the last unit without
       errors of the parser that are not in it *)
    AddSymbols(l, Outline.OfUnit(cu, text), "0");
    IF M3CUnit.State.PErrors IN cu.fe_status
         AND parsed.get(file, ref) THEN
      AddSymbols(l, NARROW(ref, Outline.Symbols), "0");
    END;
    IF cu.as_root # NIL THEN AddExports(l, cu.as_root, c, texts); END;

    (* the imports, and the formal parameters of a generic unit *)
    FOR i := 0 TO LAST(imports^) DO
      IF Text.Equal(imports[i].name, imports[i].intf) THEN
        Add(l, imports[i].name, NIL, Module, "1");
      ELSE
        Add(l, imports[i].name, imports[i].intf, Module, "1");
      END;
    END;
    TYPECASE cu.as_root OF
    | NULL =>
    | M3AST_AS.UNIT_GEN_DEF (g) =>
        VAR
          iter := SeqM3AST_AS_F_Interface_id.NewIter(g.as_id_s);
          id   : M3AST_AS.F_Interface_id;
        BEGIN
          WHILE SeqM3AST_AS_F_Interface_id.Next(iter, id) DO
            Add(l, Name(id), NIL, Module, "1");
          END;
        END;
    ELSE
    END;
    FOR i := 0 TO from.n - 1 DO
      WITH f = from.a[i] DO Add(l, f.label, f.detail, f.kind, f.sort); END;
    END;

    AddNames(l, Types, TypeParameter, "2");
    AddNames(l, Constants, Constant, "2");
    AddNames(l, Procedures, Function, "2");
    AddNames(l, Keywords, Keyword, "3");
    RETURN Result(l);
  END Get;

BEGIN
END Complete.
