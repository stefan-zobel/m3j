// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** An element of an int[] passed as a VAR parameter. */
public final class IntElem extends IntRef {
    private final int[] a;
    private final int i;

    public IntElem(int[] a, int i) {
        this.a = a;
        this.i = i;
    }

    @Override
    public int get() {
        return a[i];
    }

    @Override
    public void set(int v) {
        a[i] = v;
    }
}
