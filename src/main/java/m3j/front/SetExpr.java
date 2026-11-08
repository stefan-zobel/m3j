// Java port of CM3's m3-sys/m3front/src/exprs/SetExpr.i3 and SetExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3CG;
import m3j.middle.TInt;
import m3j.middle.TWord;
import m3j.middle.Target;
import m3j.middle.Target.CGType;
import m3j.util.AssertError;

// File: SetExpr.i3
// Last Modified On Thu Sep  9 13:59:20 PDT 1993 By kalsow
//      Modified On Fri Jul  6 23:46:44 1990 By muller

// File: SetExpr.m3
// Last modified on Fri Feb 24 16:47:18 PST 1995 by kalsow
//      modified on Thu May 20 08:20:18 PDT 1993 by muller

/** For set constructors. */
public final class SetExpr {
    private SetExpr() {}

    /** Port: Node = REF RECORD ... END. */
    public static final class Node {
        public Node next;
        public long min;
        public long max;
    }

    public static final class P extends Expr.T {
        public Type.T tipe;
        public Expr.T[] args;
        public Node tree;
        public Expr.T[] others;
        public long nOthers;
        public M3CG.Var tmp;
        public long globalOffset;
        public long minI, maxI; // Of the set's element type.
        public int[] minT = TInt.newInt(), maxT = TInt.newInt();
        public String RTErrorMsg = null;
        public M3CG.RuntimeError RTErrorCode = M3CG.RuntimeError.Unknown;
        public boolean mapped;
        public boolean is_const;
        public boolean broken;

        @Override
        public void check(M3.CheckState cs) {
            SetExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            SetExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            SetExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return SetExpr.evaluate(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public boolean isZeroes(boolean lhs) {
            return SetExpr.isZeroes(this, lhs);
        }

        @Override
        public void genFPLiteral(M3Buf.T mbuf) {
            SetExpr.genFPLiteral(this, mbuf);
        }

        @Override
        public void genLiteral(long offset, Type.T type, boolean is_const) {
            SetExpr.genLiteral(this, offset, type, is_const);
        }

        @Override
        public boolean checkUseFailure() {
            return SetExpr.checkUseFailure(this);
        }

        // Port: the overrides typeOf, repTypeOf := ExprRep.NoType, prepLV :=
        // ExprRep.NotLValue, compileLV := ExprRep.NotLValueBool, prepBR,
        // compileBR := ExprRep.NotBoolean, getBounds := ExprRep.NoBounds,
        // isWritable, isDesignator := ExprRep.IsNever, prepLiteral :=
        // ExprRep.NoPrepLiteral and note_write := ExprRep.NotWritable restate
        // the inherited methods.
    }

    public static final class VisitState {
        public Node a, b;       // private to the iterator
        public long amin, amax; // "
        public long bmin, bmax; // "
        public long min, max;   // resulting range
        public boolean inA, inB; // location of resulting range

        VisitState() {}

        VisitState copy() {
            VisitState r = new VisitState();
            r.a = a;
            r.b = b;
            r.amin = amin;
            r.amax = amax;
            r.bmin = bmin;
            r.bmax = bmax;
            r.min = min;
            r.max = max;
            r.inA = inA;
            r.inB = inB;
            return r;
        }
    }

    // (*CONST*)
    private static long Grain; // allocation grain for set values
    private static final int[] full = TInt.newInt();
    /** Port: ARRAY [0..64] OF Target.Int. */
    private static final int[][] left = newIntArray(65);
    /** Port: ARRAY [0..64] OF Target.Int. */
    private static final int[][] right = newIntArray(65);

    private static int[][] newIntArray(int n) {
        int[][] r = new int[n][];
        for (int i = 0; i < n; i++) {
            r[i] = TInt.newInt();
        }
        return r;
    }

    public static Expr.T new_(Type.T type, Expr.T[] args) {
        P p;
        p = new P();
        ExprRep.init(p);
        p.type = type;
        p.repType = Type.stripPacked(type);
        p.tipe = type;
        p.args = args;
        p.mapped = false;
        p.broken = false;
        p.tree = null;
        p.others = null;
        p.nOthers = -1;
        p.tmp = null;
        p.is_const = true;
        p.checked = false;
        p.RTErrorCode = M3CG.RuntimeError.Unknown;
        p.RTErrorMsg = null;
        return p;
    }

    private static Expr.T newFromTree(P p, Node node) {
        P c;
        c = new P();
        c.origin = p.origin;
        c.type = p.type;
        c.repType = p.repType;
        c.checked = p.checked;
        c.tipe = p.tipe;
        c.args = p.args;
        c.mapped = true;
        c.broken = false;
        c.tree = normalizeTree(node);
        c.others = null;
        c.nOthers = -1;
        c.is_const = true;
        // Not as in CM3: the bounds of the element type are copied too.
        // The new set is as checked as p, so its check does not set them
        // again, and with bounds 0 its bit numbers were relative to 0, not
        // to the first element (CM3 failed with a subscript out of range on
        // a negative element when such a set was passed by value).
        c.minI = p.minI;
        c.maxI = p.maxI;
        TInt.assign(c.minT, p.minT);
        TInt.assign(c.maxT, p.maxT);
        // Not as in CM3: the alignment is unknown, so Expr.alignment
        // computes it; CM3 left it 0, a set not aligned to a byte, and
        // refused to pass the set to a READONLY formal.
        c.align = Target.UnknownAlign;
        // Sic: resets the fields of p, not of c.
        p.RTErrorCode = M3CG.RuntimeError.Unknown;
        p.RTErrorMsg = null;
        return c;
    }

    /** Purely syntactic. Will not look through a ConsExpr. */
    public static boolean is(Expr.T e) {
        // Port: TYPECODE (NIL) is not TYPECODE (P).
        return (e != null && e.getClass() == P.class);
    }

    /** Port: s is the VAR parameter sign. */
    public static boolean compare(Expr.T a, Expr.T b, long[] s) {
        P[] p = new P[1], q = new P[1];
        boolean le = true, eq = true, ge = true;
        VisitState vs = new VisitState();
        if (!checkPair(a, b, p, q)) {
            return false;
        }
        setupVisit(vs, p[0].tree, q[0].tree);
        while (visit(vs)) {
            if ((vs.min <= vs.max)) {
                // we got a non-empty range
                if ((vs.inA) && (!vs.inB)) {
                    eq = false;
                    le = false;
                } else if ((vs.inB) && (!vs.inA)) {
                    eq = false;
                    ge = false;
                }
            }
        }
        if ((le && !eq)) {
            s[0] = -1;
        } else if ((ge && !eq)) {
            s[0] = 1;
        } else if ((eq)) {
            s[0] = 0;
        } else {
            s[0] = -99;
        }
        return true;
    }

    public static boolean union(Expr.T a, Expr.T b, Expr.T[] c) {
        P[] p = new P[1], q = new P[1];
        Node n, x;
        if (!checkPair(a, b, p, q)) {
            return false;
        }
        n = null;
        x = p[0].tree;
        while ((x != null)) {
            n = addNode(n, x.min, x.max);
            x = x.next;
        }
        x = q[0].tree;
        while ((x != null)) {
            n = addNode(n, x.min, x.max);
            x = x.next;
        }
        c[0] = newFromTree(p[0], n);
        return true;
    }

    public static boolean intersection(Expr.T a, Expr.T b, Expr.T[] c) {
        P[] p = new P[1], q = new P[1];
        Node n;
        VisitState s = new VisitState();
        if (!checkPair(a, b, p, q)) {
            return false;
        }
        n = null;
        setupVisit(s, p[0].tree, q[0].tree);
        while (visit(s)) {
            if ((s.min <= s.max) && (s.inA) && (s.inB)) {
                n = addNode(n, s.min, s.max);
            }
        }
        c[0] = newFromTree(p[0], n);
        return true;
    }

    // Port, omitted: the commented-out, unimplemented procedure Negate.

    public static boolean difference(Expr.T a, Expr.T b, Expr.T[] c) {
        P[] p = new P[1], q = new P[1];
        Node n;
        VisitState s = new VisitState();
        if (!checkPair(a, b, p, q)) {
            return false;
        }
        n = null;
        setupVisit(s, p[0].tree, q[0].tree);
        while (visit(s)) {
            if ((s.min <= s.max) && (s.inA) && (!s.inB)) {
                n = addNode(n, s.min, s.max);
            }
        }
        c[0] = newFromTree(p[0], n);
        return true;
    }

    public static boolean symDifference(Expr.T a, Expr.T b, Expr.T[] c) {
        P[] p = new P[1], q = new P[1];
        Node n;
        VisitState s = new VisitState();
        if (!checkPair(a, b, p, q)) {
            return false;
        }
        n = null;
        setupVisit(s, p[0].tree, q[0].tree);
        while (visit(s)) {
            if ((s.min <= s.max) && (s.inA != s.inB)) {
                n = addNode(n, s.min, s.max);
            }
        }
        c[0] = newFromTree(p[0], n);
        return true;
    }

    public static boolean include(Expr.T set, Expr.T elt, Expr.T[] c) {
        P[] p = new P[1];
        long[] i = new long[1];
        Node n, x;
        if (!constElt(elt, i)) {
            return false;
        }
        if (!buildMap(set, p)) {
            return false;
        }
        n = addNode(null, i[0], i[0]);
        x = p[0].tree;
        while ((x != null)) {
            n = addNode(n, x.min, x.max);
            x = x.next;
        }
        c[0] = newFromTree(p[0], n);
        return true;
    }

    public static boolean exclude(Expr.T set, Expr.T elt, Expr.T[] c) {
        P[] p = new P[1];
        long[] i = new long[1];
        Node n, x;
        if (!constElt(elt, i)) {
            return false;
        }
        if (!buildMap(set, p)) {
            return false;
        }
        n = null;
        x = p[0].tree;
        while ((x != null)) {
            if ((x.min <= i[0]) && (i[0] <= x.max)) {
                n = addNode(n, x.min, i[0] - 1);
                n = addNode(n, i[0] + 1, x.max);
            } else {
                n = addNode(n, x.min, x.max);
            }
            x = x.next;
        }
        c[0] = newFromTree(p[0], n);
        return true;
    }

    public static boolean member(Expr.T set, Expr.T elt, Expr.T[] c) {
        P[] p = new P[1];
        long[] i = new long[1];
        Node x;
        if (!constElt(elt, i)) {
            return false;
        }
        if (!buildMap(set, p)) {
            return false;
        }
        x = p[0].tree;
        while ((x != null)) {
            if ((x.min <= i[0]) && (i[0] <= x.max)) {
                c[0] = Bool.Map[1];
                return true;
            }
            x = x.next;
        }
        c[0] = Bool.Map[0];
        return true;
    }

    private static boolean constElt(Expr.T elt, long[] i) {
        Type.T[] t = new Type.T[1];
        int[] int_ = TInt.newInt();
        elt = Expr.constValue(elt);
        if ((elt == null)) {
            return false;
        }
        return (IntegerExpr.split(elt, int_, t) || EnumExpr.split(elt, int_, t))
            && TInt.toInt(int_, i);
    }

    private static boolean checkPair(Expr.T a, Expr.T b, P[] p, P[] q) {
        return buildMap(a, p)
            && buildMap(b, q)
            && Type.isEqual(p[0].tipe, q[0].tipe, null);
    }

    private static void setupVisit(VisitState s, Node x, Node y) {
        s.a = x;
        s.b = y;
        if ((x != null)) {
            s.amin = x.min;
            s.amax = x.max;
        }
        if ((y != null)) {
            s.bmin = y.min;
            s.bmax = y.max;
        }
    }

    private static boolean visit(VisitState s) {
        if ((s.a == null) && (s.b == null)) {
            // both lists are empty
            return false;
        } else if ((s.a == null)) {
            // A list is empty
            s.min = s.bmin;
            s.max = s.bmax;
            s.inA = false;
            s.inB = true;
            s.bmin = s.bmax + 1;
        } else if ((s.b == null)) {
            // B list is empty
            s.min = s.amin;
            s.max = s.amax;
            s.inA = true;
            s.inB = false;
            s.amin = s.amax + 1;
        } else { // both lists are non-empty
            if ((s.amin < s.bmin)) {
                s.min = s.amin;
                s.inA = true;
                s.inB = false;
                if ((s.amax < s.bmin)) {
                    s.max = s.amax;
                    s.amin = s.amax + 1;
                } else { // s.amax >= s.bmin
                    s.max = s.bmin - 1;
                    s.amin = s.bmin;
                    s.bmin = s.amin;
                }
            } else if ((s.amin == s.bmin)) {
                s.min = s.amin;
                s.inA = true;
                s.inB = true;
                if ((s.bmax < s.amax)) {
                    s.max = s.bmax;
                    s.amin = s.bmax + 1;
                } else { // s.amax <= s.bmax
                    s.max = s.amax;
                    s.amin = s.amax + 1;
                }
                s.bmin = s.amin;
            } else { // s.amin > s.bmin
                s.min = s.bmin;
                s.inA = false;
                s.inB = true;
                if ((s.bmax < s.amin)) {
                    s.max = s.bmax;
                    s.bmin = s.bmax + 1;
                } else { // s.amin <= s.bmax
                    s.max = s.amin - 1;
                    s.amin = s.amin;
                    s.bmin = s.amin;
                }
            }
        }
        if ((s.amax < s.amin) && (s.a != null)) {
            s.a = s.a.next;
            if ((s.a != null)) {
                s.amin = s.a.min;
                s.amax = s.a.max;
            }
        }
        if ((s.bmax < s.bmin) && (s.b != null)) {
            s.b = s.b.next;
            if ((s.b != null)) {
                s.bmin = s.b.min;
                s.bmax = s.b.max;
            }
        }
        return true;
    }

    private static boolean buildMap(Expr.T e, P[] p) {
        Type.T[] t = new Type.T[1], range = new Type.T[1];
        Expr.T elt;
        Expr.T[] eMin = new Expr.T[1], eMax = new Expr.T[1];
        int[] from = TInt.newInt(), to = TInt.newInt(),
              min = TInt.newInt(), max = TInt.newInt();
        long[] iFrom = new long[1], iTo = new long[1];
        if (e == null) {
            return false;
        } else if (e instanceof P x) {
            p[0] = x;
        } else {
            return false;
        }
        if ((p[0].mapped)) {
            return (p[0].others == null);
        }
        p[0].mapped = true;
        if (!SetType.split(p[0].tipe, range)) {
            return false;
        }
        Type.getBounds(range[0], min, max);
        // IF TInt.LT (max, min) THEN RETURN FALSE END; ?? -- 8/2/93 WKK --
        for (int i = 0; i <= p[0].args.length - 1; i++) {
            elt = Expr.constValue(p[0].args[i]);
            if ((elt == null)) { // not a constant
                p[0].is_const = false;
                addOther(p[0], p[0].args[i]);
            } else if (IntegerExpr.split(elt, from, t)
                       || EnumExpr.split(elt, from, t)) {
                if (TInt.LT(from, min) || TInt.LT(max, from)) {
                    // CheckStaticRTErrEval will have emitted a warning.
                    // Omit the value from the constructor.
                } else if (TInt.toInt(from, iFrom)) {
                    p[0].tree = addNode(p[0].tree, iFrom[0], iFrom[0]);
                } else { // treat values outside host INTEGER as non-constants
                    addOther(p[0], elt);
                }
            } else if ((RangeExpr.split(elt, eMin, eMax))) {
                eMin[0] = Expr.constValue(eMin[0]);
                eMax[0] = Expr.constValue(eMax[0]);
                if ((eMin[0] != null) && (eMax[0] != null)
                    && (IntegerExpr.split(eMin[0], from, t)
                        || EnumExpr.split(eMin[0], from, t))
                    && (IntegerExpr.split(eMax[0], to, t)
                        || EnumExpr.split(eMax[0], to, t))) {
                    if (TInt.LT(from, min) || TInt.LT(max, from)) {
                        // CheckStaticRTErrEval will have emitted a warning.
                        // Omit the value from the constructor.
                    } else if (TInt.toInt(from, iFrom) && TInt.toInt(to, iTo)) {
                        p[0].tree = addNode(p[0].tree, iFrom[0], iTo[0]);
                    } else { // since the values are so big, treat them as non-constants
                        addOther(p[0], elt);
                    }
                } else { // not a constant range
                    addOther(p[0], elt);
                }
            } else {
                AssertError.check(false, "SetExpr");
            }
        } // FOR
        p[0].tree = normalizeTree(p[0].tree);
        return (p[0].others == null);
    }

    private static void addOther(P p, Expr.T elt) {
        if ((p.others == null)) {
            p.others = new Expr.T[p.args.length];
            p.nOthers = 0;
        }
        p.others[(int) p.nOthers] = elt;
        p.nOthers++;
    }

    private static Node addNode(Node n, long min, long max) {
        Node x;
        if ((max < min)) {
            return n;
        }
        x = n;
        while (true) {
            if ((x == null)) {
                x = new Node();
                x.next = n;
                x.min = min;
                x.max = max;
                return x;
            }
            // Sic: the second test is max <= x.min, not max <= x.max.
            if (((x.min <= min) && (min <= x.max))
                || ((x.min <= max) && (max <= x.min))) {
                if ((min < x.min)) {
                    x.min = min;
                }
                if ((x.max < max)) {
                    x.max = max;
                }
                return n;
            }
            x = x.next;
        }
    }

    private static Node normalizeTree(Node n) {
        Node x1, x2, x3;
        boolean done;
        if ((n == null)) {
            return null;
        }

        // destructively sort the input list
        done = false;
        while ((!done)) {
            done = true;
            x1 = n.next;
            x2 = n;
            x3 = null;
            while ((x1 != null)) {
                if ((x1.min < x2.min)) {
                    // swap x1 and x2
                    x2.next = x1.next;
                    x1.next = x2;
                    if ((x3 == null)) {
                        n = x1;
                    } else {
                        x3.next = x1;
                    }
                    x2 = x1;
                    x1 = x2.next;
                    done = false;
                }
                x3 = x2;
                x2 = x1;
                x1 = x1.next;
            }
        }

        // merge adjacent nodes
        x1 = n.next;
        x2 = n;
        while ((x1 != null)) {
            if ((x2.min <= x1.min) && (x1.min <= x2.max)) {
                if ((x2.max < x1.max)) {
                    x2.max = x1.max;
                }
                x1 = x1.next;
                x2.next = x1;
            } else {
                x2 = x1;
                x1 = x1.next;
            }
        }

        return n;
    }

    private static void mergeRTError(P p, M3CG.RuntimeError Code, String Msg) {
        if (p.RTErrorMsg == null) {
            p.RTErrorCode = Code;
            p.RTErrorMsg = Msg;
        }
    }

    // Externally dispatched-to:
    private static void check(P p, M3.CheckState cs) {
        Type.T[] eltType = new Type.T[1];
        Expr.T[] minExpr = new Expr.T[1], maxExpr = new Expr.T[1];
        Expr.T argExpr;
        Expr.T[] value = new Expr.T[1];
        int[] key = new int[1];
        String[] RTErrorMsg = new String[1];
        M3CG.RuntimeError[] RTErrorCode = new M3CG.RuntimeError[1];
        char[] buf = new char[TInt.Size + 1];
        long[] i64 = new long[1];
        // argType          : Type.T;
        // minT, maxT       : Target.Int;
        // minE, maxE       : Target.Int;
        p.tipe = Type.check(p.tipe);
        p.type = p.tipe;
        p.repType = Type.stripPacked(p.tipe);
        for (int i = 0; i <= p.args.length - 1; i++) {
            Expr.typeCheck(p.args[i], cs);
        }
        if (!SetType.split(p.tipe, eltType)) {
            // How can this happen? ConsExpr wouldn't have created this node, if not.
            Error.msg("Set constructor must specify a set type (2.6.8).");
            p.broken = true;
            p.checked = true;
            return;
        }
        Type.getBounds(eltType[0], p.minT, p.maxT);
        // Port: the VAR results of TInt.ToInt go through i64; the
        // conditional-or evaluates them in the same order.
        boolean minOK = TInt.toInt(p.minT, i64);
        if (minOK) {
            p.minI = i64[0];
        }
        boolean maxOK = minOK && TInt.toInt(p.maxT, i64);
        if (maxOK) {
            p.maxI = i64[0];
        }
        if (!minOK || !maxOK) {
            // Sic: the host range is printed as FIRST(INTEGER)..FIRST(INTEGER).
            Error.msg("CM3 restriction: set domain ("
                      + new String(buf, 0, TInt.toChars(p.minT, buf)) + ".."
                      + new String(buf, 0, TInt.toChars(p.maxT, buf))
                      + ") too large for host INTEGER ("
                      + Long.toString(Long.MIN_VALUE) + ".." + Long.toString(Long.MIN_VALUE) + ").");
            p.minI = Long.MIN_VALUE;
            AssertError.check(TInt.fromInt(p.minI, p.minT), "SetExpr");
            p.maxI = Long.MAX_VALUE;
            AssertError.check(TInt.fromInt(p.maxI, p.maxT), "SetExpr");
        }
        for (int i = 0; i <= p.args.length - 1; i++) {
            argExpr = p.args[i];

            if (KeywordExpr.split(argExpr, key, value)) {
                Error.msg("keyword values not allowed in set constructors");
                argExpr = value[0];
                p.args[i] = value[0];
            }

            if (RangeExpr.split(argExpr, minExpr, maxExpr)) {
                if (!Type.isAssignable(eltType[0], Expr.typeOf(minExpr[0]))) {
                    Error.msg("Range min is not assignable to set constructor's element type "
                              + "(2.6.8).");
                    p.broken = true;
                } else {
                    AssignStmt.checkStaticRTErrExec(eltType[0], minExpr[0], cs,
                        /* VAR */ RTErrorCode, /* VAR */ RTErrorMsg);
                    mergeRTError(p, RTErrorCode[0], RTErrorMsg[0]);
                }
                if (!Type.isAssignable(eltType[0], Expr.typeOf(maxExpr[0]))) {
                    Error.msg("Range max is not assignable to set constructor's element type "
                              + "(2.6.8).");
                    p.broken = true;
                } else {
                    AssignStmt.checkStaticRTErrExec(eltType[0], maxExpr[0], cs,
                        /* VAR */ RTErrorCode, /* VAR */ RTErrorMsg);
                    mergeRTError(p, RTErrorCode[0], RTErrorMsg[0]);
                }
            } else { // argExpr is a single set element value
                if (!Type.isAssignable(eltType[0], Expr.typeOf(argExpr))) {
                    Error.msg("Expression is not assignable to set constructor's element type "
                              + "(2.6.8).");
                    p.broken = true;
                } else {
                    AssignStmt.checkStaticRTErrExec(eltType[0], argExpr, cs,
                        /* VAR */ RTErrorCode, /* VAR */ RTErrorMsg);
                    mergeRTError(p, RTErrorCode[0], RTErrorMsg[0]);
                }
                minExpr[0] = argExpr;
                maxExpr[0] = argExpr;
            }
            p.checked = true;
            // Port, omitted: a commented-out check of the constant range
            // values against minT and maxT ("illegal set value").
        } // FOR
    }

    // Externally dispatched-to:
    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        P b;
        Expr.T ax, bx;
        if (e == null) {
            return false;
        } else if (e instanceof P bb) {
            b = bb;
        } else {
            return false;
        }

        if ((!Type.isEqual(a.tipe, b.tipe, x))
            || ((a.args == null) != (b.args == null))
            || ((a.args != null) && (a.args.length != b.args.length))) {
            return false;
        }

        for (int i = 0; i <= a.args.length - 1; i++) {
            ax = Expr.constValue(a.args[i]);
            if ((ax == null)) {
                ax = a.args[i];
            }
            bx = Expr.constValue(b.args[i]);
            if ((bx == null)) {
                bx = b.args[i];
            }
            if (!Expr.isEqual(ax, bx, x)) {
                return false;
            }
        }

        return true;
    }

    // Externally dispatched-to:
    private static void needsAddress(P p) {
        // yep, all sets get memory addresses
    }

    // Externally dispatched-to:
    private static void prep(P p) {
        Type.Info info = new Type.Info();
        if (p.broken) {
            return;
        }
        // Port: BuildMap (p, p) passes p also as the VAR result; it is p again.
        buildMap(p, new P[] {p}); // evaluate the constants
        // prep the non-constant elements
        for (int i = 0; i <= p.nOthers - 1; i++) {
            Expr.prep(p.others[i]);
        }

        Type.checkInfo(p.tipe, info);
        if ((info.size > M3.target.Integer.size) && (p.nOthers > 0)) {
            // large non-constant sets almost always turn into procedure
            // calls in the code generator, so we might as well make life
            // simpler there...
            p.tmp = prepBig(p, info);
        }
    }

    // Externally dispatched-to:
    private static void compile(P p, boolean StaticOnly) {
        Type.Info info = new Type.Info();
        AssertError.check(!StaticOnly, "SetExpr");
        // NOTE^ If ever duplicate static sets are removed as for arrays, this
        //  will need to change.
        if (p.broken) {
            return;
        }
        Type.checkInfo(p.tipe, info);
        if (info.size <= M3.target.Word.size) {
            // Set fits w/in one target word.
            compileSmall(p, info);
        } else if (p.nOthers <= 0) {
            // Multi-word set, but all elements are constant and within host INTEGER range.
            p.globalOffset = Module.allocate(info.size, info.alignment, true, "*set*");
            genLiteral(p, p.globalOffset, p.tipe, true);
            CG.load_addr_of(Module.globalData(true), p.globalOffset, info.alignment);
        } else {
            // Multi-word set with elements non-constant and/or outside host INTEGER
            // range. We built it at Prep time.
            CG.load_addr_of_temp(p.tmp, 0, M3.target.Integer.align);
            p.tmp = null;
        }
    }

    private static M3CG.Var prepBig(P p, Type.Info info) {
        // PRE: others can contain both non-constant exprs and constant values outside
        //      the range of host INTEGER.
        // Leave result in p.tmp.
        long loWordNo, hiWordNo;
        long loBitNo, hiBitNo;
        Expr.T[] minExpr = new Expr.T[1];
        Expr.T[] maxExpr = new Expr.T[1];
        Expr.T e;
        M3CG.Var t1;
        long nWords;
        long curWordNo;
        int[] curMask = TInt.newInt();
        Node n;
        int[] tmp = TInt.newInt();
        nWords = Math.floorDiv(info.size, Grain);
        t1 = CG.declare_temp(nWords * Grain, M3.target.Word.align,
                             CGType.Struct, /* in_memory := */ true);

        // generate the constant words
        n = p.tree;
        curWordNo = 0;
        TInt.assign(curMask, TInt.Zero);
        while ((n != null)) {
            loWordNo = Math.floorDiv(n.min - p.minI, Grain);
            loBitNo = Math.floorMod(n.min - p.minI, Grain);
            hiWordNo = Math.floorDiv(n.max - p.minI, Grain);
            hiBitNo = Math.floorMod(n.max - p.minI, Grain);
            if ((loWordNo != curWordNo)) {
                emitAssign(t1, curWordNo, curMask);
                for (long i = curWordNo + 1; i <= loWordNo - 1; i++) {
                    emitAssign(t1, i, TInt.Zero);
                }
                curWordNo = loWordNo;
                TInt.assign(curMask, TInt.Zero);
            }
            if ((loWordNo != hiWordNo)) {
                TWord.or(curMask, left[(int) loBitNo], tmp);
                emitAssign(t1, loWordNo, tmp);
                for (long i = loWordNo + 1; i <= hiWordNo - 1; i++) {
                    emitAssign(t1, i, full);
                }
                curWordNo = hiWordNo;
                TInt.assign(curMask, right[(int) hiBitNo]);
            } else { // x = y
                TWord.and(left[(int) loBitNo], right[(int) hiBitNo], tmp);
                TWord.or(curMask, tmp, curMask);
            }
            n = n.next;
        } // while

        // write the last mask
        emitAssign(t1, curWordNo, curMask);

        // write zeros for the remainder of the set
        for (long i = curWordNo + 1; i <= nWords - 1; i++) {
            emitAssign(t1, i, TInt.Zero);
        }

        // finally, add the non-constant elements
        for (int i = 0; i <= p.nOthers - 1; i++) {
            e = p.others[i];
            if (RangeExpr.split(e, minExpr, maxExpr)) {
                CG.load_addr_of(t1, 0, M3.target.Integer.align);
                CG.forceStacked();
                genElement(minExpr[0], p.minT, p.maxT);
                genElement(maxExpr[0], p.minT, p.maxT);
                CG.set_range(info.size);
                // ^For info.size > Target.Word.size, works on in-memory operands.
            } else { // single value
                CG.load_addr_of(t1, 0, M3.target.Integer.align);
                genElement(e, p.minT, p.maxT);
                CG.set_singleton(info.size);
                // ^For info.size > Target.Word.size, works on in-memory operands.
            }
        }

        return t1;
    }

    private static void emitAssign(M3CG.Var set, long wordNo, int[] value) {
        int[] tmp = TInt.newInt();
        TInt.extend(value, (int) M3.target.Integer.bytes, tmp);
        CG.load_integer(M3.target.Integer.cg_type, tmp);
        AssertError.check(Grain == M3.target.Integer.size, "SetExpr");
        CG.store_int(M3.target.Integer.cg_type, set, wordNo * Grain);
    }

    private static void genElement(Expr.T e, int[] minT, int[] maxT) {
        CGType cg_type = Type.cgType(Type.base(Expr.typeOf(e)));
        CheckExpr.emitChecks(e, minT, maxT, M3CG.RuntimeError.ValueOutOfRange);
        if (!TInt.EQ(minT, TInt.Zero)) {
            CG.load_integer(cg_type, minT);
            CG.subtract(cg_type);
        }
        CG.loophole(cg_type, M3.target.Integer.cg_type);
    }

    private static void compileSmall(P p, Type.Info info) {
        // PRE: Entire set fits in a target word.
        // PRE: p.others contains no constants with values outside of host INTEGER.
        long loBitNo, hiBitNo;
        Expr.T[] minExpr = new Expr.T[1];
        Expr.T[] maxExpr = new Expr.T[1];
        Expr.T e;
        int[] curMask = TInt.newInt();
        Node n;
        int[] tmpT = TInt.newInt();
        Type.compile(p.tipe);
        AssertError.check(info.size <= M3.target.Word.size, "SetExpr");

        // generate the constant words
        n = p.tree;
        TInt.assign(curMask, TInt.Zero);
        while ((n != null)) {
            loBitNo = (n.min - p.minI);
            hiBitNo = (n.max - p.minI);
            TWord.and(left[(int) loBitNo], right[(int) hiBitNo], tmpT);
            TWord.or(curMask, tmpT, curMask);
            n = n.next;
        } // WHILE

        // push the mask
        CG.load_integer(M3.target.Word.cg_type, curMask);
        CG.forceStacked();

        // finally, add the non-constant elements
        for (int i = 0; i <= p.nOthers - 1; i++) {
            e = p.others[i];
            if (RangeExpr.split(e, minExpr, maxExpr)) {
                genElement(minExpr[0], p.minT, p.maxT);
                genElement(maxExpr[0], p.minT, p.maxT);
                CG.set_range(info.size);
                // ^For info.size <= Target.Word.size, works on on-stack operands.
            } else { // single value
                genElement(e, p.minT, p.maxT);
                CG.set_singleton(info.size);
                // ^For info.size <= Target.Word.size, works on on-stack operands.
            }
        }
    }

    // Externally dispatched-to:
    private static Expr.T evaluate(Expr.T e) {
        P[] p = new P[1];
        buildMap(e, p);
        if (p[0].is_const) {
            return e;
        } else {
            return null;
        }
    }

    // Externally dispatched-to:
    private static boolean isZeroes(P p, boolean lhs) {
        return (p.args == null) || (p.args.length <= 0);
    }

    // Externally dispatched-to:
    private static void genFPLiteral(P p, M3Buf.T buf) {
        M3Buf.putText(buf, "SET<");
        for (int i = 0; i <= p.args.length - 1; i++) {
            if ((i > 0)) {
                M3Buf.putChar(buf, ',');
            }
            Expr.genFPLiteral(p.args[i], buf);
        }
        M3Buf.putChar(buf, '>');
    }

    // Externally dispatched-to:
    private static void genLiteral(P p, long offset, Type.T type, boolean is_const) {
        long loWordNo, hiWordNo;
        long loBitNo, hiBitNo;
        long curWordNo;
        Node n;
        int[] curMask = TInt.newInt();
        int[] tmpT = TInt.newInt();
        Type.Info info = new Type.Info();
        buildMap(p, new P[] {p});
        AssertError.check(p.others == null, "SetExpr");

        TInt.assign(curMask, TInt.Zero);
        n = p.tree;
        Type.checkInfo(type, info);
        if (info.size <= M3.target.Integer.size) {
            // Set fits in a target word, possibly less.
            while ((n != null)) {
                loBitNo = (n.min - p.minI);
                hiBitNo = (n.max - p.minI);
                AssertError.check(hiBitNo < M3.target.Word.size, "SetExpr");
                TWord.and(left[(int) loBitNo], right[(int) hiBitNo], tmpT);
                TWord.or(curMask, tmpT, curMask);
                n = n.next;
            } // WHILE
            CG.init_int(offset, info.size, curMask, is_const);
        } else { // Multi-word set.  Whole words only.
            curWordNo = 0;
            while ((n != null)) {
                loWordNo = Math.floorDiv(n.min - p.minI, Grain);
                loBitNo = Math.floorMod(n.min - p.minI, Grain);
                hiWordNo = Math.floorDiv(n.max - p.minI, Grain);
                hiBitNo = Math.floorMod(n.max - p.minI, Grain);
                if ((loWordNo != curWordNo)) {
                    // write the mask we've accumulated
                    if (!TInt.EQ(curMask, TInt.Zero)) {
                        TInt.extend(curMask, (int) M3.target.Integer.bytes, tmpT);
                        CG.init_int(offset + curWordNo * M3.target.Integer.pack,
                                    M3.target.Integer.size, tmpT, is_const);
                    }
                    curWordNo = loWordNo;
                    TInt.assign(curMask, TInt.Zero);
                }
                if ((loWordNo != hiWordNo)) {
                    // write the full words [loWordNo..hiWordNo-1]
                    TWord.or(curMask, left[(int) loBitNo], tmpT);
                    TInt.extend(tmpT, (int) M3.target.Integer.bytes, tmpT);
                    CG.init_int(offset + loWordNo * M3.target.Integer.pack, M3.target.Integer.size,
                                tmpT, is_const);
                    for (long i = loWordNo + 1; i <= hiWordNo - 1; i++) {
                        TInt.extend(full, (int) M3.target.Integer.bytes, tmpT);
                        CG.init_int(offset + i * M3.target.Integer.pack, M3.target.Integer.size,
                                    tmpT, is_const);
                    }
                    curWordNo = hiWordNo;
                    TInt.assign(curMask, right[(int) hiBitNo]);
                } else {
                    TWord.and(left[(int) loBitNo], right[(int) hiBitNo], tmpT);
                    TWord.or(curMask, tmpT, curMask);
                }
                n = n.next;
            } // WHILE

            // write the last mask
            if (!TInt.EQ(curMask, TInt.Zero)) {
                TInt.extend(curMask, (int) M3.target.Integer.bytes, tmpT);
                CG.init_int(offset + curWordNo * M3.target.Integer.pack,
                            M3.target.Integer.size, tmpT, is_const);
            }
        }
    }

    public static void init() {
        Grain = M3.target.Word.size;
        TWord.not(TInt.Zero, full);
        TWord.and(full, M3.target.Word.max, full);
        for (long i = 0; i <= Grain - 1; i++) {
            TWord.shift(full, i + 1 - Grain, right[(int) i]);
            TWord.and(right[(int) i], M3.target.Word.max, right[(int) i]);
            TWord.shift(full, i, left[(int) i]);
            TWord.and(left[(int) i], M3.target.Word.max, left[(int) i]);
        }
    }

    private static P setConstrExpr(Expr.T expr) {
        // Look through a NamedExpr and then a ConsExpr, for a SetExpr.T.  NIL if not.

        Expr.T strippedExpr;
        strippedExpr = Expr.stripNamedCons(expr);
        if (strippedExpr == null) {
            return null;
        } else if (strippedExpr instanceof P setExpr) {
            return setExpr;
        } else {
            return null;
        }
    }

    /**
     * Set Code and Msg if they are not set and expr is known to produce a
     * statically unconditional runtime error when evaluated.
     */
    public static void checkStaticRTErrEval(Expr.T expr, M3CG.RuntimeError[] Code, String[] Msg) {
        // Return the first-discovered error found and stored during Check.
        P constrExpr;
        if (Code[0] != M3CG.RuntimeError.Unknown) {
            return;
        }
        constrExpr = setConstrExpr(expr);
        if (constrExpr == null) {
            // nothing
        } else {
            P p = constrExpr;
            AssertError.check(p.checked, "SetExpr");
            if (p.RTErrorCode != M3CG.RuntimeError.Unknown) {
                Code[0] = p.RTErrorCode;
                Msg[0] = p.RTErrorMsg;
            }
        }
    }

    // Externally dispatched-to:
    private static boolean checkUseFailure(P p) {
        AssertError.check(p.checked, "SetExpr");
        if (AssignStmt.doGenRTAbort(p.RTErrorCode) && evaluate(p) != null) {
            CG.comment(p.globalOffset, true,
                       "Use of set constructor with statically detected runtime error: ",
                       p.RTErrorMsg);
            CG.abort(p.RTErrorCode);
            return false;
        } else {
            return true;
        }
    }
}
