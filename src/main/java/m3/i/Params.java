// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import m3j.rt.Rt;

/** The interface Params of libm3: the command line. */
public final class Params {
    private Params() {}

    public static final boolean M3$PROVIDED = true;

    /** The number of parameters, the program name included. */
    public static long Count = Rt.args().length + 1;

    /** The exception that cm3's Params.Get raises for n >= Count, which its RAISES clause blocks. */
    static final class FatalError extends m3j.rt.M3Exception {
        private static final long serialVersionUID = 1L;

        @Override
        public String m3Name() {
            return "Params.FatalError";
        }
    }

    /** Parameter n; parameter 0 is the program name. */
    public static String Get(long n) {
        String[] a = Rt.args();
        if (n < 0) {
            throw Rt.error(RuntimeError.ValueOutOfRange);
        }
        if (n > a.length) {
            throw Rt.blocked(new FatalError().at("../src/params/Params.m3", 17));
        }
        return n == 0 ? "pgm" : a[(int) n - 1];
    }
}
