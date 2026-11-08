// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** A variable of JVM type int that is passed as a VAR parameter. */
public final class IntCell extends IntRef {
    public int v;

    public IntCell() {}

    public IntCell(int v) {
        this.v = v;
    }

    @Override
    public int get() {
        return v;
    }

    @Override
    public void set(int v) {
        this.v = v;
    }
}
