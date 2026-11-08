// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** A VAR parameter of a scalar type whose JVM type is Object. */
public abstract class ObjRef {
    protected ObjRef() {}

    public abstract Object get();

    public abstract void set(Object v);
}
