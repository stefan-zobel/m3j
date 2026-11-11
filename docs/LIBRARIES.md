# The libraries of m3j

`m3j.jar` holds a library of Modula-3, so a program needs no cm3
installation for these units:

- from CM3, with the changes that [`lib/README.md`](../lib/README.md) lists:
  the units of m3core and libm3 that the tests use (`IO`, `Rd`, `Wr`, `Fmt`,
  `Scan`, `Text`, `Thread`, `Random`, the common generics and others), the
  package tcp (`TCP`, `IP`, `ConnRW`, `ConnMsgRW`), the package netobj
  (`NetObj`, `TCPNetObj`, `StubLib` and the rest of the runtime of network
  objects), the package sysutils (`System`, `FSUtils`, `TextUtils` and
  others), the package libbuf (`Buf`, `Wx`) and the package patternmatching
  (`RegEx`, `Glob`, `GlobTree`);
- m3j's own, described below: the package tls (`TLS`), the package http
  (`HTTP`, `HTTPServer`, `HTTPClient`, `HTTPCookie`, `Multipart`), the
  package xml (`XmlReader`, `XmlWriter`), the package regexp (`Regexp`), the
  package json (`JsonWriter`, `JsonReader`), the package zip (`Deflate`,
  `Zip`), the package encoding (`Hex`, `Utf8`, `Base64`, `URI`, `RFC3339`),
  the package crypto (`Crypto`, `Digest`, `Aead`, `Kdf`, `Signature`,
  `X25519`, `UUID`), the package ssh (`SSH`, `SFTP`) and the package sql
  (`SQL`);
- the bindings of the Java platform in the package java; any other Java
  class is bound as [`USING.md`](USING.md#java-apis) describes.

# TLS

The package tls (not part of cm3) has connections of TLS 1.2 and 1.3. A
`TLS.T` is a `ConnFD.T` around another one, so `ConnRW` and `ConnMsgRW`
work on it as on a `TCP.T`, with the same timeouts and alerts. The protocol,
the check of certificates and the trusted certificates are those of the
Java platform:

```modula-3
ctx := TLS.NewContext ();                          (* a client: the platform's CAs *)
t := TLS.Client (ctx, TCP.Connect (ep), "www.example.org");
rd := ConnRW.NewRd (t);  wr := ConnRW.NewWr (t);

ctx := TLS.NewContext ("server.p12", "password");  (* a server, a thread per connection *)
t := TLS.Server (ctx, TCP.Accept (connector));
```

The certificate of a server is always checked against the name that the
client gives. The interface `TLS` describes contexts of other certificates,
certificates of clients and ALPN. A connection that ends without the notice
of closing of the protocol raises `Rd.Failure` with `TLS.Truncated`, for its
data may be incomplete. Sessions are not resumed: each connection has a full
handshake.

# HTTP

The package http (not part of cm3, whose `m3-www/http` is from the 1990s) has
a server for HTTP/1.1 and a client that also speaks HTTP/2:

```modula-3
TYPE Hello = HTTPServer.Handler OBJECT OVERRIDES request := Greet END;

PROCEDURE Greet (h: Hello; req: HTTPServer.Request; resp: HTTPServer.Response)
  RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    resp.headers.set ("Content-Type", "text/plain");
    Wr.PutText (resp.body (), "Hello\n");
  END Greet;

server := HTTPServer.New ();
server.register (NEW (Hello));
server.serve (TCP.NewConnector (IP.Endpoint {IP.NullAddress, 8080}));  (* with a TLS.Context: HTTPS *)

client := HTTPClient.New ();
page := HTTPClient.GetText (client, "https://www.example.org/");
```

The server is written in safe Modula-3 on `ConnFD.T`, so it runs over TCP
or TLS. It serves one connection per thread with keep-alive and `chunked`,
checks requests strictly against RFC 9110 and 9112, and has limits and
timeouts for each stage of a request; `HTTPServer.Options` lists them. The
client is the one of the Java platform, with its pool of connections,
redirects, proxies and TLS. Neither end does WebSockets or HTTP/2 upgrades
over plain TCP, and the server does not speak HTTP/2.

Both ends do `Content-Encoding: gzip`. A handler calls `resp.compress ()`
before `resp.body ()` for what is worth compressing, and the server does so if
the client accepts it. The client offers gzip and undoes it by itself, which
`Options.decompress := FALSE` turns off; `resp.decoded` tells what happened.

`HTTPCookie` makes `Set-Cookie` fields and refuses the cookies that browsers
would drop, reads them back from the `Cookie` fields of a request, and parses
`Set-Cookie` the lenient way of RFC 6265 for a client. `Multipart` reads and
writes forms in `multipart/form-data` as streams:

```modula-3
r := Multipart.NewReader (req.body, Multipart.Boundary (req.headers.get ("Content-Type")));
WHILE r.next (p) DO
  IF p.filename # NIL THEN Store (p.body) END;   (* p.filename comes from the client *)
END;
```

The reader has limits for the number of parts, their heads and bodies and the
whole, and rejects what readers are known to read differently; its errors are
those of the server (`HTTP.BadMessage`, `HTTP.TooLarge`), so a handler that
lets them pass answers 400 or 413.

# XML

The package xml reads XML one token at a time, so a document can be larger
than the memory of the program:

```modula-3
r := XmlReader.FromRd (rd);
r.root ("catalog");
WHILE r.child () DO
  IF r.is ("book") THEN
    id := r.toInt (r.need ("id"));
    WHILE r.child () DO
      IF r.is ("title") THEN title := r.content () ELSE r.skip () END;
    END;
  ELSE
    r.skip ();
  END;
END;
```

`r.next ()` goes through the tokens one by one instead, for a program that
wants to see all of them. Namespaces are supported, text comes in one piece
however it is written (CDATA, references), and encodings are found by the
rules of XML. It is meant for documents from anywhere: a document type
declaration is skipped unread, so that nothing outside the document is ever
fetched and entities cannot expand, and depth, attributes, names and tokens
have limits (`XmlReader.Options`). The first error of a document ends the
reader, and `XmlReader.Error` says what went wrong and where. The parser is
the one of the Java platform.

`XmlWriter` is the other direction, written piece by piece to a `Wr.T`:

```modula-3
w := XmlWriter.ToWr (wr, XmlWriter.Options {indent := "  "});
w.start ("catalog", uri := "urn:books");
w.start ("book");
w.attr ("id", Fmt.Int (id));
w.element ("title", title);
w.end ();
w.end ();
w.finish ();
```

It checks names, characters and the order of the calls, so that the result is
a well-formed document that reads back as it was written; the first error ends
the writer. Children take the namespace of their parent, and prefixes are
declared where they are needed. The output is UTF-8. It is plain Modula-3,
without the writer of the Java platform.

# Regular expressions

The package regexp has the regular expressions of the Java platform, with
the syntax of `java.util.regex.Pattern`: groups and named groups,
alternation, greedy, lazy and possessive quantifiers, lookahead and
lookbehind, back references:

```modula-3
re := Regexp.Compile ("(?<key>\\w+)\\s*=\\s*(?<value>[^;]*)");
m := Regexp.Find (re, text);
WHILE m # NIL DO
  IO.Put (m.group (1) & " is " & m.group (re.groupNumber ("value")) & "\n");
  m := Regexp.Next (m);
END;
t := Regexp.Replace (re, text, "${value}=${key}");
parts := Regexp.Split (Regexp.Compile ("\\s*,\\s*"), line);
```

The engine backtracks, and for some patterns its time grows exponentially
with the text. So a search may read the characters of the text only so
many times (`limit` of `Compile`, `Regexp.DefaultLimit` unless given, 0 for
no limit); a search over its budget, or one that overflows the stack,
raises `Regexp.TooComplex`, after some 100 milliseconds on a short text.
Indices count the `WIDECHAR`s of a text, as `Text` does. For programs of
cm3, the package patternmatching has `RegEx` and `Glob` too.

# JSON

`JsonWriter` writes JSON piece by piece to a `Wr.T`, without a tree:

```modula-3
w := JsonWriter.ToWr (wr, JsonWriter.Options {indent := "  "});
w.object ();
w.array ("books");
w.object ();
w.int (id, "id");
w.text (title, "title");
w.end ();
w.end ();
w.end ();
w.finish ();
```

It escapes strings and checks the order of the calls, so that the result is a
valid document (RFC 8259) that reads back as it was written. A surrogate
without its partner, an infinity or a NaN and a key that the object has
already are errors, and the first error ends the writer. The output is UTF-8.

`JsonReader` reads JSON piece by piece from a `Rd.T`, so a large document needs
no more memory than its longest string:

```modula-3
r := JsonReader.FromRd (rd);
r.object ();
WHILE r.member () DO
  IF r.is ("books") THEN
    r.array ();
    WHILE r.element () DO
      r.object ();
      WHILE r.member () DO
        IF r.is ("id") THEN id := r.int ();
        ELSIF r.is ("title") THEN title := r.text ();
        ELSE r.skip ();
        END;
      END;
    END;
  ELSE
    r.skip ();
  END;
END;
r.finish ();
```

`peek` tells what comes next, for a program that does not know the format. It
follows RFC 8259 strictly and decodes escapes, so `text` gives the characters
of the string. An error of the document names its line, column and offset and
ends the reader; a value of the wrong kind or a number that does not fit takes
nothing, and the program can go on. `Open` reads a file, `FromText` a text.

# Compression

The library zip reads and writes the formats of the Deflate family and ZIP
archives, with the compressor of the Java platform. `Deflate` makes readers and
writers, so it works with everything that takes a `Rd.T` or a `Wr.T`:

```modula-3
wr := Deflate.NewWr (FileWr.Open ("a.gz"), o := Deflate.WriteOptions {closeTarget := TRUE});
Wr.PutText (wr, "Hello\n");
Wr.Close (wr);

rd := Deflate.NewRd (FileRd.Open ("a.gz"), o := Deflate.ReadOptions {closeSource := TRUE});
text := Rd.GetText (rd, LAST (CARDINAL));
Rd.Close (rd);
```

The format is an argument, `Format.Gzip` by default, or `Format.Zlib` or
`Format.Raw`. Damaged data is an `Rd.Failure` with `Deflate.Corrupt` or
`Deflate.Truncated`. Small input can expand enormously, so for data from
elsewhere set `maxBytes`, beyond which the reader raises `Deflate.TooLarge`.
`Wr.Flush` passes on what has been written so far, for streams that the other
side reads as they come.

`Zip` reads an archive from a file and writes one to any `Wr.T`:

```modula-3
r := Zip.Open ("a.zip");
FOR i := 0 TO r.count () - 1 DO
  IF Zip.SafeName (r.entry (i).name) THEN
    text := Rd.GetText (r.open (i, maxBytes := 1000000), 1000000);
  END;
END;
r.close ();

w := Zip.ToWr (FileWr.Open ("b.zip"));
Wr.PutText (w.add ("hello.txt"), "Hello\n");
w.finish ();
```

The checksum of each entry is compared at its end. The name of an entry can be
something like `../x`: a program that makes files from names must check them,
and `SafeName` does. Encrypted archives and two entries of one name are refused.

# Encodings

The library encoding has the small formats that protocols are made of, in plain
Modula-3: `Hex`, `Utf8`, `Base64`, `URI` and `RFC3339`. Except for `Utf8`, a
`TEXT` here stands for bytes, one character for each byte, so text in Unicode
goes through `Utf8.Encode` first. Input from elsewhere that is malformed makes
a procedure return `FALSE`:

```modula-3
IF Base64.Decode (token, data, Base64.Alphabet.Url, Base64.Padding.Forbidden) THEN ... END;

IF URI.Parse ("https://example.com/a/b?x=1&y=a+b", u)
     AND URI.ParseQuery (u.query, pairs) THEN
  target := URI.Format (URI.Resolve (u, relative));
END;

IF RFC3339.Parse ("1996-12-19T16:39:57-08:00", time, offset) THEN ... END;
text := RFC3339.Format (Time.Now (), digits := 3);   (* in UTC, ending in Z *)
```

`Base64.Decode` accepts each value in one form only. `URI.Parse` follows the
grammar of RFC 3986 and refuses what it does not allow, such as a backslash. The
parts of a `URI.T` keep their escapes, and a part that is missing is `NIL`,
which is different from one that is empty.

# Cryptography

The library crypto has digests and HMAC (`Digest`: MD5, SHA-1, SHA-2, SHA-3),
authenticated encryption (`Aead`: AES-GCM, ChaCha20-Poly1305), key derivation
(`Kdf`: PBKDF2, HKDF), signatures (`Signature`: Ed25519, RSA with PKCS #1 v1.5
and PSS, ECDSA on P-256, P-384 and P-521), key agreement (`X25519`), random
bytes and identifiers (`Crypto`, `UUID` of versions 4 and 7). The primitives
are those of the Java platform; the library chooses the parameters and adds the
checks that the platform leaves out. As in encoding, a `TEXT` stands for bytes:

```modula-3
key := Crypto.Random (32);
sealed := Aead.Seal (Aead.Algorithm.ChaCha20Poly1305, key, Utf8.Encode (message));

mac := Digest.Hmac (Digest.Algorithm.SHA256, secret, data);
IF NOT Crypto.Equal (mac, received) THEN ... END;

pub := Signature.PublicFromPem (pem);
ok := Signature.Verify (Signature.Algorithm.EcdsaP256Sha256, pub, data, sig);
```

Errors raise `Crypto.Error` with an atom such as `Crypto.BadTag`. A message
that is not authentic gives nothing of its plain text; a signature that is
malformed in any way is not valid; a key that does not fit the algorithm is an
error, which a program that takes the algorithm from a message must treat as a
refusal. RSA keys have at least 2048 bits, and points of curves are checked,
also those from PEM texts. PEM texts are SubjectPublicKeyInfo and unencrypted
PKCS #8; other forms are `Crypto.Unsupported`.

# SSH and SFTP

The library ssh runs commands on a server (`SSH`) and works with its files
(`SFTP`), on a copy of JSch in `m3j-ssh.jar`, next to the runtime, which
programs without SSH do not carry. A connection checks the key of the
server against `~/.ssh/known_hosts` unless the options say otherwise
(`AcceptNew` takes and records a key on first use, `Fingerprint` compares with
a given `SHA256:...`); there is no option to take any key. The user logs in
with a key (a file or its text, also encrypted) or a password:

```modula-3
VAR o := SSH.Defaults;
...
o.user := "deploy";
o.keyFile := "/home/deploy/.ssh/id_ed25519";
s := SSH.Connect ("build.example.org", o);
status := s.run ("make -C /srv/app", out, err);

f := SFTP.Open (s);
wr := f.create ("/srv/app/config.json");
Wr.PutText (wr, config);
Wr.Close (wr);
f.close ();
s.close ();
```

`start` gives a running command with a writer for its input and readers for
its outputs. Errors raise `SSH.Error` with an atom such as
`SSH.HostKeyChanged` or `SFTP.NoSuchFile`; a timeout and an alert end the
wait at once. Paths are characters and are sent as UTF-8; the contents of
files and the outputs of commands are bytes, as in encoding.

# Databases: SQL

The library sql works with relational databases through JDBC, the
interface of the Java platform to their drivers. m3j brings no driver: the
program names the jar of its driver as Java classes, with
`java_classpath ("../lib/postgresql.jar")` in its m3makefile or `-cp` of
the compiler. Values go to the database as parameters, never as text of a
statement:

```modula-3
VAR o := SQL.Defaults;
...
o.user := "shop";
o.password := password;
db := SQL.Connect ("jdbc:postgresql://db.example.org/shop", o);
s := db.prepare ("SELECT name, price FROM item WHERE price < ?");
s.bindDecimal (1, "20.00");
rows := s.query ();
WHILE rows.next () DO
  IO.Put (rows.text (1) & " " & rows.decimal (2) & "\n");
END;
db.close ();
```

Parameters and columns count from 1, as in SQL. Errors raise `SQL.Error`
with a kind such as `SQL.Kind.Constraint` and the SQLSTATE, code and text
of the driver. An alert or the `timeout` of the options (none by default)
cancels the statement that runs. Dates and times without a time zone stay
as the database has them; `instant` and `bindInstant` give a `Time.T` for a
`TIMESTAMP WITH TIME ZONE`.
