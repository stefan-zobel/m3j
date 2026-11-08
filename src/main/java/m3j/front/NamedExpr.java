// Java port of CM3's m3-sys/m3front/src/exprs/NamedExpr.i3 and NamedExpr.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3ID;
import m3j.middle.TInt;
import m3j.util.AssertError;

// File: NamedExpr.i3
// Last Modified On Fri Jun 24 08:47:36 PDT 1994 By kalsow

// File: NamedExpr.m3
// Last modified on Fri Feb 24 16:44:46 PST 1995 by kalsow
//      modified on Fri Dec 21 01:22:10 1990 by muller

public final class NamedExpr {
    private NamedExpr() {}

    public static final class P extends Expr.T {
        public Scope.T scope = null;
        public Value.T value = null;
        public int name = M3ID.NoID;
        public boolean inFold = false;
        public boolean inIsZeroes = false;
        public boolean inGetBounds = false;
        public boolean inTypeOf = false;
        public CG.Val tmp = null;

        @Override
        public Type.T typeOf() {
            return NamedExpr.typeOf(this);
        }

        @Override
        public Type.T repTypeOf() {
            return NamedExpr.repTypeOf(this);
        }

        /**
         * Not in CM3: a WITH name bound to a packed field or element that
         * need not start at a byte (its representation is the packed type
         * of the field) is aligned to a bit only.
         */
        @Override
        public long exprAlign() {
            if (value != null && Value.classOf(value) == Value.Class.Var
                && Variable.isPackedAlias((Variable.T) Value.base(value))) {
                return 1;
            }
            return ExprRep.exprAlignDefault(this);
        }

        @Override
        public void check(M3.CheckState cs) {
            NamedExpr.check(this, cs);
        }

        @Override
        public void need_addr() {
            needsAddress(this);
        }

        @Override
        public void prep() {
            NamedExpr.prep(this);
        }

        @Override
        public void compile(boolean StaticOnly) {
            NamedExpr.compile(this, StaticOnly);
        }

        @Override
        public void prepLV(boolean traced) {
            NamedExpr.prepLV(this, traced);
        }

        @Override
        public void compileLV(boolean traced, boolean StaticOnly) {
            NamedExpr.compileLV(this, traced, StaticOnly);
        }

        @Override
        public void prepBR(long true_, long false_, int freq) {
            ExprRep.prepNoBranch(this, true_, false_, freq);
        }

        @Override
        public void compileBR(long true_, long false_, int freq) {
            ExprRep.noBranch(this, true_, false_, freq);
        }

        @Override
        public Expr.T evaluate() {
            return fold(this);
        }

        @Override
        public boolean isEqual(Expr.T e, Type.Assumption x) {
            return eqCheck(this, e, x);
        }

        @Override
        public void getBounds(int[] min, int[] max) {
            bounder(this, min, max);
        }

        @Override
        public boolean isWritable(boolean lhs) {
            return NamedExpr.isWritable(this, lhs);
        }

        @Override
        public boolean isDesignator(boolean lhs) {
            return NamedExpr.isDesignator(this, lhs);
        }

        @Override
        public boolean isZeroes(boolean lhs) {
            return NamedExpr.isZeroes(this, lhs);
        }

        @Override
        public void note_write() {
            noteWrites(this);
        }

        @Override
        public boolean checkUseFailure() {
            return NamedExpr.checkUseFailure(this);
        }

        // Port: the overrides genFPLiteral := ExprRep.NoFPLiteral,
        // prepLiteral := ExprRep.NoPrepLiteral and genLiteral :=
        // ExprRep.NoLiteral restate the inherited methods.
    }

    /** Port: ARRAY [0..31] OF P. */
    private static final P[] cache = new P[32];

    public static Expr.T new_(int name, Value.T value) {
        P p;
        Scope.T cur_scope = Scope.top();
        int hash = Math.floorMod(name, cache.length);
        //
        //    (* check for a cache hit... *)
        //    p := cache[hash];
        //    IF (p # NIL) AND (p.name = name)
        //      AND (p.scope = cur_scope)
        //      AND (p.value = value) THEN
        //      RETURN p;
        //    END;
        //
        // build a new node
        p = new P();
        p.name = name;
        p.value = value;
        p.scope = cur_scope;
        ExprRep.init(p);
        cache[hash] = p;

        return p;
    }

    public static Expr.T fromValue(Value.T value) {
        P p = new P();
        p.value = value;
        p.name = Value.cName(value);
        ExprRep.init(p);
        return p;
    }

    /** Port: VAR name is {@code name[0]}, VAR obj is {@code obj[0]}. */
    public static boolean split(Expr.T e, int[] name, Value.T[] obj) {
        if (e instanceof P p) {
            if ((p.value == null)) {
                resolve(p);
            }
            name[0] = p.name;
            obj[0] = p.value;
            return true;
        } else {
            return false;
        }
    }

    /** Port: VAR name is {@code name[0]}. */
    public static boolean splitName(Expr.T e, int[] name) {
        if (e instanceof P p) {
            name[0] = p.name;
            return true;
        } else {
            return false;
        }
    }

    private static void resolve(P p) {
        long save;
        if ((p.value == null)) {
            p.value = Scope.lookUp(p.scope, p.name, false);
            if ((p.value == null)) {
                save = Scanner.offset;
                Scanner.offset = p.origin;
                Error.ID(p.name, "undefined");
                p.value = VarExpr.obj(VarExpr.new_(ErrType.T, p.name));
                Scanner.offset = save;
            }
        }
    }

    private static Type.T typeOf(P p) {
        if (p.value == null) {
            resolve(p);
        }
        if (p.type == null) {
            if (p.inTypeOf) {
                Value.illegalRecursion(p.value);
                p.type = ErrType.T;
                p.repType = ErrType.T;
                return ErrType.T;
            }
            p.inTypeOf = true;
            p.type = Value.typeOf(p.value);
            p.inTypeOf = false;
        }
        return p.type;
    }

    private static Type.T repTypeOf(P p) {
        if (p.value == null) {
            resolve(p);
        }
        if (p.repType == null) {
            if (p.inTypeOf) {
                Value.illegalRecursion(p.value);
                p.type = ErrType.T;
                p.repType = ErrType.T;
                return ErrType.T;
            }
            p.inTypeOf = true;
            p.repType = Value.repTypeOf(p.value);
            p.inTypeOf = false;
        }
        return p.repType;
    }

    private static void check(P p, M3.CheckState cs) {
        if ((p.value == null)) {
            resolve(p);
        }
        Value.typeCheck(p.value, cs);
        p.type = Value.typeOf(p.value);
        p.repType = Value.repTypeOf(p.value);
        p.value = Value.base(p.value);
    }

    private static boolean eqCheck(P a, Expr.T e, Type.Assumption x) {
        if ((a.value == null)) {
            resolve(a);
        }
        if (e instanceof P b) {
            if ((b.value == null)) {
                resolve(b);
            }
            return (Value.base(a.value) == Value.base(b.value));
        } else {
            return false;
        }
    }

    private static void needsAddress(P p) {
        if ((p.value == null)) {
            resolve(p);
        }
        switch (Value.classOf(p.value)) {
            case Expr -> Expr.needsAddress(Value.toExpr(p.value));
            case Var -> Variable.needsAddress((Variable.T) p.value);
            default -> throw new AssertError("NamedExpr");
        }
    }

    private static void prep(P p) {
        Type.T[] t = new Type.T[1];
        Type.Info info = new Type.Info();
        boolean[] global = new boolean[1], indirect = new boolean[1],
                  lhs = new boolean[1];
        if ((p.value == null)) {
            resolve(p);
        }
        if (Host.doIncGC && Value.classOf(p.value) == Value.Class.Var) {
            Variable.split((Variable.T) p.value, t, global, indirect, lhs);
            Type.checkInfo(t[0], info);
            if (info.isTraced && (global[0] || indirect[0])) {
                switch (info.class_) {
                    case Object, Opaque, Ref -> {
                        Variable.load((Variable.T) p.value);
                        RunTyme.emitCheckLoadTracedRef();
                        p.tmp = CG.pop();
                    }
                    default -> {
                        // no check
                    }
                }
            }
        }
    }

    private static void compile(P p, boolean StaticOnly) {
        if (!StaticOnly) {
            if (p.tmp == null) {
                Value.load(p.value);
            } else {
                CG.push(p.tmp);
                CG.free(p.tmp);
                p.tmp = null;
            }
        }
    }

    private static void prepLV(P p, boolean traced) {
        if ((p.value == null)) {
            resolve(p);
        }
    }

    private static void compileLV(P p, boolean traced, boolean StaticOnly) {
        if (StaticOnly) {
            return;
        }
        if (p.tmp == null) {
            switch (Value.classOf(p.value)) {
                case Expr -> Value.load(p.value);
                case Var -> Variable.loadLValue((Variable.T) p.value);
                default -> throw new AssertError("NamedExpr");
            }
        } else {
            CG.push(p.tmp);
            CG.free(p.tmp);
            p.tmp = null;
        }
    }

    private static void bounder(P p, int[] min, int[] max) {
        if ((p.value == null)) {
            resolve(p);
        }
        if ((p.inGetBounds)) {
            Value.illegalRecursion(p.value);
            TInt.assign(min, TInt.Zero);
            TInt.assign(max, TInt.One);
            return;
        }
        p.inGetBounds = true;
        switch (Value.classOf(p.value)) {
            case Expr -> Expr.getBounds(Value.toExpr(p.value), min, max);
            case Var -> Variable.getBounds((Variable.T) p.value, min, max);
            default -> Type.getBounds(p.type, min, max);
        }
        p.inGetBounds = false;
    }

    private static Expr.T fold(P p) {
        Expr.T e;
        if ((p.value == null)) {
            resolve(p);
        }
        if ((p.inFold)) {
            Value.illegalRecursion(p.value);
            return null;
        }
        p.inFold = true;
        switch (Value.classOf(p.value)) {
            case Expr -> e = Expr.constValue(Value.toExpr(p.value));
            case Procedure -> e = ProcExpr.new_(p.value);
            case Type -> e = null; // TypeExpr.New (Value.ToType (p.value));
            default -> e = null;
        }
        p.inFold = false;
        return e;
    }

    private static boolean isDesignator(P p, boolean lhs) {
        if ((p.value == null)) {
            resolve(p);
        }
        return (Value.classOf(p.value) == Value.Class.Var);
    }

    private static boolean isWritable(P p, boolean lhs) {
        if ((p.value == null)) {
            resolve(p);
        }
        return Value.isWritable(p.value, lhs);
    }

    private static boolean isZeroes(P p, boolean lhs) {
        boolean b;
        if ((p.value == null)) {
            resolve(p);
        }
        if ((p.inIsZeroes)) {
            Value.illegalRecursion(p.value);
            return true;
        }
        p.inIsZeroes = true;
        b = (Value.classOf(p.value) == Value.Class.Expr)
            && Expr.isZeroes(Value.toExpr(p.value));
        p.inIsZeroes = false;
        return b;
    }

    private static void noteWrites(P p) {
        if ((p.value == null)) {
            resolve(p);
        }
        if ((Value.classOf(p.value) == Value.Class.Var)) {
            Variable.scheduleTrace((Variable.T) p.value);
        }
    }

    // Externally dispatched-to:
    private static boolean checkUseFailure(P p) {
        Expr.T base;
        AssertError.check(p.checked, "NamedExpr");
        base = Value.toExpr(p.value);
        return Expr.checkUseFailure(base);
    }

    // EXPORTED:
    public static boolean is(Expr.T e) {
        if (e instanceof P) {
            return true;
        } else {
            return false;
        }
    }
}
