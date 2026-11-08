// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import m3j.rt.ObjRef;
import m3j.rt.Rt;

/** The interface Env of libm3: the environment variables. */
public final class Env {
    private Env() {}

    public static final boolean M3$PROVIDED = true;

    private static final List<Map.Entry<String, String>> vars = new ArrayList<>(System.getenv().entrySet());

    public static long Count = vars.size();

    public static String Get(String nm) {
        return System.getenv(nm);
    }

    public static void GetNth(long n, ObjRef nm, ObjRef val) {
        if (n < 0 || n >= vars.size()) {
            throw Rt.error(RuntimeError.ValueOutOfRange);
        }
        nm.set(vars.get((int) n).getKey());
        val.set(vars.get((int) n).getValue());
    }
}
