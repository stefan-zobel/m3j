// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import m3j.rt.M3Exception;

/**
 * The interface FloatMode of m3core, as its specification describes it,
 * for the arithmetic of the JVM: the rounding mode is always
 * NearestElseEven; floating-point operations return the results of IEEE
 * 754 and integer overflow wraps around, without setting a flag (the
 * behavior Ignore), so the set of flags stays empty; integer DIV and MOD
 * by zero stop the operation with the checked runtime error of
 * RuntimeError (reported as the behavior Trap). A request for anything
 * else raises Failure. (CM3 on AMD64_NT fails an assertion in every
 * procedure but GetRounding.)
 */
public final class FloatMode {
    private FloatMode() {}

    public static final boolean M3$PROVIDED = true;

    // The values of RoundingMode.
    static final int NearestElseEven = 0;

    // The values of Flag that are not Ignore.
    static final int IntDivByZero = 6;

    // The values of Behavior.
    static final int BehaviorTrap = 0;
    static final int BehaviorIgnore = 2;

    /** EXCEPTION Failure */
    public static final class Failure extends M3Exception {
        private static final long serialVersionUID = 1L;

        @Override
        public String m3Name() {
            return "FloatMode.Failure";
        }
    }

    /** EXCEPTION Trap (Flag) */
    public static final class Trap extends M3Exception {
        private static final long serialVersionUID = 1L;

        @Override
        public String m3Name() {
            return "FloatMode.Trap";
        }
    }

    /** Sets the rounding mode: only NearestElseEven, else Failure. */
    public static void SetRounding(int md) {
        if (md != NearestElseEven) {
            throw new Failure();
        }
    }

    public static int GetRounding() {
        return NearestElseEven;
    }

    /** No condition sets a flag, so the set is empty. */
    public static long GetFlags() {
        return 0;
    }

    /** Sets the flags: only to the empty set, else Failure; returns the empty set. */
    public static long SetFlags(long s) {
        if (s != 0) {
            throw new Failure();
        }
        return 0;
    }

    /** Turns off a flag, which is already off. */
    public static void ClearFlag(int f) {
        // no flag is ever set
    }

    /** Sets the behavior for f: only to the one it has, else Failure. */
    public static void SetBehavior(int f, int b) {
        if (b != GetBehavior(f)) {
            throw new Failure();
        }
    }

    public static int GetBehavior(int f) {
        return f == IntDivByZero ? BehaviorTrap : BehaviorIgnore;
    }

    /** InitThread (VAR s: ThreadState): does nothing, as in CM3. */
    public static void InitThread(Object s) {
        // nothing to initialize
    }
}
