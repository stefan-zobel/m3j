(***************************************************************************)
(*                      Copyright (C) Olivetti 1989                        *)
(*                          All Rights reserved                            *)
(*                                                                         *)
(* Use and copy of this software and preparation of derivative works based *)
(* upon this software are permitted to any person, provided this same      *)
(* copyright notice and the following Olivetti warranty disclaimer are     *) 
(* included in any copy of the software or any modification thereof or     *)
(* derivative work therefrom made by any person.                           *)
(*                                                                         *)
(* This software is made available AS IS and Olivetti disclaims all        *)
(* warranties with respect to this software, whether expressed or implied  *)
(* under any law, including all implied warranties of merchantibility and  *)
(* fitness for any purpose. In no event shall Olivetti be liable for any   *)
(* damages whatsoever resulting from loss of use, data or profits or       *)
(* otherwise arising out of or in connection with the use or performance   *)
(* of this software.                                                       *)
(***************************************************************************)
(**)
(* Copyright (C) 1991, Digital Equipment Corporation           *)
(* All rights reserved.                                        *)
(* See the file COPYRIGHT for a full description.              *)

(* Changed for m3j: a name in an ASSERT or DEBUG pragma is a use of it. The
   text of the pragma is not part of the tree, so its names are looked up
   here, in the scope that the pragma is in. *)

MODULE M3CChkUses;

IMPORT ASCII, IntRefTbl, RefList, Word, Text;
IMPORT AST, M3AST_AS, M3AST_SM;
IMPORT ASTWalk, M3ASTScope, M3CId, M3CPragma;

IMPORT M3AST_LX_F, M3AST_AS_F, M3AST_SM_F, M3AST_TM_F;

IMPORT SeqM3AST_AS_DECL_REVL, SeqM3AST_AS_DEF_ID;
IMPORT M3Error;

IMPORT M3CSrcPos;

CONST
  DefIdTableSize = 256;
  IdentChars = ASCII.AlphaNumerics + ASCII.Set{'_'};
  Quotes = ASCII.Set{'"', '\''};

TYPE
  DefId = OBJECT
    used := FALSE; saysUnused := FALSE;
    defId: M3AST_AS.DEF_ID;
  END;

  Import = DefId OBJECT
    usedId: M3AST_AS.USED_ID;
  END;

  DefIdList = REF RefList.T; (* OF DefId *)

REVEAL
  Handle = M3ASTScope.Closure BRANDED OBJECT
    interface: BOOLEAN;
    pragmas: M3CPragma.Store;
    ignoreHeaderOrFrom, ignoreFormals, inImported := FALSE;
    ignoreFORvars := FALSE; saysUnused := FALSE;
    table: IntRefTbl.T;
    iter: M3CPragma.Iter := NIL;   (* the pragmas after 'next' *)
    next: M3CPragma.T := NIL;      (* the first that has not been read *)
    closed: M3AST_SM.SCOPE := NIL; (* the innermost scope left since the
                                      last node a pragma can come before *)
  OVERRIDES
    callback := Node;
  END;


PROCEDURE AddDefId(
    h: Handle;
    defId: M3AST_SM.DEF_ID_UNSET;
    usedId: M3AST_AS.USED_ID := NIL): DefId
    RAISES {}=
  VAR
    d: DefId;
    ra: REFANY;
    l: DefIdList;
  BEGIN
    IF usedId = NIL THEN
      d := NEW(DefId, defId := defId);
    ELSE
      d := NEW(Import, defId := defId, usedId := usedId);
    END;
    (* Assert: not in the table already *)
    IF h.table.get(M3CSrcPos.Hash(defId.lx_srcpos), ra) THEN
      (* lx_srcpos clash *)
      l := NARROW(ra, DefIdList);
    ELSE
      l := NEW(DefIdList);
      EVAL h.table.put(M3CSrcPos.Hash(defId.lx_srcpos), l);
    END;
    l^ := RefList.Cons(d, l^);
    RETURN d;
  END AddDefId;

PROCEDURE Lookup(
    h: Handle;
    defId: M3AST_AS.DEF_ID;
    VAR (*out*) d: DefId)
    : BOOLEAN
    RAISES {}=
  VAR
    ra: REFANY;
    l: DefIdList;
    dl: RefList.T;
  BEGIN
    (* cant hash references with a copying collector, so hash on
    source position and then use straight comparisions *)
    IF h.table.get(M3CSrcPos.Hash(defId.lx_srcpos), ra) THEN
      l := NARROW(ra, DefIdList);
      dl := l^;
      WHILE dl # NIL DO
        d := dl.head;
        IF d.defId = defId THEN RETURN TRUE;
	ELSE
      	  dl := dl.tail;
        END;
      END; (* while *)
    END;
    RETURN FALSE
  END Lookup;

PROCEDURE MarkName(
    h: Handle;
    name: Text.T;
    scope: M3AST_SM.SCOPE)
    RAISES {}=
  VAR
    id: M3CId.T;
    defId: M3AST_AS.DEF_ID;
    d: DefId;
  BEGIN
    (* a reserved word, like OR, is not an M3CId.T *)
    IF NOT M3CId.Lookup(name, id) THEN RETURN END;
    WHILE scope # NIL DO
      VAR
        iter := SeqM3AST_AS_DEF_ID.NewIter(scope.sm_def_id_s);
      BEGIN
        WHILE SeqM3AST_AS_DEF_ID.Next(iter, defId) DO
          IF defId.lx_symrep = id THEN
            TYPECASE defId OF
            | M3AST_AS.Module_id, M3AST_AS.Enum_id,
              M3AST_AS.Field_id, M3AST_AS.METHOD_OVERRIDE_ID =>
                RETURN;
            ELSE
            END;
            IF NOT Lookup(h, defId, d) THEN d := AddDefId(h, defId) END;
            d.used := TRUE;
            RETURN;
          END;
        END;
      END;
      scope := scope.sm_enc_scope;
    END;
  END MarkName;

PROCEDURE MarkNames(
    h: Handle;
    body: Text.T;
    scope: M3AST_SM.SCOPE)
    RAISES {}=
  (* Marks the names that 'body', the text of a pragma, uses, if the pragma
   is an ASSERT or a DEBUG. A name after a dot is that of a field, a method
   or an item of an interface, which are not tracked. *)
  VAR
    i: CARDINAL := 2;
    end := Text.Length(body) - 2;
    afterDot, keyword := FALSE;
    start: CARDINAL;
    ch: CHAR;
  BEGIN
    WHILE i < end AND Text.GetChar(body, i) IN ASCII.Spaces DO INC(i) END;
    start := i;
    WHILE i < end AND Text.GetChar(body, i) IN IdentChars DO INC(i) END;
    WITH word = Text.Sub(body, start, i - start) DO
      keyword := Text.Equal(word, "ASSERT") OR Text.Equal(word, "DEBUG");
    END;
    IF NOT keyword THEN RETURN END;
    WHILE i < end DO
      ch := Text.GetChar(body, i);
      IF ch IN ASCII.Letters THEN
        start := i;
        WHILE i < end AND Text.GetChar(body, i) IN IdentChars DO INC(i) END;
        (* an identifier before a quote is the W of a wide literal *)
        IF NOT afterDot AND NOT (i < end AND Text.GetChar(body, i) IN Quotes) THEN
          MarkName(h, Text.Sub(body, start, i - start), scope);
        END;
        afterDot := FALSE;
      ELSIF ch IN ASCII.Digits THEN
        WHILE i < end AND Text.GetChar(body, i) IN IdentChars DO INC(i) END;
        afterDot := FALSE;
      ELSIF ch IN Quotes THEN
        INC(i);
        WHILE i < end AND Text.GetChar(body, i) # ch DO
          IF Text.GetChar(body, i) = '\\' THEN INC(i) END;
          INC(i);
        END;
        INC(i);
        afterDot := FALSE;
      ELSIF ch = '(' AND i + 1 < end AND Text.GetChar(body, i + 1) = '*' THEN
        i := AfterComment(body, i, end);
      ELSIF ch = '.' AND i + 1 < end AND Text.GetChar(body, i + 1) = '.' THEN
        INC(i, 2);
        afterDot := FALSE;
      ELSIF ch = '.' THEN
        INC(i);
        afterDot := TRUE;
      ELSE
        IF NOT ch IN ASCII.Spaces THEN afterDot := FALSE END;
        INC(i);
      END;
    END;
  END MarkNames;

PROCEDURE AfterComment(
    body: Text.T;
    i, end: CARDINAL)
    : CARDINAL
    RAISES {}=
  (* 'i' is the start of a comment, comments can be nested. *)
  VAR
    depth := 0;
  BEGIN
    WHILE i + 1 < end DO
      IF Text.GetChar(body, i) = '(' AND Text.GetChar(body, i + 1) = '*' THEN
        INC(depth);
        INC(i, 2);
      ELSIF Text.GetChar(body, i) = '*' AND Text.GetChar(body, i + 1) = ')' THEN
        DEC(depth);
        INC(i, 2);
        IF depth = 0 THEN RETURN i END;
      ELSE
        INC(i);
      END;
    END;
    RETURN end;
  END AfterComment;

PROCEDURE ReadPragmas(
    h: Handle;
    before: M3AST_AS.SRC_NODE)
    RAISES {}=
  (* Reads the pragmas before the given node, all of them if it is NIL. A
   pragma after the last node of a scope still is in that scope, which is
   left before the next node is seen. *)
  VAR
    scope := h.closed;
  BEGIN
    IF scope = NIL THEN scope := h.scope END;
    h.closed := NIL;
    WHILE h.next # NIL AND
        (before = NIL OR
         M3CSrcPos.Compare(M3CPragma.Position(h.next), before.lx_srcpos) < 0) DO
      MarkNames(h, M3CPragma.Body(h.next), scope);
      IF NOT M3CPragma.Next(h.iter, h.next) THEN h.next := NIL END;
    END;
  END ReadPragmas;

PROCEDURE Encloses(outer, inner: M3AST_SM.SCOPE): BOOLEAN RAISES {}=
  BEGIN
    WHILE inner # NIL DO
      IF inner = outer THEN RETURN TRUE END;
      inner := inner.sm_enc_scope;
    END;
    RETURN FALSE;
  END Encloses;

PROCEDURE TrackScope(h: Handle; n: AST.NODE; vm: ASTWalk.VisitMode) RAISES {}=
  VAR
    old := h.scope;
  BEGIN
    M3ASTScope.Set(h, n, vm);
    (* the end of a node can open a scope too, that of a WITH binding *)
    IF vm = ASTWalk.VisitMode.Exit AND h.closed = NIL AND old # NIL AND
        h.scope # old AND (h.scope = NIL OR Encloses(h.scope, old)) THEN
      h.closed := old;
    END;
  END TrackScope;

PROCEDURE Node(h: Handle; n: AST.NODE; vm: ASTWalk.VisitMode) RAISES {}=
  VAR
    usedId: M3AST_AS.USED_ID;
    d: DefId;
  BEGIN
    IF vm = ASTWalk.VisitMode.Entry THEN
      TYPECASE n OF
      | M3AST_AS.STM, M3AST_AS.DECL_REVL, M3AST_AS.Tcase, M3AST_AS.Handler =>
          ReadPragmas(h, n);
      ELSE
      END;
    END;
    TrackScope(h, n, vm);
    IF vm = ASTWalk.VisitMode.Entry THEN
      IF n.IsA_USED_ID(usedId) THEN
        IF h.ignoreHeaderOrFrom THEN
          (* We are ignoring a unit header (contains Interface_id or Module_id
           and possibly an exports list) or the interface in an unqualified
           import (i.e. FROM interface IMPORT). If we are ignoring a FROM
           interface we set 'ignore' to FALSE so we don't ignore the imported
           ids which follow *)
          IF h.inImported THEN h.ignoreHeaderOrFrom := FALSE END;
          RETURN;
        END;
        VAR
          defId := usedId.sm_def;
        BEGIN
          TYPECASE defId OF
          | NULL, M3AST_AS.Module_id, M3AST_AS.Enum_id,
            M3AST_AS.Field_id, M3AST_AS.METHOD_OVERRIDE_ID =>
              RETURN;
          ELSE
          END;
          IF Lookup(h, defId, d) THEN
            (* This name has already been entered *)
          ELSE
            (* First encounter; put it in the table. If we are dealing with
             an import we must pass the 'usedId' to 'AddDefId' *)
            IF NOT h.inImported THEN usedId := NIL END;
            d := AddDefId(h, defId, usedId);
          END;
          IF NOT h.inImported THEN
            d.used := TRUE;
          END;
        END;
        RETURN;
      END;

      TYPECASE n OF
      | M3AST_AS.UNIT =>
          h.ignoreHeaderOrFrom := TRUE;
      | M3AST_AS.IMPORTED(imported) =>
          h.ignoreHeaderOrFrom := ISTYPE(imported, M3AST_AS.From_import);
          h.inImported := TRUE;
      | M3AST_AS.Import_item(ii) =>
          h.ignoreHeaderOrFrom := ii.as_id # NIL;
      | M3AST_AS.Block =>
          h.ignoreHeaderOrFrom := FALSE;
          h.inImported := FALSE;
      | M3AST_AS.Procedure_type(procType) =>
          TYPECASE procType.sm_def_id OF
          | NULL =>
              h.ignoreFormals := TRUE;
          | M3AST_AS.Proc_id =>
              h.ignoreFormals := FALSE;
          ELSE
              h.ignoreFormals := TRUE;
          END;
      | M3AST_AS.Var_decl_s,
        M3AST_AS.Formal_param =>
          IF UnusedPragma(h, n) THEN h.saysUnused := TRUE END;

      | M3AST_AS.Module_id, M3AST_AS.Enum_id,
        M3AST_AS.Field_id, M3AST_AS.METHOD_OVERRIDE_ID =>
          (* Don't want to take the 'DEF_ID' arm; we don't track these ids *)
      | M3AST_AS.DEF_ID(defId) =>
          TYPECASE n OF
          | M3AST_AS.For_id =>
              IF h.ignoreFORvars THEN RETURN END;
	      IF UnusedPragma(h, n) THEN h.saysUnused := TRUE END;
          ELSE (* drop through *)
          END;
          IF h.ignoreHeaderOrFrom OR h.interface OR
              h.ignoreFormals AND ISTYPE(defId, M3AST_AS.FORMAL_ID) THEN
            RETURN
          END;
          TYPECASE defId OF
          | M3AST_AS.Proc_id(procId) =>
              (* Exported proc ids are not considered to be unused *)
              IF procId.vREDEF_ID.sm_int_def # NIL THEN RETURN END;
          ELSE
          END;
          IF NOT Lookup(h, defId, d) THEN 
	    d := AddDefId(h, defId);
          END;
	  d.saysUnused := h.saysUnused;
     ELSE
     END; (* typecase *)
   ELSE (* vm = ASTWalk.VisitMode.Exit *)
     TYPECASE n OF
     | M3AST_AS.Var_decl_s, M3AST_AS.Formal_param => h.saysUnused := FALSE
     ELSE
     END;
   END
  END Node;

PROCEDURE UnusedPragma(h: Handle; n: AST.NODE):BOOLEAN RAISES {}=
  VAR
    iter := M3CPragma.BeforeNode(h.pragmas, n);
    pragma: M3CPragma.T;
    args: Text.T;
  BEGIN
    WHILE M3CPragma.Next(iter, pragma) AND
        M3CPragma.FollowingNode(pragma) = n DO
      IF M3CPragma.Match(pragma, "UNUSED", args) THEN
        RETURN TRUE;
      END;
    END;
    RETURN FALSE;
  END UnusedPragma;

PROCEDURE NewHandle(cu: M3AST_AS.Compilation_Unit;
  ignoreFORvars := FALSE): Handle RAISES {}=
  VAR
    h := NEW(Handle, interface := ISTYPE(cu.as_root, M3AST_AS.Interface),
        pragmas := cu.lx_pragmas,
        table := NEW(IntRefTbl.Default).init(DefIdTableSize),
	ignoreFORvars := ignoreFORvars,
        iter := M3CPragma.NewIter(cu.lx_pragmas));
  BEGIN
    IF NOT M3CPragma.Next(h.iter, h.next) THEN h.next := NIL END;
    RETURN h;
  END NewHandle;

PROCEDURE Unused(d: DefId; defId: M3AST_AS.DEF_ID) RAISES {}=
  VAR
    errNode: M3Error.ERROR_NODE;
  BEGIN
    TYPECASE d OF
    | Import(import) =>
        TYPECASE defId OF
        | M3AST_AS.Interface_id(intId) =>
            VAR
              iter := SeqM3AST_AS_DECL_REVL.NewIter(
                  NARROW(intId.sm_spec, M3AST_AS.UNIT_WITH_BODY).as_block.as_decl_s);
              d: M3AST_AS.DECL_REVL;
            BEGIN
              WHILE SeqM3AST_AS_DECL_REVL.Next(iter, d) DO
                IF ISTYPE(d, M3AST_AS.Revelation_s) THEN RETURN END;
              END;
            END;
        ELSE
        END;
        errNode := import.usedId;
    ELSE
      errNode := defId;
    END;
    M3Error.WarnWithId(errNode, "\'%s\' is not used", defId.lx_symrep);
  END Unused;


PROCEDURE CloseHandle(h: Handle) RAISES {}=
  VAR
    i := h.table.iterate();
    key: Word.T;  value: REFANY;
    d: DefId;
    dl: RefList.T;
  BEGIN
    ReadPragmas(h, NIL);
    WHILE i.next(key, value) DO
      dl := NARROW(value, DefIdList)^;
      WHILE dl # NIL DO
        d := dl.head;
	IF d.used THEN
	  IF d.saysUnused THEN 
            M3Error.WarnWithId(d.defId, "\'%s\' is NOT unused!",
	        d.defId.lx_symrep);
          END;
        ELSE IF NOT d.saysUnused THEN Unused(d, d.defId) END;
        END;
	dl := dl.tail;
      END;
    END;
  END CloseHandle;


BEGIN

END M3CChkUses.
