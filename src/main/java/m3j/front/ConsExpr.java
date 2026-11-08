// Java port of CM3's m3-sys/m3front/src/exprs/ConsExpr.i3 and ConsExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

// File: ConsExpr.i3
// Last Modified On Tue Jun 20 15:50:06 PDT 1995 By kalsow
//      Modified On Thu Jun 15 14:03:43 PDT 1995 By ericv

// File: ConsExpr.m3
// Last modified on Tue Jun 20 15:46:56 PDT 1995 by kalsow
//      modified on Thu Jun 15 14:17:19 PDT 1995 by ericv
//      modified on Fri Dec 14 21:41:11 1990 by muller

/**
 * A value constructor. Has an array, record, or set constructor
 * as a child.
 *
 * <p>A value constructor.  Ascertaining whether it is an array, record,
 * or set constructor requires semantic analysis, so this placeholder
 * node is built during parsing.  It stays in the tree, but is given
 * an ArrayExpr.T, RecordExpr.T, or SetExpr.T as a child (in field
 * 'base'), by Seal, later when  its type can be looked up and checked.
 */
public final class ConsExpr {
    private ConsExpr() {}

    public enum Kind { Unknown, NonConstr, Record, Set, Array }

    public static final class P extends Expr.T {
        public Expr.T typeExpr; // An *expression* for the type being constructed.
        public Expr.T[] args;
        public Expr.T base;
        public Kind kind;
        public boolean dots;
        public boolean baseIsNestedArrayConstr = false;
        // ^We're probably gonna want this someday.

        @Override
        public Type.T typeOf() {
            return ConsExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return ConsExpr.repTypeOf(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            ConsExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            ConsExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            ConsExpr.compile(this, StaticOnly);
        }

        @Override
        public Expr.T evaluate() {
            return ConsExpr.evaluate(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public boolean isZeroes(boolean lhs) {
            return ConsExpr.isZeroes(this, lhs);
        }

        @Override
        public long staticLength() {
            return ConsExpr.staticLength(this);
        }

        @Override
        public boolean usesAssignProtocol() {
            return ConsExpr.usesAssignProtocol(this);
        }

        @Override
        public boolean checkUseFailure() {
            return ConsExpr.checkUseFailure(this);
        }

        // Port: the overrides prepLV := ExprRep.NotLValue, compileLV :=
        // ExprRep.NotLValueBool, prepBR, compileBR := ExprRep.NotBoolean,
        // getBounds := ExprRep.NoBounds, isWritable, isDesignator :=
        // ExprRep.IsNever, genFPLiteral := ExprRep.NoFPLiteral, prepLiteral
        // := ExprRep.NoPrepLiteral, genLiteral := ExprRep.NoLiteral and
        // note_write := ExprRep.NotWritable restate the inherited methods.
    }

    public static Expr.T new_(Expr.T typeExpr, Expr.T[] args, boolean dots) {
        P p = new P();
        ExprRep.init(p);
        p.typeExpr = typeExpr;
        p.args = args;
        p.dots = dots;
        p.base = null;
        p.kind = Kind.Unknown;
        p.directAssignableType = true;
        return p;
    }

    public static boolean is(Expr.T e) {
        if (e == null) {
            return false;
        } else if (e instanceof P) {
            return true;
        } else {
            return false;
        }
    }

    /** PRE: Seal (e) or Expr.TypeCheck (e) has been called. */
    public static Expr.T base(Expr.T e) {
        // PRE: Seal (e) or Expr.TypeCheck (e) has been called.
        if (e == null) {
            return null;
        } else if (e instanceof P p) {
            return p.base;
        } else {
            return null;
        }
    }

    // Externally dispatched-to:
    private static Type.T typeOf(P p) {
        Type.T[] ta = new Type.T[1];
        if (TypeExpr.split(p.typeExpr, ta)) {
            return ta[0];
        } else {
            return Expr.typeOf(p.typeExpr);
        }
    }

    // Externally dispatched-to:
    private static Type.T repTypeOf(P p) {
        if (p == null) {
            return ErrType.T;
        }
        innerSeal(p);
        if (p.base == null) {
            return null;
        }
        return p.base.repTypeOf(); // Delegate.
    }

    /** POST: Base will now return a valid result. */
    public static void seal(Expr.T e) {
        // POST: Base will now return a valid result.
        if (e == null) {
            // nothing
        } else if (e instanceof P p) {
            innerSeal(p);
        } else {
            // nothing
        }
    }

    private static void innerSeal(P p) {
        // POST: Base will now return a valid result.
        Type.T consType;
        Type.T[] consType0 = new Type.T[1];
        Type.Info consTypeInfo = new Type.Info();
        if ((p.base != null)) {
            return;
        }
        if (!TypeExpr.split(p.typeExpr, consType0)) {
            return;
        }
        consType = consType0[0];
        consType = Type.stripPacked(consType); // strip named and BITS FOR.
        consType = Type.checkInfo(consType, consTypeInfo);
        if ((consType == null)) {
            // Prior error
        } else {
            switch (consTypeInfo.class_) {
                case Record -> {
                    p.base = RecordExpr.new_(consType, p.args);
                    p.kind = Kind.Record;
                }
                case Set -> {
                    p.base = SetExpr.new_(consType, p.args);
                    p.kind = Kind.Set;
                }
                case Array, OpenArray -> {
                    p.base = ArrayExpr.new_(consType, p.args, p.dots);
                    p.kind = Kind.Array;
                }
                default -> p.kind = Kind.Unknown;
            } // CASE
        }
    }

    private static void checkRecurse(P consExpr, Kind parentKind, M3.CheckState cs) {
        // Recurses only on a Cons/Array pair directly inside a Cons/Array pair,
        // especially, no named expresson in between.
        innerSeal(consExpr);
        if (consExpr.kind == Kind.Unknown) {
            Error.msg("constructor type must be array, record, or set type");
            consExpr.type = ErrType.T;
        } else {
            if (parentKind == Kind.Array && consExpr.kind == Kind.Array) {
                consExpr.baseIsNestedArrayConstr = true;
                // Port: the implicit NARROW of base to ArrayExpr.T.
                ArrayExpr.noteNested((ArrayExpr.T) consExpr.base);
            }
            Expr.typeCheck(consExpr.typeExpr, cs);
            consExpr.type = typeOf(consExpr);
            if (consExpr.dots && consExpr.kind != Kind.Array) {
                Error.msg("trailing '..' in constructor, ignored");
            }

            for (int i = 0; i <= consExpr.args.length - 1; i++) {
                Expr.T argExpr = consExpr.args[i];
                if (argExpr == null) {
                    // nothing
                } else if (argExpr instanceof P argCons) {
                    // Does not include named CONST, particularly, w/ an array
                    // constructor as its value.
                    checkRecurse(argCons, consExpr.kind, cs);
                } else {
                    Expr.typeCheck(argExpr, cs);
                }
            }
        }
        Expr.typeCheck(consExpr.base, cs);
        consExpr.checked = true;
    }

    // Externally dispatched-to:
    private static void check(P p, M3.CheckState cs) {
        checkRecurse(p, Kind.NonConstr, cs);
    }

    // Externally dispatched-to:
    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        innerSeal(a);
        if (e == null) {
            return false;
        } else if (e instanceof P b) {
            innerSeal(b);
            return Expr.isEqual(a.base, b.base, x);
        } else {
            return Expr.isEqual(a.base, e, x);
        }
    }

    // Externally dispatched-to:
    private static void needsAddress(P p) {
        innerSeal(p);
        Expr.needsAddress(p.base);
    }

    // Externally dispatched-to:
    private static void prep(P p) {
        Type.T[] t = new Type.T[1];
        innerSeal(p);
        if (TypeExpr.split(p.typeExpr, t)) {
            Type.compile(t[0]);
        }
        Expr.prep(p.base);
    }

    // Externally dispatched-to:
    private static void compile(P p, boolean StaticOnly) {
        Expr.compile(p.base, StaticOnly);
    }

    // Externally dispatched-to:
    private static Expr.T evaluate(P p) {
        innerSeal(p);
        return Expr.constValue(p.base);
    }

    // Externally dispatched-to:
    private static boolean isZeroes(P p, boolean lhs) {
        innerSeal(p);
        return Expr.isZeroes(p.base);
    }

    // Externally dispatched-to:
    private static long staticLength(P p) {
        if (p == null) {
            return Expr.lengthInvalid;
        }
        innerSeal(p);
        if (p.base == null) {
            return Expr.lengthInvalid;
        }
        return p.base.staticLength(); // Delegate.
    }

    // Externally dispatched-to:
    private static boolean usesAssignProtocol(P p) {
        if (p == null) {
            return false;
        }
        innerSeal(p);
        if (p.base == null) {
            return false;
        }
        return p.base.usesAssignProtocol(); // Delegate.
    }

    // Externally dispatched-to:
    private static boolean checkUseFailure(P p) {
        if (p == null) {
            return true;
        }
        innerSeal(p);
        return Expr.checkUseFailure(p.base); // Delegate.
    }
}
