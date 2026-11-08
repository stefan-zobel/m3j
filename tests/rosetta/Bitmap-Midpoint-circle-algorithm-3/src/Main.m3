(* Rosetta Code, task Bitmap-Midpoint-circle-algorithm: https://rosettacode.org/wiki/Bitmap_Midpoint_circle_algorithm (GNU Free Documentation License 1.3) *)
MODULE Main;

IMPORT Circle, Bitmap, PPM;

VAR testpic: Bitmap.T;

BEGIN
  testpic := Bitmap.NewImage(32, 32);
  Bitmap.Fill(testpic, Bitmap.White);
  Circle.Draw(testpic, Bitmap.Point{16, 16}, 10, Bitmap.Black);
  PPM.Create("testpic.ppm", testpic);
END Main.
