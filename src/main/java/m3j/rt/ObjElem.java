// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** An element of an array of references passed as a VAR parameter. */
public final class ObjElem extends ObjRef {
    private final Object[] a;
    private final int i;

    public ObjElem(Object[] a, int i) {
        this.a = a;
        this.i = i;
    }

    @Override
    public Object get() {
        return a[i];
    }

    @Override
    public void set(Object v) {
        a[i] = v;
    }
}
