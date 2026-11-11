// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * The pull parser behind the module XmlReader of m3j, a layer on the StAX
 * parser of the Java platform that the interface M3jXml binds.
 *
 * <p>The layer exists because the parser cannot be used as it comes. By
 * default it reads external entities, so document type declarations are
 * always switched off here. After an error its next call fails in
 * undefined ways, so the first error ends this object. It prints to
 * standard error when it meets invalid bytes, so this class decodes the
 * input itself. It hands out text in pieces of unbounded number, or
 * collects it without limit, so this class joins the pieces and counts.
 *
 * <p>An object belongs to one thread. A Modula-3 exception that the source
 * of the input raises passes through the parser to the caller of next.
 */
public final class Xml {

    /** The source of the bytes of a document. */
    public interface Fill {
        /** At most max bytes, at least one; null at the end of the input. */
        byte[] fill(int max);
    }

    // the numbers of XmlReader.Kind
    private static final int START = 1;
    private static final int END = 2;
    private static final int TEXT = 3;
    private static final int COMMENT = 4;
    private static final int PI = 5;
    private static final int EOF = 6;

    // the numbers of XmlReader.Problem
    private static final int MALFORMED = 0;
    private static final int ENCODING = 1;
    private static final int LIMIT = 2;
    private static final int CLOSED = 5;

    private static final int NO_EVENT = -1;
    private static final int FAILED = -1;

    private static final int CHUNK = 8192;
    private static final int MAX_DECLARATION = 4096;
    private static final String NAMESPACE_ERROR = "http://www.w3.org/TR/1999/REC-xml-names-19990114#";
    private static final Pattern DECLARATION = Pattern.compile(
            "<\\?xml\\s+version\\s*=\\s*(?:\"[^\"]*\"|'[^']*')\\s+encoding\\s*=\\s*[\"']([A-Za-z][A-Za-z0-9._-]*)[\"']");
    private static final byte[] DECLARATION_START = "<?xml".getBytes(StandardCharsets.US_ASCII);

    /** A token that is longer than the limit. */
    private static final class TooLong extends IOException {
        private static final long serialVersionUID = 1L;
    }

    /** An encoding that cannot be used. */
    private static final class BadEncoding extends IOException {
        private static final long serialVersionUID = 1L;

        BadEncoding(String message) {
            super(message);
        }
    }

    /** The characters of a document, from a text or from bytes that it decodes. */
    private static final class Source extends Reader {
        private final Fill fill;
        private final long limit;
        private CharBuffer chars;
        private long count;

        private byte[] head = new byte[CHUNK];
        private int headLength;
        private int skip;
        private CharsetDecoder decoder;
        private ByteBuffer bytes;
        private boolean atEnd;
        private boolean done;
        private boolean afterReturn;

        /** The place of the next character that this source gives. */
        int line = 1;
        int column = 1;

        /** The name of the encoding, null if the document is a text or nothing is read yet. */
        String encoding;
        /** The exception that reading ended with, or null. */
        IOException failure;
        /** The exception of the source of the bytes, which is not this layer's to handle. */
        RuntimeException passing;

        Source(Fill fill, long limit) {
            this.fill = fill;
            this.limit = limit;
            chars = CharBuffer.allocate(CHUNK);
            chars.limit(0);
        }

        Source(String text, long limit) {
            this.fill = null;
            this.limit = limit;
            boolean mark = !text.isEmpty() && text.charAt(0) == '\uFEFF';
            chars = CharBuffer.wrap(text, mark ? 1 : 0, text.length());
        }

        /** Starts a count of the characters of one event of the parser. */
        void mark() {
            count = 0;
        }

        @Override
        public int read(char[] b, int off, int n) throws IOException {
            if (n == 0) {
                return 0;
            }
            try {
                if (!chars.hasRemaining() && !refill()) {
                    return -1;
                }
                int k = Math.min(n, chars.remaining());
                chars.get(b, off, k);
                if (fill != null) {
                    track(b, off, k);
                }
                count += k;
                // the parser buffers, so what it asks for during one event
                // can be a buffer more than the event needs
                if (limit > 0 && count > limit + CHUNK) {
                    throw new TooLong();
                }
                return k;
            } catch (IOException e) {
                failure = e;
                throw e;
            }
        }

        /** Keeps the place of the next character: the parser cannot say where bad bytes are. */
        private void track(char[] b, int off, int k) {
            for (int i = off; i < off + k; i++) {
                char c = b[i];
                if (c == '\r') {
                    line++;
                    column = 1;
                    afterReturn = true;
                } else {
                    if (c != '\n') {
                        column++;
                    } else {
                        column = 1;
                        if (!afterReturn) {
                            line++;
                        }
                    }
                    afterReturn = false;
                }
            }
        }

        @Override
        public void close() {
            // the input belongs to the caller
        }

        private boolean refill() throws IOException {
            if (fill == null || done) {
                return false;
            }
            if (decoder == null) {
                start();
            }
            chars.clear();
            while (true) {
                CoderResult r = decoder.decode(bytes, chars, atEnd);
                if (r.isError()) {
                    if (chars.position() == 0) {
                        r.throwException();
                    }
                    break; // first the good characters; the error comes back
                }
                if (r.isOverflow()) {
                    break;
                }
                if (atEnd) {
                    decoder.flush(chars);
                    done = true;
                    break;
                }
                if (chars.position() > 0) {
                    break; // what the source has now, without waiting for more
                }
                byte[] b = pull();
                if (b != null) {
                    add(b);
                }
            }
            chars.flip();
            return chars.hasRemaining();
        }

        private void add(byte[] b) {
            if (!bytes.hasRemaining()) {
                bytes = ByteBuffer.wrap(b);
            } else {
                ByteBuffer all = ByteBuffer.allocate(bytes.remaining() + b.length);
                all.put(bytes).put(b).flip();
                bytes = all;
            }
        }

        private byte[] pull() {
            if (atEnd) {
                return null;
            }
            byte[] b;
            try {
                b = fill.fill(CHUNK);
            } catch (RuntimeException e) {
                passing = e;
                throw e;
            }
            if (b == null || b.length == 0) {
                atEnd = true;
                return null;
            }
            return b;
        }

        private void start() throws IOException {
            Charset cs = detect();
            encoding = cs.name();
            decoder = cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            bytes = ByteBuffer.wrap(head, skip, headLength - skip);
            head = null;
        }

        /** True if at least n bytes have come, waiting for them if necessary. */
        private boolean have(int n) {
            while (headLength < n) {
                if (!more()) {
                    return false;
                }
            }
            return true;
        }

        private boolean more() {
            byte[] b = pull();
            if (b == null) {
                return false;
            }
            if (headLength + b.length > head.length) {
                head = Arrays.copyOf(head, Math.max(head.length * 2, headLength + b.length));
            }
            System.arraycopy(b, 0, head, headLength, b.length);
            headLength += b.length;
            return true;
        }

        private int at(int i) {
            return head[i] & 0xFF;
        }

        /**
         * The encoding of the document, by appendix F of XML 1.0, as early
         * as the bytes allow: a source that is a connection may have
         * nothing more to give yet.
         */
        private Charset detect() throws IOException {
            if (!have(1)) {
                return StandardCharsets.UTF_8;
            }
            switch (at(0)) {
            case 0xEF:
                if (have(3) && at(1) == 0xBB && at(2) == 0xBF) {
                    skip = 3;
                }
                return StandardCharsets.UTF_8;
            case 0xFE:
                if (have(2) && at(1) == 0xFF) {
                    skip = 2;
                    return StandardCharsets.UTF_16BE;
                }
                return StandardCharsets.UTF_8;
            case 0xFF:
                if (have(2) && at(1) == 0xFE) {
                    if (have(4) && at(2) == 0 && at(3) == 0) {
                        skip = 4;
                        return named("UTF-32LE");
                    }
                    skip = 2;
                    return StandardCharsets.UTF_16LE;
                }
                return StandardCharsets.UTF_8;
            case 0:
                if (have(2) && at(1) == '<') {
                    return StandardCharsets.UTF_16BE;
                }
                if (have(4) && at(1) == 0 && at(2) == 0xFE && at(3) == 0xFF) {
                    skip = 4;
                    return named("UTF-32BE");
                }
                if (have(4) && at(1) == 0 && at(2) == 0 && at(3) == '<') {
                    return named("UTF-32BE");
                }
                return StandardCharsets.UTF_8;
            case '<':
                if (have(2) && at(1) == 0) {
                    if (have(4) && at(2) == 0 && at(3) == 0) {
                        return named("UTF-32LE");
                    }
                    return StandardCharsets.UTF_16LE;
                }
                if (headLength >= 2 && at(1) == '?') {
                    return declared();
                }
                return StandardCharsets.UTF_8;
            default:
                return StandardCharsets.UTF_8;
            }
        }

        /** The encoding that the declaration of a document names, or UTF-8 without one. */
        private Charset declared() throws IOException {
            if (!have(6) || at(2) != 'x' || at(3) != 'm' || at(4) != 'l' || !isSpace(at(5))) {
                return StandardCharsets.UTF_8;
            }
            int end;
            while ((end = endOfDeclaration()) < 0) {
                if (headLength >= MAX_DECLARATION || !more()) {
                    return StandardCharsets.UTF_8;
                }
            }
            Matcher m = DECLARATION.matcher(new String(head, 0, end, StandardCharsets.ISO_8859_1));
            if (!m.lookingAt()) {
                return StandardCharsets.UTF_8;
            }
            Charset cs = named(m.group(1));
            if (!cs.canEncode() || !Arrays.equals("<?xml".getBytes(cs), DECLARATION_START)) {
                throw new BadEncoding("the bytes of the document do not fit its declared encoding " + m.group(1));
            }
            return cs;
        }

        private int endOfDeclaration() {
            for (int i = 0; i + 1 < headLength; i++) {
                if (head[i] == '?' && head[i + 1] == '>') {
                    return i;
                }
            }
            return -1;
        }

        private static boolean isSpace(int c) {
            return c == ' ' || c == '\t' || c == '\r' || c == '\n';
        }

        private static Charset named(String name) throws BadEncoding {
            try {
                return Charset.forName(name);
            } catch (IllegalArgumentException e) {
                throw new BadEncoding("the encoding " + name + " is not supported");
            }
        }
    }

    private final Source source;
    private final boolean comments;
    private final boolean instructions;
    private final boolean whitespace;
    private final boolean rejectDoctype;
    private final long maxDepth;
    private final long maxAttributes;
    private final long maxNameChars;
    private final long maxTokenChars;

    private XMLStreamReader reader;
    private int pending = NO_EVENT;
    private boolean atEnd;
    private boolean closed;
    private String value = "";
    private String target = "";
    private String version;

    private boolean located;
    private int line = 1;
    private int column = 1;
    private int offset;

    private int problem;
    private int errorLine;
    private int errorColumn;
    private String errorMessage;

    private Xml(Source source, boolean comments, boolean instructions, boolean whitespace,
            boolean rejectDoctype, long maxDepth, long maxAttributes, long maxNameChars, long maxTokenChars) {
        this.source = source;
        this.comments = comments;
        this.instructions = instructions;
        this.whitespace = whitespace;
        this.rejectDoctype = rejectDoctype;
        this.maxDepth = maxDepth;
        this.maxAttributes = maxAttributes;
        this.maxNameChars = maxNameChars;
        this.maxTokenChars = maxTokenChars;
    }

    /** A parser of the bytes that fill gives. A limit of 0 means no limit. */
    public static Xml fromFill(Fill fill, boolean comments, boolean instructions, boolean whitespace,
            boolean rejectDoctype, long maxDepth, long maxAttributes, long maxNameChars, long maxTokenChars) {
        return new Xml(new Source(fill, maxTokenChars), comments, instructions, whitespace, rejectDoctype,
                maxDepth, maxAttributes, maxNameChars, maxTokenChars);
    }

    /** A parser of the characters of text. A limit of 0 means no limit. */
    public static Xml fromText(String text, boolean comments, boolean instructions, boolean whitespace,
            boolean rejectDoctype, long maxDepth, long maxAttributes, long maxNameChars, long maxTokenChars) {
        return new Xml(new Source(text, maxTokenChars), comments, instructions, whitespace, rejectDoctype,
                maxDepth, maxAttributes, maxNameChars, maxTokenChars);
    }

    /** The next event as a number of XmlReader.Kind, or FAILED: see problem. */
    public int next() {
        if (closed) {
            return fail(CLOSED, "the reader is closed or has failed");
        }
        closed = true; // unless the call ends normally
        int k;
        try {
            k = advance();
        } catch (XMLStreamException e) {
            return failure(e);
        } catch (RuntimeException e) {
            if (e == source.passing) {
                throw e;
            }
            return fail(MALFORMED, "the parser failed: " + e.getClass().getName());
        }
        if (k != FAILED) {
            closed = false;
        }
        return k;
    }

    private int advance() throws XMLStreamException {
        if (atEnd) {
            return EOF;
        }
        if (reader == null) {
            open();
        }
        located = false;
        String first = null;
        StringBuilder more = null;
        long total = 0;
        while (true) {
            int e = pending;
            if (e != NO_EVENT) {
                pending = NO_EVENT;
            } else {
                source.mark();
                e = reader.next();
            }
            switch (e) {
            case XMLStreamConstants.CHARACTERS:
            case XMLStreamConstants.CDATA:
            case XMLStreamConstants.SPACE:
                String piece = reader.getText();
                total += piece.length();
                if (tooLong(total)) {
                    return tokenTooLong();
                }
                if (first == null) {
                    first = piece;
                } else {
                    if (more == null) {
                        more = new StringBuilder(first);
                    }
                    more.append(piece);
                }
                locate();
                continue;
            case XMLStreamConstants.COMMENT:
                if (!comments) {
                    continue;
                }
                break;
            case XMLStreamConstants.PROCESSING_INSTRUCTION:
                if (!instructions) {
                    continue;
                }
                break;
            case XMLStreamConstants.DTD:
                if (rejectDoctype) {
                    locate();
                    return fail(MALFORMED, "the document has a document type declaration");
                }
                continue;
            case XMLStreamConstants.START_ELEMENT:
            case XMLStreamConstants.END_ELEMENT:
            case XMLStreamConstants.END_DOCUMENT:
                break;
            default:
                continue;
            }
            if (first != null) {
                String text = more == null ? first : more.toString();
                if (!text.isEmpty() && (whitespace || !isBlank(text))) {
                    value = text;
                    pending = e;
                    return TEXT;
                }
                first = null;
                more = null;
                total = 0;
                located = false;
            }
            switch (e) {
            case XMLStreamConstants.START_ELEMENT:
                return START;
            case XMLStreamConstants.END_ELEMENT:
                return END;
            case XMLStreamConstants.COMMENT:
                value = reader.getText();
                return tooLong(value.length()) ? tokenTooLong() : COMMENT;
            case XMLStreamConstants.PROCESSING_INSTRUCTION:
                target = reader.getPITarget();
                value = orEmpty(reader.getPIData());
                return tooLong(value.length()) ? tokenTooLong() : PI;
            default:
                atEnd = true;
                return EOF;
            }
        }
    }

    private void open() throws XMLStreamException {
        XMLInputFactory f = XMLInputFactory.newDefaultFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
        f.setProperty("jdk.xml.maxElementDepth", Long.toString(maxDepth));
        f.setProperty("jdk.xml.elementAttributeLimit", Long.toString(maxAttributes));
        f.setProperty("jdk.xml.maxXMLNameLimit", Long.toString(maxNameChars));
        // Without a DTD the only entities are the five predefined ones, whose
        // references the JDK counts against these limits all the same: a
        // text with more than 100,000 of "&lt;" would fail. The limit of a
        // token applies instead.
        f.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "0");
        f.setProperty("jdk.xml.totalEntitySizeLimit", "0");
        reader = f.createXMLStreamReader(source);
        version = reader.getVersion();
    }

    private boolean tooLong(long n) {
        return maxTokenChars > 0 && n > maxTokenChars;
    }

    private int tokenTooLong() {
        return fail(LIMIT, "a token is longer than the limit of " + maxTokenChars + " characters");
    }

    private static boolean isBlank(String t) {
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                return false;
            }
        }
        return true;
    }

    private void locate() {
        Location l = reader.getLocation();
        line = l.getLineNumber();
        column = l.getColumnNumber();
        offset = l.getCharacterOffset();
        located = true;
    }

    private int fail(int problem, String message) {
        this.problem = problem;
        errorLine = line;
        errorColumn = column;
        errorMessage = message;
        closed = true;
        return FAILED;
    }

    private int failure(XMLStreamException e) {
        Location l = e.getLocation();
        if (l != null) {
            line = l.getLineNumber();
            column = l.getColumnNumber();
            offset = l.getCharacterOffset();
        }
        IOException cause = source.failure;
        if (cause instanceof TooLong) {
            return tokenTooLong();
        }
        if (cause instanceof BadEncoding || cause instanceof CharacterCodingException) {
            line = source.line;
            column = source.column;
            return fail(ENCODING, cause instanceof BadEncoding ? cause.getMessage()
                    : "the bytes are not valid in the encoding " + source.encoding);
        }
        String text = String.valueOf(e.getMessage());
        int i = text.indexOf("Message: ");
        if (i >= 0) {
            text = text.substring(i + "Message: ".length());
        }
        if (text.startsWith("JAXP0001")) {
            return fail(LIMIT, text);
        }
        return fail(MALFORMED, readable(text));
    }

    /** The parser gives the key and arguments of an error of namespaces as its message. */
    private static String readable(String text) {
        if (!text.startsWith(NAMESPACE_ERROR)) {
            return text;
        }
        String[] parts = text.substring(NAMESPACE_ERROR.length()).split("\\?", 2);
        String[] args = parts.length > 1 ? parts[1].split("&") : new String[0];
        String last = args.length > 0 ? args[args.length - 1] : "";
        return switch (parts[0]) {
        case "ElementPrefixUnbound" -> "the prefix of the element " + last + " is not bound to a namespace";
        case "AttributePrefixUnbound" -> "the prefix of the attribute " + last + " is not bound to a namespace";
        case "AttributeNotUnique", "AttributeNSNotUnique" -> "the attribute " + last + " is specified twice";
        default -> "a namespace constraint is violated: " + parts[0] + " " + String.join(", ", args);
        };
    }

    /** Ends this object. The input is the caller's to close. */
    public void close() {
        closed = true;
        if (reader != null) {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                // nothing is left to do
            }
        }
    }

    public int problem() {
        return problem;
    }

    public int errorLine() {
        return errorLine;
    }

    public int errorColumn() {
        return errorColumn;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String value() {
        return value;
    }

    public String target() {
        return target;
    }

    public String version() {
        return version;
    }

    public String encoding() {
        return source.encoding;
    }

    /** The line after the current token. */
    public int line() {
        update();
        return line;
    }

    public int column() {
        update();
        return column;
    }

    public int offset() {
        update();
        return offset;
    }

    private void update() {
        if (!located && !closed && reader != null) {
            locate();
        }
    }

    // On a start or end tag only: the caller knows the kind of the event.

    public String local() {
        return reader.getLocalName();
    }

    public String uri() {
        return orEmpty(reader.getNamespaceURI());
    }

    public String prefix() {
        return orEmpty(reader.getPrefix());
    }

    // On a start tag only.

    public int attrCount() {
        return reader.getAttributeCount();
    }

    public String attrLocal(int i) {
        return reader.getAttributeLocalName(i);
    }

    public String attrUri(int i) {
        return orEmpty(reader.getAttributeNamespace(i));
    }

    public String attrPrefix(int i) {
        return orEmpty(reader.getAttributePrefix(i));
    }

    public String attrValue(int i) {
        return reader.getAttributeValue(i);
    }

    /** The namespace that prefix stands for here, or null. */
    public String lookup(String prefix) {
        return reader.getNamespaceURI(prefix);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
