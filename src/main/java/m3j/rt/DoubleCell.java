// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** A variable of JVM type double that is passed as a VAR parameter. */
public final class DoubleCell extends DoubleRef {
    public double v;

    public DoubleCell() {}

    public DoubleCell(double v) {
        this.v = v;
    }

    @Override
    public double get() {
        return v;
    }

    @Override
    public void set(double v) {
        this.v = v;
    }
}
