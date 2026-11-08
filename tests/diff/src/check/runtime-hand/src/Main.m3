(* RTIO, RTProcess, RuntimeError and Swap: RTIO.PutE, PutF and PutG, which
   print as C's printf with %e, %f and %g (the exact value rounded half to
   even); the procedures of RTIO on addresses, of which a safe program has
   only NIL; Swap.Swap8; RuntimeError.Raise and Self; RTProcess.Fork,
   RegisterForkHandlers and OnInterrupt; and Transform.Hash, which xors
   the bits of the REAL numbers (cm3 converts the numbers to integers in
   its LOOPHOLE instead). cm3 on Windows cannot link this
   program (m3core has no RTIO__PutE there), so the output is compared
   with a file checked by hand. *)

MODULE Main;

IMPORT Fmt, IO, RTIO, RTProcess, RuntimeError, Stdio, Swap, Transform, Wr;

<*FATAL ANY*>

PROCEDURE Flush () =
  BEGIN
    Wr.Flush (Stdio.stdout);
  END Flush;

PROCEDURE Floats () =
  VAR
    zero := 0.0D0;
    values := ARRAY [0 .. 15] OF LONGREAL {
      0.0D0, -0.0D0, 1.0D0, 123.456D0, 1.0D-5, 1.0D20, -2.5D0, 1.0D0 / 3.0D0,
      5.0D-7, 0.0001D0, 999999.5D0, 100000.0D0, 123456789.0D0, 0.125D0,
      1.0D0 / zero, -1.0D0 / zero};
  BEGIN
    values[1] := -zero;
    FOR i := FIRST (values) TO LAST (values) DO
      IO.Put (Fmt.Int (i) & ": ");
      Flush ();
      RTIO.PutE (values[i]);
      IO.Put (" | ");
      Flush ();
      RTIO.PutF (values[i]);
      IO.Put (" | ");
      Flush ();
      RTIO.PutG (values[i]);
      IO.Put ("\n");
    END;
    IO.Put ("nan: ");
    Flush ();
    RTIO.PutG (zero / zero);
    IO.Put ("\n");
  END Floats;

PROCEDURE Addresses () =
  BEGIN
    IO.Put ("FmtAddr NIL " & RTIO.FmtAddr (NIL) & ", FmtRef NIL " & RTIO.FmtRef (NIL)
              & "\n");
    RTIO.PutChars (NIL, 0);
    RTIO.PutString (NIL);
    RTIO.PutBytes (NIL, 0);
    IO.Put ("no characters at NIL: done\n");
    TRY
      RTIO.PutChars (NIL, 3);
      IO.Put ("PutChars (NIL, 3): no error\n");
    EXCEPT
      RuntimeError.E (e) => IO.Put ("PutChars (NIL, 3): " & RuntimeError.Tag (e) & "\n");
    END;
  END Addresses;

PROCEDURE Swaps () =
  VAR i := Swap.Int64On32 {a := 16_01020304, b := 16_05060708};  r: Swap.Int64On32;
  BEGIN
    r := Swap.Swap8 (i);
    IO.Put ("Swap8: " & Fmt.Int (r.a, 16) & " " & Fmt.Int (r.b, 16) & ", argument "
              & Fmt.Int (i.a, 16) & " " & Fmt.Int (i.b, 16) & "\n");
    i := Swap.Int64On32 {a := -2, b := 16_7F};
    r := Swap.Swap8 (i);
    IO.Put ("Swap8: " & Fmt.Int (r.a) & " " & Fmt.Int (r.b) & "\n");
  END Swaps;

PROCEDURE Errors () =
  BEGIN
    TRY
      RuntimeError.Raise (RuntimeError.T.ValueOutOfRange);
      IO.Put ("Raise: nothing\n");
    EXCEPT
      RuntimeError.E (e) => IO.Put ("Raise: " & RuntimeError.Tag (e) & "\n");
    END;
    (* m3j has no descriptors of exceptions: Self is NIL (cm3: E's). *)
    IO.Put ("Self: NIL " & Fmt.Bool (RuntimeError.Self () = NIL) & "\n");
  END Errors;

PROCEDURE Handler () =
  BEGIN
  END Handler;

PROCEDURE Fork () =
  BEGIN
  END Fork;

PROCEDURE Processes () =
  VAR old: RTProcess.InterruptHandler;
  BEGIN
    IO.Put ("Fork: " & Fmt.Int (RTProcess.Fork ()) & ", RegisterForkHandlers: "
              & Fmt.Int (RTProcess.RegisterForkHandlers (Fork, Fork, Fork)) & "\n");
    old := RTProcess.OnInterrupt (Handler);
    IO.Put ("OnInterrupt: old NIL " & Fmt.Bool (old = NIL));
    old := RTProcess.OnInterrupt (NIL);
    IO.Put (", then Handler " & Fmt.Bool (old = Handler) & "\n");
  END Processes;

PROCEDURE Hashes () =
  VAR tr := Transform.Translate (2.0, 3.0, Transform.Scale (2.0, 0.5, Transform.Identity ()));
  BEGIN
    (* 2.0, 0.0, 0.0, 0.5, 2.0, 3.0: 40000000 xor 3F000000 xor 40000000 xor 40400000 *)
    IO.Put ("Transform.Hash: " & Fmt.Int (Transform.Hash (tr), 16) & " "
              & Fmt.Int (Transform.Hash (Transform.Identity ()), 16) & "\n");
  END Hashes;

BEGIN
  Floats ();
  Addresses ();
  Swaps ();
  Errors ();
  Processes ();
  Hashes ();
END Main.
