// Java port of CM3's m3-sys/m3front/src/values/Decl.i3 and Decl.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

import m3j.middle.M3ID;
import m3j.middle.Target;
import m3j.util.AssertError;

// File: Decl.i3
// Last modified on Tue Dec 20 14:43:48 PST 1994 by kalsow
//      modified on Fri Feb 23 03:42:15 1990 by muller

// File: Decl.m3
// Last modified on Tue Dec 20 14:54:22 PST 1994 by kalsow
//      modified on Sat Mar 16 01:56:20 1991 by muller

public final class Decl {
    private Decl() {}

    public static final class Attributes {
        public int alias = M3ID.NoID;
        public boolean isExternal = false;
        public boolean isInline = false;
        public boolean isUnused = false;
        public boolean isObsolete = false;
        public boolean isImplicit = false;
        public Target.CallingConvention callingConv = null;
        public boolean isLazyAligned = false;

        public Attributes() {}

        public Attributes copy() {
            Attributes r = new Attributes();
            r.alias = alias;
            r.isExternal = isExternal;
            r.isInline = isInline;
            r.isUnused = isUnused;
            r.isObsolete = isObsolete;
            r.isImplicit = isImplicit;
            r.callingConv = callingConv;
            r.isLazyAligned = isLazyAligned;
            return r;
        }
    }

    // Port: TK = Token.T.

    public enum Attr { External, Inline, Implicit, CallConv, Obsolete, Unused,
                        LazyAligned }

    // Port: AttrSet = EnumSet<Attr>; the constant sets are never modified.
    private static final EnumSet<Attr> RevealOK = EnumSet.noneOf(Attr.class);
    private static final EnumSet<Attr> ConstantOK =
        EnumSet.of(Attr.Obsolete, Attr.Unused, Attr.LazyAligned);
    private static final EnumSet<Attr> TypeOK =
        EnumSet.of(Attr.Obsolete, Attr.Unused, Attr.LazyAligned);
    private static final EnumSet<Attr> ExceptionOK =
        EnumSet.of(Attr.Obsolete, Attr.Unused, Attr.Implicit);
    private static final EnumSet<Attr> VariableOK =
        EnumSet.of(Attr.Obsolete, Attr.Unused, Attr.External,
                   Attr.LazyAligned);
    private static final EnumSet<Attr> ProcedureOK =
        EnumSet.of(Attr.Obsolete, Attr.Unused, Attr.External,
                   Attr.Inline, Attr.CallConv);

    public static void parse(boolean interface_, boolean top_level, ESet.T[] fails) {
        Attributes att = new Attributes();
        boolean[] got_cc = {false};
        att.isInline    = false;
        att.isExternal  = false;
        att.isUnused    = false;
        att.isObsolete  = false;
        att.isImplicit  = false;
        att.alias       = M3ID.NoID;
        att.callingConv = null;
        att.isLazyAligned = Module.lazyAlignmentOn();
        loop:
        while (true) {
            switch (Scanner.cur.token) {
                case tEXTERNAL -> {
                    if (!Module.isInterface()) {
                        Error.msg("External declarations only allowed in interfaces");
                    }
                    // Port: att.alias and att.callingConv are VAR parameters.
                    int[] alias = {att.alias};
                    Target.CallingConvention[] cc = {att.callingConv};
                    parseExternalPragma(alias, cc, got_cc);
                    att.alias = alias[0];
                    att.callingConv = cc[0];
                    att.isExternal = true;
                }
                case tINLINE -> {
                    att.isInline = true;
                    Scanner.getToken(); // INLINE
                    Scanner.match(Token.T.tENDPRAGMA);
                }
                case tUNUSED -> {
                    att.isUnused = true;
                    Scanner.getToken(); // UNUSED
                    Scanner.match(Token.T.tENDPRAGMA);
                }
                case tOBSOLETE -> {
                    att.isObsolete = true;
                    Scanner.getToken(); // OBSOLETE
                    Scanner.match(Token.T.tENDPRAGMA);
                }
                case tIMPLICIT -> {
                    att.isImplicit = true;
                    Scanner.getToken(); // IMPLICIT
                    Scanner.match(Token.T.tENDPRAGMA);
                }
                case tCALLCONV -> {
                    att.callingConv = Target.findConvention(M3.target,
                        M3ID.toText(M3.ids, Scanner.cur.id));
                    got_cc[0] = true;
                    Scanner.getToken(); // convention name
                    Scanner.match(Token.T.tENDPRAGMA);
                }
                case tLAZYALIGN -> {
                    Error.msg("<*LazyAlign*> is no longer supported");
                    att.isLazyAligned = true;
                    Module.setLazyAlignment(true);
                    Scanner.getToken(); // LAZYALIGN
                    Scanner.match(Token.T.tENDPRAGMA);
                }
                case tSTRICTALIGN -> {
                    Error.msg("<*StrictAlign*> is no longer supported");
                    att.isLazyAligned = false;
                    Module.setLazyAlignment(false);
                    Scanner.getToken(); // LAZYALIGN
                    Scanner.match(Token.T.tENDPRAGMA);
                }
                default -> {
                    break loop;
                }
            }
        }

        switch (Scanner.cur.token) {
            case tCONST -> {
                checkAttrs(att, got_cc[0], ConstantOK, "constant");
                Constant.parseDecl(att);
            }
            case tTYPE -> {
                checkAttrs(att, got_cc[0], TypeOK, "type");
                Tipe.parse(att);
            }
            case tVAR -> {
                att.isExternal = att.isExternal || Module.isExternal();
                checkAttrs(att, got_cc[0], VariableOK, "variable");
                Variable.parseDecl(att);
            }
            case tPROCEDURE -> {
                att.isExternal = att.isExternal || Module.isExternal();
                checkAttrs(att, got_cc[0], ProcedureOK, "procedure");
                Procedure.parseDecl(att, interface_);
            }
            case tREVEAL -> {
                if ((!top_level)) {
                    Error.msg("revelations in nested scopes are not allowed");
                }
                checkAttrs(att, got_cc[0], RevealOK, "revelation");
                Revelation.parse(att);
            }
            case tEXCEPTION -> {
                if ((!top_level)) {
                    Error.msg("exception declarations in nested scopes are not allowed");
                }
                checkAttrs(att, got_cc[0], ExceptionOK, "exception");
                Exceptionz.parseDecl(att);
            }
            case tFATAL -> {
                fails[0] = ESet.parseFails(fails[0]);
            }
            default -> {
                if (att.isInline || att.isExternal || att.isUnused
                    || att.isObsolete || att.isImplicit) {
                    Error.msg("declaration pragma not followed by a declaration");
                }
            }
        }
    }

    /**
     * Port: the parameters are VAR (OUT) parameters; each is a one-element
     * array whose element 0 receives the result.
     */
    public static void parseExternalPragma(int[] alias, Target.CallingConvention[] cc, boolean[] got_cc) {
        String txt;
        AssertError.check(Scanner.cur.token == Token.T.tEXTERNAL, "Decl");
        Scanner.getToken(); // EXTERNAL

        alias[0]  = M3ID.NoID; // default => use the Modula-3 name
        cc[0]     = M3.target.DefaultCall;
        got_cc[0] = false;

        if ((Scanner.cur.token == Token.T.tIDENT) || (Scanner.cur.token == Token.T.tTEXTCONST)) {
            if ((Scanner.cur.token == Token.T.tIDENT)) {
                alias[0] = Scanner.cur.id;
            } else {
                alias[0] = M3ID.add(M3.ids, M3String.toText(Scanner.cur.str));
            }
            Scanner.getToken(); // IDENT, TEXTCONST

            if ((Scanner.cur.token == Token.T.tCOLON)) {
                Scanner.getToken(); // :
                if ((Scanner.cur.token == Token.T.tIDENT)) {
                    txt = M3ID.toText(M3.ids, Scanner.cur.id);
                } else if ((Scanner.cur.token == Token.T.tTEXTCONST)) {
                    txt = M3String.toText(Scanner.cur.str);
                } else {
                    txt = null;
                }
                if ((txt != null)) {
                    cc[0] = Target.findConvention(M3.target, txt);
                    got_cc[0] = true;
                    if ((cc[0] == null)) {
                        Error.ID(Scanner.cur.id, "unsupported language or calling convention");
                        cc[0] = M3.target.DefaultCall;
                    }
                    Scanner.getToken(); // IDENT/TEXTCONST
                } else {
                    Error.msg("Missing language for <*EXTERNAL*> pragma");
                }
            }

        }

        Scanner.match(Token.T.tENDPRAGMA);
    }

    private static void checkAttrs(Attributes att, boolean got_cc,
                                   EnumSet<Attr> allowed, String tag) {
        if (att.isUnused && !allowed.contains(Attr.Unused)) {
            Error.msg(tag + "s cannot be unused");
            att.isUnused = false;
        }
        if (att.isObsolete && !allowed.contains(Attr.Obsolete)) {
            Error.msg(tag + "s cannot be obsolete");
            att.isObsolete = false;
        }
        if (att.isInline && !allowed.contains(Attr.Inline)) {
            Error.msg(tag + "s cannot be inline");
            att.isInline = false;
        }
        if (att.isImplicit && !allowed.contains(Attr.Implicit)) {
            Error.msg(tag + "s cannot be declared <*IMPLICIT*>");
            att.isImplicit = false;
        }
        if (att.isExternal && !allowed.contains(Attr.External)) {
            Error.msg(tag + "s cannot be external");
            att.isExternal  = false;
            att.callingConv = null;
            att.alias       = M3ID.NoID;
        } else if (got_cc && att.callingConv != null && !allowed.contains(Attr.CallConv)) {
            Error.msg(tag + "s do not have calling conventions");
            att.callingConv = null;
        }
    }
}
