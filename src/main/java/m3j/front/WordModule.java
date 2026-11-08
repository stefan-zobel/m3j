// Java port of the instantiation WordModule of CM3's
// m3-sys/m3front/src/builtinWord/Module.ig and Module.mg, as built by
// m3-sys/m3front/src/builtinWord/m3makefile and word.tmpl
// (word ("Int", "Word")).
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
 * The instantiation WordModule of the generic module Module with Rep = Int.
 *
 * <p>Port: the instantiations WordPlus .. WordInsert and WordModule are the
 * instances below, created in the order of word.tmpl; Rep.T is
 * {@code Int.T}, read when it is used.
 */
public final class WordModule {
    private WordModule() {}

    private static final Plus WordPlus = new Plus(() -> Int.T);
    private static final Times WordTimes = new Times(() -> Int.T);
    private static final Minus WordMinus = new Minus(() -> Int.T);
    private static final Divide WordDivide = new Divide(() -> Int.T);
    private static final Mod WordMod = new Mod(() -> Int.T);
    private static final LT WordLT = new LT(() -> Int.T);
    private static final LE WordLE = new LE(() -> Int.T);
    private static final GT WordGT = new GT(() -> Int.T);
    private static final GE WordGE = new GE(() -> Int.T);
    private static final And WordAnd = new And(() -> Int.T);
    private static final Or WordOr = new Or(() -> Int.T);
    private static final Xor WordXor = new Xor(() -> Int.T);
    private static final Not WordNot = new Not(() -> Int.T);
    private static final Shift WordShift = new Shift(() -> Int.T);
    private static final Rotate WordRotate = new Rotate(() -> Int.T);
    private static final Extract WordExtract = new Extract(() -> Int.T);
    private static final Insert WordInsert = new Insert(() -> Int.T);

    /** Port: the instance that is the generic module's instantiation. */
    private static final m3j.front.builtinWord.Module Instance =
        new m3j.front.builtinWord.Module(() -> Int.T,
            WordPlus, WordTimes, WordMinus, WordDivide, WordMod,
            WordLT, WordLE, WordGT, WordGE,
            WordAnd, WordOr, WordXor, WordNot,
            WordShift, WordRotate, WordExtract, WordInsert);

    /** Port: the variable M of the instance, set by {@link #initialize}. */
    public static Module.T M;

    public static void initialize(String name) {
        Instance.initialize(name);
        M = Instance.M;
    }
}
