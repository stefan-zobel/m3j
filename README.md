# m3j - Modula-3 on the JVM

m3j compiles Modula-3 to Java bytecode. Its front end is a faithful Java
port of the front end of [Critical Mass Modula-3 (CM3)](https://github.com/modula3/cm3),
its back end new code on the JDK's ClassFile API. CM3 is the reference for everything.

- **Front end** (`m3j.front`, with the parts of `m3middle` and `m3core` it
  needs): on CM3's own libraries and on the m3tests suite it produces the
  same intermediate code and the same diagnostics as cm3 (see
  [`MODULA3-JVM.md`](MODULA3-JVM.md)).
- **Back end** (`m3j.jvm`) and **runtime** (`m3j.rt`, and Java versions of
  the library interfaces in `m3.i`): the safe language, threads and the
  parts of libm3 and m3core that safe programs use; the other library
  modules are compiled from their sources, which m3j brings (`lib`). The
  program tests of m3tests (`p0`-`p2`, `r0`) give the expected output
  except for a few listed cases (`tools/run_jvm.py`), and every class of
  the compile tests `c0` and `c1` verifies. The design is in
  [`docs/BACKEND.md`](docs/BACKEND.md).

UNSAFE modules are not supported.

## Build

Needs a POSIX shell (Linux, macOS, WSL or Git Bash on Windows) and JDK 25 or later.

```sh
JDK25=/path/to/jdk-25 sh build.sh
```

writes `m3j.jar` (compiler, runtime and the Modula-3 library), `m3j-runtime.jar`
(only what a compiled program needs), `m3j-ssh.jar` (JSch for the library
ssh, which only programs that use it need), `m3bundle.jar` (cm3's m3bundle,
compiled by m3j), `m3build.jar` (the build system, see below) with its
configuration `m3j.cfg`, `stubgen.jar` (cm3's stub generator of network
objects, compiled by m3j; `M3J_STUBGEN=no` skips it), `m3_lang_server.jar`
(cm3's language server, built by m3build; `M3J_LSP=no` skips it) and the
AOT cache `m3j.aot`, which saves start time of the compiler and of
programs run with `m3j.jar` (`M3J_AOT=no` skips it).

## Use

The library (the units of m3core and libm3 that the tests use: `IO`, `Rd`,
`Wr`, `Fmt`, `Scan`, `Text`, `Thread`, `Random`, the common generics and
others, the package tcp: `TCP`, `IP`, `ConnRW`, `ConnMsgRW`, the package
tls: `TLS`, the package http: `HTTP`, `HTTPServer`, `HTTPClient`, `HTTPCookie`, `Multipart`, the package xml: `XmlReader`, `XmlWriter`, the package zip: `Deflate`, `Zip`, the package encoding: `Hex`, `Utf8`, `Base64`, `URI`, `RFC3339`, the package crypto: `Crypto`, `Digest`, `Aead`, `Kdf`, `Signature`, `X25519`, `UUID`, the package ssh: `SSH`, `SFTP`, the package sql: `SQL`, the package netobj: `NetObj`, `TCPNetObj`, `StubLib` and the rest of the runtime of
network objects, the package sysutils: `System`, `FSUtils`,
`TextUtils` and others, the package patternmatching: `RegEx`, `Glob`,
`GlobTree`, and the packages json and libbuf: `Json`, `JsonWriter`, `JsonReader`, `Buf`,
`Wx`, and the bindings of the Java platform in the package java) is in
the jar; for these no cm3 installation is needed:

```sh
java -XX:AOTCache=m3j.aot -cp m3j.jar m3j.jvm.Compile Main.m3 -d out
java -cp m3j-runtime.jar:out m3.Main
java -cp m3j.jar m3j.jvm.Compile Main.m3 -run ARG...   # in memory
```

A program that uses the library ssh also needs `m3j-ssh.jar`:
`java -cp m3j-runtime.jar:m3j-ssh.jar:out m3.Main`. m3build adds it by
itself, and `-run` has it in `m3j.jar`.

`-I DIR` adds a source directory, `-pkg DIR` another package: a package
directory of a cm3 installation (`CM3DIR/pkg/NAME/AMD64_NT`) or a source
package (its units in `DIR/src`, as in `tests/pkg`), `-d prog.jar` writes a
jar. With `-lib`, modules that are not given are optional: they are
compiled if they can be (safe ones), as the modules of libraries are.
`-cm3 DIR` adds the packages of a cm3 installation after the library of
m3j, for the other units of m3core and libm3. `-version` prints the
version of m3j.

Files become resources of a program as in cm3, through a bundle that
m3bundle writes (the interface `NAME.i3` and the module `NAME.m3`, which
are compiled with the program; `Bundle.Get (NAME.Get (), ELEMENT)` and
`Rsrc` give the contents of a file):

```sh
java -jar m3bundle.jar -name NAME -element ELEMENT FILE ...   # m3j-runtime.jar next to it
```

### TLS

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

### HTTP

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

### XML

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

### JSON

`Json`, from cm3, parses a text into a tree. `JsonWriter` writes JSON piece by
piece to a `Wr.T`, without a tree:

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

### Compression

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

### Encodings

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

### Cryptography

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

### SSH and SFTP

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

### Databases: SQL

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

### Java APIs

An interface can stand for a Java class. The program then calls its
methods, holds its objects in typed variables and catches its exceptions,
and the compiler checks the declarations against the class:

```modula-3
<*EXTERNAL "java.util.ArrayList":JAVA*> INTERFACE ArrayList;

IMPORT Java, JavaObject;

TYPE T <: JavaObject.T;                         (* the class java.util.ArrayList *)

PROCEDURE New (): T;                            (* a constructor: New or "<init>" *)
PROCEDURE add (self: T; e: REFANY): BOOLEAN;    (* the first formal is the receiver *)
PROCEDURE size (self: T): Java.Int;

<*EXTERNAL "add(ILjava/lang/Object;)V"*>        (* a name and a descriptor pick an overload *)
PROCEDURE addAt (self: T; i: Java.Int; e: REFANY);

END ArrayList.
```

A static method has no receiver, and a static field is a variable
(`<*EXTERNAL*> VAR MAX_VALUE: Java.Int;`; assigning to a `final` field is
an error). One procedure of an ordinary interface is bound by naming the
class too: `<*EXTERNAL "java.lang.Math.max":JAVA*> PROCEDURE Max (a, b:
INTEGER): INTEGER;`. Bindings are declared in interfaces only; their
formals are VALUE formals, except arrays. A bound procedure is a procedure
value like any other: it can be assigned, compared (`=` holds across the
whole program), passed to Java as a callback and be the default of a
method. The class or member is looked up when the interface is compiled: a
class that is missing, a method without a fitting overload or an ambiguous
one (the message lists the descriptors) is an error at the declaration,
even if the program never calls it.

The public instance fields of a class are the fields of its type `T`:

```modula-3
<*EXTERNAL "java.awt.Point":JAVA*> INTERFACE Point;

IMPORT Java, JavaObject;

TYPE T <: JavaObject.T OBJECT x, y: Java.Int END;

PROCEDURE New (x, y: Java.Int): T;

END Point.
```

`p.x := 3` is then a `putfield`. The type of a field must fit as for a
static field, it has no default value, assigning to a `final` field is an
error, and the list has fields only: methods are procedures of the
interface.

| Modula-3 | Java |
|---|---|
| `INTEGER`, `LONGINT`, subranges | `long`, or `int`, `short`, `byte` with a check of the range (`Java.Int`, `Java.Short`, `Java.Byte` need none) |
| `BOOLEAN` | `boolean` |
| `CHAR`, `WIDECHAR` (`Java.Char`) | `char` |
| `REAL` | `float`, or `double` for a parameter |
| `LONGREAL` | `double` |
| `TEXT` | `String` or a supertype of it (`self: TEXT` in a binding of `String`) |
| the type `T` of a binding | the class, or a supertype of it |
| `REFANY` | any class, `Object` without a cast; `NIL` is `null` |
| a procedure type | an interface with one abstract method |
| `ARRAY OF E`, `ARRAY [a .. b] OF E` (VALUE, READONLY, VAR) | the Java array of the same elements, as a copy (see below) |
| `REF ARRAY OF E`, as a result | the Java array, as a copy; `null` is `NIL` |

Results convert the other way round, with a cast where the Java type is
wider, and `void` is a proper procedure. `ISTYPE`, `NARROW` and `TYPECASE`
on the types of bindings are `instanceof`; a Java object is not a `ROOT`.
Scalars are not boxed for a parameter of type `Object`. `Java.i3` has the
boxes (`FromInt`, `FromLong`, `ToInt`), copies for Java arrays held in a
`REFANY`, and `ToText`, `Equals`, `HashCode`, `ClassName` and `IsInstance`
for any reference.

An array of one dimension is passed as a copy, and a VAR array is copied
back after the call; the cost grows with the array. The elements must be
of the same kind in both languages: `INTEGER` is `long`, `Java.Int` `int`,
`Java.Short` `short`, `Java.Byte` and `CHAR` `byte`, `WIDECHAR` `char`,
`BOOLEAN` `boolean`, `REAL` `float`, `LONGREAL` `double`, `TEXT` `String`,
`REFANY` `Object` and the `T` of a binding its class. Java passes an array
to a procedure or method as a copy in a VALUE or READONLY `ARRAY OF E`, and
gets a copy of a `REF ARRAY OF E` that it returns. Other arrays (several
dimensions, records) stay in a `REFANY`.

An object type can have the `T` of a binding as its supertype, and its
objects are then objects of the Java class:

```modula-3
TYPE
  Worker = JThread.T OBJECT                 (* extends java.lang.Thread *)
    count := 0;
  METHODS
    run () := Run;                          (* overrides Thread.run *)
    report (): TEXT := Report;              (* of Modula-3 only *)
  END;
  Chars = CharSequence.T OBJECT             (* implements java.lang.CharSequence *)
    s: TEXT;
  METHODS
    length (): Java.Int := Length;
    charAt (i: Java.Int): Java.Char := CharAt;
    subSequence (a, b: Java.Int): CharSequence.T := Sub;
    toString (): TEXT := ToText;
  END;
```

A method that the type declares (in `METHODS`) with the name of a public or
protected Java method that is not final or static implements it; its
signature must fit, or it is an error, and one that fits several overloads
equally is one too. Each name is one method of a type: for two overloads of
one Java method, declare the second in a subtype. `OVERRIDES`, `NEW (T, m :=
P)` and further subtypes reach Java as well. The class must not be final,
and needs a public or protected constructor without parameters, which is
the one that runs; `NEW` is an error if an abstract Java method is left
without an implementation. There are no calls of the overridden Java method
(`super`), a type has one Java supertype (a class or an interface), and
such a type is a subtype of `REFANY` but not of `ROOT`. Its objects cannot
be pickled. `NEW (Point.T)` of the type of a binding is an error: call a
constructor of the binding. Exceptions pass through Java as they do from a
callback.

A Java exception that ends a call is `Java.Exception (t)`, with the
`JavaThrowable.T` as its argument. Like `RuntimeError.E` it is implicit and
need not be in a `RAISES` clause. Those that stand for a checked runtime
error (`null` as receiver, an index out of bounds, a bad cast, overflow of
the stack, no memory left) are that runtime error instead.

A procedure passed for a parameter of a functional interface (`Runnable`,
`Comparator`) is called by Java, also on Java's threads, with its arguments
and result converted as above; `NIL` stays `null`, and it can be a nested
procedure. A Modula-3 exception that it raises passes through Java and
comes back to the call that made Java run it. On a thread that Java
started there is no such call: an exception or a checked runtime error
that nothing handles ends the program, as in a thread of `Thread.Fork`.
What a Java library catches itself stays with it: `Future.get` reports the
exception of a task of `ExecutorService.submit` as its cause.

Classes that are not those of the JDK are given with `-cp` (separated as
in a class path of the system). A jar written with `-d` finds them through
its manifest:

```sh
java -cp m3j.jar m3j.jvm.Compile -cp tool.jar Main.m3 -d prog.jar
```

m3jbind writes the interfaces. Each class gets `DIR/Name.i3` with its
public constructors (`New`, `New2`, ...), the public methods that it
declares and its public constants, each with its descriptor:

```sh
java -cp m3j.jar m3j.jvm.Bind [-cp PATH] [-d DIR] [-prefix P] [-name a.b.C=Name]... a.b.C ...
```

Overloaded methods have the names of their parameter types as a suffix
(`add_int_Object`), and a name that is not an identifier of Modula-3 or is
taken gets a changed name with the Java name in the pragma (`END_`). The
interface is named after the class (`Map_Entry` for a nested class); a
name that the library has too is reported, and the interface hides that of
the library, so give another with `-prefix` or `-name`. A parameter or
result has the type of a binding if its class is bound, in the run or by
an interface in `DIR`; any other class is `REFANY`, and so is a class that
would make two interfaces import each other (`File` and `Path`). `T` is a
subtype of the type of the nearest bound superclass or superinterface. A
binding has the methods the class declares, so bind the supertypes too
for what it inherits. The public instance fields that a class declares are
the field list of `T`; a field is left out if its name is no identifier of
Modula-3 (with a warning) or if no type of Modula-3 can be assigned to it.
A Java array of a type with a name in Modula-3 is a `READONLY ARRAY OF E`
parameter and a `REF ARRAY OF E` result, with `CHAR` for `byte`; any other
array is `REFANY`. Where Java fills an array, change `READONLY` to `VAR`.
Parameters for callbacks are `REFANY` in the output: to pass a procedure,
write the formal with a procedure type by hand.

Not supported: several Java interfaces for one type, calls of the
overridden Java method (`super`), and arrays of several dimensions other
than in a `REFANY`.

### Packages: m3build

m3build builds a package from its `m3makefile`, as `cm3` does: it is cm3's
quake interpreter and the parts of cm3's driver that read m3makefiles,
compiled by m3j, with the compiler of m3j in place of cm3's back ends. In
the directory of a package (with `src/m3makefile`):

```sh
java -jar m3build.jar                        # -build: into the directory JVM
java -jar m3build.jar -ship -root DIR        # install into DIR/pkg, DIR/bin
java -jar m3build.jar -clean                 # or -realclean
```

The m3makefile is that of cm3: `import`, `module`, `interface`,
`implementation` and the other units (hidden or visible), the generic
instances of the library (`Table`, `Sequence`, `List`, `Sorted_table` and
the others) and `build_generic_intf`, `template`, `include_dir`, `resource`
and `bundle` (with m3bundle.jar), `netobj` (with stubgen.jar), `program`,
`library`, `m3overrides` with `-override`, and the rest of quake. A
program becomes `JVM/NAME.jar` with the scripts `JVM/NAME` (POSIX) and
`JVM/NAME.cmd` (Windows) that run it; it is compiled again when one of its
sources changed. A library is checked (its UNSAFE modules are left out)
and installed as its sources and its `.M3EXPORTS`, since m3j compiles a
program from the sources of the libraries it uses. Packages are found in
the installation (`-root DIR` or `M3J_ROOT`), then among those that m3j
brings (`lib`: m3core, libm3, tcp, netobj, sysutils, json, libbuf, patternmatching, java, zip, encoding, crypto, ssh, sql), then in a cm3
installation (`-cm3 DIR` or `CM3_ROOT`). C sources and the options of
cm3's back ends are not supported; `-exports` writes only the
`.M3EXPORTS`. `java -jar m3build.jar -help` lists the options.

A package with bindings of Java classes does `import ("java")` and names
the jars or directories of the classes that are not those of the JDK:
`java_classpath ("../lib/foo.jar")`, relative to the file that says so.
The compiler gets them, the program runs with them, and it is compiled
again when one of the jars changes. A library passes its jars on: each is
installed beside the `.M3EXPORTS`, which names it, so that a package that
imports the library, directly or through another one, has it on its class
path. A directory cannot be passed on (pack it into a jar), and two jars
of a library must differ in name. The option `-pkg DIR` of the compiler
reads such a `.M3EXPORTS` too.

### Editors: the language server

`m3_lang_server.jar` is cm3's language server for Modula-3, compiled by
m3j: through the Language Server Protocol, an editor gets the errors and
warnings of a file while it is edited, the type of an identifier (hover)
and the place of its declaration and of the definition of its type, from
m3tk, cm3's toolkit for Modula-3. It is started as
`java -jar m3_lang_server.jar` (with `m3j.jar` and `m3build.jar` next to
it) and checks the files of packages (below a directory `src` with an
`m3makefile`), whose imports it gets from m3build, and those of the library
(`lib`). [`tools/langserver/vscode`](tools/langserver/vscode)
is an extension for Visual Studio Code (syntax highlighting and a client
of the server, without other packages); [`tools/langserver/README.md`](tools/langserver/README.md) has
the settings for Neovim, Emacs (Eglot), Helix and Kate.

A compiled program follows the conventions of the operating system that it
runs on (`Pathname`, `OSConfig`); `-Dm3j.os=POSIX` or `-Dm3j.os=WIN32` for
the JVM chooses one. The constants `Wr.EOL`, `OSConfig.LineSep`, `ArcSep`
and `PathSep` are fixed when the program is compiled: they are those of the
system that the compiler runs on (or that `-Dm3j.os` names for it).

## Tests

These tests need nothing but the tree (their inputs and the recorded
results of cm3 are in `tests`):

```sh
JDK25=/path/to/jdk-25 python3 tools/run_jvm.py p0 p1 p2 r0      # m3tests programs
JDK25=/path/to/jdk-25 python3 tools/run_jvm.py --compile c0 c1
JDK25=/path/to/jdk-25 python3 tools/diff_test.py                # against cm3: library tests, examples, Rosetta Code,
                                                                # edge cases, random expressions
JDK25=/path/to/jdk-25 python3 tools/split_test.py               # methods too large for the JIT
JDK25=/path/to/jdk-25 python3 tools/m3front_test.py             # CM3's front end, compiled by m3j, against m3j's
JDK25=/path/to/jdk-25 python3 tools/bundle_test.py              # m3bundle, compiled by m3j, against cm3's
JDK25=/path/to/jdk-25 python3 tools/build_test.py quake cases   # m3build: cm3's quake tests, packages of tests/build
JDK25=/path/to/jdk-25 python3 tools/bind_test.py                # m3jbind: bindings of Java classes
JDK25=/path/to/jdk-25 python3 tools/zip_test.py                 # the library zip, against Python's zlib, gzip, zipfile
JDK25=/path/to/jdk-25 python3 tools/lsp_test.py                 # the language server (and the client of the
                                                                # extension for Visual Studio Code, with node)
JDK25=/path/to/jdk-25 python3 tools/perf.py                     # benchmarks (tests/bench)
JDK25=/path/to/jdk-25 python3 tools/dump_test.py fingerprint
JDK25=/path/to/jdk-25 python3 tools/dump_test.py middle
```

These need a cm3 installation or cm3 sources outside the tree:

```sh
JDK25=/path/to/jdk-25 python3 tools/pkg_gate.py --cm3 CM3DIR    # compile all packages of a cm3 installation
JDK25=/path/to/jdk-25 python3 tools/build_test.py exports packages --cm3 CM3DIR --src CM3SRC
                                                                # m3build on the m3makefiles of cm3's packages
JDK25=/path/to/jdk-25 python3 tools/perf.py --native            # also cm3 -O (cm3 on the PATH)
JDK25=/path/to/jdk-25 python3 tools/diff_test.py --record       # record the results of cm3 (cm3 on the PATH)
JDK25=/path/to/jdk-25 python3 tools/expr_fuzz.py --seeds 1-100  # random expressions against cm3 (cm3 on the PATH)
JDK25=/path/to/jdk-25 python3 tools/prog_fuzz.py --seeds 1-100  # random programs against cm3 (cm3 on the PATH)
JDK25=/path/to/jdk-25 python3 tools/cg_test.py DIR...   # needs cm3's m3cgcat
JDK25=/path/to/jdk-25 python3 tools/front_test.py PACKAGE... [--tree DIR]   # packages built by cm3
```

The coverage of m3j by these tests is measured with JaCoCo (unpacked below
`download`):

```sh
JDK25=/path/to/jdk-25 python3 tools/coverage.py --out DIR     # report in DIR/html
```

`tests/front` holds cm3 packages that cover parts of the language that
CM3's libraries and m3tests do not use; build each of them with
`cm3 -keep -commands > build.log 2>&1` (the package `safe` also with
`-DNOCHECKS`, in a copy) and pass the package directories to
`front_test.py`.

[`tools/README.md`](tools/README.md) lists the tools, [`docs/PORTING.md`](docs/PORTING.md) the rules for porting
CM3 sources, [`docs/BACKEND.md`](docs/BACKEND.md) the design of the JVM back end.

## License

The sources written for m3j are distributed under the MIT license in
[`LICENSE`](LICENSE); their head says so (`SPDX-License-Identifier: MIT`). The
sources ported or copied from CM3 keep their original license; where m3j
changed one, a line in its head says that the changes are distributed
under the MIT license in [`LICENSE`](LICENSE).

The ported CM3 sources are distributed under their original licenses, in
[`licenses/`](licenses) ([`COPYRIGHT-DEC`](licenses/COPYRIGHT-DEC) for the code from Digital Equipment
Corporation, [`COPYRIGHT-CMASS`](licenses/COPYRIGHT-CMASS) for the code from Critical Mass,
[`COPYRIGHT-COLUMBIA`](licenses/COPYRIGHT-COLUMBIA) for the code from Columbia University,
[`COPYRIGHT-LUCENT`](licenses/COPYRIGHT-LUCENT) for the notice of David Gay's conversion code,
[`MIT-BATES`](licenses/MIT-BATES) for `ArrayExpr`, [`MIT-MCKINNA`](licenses/MIT-MCKINNA) for the language server,
[`COPYRIGHT-XEROX`](licenses/COPYRIGHT-XEROX) for `RegEx`, [`BSD-POLSTRA`](licenses/BSD-POLSTRA) for `Glob` and
`GlobTree`, and [`BSD-UCB`](licenses/BSD-UCB) for the function fnmatch of FreeBSD, after which `Glob` is written).
The sources copied from CM3 keep their notices: `lib` (m3core, libm3, tcp,
netobj, sysutils, libbuf, patternmatching, and json with the notice of its author,
[`lib/json/COPYRIGHT-BSD`](lib/json/COPYRIGHT-BSD), see [`lib/README.md`](lib/README.md)), `tools/langserver/src`
(CM3's language server), `tests/m3tests`,
`tests/diff/src`, `tests/pkg`, `tests/net`, `tests/m3front` (CM3's front end, see
[`tests/m3front/README.md`](tests/m3front/README.md)) and `tests/stubgen` (CM3's stub generator for
network objects, see [`tests/stubgen/README.md`](tests/stubgen/README.md)), and `tools/m3bundle` (CM3's m3bundle).

`src/vendor/jsch` is a part of JSch (https://github.com/mwiede/jsch), taken by
`tools/jsch_import.py` with the package renamed to `m3j.jsch`; it is distributed
under its license in [`BSD-JSCH`](licenses/BSD-JSCH), its bcrypt under [`ISC-JBCRYPT`](licenses/ISC-JBCRYPT),
in `m3j-ssh.jar` and `m3j.jar`.
Every file keeps its notice; the changes, the package name, are distributed
under the MIT license in [`LICENSE`](LICENSE). `src/vendor/jsch/ORIGIN` names the release.
