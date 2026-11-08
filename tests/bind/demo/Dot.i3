(* The Java class demo.Dot, as bound by m3jbind. *)

<*EXTERNAL "demo.Dot":JAVA*> INTERFACE Dot;

IMPORT Dot_Kind, Java, JavaObject;

TYPE T <: JavaObject.T;

<*EXTERNAL "<init>()V"*>
PROCEDURE New (): T;

<*EXTERNAL "<init>(II)V"*>
PROCEDURE New2 (a1: Java.Int; a2: Java.Int): T;

<*EXTERNAL "New(J)J"*>
PROCEDURE New_ (self: T; a1: INTEGER): INTEGER;

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

<*EXTERNAL "half()D"*>
PROCEDURE half (self: T): LONGREAL;

<*EXTERNAL "kind()Ldemo/Dot$Kind;"*>
PROCEDURE kind (self: T): Dot_Kind.T;

<*EXTERNAL "parse(Ljava/lang/String;)Ldemo/Dot;"*>
PROCEDURE parse (a1: TEXT): T;

<*EXTERNAL "plus(I)Ldemo/Dot;"*>
PROCEDURE plus_int (self: T; a1: Java.Int): T;

<*EXTERNAL "plus(Ldemo/Dot;)Ldemo/Dot;"*>
PROCEDURE plus_Dot (self: T; a1: T): T;

<*EXTERNAL "sum([J)J"*>
PROCEDURE sum (a1: REFANY): INTEGER;

<*EXTERNAL "tag(C)C"*>
PROCEDURE tag (self: T; a1: Java.Char): Java.Char;

<*EXTERNAL "toString()Ljava/lang/String;"*>
PROCEDURE toString (self: T): TEXT;

<*EXTERNAL "END"*> VAR END_: Java.Int;
<*EXTERNAL*> VAR LABEL: TEXT;
<*EXTERNAL*> VAR LIMIT: INTEGER;
<*EXTERNAL*> VAR ORIGIN: T;
<*EXTERNAL "count"*> VAR count_: Java.Int;

END Dot.
