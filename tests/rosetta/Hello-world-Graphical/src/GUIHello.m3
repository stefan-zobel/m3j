(* Rosetta Code, task Hello-world-Graphical: https://rosettacode.org/wiki/Hello_world_Graphical (GNU Free Documentation License 1.3) *)
MODULE GUIHello EXPORTS Main;

IMPORT TextVBT, Trestle;

<*FATAL ANY*>

VAR v := TextVBT.New("Goodbye, World!");

BEGIN
  Trestle.Install(v);
  Trestle.AwaitDelete(v);
END GUIHello.
