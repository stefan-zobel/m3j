(* The Java class demo.Dot, as bound by m3jbind. *)

<*EXTERNAL "demo.Dot":JAVA*> INTERFACE Dot;

IMPORT Dot_Kind, Java, JavaObject;

TYPE T <: JavaObject.T OBJECT
  id: Java.Int;
  next: T;
  note: TEXT;
  table: REFANY;
  weight: Java.Int;
END;

<*EXTERNAL "<init>()V"*>
PROCEDURE New (): T;

<*EXTERNAL "<init>(II)V"*>
PROCEDURE New2 (a1: Java.Int; a2: Java.Int): T;

<*EXTERNAL "New(J)J"*>
PROCEDURE New_ (self: T; a1: INTEGER): INTEGER;

<*EXTERNAL "applyTo(Ldemo/Bytes;[B)I"*>
PROCEDURE applyTo (a1: REFANY; READONLY a2: ARRAY OF CHAR): Java.Int;

<*EXTERNAL "compareTo(Ldemo/Dot;)I"*>
PROCEDURE compareTo (self: T; a1: T): Java.Int;

<*EXTERNAL "count()I"*>
PROCEDURE count (self: T): Java.Int;

<*EXTERNAL "fail()V"*>
PROCEDURE fail (self: T);

<*EXTERNAL "getX()I"*>
PROCEDURE getX (self: T): Java.Int;

<*EXTERNAL "getY()I"*>
PROCEDURE getY (self: T): Java.Int;

<*EXTERNAL "grid()[[J"*>
PROCEDURE grid (self: T): REFANY;

<*EXTERNAL "half()D"*>
PROCEDURE half (self: T): LONGREAL;

<*EXTERNAL "kind()Ldemo/Dot$Kind;"*>
PROCEDURE kind (self: T): Dot_Kind.T;

<*EXTERNAL "names()[Ljava/lang/String;"*>
PROCEDURE names (self: T): REF ARRAY OF TEXT;

<*EXTERNAL "pair()[Ldemo/Dot;"*>
PROCEDURE pair (self: T): REF ARRAY OF T;

<*EXTERNAL "parse(Ljava/lang/String;)Ldemo/Dot;"*>
PROCEDURE parse (a1: TEXT): T;

<*EXTERNAL "plus(I)Ldemo/Dot;"*>
PROCEDURE plus_int (self: T; a1: Java.Int): T;

<*EXTERNAL "plus(Ldemo/Dot;)Ldemo/Dot;"*>
PROCEDURE plus_Dot (self: T; a1: T): T;

<*EXTERNAL "sum([J)J"*>
PROCEDURE sum (READONLY a1: ARRAY OF INTEGER): INTEGER;

<*EXTERNAL "tag(C)C"*>
PROCEDURE tag (self: T; a1: Java.Char): Java.Char;

<*EXTERNAL "toString()Ljava/lang/String;"*>
PROCEDURE toString (self: T): TEXT;

<*EXTERNAL "total([I)I"*>
PROCEDURE total (READONLY a1: ARRAY OF Java.Int): Java.Int;

<*EXTERNAL "END"*> VAR END_: Java.Int;
<*EXTERNAL*> VAR LABEL: TEXT;
<*EXTERNAL*> VAR LIMIT: INTEGER;
<*EXTERNAL*> VAR ORIGIN: T;
<*EXTERNAL "count"*> VAR count_: Java.Int;

END Dot.
