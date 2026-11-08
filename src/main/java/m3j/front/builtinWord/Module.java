// Java port of CM3's m3-sys/m3front/src/builtinWord/Module.ig and Module.mg.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front.builtinWord;

import java.util.function.Supplier;

import m3j.front.Constant;
import m3j.front.Int;
import m3j.front.IntegerExpr;
import m3j.front.M3;
import m3j.front.NamedType;
import m3j.front.Scope;
import m3j.front.Tipe;
import m3j.front.Type;
import m3j.middle.TInt;
import m3j.middle.TWord;
import m3j.middle.Target;
import m3j.util.AssertError;

/**
 * Port: the generic module Module (Rep, Plus, Times, Minus, Divide, Mod,
 * LT, LE, GT, GE, And, Or, Xor, Not, Shift, Rotate, Extract, Insert). An
 * instance of this class is an instantiation: the generic parameters are
 * the constructor parameters, the module variable M is an instance field.
 * The front end's module type is written {@code m3j.front.Module}, since
 * this class shadows it.
 */
public final class Module {
    /** Port: the generic parameter Rep; {@code Rep.get()} is Rep.T. */
    private final Supplier<Type.T> Rep;
    private final Plus Plus;
    private final Times Times;
    private final Minus Minus;
    private final Divide Divide;
    private final Mod Mod;
    private final LT LT;
    private final LE LE;
    private final GT GT;
    private final GE GE;
    private final And And;
    private final Or Or;
    private final Xor Xor;
    private final Not Not;
    private final Shift Shift;
    private final Rotate Rotate;
    private final Extract Extract;
    private final Insert Insert;

    public m3j.front.Module.T M;

    public Module(Supplier<Type.T> Rep,
                  Plus Plus, Times Times, Minus Minus, Divide Divide, Mod Mod,
                  LT LT, LE LE, GT GT, GE GE, And And, Or Or, Xor Xor, Not Not,
                  Shift Shift, Rotate Rotate, Extract Extract, Insert Insert) {
        this.Rep = Rep;
        this.Plus = Plus;
        this.Times = Times;
        this.Minus = Minus;
        this.Divide = Divide;
        this.Mod = Mod;
        this.LT = LT;
        this.LE = LE;
        this.GT = GT;
        this.GE = GE;
        this.And = And;
        this.Or = Or;
        this.Xor = Xor;
        this.Not = Not;
        this.Shift = Shift;
        this.Rotate = Rotate;
        this.Extract = Extract;
        this.Insert = Insert;
    }

    /** Port: Rep.T, read when it is used (Rep's Initialize sets it). */
    private Type.T T() {
        return Rep.get();
    }

    private long setRep() {
        int[] min = TInt.newInt(), max = TInt.newInt();
        boolean b;
        b = Type.getBounds(T(), min, max);
        AssertError.check(b, "Module");
        for (int i = 0; i <= M3.targetMap.Word_types.length - 1; i++) {
            Target.Int_type t = M3.targetMap.Word_types[i];
            if (TWord.LE(max, t.max)) {
                return i;
            }
        }
        return M3.targetMap.Word_types.length - 1;
    }

    public void initialize(String name) {
        Scope.T zz;
        int[] size = TInt.newInt();
        boolean b;
        long rep = setRep();
        b = TInt.fromInt(M3.targetMap.Word_types[(int) rep].size, size);
        AssertError.check(b, "Module");
        M = m3j.front.Module.newDefn(name, true, null);

        // WARNING: The following list must be in the same order
        //    as the actual GenWord.ig file, otherwise the version
        //    stamps will be messed up!

        zz = Scope.push(m3j.front.Module.exportScope(M));
        Tipe.define("T", NamedType.new_(T()), false);
        Constant.declare("Size", IntegerExpr.new_(Int.T, size), false);
        Plus.initialize(rep);
        Times.initialize(rep);
        Minus.initialize(rep);
        Divide.initialize(rep);
        Mod.initialize(rep);
        LT.initialize(rep);
        LE.initialize(rep);
        GT.initialize(rep);
        GE.initialize(rep);
        And.initialize(rep);
        Or.initialize(rep);
        Xor.initialize(rep);
        Not.initialize(rep);
        Shift.initialize(rep);
        Rotate.initialize(rep);
        Extract.initialize(rep);
        Insert.initialize(rep);
        Scope.pop(zz);
    }
}
