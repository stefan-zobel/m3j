// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** An element of a byte[] of unsigned values passed as a VAR parameter. */
public final class UByteElem extends IntRef {
    private final byte[] a;
    private final int i;

    public UByteElem(byte[] a, int i) {
        this.a = a;
        this.i = i;
    }

    @Override
    public int get() {
        return a[i] & 0xFF;
    }

    @Override
    public void set(int v) {
        a[i] = (byte) v;
    }
}
