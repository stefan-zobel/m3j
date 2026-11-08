MODULE G;

IMPORT IO, Fmt, A;

PROCEDURE Put (t: TEXT; v: INTEGER) =
  BEGIN
    IO.Put(t & " " & Fmt.Int(v) & "\n");
  END Put;

PROCEDURE Dump () =
  BEGIN
    Put("arr", A.arr[0] + A.arr[1]);
    Put("r", A.r.a + A.r.b);
    Put("x", A.x);
  END Dump;

BEGIN
END G.
