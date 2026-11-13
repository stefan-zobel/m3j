(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Complete;

IMPORT Rd, FileRd, OSError, Thread, TextRefTbl, RefSeq, RuntimeError;
IMPORT AST, ASTWalk, M3ASTScope, M3Context, M3CUnit, M3CId, M3CExpsMisc;
IMPORT M3AST_AS, M3AST_AS_F, M3AST_LX_F, M3AST_FE_F, M3AST_SM, M3AST_SM_F,
       M3AST_TM_F;
IMPORT SeqM3AST_AS_IMPORTED, SeqM3AST_AS_Import_item,
       SeqM3AST_AS_Used_def_id, SeqM3AST_AS_Used_interface_id,
       SeqM3AST_AS_F_Interface_id, SeqM3AST_AS_DEF_ID,
       SeqM3AST_AS_TYPE_SPEC;
IMPORT M3FindFile, M3DirFindFile, M3Extension, M3PathElem;
IMPORT Feature, Outline;
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

  (* what the members of a type are looked up in *)
  Env = RECORD
    c    : M3Context.T;
    texts: Texts;
    root : M3AST_AS.UNIT;               (* the unit of the completion *)
  END;

  (* a walk through the unit up to the cursor, with the scopes of m3tk *)
  Walk = M3ASTScope.Closure OBJECT
    src   : Outline.Source;
    cursor: INTEGER;                    (* the offset of the cursor *)
    dot   : INTEGER;                    (* that of the "." before it, or -1 *)
    best  : M3AST_SM.SCOPE   := NIL;    (* the scope at the cursor *)
    select: M3AST_AS.Select  := NIL;    (* the selection at dot *)
    bodies: RefSeq.T;                   (* the blocks of the units and
                                           procedures entered *)
  OVERRIDES
    callback := Visit;
  END;

  (* the place of the cursor in the imports or exports of a unit *)
  Place = {Code,                        (* not there *)
           Interfaces,                  (* the name of an interface *)
           Names,                       (* a name of FROM I IMPORT *)
           New};                        (* the new name after AS *)

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

(* the unit of the interface name in c and the file of its text; for a
   generic instance, the instantiated unit and the file of the generic
   interface, whose text it has; FALSE if c has none *)
PROCEDURE FindInterface (    c   : M3Context.T;
                             name: TEXT;
                         VAR cu  : M3AST_AS.Compilation_Unit;
                         VAR file: TEXT                       ): BOOLEAN =
  VAR
    gen: M3AST_AS.Compilation_Unit;
    ut                             := M3CUnit.Type.Interface;
  BEGIN
    IF name = NIL OR NOT M3Context.Find(c, name, ut, cu) OR cu = NIL
         OR cu.as_root = NIL THEN
      RETURN FALSE;
    END;
    TYPECASE cu.as_root OF
    | M3AST_AS.UNIT_GEN_INS (ins) =>
        IF NOT M3Context.FindExact(c, Name(ins.as_gen_id),
                                   M3CUnit.Type.Interface_gen_def, gen)
             OR gen = NIL THEN
          RETURN FALSE;
        END;
        file := M3CUnit.TextName(gen.fe_uid);
        cu := M3CUnit.ToGenIns(cu, ut);
        RETURN cu # NIL;
    ELSE
      file := M3CUnit.TextName(cu.fe_uid);
      RETURN TRUE;
    END;
  END FindInterface;

(* the declarations of the interface name in c, or NIL if c has none;
   those of a generic instance are those of the generic interface, in
   the text of it *)
PROCEDURE Interface (c: M3Context.T; name: TEXT; texts: Texts):
  Outline.Symbols =
  VAR
    cu  : M3AST_AS.Compilation_Unit;
    file: TEXT;
  BEGIN
    IF NOT FindInterface(c, name, cu, file) THEN RETURN NIL; END;
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

(* The scope at the cursor *)

(* m3tk keeps only the start of a node, and of an expression only that
   of its operator (the "." of a selection); a node that opens a scope
   ends where Outline.ScopeEnd finds its end in the text *)
PROCEDURE Visit (w: Walk; n: AST.NODE; vm: ASTWalk.VisitMode)
  RAISES {ASTWalk.Aborted} =
  VAR
    off := -1;
    end : INTEGER;
  BEGIN
    TYPECASE n OF
    | M3AST_AS.SRC_NODE (sn) => off := Outline.SourceOffset(w.src, sn.lx_srcpos);
    ELSE
    END;
    IF vm = ASTWalk.VisitMode.Entry THEN
      (* after the cursor: the scope is that of the last node before it;
         an expression may still have parts before it *)
      IF off > w.cursor THEN
        IF NOT ISTYPE(n, M3AST_AS.EXP) THEN ASTWalk.Abort(); END;
        off := -1;
      END;
      TYPECASE n OF
      | M3AST_AS.UNIT_WITH_BODY (u) => w.bodies.addhi(u.as_block);
      | M3AST_AS.Proc_decl (p) => w.bodies.addhi(p.as_body);
      | M3AST_AS.Select (s) => IF off = w.dot THEN w.select := s; END;
      ELSE
      END;
      M3ASTScope.Set(w, n, vm);
      IF off >= 0 THEN w.best := w.scope; END;
    ELSE
      M3ASTScope.Set(w, n, vm);
      TYPECASE n OF
      | M3AST_AS.UNIT => RETURN;
      | M3AST_AS.Proc_decl =>
          EVAL w.bodies.remhi();
          end := Outline.ScopeEnd(w.src, n);
      | M3AST_AS.With_st, M3AST_AS.For_st, M3AST_AS.Tcase, M3AST_AS.Handler,
        M3AST_AS.Binding =>
          end := Outline.ScopeEnd(w.src, n);
      | M3AST_AS.Block (b) =>
          (* the body of a unit or procedure ends with it *)
          IF w.bodies.size() > 0 AND w.bodies.gethi() = b THEN RETURN; END;
          end := Outline.ScopeEnd(w.src, b);
      ELSE
        end := -1;
        IF off > w.cursor THEN off := -1; END;
      END;
      (* the scope that the node closes holds the cursor *)
      IF end > w.cursor THEN ASTWalk.Abort(); END;
      IF off >= 0 THEN w.best := w.scope; END;
    END;
  END Visit;

(* the walk through the unit cu, compiled from text, up to off; dot is
   the offset of a "." before it, or -1; NIL if m3tk did not check the
   semantics of the unit (after an error in its imports) *)
PROCEDURE WalkTo (cu: M3AST_AS.Compilation_Unit; text: TEXT; off, dot: INTEGER):
  Walk =
  <* FATAL ANY *>
  VAR w: Walk;
  BEGIN
    IF cu.as_root = NIL OR NOT M3CUnit.State.SemChecked IN cu.fe_status THEN
      RETURN NIL;
    END;
    w := NEW(Walk, src := Outline.NewSource(text), cursor := off, dot := dot,
             bodies := NEW(RefSeq.T).init());
    EVAL w.init();
    TRY
      ASTWalk.ModeVisitNodes(cu.as_root, w, ASTWalk.OnEntryAndExit);
    EXCEPT
    | RuntimeError.E =>
        (* a tree that m3tk left incomplete after an error *)
        w.best := NIL;
        w.select := NIL;
    END;
    RETURN w;
  END WalkTo;

(* a name declared inside a procedure, or by WITH, FOR, TYPECASE or
   EXCEPT; its type as the hover shows it if the text has none *)
PROCEDURE AddLocal (VAR l  : List;
                        src: Outline.Source;
                        id : M3AST_AS.DEF_ID;
                        c  : M3Context.T     ) =
  VAR
    sym                := Outline.OfId(src, id);
    detail: TEXT;
  BEGIN
    IF sym = NIL THEN RETURN; END;
    detail := sym.detail;
    IF detail = NIL THEN
      TYPECASE id OF
      | M3AST_AS.Type_id, M3AST_AS.Proc_id, M3AST_AS.Exc_id,
        M3AST_AS.Const_id =>
      | M3AST_AS.TYPED_ID (t) => detail := Feature.TypeText(c, t.sm_type_spec);
      ELSE
      END;
    END;
    Add(l, sym.name, detail, KindOf(sym.kind), "0");
  END AddLocal;

(* the names of the scopes at the cursor inside the unit root: from the
   innermost one, each in the order of the declarations (m3tk has them
   last first) *)
PROCEDURE AddLocals (VAR l   : List;
                         w   : Walk;
                         root: M3AST_AS.UNIT;
                         c   : M3Context.T    ) =
  VAR
    s                       := w.best;
    level: INTEGER;
    ids  : RefSeq.T;
    iter : SeqM3AST_AS_DEF_ID.Iter;
    id   : M3AST_AS.DEF_ID;
  BEGIN
    IF s = NIL OR root = NIL OR root.as_id = NIL
         OR root.as_id.vSCOPE = NIL THEN
      RETURN;
    END;
    level := root.as_id.vSCOPE.sm_level;
    WHILE s # NIL AND s.sm_level > level DO
      ids := NEW(RefSeq.T).init();
      iter := SeqM3AST_AS_DEF_ID.NewIter(s.sm_def_id_s);
      WHILE SeqM3AST_AS_DEF_ID.Next(iter, id) DO ids.addlo(id); END;
      FOR i := 0 TO ids.size() - 1 DO
        AddLocal(l, w.src, NARROW(ids.get(i), M3AST_AS.DEF_ID), c);
      END;
      s := s.sm_enc_scope;
    END;
  END AddLocals;

(* The members of a type *)

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

(* the unit root can see the revelation ts: it is in an interface or in
   root itself *)
PROCEDURE Visible (ts: M3AST_AS.TYPE_SPEC; root: M3AST_AS.UNIT): BOOLEAN =
  BEGIN
    RETURN ts # NIL AND ts.tmp_unit_id # NIL
             AND (ISTYPE(ts.tmp_unit_id.sm_spec, M3AST_AS.Interface)
                    OR ts.tmp_unit_id.sm_spec = root);
  END Visible;

(* the file whose text has the type construction ts, or NIL *)
PROCEDURE TypeFile (c: M3Context.T; ts: M3AST_AS.TYPE_SPEC): TEXT =
  VAR
    cu  : M3AST_AS.Compilation_Unit;
    file: TEXT;
  BEGIN
    IF ts.tmp_unit_id = NIL THEN RETURN NIL; END;
    TYPECASE ts.tmp_unit_id.sm_spec OF
    | NULL => RETURN NIL;
    | M3AST_AS.Interface (i) =>
        (* also that of a generic instance, which has the text of the
           generic interface *)
        IF FindInterface(c, Name(i.as_id), cu, file) THEN RETURN file; END;
        RETURN NIL;
    | M3AST_AS.UNIT (u) =>
        IF u.sm_comp_unit = NIL THEN RETURN NIL; END;
        RETURN M3CUnit.TextName(u.sm_comp_unit.fe_uid);
    END;
  END TypeFile;

(* the fields and methods (only the methods if methodsOnly) or the values
   of the type construction ts itself, but its overrides *)
PROCEDURE AddType (VAR      l          : List;
                            ts         : M3AST_AS.TYPE_SPEC;
                   READONLY env        : Env;
                            sort       : TEXT;
                            methodsOnly: BOOLEAN             ) =
  VAR
    file := TypeFile(env.c, ts);
    syms: Outline.Symbols;
  BEGIN
    IF file = NIL THEN RETURN; END;
    syms := Outline.OfType(ts, FileText(file, env.texts));
    FOR i := 0 TO LAST(syms^) DO
      WITH s = syms[i] DO
        IF (s.detail = NIL OR NOT Text.Equal(s.detail, "OVERRIDES"))
             AND (NOT methodsOnly OR s.kind = Outline.Method) THEN
          Add(l, s.name, s.detail, KindOf(s.kind), sort);
        END;
      END;
    END;
  END AddType;

(* the fields and methods (only the methods if methodsOnly) that a value
   of the type m3type has for the unit env.root: those of the type
   itself sorted as sort, those of its supertypes as "1" *)
PROCEDURE Members (VAR      l          : List;
                            m3type     : M3AST_AS.M3TYPE;
                   READONLY env        : Env;
                            sort       : TEXT;
                            methodsOnly: BOOLEAN;
                            depth                         := 0) =
  VAR
    iter: SeqM3AST_AS_TYPE_SPEC.Iter;
    rev : M3AST_AS.TYPE_SPEC;
  BEGIN
    IF depth > 32 THEN RETURN; END;  (* a recursive type, after an error *)
    TYPECASE Spec(m3type) OF
    | NULL =>
    | M3AST_AS.Record_type (r) =>
        IF NOT methodsOnly THEN AddType(l, r, env, sort, FALSE); END;
    | M3AST_AS.Ref_type (r) =>
        (* a selection dereferences a REF RECORD *)
        TYPECASE Spec(r.as_type) OF
        | NULL =>
        | M3AST_AS.Record_type (rec) =>
            IF NOT methodsOnly THEN AddType(l, rec, env, sort, FALSE); END;
        ELSE
        END;
    | M3AST_AS.Object_type (o) =>
        AddType(l, o, env, sort, methodsOnly);
        Members(l, o.as_ancestor, env, "1", methodsOnly, depth + 1);
    | M3AST_AS.Opaque_type (o) =>
        (* the concrete revelation, if the unit sees it, else the type of
           the declaration T <: U and the partial revelations it sees *)
        IF Visible(o.sm_concrete_type_spec, env.root) THEN
          Members(l, o.sm_concrete_type_spec, env, sort, methodsOnly,
                  depth + 1);
        ELSE
          Members(l, o.as_type, env, sort, methodsOnly, depth + 1);
          iter := SeqM3AST_AS_TYPE_SPEC.NewIter(o.sm_type_spec_s);
          WHILE SeqM3AST_AS_TYPE_SPEC.Next(iter, rev) DO
            IF rev # o.sm_concrete_type_spec AND Visible(rev, env.root) THEN
              Members(l, rev, env, sort, methodsOnly, depth + 1);
            END;
          END;
        END;
    | M3AST_AS.Packed_type (p) =>
        Members(l, p.as_type, env, sort, methodsOnly, depth + 1);
    ELSE
    END;
  END Members;

(* after exp.: the values of an enumeration type or the methods of an
   object type that exp names, else the fields and methods of the type
   of exp *)
PROCEDURE AddMembers (VAR l: List; exp: M3AST_AS.EXP; READONLY env: Env) =
  VAR defId: M3AST_AS.DEF_ID;
  BEGIN
    IF exp = NIL THEN RETURN; END;
    IF M3CExpsMisc.IsId(exp, defId) THEN
      TYPECASE defId OF
      | M3AST_AS.Type_id (t) =>
          TYPECASE Spec(t.sm_type_spec) OF
          | NULL =>
          | M3AST_AS.Enumeration_type (e) => AddType(l, e, env, "0", FALSE);
          ELSE
            Members(l, t.sm_type_spec, env, "0", methodsOnly := TRUE);
          END;
          RETURN;
      | M3AST_AS.UNIT_ID, M3AST_AS.Interface_AS_id => RETURN;
      ELSE
      END;
    END;
    Members(l, exp.sm_exp_type_spec, env, "0", methodsOnly := FALSE);
  END AddMembers;

(* The names of interfaces *)

(* the interfaces that the finder of c finds, but self *)
PROCEDURE AddInterfaces (VAR l: List; c: M3Context.T; self: TEXT) =
  VAR
    iter: M3DirFindFile.Iter;
    name: TEXT;
    ext : M3Extension.T;
    dir : M3PathElem.T;
  BEGIN
    TYPECASE c.getSub(TYPECODE(M3FindFile.T)) OF
    | NULL =>
    | M3DirFindFile.Finder (f) =>
        iter := f.iterate();
        WHILE iter.next(name, ext, dir) DO
          IF ext = M3Extension.T.Int
               AND (self = NIL OR NOT Text.Equal(name, self)) THEN
            Add(l, name, NIL, Module, "0");
          END;
        END;
        iter.close();
    ELSE
    END;
  END AddInterfaces;

(* The place of the cursor *)

PROCEDURE IsKeyword (t: TEXT): BOOLEAN =
  BEGIN
    FOR i := 0 TO LAST(Keywords) DO
      IF Text.Equal(t, Keywords[i]) THEN RETURN TRUE; END;
    END;
    RETURN Text.Equal(t, "UNSAFE");
  END IsKeyword;

(* t is an identifier, not a reserved word *)
PROCEDURE IsName (t: TEXT): BOOLEAN =
  BEGIN
    RETURN t # NIL AND Text.Length(t) > 0 AND IsLetter(Text.GetChar(t, 0))
             AND NOT IsKeyword(t);
  END IsName;

(* the token that ends at or before i, after blanks and comments (not
   nested), or NIL at the start of a; i becomes its start *)
PROCEDURE PrevToken (READONLY a: ARRAY OF CHAR; VAR i: INTEGER): TEXT =
  VAR end: INTEGER;
  BEGIN
    LOOP
      WHILE i > 0 AND IsBlank(a[i - 1]) DO DEC(i); END;
      IF i >= 2 AND a[i - 1] = ')' AND a[i - 2] = '*' THEN
        DEC(i, 2);
        WHILE i >= 2 AND NOT (a[i - 2] = '(' AND a[i - 1] = '*') DO DEC(i); END;
        i := MAX(i - 2, 0);
      ELSE
        EXIT;
      END;
    END;
    IF i = 0 THEN RETURN NIL; END;
    end := i;
    IF IsIdChar(a[i - 1]) THEN
      WHILE i > 0 AND IsIdChar(a[i - 1]) DO DEC(i); END;
    ELSE
      DEC(i);
    END;
    RETURN Text.FromChars(SUBARRAY(a, i, end - i));
  END PrevToken;

(* the place of the word that ends at off: in the list of an IMPORT, of a
   FROM I IMPORT (intf is then I) or of EXPORTS, after FROM or after AS,
   or elsewhere *)
PROCEDURE PlaceOf (READONLY a: ARRAY OF CHAR; off: INTEGER; VAR intf: TEXT):
  Place =
  VAR
    i       := off;
    j       : INTEGER;
    t, name : TEXT;
  BEGIN
    WHILE i > 0 AND IsIdChar(a[i - 1]) DO DEC(i); END;
    t := PrevToken(a, i);
    IF t = NIL THEN RETURN Place.Code; END;
    IF Text.Equal(t, "AS") THEN RETURN Place.New; END;
    IF Text.Equal(t, "FROM") THEN RETURN Place.Interfaces; END;
    LOOP
      IF Text.Equal(t, "EXPORTS") THEN RETURN Place.Interfaces; END;
      IF Text.Equal(t, "IMPORT") THEN
        (* FROM I IMPORT *)
        j := i;
        name := PrevToken(a, j);
        IF IsName(name) THEN
          t := PrevToken(a, j);
          IF t # NIL AND Text.Equal(t, "FROM") THEN
            intf := name;
            RETURN Place.Names;
          END;
        END;
        RETURN Place.Interfaces;
      END;
      IF NOT Text.Equal(t, ",") THEN RETURN Place.Code; END;
      (* the item before the ",": I, or I AS J *)
      IF NOT IsName(PrevToken(a, i)) THEN RETURN Place.Code; END;
      t := PrevToken(a, i);
      IF t # NIL AND Text.Equal(t, "AS") THEN
        IF NOT IsName(PrevToken(a, i)) THEN RETURN Place.Code; END;
        t := PrevToken(a, i);
      END;
      IF t = NIL THEN RETURN Place.Code; END;
    END;
  END PlaceOf;

(* the offset of the "." before the word that ends at off, or -1 *)
PROCEDURE Dot (READONLY a: ARRAY OF CHAR; off: INTEGER): INTEGER =
  VAR i := off;
  BEGIN
    WHILE i > 0 AND IsIdChar(a[i - 1]) DO DEC(i); END;
    WHILE i > 0 AND IsBlank(a[i - 1]) DO DEC(i); END;
    IF i > 0 AND a[i - 1] = '.' THEN RETURN i - 1; END;
    RETURN -1;
  END Dot;

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
    w      : Walk;
    intf   : TEXT;
    self   : TEXT                      := NIL;
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

    (* in the imports or exports: the names of interfaces, or those of
       an interface *)
    CASE PlaceOf(a^, off, intf) OF
    | Place.Interfaces =>
        IF ISTYPE(cu.as_root, M3AST_AS.Interface) THEN
          self := Name(cu.as_root.as_id);
        END;
        AddInterfaces(l, c, self);
        RETURN Result(l);
    | Place.Names =>
        AddSymbols(l, Interface(c, intf, texts), "0");
        RETURN Result(l);
    | Place.New => RETURN Result(l);
    | Place.Code =>
    END;

    qual := Qualifier(a^, off);
    IF qual # NIL THEN
      (* I.: the declarations of the interface imported as I *)
      FOR i := 0 TO LAST(imports^) DO
        IF Text.Equal(imports[i].name, qual) THEN
          AddSymbols(l, Interface(c, imports[i].intf, texts), "0");
          RETURN Result(l);
        END;
      END;
      (* exp.: the members of the type of exp, or of the type exp *)
      w := WalkTo(cu, text, off, Dot(a^, off));
      IF w # NIL AND w.select # NIL THEN
        AddMembers(l, w.select.as_exp,
                   Env{c := c, texts := texts, root := cu.as_root});
      END;
      RETURN Result(l);
    END;

    (* the names declared inside the procedures and statements around
       the cursor *)
    w := WalkTo(cu, text, off, -1);
    IF w # NIL THEN AddLocals(l, w, cu.as_root, c); END;

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
