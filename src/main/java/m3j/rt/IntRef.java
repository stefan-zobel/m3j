// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/** A VAR parameter of a scalar type whose JVM type is int. */
public abstract class IntRef {
    protected IntRef() {}

    public abstract int get();

    public abstract void set(int v);
}
