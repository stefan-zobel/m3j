// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** An element of a float[] passed as a VAR parameter. */
public final class FloatElem extends FloatRef {
    private final float[] a;
    private final int i;

    public FloatElem(float[] a, int i) {
        this.a = a;
        this.i = i;
    }

    @Override
    public float get() {
        return a[i];
    }

    @Override
    public void set(float v) {
        a[i] = v;
    }
}
