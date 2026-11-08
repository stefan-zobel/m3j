// Java port of CM3's m3-sys/m3front/src/stmts/TypeCaseStmt.i3 and TypeCaseStmt.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import java.util.EnumSet;
import m3j.middle.M3CG;
import m3j.middle.M3ID;
import m3j.middle.Target.CGType;

// File: TypeCaseStmt.i3
// Last Modified On Fri Jun 24 09:32:45 PDT 1994 By kalsow

// File: TypeCaseStmt.m3
// Last modified on Wed Jun 29 17:17:47 PDT 1994 by kalsow
//      modified on Thu Feb 21 23:57:16 1991 by muller

public final class TypeCaseStmt {
    private TypeCaseStmt() {}

    public static final class P extends Stmt.T {
        public Expr.T expr;
        public Case cases;
        public boolean complete;
        public boolean hasElse;
        public Stmt.T elseBody;
        public long elseOrigin;

        @Override
        public void check(M3.CheckState cs) {
            TypeCaseStmt.check(this, cs);
        }

        @Override
        public EnumSet<Stmt.Outcome> compile() {
            return TypeCaseStmt.compile(this);
        }

        @Override
        public EnumSet<Stmt.Outcome> outcomes() {
            return getOutcome(this);
        }
    }

    /** Port: Case = REF RECORD ... END. */
    public static final class Case {
        public long origin;
        public Case next;
        public long nTags;
        public Type.T[] tags;
        public Variable.T var_;
        public Scope.T scope;
        public Stmt.T stmt;
    }

    // Port: TypeList = REF ARRAY OF Type.T is Type.T[].

    public static Stmt.T parse() {
        P p;
        boolean bar;
        p = new P();
        StmtRep.init(p);
        p.cases = null;
        p.complete = false;
        p.hasElse = false;
        p.elseBody = null;
        p.elseOrigin = 0;

        Scanner.match(Token.T.tTYPECASE);
        p.expr = Expr.parse();
        Scanner.match(Token.T.tOF);
        bar = (Scanner.cur.token == Token.T.tBAR);
        if ((bar)) {
            Scanner.getToken(); // |
        }
        while (true) {
            if ((Scanner.cur.token == Token.T.tELSE)) {
                break;
            }
            if ((Scanner.cur.token == Token.T.tEND)) {
                break;
            }
            bar = false;
            parseCase(p);
            if ((Scanner.cur.token != Token.T.tBAR)) {
                break;
            }
            bar = true;
            Scanner.getToken(); // |
        }

        reverseCases(p);
        if ((bar)) {
            Scanner.fail("missing case");
        }

        if ((Scanner.cur.token == Token.T.tELSE)) {
            p.elseOrigin = Scanner.offset;
            Scanner.getToken(); // ELSE
            p.hasElse = true;
            p.elseBody = Stmt.parse();
        }

        Scanner.match(Token.T.tEND);
        return p;
    }

    private static void parseCase(P p) {
        Case c;
        int id;
        Tracer.T trace;
        boolean u = false; // TYPECASE-bound Id has UNUSED pragma.
        c = new Case();
        c.origin = Scanner.offset;
        c.next = p.cases;
        p.cases = c;
        c.var_ = null;
        c.scope = null;
        c.stmt = null;
        c.nTags = 0;
        c.tags = new Type.T[2];

        while (true) {
            if ((c.nTags > c.tags.length - 1)) {
                expandTags(c);
            }
            c.tags[(int) c.nTags] = Type.parse();
            c.nTags++;
            if ((Scanner.cur.token != Token.T.tCOMMA)) {
                break;
            }
            Scanner.getToken(); // ,
        }

        if ((Scanner.cur.token == Token.T.tLPAREN)) {
            Scanner.getToken(); // (
            if (Scanner.cur.token == Token.T.tUNUSED) {
                u = true;
                Scanner.getToken(); // UNUSED
                Scanner.match(Token.T.tENDPRAGMA);
            }
            id = Scanner.matchID();
            trace = Variable.parseTrace();
            c.var_ = Variable.new_(id, u);
            c.scope = Scope.new1(c.var_);
            Variable.bindTrace(c.var_, trace);
            Variable.bindType(c.var_, c.tags[0], /* indirect := */ false,
                              /* readonly := */ false, /* open_array_ok := */ false,
                              /* needs_init := */ false);
            Scanner.match(Token.T.tRPAREN);
            Scanner.match(Token.T.tIMPLIES);
            c.stmt = Stmt.parse();
            Scope.popNew();
        } else {
            Scanner.match(Token.T.tIMPLIES);
            c.stmt = Stmt.parse();
        }
    }

    private static void expandTags(Case c) {
        Type.T[] new_, old;
        old = c.tags;
        new_ = new Type.T[2 * old.length];
        for (int i = 0; i <= old.length - 1; i++) {
            new_[i] = old[i];
        }
        c.tags = new_;
    }

    private static void reverseCases(P p) {
        Case c1, c2, c3;
        c1 = p.cases;
        c3 = null;
        while ((c1 != null)) {
            c2 = c1.next;
            c1.next = c3;
            c3 = c1;
            c1 = c2;
        }
        p.cases = c3;
    }

    private static void check(P p, M3.CheckState cs) {
        Type.T t;
        Case c;
        Expr.typeCheck(p.expr, cs);
        t = Type.base(Expr.typeOf(p.expr));

        if ((!Type.isSubtype(t, Reff.T))
            && (!Type.isSubtype(t, ObjectAdr.T))) {
            Error.msg("typecase selector must be a REF or OBJECT type");
        }

        // check each of the cases
        p.complete = p.hasElse;
        c = p.cases;
        while ((c != null)) {
            if (checkCase(c, t, cs)) {
                p.complete = true;
            }
            c = c.next;
        }

        Stmt.typeCheck(p.elseBody, cs);

        if ((!p.complete)) {
            Scanner.offset = p.origin;
            Error.warn(1, "TYPECASE statement may not handle all cases");
        }
    }

    private static boolean checkCase(Case c, Type.T exprType,
                                     M3.CheckState cs) {
        Type.T t, u;
        boolean complete;
        Scope.T zz;
        // check the labels
        complete = false;
        u = c.tags[0];
        for (long i = 0; i <= c.nTags - 1; i++) {
            t = Type.check(c.tags[(int) i]);
            c.tags[(int) i] = t;
            if ((c.scope != null) && (!Type.isEqual(t, u, null))) {
                Scanner.offset = c.origin;
                Error.msg("type labels are incompatible");
            }
            if (!Type.isSubtype(t, exprType)) {
                // AND (NOT Type.IsSubtype (exprType, t)) THEN
                Scanner.offset = c.origin;
                Error.msg("type label incompatible with case expression");
            }
            complete = complete || Type.isSubtype(exprType, t);
        }

        // check the body
        if ((c.scope != null)) {
            zz = Scope.push(c.scope);
              Scope.typeCheck(c.scope, cs);
              Stmt.typeCheck(c.stmt, cs);
              Scope.warnUnused(c.scope);
            Scope.pop(zz);
        } else {
            Stmt.typeCheck(c.stmt, cs);
        }
        return complete;
    }

    private static EnumSet<Stmt.Outcome> compile(P p) {
        Case c;
        M3CG.Var ref;
        long i;
        EnumSet<Stmt.Outcome> oc;
        boolean[] foundForSure = new boolean[] { false };
        long type_tbl;
        long addr_align;
        long n_cases = cntCases(p.cases);
        long n_labels = cntLabels(p.cases);
        long l_base = CG.next_label(n_cases);
        long l_else = CG.next_label();
        long l_exit = CG.next_label();
        Type.T ref_type = Type.base(Expr.typeOf(p.expr));
        long[] labels = new long[(int) (n_labels + 1)];
        long cell_size = M3.target.Address.pack + M3.target.Address.pack;
        Procedure.T proc;
        // build and intialize the type table
        type_tbl = Module.allocate((n_labels + 1) * cell_size,
                                   M3.target.Address.align, false,
                                   "TYPECASE type table");
        {
            long label_cnt = 0;
            long case_label = l_base;
            long uid_offset = type_tbl + M3.target.Address.size;
            Type.T label_type;
            c = p.cases;
            while ((c != null)) {
                for (long z = 0; z <= c.nTags - 1; z++) {
                    label_type = c.tags[(int) z];
                    labels[(int) label_cnt] = case_label;
                    label_cnt++;
                    CG.init_intt(uid_offset, M3.target.Integer.size,
                                 Type.globalUID(label_type), false);
                    uid_offset += cell_size;
                }
                c = c.next;
                case_label++;
            }
            labels[(int) label_cnt] = l_else;
        }

        // capture the ref
        Type.compile(ref_type);
        Expr.prep(p.expr);
        Expr.compile(p.expr);
        ref = CG.declare_local(M3ID.NoID, M3.target.Address.size,
                               M3.target.Address.align, CGType.Addr,
                               (int) Type.globalUID(ref_type),
                               /* in_memory := */ false, /* up_level := */ false,
                               /* f := */ CG.Never);
        CG.store_addr(ref);

        // let the runtime scan the table
        proc = RunTyme.lookUpProc(RunTyme.Hook.ScanTypecase);
        addr_align = Expr.alignment(p.expr);
        Procedure.startCall(proc);
        if (M3.target.DefaultCall.args_left_to_right) {
            CG.load_addr(ref, 0, addr_align);
            CG.pop_param(CGType.Addr);
            CG.load_addr_of(Module.globalData(false), type_tbl, CG.Max_alignment);
            CG.pop_param(CGType.Addr);
        } else {
            CG.load_addr_of(Module.globalData(false), type_tbl, CG.Max_alignment);
            CG.pop_param(CGType.Addr);
            CG.load_addr(ref, 0, addr_align);
            CG.pop_param(CGType.Addr);
        }
        Procedure.emitCall(proc);
        CG.case_jump(labels);

        // compile the case bodies
        oc = EnumSet.noneOf(Stmt.Outcome.class);
        c = p.cases;
        i = 0;
        while ((c != null)) {
            oc.addAll(compileCaseBody(p, c, ref, l_base + i, l_exit, foundForSure));
            c = c.next;
            i++;
        }

        // generate the else clause
        CG.set_label(l_else);
        if ((p.hasElse)) {
            if (foundForSure[0]) {
                Scanner.offset = p.elseOrigin;
                Error.warn(1, "unreachable ELSE in TYPECASE");
            }
            oc.addAll(Stmt.compile(p.elseBody));
        } else if ((!foundForSure[0]) && (Host.doTCaseChk)) {
            CG.abort(M3CG.RuntimeError.UnhandledTypecase);
        }

        CG.set_label(l_exit);
        return oc;
    }

    private static long cntCases(Case c) {
        long n = 0;
        while ((c != null)) {
            n++;
            c = c.next;
        }
        return n;
    }

    private static long cntLabels(Case c) {
        long n = 0;
        while ((c != null)) {
            n += c.nTags;
            c = c.next;
        }
        return n;
    }

    private static EnumSet<Stmt.Outcome> compileCaseBody(P p, Case c, M3CG.Var ref,
                                                         long case_label, long exit_label,
                                                         boolean[] done) {
        EnumSet<Stmt.Outcome> oc;
        Scope.T zz;
        CG.gen_location(c.origin);
        CG.set_label(case_label);
        if ((done[0])) {
            Error.warn(1, "unreachable case");
        }

        if ((c.scope != null)) {
            zz = Scope.push(c.scope);
              Scope.enter(c.scope);
              Scope.initValues(c.scope);
              Variable.loadLValue(c.var_);
              CG.load_addr(ref, 0, M3.target.Address.align);
              CG.store_indirect(CGType.Addr, 0, M3.target.Address.size);
              Variable.scheduleTrace(c.var_);
              oc = Stmt.compile(c.stmt);
              if (oc.contains(Stmt.Outcome.FallThrough)) {
                  CG.jump(exit_label);
              }
              // for the debugger's sake, this Jump should be inside the scope
              Scope.exit(c.scope);
            Scope.pop(zz);
        } else {
            oc = Stmt.compile(c.stmt);
            if (oc.contains(Stmt.Outcome.FallThrough)) {
                CG.jump(exit_label);
            }
        }

        // check to see if this case handles all incoming types
        if (!done[0]) {
            Type.T u = Expr.typeOf(p.expr);
            for (long i = 0; i <= c.nTags - 1; i++) {
                if (Type.isSubtype(u, c.tags[(int) i])) {
                    done[0] = true;
                    break;
                }
            }
        }

        return oc;
    }

    private static EnumSet<Stmt.Outcome> getOutcome(P p) {
        Case c;
        EnumSet<Stmt.Outcome> oc = EnumSet.noneOf(Stmt.Outcome.class);
        c = p.cases;
        while ((c != null)) {
            oc.addAll(Stmt.getOutcome(c.stmt));
            c = c.next;
        }
        if ((p.hasElse)) {
            oc.addAll(Stmt.getOutcome(p.elseBody));
        }
        return oc;
    }
}
