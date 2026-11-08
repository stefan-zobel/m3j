// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** An element of a short[] passed as a VAR parameter. */
public final class ShortElem extends IntRef {
    private final short[] a;
    private final int i;

    public ShortElem(short[] a, int i) {
        this.a = a;
        this.i = i;
    }

    @Override
    public int get() {
        return a[i];
    }

    @Override
    public void set(int v) {
        a[i] = (short) v;
    }
}
