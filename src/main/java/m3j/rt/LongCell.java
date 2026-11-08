// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** A variable of JVM type long that is passed as a VAR parameter. */
public final class LongCell extends LongRef {
    public long v;

    public LongCell() {}

    public LongCell(long v) {
        this.v = v;
    }

    @Override
    public long get() {
        return v;
    }

    @Override
    public void set(long v) {
        this.v = v;
    }
}
