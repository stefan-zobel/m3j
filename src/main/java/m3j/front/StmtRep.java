// Java port of CM3's m3-sys/m3front/src/stmts/StmtRep.i3 and of the
// procedure Init in m3-sys/m3front/src/stmts/Stmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: StmtRep.i3
// Last Modified On Mon Sep 21 09:19:08 PDT 1992 By kalsow

public final class StmtRep {
    private StmtRep() {}

    // Port: the revelation of M3.Stmt is the class Stmt.T.

    /** initializes the common fields of a Stmt.T */
    public static void init(Stmt.T stmt) {
        // Port: the body of Init in Stmt.m3 (MODULE Stmt EXPORTS Stmt, StmtRep).
        stmt.next = null;
        stmt.origin = Scanner.offset;
    }
}
