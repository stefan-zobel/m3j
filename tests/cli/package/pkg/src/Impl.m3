(* The module of Pub and Priv, named after neither, as M3PathElemWin32 of
   m3tk exports M3PathElemOS and M3PathElemOSPriv. *)

MODULE Impl EXPORTS Pub, Priv;

PROCEDURE Name (): TEXT =
  BEGIN
    RETURN "Impl";
  END Name;

BEGIN
  Sep := '/';
END Impl.
