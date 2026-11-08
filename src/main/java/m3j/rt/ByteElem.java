// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** An element of a byte[] of signed values passed as a VAR parameter. */
public final class ByteElem extends IntRef {
    private final byte[] a;
    private final int i;

    public ByteElem(byte[] a, int i) {
        this.a = a;
        this.i = i;
    }

    @Override
    public int get() {
        return a[i];
    }

    @Override
    public void set(int v) {
        a[i] = (byte) v;
    }
}
