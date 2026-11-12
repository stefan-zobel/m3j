# feedreader

A reader of news feeds, written in Modula-3 and compiled by m3j: a web
application that fetches RSS and Atom feeds in the background, keeps them
in SQLite, and shows them in the browser, with a search, a JSON API and
live updates. It is meant to show what the libraries of m3j are for, in a
program of about 4,700 lines that does something useful.

It reads RSS 2.0 (and 0.91), RSS 1.0 (RDF) and Atom 1.0, as real feeds
write them: tried on some 80 feeds of news sites, blogs, podcasts and
software projects in English, German, French, Italian, Spanish and
Japanese.

## Build and run

It needs m3j (see the [README](../../README.md) of m3j) and, once, the
JDBC driver of SQLite, which `fetch-driver.sh` fetches from Maven Central
into `lib` and checks against its SHA-256 (it is under the Apache License
2.0 and not part of m3j). In this directory (`M3J` stands for the
directory of m3j):

```sh
sh fetch-driver.sh
java -jar $M3J/m3build.jar
JVM/feedreader serve                 # JVM\feedreader.cmd serve on Windows
```

Then open http://127.0.0.1:8080/ in a browser. The first visit makes the
first user; the page then offers a few feeds to start with (`demo.opml`:
Hacker News, LWN, the blogs of Go and Rust, the commits of CM3, the tags of
OpenJDK, arXiv on programming languages, the featured articles of
Wikipedia and xkcd). New entries show up while the page is open. The
database is the file `feedreader.db` in the current directory.

The server listens on 127.0.0.1 only: a user of the reader can make it
fetch any URL, so on a network it would be a way into that network.
`-listen HOST:PORT` changes that for those who know what they do.

## The command line

```sh
JVM/feedreader fetch https://go.dev/blog/feed.atom     # a feed, read and shown, without the database
JVM/feedreader check URL...                             # one line per feed: what was understood
echo 'a-password' | JVM/feedreader adduser alice        # a user, for the web interface too
JVM/feedreader add https://jvns.ca/                     # a site: the feed it names is found
JVM/feedreader import subscriptions.opml
JVM/feedreader sync                                     # fetch what is due, once
JVM/feedreader list -q "rust compiler"                  # a search, with the words found in [brackets]
JVM/feedreader feeds                                    # how the fetching goes
JVM/feedreader export > subscriptions.opml
```

`JVM/feedreader` without arguments lists all commands and options.

## The API

The page of the feeds makes a token for programs:

```sh
curl -H "Authorization: Bearer $TOKEN" 'http://127.0.0.1:8080/api/entries?q=modula&n=5'
curl -H "Authorization: Bearer $TOKEN" -d '{"url": "https://xkcd.com/"}' http://127.0.0.1:8080/api/feeds
curl -H "Authorization: Bearer $TOKEN" -d '{"read": true}' http://127.0.0.1:8080/api/entries/42
```

`src/Api.i3` describes all of it.

## A tour of the sources

| Module | What it does | With |
|---|---|---|
| `Feed` | reads the three formats into one form; XHTML content back into markup | `XmlReader` |
| `FeedDate` | the dates that feeds write, also those that break the rules | `RFC3339`, `RFC5322`, `Regexp` |
| `HtmlText` | the text of HTML, for lists and search | `Regexp`, `Html` |
| `Links` | relative links, spaces and Unicode in URLs, nothing but `http` and `https` | `URI`, `Utf8` |
| `Fetch` | a GET with the validators of the last fetch, gzip and a limit of 32 MB | `HTTPClient`, `Deflate` |
| `Discover` | the feeds that a web page names | `Regexp` |
| `Fetcher` | a planner and worker threads; a failing feed is tried again after 15 minutes, doubling up to a day | `Thread` |
| `Store` | all SQL: schema, entries kept once per feed, full-text search with snippets | `SQL`, SQLite with FTS5 |
| `Auth` | passwords with PBKDF2, random tokens of which only hashes are kept | `Kdf`, `Digest`, `Crypto`, `Base64` |
| `Opml` | lists of subscriptions, read and written | `XmlReader`, `XmlWriter` |
| `Web`, `Page` | the pages, the session cookie, the token in each form, uploads, Server-Sent Events | `HTTPServer`, `HTTPCookie`, `Multipart`, `Html` |
| `Api` | the JSON API | `JsonReader`, `JsonWriter` |
| `Events`, `Log`, `TextBuf` | news for the pages that wait, messages, texts in pieces | |
| `Main` | the command line | |

`src/static` and `demo.opml` are in the program, through m3bundle; the
`m3makefile` names the driver with `java_classpath` and passes
`--enable-native-access=ALL-UNNAMED`, which the driver of SQLite needs, to
java with `java_option`.

The HTML of an entry comes from a stranger. It never becomes part of a
page: it is shown in an `iframe` from a path of its own, in a sandbox in
which no script runs, by the attribute of the frame and by the
`Content-Security-Policy` of its response. That policy also keeps out
the images of other servers for a user who does not want them, since an
image tells its server who read what when: a setting on the page of the
feeds, on at first, which a link of each entry overrides. Pages are not
compressed, as they hold the token of the forms next to text that others
choose.

## Tests

`tools/feedreader_test.py` in m3j builds the reader and tests it without
the internet: the parser on the feeds in `tests/feedreader/feeds`, the
fetching against a server of the test (304, redirects, gzip, errors,
limits, a slow server, a feed that changes), the web interface, the API,
the live updates, 60 feeds fetched by 8 threads, and the end of the
server by Ctrl+C.

## Limits

- No sanitizer of HTML: the sandbox stands in for it.
- No JSON Feed, no WebSub, no podcasts beyond a link to the episode.
- Entries are kept until their feed is unsubscribed.
- Ctrl+C ends the server in order (the database is closed); a second
  Ctrl+C ends it at once, and SQLite keeps its data whole then too.
- Tested on Windows; the scripts for POSIX are those that m3build writes.
