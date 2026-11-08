// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** An element of a boolean[] passed as a VAR parameter. */
public final class BoolElem extends IntRef {
    private final boolean[] a;
    private final int i;

    public BoolElem(boolean[] a, int i) {
        this.a = a;
        this.i = i;
    }

    @Override
    public int get() {
        return a[i] ? 1 : 0;
    }

    @Override
    public void set(int v) {
        a[i] = v != 0;
    }
}
