// Java port of CM3's m3-sys/m3front/src/misc/Token.i3 and Token.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3ID;

// File: Token.i3
// Last modified on Thu Apr  6 09:39:07 PDT 1995 by kalsow
//      modified on Sat Mar 16 00:31:16 1991 by muller

// File: Token.m3
// Last modified on Tue Dec 20 10:37:51 PST 1994 by kalsow
//      modified on Sat Mar 16 00:31:12 1991 by muller

public final class Token {
    private Token() {}

    public enum T {
        tEOF,

        // lexical classes
        tIDENT,
        tCARDCONST, tLONGCARDCONST,
        tREALCONST, tLONGREALCONST, tEXTENDEDCONST,
        tCHARCONST, tWCHARCONST,
        tTEXTCONST, tWTEXTCONST,

        // operators
        tPLUS, tMINUS, tASTERISK, tSLASH, tASSIGN, tAMPERSAND, tDOT, tCOMMA,
        tSEMI, tLPAREN, tLBRACKET, tLBRACE, tARROW, tEQUAL, tSHARP, tLESS,
        tGREATER, tLSEQUAL, tGREQUAL, tDOTDOT, tCOLON, tRPAREN, tRBRACKET,
        tRBRACE, tBAR, tSUBTYPE, tIMPLIES, tENDPRAGMA,

        // pragmas (that escape from the scanner)
        tINLINE, tEXTERNAL, tASSERT, tUNUSED, tOBSOLETE, tTRACE,
        tCALLCONV, tIMPLICIT, tDEBUG, tLAZYALIGN, tSTRICTALIGN, tFATAL,

        // reserved words
        tAND, tANY, tARRAY, tAS,
        tBEGIN, tBITS, tBRANDED, tBY,
        tCASE, tCONST,
        tDIV, tDO,
        tELSE, tELSIF, tEND, tEVAL, tEXCEPT, tEXCEPTION, tEXIT, tEXPORTS,
        tFINALLY, tFOR, tFROM,
        tGENERIC,
        tIF, tIMPORT, tIN, tINTERFACE,
        tLOCK, tLOOP,
        tMETHODS, tMOD, tMODULE,
        tNOT,
        tOBJECT, tOF, tOR, tOVERRIDES,
        tPROCEDURE,
        tRAISE, tRAISES, tREADONLY, tRECORD, tREF, tREPEAT, tRETURN, tREVEAL,
        tSET,
        tTHEN, tTO, tTRY, tTYPE, tTYPECASE,
        tUNSAFE, tUNTIL, tUNTRACED,
        tVALUE, tVAR,
        tWHILE, tWITH
    }

    public static final T First_Literal = T.tIDENT;

    public static final T Last_Literal = T.tWTEXTCONST;

    public static final T First_Operator = T.tPLUS;

    public static final T Last_Operator = T.tENDPRAGMA;

    public static final T First_Pragma = T.tINLINE;

    public static final T Last_Pragma = T.tFATAL;

    public static final T First_Keyword = T.tAND;

    public static final T Last_Keyword = T.tWITH;

    // Port: Set = SET OF T is EnumSet<T>; the constant sets are never modified.

    public static final EnumSet<T> EmptySet = EnumSet.noneOf(T.class);

    public static final EnumSet<T> DeclStart = EnumSet.of(T.tCONST, T.tTYPE, T.tREVEAL, T.tVAR, T.tIMPLICIT,
            T.tEXTERNAL, T.tINLINE, T.tUNUSED, T.tOBSOLETE,
            T.tLAZYALIGN, T.tSTRICTALIGN,
            T.tEXCEPTION, T.tPROCEDURE, T.tFATAL, T.tCALLCONV);

    public static final EnumSet<T> TypeStart = EnumSet.of(T.tIDENT, T.tARRAY, T.tBITS, T.tBRANDED, T.tLBRACE,
            T.tLAZYALIGN, T.tSTRICTALIGN,
            T.tUNTRACED, T.tOBJECT, T.tPROCEDURE, T.tRECORD,
            T.tREF, T.tSET, T.tLBRACKET, T.tLPAREN);

    public static final EnumSet<T> ExprStart = EnumSet.of(T.tNOT, T.tPLUS, T.tMINUS, T.tIDENT,
            T.tCARDCONST, T.tLONGCARDCONST,
            T.tLONGREALCONST, T.tREALCONST, T.tEXTENDEDCONST,
            T.tCHARCONST, T.tWCHARCONST, T.tTEXTCONST, T.tWTEXTCONST,
            T.tLPAREN, T.tARRAY, T.tBITS, T.tRECORD, T.tSET);

    public static final EnumSet<T> StmtStart = union(EnumSet.of(T.tCASE, T.tEXIT, T.tEVAL, T.tFOR, T.tIF, T.tLOCK,
            T.tLOOP, T.tRAISE, T.tREPEAT, T.tRETURN, T.tTRY,
            T.tTYPECASE, T.tWHILE, T.tWITH, T.tBEGIN, T.tASSERT,
            T.tIDENT, T.tLPAREN, T.tARRAY, T.tRECORD, T.tDEBUG),
            DeclStart);

    private static EnumSet<T> union(EnumSet<T> a, EnumSet<T> b) {
        EnumSet<T> r = EnumSet.copyOf(a);
        r.addAll(b);
        return r;
    }

    /** Port: a VAR used as a constant; indexed by ordinal(). */
    public static int[] name = new int[T.values().length];

    /** Port: indexed by ordinal(). */
    private static final String[] Keywords = {
        "*EOF*",

        "*id*", "*integer*", "*longint*",
        "*real*", "*longreal*", "*extended*",
        "*char*", "*wchar*", "*text*", "*wtext*",

        "+", "-", "*", "/", ":=", "&", ".", ",",
        ";", "(", "[", "{", "^", "=", "#", "<",
        ">", "<=", ">=", "..", ":", ")", "]",
        "}", "|", "<:", "=>", "*>",

        "INLINE", "EXTERNAL", "ASSERT", "UNUSED", "OBSOLETE", "TRACE",
        "*CALLCONV*", "IMPLICIT", "DEBUG", "LAZYALIGN", "STRICTALIGN", "FATAL",

        "AND", "ANY", "ARRAY", "AS",
        "BEGIN", "BITS", "BRANDED", "BY",
        "CASE", "CONST",
        "DIV", "DO",
        "ELSE", "ELSIF", "END", "EVAL", "EXCEPT", "EXCEPTION", "EXIT", "EXPORTS",
        "FINALLY", "FOR", "FROM",
        "GENERIC",
        "IF", "IMPORT", "IN", "INTERFACE",
        "LOCK", "LOOP",
        "METHODS", "MOD", "MODULE",
        "NOT",
        "OBJECT", "OF", "OR", "OVERRIDES",
        "PROCEDURE",
        "RAISE", "RAISES", "READONLY", "RECORD", "REF", "REPEAT", "RETURN", "REVEAL",
        "SET",
        "THEN", "TO", "TRY", "TYPE", "TYPECASE",
        "UNSAFE", "UNTIL", "UNTRACED",
        "VALUE", "VAR",
        "WHILE", "WITH"
    };

    private static final String[] Reserved = {
        "ABS", "ADDRESS", "ADR", "ADRSIZE", "BITSIZE", "BOOLEAN",
        "BYTESIZE", "CARDINAL", "CEILING", "CHAR", "DEC", "DISPOSE",
        "EXTENDED", "FALSE", "FIRST", "FLOAT", "FLOOR", "INC",
        "INTEGER", "ISTYPE", "LAST", "LONGCARD", "LONGINT", "LONGREAL", "LOOPHOLE",
        "MAX", "MIN", "MUTEX", "NARROW", "NEW", "NIL", "NULL", "NUMBER",
        "ORD", "REAL", "REFANY", "ROOT", "ROUND", "SUBARRAY", "TEXT",
        "TRUE", "TRUNC", "TYPECODE", "VAL", "WIDECHAR"
    };

    /**
     * Make sure that the keywords and reserved identifiers get
     * assigned small M3ID.Ts.  Otherwise, the tables indexed by ID in
     * Scanner will overflow.
     */
    public static void initialize() {
        for (T t : T.values()) {
            name[t.ordinal()] = M3ID.add(M3.ids, Keywords[t.ordinal()], t.ordinal());
        }
        for (int i = 0; i < Reserved.length; i++) {
            M3ID.add(M3.ids, Reserved[i]);
        }
    }
}
