// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** An element of a long[] passed as a VAR parameter. */
public final class LongElem extends LongRef {
    private final long[] a;
    private final int i;

    public LongElem(long[] a, int i) {
        this.a = a;
        this.i = i;
    }

    @Override
    public long get() {
        return a[i];
    }

    @Override
    public void set(long v) {
        a[i] = v;
    }
}
