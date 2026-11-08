// Java port of CM3's m3-sys/m3front/src/misc/M3Header.i3 and M3Header.m3.
// Original: Copyright (C) 1994, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.io.InputStream;
import m3j.middle.M3ID;

// File: M3Header.i3
// Last modified on Mon Jul 11 11:51:40 PDT 1994 by kalsow

// File: M3Header.m3
// Last modified on Mon Jul 11 11:55:37 PDT 1994 by kalsow

public final class M3Header {
    private M3Header() {}

    // Port: IDList = M3Front.IDList.

    // Port: TK = Token.T; GetToken and cur come from Scanner.

    /** Port: File.T is java.io.InputStream. */
    public static final class State {
        public M3Front.IDList imports = null;
        public InputStream generic = null;
        public boolean interface_ = false;
        public boolean failed = false;

        State copy() {
            State r = new State();
            r.imports = imports;
            r.generic = generic;
            r.interface_ = interface_;
            r.failed = failed;
            return r;
        }
    }

    public static M3Front.IDList parse() {
        State s = new State();
        int[] id = new int[1];
        pushID(s, M3ID.add(M3.ids, "RTHooks")); // compiler magic

        if ((Scanner.cur.token == Token.T.tEXTERNAL)) {
            skipExternalPragma();
        }
        if ((Scanner.cur.token == Token.T.tUNSAFE)) {
            Scanner.getToken();
        }

        s.interface_ = (Scanner.cur.token == Token.T.tINTERFACE);
        if ((Scanner.cur.token != Token.T.tINTERFACE) && (Scanner.cur.token != Token.T.tMODULE)) {
            return s.imports;
        }
        Scanner.getToken(); // INTERFACE/MODULE

        if (!matchID(s, id)) {
            return s.imports;
        }

        if ((Scanner.cur.token == Token.T.tEXPORTS)) {
            Scanner.getToken(); // EXPORTS
            matchIDList(s, /* save := */ true);
        } else if ((!s.interface_)) {
            pushID(s, id[0]);
        }

        if ((Scanner.cur.token == Token.T.tSEMI)) {
            // this is a simple module/interface, just fall through
            Scanner.getToken(); // ;
        } else if ((Scanner.cur.token == Token.T.tEQUAL)) {
            // this is an instantiation of a generic module/interface
            Scanner.getToken(); // =
            pushGeneric(s);
        } else { // bail out
            return s.imports;
        }

        parseImports(s);

        if ((s.generic != null)) {
            Scanner.pop();
            Host.closeFile(s.generic);
        }

        return s.imports;
    }

    private static void skipExternalPragma() {
        Scanner.getToken(); // EXTERNAL
        if ((Scanner.cur.token == Token.T.tIDENT) || (Scanner.cur.token == Token.T.tTEXTCONST)) {
            Scanner.getToken(); // IDENT, TEXTCONST
            if ((Scanner.cur.token == Token.T.tCOLON)) {
                Scanner.getToken(); // :
                if ((Scanner.cur.token == Token.T.tIDENT) || (Scanner.cur.token == Token.T.tTEXTCONST)) {
                    Scanner.getToken(); // language
                }
            }
        }
        if ((Scanner.cur.token == Token.T.tENDPRAGMA)) {
            Scanner.getToken(); // "*>"
        }
    }

    /** instantiate a call on a generic interface or module */
    private static void pushGeneric(State s) {
        int[] genericName = new int[1];
        String[] filename = new String[1];
        String[] old_filename = new String[1];
        long[] old_line = new long[1];
        Scanner.here(old_filename, old_line);

        if (!matchID(s, genericName)) {
            return;
        }

        // parse the list of actuals
        parseGenericArgs(s, /* save := */ true);

        // open the external file
        s.generic = Host.openUnit(genericName[0], s.interface_, true, filename);
        if ((s.generic == null)) {
            s.failed = true;
            return;
        }

        // build a synthetic file name & start reading
        filename[0] = old_filename[0] + " => " + filename[0];
        Scanner.push(filename[0], s.generic, /* is_main := */ Scanner.in_main);

        // make sure we got what we wanted
        if (!match(s, Token.T.tGENERIC)) {
            return;
        }
        if ((Scanner.cur.token != Token.T.tINTERFACE) && (Scanner.cur.token != Token.T.tMODULE)) {
            return;
        }
        Scanner.getToken();

        // get the generic's name
        if (!match(s, Token.T.tIDENT)) {
            return;
        }

        // skip the list of formals
        parseGenericArgs(s, /* save := */ false);

        match(s, Token.T.tSEMI);
    }

    private static void parseGenericArgs(State s, boolean save) {
        if (!match(s, Token.T.tLPAREN)) {
            return;
        }
        matchIDList(s, save);
        if (!match(s, Token.T.tRPAREN)) {
            return;
        }
    }

    private static void parseImports(State s) {
        for (;;) {
            if ((Scanner.cur.token == Token.T.tIMPORT)) {
                parseImport(s);
            } else if ((Scanner.cur.token == Token.T.tFROM)) {
                parseFromImport(s);
            } else {
                break;
            }
        }
    }

    private static void parseImport(State s) {
        int[] id = new int[1];
        Scanner.getToken(); // IMPORT
        for (;;) {
            if (!matchID(s, id)) {
                return;
            }
            pushID(s, id[0]);

            if ((Scanner.cur.token == Token.T.tAS)) {
                Scanner.getToken(); // AS
                if (!matchID(s, id)) {
                    return;
                }
            }

            if ((Scanner.cur.token != Token.T.tCOMMA)) {
                break;
            }
            Scanner.getToken(); // ,
        }
        match(s, Token.T.tSEMI);
    }

    /**
     * Sic: s is passed by value, so neither the imported interface nor a
     * failure reaches the caller's state.
     */
    private static void parseFromImport(State s_) {
        State s = s_.copy();
        int[] id = new int[1];
        Scanner.getToken(); // FROM
        if (!matchID(s, id)) {
            return;
        }
        pushID(s, id[0]);
        if (!match(s, Token.T.tIMPORT)) {
            return;
        }
        matchIDList(s, /* save := */ false);
        match(s, Token.T.tSEMI);
    }

    private static void matchIDList(State s, boolean save) {
        int[] id = new int[1];
        for (;;) {
            if (!matchID(s, id)) {
                return;
            }
            if (save) {
                pushID(s, id[0]);
            }
            if ((Scanner.cur.token != Token.T.tCOMMA)) {
                break;
            }
            Scanner.getToken(); // ,
        }
    }

    private static boolean match(State s, Token.T t) {
        if ((s.failed) || (Scanner.cur.token != t)) {
            s.failed = true;
            return false;
        }
        Scanner.getToken();
        return true;
    }

    /** Port: the VAR (OUT) parameter id is a one-element array. */
    private static boolean matchID(State s, int[] id) {
        if ((s.failed) || (Scanner.cur.token != Token.T.tIDENT)) {
            s.failed = true;
            return false;
        }
        id[0] = Scanner.cur.id;
        Scanner.getToken();
        return true;
    }

    private static void pushID(State s, int id) {
        M3Front.IDList r = new M3Front.IDList();
        r.interface_ = id;
        r.next = s.imports;
        s.imports = r;
    }
}
