// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** A variable of JVM type Object that is passed as a VAR parameter. */
public final class ObjCell extends ObjRef {
    public Object v;

    public ObjCell() {}

    public ObjCell(Object v) {
        this.v = v;
    }

    @Override
    public Object get() {
        return v;
    }

    @Override
    public void set(Object v) {
        this.v = v;
    }
}
