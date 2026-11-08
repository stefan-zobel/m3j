// Java port of CM3's m3-sys/m3front/src/misc/Scanner.i3 and Scanner.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Original: portions Copyright 1996-2000, Critical Mass, Inc.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumSet;
import java.util.HashMap;
import m3j.middle.M3Buf;
import m3j.middle.M3ID;
import m3j.middle.TFloat;
import m3j.middle.TInt;
import m3j.middle.TWord;
import m3j.middle.Target;
import m3j.util.AssertError;
import m3j.util.RangeError;

// File: Scanner.i3
// Last modified on Tue Jul  5 10:45:56 PDT 1994 by kalsow

public final class Scanner {
    private Scanner() {}

    public static final class Symbol {
        public Token.T token;
        public int id;
        public M3String.T str;
        public M3WString.T wstr;
        public int[] int_ = TInt.newInt();
        public Target.Float float_ = new Target.Float();
        public long offset;
        public Value.T defn;

        public Symbol() {}

        public Symbol copy() {
            Symbol r = new Symbol();
            r.assign(this);
            return r;
        }

        /** Port: the assignment {@code this := x}. */
        public void assign(Symbol x) {
            token = x.token;
            id = x.id;
            str = x.str;
            wstr = x.wstr;
            TInt.assign(int_, x.int_);
            float_ = x.float_.copy();
            offset = x.offset;
            defn = x.defn;
        }
    }

    /** READONLY */
    public static Scanner.Symbol cur = new Symbol();

    public static long offset;

    /** READONLY */
    public static long nLines = 0;

    /** READONLY */
    public static long nPushed = 0;

    public static boolean in_main = false;

    private static final int MaxStack = 40;
    private static final long MaxLines = 100000;
    private static final int MaxString = 4095;
    private static final int MaxBuffer = 4096;
    private static final int UndoPad = 4;
    private static final char EOFChar = '\000';
    /** => only the first few ids may be reserved */
    private static final int MaxRsrvd = 250;

    // CharSet = SET OF CHAR;
    // Port: TK = Token.T.

    public static final class IDList {
        public int id;
        public IDList next;
    }

    // Port: CharMap = ARRAY CHAR OF BOOLEAN is boolean[256].

    private static final boolean[] WhiteSpace = new boolean[256];
    private static final boolean[] AlphaNumerics = new boolean[256];
    private static final boolean[] Digits = new boolean[256];
    private static final boolean[] OctalDigits = new boolean[256];
    private static final boolean[] HexDigits = new boolean[256];
    private static final boolean[] CommentAlert = new boolean[256];
    private static int missing;
    private static int LINE;
    private static int NOWARN;
    private static int PRAGMA;

    // Port: InputBufferIndex = [-UndoPad .. MaxBuffer+1]; InputBuffer is
    // byte[UndoPad + MaxBuffer + 2], the element i is at UndoPad + i, and a
    // File.Byte is (b & 0xFF).
    // Note: to avoid a range check in GetCh (and ScanComment), we don't
    // use "MaxBuffer-1" as the upper bound of the input buffer.

    // Port: StringBufferIndex = [0..MaxString];
    // StringBuffer = ARRAY StringBufferIndex OF CHAR is char[MaxString + 1].

    /** Port: File.T is java.io.InputStream. */
    public static final class FileState {
        public char ch;
        /** fileno * MaxLines + lineno */
        public long offs;
        public InputStream input;
        public byte[] buf;
        public int buf_ptr;
        public int buf_len;
        public Symbol sym = new Symbol();
        /** pragmas to ignore */
        public IDList ignore;
        public long accepted;
        public boolean is_main;
    }

    // Port: FileNames = REF ARRAY OF TEXT is String[].

    // never reset
    private static long nFiles;
    /** Port: TextIntTbl.Default is a HashMap. */
    private static final HashMap<String, Long> file_map = new HashMap<>();
    private static String[] files = new String[200];
    private static String[] local_files = new String[200];
    private static final Value.T[] reserved = new Value.T[MaxRsrvd + 1];

    // explicitly reset
    private static InputStream input;
    private static byte[] input_buf;
    private static int input_ptr;
    private static int input_len;
    private static char ch;
    private static IDList ignore;
    private static long accepted = 2;
    private static int tos;
    private static final FileState[] stack = newStack();
    private static final char[] buf = new char[MaxString + 1];

    private static FileState[] newStack() {
        FileState[] s = new FileState[MaxStack + 1];
        for (int i = 0; i < s.length; i++) {
            s[i] = new FileState();
        }
        return s;
    }

    public static void initialize() {
        missing = M3ID.add(M3.ids, "<missing id>");
        LINE = M3ID.add(M3.ids, "LINE");
        NOWARN = M3ID.add(M3.ids, "NOWARN");
        PRAGMA = M3ID.add(M3.ids, "PRAGMA");

        nFiles = 0;
        tos = 0;

        WhiteSpace[' '] = true;
        WhiteSpace['\n'] = true;
        WhiteSpace['\t'] = true;
        WhiteSpace['\r'] = true;
        WhiteSpace['\f'] = true;

        AlphaNumerics['_'] = true;
        for (char c = 'a'; c <= 'z'; c++) {
            AlphaNumerics[c] = true;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            AlphaNumerics[c] = true;
        }
        for (char c = '0'; c <= '9'; c++) {
            AlphaNumerics[c] = true;
        }

        for (char c = '0'; c <= '9'; c++) {
            Digits[c] = true;
        }

        for (char c = '0'; c <= '7'; c++) {
            OctalDigits[c] = true;
        }

        for (char c = '0'; c <= '9'; c++) {
            HexDigits[c] = true;
        }
        for (char c = 'a'; c <= 'f'; c++) {
            HexDigits[c] = true;
        }
        for (char c = 'A'; c <= 'F'; c++) {
            HexDigits[c] = true;
        }

        CommentAlert['*'] = true;
        CommentAlert['('] = true;
        CommentAlert[EOFChar] = true;
        CommentAlert['\n'] = true;
        CommentAlert['@'] = true;
    }

    public static void reset() {
        while ((tos > 0)) {
            pop();
            // Sic: Pop has already restored the enclosing file's input, so
            // that one is closed, not the popped one.
            Host.closeFile(input);
        }
        input_buf = null;
        input_len = -1; // not 0 ==> EOF
        input_ptr = 0;
        ch = ' ';
        ignore = null;
        accepted = 2;

        // interface variables
        offset = 0;
        nLines = 0;
        nPushed = 0;
        cur.token = Token.T.tEOF;
    }

    public static void push(String name, InputStream file, boolean is_main) {
        nPushed++;
        {
            FileState z = stack[tos];
            z.ch = ch;
            z.offs = offset;
            z.sym.assign(cur);
            z.input = input;
            z.buf = input_buf;
            z.buf_len = input_len;
            z.buf_ptr = input_ptr;
            z.ignore = ignore;
            z.accepted = accepted;
            z.is_main = in_main;
        }
        tos++;
        in_main = is_main;
        offset = fileNumber(name) * MaxLines + 1;
        ch = ' ';
        ignore = null;
        accepted = 2;
        input = file;
        input_ptr = 0;
        input_len = -1; // not 0 ==> EOF
        input_buf = stack[tos].buf;
        if ((input_buf == null)) {
            input_buf = new byte[UndoPad + MaxBuffer + 2];
            stack[tos].buf = input_buf;
        }
        if ((file != null)) {
            getToken(); // prime the input stream
        }
    }

    public static void pop() {
        tos--;
        {
            FileState z = stack[tos];
            ch = z.ch;
            offset = z.offs;
            cur.assign(z.sym);
            input = z.input;
            input_buf = z.buf;
            input_ptr = z.buf_ptr;
            input_len = z.buf_len;
            ignore = z.ignore;
            accepted = z.accepted;
            in_main = z.is_main;
        }
    }

    /** returns index into files of filename, adding it if it doesn't exist */
    private static long fileNumber(String filename) {
        long index = Math.floorDiv(offset, MaxLines);
        // often we'll hit the current file
        if ((index < files.length)
            && (files[(int) index] != null)
            && files[(int) index].equals(filename)) {
            return index;
        }

        {
            Long i = file_map.get(filename);
            if (i != null) {
                index = i;
                return index;
            }
        }

        // add a new one
        if ((nFiles >= files.length)) {
            expandFiles();
        }
        file_map.put(filename, nFiles);
        files[(int) nFiles] = filename;
        local_files[(int) nFiles] = null;
        nFiles++;
        return nFiles - 1;
    }

    private static void expandFiles() {
        int n = files.length;
        String[] new_global = new String[n + n];
        String[] new_local = new String[n + n];
        System.arraycopy(files, 0, new_global, 0, n);
        System.arraycopy(local_files, 0, new_local, 0, n);
        files = new_global;
        local_files = new_local;
    }

    /** Port: the VAR parameters file and line are one-element arrays. */
    public static void here(String[] file, long[] line) {
        file[0] = files[(int) Math.floorDiv(offset, MaxLines)];
        line[0] = Math.floorMod(offset, MaxLines);
    }

    /** Port: the VAR parameters file and line are one-element arrays. */
    public static void localHere(String[] file, long[] line) {
        int fnum = (int) Math.floorDiv(offset, MaxLines);
        if ((local_files[fnum] == null)) {
            local_files[fnum] = files[fnum];
        }
        file[0] = local_files[fnum];
        line[0] = Math.floorMod(offset, MaxLines);
    }

    public static boolean sameFile(long a, long b) {
        return Math.floorDiv(a, MaxLines) == Math.floorDiv(b, MaxLines);
    }

    public static void match(Token.T t) {
        if ((cur.token == t)) {
            getToken();
        } else {
            doFail("missing \'" + M3ID.toText(M3.ids, Token.name[t.ordinal()]) + "\'", t);
            if ((cur.token == t)) {
                getToken();
            }
        }
    }

    public static int matchID() {
        int id;
        if ((cur.token == Token.T.tIDENT)) {
            id = cur.id;
            getToken();
        } else {
            doFail("missing identifier", Token.T.tIDENT);
            if ((cur.token == Token.T.tIDENT)) {
                id = cur.id;
                getToken();
            } else {
                id = missing;
            }
        }
        return id;
    }

    public static void fail(String msg) {
        doFail(msg, Token.T.tEOF);
    }

    private static void doFail(String msg, Token.T stop) {
        String t;
        long[] i = new long[1];
        if ((accepted > 1)) {
            t = "syntax error: " + msg;
            switch (cur.token) {
                case tIDENT -> {
                    Error.ID(cur.id, t);
                }
                case tTEXTCONST -> {
                    Error.txt("\"" + M3String.toText(cur.str) + "\"", t);
                }
                case tWTEXTCONST -> {
                    Error.txt(M3WString.toLiteral(cur.wstr), t);
                }
                case tREALCONST, tLONGREALCONST, tEXTENDEDCONST -> {
                    Error.txt("<float>", t);
                }
                case tCARDCONST, tLONGCARDCONST -> {
                    if (TInt.toInt(cur.int_, i)) {
                        Error.int_(i[0], t);
                    } else {
                        Error.txt("<integer>", t);
                    }
                }
                case tCHARCONST -> {
                    if (TInt.toInt(cur.int_, i)) {
                        Error.txt(charLiteral(i[0]), t);
                    } else {
                        Error.txt("<char>", t);
                    }
                }
                case tWCHARCONST -> {
                    if (TInt.toInt(cur.int_, i)) {
                        Error.txt(charLiteral(i[0]), t);
                    } else {
                        Error.txt("<wide-char>", t);
                    }
                }
                default -> { // no extra info
                    Error.msg(t);
                }
            }
        }
        // skip forward to a major token...
        while ((cur.token != stop) && !(Restart.contains(cur.token))) {
            getToken();
        }
        accepted = 0;
        if ((cur.token == stop)) {
            accepted = 1;
        }
    }

    private static String charLiteral(long c) {
        String lit = "";
        if ((c == '\n')) {
            lit = "'\\n' = ";
        } else if ((c == '\t')) {
            lit = "'\\t' = ";
        } else if ((c == '\r')) {
            lit = "'\\r' = ";
        } else if ((c == '\f')) {
            lit = "'\\f' = ";
        } else if ((c == '\\')) {
            lit = "'\\\\' = ";
        } else if ((c == '\'')) {
            lit = "'\\\'' = ";
        } else if ((c == '\"')) {
            lit = "'\\\"' = ";
        } else if ((' ' <= c) && (c <= '~')) {
            lit = "'" + String.valueOf((char) c) + "' = ";
        }
        // Port: Fmt.Int (c, 16) gives the same lowercase digits.
        return lit + "16_" + Long.toString(c, 16);
    }

    private static final EnumSet<Token.T> Restart = EnumSet.of(
        Token.T.tEOF, Token.T.tSEMI, Token.T.tINLINE, Token.T.tEXTERNAL, Token.T.tASSERT,
        Token.T.tUNUSED, Token.T.tOBSOLETE, Token.T.tTRACE, Token.T.tCALLCONV,
        Token.T.tFATAL, Token.T.tIMPLICIT, Token.T.tDEBUG, Token.T.tBEGIN, Token.T.tCASE, Token.T.tCONST,
        Token.T.tELSE, Token.T.tELSIF, Token.T.tEVAL, Token.T.tEXCEPT, Token.T.tEXCEPTION,
        Token.T.tEXIT, Token.T.tEXPORTS, Token.T.tFINALLY, Token.T.tFOR, Token.T.tFROM,
        Token.T.tGENERIC, Token.T.tIF, Token.T.tIMPORT, Token.T.tINTERFACE,
        Token.T.tLOCK, Token.T.tLOOP, Token.T.tMODULE, Token.T.tPROCEDURE,
        Token.T.tRAISE, Token.T.tREADONLY, Token.T.tREPEAT, Token.T.tRETURN, Token.T.tREVEAL,
        Token.T.tTHEN, Token.T.tTRY, Token.T.tTYPE, Token.T.tTYPECASE, Token.T.tUNSAFE, Token.T.tUNTIL,
        Token.T.tVALUE, Token.T.tVAR, Token.T.tWHILE, Token.T.tWITH);

    public static void noteReserved(int name, Value.T value) {
        AssertError.check(M3ID.getClass(M3.ids, name) == 0, "Scanner");
        AssertError.check(reserved[name] == null, "Scanner");
        reserved[name] = value;
    }

    /**
     * Port: OSError.E is FATAL in the original; an IOException becomes an
     * UncheckedIOException.  InputStream.read returns -1 where File.T.read
     * returns 0 at the end of the file.
     */
    private static void getCh() {
        for (;;) {
            if ((input_ptr < input_len)) {
                ch = (char) (input_buf[UndoPad + input_ptr] & 0xFF);

                if (capturingText) {
                    capturedText.addhi(ch);
                }

                input_ptr++;
                return;
            } else if ((input_len == 0)) {
                ch = EOFChar;
                return;
            } else {
                try {
                    input_len = input.read(input_buf, UndoPad, MaxBuffer);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (input_len < 0) {
                    input_len = 0;
                }
                input_ptr = 0;
                input_buf[UndoPad + input_len] = (byte) '@'; // => in CommentAlert
                // loop around and try again
            }
        }
    }

    /**
     * Port: INC on a StringBufferIndex variable; the result is range
     * checked as in the original.
     */
    private static int incLen(int len) {
        if (len >= MaxString) {
            throw new RangeError("Scanner");
        }
        return len + 1;
    }

    public static void getToken() {
        int len;
        accepted++;
        for (;;) {
            // skip white space
            while ((WhiteSpace[ch])) {
                if ((ch == '\n')) {
                    offset++;
                    nLines++;
                }
                getCh();
            }
            // remember where this token starts
            cur.offset = offset;

            switch (ch) {

                case 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
                     'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
                     'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
                     'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' -> {
                    // scan an identifier
                    len = 0;
                    if ((ch == 'w') || (ch == 'W')) {
                        // check for a wide-char or wide-text literal
                        buf[len] = ch;
                        len = incLen(len);
                        getCh();
                        if ((ch == '\'')) {
                            scanChar(/* wide := */ true);
                            return;
                        } else if ((ch == '\"')) {
                            scanWideText();
                            return;
                        }
                    }
                    while ((AlphaNumerics[ch])) {
                        buf[len] = ch;
                        len = incLen(len);
                        getCh();
                    }
                    cur.id = M3ID.fromStr(M3.ids, buf, len);
                    cur.token = Token.T.tIDENT;
                    cur.defn = null;
                    {
                        int i = M3ID.getClass(M3.ids, cur.id);
                        if ((Token.First_Keyword.ordinal() <= i)
                            && (i <= Token.Last_Keyword.ordinal())) {
                            cur.token = Token.T.values()[i];
                        }
                    }
                    if ((cur.id <= MaxRsrvd)) {
                        cur.defn = reserved[cur.id];
                    }
                    return;
                }

                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    scanNumber();
                    return;
                }
                case '\'' -> {
                    scanChar(/* wide := */ false);
                    return;
                }
                case '\"' -> {
                    scanText();
                    return;
                }
                case '+' -> {
                    cur.token = Token.T.tPLUS;
                    getCh();
                    return;
                }
                case '-' -> {
                    cur.token = Token.T.tMINUS;
                    getCh();
                    return;
                }
                case '/' -> {
                    cur.token = Token.T.tSLASH;
                    getCh();
                    return;
                }
                case '&' -> {
                    cur.token = Token.T.tAMPERSAND;
                    getCh();
                    return;
                }
                case ',' -> {
                    cur.token = Token.T.tCOMMA;
                    getCh();
                    return;
                }
                case ';' -> {
                    cur.token = Token.T.tSEMI;
                    getCh();
                    return;
                }
                case '[' -> {
                    cur.token = Token.T.tLBRACKET;
                    getCh();
                    return;
                }
                case '{' -> {
                    cur.token = Token.T.tLBRACE;
                    getCh();
                    return;
                }
                case '^' -> {
                    cur.token = Token.T.tARROW;
                    getCh();
                    return;
                }
                case '#' -> {
                    cur.token = Token.T.tSHARP;
                    getCh();
                    return;
                }
                case ')' -> {
                    cur.token = Token.T.tRPAREN;
                    getCh();
                    return;
                }
                case ']' -> {
                    cur.token = Token.T.tRBRACKET;
                    getCh();
                    return;
                }
                case '}' -> {
                    cur.token = Token.T.tRBRACE;
                    getCh();
                    return;
                }
                case '|' -> {
                    cur.token = Token.T.tBAR;
                    getCh();
                    return;
                }
                case EOFChar -> {
                    cur.token = Token.T.tEOF;
                    return;
                }

                case '*' -> { // '*>' '*'
                    getCh();
                    if ((ch == '>')) {
                        cur.token = Token.T.tENDPRAGMA;
                        getCh();
                    } else {
                        cur.token = Token.T.tASTERISK;
                    }
                    return;
                }
                case '=' -> { //  '='  '=>'
                    getCh();
                    if ((ch == '>')) {
                        cur.token = Token.T.tIMPLIES;
                        getCh();
                    } else {
                        cur.token = Token.T.tEQUAL;
                    }
                    return;
                }
                case ':' -> { //  ':'  ':='
                    getCh();
                    if ((ch == '=')) {
                        cur.token = Token.T.tASSIGN;
                        getCh();
                    } else {
                        cur.token = Token.T.tCOLON;
                    }
                    return;
                }
                case '.' -> { //  '.'  '..'
                    getCh();
                    if ((ch == '.')) {
                        cur.token = Token.T.tDOTDOT;
                        getCh();
                    } else {
                        cur.token = Token.T.tDOT;
                    }
                    return;
                }
                case '(' -> { //  '('*'  '('
                    getCh();
                    if ((ch == '*')) {
                        scanComment();
                    } else {
                        cur.token = Token.T.tLPAREN;
                        return;
                    }
                }
                case '>' -> { //  '>'  '>='
                    getCh();
                    if ((ch == '=')) {
                        cur.token = Token.T.tGREQUAL;
                        getCh();
                    } else {
                        cur.token = Token.T.tGREATER;
                    }
                    return;
                }
                case '<' -> { //  '<'  '<='  '<:'  '<*'
                    getCh();
                    if ((ch == '=')) {
                        cur.token = Token.T.tLSEQUAL;
                        getCh();
                    } else if ((ch == ':')) {
                        cur.token = Token.T.tSUBTYPE;
                        getCh();
                    } else if ((ch == '*')) {
                        scanPragma();
                    } else {
                        cur.token = Token.T.tLESS;
                    }
                    return;
                }

                default -> {
                    Error.int_(ch, "Illegal character");
                    getCh();
                }

            } // case
        } // loop
    }

    private static long hexDigitValue(char ch) {
        if ('0' <= ch && ch <= '9') {
            return ch - '0';
        } else if ('A' <= ch && ch <= 'F') {
            return ch - 'A' + 10;
        } else if ('a' <= ch && ch <= 'f') {
            return ch - 'a' + 10;
        } else {
            return Long.MIN_VALUE; // Shouldn't happen.
        }
    }

    private static void scanNumber() {
        // Sic: base is uninitialized in the original when TInt.New fails;
        // here it is 0.
        long[] base = new long[1];
        int[] val = TInt.newInt();
        // Sic: the original passes pre uninitialized to TFloat.New for a
        // real literal without exponent; here it starts as Short, the
        // precision of the tREALCONST token that such a literal yields.
        Target.Precision pre = Target.Precision.Short;
        int len;
        // scan the decimal digits
        len = 0;
        while ((Digits[ch])) {
            buf[len] = ch;
            len = incLen(len);
            getCh();
        }

        if ((ch == '_')) {
            // scan a based integer
            if (!TInt.New(buf, 0, len, val)
                || !TInt.toInt(val, base)
                || (base[0] < 2)
                || (16 < base[0])) {
                Error.int_(base[0], "illegal base for based literal, 10 used");
                base[0] = 10;
            }
            len = 0;
            for (;;) {
                getCh();
                if (!(HexDigits[ch])) {
                    break;
                }
                if (hexDigitValue(ch) >= base[0]) {
                    Error.msg("digit in based literal with value not less than base.");
                }
                buf[len] = ch;
                len = incLen(len);
            }
            if ((ch == 'l') || (ch == 'L')) {
                getCh(); // eat the precision character
                if ((len == 0)
                    || !(TWord.New(buf, 0, len, (int) base[0], val))
                    || TWord.LT(M3.target.Long.max, val)) {
                    Error.msg("illegal based LONGINT literal, zero used");
                    TInt.assign(val, TInt.Zero);
                }
                cur.token = Token.T.tLONGCARDCONST;
                TInt.extend(val, (int) M3.target.Longint.bytes, cur.int_);
            } else {
                if ((len == 0)
                    || !TWord.New(buf, 0, len, (int) base[0], val)
                    || TWord.LT(M3.target.Word.max, val)) {
                    Error.msg("illegal based INTEGER literal, zero used");
                    TInt.assign(val, TInt.Zero);
                }
                cur.token = Token.T.tCARDCONST;
                TInt.extend(val, (int) M3.target.Integer.bytes, cur.int_);
            }

        } else if ((ch == '.')) {
            // scan a floating point number
            buf[len] = '.';
            len = incLen(len);
            getCh(); // eat the '.'
            if ((ch == '.')) {
                // we saw  "dddd.."

                //****  Rd.UnGetChar (input);  ****
                input_ptr--;
                input_buf[UndoPad + input_ptr] = (byte) '.';

                if (!TInt.New(buf, 0, len - 1, val)
                    || TInt.LT(val, M3.target.Integer.min)
                    || TInt.LT(M3.target.Integer.max, val)) {
                    Error.msg("illegal INTEGER literal, zero used");
                    TInt.assign(val, TInt.Zero);
                }
                cur.token = Token.T.tCARDCONST;
                TInt.assign(cur.int_, val);
                return;
            }

            // scan the fractional digits
            if (!(Digits[ch])) {
                Error.msg("missing digits in real fraction");
                buf[len] = '0';
                len = incLen(len);
            }
            while ((Digits[ch])) {
                buf[len] = ch;
                len = incLen(len);
                getCh();
            }

            // check for the exponent
            if ((ch == 'e') || (ch == 'E')) {
                buf[len] = 'e';
                len = incLen(len);
                cur.token = Token.T.tREALCONST;
                pre = Target.Precision.Short;
            } else if ((ch == 'd') || (ch == 'D')) {
                buf[len] = 'e';
                len = incLen(len);
                cur.token = Token.T.tLONGREALCONST;
                pre = Target.Precision.Long;
            } else if ((ch == 'x') || (ch == 'X')) {
                buf[len] = 'e';
                len = incLen(len);
                cur.token = Token.T.tEXTENDEDCONST;
                pre = Target.Precision.Extended;
            } else { // real constant with no exponent
                if (!TFloat.New(buf, 0, len, pre, cur.float_)) {
                    Error.msg("illegal floating-point literal");
                }
                cur.token = Token.T.tREALCONST;
                pre = Target.Precision.Short;
                return;
            }
            getCh(); // eat the exponent entry char

            // get the exponent sign
            if ((ch == '+')) {
                buf[len] = '+';
                len = incLen(len);
                getCh();
            } else if ((ch == '-')) {
                buf[len] = '-';
                len = incLen(len);
                getCh();
            } else {
                buf[len] = '+';
            }

            // finally, get the exponent digits
            if (!(Digits[ch])) {
                Error.msg("missing digits in real exponent");
                buf[len] = '0';
                len = incLen(len);
            }
            while ((Digits[ch])) {
                buf[len] = ch;
                len = incLen(len);
                getCh();
            }

            if (!TFloat.New(buf, 0, len, pre, cur.float_)) {
                Error.msg("illegal floating-point literal");
            }

        } else {
            // already scanned a decimal integer
            if ((ch == 'l') || (ch == 'L')) {
                getCh(); // eat the precision character
                if (!TInt.New(buf, 0, len, val)
                    || TInt.LT(val, M3.target.Longint.min)
                    || TInt.LT(M3.target.Longint.max, val)) {
                    Error.msg("illegal LONGINT literal, zero used");
                    TInt.assign(val, TInt.Zero);
                }
                cur.token = Token.T.tLONGCARDCONST;
                TInt.assign(cur.int_, val);
            } else {
                if (!TInt.New(buf, 0, len, val)
                    || TInt.LT(val, M3.target.Integer.min)
                    || TInt.LT(M3.target.Integer.max, val)) {
                    Error.msg("illegal INTEGER literal, zero used");
                    TInt.assign(val, TInt.Zero);
                }
                cur.token = Token.T.tCARDCONST;
                TInt.assign(cur.int_, val);
            }
        }

    }

    private static void scanChar(boolean wide) {
        // Port: Tok = ARRAY BOOLEAN OF TK {tCHARCONST, tWCHARCONST} and
        // Bytes = ARRAY BOOLEAN OF [1..3] {1, 2} are conditional expressions.
        long val = 0;
        cur.token = wide ? Token.T.tWCHARCONST : Token.T.tCHARCONST;
        TInt.assign(cur.int_, TInt.Zero);
        getCh();
        if ((ch == '\'')) {
            Error.msg("missing character in character literal");
            getCh();
            return;
        } else if ((ch == '\n') || (ch == '\r') || (ch == '\f')) {
            Error.msg("end-of-line encountered in character literal");
            return;
        } else if ((ch == '\\')) {
            getCh();
            if ((ch == 'n')) {
                val = '\n';
                getCh();
            } else if ((ch == 't')) {
                val = '\t';
                getCh();
            } else if ((ch == 'r')) {
                val = '\r';
                getCh();
            } else if ((ch == 'f')) {
                val = '\f';
                getCh();
            } else if ((ch == '\\')) {
                val = '\\';
                getCh();
            } else if ((ch == '\'')) {
                val = '\'';
                getCh();
            } else if ((ch == '\"')) {
                val = '\"';
                getCh();
            } else if ((ch == 'x') || (ch == 'X')) {
                getCh();
                val = getHexChar(/* bytes := */ wide ? 2 : 1);
            } else if ((OctalDigits[ch])) {
                val = getOctalChar(wide);
            } else if (wide && ch == 'U') {
                getCh();
                val = getHexChar(/* bytes := */ 3);
            } else {
                Error.msg("unknown escape sequence in character literal");
            }
        } else if ((ch == EOFChar)) {
            Error.msg("EOF encountered in character literal");
            return;
        } else { // a simple character literal
            val = ch;
            getCh();
        }
        if ((ch != '\'')) {
            Error.msg("missing closing quote on character literal");
        } else {
            getCh();
        }
        if (!TInt.fromInt(val, cur.int_)) {
            Error.msg("illegal character literal");
        }
    }

    /** Port: the locals of ScanText that its nested procedure Stuff uses. */
    public static final class TextLocals {
        public long i;
        public M3Buf.T mbuf = null;
    }

    private static void stuff(TextLocals l, char c) {
        if ((l.i < buf.length)) {
            buf[(int) l.i] = c;
            l.i++;
        } else if ((l.i == buf.length)) {
            l.mbuf = M3Buf.New();
            M3Buf.putSub(l.mbuf, buf);
            M3Buf.putChar(l.mbuf, c);
            l.i++;
        } else {
            M3Buf.putChar(l.mbuf, c);
            l.i++;
        }
    }

    private static void scanText() {
        TextLocals l = new TextLocals();
        l.i = 0;
        cur.token = Token.T.tTEXTCONST;
        getCh();
        for (;;) {
            if ((ch == '\"')) {
                getCh();
                break;
            } else if ((ch == '\n') || (ch == '\r') || (ch == '\f')) {
                Error.msg("end-of-line encountered in text literal");
                break;
            } else if ((ch == '\\')) {
                getCh();
                if ((ch == 'n')) {
                    stuff(l, '\n');
                    getCh();
                } else if ((ch == 't')) {
                    stuff(l, '\t');
                    getCh();
                } else if ((ch == 'r')) {
                    stuff(l, '\r');
                    getCh();
                } else if ((ch == 'f')) {
                    stuff(l, '\f');
                    getCh();
                } else if ((ch == '\\')) {
                    stuff(l, '\\');
                    getCh();
                } else if ((ch == '\'')) {
                    stuff(l, '\'');
                    getCh();
                } else if ((ch == '\"')) {
                    stuff(l, '\"');
                    getCh();
                } else if ((ch == 'x')
                           || (ch == 'X')) {
                    getCh();
                    stuff(l, toChar(getHexChar(/* bytes := */ 1)));
                } else if ((OctalDigits[ch])) {
                    stuff(l, toChar(getOctalChar(/* wide := */ false)));
                } else {
                    Error.msg("unknown escape sequence in text literal");
                }
            } else if ((ch == EOFChar)) {
                Error.msg("EOF encountered in text literal");
                break;
            } else { // a simple character
                stuff(l, ch);
                getCh();
            }
        }

        if ((l.mbuf == null)) {
            cur.str = M3String.fromStr(buf, l.i);
        } else {
            cur.str = M3String.add(M3Buf.toText(l.mbuf));
        }
    }

    /** Port: VAL (c, CHAR), range checked. */
    private static char toChar(long c) {
        if (c < 0 || c > 255) {
            throw new RangeError("Scanner");
        }
        return (char) c;
    }

    /**
     * Port: the locals of ScanWideText that its nested procedure Stuff
     * uses; an M3WString.Char is an int.
     */
    public static final class WideTextLocals {
        public M3WString.T res = null;
        public long i = 0;
        public final int[] wbuf = new int[128];
    }

    private static void stuff(WideTextLocals l, long c) {
        c = c & 0x1fffff;
        if ((l.i == l.wbuf.length)) {
            if ((l.res == null)) {
                l.res = M3WString.fromStr(l.wbuf);
            } else {
                l.res = M3WString.concat(l.res, M3WString.fromStr(l.wbuf));
            }
            l.i = 0;
        }
        l.wbuf[(int) l.i] = (int) c;
        l.i++;
    }

    private static void scanWideText() {
        WideTextLocals l = new WideTextLocals();
        cur.token = Token.T.tWTEXTCONST;
        getCh(); // opening quote
        for (;;) {
            if ((ch == '\"')) {
                getCh();
                break;
            } else if ((ch == '\n') || (ch == '\r') || (ch == '\f')) {
                Error.msg("end-of-line encountered in wide text literal");
                break;
            } else if ((ch == '\\')) {
                getCh();
                if ((ch == 'n')) {
                    stuff(l, '\n');
                    getCh();
                } else if ((ch == 't')) {
                    stuff(l, '\t');
                    getCh();
                } else if ((ch == 'r')) {
                    stuff(l, '\r');
                    getCh();
                } else if ((ch == 'f')) {
                    stuff(l, '\f');
                    getCh();
                } else if ((ch == '\\')) {
                    stuff(l, '\\');
                    getCh();
                } else if ((ch == '\'')) {
                    stuff(l, '\'');
                    getCh();
                } else if ((ch == '\"')) {
                    stuff(l, '\"');
                    getCh();
                } else if ((ch == 'x')
                           || (ch == 'X')) {
                    getCh();
                    stuff(l, getHexChar(/* bytes := */ 2));
                } else if ((OctalDigits[ch])) {
                    stuff(l, getOctalChar(/* wide := */ true));
                } else if (ch == 'U') {
                    getCh();
                    stuff(l, getHexChar(/* bytes := */ 3));
                } else {
                    Error.msg("unknown escape sequence in wide text literal");
                }
            } else if ((ch == EOFChar)) {
                Error.msg("EOF encountered in wide text literal");
                break;
            } else { // a simple character
                stuff(l, (long) ch);
                getCh();
            }
        }
        if ((l.res == null)) {
            cur.wstr = M3WString.fromStr(l.wbuf, l.i);
        } else {
            cur.wstr = M3WString.concat(l.res, M3WString.fromStr(l.wbuf, l.i));
        }
    }

    private static long getOctalChar(boolean wide) {
        long[] value = {0};
        if (!getOctalDigit(wide, value)) {
            return value[0];
        }
        if (!getOctalDigit(wide, value)) {
            return value[0];
        }
        if (!getOctalDigit(wide, value)) {
            return value[0];
        }
        if (wide) {
            if (!getOctalDigit(wide, value)) {
                return value[0];
            }
            if (!getOctalDigit(wide, value)) {
                return value[0];
            }
            if (!getOctalDigit(wide, value)) {
                return value[0];
            }
            if (value[0] > Target.WideChar16Max) {
                Error.msg("Octal escaped WIDECHAR value out of range");
                value[0] = 0;
            }
        } else {
            if (value[0] > 0xFF) {
                Error.msg("Octal escaped CHAR value out of range");
                value[0] = 0;
            }
        }
        return value[0];
    }

    /** Port: indexed by ORD (wide). */
    private static final String[] BadOctal = {
        "octal character escape must have 3 digits",
        "wide octal character escape must have 6 digits" };

    /** Port: the VAR parameter value is a one-element array. */
    private static boolean getOctalDigit(boolean wide, long[] value) {
        if (OctalDigits[ch]) {
            value[0] = value[0] * 8 + ch - '0';
            getCh();
            return true;
        } else {
            Error.msg(BadOctal[wide ? 1 : 0]);
            return false;
        }
    }

    // CONST ReplacementWt = UniCodec.ReplacementWt;
    // We don't have UniCodec when compiling this with an earlier compiler.
    private static final long ReplacementWt = 0xFFFD;

    private static long getHexChar(int bytes) {
        long[] value = {0};
        if (!getHexDigit(bytes, value)) {
            return value[0];
        }
        if (!getHexDigit(bytes, value)) {
            return value[0];
        }
        if (bytes > 1) {
            if (!getHexDigit(bytes, value)) {
                return value[0];
            }
            if (!getHexDigit(bytes, value)) {
                return value[0];
            }
            if (bytes == 3) {
                // Only get here for a Unicode escape, which is always 6 hex digits.
                if (!getHexDigit(bytes, value)) {
                    return value[0];
                }
                if (!getHexDigit(bytes, value)) {
                    return value[0];
                }
                if (value[0] > Target.WideChar32Max) {
                    Error.msg("Unicode escaped character outside of Unicode range");
                    value[0] = 0;
                } else if (value[0] > Target.wideCharMax(M3.target)) {
                    Error.warn(2, "Character outside WIDECHAR range, replaced by Unicode replacement character.");
                    value[0] = ReplacementWt;
                }
            }
        }
        return value[0];
    }

    /** Port: ARRAY [1..3] OF TEXT, indexed by bytes - 1. */
    private static final String[] BadHex = {
        "hex character escape must have 2 digits",
        "wide hex character escape must have 4 digits",
        "Unicode character escape must have 6 digits" };

    /** Port: the VAR parameter value is a one-element array. */
    private static boolean getHexDigit(int bytes, long[] value) {
        // Port: x starts as 0 because Java requires a definite value; every
        // path that reaches its use assigns it.
        long x = 0;
        if (!(HexDigits[ch])) {
            Error.msg(BadHex[bytes - 1]);
            return false;
        } else if (('0' <= ch) && (ch <= '9')) {
            x = ch - '0';
        } else if (('a' <= ch) && (ch <= 'f')) {
            x = ch - 'a' + 10;
        } else if (('A' <= ch) && (ch <= 'F')) {
            x = ch - 'A' + 10;
        }
        value[0] = value[0] * 16 + x;
        getCh();
        return true;
    }

    private static void scanComment() {
        long nest, save;
        long start;
        start = cur.offset;
        getCh();
        nest = 1;
        while ((nest > 0)) {
            while ((!CommentAlert[ch])) {
                // INLINE GetCh ();
                ch = (char) (input_buf[UndoPad + input_ptr] & 0xFF);
                input_ptr++;
            }
            if ((ch == '*')) {
                getCh();
                if ((ch == ')')) {
                    nest--;
                    getCh();
                }
            } else if ((ch == '(')) {
                getCh();
                if ((ch == '*')) {
                    nest++;
                    getCh();
                }
            } else if ((ch == EOFChar)) {
                save = offset;
                offset = start;
                Error.msg("EOF encountered in comment");
                offset = save;
                nest = 0;
            } else if ((ch == '\n')) {
                offset++;
                nLines++;
                getCh();
            } else {
                getCh();
            }
        }
    }

    private static void scanPragma() {
        long nest, save, start, fileno;
        int i;
        long[] lineno = new long[1];
        IDList ss;
        start = cur.offset;
        getCh(); // '*'

        // skip white space
        while ((WhiteSpace[ch])) {
            if ((ch == '\n')) {
                offset++;
                nLines++;
            }
            getCh();
        }

        // scan an identifier
        i = 0;
        while ((AlphaNumerics[ch])) {
            buf[i] = ch;
            i++;
            getCh();
        }
        cur.id = M3ID.fromStr(M3.ids, buf, i);
        cur.token = Token.T.values()[M3ID.getClass(M3.ids, cur.id)];

        if ((Token.First_Pragma.compareTo(cur.token) <= 0)
            && (cur.token.compareTo(Token.Last_Pragma) <= 0)) {
            return;
        }

        if ((cur.id == LINE)) {
            getToken(); // LINE
            if ((cur.token != Token.T.tCARDCONST)) {
                Error.msg("missing line number on LINE pragma; skipping to \'*>\'");
                while ((cur.token != Token.T.tENDPRAGMA) && (cur.token != Token.T.tEOF)) {
                    getToken();
                }
                if ((cur.token == Token.T.tENDPRAGMA)) {
                    getToken();
                }
                return;
            }
            if (!TInt.toInt(cur.int_, lineno)) {
                Error.msg("illegal line number, ignored");
                lineno[0] = Math.floorMod(offset, MaxLines);
            }
            fileno = Math.floorDiv(offset, MaxLines);
            getToken(); // CARD "line number"
            if ((cur.token == Token.T.tTEXTCONST)) {
                fileno = fileNumber(M3String.toText(cur.str));
                getToken(); // TEXT "filename"
            }
            offset = fileno * MaxLines + lineno[0] - 1;
            if ((cur.token != Token.T.tENDPRAGMA)) {
                Error.msg("missing \'*>\' on LINE pragma");
            } else {
                getToken(); // fetch the next one
            }
            return;
        } else if ((cur.id == NOWARN)) {
            Error.ignoreWarning(cur.offset);
            getToken(); // NOWARN
            if ((cur.token != Token.T.tENDPRAGMA)) {
                Error.msg("missing \'*>\' on NOWARN pragma");
            } else {
                getToken(); // fetch the next one
            }
            return;
        } else if ((cur.id == PRAGMA)) {
            getToken(); // PRAGMA
            while ((cur.token == Token.T.tIDENT)
                   || ((Token.First_Pragma.compareTo(cur.token) <= 0)
                       && (cur.token.compareTo(Token.Last_Pragma) <= 0))
                   || ((Token.First_Keyword.compareTo(cur.token) <= 0)
                       && (cur.token.compareTo(Token.Last_Keyword) <= 0))) {
                IDList n = new IDList();
                n.id = cur.id;
                n.next = ignore;
                ignore = n;
                getToken(); // IDENT
                if ((cur.token != Token.T.tCOMMA)) {
                    break;
                }
                getToken(); // COMMA
            }
            if ((cur.token != Token.T.tENDPRAGMA)) {
                Error.msg("missing \'*>\' on PRAGMA pragma");
            } else {
                getToken(); // fetch the next real token
            }
            return;
        } else if (Target.findConvention(M3.target, M3ID.toText(M3.ids, cur.id)) != null) {
            cur.token = Token.T.tCALLCONV;
            return;
        } else { // scan and ignore the list
            ss = ignore;
            while ((ss != null) && (ss.id != cur.id)) {
                ss = ss.next;
            }
            if ((ss == null)) {
                Error.warnID(2, cur.id, "unrecognized pragma (ignored)");
            }
        }


        // scan over and ignore the offending pragma
        nest = 1;
        while ((nest > 0)) {
            if ((ch == '*')) {
                getCh();
                if ((ch == '>')) {
                    nest--;
                    getCh();
                }
            } else if ((ch == '<')) {
                getCh();
                if ((ch == '*')) {
                    nest++;
                    getCh();
                }
            } else if ((ch == EOFChar)) {
                save = offset;
                offset = start;
                Error.msg("EOF encountered in pragma");
                offset = save;
                nest = 0;
            } else if ((ch == '\n')) {
                offset++;
                nLines++;
                getCh();
            } else {
                getCh();
            }
        }

        getToken(); // get the next token
    }

    // Support capturing underlying text while scanning.
    // This is used for assertion messages.
    // Consider a different implementation:
    //  - Turn on full buffering
    //  - Use positions within the buffer.
    //
    // Or consider mmap, really.

    /**
     * Port: the parts of libm3's CharSeq.T (with its revelation in
     * CharSeqRep) that the scanner uses: init and addhi.
     */
    public static final class CharSeq {
        public char[] elem;
        public int st = 0;
        public int sz = 0;

        CharSeq init(int sizeHint) {
            if (elem == null || elem.length == 0) {
                elem = new char[Math.max(sizeHint, 1)];
            } else {
                // Clear the previous entries to help the GC
                for (int i = st; i <= Math.min(st + sz - 1, elem.length - 1); i++) {
                    elem[i] = '\000';
                }
                for (int i = 0; i <= Math.max(-1, st + sz - 1 - elem.length); i++) {
                    elem[i] = '\000';
                }
            }
            sz = 0;
            st = 0;
            return this;
        }

        void addhi(char x) {
            if (sz == elem.length) {
                expand();
            }
            {
                int i = st + sz;
                if (i >= elem.length) {
                    i = i - elem.length;
                }
                elem[i] = x;
            }
            sz++;
        }

        private void expand() {
            int n = elem.length;
            char[] new_ = new char[2 * n];
            int m = n - st;
            System.arraycopy(elem, st, new_, 0, m);
            System.arraycopy(elem, 0, new_, m, st);
            st = 0;
            elem = new_;
        }
    }

    private static boolean capturingText;
    private static CharSeq capturedText;

    public static void enableTextCapture() {
        if (capturedText == null) {
            capturedText = new CharSeq();
        }
        capturedText.init(99);
        capturingText = true;
    }

    /**
     * Port: the VAR parameters are one-element arrays; text[0] receives
     * the REF ARRAY OF CHAR.
     */
    public static void disableTextCapture(char[][] text, long[] size) {
        if (capturingText) {
            capturingText = false;
            text[0] = capturedText.elem;
            size[0] = capturedText.sz;
        }
    }
}
