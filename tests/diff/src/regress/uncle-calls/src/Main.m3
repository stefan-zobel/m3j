(* A nested procedure calls a procedure declared beside one that encloses
   it, which uses the variables of a procedure further out: the static
   links of the procedures between must pass the frame on. *)

MODULE Main;
IMPORT IO, Fmt;

PROCEDURE Put (t: TEXT; v: INTEGER) =
  BEGIN
    IO.Put(t & " " & Fmt.Int(v) & "\n");
  END Put;

PROCEDURE P0 (x: INTEGER) =
  PROCEDURE P1 () =
    VAR v1 := 3;
    PROCEDURE P2 () =
      BEGIN
        P3(v1);
      END P2;
    BEGIN
      P2();
    END P1;
  PROCEDURE P3 (a: INTEGER) =
    BEGIN
      Put("p3", a + x);
      INC(x);
    END P3;
  BEGIN
    P1();
    Put("p0", x);
  END P0;

PROCEDURE Q0 (y: INTEGER) =
  PROCEDURE Q1 () =
    PROCEDURE Q2 () =
      PROCEDURE Q3 () =
        BEGIN
          Q4(5);
        END Q3;
      BEGIN
        Q3();
      END Q2;
    BEGIN
      Q2();
    END Q1;
  PROCEDURE Q4 (a: INTEGER) =
    BEGIN
      Put("q4", a * y);
      y := y + 1;
    END Q4;
  BEGIN
    Q1();
    Q1();
    Put("q0", y);
  END Q0;

PROCEDURE R0 (z: INTEGER) =
  PROCEDURE R1 () =
    VAR w := 2;
    PROCEDURE R2 () =
      BEGIN
        R3(w);
      END R2;
    BEGIN
      R2();
    END R1;
  PROCEDURE R3 (a: INTEGER) =
    PROCEDURE R4 () =
      BEGIN
        Put("r4", a + z);
      END R4;
    BEGIN
      R4();
    END R3;
  BEGIN
    R1();
  END R0;

BEGIN
  P0(10);
  Q0(3);
  R0(7);
END Main.
