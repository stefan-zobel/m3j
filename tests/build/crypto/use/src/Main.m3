(* The package crypto through m3build. *)

MODULE Main;

IMPORT Aead, Crypto, Digest, Fmt, Hex, IO, Signature, Text, UUID;

<*FATAL Crypto.Error*>

VAR
  key := Crypto.Random (32);
  pair := Signature.GenerateEd25519 ();
  sig := Signature.Sign (Signature.Algorithm.Ed25519, pair.private, "data");

BEGIN
  IO.Put (Hex.Encode (Text.Sub (Digest.Of (Digest.Algorithm.SHA256, "abc"), 0, 4)) & " "
            & Aead.Open (Aead.Algorithm.AesGcm, key, Aead.Seal (Aead.Algorithm.AesGcm, key, "linked")) & " "
            & Fmt.Int (UUID.Version (UUID.NewV7 ())) & " "
            & Fmt.Bool (Signature.Verify (Signature.Algorithm.Ed25519, pair.public, "data", sig)) & "\n");
END Main.
