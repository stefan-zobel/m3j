// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** An element of a char[] passed as a VAR parameter. */
public final class CharElem extends IntRef {
    private final char[] a;
    private final int i;

    public CharElem(char[] a, int i) {
        this.a = a;
        this.i = i;
    }

    @Override
    public int get() {
        return a[i];
    }

    @Override
    public void set(int v) {
        a[i] = (char) v;
    }
}
