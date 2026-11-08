// Java port of CM3's m3-sys/m3front/src/stmts/WithStmt.i3 and WithStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;

import m3j.middle.Target;
import m3j.middle.Target.CGType;

// File: WithStmt.i3
// Last Modified On Fri Jun 24 09:22:54 PDT 1994 By kalsow

// File: WithStmt.m3
// Last modified on Tue Jun 20 09:28:07 PDT 1995 by kalsow
//      modified on Fri Jun 16 12:48:09 PDT 1995 by ericv
//      modified on Tue Jun 26 08:01:23 1990 by muller

public final class WithStmt {
    private WithStmt() {}

    public enum Kind { designator, openarray, structure, other }

    public static final class P extends Stmt.T {
        public Variable.T var_;
        public Expr.T expr;
        public Scope.T scope;
        public Stmt.T body;
        public Kind kind;

        @Override
        public void check(M3.CheckState cs) {
            WithStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return WithStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    public static Stmt.T parse() {
        Scanner.match(Token.T.tWITH);
        return parseTail();
    }

    private static Stmt.T parseTail() {
        P p;
        int id;
        Tracer.T trace;
        boolean u = false; // WITH-bound Id has UNUSED pragma.
        p = new P();
        StmtRep.init(p);
        if (Scanner.cur.token == Token.T.tUNUSED) {
            u = true;
            Scanner.getToken(); // UNUSED
            Scanner.match(Token.T.tENDPRAGMA);
        }
        id = Scanner.matchID();
        trace = Variable.parseTrace();
        p.var_ = Variable.new_(id, u);
        Scanner.match(Token.T.tEQUAL);
        p.expr = Expr.parse();
        p.scope = Scope.new1(p.var_);
        Variable.bindTrace(p.var_, trace);
        if ((Scanner.cur.token == Token.T.tCOMMA)) {
            Scanner.getToken(); // ,
            p.body = parseTail();
        } else {
            Scanner.match(Token.T.tDO);
            p.body = Stmt.parse();
            Scanner.match(Token.T.tEND);
        }
        Scope.popNew();
        return p;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T t;
        Scope.T zz;
        Expr.typeCheck(p.expr, cs);
        t = Expr.typeOf(p.expr);

        if (OpenArrayType.is(t)) {
            p.kind = Kind.openarray;
            Variable.needsAddress(p.var_);
            ArrayExpr.noteTargetType(p.expr, t);
        } else if (Expr.isDesignator(p.expr)) {
            p.kind = Kind.designator;
            Expr.needsAddress(p.expr);
        } else if (Type.isStructured(t)) {
            p.kind = Kind.structure;
            Variable.needsAddress(p.var_);
            AssignStmt.check(t, p.expr, cs);
        } else {
            p.kind = Kind.other;
        }

        Variable.bindType(p.var_, t, /* indirect := */ (p.kind == Kind.designator),
                          /* readonly := */ !Expr.isWritable(p.expr, /* traced := */ false),
                          /* open_array_ok := */ true, /* needs_init := */ false);

        Scope.typeCheck(p.scope, cs);
        if (p.kind == Kind.designator
            && Math.floorMod(Expr.alignment(p.expr), (long) Target.Byte) != 0
            && PackedType.is(Expr.repTypeOf(p.expr))) {
            // Not in CM3: a packed field or element that need not start
            // at a byte; see compilePackedAddress.
            Variable.bindPacked(p.var_, Expr.repTypeOf(p.expr));
        }
        zz = Scope.push(p.scope);
          Stmt.typeCheck(p.body, cs);
        Scope.pop(zz);
        Scope.warnUnused(p.scope);
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        EnumSet<Stmt.Outcome> oc;
        Scope.T zz;
        Type.Info info = new Type.Info();
        CG.Val val = null;
        long dope_size;
        Type.T t;
        Type.T[] tlhs = new Type.T[1];
        boolean[] global = new boolean[1], indirect = new boolean[1],
                  traced = new boolean[1];
        t = Type.checkInfo(Value.typeOf(p.var_), info);

        // evaluate the expr outside the new scope and capture its value
        Expr.checkUseFailure(p.expr);
        switch (p.kind) {
            case designator -> {
                Variable.split(p.var_, tlhs, global, indirect, traced);
                Expr.prepLValue(p.expr, traced[0]);
                if (Variable.isPackedAlias(p.var_)) {
                    compilePackedAddress(p, traced[0]);
                } else {
                    Expr.compileAddress(p.expr, traced[0]);
                }
                val = CG.pop();
            }
            case structure -> {
                tlhs[0] = Value.typeOf(p.var_);
                ArrayExpr.noteUseTargetVar(p.expr);
                AssignStmt.prepForEmit(tlhs[0], p.expr, /* initializing := */ true);
            }
            case openarray, other -> {
                Expr.prep(p.expr);
                Expr.compile(p.expr);
                val = CG.pop();
            }
        }

        // open the new scope
        zz = Scope.push(p.scope);
          Scope.enter(p.scope);

          // initialize the variable
          switch (p.kind) {
              case designator -> {
                  CG.push(val);
                  Variable.setLValue(p.var_);
                  CG.free(val);
              }
              case openarray -> {
                  // Copy dope-only into p.var.
                  dope_size = OpenArrayType.openDepth(t) * M3.target.Integer.pack;
                  // p.var will have been declared with independently-computed
                  //  size equal to dope_size.  See Variable.Declare.
                  dope_size += M3.rt.OA_sizes;
                  Variable.loadLValue(p.var_);
                  CG.push(val);
                  CG.copy(dope_size, /* overlap := */ false);
                  CG.free(val);

                  // CHECK: p.expr can, if it's a truly dynamic open array constructor, be
                  //        compiled as a heap-allocated temporary, complete with contiguous
                  //        dope.  This will keep a pointer only to its interior, i.e., its
                  //        elements.  Is this enough to protect it from CG? See heapTempPtr
                  //        in ArrayExpr.m3, which maintains a pointer to the dope, but it
                  //        will remain until the containing procedure is exited -- overly
                  //        long.
              }
              case structure -> {
                  Variable.loadLValue(p.var_);
                  AssignStmt.doEmit(tlhs[0], p.expr, /* initializing := */ true);
              }
              case other -> {
                  Variable.loadLValue(p.var_);
                  CG.push(val);
                  CG.store_indirect(info.stk_type, 0, info.size);
                  CG.free(val);
              }
          }
          Variable.scheduleTrace(p.var_);

          oc = Stmt.compile(p.body);
          Scope.exit(p.scope);
        Scope.pop(zz);
        return oc;
    }

    /**
     * Not in CM3 (whose code generator failed on such an address): the
     * designator need not start at a byte: a field of a packed record or an
     * element of a packed array.  The name holds an address before it,
     * aligned as the record or array, and its variable the offset in bits
     * from there.  A packed element whose offset is known only at run time
     * (an index that is not constant) is refused.
     */
    private static void compilePackedAddress(P p, boolean traced) {
        long bias;
        long[] align = new long[1];
        Expr.compileLValue(p.expr, traced);
        bias = CG.split_bit_offset(align);
        if (bias < 0) {
            Error.msg("CM3 restriction: WITH cannot bind a name to a packed"
                      + " element whose bit offset is known only at run time");
            CG.discard(CGType.Addr);
            CG.load_nil();
            return;
        }
        CG.check_byte_aligned();
        p.var_.bitBias = bias;
        p.var_.biasAlign = align[0];
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        return Stmt.getOutcome(p.body);
    }
}
