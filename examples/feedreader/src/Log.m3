(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Log;

IMPORT FeedDate, Stdio, Thread, Time, Utf8, Wr;

PROCEDURE Put (message: TEXT) =
  BEGIN
    IF quiet THEN RETURN END;
    TRY
      Wr.PutText (Stdio.stderr, Utf8.Encode (FeedDate.Format (Time.Now ()) & " " & message & "\n"));
      Wr.Flush (Stdio.stderr);
    EXCEPT
    | Wr.Failure, Thread.Alerted => (* nowhere to say it *)
    END;
  END Put;

BEGIN
END Log.
