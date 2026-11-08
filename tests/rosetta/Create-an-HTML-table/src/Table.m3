(* Rosetta Code, task Create-an-HTML-table: https://rosettacode.org/wiki/Create_an_HTML_table (GNU Free Documentation License 1.3) *)
MODULE Table EXPORTS Main;

IMPORT IO, Random;
FROM Fmt IMPORT Int, F;

BEGIN
     IO.Put("<table style=\"text-align:center; "
       & "border: 1px solid\"><th></th>"
       & "<th>X</th><th>Y</th><th><Z</th>");
     WITH rand = NEW(Random.Default).init() DO
     FOR I := 0 TO 3 DO
          IO.Put(F("<tr><th>%s</th><td>%s</td>"
         & "<td>%s</td><td>%s</td></tr>",
           Int(I),
           Int(rand.integer(0, 1000)),
           Int(rand.integer(0, 1000)),
           Int(rand.integer(0, 1000))));
     END;
     END;
END Table.
