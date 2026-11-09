(* Copyright (C) 2023, Peter McKinna *)
(* All rights reserved.              *)
(* Licensed under the MIT license.   *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: the methods package and inLibrary; no rootPath and
   rootUri, which the server does not use. *)

INTERFACE AstCompile;

IMPORT M3ToolFrame, M3Context, RefSeq;

TYPE
  CompType = {Interface,Module,GenInt,GenMod};

  WorkerClosure <: WorkerClosurePublic;
  WorkerClosurePublic = M3ToolFrame.Worker OBJECT
  METHODS
    init() : WorkerClosure;
    getContext() : M3Context.T; 
    fileName() : TEXT;
    path(uri : TEXT) : TEXT;
    getTargetDir(uri : TEXT) : TEXT;
    getUnit(uri : TEXT) : TEXT;
    package(uri : TEXT) : TEXT;
    (* the directory of the package of the file: the directory that holds
       the directory src above the file, or NIL if there is none *)
    inLibrary(uri : TEXT) : BOOLEAN;
    (* the file is in the library of m3j (the directory lib of m3j) *)
    compile(uri, text : TEXT) : RefSeq.T;
  END;

  (* equiv to NEW(WorkerClosure).init(); *)
  PROCEDURE New() : WorkerClosure;

VAR
  wc : WorkerClosure;

END AstCompile.
