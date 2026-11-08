(* The objects of a test of the distributed collection of network
   objects. *)

INTERFACE Test;

IMPORT NetObj, Thread;

TYPE
  T = NetObj.T OBJECT METHODS
    (* a new object of the server, with the number id, which the server
       does not keep *)
    make (id: INTEGER): Obj RAISES {NetObj.Error, Thread.Alerted};
    (* the object of the server with the number id, which it keeps *)
    get (id: INTEGER): Obj RAISES {NetObj.Error, Thread.Alerted};
    (* the number of objects that make returned and that the collector
       of the server has not freed yet *)
    live (): INTEGER RAISES {NetObj.Error, Thread.Alerted};
    (* keeps o until drop *)
    hold (o: Obj) RAISES {NetObj.Error, Thread.Alerted};
    (* the sum of the numbers of the objects kept since the last drop;
       it forgets them *)
    drop (): INTEGER RAISES {NetObj.Error, Thread.Alerted};
  END;

  Obj = NetObj.T OBJECT METHODS
    id (): INTEGER RAISES {NetObj.Error, Thread.Alerted};
  END;

END Test.
