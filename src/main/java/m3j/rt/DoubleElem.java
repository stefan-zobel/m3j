// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** An element of a double[] passed as a VAR parameter. */
public final class DoubleElem extends DoubleRef {
    private final double[] a;
    private final int i;

    public DoubleElem(double[] a, int i) {
        this.a = a;
        this.i = i;
    }

    @Override
    public double get() {
        return a[i];
    }

    @Override
    public void set(double v) {
        a[i] = v;
    }
}
