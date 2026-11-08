// Java port of the instantiation LongModule of CM3's
// m3-sys/m3front/src/builtinWord/Module.ig and Module.mg, as built by
// m3-sys/m3front/src/builtinWord/m3makefile and word.tmpl
// (word ("LInt", "Long")).
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.front.builtinWord.And;
import m3j.front.builtinWord.Divide;
import m3j.front.builtinWord.Extract;
import m3j.front.builtinWord.GE;
import m3j.front.builtinWord.GT;
import m3j.front.builtinWord.Insert;
import m3j.front.builtinWord.LE;
import m3j.front.builtinWord.LT;
import m3j.front.builtinWord.Minus;
import m3j.front.builtinWord.Mod;
import m3j.front.builtinWord.Not;
import m3j.front.builtinWord.Or;
import m3j.front.builtinWord.Plus;
import m3j.front.builtinWord.Rotate;
import m3j.front.builtinWord.Shift;
import m3j.front.builtinWord.Times;
import m3j.front.builtinWord.Xor;

/**
 * The instantiation LongModule of the generic module Module with Rep = LInt.
 *
 * <p>Port: the instantiations LongPlus .. LongInsert and LongModule are the
 * instances below, created in the order of word.tmpl; Rep.T is
 * {@code LInt.T}, read when it is used.
 */
public final class LongModule {
    private LongModule() {}

    private static final Plus LongPlus = new Plus(() -> LInt.T);
    private static final Times LongTimes = new Times(() -> LInt.T);
    private static final Minus LongMinus = new Minus(() -> LInt.T);
    private static final Divide LongDivide = new Divide(() -> LInt.T);
    private static final Mod LongMod = new Mod(() -> LInt.T);
    private static final LT LongLT = new LT(() -> LInt.T);
    private static final LE LongLE = new LE(() -> LInt.T);
    private static final GT LongGT = new GT(() -> LInt.T);
    private static final GE LongGE = new GE(() -> LInt.T);
    private static final And LongAnd = new And(() -> LInt.T);
    private static final Or LongOr = new Or(() -> LInt.T);
    private static final Xor LongXor = new Xor(() -> LInt.T);
    private static final Not LongNot = new Not(() -> LInt.T);
    private static final Shift LongShift = new Shift(() -> LInt.T);
    private static final Rotate LongRotate = new Rotate(() -> LInt.T);
    private static final Extract LongExtract = new Extract(() -> LInt.T);
    private static final Insert LongInsert = new Insert(() -> LInt.T);

    /** Port: the instance that is the generic module's instantiation. */
    private static final m3j.front.builtinWord.Module Instance =
        new m3j.front.builtinWord.Module(() -> LInt.T,
            LongPlus, LongTimes, LongMinus, LongDivide, LongMod,
            LongLT, LongLE, LongGT, LongGE,
            LongAnd, LongOr, LongXor, LongNot,
            LongShift, LongRotate, LongExtract, LongInsert);

    /** Port: the variable M of the instance, set by {@link #initialize}. */
    public static Module.T M;

    public static void initialize(String name) {
        Instance.initialize(name);
        M = Instance.M;
    }
}
