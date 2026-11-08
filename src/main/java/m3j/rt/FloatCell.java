// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** A variable of JVM type float that is passed as a VAR parameter. */
public final class FloatCell extends FloatRef {
    public float v;

    public FloatCell() {}

    public FloatCell(float v) {
        this.v = v;
    }

    @Override
    public float get() {
        return v;
    }

    @Override
    public void set(float v) {
        this.v = v;
    }
}
