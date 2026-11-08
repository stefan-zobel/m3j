// Java port of CM3's m3-sys/m3front/src/stmts/Stmt.i3 and Stmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3ID;
import m3j.util.AssertError;

// File: Stmt.i3
// Last Modified On Thu Feb 23 15:22:19 PST 1995 By kalsow
//      Modified On Tue Jan 16 06:44:23 1990 By muller

// File: Stmt.m3
// Last modified on Thu Apr  6 12:59:21 PDT 1995 by kalsow
//      modified on Tue Jan 16 06:44:48 1990 by muller

public final class Stmt {
    private Stmt() {}

    /**
     * Port: M3.Stmt, with the fields and methods of its revelation in
     * StmtRep.
     */
    public static class T extends M3.Node {
        public T next;

        public void check(M3.CheckState cs) {
            throw new AssertError("StmtRep.check");
        }

        public EnumSet<Outcome> compile() {
            throw new AssertError("StmtRep.compile");
        }

        public EnumSet<Outcome> outcomes() {
            throw new AssertError("StmtRep.outcomes");
        }
    }

    // Port: CheckState = M3.CheckState.

    public enum Outcome { FallThrough, Exits, Returns }

    // Port: Outcomes = SET OF Outcome is EnumSet<Outcome>.

    public static Stmt.T parse() {
        Stmt.T t, first, last;
        first = null;
        last = null;
        while (true) {
            switch (Scanner.cur.token) {
                case tCONST,
                     tTYPE,
                     tREVEAL,
                     tVAR,
                     tEXTERNAL,
                     tINLINE,
                     tUNUSED,
                     tOBSOLETE,
                     tEXCEPTION,
                     tCALLCONV,
                     tPROCEDURE,
                     tFATAL,
                     tBEGIN -> t = BlockStmt.parse(true);
                case tIDENT,
                     tLPAREN,
                     tARRAY,
                     tRECORD -> t = AssignStmt.parse();
                case tASSERT -> t = AssertStmt.parse();
                case tCASE -> t = CaseStmt.parse();
                case tDEBUG -> t = DebugStmt.parse();
                case tEXIT -> t = ExitStmt.parse();
                case tEVAL -> t = EvalStmt.parse();
                case tFOR -> t = ForStmt.parse();
                case tIF -> t = IfStmt.parse();
                case tLOCK -> t = LockStmt.parse();
                case tLOOP -> t = LoopStmt.parse();
                case tRAISE -> t = RaiseStmt.parse();
                case tREPEAT -> t = RepeatStmt.parse();
                case tRETURN -> t = ReturnStmt.parse();
                case tTRY -> t = TryStmt.parse();
                case tTYPECASE -> t = TypeCaseStmt.parse();
                case tWHILE -> t = WhileStmt.parse();
                case tWITH -> t = WithStmt.parse();
                default -> {
                    return first;
                }
            }
            if ((first == null)) {
                first = t;
            } else {
                last.next = t;
            }
            last = t;
            if ((Scanner.cur.token == Token.T.tSEMI)) {
                Scanner.getToken(); // ;
                {
                    long err_line = -1;
                    while ((Scanner.cur.token == Token.T.tSEMI)) {
                        // handle empty statements gracefully
                        if ((err_line != Scanner.offset)) {
                            Error.msg("empty statement");
                            err_line = Scanner.offset;
                        }
                        Scanner.getToken(); // ;
                    }
                }
            } else if (Token.StmtStart.contains(Scanner.cur.token)) {
                // assume the simple mistake and keep going
                Error.msg("expected \";\", found "
                    + M3ID.toText(M3.ids, Token.name[Scanner.cur.token.ordinal()])
                    + " instead");
            } else {
                return first;
            }
        }
        // RETURN first;
    }

    // Port: Init (exported to StmtRep) is StmtRep.init.

    /**
     * we defer calling Coverage.NoteLine until typechecking because
     * some statements (e.g. assign, call) adjust their origins after
     * they've called Init.
     */
    public static void typeCheck(Stmt.T t, M3.CheckState cs) {
        while ((t != null)) {
            Scanner.offset = t.origin;
            Coverage.noteLine();
            t.check(cs);
            t = t.next;
        }
    }

    public static EnumSet<Stmt.Outcome> compile(Stmt.T t) {
        EnumSet<Outcome> oc, xc; // x: T;
        // x := t;
        oc = EnumSet.of(Outcome.FallThrough);
        while ((t != null)) {
            CG.free_temps();
            Scanner.offset = t.origin;
            CG.gen_location(t.origin);
            Coverage.countLine();
            Tracer.emitPending();
            xc = t.compile();
            // DumpOutcome (t, xc);
            oc.addAll(xc);
            if (xc.contains(Outcome.FallThrough)) {
                t = t.next;
            } else {
                if ((t.next != null)) {
                    Scanner.offset = t.next.origin;
                    Error.warn(1, "unreachable statement");
                }
                t = null;
                oc.remove(Outcome.FallThrough);
            }
        }
        Tracer.emitPending();
        // DumpOutcome (x, oc);
        CG.free_temps();
        return oc;
    }

    public static EnumSet<Stmt.Outcome> getOutcome(Stmt.T t) {
        EnumSet<Outcome> oc, xc;
        // Port, omitted: x := t; (only used by the disabled DumpOutcome).
        oc = EnumSet.of(Outcome.FallThrough);
        while ((t != null)) {
            xc = t.outcomes();
            // DumpOutcome (t, xc);
            oc.addAll(xc);
            if (xc.contains(Outcome.FallThrough)) {
                t = t.next;
            } else {
                t = null;
                oc.remove(Outcome.FallThrough);
            }
        }
        // DumpOutcome (x, oc);
        return oc;
    }

    // Port, omitted: the procedure DumpOutcome, which is commented out in
    // the original.
}
